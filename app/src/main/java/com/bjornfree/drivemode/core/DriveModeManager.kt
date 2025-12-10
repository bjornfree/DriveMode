package com.bjornfree.drivemode.core

import android.content.Context

interface DriveModeListener {
    fun onDriveModeChanged(mode: String)
}

/**
 * Лёгкий адаптер поверх CarFunctionManagerSingleton
 * для получения режима вождения.
 *
 * Внутри:
 * - подписывается на FUNCTION_DRIVE_MODE;
 * - мапит raw значения в "eco" / "comfort" / "sport" / "adaptive";
 * - уведомляет listener только при валидных значениях.
 *
 * В будущем можно не трогать этот класс, а добавлять новые функции
 * прямо через CarFunctionManagerSingleton.
 */
class DriveModeManager(
    private val context: Context,
    private val listener: DriveModeListener
) {

    companion object {
        // ID функции режима движения (AdaptAPI)
        private const val FUNCTION_DRIVE_MODE = 570491136

        // Значения, которые приходят в onFunctionValueChanged
        private const val MODE_ECO = 570491137
        private const val MODE_COMFORT = 570491138
        private const val MODE_SPORT = 570491139
        private const val MODE_ADAPTIVE = 570491201
    }

    private var unsubscribe: (() -> Unit)? = null

    fun connect() {
        // Сначала отписываемся, если уже были подписаны
        unsubscribe?.invoke()
        unsubscribe = null

        // Регистрируемся на FUNCTION_DRIVE_MODE в общем менеджере
        unsubscribe = CarFunctionManagerSingleton.registerFunctionListener(
            context = context,
            functionId = FUNCTION_DRIVE_MODE
        ) { rawValue ->
            val mode = mapValueToMode(rawValue) ?: return@registerFunctionListener
            listener.onDriveModeChanged(mode)
        }

        // Однократное чтение текущего значения (если доступно)
        CarFunctionManagerSingleton.getCurrentValue(FUNCTION_DRIVE_MODE)?.let { raw ->
            val mode = mapValueToMode(raw)
            if (mode != null) {
                listener.onDriveModeChanged(mode)
            }
        }
    }

    fun disconnect() {
        unsubscribe?.invoke()
        unsubscribe = null
    }

    private fun mapValueToMode(value: Int): String? =
        when (value) {
            MODE_ECO -> "eco"
            MODE_COMFORT -> "comfort"
            MODE_SPORT -> "sport"
            MODE_ADAPTIVE -> "adaptive"
            else -> null
        }
}