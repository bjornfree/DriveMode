package com.bjornfree.drivemode.data.repository

import com.bjornfree.drivemode.data.car.CarPropertyManagerSingleton
import com.bjornfree.drivemode.data.constants.VehiclePropertyConstants
import com.bjornfree.drivemode.data.preferences.PreferencesManager
import com.bjornfree.drivemode.domain.model.IgnitionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository для мониторинга состояния зажигания.
 *
 * КОНСОЛИДАЦИЯ:
 * - Заменяет дублированную логику мониторинга зажигания из сервисов
 * - Единый source of truth для состояния зажигания
 * - Автоматическое сохранение истории состояний
 *
 * @param carManager singleton для чтения свойств
 * @param prefsManager для сохранения истории
 */
class IgnitionStateRepository(
    private val carManager: CarPropertyManagerSingleton,
    private val prefsManager: PreferencesManager
) {
    companion object {
        private const val POLL_INTERVAL_MS = 2500L  // Опрос каждые 2.5 секунды
    }

    // Реактивный state
    private val _ignitionState = MutableStateFlow(IgnitionState.UNKNOWN)
    val ignitionState: StateFlow<IgnitionState> = _ignitionState.asStateFlow()

    // Coroutine scope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    @Volatile
    private var isMonitoring = false

    /**
     * Запускает мониторинг состояния зажигания.
     * Thread-safe start с синхронизацией для предотвращения дублирующих запусков.
     */
    @Synchronized
    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true

        // Загружаем последнее известное состояние из preferences
        val lastState = prefsManager.lastIgnitionState
        if (lastState != -1) {
            _ignitionState.value = IgnitionState.fromRaw(lastState)
        }

        monitorJob = scope.launch {
            while (isActive && isMonitoring) {
                try {
                    val rawState = readIgnitionState()

                    if (rawState != null) {
                        val newState = IgnitionState.fromRaw(rawState)
                        val previousState = _ignitionState.value

                        // Обновляем только при реальном изменении состояния
                        if (newState.rawState != previousState.rawState) {
                            // Сохраняем в preferences
                            prefsManager.saveIgnitionState(rawState, newState.isOff)
                            // Обновляем state
                            _ignitionState.value = newState
                        }
                    }
                } catch (_: Exception) {
                    // Ошибки чтения просто игнорируются до следующего опроса
                }

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /**
     * Останавливает мониторинг.
     */
    fun stopMonitoring() {
        isMonitoring = false
        monitorJob?.cancel()
        monitorJob = null
    }

    /**
     * Читает сырое состояние зажигания.
     * @return raw ignition state или null при ошибке
     */
    private fun readIgnitionState(): Int? {
        return carManager.readIntProperty(
            VehiclePropertyConstants.VEHICLE_PROPERTY_IGNITION_STATE
        )
    }

    /**
     * Получает текущее состояние синхронно.
     * @return текущее IgnitionState
     */
    fun getCurrentState(): IgnitionState = _ignitionState.value

    /**
     * Проверяет был ли "свежий старт".
     * @return true если зажигание было выключено достаточно долго
     */
    fun isFreshStart(): Boolean = prefsManager.isFreshStart()

    /**
     * Освобождает ресурсы.
     */
    fun release() {
        stopMonitoring()
        scope.cancel()
    }
}