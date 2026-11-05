package de.aniworld.tv.ui.screens

import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.awaitAll
import de.aniworld.tv.data.remote.TitleSearch
import de.aniworld.tv.data.remote.LetterboxdLists
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.shape.CircleShape
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.aniworld.tv.cast.CastPairing
import de.aniworld.tv.cast.TvCastManager
import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.model.AnimeCard
import de.aniworld.tv.data.model.CatalogSection
import de.aniworld.tv.data.model.LanguageOption
import de.aniworld.tv.data.model.LiveTvChannel
import de.aniworld.tv.data.model.SearchScope
import de.aniworld.tv.data.model.WatchProgressEntry
import de.aniworld.tv.data.remote.AniWorldScraper
import de.aniworld.tv.data.remote.FilmpalastScraper
import de.aniworld.tv.data.remote.HdFilmeScraper
import de.aniworld.tv.data.remote.SerienStreamScraper
import de.aniworld.tv.data.remote.TitlePreview
import de.aniworld.tv.data.remote.withoutStoAnime
import de.aniworld.tv.data.remote.StoAnimeFilter
import de.aniworld.tv.data.update.AppRelease
import de.aniworld.tv.data.update.UpdateCheckResult
import de.aniworld.tv.data.update.UpdateManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import de.aniworld.tv.ui.components.TvAnimeCard
import de.aniworld.tv.ui.components.TvContinueWatchingCard
import de.aniworld.tv.ui.components.TvFocusableButton
import de.aniworld.tv.ui.theme.AccentBlue
import de.aniworld.tv.ui.theme.AccentAmber
import de.aniworld.tv.ui.theme.AccentCyan
import de.aniworld.tv.ui.theme.BgElevated
import de.aniworld.tv.ui.theme.BgPrimary
import de.aniworld.tv.ui.theme.BgSurface
import de.aniworld.tv.ui.theme.FocusBorder
import de.aniworld.tv.ui.theme.TextMuted
import de.aniworld.tv.ui.theme.TextPrimary
import de.aniworld.tv.ui.theme.TextSecondary
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
    WATCHLIST("Watchlist"),
    SETTINGS("Einstellungen")
}

/**
 * Reiter der oberen Leiste in dieser Reihenfolge; Suche und Einstellungen sitzen als Symbole rechts.
 * Genres ist wie am PC vorerst ausgeblendet (gilt nur für Anime).
 */
