package com.bjornfree.drivemode.util

import android.util.Log
import com.bjornfree.drivemode.data.repository.DriveModeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Централизованный сервис логирования для всего приложения.
 *
 * Обеспечивает единую точку для всех логов:
 * - Логирование в консоль приложения через DriveModeRepository
 * - Логирование в Android Logcat
 * - Поддержка различных уровней логирования
 *
 * Использование:
 * ```
 * AppLogger.initialize(driveModeRepository)
 * AppLogger.log("MyTag", "Сообщение")
 * AppLogger.logError("MyTag", "Ошибка", exception)
 * ```
 */
object AppLogger {
    private const val TAG = "AppLogger"

    private var repository: DriveModeRepository? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Инициализирует AppLogger с репозиторием для логирования в консоль.
     * Должна быть вызвана при старте приложения.
     */
    fun initialize(driveModeRepository: DriveModeRepository) {
        repository = driveModeRepository
        log(TAG, "AppLogger инициализирован")
    }

    /**
     * Логирует информационное сообщение.
     *
     * @param tag Тег компонента (например, "MainActivity", "VehicleService")
     * @param message Сообщение для логирования
     */
    fun log(tag: String, message: String) {
        // Логируем в Android Logcat
        Log.i(tag, message)

        // Логируем в консоль приложения
        logToConsole("$tag: $message")
    }

    /**
     * Логирует предупреждение.
     */
    fun logWarning(tag: String, message: String) {
        Log.w(tag, message)
        logToConsole("⚠️ $tag: $message")
    }

    /**
     * Логирует ошибку.
     *
     * @param tag Тег компонента
     * @param message Сообщение об ошибке
     * @param throwable Исключение (опционально)
     */
    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
            logToConsole("❌ $tag: $message - ${throwable.javaClass.simpleName}: ${throwable.message}")
        } else {
            Log.e(tag, message)
            logToConsole("❌ $tag: $message")
        }
    }

    /**
     * Логирует отладочное сообщение (только в Logcat, не в консоль приложения).
     */
    fun logDebug(tag: String, message: String) {
        Log.d(tag, message)
    }

    /**
     * Логирует сообщение в консоль приложения без дубликатов.
     */
    private fun logToConsole(message: String) {
        scope.launch {
            try {
                repository?.logConsole(message)
            } catch (e: Exception) {
                // Fallback на обычный лог если репозиторий недоступен
                Log.w(TAG, "Failed to log to console: ${e.message}")
            }
        }
    }
}
