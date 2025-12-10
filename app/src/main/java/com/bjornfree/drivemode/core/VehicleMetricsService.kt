package com.bjornfree.drivemode.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.bjornfree.drivemode.R
import java.lang.reflect.Method

class VehicleMetricsService : Service() {

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        private var serviceInstance: VehicleMetricsService? = null

        @Volatile
        private var currentSpeed: Float? = null

        @Volatile
        private var currentRPM: Float? = null

        @Volatile
        private var currentGear: String? = null

        private const val ECARX_PROPERTY_VEHICLE_SPEED = 0x11600207
        private const val ECARX_PROPERTY_ENGINE_RPM = 0x2140a609
        private const val ECARX_PROPERTY_GEAR_SELECTION = 0x11400401

        fun start(context: Context) {
            try {
                val intent = Intent(context, VehicleMetricsService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }

        fun getSpeed(): Float? = currentSpeed
        fun getRPM(): Float? = currentRPM
        fun getGear(): String? = currentGear

        fun isServiceRunning(): Boolean {
            return isRunning && serviceInstance != null
        }

        fun restartService(context: Context) {
            try {
                context.stopService(Intent(context, VehicleMetricsService::class.java))
                start(context)
            } catch (_: Exception) {
            }
        }
    }

    private var carObj: Any? = null
    private var carPropertyManagerObj: Any? = null
    private var speedCallback: Any? = null
    private var rpmCallback: Any? = null
    private var gearCallback: Any? = null

    // Кэширование метода getValue для CarPropertyValue
    private var carPropertyValueGetValueMethod: Method? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        serviceInstance = this

        startForeground(3, buildNotification())

        initCarPropertyManager()
        registerCallbacks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        serviceInstance = null

        unregisterCallbacks()
        disconnectCar()

        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val channelId = "vehicle_metrics_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Мониторинг параметров автомобиля",
                NotificationManager.IMPORTANCE_LOW
            )
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
        } catch (_: Exception) {
            carObj = null
            carPropertyManagerObj = null
        }
    }

    private fun registerCallbacks() {
        val pmObj = carPropertyManagerObj ?: return

        try {
            val cpmClass =
                Class.forName("android.car.hardware.property.CarPropertyManager")
            val callbackClass =
                Class.forName("android.car.hardware.property.CarPropertyManager\$CarPropertyEventCallback")
            val registerCallbackMethod = cpmClass.getMethod(
                "registerCallback",
                callbackClass,
                Int::class.javaPrimitiveType,
                Float::class.javaPrimitiveType
            )

            // Callback скорости
            val speedCallbackId = System.identityHashCode(Any())
            speedCallback = java.lang.reflect.Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass)
            ) { proxy, method, args ->
                when (method.name) {
                    "onChangeEvent" -> {
                        handleSpeedChange(args?.get(0))
                        null
                    }
                    "onErrorEvent" -> null
                    "hashCode" -> speedCallbackId
                    "equals" -> (proxy === args?.getOrNull(0))
                    "toString" -> "SpeedCallback@$speedCallbackId"
                    else -> null
                }
            }
            registerCallbackMethod.invoke(pmObj, speedCallback, ECARX_PROPERTY_VEHICLE_SPEED, 0f)

            // Callback оборотов
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
                    "onErrorEvent" -> null
                    "hashCode" -> rpmCallbackId
                    "equals" -> (proxy === args?.getOrNull(0))
                    "toString" -> "RPMCallback@$rpmCallbackId"
                    else -> null
                }
            }
            registerCallbackMethod.invoke(pmObj, rpmCallback, ECARX_PROPERTY_ENGINE_RPM, 0f)

            // Callback передачи
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
                    "onErrorEvent" -> null
                    "hashCode" -> gearCallbackId
                    "equals" -> (proxy === args?.getOrNull(0))
                    "toString" -> "GearCallback@$gearCallbackId"
                    else -> null
                }
            }
            registerCallbackMethod.invoke(pmObj, gearCallback, ECARX_PROPERTY_GEAR_SELECTION, 0f)

        } catch (_: Exception) {
        }
    }

    private fun unregisterCallbacks() {
        val pmObj = carPropertyManagerObj ?: return

        try {
            val cpmClass =
                Class.forName("android.car.hardware.property.CarPropertyManager")
            val callbackClass =
                Class.forName("android.car.hardware.property.CarPropertyManager\$CarPropertyEventCallback")
            val unregisterMethod = cpmClass.getMethod("unregisterCallback", callbackClass)

            speedCallback?.let { unregisterMethod.invoke(pmObj, it) }
            rpmCallback?.let { unregisterMethod.invoke(pmObj, it) }
            gearCallback?.let { unregisterMethod.invoke(pmObj, it) }
        } catch (_: Exception) {
        } finally {
            speedCallback = null
            rpmCallback = null
            gearCallback = null
        }
    }

    private fun getCarPropertyValue(propertyValue: Any?): Any? {
        if (propertyValue == null) return null
        val method = carPropertyValueGetValueMethod
            ?: try {
                val m = propertyValue.javaClass.getMethod("getValue")
                carPropertyValueGetValueMethod = m
                m
            } catch (_: Exception) {
                null
            }
        return try {
            method?.invoke(propertyValue)
        } catch (_: Exception) {
            null
        }
    }

    private fun handleSpeedChange(propertyValue: Any?) {
        val value = getCarPropertyValue(propertyValue) as? Float ?: return
        if (value != currentSpeed) {
            currentSpeed = value
        }
    }

    private fun handleRPMChange(propertyValue: Any?) {
        val raw = getCarPropertyValue(propertyValue) as? Int ?: return
        val value = raw / 4f
        if (value != currentRPM) {
            currentRPM = value
        }
    }

    private fun handleGearChange(propertyValue: Any?) {
        val gearInt = getCarPropertyValue(propertyValue) as? Int ?: return

        val gearString = when (gearInt) {
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
        }
    }

    private fun disconnectCar() {
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
        }
    }
}