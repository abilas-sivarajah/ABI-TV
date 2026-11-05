package de.aniworld.tv.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.model.AnimeCard
import de.aniworld.tv.data.model.AnimeDetail
import de.aniworld.tv.data.model.CatalogSection
import de.aniworld.tv.data.model.EpisodeItem
import de.aniworld.tv.data.model.LanguageOption
import de.aniworld.tv.data.model.WatchProgressEntry
import de.aniworld.tv.data.remote.FilmpalastScraper
import de.aniworld.tv.data.remote.SerienStreamScraper
import de.aniworld.tv.data.remote.LetterboxdRatings
import de.aniworld.tv.data.remote.TitlePreview
import de.aniworld.tv.data.remote.TmdbArtwork
import de.aniworld.tv.ui.components.TvAnimeCard
import de.aniworld.tv.ui.components.TvContinueWatchingCard
import de.aniworld.tv.ui.components.TvFocusableButton
import de.aniworld.tv.ui.theme.AccentAmber
import de.aniworld.tv.ui.theme.AccentCyan
import de.aniworld.tv.ui.theme.BgElevated
import de.aniworld.tv.ui.theme.BgPrimary
import de.aniworld.tv.ui.theme.BgSurface
import de.aniworld.tv.ui.theme.FocusBorder
import de.aniworld.tv.ui.theme.TextPrimary
import de.aniworld.tv.ui.theme.TextSecondary
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Startet einen Titel direkt im Player (Details, Folge, Folgen der Staffel, Sprache). */
typealias PlayTitle = (AnimeDetail, EpisodeItem, List<EpisodeItem>, LanguageOption) -> Unit

internal val LocalPlayTitle = staticCompositionLocalOf<PlayTitle> { { _, _, _, _ -> } }

/** Welcher Titel gerade in welcher Reihe als Info-Feld aufgeklappt ist ("Reihentitel|slug"); immer nur einer. */
internal class ExpandedTitleState {
    var key by mutableStateOf<String?>(null)
}

internal val LocalExpandedTitle = staticCompositionLocalOf { ExpandedTitleState() }

/** Höhe des Info-Felds in der Reihe = Höhe eines Karten-Covers (152 dp breit, 2:3). */
internal val TitleTileHeight = 228.dp

// ------------------- Startseite -------------------

/**
 * Startseite über alle Anbieter: Titelbild, Weiterschauen und je eine Reihe mit den beliebtesten
 * Filmen (Filmpalast), Serien (S.to) und Animes (AniWorld).
 */
