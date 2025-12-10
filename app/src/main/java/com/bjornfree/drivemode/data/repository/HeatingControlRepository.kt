package com.bjornfree.drivemode.data.repository

import com.bjornfree.drivemode.data.preferences.PreferencesManager
import com.bjornfree.drivemode.domain.model.HeatingMode
import com.bjornfree.drivemode.domain.model.HeatingState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Repository для управления автоподогревом сидений.
 *
 * Принимает решения о включении/выключении подогрева на основе:
 * - Состояния зажигания (IgnitionStateRepository)
 * - Температуры (VehicleMetricsRepository)
 * - Настроек пользователя (PreferencesManager)
 */
class HeatingControlRepository(
    private val prefsManager: PreferencesManager,
    private val ignitionRepo: IgnitionStateRepository,
    private val metricsRepo: VehicleMetricsRepository
) {
    private val _heatingState = MutableStateFlow(HeatingState())
    val heatingState: StateFlow<HeatingState> = _heatingState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var controlJob: Job? = null

    @Volatile
    private var isRunning = false

    // Флаг, что решение о подогреве уже принято при этом включении зажигания
    private var heatingDecisionMade = false

    // Время активации подогрева для автоотключения
    private var heatingActivatedAt: Long = 0L

    // Флаг, что подогрев был отключён именно таймером
    private var turnedOffByTimer: Boolean = false

    private fun resetDecisionState() {
        heatingDecisionMade = false
        heatingActivatedAt = 0L
        turnedOffByTimer = false
    }

    /**
     * Запускает логику автоподогрева.
     */
    fun startAutoHeating() {
        if (isRunning) return
        isRunning = true

        controlJob = scope.launch {
            combine(
                ignitionRepo.ignitionState,
                metricsRepo.vehicleMetrics
            ) { ignition, metrics ->
                ignition to metrics
            }.collect { (ignition, metrics) ->

                val currentMode = HeatingMode.fromKey(prefsManager.seatAutoHeatMode)
                val isAdaptive = prefsManager.adaptiveHeating
                val threshold = prefsManager.temperatureThreshold
                val checkOnce = prefsManager.checkTempOnceOnStartup
                val autoOffTimerMinutes = prefsManager.autoOffTimerMinutes
                val temperatureSource = prefsManager.temperatureSource
                val levelConfigured = prefsManager.heatingLevel

                val tempToCheck = if (temperatureSource == "ambient") {
                    metrics.ambientTemperature
                } else {
                    metrics.cabinTemperature
                }
                val cabinTemp = metrics.cabinTemperature

                if (!ignition.isOn) {
                    heatingDecisionMade = false
                    heatingActivatedAt = 0L
                    turnedOffByTimer = false
                }

                val isTimerExpired = if (autoOffTimerMinutes > 0 && heatingActivatedAt > 0L) {
                    val elapsedMinutes = (System.currentTimeMillis() - heatingActivatedAt) / 60_000
                    elapsedMinutes >= autoOffTimerMinutes
                } else {
                    false
                }

                val previousActive = _heatingState.value.isActive

                val baseTempDecision: Boolean =
                    if (!checkOnce || !heatingDecisionMade) {
                        val decision = if (isAdaptive) {
                            if (tempToCheck == null) {
                                false
                            } else {
                                tempToCheck < 10f
                            }
                        } else {
                            if (tempToCheck == null) {
                                false
                            } else {
                                tempToCheck < threshold
                            }
                        }

                        if (checkOnce && ignition.isOn && tempToCheck != null && !heatingDecisionMade) {
                            heatingDecisionMade = true
                        }
                        decision
                    } else {
                        previousActive
                    }

                val rawShouldBeActive =
                    if (currentMode == HeatingMode.OFF) {
                        false
                    } else if (!ignition.isOn) {
                        false
                    } else {
                        baseTempDecision
                    }

                if (isTimerExpired && previousActive) {
                    turnedOffByTimer = true
                }

                val shouldBeActive = if (turnedOffByTimer) {
                    false
                } else {
                    rawShouldBeActive
                }

                val wasActive = previousActive
                if (shouldBeActive && !wasActive) {
                    heatingActivatedAt =
                        if (autoOffTimerMinutes > 0) System.currentTimeMillis() else 0L
                } else if (!shouldBeActive && wasActive) {
                    heatingActivatedAt = 0L
                }

                val tempSourceLabel = if (temperatureSource == "ambient") "наружная" else "салон"
                val reason = when {
                    !ignition.isOn ->
                        "Зажигание выключено"
                    currentMode == HeatingMode.OFF ->
                        "Режим: выключен"
                    turnedOffByTimer ->
                        "[Таймер] Автоотключение через ${autoOffTimerMinutes} мин"
                    isAdaptive && tempToCheck == null ->
                        "[Адаптив] Температура ($tempSourceLabel) недоступна → выключено"
                    isAdaptive && tempToCheck != null && tempToCheck < 10f ->
                        "[Адаптив] Температура ($tempSourceLabel) ${tempToCheck.toInt()}°C < 10°C → включено"
                    isAdaptive && tempToCheck != null ->
                        "[Адаптив] Температура ($tempSourceLabel) ${tempToCheck.toInt()}°C ≥ 10°C → выключено"
                    tempToCheck == null ->
                        "[Порог] Температура ($tempSourceLabel) недоступна → выключено"
                    tempToCheck < threshold ->
                        "[Порог] Температура ($tempSourceLabel) ${tempToCheck.toInt()}°C < ${threshold}°C → включено"
                    else ->
                        "[Порог] Температура ($tempSourceLabel) ${tempToCheck.toInt()}°C ≥ ${threshold}°C → выключено"
                }

                _heatingState.value = HeatingState(
                    isActive = shouldBeActive,
                    mode = currentMode,
                    adaptiveHeating = isAdaptive,
                    heatingLevel = levelConfigured,
                    reason = reason,
                    currentTemp = tempToCheck ?: cabinTemp,
                    temperatureThreshold = threshold,
                    turnedOffByTimer = turnedOffByTimer
                )
            }
        }
    }

    fun stopAutoHeating() {
        isRunning = false
        controlJob?.cancel()
        controlJob = null

        _heatingState.value = _heatingState.value.copy(
            isActive = false,
            reason = "Stopped",
            turnedOffByTimer = false
        )
    }

    fun setMode(mode: HeatingMode) {
        prefsManager.seatAutoHeatMode = mode.key
        resetDecisionState()
    }

    fun setTemperatureThreshold(threshold: Int) {
        prefsManager.temperatureThreshold = threshold
        resetDecisionState()
    }

    fun getCurrentMode(): HeatingMode =
        HeatingMode.fromKey(prefsManager.seatAutoHeatMode)

    fun getTemperatureThreshold(): Int =
        prefsManager.temperatureThreshold

    fun isAdaptiveEnabled(): Boolean =
        prefsManager.adaptiveHeating

    fun setAdaptiveHeating(enabled: Boolean) {
        prefsManager.adaptiveHeating = enabled
        resetDecisionState()
    }

    fun setHeatingLevel(level: Int) {
        prefsManager.heatingLevel = level
    }

    fun getHeatingLevel(): Int =
        prefsManager.heatingLevel

    fun setCheckTempOnceOnStartup(enabled: Boolean) {
        prefsManager.checkTempOnceOnStartup = enabled
        resetDecisionState()
    }

    fun isCheckTempOnceOnStartup(): Boolean =
        prefsManager.checkTempOnceOnStartup

    fun setAutoOffTimer(minutes: Int) {
        prefsManager.autoOffTimerMinutes = minutes
        heatingActivatedAt = 0L
        resetDecisionState()
    }

    fun getAutoOffTimer(): Int =
        prefsManager.autoOffTimerMinutes

    fun setTemperatureSource(source: String) {
        prefsManager.temperatureSource = source
        resetDecisionState()
    }

    fun getTemperatureSource(): String =
        prefsManager.temperatureSource

    fun release() {
        stopAutoHeating()
        scope.cancel()
    }
}