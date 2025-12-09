package com.bjornfree.drivemode.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.bjornfree.drivemode.presentation.viewmodel.DiagnosticsViewModel
import com.bjornfree.drivemode.ui.components.*
import com.bjornfree.drivemode.ui.theme.AdaptiveColors
import com.bjornfree.drivemode.ui.theme.AppTheme

/**
 * Оптимизированный DiagnosticsTab
 *
 * ОПТИМИЗАЦИИ:
 * - LazyColumn вместо Column + verticalScroll
 * - Виртуализация для больших списков диагностических данных
 * - Оптимизированный рендеринг с key()
 *
 * ПРОИЗВОДИТЕЛЬНОСТЬ:
 * - Отрисовывает только видимые элементы
 * - Автоматический recycling для длинных списков
 */
@Composable
fun DiagnosticsTabOptimized(viewModel: DiagnosticsViewModel) {
    val carManagerStatus by viewModel.carManagerStatus.collectAsState()
    val metricsUpdateCount by viewModel.metricsUpdateCount.collectAsState()
    val diagnosticMessages by viewModel.diagnosticMessages.collectAsState()

    val isCarConnected = carManagerStatus == com.bjornfree.drivemode.presentation.viewmodel.ServiceStatus.Running

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(AppTheme.Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(AppTheme.Spacing.Medium)
    ) {
        // ============ СТАТУС ПОДКЛЮЧЕНИЯ ============
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
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = if (isCarConnected) AdaptiveColors.success else AdaptiveColors.error
                        )

                        if (isCarConnected) {
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(AppTheme.Spacing.Small))
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

        // ============ ДИАГНОСТИЧЕСКИЕ СООБЩЕНИЯ ============
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
            // Виртуализированный список сообщений
            items(
                items = diagnosticMessages,
                key = { it.hashCode() }
            ) { message ->
                DiagnosticMessageCard(message = message)
            }
        }
    }
}

/**
 * Карточка диагностического сообщения
 */
@Composable
private fun DiagnosticMessageCard(message: String) {
    PremiumCard {
        Text(
            text = message,
            fontSize = AppTheme.Typography.BodySmall.first,
            color = AdaptiveColors.textPrimary,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}
