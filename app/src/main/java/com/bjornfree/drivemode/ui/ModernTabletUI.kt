package com.bjornfree.drivemode.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bjornfree.drivemode.R
import com.bjornfree.drivemode.core.AutoSeatHeatService
import com.bjornfree.drivemode.core.DriveModeService
import com.bjornfree.drivemode.core.VehicleMetricsService
import com.bjornfree.drivemode.domain.model.TireData
import com.bjornfree.drivemode.ui.tabs.VehicleInfoTabOptimized
import com.bjornfree.drivemode.ui.tabs.AutoHeatingTabOptimized
import com.bjornfree.drivemode.ui.tabs.DiagnosticsTabOptimized
import com.bjornfree.drivemode.ui.tabs.ConsoleTabOptimized
import com.bjornfree.drivemode.domain.model.TirePressureData
import com.bjornfree.drivemode.presentation.viewmodel.*
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Современный UI для планшета 14" с Material Design 3.
 * Адаптивная раскладка, красивые карточки, приятная типографика.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernTabletUI() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // Inject ViewModels через Koin
    val vehicleInfoViewModel: VehicleInfoViewModel = koinViewModel()
    val autoHeatingViewModel: AutoHeatingViewModel = koinViewModel()
    val diagnosticsViewModel: DiagnosticsViewModel = koinViewModel()
    val consoleViewModel: ConsoleViewModel = koinViewModel()
    val settingsViewModel: SettingsViewModel = koinViewModel()

    var selectedTab by remember { mutableStateOf(0) }
    var showAbout by remember { mutableStateOf(false) }

    val tabs = listOf(
        TabItem("Бортовой ПК", Icons.Default.Star),      // Информация об авто
        TabItem("Автоподогрев", Icons.Default.Favorite), // Настройки подогрева
        TabItem("Диагностика", Icons.Default.Build),
        TabItem("Консоль", Icons.Default.List),
        TabItem("Настройки", Icons.Default.Settings)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "DriveMode",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = {
                        showAbout = true
                    }) {
                        Icon(Icons.Default.Info, "О приложении")
                    }
                }
            )
        }
    ) { paddingValues ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Navigation Rail для планшета
            NavigationRail(
                modifier = Modifier.fillMaxHeight(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Spacer(Modifier.height(12.dp))
                tabs.forEachIndexed { index, item ->
                    NavigationRailItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                }
            }

            // Основной контент
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(24.dp)
            ) {
                when (selectedTab) {
                    0 -> VehicleInfoTabOptimized(viewModel = vehicleInfoViewModel)
                    1 -> AutoHeatingTabOptimized(viewModel = autoHeatingViewModel)
                    2 -> DiagnosticsTabOptimized(viewModel = diagnosticsViewModel)
                    3 -> ConsoleTabOptimized(viewModel = consoleViewModel)
                    4 -> SettingsTab(viewModel = settingsViewModel)
                }
            }
        }
    }

    // Диалог "О приложении" с QR-кодом
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("OK") }
            },
            title = { Text("О приложении") },
            text = {
                val res = ctx.resources
                // Загружаем bitmap из raw/donate.png
                val donateBitmap = remember {
                    try {
                        BitmapFactory.decodeResource(res, R.raw.donate)
                    } catch (_: Exception) {
                        null
                    }
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

data class TabItem(val title: String, val icon: ImageVector)

@Composable
fun SettingsTab(viewModel: SettingsViewModel) {
    val ctx = LocalContext.current

    // Реактивное получение настроек из ViewModel
    val borderEnabled by viewModel.borderEnabled.collectAsState()
    val panelEnabled by viewModel.panelEnabled.collectAsState()
    val metricsBarEnabled by viewModel.metricsBarEnabled.collectAsState()
    val metricsBarPosition by viewModel.metricsBarPosition.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    // Локальные состояния для разрешений (проверяются периодически)
    var overlayGranted by remember { mutableStateOf(viewModel.hasSystemAlertWindowPermission()) }
    var batteryOptimized by remember { mutableStateOf(!viewModel.isBatteryOptimizationIgnored()) }
    var serviceRunning by remember { mutableStateOf(false) }

    // Проверяем разрешения периодически
    LaunchedEffect(Unit) {
        while (true) {
            overlayGranted = viewModel.hasSystemAlertWindowPermission()
            batteryOptimized = !viewModel.isBatteryOptimizationIgnored()
            serviceRunning = DriveModeService.isRunning
            delay(2000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Настройки",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // Тема приложения
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Тема приложения",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Кнопка "Авто"
                    FilterChip(
                        selected = themeMode == "auto",
                        onClick = { viewModel.setThemeMode("auto") },
                        label = { Text("Авто") },
                        modifier = Modifier.weight(1f)
                    )

                    // Кнопка "Светлая"
                    FilterChip(
                        selected = themeMode == "light",
                        onClick = { viewModel.setThemeMode("light") },
                        label = { Text("Светлая") },
                        modifier = Modifier.weight(1f)
                    )

                    // Кнопка "Темная"
                    FilterChip(
                        selected = themeMode == "dark",
                        onClick = { viewModel.setThemeMode("dark") },
                        label = { Text("Темная") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    when (themeMode) {
                        "auto" -> "Следует системной теме"
                        "light" -> "Всегда светлая тема"
                        "dark" -> "Всегда темная тема"
                        else -> "Следует системной теме"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Оверлеи и интерфейс
        Text(
            "Оверлеи и интерфейс",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        // Border Overlay
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Рамка при смене режима",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Отображать цветную рамку при переключении режимов вождения",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = borderEnabled,
                    onCheckedChange = { viewModel.setBorderEnabled(it) }
                )
            }
        }

        // Panel Overlay
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Панель режима вождения",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Отображать информационную панель с названием текущего режима",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = panelEnabled,
                    onCheckedChange = { viewModel.setPanelEnabled(it) }
                )
            }
        }

        // Metrics Bar
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Нижняя полоска метрик",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Отображать полоску с режимом, передачей, скоростью, запасом хода, температурой и давлением в шинах",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = metricsBarEnabled,
                    onCheckedChange = { viewModel.setMetricsBarEnabled(it) }
                )
            }
        }

        // Metrics Bar Position
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Положение полоски метрик",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Кнопка "Снизу"
                    FilterChip(
                        selected = metricsBarPosition == "bottom",
                        onClick = { viewModel.setMetricsBarPosition("bottom") },
                        label = { Text("Снизу") },
                        modifier = Modifier.weight(1f)
                    )

                    // Кнопка "Сверху"
                    FilterChip(
                        selected = metricsBarPosition == "top",
                        onClick = { viewModel.setMetricsBarPosition("top") },
                        label = { Text("Сверху") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    when (metricsBarPosition) {
                        "bottom" -> "Полоска отображается внизу экрана"
                        "top" -> "Полоска отображается вверху экрана"
                        else -> "Положение по умолчанию"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Разрешения
        Text(
            "Разрешения приложения",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        // Overlay Permission
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Overlay (Оверлей)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        when (overlayGranted) {
                            true -> "Разрешено ✓"
                            false -> "Запрещено - нужно для отображения режимов вождения"
                            null -> "Проверяем..."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (overlayGranted) {
                            true -> MaterialTheme.colorScheme.primary
                            false -> MaterialTheme.colorScheme.error
                            null -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                if (overlayGranted == false) {
                    Button(onClick = {
                        try {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${ctx.packageName}")
                            )
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("Настроить")
                    }
                }
            }
        }

        // Battery Optimization
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Энергосбережение",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        when (batteryOptimized) {
                            false -> "Исключено из оптимизации ✓"
                            true -> "Оптимизируется - может останавливать сервис"
                            null -> "Проверяем..."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (batteryOptimized) {
                            false -> MaterialTheme.colorScheme.primary
                            true -> MaterialTheme.colorScheme.error
                            null -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                if (batteryOptimized == true) {
                    Button(onClick = {
                        try {
                            val intent =
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${ctx.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            ctx.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("Настроить")
                    }
                }
            }
        }

        // Service Status (только для информации)
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Статус сервиса",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (serviceRunning) "Запущен ✓" else "Остановлен",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (serviceRunning)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
