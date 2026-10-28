package de.aniworld.mobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.aniworld.mobile.cast.MobileCastManager
import de.aniworld.mobile.ui.theme.AccentCyan
import de.aniworld.mobile.ui.theme.BgElevated
import de.aniworld.mobile.ui.theme.BgSurface
import de.aniworld.mobile.ui.theme.TextMuted
import de.aniworld.mobile.ui.theme.TextPrimary
import de.aniworld.mobile.ui.theme.TextSecondary

@Composable
fun MiniRemoteBar(
    modifier: Modifier = Modifier
) {
    val status by MobileCastManager.tvPlaybackStatus.collectAsState()
    val connected by MobileCastManager.connectedDevice.collectAsState()

    val isVisible = connected != null && status != null && (status!!.title.isNotBlank())

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        val s = status ?: return@AnimatedVisibility
        // Der TV meldet Live-Sender mit Staffel "LIVE": keine Zeitleiste, kein Spulen
        val isLive = s.seasonLabel == "LIVE"

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(BgSurface)
                .border(1.dp, AccentCyan.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // TV-Icon + Titel & Episode
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(AccentCyan.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = s.title,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val epText = when {
                            isLive -> "🔴 Live auf ${connected?.name ?: "TV"}"
                            s.episodeTitle.isNotBlank() -> "S${s.seasonLabel} E${s.episodeNumber}: ${s.episodeTitle}"
                            else -> "Läuft auf ${connected?.name ?: "TV"}"
                        }
                        Text(
                            text = epText,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Steuerungsknöpfe (10s zurück, Play/Pause, 10s vor)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isLive) {
                        IconButton(
                            onClick = { MobileCastManager.controlTv("seek_by", -10_000L) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = TextPrimary, modifier = Modifier.size(20.dp))
                        }
                    }

                    IconButton(
                        onClick = { MobileCastManager.controlTv("toggle") },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(AccentCyan)
                    ) {
                        Icon(
                            imageVector = if (s.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (s.isPlaying) "Pause" else "Play",
                            tint = Color(0xFF050811),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    if (!isLive) {
                        IconButton(
                            onClick = { MobileCastManager.controlTv("seek_by", 10_000L) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = TextPrimary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // Fortschrittsbalken
            if (!isLive) {
                val progress = if (s.durationMs > 0) {
                    (s.positionMs.toFloat() / s.durationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = AccentCyan,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
            }
        }
    }
}
