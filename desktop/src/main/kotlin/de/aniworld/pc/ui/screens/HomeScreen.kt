package de.aniworld.pc.ui.screens

import de.aniworld.pc.ui.compat.ScrollLazyRow
import de.aniworld.pc.ui.compat.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.staticCompositionLocalOf
import de.aniworld.tv.data.model.EpisodeItem
import de.aniworld.pc.ui.theme.AccentAmber
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.aniworld.pc.ui.compat.AsyncImage
import de.aniworld.pc.ui.compat.loadCachedImage
import de.aniworld.pc.platform.TmdbArtwork
import de.aniworld.tv.cast.CastPairing
import de.aniworld.pc.cast.PcCastManager
import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.model.AnimeCard
import de.aniworld.tv.data.model.AnimeDetail
import de.aniworld.tv.data.model.CatalogSection
import de.aniworld.tv.data.model.LanguageOption
import de.aniworld.tv.data.model.LiveTvChannel
import de.aniworld.tv.data.model.SearchScope
import de.aniworld.tv.data.model.WatchProgressEntry
import de.aniworld.tv.data.remote.AniWorldScraper
import de.aniworld.tv.data.remote.FilmpalastScraper
import de.aniworld.tv.data.remote.HdFilmeScraper
import de.aniworld.tv.data.remote.SerienStreamScraper
import de.aniworld.tv.data.update.AppRelease
import de.aniworld.tv.data.update.UpdateCheckResult
import de.aniworld.pc.update.UpdateManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import de.aniworld.pc.ui.components.TvAnimeCard
import de.aniworld.pc.ui.components.TvContinueWatchingCard
import de.aniworld.pc.ui.components.TvFocusableButton
import de.aniworld.pc.ui.theme.AccentBlue
import de.aniworld.pc.ui.theme.AccentCyan
import de.aniworld.pc.ui.theme.BgElevated
import de.aniworld.pc.ui.theme.BgPrimary
import de.aniworld.pc.ui.theme.BgSurface
import de.aniworld.pc.ui.theme.FocusBorder
import de.aniworld.pc.ui.theme.TextMuted
import de.aniworld.pc.ui.theme.TextPrimary
import de.aniworld.pc.ui.theme.TextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class MainNavTab(val label: String) {
    START("Startseite"),
    HOME("Anime"),
    MOVIES("Filme"),
    SERIES("Serien"),
    LIVETV("Live TV"),
    SEARCH("Suche"),
    GENRES("Genres"),
    WATCHLIST("Merkliste"),
    SETTINGS("Einstellungen")
}

/**
 * Reiter der oberen Leiste in dieser Reihenfolge; Suche und Einstellungen sitzen als Symbole rechts.
 * Genres ist vorerst ausgeblendet (gilt nur für Anime).
 */
private val NavBarTabs = listOf(
    MainNavTab.START,
    MainNavTab.MOVIES,
    MainNavTab.SERIES,
    MainNavTab.HOME,
    MainNavTab.LIVETV,
    MainNavTab.WATCHLIST
)

/** Startet einen Titel direkt im Player (Details, Folge, Folgen der Staffel, Sprache). */
typealias PlayTitle = (AnimeDetail, EpisodeItem, List<EpisodeItem>, LanguageOption) -> Unit

private val LocalPlayTitle = staticCompositionLocalOf<PlayTitle> { { _, _, _, _ -> } }

/** Welcher Titel gerade unter welcher Reihe aufgeklappt ist ("Reihentitel|slug"); immer nur einer. */
private class ExpandedTitleState {
    var key by mutableStateOf<String?>(null)
}

private val LocalExpandedTitle = staticCompositionLocalOf { ExpandedTitleState() }

class HomeUiState {
    var activeTab by mutableStateOf(MainNavTab.START)
    var tabHistory by mutableStateOf<List<MainNavTab>>(emptyList())
    var moviesCategoryPath by mutableStateOf("OVERVIEW")
    var moviesOverviewSections by mutableStateOf<List<CatalogSection>>(emptyList())
    var moviesCategoryItems by mutableStateOf<List<AnimeCard>>(emptyList())
    // Welche Kategorie in moviesCategoryItems steckt und bis zu welcher Seite sie geladen ist ("Mehr Filme laden")
    var moviesLoadedPath by mutableStateOf<String?>(null)
    var moviesPage by mutableStateOf(1)
    var moviesCanLoadMore by mutableStateOf(true)
    var seriesCategoryPath by mutableStateOf("OVERVIEW")
    var seriesOverviewSections by mutableStateOf<List<CatalogSection>>(emptyList())
    var seriesCategoryItems by mutableStateOf<List<AnimeCard>>(emptyList())
    var seriesLoadedPath by mutableStateOf<String?>(null)
    var searchScope by mutableStateOf(SearchScope.ANIMES)
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<AnimeCard>>(emptyList())
    var selectedGenreKey by mutableStateOf(AniWorldScraper.GENRE_CATEGORIES.first().first)
    var genreItems by mutableStateOf<List<AnimeCard>>(emptyList())
    // Startseite: beliebteste Titel aller Anbieter (einmal laden, beim Tab-Wechsel behalten)
    var startTopMovies by mutableStateOf<List<AnimeCard>>(emptyList())
    var startPopularSeries by mutableStateOf<List<AnimeCard>>(emptyList())
    var startLoaded by mutableStateOf(false)

