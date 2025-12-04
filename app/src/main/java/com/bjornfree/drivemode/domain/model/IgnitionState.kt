package com.bjornfree.drivemode.domain.model

/**
 * Состояние зажигания автомобиля.
 *
 * Используется для отслеживания включения/выключения зажигания
 * и определения момента запуска двигателя.
 */
data class IgnitionState(
    /**
     * Сырое значение состояния зажигания.
     * Значения зависят от прошивки:
     * - 0: OFF (выключено)
     * - 1-3: ACC/различные промежуточные состояния
     * - 4-5: ON/START (зажигание включено, двигатель может быть запущен)
     */
    val rawState: Int,

    /**
     * Timestamp когда состояние было зафиксировано.
     */
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Проверяет что зажигание включено (ON/START).
     * Состояния 4 и 5 считаются "включенными".
     */
    val isOn: Boolean
        get() = rawState in listOf(4, 5)

    /**
     * Проверяет что зажигание выключено (OFF).
     * Состояния 0 и 2 считаются "выключенными".
     */
    val isOff: Boolean
        get() = rawState in listOf(0, 2)

    /**
     * Проверяет что зажигание в промежуточном состоянии (ACC и др.).
     */
    val isIntermediate: Boolean
        get() = !isOn && !isOff

    /**
     * Возвращает человекочитаемое название состояния.
     */
    val stateName: String
        get() = when (rawState) {
            0 -> "OFF"
            1 -> "LOCK"
            2 -> "OFF"
            3 -> "ACC"
            4 -> "ON"
            5 -> "START"
            else -> "UNKNOWN ($rawState)"
        }

    companion object {
        /**
         * Состояние "неизвестно".
         * Используется когда зажигание еще не было прочитано.
         */
        val UNKNOWN = IgnitionState(rawState = -1)

        /**
         * Состояние "выключено".
         */
        val OFF = IgnitionState(rawState = 0)

        /**
         * Состояние "включено".
         */
        val ON = IgnitionState(rawState = 4)

        /**
         * Создает IgnitionState из сырого значения с текущим timestamp.
         * @param raw сырое значение состояния зажигания
         * @return новый IgnitionState
         */
        fun fromRaw(raw: Int): IgnitionState {
            return IgnitionState(rawState = raw)
        }
    }

    /**
     * Определяет был ли переход между состояниями.
     * @param previous предыдущее состояние
     * @return true если произошел переход OFF -> ON или ON -> OFF
     */
    fun hasTransitionFrom(previous: IgnitionState): Boolean {
        return (previous.isOff && this.isOn) || (previous.isOn && this.isOff)
    }

    /**
     * Определяет был ли переход из OFF в ON (запуск).
     * @param previous предыдущее состояние
     * @return true если это момент запуска
     */
    fun isStartupFrom(previous: IgnitionState): Boolean {
        return previous.isOff && this.isOn
    }

    /**
     * Определяет был ли переход из ON в OFF (остановка).
     * @param previous предыдущее состояние
     * @return true если это момент остановки
     */
    fun isShutdownFrom(previous: IgnitionState): Boolean {
        return previous.isOn && this.isOff
    }
}
