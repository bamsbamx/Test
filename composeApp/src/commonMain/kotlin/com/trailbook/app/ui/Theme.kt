package com.trailbook.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GreenPrimary = Color(0xFF2E6B34)
private val GreenSecondary = Color(0xFF517E5A)
private val AmberAccent = Color(0xFFB0791F)

private val LightColors: ColorScheme = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F0B7),
    onPrimaryContainer = Color(0xFF002104),
    secondary = GreenSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E8D4),
    onSecondaryContainer = Color(0xFF0F1F13),
    tertiary = AmberAccent,
    onTertiary = Color.White,
    background = Color(0xFFFBFDF7),
    surface = Color(0xFFFBFDF7),
    surfaceVariant = Color(0xFFDEE5D9),
    onSurfaceVariant = Color(0xFF424940),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF99D49C),
    onPrimary = Color(0xFF003911),
    primaryContainer = Color(0xFF1D5226),
    onPrimaryContainer = Color(0xFFB4F0B7),
    secondary = Color(0xFFB7CCB9),
    onSecondary = Color(0xFF223527),
    secondaryContainer = Color(0xFF394B3C),
    onSecondaryContainer = Color(0xFFD3E8D4),
    tertiary = Color(0xFFE5C36D),
    onTertiary = Color(0xFF422F00),
    background = Color(0xFF10140F),
    surface = Color(0xFF10140F),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC2C9BD),
)

@Composable
fun TrailBookTheme(themeMode: String, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
