package de.aniworld.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.aniworld.mobile.cast.MobileCastManager
import de.aniworld.mobile.ui.theme.AccentBlue
import de.aniworld.mobile.ui.theme.AccentCyan
import de.aniworld.mobile.ui.theme.BgElevated
import de.aniworld.mobile.ui.theme.BgSurface
import de.aniworld.mobile.ui.theme.TextMuted
import de.aniworld.mobile.ui.theme.TextPrimary
import de.aniworld.mobile.ui.theme.TextSecondary
import de.aniworld.tv.data.model.AnimeCard
import de.aniworld.tv.data.model.LanguageOption
import de.aniworld.tv.data.model.WatchProgressEntry

@Composable
fun CastActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connected by MobileCastManager.connectedDevice.collectAsState()

    IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        if (connected != null) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.CastConnected,
                    contentDescription = "Verbunden mit ${connected!!.name}",
                    tint = AccentCyan,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            Icon(
                imageVector = Icons.Default.Cast,
                contentDescription = "Mit TV verbinden",
                tint = TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun MobileAnimeCard(
    anime: AnimeCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 130.dp,
    isBookmarked: Boolean = false
) {
    Column(
        modifier = modifier
            .width(width)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(BgElevated)
                .border(0.8.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
        ) {
            if (anime.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = anime.coverUrl,
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Schattenverlauf unten
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
            )

            if (isBookmarked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AccentBlue)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("★", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = anime.title,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (anime.genreOrSubtitle.isNotBlank()) {
            Text(
                text = anime.genreOrSubtitle,
                color = TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MobileContinueCard(
    entry: WatchProgressEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .width(260.dp)
            .height(84.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(BgSurface)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(50.dp)
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp))
                .background(BgElevated)
        ) {
            if (entry.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = entry.coverUrl,
                    contentDescription = entry.animeTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = entry.animeTitle,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val sText = if (entry.seasonLabel.equals("Filme", true)) "Film" else "S${entry.seasonLabel} E${entry.episodeNumber}"
            Text(
                text = "$sText • ${entry.episodeTitle}",
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            LinearProgressIndicator(
                progress = { entry.progressFraction.coerceIn(0.05f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = AccentCyan,
                trackColor = Color.White.copy(alpha = 0.15f)
            )
        }
    }
}
