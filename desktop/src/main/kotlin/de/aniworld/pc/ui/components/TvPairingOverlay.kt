package de.aniworld.pc.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import de.aniworld.tv.cast.CastPairing
import de.aniworld.tv.cast.PairingNotice
import de.aniworld.pc.ui.theme.AccentCyan
import de.aniworld.pc.ui.theme.BgElevated
import de.aniworld.pc.ui.theme.TextPrimary
import de.aniworld.pc.ui.theme.TextSecondary

/**
 * Zeigt den Kopplungscode für die Handy-Fernbedienung oben rechts an.
 * Nimmt keinen Fokus, damit Player und Menüs mit der Fernbedienung bedienbar bleiben.
 */
@Composable
fun TvPairingOverlay() {
    val notice by CastPairing.notice.collectAsState()
    // Letzten Inhalt merken, damit beim Ausblenden nicht leer animiert wird
    val lastNotice = remember { arrayOfNulls<PairingNotice>(1) }
    if (notice != null) lastNotice[0] = notice

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(200f)
            .padding(28.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        AnimatedVisibility(visible = notice != null, enter = fadeIn(), exit = fadeOut()) {
            val shown = notice ?: lastNotice[0] ?: return@AnimatedVisibility
            Column(
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(BgElevated.copy(alpha = 0.96f))
                    .border(2.dp, AccentCyan.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 22.dp, vertical = 18.dp)
            ) {
                when (shown) {
                    is PairingNotice.ShowCode -> {
                        Text(
                            text = "📱 Handy koppeln",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "„${shown.clientName}“ möchte diesen Fernseher steuern. Gib diesen Code am Handy ein:",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = shown.code.chunked(3).joinToString(" "),
                            color = AccentCyan,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 4.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Gilt 2 Minuten. Nicht angefordert? Einfach ignorieren.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    is PairingNotice.Paired -> {
                        Text(
                            text = "✅ ${shown.clientName} ist gekoppelt",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Das Handy kann jetzt Folgen auf diesem Fernseher starten.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }

                    PairingNotice.Blocked -> {
                        Text(
                            text = "⛔ Kopplung gesperrt",
                            color = Color(0xFFFF5252),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Zu viele falsche Codes. In 30 Sekunden erneut versuchen.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
