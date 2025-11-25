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
import kotlinx.coroutines.*

/**
 * Отдельный сервис для авто-подогрева сидений.
 * Новый сценарий:
 *  - стартуем foreground сервис (при старте ГУ / приложения);
 *  - он ПОСТОЯННО мониторит состояние зажигания в фоне;
 *  - при переходе OFF/ACC -> ON/START автоматически включает подогрев;
 *  - НЕ ОСТАНАВЛИВАЕТСЯ, работает пока не убьют (START_STICKY).
 */
class AutoSeatHeatService : Service() {

    companion object {
        private const val TAG = "AutoSeatHeatService"

        // VehicleProperty IDs
        private const val VEHICLE_PROPERTY_IGNITION_STATE = 289408009
        private const val VEHICLE_PROPERTY_HVAC_SEAT_TEMPERATURE = 356517131
        private const val VEHICLE_PROPERTY_ENV_OUTSIDE_TEMPERATURE = 291505923
        private const val VEHICLE_PROPERTY_HVAC_TEMPERATURE_CURRENT = 358614274

        @Volatile
        private var isRunning = false

        @Volatile
        private var serviceInstance: AutoSeatHeatService? = null

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

        // Получение текущей наружной температуры
        fun getOutsideTemperature(): Float? {
            return serviceInstance?.readOutsideTemperature()
        }

        // Проверка запущен ли сервис
        fun isServiceRunning(): Boolean {
            return isRunning && serviceInstance != null
        }
    }

    private var carObj: Any? = null
    private var carPropertyManagerObj: Any? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null
    private var lastIgnitionState: Int? = null
    private var heatingAppliedForCurrentSession = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        serviceInstance = this
        startForeground(2, buildNotification())
        DriveModeService.logConsole("Автоподогрев: сервис запущен, начинаем мониторинг зажигания")

        // Запускаем постоянный мониторинг зажигания
        startIgnitionMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val test = intent?.getBooleanExtra("test", false) == true

        if (test) {
            // Тестовый режим - применяем подогрев сразу и не останавливаемся
            DriveModeService.logConsole("Автоподогрев: запущен тестовый режим")
            scope.launch {
                applyAutoSeatHeat(test = true)
            }
            return START_STICKY
        }