private val NavBarTabs = listOf(
    MainNavTab.START,
    MainNavTab.MOVIES,
    MainNavTab.SERIES,
    MainNavTab.HOME,
    MainNavTab.LIVETV,
    MainNavTab.WATCHLIST
)

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
    var seriesPage by mutableStateOf(1)
    var seriesCanLoadMore by mutableStateOf(true)
    // Anime-Reiter: "OVERVIEW" oder ein AniWorld-Genre ("action", ...)
    var animeCategoryPath by mutableStateOf("OVERVIEW")
    var animeGenreItems by mutableStateOf<List<AnimeCard>>(emptyList())
    var animeLoadedPath by mutableStateOf<String?>(null)
    var animePage by mutableStateOf(1)
    var animeCanLoadMore by mutableStateOf(true)
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
    // Beim Reiterwechsel ein offenes Info-Feld schließen
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
                    isLoadingAnime = isLoading,
                    continueWatching = progressList,
                    watchlist = watchlist,
                    onRetryAnime = onRetry,
                    onOpenAnime = onOpenAnime,
                    onResumeWatch = onResumeWatch
                )

                MainNavTab.HOME -> AnimeTabContent(
                    uiState = uiState,
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
                    animeSections = sections,
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
                .padding(horizontal = 28.dp, vertical = 12.dp),
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
                    .padding(start = 24.dp),
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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

/** Anime-Genres für die Genre-Seite ("Beliebt"/"Neu" stecken schon in der Übersicht). */
private val AnimeGenres = AniWorldScraper.GENRE_CATEGORIES.filter { it.first !in setOf("beliebt", "neu", "isekai") }

/**
 * Anime-Reiter: Übersicht (Titelbild, Weiterschauen, Reihen) oder Genre-Seite – wie bei Filmen
 * und Serien über den Umschalter "Übersicht & Highlights" / "Genre".
 */
@Composable
private fun AnimeTabContent(
    uiState: HomeUiState,
    sections: List<CatalogSection>,
    continueWatching: List<WatchProgressEntry>,
    watchlist: List<AnimeCard>,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onOpenAnime: (AnimeCard) -> Unit,
    onResumeWatch: (WatchProgressEntry) -> Unit
) {
    val selectedPath = uiState.animeCategoryPath
    val scope = rememberCoroutineScope()
    var lastGenre by remember { mutableStateOf(uiState.animeLoadedPath ?: AnimeGenres.first().first) }
    var genreLoading by remember { mutableStateOf(false) }
    var genreReloadKey by remember { mutableStateOf(0) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var loadMoreFailed by remember { mutableStateOf(false) }

    LaunchedEffect(selectedPath, genreReloadKey) {
        if (selectedPath == "OVERVIEW") return@LaunchedEffect
        lastGenre = selectedPath
        if (uiState.animeLoadedPath == selectedPath && uiState.animeGenreItems.isNotEmpty()) return@LaunchedEffect
        genreLoading = true
        val firstPage = runCatching { AniWorldScraper.fetchCategoryOrGenre(selectedPath) }.getOrDefault(emptyList())
        uiState.animeGenreItems = firstPage.distinctBy { it.cleanSlug }
        uiState.animeLoadedPath = selectedPath
        uiState.animePage = 1
        uiState.animeCanLoadMore = firstPage.isNotEmpty()
        loadMoreFailed = false
        genreLoading = false
    }

    // Nächste Seite des Genres anhängen ("Mehr Anime laden")
    fun loadMoreAnime() {
        if (isLoadingMore || !uiState.animeCanLoadMore) return
        val path = uiState.animeLoadedPath ?: return
        val nextPage = uiState.animePage + 1
        isLoadingMore = true
        loadMoreFailed = false
        scope.launch {
            val result = runCatching { AniWorldScraper.fetchCategoryOrGenre(path, nextPage) }
            if (uiState.animeLoadedPath == path) {
                result.onSuccess { more ->
                    val known = uiState.animeGenreItems.mapTo(HashSet()) { it.cleanSlug }
                    val fresh = more.filter { known.add(it.cleanSlug) }
                    uiState.animeGenreItems = uiState.animeGenreItems + fresh
                    uiState.animePage = nextPage
                    uiState.animeCanLoadMore = fresh.isNotEmpty()
                }.onFailure {
                    // Hinter der letzten Seite antwortet AniWorld mit 404: dann ist Schluss
                    if (it.message?.contains("404") == true) uiState.animeCanLoadMore = false else loadMoreFailed = true
                }
            }
            isLoadingMore = false
        }
    }

    val switcher: @Composable () -> Unit = {
        CatalogModeSwitcher(
            isOverview = selectedPath == "OVERVIEW",
            onOverview = { uiState.animeCategoryPath = "OVERVIEW" },
            onGenre = { uiState.animeCategoryPath = lastGenre }
        )
    }

    if (selectedPath == "OVERVIEW") {
        HomeTabContent(
            switcher = switcher,
            sections = sections,
            continueWatching = continueWatching,
            watchlist = watchlist,
            isLoading = isLoading,
            errorMessage = errorMessage,
            onRetry = onRetry,
            onOpenAnime = onOpenAnime,
            onResumeWatch = onResumeWatch
        )
        return
    }

    val watchlistSlugs = remember(watchlist) { watchlist.map { it.cleanSlug }.toSet() }
    GenreBrowsePage(
        genres = AnimeGenres,
        selectedPath = selectedPath,
        onSelectGenre = { uiState.animeCategoryPath = it },
        items = uiState.animeGenreItems,
        isLoading = genreLoading || uiState.animeLoadedPath != selectedPath,
        loadingText = "Lade Animes von AniWorld...",
        emptyText = "Keine Animes gefunden. AniWorld ist gerade nicht erreichbar.",
        onRetry = { genreReloadKey++ },
        watchlistSlugs = watchlistSlugs,
        onOpenAnime = onOpenAnime,
        switcher = switcher,
        gridFooter = {
            if (uiState.animeCanLoadMore) {
                item(key = "load_more_anime", span = { GridItemSpan(maxLineSpan) }) {
                    LoadMoreButton(
                        text = when {
                            isLoadingMore -> "⏳ Lade weitere Animes..."
                            loadMoreFailed -> "⚠️ Laden fehlgeschlagen – erneut versuchen"
                            else -> "⬇️ Mehr Anime laden"
                        },
                        onClick = { loadMoreAnime() }
                    )
                }
            }
        }
    )
}

@Composable
private fun HomeTabContent(
    switcher: @Composable () -> Unit,
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

    // Titelbild: 6 zufällige Animes zum Durchblättern
    val heroItems = remember(sections) { TitlePreview.randomPicks(sections.flatMap { it.items }) }
    val watchlistSlugs = remember(watchlist) { watchlist.map { it.cleanSlug }.toSet() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        // 1. Titelbild mit zufälligen Vorschlägen
        if (heroItems.isNotEmpty()) {
            item(key = "hero_banner") {
                HeroCarousel(items = heroItems, watchlistSlugs = watchlistSlugs, onOpenAnime = onOpenAnime)
            }
        }
        // Umschalter "Übersicht & Highlights" / "Genre"
        item(key = "anime_switcher") { switcher() }

        // 2. Weiterschauen-Zeile (falls vorhanden)
        if (continueWatching.isNotEmpty()) {
            item(key = "row_continue_watching") {
                Column {
                    SectionHeader(
                        title = "▶️ Weiterschauen",
                        subtitle = "${continueWatching.size} Animes im Verlauf"
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
                    onOpenAnime = onOpenAnime
                )
            }
        }

        // 4. Alle AniWorld-Katalog-Reihen (Beliebt, Neue Animes, Derzeit beliebt, etc.)
        items(sections, key = { it.id }) { section ->
            CatalogRowSection(
                title = section.title,
                items = section.items,
                watchlistSlugs = watchlistSlugs,
                onOpenAnime = onOpenAnime
            )
        }
    }
}

@Composable
internal fun SectionHeader(title: String, subtitle: String = "") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Lange Titel (z. B. Letterboxd-Listen) kürzen, damit die Angabe rechts in einer Zeile bleibt
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = subtitle,
                color = TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false
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
        // Nur echte Genres; "Top"/"Neu" stecken schon in der Übersicht
        genres = FilmpalastScraper.CATEGORIES.filter { "/genre/" in it.first },
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
                    LoadMoreButton(
                        text = when {
                            isLoadingMore -> "⏳ Lade weitere Filme..."
                            loadMoreFailed -> "⚠️ Laden fehlgeschlagen – erneut versuchen"
                            else -> "⬇️ Mehr Filme laden"
                        },
                        onClick = { loadMoreMovies() }
                    )
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
    val scope = rememberCoroutineScope()

    fun needsLoad(path: String): Boolean = if (path == "OVERVIEW") {
        uiState.seriesOverviewSections.isEmpty()
    } else {
        uiState.seriesLoadedPath != path || uiState.seriesCategoryItems.isEmpty()
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
            uiState.seriesOverviewSections = runCatching { SerienStreamScraper.fetchHomeSections() }.getOrDefault(emptyList())
        } else {
            val firstPage = runCatching { SerienStreamScraper.fetchCategory(path) }.getOrDefault(emptyList())
            uiState.seriesCategoryItems = firstPage.distinctBy { it.cleanSlug }
            uiState.seriesLoadedPath = path
            uiState.seriesPage = 1
            uiState.seriesCanLoadMore = firstPage.isNotEmpty()
            loadMoreFailed = false
        }
        isLoading = false
    }

    fun loadMoreSeries() {
        if (isLoadingMore || !uiState.seriesCanLoadMore) return
        val path = uiState.seriesLoadedPath ?: return
        val nextPage = uiState.seriesPage + 1
        isLoadingMore = true
        loadMoreFailed = false
        scope.launch {
            val result = runCatching { SerienStreamScraper.fetchCategory(path, nextPage) }
            if (uiState.seriesLoadedPath == path) {
                result.onSuccess { more ->
                    val known = uiState.seriesCategoryItems.mapTo(HashSet()) { it.cleanSlug }
                    val fresh = more.filter { known.add(it.cleanSlug) }
                    uiState.seriesCategoryItems = uiState.seriesCategoryItems + fresh
                    uiState.seriesPage = nextPage
                    uiState.seriesCanLoadMore = fresh.isNotEmpty()
                }.onFailure {
                    loadMoreFailed = true
                }
            }
            isLoadingMore = false
        }
    }

    // Anime gibt es im Anime-Reiter (AniWorld): S.to-Anime hier ausblenden
    val stoAnimeSlugs by StoAnimeFilter.slugs.collectAsState()
    val overviewWithoutAnime = remember(uiState.seriesOverviewSections, stoAnimeSlugs) {
        uiState.seriesOverviewSections
            .map { it.copy(items = it.items.withoutStoAnime(stoAnimeSlugs)) }
            .filter { it.items.isNotEmpty() }
    }
    val categoryWithoutAnime = remember(uiState.seriesCategoryItems, stoAnimeSlugs) {
        uiState.seriesCategoryItems.withoutStoAnime(stoAnimeSlugs)
    }
    CatalogBrowser(
        genres = SerienStreamScraper.CATEGORIES.filter { "/genre/" in it.first && it.first != "/genre/anime" },
        selectedCategoryPath = selectedCategoryPath,
        onSelectCategory = { uiState.seriesCategoryPath = it },
        overviewSections = overviewWithoutAnime,
        categoryItems = categoryWithoutAnime,
        isLoading = isLoading ||
            (selectedCategoryPath != "OVERVIEW" && uiState.seriesLoadedPath != selectedCategoryPath),
        loadingText = "Lade Serien...",
        emptyText = "Keine Serien gefunden.",
        onRetry = { reloadKey++ },
        watchlist = watchlist,
        onOpenAnime = onOpenAnime,
        gridFooter = {
            if (uiState.seriesCanLoadMore) {
                item(key = "load_more_series", span = { GridItemSpan(maxLineSpan) }) {
                    LoadMoreButton(
                        text = when {
                            isLoadingMore -> "⏳ Lade weitere Serien..."
                            loadMoreFailed -> "⚠️ Laden fehlgeschlagen – erneut versuchen"
                            else -> "⬇️ Mehr Serien laden"
                        },
                        onClick = { loadMoreSeries() }
                    )
                }
            }
        }
    )
}

/**
 * Gemeinsamer Aufbau von Filme- und Serien-Tab (wie am PC): Übersicht mit Titelbild, Umschalter
 * "Übersicht & Highlights" / "Genre" und Reihen – oder die Genre-Seite (links Genres, rechts Titel).
 * [gridFooter] hängt z. B. "Mehr Filme laden" ans Raster an.
 */
@Composable
private fun CatalogBrowser(
    genres: List<Pair<String, String>>,
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

    // Titelbild: 6 zufällige Titel dieses Bereichs (Filme bzw. Serien)
    val heroItems = remember(overviewSections) { TitlePreview.randomPicks(overviewSections.flatMap { it.items }) }

    // Zuletzt gewähltes Genre merken, damit "Genre" dort weitermacht
    var lastGenre by remember { mutableStateOf(genres.firstOrNull()?.first) }
    LaunchedEffect(selectedCategoryPath) {
        if (genres.any { it.first == selectedCategoryPath }) lastGenre = selectedCategoryPath
    }
    val switcher: @Composable () -> Unit = {
        CatalogModeSwitcher(
            isOverview = isOverview,
            onOverview = { onSelectCategory("OVERVIEW") },
            onGenre = { (lastGenre ?: genres.firstOrNull()?.first)?.let(onSelectCategory) }
        )
    }

    if (isOverview) {
        when {
            isLoading -> CatalogLoading(loadingText)
            overviewSections.isEmpty() -> CatalogEmpty(emptyText, onRetry)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (heroItems.isNotEmpty()) {
                    item(key = "catalog_hero") {
                        HeroCarousel(items = heroItems, watchlistSlugs = watchlistSlugs, onOpenAnime = onOpenAnime)
                    }
                }
                item(key = "catalog_switcher") { switcher() }
                items(overviewSections, key = { it.id }) { section ->
                    CatalogRowSection(
                        title = section.title,
                        items = section.items,
                        watchlistSlugs = watchlistSlugs,
                        onOpenAnime = onOpenAnime
                    )
                }
            }
        }
        return
    }

    GenreBrowsePage(
        genres = genres,
        selectedPath = selectedCategoryPath,
        onSelectGenre = onSelectCategory,
        items = categoryItems,
        isLoading = isLoading,
        loadingText = loadingText,
        emptyText = emptyText,
        onRetry = onRetry,
        watchlistSlugs = watchlistSlugs,
        onOpenAnime = onOpenAnime,
        switcher = switcher,
        gridFooter = gridFooter
    )
}

