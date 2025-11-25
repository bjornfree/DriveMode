package com.bjornfree.drivemode.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.buffer
import com.bjornfree.drivemode.ui.theme.BorderOverlayController
import com.bjornfree.drivemode.ui.theme.ModePanelOverlayController

class DriveModeService : Service() {

    // Канонические режимы движения, с единым ключом для оверлея/уведомлений
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
        @Volatile var isRunning: Boolean = false
        @Volatile var isWatching: Boolean = false

        @Volatile
        private var instance: DriveModeService? = null
        // VehicleProperty IDs из фреймворка (android.hardware.automotive.vehicle.V2_0.VehicleProperty)
        // Используем захардкоженные значения, чтобы не зависеть от android.car.VehiclePropertyIds.
        private const val VEHICLE_PROPERTY_CHANGWEI_DRIVE_MODE = 779092012
        private const val VEHICLE_PROPERTY_CHANGWEI_SWITCH_DRIVER_MODE = 779092013

        // Буфер последних событий для «консоли» (макс. 100 строк)
        private val _console = java.util.Collections.synchronizedList(mutableListOf<String>())
        private const val CONSOLE_LIMIT = 100

        @JvmStatic fun logConsole(msg: String) {
            val line = "${System.currentTimeMillis()} | $msg"
            _console.add(line)
            val overflow = _console.size - CONSOLE_LIMIT
            if (overflow > 0) repeat(overflow) { _console.removeAt(0) }
        }
        @JvmStatic fun consoleSnapshot(): List<String> = java.util.Collections.synchronizedList(_console).toList()

    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val watcher = LogcatWatcher()
    private lateinit var borderOverlay: BorderOverlayController

    private lateinit var modePanelOverlayController: ModePanelOverlayController
    private lateinit var carCore: CarCoreService

    private var watchStartElapsedMs: Long = 0L
    private var isIgnitionOn: Boolean = false
    private var ignitionMonitorJob: Job? = null

    // Watchdog/троттлинг для logcat
    private var watchJob: Job? = null
    private var lastLineAtMs: Long = 0L
    private val MAX_STALL_MS = 10_000L      // если 10с нет строк — перезапуск вотчера
    private val MIN_EVENT_INTERVAL_MS = 100L // минимум 100мс между обработками событий

    // Счетчики ошибок для обработки падений
    private var ignitionMonitorConsecutiveErrors = 0
    private var logcatWatcherConsecutiveErrors = 0
    private var logcatRestartCount = 0
    private val MAX_CONSECUTIVE_ERRORS = 10
    private val MAX_LOGCAT_RESTARTS_PER_HOUR = 20

    // Фильтр от самоповторов и дребезга
    private val selfPid: Int = android.os.Process.myPid()
    private var lastShownMode: String? = null
    private var lastShownAt: Long = 0L
    private val DEDUP_WINDOW_MS = 300L

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        instance = this
        logConsole("Режимы: сервис запущен")
        logConsole("Режимы: источник данных - logcat (принудительно)")

        // Инициализация CarCoreService для мониторинга зажигания
        carCore = CarCoreService(applicationContext)
        carCore.init()

        // Запускаем мониторинг зажигания
        startIgnitionMonitoring()

        // Проверяем текущее состояние зажигания перед запуском logcat
        checkInitialIgnitionState()

