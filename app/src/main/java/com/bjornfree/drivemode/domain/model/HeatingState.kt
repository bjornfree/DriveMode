package com.bjornfree.drivemode.domain.model

/**
 * Состояние системы автоподогрева сидений.
 *
 * Используется для передачи текущего состояния подогрева
 * между Repository и ViewModel/UI.
 */
data class HeatingState(
    /**
     * Включен ли подогрев сейчас.
     */
    val isActive: Boolean = false,

    /**
     * Режим автоподогрева.
     * Возможные значения: "off", "adaptive", "always"
     */
    val mode: HeatingMode = HeatingMode.OFF,

    /**
     * Причина включения/выключения подогрева.
     */
    val reason: String? = null,

    /**
     * Текущая температура в салоне (если доступна).
     */
    val currentTemp: Float? = null,

    /**
     * Температурный порог для автоподогрева (°C).
     */
    val temperatureThreshold: Int = 15,

    /**
     * Timestamp последнего изменения состояния.
     */
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Проверяет что подогрев должен быть активен на основе температуры.
     * @return true если температура ниже порога
     */
    fun shouldActivateByTemp(): Boolean {
        return currentTemp != null && currentTemp < temperatureThreshold
    }
}

/**
 * Enum для режимов автоподогрева.
 */
enum class HeatingMode(val key: String) {
    /**
     * Подогрев выключен.
     */
    OFF("off"),

    /**
     * Адаптивный режим - включается на основе температуры.
     */
    ADAPTIVE("adaptive"),

    /**
     * Всегда включен при старте двигателя.
     */
    ALWAYS("always");

    companion object {
        /**
         * Получить режим по строковому ключу.
         * @param key строковый ключ режима
         * @return HeatingMode или OFF если не найдено
         */
        fun fromKey(key: String?): HeatingMode {
            return values().firstOrNull { it.key == key } ?: OFF
        }
    }
}
