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
                val threshold = prefsManager.temperatureThreshold
                val cabinTemp = metrics.cabinTemperature

                // Определяем должен ли быть активен подогрев
                val shouldBeActive = when (currentMode) {
                    HeatingMode.OFF -> {
                        // Режим выключен - ничего не делаем
                        false
                    }

                    HeatingMode.ALWAYS -> {
                        // Всегда включен при работающем зажигании
                        ignition.isOn
                    }

                    HeatingMode.ADAPTIVE -> {
                        // Включается если зажигание ON и температура ниже порога
                        if (ignition.isOn && cabinTemp != null) {
                            cabinTemp < threshold
                        } else {
                            false
                        }
                    }
                }

                // Обновляем state
                val reason = when {
                    !ignition.isOn -> "Ignition OFF"
                    currentMode == HeatingMode.OFF -> "Mode: OFF"
                    currentMode == HeatingMode.ALWAYS -> "Mode: ALWAYS"
                    cabinTemp == null -> "Temperature unavailable"
                    cabinTemp < threshold -> "Temperature ${cabinTemp}°C < ${threshold}°C"
                    else -> "Temperature ${cabinTemp}°C >= ${threshold}°C"
                }

                _heatingState.value = HeatingState(
                    isActive = shouldBeActive,
                    mode = currentMode,
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
     * Освобождает ресурсы.
     */
    fun release() {
        stopAutoHeating()
        scope.cancel()
        Log.i(TAG, "HeatingControlRepository released")
    }
}
