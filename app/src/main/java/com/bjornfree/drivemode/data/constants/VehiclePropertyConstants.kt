package com.bjornfree.drivemode.data.constants

/**
 * Центральное хранилище всех Vehicle Property IDs для ECARX/Geely автомобилей.
 *
 * Консолидирует 80+ констант из AutoHeaterService и VehicleMetricsService
 * в одно место для упрощения поддержки и предотвращения дублирования.
 *
 * Источник: дамп свойств из ECARX ROM для Geely Binyue L / Coolray
 *
 * Критическая оптимизация: все константы теперь в одном месте,
 * что устраняет дублирование и упрощает обновления.
 */
object VehiclePropertyConstants {

    // ========================================
    // Базовые Android Car API Properties
    // ========================================

    const val VEHICLE_PROPERTY_IGNITION_STATE = 289408009
    const val VEHICLE_PROPERTY_HVAC_SEAT_TEMPERATURE = 356517131

    // ========================================
    // Температура
    // ========================================

    /**
     * AC_INSIDE_TEMP: Температура в салоне (°C)
     * Формула: (raw - 80) / 2f
     * Диапазон: -40°C до +80°C
     */
    const val CABIN_TEMPERATURE = 0x2140a379

    /**
     * AC_AMBIENT_TEMP: Температура снаружи (°C)
     * Формула: (raw - 80) / 2f
     */
    const val AMBIENT_TEMP = 0x2140a377

    /**
     * ENGINE_OIL_TEMP: Температура масла двигателя (°C)
     */
    const val ENGINE_OIL_TEMP = 0x11600304

    /**
     * IPKINFO_COOLAN_TEMP: Температура охлаждающей жидкости (°C)
     */
    const val COOLANT_TEMP = 0x2140a578

    // ========================================
    // Скорость и двигатель
    // ========================================

    /**
     * PERF_VEHICLE_SPEED: Скорость автомобиля (Float, км/ч)
     */
    const val VEHICLE_SPEED = 0x11600207

    /**
     * EMS_ENGINE_SPEED_RPM: Обороты двигателя (RPM)
     * Формула: raw / 4 для получения реальных оборотов
     */
    const val ENGINE_RPM = 0x2140a609

    /**
     * ENGINE_OIL_LEVEL: Уровень масла в двигателе
     */
    const val ENGINE_OIL_LEVEL = 0x11400303

    // ========================================
    // Трансмиссия
    // ========================================

    /**
     * GEAR_SELECTION: Текущая передача
     * Возможные значения:
     * - 16: D (Drive)
     * - 1, 2, ...: Ручные передачи
     * - 0: Нейтраль / другие режимы
     */
    const val GEAR_SELECTION = 0x11400401

    // ========================================
    // Топливо и запас хода
    // ========================================

    /**
     * RANGE_REMAINING: Остаток запаса хода (км)
     * Float значение
     */
    const val RANGE_REMAINING = 0x11400308

    /**
     * AVERAGE_FUEL_CONSUMPTION: Средний расход топлива (л/100км)
     * Float значение, нужно читать из areaId 0, 1, 2
     */
    const val AVERAGE_FUEL = 0x2740a665

    /**
     * INFO_FUEL_CAPACITY: Ёмкость топливного бака (л)
     */
    const val FUEL_CAPACITY = 0x11600104

    // ========================================
    // Одометр и поездка
    // ========================================

    /**
     * PERF_ODOMETER: Общий пробег (км)
     * Float значение
     */
    const val ODOMETER = 0x11400204

    /**
     * DRIVE_MILEAGE: Пробег текущей поездки (км)
     */
    const val DRIVE_MILEAGE = 0x2740a679

    /**
     * DRIVE_TIME: Время текущей поездки (секунды)
     */
    const val DRIVE_TIME = 0x2740a67a

    // ========================================
    // TPMS - Система контроля давления в шинах
    // ========================================

    // Давление (кПа)
    const val TPMS_PRESSURE_FL = 0x2140a456    // Front Left
    const val TPMS_PRESSURE_FR = 0x2140a457    // Front Right
    const val TPMS_PRESSURE_RL = 0x2140a458    // Rear Left
    const val TPMS_PRESSURE_RR = 0x2140a459    // Rear Right

    // Температура шин (°C)
    const val TPMS_TEMP_FL = 0x2140a460        // Front Left
    const val TPMS_TEMP_FR = 0x2140a461        // Front Right
    const val TPMS_TEMP_RL = 0x2140a462        // Rear Left
    const val TPMS_TEMP_RR = 0x2140a463        // Rear Right

    // ========================================
    // Батарея и электрика
    // ========================================

    /**
     * BODY_BATTERY_BCN_LEVEL: Уровень 12В батареи (%)
     */
    const val BATTERY_LEVEL = 0x2140309b

    // ========================================
    // Качество воздуха и климат
    // ========================================

    /**
     * AC_PM25STS: Качество воздуха PM2.5
     */
    const val PM25_STATUS = 0x2140a358

    // ========================================
    // Режимы и настройки
    // ========================================

    /**
     * NIGHT_MODE: Ночной режим (0 = выкл, 1 = вкл)
     */
    const val NIGHT_MODE = 0x11400501

    // ========================================
    // Area IDs для multi-zone properties
    // ========================================

    /**
     * Список Area IDs для свойств которые поддерживают разные зоны
     * Используется для чтения AVERAGE_FUEL и других multi-area свойств
     */
    val AREA_IDS = listOf(0, 1, 2)

    // ========================================
    // Gear Selection маппинг
    // ========================================

    /**
     * Маппинг кодов передач в человекочитаемые строки
     */
    fun gearToString(gearCode: Int): String = when (gearCode) {
        16 -> "D"
        1 -> "1"
        2 -> "2"
        3 -> "3"
        4 -> "4"
        5 -> "5"
        6 -> "6"
        -1 -> "R"
        0 -> "N"
        else -> "P"
    }

    // ========================================
    // Температурные формулы
    // ========================================

    /**
     * Конвертирует сырое значение температуры ECARX в градусы Цельсия
     * Формула: (raw - 80) / 2
     *
     * @param raw сырое значение от датчика
     * @return температура в °C, или null если значение вне диапазона
     */
    fun rawToCelsius(raw: Int): Float? {
        val temp = (raw - 80) / 2f
        return if (temp in -40f..80f) temp else null
    }

    /**
     * Конвертирует RPM из сырого значения
     * Формула: raw / 4
     */
    fun rawToRPM(raw: Int): Int = raw / 4
}
