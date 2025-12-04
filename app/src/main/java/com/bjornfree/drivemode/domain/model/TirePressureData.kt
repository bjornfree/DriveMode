package com.bjornfree.drivemode.domain.model

/**
 * Data class для хранения данных о давлении и температуре одной шины.
 */
data class TireData(
    val pressure: Int?,      // Давление в кПа
    val temperature: Int?    // Температура в °C
) {
    /**
     * Проверяет что давление в норме (190-250 кПа для легковых авто).
     */
    fun isPressureNormal(): Boolean {
        return pressure != null && pressure in 190..250
    }

    /**
     * Проверяет что давление низкое (<190 кПа).
     */
    fun isPressureLow(): Boolean {
        return pressure != null && pressure < 190
    }

    /**
     * Проверяет что давление высокое (>250 кПа).
     */
    fun isPressureHigh(): Boolean {
        return pressure != null && pressure > 250
    }

    /**
     * Возвращает статус давления для UI.
     */
    fun getPressureStatus(): String = when {
        pressure == null -> "N/A"
        isPressureLow() -> "LOW"
        isPressureHigh() -> "HIGH"
        else -> "OK"
    }
}

/**
 * Data class для хранения данных всех 4 шин.
 *
 * Используется для отображения TPMS (Tire Pressure Monitoring System)
 * информации в UI.
 */
data class TirePressureData(
    val frontLeft: TireData,
    val frontRight: TireData,
    val rearLeft: TireData,
    val rearRight: TireData,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Проверяет что все шины имеют нормальное давление.
     */
    fun allTiresOk(): Boolean {
        return frontLeft.isPressureNormal() &&
                frontRight.isPressureNormal() &&
                rearLeft.isPressureNormal() &&
                rearRight.isPressureNormal()
    }

    /**
     * Проверяет что есть хотя бы одна шина с низким давлением.
     */
    fun hasLowPressure(): Boolean {
        return frontLeft.isPressureLow() ||
                frontRight.isPressureLow() ||
                rearLeft.isPressureLow() ||
                rearRight.isPressureLow()
    }

    /**
     * Возвращает список шин с проблемами.
     * @return список строк с названиями проблемных шин
     */
    fun getProblematicTires(): List<String> {
        val problems = mutableListOf<String>()
        if (!frontLeft.isPressureNormal()) problems.add("Front Left")
        if (!frontRight.isPressureNormal()) problems.add("Front Right")
        if (!rearLeft.isPressureNormal()) problems.add("Rear Left")
        if (!rearRight.isPressureNormal()) problems.add("Rear Right")
        return problems
    }
}
