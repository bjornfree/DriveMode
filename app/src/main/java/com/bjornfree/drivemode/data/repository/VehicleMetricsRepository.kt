package com.bjornfree.drivemode.data.repository

import android.util.Log
import com.bjornfree.drivemode.data.car.CarPropertyManagerSingleton
import com.bjornfree.drivemode.data.constants.VehiclePropertyConstants
import com.bjornfree.drivemode.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository для работы с метриками автомобиля.
 *
 * КРИТИЧЕСКАЯ КОНСОЛИДАЦИЯ:
 * - Заменяет 175+ строк дублированного кода из AutoHeaterService
 * - Централизует все чтение свойств автомобиля в одном месте
 * - Предоставляет реактивный StateFlow вместо polling
 * - Использует CarPropertyManagerSingleton для оптимизации
 *
 * Консолидируемые методы из AutoHeaterService:
 * - readCabinTemperature() (lines 688-708)
 * - readAmbientTemperature() (lines 714-734)
 * - readFuel() (lines 740-759)
 * - readOdometer() (lines 765-770)
 * - readAverageFuel() (lines 797-821)
 * - readTirePressure() (lines 876-935)
 * - И еще 10+ методов
 *
 * @param carManager singleton для работы с CarPropertyManager
 */
class VehicleMetricsRepository(
    private val carManager: CarPropertyManagerSingleton
) {
    companion object {
        private const val TAG = "VehicleMetricsRepo"

        // Частота "быстрых" обновлений (скорость, обороты, передача)
        private const val UPDATE_INTERVAL_MS = 500L  // Обновление каждые полсекунды

        // Каждые N тиков будем обновлять "медленные" метрики (топливо, температуры, пробеги и т.д.)
        private const val SLOW_UPDATE_TICKS = 6L     // То есть раз в 3 секунды
        private const val DEBUG_LOGS = false
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

    // Флаги поддержки проблемных свойств, чтобы не спамить HAL и не ловить исключения каждый тик
    private var isAverageFuelSupported = true
    private var isTripMileageSupported = true
    private var isTripTimeSupported = true

    /**
     * Запускает мониторинг метрик автомобиля.
     * Обновляет state с разделением на быстрые и медленные метрики.
     */
    fun startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "Monitoring already running")
            return
        }

        Log.i(TAG, "Starting vehicle metrics monitoring...")
        isMonitoring = true

        monitorJob = scope.launch {
            var tickCounter = 0L

            while (isActive && isMonitoring) {
                try {
                    tickCounter++
                    val isSlowTick = (tickCounter % SLOW_UPDATE_TICKS == 0L)

                    // Берём предыдущее состояние, чтобы переиспользовать "медленные" поля
                    val previous = lastEmittedMetrics ?: VehicleMetrics()

                    // Быстрые метрики: читаем каждый тик
                    val speed = readSpeed() ?: previous.speed
                    val rpm = readRPM() ?: previous.rpm
                    val gear = readGear() ?: previous.gear

                    val metrics: VehicleMetrics = if (isSlowTick) {
                        // Медленные метрики: читаем раз в несколько тиков
                        val rangeKm = readRangeRemaining()
                        val avgFuel = readAverageFuel()

                        previous.copy(
                            // быстрые поля
                            speed = speed,
                            rpm = rpm,
                            gear = gear,

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

                            // батарея и другое
                            batteryLevel = readBatteryLevel(),
                            pm25Status = readPM25Status(),
                            nightMode = readNightMode(),

                            serviceDaysRemaining = readServiceDaysRemaining(),
                            serviceDistanceRemainingKm = readServiceDistanceRemainingKm()
                        )
                    } else {
                        // На быстрых тиках обновляем только скорость/обороты/передачу,
                        // все остальные значения берём из предыдущего состояния
                        previous.copy(
                            speed = speed,
                            rpm = rpm,
                            gear = gear,
                        )
                    }

                    // Обновляем state только если данные реально изменились
                    if (metrics != lastEmittedMetrics) {
                        _vehicleMetrics.value = metrics
                        lastEmittedMetrics = metrics
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error reading vehicle metrics", e)
                }

                delay(UPDATE_INTERVAL_MS)
            }
        }

        Log.i(TAG, "Vehicle metrics monitoring started")
    }

    /**
     * Останавливает мониторинг метрик.
     */
    fun stopMonitoring() {
        Log.i(TAG, "Stopping vehicle metrics monitoring...")
        isMonitoring = false
        monitorJob?.cancel()
        monitorJob = null
        lastEmittedMetrics = null
    }

    // ========================================
    // Методы чтения свойств (консолидация из AutoHeaterService)
    // ========================================

    /**
     * Читает температуру в салоне (°C).
     * Консолидация из AutoHeaterService:688-708
     */
    private fun readCabinTemperature(): Float? {
        val raw =
            carManager.readIntProperty(VehiclePropertyConstants.CABIN_TEMPERATURE) ?: return null
        return VehiclePropertyConstants.rawToCelsius(raw)
    }

    /**
     * Читает температуру снаружи (°C).
     * Консолидация из AutoHeaterService:714-734
     */
    private fun readAmbientTemperature(): Float? {
        val raw = carManager.readIntProperty(VehiclePropertyConstants.AMBIENT_TEMP) ?: return null
        return VehiclePropertyConstants.rawToCelsius(raw)
    }

    /**
     * Читает температуру масла двигателя (°C).
     */
    private fun readEngineOilTemp(): Float? {
        return carManager.readFloatProperty(VehiclePropertyConstants.ENGINE_OIL_TEMP)
    }

    /**
     * Читает температуру охлаждающей жидкости (°C).
     * На этой платформе свойство приходит как Int, поэтому читаем через readIntProperty.
     */
    private fun readCoolantTemp(): Float? {
        val raw = carManager.readIntProperty(VehiclePropertyConstants.COOLANT_TEMP) ?: return null
        return raw.toFloat()
    }

    /**
     * Читает скорость автомобиля (км/ч).
     */
    private fun readSpeed(): Float? {
        return carManager.readFloatProperty(VehiclePropertyConstants.VEHICLE_SPEED)
    }

    /**
     * Читает обороты двигателя (RPM).
     * Консолидация из VehicleMetricsService
     * Формула: raw / 4
     */
    private fun readRPM(): Int? {
        val raw = carManager.readIntProperty(VehiclePropertyConstants.ENGINE_RPM, 2) ?: return null
        val rpm = raw / 4

        // DEBUG: Логируем RPM только в отладочном режиме
        if (DEBUG_LOGS && rpm > 100) {
            Log.d(TAG, "RPM DEBUG: raw=$raw (0x${raw.toString(16)}), calculated=$rpm")
        }

        return rpm
    }

    /**
     * Читает текущую передачу.
     * Консолидация из VehicleMetricsService
     */
    private fun readGear(): String? {
        val gearCode =
            carManager.readIntProperty(VehiclePropertyConstants.GEAR_SELECTION) ?: return null
        val gearString = VehiclePropertyConstants.gearToString(gearCode)

        // DEBUG: Логируем значения передачи только в отладочном режиме
        if (DEBUG_LOGS) {
            Log.d(
                TAG,
                "GEAR DEBUG: code=$gearCode (0x${gearCode.toString(16)}), string='$gearString'"
            )
        }

        return gearString
    }

    /**
     * Читает запас хода (км).
     */
    private fun readRangeRemaining(): Float? {
        return carManager.readFloatProperty(VehiclePropertyConstants.RANGE_REMAINING)
    }

    /**
     * Читает средний расход топлива (л/100км).
     * Если property не поддерживается и кидает IllegalArgumentException/InvocationTargetException,
     * отключаем его после первой ошибки, чтобы не спамить HAL.
     */
    private fun readAverageFuel(): Float? {
        if (!isAverageFuelSupported) return null

        return try {
            // Для AVERAGE_FUEL_CONSUMPTION по дампу доступны areaId 1 и 2, areaId 0 кидает IllegalArgumentException.
            val supportedAreaIds = listOf(1, 2)
            for (areaId in supportedAreaIds) {
                // Сначала пробуем Float
                val avgFuel = carManager.readFloatProperty(
                    VehiclePropertyConstants.AVERAGE_FUEL,
                    areaId
                )
                if (avgFuel != null && avgFuel > 0) {
                    return avgFuel
                }

                // Потом Int (конвертируем в Float)
                val rawInt = carManager.readIntProperty(
                    VehiclePropertyConstants.AVERAGE_FUEL,
                    areaId
                )
                if (rawInt != null && rawInt > 0) {
                    return rawInt / 10f
                }
            }
            null
        } catch (e: Exception) {
            if (DEBUG_LOGS) {
                Log.w(TAG, "Average fuel property not supported, disabling further reads", e)
            }
            isAverageFuelSupported = false
            null
        }
    }

    /**
     * Читает общий пробег (км).
     * Консолидация из AutoHeaterService:765-770
     */
    private fun readOdometer(): Float? {
        return carManager.readFloatProperty(VehiclePropertyConstants.ODOMETER)
    }

    /**
     * Читает пробег текущей поездки (км).
     * Если property не поддерживается, отключаем его после первой ошибки.
     */
    /**
     * Читает пробег текущей поездки (км).
     * Если property не поддерживается, отключаем его после первой ошибки.
     *
     * ВАЖНО:
     * По логам 0x2740a679 (DRIVE_MILEAGE) с areaId 0 кидает IllegalArgumentException,
     * поэтому пробуем только areaId 1 и 2.
     */
    private fun readTripMileage(): Float? {
        if (!isTripMileageSupported) return null

        return try {
            val supportedAreaIds = listOf(2)
            for (areaId in supportedAreaIds) {
                val value = carManager.readFloatProperty(
                    VehiclePropertyConstants.DRIVE_MILEAGE,
                    areaId
                )
                if (value != null && value >= 0f) {
                    return value
                }
            }
            null
        } catch (e: Exception) {
            if (DEBUG_LOGS) {
                Log.w(TAG, "Trip mileage property not supported, disabling further reads", e)
            }
            isTripMileageSupported = false
            null
        }
    }

    /**
     * Читает время текущей поездки (секунды).
     * Если property не поддерживается, отключаем его после первой ошибки.
     *
     * ВАЖНО:
     * По логам 0x2740a67a (DRIVE_TIME) с areaId 0 кидает IllegalArgumentException,
     * поэтому пробуем только areaId 1 и 2.
     */
    private fun readTripTime(): Int? {
        if (!isTripTimeSupported) return null

        return try {
            val supportedAreaIds = listOf(2)
            for (areaId in supportedAreaIds) {
                val value = carManager.readIntProperty(
                    VehiclePropertyConstants.DRIVE_TIME,
                    areaId
                )
                if (value != null && value >= 0) {
                    return value
                }
            }
            null
        } catch (e: Exception) {
            if (DEBUG_LOGS) {
                Log.w(TAG, "Trip time property not supported, disabling further reads", e)
            }
            isTripTimeSupported = false
            null
        }
    }

    /**
     * Читает уровень батареи 12В (%).
     */
    private fun readBatteryLevel(): Int? {
        return carManager.readIntProperty(VehiclePropertyConstants.BATTERY_LEVEL)
    }

    /**
     * Читает качество воздуха PM2.5.
     */
    private fun readPM25Status(): Int? {
        return carManager.readIntProperty(VehiclePropertyConstants.PM25_STATUS)
    }

    /**
     * Читает ночной режим.
     */
    private fun readNightMode(): Boolean {
        val value = carManager.readIntProperty(VehiclePropertyConstants.NIGHT_MODE)
        return value == 1
    }

    /**
     * Остаток до ТО по времени (дни), AP_TIME_REMAINING: 0x2140301c
     */
    private fun readServiceDaysRemaining(): Int? {
        return try {
            // по аналогии с DRIVE_* используем areaId 2 (и можем добавить 1 на всякий случай)
            val supportedAreaIds = listOf(2, 1)
            for (areaId in supportedAreaIds) {
                val value = carManager.readIntProperty(0x2140301c, areaId)
                if (value != null && value > 0) {
                    return value
                }
            }
            null
        } catch (e: Exception) {
            if (DEBUG_LOGS) {
                Log.w(TAG, "Failed to read AP_TIME_REMAINING (0x2140301c)", e)
            }
            null
        }
    }

    /**
     * Остаток до ТО по пробегу (км), AP_TOTAL_MIL_REMAINING: 0x2140301b
     */
    private fun readServiceDistanceRemainingKm(): Int? {
        return try {
            val supportedAreaIds = listOf(2, 1)
            for (areaId in supportedAreaIds) {
                val value = carManager.readIntProperty(0x2140301b, areaId)
                if (value != null && value > 0) {
                    return value
                }
            }
            null
        } catch (e: Exception) {
            if (DEBUG_LOGS) {
                Log.w(TAG, "Failed to read AP_TOTAL_MIL_REMAINING (0x2140301b)", e)
            }
            null
        }
    }

    /**
     * Читает данные о топливе на основе уже полученных rangeKm и averageFuel.
     *
     * Рассчитывает:
     * - currentFuelLiters из rangeKm и averageFuel
     * - capacityLiters константа для Geely Coolray
     *
     * ВАЖНО:
     * - Не вызывает readAverageFuel() внутри, чтобы не читать property дважды
     * - Не использует 0x11600307, т.к. он всегда возвращает дефолт 1500 на этой платформе
     */
    private fun readFuelData(rangeKm: Float?, averageFuel: Float?): FuelData? {
        val capacityLiters = 45f

        // Вычисляем currentFuelLiters только если есть оба значения
        val currentFuelLiters = if (rangeKm != null && averageFuel != null && averageFuel > 0) {
            if (DEBUG_LOGS) {
                Log.d(
                    TAG,
                    "FuelData: rangeKm=$rangeKm, avgFuel=$averageFuel → currentFuel=${(rangeKm * averageFuel) / 100f}L"
                )
            }
            (rangeKm * averageFuel) / 100f
        } else {
            null
        }

        // Возвращаем данные если есть хоть что-то
        return if (rangeKm != null || currentFuelLiters != null) {
            if (DEBUG_LOGS) {
                Log.d(
                    TAG,
                    "FuelData: Возвращаем данные: rangeKm=$rangeKm, currentFuel=$currentFuelLiters"
                )
            }
            FuelData(
                rangeKm = rangeKm,
                currentFuelLiters = currentFuelLiters?.coerceIn(0f, capacityLiters),
                capacityLiters = capacityLiters
            )
        } else {
            if (DEBUG_LOGS) {
                Log.w(TAG, "FuelData: Нет данных для возврата")
            }
            null
        }
    }

    /**
     * Читает данные о давлении в шинах.
     * Консолидация из AutoHeaterService:876-935
     */
    private fun readTirePressureData(): TirePressureData? {
        try {
            // Читаем давление и температуру для каждой шины
            // Давление возвращается как Float, конвертируем в Int
            // Температура: преобразуем из °F в °C
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

            // Возвращаем только если хотя бы одна шина имеет данные
            if (frontLeft.pressure != null || frontRight.pressure != null ||
                rearLeft.pressure != null || rearRight.pressure != null
            ) {
                return TirePressureData(
                    frontLeft = frontLeft,
                    frontRight = frontRight,
                    rearLeft = rearLeft,
                    rearRight = rearRight
                )
            }

        } catch (e: Exception) {
            if (DEBUG_LOGS) {
                Log.e(TAG, "Error reading tire pressure data", e)
            }
        }

        return null
    }

    /**
     * Освобождает ресурсы.
     * Вызывается при остановке приложения.
     */
    fun release() {
        stopMonitoring()
        scope.cancel()
        Log.i(TAG, "VehicleMetricsRepository released")
    }
}
