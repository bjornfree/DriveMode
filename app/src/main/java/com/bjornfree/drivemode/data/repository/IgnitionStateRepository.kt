package com.bjornfree.drivemode.data.repository

import android.util.Log
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
 * - Заменяет дублированную логику мониторинга зажигания из:
 *   - DriveModeService.startIgnitionMonitoring() (lines 530-605)
 *   - AutoHeaterService.startIgnitionMonitoring() (lines 332-404)
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
        private const val TAG = "IgnitionStateRepo"
        private const val POLL_INTERVAL_MS = 2500L  // Опрос каждые 2.5 секунды
        private const val MAX_CONSECUTIVE_ERRORS = 5
    }

    // Реактивный state
    private val _ignitionState = MutableStateFlow(IgnitionState.UNKNOWN)
    val ignitionState: StateFlow<IgnitionState> = _ignitionState.asStateFlow()

    // Coroutine scope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    @Volatile
    private var isMonitoring = false

    private var consecutiveErrors = 0

    /**
     * Запускает мониторинг состояния зажигания.
     */
    fun startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "Monitoring already running")
            return
        }

        Log.i(TAG, "Starting ignition monitoring...")
        isMonitoring = true

        // Загружаем последнее известное состояние из preferences
        val lastState = prefsManager.lastIgnitionState
        if (lastState != -1) {
            _ignitionState.value = IgnitionState.fromRaw(lastState)
            Log.d(TAG, "Restored last ignition state: $lastState")
        }

        monitorJob = scope.launch {
            while (isActive && isMonitoring) {
                try {
                    val rawState = readIgnitionState()

                    if (rawState != null) {
                        // Успешно прочитали
                        consecutiveErrors = 0

                        val newState = IgnitionState.fromRaw(rawState)
                        val previousState = _ignitionState.value

                        // Проверяем изменение состояния
                        if (newState.rawState != previousState.rawState) {
                            Log.i(TAG, "Ignition state changed: ${previousState.stateName} -> ${newState.stateName}")

                            // Сохраняем в preferences
                            prefsManager.saveIgnitionState(rawState, newState.isOff)

                            // Обновляем state
                            _ignitionState.value = newState
                        }
                    } else {
                        // Ошибка чтения
                        consecutiveErrors++
                        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                            Log.w(TAG, "Failed to read ignition state $consecutiveErrors times in a row")
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error in ignition monitoring", e)
                    consecutiveErrors++
                }

                delay(POLL_INTERVAL_MS)
            }
        }

        Log.i(TAG, "Ignition monitoring started")
    }

    /**
     * Останавливает мониторинг.
     */
    fun stopMonitoring() {
        Log.i(TAG, "Stopping ignition monitoring...")
        isMonitoring = false
        monitorJob?.cancel()
        monitorJob = null
    }

    /**
     * Читает сырое состояние зажигания.
     * @return raw ignition state или null при ошибке
     */
    private fun readIgnitionState(): Int? {
        return carManager.readIntProperty(VehiclePropertyConstants.VEHICLE_PROPERTY_IGNITION_STATE)
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
        Log.i(TAG, "IgnitionStateRepository released")
    }
}