    fun selectTab(newTab: MainNavTab) {
        if (newTab != activeTab) {
            if (newTab == MainNavTab.SEARCH) {
                if (activeTab == MainNavTab.MOVIES) {
                    searchScope = SearchScope.MOVIES
                } else if (activeTab == MainNavTab.SERIES) {
                    searchScope = SearchScope.SERIES
                } else if (activeTab == MainNavTab.HOME || activeTab == MainNavTab.GENRES) {
                    searchScope = SearchScope.ANIMES
                } else if (activeTab == MainNavTab.START) {
                    searchScope = SearchScope.ALL
                }
            }
            tabHistory = (tabHistory + activeTab).takeLast(10)
            activeTab = newTab
        }
    }
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    sections: List<CatalogSection>,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onOpenAnime: (AnimeCard) -> Unit,
    onResumeWatch: (WatchProgressEntry) -> Unit,
    onPlayLiveChannel: (LiveTvChannel) -> Unit = {},
    onUpdateFound: (AppRelease) -> Unit = {},
    onPlayTitle: PlayTitle = { _, _, _, _ -> }
) {
    val expandedTitle = remember { ExpandedTitleState() }
    // Beim Reiterwechsel aufgeklappte Banner schließen
    LaunchedEffect(uiState.activeTab) { expandedTitle.key = null }
    var preferredLang by remember { mutableStateOf(LocalStorage.preferredLanguage) }
    val activeTabFocusRequester = remember { FocusRequester() }

    val watchlist by LocalStorage.watchlistFlow.collectAsState()
    val progressList by LocalStorage.progressFlow.collectAsState()

    LaunchedEffect(Unit) {
        delay(50)
        runCatching { activeTabFocusRequester.requestFocus() }
    }

    // Schrittweise Zurück-Navigation innerhalb des Hauptbildschirms
    val canGoBackInHome =
        (uiState.activeTab == MainNavTab.MOVIES && uiState.moviesCategoryPath != "OVERVIEW") ||
            (uiState.activeTab == MainNavTab.SERIES && uiState.seriesCategoryPath != "OVERVIEW") ||
            (uiState.activeTab == MainNavTab.SEARCH && uiState.searchQuery.isNotEmpty()) ||
            uiState.tabHistory.isNotEmpty() ||
            uiState.activeTab != MainNavTab.START

    BackHandler(enabled = canGoBackInHome) {
        when {
            uiState.activeTab == MainNavTab.MOVIES && uiState.moviesCategoryPath != "OVERVIEW" -> {
                uiState.moviesCategoryPath = "OVERVIEW"
            }
            uiState.activeTab == MainNavTab.SERIES && uiState.seriesCategoryPath != "OVERVIEW" -> {
                uiState.seriesCategoryPath = "OVERVIEW"
            }
            uiState.activeTab == MainNavTab.SEARCH && uiState.searchQuery.isNotEmpty() -> {
                uiState.searchQuery = ""
            }
            uiState.tabHistory.isNotEmpty() -> {
                val prev = uiState.tabHistory.last()
                uiState.tabHistory = uiState.tabHistory.dropLast(1)
                uiState.activeTab = prev
            }
            uiState.activeTab != MainNavTab.START -> {
                uiState.activeTab = MainNavTab.START
            }
        }
    }

    CompositionLocalProvider(LocalPlayTitle provides onPlayTitle, LocalExpandedTitle provides expandedTitle) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        // ------------------- Obere TV-Navigationsleiste -------------------
        TopTvNavigationBar(
            activeTab = uiState.activeTab,
            activeTabFocusRequester = activeTabFocusRequester,
            onSelectTab = { uiState.selectTab(it) },
            preferredLanguage = preferredLang,
            onCycleLanguage = {
                val next = when (preferredLang) {
                    LanguageOption.GER_DUB -> LanguageOption.GER_SUB
                    LanguageOption.GER_SUB -> LanguageOption.ENG_SUB
                    LanguageOption.ENG_SUB -> LanguageOption.GER_DUB
                }
                LocalStorage.preferredLanguage = next
                preferredLang = next
            }
        )

        // ------------------- Hauptinhalt je nach Tab -------------------
        Box(modifier = Modifier.fillMaxSize()) {
            when (uiState.activeTab) {
                MainNavTab.START -> StartTabContent(
                    uiState = uiState,
                    animeSections = sections,
                    continueWatching = progressList,
                    watchlist = watchlist,
                    onOpenAnime = onOpenAnime,
                    onResumeWatch = onResumeWatch
                )

                MainNavTab.HOME -> HomeTabContent(
                    sections = sections,
                    continueWatching = progressList,
                    watchlist = watchlist,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onRetry = onRetry,
                    onOpenAnime = onOpenAnime,
                    onResumeWatch = onResumeWatch
                )

                MainNavTab.MOVIES -> MoviesTabContent(
                    uiState = uiState,
                    watchlist = watchlist,
                    onOpenAnime = onOpenAnime
                )

                MainNavTab.SERIES -> SeriesTabContent(
                    uiState = uiState,
                    watchlist = watchlist,
                    onOpenAnime = onOpenAnime
                )

                MainNavTab.LIVETV -> LiveTvScreen(
                    onPlayChannel = onPlayLiveChannel
                )

                MainNavTab.SEARCH -> SearchTabContent(
                    uiState = uiState,
                    watchlist = watchlist,
                    onOpenAnime = onOpenAnime
                )

                MainNavTab.GENRES -> GenresTabContent(
                    uiState = uiState,
                    watchlist = watchlist,
                    onOpenAnime = onOpenAnime
                )

                MainNavTab.WATCHLIST -> WatchlistTabContent(
                    watchlist = watchlist,
                    continueWatching = progressList,
                    onOpenAnime = onOpenAnime,
                    onResumeWatch = onResumeWatch
                )

                MainNavTab.SETTINGS -> SettingsTabContent(
                    preferredLanguage = preferredLang,
                    onCycleLanguage = {
                        val next = when (preferredLang) {
                            LanguageOption.GER_DUB -> LanguageOption.GER_SUB
                            LanguageOption.GER_SUB -> LanguageOption.ENG_SUB
                            LanguageOption.ENG_SUB -> LanguageOption.GER_DUB
                        }
                        LocalStorage.preferredLanguage = next
                        preferredLang = next
                    },
                    onUpdateFound = onUpdateFound
                )
            }
        }
    }
    }
}

@Composable
private fun TopTvNavigationBar(
    activeTab: MainNavTab,
    activeTabFocusRequester: FocusRequester,
    onSelectTab: (MainNavTab) -> Unit,
    preferredLanguage: LanguageOption,
    onCycleLanguage: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF05070B), BgPrimary)
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Logo links
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.horizontalGradient(listOf(AccentBlue, AccentCyan))
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "ABI",
                        color = Color.White,
                        fontSize = 15.sp,
                        maxLines = 1,
                        softWrap = false,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "TV",
                    color = AccentCyan,
                    fontSize = 15.sp,
                    maxLines = 1,
                    softWrap = false,
                    fontWeight = FontWeight.Bold
                )
            }

            fun focusIfActive(tab: MainNavTab): Modifier =
                if (activeTab == tab) Modifier.focusRequester(activeTabFocusRequester) else Modifier

            // Reiter direkt neben dem Logo (wie bei Streaming-Apps)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavBarTabs.forEach { tab ->
                    NavTextTab(
                        label = tab.label,
                        selected = activeTab == tab,
                        modifier = focusIfActive(tab),
                        onClick = { onSelectTab(tab) }
                    )
                }
            }

            // Rechts: Sprachwahl, Suche (Lupe) und Einstellungen (Zahnrad)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavTextTab(
                    label = preferredLanguage.shortLabel,
                    selected = false,
                    badgeColor = Color(preferredLanguage.badgeColorHex),
                    onClick = onCycleLanguage
                )
                NavIconButton(
                    icon = Icons.Outlined.Search,
                    description = "Suche",
                    selected = activeTab == MainNavTab.SEARCH,
                    modifier = focusIfActive(MainNavTab.SEARCH),
                    onClick = { onSelectTab(MainNavTab.SEARCH) }
                )
                NavIconButton(
                    icon = Icons.Outlined.Settings,
                    description = "Einstellungen",
                    selected = activeTab == MainNavTab.SETTINGS,
                    modifier = focusIfActive(MainNavTab.SETTINGS),
                    onClick = { onSelectTab(MainNavTab.SETTINGS) }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.06f))
        )
    }
}

/** Reiter als schlichter Text; der aktive bekommt eine helle Pille, Fokus (Tastatur) einen cyanfarbenen Rand. */
@Composable
private fun NavTextTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeColor: Color? = null
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(
                when {
                    selected -> Color.White.copy(alpha = 0.16f)
                    hovered || focused -> Color.White.copy(alpha = 0.07f)
                    else -> Color.Transparent
                }
            )
            .border(2.dp, if (focused) FocusBorder else Color.Transparent, shape)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (badgeColor != null) {
            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(badgeColor))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = label,
            color = if (selected || hovered || focused) TextPrimary else TextSecondary,
            fontSize = 15.sp,
            maxLines = 1,
            softWrap = false,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/** Rundes Symbol oben rechts (Lupe, Zahnrad). */
@Composable
private fun NavIconButton(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                when {
                    selected -> Color.White.copy(alpha = 0.16f)
                    hovered || focused -> Color.White.copy(alpha = 0.07f)
                    else -> Color.Transparent
                }
            )
            .border(2.dp, if (focused) FocusBorder else Color.Transparent, CircleShape)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (selected || hovered || focused) TextPrimary else TextSecondary,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * Startseite über alle Anbieter: Titelbild, Weiterschauen und je eine Reihe mit den beliebtesten
 * Filmen (Filmpalast), Serien (S.to) und Animes (AniWorld).
 */
