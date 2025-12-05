package com.bjornfree.drivemode.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        // Можно дать настройку в префах: включен ли автозапуск
        val prefs = context.getSharedPreferences("drivemode_prefs", Context.MODE_PRIVATE)
        val autoStart = prefs.getBoolean("autostart_on_boot", true)
        if (!autoStart) return

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // Используем WorkManager для надежного запуска с задержкой
                val startServicesWork = OneTimeWorkRequestBuilder<StartServicesWorker>()
                    .setInitialDelay(4, TimeUnit.SECONDS)
                    .build()

                WorkManager.getInstance(context).enqueue(startServicesWork)

                // Запускаем периодический watchdog для проверки и перезапуска сервисов
                // Используем uniqueWork чтобы не создавать дубликаты при множественных перезагрузках
                // Каждые 5 минут для быстрого обнаружения падений после долгой стоянки
                val watchdogWork = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(
                    5, TimeUnit.MINUTES
                ).build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "ServiceWatchdogWork",
                    ExistingPeriodicWorkPolicy.KEEP, // Не перезаписываем если уже есть
                    watchdogWork
                )
            }
        }
    }
}

/**
 * Worker для запуска сервисов после перезагрузки.
 * WorkManager гарантирует выполнение даже если процесс BroadcastReceiver завершится.
 */
class StartServicesWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            // Стартуем сервис визуализации режима
            val driveModeIntent = Intent(applicationContext, DriveModeService::class.java)
            applicationContext.startForegroundService(driveModeIntent)

            // Стартуем сервис автоподогрева сидений
            val heaterIntent = Intent(applicationContext, AutoSeatHeatService::class.java)
            applicationContext.startForegroundService(heaterIntent)

            // Стартуем сервис мониторинга параметров автомобиля
            VehicleMetricsService.start(applicationContext)

            DriveModeService.logConsole("BootReceiver: services started via WorkManager")
            Result.success()
        } catch (e: Exception) {
            DriveModeService.logConsole("BootReceiver: error starting services: ${e.javaClass.simpleName}: ${e.message}")
            Result.failure()
        }
    }
}

/**
 * Watchdog Worker для периодической проверки состояния сервисов.
 * Перезапускает упавшие сервисы автоматически.
 */
class ServiceWatchdogWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            // Watchdog ВСЕГДА работает и проверяет сервисы 24/7
            // Настройка autostart_on_boot влияет только на запуск при загрузке системы

            // Проверяем AutoSeatHeatService
            if (!AutoSeatHeatService.isServiceRunning()) {
                DriveModeService.logConsole("Watchdog: AutoSeatHeatService не работает, перезапускаем...")
                try {
                    AutoSeatHeatService.start(applicationContext)
                    DriveModeService.logConsole("Watchdog: AutoSeatHeatService перезапущен успешно")
                } catch (e: Exception) {
                    DriveModeService.logConsole("Watchdog: Не удалось перезапустить AutoSeatHeatService: ${e.message}")
                }
            } else {
                DriveModeService.logConsole("Watchdog: AutoSeatHeatService работает OK")
            }

            // Проверяем DriveModeService
            if (!DriveModeService.isRunning) {
                DriveModeService.logConsole("Watchdog: DriveModeService не работает, перезапускаем...")
                try {
                    val intent = Intent(applicationContext, DriveModeService::class.java)
                    applicationContext.startForegroundService(intent)
                    DriveModeService.logConsole("Watchdog: DriveModeService перезапущен успешно")
                } catch (e: Exception) {
                    DriveModeService.logConsole("Watchdog: Не удалось перезапустить DriveModeService: ${e.message}")
                }
            } else {
                DriveModeService.logConsole("Watchdog: DriveModeService работает OK")
            }

            // Проверяем VehicleMetricsService
            if (!VehicleMetricsService.isServiceRunning()) {
                DriveModeService.logConsole("Watchdog: VehicleMetricsService не работает, перезапускаем...")
                try {
                    VehicleMetricsService.start(applicationContext)
                    DriveModeService.logConsole("Watchdog: VehicleMetricsService перезапущен успешно")
                } catch (e: Exception) {
                    DriveModeService.logConsole("Watchdog: Не удалось перезапустить VehicleMetricsService: ${e.message}")
                }
            } else {
                DriveModeService.logConsole("Watchdog: VehicleMetricsService работает OK")
            }

            Result.success()
        } catch (e: Exception) {
            DriveModeService.logConsole("Watchdog: ошибка проверки: ${e.javaClass.simpleName}: ${e.message}")
            Result.retry()
        }
    }
}
