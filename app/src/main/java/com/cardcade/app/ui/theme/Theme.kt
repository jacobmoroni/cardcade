package com.cardcade.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FeltGreen = Color(0xFF0B6A3A)
private val FeltGreenDark = Color(0xFF063F23)
private val Gold = Color(0xFFE6B54A)

private val LightColors = lightColorScheme(
    primary = FeltGreen,
    onPrimary = Color.White,
    secondary = Gold,
    background = FeltGreen,
    onBackground = Color.White,
    surface = FeltGreenDark,
    onSurface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = FeltGreen,
    onPrimary = Color.White,
    secondary = Gold,
    background = FeltGreenDark,
    onBackground = Color.White,
    surface = FeltGreenDark,
    onSurface = Color.White,
)

@Composable
fun CardcadeTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