        // Обычный режим - мониторинг уже запущен в onCreate
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceInstance = null
        monitorJob?.cancel()
        scope.cancel()
        DriveModeService.logConsole("Автоподогрев: сервис остановлен")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startIgnitionMonitoring() {
        monitorJob = scope.launch {
            var consecutiveErrors = 0
            while (isActive) {
                try {
                    val currentState = readIgnitionState()

                    if (currentState != -1) {
                        consecutiveErrors = 0 // Сброс счетчика ошибок при успешном чтении

                        val isOn = isIgnitionOnLike(currentState)
                        val wasOn = lastIgnitionState?.let { isIgnitionOnLike(it) } ?: false

                        // Детектируем переход OFF -> ON
                        if (isOn && !wasOn && lastIgnitionState != null) {
                            DriveModeService.logConsole(
                                "Автоподогрев: зажигание включено (переход OFF→ON: $lastIgnitionState → $currentState)"
                            )

                            // Применяем подогрев только один раз за эту сессию включения
                            if (!heatingAppliedForCurrentSession) {
                                try {
                                    applyAutoSeatHeat(test = false)
                                    heatingAppliedForCurrentSession = true
                                } catch (e: Exception) {
                                    DriveModeService.logConsole(
                                        "Автоподогрев: ОШИБКА применения подогрева: ${e.javaClass.simpleName}: ${e.message}"
                                    )
                                    Log.e(TAG, "Error applying seat heat", e)
                                }
                            }
                        }

                        // Сбрасываем флаг при выключении зажигания
                        if (!isOn && wasOn) {
                            DriveModeService.logConsole(
                                "Автоподогрев: зажигание выключено (переход ON→OFF: $lastIgnitionState → $currentState)"
                            )
                            heatingAppliedForCurrentSession = false
                        }

                        lastIgnitionState = currentState
                    } else {
                        consecutiveErrors++
                        if (consecutiveErrors >= 5) {
                            DriveModeService.logConsole(
                                "Автоподогрев: ВНИМАНИЕ - ${consecutiveErrors} последовательных ошибок чтения зажигания"
                            )
                        }
                    }
                } catch (e: Exception) {
                    consecutiveErrors++
                    DriveModeService.logConsole(
                        "Автоподогрев: ошибка мониторинга (#$consecutiveErrors): ${e.javaClass.simpleName}: ${e.message}"
                    )
                    Log.e(TAG, "Monitor loop error", e)

                    // Если слишком много ошибок подряд, пробуем переинициализировать CarPropertyManager
                    if (consecutiveErrors >= 10) {
                        DriveModeService.logConsole(
                            "Автоподогрев: слишком много ошибок, переинициализация CarPropertyManager..."
                        )
                        carObj = null
                        carPropertyManagerObj = null
                        delay(5000) // Пауза перед повторной инициализацией
                    }
                }

                // Проверяем каждые 2 секунды для быстрой реакции на включение зажигания
                delay(2000)
            }
        }
    }

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
            DriveModeService.logConsole("Автоподогрев: CarPropertyManager недоступен")
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
            DriveModeService.logConsole("Автоподогрев: ошибка чтения зажигания: ${e.javaClass.simpleName}: ${e.message}")
            -1
        }
    }

    fun readOutsideTemperature(): Float? {
        if (!ensureCarPropertyManager()) {
            DriveModeService.logConsole("Автоподогрев: чтение температуры - CarPropertyManager недоступен")
            return null
        }
        val pmObj = carPropertyManagerObj ?: run {
            DriveModeService.logConsole("Автоподогрев: чтение температуры - pmObj равен null")
            return null
        }
        return try {
            val cpmClass = Class.forName("android.car.hardware.property.CarPropertyManager")
            val carPropertyValueClass = Class.forName("android.car.hardware.property.CarPropertyValue")

            // Пробуем прочитать ENV_OUTSIDE_TEMPERATURE
            val getFloatProperty = cpmClass.getMethod(
                "getFloatProperty",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            val outsideTemp = try {
                getFloatProperty.invoke(pmObj, VEHICLE_PROPERTY_ENV_OUTSIDE_TEMPERATURE, 0) as Float
            } catch (e: Exception) {
                DriveModeService.logConsole("Автоподогрев: ошибка чтения ENV_OUTSIDE_TEMPERATURE: ${e.message}")
                null
            }

            DriveModeService.logConsole("Автоподогрев: температура снаружи (ENV_OUTSIDE_TEMPERATURE) = $outsideTemp°C")

            // Пробуем прочитать HVAC_TEMPERATURE_CURRENT (температура салона для сравнения)
            val cabinTemp = try {
                getFloatProperty.invoke(pmObj, VEHICLE_PROPERTY_HVAC_TEMPERATURE_CURRENT, 0) as Float
            } catch (e: Exception) {
                null
            }
            DriveModeService.logConsole("Автоподогрев: температура в салоне (HVAC_TEMPERATURE_CURRENT) = $cabinTemp°C")

            // Детальная информация о ENV_OUTSIDE_TEMPERATURE
            try {
                val getPropertyMethod = cpmClass.getMethod("getProperty", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                val prop = getPropertyMethod.invoke(pmObj, VEHICLE_PROPERTY_ENV_OUTSIDE_TEMPERATURE, 0)

                if (prop != null) {
                    val getValueMethod = carPropertyValueClass.getMethod("getValue")
                    val getTimestampMethod = carPropertyValueClass.getMethod("getTimestamp")
                    val getStatusMethod = carPropertyValueClass.getMethod("getStatus")
                    val getAreaIdMethod = carPropertyValueClass.getMethod("getAreaId")

                    val rawValue = getValueMethod.invoke(prop)
                    val timestamp = getTimestampMethod.invoke(prop)
                    val status = getStatusMethod.invoke(prop)
                    val areaId = getAreaIdMethod.invoke(prop)

                    DriveModeService.logConsole("Автоподогрев: детали свойства ENV_OUTSIDE_TEMPERATURE:")
                    DriveModeService.logConsole("  - значение: $rawValue, тип: ${rawValue?.javaClass?.simpleName}")
                    DriveModeService.logConsole("  - timestamp: $timestamp")
                    DriveModeService.logConsole("  - статус: $status")
                    DriveModeService.logConsole("  - areaId: $areaId")
                }
            } catch (e: Exception) {
                DriveModeService.logConsole("Автоподогрев: не удалось получить детальную информацию о свойстве: ${e.message}")
            }

            // Пробуем разные area ID
            for (testAreaId in listOf(-1, 1, 49, 117)) {
                try {
                    val testValue = getFloatProperty.invoke(pmObj, VEHICLE_PROPERTY_ENV_OUTSIDE_TEMPERATURE, testAreaId) as Float
                    DriveModeService.logConsole("Автоподогрев: ENV_OUTSIDE_TEMPERATURE с area=$testAreaId: $testValue°C")
                } catch (e: Exception) {
                    // Игнорируем ошибки для тестовых area ID
                }
            }

            outsideTemp
        } catch (e: Exception) {
            DriveModeService.logConsole("Автоподогрев: ошибка чтения температуры: ${e.javaClass.simpleName}: ${e.message}")
            Log.e(TAG, "Error reading outside temperature", e)
            null
        }
    }

    // Та же логика, что была в DriveModeService.applyAutoSeatHeat, но локально
    private fun applyAutoSeatHeat(test: Boolean) {
        val prefs = getSharedPreferences("drivemode_prefs", Context.MODE_PRIVATE)
        val mode = prefs.getString("seat_auto_heat_mode", "off") ?: "off"

        if (mode == "off") {
            DriveModeService.logConsole(
                if (test) "Подогрев сидений: тест - режим выключен, ничего не делаем"
                else "Подогрев сидений: автоматический режим выключен, пропускаем"
            )
            return
        }

        // Проверка температурного порога
        val tempThresholdEnabled = prefs.getBoolean("seat_heat_temp_threshold_enabled", false)
        if (tempThresholdEnabled) {
            val outsideTemp = readOutsideTemperature()
            if (outsideTemp == null) {
                DriveModeService.logConsole("Подогрев сидений: температурный порог включен, но не удается прочитать температуру, пропускаем")
                return
            }
            val threshold = prefs.getFloat("seat_heat_temp_threshold", 12f)
            if (outsideTemp >= threshold) {
                DriveModeService.logConsole(
                    "Подогрев сидений: температура ${outsideTemp}°C >= порога ${threshold}°C, пропускаем"
                )
                return
            }
            DriveModeService.logConsole(
                "Подогрев сидений: температура ${outsideTemp}°C < порога ${threshold}°C, включаем"
            )
        }

        if (!ensureCarPropertyManager()) {
            DriveModeService.logConsole("Подогрев сидений: CarPropertyManager недоступен")
            return
        }

        val pmObj = carPropertyManagerObj ?: run {
            DriveModeService.logConsole("Подогрев сидений: CarPropertyManager равен null, невозможно включить подогрев")
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
            DriveModeService.logConsole("Подогрев сидений: нет целей для режима=$mode")
            return
        }

        // Читаем выбранную пользователем мощность обогрева (1-3), по умолчанию 1
        val level = prefs.getInt("seat_heat_level", 1).coerceIn(1, 3)
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
                    DriveModeService.logConsole("Подогрев сидений: установлено area=$areaId уровень=$level (режим=$mode, тест=$test)")
                } catch (e: Exception) {
                    successAll = false
                    DriveModeService.logConsole(
                        "Подогрев сидений: ошибка установки area=$areaId: ${e.javaClass.simpleName}: ${e.message}"
                    )
                }
            }
        } catch (e: Exception) {
            successAll = false
            DriveModeService.logConsole(
                "Подогрев сидений: ошибка вызова через reflection: ${e.javaClass.simpleName}: ${e.message}"
            )
        }

        if (!test) {
            DriveModeService.logConsole("Подогрев сидений: автоматически применен, режим=$mode, успех=$successAll")
        } else {
            DriveModeService.logConsole("Подогрев сидений: тест завершен, режим=$mode, успех=$successAll")
        }
    }
}