        // Watchdog: раз в 5 секунд проверяем, что logcat-живой; если зависли — перезапускаем
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
                    logConsole("Logcat: зависание обнаружено (${sinceLastLine}мс без данных)")

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
                        delay(1000) // Небольшая пауза перед перезапуском
                    } catch (e: Exception) {
                        logConsole("Logcat: ошибка при подготовке к перезапуску: ${e.javaClass.simpleName}: ${e.message}")
                    }

                    // запускаем чтение логов по новой
                    try {
                        startWatchLoop()
                    } catch (e: Exception) {
                        logConsole("Logcat: ОШИБКА не удалось перезапустить: ${e.javaClass.simpleName}: ${e.message}")
                        Log.e("DM", "Failed to restart watchLoop", e)
                    }
                }
            }
        }

        borderOverlay = BorderOverlayController(applicationContext)
        modePanelOverlayController = ModePanelOverlayController(applicationContext)

        logConsole("UI: граница и плавающая панель инициализированы")

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
        ensureOverlayPermissionTip()
        ensureKeepAliveWhitelist()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Позволяем тестировать без logcat: если пришёл mode через broadcast/intent — сразу показываем
        val modeRaw = intent?.getStringExtra("mode")
        val driveMode = DriveMode.fromKeyOrNull(modeRaw)
        Log.i("DM", "onStartCommand: modeRaw=$modeRaw, mapped=$driveMode")
        if (driveMode != null) {
            scope.launch(Dispatchers.Main) { onDriveModeDetected(driveMode) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try { watcher.stop() } catch (_: Exception) {}
        watchJob?.cancel()
        ignitionMonitorJob?.cancel()

        isRunning = false
        instance = null
        isWatching = false
        logConsole("Режимы: сервис остановлен")

        try {
            carCore.release()
        } catch (_: Exception) {}

        super.onDestroy()
        scope.cancel()
        borderOverlay.destroy()
        modePanelOverlayController.destroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Комментарий: читаем логкат и реагируем, с автоматическим перезапуском
    private fun startWatchLoop() {
        watchStartElapsedMs = SystemClock.elapsedRealtime()

        // отменяем предыдущий вотч-джоб, если был
        watchJob?.cancel()

        watchJob = scope.launch(Dispatchers.IO) {
            try {
                isWatching = true
                logConsole("Logcat: мониторинг запущен")

                // Проверяем доступ к logcat после первого запуска
                var logcatCheckDone = false

                watcher
                    .linesFlow()
                    // ограничиваем бэкпрешер: при спаме в logcat берём последние строки
                    .buffer(capacity = 256)
                    .collect { line ->
                        try {
                            // После первой строки проверяем статус доступа
                            if (!logcatCheckDone) {
                                logcatCheckDone = true
                                if (!watcher.hasRootAccess) {
                                    logConsole("WARNING: running without root - may not work on production devices")
                                    showErrorNotification("DriveMode работает без root-доступа. Возможны проблемы с обнаружением режимов.")
                                }
                                // Сбрасываем счетчик ошибок при успешном запуске
                                logcatWatcherConsecutiveErrors = 0
                            }
                            // обновляем метку последней прочитанной строки (для watchdog)
                            lastLineAtMs = SystemClock.elapsedRealtime()

                            // Отсеиваем собственные логи приложения, чтобы не ловить эхо
                            if (line.contains("(${selfPid})") ||
                                line.contains("/DM ") ||
                                line.contains("com.bjornfree.drivemode")
                            ) {
                                return@collect
                            }

                            // даём logcat'у «прогреться» после старта, чтобы не реагировать на старые записи
                            if (SystemClock.elapsedRealtime() - watchStartElapsedMs < 1500) {
                                return@collect
                            }

                            // Пробуем распознать режим езды по логам
                            val mode = LogcatWatcher.parseModeOrNull(line)
                            if (mode != null) {
                                // rate-limit: не чаще, чем раз в MIN_EVENT_INTERVAL_MS для одного и того же режима
                                val now = SystemClock.elapsedRealtime()
                                if ((now - lastShownAt) < MIN_EVENT_INTERVAL_MS && lastShownMode == mode) {
                                    return@collect
                                }

                                logConsole("Logcat: ${line.take(120)} → режим: $mode")
                                Log.d("DM", "Logcat hit: $mode from $line")

                                withContext(Dispatchers.Main) {
                                    onModeDetected(mode)
                                }
                            }
                        } catch (e: Exception) {
                            logcatWatcherConsecutiveErrors++
                            logConsole("Logcat: ошибка обработки строки (#$logcatWatcherConsecutiveErrors): ${e.javaClass.simpleName}: ${e.message}")
                            Log.e("DM", "Error processing logcat line", e)

                            if (logcatWatcherConsecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                                logConsole("Logcat: ОШИБКА - слишком много последовательных ошибок, останавливаем")
                                throw e
                            }
                        }
                    }
            } catch (e: Exception) {
                logcatWatcherConsecutiveErrors++
                logConsole("Logcat: КРИТИЧЕСКАЯ ошибка (#$logcatWatcherConsecutiveErrors): ${e.javaClass.simpleName}: ${e.message}")
                Log.e("DM", "Logcat watcher failed", e)

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
                } catch (_: Exception) {
                }
            }
        }
    }

    // Централизованная обработка режима по enum DriveMode
    private fun onDriveModeDetected(mode: DriveMode) {
        val key = mode.key
        val now = SystemClock.elapsedRealtime()
        if (lastShownMode == key && (now - lastShownAt) < DEDUP_WINDOW_MS) {
            Log.d("DM", "Skip duplicate mode within window: $key")
            return
        }
        lastShownMode = key
        lastShownAt = now

        Log.i("DM", "onDriveModeDetected: $key -> show border overlay + panel")
        try {
            borderOverlay.showMode(key)
            modePanelOverlayController.showMode(key)
        } catch (e: Exception) {
            logConsole("overlay error in onDriveModeDetected: ${e.javaClass.simpleName}: ${e.message}")
            Log.e("DM", "Overlay error in onDriveModeDetected", e)
            try {
                borderOverlay.hide()
            } catch (_: Exception) {
            }
        }

        try {
            updateNotification(key)
        } catch (e: Exception) {
            logConsole("notification error in onDriveModeDetected: ${e.javaClass.simpleName}: ${e.message}")
            Log.e("DM", "Notification error in onDriveModeDetected", e)
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



    // Комментарий: уведомление ForegroundService
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

    // Обновляем уведомление с текущим режимом (для быстрой отладки)
    private fun updateNotification(mode: String) {
        val chId = "drive_mode_service"
        val text = "Режим: ${mode.uppercase()}"
        val n = NotificationCompat.Builder(this, chId)
            .setContentTitle("DriveMode: сервис активен")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
        // Повторный вызов startForeground с тем же ID обновляет уведомление
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
            logConsole("startForeground error in updateNotification: ${e.javaClass.simpleName}: ${e.message}")
            Log.e("DM", "startForeground error in updateNotification", e)
        }
    }

    private fun ensureOverlayPermissionTip() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w("DM", "Overlay permission NOT granted. Enable: Settings > Apps > Special access > Display over other apps > DriveMode")
        } else {
            Log.i("DM", "Overlay permission granted")
        }
    }

    // Просим исключить приложение из оптимизаций батареи (защита от Doze/киллеров)
    private fun ensureKeepAliveWhitelist() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                Log.w("DM", "Requested ignore battery optimizations for $packageName")
            }
        } catch (e: Exception) {
            logConsole("keepAliveWhitelist error: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun checkInitialIgnitionState() {
        scope.launch(Dispatchers.IO) {
            try {
                val ignitionState = carCore.readIgnitionStateRawOrNull()
                if (ignitionState != null) {
                    val isOn = isIgnitionOnLike(ignitionState)
                    isIgnitionOn = isOn
                    logConsole("ignition: initial state=$ignitionState (isOn=$isOn)")

                    if (isOn) {
                        withContext(Dispatchers.Main) {
                            startWatchLoop()
                        }
                    } else {
                        logConsole("ignition: OFF, logcat monitoring paused")
                    }
                } else {
                    // Если не удалось прочитать - запускаем logcat на всякий случай
                    logConsole("ignition: unable to read, starting logcat anyway")
                    withContext(Dispatchers.Main) {
                        startWatchLoop()
                    }
                }
            } catch (e: Exception) {
                logConsole("ignition: check error: ${e.javaClass.simpleName}: ${e.message}")
                // При ошибке запускаем logcat
                withContext(Dispatchers.Main) {
                    startWatchLoop()
                }
            }
        }
    }

    private fun startIgnitionMonitoring() {
        ignitionMonitorJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val ignitionState = carCore.readIgnitionStateRawOrNull()
                    if (ignitionState != null) {
                        // Успешное чтение - сбрасываем счетчик ошибок
                        ignitionMonitorConsecutiveErrors = 0

                        val isOn = isIgnitionOnLike(ignitionState)

                        if (isOn != isIgnitionOn) {
                            isIgnitionOn = isOn
                            logConsole("ignition: changed to ${if (isOn) "ON" else "OFF"} (state=$ignitionState)")

                            withContext(Dispatchers.Main) {
                                if (isOn) {
                                    // Зажигание включено - запускаем/возобновляем logcat
                                    if (!isWatching) {
                                        logConsole("ignition: ON, starting logcat monitoring")
                                        try {
                                            startWatchLoop()
                                        } catch (e: Exception) {
                                            logConsole("ignition: ERROR starting watchLoop: ${e.javaClass.simpleName}: ${e.message}")
                                            Log.e("DM", "Failed to start watchLoop on ignition ON", e)
                                        }
                                    }
                                } else {
                                    // Зажигание выключено - останавливаем logcat
                                    if (isWatching) {
                                        logConsole("ignition: OFF, pausing logcat monitoring")
                                        try {
                                            watchJob?.cancel()
                                            watcher.stop()
                                        } catch (e: Exception) {
                                            logConsole("ignition: error stopping watcher: ${e.javaClass.simpleName}: ${e.message}")
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Не удалось прочитать состояние зажигания
                        ignitionMonitorConsecutiveErrors++
                        if (ignitionMonitorConsecutiveErrors >= 5) {
                            logConsole("ignition: WARNING - ${ignitionMonitorConsecutiveErrors} consecutive read failures")
                        }

                        // При множественных ошибках пробуем переинициализировать carCore
                        if (ignitionMonitorConsecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                            logConsole("ignition: ERROR - too many failures, reinitializing CarCore...")
                            try {
                                carCore.release()
                                delay(2000)
                                carCore = CarCoreService(applicationContext)
                                carCore.init()
                                ignitionMonitorConsecutiveErrors = 0
                                logConsole("ignition: CarCore reinitialized successfully")
                            } catch (e: Exception) {
                                logConsole("ignition: CRITICAL - failed to reinitialize CarCore: ${e.javaClass.simpleName}: ${e.message}")
                                Log.e("DM", "Failed to reinitialize CarCore", e)
                                showErrorNotification("Критическая ошибка мониторинга зажигания")
                                delay(10000) // Большая пауза перед следующей попыткой
                            }
                        }
                    }
                } catch (e: Exception) {
                    ignitionMonitorConsecutiveErrors++
                    logConsole("ignition: monitor error (#$ignitionMonitorConsecutiveErrors): ${e.javaClass.simpleName}: ${e.message}")
                    Log.e("DM", "Ignition monitor error", e)

                    if (ignitionMonitorConsecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                        logConsole("ignition: CRITICAL - too many consecutive errors")
                        showErrorNotification("Критическая ошибка мониторинга зажигания")
                        delay(10000) // Увеличиваем интервал при множественных ошибках
                    }
                }

                // Проверяем каждые 3 секунды
                delay(3000)
            }
        }
    }

    private fun isIgnitionOnLike(state: Int): Boolean {
        // 4 = START, 5 = RUN, 2 = ACC, 0 = OFF
        return when (state) {
            4, 5 -> true
            else -> false
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
            Log.e("DM", "Failed to show error notification", e)
        }
    }

}

/**
 * Ресивер для локального теста: принимает broadcast и пробрасывает режим в сервис.
 * Команда для ADB:
 *  adb shell am broadcast -a com.bjornfree.drivemode.TRIGGER --es mode sport
 *
 * Защита: требует android.permission.DUMP (signature permission), что позволяет
 * вызовы только от system/shell и от самого приложения.
 */
class TriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Дополнительная проверка: принимаем только корректный action
        if (intent.action != "com.bjornfree.drivemode.TRIGGER") {
            Log.w("DM", "TriggerReceiver: invalid action ${intent.action}")
            return
        }

        Log.i("DM", "TriggerReceiver: intent=$intent")
        val mode = intent.getStringExtra("mode")

        // Валидация режима
        if (mode.isNullOrBlank() || mode !in listOf("sport", "comfort", "eco", "adaptive", "normal")) {
            Log.w("DM", "TriggerReceiver: invalid mode '$mode'")
            DriveModeService.logConsole("broadcast: invalid mode='$mode'")
            return
        }

        DriveModeService.logConsole("broadcast: mode=$mode")
        val service = Intent(context, DriveModeService::class.java).apply {
            putExtra("mode", mode)
        }
        Log.i("DM", "TriggerReceiver -> startService with mode=$mode")
        // Стартуем/пингуем ForegroundService; если уже работает — прилетит в onStartCommand
        try {
            context.startForegroundService(service)
            DriveModeService.logConsole("broadcast: startForegroundService OK, mode=$mode")
        } catch (e: IllegalStateException) {
            DriveModeService.logConsole("broadcast: startForegroundService ISE: ${e.message}, fallback to startService, mode=$mode")
            try {
                context.startService(service)
                DriveModeService.logConsole("broadcast: startService OK, mode=$mode")
            } catch (e2: Exception) {
                DriveModeService.logConsole("broadcast: startService error: ${e2.javaClass.simpleName}: ${e2.message}")
                Log.e("DM", "TriggerReceiver startService error", e2)
            }
        } catch (e: Exception) {
            DriveModeService.logConsole("broadcast: startForegroundService error: ${e.javaClass.simpleName}: ${e.message}")
            Log.e("DM", "TriggerReceiver startForegroundService error", e)
        }
    }
}