package com.bjornfree.drivemode.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bjornfree.drivemode.data.repository.HeatingControlRepository
import com.bjornfree.drivemode.domain.model.HeatingMode
import com.bjornfree.drivemode.domain.model.HeatingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel для AutoHeatingTab (Автоподогрев).
 *
 * Управляет настройками автоподогрева сидений и отображает текущее состояние.
 *
 * Функции:
 * - Просмотр текущего состояния подогрева (активен/неактивен)
 * - Изменение режима (off/adaptive/always)
 * - Настройка температурного порога
 * - Отображение причины активации/деактивации
 *
 * @param heatingRepo repository для управления подогревом
 */
class AutoHeatingViewModel(
    private val heatingRepo: HeatingControlRepository
) : ViewModel() {

    /**
     * Реактивное состояние подогрева.
     * Содержит информацию о том включен ли подогрев, почему, и текущие настройки.
     */
    val heatingState: StateFlow<HeatingState> = heatingRepo.heatingState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HeatingState()
        )

    /**
     * Текущий режим подогрева.
     */
    private val _currentMode = MutableStateFlow(heatingRepo.getCurrentMode())
    val currentMode: StateFlow<HeatingMode> = _currentMode.asStateFlow()

    /**
     * Текущий температурный порог (°C).
     */
    private val _temperatureThreshold = MutableStateFlow(heatingRepo.getTemperatureThreshold())
    val temperatureThreshold: StateFlow<Int> = _temperatureThreshold.asStateFlow()

    /**
     * Адаптивный режим включен.
     */
    private val _adaptiveHeating = MutableStateFlow(heatingRepo.isAdaptiveEnabled())
    val adaptiveHeating: StateFlow<Boolean> = _adaptiveHeating.asStateFlow()

    /**
     * Уровень подогрева (0-3).
     */
    private val _heatingLevel = MutableStateFlow(heatingRepo.getHeatingLevel())
    val heatingLevel: StateFlow<Int> = _heatingLevel.asStateFlow()

    /**
     * Проверять температуру только при запуске двигателя.
     */
    private val _checkTempOnceOnStartup = MutableStateFlow(heatingRepo.isCheckTempOnceOnStartup())
    val checkTempOnceOnStartup: StateFlow<Boolean> = _checkTempOnceOnStartup.asStateFlow()

    init {
        // Запускаем автоподогрев при создании ViewModel
        startAutoHeating()
    }

    /**
     * Запускает логику автоподогрева.
     */
    private fun startAutoHeating() {
        viewModelScope.launch {
            heatingRepo.startAutoHeating()
        }
    }

    /**
     * Изменяет режим автоподогрева.
     *
     * @param mode новый режим (OFF, ADAPTIVE, ALWAYS)
     */
    fun setHeatingMode(mode: HeatingMode) {
        viewModelScope.launch {
            heatingRepo.setMode(mode)
            _currentMode.value = mode
        }
    }

    /**
     * Изменяет температурный порог для автоподогрева.
     *
     * @param threshold новый порог в градусах Цельсия
     */
    fun setTemperatureThreshold(threshold: Int) {
        viewModelScope.launch {
            heatingRepo.setTemperatureThreshold(threshold)
            _temperatureThreshold.value = threshold
        }
    }

    /**
     * Включает/выключает адаптивный режим.
     *
     * @param enabled true для адаптивного режима
     */
    fun setAdaptiveHeating(enabled: Boolean) {
        viewModelScope.launch {
            heatingRepo.setAdaptiveHeating(enabled)
            _adaptiveHeating.value = enabled
        }
    }

    /**
     * Устанавливает уровень подогрева.
     *
     * @param level уровень подогрева (0-3)
     */
    fun setHeatingLevel(level: Int) {
        viewModelScope.launch {
            heatingRepo.setHeatingLevel(level)
            _heatingLevel.value = level
        }
    }

    /**
     * Включает/выключает режим "проверка температуры только при запуске".
     *
     * @param enabled true для проверки только при запуске
     */
    fun setCheckTempOnceOnStartup(enabled: Boolean) {
        viewModelScope.launch {
            heatingRepo.setCheckTempOnceOnStartup(enabled)
            _checkTempOnceOnStartup.value = enabled
        }
    }

    /**
     * Получает доступные режимы подогрева для UI.
     */
    fun getAvailableModes(): List<HeatingMode> {
        return listOf(
            HeatingMode.OFF,
            HeatingMode.DRIVER,
            HeatingMode.PASSENGER,
            HeatingMode.BOTH
        )
    }

    /**
     * Вызывается когда ViewModel больше не нужна.
     */
    override fun onCleared() {
        super.onCleared()
        heatingRepo.stopAutoHeating()
    }
}
