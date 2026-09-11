package com.telegramreader.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Telegram-inspired palette.
val TgBlue = Color(0xFF2AABEE)
val TgBlueDeep = Color(0xFF1E88E5)
val TgIndigo = Color(0xFF5B6CFF)
val TgAmber = Color(0xFFF5A623)
val TgCoral = Color(0xFFFF6B6B)
val TgMint = Color(0xFF34D399)

private val LightScheme = lightColorScheme(
    primary = TgBlueDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6ECFF),
    onPrimaryContainer = Color(0xFF00385C),
    secondary = TgIndigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E4FF),
    onSecondaryContainer = Color(0xFF1B1F5C),
    tertiary = TgMint,
    background = Color(0xFFF2F6FA),
    onBackground = Color(0xFF13202C),
    surface = Color.White,
    onSurface = Color(0xFF13202C),
    surfaceVariant = Color(0xFFE6EDF4),
    onSurfaceVariant = Color(0xFF4E5C6A),
    outline = Color(0xFFB8C4D0),
    outlineVariant = Color(0xFFDDE5EC),
    error = Color(0xFFD93B3B),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF5EB5F7),
    onPrimary = Color(0xFF00304F),
    primaryContainer = Color(0xFF12466E),
    onPrimaryContainer = Color(0xFFCDE7FF),
    secondary = Color(0xFF9DA8FF),
    onSecondary = Color(0xFF14196B),
    secondaryContainer = Color(0xFF2B347F),
    onSecondaryContainer = Color(0xFFE2E4FF),
    tertiary = TgMint,
    background = Color(0xFF0E1621),
    onBackground = Color(0xFFE7EEF5),
    surface = Color(0xFF17212B),
    onSurface = Color(0xFFE7EEF5),
    surfaceVariant = Color(0xFF212F3D),
    onSurfaceVariant = Color(0xFFA6B4C2),
    outline = Color(0xFF4A5A6B),
    outlineVariant = Color(0xFF2C3B4A),
    error = Color(0xFFFF7A7A),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        shapes = AppShapes,
        content = content,
    )
}