@Composable
private fun StartTabContent(
    uiState: HomeUiState,
    animeSections: List<CatalogSection>,
    continueWatching: List<WatchProgressEntry>,
    watchlist: List<AnimeCard>,
    onOpenAnime: (AnimeCard) -> Unit,
    onResumeWatch: (WatchProgressEntry) -> Unit
) {
    var isLoading by remember { mutableStateOf(!uiState.startLoaded) }
    LaunchedEffect(Unit) {
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

    val firstTitle = uiState.startTopMovies.firstOrNull()
        ?: uiState.startPopularSeries.firstOrNull()
        ?: popularAnime.firstOrNull()
    var heroItem by remember(firstTitle) { mutableStateOf(firstTitle) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        heroItem?.let { featured ->
            item(key = "start_hero") {
                HeroBanner(
                    anime = featured,
                    isBookmarked = featured.cleanSlug in watchlistSlugs,
                    onOpenDetail = { onOpenAnime(featured) },
                    onToggleWatchlist = { LocalStorage.toggleWatchlist(featured) }
                )
            }
        }

        if (continueWatching.isNotEmpty()) {
            item(key = "start_continue_watching") {
                Column {
                    SectionHeader(
                        title = "▶️ Weiterschauen",
                        subtitle = "${continueWatching.size} Titel im Verlauf"
                    )
                    ScrollLazyRow(
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
                    onOpenAnime = onOpenAnime,
                    onFocusAnime = { heroItem = it }
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

@Composable
private fun HomeTabContent(
    sections: List<CatalogSection>,
    continueWatching: List<WatchProgressEntry>,
    watchlist: List<AnimeCard>,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onOpenAnime: (AnimeCard) -> Unit,
    onResumeWatch: (WatchProgressEntry) -> Unit
) {
    if (isLoading && sections.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = AccentCyan)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Lade AniWorld-Katalog...",
                    color = TextSecondary,
                    fontSize = 15.sp
                )
            }
        }
        return
    }

    if (errorMessage != null && sections.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Verbindungsfehler: $errorMessage",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(14.dp))
                TvFocusableButton(
                    text = "Erneut versuchen",
                    isPrimary = true,
                    onClick = onRetry
                )
            }
        }
        return
    }

    var heroAnime by remember(sections) {
        mutableStateOf(sections.firstOrNull()?.items?.firstOrNull())
    }
    val watchlistSlugs = remember(watchlist) { watchlist.map { it.cleanSlug }.toSet() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        // 1. Hero-Spotlight-Banner für den aktuell fokussierten Anime
        heroAnime?.let { featured ->
            item(key = "hero_banner") {
                HeroBanner(
                    anime = featured,
                    isBookmarked = featured.cleanSlug in watchlistSlugs,
                    onOpenDetail = { onOpenAnime(featured) },
                    onToggleWatchlist = { LocalStorage.toggleWatchlist(featured) }
                )
            }
        }

        // 2. Weiterschauen-Zeile (falls vorhanden)
        if (continueWatching.isNotEmpty()) {
            item(key = "row_continue_watching") {
                Column {
                    SectionHeader(
                        title = "▶️ Weiterschauen",
                        subtitle = "${continueWatching.size} Animes im Verlauf"
                    )
                    ScrollLazyRow(
                        contentPadding = PaddingValues(horizontal = 36.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(continueWatching, key = { it.animeSlug }) { entry ->
                            TvContinueWatchingCard(
                                entry = entry,
                                onClick = { onResumeWatch(entry) },
                                onDetailClick = {
                                    onOpenAnime(
                                        AnimeCard(
                                            slug = entry.animeSlug,
                                            title = entry.animeTitle,
                                            coverUrl = entry.coverUrl
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // 3. Merkliste-Schnellzugriff auf der Startseite (falls befüllt)
        if (watchlist.isNotEmpty()) {
            item(key = "row_watchlist_home") {
                CatalogRowSection(
                    title = "★ Deine Merkliste",
                    items = watchlist,
                    watchlistSlugs = watchlistSlugs,
                    onOpenAnime = onOpenAnime,
                    onFocusAnime = { heroAnime = it }
                )
            }
        }

        // 4. Alle AniWorld-Katalog-Reihen (Beliebt, Neue Animes, Derzeit beliebt, etc.)
        items(sections, key = { it.id }) { section ->
            CatalogRowSection(
                title = section.title,
                items = section.items,
                watchlistSlugs = watchlistSlugs,
                onOpenAnime = onOpenAnime,
                onFocusAnime = { heroAnime = it }
            )
        }
    }
}

/** Bereits geladene Details für das Titelbild (Hintergrundbild, Beschreibung …), damit nichts doppelt geladen wird. */
private val heroDetailCache = java.util.concurrent.ConcurrentHashMap<String, AnimeDetail>()

/** TMDB-Bilder je Titel (zusätzlich speichert [TmdbArtwork] sie auf der Platte). */
private val heroArtworkCache = java.util.concurrent.ConcurrentHashMap<String, TmdbArtwork.Artwork>()

/** Wahrscheinlicher Typ für die TMDB-Suche; der andere wird notfalls auch probiert. */
private fun guessTmdbKind(anime: AnimeCard, detail: AnimeDetail?): TmdbArtwork.Kind = when {
    anime.isSto -> TmdbArtwork.Kind.TV
    anime.isFilmpalast -> TmdbArtwork.Kind.MOVIE
    anime.isHdFilme -> if (detail?.seasons?.isNotEmpty() == true) TmdbArtwork.Kind.TV else TmdbArtwork.Kind.MOVIE
    else -> TmdbArtwork.Kind.TV // AniWorld: meist Serien
}

/** Dieselbe Quelle wie die Detailseite, je nach Anbieter des Titels. */
private suspend fun fetchHeroDetail(slug: String): AnimeDetail = when {
    FilmpalastScraper.isFilmpalastSlug(slug) -> FilmpalastScraper.fetchDetail(slug)
    HdFilmeScraper.isHdFilmeSlug(slug) -> HdFilmeScraper.fetchDetail(slug)
    SerienStreamScraper.isStoSlug(slug) -> SerienStreamScraper.fetchDetail(slug)
    else -> AniWorldScraper.fetchAnimeDetail(slug)
}

/**
 * Großes Titelbild im Stil von Streaming-Apps: breites Hintergrundbild, darüber unten links
 * Titel, Eckdaten, Beschreibung und die Knöpfe. Fehlen die Details noch, wird das Cover gezeigt.
 */
@Composable
private fun HeroBanner(
    anime: AnimeCard,
    isBookmarked: Boolean,
    onOpenDetail: () -> Unit,
    onToggleWatchlist: () -> Unit,
    modifier: Modifier = Modifier,
    /** Gesetzt = aufgeklappter Banner unter einer Reihe, mit X zum Schließen. */
    onClose: (() -> Unit)? = null,
    loadDelayMs: Long = 350
) {
    val slug = anime.cleanSlug
    var detail by remember(slug) { mutableStateOf(heroDetailCache[slug]) }
    var artwork by remember(slug) { mutableStateOf(heroArtworkCache[slug]) }
    LaunchedEffect(slug) {
        if (detail == null) {
            // Kurz warten: beim Überfahren vieler Karten nur den Titel laden, auf dem man stehen bleibt
            delay(loadDelayMs)
            detail = runCatching { fetchHeroDetail(slug) }.getOrNull()?.also { heroDetailCache[slug] = it }
        }
        if (artwork == null && TmdbArtwork.isConfigured) {
            val current = detail
            val title = current?.title?.takeIf { it.isNotBlank() } ?: anime.title
            val year = current?.years?.let { Regex("""(19|20)\d{2}""").find(it)?.value?.toInt() }
            artwork = TmdbArtwork.find(title, year, guessTmdbKind(anime, current))?.also { heroArtworkCache[slug] = it }
        }
    }

    // Hintergrund: TMDB, sonst das Bild der Seite – aber nur, wenn es wirklich querformatig ist.
    // Ein gestrecktes Hochformat-Poster wäre unscharf.
    val candidate = artwork?.backdropUrl
        ?: detail?.backdropUrl?.takeIf { it.isNotBlank() && it != anime.coverUrl }
    var backdropBitmap by remember(candidate) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(candidate) {
        val url = candidate ?: return@LaunchedEffect
        val bitmap = runCatching { loadCachedImage(url) }.getOrNull() ?: return@LaunchedEffect
        if (bitmap.width >= bitmap.height * 1.4f) backdropBitmap = bitmap
    }
    val logoUrl = artwork?.logoUrl
    var logoBitmap by remember(logoUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(logoUrl) {
        val url = logoUrl ?: return@LaunchedEffect
        logoBitmap = runCatching { loadCachedImage(url) }.getOrNull()
    }

    // "Abspielen": Film bzw. zuletzt geschaute Folge (sonst Folge 1) direkt im Vollbild starten
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
            val loaded = detail ?: runCatching { fetchHeroDetail(slug) }.getOrNull()
                ?.also { heroDetailCache[slug] = it; detail = it }
            isStarting = false
            if (loaded == null) {
                playError = "Titel konnte nicht geladen werden."
                return@launch
            }
            val language = LocalStorage.preferredLanguage
            val episode = pickEpisodeToPlay(slug, loaded, language)
            if (episode == null) {
                playError = "Keine Folge gefunden – bitte über „Details“ auswählen."
                return@launch
            }
            playTitle(loaded, episode, loaded.episodes, language)
        }
    }

    val shape = RoundedCornerShape(18.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 6.dp)
    ) {
        val heroHeight = (maxWidth * 0.40f).coerceIn(340.dp, 560.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .clip(shape)
                .background(BgSurface)
                .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
        ) {
            val wideBackdrop = backdropBitmap
            if (wideBackdrop != null) {
                Image(
                    bitmap = wideBackdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (anime.coverUrl.isNotBlank()) {
                // Nur ein kleines Hochformat-Cover: weichgezeichnet als Stimmung, rechts scharf als Poster
                AsyncImage(
                    model = anime.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.75f,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(48.dp)
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

            if (wideBackdrop == null && anime.coverUrl.isNotBlank()) {
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
                    .widthIn(max = 680.dp)
                    .padding(start = 40.dp, end = 24.dp, bottom = 36.dp)
            ) {
                Text(
                    text = when {
                        anime.cleanSlug.startsWith("filmpalast:") -> "FILMPALAST HIGHLIGHT"
                        anime.cleanSlug.startsWith("sto:") || anime.cleanSlug.startsWith("/serie/") -> "SERIENSTREAM HIGHLIGHT"
                        anime.cleanSlug.startsWith("hdfilme:") -> "HD-FILME HIGHLIGHT"
                        else -> "ANIWORLD EMPFEHLUNG"
                    },
                    color = AccentCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                val heroTitle = detail?.title?.takeIf { it.isNotBlank() } ?: anime.title
                val logo = logoBitmap
                if (logo != null) {
                    // Titel-Logo von TMDB statt Schrift (wie bei Streaming-Apps)
                    Image(
                        bitmap = logo,
                        contentDescription = heroTitle,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                        modifier = Modifier
                            .widthIn(max = 460.dp)
                            .heightIn(max = 130.dp)
                            .aspectRatio(logo.width.toFloat() / logo.height.coerceAtLeast(1))
                    )
                } else {
                    Text(
                        text = heroTitle,
                        color = TextPrimary,
                        fontSize = 44.sp,
                        lineHeight = 48.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Eckdaten wie "Action • Fantasy • 2023 • FSK 12"
                val meta = buildList {
                    val genres = detail?.genres.orEmpty().filter { it.isNotBlank() }.take(2)
                    if (genres.isNotEmpty()) addAll(genres) else anime.genreOrSubtitle.takeIf { it.isNotBlank() }?.let { add(it) }
                    detail?.years?.takeIf { it.isNotBlank() }?.let { add(it) }
                    detail?.ageRating?.takeIf { it.isNotBlank() }?.let { add(if (it.all(Char::isDigit)) "FSK $it" else it) }
                }
                if (meta.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = meta.joinToString("  •  "),
                        color = TextPrimary.copy(alpha = 0.9f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                detail?.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = description,
                        color = TextPrimary.copy(alpha = 0.85f),
                        fontSize = 16.sp,
                        lineHeight = 23.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HeroButton(
                        text = when {
                            isStarting -> "Lädt …"
                            savedProgress != null -> "▶  Weiterschauen"
                            else -> "▶  Abspielen"
                        },
                        primary = true,
                        onClick = { play() }
                    )
                    HeroButton(
                        text = "ⓘ  Details",
                        primary = false,
                        onClick = onOpenDetail
                    )
                    HeroButton(
                        text = if (isBookmarked) "✓  Auf der Merkliste" else "+  Merkliste",
                        primary = false,
                        onClick = onToggleWatchlist
                    )
                }
                playError?.let {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = it, color = AccentAmber, fontSize = 13.sp)
                }
            }

            if (onClose != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    NavIconButton(
                        icon = Icons.Filled.Close,
                        description = "Schließen",
                        selected = false,
                        onClick = onClose
                    )
                }
            }
        }
    }
}

/** Knöpfe im Titelbild: weiß gefüllt (Hauptaktion) bzw. halbtransparent. */
@Composable
private fun HeroButton(text: String, primary: Boolean, onClick: () -> Unit, compact: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val active = focused || hovered
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                when {
                    primary && active -> Color.White.copy(alpha = 0.80f)
                    primary -> Color.White
                    active -> Color.White.copy(alpha = 0.30f)
                    else -> Color.White.copy(alpha = 0.18f)
                }
            )
            .border(2.dp, if (focused) FocusBorder else Color.Transparent, shape)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = if (compact) 16.dp else 26.dp, vertical = if (compact) 8.dp else 12.dp)
    ) {
        Text(
            text = text,
            color = if (primary) Color(0xFF0B0E14) else TextPrimary,
            fontSize = if (compact) 14.sp else 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun CatalogRowSection(
    title: String,
    items: List<AnimeCard>,
    watchlistSlugs: Set<String>,
    onOpenAnime: (AnimeCard) -> Unit,
    onFocusAnime: (AnimeCard) -> Unit
) {
    // Klick auf eine Karte: an ihrer Stelle in der Reihe erscheint ein breites Info-Feld,
    // die übrigen Titel reihen sich dahinter an und bleiben auswählbar
    val expandedState = LocalExpandedTitle.current
    val expandedSlug = expandedState.key?.takeIf { it.startsWith("$title|") }?.removePrefix("$title|")
    val rowState = rememberLazyListState()

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
        SectionHeader(title = title, subtitle = "${items.size} Titel")
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // Halb so breit wie die Reihe, aber nie so schmal, dass die Knöpfe nicht mehr passen
            val panelWidth = ((maxWidth - 72.dp) / 2).coerceAtLeast(560.dp)
            ScrollLazyRow(
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
                            onClose = { expandedState.key = null },
                            modifier = Modifier
                                .width(panelWidth)
                                .height(TitleTileHeight)
                        )
                    } else {
                        TvAnimeCard(
                            anime = anime,
                            isBookmarked = anime.cleanSlug in watchlistSlugs,
                            onClick = { expandedState.key = "$title|${anime.cleanSlug}" },
                            onFocused = { onFocusAnime(anime) }
                        )
                    }
                }
            }
        }
    }
}

/** Höhe des Info-Felds in der Reihe = Höhe eines Karten-Covers (152 dp breit, 2:3). */
private val TitleTileHeight = 228.dp

/**
 * Kompaktes Info-Feld an der Stelle einer Kartenreihe (gleiche Höhe): Poster links, rechts
 * Jahr/Bewertung, Titel, Genres, Beschreibung und Knöpfe – "Abspielen" startet direkt im Vollbild.
 */
@Composable
private fun TitleInfoPanel(
    anime: AnimeCard,
    isBookmarked: Boolean,
    onOpenDetail: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val slug = anime.cleanSlug
    var detail by remember(slug) { mutableStateOf(heroDetailCache[slug]) }
    LaunchedEffect(slug) {
        if (detail == null) {
            detail = runCatching { fetchHeroDetail(slug) }.getOrNull()?.also { heroDetailCache[slug] = it }
        }
    }

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
            val loaded = detail ?: runCatching { fetchHeroDetail(slug) }.getOrNull()
                ?.also { heroDetailCache[slug] = it; detail = it }
            isStarting = false
            if (loaded == null) {
                playError = "Titel konnte nicht geladen werden."
                return@launch
            }
            val language = LocalStorage.preferredLanguage
            val episode = pickEpisodeToPlay(slug, loaded, language)
            if (episode == null) {
                playError = "Keine Folge gefunden – bitte über „Details“ auswählen."
                return@launch
            }
            playTitle(loaded, episode, loaded.episodes, language)
        }
    }

    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
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
                // Eckdaten als kleine Kästchen: Jahr, Bewertung, FSK
                val chips = buildList {
                    detail?.years?.takeIf { it.isNotBlank() }?.let { add(it) }
                    detail?.rating?.takeIf { it.isNotBlank() }?.let { add("★ $it") }
                    detail?.ageRating?.takeIf { it.isNotBlank() }?.let { add(if (it.all(Char::isDigit)) "FSK $it" else it) }
                }
                if (chips.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        chips.forEach { chip ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(BgElevated)
                                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(text = chip, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = detail?.title?.takeIf { it.isNotBlank() } ?: anime.title,
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val genres = detail?.genres.orEmpty().filter { it.isNotBlank() }.take(3)
                val genreLine = if (genres.isNotEmpty()) genres.joinToString(" • ") else anime.genreOrSubtitle
                if (genreLine.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = genreLine, color = AccentCyan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }

                detail?.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = description,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    HeroButton(
                        text = when {
                            isStarting -> "Lädt …"
                            savedProgress != null -> "▶  Weiterschauen"
                            else -> "▶  Abspielen"
                        },
                        primary = true,
                        compact = true,
                        onClick = { play() }
                    )
                    HeroButton(
                        text = if (isBookmarked) "✓  Merkliste" else "+  Merkliste",
                        primary = false,
                        compact = true,
                        onClick = onToggleWatchlist
                    )
                    HeroButton(text = "ⓘ  Details", primary = false, compact = true, onClick = onOpenDetail)
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
            NavIconButton(icon = Icons.Filled.Close, description = "Schließen", selected = false, onClick = onClose)
        }
    }
}

/** Film bzw. zuletzt geschaute Folge (sonst Folge 1) – das, was "Abspielen" startet. */
private fun pickEpisodeToPlay(slug: String, detail: AnimeDetail, language: LanguageOption): EpisodeItem? {
    val progress = LocalStorage.getProgressForAnime(slug) ?: return detail.episodes.firstOrNull()
    return detail.episodes.firstOrNull { it.path == progress.episodePath } ?: EpisodeItem(
        number = progress.episodeNumber,
        seasonLabel = progress.seasonLabel,
        path = progress.episodePath,
        titleDe = progress.episodeTitle,
        titleEn = "",
        languages = listOf(language)
    )
}

@Composable
private fun SectionHeader(title: String, subtitle: String = "") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                color = TextMuted,
                fontSize = 12.sp
            )
        }
    }
}

// =========================================================================
// TAB: FILME (HD-FILME.ONE)
// =========================================================================

@Composable
private fun MoviesTabContent(
    uiState: HomeUiState,
    watchlist: List<AnimeCard>,
    onOpenAnime: (AnimeCard) -> Unit
) {
    val selectedCategoryPath = uiState.moviesCategoryPath
    val scope = rememberCoroutineScope()

    // Bereits geladene Kategorien (inkl. nachgeladener Seiten) nach Detailseite/Tab-Wechsel nicht neu laden
    fun needsLoad(path: String): Boolean = if (path == "OVERVIEW") {
        uiState.moviesOverviewSections.isEmpty()
    } else {
        uiState.moviesLoadedPath != path || uiState.moviesCategoryItems.isEmpty()
    }

    var isLoading by remember { mutableStateOf(needsLoad(selectedCategoryPath)) }
    var reloadKey by remember { mutableStateOf(0) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var loadMoreFailed by remember { mutableStateOf(false) }

    LaunchedEffect(selectedCategoryPath, reloadKey) {
        val path = selectedCategoryPath
        if (!needsLoad(path)) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        if (path == "OVERVIEW") {
            uiState.moviesOverviewSections = runCatching { FilmpalastScraper.fetchHomeSections() }.getOrDefault(emptyList())
        } else {
            val firstPage = runCatching { FilmpalastScraper.fetchCategory(path) }.getOrDefault(emptyList())
            uiState.moviesCategoryItems = firstPage.distinctBy { it.cleanSlug }
            uiState.moviesLoadedPath = path
            uiState.moviesPage = 1
            uiState.moviesCanLoadMore = firstPage.isNotEmpty()
            loadMoreFailed = false
        }
        isLoading = false
    }

    fun loadMoreMovies() {
        if (isLoadingMore || !uiState.moviesCanLoadMore) return
        val path = uiState.moviesLoadedPath ?: return
        val nextPage = uiState.moviesPage + 1
        isLoadingMore = true
        loadMoreFailed = false
        scope.launch {
            val result = runCatching { FilmpalastScraper.fetchCategory(path, nextPage) }
            // Inzwischen andere Kategorie gewählt: Ergebnis verwerfen
            if (uiState.moviesLoadedPath == path) {
                result.onSuccess { more ->
                    // Doppelte Titel würden die Schlüssel im Raster sprengen; keine neuen Titel = letzte Seite
                    val known = uiState.moviesCategoryItems.mapTo(HashSet()) { it.cleanSlug }
                    val fresh = more.filter { known.add(it.cleanSlug) }
                    uiState.moviesCategoryItems = uiState.moviesCategoryItems + fresh
                    uiState.moviesPage = nextPage
                    uiState.moviesCanLoadMore = fresh.isNotEmpty()
                }.onFailure {
                    loadMoreFailed = true
                }
            }
            isLoadingMore = false
        }
    }

    CatalogBrowser(
        searchLabel = "🔍 Filme suchen",
        onOpenSearch = {
            uiState.searchScope = SearchScope.MOVIES
            uiState.selectTab(MainNavTab.SEARCH)
        },
        categories = FilmpalastScraper.CATEGORIES,
        selectedCategoryPath = selectedCategoryPath,
        onSelectCategory = { uiState.moviesCategoryPath = it },
        overviewSections = uiState.moviesOverviewSections,
        categoryItems = uiState.moviesCategoryItems,
        isLoading = isLoading ||
            (selectedCategoryPath != "OVERVIEW" && uiState.moviesLoadedPath != selectedCategoryPath),
        loadingText = "Lade Filme von Filmpalast...",
        emptyText = "Keine Filme gefunden. Filmpalast ist gerade nicht erreichbar.",
        onRetry = { reloadKey++ },
        watchlist = watchlist,
        onOpenAnime = onOpenAnime,
        gridFooter = {
            if (uiState.moviesCanLoadMore) {
                item(key = "load_more_movies", span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        TvFocusableButton(
                            text = when {
                                isLoadingMore -> "⏳ Lade weitere Filme..."
                                loadMoreFailed -> "⚠️ Laden fehlgeschlagen – erneut versuchen"
                                else -> "⬇️ Mehr Filme laden"
                            },
                            isPrimary = true,
                            onClick = { loadMoreMovies() }
                        )
                    }
                }
            }
        }
    )
}

// =========================================================================
// TAB: SERIEN (SERIENSTREAM / S.TO)
// =========================================================================

@Composable
private fun SeriesTabContent(
    uiState: HomeUiState,
    watchlist: List<AnimeCard>,
    onOpenAnime: (AnimeCard) -> Unit
) {
    val selectedCategoryPath = uiState.seriesCategoryPath

    fun needsLoad(path: String): Boolean = if (path == "OVERVIEW") {
        uiState.seriesOverviewSections.isEmpty()
    } else {
        uiState.seriesLoadedPath != path || uiState.seriesCategoryItems.isEmpty()
    }

    var isLoading by remember { mutableStateOf(needsLoad(selectedCategoryPath)) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(selectedCategoryPath, reloadKey) {
        val path = selectedCategoryPath
        if (!needsLoad(path)) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        if (path == "OVERVIEW") {
            uiState.seriesOverviewSections = runCatching { SerienStreamScraper.fetchHomeSections() }.getOrDefault(emptyList())
        } else {
            uiState.seriesCategoryItems = runCatching { SerienStreamScraper.fetchCategory(path) }
                .getOrDefault(emptyList())
                .distinctBy { it.cleanSlug }
            uiState.seriesLoadedPath = path
        }
        isLoading = false
    }

    CatalogBrowser(
        searchLabel = "🔍 Serien suchen",
        onOpenSearch = {
            uiState.searchScope = SearchScope.SERIES
            uiState.selectTab(MainNavTab.SEARCH)
        },
        categories = SerienStreamScraper.CATEGORIES,
        selectedCategoryPath = selectedCategoryPath,
        onSelectCategory = { uiState.seriesCategoryPath = it },
        overviewSections = uiState.seriesOverviewSections,
        categoryItems = uiState.seriesCategoryItems,
        isLoading = isLoading ||
            (selectedCategoryPath != "OVERVIEW" && uiState.seriesLoadedPath != selectedCategoryPath),
        loadingText = "Lade Serien von S.to...",
        emptyText = "Keine Serien gefunden. S.to ist gerade nicht erreichbar.",
        onRetry = { reloadKey++ },
        watchlist = watchlist,
        onOpenAnime = onOpenAnime
    )
}

/**
 * Gemeinsamer Aufbau von Filme- und Serien-Tab: Kategorie-Leiste, dann Übersicht mit Hero-Banner
 * oder Raster einer Kategorie. [gridFooter] hängt z. B. "Mehr Filme laden" ans Raster an.
 */
@Composable
private fun CatalogBrowser(
    searchLabel: String,
    onOpenSearch: () -> Unit,
    categories: List<Pair<String, String>>,
    selectedCategoryPath: String,
    onSelectCategory: (String) -> Unit,
    overviewSections: List<CatalogSection>,
    categoryItems: List<AnimeCard>,
    isLoading: Boolean,
    loadingText: String,
    emptyText: String,
    onRetry: () -> Unit,
    watchlist: List<AnimeCard>,
    onOpenAnime: (AnimeCard) -> Unit,
    gridFooter: LazyGridScope.() -> Unit = {}
) {
    val isOverview = selectedCategoryPath == "OVERVIEW"
    val watchlistSlugs = remember(watchlist) { watchlist.map { it.cleanSlug }.toSet() }

    var heroItem by remember(overviewSections) {
        mutableStateOf(overviewSections.firstOrNull()?.items?.firstOrNull())
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Horizontale Kategorie-Leiste (Suche, Übersicht, Genres...)
        ScrollLazyRow(
            contentPadding = PaddingValues(horizontal = 36.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                TvFocusableButton(
                    text = searchLabel,
                    onClick = onOpenSearch
                )
            }
            item {
                TvFocusableButton(
                    text = "🔥 Übersicht & Highlights",
                    isSelected = isOverview,
                    onClick = { onSelectCategory("OVERVIEW") }
                )
            }
            items(categories, key = { it.first }) { (path, label) ->
                TvFocusableButton(
                    text = label,
                    isSelected = selectedCategoryPath == path,
                    onClick = { onSelectCategory(path) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when {
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AccentCyan)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = loadingText,
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            (isOverview && overviewSections.isEmpty()) || (!isOverview && categoryItems.isEmpty()) -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = emptyText,
                            color = TextSecondary,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        TvFocusableButton(
                            text = "Erneut versuchen",
                            isPrimary = true,
                            onClick = onRetry
                        )
                    }
                }
            }

            isOverview -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    heroItem?.let { featured ->
                        item(key = "catalog_hero") {
                            HeroBanner(
                                anime = featured,
                                isBookmarked = featured.cleanSlug in watchlistSlugs,
                                onOpenDetail = { onOpenAnime(featured) },
                                onToggleWatchlist = { LocalStorage.toggleWatchlist(featured) }
                            )
                        }
                    }

                    items(overviewSections, key = { it.id }) { section ->
                        CatalogRowSection(
                            title = section.title,
                            items = section.items,
                            watchlistSlugs = watchlistSlugs,
                            onOpenAnime = onOpenAnime,
                            onFocusAnime = { heroItem = it }
                        )
                    }
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 148.dp),
                    contentPadding = PaddingValues(horizontal = 36.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(categoryItems, key = { it.cleanSlug }) { card ->
                        TvAnimeCard(
                            anime = card,
                            isBookmarked = card.cleanSlug in watchlistSlugs,
                            onClick = { onOpenAnime(card) }
                        )
                    }
                    gridFooter()
                }
            }
        }
    }
}

// =========================================================================
// TAB: SUCHE (inkl. On-Screen Schnell-Tastatur + System-Tastatur + Live-AJAX)
// =========================================================================

@Composable
private fun SearchTabContent(
    uiState: HomeUiState,
    watchlist: List<AnimeCard>,
    onOpenAnime: (AnimeCard) -> Unit
) {
    val query = uiState.searchQuery
    val results = uiState.searchResults
    val searchScope = uiState.searchScope
    var isSearching by remember { mutableStateOf(false) }
    val watchlistSlugs = remember(watchlist) { watchlist.map { it.cleanSlug }.toSet() }

    LaunchedEffect(query, searchScope) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            uiState.searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(300)
        val combined = coroutineScope {
            when (searchScope) {
                SearchScope.ANIMES -> {
                    runCatching { AniWorldScraper.searchAnimes(trimmed) }.getOrDefault(emptyList())
                }
                SearchScope.SERIES -> {
                    runCatching { SerienStreamScraper.searchSeries(trimmed) }.getOrDefault(emptyList())
                }
                SearchScope.MOVIES -> {
                    runCatching { FilmpalastScraper.searchMovies(trimmed) }.getOrDefault(emptyList())
                }
                SearchScope.ALL -> {
                    val animeDeferred = async {
                        runCatching { AniWorldScraper.searchAnimes(trimmed) }.getOrDefault(emptyList())
                    }
                    val seriesDeferred = async {
                        runCatching { SerienStreamScraper.searchSeries(trimmed) }.getOrDefault(emptyList())
                    }
                    val movieDeferred = async {
                        runCatching { FilmpalastScraper.searchMovies(trimmed) }.getOrDefault(emptyList())
                    }
                    animeDeferred.await() + seriesDeferred.await() + movieDeferred.await()
                }
            }
        }
        // Doppelte Treffer (z. B. aus mehreren Quellen) würden die Schlüssel im Ergebnisraster sprengen
        uiState.searchResults = combined.distinctBy { it.cleanSlug }
        isSearching = false
    }

    fun triggerSearch(newQuery: String) {
        uiState.searchQuery = newQuery
    }

    val placeholderText = when (searchScope) {
        SearchScope.ANIMES -> "Anime suchen..."
        SearchScope.SERIES -> "Serien suchen..."
        SearchScope.MOVIES -> "Filme suchen..."
        SearchScope.ALL -> "Alles durchsuchen..."
    }

    val quickSuggestions = when (searchScope) {
        SearchScope.ANIMES -> listOf(
            "Solo Leveling", "One Piece", "Naruto", "Attack on Titan",
            "Jujutsu Kaisen", "Demon Slayer", "Black Clover", "Bleach"
        )
        SearchScope.SERIES -> listOf(
            "Chicago Fire", "Breaking Bad", "Game of Thrones", "Wednesday",
            "Stranger Things", "The Boys", "The Rookie", "House of the Dragon"
        )
        SearchScope.MOVIES -> listOf(
            "Deadpool", "Oppenheimer", "Spider-Man", "Batman",
            "Harry Potter", "Avatar", "John Wick", "Dune"
        )
        SearchScope.ALL -> listOf(
            "Solo Leveling", "Chicago Fire", "One Piece", "Breaking Bad",
            "Deadpool", "Stranger Things", "Harry Potter", "Spider-Man"
        )
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val textFieldFocusRequester = remember { FocusRequester() }
    val boxFocusRequester = remember { FocusRequester() }
    var isEditing by remember { mutableStateOf(false) }
    var isBoxFocused by remember { mutableStateOf(false) }
    // Text samt Cursor des Eingabefelds. Das Feld wird bei jedem Einstieg neu angelegt und hätte den Cursor
    // sonst am Textanfang, sodass Weitertippen vor dem alten Text landete
    var fieldValue by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    var textFieldHadFocus by remember { mutableStateOf(false) }

    fun startEditing() {
        if (isEditing) return
        fieldValue = TextFieldValue(query, TextRange(query.length))
        textFieldHadFocus = false
        isEditing = true
    }

    fun stopEditing(keepFocusOnBox: Boolean) {
        if (!isEditing) return
        isEditing = false
        keyboardController?.hide()
        // Den Fokus aufs Suchfeld holen, solange das Textfeld noch da ist: Verschwindet es mit Fokus, bleibt im
        // Fokusbaum ein "ActiveParent ohne Kind" zurück, und das nächste OK auf dem Suchfeld ließ die App abstürzen
        if (keepFocusOnBox) runCatching { boxFocusRequester.requestFocus() }
    }

    BackHandler(enabled = isEditing) {
        stopEditing(keepFocusOnBox = true)
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            delay(50L)
            runCatching { textFieldFocusRequester.requestFocus() }
            keyboardController?.show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp)
    ) {
        // Kompakte Kopfzeile: Suchfeld + Filter-Kategorien auf EINER Zeile
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Suchfeld
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .focusRequester(boxFocusRequester)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BgSurface)
                    .border(
                        width = if (isBoxFocused || isEditing) 2.dp else 1.dp,
                        color = if (isBoxFocused || isEditing) FocusBorder else Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .onFocusChanged { isBoxFocused = it.isFocused }
                    // Nur clickable als Fokusziel: Ein zusätzliches focusable(!isEditing) wäre beim Beenden des
                    // Tippens noch abgeschaltet, und der Fokus käme nicht aufs Suchfeld zurück
                    .clickable { startEditing() }
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp &&
                            (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                        ) {
                            startEditing()
                            true
                        } else false
                    }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (!isEditing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Text("🔍", fontSize = 13.sp)
                            Text(
                                text = if (query.isEmpty()) placeholderText else query,
                                color = if (query.isEmpty()) TextMuted else TextPrimary,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isBoxFocused) AccentCyan.copy(alpha = 0.2f) else BgElevated)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "OK zum Tippen",
                                color = if (isBoxFocused) AccentCyan else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    BasicTextField(
                        // Wurde die Suche von außen geändert (z. B. Vorschlag gewählt), gilt der neue Text
                        value = if (fieldValue.text == query) fieldValue else TextFieldValue(query, TextRange(query.length)),
                        onValueChange = {
                            fieldValue = it
                            triggerSearch(it.text)
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(AccentCyan),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { stopEditing(keepFocusOnBox = true) }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(textFieldFocusRequester)
                            .onFocusChanged { state ->
                                // Verlässt der Fokus das Feld (z. B. per Pfeiltaste), ist das Tippen beendet;
                                // beim nächsten OK beginnt es sauber neu statt in einem halb offenen Zustand
                                if (state.isFocused) {
                                    textFieldHadFocus = true
                                } else if (textFieldHadFocus) {
                                    stopEditing(keepFocusOnBox = false)
                                }
                            }
                    )
                }
            }

            if (query.isNotEmpty()) {
                TvFocusableButton(
                    text = "✕ Löschen",
                    onClick = {
                        stopEditing(keepFocusOnBox = false)
                        triggerSearch("")
                        // Der Knopf verschwindet mit dem Text, also den Fokus aufs Suchfeld setzen
                        runCatching { boxFocusRequester.requestFocus() }
                    }
                )
            }

            // Kategorien-Filter (Animes | Serien | Filme | Alle) direkt in derselben Zeile
            for (scopeOption in SearchScope.entries) {
                val isSelected = searchScope == scopeOption
                TvFocusableButton(
                    text = "${scopeOption.icon} ${scopeOption.label}",
                    isSelected = isSelected,
                    badgeColor = if (isSelected) AccentCyan else null,
                    onClick = {
                        uiState.searchScope = scopeOption
                    }
                )
            }
        }

        // Beliebte Suchvorschläge (NUR sichtbar wenn das Suchfeld leer ist)
        if (query.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Beliebt:",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                ScrollLazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(quickSuggestions) { suggestion ->
                        TvFocusableButton(
                            text = suggestion,
                            isSelected = false,
                            onClick = { triggerSearch(suggestion) }
                        )
                    }
                }
            }
        }

        // Ergebnisbereich: Voller Platz für die Cover-Karten
        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentCyan)
            }
        } else if (query.trim().length >= 2 && results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Keine Treffer für „$query“ in ${searchScope.label} gefunden.",
                    color = TextSecondary,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 148.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(results, key = { it.cleanSlug }) { anime ->
                    TvAnimeCard(
                        anime = anime,
                        isBookmarked = anime.cleanSlug in watchlistSlugs,
                        onClick = { onOpenAnime(anime) }
                    )
                }
            }
        }
    }
}

