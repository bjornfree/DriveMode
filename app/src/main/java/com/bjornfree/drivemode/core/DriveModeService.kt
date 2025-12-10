package com.bjornfree.drivemode.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.bjornfree.drivemode.data.repository.IgnitionStateRepository
import com.bjornfree.drivemode.ui.theme.BorderOverlayController
import com.bjornfree.drivemode.ui.theme.ModePanelOverlayController
import com.bjornfree.drivemode.data.repository.VehicleMetricsRepository
import com.bjornfree.drivemode.ui.theme.DrivingStatusOverlayController
import com.bjornfree.drivemode.ui.theme.DrivingStatusOverlayState
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject

class DriveModeService : Service(), DriveModeListener {

    private enum class DriveMode(val key: String) {
        SPORT("sport"),
        COMFORT("comfort"),
        ECO("eco"),
        ADAPTIVE("adaptive");

        companion object {
            fun fromKeyOrNull(raw: String?): DriveMode? = when (raw?.lowercase()) {
                "sport" -> SPORT
                "comfort", "normal" -> COMFORT
                "eco" -> ECO
                "adaptive" -> ADAPTIVE
                else -> null
            }
        }
    }

    companion object {

        @Volatile
        var isRunning: Boolean = false

        @Volatile
        private var instance: DriveModeService? = null

        @JvmStatic
        fun getServiceStatus(): Boolean {
            return isRunning && instance != null
        }

        @JvmStatic
        fun restartService(context: Context) {
            try {
                context.stopService(Intent(context, DriveModeService::class.java))
                context.startForegroundService(
                    Intent(context, DriveModeService::class.java)
                )
            } catch (_: Exception) {
            }
        }

        @JvmStatic
        fun logConsole(msg: String) {
            // no-op
        }
    }

    private val ignitionRepo: IgnitionStateRepository by inject()
    private val vehicleMetricsRepo: VehicleMetricsRepository by inject()
    private val prefsManager: com.bjornfree.drivemode.data.preferences.PreferencesManager by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var borderOverlay: BorderOverlayController
    private lateinit var modePanelOverlayController: ModePanelOverlayController
    private lateinit var drivingStatusOverlay: DrivingStatusOverlayController

    private var statusOverlayJob: Job? = null
    private var themeMonitorJob: Job? = null
    private var overlaySettingsMonitorJob: Job? = null
    private var ignitionStateJob: Job? = null

    private var lastShownMode: String? = null
    private var lastShownAt: Long = 0L
    private val DEDUP_WINDOW_MS = 300L

