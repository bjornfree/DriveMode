package com.bjornfree.drivemode.data.repository

import com.bjornfree.drivemode.data.car.CarPropertyManagerSingleton
import com.bjornfree.drivemode.data.constants.VehiclePropertyConstants
import com.bjornfree.drivemode.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Централизованный репозиторий для метрик автомобиля.
 *
 * - Все чтения свойств идут через CarPropertyManagerSingleton
 * - Быстрые метрики (скорость/RPM/передача) приходят через callbacks от CarPropertyManager
 * - Остальные свойства читаются периодическим polling с уменьшенной нагрузкой
 * - Отдаёт единый StateFlow<VehicleMetrics> для всего приложения
 */
class VehicleMetricsRepository(
    private val carManager: CarPropertyManagerSingleton
) {
    companion object {
        // Интервал опроса "медленных" свойств
        private const val UPDATE_INTERVAL_MS = 1000L

        // Частота обновления быстрых метрик через callback (Гц)
        private const val FAST_PROPERTIES_RATE_HZ = 5f

        // areaId’ы, которые реально работают на этой платформе
        private val SUPPORTED_AVG_FUEL_AREAS = intArrayOf(1, 2)
        private val SUPPORTED_TRIP_MILEAGE_AREAS = intArrayOf(2)
        private val SUPPORTED_TRIP_TIME_AREAS = intArrayOf(2)
        private val SUPPORTED_SERVICE_AREAS = intArrayOf(2, 1)
    }

    // Реактивный state для UI
    private val _vehicleMetrics = MutableStateFlow(VehicleMetrics())
    val vehicleMetrics: StateFlow<VehicleMetrics> = _vehicleMetrics.asStateFlow()

    // Coroutine scope для мониторинга
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    @Volatile
    private var isMonitoring = false

    // Последнее отправленное состояние для отсечения дубликатов
    private var lastEmittedMetrics: VehicleMetrics? = null

    // Флаги поддержки проблемных свойств
    private var isAverageFuelSupported = true
    private var isTripMileageSupported = true
    private var isTripTimeSupported = true

    // --------- быстрые метрики через callback (скорость / RPM / передача) ---------

    @Volatile
    private var latestSpeed: Float? = null

    @Volatile
    private var latestRPM: Int? = null

    @Volatile
    private var latestGear: String? = null

    // токены callback’ов для отписки
    private var speedCallbackToken: Any? = null
    private var rpmCallbackToken: Any? = null
    private var gearCallbackToken: Any? = null

    /**
     * Запускает мониторинг метрик автомобиля.
     * Thread-safe start.
     */
    @Synchronized
    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true

        // Регистрируем callbacks для быстрых свойств
        registerFastPropertyCallbacks()

        monitorJob = scope.launch {
            while (isActive && isMonitoring) {
                try {
                    val previous = lastEmittedMetrics ?: _vehicleMetrics.value

                    val rangeKm = readRangeRemaining()
                    val avgFuel = readAverageFuel()

                    val metrics = previous.copy(
                        // быстрые метрики — из callback’ов, с fallback на прямое чтение
                        speed = latestSpeed ?: readSpeed() ?: previous.speed,
                        rpm = latestRPM ?: readRPM() ?: previous.rpm,
                        gear = latestGear ?: readGear() ?: previous.gear,

                        // температуры
                        cabinTemperature = readCabinTemperature(),
                        ambientTemperature = readAmbientTemperature(),
                        engineOilTemp = readEngineOilTemp(),
                        coolantTemp = readCoolantTemp(),

                        // топливо
                        fuel = readFuelData(rangeKm, avgFuel),
                        rangeRemaining = rangeKm,
                        averageFuel = avgFuel,

                        // пробег
                        odometer = readOdometer(),
                        tripMileage = readTripMileage(),
                        tripTime = readTripTime(),

                        // шины
                        tirePressure = readTirePressureData(),
                    )

                    if (metrics != lastEmittedMetrics) {
                        _vehicleMetrics.value = metrics
                        lastEmittedMetrics = metrics
                    }
                } catch (_: Exception) {
                    // Ошибки чтения просто игнорируются до следующего опроса
                }

                delay(UPDATE_INTERVAL_MS)
            }
        }
    }

    /**
     * Останавливает мониторинг метрик.
     */
    fun stopMonitoring() {
        if (!isMonitoring) return

        isMonitoring = false
        monitorJob?.cancel()
        monitorJob = null
        lastEmittedMetrics = null

        unregisterFastPropertyCallbacks()
    }

    // ========================================
    // Регистрация быстрых колбэков
    // ========================================

    private fun registerFastPropertyCallbacks() {
        // Скорость (км/ч)
        speedCallbackToken = carManager.registerPropertyCallback(
            propertyId = VehiclePropertyConstants.VEHICLE_SPEED,
            rate = FAST_PROPERTIES_RATE_HZ,
            callback = { propId, value ->
                if (propId != VehiclePropertyConstants.VEHICLE_SPEED) return@registerPropertyCallback
                val v = (value as? Float) ?: (value as? Number)?.toFloat()
                latestSpeed = v
            }
        )

        // Обороты двигателя (RPM, raw / 4)
        rpmCallbackToken = carManager.registerPropertyCallback(
            propertyId = VehiclePropertyConstants.ENGINE_RPM,
            rate = FAST_PROPERTIES_RATE_HZ,
            callback = { propId, value ->
                if (propId != VehiclePropertyConstants.ENGINE_RPM) return@registerPropertyCallback
                val raw = (value as? Int) ?: (value as? Number)?.toInt()
                latestRPM = raw?.div(4)
            })

        // Передача
        gearCallbackToken = carManager.registerPropertyCallback(
            propertyId = VehiclePropertyConstants.GEAR_SELECTION,
            rate = FAST_PROPERTIES_RATE_HZ,
            callback = { propId, value ->
                if (propId != VehiclePropertyConstants.GEAR_SELECTION) return@registerPropertyCallback
                val code = (value as? Int) ?: (value as? Number)?.toInt()
                latestGear = code?.let { VehiclePropertyConstants.gearToString(it) }
            })
    }

    private fun unregisterFastPropertyCallbacks() {
        carManager.unregisterPropertyCallback(speedCallbackToken)
        carManager.unregisterPropertyCallback(rpmCallbackToken)
        carManager.unregisterPropertyCallback(gearCallbackToken)

        speedCallbackToken = null
        rpmCallbackToken = null
        gearCallbackToken = null
    }

    // ========================================
    // Методы чтения свойств
    // ========================================

    /**
     * Температура в салоне (°C).
     */
    private fun readCabinTemperature(): Float? {
        val raw =
            carManager.readIntProperty(VehiclePropertyConstants.CABIN_TEMPERATURE) ?: return null
        return VehiclePropertyConstants.rawToCelsius(raw)
    }

    /**
     * Температура снаружи (°C).
     */
    private fun readAmbientTemperature(): Float? {
        val raw = carManager.readIntProperty(VehiclePropertyConstants.AMBIENT_TEMP) ?: return null
        return VehiclePropertyConstants.rawToCelsius(raw)
    }

    /**
     * Температура масла двигателя (°C).
     */
    private fun readEngineOilTemp(): Float? {
        return carManager.readFloatProperty(VehiclePropertyConstants.ENGINE_OIL_TEMP)
    }

    /**
     * Температура охлаждающей жидкости (°C).
     */
    private fun readCoolantTemp(): Float? {
        val raw = carManager.readIntProperty(VehiclePropertyConstants.COOLANT_TEMP) ?: return null
        return raw.toFloat()
    }

    /**
     * Скорость автомобиля (км/ч) — резервный путь, если callback ещё не дал значение.
     */
    private fun readSpeed(): Float? {
        return carManager.readFloatProperty(VehiclePropertyConstants.VEHICLE_SPEED)
    }

    /**
     * Обороты двигателя (RPM), формула raw / 4 — резервный путь.
     */
    private fun readRPM(): Int? {
        val raw = carManager.readIntProperty(VehiclePropertyConstants.ENGINE_RPM, 2) ?: return null
        return raw / 4
    }

    /**
     * Текущая передача — резервный путь.
     */
    private fun readGear(): String? {
        val gearCode =
            carManager.readIntProperty(VehiclePropertyConstants.GEAR_SELECTION) ?: return null
        return VehiclePropertyConstants.gearToString(gearCode)
    }

    /**
     * Запас хода (км).
     */
    private fun readRangeRemaining(): Float? {
        return carManager.readFloatProperty(VehiclePropertyConstants.RANGE_REMAINING)
    }

    /**
     * Средний расход топлива (л/100км).
     * Если свойство не поддерживается, отключаем его после первой ошибки.
     */
    private fun readAverageFuel(): Float? {
        if (!isAverageFuelSupported) return null

        return try {
            for (areaId in SUPPORTED_AVG_FUEL_AREAS) {
                val avgFuel = carManager.readFloatProperty(
                    VehiclePropertyConstants.AVERAGE_FUEL,
                    areaId
                )
                if (avgFuel != null && avgFuel > 0f) {
                    return avgFuel
                }

                val rawInt = carManager.readIntProperty(
                    VehiclePropertyConstants.AVERAGE_FUEL,
                    areaId
                )
                if (rawInt != null && rawInt > 0) {
                    return rawInt / 10f
                }
            }
            null
        } catch (_: Exception) {
            isAverageFuelSupported = false
            null
        }
    }

    /**
     * Общий пробег (км).
     */
    private fun readOdometer(): Float? {
        return carManager.readFloatProperty(VehiclePropertyConstants.ODOMETER)
    }

    /**
     * Пробег текущей поездки (км).
     * Если свойство не поддерживается, отключаем его после первой ошибки.
     */
    private fun readTripMileage(): Float? {
        if (!isTripMileageSupported) return null

        return try {
            for (areaId in SUPPORTED_TRIP_MILEAGE_AREAS) {
                val value = carManager.readFloatProperty(
                    VehiclePropertyConstants.DRIVE_MILEAGE,
                    areaId
                )
                if (value != null && value >= 0f) {
                    return value
                }
            }
            null
        } catch (_: Exception) {
            isTripMileageSupported = false
            null
        }
    }

    /**
     * Время текущей поездки (секунды).
     * Если свойство не поддерживается, отключаем его после первой ошибки.
     */
    private fun readTripTime(): Int? {
        if (!isTripTimeSupported) return null

        return try {
            for (areaId in SUPPORTED_TRIP_TIME_AREAS) {
                val value = carManager.readIntProperty(
                    VehiclePropertyConstants.DRIVE_TIME,
                    areaId
                )
                if (value != null && value >= 0) {
                    return value
                }
            }
            null
        } catch (_: Exception) {
            isTripTimeSupported = false
            null
        }
    }


    /**
     * Данные о топливе на основе rangeKm и averageFuel.
     *
     * capacityLiters — константа (45L) для конкретного авто.
     */
    private fun readFuelData(rangeKm: Float?, averageFuel: Float?): FuelData? {
        val capacityLiters = 45f

        val currentFuelLiters = if (rangeKm != null && averageFuel != null && averageFuel > 0f) {
            (rangeKm * averageFuel) / 100f
        } else {
            null
        }

        return if (rangeKm != null || currentFuelLiters != null) {
            FuelData(
                rangeKm = rangeKm,
                currentFuelLiters = currentFuelLiters?.coerceIn(0f, capacityLiters),
                capacityLiters = capacityLiters
            )
        } else {
            null
        }
    }

    /**
     * Данные о давлении в шинах.
     */
    private fun readTirePressureData(): TirePressureData? {
        return try {
            val frontLeft = TireData(
                pressure = carManager.readFloatProperty(VehiclePropertyConstants.TPMS_PRESSURE_FL)
                    ?.toInt(),
                temperature = carManager.readIntProperty(VehiclePropertyConstants.TPMS_TEMP_FL)
                    ?.let { ((it - 32) * 5) / 9 }
            )

            val frontRight = TireData(
                pressure = carManager.readFloatProperty(VehiclePropertyConstants.TPMS_PRESSURE_FR)
                    ?.toInt(),
                temperature = carManager.readIntProperty(VehiclePropertyConstants.TPMS_TEMP_FR)
                    ?.let { ((it - 32) * 5) / 9 }
            )

            val rearLeft = TireData(
                pressure = carManager.readFloatProperty(VehiclePropertyConstants.TPMS_PRESSURE_RL)
                    ?.toInt(),
                temperature = carManager.readIntProperty(VehiclePropertyConstants.TPMS_TEMP_RL)
                    ?.let { ((it - 32) * 5) / 9 }
            )

            val rearRight = TireData(
                pressure = carManager.readFloatProperty(VehiclePropertyConstants.TPMS_PRESSURE_RR)
                    ?.toInt(),
                temperature = carManager.readIntProperty(VehiclePropertyConstants.TPMS_TEMP_RR)
                    ?.let { ((it - 32) * 5) / 9 }
            )

            if (frontLeft.pressure != null ||
                frontRight.pressure != null ||
                rearLeft.pressure != null ||
                rearRight.pressure != null
            ) {
                TirePressureData(
                    frontLeft = frontLeft,
                    frontRight = frontRight,
                    rearLeft = rearLeft,
                    rearRight = rearRight
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Освобождение ресурсов.
     */
    fun release() {
        stopMonitoring()
        scope.cancel()
    }
}