@Composable
internal fun StartTabContent(
    uiState: HomeUiState,
    animeSections: List<CatalogSection>,
    isLoadingAnime: Boolean,
    continueWatching: List<WatchProgressEntry>,
    watchlist: List<AnimeCard>,
    onRetryAnime: () -> Unit,
    onOpenAnime: (AnimeCard) -> Unit,
    onResumeWatch: (WatchProgressEntry) -> Unit
) {
    var isLoading by remember { mutableStateOf(!uiState.startLoaded) }
    var reloadKey by remember { mutableStateOf(0) }
    LaunchedEffect(reloadKey) {
        if (uiState.startLoaded) return@LaunchedEffect
        isLoading = true
        coroutineScope {
            val movies = async { runCatching { FilmpalastScraper.fetchCategory("/movies/top") }.getOrDefault(emptyList()) }
            val series = async { runCatching { SerienStreamScraper.fetchCategory("/beliebte-serien") }.getOrDefault(emptyList()) }
            uiState.startTopMovies = movies.await().distinctBy { it.cleanSlug }.take(24)
            uiState.startPopularSeries = series.await().distinctBy { it.cleanSlug }.take(24)
        }
        uiState.startLoaded = uiState.startTopMovies.isNotEmpty() || uiState.startPopularSeries.isNotEmpty()
        isLoading = false
    }

    // Beliebte Animes: die Reihe "Beliebt bei AniWorld" der AniWorld-Startseite
    val popularAnime = remember(animeSections) {
        (animeSections.firstOrNull { it.title.contains("beliebt", ignoreCase = true) } ?: animeSections.firstOrNull())
            ?.items.orEmpty()
    }
    val watchlistSlugs = remember(watchlist) { watchlist.map { it.cleanSlug }.toSet() }

    // Titelbild: je 2 zufällige Filme, Serien und Animes, immer abwechselnd
    val heroItems = remember(uiState.startTopMovies, uiState.startPopularSeries, popularAnime) {
        TitlePreview.interleavedPicks(listOf(uiState.startTopMovies, uiState.startPopularSeries, popularAnime), perSource = 2)
    }

    // Noch nichts zum Anzeigen: Erst die Liste aufbauen, wenn das Titelbild da ist. Sonst bliebe sie an
    // der Lade-Zeile hängen, während die Inhalte darüber einsortiert werden, und öffnete ganz unten.
    if (heroItems.isEmpty() && continueWatching.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isLoading || isLoadingAnime) {
                    CircularProgressIndicator(color = AccentCyan)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(text = "Lade beliebte Titel...", color = TextSecondary, fontSize = 15.sp)
                } else {
                    Text(
                        text = "Die Startseite konnte nicht geladen werden.",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    TvFocusableButton(
                        text = "Erneut versuchen",
                        isPrimary = true,
                        onClick = {
                            reloadKey++
                            onRetryAnime()
                        }
                    )
                }
            }
        }
        return
    }

    // Kommt das Titelbild erst nach "Weiterschauen" dazu, trotzdem oben beginnen
    val listState = rememberLazyListState()
    LaunchedEffect(heroItems.isNotEmpty()) {
        if (heroItems.isNotEmpty() && listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        if (heroItems.isNotEmpty()) {
            item(key = "start_hero") {
                HeroCarousel(items = heroItems, watchlistSlugs = watchlistSlugs, onOpenAnime = onOpenAnime)
            }
        }

        if (continueWatching.isNotEmpty()) {
            item(key = "start_continue_watching") {
                Column {
                    SectionHeader(
                        title = "▶️ Weiterschauen",
                        subtitle = "${continueWatching.size} Titel im Verlauf"
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 36.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(continueWatching, key = { it.animeSlug }) { entry ->
                            TvContinueWatchingCard(
                                entry = entry,
                                onClick = { onResumeWatch(entry) },
                                onDetailClick = {
                                    onOpenAnime(AnimeCard(slug = entry.animeSlug, title = entry.animeTitle, coverUrl = entry.coverUrl))
                                }
                            )
                        }
                    }
                }
            }
        }

        val rows = listOf(
            Triple("start_movies", "🔥 Beliebte Top-Filme auf Filmpalast", uiState.startTopMovies),
            Triple("start_series", "🍿 Beliebte Serien auf S.to", uiState.startPopularSeries),
            Triple("start_anime", "⭐ Beliebte Anime auf AniWorld", popularAnime)
        )
        rows.filter { it.third.isNotEmpty() }.forEach { (key, title, items) ->
            item(key = key) {
                CatalogRowSection(
                    title = title,
                    items = items,
                    watchlistSlugs = watchlistSlugs,
                    onOpenAnime = onOpenAnime
                )
            }
        }

        if (isLoading) {
            item(key = "start_loading") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = AccentCyan, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "Lade beliebte Filme und Serien...", color = TextSecondary, fontSize = 14.sp)
                }
            }
        }
    }
}

// ------------------- Navigation -------------------

