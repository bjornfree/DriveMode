package com.bjornfree.drivemode.data.repository

import com.bjornfree.drivemode.data.preferences.PreferencesManager
import com.bjornfree.drivemode.domain.model.HeatingMode
import com.bjornfree.drivemode.domain.model.HeatingState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Repository для управления автоподогревом сидений.
 *
 * Извлекает чистую бизнес-логику подогрева из AutoSeatHeatService.
 * Принимает решения о включении/выключении подогрева на основе:
 * - Состояния зажигания (from IgnitionStateRepository)
 * - Температуры (from VehicleMetricsRepository)
 * - Настроек пользователя (from PreferencesManager)
 *
 * @param prefsManager для настроек подогрева
 * @param ignitionRepo для мониторинга зажигания
 * @param metricsRepo для температуры
 */
class HeatingControlRepository(
    private val prefsManager: PreferencesManager,
    private val ignitionRepo: IgnitionStateRepository,
    private val metricsRepo: VehicleMetricsRepository
) {
    // Реактивный state
    private val _heatingState = MutableStateFlow(HeatingState())
    val heatingState: StateFlow<HeatingState> = _heatingState.asStateFlow()

    // Coroutine scope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var controlJob: Job? = null

    @Volatile
    private var isRunning = false

    // Флаг, что решение о подогреве уже принято при этом включении зажигания
    private var heatingDecisionMade = false

    // Время активации подогрева для автоотключения
    private var heatingActivatedAt: Long = 0L

    // Флаг, что подогрев был отключён именно таймером (до следующего цикла зажигания/смены настроек)
    private var turnedOffByTimer: Boolean = false

    /**
     * Сбрасывает внутреннее решение и таймер автоподогрева.
     * Используется при смене настроек, чтобы логика пересчиталась как при новом запуске.
     */
    private fun resetDecisionState() {
        heatingDecisionMade = false
        heatingActivatedAt = 0L
        turnedOffByTimer = false
    }

    /**
     * Запускает логику автоподогрева.
     * Слушает изменения зажигания и температуры, принимает решения.
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

                // Выбираем источник температуры
                val tempToCheck = if (temperatureSource == "ambient") {
                    metrics.ambientTemperature
                } else {
                    metrics.cabinTemperature
                }
                val cabinTemp = metrics.cabinTemperature

                // При выключении зажигания всегда сбрасываем решение и состояние таймера
                if (!ignition.isOn) {
                    heatingDecisionMade = false
                    heatingActivatedAt = 0L
                    turnedOffByTimer = false
                }

                // Проверяем автоотключение по таймеру
                val isTimerExpired = if (autoOffTimerMinutes > 0 && heatingActivatedAt > 0L) {
                    val elapsedMinutes = (System.currentTimeMillis() - heatingActivatedAt) / 60_000
                    elapsedMinutes >= autoOffTimerMinutes
                } else {
                    false
                }

                val previousActive = _heatingState.value.isActive

                // Решение по температуре (адаптив/порог) с учётом режима "проверка только при запуске"
                val baseTempDecision: Boolean =
                    if (!checkOnce || !heatingDecisionMade) {
                        val decision = if (isAdaptive) {
                            // Адаптивный режим — порог температуры из настроек игнорируется
                            if (tempToCheck == null) {
                                false
                            } else {
                                tempToCheck < 10f
                            }
                        } else {
                            // Обычный режим — используем настраиваемый порог
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
                        // В режиме однократной проверки сохраняем прошлое решение
                        previousActive
                    }

                // "Сырое" решение без учёта таймера/латча
                val rawShouldBeActive =
                    if (currentMode == HeatingMode.OFF) {
                        false
                    } else if (!ignition.isOn) {
                        false
                    } else {
                        baseTempDecision
                    }

                // Если таймер истёк в момент, когда подогрев был активен, фиксируем, что он отключён именно таймером
                if (isTimerExpired && previousActive) {
                    turnedOffByTimer = true
                }

                // Финальное решение с учётом того, что таймер мог уже один раз отключить подогрев
                val shouldBeActive = if (turnedOffByTimer) {
                    false
                } else {
                    rawShouldBeActive
                }

                // Отслеживаем активацию подогрева для таймера
                val wasActive = previousActive
                if (shouldBeActive && !wasActive) {
                    if (autoOffTimerMinutes > 0) {
                        heatingActivatedAt = System.currentTimeMillis()
                    } else {
                        heatingActivatedAt = 0L
                    }
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
                    heatingLevel = prefsManager.heatingLevel,
                    reason = reason,
                    currentTemp = tempToCheck ?: cabinTemp,
                    temperatureThreshold = threshold,
                    turnedOffByTimer = turnedOffByTimer
                )
            }
        }
    }

    /**
     * Останавливает логику автоподогрева.
     */
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

    /**
     * Изменяет режим автоподогрева.
     */
    fun setMode(mode: HeatingMode) {
        prefsManager.seatAutoHeatMode = mode.key
        resetDecisionState()
    }

    /**
     * Изменяет температурный порог.
     */
    fun setTemperatureThreshold(threshold: Int) {
        prefsManager.temperatureThreshold = threshold
        resetDecisionState()
    }

    /**
     * Текущий режим подогрева.
     */
    fun getCurrentMode(): HeatingMode =
        HeatingMode.fromKey(prefsManager.seatAutoHeatMode)

    /**
     * Текущий температурный порог.
     */
    fun getTemperatureThreshold(): Int =
        prefsManager.temperatureThreshold

    /**
     * Включен ли адаптивный режим.
     */
    fun isAdaptiveEnabled(): Boolean =
        prefsManager.adaptiveHeating

    /**
     * Включает/выключает адаптивный режим.
     */
    fun setAdaptiveHeating(enabled: Boolean) {
        prefsManager.adaptiveHeating = enabled
        resetDecisionState()
    }

    /**
     * Уровень подогрева (0–3).
     */
    fun setHeatingLevel(level: Int) {
        prefsManager.heatingLevel = level
    }

    fun getHeatingLevel(): Int =
        prefsManager.heatingLevel

    /**
     * Режим "проверка температуры только при запуске".
     */
    fun setCheckTempOnceOnStartup(enabled: Boolean) {
        prefsManager.checkTempOnceOnStartup = enabled
        resetDecisionState()
    }

    fun isCheckTempOnceOnStartup(): Boolean =
        prefsManager.checkTempOnceOnStartup

    /**
     * Таймер автоотключения подогрева.
     */
    fun setAutoOffTimer(minutes: Int) {
        prefsManager.autoOffTimerMinutes = minutes
        heatingActivatedAt = 0L
        resetDecisionState()
    }

    fun getAutoOffTimer(): Int =
        prefsManager.autoOffTimerMinutes

    /**
     * Источник температуры для условия включения подогрева ("cabin" или "ambient").
     */
    fun setTemperatureSource(source: String) {
        prefsManager.temperatureSource = source
        resetDecisionState()
    }

    fun getTemperatureSource(): String =
        prefsManager.temperatureSource

    /**
     * Освобождает ресурсы.
     */
    fun release() {
        stopAutoHeating()
        scope.cancel()
    }
}