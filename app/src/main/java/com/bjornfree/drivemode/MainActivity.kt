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
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.bjornfree.drivemode.ui.theme.DriveModeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.bjornfree.drivemode.core.CarCoreService
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.asImageBitmap
import com.bjornfree.drivemode.core.AutoSeatHeatService
import com.bjornfree.drivemode.core.ServiceWatchdogWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Стартуем ForegroundService'ы, чтобы при открытии приложения всё сразу работало
        try {
            startForegroundService(Intent(this, DriveModeService::class.java))
            startForegroundService(Intent(this, AutoSeatHeatService::class.java))
        } catch (_: IllegalStateException) {
            startService(Intent(this, DriveModeService::class.java))
            startService(Intent(this, AutoSeatHeatService::class.java))
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
                AppScreen(
                    shouldAutoplayAbout = shouldAutoplayAbout,
                    onAboutShownOnce = {
                        prefs.edit().putInt("launch_count", 0).apply()
                    }
                )
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

            DriveModeService.logConsole("MainActivity: ServiceWatchdog scheduled (periodic 15 min)")
        } catch (e: Exception) {
            DriveModeService.logConsole("MainActivity: Failed to schedule watchdog: ${e.message}")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScreen(
    shouldAutoplayAbout: Boolean,
    onAboutShownOnce: () -> Unit,
) {
    val ctx = LocalContext.current
    var showConsole by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var wasAuto by remember { mutableStateOf(false) }

    LaunchedEffect(shouldAutoplayAbout) {
        if (shouldAutoplayAbout) {
            delay(1500)
            showAbout = true
            wasAuto = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DriveMode overlay") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Default.Info, contentDescription = "О приложении", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusCard()
                ActionsCard(showConsole = showConsole, onToggleConsole = { showConsole = !showConsole })
                SeatHeatAutoCard()
                HelpCard()
                ConsoleCard(visible = showConsole)
            }
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = {
                if (wasAuto) {
                    onAboutShownOnce()
                    wasAuto = false
                }
                showAbout = false
            },
            confirmButton = {
                TextButton(onClick = {
                    if (wasAuto) {
                        onAboutShownOnce()
                        wasAuto = false
                    }
                    showAbout = false
                }) { Text("OK") }
            },
            title = { Text("О приложении") },
            text = {
                val ctx2 = LocalContext.current
                val res = ctx2.resources
                // Загружаем bitmap из raw/donate.png
                val donateBitmap = remember {
                    try { BitmapFactory.decodeResource(res, R.raw.donate) } catch (_: Exception) { null }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Автор: bj0rnfree")
                    Text("Александр Сапожников")
                    Text("Geely Binyue L / Coolray", color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Поддержать разработчика",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (donateBitmap != null) {
                        Image(
                            bitmap = donateBitmap.asImageBitmap(),
                            contentDescription = "QR для доната",
                        )
                    } else {
                        Text("QR для доната не удалось загрузить")
                    }
                }
            }
        )
    }
}

