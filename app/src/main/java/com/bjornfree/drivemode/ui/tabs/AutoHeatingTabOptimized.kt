package com.bjornfree.drivemode.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.bjornfree.drivemode.domain.model.HeatingMode
import com.bjornfree.drivemode.presentation.viewmodel.AutoHeatingViewModel
import com.bjornfree.drivemode.ui.components.*
import com.bjornfree.drivemode.ui.theme.AdaptiveColors
import com.bjornfree.drivemode.ui.theme.AppTheme

@Composable
fun AutoHeatingTabOptimized(viewModel: AutoHeatingViewModel) {
    val heatingState by viewModel.heatingState.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()
    val tempThreshold by viewModel.temperatureThreshold.collectAsState()
    val adaptiveHeating by viewModel.adaptiveHeating.collectAsState()
    val heatingLevel by viewModel.heatingLevel.collectAsState()
    val checkTempOnceOnStartup by viewModel.checkTempOnceOnStartup.collectAsState()
    val autoOffTimer by viewModel.autoOffTimer.collectAsState()
    val temperatureSource by viewModel.temperatureSource.collectAsState()
    val cabinTemp by viewModel.cabinTemperature.collectAsState()
    val ambientTemp by viewModel.ambientTemperature.collectAsState()

    val availableModes = viewModel.getAvailableModes()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(AppTheme.Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(AppTheme.Spacing.Medium)
    ) {
        PremiumCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (heatingState.isActive) "ПОДОГРЕВ АКТИВЕН" else "ПОДОГРЕВ ВЫКЛЮЧЕН",
                        fontSize = AppTheme.Typography.HeadlineMedium.first,
                        fontWeight = FontWeight.Bold,
                        color = if (heatingState.isActive) {
                            AdaptiveColors.success
                        } else {
                            AdaptiveColors.textSecondary
                        }
                    )

                    Spacer(modifier = Modifier.height(AppTheme.Spacing.Small))

                    heatingState.currentTemp?.let { temp ->
                        Text(
                            text = "Температура: ${temp.toInt()}°C",
                            fontSize = AppTheme.Typography.BodyLarge.first,
                            color = AdaptiveColors.textPrimary
                        )
                    }

                    heatingState.reason?.let { reason ->
                        Text(
                            text = reason,
                            fontSize = AppTheme.Typography.BodyMedium.first,
                            color = AdaptiveColors.textSecondary
                        )
                    }
                }

                StatusIndicator(
                    isActive = heatingState.isActive,
                    activeText = "ВКЛ",
                    inactiveText = "ВЫКЛ"
                )
            }
        }

        Section(title = "Настройки") {
            PremiumCard {
                Column(
                    verticalArrangement = Arrangement.spacedBy(AppTheme.Spacing.Small)
                ) {
                    ModeSelector(
                        modes = availableModes,
                        selectedMode = currentMode,
                        onModeSelect = viewModel::setHeatingMode
                    )

                    PremiumDivider()

                    PremiumSwitch(
                        checked = adaptiveHeating,
                        onCheckedChange = viewModel::setAdaptiveHeating,
                        label = "Адаптивный режим",
                        subtitle = if (adaptiveHeating) {
                            "Автоматический выбор уровня по температуре"
                        } else {
                            "Фиксированный уровень подогрева"
                        }
                    )

                    PremiumDivider()

                    PremiumSwitch(
                        checked = checkTempOnceOnStartup,
                        onCheckedChange = viewModel::setCheckTempOnceOnStartup,
                        label = "Проверка только при запуске",
                        subtitle = if (checkTempOnceOnStartup) {
                            "Подогрев включается/выключается один раз при запуске двигателя"
                        } else {
                            "Постоянный мониторинг температуры"
                        }
                    )

                    PremiumDivider()

                    TemperatureSourceSelector(
                        source = temperatureSource,
                        cabinTemp = cabinTemp,
                        ambientTemp = ambientTemp,
                        onSourceChange = viewModel::setTemperatureSource
                    )

                    PremiumDivider()

                    AutoOffTimerSelector(
                        timerMinutes = autoOffTimer,
                        onTimerChange = viewModel::setAutoOffTimer
                    )

                    if (!adaptiveHeating) {
                        PremiumDivider()

                        PremiumSlider(
                            value = tempThreshold.toFloat(),
                            onValueChange = { viewModel.setTemperatureThreshold(it.toInt()) },
                            label = "Температурный порог",
                            valueRange = 0f..30f,
                            valueLabel = "${tempThreshold}°C"
                        )

                        PremiumDivider()

                        HeatingLevelSelector(
                            level = heatingLevel,
                            onLevelChange = viewModel::setHeatingLevel
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeSelector(
    modes: List<HeatingMode>,
    selectedMode: HeatingMode,
    onModeSelect: (HeatingMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.Spacing.Small)) {
        Text(
            text = "Режим работы",
            fontSize = AppTheme.Typography.BodyLarge.first,
            fontWeight = FontWeight.Medium,
            color = AdaptiveColors.textPrimary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.Spacing.Small)
        ) {
            modes.forEach { mode ->
                FilterChip(
                    selected = mode == selectedMode,
                    onClick = { onModeSelect(mode) },
                    label = { Text(mode.displayName) },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AdaptiveColors.primary,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
    }
}

@Composable
private fun HeatingLevelSelector(
    level: Int,
    onLevelChange: (Int) -> Unit
) {
    val levelNames = listOf("Выкл", "Низкий", "Средний", "Высокий")

    PremiumSlider(
        value = level.toFloat(),
        onValueChange = { onLevelChange(it.toInt()) },
        label = "Уровень подогрева",
        valueRange = 0f..3f,
        steps = 2,
        valueLabel = levelNames.getOrElse(level) { "?" }
    )
}

@Composable
private fun TemperatureSourceSelector(
    source: String,
    cabinTemp: Float?,
    ambientTemp: Float?,
    onSourceChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.Spacing.Small)) {
        Text(
            text = "Источник температуры",
            fontSize = AppTheme.Typography.BodyLarge.first,
            fontWeight = FontWeight.Medium,
            color = AdaptiveColors.textPrimary
        )

        Text(
            text = "Какую температуру использовать для условия включения подогрева",
            fontSize = AppTheme.Typography.BodySmall.first,
            color = AdaptiveColors.textSecondary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.Spacing.Small)
        ) {
            FilterChip(
                selected = source == "cabin",
                onClick = { onSourceChange("cabin") },
                label = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("В салоне")
                        cabinTemp?.let { temp ->
                            Text(
                                text = "${temp.toInt()}°C",
                                fontSize = AppTheme.Typography.LabelSmall.first,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AdaptiveColors.primary,
                    selectedLabelColor = Color.White
                )
            )

            FilterChip(
                selected = source == "ambient",
                onClick = { onSourceChange("ambient") },
                label = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Наружная")
                        ambientTemp?.let { temp ->
                            Text(
                                text = "${temp.toInt()}°C",
                                fontSize = AppTheme.Typography.LabelSmall.first,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AdaptiveColors.primary,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
private fun AutoOffTimerSelector(
    timerMinutes: Int,
    onTimerChange: (Int) -> Unit
) {
    val timerLabel = if (timerMinutes == 0) {
        "Всегда работает"
    } else {
        "$timerMinutes мин"
    }

    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.Spacing.Small)) {
        PremiumSlider(
            value = timerMinutes.toFloat(),
            onValueChange = { onTimerChange(it.toInt()) },
            label = "Автоотключение подогрева",
            valueRange = 0f..20f,
            valueLabel = timerLabel
        )

        Text(
            text = if (timerMinutes == 0) {
                "Подогрев работает пока включено зажигание"
            } else {
                "Подогрев автоматически отключится через $timerMinutes мин после активации"
            },
            fontSize = AppTheme.Typography.BodySmall.first,
            color = AdaptiveColors.textSecondary
        )
    }
}