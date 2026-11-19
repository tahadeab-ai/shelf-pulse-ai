package com.shelfpulse.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Emerald500,
    onPrimary = Slate900,
    primaryContainer = Slate800,
    onPrimaryContainer = Emerald400,
    secondary = Cyan500,
    onSecondary = Slate900,
    secondaryContainer = Slate700,
    onSecondaryContainer = Cyan400,
    tertiary = Sky400,
    background = Slate900,
    onBackground = Slate100,
    surface = Slate800,
    onSurface = Slate100,
    surfaceVariant = Slate700,
    onSurfaceVariant = Slate400,
    error = Rose500
)

@Composable
fun ShelfPulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = ShelfPulseTypography,
        content = content
    )
}
