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
import com.bjornfree.drivemode.domain.model.TirePressureData
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
                    0 -> VehicleInfoTab()     // Бортовой ПК
                    1 -> AutoHeatingTab()     // Автоподогрев
                    2 -> DiagnosticsTab()
                    3 -> ConsoleTab()
                    4 -> SettingsTab()
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

/**
 * Вкладка с информацией об автомобиле (бортовой компьютер)
 */
@Composable
fun VehicleInfoTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var cabinTemp by remember { mutableStateOf<Float?>(null) }
    var ambientTemp by remember { mutableStateOf<Float?>(null) }
    var oilTemp by remember { mutableStateOf<Float?>(null) }
    var coolantTemp by remember { mutableStateOf<Float?>(null) }
    var fuel by remember { mutableStateOf<Float?>(null) }
    var avgFuel by remember { mutableStateOf<Float?>(null) }
    var odometer by remember { mutableStateOf<Float?>(null) }
    var tripMileage by remember { mutableStateOf<Float?>(null) }
    var tripTime by remember { mutableStateOf<Float?>(null) }
    var tirePressure by remember { mutableStateOf<TirePressureData?>(null) }
    var gear by remember { mutableStateOf<String?>(null) }
    var speed by remember { mutableStateOf<Float?>(null) }
    var rpm by remember { mutableStateOf<Float?>(null) }

    // Быстрое обновление real-time метрик (коробка передач) - каждые 200мс
    LaunchedEffect(Unit) {
        while (true) {
            gear = VehicleMetricsService.getGear()
            speed = VehicleMetricsService.getSpeed()
            rpm = VehicleMetricsService.getRPM()
            kotlinx.coroutines.delay(200)
        }
    }

    // Медленное обновление остальных данных - каждые 5 секунд
    LaunchedEffect(Unit) {
        while (true) {
            cabinTemp = AutoSeatHeatService.getCabinTemperature()
            ambientTemp = AutoSeatHeatService.getAmbientTemperature()
            oilTemp = AutoSeatHeatService.getOilTemperature()
            coolantTemp = AutoSeatHeatService.getCoolantTemperature()
            fuel = AutoSeatHeatService.getFuel()
            avgFuel = AutoSeatHeatService.getAverageFuel()
            odometer = AutoSeatHeatService.getOdometer()
            tripMileage = AutoSeatHeatService.getTripMileage()
            tripTime = AutoSeatHeatService.getTripTime()
            tirePressure = AutoSeatHeatService.getTirePressureData()
            kotlinx.coroutines.delay(5000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Заголовок
        Text(
            "Бортовой компьютер",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // Карточка: Коробка передач
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Коробка передач",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Текущая передача (большая)
                    InfoItem(
                        modifier = Modifier.weight(1f),
                        label = "Передача",
                        value = gear ?: "—",
                        color = when (gear) {
                            "P" -> Color(0xFFE53935)
                            "R" -> Color(0xFFFF9800)
                            "N" -> Color(0xFF2196F3)
                            "D" -> Color(0xFF4CAF50)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )

                    // Скорость
                    InfoItem(
                        modifier = Modifier.weight(1f),
                        label = "Скорость",
                        value = speed?.let { "${it.toInt()} км/ч" } ?: "—",
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Обороты
                    InfoItem(
                        modifier = Modifier.weight(1f),
                        label = "Обороты",
                        value = rpm?.let { "${it.toInt()} RPM" } ?: "—",
                        color = when {
                            rpm == null -> Color.Gray
                            rpm!! < 1000 -> Color(0xFF4CAF50)
                            rpm!! < 3000 -> MaterialTheme.colorScheme.onSurface
                            else -> Color(0xFFFF9800)
                        }
                    )
                }
            }
        }

        // Карточка: Топливо и давление в шинах
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max) // Выравниваем высоты обеих карточек
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Топливо (слева)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    FuelGauge(
                        fuelLiters = fuel,
                        tankCapacity = 45f
                    )
                }

                // Давление в шинах (справа)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    TirePressureDisplay(tirePressureData = tirePressure)
                }
            }
        }

        // Карточка: Температура
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Температура",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                // Первая строка: Салон и Снаружи
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    InfoItem(
                        modifier = Modifier.weight(1f),
                        label = "В салоне",
                        value = cabinTemp?.let { "${it.toInt()}°C" } ?: "—",
                        color = when {
                            cabinTemp == null -> Color.Gray
                            cabinTemp!! < 0f -> Color(0xFF2196F3)
                            cabinTemp!! < 15f -> Color(0xFF03A9F4)
                            else -> Color(0xFFFF9800)
                        }
                    )

                    InfoItem(
                        modifier = Modifier.weight(1f),
                        label = "Снаружи",
                        value = ambientTemp?.let { "${it.toInt()}°C" } ?: "—",
                        color = when {
                            ambientTemp == null -> Color.Gray
                            ambientTemp!! < 0f -> Color(0xFF2196F3)
                            ambientTemp!! < 10f -> Color(0xFF03A9F4)
                            else -> Color(0xFF4CAF50)
                        }
                    )
                }

                HorizontalDivider()

                // Вторая строка: Масло и Охлаждающая жидкость
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    InfoItem(
                        modifier = Modifier.weight(1f),
                        label = "Масло двигателя",
                        value = oilTemp?.let { "${it.toInt()}°C" } ?: "—",
                        color = when {
                            oilTemp == null -> Color.Gray
                            oilTemp!! < 60f -> Color(0xFF2196F3)
                            oilTemp!! < 90f -> Color(0xFF4CAF50)
                            oilTemp!! < 110f -> Color(0xFFFF9800)
                            else -> Color(0xFFE53935)
                        }
                    )

                    InfoItem(
                        modifier = Modifier.weight(1f),
                        label = "Охлаждающая жидкость",
                        value = coolantTemp?.let { "${it.toInt()}°C" } ?: "—",
                        color = when {
                            coolantTemp == null -> Color.Gray
                            coolantTemp!! < 60f -> Color(0xFF2196F3)
                            coolantTemp!! < 90f -> Color(0xFF4CAF50)
                            coolantTemp!! < 105f -> Color(0xFFFF9800)
                            else -> Color(0xFFE53935)
                        }
                    )
                }
            }
        }

        // Карточка: Расход и пробег
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Расход и пробег",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                // Первая строка: Средний расход и Запас хода
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    InfoItem(
                        modifier = Modifier.weight(1f),
                        label = "Расход (средний)",
                        value = avgFuel?.let { "${String.format("%.1f", it)} л/100км" } ?: "—",
                        color = when {
                            avgFuel == null -> Color.Gray
                            avgFuel!! < 7f -> Color(0xFF4CAF50)
                            avgFuel!! < 10f -> Color(0xFFFF9800)
                            else -> Color(0xFFE53935)
                        }
                    )

                    val range = if (fuel != null && avgFuel != null && avgFuel!! > 0) {
                        (fuel!! / avgFuel!!) * 100
                    } else null

                    InfoItem(
                        modifier = Modifier.weight(1f),
                        label = "Запас хода",
                        value = range?.let { "${String.format("%.0f", it)} км" } ?: "—",
                        color = when {
                            range == null -> Color.Gray
                            range < 50 -> Color(0xFFE53935)
                            range < 150 -> Color(0xFFFF9800)
                            else -> Color(0xFF4CAF50)
                        }
                    )
                }

                HorizontalDivider()

                // Вторая строка: Общий пробег и Пробег поездки
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    InfoItem(
                        modifier = Modifier.weight(1f),
                        label = "Пробег общий",
                        value = odometer?.let { "${String.format("%.0f", it)} км" } ?: "—",
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    InfoItem(
                        modifier = Modifier.weight(1f),
                        label = "Пробег поездки",
                        value = tripMileage?.let { "${String.format("%.1f", it)} км" } ?: "—",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                HorizontalDivider()

                // Третья строка: Время поездки
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val tripTimeFormatted = tripTime?.let {
                        val hours = (it / 60).toInt()
                        val mins = (it % 60).toInt()
                        "${hours}ч ${mins}м"
                    } ?: "—"

                    InfoItem(
                        modifier = Modifier.weight(1f),
                        label = "Время поездки",
                        value = tripTimeFormatted,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Компонент для отображения одного показателя
 */
@Composable
fun InfoItem(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: Color
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun AutoHeatingTab() {
    val ctx = LocalContext.current
    val prefs = ctx.getSharedPreferences("drivemode_prefs", Context.MODE_PRIVATE)
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(prefs.getString("seat_auto_heat_mode", "off") ?: "off") }
    var heatLevel by remember { mutableStateOf(prefs.getInt("seat_heat_level", 2)) }
    var adaptiveHeating by remember { mutableStateOf(prefs.getBoolean("adaptive_heating", false)) }
    var tempThreshold by remember {
        mutableStateOf(
            prefs.getFloat(
                "seat_heat_temp_threshold",
                12f
            )
        )
    }
    var tempThresholdEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                "seat_heat_temp_threshold_enabled",
                false
            )
        )
    }
    var outsideTemp by remember { mutableStateOf<Float?>(null) }

    // Обновляем температуру для порога
    LaunchedEffect(Unit) {
        while (true) {
            outsideTemp = AutoSeatHeatService.getCabinTemperature()
            kotlinx.coroutines.delay(5000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Заголовок секции
        Text(
            "Автоматический подогрев сидений",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // Карточка режима работы
        ModeCard(
            mode = mode,
            onModeChange = { newMode ->
                mode = newMode
                prefs.edit().putString("seat_auto_heat_mode", newMode).apply()
            }
        )

        // Карточка настроек мощности
        HeatLevelCard(
            heatLevel = heatLevel,
            enabled = !adaptiveHeating,
            onHeatLevelChange = { newLevel ->
                heatLevel = newLevel
                prefs.edit().putInt("seat_heat_level", newLevel).apply()
            }
        )

        // Карточка адаптивного обогрева
        AdaptiveHeatingCard(
            enabled = adaptiveHeating,
            onEnabledChange = { enabled ->
                adaptiveHeating = enabled
                prefs.edit().putBoolean("adaptive_heating", enabled).apply()
            }
        )

        // Карточка температурного порога
        if (outsideTemp != null) {
            TemperatureThresholdCard(
                enabled = tempThresholdEnabled,
                threshold = tempThreshold,
                adaptiveEnabled = adaptiveHeating,
                onEnabledChange = { enabled ->
                    tempThresholdEnabled = enabled
                    prefs.edit().putBoolean("seat_heat_temp_threshold_enabled", enabled).apply()
                },
                onThresholdChange = { threshold ->
                    tempThreshold = threshold
                    prefs.edit().putFloat("seat_heat_temp_threshold", threshold).apply()
                }
            )
        }

        // Кнопка теста
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Тестирование",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                FilledTonalButton(
                    onClick = {
                        AutoSeatHeatService.startTest(ctx)
                        Toast.makeText(ctx, "Тест подогрева запущен", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, "Тест", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Запустить тест подогрева")
                }
            }
        }
    }
}

/**
 * Визуальный индикатор топлива в стиле ретро-приборки с динамическими цветами
 */
@Composable
fun FuelGauge(
    fuelLiters: Float?,
    tankCapacity: Float = 45f
) {
    val fuelPercent = if (fuelLiters != null && fuelLiters > 0) {
        (fuelLiters / tankCapacity * 100).coerceIn(0f, 100f)
    } else {
        0f
    }

    // Определяем основной цвет в зависимости от уровня топлива
    val gaugeColor = when {
        fuelPercent < 15f -> Color(0xFFE53935)  // Красный - критический
        fuelPercent < 30f -> Color(0xFFFF6F00)  // Темно-оранжевый - низкий
        fuelPercent < 50f -> Color(0xFFFFA726)  // Оранжевый
        fuelPercent < 70f -> Color(0xFFFFD54F)  // Желтый
        fuelPercent < 85f -> Color(0xFF9CCC65)  // Светло-зеленый
        else -> Color(0xFF66BB6A)               // Зеленый - полный
    }

    // Цвет фона карточки с легким оттенком
    val backgroundColor = when {
        fuelPercent < 15f -> Color(0xFFE53935).copy(alpha = 0.08f)  // Красноватый фон
        fuelPercent < 30f -> Color(0xFFFF9800).copy(alpha = 0.08f)  // Оранжевый фон
        else -> Color(0xFF4CAF50).copy(alpha = 0.05f)               // Зеленоватый фон
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight() // Заполняем всю доступную высоту
            .background(
                backgroundColor,
                androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center // Центрируем контент по вертикали
    ) {
        // Визуальная шкала
        Box(
            modifier = Modifier
                .size(220.dp)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val centerX = canvasWidth / 2f
                val centerY = canvasHeight * 0.75f
                val radius = canvasWidth * 0.38f

                val startAngle = 180f
                val sweepAngle = 180f

                // Фон шкалы (темнее для лучшего контраста)
                drawArc(
                    color = Color(0xFF1A1A1A),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(centerX - radius, centerY - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = 24f, cap = StrokeCap.Round)
                )

                // Цветная шкала с плавным градиентом от красного (слева, Empty) к зеленому (справа, Full)
                val fuelSweep = sweepAngle * (fuelPercent / 100f)

                drawArc(
                    brush = Brush.sweepGradient(
                        0f to Color(0xFF66BB6A),      // 0° (справа) - зеленый (Full)
                        0.125f to Color(0xFF9CCC65),  // светло-зеленый
                        0.25f to Color(0xFFFFD54F),   // желтый
                        0.375f to Color(0xFFFFA726),  // оранжевый
                        0.4375f to Color(0xFFFF6F00), // темно-оранжевый
                        0.5f to Color(0xFFE53935),    // 180° (слева) - красный (Empty)
                        0.5625f to Color(0xFFFF6F00), // темно-оранжевый (обратно)
                        0.625f to Color(0xFFFFA726),  // оранжевый
                        0.75f to Color(0xFFFFD54F),   // желтый
                        0.875f to Color(0xFF9CCC65),  // светло-зеленый
                        1f to Color(0xFF66BB6A),      // 360° = 0° (справа) - зеленый (Full)
                        center = Offset(centerX, centerY)
                    ),
                    startAngle = startAngle,
                    sweepAngle = fuelSweep,
                    useCenter = false,
                    topLeft = Offset(centerX - radius, centerY - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = 24f, cap = StrokeCap.Round)
                )

                // Добавляем внутреннее свечение для эффекта глубины
                if (fuelSweep > 0) {
                    drawArc(
                        color = gaugeColor.copy(alpha = 0.25f),
                        startAngle = startAngle,
                        sweepAngle = fuelSweep,
                        useCenter = false,
                        topLeft = Offset(centerX - radius + 6f, centerY - radius + 6f),
                        size = Size((radius - 6f) * 2, (radius - 6f) * 2),
                        style = Stroke(width = 10f, cap = StrokeCap.Round)
                    )
                }

                // Отметки на шкале
                val marks = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
                marks.forEach { mark ->
                    val angle = startAngle + sweepAngle * mark
                    val angleRad = angle * PI / 180.0
                    val x1 = centerX + (radius - 25f) * cos(angleRad).toFloat()
                    val y1 = centerY + (radius - 25f) * sin(angleRad).toFloat()
                    val x2 = centerX + (radius - 5f) * cos(angleRad).toFloat()
                    val y2 = centerY + (radius - 5f) * sin(angleRad).toFloat()

                    drawLine(
                        color = Color.White,
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = 2f
                    )
                }

                // Стрелка указателя
                val pointerAngle = startAngle + sweepAngle * (fuelPercent / 100f)
                val pointerLength = radius - 30f

                rotate(degrees = pointerAngle, pivot = Offset(centerX, centerY)) {
                    drawLine(
                        color = Color.Black.copy(alpha = 0.3f),
                        start = Offset(centerX + 2f, centerY + 2f),
                        end = Offset(centerX + pointerLength + 2f, centerY + 2f),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(centerX, centerY),
                        end = Offset(centerX + pointerLength, centerY),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                }

                // Центральная точка
                drawCircle(
                    color = Color(0xFF424242),
                    radius = 8f,
                    center = Offset(centerX, centerY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 5f,
                    center = Offset(centerX, centerY)
                )
            }

            // Метки E и F
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 120.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "E",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFE53935),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "F",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Числовые значения с динамическими цветами
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = when (fuelLiters) {
                    null -> "Нет данных"
                    else -> "${String.format("%.1f", fuelLiters)}л"
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (fuelLiters == null) Color.Gray else gaugeColor
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (fuelLiters) {
                        null -> "—"
                        else -> "${fuelPercent.toInt()}%"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (fuelLiters == null) Color.Gray else gaugeColor
                )
                Text(
                    "•",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Text(
                    text = "от ${tankCapacity.toInt()}л",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }

            // Индикатор уровня текстом
            if (fuelLiters != null) {
                Text(
                    text = when {
                        fuelPercent < 15f -> "⚠️ КРИТИЧЕСКИЙ УРОВЕНЬ"
                        fuelPercent < 30f -> "Низкий уровень"
                        fuelPercent < 50f -> "Средний уровень"
                        else -> "Нормальный уровень"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = gaugeColor,
                    fontWeight = if (fuelPercent < 15f) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * Компонент для отображения давления в шинах в виде схемы автомобиля
 */
@Composable
fun TirePressureDisplay(tirePressureData: TirePressureData?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight() // Заполняем всю доступную высоту
            .background(
                Color.White,
                androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Давление в шинах",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Основной контент - заполняет оставшееся пространство
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            // Схема автомобиля с шинами (вид сверху, машина вертикально)
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Левая сторона
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TireItem(
                        label = "FL",
                        tire = tirePressureData?.frontLeft ?: TireData(null, null)
                    )
                    Spacer(Modifier.height(20.dp))
                    TireItem(
                        label = "RL",
                        tire = tirePressureData?.rearLeft ?: TireData(null, null)
                    )
                }

                // Машина по центру (всегда)
                Image(
                    painter = painterResource(id = R.drawable.car),
                    contentDescription = "Car image",
                    modifier = Modifier
                        .width(120.dp)
                        .fillMaxHeight()
                        .padding(vertical = 20.dp),
                    contentScale = ContentScale.Fit
                )

                // Правая сторона
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TireItem(
                        label = "FR",
                        tire = tirePressureData?.frontRight ?: TireData(null, null)
                    )
                    Spacer(Modifier.height(20.dp))
                    TireItem(
                        label = "RR",
                        tire = tirePressureData?.rearRight ?: TireData(null, null)
                    )
                }
            }
        }
    }
}

/**
 * Отображение одной шины с давлением и температурой
 */
@Composable
fun TireItem(label: String, tire: TireData, modifier: Modifier = Modifier) {
    val pressure = tire.pressure
    val temperature = tire.temperature

    // Определяем цвет в зависимости от давления (нормальное 220-250 кПа)
    val pressureColor = when {
        pressure == null -> Color.Gray
        pressure < 200 -> Color(0xFFE53935)  // Красный - низкое
        pressure > 260 -> Color(0xFFFF9800)  // Оранжевый - высокое
        else -> Color(0xFF4CAF50)            // Зеленый - нормальное
    }

    // Полное название колеса
    val fullLabel = when (label) {
        "FL" -> "Перед. Л"
        "FR" -> "Перед. П"
        "RL" -> "Зад. Л"
        "RR" -> "Зад. П"
        else -> label
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Подпись колеса
        Text(
            text = fullLabel,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFB0BEC5), // Более светлый серый для читаемости
            fontWeight = FontWeight.Medium
        )

        // Значения давления и температуры
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = if (pressure != null) "${pressure}кПа" else "—",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = pressureColor
            )
            Text(
                text = if (temperature != null) "${temperature}°C" else "—",
                style = MaterialTheme.typography.bodySmall,
                color = if (temperature != null) Color.White else Color(0xFFB0BEC5)
            )
        }
    }
}

@Composable
fun ModeCard(mode: String, onModeChange: (String) -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Режим работы",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeOption("Выключен", "off", mode, onModeChange, Icons.Default.Close)
                ModeOption("Только водитель", "driver", mode, onModeChange, Icons.Default.Person)
                ModeOption("Только пассажир", "passenger", mode, onModeChange, Icons.Default.Person)
                ModeOption("Оба сиденья", "both", mode, onModeChange, Icons.Default.Favorite)
            }
        }
    }
}

@Composable
fun ModeOption(
    label: String,
    value: String,
    currentMode: String,
    onModeChange: (String) -> Unit,
    icon: ImageVector
) {
    FilterChip(
        selected = currentMode == value,
        onClick = { onModeChange(value) },
        label = { Text(label) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun HeatLevelCard(heatLevel: Int, enabled: Boolean = true, onHeatLevelChange: (Int) -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .alpha(if (enabled) 1f else 0.5f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(
                    "Мощность обогрева (фиксированная)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (!enabled) {
                    Text(
                        "Отключено (используется адаптивный режим)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (1..3).forEach { level ->
                    FilterChip(
                        selected = heatLevel == level,
                        onClick = { if (enabled) onHeatLevelChange(level) },
                        label = { Text("Уровень $level") },
                        modifier = Modifier.weight(1f),
                        enabled = enabled
                    )
                }
            }
        }
    }
}

@Composable
fun AdaptiveHeatingCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Адаптивный подогрев",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Автоматически выбирает мощность по температуре",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange
                )
            }

            if (enabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Правила адаптивного обогрева:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "• Температура ≥ 10°C — обогрев отключен",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "• Температура < 10°C — уровень 1",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "• Температура < 5°C — уровень 2",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "• Температура ≤ 0°C — уровень 3",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun TemperatureThresholdCard(
    enabled: Boolean,
    threshold: Float,
    adaptiveEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onThresholdChange: (Float) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Температурный порог",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (adaptiveEnabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (adaptiveEnabled) "Отключено: используется адаптивный режим"
                        else "Включать только при низкой температуре",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (adaptiveEnabled) MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = !adaptiveEnabled
                )
            }

            if (enabled && !adaptiveEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Порог: ниже ${threshold.toInt()}°C",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = threshold,
                        onValueChange = onThresholdChange,
                        valueRange = 0f..15f,
                        steps = 14
                    )
                }
            }
        }
    }
}