/** Reiter als schlichter Text; der aktive bekommt eine helle Pille, der Fokus einen cyanfarbenen Rand. */
@Composable
internal fun NavTextTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeColor: Color? = null
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(
                when {
                    focused -> Color.White.copy(alpha = 0.22f)
                    selected -> Color.White.copy(alpha = 0.16f)
                    else -> Color.Transparent
                }
            )
            .border(2.dp, if (focused) FocusBorder else Color.Transparent, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (badgeColor != null) {
            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(badgeColor))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = label,
            color = if (selected || focused) TextPrimary else TextSecondary,
            fontSize = 15.sp,
            maxLines = 1,
            softWrap = false,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/** Rundes Symbol (Lupe, Zahnrad, Schließen …). */
@Composable
internal fun NavIconButton(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(40.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(
                when {
                    focused -> Color.White.copy(alpha = 0.22f)
                    selected -> Color.White.copy(alpha = 0.16f)
                    else -> Color.Transparent
                }
            )
            .border(2.dp, if (focused) FocusBorder else Color.Transparent, CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (selected || focused) TextPrimary else TextSecondary,
            modifier = Modifier.size(24.dp)
        )
    }
}

// ------------------- Titelbild -------------------

/**
 * Titelbild als Karussell: blättert alle 9 s weiter, solange der Fokus nicht darauf liegt.
 * Die Pfeile links und rechts sind mit der Fernbedienung erreichbar, die Punkte zeigen nur die Position.
 */
@Composable
internal fun HeroCarousel(
    items: List<AnimeCard>,
    watchlistSlugs: Set<String>,
    onOpenAnime: (AnimeCard) -> Unit
) {
    var index by remember(items) { mutableStateOf(0) }
    val safeIndex = index.coerceIn(0, items.lastIndex)
    var hasFocusInside by remember { mutableStateOf(false) }

    fun previous() { index = (safeIndex - 1 + items.size) % items.size }
    fun next() { index = (safeIndex + 1) % items.size }

    LaunchedEffect(items, safeIndex, hasFocusInside) {
        if (items.size < 2 || hasFocusInside) return@LaunchedEffect
        delay(TitlePreview.HERO_ROTATE_MS)
        next()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { hasFocusInside = it.hasFocus }
    ) {
        Crossfade(targetState = items[safeIndex], animationSpec = tween(450), label = "heroCarousel") { anime ->
            HeroBanner(
                anime = anime,
                isBookmarked = anime.cleanSlug in watchlistSlugs,
                onOpenDetail = { onOpenAnime(anime) },
                onToggleWatchlist = { LocalStorage.toggleWatchlist(anime) }
            )
        }

        if (items.size > 1) {
            HeroArrow(
                icon = Icons.Filled.ChevronLeft,
                description = "Vorheriger Vorschlag",
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp),
                onClick = { previous() }
            )
            HeroArrow(
                icon = Icons.Filled.ChevronRight,
                description = "Nächster Vorschlag",
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp),
                onClick = { next() }
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 64.dp, bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.indices.forEach { i ->
                    val active = i == safeIndex
                    Box(
                        modifier = Modifier
                            .width(if (active) 22.dp else 8.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (active) Color.White else Color.White.copy(alpha = 0.35f))
                    )
                }
            }
        }
    }
}

/** Runder Pfeil am Rand des Titelbilds. */
@Composable
private fun HeroArrow(icon: ImageVector, description: String, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, tween(140), label = "heroArrow")
    Box(
        modifier = modifier
            .size(46.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = if (focused) 0.8f else 0.45f))
            .border(if (focused) 3.dp else 1.dp, if (focused) FocusBorder else Color.White.copy(alpha = 0.15f), CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(30.dp))
    }
}

/**
 * Großes Titelbild im Stil von Streaming-Apps: breites Hintergrundbild, darüber unten links
 * Titel, Eckdaten, Beschreibung und die Knöpfe. Ohne breites Bild wird das Cover weichgezeichnet
 * als Hintergrund und rechts scharf als Poster gezeigt.
 */
