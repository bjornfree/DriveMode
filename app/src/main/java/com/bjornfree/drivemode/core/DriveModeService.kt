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
import com.bjornfree.drivemode.data.car.CarPropertyManagerSingleton
import com.bjornfree.drivemode.data.preferences.PreferencesManager
import com.bjornfree.drivemode.data.repository.HeatingControlRepository
import com.bjornfree.drivemode.data.repository.IgnitionStateRepository
import com.bjornfree.drivemode.data.repository.VehicleMetricsRepository
import com.bjornfree.drivemode.domain.model.HeatingState
import com.bjornfree.drivemode.ui.theme.BorderOverlayController
import com.bjornfree.drivemode.ui.theme.ModePanelOverlayController
import com.bjornfree.drivemode.ui.theme.DrivingStatusOverlayController
import com.bjornfree.drivemode.ui.theme.DrivingStatusOverlayState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
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

    // --- DI ---

    private val ignitionRepo: IgnitionStateRepository by inject()
    private val vehicleMetricsRepo: VehicleMetricsRepository by inject()
    private val heatingRepo: HeatingControlRepository by inject()
    private val carManager: CarPropertyManagerSingleton by inject()
    private val prefsManager: PreferencesManager by inject()

    // --- Scope / overlays / jobs ---

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var borderOverlay: BorderOverlayController
    private lateinit var modePanelOverlayController: ModePanelOverlayController
    private lateinit var drivingStatusOverlay: DrivingStatusOverlayController

    private var statusOverlayJob: Job? = null
    private var uiMonitorJob: Job? = null
    private var ignitionStateJob: Job? = null
    private var heatingJob: Job? = null

    // --- Drive mode ---

    private var lastShownMode: String? = null
    private var lastShownAt: Long = 0L
    private val DEDUP_WINDOW_MS = 300L

    private lateinit var driveModeManager: DriveModeManager

    private var wakeLock: PowerManager.WakeLock? = null

    // --- Автоподогрев сидений ---

    private val VEHICLE_PROPERTY_HVAC_SEAT_TEMPERATURE = 356517131
    private val AREA_DRIVER = 1
    private val AREA_PASSENGER = 4

    private val manualLevelOverride = mutableMapOf<Int, Int?>()
    private val manualDisabledAreas = mutableSetOf<Int>()
    private val lastSetLevels = mutableMapOf<Int, Int>()

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

        // начальная инициализация полосы
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

        // Старт централизованного мониторинга метрик
        vehicleMetricsRepo.startMonitoring()

        // Подключение к режимам движения
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                driveModeManager = DriveModeManager(applicationContext, this)
                driveModeManager.connect()
            } catch (_: Exception) {
            }
        }, 3000L)

        // Реактивный монитор настроек темы и полосы метрик
        uiMonitorJob = scope.launch {
            var lastIsDark: Boolean? = null
            var lastMetricsBarEnabled: Boolean? = null
            var lastMetricsBarPosition: String? = null

            combine(
                prefsManager.themeModeFlow(),
                prefsManager.metricsBarEnabledFlow(),
                prefsManager.metricsBarPositionFlow()
            ) { themeMode, metricsEnabled, metricsPos ->
                Triple(themeMode, metricsEnabled, metricsPos)
            }.collect { (themeMode, metricsEnabled, metricsPos) ->
                // Тема (как было)
                val isDarkNow = when (themeMode) {
                    "dark" -> true
                    "light" -> false
                    else -> {
                        val uiMode = applicationContext.resources.configuration.uiMode
                        (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                                android.content.res.Configuration.UI_MODE_NIGHT_YES
                    }
                }
                if (isDarkNow != lastIsDark) {
                    drivingStatusOverlay.setDarkTheme(isDarkNow)
                    lastIsDark = isDarkNow
                }

                // Включение / выключение полоски
                if (metricsEnabled != lastMetricsBarEnabled) {
                    drivingStatusOverlay.setEnabled(metricsEnabled)
                    if (metricsEnabled) {
                        drivingStatusOverlay.ensureVisible()
                    } else {
                        drivingStatusOverlay.destroy()
                    }
                    lastMetricsBarEnabled = metricsEnabled
                }

                // Смена позиции
                if (metricsPos != lastMetricsBarPosition && metricsEnabled) {
                    drivingStatusOverlay.destroy()
                    drivingStatusOverlay.setPosition(metricsPos)
                    drivingStatusOverlay.ensureVisible()
                    lastMetricsBarPosition = metricsPos
                }
            }
        }

        // Обновление статусной полосы
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

        // Запуск автоподогрева в core-сервисе
        heatingRepo.startAutoHeating()
        heatingJob = scope.launch(Dispatchers.IO) {
            heatingRepo.heatingState.collect { state ->
                if (state.isActive) {
                    activateSeatHeating(state)
                } else {
                    deactivateSeatHeating(state)
                }
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
                } else {
                    // При выключении зажигания сбрасываем ручные вмешательства в подогрев
                    manualLevelOverride.clear()
                    manualDisabledAreas.clear()
                    lastSetLevels.clear()
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

    override fun onDestroy() {
        ignitionStateJob?.cancel()
        statusOverlayJob?.cancel()
        uiMonitorJob?.cancel()
        heatingJob?.cancel()

        vehicleMetricsRepo.stopMonitoring()
        try {
            heatingRepo.stopAutoHeating()
        } catch (_: Exception) {
        }

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
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --------- Режим вождения ---------

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

        try {
            updateNotification(key)
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

    // --------- Автоподогрев сидений ---------

    private fun activateSeatHeating(state: HeatingState) {
        if (!carManager.isCarApiAvailable()) return

        val hvacLevel = if (state.adaptiveHeating) {
            val temp = state.currentTemp
            when {
                temp == null -> 2
                temp <= 0f -> 3
                temp < 5f -> 2
                temp < 10f -> 1
                else -> 0
            }
        } else {
            state.heatingLevel
        }

        val activeAreas = when (state.mode.key) {
            "driver" -> listOf(AREA_DRIVER)
            "passenger" -> listOf(AREA_PASSENGER)
            "both" -> listOf(AREA_DRIVER, AREA_PASSENGER)
            else -> emptyList()
        }
        if (activeAreas.isEmpty()) return

        val allSeatAreas = listOf(AREA_DRIVER, AREA_PASSENGER)

        if (!state.adaptiveHeating) {
            for (area in allSeatAreas) {
                val currentLevel = getCurrentHeatingLevel(area)
                val expectedLevel = lastSetLevels[area]
                if (currentLevel != null && expectedLevel != null && currentLevel != expectedLevel) {
                    if (currentLevel == 0 && expectedLevel > 0) {
                        manualDisabledAreas.add(area)
                    } else if (currentLevel > 0) {
                        manualLevelOverride[area] = currentLevel
                    }
                }
            }
        }

        for (area in allSeatAreas) {
            if (manualDisabledAreas.contains(area)) continue

            val finalLevel = if (activeAreas.contains(area)) {
                if (!state.adaptiveHeating && manualLevelOverride.containsKey(area)) {
                    manualLevelOverride[area] ?: hvacLevel
                } else {
                    hvacLevel
                }
            } else {
                0
            }

            carManager.writeIntProperty(
                propertyId = VEHICLE_PROPERTY_HVAC_SEAT_TEMPERATURE,
                areaId = area,
                value = finalLevel
            )
            lastSetLevels[area] = finalLevel
        }
    }

    private fun deactivateSeatHeating(state: HeatingState) {
        if (!carManager.isCarApiAvailable()) return

        if (state.turnedOffByTimer) {
            manualLevelOverride.clear()
            manualDisabledAreas.clear()
        }

        val areas = when (state.mode.key) {
            "driver" -> listOf(AREA_DRIVER)
            "passenger" -> listOf(AREA_PASSENGER)
            "both" -> listOf(AREA_DRIVER, AREA_PASSENGER)
            else -> listOf(AREA_DRIVER, AREA_PASSENGER)
        }

        for (area in areas) {
            if (manualDisabledAreas.contains(area)) continue

            carManager.writeIntProperty(
                propertyId = VEHICLE_PROPERTY_HVAC_SEAT_TEMPERATURE,
                areaId = area,
                value = 0
            )
            lastSetLevels[area] = 0
        }
    }

    private fun getCurrentHeatingLevel(area: Int): Int? {
        return carManager.readIntProperty(
            propertyId = VEHICLE_PROPERTY_HVAC_SEAT_TEMPERATURE,
            areaId = area
        )
    }

    // --------- Notification / keep-alive ---------

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

    private fun updateNotification(mode: String) {
        val chId = "drive_mode_service"
        val text = "Режим: ${mode.uppercase()}"
        val n = NotificationCompat.Builder(this, chId)
            .setContentTitle("DriveMode: сервис активен")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    1,
                    n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(1, n)
            }
        } catch (_: Exception) {
        }
    }

    private fun ensureOverlayPermissionTip() {
        // расширяемая точка, сейчас без логики
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