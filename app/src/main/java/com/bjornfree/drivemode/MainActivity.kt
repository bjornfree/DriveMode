package com.bjornfree.drivemode

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import com.bjornfree.drivemode.core.DriveModeService
import com.bjornfree.drivemode.ui.theme.DriveModeTheme
import com.bjornfree.drivemode.core.AutoSeatHeatService
import com.bjornfree.drivemode.core.ServiceWatchdogWorker
import com.bjornfree.drivemode.core.VehicleMetricsService
import com.bjornfree.drivemode.ui.ModernTabletUI

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ОПТИМИЗАЦИЯ: Сначала показываем UI, потом запускаем сервисы
        enableEdgeToEdge()
        setContent {
            DriveModeTheme {
                // Используем новый современный UI для планшета
                ModernTabletUI()
            }
        }

        // Запускаем сервисы АСИНХРОННО после отображения UI
        // Это позволяет UI показаться мгновенно, а сервисы запустятся в фоне
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            startServicesAsync()
        }

        // Запускаем периодический watchdog для автоматического перезапуска сервисов
        startServiceWatchdog()

        val prefs = getSharedPreferences("drivemode_prefs", Context.MODE_PRIVATE)
        val launches = prefs.getInt("launch_count", 0) + 1
        prefs.edit().putInt("launch_count", launches).apply()
    }

    /**
     * Запускает сервисы асинхронно, не блокируя UI.
     * ОПТИМИЗАЦИЯ: UI показывается моментально, сервисы стартуют в фоне.
     */
    private fun startServicesAsync() {
        try {
            startForegroundService(Intent(this, DriveModeService::class.java))
            startForegroundService(Intent(this, AutoSeatHeatService::class.java))
            startForegroundService(Intent(this, VehicleMetricsService::class.java))
        } catch (_: IllegalStateException) {
            startService(Intent(this, DriveModeService::class.java))
            startService(Intent(this, AutoSeatHeatService::class.java))
            startService(Intent(this, VehicleMetricsService::class.java))
        }
    }

    /**
     * Запускает периодический watchdog для проверки и перезапуска сервисов.
     * Использует uniqueWork чтобы не создавать дубликаты.
     */
    private fun startServiceWatchdog() {
        try {
            val watchdogWork = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "ServiceWatchdogWork",
                ExistingPeriodicWorkPolicy.KEEP, // Не перезаписываем если уже есть
                watchdogWork
            )

            DriveModeService.logConsole("MainActivity: ServiceWatchdog scheduled (periodic 15 min)")
        } catch (e: Exception) {
            DriveModeService.logConsole("MainActivity: Failed to schedule watchdog: ${e.message}")
        }
    }
}

