package com.bjornfree.drivemode.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bjornfree.drivemode.presentation.viewmodel.ConsoleViewModel
import com.bjornfree.drivemode.ui.components.PremiumButton
import com.bjornfree.drivemode.ui.components.Section
import com.bjornfree.drivemode.ui.theme.AdaptiveColors
import com.bjornfree.drivemode.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * Оптимизированный ConsoleTab
 *
 * ОПТИМИЗАЦИИ:
 * - LazyColumn для виртуализации логов (отрисовка только видимых)
 * - Автоматический scroll к последним логам
 * - Оптимизированный рендеринг с key()
 * - Кнопка очистки консоли
 *
 * ПРОИЗВОДИТЕЛЬНОСТЬ:
 * - Поддержка тысяч строк логов без тормозов
 * - Recycling view для памяти
 */
@Composable
fun ConsoleTabOptimized(viewModel: ConsoleViewModel) {
    val consoleLogs by viewModel.consoleLogs.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Автоматический scroll к последним логам при добавлении новых
    LaunchedEffect(consoleLogs.size) {
        if (consoleLogs.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(consoleLogs.size - 1)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AppTheme.Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(AppTheme.Spacing.Medium)
    ) {
        // Заголовок с кнопкой очистки
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = "Консоль (${consoleLogs.size} строк)",
                fontSize = AppTheme.Typography.HeadlineSmall.first,
                fontWeight = AppTheme.Typography.HeadlineSmall.second,
                color = AdaptiveColors.textPrimary
            )
            if (consoleLogs.isNotEmpty()) {
                PremiumButton(
                    text = "Очистить",
                    onClick = { viewModel.clearConsole() },
                    modifier = Modifier.height(36.dp)
                )
            }
        }

        // Виртуализированный список логов - используем MaterialTheme для определения темы
        val consoleBackground = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
            Color(0xFFF6F8FA)  // Светлая тема - светло-серый
        } else {
            Color(0xFF0D1117)  // Темная тема - темный терминал
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(AppTheme.Sizes.CardCornerRadius))
                .background(consoleBackground)
        ) {
            if (consoleLogs.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(AppTheme.Spacing.Large)
                ) {
                    Text(
                        text = "Консоль пуста",
                        fontSize = AppTheme.Typography.BodyMedium.first,
                        color = AppTheme.Colors.TextDisabled,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(AppTheme.Spacing.Small),
                    verticalArrangement = Arrangement.spacedBy(AppTheme.Spacing.ExtraSmall)
                ) {
                    items(
                        items = consoleLogs,
                        key = { log -> log.hashCode() }  // key для оптимизации
                    ) { log ->
                        LogLine(log = log)
                    }
                }
            }
        }
    }
}

/**
 * Строка лога
 * Оптимизирована: минимальная вложенность, monospace шрифт
 */
@Composable
private fun LogLine(log: String) {
    // Парсим цвет на основе содержимого
    val color = when {
        log.contains("ERROR") || log.contains("⚠") -> AdaptiveColors.error
        log.contains("WARNING") || log.contains("WARN") -> AdaptiveColors.warning
        log.contains("SUCCESS") || log.contains("✓") -> AdaptiveColors.success
        log.contains("INFO") || log.contains("ℹ") -> AdaptiveColors.info
        else -> AdaptiveColors.textPrimary
    }

    Text(
        text = log,
        fontSize = AppTheme.Typography.BodySmall.first,
        color = color,
        fontFamily = FontFamily.Monospace,
        lineHeight = AppTheme.Typography.BodySmall.first * 1.4
    )
}
