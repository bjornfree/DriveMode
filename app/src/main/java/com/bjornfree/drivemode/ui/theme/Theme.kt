package com.bjornfree.drivemode.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.bjornfree.drivemode.data.preferences.PreferencesManager
import kotlinx.coroutines.delay

/**
 * Премиальная темная тема
 * - Темный фон с синими акцентами
 * - Высокий контраст для читаемости
 * - Минималистичный дизайн
 */
private val PremiumDarkColorScheme = darkColorScheme(
    // Primary - фирменный синий
    primary = AppTheme.Colors.Primary,
    onPrimary = Color.White,
    primaryContainer = AppTheme.Colors.PrimaryDark,
    onPrimaryContainer = Color.White,

    // Secondary
    secondary = AppTheme.Colors.Info,
    onSecondary = Color.White,

    // Tertiary
    tertiary = AppTheme.Colors.Success,
    onTertiary = Color.White,

    // Background
    background = AppTheme.Colors.BackgroundDark,
    onBackground = AppTheme.Colors.TextPrimaryDark,

    // Surface - темный фон для всех поверхностей
    surface = AppTheme.Colors.CardBackgroundDark,
    onSurface = AppTheme.Colors.TextPrimaryDark,
    surfaceVariant = AppTheme.Colors.SurfaceDark,
    onSurfaceVariant = AppTheme.Colors.TextSecondaryDark,

    // Surface containers - для ElevatedCard
    surfaceContainer = AppTheme.Colors.CardBackgroundDark,
    surfaceContainerHigh = AppTheme.Colors.CardBackgroundDark,
    surfaceContainerHighest = AppTheme.Colors.CardBackgroundDark,

    // Error
    error = AppTheme.Colors.Error,
    onError = Color.White
)

/**
 * Премиальная светлая тема
 * - Светлый фон с синими акцентами
 * - Четкая читаемость
 * - Минималистичный дизайн
 */
private val PremiumLightColorScheme = lightColorScheme(
    // Primary - фирменный синий
    primary = AppTheme.Colors.Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F2FD),  // Очень светлый синий
    onPrimaryContainer = AppTheme.Colors.Primary,

    // Secondary
    secondary = AppTheme.Colors.Info,
    onSecondary = Color.White,

    // Tertiary
    tertiary = AppTheme.Colors.Success,
    onTertiary = Color.White,

    // Background - светло-серый BMW стиль
    background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF1A1F2E),

    // Surface - белые карточки
    surface = Color.White,
    onSurface = Color(0xFF1A1F2E),
    surfaceVariant = Color.White,  // Карточки белые
    onSurfaceVariant = Color(0xFF5A6577),

    // Error
    error = AppTheme.Colors.Error,
    onError = Color.White
)

@Composable
fun DriveModeTheme(
    // Отключаем dynamic color чтобы использовать нашу премиальную тему
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Читаем настройку темы из PreferencesManager
    val context = LocalContext.current
    val prefsManager = remember { PreferencesManager(context) }
    var themeMode by remember { mutableStateOf(prefsManager.themeMode) }

    // Слушаем изменения темы через LaunchedEffect
    LaunchedEffect(Unit) {
        while (true) {
            delay(100) // Проверяем каждые 100ms
            val currentMode = prefsManager.themeMode
            if (currentMode != themeMode) {
                themeMode = currentMode
            }
        }
    }

    // Определяем темную ли тема на основе режима
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()  // "auto" - следуем системе
    }

    val colorScheme = if (darkTheme) PremiumDarkColorScheme else PremiumLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val statusBarColor = if (darkTheme) {
                AppTheme.Colors.BackgroundDark.toArgb()
            } else {
                AppTheme.Colors.BackgroundLight.toArgb()
            }
            window.statusBarColor = statusBarColor
            window.navigationBarColor = statusBarColor
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}