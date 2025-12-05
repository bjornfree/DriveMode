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
     * Возможные значения: "off", "driver", "passenger", "both"
     */
    val mode: HeatingMode = HeatingMode.OFF,

    /**
     * Адаптивный режим включен.
     * Если true - уровень подогрева зависит от температуры.
     * Если false - фиксированный уровень.
     */
    val adaptiveHeating: Boolean = false,

    /**
     * Уровень подогрева (0-3).
     * 0 = off, 1 = low, 2 = medium, 3 = high
     * Используется когда adaptiveHeating = false
     */
    val heatingLevel: Int = 2,

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
 * Совместимо со старой версией (driver/passenger/both/off).
 */
enum class HeatingMode(val key: String, val displayName: String) {
    /**
     * Подогрев выключен.
     */
    OFF("off", "Выключен"),

    /**
     * Подогрев только водительского сиденья.
     */
    DRIVER("driver", "Водитель"),

    /**
     * Подогрев только пассажирского сиденья.
     */
    PASSENGER("passenger", "Пассажир"),

    /**
     * Подогрев обоих сидений.
     */
    BOTH("both", "Оба");

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