@Composable
fun DiagnosticsTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var driveModeServiceStatus by remember { mutableStateOf(false) }
    var autoHeatServiceStatus by remember { mutableStateOf(false) }
    var vehicleMetricsServiceStatus by remember { mutableStateOf(false) }

    // Обновляем статусы сервисов каждую секунду
    LaunchedEffect(Unit) {
        while (true) {
            driveModeServiceStatus = DriveModeService.getServiceStatus()
            autoHeatServiceStatus = AutoSeatHeatService.isServiceRunning()
            vehicleMetricsServiceStatus = VehicleMetricsService.isServiceRunning()
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Диагностика и тестирование",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // Статусы сервисов
        Text(
            "Статус сервисов",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Статус DriveModeService
            ElevatedCard(
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Статус-кружочек
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = if (driveModeServiceStatus) Color(0xFF4CAF50) else Color(
                                        0xFFE53935
                                    ),
                                    shape = CircleShape
                                )
                        )
                        Text(
                            "Режимы вождения",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (!driveModeServiceStatus) {
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    DriveModeService.restartService(ctx)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Перезапустить", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Статус AutoSeatHeatService
            ElevatedCard(
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Статус-кружочек
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = if (autoHeatServiceStatus) Color(0xFF4CAF50) else Color(
                                        0xFFE53935
                                    ),
                                    shape = CircleShape
                                )
                        )
                        Text(
                            "Автоподогрев",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (!autoHeatServiceStatus) {
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    AutoSeatHeatService.restartService(ctx)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Перезапустить", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Статус VehicleMetricsService
            ElevatedCard(
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Статус-кружочек
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = if (vehicleMetricsServiceStatus) Color(0xFF4CAF50) else Color(
                                        0xFFE53935
                                    ),
                                    shape = CircleShape
                                )
                        )
                        Text(
                            "Параметры авто",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (!vehicleMetricsServiceStatus) {
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    VehicleMetricsService.restartService(ctx)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Перезапустить", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Тесты свойств
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Тесты свойств ECARX",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                FilledTonalButton(
                    onClick = {
                        Toast.makeText(
                            ctx,
                            "Диагностика Топлива!\nПроверь консоль",
                            Toast.LENGTH_LONG
                        ).show()
                        scope.launch(Dispatchers.IO) {
                            AutoSeatHeatService.diagnosticTemperaturesAndRPM()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Диагностика ТОПЛИВА")
                }
            }
        }
    }
}

@Composable
fun SettingsTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = ctx.getSharedPreferences("drivemode_prefs", Context.MODE_PRIVATE)

    var demoModeEnabled by remember { mutableStateOf(prefs.getBoolean("demo_mode_enabled", true)) }

    // Состояния разрешений
    var overlayGranted by remember { mutableStateOf<Boolean?>(null) }
    var batteryOptimized by remember { mutableStateOf<Boolean?>(null) }
    var serviceRunning by remember { mutableStateOf(false) }

    // Проверяем разрешения при запуске и каждые 2 секунды
    LaunchedEffect(Unit) {
        while (true) {
            overlayGranted = Settings.canDrawOverlays(ctx)
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
            batteryOptimized = pm?.isIgnoringBatteryOptimizations(ctx.packageName) == false
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

        // Demo Mode
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Demo Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (demoModeEnabled) "AOSP возвращает тестовые значения" else "Читаем реальные данные",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (demoModeEnabled)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary
                        )
                    }
                    Switch(
                        checked = demoModeEnabled,
                        onCheckedChange = { enabled ->
                            // TODO: Implement demo mode toggle via root
                            demoModeEnabled = enabled
                            prefs.edit().putBoolean("demo_mode_enabled", enabled).apply()
                            Toast.makeText(
                                ctx,
                                "Demo Mode ${if (enabled) "включен" else "выключен"}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
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

@Composable
fun ConsoleTab() {
    val scope = rememberCoroutineScope()
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }

    // Автоматическое обновление логов каждые 500мс
    LaunchedEffect(Unit) {
        scope.launch {
            while (isActive) {
                lines = DriveModeService.consoleSnapshot()
                delay(500)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Консоль",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        ElevatedCard(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1E1E))
                    .padding(12.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = false
                ) {
                    itemsIndexed(lines) { _, line ->
                        Text(
                            text = line,
                            color = Color(0xFFE0E0E0),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
