package com.bjornfree.drivemode.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bjornfree.drivemode.data.repository.HeatingControlRepository
import com.bjornfree.drivemode.data.repository.VehicleMetricsRepository
import com.bjornfree.drivemode.domain.model.HeatingMode
import com.bjornfree.drivemode.domain.model.HeatingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class AutoHeatingViewModel(
    private val heatingRepo: HeatingControlRepository,
    private val metricsRepo: VehicleMetricsRepository
) : ViewModel() {

    // Состояние подогрева напрямую из репозитория (без лишнего stateIn)
    val heatingState: StateFlow<HeatingState> = heatingRepo.heatingState

    private val _currentMode = MutableStateFlow(heatingRepo.getCurrentMode())
    val currentMode: StateFlow<HeatingMode> = _currentMode.asStateFlow()

    private val _temperatureThreshold = MutableStateFlow(heatingRepo.getTemperatureThreshold())
    val temperatureThreshold: StateFlow<Int> = _temperatureThreshold.asStateFlow()

    private val _adaptiveHeating = MutableStateFlow(heatingRepo.isAdaptiveEnabled())
    val adaptiveHeating: StateFlow<Boolean> = _adaptiveHeating.asStateFlow()

    private val _heatingLevel = MutableStateFlow(heatingRepo.getHeatingLevel())
    val heatingLevel: StateFlow<Int> = _heatingLevel.asStateFlow()

    private val _checkTempOnceOnStartup = MutableStateFlow(heatingRepo.isCheckTempOnceOnStartup())
    val checkTempOnceOnStartup: StateFlow<Boolean> = _checkTempOnceOnStartup.asStateFlow()

    private val _autoOffTimer = MutableStateFlow(heatingRepo.getAutoOffTimer())
    val autoOffTimer: StateFlow<Int> = _autoOffTimer.asStateFlow()

    private val _temperatureSource = MutableStateFlow(heatingRepo.getTemperatureSource())
    val temperatureSource: StateFlow<String> = _temperatureSource.asStateFlow()

    val cabinTemperature: StateFlow<Float?> = metricsRepo.vehicleMetrics
        .map { it.cabinTemperature }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val ambientTemperature: StateFlow<Float?> = metricsRepo.vehicleMetrics
        .map { it.ambientTemperature }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun setHeatingMode(mode: HeatingMode) {
        // методы репозитория синхронные — корутину не запускаем
        heatingRepo.setMode(mode)
        _currentMode.value = mode
    }

    fun setTemperatureThreshold(threshold: Int) {
        heatingRepo.setTemperatureThreshold(threshold)
        _temperatureThreshold.value = threshold
    }

    fun setAdaptiveHeating(enabled: Boolean) {
        heatingRepo.setAdaptiveHeating(enabled)
        _adaptiveHeating.value = enabled
    }

    fun setHeatingLevel(level: Int) {
        heatingRepo.setHeatingLevel(level)
        _heatingLevel.value = level
    }

    fun setCheckTempOnceOnStartup(enabled: Boolean) {
        heatingRepo.setCheckTempOnceOnStartup(enabled)
        _checkTempOnceOnStartup.value = enabled
    }

    fun setAutoOffTimer(minutes: Int) {
        heatingRepo.setAutoOffTimer(minutes)
        _autoOffTimer.value = minutes
    }

    fun setTemperatureSource(source: String) {
        heatingRepo.setTemperatureSource(source)
        _temperatureSource.value = source
    }

    fun getAvailableModes(): List<HeatingMode> =
        listOf(
            HeatingMode.OFF,
            HeatingMode.DRIVER,
            HeatingMode.PASSENGER,
            HeatingMode.BOTH
        )
}