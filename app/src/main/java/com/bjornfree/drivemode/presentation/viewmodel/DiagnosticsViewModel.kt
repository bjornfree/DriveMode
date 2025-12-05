package com.bjornfree.drivemode.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bjornfree.drivemode.data.car.CarPropertyManagerSingleton
import com.bjornfree.drivemode.data.repository.VehicleMetricsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel для DiagnosticsTab (Диагностика).
 *
 * Предоставляет информацию о статусе сервисов и результатах диагностических тестов.
 *
 * Функции:
 * - Проверка статуса сервисов (работают ли)
 * - Проверка доступности Car API
 * - Статус CarPropertyManager
 * - Количество обновлений метрик
 * - Диагностические тесты (опционально)
 *
 * @param carManager singleton для диагностики Car API
 * @param metricsRepo repository для статуса метрик
 */
class DiagnosticsViewModel(
    private val carManager: CarPropertyManagerSingleton,
    private val metricsRepo: VehicleMetricsRepository
) : ViewModel() {

    /**
     * Статус Car API.
     */
    private val _carApiStatus = MutableStateFlow<ServiceStatus>(ServiceStatus.Unknown)
    val carApiStatus: StateFlow<ServiceStatus> = _carApiStatus.asStateFlow()

    /**
     * Статус CarPropertyManager.
     */
    private val _carManagerStatus = MutableStateFlow<ServiceStatus>(ServiceStatus.Unknown)
    val carManagerStatus: StateFlow<ServiceStatus> = _carManagerStatus.asStateFlow()

    /**
     * Счетчик обновлений метрик.
     */
    private val _metricsUpdateCount = MutableStateFlow(0)
    val metricsUpdateCount: StateFlow<Int> = _metricsUpdateCount.asStateFlow()

    /**
     * Последнее время обновления метрик.
     */
    private val _lastMetricsUpdate = MutableStateFlow<Long?>(null)
    val lastMetricsUpdate: StateFlow<Long?> = _lastMetricsUpdate.asStateFlow()

    /**
     * Сообщения диагностики.
     */
    private val _diagnosticMessages = MutableStateFlow<List<String>>(emptyList())
    val diagnosticMessages: StateFlow<List<String>> = _diagnosticMessages.asStateFlow()

    init {
        checkServicesStatus()
        monitorMetricsUpdates()
    }

    /**
     * Проверяет статус всех сервисов.
     */
    fun checkServicesStatus() {
        viewModelScope.launch {
            addDiagnosticMessage("Checking services status...")

            // Проверяем Car API
            val carApiAvailable = carManager.isCarApiAvailable()
            _carApiStatus.value = if (carApiAvailable) {
                addDiagnosticMessage("✓ Car API available")
                ServiceStatus.Running
            } else {
                addDiagnosticMessage("✗ Car API not available")
                ServiceStatus.Stopped
            }

            // Проверяем CarPropertyManager
            val managerReady = carManager.isReady()
            _carManagerStatus.value = if (managerReady) {
                addDiagnosticMessage("✓ CarPropertyManager ready")
                ServiceStatus.Running
            } else {
                addDiagnosticMessage("⚠ CarPropertyManager not ready, attempting initialization...")
                val initialized = carManager.initialize()
                if (initialized) {
                    addDiagnosticMessage("✓ CarPropertyManager initialized successfully")
                    ServiceStatus.Running
                } else {
                    addDiagnosticMessage("✗ CarPropertyManager initialization failed")
                    ServiceStatus.Error
                }
            }

            addDiagnosticMessage("Status check completed")
        }
    }

    /**
     * Мониторит обновления метрик для диагностики.
     */
    private fun monitorMetricsUpdates() {
        viewModelScope.launch {
            metricsRepo.vehicleMetrics.collect { metrics ->
                _metricsUpdateCount.value++
                _lastMetricsUpdate.value = metrics.timestamp
            }
        }
    }

    /**
     * Добавляет сообщение диагностики.
     */
    private fun addDiagnosticMessage(message: String) {
        val timestamp = System.currentTimeMillis()
        val formattedMessage = "$timestamp | $message"
        _diagnosticMessages.value = (_diagnosticMessages.value + formattedMessage).takeLast(50)
    }

    /**
     * Очищает сообщения диагностики.
     */
    fun clearDiagnosticMessages() {
        _diagnosticMessages.value = emptyList()
    }

    /**
     * Проверяет свежесть данных метрик.
     * @return true если данные свежие (обновлены за последние 5 секунд)
     */
    fun areMetricsFresh(): Boolean {
        val lastUpdate = _lastMetricsUpdate.value ?: return false
        val age = System.currentTimeMillis() - lastUpdate
        return age < 5000 // 5 seconds
    }

    /**
     * Получает общий статус системы.
     */
    fun getOverallStatus(): ServiceStatus {
        return when {
            _carApiStatus.value == ServiceStatus.Error ||
            _carManagerStatus.value == ServiceStatus.Error -> ServiceStatus.Error

            _carApiStatus.value == ServiceStatus.Running &&
            _carManagerStatus.value == ServiceStatus.Running -> ServiceStatus.Running

            else -> ServiceStatus.Unknown
        }
    }

    /**
     * Тест диагностики топлива.
     * Выводит текущие значения всех свойств связанных с топливом.
     */
    fun testFuelDiagnostics(): String {
        addDiagnosticMessage("Starting fuel diagnostics test...")

        val report = StringBuilder()
        report.appendLine("=== FUEL DIAGNOSTICS REPORT ===")
        report.appendLine()

        // Текущие метрики
        val metrics = metricsRepo.vehicleMetrics.value
        report.appendLine("Current Metrics:")
        report.appendLine("  Range Remaining: ${metrics.rangeRemaining ?: "N/A"} km")
        report.appendLine("  Average Fuel: ${metrics.averageFuel ?: "N/A"} L/100km")
        report.appendLine("  Fuel Data: ${metrics.fuel?.let {
            "Range=${it.rangeKm}km, Current=${it.currentFuelLiters}L, Capacity=${it.capacityLiters}L"
        } ?: "N/A"}")
        report.appendLine()

        addDiagnosticMessage("Fuel diagnostics test completed")
        return report.toString()
    }
}

/**
 * Статус сервиса.
 */
enum class ServiceStatus {
    Unknown,    // Статус неизвестен
    Running,    // Работает
    Stopped,    // Остановлен
    Error       // Ошибка
}