@Composable
private fun HeroBanner(
    anime: AnimeCard,
    isBookmarked: Boolean,
    onOpenDetail: () -> Unit,
    onToggleWatchlist: () -> Unit
) {
    val slug = anime.cleanSlug
    var detail by remember(slug) { mutableStateOf(TitlePreview.cachedDetail(slug)) }
    var artwork by remember(slug) { mutableStateOf(TitlePreview.cachedArtwork(slug)) }
    LaunchedEffect(slug) {
        if (detail == null) detail = TitlePreview.loadDetail(slug)
        if (artwork == null) artwork = TitlePreview.loadArtwork(anime, detail)
    }

    // Hintergrund: gefundenes Breitbild (TVmaze, Kitsu, Letterboxd), sonst das Bild der Seite – aber nur, wenn es wirklich querformatig ist.
    // Ein gestrecktes Hochformat-Poster wäre unscharf.
    val backdropUrl = artwork?.backdropUrl
        ?: detail?.backdropUrl?.takeIf { it.isNotBlank() && it != anime.coverUrl }
    var isWideBackdrop by remember(backdropUrl) { mutableStateOf(false) }
    val logoUrl = artwork?.logoUrl
    var isLogoLoaded by remember(logoUrl) { mutableStateOf(false) }

    val playTitle = LocalPlayTitle.current
    val scope = rememberCoroutineScope()
    val progressList by LocalStorage.progressFlow.collectAsState()
    val savedProgress = remember(progressList, slug) { LocalStorage.getProgressForAnime(slug) }
    var isStarting by remember(slug) { mutableStateOf(false) }
    var playError by remember(slug) { mutableStateOf<String?>(null) }

    fun play() {
        if (isStarting) return
        scope.launch {
            isStarting = true
            playError = null
            val target = TitlePreview.resolvePlayback(slug)
            isStarting = false
            when (target) {
                is TitlePreview.PlayTarget.Ready -> playTitle(target.detail, target.episode, target.detail.episodes, target.language)
                is TitlePreview.PlayTarget.Failed -> playError = target.message
            }
        }
    }

    val shape = RoundedCornerShape(18.dp)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 6.dp)
    ) {
        val heroHeight = (maxWidth * 0.40f).coerceIn(300.dp, 460.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .clip(shape)
                .background(BgSurface)
                .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
        ) {
            if (!isWideBackdrop && anime.coverUrl.isNotBlank()) {
                // blur gibt es erst ab Android 12; darunter das Bild stärker abdunkeln
                val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                AsyncImage(
                    model = anime.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = if (canBlur) 0.75f else 0.35f,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (canBlur) Modifier.blur(48.dp) else Modifier)
                )
            }
            if (backdropUrl != null) {
                AsyncImage(
                    model = backdropUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    alpha = if (isWideBackdrop) 1f else 0f,
                    onSuccess = { state ->
                        val image = state.result.drawable
                        isWideBackdrop = image.intrinsicWidth >= image.intrinsicHeight * 1.4f
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Abdunkeln links (Text) und unten (Übergang zur Seite)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to BgPrimary.copy(alpha = 0.92f),
                            0.35f to BgPrimary.copy(alpha = 0.70f),
                            0.65f to Color.Transparent
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to BgPrimary.copy(alpha = 0.85f)
                        )
                    )
            )

            if (!isWideBackdrop && anime.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = anime.coverUrl,
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 56.dp)
                        .fillMaxHeight(0.78f)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .widthIn(max = 600.dp)
                    .padding(start = 40.dp, end = 24.dp, bottom = 24.dp)
            ) {
                Text(
                    text = TitlePreview.heroLabel(anime, detail),
                    color = AccentCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(8.dp))

                val heroTitle = detail?.title?.takeIf { it.isNotBlank() } ?: anime.title
                if (logoUrl != null) {
                    // Titel-Logo statt Schrift (wie bei Streaming-Apps), wenn eins gefunden wurde
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = heroTitle,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                        onSuccess = { isLogoLoaded = true },
                        onError = { isLogoLoaded = false },
                        modifier = if (isLogoLoaded) {
                            Modifier.widthIn(max = 380.dp).heightIn(max = 96.dp)
                        } else {
                            Modifier.size(1.dp)
                        }
                    )
                }
                if (!isLogoLoaded) {
                    Text(
                        text = heroTitle,
                        color = TextPrimary,
                        fontSize = 34.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val meta = TitlePreview.metaLine(anime, detail)
                val letterboxd = rememberLetterboxdRating(artwork)
                if (meta.isNotEmpty() || letterboxd != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (meta.isNotEmpty()) {
                            Text(
                                text = meta.joinToString("  •  "),
                                color = TextPrimary.copy(alpha = 0.9f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        letterboxd?.takeIf { it.average != null }?.let { LetterboxdChip(it, large = true) }
                    }
                }

                detail?.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = description,
                        color = TextPrimary.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HeroButton(
                        text = when {
                            isStarting -> "Lädt …"
                            savedProgress != null -> "Weiterschauen"
                            else -> "Abspielen"
                        },
                        icon = Icons.Filled.PlayArrow,
                        primary = true,
                        onClick = { play() }
                    )
                    HeroButton(
                        text = "Details",
                        icon = Icons.Outlined.Info,
                        primary = false,
                        onClick = onOpenDetail
                    )
                    HeroButton(
                        text = if (isBookmarked) "Auf der Watchlist" else "Watchlist",
                        icon = if (isBookmarked) Icons.Filled.Check else Icons.Filled.Add,
                        primary = false,
                        onClick = onToggleWatchlist
                    )
                }
                playError?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = it, color = AccentAmber, fontSize = 13.sp)
                }
            }
        }
    }
}

