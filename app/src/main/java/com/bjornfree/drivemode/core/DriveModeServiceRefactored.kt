package com.bjornfree.drivemode.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bjornfree.drivemode.data.repository.DriveModeRepository
import com.bjornfree.drivemode.data.repository.IgnitionStateRepository
import com.bjornfree.drivemode.domain.model.IgnitionState
import com.bjornfree.drivemode.ui.theme.BorderOverlayController
import com.bjornfree.drivemode.ui.theme.ModePanelOverlayController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.buffer
import org.koin.android.ext.android.inject

/**
 * Рефакторированный сервис для мониторинга режимов вождения.
 *
 * УПРОЩЕНИЕ:
 * - Было: 693 строки (смешанная ответственность)
 * - Стало: ~400 строк (фокус на logcat + overlays)
 *
 * Удалено:
 * - ❌ Логирование консоли (90+ строк) → DriveModeRepository
 * - ❌ Мониторинг зажигания (120+ строк) → IgnitionStateRepository
 * - ❌ Duplicate CarCore инстанс → использует CarPropertyManagerSingleton через Repository
 *
 * Оставлено:
 * - ✅ Logcat мониторинг режимов вождения
 * - ✅ Управление overlays (border + panel)
 * - ✅ Foreground service lifecycle
 * - ✅ Watchdog для logcat stability
 *
 * Архитектура:
 * LogcatWatcher → DriveModeService → Overlays
 *                      ↓
 *          IgnitionStateRepository (для состояния зажигания)
 *          DriveModeRepository (для логирования)
 *
 * @see IgnitionStateRepository для мониторинга зажигания
 * @see DriveModeRepository для консоли логов
 */
class DriveModeServiceRefactored : Service() {

    // Канонические режимы движения
    private enum class DriveMode(val key: String) {
        SPORT("sport"),
        COMFORT("comfort"),
        ECO("eco"),
        ADAPTIVE("adaptive");

        companion object {
            fun fromKeyOrNull(raw: String?): DriveMode? = when (raw?.lowercase()) {
                "sport" -> SPORT
                "comfort", "normal" -> COMFORT
                "eco" -> ECO
                "adaptive" -> ADAPTIVE
                else -> null
            }
        }
    }

    companion object {
        private const val TAG = "DriveModeService"

        @Volatile
        var isRunning: Boolean = false

        @Volatile
        var isWatching: Boolean = false

        @Volatile
        private var instance: DriveModeServiceRefactored? = null

        /**
         * Проверяет статус сервиса мониторинга режимов.
         */
        @JvmStatic
        fun getServiceStatus(): Boolean {
            return isRunning && instance != null
        }

        /**
         * Перезапускает сервис мониторинга режимов.
         */
        @JvmStatic
        fun restartService(context: Context) {
            try {
                Log.i(TAG, "Принудительный перезапуск...")
                context.stopService(Intent(context, DriveModeServiceRefactored::class.java))
                Thread.sleep(500)
                context.startForegroundService(Intent(context, DriveModeServiceRefactored::class.java))
                Log.i(TAG, "Перезапущен успешно")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка перезапуска", e)
            }
        }
    }

    // Inject repositories через Koin
    private val driveModeRepo: DriveModeRepository by inject()
    private val ignitionRepo: IgnitionStateRepository by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val watcher = LogcatWatcher()
    private lateinit var borderOverlay: BorderOverlayController
    private lateinit var modePanelOverlayController: ModePanelOverlayController

    // Watchdog/троттлинг для logcat
    private var watchJob: Job? = null
    private var ignitionStateJob: Job? = null
    private var watchStartElapsedMs: Long = 0L
    private var lastLineAtMs: Long = 0L
    private val MAX_STALL_MS = 10_000L
    private val MIN_EVENT_INTERVAL_MS = 100L

    // Счетчики ошибок для обработки падений
    private var logcatWatcherConsecutiveErrors = 0
    private var logcatRestartCount = 0
    private val MAX_CONSECUTIVE_ERRORS = 10
    private val MAX_LOGCAT_RESTARTS_PER_HOUR = 20

