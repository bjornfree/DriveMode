package com.bjornfree.drivemode.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bjornfree.drivemode.data.repository.DriveModeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel для ConsoleTab (Консоль логов).
 *
 * Предоставляет доступ к логам системы для отладки и мониторинга.
 *
 * До (в ModernTabletUI):
 * ```
 * LaunchedEffect(Unit) {
 *     while (true) {
 *         logs = DriveModeService.getConsoleLogs()  // ❌ Static method call
 *         delay(1000)  // ❌ Polling
 *     }
 * }
 * ```
 *
 * После (с ViewModel):
 * ```
 * val viewModel: ConsoleViewModel = koinViewModel()
 * val logs by viewModel.consoleLogs.collectAsState()
 *
 * LazyColumn {
 *     items(logs) { log ->
 *         Text(log)  // ✅ Реактивно обновляется
 *     }
 * }
 * ```
 *
 * @param driveModeRepo repository с логами консоли
 */
class ConsoleViewModel(
    private val driveModeRepo: DriveModeRepository
) : ViewModel() {

    /**
     * Реактивный поток логов консоли.
     * Автоматически обновляется при добавлении новых логов.
     */
    val consoleLogs: StateFlow<List<String>> = driveModeRepo.console
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Текущий режим вождения.
     */
    val currentMode: StateFlow<String?> = driveModeRepo.currentMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /**
     * Добавляет сообщение в консоль.
     *
     * @param message сообщение для логирования
     */
    fun log(message: String) {
        viewModelScope.launch {
            driveModeRepo.logConsole(message)
        }
    }

    /**
     * Очищает консоль.
     */
    fun clearConsole() {
        viewModelScope.launch {
            driveModeRepo.clearConsole()
        }
    }

    /**
     * Получает количество сообщений в консоли.
     */
    fun getMessageCount(): Int {
        return consoleLogs.value.size
    }

    /**
     * Проверяет пустая ли консоль.
     */
    fun isEmpty(): Boolean {
        return consoleLogs.value.isEmpty()
    }

    /**
     * Получает последние N сообщений.
     *
     * @param count количество сообщений
     * @return список последних сообщений
     */
    fun getRecentMessages(count: Int): List<String> {
        return driveModeRepo.getRecentMessages(count)
    }

    /**
     * Фильтрует логи по ключевому слову.
     *
     * @param keyword ключевое слово для поиска
     * @return отфильтрованный список логов
     */
    fun filterLogs(keyword: String): List<String> {
        if (keyword.isBlank()) {
            return consoleLogs.value
        }
        return consoleLogs.value.filter { it.contains(keyword, ignoreCase = true) }
    }
}
