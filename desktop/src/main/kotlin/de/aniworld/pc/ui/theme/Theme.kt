package de.aniworld.pc.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgPrimary = Color(0xFF0B0E14)
val BgSurface = Color(0xFF151A24)
val BgElevated = Color(0xFF1E2636)
val AccentCyan = Color(0xFF38BDF8)
val AccentBlue = Color(0xFF2563EB)
val AccentEmerald = Color(0xFF10B981)
val AccentAmber = Color(0xFFF59E0B)
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)
val FocusBorder = Color(0xFF38BDF8)

private val DarkTvColorScheme = darkColorScheme(
    primary = AccentCyan,
    secondary = AccentBlue,
    tertiary = AccentEmerald,
    background = BgPrimary,
    surface = BgSurface,
    surfaceVariant = BgElevated,
    onPrimary = Color(0xFF05080F),
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary
)

@Composable
fun AniWorldTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkTvColorScheme,
        content = content
    )
}
