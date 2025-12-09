package com.bjornfree.drivemode.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bjornfree.drivemode.data.repository.VehicleMetricsRepository
import com.bjornfree.drivemode.domain.model.VehicleMetrics
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

data class MainMetrics(
    val speed: Float,
    val rpm: Int,
    val gear: String
)

data class FuelMetrics(
    val fuel: com.bjornfree.drivemode.domain.model.FuelData?,
    val rangeRemaining: Float?,
    val averageFuel: Float?
)

data class TripMetrics(
    val odometer: Float?,
    val tripMileage: Float?,
    val tripTime: Int?
)

data class TireMetrics(
    val tirePressure: com.bjornfree.drivemode.domain.model.TirePressureData?
)

data class TemperatureMetrics(
    val cabinTemperature: Float?,
    val ambientTemperature: Float?,
    val engineOilTemp: Float?,
    val coolantTemp: Float?
)

/**
 * ViewModel для VehicleInfoTab (Бортовой ПК).
 *
 * Предоставляет реактивные данные о метриках автомобиля для UI.
 * Заменяет polling loops в ModernTabletUI.
 *
 * До (в ModernTabletUI):
 * ```
 * LaunchedEffect(Unit) {
 *     while (true) {
 *         speed = VehicleMetricsService.getSpeed()
 *         rpm = VehicleMetricsService.getRPM()
 *         delay(200)  // ❌ Polling каждые 200ms
 *     }
 * }
 * ```
 *
 * После (с ViewModel):
 * ```
 * val viewModel: VehicleInfoViewModel = koinViewModel()
 * val metrics by viewModel.vehicleMetrics.collectAsState()
 *
 * Text("Speed: ${metrics.speed}")  // ✅ Реактивно обновляется
 * ```
 *
 * @param metricsRepo repository с метриками автомобиля
 */
class VehicleInfoViewModel(
    private val metricsRepo: VehicleMetricsRepository
) : ViewModel() {

    /**
     * Реактивный поток метрик автомобиля.
     * Автоматически обновляется когда Repository получает новые данные.
     *
     * UI подписывается через collectAsState() и получает обновления
     * без polling loops.
     */
    val vehicleMetrics: StateFlow<VehicleMetrics> = metricsRepo.vehicleMetrics
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = VehicleMetrics()
        )

    // Базовый поток с полными метриками
    private val baseMetrics: StateFlow<VehicleMetrics> = vehicleMetrics

    // Основные метрики: скорость / обороты / передача
    val mainMetrics: StateFlow<MainMetrics> = baseMetrics
        .map { m ->
            MainMetrics(
                speed = m.speed,
                rpm = m.rpm,
                gear = m.gear ?: "P"
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MainMetrics(0f, 0, "P")
        )

    // Метрики топлива
    val fuelMetrics: StateFlow<FuelMetrics> = baseMetrics
        .map { m ->
            FuelMetrics(
                fuel = m.fuel,
                rangeRemaining = m.rangeRemaining,
                averageFuel = m.averageFuel
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FuelMetrics(null, null, null)
        )

    // Пробег и время поездки
    val tripMetrics: StateFlow<TripMetrics> = baseMetrics
        .map { m ->
            TripMetrics(
                odometer = m.odometer,
                tripMileage = m.tripMileage,
                tripTime = m.tripTime
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TripMetrics(null, null, null)
        )

    // Давление в шинах
    val tireMetrics: StateFlow<TireMetrics> = baseMetrics
        .map { m ->
            TireMetrics(
                tirePressure = m.tirePressure
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TireMetrics(null)
        )

    // Температуры
    val temperatureMetrics: StateFlow<TemperatureMetrics> = baseMetrics
        .map { m ->
            TemperatureMetrics(
                cabinTemperature = m.cabinTemperature,
                ambientTemperature = m.ambientTemperature,
                engineOilTemp = m.engineOilTemp,
                coolantTemp = m.coolantTemp
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TemperatureMetrics(null, null, null, null)
        )

    init {
        // Запускаем мониторинг метрик при создании ViewModel
        startMonitoring()
    }

    /**
     * Запускает мониторинг метрик автомобиля.
     */
    private fun startMonitoring() {
        viewModelScope.launch {
            metricsRepo.startMonitoring()
        }
    }

    /**
     * Вызывается когда ViewModel больше не нужна.
     * Останавливает мониторинг для экономии ресурсов.
     */
    override fun onCleared() {
        super.onCleared()
        metricsRepo.stopMonitoring()
    }
}