@Composable
private fun StatusCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var carApiAvailable by remember { mutableStateOf<Boolean?>(null) }
    var overlayGranted by remember { mutableStateOf<Boolean?>(null) }
    var ignoreBattery by remember { mutableStateOf<Boolean?>(null) }
    var suAvailable by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            val appCtx = ctx.applicationContext
            val core = CarCoreService(appCtx)
            core.init()

            val hasApi = try {
                Class.forName("android.car.Car")
                true
            } catch (_: ClassNotFoundException) {
                false
            }

            // Разрешение на оверлей
            val overlayOk = Settings.canDrawOverlays(appCtx)

            // Исключение из Doze / энергосбережения
            val pm = appCtx.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val batteryOk = pm?.isIgnoringBatteryOptimizations(appCtx.packageName) == true

            // Наличие root (su)
            val suOk = checkSuAvailable()

            withContext(Dispatchers.Main) {
                carApiAvailable = hasApi
                overlayGranted = overlayOk
                ignoreBattery = batteryOk
                suAvailable = suOk
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Статус окружения",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            val carApiText = when (carApiAvailable) {
                null -> "Проверяем…"
                true -> "ОК"
                false -> "Нет android.car"
            }
            val carApiOk: Boolean? = when (carApiAvailable) {
                null -> null
                true -> true
                false -> false
            }

            val overlayText = when (overlayGranted) {
                null -> "Проверяем…"
                true -> "Разрешено"
                false -> "Запрещено"
            }
            val overlayOk: Boolean? = when (overlayGranted) {
                null -> null
                true -> true
                false -> false
            }

            val batteryText = when (ignoreBattery) {
                null -> "Проверяем…"
                true -> "Исключён"
                false -> "Ограничен"
            }
            val batteryOk: Boolean? = when (ignoreBattery) {
                null -> null
                true -> true
                false -> false
            }

            val suText = when (suAvailable) {
                null -> "Проверяем…"
                true -> "Есть su"
                false -> "Нет su"
            }
            val suOk: Boolean? = when (suAvailable) {
                null -> null
                true -> true
                false -> false
            }

            val serviceRunning = DriveModeService.isRunning
            val serviceText = if (serviceRunning) "Запущен" else "Не запущен"
            val serviceOk: Boolean? = serviceRunning

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusTile(
                        title = "Car API",
                        value = carApiText,
                        ok = carApiOk
                    )
                    StatusTile(
                        title = "Overlay",
                        value = overlayText,
                        ok = overlayOk
                    )
                    StatusTile(
                        title = "Root",
                        value = suText,
                        ok = suOk
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusTile(
                        title = "Doze",
                        value = batteryText,
                        ok = batteryOk
                    )
                    StatusTile(
                        title = "Service",
                        value = serviceText,
                        ok = serviceOk
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusTile(
    title: String,
    value: String,
    ok: Boolean?
) {
    val bgColor = when (ok) {
        null -> Color(0xFF424242)
        true -> Color(0xFF2E7D32)
        false -> Color(0xFFC62828)
    }
    val valueColor = Color(0xFFE0E0E0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFBDBDBD)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


@Composable
private fun ActionsCard(
    showConsole: Boolean,
    onToggleConsole: () -> Unit
) {
    val ctx = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Действия",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { onToggleConsole() }) {
                    Text(if (showConsole) "Скрыть консоль" else "Показать консоль")
                }
                Button(onClick = { openOverlaySettings(ctx) }) {
                    Text("Разрешить оверлей")
                }
                Button(onClick = { requestIgnoreBattery(ctx) }) {
                    Text("Исключить из Doze")
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = {
                    val watchdogWork = OneTimeWorkRequestBuilder<ServiceWatchdogWorker>().build()
                    WorkManager.getInstance(ctx).enqueue(watchdogWork)
                    Toast.makeText(ctx, "Watchdog запущен вручную", Toast.LENGTH_SHORT).show()
                    DriveModeService.logConsole("Manual watchdog check triggered")
                }) {
                    Text("Проверить сервисы")
                }
            }
        }
    }
}