/** Genre-Seite (Filme, Serien, Anime): oben der Umschalter, links die Genres, rechts die Titel. */
@Composable
private fun GenreBrowsePage(
    genres: List<Pair<String, String>>,
    selectedPath: String,
    onSelectGenre: (String) -> Unit,
    items: List<AnimeCard>,
    isLoading: Boolean,
    loadingText: String,
    emptyText: String,
    onRetry: () -> Unit,
    watchlistSlugs: Set<String>,
    onOpenAnime: (AnimeCard) -> Unit,
    switcher: @Composable () -> Unit,
    gridFooter: LazyGridScope.() -> Unit = {}
) {
    // OK auf einem Titel: an seiner Stelle im Raster erscheint die Info-Kachel (wie in den Reihen)
    val expandedState = LocalExpandedTitle.current
    val expandedSlug = expandedState.key?.takeIf { it.startsWith("genre|") }?.removePrefix("genre|")
    val gridState = rememberLazyGridState()
    // Nach dem Schließen springt der Fokus zurück auf die Karte, sonst wäre er auf dem TV verloren
    val cardFocus = remember { FocusRequester() }
    var refocusSlug by remember { mutableStateOf<String?>(null) }

    // Anderes Genre gewählt: Kachel schließen und oben beginnen
    LaunchedEffect(selectedPath) {
        if (expandedState.key?.startsWith("genre|") == true) expandedState.key = null
        runCatching { gridState.scrollToItem(0) }
    }
    // Kachel ins Bild holen, falls sie unten oder oben abgeschnitten ist
    LaunchedEffect(expandedSlug) {
        val index = items.indexOfFirst { it.cleanSlug == expandedSlug }
        if (index < 0) return@LaunchedEffect
        delay(60)
        val info = gridState.layoutInfo
        val visible = info.visibleItemsInfo.firstOrNull { it.index == index }
        if (visible == null || visible.offset.y < 0 || visible.offset.y + visible.size.height > info.viewportEndOffset) {
            gridState.animateScrollToItem(index)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(12.dp))
        switcher()
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxSize()) {
            // Links: Genres untereinander
            LazyColumn(
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(start = 36.dp, end = 12.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(genres, key = { it.first }) { (path, label) ->
                    GenreListItem(
                        label = label.dropWhile { !it.isLetter() }.trim(),
                        selected = path == selectedPath,
                        onClick = { onSelectGenre(path) }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp)
                    .background(Color.White.copy(alpha = 0.06f))
            )

            // Rechts: Titel des gewählten Genres
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when {
                    isLoading -> CatalogLoading(loadingText)
                    items.isEmpty() -> CatalogEmpty(emptyText, onRetry)
                    else -> LazyVerticalGrid(
                        state = gridState,
                        // Auf dem TV (960 dp breit) passen so 4 Karten neben die Genre-Liste
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        contentPadding = PaddingValues(start = 24.dp, end = 36.dp, top = 4.dp, bottom = 36.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items,
                            key = { it.cleanSlug },
                            // Die Kachel ist 4 Karten breit (bei wenig Platz entsprechend weniger)
                            span = { card -> GridItemSpan(if (card.cleanSlug == expandedSlug) minOf(4, maxLineSpan) else 1) }
                        ) { card ->
                            if (card.cleanSlug == expandedSlug) {
                                TitleInfoPanel(
                                    anime = card,
                                    isBookmarked = card.cleanSlug in watchlistSlugs,
                                    onOpenDetail = { onOpenAnime(card) },
                                    onToggleWatchlist = { LocalStorage.toggleWatchlist(card) },
                                    onClose = { hadFocus ->
                                        if (hadFocus) refocusSlug = card.cleanSlug
                                        expandedState.key = null
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(TitleTileHeight)
                                )
                            } else {
                                val isRefocusTarget = card.cleanSlug == refocusSlug
                                TvAnimeCard(
                                    anime = card,
                                    isBookmarked = card.cleanSlug in watchlistSlugs,
                                    modifier = if (isRefocusTarget) Modifier.focusRequester(cardFocus) else Modifier,
                                    onClick = { expandedState.key = "genre|${card.cleanSlug}" }
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
                        gridFooter()
                    }
                }
            }
        }
    }
}

/**
 * Umschalter "Übersicht & Highlights" / "Genre" (unter dem Titelbild bzw. oben auf der Genre-Seite).
 * Nach dem Umschalten wird die Seite neu aufgebaut: Der Fokus geht dann wieder auf den gewählten
 * Knopf, sonst landete er auf dem TV ganz oben in der Navigationsleiste.
 */
@Composable
private fun CatalogModeSwitcher(isOverview: Boolean, onOverview: () -> Unit, onGenre: () -> Unit) {
    val selectedFocus = remember { FocusRequester() }
    LaunchedEffect(isOverview) {
        if (SwitcherFocusPending.value) {
            SwitcherFocusPending.value = false
            delay(80)
            runCatching { selectedFocus.requestFocus() }
        }
    }
    Row(
        modifier = Modifier.padding(horizontal = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavTextTab(
            label = "Übersicht & Highlights",
            selected = isOverview,
            modifier = if (isOverview) Modifier.focusRequester(selectedFocus) else Modifier,
            onClick = {
                if (!isOverview) SwitcherFocusPending.value = true
                onOverview()
            }
        )
        NavTextTab(
            label = "Genre",
            selected = !isOverview,
            modifier = if (!isOverview) Modifier.focusRequester(selectedFocus) else Modifier,
            onClick = {
                if (isOverview) SwitcherFocusPending.value = true
                onGenre()
            }
        )
    }
}

/** Wurde gerade umgeschaltet? Dann holt sich der neu aufgebaute Umschalter den Fokus. */
private val SwitcherFocusPending = mutableStateOf(false)

/** Ein Eintrag in der Genre-Liste links; der gewählte mit heller Fläche und cyanfarbenem Strich. */
@Composable
private fun GenreListItem(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    selected -> Color.White.copy(alpha = 0.12f)
                    focused -> Color.White.copy(alpha = 0.06f)
                    else -> Color.Transparent
                }
            )
            .border(2.dp, if (focused) FocusBorder else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            // clickable ist selbst fokussierbar; OK auf der Fernbedienung löst es aus
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) AccentCyan else Color.Transparent)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = if (selected || focused) TextPrimary else TextSecondary,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** "Mehr … laden" unter einem Raster, über die ganze Breite zentriert. */
@Composable
private fun LoadMoreButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        TvFocusableButton(text = text, isPrimary = true, onClick = onClick)
    }
}

@Composable
private fun CatalogLoading(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = AccentCyan)
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = text, color = TextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun CatalogEmpty(text: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = text, color = TextSecondary, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(14.dp))
            TvFocusableButton(text = "Erneut versuchen", isPrimary = true, onClick = onRetry)
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
    animeSections: List<CatalogSection>,
    onOpenAnime: (AnimeCard) -> Unit
) {
    val query = uiState.searchQuery
    val results = uiState.searchResults
    val searchScope = uiState.searchScope
    var isSearching by remember { mutableStateOf(false) }
    val watchlistSlugs = remember(watchlist) { watchlist.map { it.cleanSlug }.toSet() }
    val stoAnimeSlugs by StoAnimeFilter.slugs.collectAsState()

    // ---------- Vorschläge bei leerer Suche: zufällige Titel des Reiters ----------
    var suggestionMovies by remember { mutableStateOf(searchSuggestionMovies) }
    var suggestionSeries by remember { mutableStateOf(searchSuggestionSeries) }
    LaunchedEffect(Unit) {
        coroutineScope {
            if (suggestionMovies.isEmpty()) launch {
                val loaded = uiState.startTopMovies.ifEmpty {
                    runCatching { FilmpalastScraper.fetchCategory("/movies/top") }.getOrDefault(emptyList())
                } + uiState.moviesOverviewSections.flatMap { it.items }
                suggestionMovies = loaded.distinctBy { it.cleanSlug }
                searchSuggestionMovies = suggestionMovies
            }
            if (suggestionSeries.isEmpty()) launch {
                val loaded = uiState.startPopularSeries.ifEmpty {
                    runCatching { SerienStreamScraper.fetchCategory("/beliebte-serien") }.getOrDefault(emptyList())
                } + uiState.seriesOverviewSections.flatMap { it.items }
                suggestionSeries = loaded.distinctBy { it.cleanSlug }
                searchSuggestionSeries = suggestionSeries
            }
        }
    }
    val suggestionAnime = remember(animeSections) { animeSections.flatMap { it.items }.distinctBy { it.cleanSlug } }
    val suggestions = remember(searchScope, suggestionMovies, suggestionSeries, suggestionAnime, stoAnimeSlugs) {
        val movies = suggestionMovies.shuffled()
        val series = suggestionSeries.withoutStoAnime(stoAnimeSlugs).shuffled()
        val anime = suggestionAnime.shuffled()
        when (searchScope) {
            SearchScope.MOVIES -> movies.take(36)
            SearchScope.SERIES -> series.take(36)
            SearchScope.ANIMES -> anime.take(36)
            // Abwechselnd Film, Serie, Anime
            SearchScope.ALL -> (0 until 12).flatMap { i -> listOfNotNull(movies.getOrNull(i), series.getOrNull(i), anime.getOrNull(i)) }
        }.filter { it.coverUrl.isNotBlank() }
    }

    LaunchedEffect(query, searchScope) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            uiState.searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(300)

        // Seiten können nur "Titel enthält" – darum mit Teilbegriffen abfragen und danach selbst
        // nach Wortanfängen filtern (TitleSearch): "sp" → "Marvel's Spider-Man", "spiderman" → "Spider-Man"
        suspend fun siteSearch(search: suspend (String) -> List<AnimeCard>): List<AnimeCard> = coroutineScope {
            TitleSearch.siteQueries(trimmed)
                .map { q -> async { runCatching { search(q) }.getOrDefault(emptyList()) } }
                .awaitAll()
                .flatten()
        }
        suspend fun animes() = runCatching { AniWorldScraper.searchCatalog(trimmed) }.getOrDefault(emptyList())
        suspend fun movies() = TitleSearch.filterAndRank(
            siteSearch { FilmpalastScraper.searchMovies(it) }.filterNot { it.title.contains("*ENGLISH*", ignoreCase = true) || SingleEpisodeTitle.containsMatchIn(it.title) },
            trimmed
        )
        suspend fun series() = runCatching { SerienStreamScraper.searchSeries(trimmed) }.getOrDefault(emptyList())
            .withoutStoAnime(stoAnimeSlugs)

        val combined = coroutineScope {
            when (searchScope) {
                SearchScope.ANIMES -> animes()
                SearchScope.SERIES -> series()
                SearchScope.MOVIES -> movies()
                SearchScope.ALL -> {
                    val a = async { animes() }
                    val sr = async { series() }
                    val m = async { movies() }
                    TitleSearch.filterAndRank(m.await() + sr.await() + a.await(), trimmed)
                }
            }
        }
        uiState.searchResults = combined.distinctBy { it.cleanSlug }.take(60)
        isSearching = false

        // S.to-Treffer haben keine Cover (sie kommen aus der A–Z-Liste): für die ersten nachladen
        val missing = uiState.searchResults.filter { it.coverUrl.isBlank() }.take(24)
        if (missing.isNotEmpty()) {
            val slots = Semaphore(6)
            coroutineScope {
                missing.forEach { card ->
                    launch {
                        val cover = searchCoverCache[card.cleanSlug] ?: slots.withPermit {
                            TitlePreview.loadDetail(card.cleanSlug)?.coverUrl.orEmpty()
                        }.also { if (it.isNotBlank()) searchCoverCache[card.cleanSlug] = it }
                        if (cover.isNotBlank() && uiState.searchQuery.trim() == trimmed) {
                            uiState.searchResults = uiState.searchResults.map {
                                if (it.cleanSlug == card.cleanSlug) it.copy(coverUrl = cover) else it
                            }
                        }
                    }
                }
            }
        }
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
        // Kopf: Suchfeld (nicht über die ganze Breite), darunter die Filter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Suchfeld
            Box(
                modifier = Modifier
                    .width(520.dp)
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
                                // Verlässt der Fokus das Feld (z. B. per Steuerkreuz), ist das Tippen beendet;
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

        }

        // Filter unter dem Suchfeld: Filme · Serien · Anime · Alle
        Row(
            modifier = Modifier.padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                SearchScope.MOVIES to "Filme",
                SearchScope.SERIES to "Serien",
                SearchScope.ANIMES to "Anime",
                SearchScope.ALL to "Alle"
            ).forEach { (scopeOption, label) ->
                NavTextTab(
                    label = label,
                    selected = searchScope == scopeOption,
                    onClick = { uiState.searchScope = scopeOption }
                )
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
        } else if (query.trim().length < 2) {
            // Leere Suche: zufällige Titel des Reiters zum Stöbern
            ExpandableTitleGrid(
                items = suggestions,
                header = when (searchScope) {
                    SearchScope.MOVIES -> "Filme entdecken"
                    SearchScope.SERIES -> "Serien entdecken"
                    SearchScope.ANIMES -> "Anime entdecken"
                    SearchScope.ALL -> "Entdecken"
                },
                keyPrefix = "suggest-${searchScope.name}",
                watchlistSlugs = watchlistSlugs,
                onOpenAnime = onOpenAnime
            )
        } else {
            ExpandableTitleGrid(
                items = results,
                header = "${results.size} Treffer für „${query.trim()}“",
                keyPrefix = "search",
                watchlistSlugs = watchlistSlugs,
                onOpenAnime = onOpenAnime
            )
        }
    }
}

