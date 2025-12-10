package com.bjornfree.drivemode.data.repository

import com.bjornfree.drivemode.data.car.CarPropertyManagerSingleton
import com.bjornfree.drivemode.data.constants.VehiclePropertyConstants
import com.bjornfree.drivemode.data.preferences.PreferencesManager
import com.bjornfree.drivemode.domain.model.IgnitionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository для мониторинга состояния зажигания.
 *
 * FULL REWORK:
 *  - удалён опрос (while + delay)
 *  - используется callback от CarPropertyManagerSingleton
 *  - нет лишних потоков, нет polling, нет задержек
 *  - state обновляется мгновенно и без нагрузки на CPU
 */
class IgnitionStateRepository(
    private val carManager: CarPropertyManagerSingleton,
    private val prefsManager: PreferencesManager
) {

    private val _ignitionState = MutableStateFlow(IgnitionState.UNKNOWN)
    val ignitionState: StateFlow<IgnitionState> = _ignitionState.asStateFlow()

    private var callbackToken: Any? = null
    private var isMonitoring = false

    @Synchronized
    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true

        // восстановить последнее известное состояние
        val lastState = prefsManager.lastIgnitionState
        if (lastState != -1) {
            _ignitionState.value = IgnitionState.fromRaw(lastState)
        }

        // начальное чтение реального состояния с машины
        carManager.readIntProperty(
            VehiclePropertyConstants.VEHICLE_PROPERTY_IGNITION_STATE
        )?.let { rawInt ->
            val newState = IgnitionState.fromRaw(rawInt)
            prefsManager.saveIgnitionState(rawInt, newState.isOff)
            _ignitionState.value = newState
        }

        // регистрация колбэка
        callbackToken = carManager.registerPropertyCallback(
            propertyId = VehiclePropertyConstants.VEHICLE_PROPERTY_IGNITION_STATE,
            rate = 4f, // обновления по мере прихода событий
            callback = { _, raw ->
                val rawInt = (raw as? Int) ?: return@registerPropertyCallback
                val newState = IgnitionState.fromRaw(rawInt)
                val prevState = _ignitionState.value

                if (newState.rawState != prevState.rawState) {
                    prefsManager.saveIgnitionState(rawInt, newState.isOff)
                    _ignitionState.value = newState
                }
            }
        )
    }

    fun stopMonitoring() {
        if (!isMonitoring) return
        isMonitoring = false

        callbackToken?.let { token ->
            carManager.unregisterPropertyCallback(token)
        }
        callbackToken = null
    }

    fun getCurrentState(): IgnitionState = _ignitionState.value

    fun isFreshStart(): Boolean = prefsManager.isFreshStart()

    fun release() {
        stopMonitoring()
    }
}