@Composable
private fun SeatHeatAutoCard() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("drivemode_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var mode by remember {
        mutableStateOf(
            prefs.getString("seat_auto_heat_mode", "off") ?: "off"
        )
    }

    var heatLevel by remember {
        mutableStateOf(
            prefs.getInt("seat_heat_level", 1)
        )
    }

    var tempThresholdEnabled by remember {
        mutableStateOf(
            prefs.getBoolean("seat_heat_temp_threshold_enabled", false)
        )
    }

    var tempThreshold by remember {
        mutableStateOf(
            prefs.getFloat("seat_heat_temp_threshold", 12f)
        )
    }

    var expandedDropdown by remember { mutableStateOf(false) }
    var expandedTempDropdown by remember { mutableStateOf(false) }
    var outsideTemp by remember { mutableStateOf<Float?>(null) }

    // Динамическое обновление температуры
    LaunchedEffect(Unit) {
        scope.launch {
            while (isActive) {
                outsideTemp = AutoSeatHeatService.getOutsideTemperature()
                delay(2000)
            }
        }
    }

    fun persist(newMode: String) {
        mode = newMode
        prefs.edit().putString("seat_auto_heat_mode", newMode).apply()
        DriveModeService.logConsole("pref: seat_auto_heat_mode=$newMode")
    }

    fun persistHeatLevel(newLevel: Int) {
        heatLevel = newLevel
        prefs.edit().putInt("seat_heat_level", newLevel).apply()
        DriveModeService.logConsole("pref: seat_heat_level=$newLevel")
    }

    fun persistTempThreshold(enabled: Boolean) {
        tempThresholdEnabled = enabled
        prefs.edit().putBoolean("seat_heat_temp_threshold_enabled", enabled).apply()
        DriveModeService.logConsole("pref: seat_heat_temp_threshold_enabled=$enabled")
    }

    fun persistTempThresholdValue(value: Float) {
        tempThreshold = value
        prefs.edit().putFloat("seat_heat_temp_threshold", value).apply()
        DriveModeService.logConsole("pref: seat_heat_temp_threshold=$value")
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Авто-подогрев сидений",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioWithLabel(
                        selected = mode == "off",
                        onClick = { persist("off") },
                        text = "Выключен"
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioWithLabel(
                        selected = mode == "driver",
                        onClick = { persist("driver") },
                        text = "Только водитель"
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioWithLabel(
                        selected = mode == "passenger",
                        onClick = { persist("passenger") },
                        text = "Только пассажир"
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioWithLabel(
                        selected = mode == "both",
                        onClick = { persist("both") },
                        text = "Водитель и пассажир"
                    )
                }
            }

            // Dropdown для выбора мощности обогрева
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Мощность обогрева:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Box {
                    Button(onClick = { expandedDropdown = true }) {
                        Text("Уровень $heatLevel")
                    }
                    DropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Уровень 1") },
                            onClick = {
                                persistHeatLevel(1)
                                expandedDropdown = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Уровень 2") },
                            onClick = {
                                persistHeatLevel(2)
                                expandedDropdown = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Уровень 3") },
                            onClick = {
                                persistHeatLevel(3)
                                expandedDropdown = false
                            }
                        )
                    }
                }
            }

            // Отображение текущей температуры
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Температура снаружи:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = when (outsideTemp) {
                        null -> "Нет данных"
                        else -> "${String.format("%.1f", outsideTemp)}°C"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = when {
                        outsideTemp == null -> Color.Gray
                        outsideTemp!! < tempThreshold -> Color(0xFF2196F3)
                        else -> Color(0xFFFF9800)
                    }
                )
            }

            // Чекбокс для температурного порога (показывается только если есть данные о температуре)
            if (outsideTemp != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = tempThresholdEnabled,
                        onCheckedChange = { persistTempThreshold(it) }
                    )
                    Text("Включать только при низкой температуре")
                }

                // Выбор порога температуры (показывается только если чекбокс включен)
                if (tempThresholdEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Порог температуры:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Box {
                            Button(onClick = { expandedTempDropdown = true }) {
                                Text("Ниже ${tempThreshold.toInt()}°C")
                            }
                            DropdownMenu(
                                expanded = expandedTempDropdown,
                                onDismissRequest = { expandedTempDropdown = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Ниже 0°C") },
                                    onClick = {
                                        persistTempThresholdValue(0f)
                                        expandedTempDropdown = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Ниже 5°C") },
                                    onClick = {
                                        persistTempThresholdValue(5f)
                                        expandedTempDropdown = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Ниже 10°C") },
                                    onClick = {
                                        persistTempThresholdValue(10f)
                                        expandedTempDropdown = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Ниже 12°C") },
                                    onClick = {
                                        persistTempThresholdValue(12f)
                                        expandedTempDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        AutoSeatHeatService.startTest(ctx)
                        Toast.makeText(ctx, "Тест автоподогрева отправлен", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Тест подогрева")
                }
                Button(
                    onClick = {
                        val temp = AutoSeatHeatService.getOutsideTemperature()
                        Toast.makeText(ctx, "Температура: ${temp?.let { String.format("%.1f", it) } ?: "нет данных"}°C. Смотри консоль.", Toast.LENGTH_LONG).show()
                        DriveModeService.logConsole("=== TEMPERATURE DEBUG TEST ===")
                        DriveModeService.logConsole("Triggered manual temperature read")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Тест темп.")
                }
            }
        }
    }
}

@Composable
private fun RadioWithLabel(
    selected: Boolean,
    onClick: () -> Unit,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { if (!selected) onClick() }
        )
        Text(text)
    }
}

@Composable
private fun HelpCard() {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Подсказки",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "1. Дайте приложению право \"Показывать поверх других окон\".\n" +
                        "2. Добавьте в исключения энергосбережения.\n" +
                        "3. Убедитесь, что сервис DriveModeService запущен.\n" +
                        "4. Для автозапуска после перезагрузки используется BootReceiver.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ConsoleCard(visible: Boolean) {
    if (!visible) return

    val scope = rememberCoroutineScope()
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        scope.launch {
            while (isActive) {
                lines = DriveModeService.consoleSnapshot()
                delay(500)
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Text(
                "Консоль",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF111111))
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(lines) { _, line ->
                        Text(
                            text = line,
                            color = Color(0xFFEEEEEE),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun openOverlaySettings(ctx: Context) {
    try {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    } catch (_: Exception) {
    }
}

private fun requestIgnoreBattery(ctx: Context) {
    try {
        val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${ctx.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(i)
    } catch (_: Exception) {
    }
}

private fun checkSuAvailable(): Boolean {
    return try {
        val p = ProcessBuilder("su", "-c", "id").start()
        val exit =
            try {
                p.waitFor()
            } catch (_: InterruptedException) {
                -1
            }
        exit == 0
    } catch (_: Exception) {
        false
    }
}