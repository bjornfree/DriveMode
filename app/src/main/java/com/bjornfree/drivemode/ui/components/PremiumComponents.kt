package com.bjornfree.drivemode.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bjornfree.drivemode.ui.theme.AdaptiveColors
import com.bjornfree.drivemode.ui.theme.AppTheme

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
            defaultElevation = if (isDark) 2.dp else 4.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(AppTheme.Spacing.Medium),
            content = content
        )
    }
}

@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.Sizes.CardCornerRadius))
            .then(
                if (isDark) {
                    Modifier.background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF1A1F2E),
                                Color(0xFF252B3D)
                            )
                        )
                    )
                } else {
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
            Text(
                text = value,
                fontSize = AppTheme.Typography.DisplayMedium.first,
                fontWeight = FontWeight.Bold,
                color = color
            )

            if (unit.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = unit,
                    fontSize = AppTheme.Typography.BodyLarge.first,
                    fontWeight = FontWeight.Medium,
                    color = AdaptiveColors.textPrimary.copy(alpha = 0.85f)
                )
            }
        }

        Spacer(modifier = Modifier.height(AppTheme.Spacing.Small))

        Text(
            text = label,
            fontSize = AppTheme.Typography.BodyMedium.first,
            fontWeight = FontWeight.Medium,
            color = AdaptiveColors.textPrimary.copy(alpha = 0.7f)
        )
    }
}

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

@Composable
fun StatusIndicator(
    isActive: Boolean,
    activeText: String,
    inactiveText: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "status_pulse")
    val animatedAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    val dotAlpha = if (isActive) animatedAlpha else 1f

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(AppTheme.Sizes.ButtonCornerRadius))
            .background(
                if (isActive) AppTheme.Colors.Success.copy(alpha = 0.2f)
                else AppTheme.Colors.TextDisabled.copy(alpha = 0.2f)
            )
            .padding(
                horizontal = AppTheme.Spacing.Medium,
                vertical = AppTheme.Spacing.Small
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.Spacing.Small)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (isActive) AppTheme.Colors.Success.copy(alpha = dotAlpha)
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

@Composable
fun PremiumDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = AppTheme.Sizes.DividerThickness,
        color = AdaptiveColors.divider
    )
}

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