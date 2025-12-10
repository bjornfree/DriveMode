package com.bjornfree.drivemode.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.bjornfree.drivemode.presentation.viewmodel.DiagnosticsViewModel
import com.bjornfree.drivemode.presentation.viewmodel.ServiceStatus
import com.bjornfree.drivemode.ui.components.PremiumCard
import com.bjornfree.drivemode.ui.components.Section
import com.bjornfree.drivemode.ui.components.StatusIndicator
import com.bjornfree.drivemode.ui.theme.AdaptiveColors
import com.bjornfree.drivemode.ui.theme.AppTheme

@Composable
fun DiagnosticsTabOptimized(viewModel: DiagnosticsViewModel) {
    val carManagerStatus by viewModel.carManagerStatus.collectAsState()
    val metricsUpdateCount by viewModel.metricsUpdateCount.collectAsState()
    val diagnosticMessages by viewModel.diagnosticMessages.collectAsState()

    val isCarConnected = carManagerStatus == ServiceStatus.Running

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(AppTheme.Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(AppTheme.Spacing.Medium)
    ) {
        item(key = "car_status") {
            PremiumCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isCarConnected) "CAR API ПОДКЛЮЧЕН" else "CAR API ОТКЛЮЧЕН",
                            fontSize = AppTheme.Typography.HeadlineMedium.first,
                            fontWeight = FontWeight.Bold,
                            color = if (isCarConnected) AdaptiveColors.success else AdaptiveColors.error
                        )

                        if (isCarConnected) {
                            Spacer(modifier = Modifier.height(AppTheme.Spacing.Small))
                            Text(
                                text = "Обновлений: $metricsUpdateCount",
                                fontSize = AppTheme.Typography.BodyLarge.first,
                                color = AdaptiveColors.textPrimary
                            )
                        }
                    }

                    StatusIndicator(
                        isActive = isCarConnected,
                        activeText = "ВКЛ",
                        inactiveText = "ВЫКЛ"
                    )
                }
            }
        }

        item(key = "diagnostic_messages_header") {
            Section(title = "Диагностические сообщения (${diagnosticMessages.size})") {}
        }

        if (diagnosticMessages.isEmpty()) {
            item(key = "empty_messages") {
                PremiumCard {
                    Text(
                        text = "Нет диагностических сообщений",
                        fontSize = AppTheme.Typography.BodyMedium.first,
                        color = AdaptiveColors.textDisabled
                    )
                }
            }
        } else {
            items(
                items = diagnosticMessages,
                key = { it.hashCode() }
            ) { message ->
                DiagnosticMessageCard(message = message)
            }
        }
    }
}

@Composable
private fun DiagnosticMessageCard(message: String) {
    PremiumCard {
        Text(
            text = message,
            fontSize = AppTheme.Typography.BodySmall.first,
            color = AdaptiveColors.textPrimary,
            fontFamily = FontFamily.Monospace
        )
    }
}