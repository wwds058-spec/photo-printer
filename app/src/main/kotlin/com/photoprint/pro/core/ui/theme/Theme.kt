package com.photoprint.pro.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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
    onSurfaceVariant = Ink600,
    outline = Ink300,
    error = ErrorRed,
)

/**
 * Light theme only for now: the product brief asks for a light background, and a print-preview
 * workspace needs a neutral, predictable canvas. A dark scheme can be added without API changes.
 */
@Composable
fun PhotoPrintTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
