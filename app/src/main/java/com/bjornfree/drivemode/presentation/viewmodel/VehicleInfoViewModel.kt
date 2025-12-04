package com.bjornfree.drivemode.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bjornfree.drivemode.data.repository.VehicleMetricsRepository
import com.bjornfree.drivemode.domain.model.VehicleMetrics
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
