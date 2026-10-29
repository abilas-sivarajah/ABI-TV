package de.aniworld.tv.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.model.AnimeCard
import de.aniworld.tv.data.model.AnimeDetail
import de.aniworld.tv.data.model.EpisodeItem
import de.aniworld.tv.data.model.LanguageOption
import de.aniworld.tv.data.remote.AniWorldScraper
import de.aniworld.tv.data.remote.FilmpalastScraper
import de.aniworld.tv.data.remote.HdFilmeScraper
import de.aniworld.tv.data.remote.SerienStreamScraper
import de.aniworld.tv.ui.components.LanguageBadge
import de.aniworld.tv.ui.components.TvFocusableButton
import de.aniworld.tv.ui.theme.AccentCyan
import de.aniworld.tv.ui.theme.AccentEmerald
import de.aniworld.tv.ui.theme.BgElevated
import de.aniworld.tv.ui.theme.BgPrimary
import de.aniworld.tv.ui.theme.BgSurface
import de.aniworld.tv.ui.theme.FocusBorder
import de.aniworld.tv.ui.theme.TextMuted
import de.aniworld.tv.ui.theme.TextPrimary
import de.aniworld.tv.ui.theme.TextSecondary

@Composable
fun DetailScreen(
    animeSlug: String,
    initialTitle: String,
    initialCover: String,
    onBack: () -> Unit,
    onPlayEpisode: (AnimeDetail, EpisodeItem, List<EpisodeItem>, LanguageOption) -> Unit
) {
    var detail by remember { mutableStateOf<AnimeDetail?>(null) }
    var selectedSeasonPath by remember { mutableStateOf<String?>(null) }
    var selectedLanguage by remember { mutableStateOf(LocalStorage.preferredLanguage) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val watchlist by LocalStorage.watchlistFlow.collectAsState()
    val watchedSet by LocalStorage.watchedEpisodesFlow.collectAsState()
    val progressList by LocalStorage.progressFlow.collectAsState()

    val isBookmarked = remember(watchlist, animeSlug) {
        LocalStorage.isInWatchlist(animeSlug)
    }

    val savedProgress = remember(progressList, animeSlug) {
        LocalStorage.getProgressForAnime(animeSlug)
    }

    val primaryFocusRequester = remember { FocusRequester() }
    val loadingBackFocusRequester = remember { FocusRequester() }

    LaunchedEffect(animeSlug, selectedSeasonPath) {
        isLoading = true
        errorMsg = null
        runCatching {
            when {
                FilmpalastScraper.isFilmpalastSlug(animeSlug) -> {
                    FilmpalastScraper.fetchDetail(animeSlug)
                }
                HdFilmeScraper.isHdFilmeSlug(animeSlug) -> {
                    HdFilmeScraper.fetchDetail(animeSlug, selectedSeasonPath)
                }
                SerienStreamScraper.isStoSlug(animeSlug) -> {
                    SerienStreamScraper.fetchDetail(animeSlug, selectedSeasonPath)
                }
                else -> {
                    AniWorldScraper.fetchAnimeDetail(animeSlug, selectedSeasonPath)
                }
            }
        }.onSuccess { fetched ->
            detail = fetched
            isLoading = false
        }.onFailure { err ->
            errorMsg = err.message ?: "Fehler beim Laden der Anime-Details"
            isLoading = false
        }
    }

    // Sobald die Detailseite geladen ist, Fokus automatisch auf den Haupt-Play-Button setzen
    LaunchedEffect(detail != null, errorMsg != null) {
        delay(60)
        if (detail != null || errorMsg != null) {
            runCatching { primaryFocusRequester.requestFocus() }
        } else {
            runCatching { loadingBackFocusRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        val currentDetail = detail

        if (isLoading && currentDetail == null) {
            LaunchedEffect(Unit) {
                delay(50)
                runCatching { loadingBackFocusRequester.requestFocus() }
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AccentCyan)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Lade Staffeln & Episoden für $initialTitle...",
                        color = TextSecondary,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    TvFocusableButton(
                        text = "← Zurück",
                        onClick = onBack,
                        modifier = Modifier.focusRequester(loadingBackFocusRequester)
                    )
                }
            }
            return@Box
        }

        if (errorMsg != null && currentDetail == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = errorMsg ?: "Fehler", color = TextPrimary, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvFocusableButton(
                            text = "← Zurück",
                            onClick = onBack,
                            modifier = Modifier.focusRequester(primaryFocusRequester)
                        )
                        TvFocusableButton(
                            text = "Erneut versuchen",
                            isPrimary = true,
                            onClick = { selectedSeasonPath = selectedSeasonPath }
                        )
                    }
                }
            }
            return@Box
        }

        if (currentDetail == null) return@Box

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Kopfbereich mit Poster, Titel, Beschreibung, Genres & Aktionsbuttons
            item(key = "detail_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Poster
                    AsyncImage(
                        model = currentDetail.coverUrl.ifBlank { initialCover },
                        contentDescription = currentDetail.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(175.dp)
                            .height(260.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.5.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                    )

                    // Infos & Aktionen
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TvFocusableButton(text = "← Zurück", onClick = onBack)

                            if (currentDetail.years.isNotBlank()) {
                                InfoPill(currentDetail.years)
                            }
                            if (currentDetail.ageRating.isNotBlank()) {
                                InfoPill(currentDetail.ageRating)
                            }
                            if (currentDetail.rating.isNotBlank()) {
                                InfoPill("★ ${currentDetail.rating}")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = currentDetail.title,
                            color = TextPrimary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (currentDetail.genres.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = currentDetail.genres.joinToString(" • "),
                                color = AccentCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = currentDetail.description.ifBlank { "Keine Beschreibung verfügbar." },
                            color = TextSecondary,
                            fontSize = 13.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Haupt-Aktionsleiste (Abspielen / Weiterschauen / Merkliste / Sprache)
                        val isHdFilme = HdFilmeScraper.isHdFilmeSlug(animeSlug)
                        val langChoices = if (isHdFilme) listOf(LanguageOption.GER_DUB) else LanguageOption.entries

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            val firstEp = currentDetail.episodes.firstOrNull()
                            if (savedProgress != null) {
                                item {
                                    TvFocusableButton(
                                        text = "▶ Weiterschauen (S${savedProgress.seasonLabel} E${savedProgress.episodeNumber})",
                                        isPrimary = true,
                                        modifier = Modifier.focusRequester(primaryFocusRequester),
                                        onClick = {
                                            val matching = currentDetail.episodes.firstOrNull {
                                                it.path == savedProgress.episodePath
                                            } ?: EpisodeItem(
                                                number = savedProgress.episodeNumber,
                                                seasonLabel = savedProgress.seasonLabel,
                                                path = savedProgress.episodePath,
                                                titleDe = savedProgress.episodeTitle,
                                                titleEn = "",
                                                languages = listOf(selectedLanguage)
                                            )
                                            onPlayEpisode(
                                                currentDetail,
                                                matching,
                                                currentDetail.episodes,
                                                selectedLanguage
                                            )
                                        }
                                    )
                                }
                            } else if (firstEp != null) {
                                item {
                                    TvFocusableButton(
                                        text = if (isHdFilme && currentDetail.seasons.isEmpty()) "▶ Film starten" else "▶ Folge 1 starten",
                                        isPrimary = true,
                                        modifier = Modifier.focusRequester(primaryFocusRequester),
                                        onClick = {
                                            onPlayEpisode(
                                                currentDetail,
                                                firstEp,
                                                currentDetail.episodes,
                                                selectedLanguage
                                            )
                                        }
                                    )
                                }
                            }

                            item {
                                TvFocusableButton(
                                    text = if (isBookmarked) "★ Auf Merkliste" else "+ Merkliste",
                                    isSelected = isBookmarked,
                                    modifier = if (savedProgress == null && firstEp == null) {
                                        Modifier.focusRequester(primaryFocusRequester)
                                    } else {
                                        Modifier
                                    },
                                    onClick = {
                                        LocalStorage.toggleWatchlist(
                                            AnimeCard(
                                                slug = currentDetail.slug,
                                                title = currentDetail.title,
                                                coverUrl = currentDetail.coverUrl.ifBlank { initialCover },
                                                genreOrSubtitle = currentDetail.genres.take(2).joinToString(", ")
                                            )
                                        )
                                    }
                                )
                            }

                            items(langChoices, key = { it.name }) { lang ->
                                TvFocusableButton(
                                    text = lang.shortLabel,
                                    isSelected = selectedLanguage == lang,
                                    badgeColor = Color(lang.badgeColorHex),
                                    onClick = {
                                        selectedLanguage = lang
                                        LocalStorage.preferredLanguage = lang
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 2. Staffel-Auswahlleiste
            if (currentDetail.seasons.isNotEmpty()) {
                item(key = "season_selector") {
                    Column {
                        Text(
                            text = "Staffel auswählen",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(currentDetail.seasons, key = { it.path }) { season ->
                                val isSelected = currentDetail.currentSeasonPath.endsWith(season.path) ||
                                    (selectedSeasonPath == null && season == currentDetail.seasons.firstOrNull { !it.isMovies })
                                val label = if (season.isMovies) "🎬 Filme & Specials" else "Staffel ${season.label}"
                                TvFocusableButton(
                                    text = label,
                                    isSelected = isSelected,
                                    onClick = { selectedSeasonPath = season.path }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Episoden-Header
            item(key = "episodes_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Episoden (${currentDetail.episodes.size})",
                        color = TextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = AccentCyan,
                            modifier = Modifier.width(22.dp).height(22.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            // 4. Episodenliste
            items(currentDetail.episodes, key = { it.path }) { episode ->
                val isWatched = episode.path in watchedSet
                EpisodeRowCard(
                    episode = episode,
                    isWatched = isWatched,
                    onClick = {
                        onPlayEpisode(
                            currentDetail,
                            episode,
                            currentDetail.episodes,
                            selectedLanguage
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun InfoPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(BgElevated)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EpisodeRowCard(
    episode: EpisodeItem,
    isWatched: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1.0f,
        animationSpec = tween(140),
        label = "epScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) BgElevated else BgSurface)
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) FocusBorder else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp)
            )
            .focusable()
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Folgen-Nummer
            Box(
                modifier = Modifier
                    .width(54.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isFocused) AccentCyan else BgPrimary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#${episode.number}",
                    color = if (isFocused) Color(0xFF05080F) else AccentCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = episode.displayTitle,
                        color = if (isFocused) AccentCyan else TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isWatched) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AccentEmerald.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "✓ GESEHEN",
                                color = AccentEmerald,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (episode.secondaryTitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = episode.secondaryTitle,
                        color = TextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Verfügbare Sprachen der Folge als Badges rechts
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            episode.languages.forEach { lang ->
                LanguageBadge(language = lang)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "▶ Abspielen",
                color = if (isFocused) AccentCyan else TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
