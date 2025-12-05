package com.bjornfree.drivemode.data.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * Централизованный менеджер для работы с SharedPreferences.
 *
 * Заменяет множественные вызовы getSharedPreferences() по всему проекту
 * на единый type-safe интерфейс с properties.
 *
 * Улучшения:
 * - Type safety (не нужно помнить строковые ключи)
 * - Единое место для всех настроек
 * - Автоматический apply()
 * - Кэширование SharedPreferences instance
 *
 * @param context Application context
 */
class PreferencesManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "drivemode_prefs"

        // Ключи preferences
        private const val KEY_SEAT_AUTO_HEAT_MODE = "seat_auto_heat_mode"
        private const val KEY_ADAPTIVE_HEATING = "adaptive_heating"
        private const val KEY_TEMP_THRESHOLD = "temp_threshold"
        private const val KEY_HEATING_LEVEL = "heating_level"
        private const val KEY_CHECK_TEMP_ONCE_ON_STARTUP = "check_temp_once_on_startup"
        private const val KEY_LAUNCH_COUNT = "launch_count"
        private const val KEY_LAST_IGNITION_STATE = "last_ignition_state"
        private const val KEY_LAST_IGNITION_TIMESTAMP = "last_ignition_timestamp"
        private const val KEY_LAST_IGNITION_OFF_TIMESTAMP = "last_ignition_off_timestamp"
        private const val KEY_BORDER_ENABLED = "border_enabled"
        private const val KEY_PANEL_ENABLED = "panel_enabled"
        private const val KEY_DEMO_MODE = "demo_mode"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ========================================
    // Seat Heating Settings
    // ========================================

    /**
     * Режим автоподогрева сидений.
     * Возможные значения: "off", "driver", "passenger", "both"
     */
    var seatAutoHeatMode: String
        get() = prefs.getString(KEY_SEAT_AUTO_HEAT_MODE, "off") ?: "off"
        set(value) {
            prefs.edit().putString(KEY_SEAT_AUTO_HEAT_MODE, value).apply()
        }

    /**
     * Адаптивный режим подогрева.
     * Если true - уровень подогрева зависит от температуры в салоне.
     * Если false - фиксированный уровень (2).
     */
    var adaptiveHeating: Boolean
        get() = prefs.getBoolean(KEY_ADAPTIVE_HEATING, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ADAPTIVE_HEATING, value).apply()
        }

    /**
     * Температурный порог для автоподогрева (°C).
     * По умолчанию 15°C - если температура ниже, включается подогрев.
     */
    var temperatureThreshold: Int
        get() = prefs.getInt(KEY_TEMP_THRESHOLD, 15)
        set(value) {
            prefs.edit().putInt(KEY_TEMP_THRESHOLD, value).apply()
        }

    /**
     * Уровень подогрева (0-3).
     * 0 = off, 1 = low, 2 = medium, 3 = high
     * По умолчанию 2 (medium).
     */
    var heatingLevel: Int
        get() = prefs.getInt(KEY_HEATING_LEVEL, 2)
        set(value) {
            prefs.edit().putInt(KEY_HEATING_LEVEL, value.coerceIn(0, 3)).apply()
        }

    /**
     * Проверять температуру только один раз при запуске двигателя.
     * Если true - подогрев включается/выключается только при включении зажигания.
     * Если false - постоянный мониторинг температуры (может включаться/выключаться во время поездки).
     */
    var checkTempOnceOnStartup: Boolean
        get() = prefs.getBoolean(KEY_CHECK_TEMP_ONCE_ON_STARTUP, false)
        set(value) {
            prefs.edit().putBoolean(KEY_CHECK_TEMP_ONCE_ON_STARTUP, value).apply()
        }

    // ========================================
    // Application State
    // ========================================

    /**
     * Количество запусков приложения.
     * Используется для onboarding flow.
     */
    var launchCount: Int
        get() = prefs.getInt(KEY_LAUNCH_COUNT, 0)
        set(value) {
            prefs.edit().putInt(KEY_LAUNCH_COUNT, value).apply()
        }

    /**
     * Инкрементирует счетчик запусков.
     * @return новое значение счетчика
     */
    fun incrementLaunchCount(): Int {
        val newCount = launchCount + 1
        launchCount = newCount
        return newCount
    }

    // ========================================
    // Ignition State Tracking
    // ========================================

    /**
     * Последнее известное состояние зажигания.
     * -1 означает неизвестное состояние.
     */
    var lastIgnitionState: Int
        get() = prefs.getInt(KEY_LAST_IGNITION_STATE, -1)
        set(value) {
            prefs.edit().putInt(KEY_LAST_IGNITION_STATE, value).apply()
        }

    /**
     * Timestamp последнего изменения состояния зажигания (ms).
     */
    var lastIgnitionTimestamp: Long
        get() = prefs.getLong(KEY_LAST_IGNITION_TIMESTAMP, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_IGNITION_TIMESTAMP, value).apply()
        }

    /**
     * Timestamp последнего выключения зажигания (ms).
     * Используется для определения "свежего старта".
     */
    var lastIgnitionOffTimestamp: Long
        get() = prefs.getLong(KEY_LAST_IGNITION_OFF_TIMESTAMP, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_IGNITION_OFF_TIMESTAMP, value).apply()
        }

    // ========================================
    // UI Preferences
    // ========================================

    /**
     * Включен ли border overlay при смене режимов.
     */
    var borderEnabled: Boolean
        get() = prefs.getBoolean(KEY_BORDER_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_BORDER_ENABLED, value).apply()
        }

    /**
     * Включен ли panel overlay при смене режимов.
     */
    var panelEnabled: Boolean
        get() = prefs.getBoolean(KEY_PANEL_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_PANEL_ENABLED, value).apply()
        }

    /**
     * Demo mode для тестирования без реального автомобиля.
     */
    var demoMode: Boolean
        get() = prefs.getBoolean(KEY_DEMO_MODE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DEMO_MODE, value).apply()
        }

    // ========================================
    // Utility Methods
    // ========================================

    /**
     * Очистить все настройки (для отладки/reset).
     */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    /**
     * Проверяет был ли "свежий старт" (зажигание выключено дольше чем FRESH_START_WINDOW_MS).
     * @param freshStartWindowMs окно в миллисекундах (по умолчанию 60 секунд)
     * @return true если прошло достаточно времени с последнего выключения
     */
    fun isFreshStart(freshStartWindowMs: Long = 60_000L): Boolean {
        val now = System.currentTimeMillis()
        val lastOff = lastIgnitionOffTimestamp

        return if (lastOff == 0L) {
            // Никогда не записывали - считаем fresh start
            true
        } else {
            // Проверяем прошло ли достаточно времени
            (now - lastOff) > freshStartWindowMs
        }
    }

    /**
     * Сохраняет состояние зажигания с timestamp.
     * @param state новое состояние зажигания
     * @param isOff true если зажигание выключается
     */
    fun saveIgnitionState(state: Int, isOff: Boolean) {
        val now = System.currentTimeMillis()
        lastIgnitionState = state
        lastIgnitionTimestamp = now

        if (isOff) {
            lastIgnitionOffTimestamp = now
        }
    }
}
