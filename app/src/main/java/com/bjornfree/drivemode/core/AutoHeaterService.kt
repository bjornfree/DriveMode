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

/**
 * Отдельный сервис для авто-подогрева сидений.
 * Сценарий:
 *  - стартуем сервис (при старте ГУ / приложения);
 *  - он читает текущее состояние зажигания;
 *  - если есть переход OFF/ACC -> ON/START – вызывает автоподогрев;
 *  - затем останавливается сам.
 */
class AutoSeatHeatService : Service() {

    companion object {
        private const val TAG = "AutoSeatHeatService"

        // VehicleProperty IDs
        private const val VEHICLE_PROPERTY_IGNITION_STATE = 289408009
        private const val VEHICLE_PROPERTY_HVAC_SEAT_TEMPERATURE = 356517131

        // Запуск сервиса «по-обычному»
        fun start(context: Context) {
            val i = Intent(context, AutoSeatHeatService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        // Тест автоподогрева (аналог triggerSeatHeatTest)
        fun startTest(context: Context) {
            val i = Intent(context, AutoSeatHeatService::class.java)
                .putExtra("test", true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
    }

    private var carObj: Any? = null
    private var carPropertyManagerObj: Any? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(2, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val test = intent?.getBooleanExtra("test", false) == true

        if (test) {
            DriveModeService.logConsole("AutoSeatHeatService: test start")
            applyAutoSeatHeat(test = true)
            stopSelf()
            return START_NOT_STICKY
        }

        // Автоматический режим: прочитать текущее состояние зажигания
        val current = readIgnitionState()
        val isOnLike = isIgnitionOnLike(current)

        DriveModeService.logConsole(
            "AutoSeatHeatService: ignition current=$current (isOnLike=$isOnLike)"
        )

        if (isOnLike) {
            DriveModeService.logConsole("AutoSeatHeatService: ignition ON-like, applying auto seatHeat")
            applyAutoSeatHeat(test = false)
        } else {
            DriveModeService.logConsole("AutoSeatHeatService: ignition not ON-like, skip auto seatHeat")
        }

        // Ничего циклично не держим – выполнили и умерли
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val chId = "auto_seat_heat"
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                chId,
                "Auto seat heating",
                NotificationManager.IMPORTANCE_MIN
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, chId)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Auto seat heating")
            .setContentText("Контроль подогрева сидений")
            .setOngoing(true)
            .build()
    }

    private fun isIgnitionOnLike(state: Int): Boolean {
        // Подстрой под реальные значения, если нужно.
        return when (state) {
            4, 5 -> true        // START / RUN
            2, 0 -> false       // ACC / OFF
            else -> false
        }
    }

    private fun ensureCarPropertyManager(): Boolean {
        try {
            Class.forName("android.car.Car")
            Class.forName("android.car.hardware.property.CarPropertyManager")
        } catch (_: ClassNotFoundException) {
            return false
        } catch (_: Exception) {
            return false
        }
        if (carObj != null && carPropertyManagerObj != null) return true

        return try {
            val carClass = Class.forName("android.car.Car")
            val cpmClass = Class.forName("android.car.hardware.property.CarPropertyManager")

            val createCar = carClass.getMethod("createCar", Context::class.java)
            val carInstance = createCar.invoke(null, this)

            val getCarManagerByClass = try {
                carClass.getMethod("getCarManager", Class::class.java)
            } catch (_: NoSuchMethodException) {
                null
            }

            val pm: Any? = if (getCarManagerByClass != null) {
                getCarManagerByClass.invoke(carInstance, cpmClass)
            } else {
                val getCarManagerByString =
                    carClass.getMethod("getCarManager", String::class.java)
                val propertyServiceField = carClass.getField("PROPERTY_SERVICE")
                val propertyService = try {
                    propertyServiceField.get(null) as? String
                } catch (_: Exception) {
                    propertyServiceField.get(carInstance) as? String
                }
                if (propertyService == null) null
                else getCarManagerByString.invoke(carInstance, propertyService)
            }

            if (pm == null) {
                carObj = carInstance
                carPropertyManagerObj = null
                false
            } else {
                carObj = carInstance
                carPropertyManagerObj = pm
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "ensureCarPropertyManager error", e)
            carObj = null
            carPropertyManagerObj = null
            false
        }
    }

    private fun readIgnitionState(): Int {
        if (!ensureCarPropertyManager()) {
            DriveModeService.logConsole("AutoSeatHeatService: CarPropertyManager not available")
            return -1
        }
        val pmObj = carPropertyManagerObj ?: return -1
        return try {
            val cpmClass = Class.forName("android.car.hardware.property.CarPropertyManager")
            val getIntProperty = cpmClass.getMethod(
                "getIntProperty",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            val value = getIntProperty.invoke(pmObj, VEHICLE_PROPERTY_IGNITION_STATE, 0) as Int
            value
        } catch (e: Exception) {
            DriveModeService.logConsole("AutoSeatHeatService: get ignition error: ${e.javaClass.simpleName}: ${e.message}")
            -1
        }
    }

    // Та же логика, что была в DriveModeService.applyAutoSeatHeat, но локально
    private fun applyAutoSeatHeat(test: Boolean) {
        val prefs = getSharedPreferences("drivemode_prefs", Context.MODE_PRIVATE)
        val mode = prefs.getString("seat_auto_heat_mode", "off") ?: "off"

        if (mode == "off") {
            DriveModeService.logConsole(
                if (test) "seatHeat test: mode=off, nothing to do"
                else "seatHeat auto: mode=off, skip"
            )
            return
        }

        if (!ensureCarPropertyManager()) {
            DriveModeService.logConsole("seatHeat: CarPropertyManager not available")
            return
        }

        val pmObj = carPropertyManagerObj ?: run {
            DriveModeService.logConsole("seatHeat: CarPropertyManager is null, cannot set heat")
            return
        }

        val propertyId = VEHICLE_PROPERTY_HVAC_SEAT_TEMPERATURE
        val targets = when (mode) {
            "driver" -> listOf(1)
            "passenger" -> listOf(4)
            "both" -> listOf(1, 4)
            else -> emptyList()
        }
        if (targets.isEmpty()) {
            DriveModeService.logConsole("seatHeat: no targets for mode=$mode")
            return
        }

        val level = 2 // 1..3
        var successAll = true

        try {
            val cpmClass = Class.forName("android.car.hardware.property.CarPropertyManager")
            val setIntProperty = cpmClass.getMethod(
                "setIntProperty",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            for (areaId in targets) {
                try {
                    setIntProperty.invoke(pmObj, propertyId, areaId, level)
                    DriveModeService.logConsole("seatHeat: set area=$areaId level=$level (mode=$mode, test=$test)")
                } catch (e: Exception) {
                    successAll = false
                    DriveModeService.logConsole(
                        "seatHeat: error set area=$areaId: ${e.javaClass.simpleName}: ${e.message}"
                    )
                }
            }
        } catch (e: Exception) {
            successAll = false
            DriveModeService.logConsole(
                "seatHeat: reflection invocation error: ${e.javaClass.simpleName}: ${e.message}"
            )
        }

        if (!test) {
            DriveModeService.logConsole("seatHeat auto applied: mode=$mode, success=$successAll")
        } else {
            DriveModeService.logConsole("seatHeat test done: mode=$mode, success=$successAll")
        }
    }
}