/** Knöpfe im Titelbild: weiß gefüllt (Hauptaktion) bzw. halbtransparent. */
@Composable
private fun HeroButton(
    text: String,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(140), label = "heroButton")
    val shape = RoundedCornerShape(50)
    val contentColor = if (primary) Color(0xFF0B0E14) else TextPrimary
    Row(
        modifier = modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(
                when {
                    primary -> Color.White
                    focused -> Color.White.copy(alpha = 0.32f)
                    else -> Color.White.copy(alpha = 0.18f)
                }
            )
            .border(3.dp, if (focused) FocusBorder else Color.Transparent, shape)
            // clickable ist selbst fokussierbar; ein zusätzliches focusable() ließe OK/Enter ins Leere laufen
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = if (compact) 16.dp else 22.dp, vertical = if (compact) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(if (compact) 18.dp else 20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = contentColor,
            fontSize = if (compact) 14.sp else 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

// ------------------- Reihen mit Info-Feld -------------------

@Composable
internal fun CatalogRowSection(
    title: String,
    items: List<AnimeCard>,
    watchlistSlugs: Set<String>,
    onOpenAnime: (AnimeCard) -> Unit,
    subtitle: String = "${items.size} Titel"
) {
    // OK auf einer Karte: an ihrer Stelle in der Reihe erscheint ein breites Info-Feld,
    // die übrigen Titel reihen sich dahinter an und bleiben auswählbar
    val expandedState = LocalExpandedTitle.current
    val expandedSlug = expandedState.key?.takeIf { it.startsWith("$title|") }?.removePrefix("$title|")
    val rowState = rememberLazyListState()

    // Nach dem Schließen springt der Fokus zurück auf die Karte, sonst wäre er auf dem TV verloren
    val cardFocus = remember { FocusRequester() }
    var refocusSlug by remember { mutableStateOf<String?>(null) }

    // Info-Feld ins Bild holen, falls es am Rand steht
    LaunchedEffect(expandedSlug) {
        val index = items.indexOfFirst { it.cleanSlug == expandedSlug }
        if (index < 0) return@LaunchedEffect
        delay(60)
        val visible = rowState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        val viewportEnd = rowState.layoutInfo.viewportEndOffset
        if (visible == null || visible.offset < 0 || visible.offset + visible.size > viewportEnd) {
            rowState.animateScrollToItem(index)
        }
    }

    Column {
        SectionHeader(title = title, subtitle = subtitle)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // Ein Viertel der Reihenbreite, aber nie so schmal, dass Text und Knöpfe nicht mehr passen
            val panelWidth = ((maxWidth - 72.dp) / 4).coerceAtLeast(560.dp)
            LazyRow(
                state = rowState,
                contentPadding = PaddingValues(horizontal = 36.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(items, key = { "${title}_${it.cleanSlug}" }) { anime ->
                    if (anime.cleanSlug == expandedSlug) {
                        TitleInfoPanel(
                            anime = anime,
                            isBookmarked = anime.cleanSlug in watchlistSlugs,
                            onOpenDetail = { onOpenAnime(anime) },
                            onToggleWatchlist = { LocalStorage.toggleWatchlist(anime) },
                            onClose = { hadFocus ->
                                if (hadFocus) refocusSlug = anime.cleanSlug
                                expandedState.key = null
                            },
                            modifier = Modifier
                                .width(panelWidth)
                                .height(TitleTileHeight)
                        )
                    } else {
                        val isRefocusTarget = anime.cleanSlug == refocusSlug
                        TvAnimeCard(
                            anime = anime,
                            isBookmarked = anime.cleanSlug in watchlistSlugs,
                            modifier = if (isRefocusTarget) Modifier.focusRequester(cardFocus) else Modifier,
                            onClick = { expandedState.key = "$title|${anime.cleanSlug}" }
                        )
                        if (isRefocusTarget) {
                            LaunchedEffect(Unit) {
                                delay(40)
                                runCatching { cardFocus.requestFocus() }
                                refocusSlug = null
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Kompaktes Info-Feld an der Stelle einer Kartenreihe (gleiche Höhe): Poster links, rechts
 * Eckdaten, Titel, Genres, Beschreibung und Knöpfe. "Abspielen" bekommt beim Öffnen den Fokus
 * und startet direkt den Player, die Zurück-Taste schließt das Feld wieder.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TitleInfoPanel(
    anime: AnimeCard,
    isBookmarked: Boolean,
    onOpenDetail: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onClose: (hadFocus: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val slug = anime.cleanSlug
    var detail by remember(slug) { mutableStateOf(TitlePreview.cachedDetail(slug)) }
    var artwork by remember(slug) { mutableStateOf(TitlePreview.cachedArtwork(slug)) }
    LaunchedEffect(slug) {
        if (detail == null) detail = TitlePreview.loadDetail(slug)
        // Für die Letterboxd-Bewertung von Filmen
        if (artwork == null) artwork = TitlePreview.loadArtwork(anime, detail)
    }
    val letterboxd = rememberLetterboxdRating(artwork)

    val playFocus = remember { FocusRequester() }
    var hasFocusInside by remember { mutableStateOf(false) }
    // Das Feld ist höher als die Karte: Die ganze Kachel ins Bild holen, nicht nur den fokussierten Knopf
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(slug) {
        delay(40)
        runCatching { playFocus.requestFocus() }
        delay(120)
        runCatching { bringIntoView.bringIntoView() }
    }
    BackHandler { onClose(hasFocusInside) }

    val playTitle = LocalPlayTitle.current
    val scope = rememberCoroutineScope()
    val progressList by LocalStorage.progressFlow.collectAsState()
    val savedProgress = remember(progressList, slug) { LocalStorage.getProgressForAnime(slug) }
    var isStarting by remember(slug) { mutableStateOf(false) }
    var playError by remember(slug) { mutableStateOf<String?>(null) }

    fun play() {
        if (isStarting) return
        scope.launch {
            isStarting = true
            playError = null
            val target = TitlePreview.resolvePlayback(slug)
            isStarting = false
            when (target) {
                is TitlePreview.PlayTarget.Ready -> playTitle(target.detail, target.episode, target.detail.episodes, target.language)
                is TitlePreview.PlayTarget.Failed -> playError = target.message
            }
        }
    }

    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .bringIntoViewRequester(bringIntoView)
            .onFocusChanged { hasFocusInside = it.hasFocus }
            .clip(shape)
            .background(BgSurface)
            .border(1.dp, AccentCyan.copy(alpha = 0.35f), shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = detail?.coverUrl?.takeIf { it.isNotBlank() } ?: anime.coverUrl,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            )

            Spacer(modifier = Modifier.width(18.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 36.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val chips = TitlePreview.chips(anime, detail)
                    // Bei Filmen die Letterboxd-Bewertung direkt hinter Art und Jahr
                    val lbAfter = if (chips.firstOrNull() == "Film") minOf(2, chips.size) else -1
                    chips.forEachIndexed { index, chip ->
                        if (index == lbAfter) letterboxd?.takeIf { it.average != null }?.let { LetterboxdChip(it) }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(BgElevated)
                                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = chip,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    if (lbAfter == chips.size) letterboxd?.takeIf { it.average != null }?.let { LetterboxdChip(it) }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = detail?.title?.takeIf { it.isNotBlank() } ?: anime.title,
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val genreLine = TitlePreview.genreLine(anime, detail)
                if (genreLine.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = TextSecondary, fontWeight = FontWeight.Medium)) { append("Genre: ") }
                            withStyle(SpanStyle(color = AccentCyan, fontWeight = FontWeight.SemiBold)) { append(genreLine) }
                        },
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                detail?.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = description,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    HeroButton(
                        text = when {
                            isStarting -> "Lädt …"
                            savedProgress != null -> "Weiterschauen"
                            else -> "Abspielen"
                        },
                        icon = Icons.Filled.PlayArrow,
                        primary = true,
                        compact = true,
                        modifier = Modifier.focusRequester(playFocus),
                        onClick = { play() }
                    )
                    TileIconButton(
                        icon = if (isBookmarked) Icons.Filled.Check else Icons.Filled.Add,
                        description = if (isBookmarked) "Auf der Watchlist" else "Zur Watchlist",
                        onClick = onToggleWatchlist
                    )
                    TileIconButton(icon = Icons.Outlined.Info, description = "Details", onClick = onOpenDetail)
                }
                playError?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = it, color = AccentAmber, fontSize = 12.sp, maxLines = 1)
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
        ) {
            NavIconButton(
                icon = Icons.Filled.Close,
                description = "Schließen",
                selected = false,
                onClick = { onClose(true) }
            )
        }
    }
}

/** Rundes Symbol im Info-Feld (Merkliste, Details). */
@Composable
private fun TileIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    NavIconButton(
        icon = icon,
        description = description,
        selected = true,
        onClick = onClick
    )
}

/** Letterboxd-Bewertung eines Films (über TMDB- oder IMDb-Nummer); Serien und Anime: keine. */
@Composable
private fun rememberLetterboxdRating(artwork: TmdbArtwork.Artwork?): LetterboxdRatings.Rating? {
    var rating by remember(artwork) { mutableStateOf<LetterboxdRatings.Rating?>(null) }
    LaunchedEffect(artwork) { rating = TitlePreview.loadLetterboxdRating(artwork) }
    return rating
}

private val LetterboxdGreen = Color(0xFF00E054)

/** Kleines Kästchen "LB ★ 3,9" (Letterboxd-Bewertung) – nur Anzeige. */
@Composable
private fun LetterboxdChip(rating: LetterboxdRatings.Rating, large: Boolean = false) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(BgElevated)
            .border(1.dp, LetterboxdGreen.copy(alpha = 0.45f), shape)
            .padding(horizontal = if (large) 12.dp else 10.dp, vertical = if (large) 5.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(LetterboxdGreen))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "LB" + (rating.shortLabel?.let { " ★ $it" } ?: ""),
            color = TextPrimary,
            fontSize = if (large) 13.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
        )
    }
}