/** Filmpalast führt auch einzelne Serienfolgen als "Film" ("Spider-Noir S01E08") – in der Filmsuche weglassen. */
private val SingleEpisodeTitle = Regex("""\bS\d{1,2}E\d{1,3}\b""", RegexOption.IGNORE_CASE)

/** Vorschläge der Suche pro App-Start merken (Reiterwechsel lädt nicht neu). */
private var searchSuggestionMovies: List<AnimeCard> = emptyList()
private var searchSuggestionSeries: List<AnimeCard> = emptyList()
private val searchCoverCache = java.util.concurrent.ConcurrentHashMap<String, String>()

/**
 * Raster mit Überschrift; ein Klick auf einen Titel macht ihn (wie in den Reihen) zur Info-Kachel,
 * vier Karten breit, die übrigen rücken nach.
 */
@Composable
private fun ExpandableTitleGrid(
    items: List<AnimeCard>,
    header: String,
    keyPrefix: String,
    watchlistSlugs: Set<String>,
    onOpenAnime: (AnimeCard) -> Unit
) {
    val expandedState = LocalExpandedTitle.current
    val expandedSlug = expandedState.key?.takeIf { it.startsWith("$keyPrefix|") }?.removePrefix("$keyPrefix|")
    val gridState = rememberLazyGridState()
    // Nach dem Schließen springt der Fokus zurück auf die Karte, sonst wäre er auf dem TV verloren
    val cardFocus = remember { FocusRequester() }
    var refocusSlug by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(keyPrefix, items.firstOrNull()?.cleanSlug) {
        if (expandedState.key?.startsWith("$keyPrefix|") == true && items.none { it.cleanSlug == expandedSlug }) {
            expandedState.key = null
        }
    }
    LaunchedEffect(expandedSlug) {
        val index = items.indexOfFirst { it.cleanSlug == expandedSlug }
        if (index < 0) return@LaunchedEffect
        delay(60)
        val info = gridState.layoutInfo
        val visible = info.visibleItemsInfo.firstOrNull { it.index == index + 1 }
        if (visible == null || visible.offset.y < 0 || visible.offset.y + visible.size.height > info.viewportEndOffset) {
            gridState.animateScrollToItem(index + 1)
        }
    }
    LazyVerticalGrid(
        state = gridState,
        // Auf dem TV (960 dp breit) passen so 6 Karten nebeneinander
        columns = GridCells.Adaptive(minSize = 130.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Text(text = header, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        items(
            items,
            key = { it.cleanSlug },
            span = { card -> GridItemSpan(if (card.cleanSlug == expandedSlug) minOf(4, maxLineSpan) else 1) }
        ) { card ->
            if (card.cleanSlug == expandedSlug) {
                TitleInfoPanel(
                    anime = card,
                    isBookmarked = card.cleanSlug in watchlistSlugs,
                    onOpenDetail = { onOpenAnime(card) },
                    onToggleWatchlist = { LocalStorage.toggleWatchlist(card) },
                    onClose = { hadFocus ->
                        if (hadFocus) refocusSlug = card.cleanSlug
                        expandedState.key = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TitleTileHeight)
                )
            } else {
                val isRefocusTarget = card.cleanSlug == refocusSlug
                TvAnimeCard(
                    anime = card,
                    isBookmarked = card.cleanSlug in watchlistSlugs,
                    modifier = if (isRefocusTarget) Modifier.focusRequester(cardFocus) else Modifier,
                    onClick = { expandedState.key = "$keyPrefix|${card.cleanSlug}" }
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
    val letterboxdUsers by LocalStorage.letterboxdUsersFlow.collectAsState()
    // Empfehlungsliste bei jedem Öffnen des Reiters frisch laden (sie ändert sich, wenn Ilkem am PC etwas hinzufügt)
    val featuredKey = remember {
        letterboxdFilmsCache.keys.removeIf { it.startsWith("list:${LetterboxdLists.FeaturedList.PATH}#") }
        "list:${LetterboxdLists.FeaturedList.PATH}#${System.currentTimeMillis()}"
    }

    // Beliebte Letterboxd-Listen (einmal pro App-Start laden)
    var popularLists by remember { mutableStateOf(letterboxdPopularListsCache) }
    var popularError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (popularLists != null) return@LaunchedEffect
        // Eine mehr laden, falls die eigene Empfehlungsliste darunter ist (sie steht ohnehin oben)
        runCatching { LetterboxdLists.popularLists(limit = 5).filter { it.path != LetterboxdLists.FeaturedList.PATH }.take(4) }
            .onSuccess { popularLists = it; letterboxdPopularListsCache = it }
            .onFailure { popularError = "Letterboxd-Listen nicht erreichbar (${it.message})." }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        if (continueWatching.isNotEmpty()) {
            item(key = "wl_continue") {
                Column {
                    SectionHeader(title = "▶️ Weiterschauen", subtitle = "${continueWatching.size} Titel")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 36.dp, vertical = 10.dp),
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

        item(key = "wl_own") {
            if (watchlist.isNotEmpty()) {
                CatalogRowSection(
                    title = "★ Meine Watchlist",
                    items = watchlist,
                    watchlistSlugs = watchlistSlugs,
                    onOpenAnime = onOpenAnime
                )
            } else {
                Column {
                    SectionHeader(title = "★ Meine Watchlist")
                    Text(
                        text = "Noch leer – füge Titel über „+“ in der Info-Kachel oder „+ Watchlist“ hinzu.",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 36.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // ------------------- Letterboxd -------------------
        item(key = "wl_lb_header") {
            Column(modifier = Modifier.padding(horizontal = 36.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(LetterboxdGreen))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "Letterboxd-Watchlists", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Gib deinen oder einen anderen Letterboxd-Benutzernamen ein. Angezeigt werden die Filme " +
                        "der Watchlist, die es bei Filmpalast zum Streamen gibt.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                LetterboxdUserInput()
                if (letterboxdUsers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        letterboxdUsers.forEach { user ->
                            NavTextTab(label = "@$user  ✕", selected = false, onClick = { LocalStorage.removeLetterboxdUser(user) })
                        }
                    }
                }
            }
        }

        letterboxdUsers.forEach { user ->
            item(key = "wl_lb_user_$user") {
                LetterboxdFilmsRow(
                    cacheKey = "user:$user",
                    title = "🎬 Watchlist von @$user",
                    watchlistSlugs = watchlistSlugs,
                    onOpenAnime = onOpenAnime,
                    load = { LetterboxdLists.watchlist(user, maxPages = 3) }
                )
            }
        }

        item(key = "wl_lb_lists_header") {
            Column(modifier = Modifier.padding(horizontal = 36.dp)) {
                Text(text = "Interessante Letterboxd-Listen", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = popularError ?: if (popularLists == null) "Lade beliebte Listen …" else "Beliebte Listen der Letterboxd-Community – nur Filme, die es hier gibt.",
                    color = if (popularError != null) AccentAmber else TextSecondary,
                    fontSize = 13.sp
                )
            }
        }

        // Eigene Empfehlungsliste immer zuerst
        item(key = "wl_lb_featured") {
            LetterboxdFilmsRow(
                cacheKey = featuredKey,
                title = "⭐ ${LetterboxdLists.FeaturedList.NAME}",
                watchlistSlugs = watchlistSlugs,
                onOpenAnime = onOpenAnime,
                load = { LetterboxdLists.listFilms(LetterboxdLists.FeaturedList.PATH, maxPages = 2).take(100) }
            )
        }

        popularLists.orEmpty().forEach { list ->
            item(key = "wl_lb_list_${list.path}") {
                LetterboxdFilmsRow(
                    cacheKey = "list:${list.path}",
                    title = "📋 ${list.name} · von ${list.owner}",
                    watchlistSlugs = watchlistSlugs,
                    onOpenAnime = onOpenAnime,
                    load = { LetterboxdLists.listFilms(list.path, maxPages = 1).take(60) }
                )
            }
        }
    }
}

private val LetterboxdGreen = Color(0xFF00E054)

/** Beliebte Listen und geladene Letterboxd-Filme pro Sitzung merken (Reiterwechsel lädt nicht neu). */
private var letterboxdPopularListsCache: List<LetterboxdLists.ListInfo>? = null
private val letterboxdFilmsCache = java.util.concurrent.ConcurrentHashMap<String, List<LetterboxdLists.Film>>()

/** Eingabe für einen Letterboxd-Benutzernamen (oder Profil-Link); OK öffnet die Bildschirmtastatur. */
@Composable
private fun LetterboxdUserInput() {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var focused by remember { mutableStateOf(false) }
    fun submit() {
        val name = LetterboxdLists.normalizeUsername(text)
        if (name == null) {
            error = "Bitte einen Letterboxd-Benutzernamen eingeben (z. B. dave) oder den Profil-Link."
            return
        }
        LocalStorage.addLetterboxdUser(name)
        text = ""
        error = null
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .width(380.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(BgSurface)
                .border(if (focused) 2.dp else 1.dp, if (focused) FocusBorder else Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (text.isEmpty()) {
                Text(text = "Letterboxd-Benutzername, z. B. dave", color = TextMuted, fontSize = 14.sp)
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it; error = null },
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(AccentCyan),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
            )
        }
        TvFocusableButton(text = "+ Hinzufügen", isPrimary = true, onClick = { submit() })
    }
    error?.let {
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = it, color = AccentAmber, fontSize = 13.sp)
    }
}

/**
 * Reihe mit Filmen einer Letterboxd-Liste/-Watchlist. Lädt die Liste, gleicht jeden Film mit
 * Filmpalast ab und zeigt nach und nach die, die sich streamen lassen.
 */
@Composable
private fun LetterboxdFilmsRow(
    cacheKey: String,
    title: String,
    watchlistSlugs: Set<String>,
    onOpenAnime: (AnimeCard) -> Unit,
    load: suspend () -> List<LetterboxdLists.Film>
) {
    var films by remember(cacheKey) { mutableStateOf(letterboxdFilmsCache[cacheKey]) }
    var error by remember(cacheKey) { mutableStateOf<String?>(null) }
    // Abgleich-Ergebnisse in Listen-Reihenfolge (null = noch offen oder nicht vorhanden)
    val matches = remember(cacheKey) { mutableStateMapOf<String, AnimeCard?>() }
    var checked by remember(cacheKey) { mutableStateOf(0) }

    LaunchedEffect(cacheKey) {
        val list = films ?: runCatching { load() }
            .onSuccess { letterboxdFilmsCache[cacheKey] = it }
            .getOrElse {
                error = when (it) {
                    is LetterboxdLists.UserNotFoundException -> "Diesen Letterboxd-Benutzer gibt es nicht."
                    else -> "Letterboxd nicht erreichbar (${it.message})."
                }
                return@LaunchedEffect
            }
        films = list
        coroutineScope {
            list.forEach { film ->
                launch {
                    matches[film.slug] = LetterboxdLists.findOnFilmpalast(film)
                    checked++
                }
            }
        }
    }

    val available = films.orEmpty().mapNotNull { matches[it.slug] }.distinctBy { it.cleanSlug }
    val total = films?.size ?: 0
    val status = when {
        error != null -> error!!
        films == null -> "Lade Liste …"
        total == 0 -> "Die Liste ist leer oder nicht öffentlich."
        checked < total -> "${available.size} von $total bei Filmpalast · prüfe ${total - checked} weitere …"
        else -> "${available.size} von $total bei Filmpalast verfügbar"
    }

    if (available.isNotEmpty()) {
        CatalogRowSection(
            title = title,
            items = available,
            watchlistSlugs = watchlistSlugs,
            onOpenAnime = onOpenAnime,
            subtitle = status
        )
    } else {
        Column {
            SectionHeader(title = title)
            Text(
                text = if (error == null && checked >= total && total > 0) "Keiner dieser Filme ist bei Filmpalast verfügbar." else status,
                color = if (error != null) AccentAmber else TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 36.dp, vertical = 8.dp)
            )
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentVersion = remember { UpdateManager.getCurrentVersionName(context) }
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
                                    val result = UpdateManager.checkForUpdate(
                                        context = context,
                                        isTvApp = true,
                                        forceCheck = true
                                    )
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
                        text = "Zum Koppeln in der Handy-App aufs Cast-Symbol tippen, diesen Fernseher wählen " +
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
                    text = "📺 Geräte-Information",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Gerät: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (${android.os.Build.PRODUCT})",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Text(
                    text = "Android Version: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Text(
                    text = "Fernbedienungs-Dienst: Aktiv (${TvCastManager.deviceName})",
                    color = AccentCyan,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        // Quellen der Bilder und Bewertungen (TVmaze verlangt die Nennung)
        Text(
            text = "Bilder im Titelbild: TVmaze (Serien, Lizenz CC BY-SA), Kitsu und AniList (Anime), " +
                "Letterboxd (Filme, über Wikidata). Bewertungen: Letterboxd.",
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(0.92f)
        )
    }
}

