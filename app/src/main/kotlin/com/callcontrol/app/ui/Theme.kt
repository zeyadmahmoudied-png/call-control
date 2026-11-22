package com.callcontrol.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.callcontrol.domain.Mode
import com.callcontrol.storage.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6F78),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDEBEE),
    onPrimaryContainer = Color(0xFF00363B),
    secondary = Color(0xFF4A6366),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE7EA),
    onSecondaryContainer = Color(0xFF051F22),
    background = Color(0xFFF1F6F6),
    onBackground = Color(0xFF191C1D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C1D),
    surfaceVariant = Color(0xFFDBE4E5),
    onSurfaceVariant = Color(0xFF3F4849),
    outline = Color(0xFF6F797A),
    outlineVariant = Color(0xFFBFC8C9),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF84D2DB),
    onPrimary = Color(0xFF00363B),
    primaryContainer = Color(0xFF004F56),
    onPrimaryContainer = Color(0xFFA0EEF8),
    secondary = Color(0xFFB1CBCE),
    onSecondary = Color(0xFF1C3437),
    secondaryContainer = Color(0xFF334B4E),
    onSecondaryContainer = Color(0xFFCDE7EA),
    background = Color(0xFF0B1011),
    onBackground = Color(0xFFDEE3E3),
    surface = Color(0xFF161D1E),
    onSurface = Color(0xFFDEE3E3),
    surfaceVariant = Color(0xFF3F4849),
    onSurfaceVariant = Color(0xFFBFC8C9),
    outline = Color(0xFF899394),
    outlineVariant = Color(0xFF3F4849),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

/** Accent per state. The state is always also shown with an icon and its name, never by color alone. */
fun modeAccent(mode: Mode): Color = when (mode) {
    Mode.AVAILABLE -> Color(0xFF2F9E66)
    Mode.BUSY -> Color(0xFFD9534F)
    Mode.UNAVAILABLE -> Color(0xFF7F6CC0)
    Mode.SILENT -> Color(0xFFC98A1C)
}

@Composable
fun CallControlTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = AppShapes,
        content = content,
    )
}
