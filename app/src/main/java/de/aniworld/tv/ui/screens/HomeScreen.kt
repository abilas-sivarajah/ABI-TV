package de.aniworld.tv.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import de.aniworld.tv.data.update.AppRelease
import de.aniworld.tv.data.update.UpdateCheckResult
import de.aniworld.tv.data.update.UpdateManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import de.aniworld.tv.ui.components.TvAnimeCard
import de.aniworld.tv.ui.components.TvContinueWatchingCard
import de.aniworld.tv.ui.components.TvFocusableButton
import de.aniworld.tv.ui.theme.AccentBlue
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
    HOME("🏠 Animes"),
    MOVIES("🎬 Filme"),
    SERIES("🍿 Serien"),
    LIVETV("📺 Live TV"),
    SEARCH("🔍 Suche"),
    GENRES("🎭 Genres"),
    WATCHLIST("★ Merkliste"),
    SETTINGS("⚙️ Einstellungen")
}

class HomeUiState {
    var activeTab by mutableStateOf(MainNavTab.HOME)
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

    fun selectTab(newTab: MainNavTab) {
        if (newTab != activeTab) {
            if (newTab == MainNavTab.SEARCH) {
                if (activeTab == MainNavTab.MOVIES) {
                    searchScope = SearchScope.MOVIES
                } else if (activeTab == MainNavTab.SERIES) {
                    searchScope = SearchScope.SERIES
                } else if (activeTab == MainNavTab.HOME || activeTab == MainNavTab.GENRES) {
                    searchScope = SearchScope.ANIMES
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
    onUpdateFound: (AppRelease) -> Unit = {}
) {
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
            uiState.activeTab != MainNavTab.HOME

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
            uiState.activeTab != MainNavTab.HOME -> {
                uiState.activeTab = MainNavTab.HOME
            }
        }
    }

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

            // Zentrierte Haupt-Tabs mit genügend Platz für Fokus-Skalierung
            LazyRow(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
            ) {
                items(MainNavTab.entries, key = { it.name }) { tab ->
                    TvFocusableButton(
                        text = tab.label,
                        isSelected = activeTab == tab,
                        modifier = if (activeTab == tab) {
                            Modifier.focusRequester(activeTabFocusRequester)
                        } else {
                            Modifier
                        },
                        onClick = { onSelectTab(tab) }
                    )
                }
            }

            // Kompakte Sprachwahl rechts
            TvFocusableButton(
                text = "🌐 ${preferredLanguage.shortLabel}",
                badgeColor = Color(preferredLanguage.badgeColorHex),
                onClick = onCycleLanguage
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.06f))
        )
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

@Composable
private fun HeroBanner(
    anime: AnimeCard,
    isBookmarked: Boolean,
    onOpenDetail: () -> Unit,
    onToggleWatchlist: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp, vertical = 6.dp)
            .height(226.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(BgSurface)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
    ) {
        // Hintergrundbild rechtsbündig mit sanftem Verlauf nach links
        if (anime.coverUrl.isNotBlank()) {
            AsyncImage(
                model = anime.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF0B0E14),
                            Color(0xFF0B0E14).copy(alpha = 0.94f),
                            Color(0xFF0B0E14).copy(alpha = 0.55f),
                            Color.Transparent
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AccentCyan.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = when {
                            anime.cleanSlug.startsWith("filmpalast:") -> "FILMPALAST HIGHLIGHT"
                            anime.cleanSlug.startsWith("sto:") || anime.cleanSlug.startsWith("/serie/") -> "SERIENSTREAM HIGHLIGHT"
                            anime.cleanSlug.startsWith("hdfilme:") -> "HD-FILME HIGHLIGHT"
                            else -> "ANIWORLD EMPFEHLUNG"
                        },
                        color = AccentCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = anime.title,
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (anime.genreOrSubtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = anime.genreOrSubtitle,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvFocusableButton(
                        text = "▶ Jetzt ansehen",
                        isPrimary = true,
                        onClick = onOpenDetail
                    )
                    TvFocusableButton(
                        text = if (isBookmarked) "★ Auf der Merkliste" else "+ Zur Merkliste",
                        isSelected = isBookmarked,
                        onClick = onToggleWatchlist
                    )
                }
            }

            if (anime.coverUrl.isNotBlank()) {
                Spacer(modifier = Modifier.width(24.dp))
                AsyncImage(
                    model = anime.coverUrl,
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(112.dp)
                        .height(166.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                )
            }
        }
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
    Column {
        SectionHeader(title = title, subtitle = "${items.size} Titel")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 36.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            items(items, key = { "${title}_${it.cleanSlug}" }) { anime ->
                TvAnimeCard(
                    anime = anime,
                    isBookmarked = anime.cleanSlug in watchlistSlugs,
                    onClick = { onOpenAnime(anime) },
                    onFocused = { onFocusAnime(anime) }
                )
            }
        }
    }
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
        LazyRow(
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
    var isEditing by remember { mutableStateOf(false) }
    var isBoxFocused by remember { mutableStateOf(false) }

    BackHandler(enabled = isEditing) {
        isEditing = false
        keyboardController?.hide()
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            delay(50L)
            textFieldFocusRequester.requestFocus()
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
                    .clip(RoundedCornerShape(10.dp))
                    .background(BgSurface)
                    .border(
                        width = if (isBoxFocused || isEditing) 2.dp else 1.dp,
                        color = if (isBoxFocused || isEditing) FocusBorder else Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .onFocusChanged { isBoxFocused = it.isFocused }
                    .focusable(!isEditing)
                    .clickable { isEditing = true }
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp &&
                            (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                        ) {
                            isEditing = true
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
                        value = query,
                        onValueChange = { triggerSearch(it) },
                        singleLine = true,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(AccentCyan),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                isEditing = false
                                keyboardController?.hide()
                                triggerSearch(query)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(textFieldFocusRequester)
                    )
                }
            }

            if (query.isNotEmpty()) {
                TvFocusableButton(
                    text = "✕ Löschen",
                    onClick = {
                        isEditing = false
                        keyboardController?.hide()
                        triggerSearch("")
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
                LazyRow(
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
                    LazyRow(
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
                LazyRow(
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
    }
}

