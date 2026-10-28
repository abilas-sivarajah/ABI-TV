package de.aniworld.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.aniworld.mobile.cast.MobileCastManager
import de.aniworld.mobile.ui.components.CastActionButton
import de.aniworld.mobile.ui.theme.AccentBlue
import de.aniworld.mobile.ui.theme.AccentCyan
import de.aniworld.mobile.ui.theme.BgElevated
import de.aniworld.mobile.ui.theme.BgPrimary
import de.aniworld.mobile.ui.theme.BgSurface
import de.aniworld.mobile.ui.theme.TextMuted
import de.aniworld.mobile.ui.theme.TextPrimary
import de.aniworld.mobile.ui.theme.TextSecondary
import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.model.AnimeCard
import de.aniworld.tv.data.model.AnimeDetail
import de.aniworld.tv.data.model.EpisodeItem
import de.aniworld.tv.data.model.LanguageOption
import de.aniworld.tv.data.remote.AniWorldScraper
import de.aniworld.tv.data.remote.HdFilmeScraper
import de.aniworld.tv.data.remote.SerienStreamScraper
import kotlinx.coroutines.launch

@Composable
fun MobileDetailScreen(
    animeSlug: String,
    initialTitle: String,
    initialCover: String,
    onBack: () -> Unit,
    onOpenCastSheet: () -> Unit,
    onPlayOnPhone: (AnimeDetail, EpisodeItem, LanguageOption) -> Unit
) {
    var detail by remember { mutableStateOf<AnimeDetail?>(null) }
    var selectedSeasonPath by remember { mutableStateOf<String?>(null) }
    var selectedLanguage by remember { mutableStateOf(LocalStorage.preferredLanguage) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val watchlist by LocalStorage.watchlistFlow.collectAsState()
    val connectedDevice by MobileCastManager.connectedDevice.collectAsState()
    val isBookmarked = remember(watchlist, animeSlug) { LocalStorage.isInWatchlist(animeSlug) }

    LaunchedEffect(animeSlug, selectedSeasonPath) {
        isLoading = true
        errorMsg = null
        runCatching {
            // Gleiche Reihenfolge wie auf dem TV: HD-Filme, dann S.to, sonst AniWorld
            if (HdFilmeScraper.isHdFilmeSlug(animeSlug)) {
                HdFilmeScraper.fetchDetail(animeSlug, selectedSeasonPath)
            } else if (SerienStreamScraper.isStoSlug(animeSlug)) {
                SerienStreamScraper.fetchDetail(animeSlug, selectedSeasonPath)
            } else {
                AniWorldScraper.fetchAnimeDetail(animeSlug, selectedSeasonPath)
            }
        }.onSuccess { fetched ->
            detail = fetched
            isLoading = false
        }.onFailure { err ->
            errorMsg = err.message ?: "Fehler beim Laden"
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        val currentDetail = detail

        if (isLoading && currentDetail == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AccentCyan)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Lade Details...", color = TextSecondary, fontSize = 14.sp)
                }
            }
            return@Box
        }

        if (currentDetail == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = errorMsg ?: "Fehler beim Laden", color = TextPrimary)
            }
            return@Box
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            // Header-Bild mit Verlauf & Zurück-Button
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    val bgImage = currentDetail.backdropUrl.ifBlank { currentDetail.coverUrl.ifBlank { initialCover } }
                    AsyncImage(
                        model = bgImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.4f),
                                        Color.Transparent,
                                        BgPrimary
                                    )
                                )
                            )
                    )

                    // Obere Leiste: Zurück-Pfeil & Cast-Icon
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Zurück", tint = TextPrimary)
                        }

                        CastActionButton(onClick = onOpenCastSheet)
                    }

                    // Poster & Titel unten im Banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        AsyncImage(
                            model = currentDetail.coverUrl.ifBlank { initialCover },
                            contentDescription = currentDetail.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(90.dp)
                                .height(130.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = currentDetail.title,
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (currentDetail.genres.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentDetail.genres.joinToString(" • "),
                                    color = AccentCyan,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Haupt-Aktionsbuttons: "Auf Handy spielen" & "Auf TV abspielen"
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    val firstEp = currentDetail.episodes.firstOrNull()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Button 1: Auf Handy abspielen
                        Button(
                            onClick = {
                                if (firstEp != null) {
                                    onPlayOnPhone(currentDetail, firstEp, selectedLanguage)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color(0xFF050811)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Auf Handy", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        // Button 2: Auf TV abspielen
                        Button(
                            onClick = {
                                if (firstEp != null) {
                                    scope.launch {
                                        val ok = MobileCastManager.playOnTv(
                                            animeSlug = currentDetail.slug,
                                            animeTitle = currentDetail.title,
                                            coverUrl = currentDetail.coverUrl.ifBlank { initialCover },
                                            episodePath = firstEp.path,
                                            episodeNumber = firstEp.number,
                                            seasonLabel = firstEp.seasonLabel,
                                            titleDe = firstEp.displayTitle,
                                            preferredLangKey = selectedLanguage.langKey
                                        )
                                        if (!ok && MobileCastManager.connectedDevice.value == null) {
                                            onOpenCastSheet()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Auf TV",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Merkliste Button
                    OutlinedButton(
                        onClick = {
                            LocalStorage.toggleWatchlist(
                                AnimeCard(
                                    slug = currentDetail.slug,
                                    title = currentDetail.title,
                                    coverUrl = currentDetail.coverUrl.ifBlank { initialCover }
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                    ) {
                        Text(if (isBookmarked) "★ Auf der Merkliste" else "+ Zur Merkliste hinzufügen", fontSize = 13.sp)
                    }
                }
            }

            // Beschreibung
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        text = currentDetail.description.ifBlank { "Keine Beschreibung verfügbar." },
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            // Staffeln (falls Serie)
            if (currentDetail.seasons.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Text(
                            text = "Staffeln",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(currentDetail.seasons, key = { it.path }) { season ->
                                val isSelected = currentDetail.currentSeasonPath.endsWith(season.path) ||
                                    (selectedSeasonPath == null && season == currentDetail.seasons.firstOrNull { !it.isMovies })
                                val label = if (season.isMovies) "Filme" else "Staffel ${season.label}"
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) AccentCyan else BgElevated)
                                        .clickable { selectedSeasonPath = season.path }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color(0xFF050811) else TextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Episoden-Liste
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Episoden (${currentDetail.episodes.size})",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(currentDetail.episodes, key = { it.path }) { ep ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(BgSurface)
                        .clickable { onPlayOnPhone(currentDetail, ep, selectedLanguage) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(BgElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = ep.number.toString(),
                                color = AccentCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = ep.displayTitle,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (ep.titleEn.isNotBlank()) {
                                Text(
                                    text = ep.titleEn,
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Cast-Knopf für diese Episode direkt an den TV
                    IconButton(
                        onClick = {
                            scope.launch {
                                val ok = MobileCastManager.playOnTv(
                                    animeSlug = currentDetail.slug,
                                    animeTitle = currentDetail.title,
                                    coverUrl = currentDetail.coverUrl.ifBlank { initialCover },
                                    episodePath = ep.path,
                                    episodeNumber = ep.number,
                                    seasonLabel = ep.seasonLabel,
                                    titleDe = ep.displayTitle,
                                    preferredLangKey = selectedLanguage.langKey
                                )
                                if (!ok && MobileCastManager.connectedDevice.value == null) {
                                    onOpenCastSheet()
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = "Auf TV abspielen",
                            tint = if (connectedDevice != null) AccentCyan else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
