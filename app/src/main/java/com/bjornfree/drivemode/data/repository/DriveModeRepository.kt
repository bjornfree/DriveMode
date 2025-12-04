package com.bjornfree.drivemode.data.repository

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Repository для управления режимами вождения и логированием.
 *
 * ОПТИМИЗАЦИЯ КОНСОЛИ:
 * - Заменяет MutableList + removeAt(0) на ArrayDeque
 * - O(n) → O(1) для удаления первого элемента
 * - Thread-safe доступ через Mutex
 * - Реактивный StateFlow для UI
 *
 * Заменяет:
 * - DriveModeService.logConsole() static method
 * - DriveModeService._console MutableList (lines 56-68)
 */
class DriveModeRepository {

    companion object {
        private const val TAG = "DriveModeRepo"
        private const val CONSOLE_LIMIT = 500  // Максимум строк в консоли
    }

    // Оптимизация: ArrayDeque вместо MutableList
    // ArrayDeque.removeFirst() = O(1), MutableList.removeAt(0) = O(n)
    private val consoleBuffer = ArrayDeque<String>(CONSOLE_LIMIT)
    private val mutex = Mutex()  // Thread safety

    // Реактивный state для UI
    private val _console = MutableStateFlow<List<String>>(emptyList())
    val console: StateFlow<List<String>> = _console.asStateFlow()

    // Текущий режим вождения
    private val _currentMode = MutableStateFlow<String?>(null)
    val currentMode: StateFlow<String?> = _currentMode.asStateFlow()

    /**
     * Добавляет сообщение в консоль.
     * Thread-safe, с автоматической очисткой старых сообщений.
     *
     * @param msg сообщение для логирования
     */
    suspend fun logConsole(msg: String) {
        mutex.withLock {
            val timestamp = System.currentTimeMillis()
            val line = "$timestamp | $msg"

            // Добавляем в конец (O(1))
            consoleBuffer.addLast(line)

            // Удаляем старые сообщения если превышен лимит
            while (consoleBuffer.size > CONSOLE_LIMIT) {
                consoleBuffer.removeFirst()  // O(1) благодаря ArrayDeque!
            }

            // Обновляем state для UI
            _console.value = consoleBuffer.toList()
        }

        // Логируем также в Logcat
        Log.d(TAG, msg)
    }

    /**
     * Синхронная версия logConsole для вызова из Java/статических методов.
     * НЕ thread-safe - используйте suspend версию где возможно.
     */
    fun logConsoleSync(msg: String) {
        val timestamp = System.currentTimeMillis()
        val line = "$timestamp | $msg"

        synchronized(consoleBuffer) {
            consoleBuffer.addLast(line)

            while (consoleBuffer.size > CONSOLE_LIMIT) {
                consoleBuffer.removeFirst()
            }

            _console.value = consoleBuffer.toList()
        }

        Log.d(TAG, msg)
    }

    /**
     * Очищает консоль.
     */
    suspend fun clearConsole() {
        mutex.withLock {
            consoleBuffer.clear()
            _console.value = emptyList()
        }
        Log.d(TAG, "Console cleared")
    }

    /**
     * Устанавливает текущий режим вождения.
     * @param mode режим вождения: "eco", "comfort", "sport", "adaptive"
     */
    suspend fun setCurrentMode(mode: String) {
        _currentMode.value = mode
        logConsole("Drive mode changed to: $mode")
    }

    /**
     * Получает все сообщения консоли.
     * @return список всех сообщений
     */
    fun getAllMessages(): List<String> {
        return synchronized(consoleBuffer) {
            consoleBuffer.toList()
        }
    }

    /**
     * Получает последние N сообщений.
     * @param count количество сообщений
     * @return список последних сообщений
     */
    fun getRecentMessages(count: Int): List<String> {
        return synchronized(consoleBuffer) {
            consoleBuffer.takeLast(count)
        }
    }
}
