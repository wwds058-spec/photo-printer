package com.photoprint.pro.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.photoprint.pro.presentation.settings.ThemeMode

private val LightColors = lightColorScheme(
    primary = Blue600,
    onPrimary = White,
    primaryContainer = Blue100,
    onPrimaryContainer = Blue900,
    secondary = Blue700,
    background = Surface50,
    onBackground = Ink900,
    surface = White,
    onSurface = Ink900,
    surfaceVariant = Color(0xFFEAEFF6),
    onSurfaceVariant = Ink600,
    outline = Ink300,
    error = ErrorRed,
)

private val DarkColors = darkColorScheme(
    primary = Blue300,
    onPrimary = Blue900,
    primaryContainer = Blue700,
    onPrimaryContainer = Blue100,
    background = Color(0xFF0E141C),
    onBackground = Color(0xFFE6EBF2),
    surface = Color(0xFF151C26),
    onSurface = Color(0xFFE6EBF2),
    surfaceVariant = Color(0xFF212B38),
    onSurfaceVariant = Color(0xFFB4BFCE),
    outline = Color(0xFF3A4757),
    error = Color(0xFFF2B8B5),
)

@Composable
fun PhotoPrintTheme(mode: ThemeMode = ThemeMode.LIGHT, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
