package de.aniworld.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgPrimary = Color(0xFF090C13)
val BgSurface = Color(0xFF131824)
val BgElevated = Color(0xFF1C2436)
val AccentCyan = Color(0xFF00E5FF)
val AccentBlue = Color(0xFF2979FF)
val AccentEmerald = Color(0xFF00E676)
val TextPrimary = Color(0xFFF0F4FC)
val TextSecondary = Color(0xFF9AA5BC)
val TextMuted = Color(0xFF657189)

private val DarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = Color(0xFF050811),
    secondary = AccentBlue,
    onSecondary = Color.White,
    background = BgPrimary,
    onBackground = TextPrimary,
    surface = BgSurface,
    onSurface = TextPrimary,
    surfaceVariant = BgElevated,
    onSurfaceVariant = TextSecondary
)

@Composable
fun AniWorldMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
