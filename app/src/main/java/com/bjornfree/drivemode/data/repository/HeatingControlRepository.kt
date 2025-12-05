package com.bjornfree.drivemode.data.repository

import android.util.Log
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
 * - Температуры в салоне (from VehicleMetricsRepository)
 * - Настроек пользователя (from PreferencesManager)
 *
 * @param prefsManager для настроек подогрева
 * @param ignitionRepo для мониторинга зажигания
 * @param metricsRepo для температуры салона
 */
class HeatingControlRepository(
    private val prefsManager: PreferencesManager,
    private val ignitionRepo: IgnitionStateRepository,
    private val metricsRepo: VehicleMetricsRepository
) {
    companion object {
        private const val TAG = "HeatingControlRepo"
    }

    // Реактивный state
    private val _heatingState = MutableStateFlow(HeatingState())
    val heatingState: StateFlow<HeatingState> = _heatingState.asStateFlow()

    // Coroutine scope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var controlJob: Job? = null

    @Volatile
    private var isRunning = false

    // Отслеживаем предыдущее состояние зажигания для детекта включения
    private var lastIgnitionOn = false

    // Флаг что решение о подогреве уже принято при этом включении зажигания
    private var heatingDecisionMade = false

    /**
     * Запускает логику автоподогрева.
     * Слушает изменения зажигания и температуры, принимает решения.
     */
    fun startAutoHeating() {
        if (isRunning) {
            Log.d(TAG, "Auto heating already running")
            return
        }

        Log.i(TAG, "Starting auto heating control...")
        isRunning = true

        // КРИТИЧЕСКИ ВАЖНО: Запускаем мониторинг зажигания и метрик!
        ignitionRepo.startMonitoring()
        metricsRepo.startMonitoring()

        // Загружаем текущий режим из настроек
        val mode = HeatingMode.fromKey(prefsManager.seatAutoHeatMode)
        val tempThreshold = prefsManager.temperatureThreshold

        controlJob = scope.launch {
            // Комбинируем потоки зажигания и метрик
            combine(
                ignitionRepo.ignitionState,
                metricsRepo.vehicleMetrics
            ) { ignition, metrics ->
                Pair(ignition, metrics)
            }.collect { (ignition, metrics) ->

                val currentMode = HeatingMode.fromKey(prefsManager.seatAutoHeatMode)
                val isAdaptive = prefsManager.adaptiveHeating
                val threshold = prefsManager.temperatureThreshold
                val cabinTemp = metrics.cabinTemperature
                val checkOnce = prefsManager.checkTempOnceOnStartup

                // Детектируем включение зажигания (переход с OFF на ON)
                val ignitionJustTurnedOn = ignition.isOn && !lastIgnitionOn
                lastIgnitionOn = ignition.isOn

                // Сбрасываем флаг решения при выключении зажигания
                if (!ignition.isOn) {
                    heatingDecisionMade = false
                }

                // Если режим "проверка только при запуске" и решение уже принято - не меняем состояние
                if (checkOnce && heatingDecisionMade && ignition.isOn) {
                    // Пропускаем обновление, оставляем текущее состояние
                    return@collect
                }

                // Помечаем что решение принято (если зажигание включено)
                if (ignition.isOn) {
                    heatingDecisionMade = true
                }

                // Определяем должен ли быть активен подогрев
                // Логика как в старой версии (AutoHeaterService.kt.backup lines 1225-1289)
                val shouldBeActive = if (currentMode == HeatingMode.OFF) {
                    // Режим выключен - ничего не делаем
                    false
                } else if (!ignition.isOn) {
                    // Зажигание выключено - подогрев не нужен
                    false
                } else {
                    // Режим driver/passenger/both + зажигание ON
                    if (isAdaptive) {
                        // ПРИОРИТЕТ 1: Адаптивный режим - порог температуры игнорируется!
                        // Включаем если температура салона < 10°C
                        if (cabinTemp == null) {
                            false // Температура недоступна - на всякий случай включаем
                        } else {
                            cabinTemp < 10f // Адаптивный порог жестко закодирован
                        }
                    } else {
                        // ПРИОРИТЕТ 2: Обычный режим - проверяем температурный порог
                        if (cabinTemp == null) {
                            true // Температура недоступна - включаем
                        } else {
                            cabinTemp < threshold // Используем настраиваемый порог
                        }
                    }
                }

                // Обновляем state
                val reason = when {
                    !ignition.isOn -> "Зажигание выключено"
                    currentMode == HeatingMode.OFF -> "Режим: выключен"
                    isAdaptive && cabinTemp == null -> "[Адаптив] Температура недоступна → включено"
                    isAdaptive && cabinTemp!! < 10f -> "[Адаптив] Температура ${cabinTemp.toInt()}°C < 10°C → включено"
                    isAdaptive -> "[Адаптив] Температура ${cabinTemp?.toInt()}°C ≥ 10°C → выключено"
                    cabinTemp == null -> "[Порог] Температура недоступна → включено"
                    cabinTemp < threshold -> "[Порог] Температура ${cabinTemp.toInt()}°C < ${threshold}°C → включено"
                    else -> "[Порог] Температура ${cabinTemp.toInt()}°C ≥ ${threshold}°C → выключено"
                }

                _heatingState.value = HeatingState(
                    isActive = shouldBeActive,
                    mode = currentMode,
                    adaptiveHeating = isAdaptive,
                    heatingLevel = prefsManager.heatingLevel,
                    reason = reason,
                    currentTemp = cabinTemp,
                    temperatureThreshold = threshold
                )

                if (shouldBeActive) {
                    Log.d(TAG, "Heating should be ACTIVE: $reason")
                } else {
                    Log.d(TAG, "Heating should be INACTIVE: $reason")
                }
            }
        }

        Log.i(TAG, "Auto heating control started")
    }

    /**
     * Останавливает логику автоподогрева.
     */
    fun stopAutoHeating() {
        Log.i(TAG, "Stopping auto heating control...")
        isRunning = false
        controlJob?.cancel()
        controlJob = null

        // Деактивируем подогрев
        _heatingState.value = HeatingState(isActive = false, reason = "Stopped")
    }

    /**
     * Изменяет режим автоподогрева.
     * @param mode новый режим (off/adaptive/always)
     */
    fun setMode(mode: HeatingMode) {
        Log.i(TAG, "Setting heating mode to: ${mode.key}")
        prefsManager.seatAutoHeatMode = mode.key
    }

    /**
     * Изменяет температурный порог.
     * @param threshold новый порог в °C
     */
    fun setTemperatureThreshold(threshold: Int) {
        Log.i(TAG, "Setting temperature threshold to: $threshold°C")
        prefsManager.temperatureThreshold = threshold
    }

    /**
     * Получает текущий режим подогрева.
     */
    fun getCurrentMode(): HeatingMode {
        return HeatingMode.fromKey(prefsManager.seatAutoHeatMode)
    }

    /**
     * Получает текущий температурный порог.
     */
    fun getTemperatureThreshold(): Int {
        return prefsManager.temperatureThreshold
    }

    /**
     * Проверяет включен ли адаптивный режим.
     */
    fun isAdaptiveEnabled(): Boolean {
        return prefsManager.adaptiveHeating
    }

    /**
     * Включает/выключает адаптивный режим.
     * @param enabled true для адаптивного режима
     */
    fun setAdaptiveHeating(enabled: Boolean) {
        Log.i(TAG, "Setting adaptive heating to: $enabled")
        prefsManager.adaptiveHeating = enabled
    }

    /**
     * Устанавливает уровень подогрева (0-3).
     * @param level уровень подогрева (0=off, 1=low, 2=medium, 3=high)
     */
    fun setHeatingLevel(level: Int) {
        Log.i(TAG, "Setting heating level to: $level")
        prefsManager.heatingLevel = level
    }

    /**
     * Получает текущий уровень подогрева.
     */
    fun getHeatingLevel(): Int {
        return prefsManager.heatingLevel
    }

    /**
     * Включает/выключает режим "проверка температуры только при запуске".
     * @param enabled true для проверки только при запуске
     */
    fun setCheckTempOnceOnStartup(enabled: Boolean) {
        Log.i(TAG, "Setting checkTempOnceOnStartup to: $enabled")
        prefsManager.checkTempOnceOnStartup = enabled
    }

    /**
     * Проверяет включен ли режим "проверка только при запуске".
     */
    fun isCheckTempOnceOnStartup(): Boolean {
        return prefsManager.checkTempOnceOnStartup
    }

    /**
     * Освобождает ресурсы.
     */
    fun release() {
        stopAutoHeating()
        scope.cancel()
        Log.i(TAG, "HeatingControlRepository released")
    }
}
