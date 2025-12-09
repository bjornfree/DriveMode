package com.bjornfree.drivemode.domain.model

/**
 * Главная модель всех метрик автомобиля.
 * Консолидирует данные из различных источников в единый immutable объект.
 *
 * Используется Repository -> ViewModel -> UI для передачи состояния автомобиля.
 */
data class VehicleMetrics(
    // Скорость и двигатель
    val speed: Float = 0f,                      // км/ч
    val rpm: Int = 0,                           // обороты/мин
    val gear: String = "P",                     // текущая передача
    val gearValue: Int = 0,                     // текущая передача

    // Температуры
    val cabinTemperature: Float? = null,        // °C в салоне
    val ambientTemperature: Float? = null,      // °C снаружи
    val engineOilTemp: Float? = null,           // °C масло двигателя
    val coolantTemp: Float? = null,             // °C охлаждающая жидкость

    // Топливо и запас хода
    val fuel: FuelData? = null,                 // данные о топливе
    val rangeRemaining: Float? = null,          // км остаток запаса хода
    val averageFuel: Float? = null,             // л/100км средний расход

    // Пробег
    val odometer: Float? = null,                // км общий пробег
    val tripMileage: Float? = null,             // км пробег поездки
    val tripTime: Int? = null,                  // сек время поездки

    // Шины
    val tirePressure: TirePressureData? = null, // давление и температура шин

    // Батарея и другое
    val batteryLevel: Int? = null,              // % уровень 12В батареи
    val pm25Status: Int? = null,                // качество воздуха
    val nightMode: Boolean = false,             // ночной режим

    val serviceDaysRemaining: Int? = null,
    val serviceDistanceRemainingKm: Int? = null,

    // Timestamp
    val timestamp: Long = System.currentTimeMillis()  // когда обновлены данные
) {
    /**
     * Проверяет что данные свежие (не старше указанного времени).
     * @param maxAgeMs максимальный возраст данных в миллисекундах
     * @return true если данные свежие
     */
    fun isFresh(maxAgeMs: Long = 5000L): Boolean {
        return (System.currentTimeMillis() - timestamp) < maxAgeMs
    }

    /**
     * Проверяет что автомобиль в движении.
     * @return true если скорость > 0
     */
    fun isMoving(): Boolean = speed > 0f

    /**
     * Проверяет что двигатель работает.
     * @return true если RPM > 0
     */
    fun isEngineRunning(): Boolean = rpm > 0
}
