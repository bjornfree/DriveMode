package com.bjornfree.drivemode

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bjornfree.drivemode.core.DriveModeService
import com.bjornfree.drivemode.ui.ModernTabletUI
import com.bjornfree.drivemode.ui.theme.DriveModeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Сначала показываем UI
        enableEdgeToEdge()
        setContent {
            DriveModeTheme {
                ModernTabletUI()
            }
        }

        // Сервис стартуем после отрисовки UI, но без лишних задержек и дубликатов
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            startServicesAsync()
        }
    }

    /**
     * Запускает core-сервис, не создавая дубликатов.
     */
    private fun startServicesAsync() {
        // Если сервис уже запущен – ничего не делаем
        if (DriveModeService.getServiceStatus()) return

        val appContext = applicationContext
        val intent = Intent(appContext, DriveModeService::class.java)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        } catch (_: IllegalStateException) {
            // fallback на обычный startService, если система не разрешила foreground
            appContext.startService(intent)
        }
    }
}