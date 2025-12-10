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

        val prefs = context.getSharedPreferences("drivemode_prefs", Context.MODE_PRIVATE)
        val autoStart = prefs.getBoolean("autostart_on_boot", true)

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (!autoStart) return

                val wm = WorkManager.getInstance(context)

                val startServicesWork = OneTimeWorkRequestBuilder<StartServicesWorker>()
                    .setInitialDelay(4, TimeUnit.SECONDS)
                    .build()
                wm.enqueue(startServicesWork)

                // Периодический watchdog для проверки и перезапуска сервисов
                val watchdogWork = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(
                    1, TimeUnit.MINUTES
                ).build()

                wm.enqueueUniquePeriodicWork(
                    "ServiceWatchdogWork",
                    ExistingPeriodicWorkPolicy.KEEP,
                    watchdogWork
                )
            }
        }
    }
}

class StartServicesWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            val driveModeIntent = Intent(applicationContext, DriveModeService::class.java)
            applicationContext.startForegroundService(driveModeIntent)

            val heaterIntent = Intent(applicationContext, AutoSeatHeatService::class.java)
            applicationContext.startForegroundService(heaterIntent)

            VehicleMetricsService.start(applicationContext)

            Result.success()
        } catch (_: Exception) {
            Result.failure()
        }
    }
}

class ServiceWatchdogWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            if (!AutoSeatHeatService.isServiceRunning()) {
                try {
                    AutoSeatHeatService.start(applicationContext)
                } catch (_: Exception) {
                }
            }

            if (!DriveModeService.isRunning) {
                try {
                    val intent = Intent(applicationContext, DriveModeService::class.java)
                    applicationContext.startForegroundService(intent)
                } catch (_: Exception) {
                }
            }

            if (!VehicleMetricsService.isServiceRunning()) {
                try {
                    VehicleMetricsService.start(applicationContext)
                } catch (_: Exception) {
                }
            }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}