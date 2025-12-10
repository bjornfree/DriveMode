package com.bjornfree.drivemode.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bjornfree.drivemode.data.car.CarPropertyManagerSingleton
import com.bjornfree.drivemode.data.repository.VehicleMetricsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DiagnosticsViewModel(
    private val carManager: CarPropertyManagerSingleton,
    private val metricsRepo: VehicleMetricsRepository
) : ViewModel() {

    private val _carApiStatus = MutableStateFlow<ServiceStatus>(ServiceStatus.Unknown)
    val carApiStatus: StateFlow<ServiceStatus> = _carApiStatus.asStateFlow()

    private val _carManagerStatus = MutableStateFlow<ServiceStatus>(ServiceStatus.Unknown)
    val carManagerStatus: StateFlow<ServiceStatus> = _carManagerStatus.asStateFlow()

    private val _metricsUpdateCount = MutableStateFlow(0)
    val metricsUpdateCount: StateFlow<Int> = _metricsUpdateCount.asStateFlow()

    private val _lastMetricsUpdate = MutableStateFlow<Long?>(null)
    val lastMetricsUpdate: StateFlow<Long?> = _lastMetricsUpdate.asStateFlow()

    private val _diagnosticMessages = MutableStateFlow<List<String>>(emptyList())
    val diagnosticMessages: StateFlow<List<String>> = _diagnosticMessages.asStateFlow()

    private var statusCheckJob: Job? = null

    init {
        checkServicesStatus()
    }

    fun checkServicesStatus() {
        statusCheckJob?.cancel()
        statusCheckJob = viewModelScope.launch(Dispatchers.IO) {
            addDiagnosticMessage("Checking services status...")

            val carApiAvailable = carManager.isCarApiAvailable()
            _carApiStatus.value = if (carApiAvailable) {
                addDiagnosticMessage("✓ Car API available")
                ServiceStatus.Running
            } else {
                addDiagnosticMessage("✗ Car API not available")
                ServiceStatus.Stopped
            }

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

    private fun addDiagnosticMessage(message: String) {
        val timestamp = System.currentTimeMillis()
        val formattedMessage = "$timestamp | $message"
        _diagnosticMessages.update { (it + formattedMessage).takeLast(50) }
    }

    fun clearDiagnosticMessages() {
        _diagnosticMessages.value = emptyList()
    }

    fun areMetricsFresh(): Boolean {
        val lastUpdate = _lastMetricsUpdate.value ?: return false
        val age = System.currentTimeMillis() - lastUpdate
        return age < 5000
    }

    fun getOverallStatus(): ServiceStatus {
        return when {
            _carApiStatus.value == ServiceStatus.Error ||
                    _carManagerStatus.value == ServiceStatus.Error -> ServiceStatus.Error

            _carApiStatus.value == ServiceStatus.Running &&
                    _carManagerStatus.value == ServiceStatus.Running -> ServiceStatus.Running

            else -> ServiceStatus.Unknown
        }
    }

    fun testFuelDiagnostics(): String {
        addDiagnosticMessage("Starting fuel diagnostics test...")

        val report = StringBuilder()
        report.appendLine("=== FUEL DIAGNOSTICS REPORT ===")
        report.appendLine()

        val metrics = metricsRepo.vehicleMetrics.value
        report.appendLine("Current Metrics:")
        report.appendLine("  Range Remaining: ${metrics.rangeRemaining ?: "N/A"} km")
        report.appendLine("  Average Fuel: ${metrics.averageFuel ?: "N/A"} L/100km")
        report.appendLine(
            "  Fuel Data: " + (metrics.fuel?.let {
                "Range=${it.rangeKm}km, Current=${it.currentFuelLiters}L, Capacity=${it.capacityLiters}L"
            } ?: "N/A")
        )
        report.appendLine()

        addDiagnosticMessage("Fuel diagnostics test completed")
        return report.toString()
    }
}

enum class ServiceStatus {
    Unknown,
    Running,
    Stopped,
    Error
}