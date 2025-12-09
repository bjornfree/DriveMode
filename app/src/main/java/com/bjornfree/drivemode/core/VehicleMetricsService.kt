package com.bjornfree.drivemode.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bjornfree.drivemode.R

/**
 * Сервис для мониторинга параметров автомобиля в реальном времени
 * Использует callbacks для получения обновлений вместо polling
 */
class VehicleMetricsService : Service() {

    companion object {
        private const val TAG = "VehicleMetricsService"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        private var serviceInstance: VehicleMetricsService? = null

        // Текущие значения метрик
        @Volatile
        private var currentSpeed: Float? = null

        @Volatile
        private var currentRPM: Float? = null

        @Volatile
        private var currentGear: String? = null

        // Property IDs
        private const val ECARX_PROPERTY_VEHICLE_SPEED = 0x11600207
        private const val ECARX_PROPERTY_ENGINE_RPM = 0x2140a609  // EMS_ENGINE_SPEED_RPM (Int / 4)
        private const val ECARX_PROPERTY_GEAR_SELECTION = 0x11400401

        /**
         * Запуск сервиса
         */
        fun start(context: Context) {
            try {
                val intent = Intent(context, VehicleMetricsService::class.java)
                context.startForegroundService(intent)
                DriveModeService.logConsole("VehicleMetricsService: запуск сервиса")
            } catch (e: Exception) {
                DriveModeService.logConsole("VehicleMetricsService: ошибка запуска: ${e.message}")
                Log.e(TAG, "Error starting service", e)
            }
        }

        /**
         * Получение текущей скорости
         */
        fun getSpeed(): Float? = currentSpeed

        /**
         * Получение текущих оборотов
         */
        fun getRPM(): Float? = currentRPM

        /**
         * Получение текущей передачи
         */
        fun getGear(): String? = currentGear

        /**
         * Проверка статуса сервиса
         */
        fun isServiceRunning(): Boolean {
            return isRunning && serviceInstance != null
        }

        /**
         * Перезапуск сервиса
         */
        fun restartService(context: Context) {
            try {
                DriveModeService.logConsole("VehicleMetricsService: принудительный перезапуск...")
                context.stopService(Intent(context, VehicleMetricsService::class.java))
                Thread.sleep(500)
                start(context)
                DriveModeService.logConsole("VehicleMetricsService: перезапущен успешно")
            } catch (e: Exception) {
                DriveModeService.logConsole("VehicleMetricsService: ошибка перезапуска: ${e.message}")
            }
        }
    }

    private var carObj: Any? = null
    private var carPropertyManagerObj: Any? = null
    private var speedCallback: Any? = null
    private var rpmCallback: Any? = null
    private var gearCallback: Any? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        serviceInstance = this

        startForeground(3, buildNotification())
        DriveModeService.logConsole("VehicleMetricsService: сервис создан")

        initCarPropertyManager()
        registerCallbacks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DriveModeService.logConsole("VehicleMetricsService: onStartCommand")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceInstance = null

        unregisterCallbacks()
        disconnectCar()

