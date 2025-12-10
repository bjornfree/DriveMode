package com.bjornfree.drivemode.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.bjornfree.drivemode.data.repository.HeatingControlRepository
import com.bjornfree.drivemode.data.repository.IgnitionStateRepository
import com.bjornfree.drivemode.domain.model.HeatingState
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import java.lang.reflect.Method

class AutoSeatHeatService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "auto_seat_heat_channel"

        private const val VEHICLE_PROPERTY_HVAC_SEAT_TEMPERATURE = 356517131
        private const val AREA_DRIVER = 1
        private const val AREA_PASSENGER = 4

        @Volatile
        private var isRunning = false

        @Volatile
        private var serviceInstance: AutoSeatHeatService? = null

        fun start(context: Context) {
            val intent = Intent(context, AutoSeatHeatService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun restartService(context: Context) {
            try {
                context.stopService(Intent(context, AutoSeatHeatService::class.java))
                Handler(Looper.getMainLooper()).postDelayed(
                    { start(context) },
                    500L
                )
            } catch (_: Exception) {
            }
        }

        fun isServiceRunning(): Boolean = isRunning && serviceInstance != null
    }

    private val heatingRepo: HeatingControlRepository by inject()
    private val ignitionRepo: IgnitionStateRepository by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var heatingJob: Job? = null
    private var ignitionMonitorJob: Job? = null

    // Car API
    private var carObj: Any? = null
    private var carPropertyManagerObj: Any? = null
    private var setSeatTempMethod: Method? = null
    private var getSeatTempMethod: Method? = null

    // Ручное управление
    private val manualLevelOverride = mutableMapOf<Int, Int?>()
    private val manualDisabledAreas = mutableSetOf<Int>()
    private val lastSetLevels = mutableMapOf<Int, Int>()

    override fun onCreate() {
        super.onCreate()
        serviceInstance = this
        isRunning = true

        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        initializeCarApi()

        // HeatingControlRepository сам следит за состоянием; запускаем один раз
        heatingRepo.startAutoHeating()

        startHeatingListener()
        startIgnitionMonitor()
    }

    private fun initializeCarApi() {
        try {
            val carClass = Class.forName("android.car.Car")
            val createCarMethod = carClass.getMethod("createCar", Context::class.java)
            val getCarManagerMethod = carClass.getMethod("getCarManager", String::class.java)

            carObj = createCarMethod.invoke(null, applicationContext)
            carPropertyManagerObj = getCarManagerMethod.invoke(carObj, "property")

            carPropertyManagerObj?.let { manager ->
                val managerClass = manager.javaClass
                setSeatTempMethod = managerClass.getMethod(
                    "setIntProperty",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                getSeatTempMethod = managerClass.getMethod(
                    "getIntProperty",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
            }
        } catch (_: Exception) {
            carObj = null
            carPropertyManagerObj = null
            setSeatTempMethod = null
            getSeatTempMethod = null
        }
    }

    private fun startHeatingListener() {
        heatingJob = scope.launch {
            heatingRepo.heatingState.collect { state ->
                if (state.isActive) {
                    activateSeatHeating(state)
                } else {
                    deactivateSeatHeating(state)
                }
            }
        }
    }

    private fun startIgnitionMonitor() {
        ignitionMonitorJob = scope.launch {
            ignitionRepo.ignitionState.collect { ignitionState ->
                if (ignitionState.isOff) {
                    manualLevelOverride.clear()
                    manualDisabledAreas.clear()
                    lastSetLevels.clear()
                }
            }
        }
    }

    private fun activateSeatHeating(state: HeatingState) {
        val manager = carPropertyManagerObj ?: return
        val setter = setSeatTempMethod ?: return

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

            try {
                setter.invoke(
                    manager,
                    VEHICLE_PROPERTY_HVAC_SEAT_TEMPERATURE,
                    area,
                    finalLevel
                )
                lastSetLevels[area] = finalLevel
            } catch (_: Exception) {
            }
        }
    }

    private fun deactivateSeatHeating(state: HeatingState) {
        val manager = carPropertyManagerObj ?: return
        val setter = setSeatTempMethod ?: return

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

            try {
                setter.invoke(
                    manager,
                    VEHICLE_PROPERTY_HVAC_SEAT_TEMPERATURE,
                    area,
                    0
                )
                lastSetLevels[area] = 0
            } catch (_: Exception) {
            }
        }
    }

    private fun getCurrentHeatingLevel(area: Int): Int? {
        val manager = carPropertyManagerObj ?: return null
        val getter = getSeatTempMethod ?: return null

        return try {
            val result = getter.invoke(
                manager,
                VEHICLE_PROPERTY_HVAC_SEAT_TEMPERATURE,
                area
            )
            result as? Int
        } catch (_: Exception) {
            null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        serviceInstance = null

        try {
            heatingRepo.stopAutoHeating()
        } catch (_: Exception) {
        }

        heatingJob?.cancel()
        ignitionMonitorJob?.cancel()
        scope.cancel()

        disconnectCarApi()

        super.onDestroy()
    }

    private fun disconnectCarApi() {
        try {
            carObj?.let { car ->
                val carClass = car.javaClass
                val disconnectMethod = carClass.getMethod("disconnect")
                disconnectMethod.invoke(car)
            }
        } catch (_: Exception) {
        } finally {
            carObj = null
            carPropertyManagerObj = null
            setSeatTempMethod = null
            getSeatTempMethod = null
        }
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Автоподогрев сидений",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Автоподогрев активен")
            .setContentText("Мониторинг температуры и зажигания")
            .setSmallIcon(android.R.drawable.ic_menu_preferences)
            .setOngoing(true)
            .build()
    }
}