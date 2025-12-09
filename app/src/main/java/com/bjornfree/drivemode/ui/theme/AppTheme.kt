package com.bjornfree.drivemode.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Премиальная тема приложения - цвета, типографика, spacing
 *
 * Философия: Минимализм, премиальность, технологичность
 * - Темная база с синими акцентами
 * - Четкая иерархия через контраст
 * - Плавные градиенты
 */
object AppTheme {

    // ============ ЦВЕТА ============

    object Colors {
        // Primary - фирменный синий
        val Primary = Color(0xFF1C69D4)
        val PrimaryDark = Color(0xFF0F4C9C)
        val PrimaryLight = Color(0xFF4A8FFF)

        // ТЕМНАЯ ТЕМА
        // Background - темная премиальная база
        val BackgroundDark = Color(0xFF0A0E14)
        val SurfaceDark = Color(0xFF141A24)
        val CardBackgroundDark = Color(0xFF1A2332)

        // Text - темная тема
        val TextPrimaryDark = Color(0xFFFFFFFF)
        val TextSecondaryDark = Color(0xFFB0B8C8)
        val TextDisabledDark = Color(0xFF666E7E)

        // Divider - темная тема
        val DividerDark = Color(0xFF2A3447)

        // СВЕТЛАЯ ТЕМА
        // Background - светлая премиальная база
        val BackgroundLight = Color(0xFFF5F7FA)
        val SurfaceLight = Color(0xFFFFFFFF)
        val CardBackgroundLight = Color(0xFFFFFFFF)

        // Text - светлая тема
        val TextPrimaryLight = Color(0xFF1A1F2E)
        val TextSecondaryLight = Color(0xFF5A6577)
        val TextDisabledLight = Color(0xFFB0B8C8)

        // Divider - светлая тема
        val DividerLight = Color(0xFFE5E8ED)

        // ОБЩИЕ (для обеих тем)
        // Status colors
        val Success = Color(0xFF00E676)
        val Warning = Color(0xFFFFAB00)
        val Error = Color(0xFFFF5252)
        val Info = Color(0xFF40C4FF)

        // Gradient
        val GradientStart = Color(0xFF1C69D4)
        val GradientEnd = Color(0xFF0F4C9C)

        // Overlay
        val Overlay = Color(0x80000000)

        // Для обратной совместимости (используют темную тему по умолчанию)
        val BackgroundDarkCompat = BackgroundDark
        val SurfaceDarkCompat = SurfaceDark
        val CardBackground = CardBackgroundDark
        val TextPrimary = TextPrimaryDark
        val TextSecondary = TextSecondaryDark
        val TextDisabled = TextDisabledDark
        val Divider = DividerDark
    }

    // ============ ТИПОГРАФИКА ============

    object Typography {
        // Display - для больших чисел (скорость, обороты)
        val DisplayLarge = 72.sp to FontWeight.Light
        val DisplayMedium = 56.sp to FontWeight.Light
        val DisplaySmall = 44.sp to FontWeight.Normal

        // Headline - заголовки карточек
        val HeadlineLarge = 32.sp to FontWeight.Medium
        val HeadlineMedium = 24.sp to FontWeight.Medium
        val HeadlineSmall = 20.sp to FontWeight.Medium

        // Body - основной текст
        val BodyLarge = 16.sp to FontWeight.Normal
        val BodyMedium = 14.sp to FontWeight.Normal
        val BodySmall = 12.sp to FontWeight.Normal

        // Label - метки и подписи
        val LabelLarge = 14.sp to FontWeight.Medium
        val LabelMedium = 12.sp to FontWeight.Medium
        val LabelSmall = 10.sp to FontWeight.Medium
    }

    // ============ SPACING ============

    object Spacing {
        val ExtraSmall = 4.dp
        val Small = 8.dp
        val Medium = 16.dp
        val Large = 24.dp
        val ExtraLarge = 32.dp
        val Huge = 48.dp
    }

    // ============ РАЗМЕРЫ ============

    object Sizes {
        // Card
        val CardElevation = 2.dp
        val CardCornerRadius = 12.dp
        val CardMinHeight = 80.dp

        // Icons
        val IconSmall = 16.dp
        val IconMedium = 24.dp
        val IconLarge = 32.dp
        val IconHuge = 48.dp

        // Buttons
        val ButtonHeight = 48.dp
        val ButtonCornerRadius = 24.dp

        // Divider
        val DividerThickness = 1.dp
    }

    // ============ АНИМАЦИЯ ============

    object Animation {
        const val DurationFast = 150
        const val DurationNormal = 300
        const val DurationSlow = 500
    }
}