// =========================================================================
// TAB 3: GENRES & KATEGORIEN
// =========================================================================

@Composable
private fun GenresTabContent(
    uiState: HomeUiState,
    watchlist: List<AnimeCard>,
    onOpenAnime: (AnimeCard) -> Unit
) {
    val selectedGenreKey = uiState.selectedGenreKey
    val items = uiState.genreItems
    var isLoading by remember { mutableStateOf(false) }
    val watchlistSlugs = remember(watchlist) { watchlist.map { it.cleanSlug }.toSet() }

    LaunchedEffect(selectedGenreKey) {
        isLoading = true
        uiState.genreItems = runCatching { AniWorldScraper.fetchCategoryOrGenre(selectedGenreKey) }
            .getOrDefault(emptyList())
        isLoading = false
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Linke Seitenleiste: Genre-Auswahl
        LazyColumn(
            modifier = Modifier.width(230.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            items(AniWorldScraper.GENRE_CATEGORIES, key = { it.first }) { (key, title) ->
                TvFocusableButton(
                    text = title,
                    isSelected = selectedGenreKey == key,
                    onClick = { uiState.selectedGenreKey = key },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Rechtes Poster-Raster
        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentCyan)
                }
            } else if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Keine Einträge in dieser Kategorie gefunden.",
                        color = TextSecondary,
                        fontSize = 15.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 148.dp),
                    contentPadding = PaddingValues(bottom = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(items, key = { it.cleanSlug }) { anime ->
                        TvAnimeCard(
                            anime = anime,
                            isBookmarked = anime.cleanSlug in watchlistSlugs,
                            onClick = { onOpenAnime(anime) }
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 4: MERKLISTE & VERLAUF
// =========================================================================

@Composable
private fun WatchlistTabContent(
    watchlist: List<AnimeCard>,
    continueWatching: List<WatchProgressEntry>,
    onOpenAnime: (AnimeCard) -> Unit,
    onResumeWatch: (WatchProgressEntry) -> Unit
) {
    val watchlistSlugs = remember(watchlist) { watchlist.map { it.cleanSlug }.toSet() }

    if (watchlist.isEmpty() && continueWatching.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Deine Merkliste & dein Verlauf sind noch leer",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Füge Animes über „+ Zur Merkliste“ hinzu oder starte eine Folge.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        if (continueWatching.isNotEmpty()) {
            item {
                Column {
                    SectionHeader(
                        title = "▶️ Zuletzt geschaut (Weiterschauen)",
                        subtitle = "${continueWatching.size} Animes"
                    )
                    ScrollLazyRow(
                        contentPadding = PaddingValues(horizontal = 36.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(continueWatching, key = { it.animeSlug }) { entry ->
                            TvContinueWatchingCard(
                                entry = entry,
                                onClick = { onResumeWatch(entry) },
                                onDetailClick = {
                                    onOpenAnime(
                                        AnimeCard(
                                            slug = entry.animeSlug,
                                            title = entry.animeTitle,
                                            coverUrl = entry.coverUrl
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        if (watchlist.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "★ Gespeicherte Animes auf deiner Merkliste",
                    subtitle = "${watchlist.size} Titel"
                )
            }
            item {
                ScrollLazyRow(
                    contentPadding = PaddingValues(horizontal = 36.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    items(watchlist, key = { it.cleanSlug }) { anime ->
                        TvAnimeCard(
                            anime = anime,
                            isBookmarked = anime.cleanSlug in watchlistSlugs,
                            onClick = { onOpenAnime(anime) }
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB: EINSTELLUNGEN & MANUELLES UPDATE
// =========================================================================

@Composable
private fun SettingsTabContent(
    preferredLanguage: LanguageOption,
    onCycleLanguage: () -> Unit,
    onUpdateFound: (AppRelease) -> Unit
) {
    val scope = rememberCoroutineScope()
    val currentVersion = remember { UpdateManager.getCurrentVersionName() }
    val currentRepo = remember { UpdateManager.getTargetRepo() }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateStatusMessage by remember { mutableStateOf<String?>(null) }
    var updateStatusIsSuccess by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "⚙️ Einstellungen & Updates",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "App-Informationen, Sprache und manuelle Update-Prüfung",
            color = TextSecondary,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Karte: Updates
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(14.dp))
                .background(BgElevated)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "🚀 App-Version & Updates",
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Installierte Version: v$currentVersion",
                            color = AccentCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "GitHub-Quelle: $currentRepo",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }

                    if (isCheckingUpdate) {
                        CircularProgressIndicator(
                            color = AccentCyan,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        TvFocusableButton(
                            text = "🔍 Nach Updates suchen",
                            isPrimary = true,
                            onClick = {
                                isCheckingUpdate = true
                                updateStatusMessage = null
                                scope.launch {
                                    val result = UpdateManager.checkForUpdate(forceCheck = true)
                                    isCheckingUpdate = false
                                    when (result) {
                                        is UpdateCheckResult.Available -> {
                                            updateStatusIsSuccess = true
                                            updateStatusMessage = "Neue Version ${result.release.versionName} gefunden!"
                                            onUpdateFound(result.release)
                                        }
                                        UpdateCheckResult.UpToDate -> {
                                            updateStatusIsSuccess = true
                                            updateStatusMessage = "Du hast bereits die neueste Version (v$currentVersion)! 🎉"
                                        }
                                        is UpdateCheckResult.Failed -> {
                                            updateStatusIsSuccess = false
                                            updateStatusMessage = result.message
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                if (updateStatusMessage != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (updateStatusIsSuccess) AccentCyan.copy(alpha = 0.12f)
                                else Color(0xFFFF5252).copy(alpha = 0.12f)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = updateStatusMessage ?: "",
                            color = if (updateStatusIsSuccess) AccentCyan else Color(0xFFFF5252),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Karte: Spracheinstellungen
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(14.dp))
                .background(BgElevated)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "🌐 Standard-Audiosprache",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Aktuell: ${preferredLanguage.shortLabel} (${preferredLanguage.fullLabel})",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }

                TvFocusableButton(
                    text = "Ändern (${preferredLanguage.shortLabel})",
                    onClick = onCycleLanguage
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Karte: Handy-Fernbedienung (Kopplung)
        val pairedCount by CastPairing.pairedCount.collectAsState()
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(14.dp))
                .background(BgElevated)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "📱 Handy-Fernbedienung",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (pairedCount) {
                            0 -> "Noch kein Handy gekoppelt"
                            1 -> "1 Handy gekoppelt"
                            else -> "$pairedCount Handys gekoppelt"
                        },
                        color = AccentCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Zum Koppeln in der Handy-App aufs Cast-Symbol tippen, diesen PC wählen " +
                            "und den Code eingeben, der dann hier erscheint.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }

                if (pairedCount > 0) {
                    Spacer(modifier = Modifier.width(16.dp))
                    TvFocusableButton(
                        text = "Kopplungen entfernen",
                        onClick = { CastPairing.clearPairedDevices() }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Karte: Geräte- & System-Info
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(14.dp))
                .background(BgElevated)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "💻 Geräte-Information",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "System: ${System.getProperty("os.name")} (${System.getProperty("os.arch")})",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Text(
                    text = "Java: ${System.getProperty("java.runtime.version") ?: System.getProperty("java.version")}",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Text(
                    text = "Fernbedienungs-Dienst: ${if (PcCastManager.isRunning) "Aktiv" else "Aus"} (${PcCastManager.deviceName})",
                    color = AccentCyan,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        // Von TMDB vorgeschriebener Hinweis, sobald ihre Daten genutzt werden
        Text(
            text = if (TmdbArtwork.isConfigured) {
                "Bilder im Titelbild: The Movie Database (TMDB). Dieses Produkt nutzt die TMDB-API, " +
                    "ist aber nicht von TMDB unterstützt oder zertifiziert."
            } else {
                "Bilder von TMDB sind nicht eingerichtet (kein API-Schlüssel)."
            },
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(0.92f)
        )
    }
}

