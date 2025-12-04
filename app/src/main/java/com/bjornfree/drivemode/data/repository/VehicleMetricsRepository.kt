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
        private const val UPDATE_INTERVAL_MS = 1000L  // Обновление каждую секунду
    }

    // Реактивный state для UI
    private val _vehicleMetrics = MutableStateFlow(VehicleMetrics())
    val vehicleMetrics: StateFlow<VehicleMetrics> = _vehicleMetrics.asStateFlow()

    // Coroutine scope для мониторинга
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    @Volatile
    private var isMonitoring = false

    /**
     * Запускает мониторинг метрик автомобиля.
     * Обновляет state каждую секунду.
     */
    fun startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "Monitoring already running")
            return
        }

        Log.i(TAG, "Starting vehicle metrics monitoring...")
        isMonitoring = true

        monitorJob = scope.launch {
            while (isActive && isMonitoring) {
                try {
                    // Читаем все метрики
                    val metrics = VehicleMetrics(
                        // Скорость и двигатель
                        speed = readSpeed() ?: 0f,
                        rpm = readRPM() ?: 0,
                        gear = readGear() ?: "P",

                        // Температуры
                        cabinTemperature = readCabinTemperature(),
                        ambientTemperature = readAmbientTemperature(),
                        engineOilTemp = readEngineOilTemp(),
                        coolantTemp = readCoolantTemp(),

                        // Топливо
                        fuel = readFuelData(),
                        rangeRemaining = readRangeRemaining(),
                        averageFuel = readAverageFuel(),

                        // Пробег
                        odometer = readOdometer(),
                        tripMileage = readTripMileage(),
                        tripTime = readTripTime(),

                        // Шины
                        tirePressure = readTirePressureData(),

                        // Батарея и другое
                        batteryLevel = readBatteryLevel(),
                        pm25Status = readPM25Status(),
                        nightMode = readNightMode()
                    )

                    // Обновляем state
                    _vehicleMetrics.value = metrics

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
    }

    // ========================================
    // Методы чтения свойств (консолидация из AutoHeaterService)
    // ========================================

    /**
     * Читает температуру в салоне (°C).
     * Консолидация из AutoHeaterService:688-708
     */
    private fun readCabinTemperature(): Float? {
        val raw = carManager.readIntProperty(VehiclePropertyConstants.CABIN_TEMPERATURE) ?: return null
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
     */
    private fun readCoolantTemp(): Float? {
        val raw = carManager.readIntProperty(VehiclePropertyConstants.COOLANT_TEMP) ?: return null
        return VehiclePropertyConstants.rawToCelsius(raw)
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
     */
    private fun readRPM(): Int? {
        val raw = carManager.readIntProperty(VehiclePropertyConstants.ENGINE_RPM) ?: return null
        return VehiclePropertyConstants.rawToRPM(raw)
    }

    /**
     * Читает текущую передачу.
     * Консолидация из VehicleMetricsService
     */
    private fun readGear(): String? {
        val gearCode = carManager.readIntProperty(VehiclePropertyConstants.GEAR_SELECTION) ?: return null
        return VehiclePropertyConstants.gearToString(gearCode)
    }

    /**
     * Читает запас хода (км).
     */
    private fun readRangeRemaining(): Float? {
        return carManager.readFloatProperty(VehiclePropertyConstants.RANGE_REMAINING)
    }

    /**
     * Читает средний расход топлива (л/100км).
     * Консолидация из AutoHeaterService:797-821
     * Пробует читать из нескольких area IDs.
     */
    private fun readAverageFuel(): Float? {
        for (areaId in VehiclePropertyConstants.AREA_IDS) {
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
        return null
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
     */
    private fun readTripMileage(): Float? {
        return carManager.readFloatProperty(VehiclePropertyConstants.DRIVE_MILEAGE)
    }

    /**
     * Читает время текущей поездки (секунды).
     */
    private fun readTripTime(): Int? {
        return carManager.readIntProperty(VehiclePropertyConstants.DRIVE_TIME)
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
     * Читает данные о топливе.
     * Консолидация из AutoHeaterService:740-759
     *
     * Рассчитывает:
     * - rangeKm из RANGE_REMAINING
     * - currentFuelLiters на основе averageFuel
     * - capacityLiters константа для Geely Coolray
     */
    private fun readFuelData(): FuelData? {
        val rangeKm = readRangeRemaining() ?: return null
        val averageFuel = readAverageFuel() ?: 7.5f  // дефолтный расход

        // Рассчитываем текущий объем топлива
        val currentFuelLiters = if (averageFuel > 0) {
            (rangeKm * averageFuel) / 100f
        } else {
            0f
        }

        // Емкость бака для Geely Binyue L / Coolray
        val capacityLiters = 50f

        return FuelData(
            rangeKm = rangeKm,
            currentFuelLiters = currentFuelLiters.coerceIn(0f, capacityLiters),
            capacityLiters = capacityLiters
        )
    }

    /**
     * Читает данные о давлении в шинах.
     * Консолидация из AutoHeaterService:876-935
     */
    private fun readTirePressureData(): TirePressureData? {
        try {
            // Читаем давление и температуру для каждой шины
            val frontLeft = TireData(
                pressure = carManager.readIntProperty(VehiclePropertyConstants.TPMS_PRESSURE_FL),
                temperature = carManager.readIntProperty(VehiclePropertyConstants.TPMS_TEMP_FL)
            )

            val frontRight = TireData(
                pressure = carManager.readIntProperty(VehiclePropertyConstants.TPMS_PRESSURE_FR),
                temperature = carManager.readIntProperty(VehiclePropertyConstants.TPMS_TEMP_FR)
            )

            val rearLeft = TireData(
                pressure = carManager.readIntProperty(VehiclePropertyConstants.TPMS_PRESSURE_RL),
                temperature = carManager.readIntProperty(VehiclePropertyConstants.TPMS_TEMP_RL)
            )

            val rearRight = TireData(
                pressure = carManager.readIntProperty(VehiclePropertyConstants.TPMS_PRESSURE_RR),
                temperature = carManager.readIntProperty(VehiclePropertyConstants.TPMS_TEMP_RR)
            )

            // Возвращаем только если хотя бы одна шина имеет данные
            if (frontLeft.pressure != null || frontRight.pressure != null ||
                rearLeft.pressure != null || rearRight.pressure != null) {
                return TirePressureData(
                    frontLeft = frontLeft,
                    frontRight = frontRight,
                    rearLeft = rearLeft,
                    rearRight = rearRight
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error reading tire pressure data", e)
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
