package com.bjornfree.drivemode.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bjornfree.drivemode.ui.theme.AdaptiveColors
import com.bjornfree.drivemode.ui.theme.AppTheme

/**
 * Премиальные компоненты для оптимизированного UI
 * - Минималистичный дизайн
 * - Переиспользуемые элементы
 * - Оптимизированный рендеринг
 */

/**
 * Премиальная карточка (адаптивная для светлой/темной темы)
 * BMW стиль: на светлой теме - белая с тенью, на темной - темная с легкой тенью
 */
@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = AdaptiveColors.cardBackground
        ),
        shape = RoundedCornerShape(AppTheme.Sizes.CardCornerRadius),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDark) 2.dp else 4.dp  // Больше тени на светлой теме
        )
    ) {
        Column(
            modifier = Modifier.padding(AppTheme.Spacing.Medium),
            content = content
        )
    }
}

/**
 * Карточка с градиентом для акцентов (адаптивная для светлой/темной темы)
 * BMW стиль: темно-серый градиент на темной, светло-серый с синей обводкой на светлой
 */
@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.Sizes.CardCornerRadius))
            .then(
                if (isDark) {
                    // Темная тема: темно-серый градиент (для контраста с белым текстом)
                    Modifier.background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF1A1F2E),  // Темно-серый
                                Color(0xFF252B3D)   // Чуть светлее темно-серый
                            )
                        )
                    )
                } else {
                    // Светлая тема: белый фон с синей обводкой
                    Modifier
                        .background(Color.White)
                        .border(
                            width = 2.dp,
                            color = AppTheme.Colors.Primary,
                            shape = RoundedCornerShape(AppTheme.Sizes.CardCornerRadius)
                        )
                }
            )
    ) {
        Column(
            modifier = Modifier.padding(AppTheme.Spacing.Medium),
            content = content
        )
    }
}

/**
 * Метрика с большим числом (скорость, обороты, температура)
 * Оптимизирована: использует remember для избежания recomposition
 * BMW стиль: всегда белый/яркий текст для лучшей читаемости
 */
@Composable
fun MetricDisplay(
    value: String,
    unit: String,
    label: String,
    modifier: Modifier = Modifier,
    color: Color = AdaptiveColors.textPrimary
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            // Большое число - всегда яркое для читаемости
            Text(
                text = value,
                fontSize = AppTheme.Typography.DisplayMedium.first,
                fontWeight = FontWeight.Bold,
                color = color
            )

            if (unit.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))

                // Единица измерения рядом с значением
                Text(
                    text = unit,
                    fontSize = AppTheme.Typography.BodyLarge.first,
                    fontWeight = FontWeight.Medium,
                    color = AdaptiveColors.textPrimary.copy(alpha = 0.85f)
                )
            }
        }

        Spacer(modifier = Modifier.height(AppTheme.Spacing.Small))

        // Метка - увеличенный размер для читаемости
        Text(
            text = label,
            fontSize = AppTheme.Typography.BodyMedium.first,
            fontWeight = FontWeight.Medium,
            color = AdaptiveColors.textPrimary.copy(alpha = 0.7f)
        )
    }
}

/**
 * Компактная метрика для мелких показаний
 * Улучшенная читаемость: увеличенный размер шрифта и вес
 */
@Composable
fun CompactMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AppTheme.Spacing.ExtraSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.Spacing.Small)
        ) {
            icon?.invoke()
            Text(
                text = label,
                fontSize = AppTheme.Typography.BodyLarge.first,
                fontWeight = FontWeight.Medium,
                color = AdaptiveColors.textPrimary.copy(alpha = 0.75f)
            )
        }

        Text(
            text = value,
            fontSize = AppTheme.Typography.HeadlineSmall.first,
            fontWeight = FontWeight.Bold,
            color = AdaptiveColors.textPrimary
        )
    }
}

/**
 * Премиальная кнопка с градиентом
 */
