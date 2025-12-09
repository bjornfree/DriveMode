package com.bjornfree.drivemode.domain.model

/**
 * Данные о топливе автомобиля.
 *
 * Содержит информацию о запасе хода, объеме топлива и емкости бака.
 * Используется для отображения топливного датчика и расчетов.
 */
data class FuelData(
    /**
     * Запас хода на текущем топливе (км).
     * Рассчитывается ECU на основе уровня топлива и среднего расхода.
     * Может быть null если данные недоступны.
     */
    val rangeKm: Float?,

    /**
     * Текущий объем топлива в баке (литры).
     * Рассчитывается на основе rangeKm и среднего расхода.
     * Может быть null если данные недоступны.
     */
    val currentFuelLiters: Float?,

    /**
     * Емкость топливного бака (литры).
     * Для Geely Binyue L / Coolray обычно ~50л.
     */
    val capacityLiters: Float,

    /**
     * Timestamp когда данные были получены.
     */
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Процент заполнения бака (0-100).
     * Возвращает 0 если данные недоступны.
     */
    val fuelPercentage: Int
        get() = currentFuelLiters?.let {
            ((it / capacityLiters) * 100).toInt().coerceIn(0, 100)
        } ?: 0

    /**
     * Проверяет что топливо на критически низком уровне (<10%).
     */
    fun isLowFuel(): Boolean = fuelPercentage < 10

    /**
     * Проверяет что топливо почти закончилось (<5%).
     */
    fun isCriticalFuel(): Boolean = fuelPercentage < 5

    /**
     * Возвращает цвет для индикатора топлива.
     * @return строка с цветом: "green", "yellow", "red"
     */
    fun getFuelColor(): String = when {
        fuelPercentage >= 30 -> "green"
        fuelPercentage >= 10 -> "yellow"
        else -> "red"
    }
}