        DriveModeService.logConsole("VehicleMetricsService: сервис остановлен")
    }

    private fun buildNotification(): Notification {
        val channelId = "vehicle_metrics_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Мониторинг параметров автомобиля",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Real-time мониторинг скорости, оборотов, передачи"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Мониторинг параметров")
            .setContentText("Отслеживание скорости, оборотов, передачи")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun initCarPropertyManager() {
        try {
            val carClass = Class.forName("android.car.Car")
            val createCarMethod = carClass.getMethod(
                "createCar",
                Context::class.java
            )
            carObj = createCarMethod.invoke(null, applicationContext)

            val getCarManagerMethod = carClass.getMethod(
                "getCarManager",
                String::class.java
            )
            carPropertyManagerObj = getCarManagerMethod.invoke(carObj, "property")

            if (carPropertyManagerObj != null) {
                DriveModeService.logConsole("VehicleMetricsService: CarPropertyManager инициализирован")
            } else {
                DriveModeService.logConsole("VehicleMetricsService: ошибка - CarPropertyManager = null")
            }
        } catch (e: Exception) {
            DriveModeService.logConsole("VehicleMetricsService: ошибка инициализации: ${e.message}")
        }
    }

    private fun registerCallbacks() {
        val pmObj = carPropertyManagerObj ?: return

        try {
            val cpmClass = Class.forName("android.car.hardware.property.CarPropertyManager")
            val callbackClass = Class.forName("android.car.hardware.property.CarPropertyManager\$CarPropertyEventCallback")
            val registerCallbackMethod = cpmClass.getMethod(
                "registerCallback",
                callbackClass,
                Int::class.javaPrimitiveType,
                Float::class.javaPrimitiveType
            )

            // Callback для скорости
            val speedCallbackId = System.identityHashCode(Any()) // Уникальный ID для callback
            speedCallback = java.lang.reflect.Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass)
            ) { proxy, method, args ->
                when (method.name) {
                    "onChangeEvent" -> {
                        handleSpeedChange(args?.get(0))
                        null
                    }
                    "onErrorEvent" -> {
                        DriveModeService.logConsole("VehicleMetricsService: ошибка callback скорости")
                        null
                    }
                    "hashCode" -> speedCallbackId  // Возвращаем Int, не Any?
                    "equals" -> (proxy === args?.getOrNull(0))
                    "toString" -> "SpeedCallback@$speedCallbackId"
                    else -> null
                }
            }
            registerCallbackMethod.invoke(pmObj, speedCallback, ECARX_PROPERTY_VEHICLE_SPEED, 0f)
            DriveModeService.logConsole("VehicleMetricsService: callback скорости зарегистрирован")

            // Callback для оборотов
            val rpmCallbackId = System.identityHashCode(Any())
            rpmCallback = java.lang.reflect.Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass)
            ) { proxy, method, args ->
                when (method.name) {
                    "onChangeEvent" -> {
                        handleRPMChange(args?.get(0))
                        null
                    }
                    "onErrorEvent" -> {
                        DriveModeService.logConsole("VehicleMetricsService: ошибка callback оборотов")
                        null
                    }
                    "hashCode" -> rpmCallbackId
                    "equals" -> (proxy === args?.getOrNull(0))
                    "toString" -> "RPMCallback@$rpmCallbackId"
                    else -> null
                }
            }
            registerCallbackMethod.invoke(pmObj, rpmCallback, ECARX_PROPERTY_ENGINE_RPM, 0f)
            DriveModeService.logConsole("VehicleMetricsService: callback оборотов зарегистрирован")

            // Callback для передачи
            val gearCallbackId = System.identityHashCode(Any())
            gearCallback = java.lang.reflect.Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass)
            ) { proxy, method, args ->
                when (method.name) {
                    "onChangeEvent" -> {
                        handleGearChange(args?.get(0))
                        null
                    }
                    "onErrorEvent" -> {
                        DriveModeService.logConsole("VehicleMetricsService: ошибка callback передачи")
                        null
                    }
                    "hashCode" -> gearCallbackId
                    "equals" -> (proxy === args?.getOrNull(0))
                    "toString" -> "GearCallback@$gearCallbackId"
                    else -> null
                }
            }
            registerCallbackMethod.invoke(pmObj, gearCallback, ECARX_PROPERTY_GEAR_SELECTION, 0f)
            DriveModeService.logConsole("VehicleMetricsService: callback передачи зарегистрирован")

        } catch (e: Exception) {
            DriveModeService.logConsole("VehicleMetricsService: ошибка регистрации callbacks: ${e.message}")
            Log.e(TAG, "Error registering callbacks", e)
        }
    }

    private fun unregisterCallbacks() {
        val pmObj = carPropertyManagerObj ?: return

        try {
            val cpmClass = Class.forName("android.car.hardware.property.CarPropertyManager")
            val callbackClass = Class.forName("android.car.hardware.property.CarPropertyManager\$CarPropertyEventCallback")
            val unregisterMethod = cpmClass.getMethod("unregisterCallback", callbackClass)

            speedCallback?.let { unregisterMethod.invoke(pmObj, it) }
            rpmCallback?.let { unregisterMethod.invoke(pmObj, it) }
            gearCallback?.let { unregisterMethod.invoke(pmObj, it) }

            DriveModeService.logConsole("VehicleMetricsService: callbacks отменены")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering callbacks", e)
        }
    }

    private fun handleSpeedChange(propertyValue: Any?) {
        try {
            val valueClass = propertyValue?.javaClass ?: return
            val getValueMethod = valueClass.getMethod("getValue")
            val value = getValueMethod.invoke(propertyValue) as? Float

            if (value != null && value != currentSpeed) {
                currentSpeed = value
                // Не логируем каждое изменение чтобы не спамить
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling speed change", e)
        }
    }

    private fun handleRPMChange(propertyValue: Any?) {
        try {
            val valueClass = propertyValue?.javaClass ?: return
            val getValueMethod = valueClass.getMethod("getValue")

            val raw = getValueMethod.invoke(propertyValue) as? Int
            val value = raw?.let { it / 4f }

            if (value != null && value != currentRPM) {
                currentRPM = value
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling RPM change", e)
        }
    }

    private fun handleGearChange(propertyValue: Any?) {
        try {
            val valueClass = propertyValue?.javaClass ?: return
            val getValueMethod = valueClass.getMethod("getValue")
            val gearInt = getValueMethod.invoke(propertyValue) as? Int ?: return

            val gearString = when(gearInt) {
                1 -> "N"
                2 -> "R"
                4 -> "P"
                8 -> "D"
                16 -> "1"
                32 -> "2"
                64 -> "3"
                128 -> "4"
                256 -> "5"
                512 -> "6"
                else -> "?"
            }

            if (gearString != currentGear) {
                currentGear = gearString
                // Не логируем каждое изменение чтобы не спамить
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling gear change", e)
        }
    }

    private fun disconnectCar() {
        try {
            carObj?.let { car ->
                val carClass = car.javaClass
                val disconnectMethod = carClass.getMethod("disconnect")
                disconnectMethod.invoke(car)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting car", e)
        }
    }
}
