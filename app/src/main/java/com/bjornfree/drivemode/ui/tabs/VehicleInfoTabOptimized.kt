package com.bjornfree.drivemode.ui.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bjornfree.drivemode.R
import com.bjornfree.drivemode.domain.model.TireData
import com.bjornfree.drivemode.domain.model.TirePressureData
import com.bjornfree.drivemode.presentation.viewmodel.VehicleInfoViewModel
import com.bjornfree.drivemode.ui.components.*
import com.bjornfree.drivemode.ui.theme.AdaptiveColors
import com.bjornfree.drivemode.ui.theme.AppTheme

@Composable
fun VehicleInfoTabOptimized(viewModel: VehicleInfoViewModel) {
    val main by viewModel.mainMetrics.collectAsState()
    val fuel by viewModel.fuelMetrics.collectAsState()
    val trip by viewModel.tripMetrics.collectAsState()
    val tires by viewModel.tireMetrics.collectAsState()
    val temps by viewModel.temperatureMetrics.collectAsState()

    val scrollState = rememberScrollState()

    val gearColor = when (main.gear) {
        "P" -> AdaptiveColors.error
        "R" -> AdaptiveColors.warning
        "N" -> AdaptiveColors.info
        "D" -> AdaptiveColors.success
        else -> AdaptiveColors.textPrimary
    }

    val rpmColor = when {
        main.rpm < 1000 -> AdaptiveColors.success
        main.rpm < 3000 -> AdaptiveColors.textPrimary
        else -> AdaptiveColors.warning
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(AppTheme.Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(AppTheme.Spacing.Medium)
    ) {
        // Ряд 1: Передача, Скорость, Обороты
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.Spacing.Medium)
        ) {
            PremiumCard(
                modifier = Modifier
                    .weight(1f)
                    .height(180.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    MetricDisplay(
                        value = main.gear.ifEmpty { "—" },
                        unit = "",
                        label = "Передача",
                        color = gearColor
                    )
                }
            }

            PremiumCard(
                modifier = Modifier
                    .weight(1f)
                    .height(180.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    MetricDisplay(
                        value = main.speed.toInt().toString(),
                        unit = "км/ч",
                        label = "Скорость",
                        color = AdaptiveColors.textPrimary
                    )
                }
            }

            PremiumCard(
                modifier = Modifier
                    .weight(1f)
                    .height(180.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    MetricDisplay(
                        value = main.rpm.toString(),
                        unit = "RPM",
                        label = "Обороты",
                        color = rpmColor
                    )
                }
            }
        }

        // Топливо + Шины
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.Spacing.Medium)
        ) {
            PremiumCard(
                modifier = Modifier
                    .weight(1f)
                    .height(320.dp)
            ) {
                FuelGaugeOptimized(
                    fuelLiters = fuel.fuel?.currentFuelLiters,
                    tankCapacity = fuel.fuel?.capacityLiters ?: 45f,
                    rangeKm = fuel.fuel?.rangeKm ?: fuel.rangeRemaining
                )
            }

            PremiumCard(
                modifier = Modifier
                    .weight(1f)
                    .height(320.dp)
            ) {
                TirePressureOptimized(tirePressure = tires.tirePressure)
            }
        }

        // Температуры
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.Spacing.Medium)
        ) {
            PremiumCard(
                modifier = Modifier
                    .weight(1f)
                    .height(180.dp)
            ) {
                TemperatureCard(
                    label = "В салоне",
                    temp = temps.cabinTemperature,
                    coldThreshold = 15f
                )
            }

            PremiumCard(
                modifier = Modifier
                    .weight(1f)
                    .height(180.dp)
            ) {
                TemperatureCard(
                    label = "Снаружи",
                    temp = temps.ambientTemperature,
                    coldThreshold = 10f
                )
            }
        }

        // Расход и пробег
        Section(title = "Расход и пробег") {
            PremiumCard {
                Column(verticalArrangement = Arrangement.spacedBy(AppTheme.Spacing.Small)) {
                    InfoRow(
                        label = "Средний расход",
                        value = fuel.averageFuel?.let { "%.1f л/100км".format(it) } ?: "—"
                    )

                    PremiumDivider()

                    InfoRow(
                        label = "Запас хода",
                        value = (fuel.fuel?.rangeKm ?: fuel.rangeRemaining)
                            ?.let { "%.0f км".format(it) } ?: "—"
                    )

                    PremiumDivider()

                    InfoRow(
                        label = "Пробег (общий)",
                        value = trip.odometer?.let { "%.0f км".format(it) } ?: "—"
                    )

                    PremiumDivider()

                    InfoRow(
                        label = "Пробег (поездка)",
                        value = trip.tripMileage?.let { "%.1f км".format(it) } ?: "—"
                    )

                    PremiumDivider()

                    InfoRow(
                        label = "Время (поездка)",
                        value = trip.tripTime?.let { formatTripTime(it * 60) } ?: "—"
                    )
                }
            }
        }
    }
}