    private lateinit var driveModeManager: DriveModeManager

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        instance = this

        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "DriveMode::DriveModeService"
            )
            wakeLock?.acquire(10 * 60 * 1000L)
        } catch (_: Exception) {
        }

        subscribeToIgnitionState()

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(1, notification)
        }

        borderOverlay = BorderOverlayController(applicationContext)
        modePanelOverlayController = ModePanelOverlayController(applicationContext)
        drivingStatusOverlay = DrivingStatusOverlayController(applicationContext)

        drivingStatusOverlay.setEnabled(prefsManager.metricsBarEnabled)
        drivingStatusOverlay.setPosition(prefsManager.metricsBarPosition)

        if (prefsManager.metricsBarEnabled && Settings.canDrawOverlays(applicationContext)) {
            try {
                drivingStatusOverlay.ensureVisible()
            } catch (_: Exception) {
            }
        } else if (!prefsManager.metricsBarEnabled) {
            drivingStatusOverlay.setEnabled(false)
        }

        vehicleMetricsRepo.startMonitoring()

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                driveModeManager = DriveModeManager(applicationContext, this)
                driveModeManager.connect()
            } catch (_: Exception) {
            }
        }, 1000L)

        themeMonitorJob = scope.launch {
            while (isActive) {
                val isDark = when (prefsManager.themeMode) {
                    "dark" -> true
                    "light" -> false
                    else -> {
                        val uiMode = applicationContext.resources.configuration.uiMode
                        (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                                android.content.res.Configuration.UI_MODE_NIGHT_YES
                    }
                }
                drivingStatusOverlay.setDarkTheme(isDark)
                delay(1000)
            }
        }

        overlaySettingsMonitorJob = scope.launch {
            var lastMetricsBarEnabled = prefsManager.metricsBarEnabled
            var lastMetricsBarPosition = prefsManager.metricsBarPosition
            var lastHasOverlayPermission = Settings.canDrawOverlays(applicationContext)

            drivingStatusOverlay.setEnabled(lastMetricsBarEnabled)

            while (isActive) {
                delay(500)

                val hasOverlayPermission = Settings.canDrawOverlays(applicationContext)
                val currentMetricsBarEnabled = prefsManager.metricsBarEnabled
                val currentMetricsBarPosition = prefsManager.metricsBarPosition

                if (currentMetricsBarEnabled != lastMetricsBarEnabled) {
                    drivingStatusOverlay.setEnabled(currentMetricsBarEnabled)

                    if (currentMetricsBarEnabled && hasOverlayPermission) {
                        try {
                            drivingStatusOverlay.ensureVisible()
                        } catch (_: Exception) {
                        }
                    }

                    lastMetricsBarEnabled = currentMetricsBarEnabled
                }

                if (hasOverlayPermission != lastHasOverlayPermission) {
                    if (hasOverlayPermission && currentMetricsBarEnabled) {
                        try {
                            drivingStatusOverlay.ensureVisible()
                            drivingStatusOverlay.setPosition(currentMetricsBarPosition)
                        } catch (_: Exception) {
                        }
                    } else if (!hasOverlayPermission) {
                        try {
                            drivingStatusOverlay.destroy()
                        } catch (_: Exception) {
                        }
                    }
                    lastHasOverlayPermission = hasOverlayPermission
                }

                if (currentMetricsBarPosition != lastMetricsBarPosition &&
                    currentMetricsBarEnabled && hasOverlayPermission
                ) {
                    try {
                        drivingStatusOverlay.destroy()
                        drivingStatusOverlay.setPosition(currentMetricsBarPosition)
                        drivingStatusOverlay.ensureVisible()
                    } catch (_: Exception) {
                    }
                    lastMetricsBarPosition = currentMetricsBarPosition
                }
            }
        }

        statusOverlayJob = scope.launch {
            vehicleMetricsRepo.vehicleMetrics.collect { m ->
                val tire = m.tirePressure

                val modeTitle = lastShownMode?.let { key ->
                    when (DriveMode.fromKeyOrNull(key)) {
                        DriveMode.SPORT -> "SPORT"
                        DriveMode.COMFORT -> "COMFORT"
                        DriveMode.ECO -> "ECO"
                        DriveMode.ADAPTIVE -> "ADAPTIVE"
                        null -> null
                    }
                }

                val state = DrivingStatusOverlayState(
                    modeTitle = modeTitle,
                    gear = m.gear,
                    speedKmh = m.speed?.toInt(),
                    rangeKm = (m.fuel?.rangeKm ?: m.rangeRemaining)?.toInt(),
                    cabinTempC = m.cabinTemperature,
                    ambientTempC = m.ambientTemperature,
                    tirePressureFrontLeft = tire?.frontLeft?.pressure,
                    tirePressureFrontRight = tire?.frontRight?.pressure,
                    tirePressureRearLeft = tire?.rearLeft?.pressure,
                    tirePressureRearRight = tire?.rearRight?.pressure
                )

                drivingStatusOverlay.updateStatus(state)
            }
        }

        ensureOverlayPermissionTip()
        ensureKeepAliveWhitelist()
    }

    private fun subscribeToIgnitionState() {
        ignitionStateJob = scope.launch {
            ignitionRepo.ignitionState.collect { state ->
                if (state.isOn) {
                    val last = lastShownMode
                    if (last != null) {
                        val driveMode = DriveMode.fromKeyOrNull(last)
                        if (driveMode != null) {
                            onDriveModeDetected(driveMode)
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(10 * 60 * 1000L)
            }
        } catch (_: Exception) {
        }

        val modeRaw = intent?.getStringExtra("mode")
        val driveMode = DriveMode.fromKeyOrNull(modeRaw)
        if (driveMode != null) {
            scope.launch(Dispatchers.Main) { onDriveModeDetected(driveMode) }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val restartIntent = Intent(applicationContext, DriveModeService::class.java)
        applicationContext.startForegroundService(restartIntent)
    }

    override fun onDestroy() {
        ignitionStateJob?.cancel()
        statusOverlayJob?.cancel()
        themeMonitorJob?.cancel()
        overlaySettingsMonitorJob?.cancel()

        vehicleMetricsRepo.stopMonitoring()

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        }

        isRunning = false
        instance = null

        try {
            if (::driveModeManager.isInitialized) {
                driveModeManager.disconnect()
            }
        } catch (_: Exception) {
        }

        super.onDestroy()
        scope.cancel()

        try {
            borderOverlay.destroy()
        } catch (_: Exception) {
        }

        try {
            modePanelOverlayController.destroy()
        } catch (_: Exception) {
        }

        try {
            drivingStatusOverlay.destroy()
        } catch (_: Exception) {
        }

        val prefs = getSharedPreferences("drivemode_prefs", Context.MODE_PRIVATE)
        val autoStart = prefs.getBoolean("autostart_on_boot", true)
        if (autoStart) {
            try {
                sendBroadcast(Intent(this, RestartReceiver::class.java))
            } catch (_: Exception) {
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onDriveModeDetected(mode: DriveMode) {
        val key = mode.key
        val now = SystemClock.elapsedRealtime()

        if (lastShownMode == key && (now - lastShownAt) < DEDUP_WINDOW_MS) {
            return
        }

        lastShownMode = key
        lastShownAt = now

        try {
            if (Settings.canDrawOverlays(applicationContext)) {
                if (prefsManager.borderEnabled) {
                    borderOverlay.showMode(key)
                } else {
                    borderOverlay.hide()
                }

                if (prefsManager.panelEnabled) {
                    modePanelOverlayController.showMode(key)
                } else {
                    modePanelOverlayController.hide()
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun onModeDetected(mode: String) {
        val driveMode = DriveMode.fromKeyOrNull(mode) ?: return
        onDriveModeDetected(driveMode)
    }

    override fun onDriveModeChanged(mode: String) {
        onModeDetected(mode)
    }

    private fun buildNotification(): Notification {
        val chId = "drive_mode_service"
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(chId, "DriveMode", NotificationManager.IMPORTANCE_MIN)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, chId)
            .setContentTitle("DriveMode: сервис активен")
            .setContentText("Отслеживание режимов через Car API")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }


    private fun ensureOverlayPermissionTip() {
        // оставлено как расширяемая точка, сейчас без логики
    }

    private fun ensureKeepAliveWhitelist() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        } catch (_: Exception) {
        }
    }
}