@Composable
fun PremiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .height(AppTheme.Sizes.ButtonHeight)
            .clip(RoundedCornerShape(AppTheme.Sizes.ButtonCornerRadius))
            .background(
                if (enabled) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            AppTheme.Colors.Primary,
                            AppTheme.Colors.PrimaryDark
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(
                            AdaptiveColors.textDisabled,
                            AdaptiveColors.textDisabled
                        )
                    )
                }
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = AppTheme.Typography.LabelLarge.first,
            fontWeight = AppTheme.Typography.LabelLarge.second,
            color = Color.White
        )
    }
}

/**
 * Премиальный переключатель (Switch)
 */
@Composable
fun PremiumSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = AppTheme.Spacing.Small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = AppTheme.Typography.BodyLarge.first,
                fontWeight = FontWeight.Medium,
                color = AdaptiveColors.textPrimary
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = AppTheme.Typography.BodySmall.first,
                    color = AdaptiveColors.textSecondary
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AdaptiveColors.primary,
                checkedTrackColor = AppTheme.Colors.PrimaryLight,
                uncheckedThumbColor = AppTheme.Colors.TextDisabled,
                uncheckedTrackColor = AdaptiveColors.divider
            )
        )
    }
}

/**
 * Премиальный слайдер
 */
@Composable
fun PremiumSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    valueLabel: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = AppTheme.Typography.BodyLarge.first,
                fontWeight = FontWeight.Medium,
                color = AdaptiveColors.textPrimary
            )
            Text(
                text = valueLabel,
                fontSize = AppTheme.Typography.BodyLarge.first,
                fontWeight = FontWeight.Bold,
                color = AdaptiveColors.primary
            )
        }

        Spacer(modifier = Modifier.height(AppTheme.Spacing.Small))

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = AdaptiveColors.primary,
                activeTrackColor = AdaptiveColors.primary,
                inactiveTrackColor = AdaptiveColors.divider
            )
        )
    }
}

/**
 * Индикатор состояния (активен/неактивен)
 */
@Composable
fun StatusIndicator(
    isActive: Boolean,
    activeText: String,
    inactiveText: String,
    modifier: Modifier = Modifier
) {
    // Анимированное пульсирование для активного состояния
    val infiniteTransition = rememberInfiniteTransition(label = "status_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(AppTheme.Sizes.ButtonCornerRadius))
            .background(
                if (isActive) AppTheme.Colors.Success.copy(alpha = 0.2f)
                else AppTheme.Colors.TextDisabled.copy(alpha = 0.2f)
            )
            .padding(horizontal = AppTheme.Spacing.Medium, vertical = AppTheme.Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.Spacing.Small)
    ) {
        // Индикатор точка
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (isActive) AppTheme.Colors.Success.copy(alpha = if (isActive) alpha else 1f)
                    else AppTheme.Colors.TextDisabled
                )
        )

        Text(
            text = if (isActive) activeText else inactiveText,
            fontSize = AppTheme.Typography.LabelMedium.first,
            fontWeight = FontWeight.Medium,
            color = if (isActive) AppTheme.Colors.Success else AppTheme.Colors.TextDisabled
        )
    }
}

/**
 * Разделитель (адаптивный для светлой/темной темы)
 */
@Composable
fun PremiumDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = AppTheme.Sizes.DividerThickness,
        color = AdaptiveColors.divider
    )
}

/**
 * Секция с заголовком
 */
@Composable
fun Section(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = AppTheme.Typography.HeadlineSmall.first,
                fontWeight = AppTheme.Typography.HeadlineSmall.second,
                color = AdaptiveColors.textPrimary
            )
            action?.invoke()
        }

        Spacer(modifier = Modifier.height(AppTheme.Spacing.Medium))

        content()
    }
}

/**
 * Информационная строка (ключ: значение)
 * Улучшенная читаемость: увеличенный размер шрифта и контраст
 */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AppTheme.Spacing.Small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = AppTheme.Typography.BodyLarge.first,
            fontWeight = FontWeight.Medium,
            color = AdaptiveColors.textPrimary.copy(alpha = 0.75f)
        )
        Text(
            text = value,
            fontSize = AppTheme.Typography.BodyLarge.first,
            fontWeight = FontWeight.SemiBold,
            color = AdaptiveColors.textPrimary
        )
    }
}