@Composable
private fun TemperatureCard(
    label: String,
    temp: Float?,
    coldThreshold: Float
) {
    val tempColor = when {
        temp == null -> AdaptiveColors.textDisabled
        temp < coldThreshold -> AdaptiveColors.info
        temp < coldThreshold + 10 -> AdaptiveColors.success
        else -> AdaptiveColors.warning
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        MetricDisplay(
            value = temp?.toInt()?.toString() ?: "—",
            unit = "°C",
            label = label,
            color = tempColor
        )
    }
}

@Composable
private fun FuelGaugeOptimized(
    fuelLiters: Float?,
    tankCapacity: Float,
    rangeKm: Float?
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(AppTheme.Spacing.Small),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val percentage = remember(fuelLiters, tankCapacity) {
            if (fuelLiters != null && tankCapacity > 0f) {
                ((fuelLiters / tankCapacity) * 100)
                    .toInt()
                    .coerceIn(0, 100)
            } else {
                0
            }
        }

        MetricDisplay(
            value = percentage.toString(),
            unit = "%",
            label = "Топливо",
            color = when {
                percentage < 10 -> AdaptiveColors.error
                percentage < 30 -> AdaptiveColors.warning
                else -> AdaptiveColors.success
            }
        )

        PremiumDivider()

        InfoRow(
            label = "Литров",
            value = fuelLiters?.let { "%.1f / %.0f".format(it, tankCapacity) } ?: "—"
        )

        InfoRow(
            label = "Запас хода",
            value = rangeKm?.let { "%.0f км".format(it) } ?: "—"
        )
    }
}

@Composable
private fun TirePressureOptimized(tirePressure: TirePressureData?) {
    val carPainter = painterResource(R.drawable.car)

    Column(
        verticalArrangement = Arrangement.spacedBy(AppTheme.Spacing.Small),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = carPainter,
                contentDescription = "Автомобиль",
                modifier = Modifier.fillMaxWidth(0.9f),
                contentScale = ContentScale.Fit,
                alpha = 0.3f
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = AppTheme.Spacing.Medium),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TireBadge(tirePressure?.frontLeft, "FL")
                    TireBadge(tirePressure?.frontRight, "FR")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TireBadge(tirePressure?.rearLeft, "RL")
                    TireBadge(tirePressure?.rearRight, "RR")
                }
            }
        }
    }
}

@Composable
private fun TireBadge(tireData: TireData?, label: String) {
    val pressure = tireData?.pressure
    val temperature = tireData?.temperature

    val bgColor = when {
        tireData == null || pressure == null -> AdaptiveColors.textDisabled.copy(alpha = 0.3f)
        pressure < 200 -> AdaptiveColors.error.copy(alpha = 0.3f)
        pressure > 280 -> AdaptiveColors.warning.copy(alpha = 0.3f)
        else -> AdaptiveColors.success.copy(alpha = 0.3f)
    }

    val pressureColor = when {
        tireData == null || pressure == null -> AdaptiveColors.textDisabled
        pressure < 200 -> AdaptiveColors.error
        pressure > 280 -> AdaptiveColors.warning
        else -> AdaptiveColors.success
    }

    Column(
        modifier = Modifier
            .background(
                color = bgColor,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(AppTheme.Spacing.Small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            fontSize = AppTheme.Typography.LabelSmall.first,
            fontWeight = AppTheme.Typography.LabelSmall.second,
            color = AdaptiveColors.textSecondary
        )

        Text(
            text = pressure?.toString() ?: "—",
            fontSize = AppTheme.Typography.BodyLarge.first,
            fontWeight = FontWeight.Bold,
            color = pressureColor
        )

        Text(
            text = temperature?.let { "${it}°C" } ?: "—",
            fontSize = AppTheme.Typography.LabelSmall.first,
            color = AdaptiveColors.textSecondary
        )
    }
}

private fun formatTripTime(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return "%02d:%02d".format(hours, minutes)
}