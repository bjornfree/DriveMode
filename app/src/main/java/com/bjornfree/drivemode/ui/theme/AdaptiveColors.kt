package com.bjornfree.drivemode.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Адаптивные цвета которые меняются в зависимости от темы (светлая/темная)
 */
object AdaptiveColors {

    /**
     * Фон приложения
     */
    val background: Color
        @Composable
        get() = MaterialTheme.colorScheme.background

    /**
     * Фон поверхности (карточки и т.д.)
     */
    val surface: Color
        @Composable
        get() = MaterialTheme.colorScheme.surfaceVariant

    /**
     * Фон карточек (используется для ElevatedCard)
     */
    val cardBackground: Color
        @Composable
        get() = MaterialTheme.colorScheme.surface

    /**
     * Основной текст
     */
    val textPrimary: Color
        @Composable
        get() = MaterialTheme.colorScheme.onBackground

    /**
     * Вторичный текст
     */
    val textSecondary: Color
        @Composable
        get() = MaterialTheme.colorScheme.onSurfaceVariant

    /**
     * Отключенный текст
     */
    val textDisabled: Color
        @Composable
        get() = if (isSystemInDarkTheme()) {
            AppTheme.Colors.TextDisabledDark
        } else {
            AppTheme.Colors.TextDisabledLight
        }

    /**
     * Разделитель
     */
    val divider: Color
        @Composable
        get() = if (isSystemInDarkTheme()) {
            AppTheme.Colors.DividerDark
        } else {
            AppTheme.Colors.DividerLight
        }

    /**
     * Основной цвет (Primary)
     */
    val primary: Color
        @Composable
        get() = MaterialTheme.colorScheme.primary

    /**
     * Цвет успеха (Success)
     */
    val success: Color
        @Composable
        get() = AppTheme.Colors.Success

    /**
     * Цвет предупреждения (Warning)
     */
    val warning: Color
        @Composable
        get() = AppTheme.Colors.Warning

    /**
     * Цвет ошибки (Error)
     */
    val error: Color
        @Composable
        get() = MaterialTheme.colorScheme.error

    /**
     * Цвет информации (Info)
     */
    val info: Color
        @Composable
        get() = AppTheme.Colors.Info
}