    // Фильтр от самоповторов и дребезга
    private val selfPid: Int = android.os.Process.myPid()
    private var lastShownMode: String? = null
    private var lastShownAt: Long = 0L
    private val DEDUP_WINDOW_MS = 300L

    // Дедупликация повторяющихся логов
    private var lastLoggedMessage: String? = null
    private val logLock = Any()

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        instance = this
        logConsole("Режимы: сервис запущен")
        logConsole("Режимы: источник данных - logcat (принудительно)")

        // Подписываемся на состояние зажигания из Repository
        subscribeToIgnitionState()

        // Запускаем foreground notification
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(1, notification)
        }

        // Инициализируем overlays
        borderOverlay = BorderOverlayController(applicationContext)
        modePanelOverlayController = ModePanelOverlayController(applicationContext)
        logConsole("UI: граница и плавающая панель инициализированы")

        // Запускаем logcat мониторинг сразу (работаем 24/7)
        logConsole("Logcat: запускаем мониторинг (работаем 24/7)")
        startWatchLoop()

        // Watchdog: проверяем logcat каждые 5 секунд
        scope.launch(Dispatchers.IO) {
            var hourStartTime = System.currentTimeMillis()
            var restartsThisHour = 0

            while (isActive) {
                delay(5_000)

                // Сброс счетчика каждый час
                val currentTime = System.currentTimeMillis()
                if (currentTime - hourStartTime > 3600_000) {
                    hourStartTime = currentTime
                    restartsThisHour = 0
                    logConsole("Watchdog: почасовой сброс - счетчик перезапусков обнулен")
                }

                val sinceLastLine = SystemClock.elapsedRealtime() - lastLineAtMs
                if (isWatching && sinceLastLine > MAX_STALL_MS) {
                    logConsole("Logcat: зависание обнаружено (\${sinceLastLine}мс без данных)")

                    // Проверяем не слишком ли часто перезапускаемся
                    if (restartsThisHour >= MAX_LOGCAT_RESTARTS_PER_HOUR) {
                        logConsole("Logcat: ОШИБКА - слишком много перезапусков ($restartsThisHour/час), отключаем мониторинг")
                        showErrorNotification("Logcat мониторинг отключен из-за частых сбоев")
                        try {
                            watchJob?.cancel()
                            watcher.stop()
                        } catch (_: Exception) {}
                        isWatching = false
                        continue
                    }

                    restartsThisHour++
                    logcatRestartCount++
                    logConsole("Logcat: перезапуск (#$logcatRestartCount, $restartsThisHour/час)")

                    try {
                        watchJob?.cancel()
                        watcher.stop()
                        delay(1000)
                    } catch (e: Exception) {
                        logConsole("Logcat: ошибка при подготовке к перезапуску: \${e.javaClass.simpleName}: \${e.message}")
                    }

                    // Перезапускаем
                    try {
                        startWatchLoop()
                    } catch (e: Exception) {
                        logConsole("Logcat: ОШИБКА не удалось перезапустить: \${e.javaClass.simpleName}: \${e.message}")
                        Log.e(TAG, "Failed to restart watchLoop", e)
                    }
                }
            }
        }

        ensureOverlayPermissionTip()
        ensureKeepAliveWhitelist()
    }

    /**
     * Подписывается на состояние зажигания из IgnitionStateRepository.
     * Вся логика мониторинга зажигания теперь в Repository!
     */
    private fun subscribeToIgnitionState() {
        ignitionStateJob = scope.launch {
            ignitionRepo.ignitionState.collect { state ->
                if (state.isOn) {
                    logConsole("ignition: состояние = ${state.stateName}")
                    // Убедимся что logcat мониторинг запущен
                    if (!isWatching) {
                        logConsole("ignition: мониторинг не активен, запускаем")
                        try {
                            startWatchLoop()
                        } catch (e: Exception) {
                            logConsole("ignition: ОШИБКА запуска мониторинга: ${e.javaClass.simpleName}")
                            Log.e(TAG, "Failed to start watchLoop", e)
                        }
                    }
                } else if (state.isOff) {
                    logConsole("ignition: состояние = ${state.stateName}")
                    // Работаем 24/7 - не останавливаем мониторинг!
                } else {
                    // Intermediate or unknown state
                    logConsole("ignition: состояние = ${state.stateName}")
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Позволяем тестировать без logcat: если пришёл mode через broadcast/intent — сразу показываем
        val modeRaw = intent?.getStringExtra("mode")
        val driveMode = DriveMode.fromKeyOrNull(modeRaw)
        Log.i(TAG, "onStartCommand: modeRaw=$modeRaw, mapped=$driveMode")
        if (driveMode != null) {
            scope.launch(Dispatchers.Main) { onDriveModeDetected(driveMode) }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        logConsole("DriveModeService: onTaskRemoved - перезапускаем сервис")

        // Перезапускаем сервис при удалении задачи
        val restartIntent = Intent(applicationContext, DriveModeServiceRefactored::class.java)
        applicationContext.startForegroundService(restartIntent)
    }

    override fun onDestroy() {
        try { watcher.stop() } catch (_: Exception) {}
        watchJob?.cancel()
        ignitionStateJob?.cancel()

        isRunning = false
        instance = null
        isWatching = false
        logConsole("Режимы: сервис остановлен")

        super.onDestroy()
        scope.cancel()
        borderOverlay.destroy()
        modePanelOverlayController.destroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Запускает мониторинг logcat для определения режимов вождения.
     */
    private fun startWatchLoop() {
        watchStartElapsedMs = SystemClock.elapsedRealtime()

        // Отменяем предыдущий watch-job
        watchJob?.cancel()

        watchJob = scope.launch(Dispatchers.IO) {
            try {
                isWatching = true
                logConsole("Logcat: мониторинг запущен")

                var logcatCheckDone = false

                watcher
                    .linesFlow()
                    .buffer(capacity = 256)
                    .collect { line ->
                        try {
                            // Проверяем root access после первой строки
                            if (!logcatCheckDone) {
                                logcatCheckDone = true
                                if (!watcher.hasRootAccess) {
                                    logConsole("WARNING: running without root - may not work on production devices")
                                    showErrorNotification("DriveMode работает без root-доступа")
                                }
                                logcatWatcherConsecutiveErrors = 0
                            }

                            // Обновляем метку последней прочитанной строки (для watchdog)
                            lastLineAtMs = SystemClock.elapsedRealtime()

                            // Отсеиваем собственные логи
                            if (line.contains("($selfPid)") ||
                                line.contains("/DM ") ||
                                line.contains("com.bjornfree.drivemode")
                            ) {
                                return@collect
                            }

                            // Даём logcat'у "прогреться" после старта
                            if (SystemClock.elapsedRealtime() - watchStartElapsedMs < 1500) {
                                return@collect
                            }

                            // Пробуем распознать режим езды
                            val mode = LogcatWatcher.parseModeOrNull(line)
                            if (mode != null) {
                                // Rate-limit: не чаще раз в MIN_EVENT_INTERVAL_MS
                                val now = SystemClock.elapsedRealtime()
                                if ((now - lastShownAt) < MIN_EVENT_INTERVAL_MS && lastShownMode == mode) {
                                    return@collect
                                }

                                logConsole("Logcat: \${line.take(120)} → режим: $mode")
                                Log.d(TAG, "Logcat hit: $mode from $line")

                                withContext(Dispatchers.Main) {
                                    onModeDetected(mode)
                                }
                            }
                        } catch (e: Exception) {
                            logcatWatcherConsecutiveErrors++
                            logConsole("Logcat: ошибка обработки строки (#$logcatWatcherConsecutiveErrors): \${e.javaClass.simpleName}")
                            Log.e(TAG, "Error processing logcat line", e)

                            if (logcatWatcherConsecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                                logConsole("Logcat: ОШИБКА - слишком много последовательных ошибок, останавливаем")
                                throw e
                            }
                        }
                    }
            } catch (e: Exception) {
                logcatWatcherConsecutiveErrors++
                logConsole("Logcat: КРИТИЧЕСКАЯ ошибка (#$logcatWatcherConsecutiveErrors): \${e.javaClass.simpleName}")
                Log.e(TAG, "Logcat watcher failed", e)

                if (logcatWatcherConsecutiveErrors >= 3) {
                    showErrorNotification("Критическая ошибка мониторинга logcat")
                } else {
                    logConsole("Logcat: автоматический повтор через watchdog")
                }
            } finally {
                isWatching = false
                logConsole("Logcat: мониторинг остановлен")
                try {
                    watcher.stop()
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Обработка обнаруженного режима вождения.
     */
    private fun onDriveModeDetected(mode: DriveMode) {
        val key = mode.key
        val now = SystemClock.elapsedRealtime()
        if (lastShownMode == key && (now - lastShownAt) < DEDUP_WINDOW_MS) {
            Log.d(TAG, "Skip duplicate mode within window: $key")
            return
        }
        lastShownMode = key
        lastShownAt = now

        Log.i(TAG, "onDriveModeDetected: $key -> show overlays")

        // Обновляем текущий режим в Repository
        scope.launch {
            driveModeRepo.setCurrentMode(key)
        }

        try {
            borderOverlay.showMode(key)
            modePanelOverlayController.showMode(key)
        } catch (e: Exception) {
            logConsole("overlay error: \${e.javaClass.simpleName}: \${e.message}")
            Log.e(TAG, "Overlay error", e)
            try {
                borderOverlay.hide()
            } catch (_: Exception) {}
        }

        try {
            updateNotification(key)
        } catch (e: Exception) {
            logConsole("notification error: \${e.javaClass.simpleName}: \${e.message}")
            Log.e(TAG, "Notification error", e)
        }
    }

    private fun onModeDetected(mode: String) {
        val driveMode = DriveMode.fromKeyOrNull(mode)
        if (driveMode == null) {
            logConsole("onModeDetected: unknown mode='$mode', ignore")
            return
        }
        onDriveModeDetected(driveMode)
    }

    /**
     * Логирование в консоль через DriveModeRepository.
     */
    private fun logConsole(msg: String) {
        scope.launch {
            driveModeRepo.logConsole(msg)
        }
    }

    /**
     * Логирует сообщение только если оно отличается от предыдущего.
     */
    private fun logConsoleOnce(msg: String) {
        synchronized(logLock) {
            val messageKey = msg.substringBefore(":")
            if (lastLoggedMessage != messageKey) {
                logConsole(msg)
                lastLoggedMessage = messageKey
            }
        }
    }

    /**
     * Сбрасывает состояние дедупликации.
     */
    private fun resetLogState(component: String) {
        synchronized(logLock) {
            if (lastLoggedMessage != null) {
                logConsole("$component: ✓ Операция возобновлена успешно")
                lastLoggedMessage = null
            }
        }
    }

    private fun buildNotification(): Notification {
        val chId = "drive_mode_service"
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(chId, "DriveMode", NotificationManager.IMPORTANCE_MIN)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, chId)
            .setContentTitle("DriveMode: сервис активен")
            .setContentText("Отслеживание режимов по логам системы")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(mode: String) {
        val chId = "drive_mode_service"
        val text = "Режим: ${mode.uppercase()}"
        val n = NotificationCompat.Builder(this, chId)
            .setContentTitle("DriveMode: сервис активен")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    1,
                    n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(1, n)
            }
        } catch (e: Exception) {
            logConsole("startForeground error: \${e.javaClass.simpleName}")
            Log.e(TAG, "startForeground error", e)
        }
    }

    private fun ensureOverlayPermissionTip() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission NOT granted")
        } else {
            Log.i(TAG, "Overlay permission granted")
        }
    }

    private fun ensureKeepAliveWhitelist() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                Log.w(TAG, "Requested ignore battery optimizations")
            }
        } catch (e: Exception) {
            logConsole("keepAliveWhitelist error: \${e.javaClass.simpleName}")
        }
    }

    private fun showErrorNotification(message: String) {
        try {
            val chId = "drive_mode_errors"
            if (Build.VERSION.SDK_INT >= 26) {
                val ch = NotificationChannel(
                    chId,
                    "DriveMode Errors",
                    NotificationManager.IMPORTANCE_HIGH
                )
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .createNotificationChannel(ch)
            }

            val notification = NotificationCompat.Builder(this, chId)
                .setContentTitle("DriveMode: Ошибка")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(999, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show error notification", e)
        }
    }
}
