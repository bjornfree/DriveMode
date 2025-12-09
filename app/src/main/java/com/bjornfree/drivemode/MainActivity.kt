package com.bjornfree.drivemode

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bjornfree.drivemode.core.DriveModeService
import com.bjornfree.drivemode.core.DriveModeServiceRefactored
import com.bjornfree.drivemode.data.preferences.PreferencesManager
import com.bjornfree.drivemode.ui.theme.DriveModeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.bjornfree.drivemode.core.CarCoreService
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.asImageBitmap
import com.bjornfree.drivemode.core.AutoSeatHeatService
import com.bjornfree.drivemode.core.ServiceWatchdogWorker
import com.bjornfree.drivemode.core.VehicleMetricsService
import com.bjornfree.drivemode.ui.ModernTabletUI

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Стартуем ForegroundService'ы, чтобы при открытии приложения всё сразу работало
        try {
            startForegroundService(Intent(this, DriveModeServiceRefactored::class.java))
            startForegroundService(Intent(this, AutoSeatHeatService::class.java))
            startForegroundService(Intent(this, VehicleMetricsService::class.java))
        } catch (_: IllegalStateException) {
            startService(Intent(this, DriveModeServiceRefactored::class.java))
            startService(Intent(this, AutoSeatHeatService::class.java))
            startService(Intent(this, VehicleMetricsService::class.java))
        }

        // Запускаем периодический watchdog для автоматического перезапуска сервисов
        startServiceWatchdog()

        val prefs = getSharedPreferences("drivemode_prefs", Context.MODE_PRIVATE)
        val launches = prefs.getInt("launch_count", 0) + 1
        prefs.edit().putInt("launch_count", launches).apply()
        val shouldAutoplayAbout = launches >= 3

        enableEdgeToEdge()
        setContent {
            DriveModeTheme {
                // Используем новый современный UI для планшета
                ModernTabletUI()
            }
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

            DriveModeServiceRefactored.logConsole("MainActivity: ServiceWatchdog scheduled (periodic 15 min)")
        } catch (e: Exception) {
            DriveModeServiceRefactored.logConsole("MainActivity: Failed to schedule watchdog: ${e.message}")
        }
    }
}

