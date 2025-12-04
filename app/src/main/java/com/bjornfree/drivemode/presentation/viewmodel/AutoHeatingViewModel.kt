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
     * Получает доступные режимы подогрева для UI.
     */
    fun getAvailableModes(): List<HeatingMode> {
        return listOf(
            HeatingMode.OFF,
            HeatingMode.ADAPTIVE,
            HeatingMode.ALWAYS
        )
    }

    /**
     * Получает человекочитаемое название режима.
     */
    fun getModeName(mode: HeatingMode): String {
        return when (mode) {
            HeatingMode.OFF -> "Выключен"
            HeatingMode.ADAPTIVE -> "Адаптивный"
            HeatingMode.ALWAYS -> "Всегда включен"
        }
    }

    /**
     * Получает описание режима.
     */
    fun getModeDescription(mode: HeatingMode): String {
        return when (mode) {
            HeatingMode.OFF -> "Подогрев сидений выключен"
            HeatingMode.ADAPTIVE -> "Включается при температуре ниже порога"
            HeatingMode.ALWAYS -> "Включается всегда при запуске двигателя"
        }
    }

    /**
     * Вызывается когда ViewModel больше не нужна.
     */
    override fun onCleared() {
        super.onCleared()
        heatingRepo.stopAutoHeating()
    }
}
