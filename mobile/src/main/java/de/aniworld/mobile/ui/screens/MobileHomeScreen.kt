package de.aniworld.mobile.ui.screens

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import de.aniworld.tv.data.remote.LetterboxdLists
import de.aniworld.tv.data.remote.StoAnimeFilter
import de.aniworld.tv.data.remote.TitleSearch
import de.aniworld.tv.data.remote.withoutStoAnime
import de.aniworld.tv.platform.LetterboxdAccountAndroid
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.aniworld.mobile.ui.components.CastActionButton
import de.aniworld.mobile.ui.components.MiniRemoteBar
import de.aniworld.mobile.ui.components.MobileAnimeCard
import de.aniworld.mobile.ui.components.MobileContinueCard
import de.aniworld.mobile.ui.components.MobileHeroCarousel
import de.aniworld.mobile.ui.components.MobilePlayTitle
import de.aniworld.mobile.ui.components.MobileTitleSheet
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
import de.aniworld.tv.data.update.AppRelease
import de.aniworld.tv.data.update.UpdateCheckResult
import de.aniworld.tv.data.update.UpdateManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

enum class MobileTab(val label: String, val icon: ImageVector) {
    START("Start", Icons.Default.Home),
    MOVIES("Filme", Icons.Default.Movie),
    SERIES("Serien", Icons.Default.Tv),
    ANIMES("Anime", Icons.Default.AutoAwesome),
    LIVE_TV("Live TV", Icons.Default.LiveTv),
    WATCHLIST("Merkliste", Icons.Default.Bookmark),
    SEARCH("Suche", Icons.Default.Search)
}

/** Anime-Genres ("Beliebt"/"Neu" stecken schon in der Übersicht). */
private val MobileAnimeGenres = AniWorldScraper.GENRE_CATEGORIES.filter { it.first !in setOf("beliebt", "neu", "isekai") }

/** Reiter der unteren Leiste; die Suche sitzt wie am PC als Lupe in der oberen Leiste. */
private val BottomBarTabs = listOf(
    MobileTab.START,
    MobileTab.MOVIES,
    MobileTab.SERIES,
    MobileTab.ANIMES,
    MobileTab.LIVE_TV,
    MobileTab.WATCHLIST
)

/** Katalog eines Tabs (Filme/Serien): gewählte Kategorie, geladene Titel und wie weit nachgeladen wurde. */
class MobileCatalogState {
    var selectedCat by mutableStateOf("OVERVIEW")
    var overviewSections by mutableStateOf<List<CatalogSection>>(emptyList())
    var categoryItems by mutableStateOf<List<AnimeCard>>(emptyList())
    var loadedCat by mutableStateOf<String?>(null)
    var page by mutableStateOf(1)
    var canLoadMore by mutableStateOf(true)
}

/**
 * Zustand des Startbildschirms. Liegt in der MainActivity, weil der Startbildschirm beim Öffnen
 * einer Detailseite verschwindet – sonst wären Tab, Kategorie und Suche danach zurückgesetzt.
 */
class MobileHomeState {
    var currentTab by mutableStateOf(MobileTab.START)
    // Woher die Suche geöffnet wurde, damit "Zurück" dorthin führt
    var tabBeforeSearch by mutableStateOf(MobileTab.START)
    var searchScope by mutableStateOf(SearchScope.ALL)
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<AnimeCard>>(emptyList())
    var searchResultsKey by mutableStateOf<String?>(null)
    val movies = MobileCatalogState()
    val series = MobileCatalogState()
    val animes = MobileCatalogState()
    // Live TV: gewählter Reiter, Unterreiter je Ebene und Suche (bleiben beim Reiterwechsel erhalten)
    var liveCategory by mutableStateOf("all")
    var liveUserPickedTab by mutableStateOf(false)
    var liveTreePath by mutableStateOf<List<String>>(emptyList())
    var liveQuery by mutableStateOf("")
    // Startseite: beliebteste Titel aller Anbieter (einmal laden, beim Tab-Wechsel behalten)
    var startTopMovies by mutableStateOf<List<AnimeCard>>(emptyList())
    var startPopularSeries by mutableStateOf<List<AnimeCard>>(emptyList())
    var startLoaded by mutableStateOf(false)

    fun selectTab(tab: MobileTab) {
        if (tab == MobileTab.SEARCH && currentTab != MobileTab.SEARCH) {
            tabBeforeSearch = currentTab
            when (currentTab) {
                MobileTab.ANIMES -> searchScope = SearchScope.ANIMES
                MobileTab.MOVIES -> searchScope = SearchScope.MOVIES
                MobileTab.SERIES -> searchScope = SearchScope.SERIES
                MobileTab.START -> searchScope = SearchScope.ALL
                else -> Unit
            }
        }
        currentTab = tab
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileHomeScreen(
    sections: List<CatalogSection>,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onOpenAnime: (AnimeCard) -> Unit,
    onOpenCastSheet: () -> Unit,
    onResumeWatch: (WatchProgressEntry) -> Unit,
    onPlayLiveChannel: (LiveTvChannel) -> Unit = {},
    onUpdateFound: (AppRelease) -> Unit = {},
    onPlayTitle: MobilePlayTitle = { _, _, _ -> },
    state: MobileHomeState = remember { MobileHomeState() }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val watchlist by LocalStorage.watchlistFlow.collectAsState()
    val progressList by LocalStorage.progressFlow.collectAsState()
    var showSettingsSheet by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateCheckStatus by remember { mutableStateOf<String?>(null) }
    var updateCheckFailed by remember { mutableStateOf(false) }
    // Tippen auf eine Karte in einer Reihe: Info-Feld statt gleich die Detailseite
    var sheetTitle by remember { mutableStateOf<AnimeCard?>(null) }
    val watchlistSlugs = remember(watchlist) { watchlist.map { it.cleanSlug }.toSet() }
    val onShowTitle: (AnimeCard) -> Unit = { sheetTitle = it }

    // Zurück führt von jedem Reiter erst zur Startseite (aus der Suche dorthin, woher sie kam),
    // statt die App zu verlassen
    BackHandler(enabled = state.currentTab != MobileTab.START) {
        state.currentTab = if (state.currentTab == MobileTab.SEARCH) state.tabBeforeSearch else MobileTab.START
    }

    Scaffold(
        containerColor = BgPrimary,
        topBar = {
            // Obere Leiste mit Logo, Cast-Button und Einstellungen (respektiert Statusleiste / Uhrzeit)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgPrimary)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Brush.horizontalGradient(listOf(AccentBlue, AccentCyan)))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "ABI",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "TV",
                        color = AccentCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = { state.selectTab(MobileTab.SEARCH) }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Suche",
                            tint = if (state.currentTab == MobileTab.SEARCH) AccentCyan else TextPrimary
                        )
                    }
                    CastActionButton(onClick = onOpenCastSheet)
                    IconButton(onClick = {
                        updateCheckStatus = null
                        showSettingsSheet = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Einstellungen & Updates",
                            tint = TextSecondary
                        )
                    }
                }
            }
        },
        bottomBar = {
            Column {
                MiniRemoteBar()

                NavigationBar(
                    containerColor = BgSurface,
                    tonalElevation = 8.dp
                ) {
                    BottomBarTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = state.currentTab == tab,
                            onClick = { state.selectTab(tab) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentCyan,
                                selectedTextColor = AccentCyan,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = AccentCyan.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (state.currentTab) {
                MobileTab.START -> MobileStartTab(
                    state = state,
                    animeSections = sections,
                    isLoadingAnime = isLoading,
                    continueWatching = progressList,
                    watchlistSlugs = watchlistSlugs,
                    onRetryAnime = onRetry,
                    onOpenAnime = onOpenAnime,
                    onShowTitle = { sheetTitle = it },
                    onResumeWatch = onResumeWatch,
                    onPlayTitle = onPlayTitle
                )

                MobileTab.ANIMES -> MobileCatalogTab(
                    catalog = state.animes,
                    categories = MobileAnimeGenres,
                    searchLabel = "🔍 Anime suchen",
                    emptyText = "Keine Anime gefunden.",
                    watchlist = watchlist,
                    onOpenAnime = onOpenAnime,
                    onShowTitle = onShowTitle,
                    onPlayTitle = onPlayTitle,
                    onOpenSearch = { state.selectTab(MobileTab.SEARCH) },
                    loadOverview = { emptyList() },
                    loadCategory = { path, page -> AniWorldScraper.fetchCategoryOrGenre(path, page) },
                    loadMoreLabel = "Mehr Anime laden",
                    overviewContent = {
                        MobileAnimesTab(
                            sections = sections,
                            continueWatching = progressList,
                            watchlistSlugs = watchlistSlugs,
                            isLoading = isLoading,
                            errorMessage = errorMessage,
                            onRetry = onRetry,
                            onOpenAnime = onOpenAnime,
                            onShowTitle = onShowTitle,
                            onResumeWatch = onResumeWatch,
                            onPlayTitle = onPlayTitle
                        )
                    }
                )

                MobileTab.MOVIES -> MobileCatalogTab(
                    catalog = state.movies,
                    categories = FilmpalastScraper.CATEGORIES,
                    searchLabel = "🔍 Filme suchen",
                    emptyText = "Keine Filme gefunden. Filmpalast ist gerade nicht erreichbar.",
                    watchlist = watchlist,
                    onOpenAnime = onOpenAnime,
                    onShowTitle = { sheetTitle = it },
                    onPlayTitle = onPlayTitle,
                    onOpenSearch = { state.selectTab(MobileTab.SEARCH) },
                    loadOverview = { FilmpalastScraper.fetchHomeSections() },
                    loadCategory = { path, page -> FilmpalastScraper.fetchCategory(path, page) },
                    loadMoreLabel = "Mehr Filme laden"
                )

                MobileTab.SERIES -> MobileCatalogTab(
                    catalog = state.series,
                    categories = SerienStreamScraper.CATEGORIES,
                    searchLabel = "🔍 Serien suchen",
                    emptyText = "Keine Serien gefunden.",
                    watchlist = watchlist,
                    onOpenAnime = onOpenAnime,
                    onShowTitle = { sheetTitle = it },
                    onPlayTitle = onPlayTitle,
                    onOpenSearch = { state.selectTab(MobileTab.SEARCH) },
                    loadOverview = { SerienStreamScraper.fetchHomeSections() },
                    loadCategory = { path, page -> SerienStreamScraper.fetchCategory(path, page) },
                    loadMoreLabel = "Mehr Serien laden",
                    hideStoAnime = true
                )

                MobileTab.LIVE_TV -> MobileLiveTvScreen(
                    state = state,
                    onPlayChannel = onPlayLiveChannel
                )

                MobileTab.SEARCH -> MobileSearchTab(
                    state = state,
                    watchlist = watchlist,
                    animeSections = sections,
                    onShowTitle = onShowTitle
                )

                MobileTab.WATCHLIST -> MobileWatchlistTab(
                    watchlist = watchlist,
                    continueWatching = progressList,
                    onShowTitle = onShowTitle,
                    onResumeWatch = onResumeWatch
                )
            }
        }
    }

    sheetTitle?.let { title ->
        MobileTitleSheet(
            anime = title,
            isBookmarked = title.cleanSlug in watchlistSlugs,
            onDismiss = { sheetTitle = null },
            onOpenDetail = { onOpenAnime(title) },
            onPlayTitle = onPlayTitle
        )
    }

    if (showSettingsSheet) {
        val currentVersion = remember { UpdateManager.getCurrentVersionName(context) }
        val currentRepo = remember { UpdateManager.getTargetRepo() }
        val lbUser by LetterboxdAccountAndroid.username.collectAsState()
        val lbStatus by LetterboxdAccountAndroid.status.collectAsState()

        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = BgElevated,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "⚙️ Einstellungen & Updates",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showSettingsSheet = false }) {
                        Icon(Icons.Default.Clear, contentDescription = "Schließen", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Updates & Version
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgSurface)
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "🚀 ABI TV Mobile",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Installierte Version: v$currentVersion",
                            color = AccentCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Quelle: $currentRepo",
                            color = TextMuted,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (isCheckingUpdate) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = AccentCyan,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text("Prüfe auf GitHub-Updates...", color = TextSecondary, fontSize = 13.sp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    isCheckingUpdate = true
                                    updateCheckStatus = null
                                    scope.launch {
                                        val result = UpdateManager.checkForUpdate(
                                            context = context,
                                            isTvApp = false,
                                            forceCheck = true
                                        )
                                        isCheckingUpdate = false
                                        when (result) {
                                            is UpdateCheckResult.Available -> {
                                                showSettingsSheet = false
                                                onUpdateFound(result.release)
                                            }
                                            UpdateCheckResult.UpToDate -> {
                                                updateCheckFailed = false
                                                updateCheckStatus = "Du hast bereits die neueste Version (v$currentVersion)! 🎉"
                                            }
                                            is UpdateCheckResult.Failed -> {
                                                updateCheckFailed = true
                                                updateCheckStatus = result.message
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "🔍 Nach Updates suchen",
                                    color = Color(0xFF060911),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (updateCheckStatus != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = updateCheckStatus ?: "",
                                color = if (updateCheckFailed) Color(0xFFFF5252) else AccentCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Letterboxd-Konto (Anmeldung auf der echten Letterboxd-Seite)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgSurface)
                        .padding(16.dp)
                ) {
                    Column {
                        Text(text = "🎬 Letterboxd", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = lbUser?.let { "Angemeldet als $it" } ?: "Nicht angemeldet",
                            color = if (lbUser != null) AccentCyan else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = lbStatus ?: if (lbUser != null && LetterboxdAccountAndroid.isListOwner) {
                                "Filme, die du auf die Merkliste setzt, kommen auch in „${LetterboxdLists.FeaturedList.NAME}“."
                            } else {
                                "Die Anmeldung läuft auf der echten Letterboxd-Seite; die App sieht dein Passwort nicht."
                            },
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (lbUser != null) {
                            OutlinedButton(
                                onClick = { LetterboxdAccountAndroid.signOut() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Abmelden", color = AccentCyan)
                            }
                        } else {
                            Button(
                                onClick = { context.startActivity(LetterboxdAccountAndroid.signInIntent(context)) },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Mit Letterboxd anmelden", color = Color(0xFF060911), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                // Quellen der Bilder und Bewertungen (TVmaze verlangt die Nennung)
                Text(
                    text = "Bilder im Titelbild: TVmaze (Serien, Lizenz CC BY-SA), Kitsu und AniList (Anime), " +
                        "Letterboxd (Filme, über Wikidata). Bewertungen: Letterboxd.",
                    color = TextMuted,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Startseite über alle Anbieter: Titelbild, Weiterschauen und je eine Reihe mit den beliebtesten
 * Filmen (Filmpalast), Serien (S.to) und Animes (AniWorld).
 */
@Composable
private fun MobileStartTab(
    state: MobileHomeState,
    animeSections: List<CatalogSection>,
    isLoadingAnime: Boolean,
    continueWatching: List<WatchProgressEntry>,
    watchlistSlugs: Set<String>,
    onRetryAnime: () -> Unit,
    onOpenAnime: (AnimeCard) -> Unit,
    onShowTitle: (AnimeCard) -> Unit,
    onResumeWatch: (WatchProgressEntry) -> Unit,
    onPlayTitle: MobilePlayTitle
) {
    var isLoading by remember { mutableStateOf(!state.startLoaded) }
    var reloadKey by remember { mutableStateOf(0) }
    LaunchedEffect(reloadKey) {
        if (state.startLoaded) return@LaunchedEffect
        isLoading = true
        coroutineScope {
            val movies = async { runCatching { FilmpalastScraper.fetchCategory("/movies/top") }.getOrDefault(emptyList()) }
            val series = async { runCatching { SerienStreamScraper.fetchCategory("/beliebte-serien") }.getOrDefault(emptyList()) }
            state.startTopMovies = movies.await().distinctBy { it.cleanSlug }.take(24)
            state.startPopularSeries = series.await().distinctBy { it.cleanSlug }.take(24)
        }
        state.startLoaded = state.startTopMovies.isNotEmpty() || state.startPopularSeries.isNotEmpty()
        isLoading = false
    }

    // Beliebte Animes: die Reihe "Beliebt bei AniWorld" der AniWorld-Startseite
    val popularAnime = remember(animeSections) {
        (animeSections.firstOrNull { it.title.contains("beliebt", ignoreCase = true) } ?: animeSections.firstOrNull())
            ?.items.orEmpty()
    }

    // Titelbild: je 2 zufällige Filme, Serien und Animes, immer abwechselnd
    val heroItems = remember(state.startTopMovies, state.startPopularSeries, popularAnime) {
        TitlePreview.interleavedPicks(listOf(state.startTopMovies, state.startPopularSeries, popularAnime), perSource = 2)
    }

    if (heroItems.isEmpty() && continueWatching.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading || isLoadingAnime) {
                CircularProgressIndicator(color = AccentCyan)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Die Startseite konnte nicht geladen werden.", color = TextSecondary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            reloadKey++
                            onRetryAnime()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                    ) {
                        Text("Erneut versuchen", color = Color(0xFF060911), fontWeight = FontWeight.Bold)
                    }
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
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        if (heroItems.isNotEmpty()) {
            item(key = "start_hero") {
                MobileHeroCarousel(
                    items = heroItems,
                    watchlistSlugs = watchlistSlugs,
                    onOpenDetail = onOpenAnime,
                    onPlayTitle = onPlayTitle
                )
            }
        }

        if (continueWatching.isNotEmpty()) {
            item(key = "start_continue") {
                MobileContinueRow(entries = continueWatching, onResumeWatch = onResumeWatch)
            }
        }

        val rows = listOf(
            Triple("start_movies", "🔥 Beliebte Top-Filme auf Filmpalast", state.startTopMovies),
            Triple("start_series", "🍿 Beliebte Serien auf S.to", state.startPopularSeries),
            Triple("start_anime", "⭐ Beliebte Anime auf AniWorld", popularAnime)
        )
        rows.filter { it.third.isNotEmpty() }.forEach { (key, title, items) ->
            item(key = key) {
                MobileCatalogRow(
                    rowKey = key,
                    title = title,
                    items = items,
                    watchlistSlugs = watchlistSlugs,
                    onShowTitle = onShowTitle
                )
            }
        }

        if (isLoading) {
            item(key = "start_loading") {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentCyan, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun MobileAnimesTab(
    sections: List<CatalogSection>,
    continueWatching: List<WatchProgressEntry>,
    watchlistSlugs: Set<String>,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onOpenAnime: (AnimeCard) -> Unit,
    onShowTitle: (AnimeCard) -> Unit,
    onResumeWatch: (WatchProgressEntry) -> Unit,
    onPlayTitle: MobilePlayTitle
) {
    if (isLoading && sections.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AccentCyan)
        }
        return
    }

    if (errorMessage != null && sections.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Verbindungsfehler: $errorMessage", color = TextSecondary, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)) {
                    Text("Erneut versuchen", color = Color(0xFF060911), fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    // Titelbild: 6 zufällige Animes zum Durchblättern
    val heroItems = remember(sections) { TitlePreview.randomPicks(sections.flatMap { it.items }) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        if (heroItems.isNotEmpty()) {
            item(key = "anime_hero") {
                MobileHeroCarousel(
                    items = heroItems,
                    watchlistSlugs = watchlistSlugs,
                    onOpenDetail = onOpenAnime,
                    onPlayTitle = onPlayTitle
                )
            }
        }

        // Weiterschauen
        if (continueWatching.isNotEmpty()) {
            item(key = "anime_continue") {
                MobileContinueRow(entries = continueWatching, onResumeWatch = onResumeWatch)
            }
        }

        // Alle Katalogbereiche
        items(sections, key = { it.id }) { sec ->
            MobileCatalogRow(
                rowKey = sec.id,
                title = sec.title,
                items = sec.items,
                watchlistSlugs = watchlistSlugs,
                onShowTitle = onShowTitle
            )
        }
    }
}

/** Überschrift mit waagerechter Reihe zuletzt geschauter Titel. */
@Composable
private fun MobileContinueRow(
    entries: List<WatchProgressEntry>,
    onResumeWatch: (WatchProgressEntry) -> Unit
) {
    Column {
        Text(
            text = "▶ Weiterschauen",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(entries, key = { it.animeSlug }) { entry ->
                MobileContinueCard(entry = entry, onClick = { onResumeWatch(entry) })
            }
        }
    }
}

/** Überschrift mit waagerechter Kartenreihe; Tippen öffnet das Info-Feld. */
@Composable
internal fun MobileCatalogRow(
    rowKey: String,
    title: String,
    items: List<AnimeCard>,
    watchlistSlugs: Set<String>,
    onShowTitle: (AnimeCard) -> Unit
) {
    Column {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = { "${rowKey}_${it.cleanSlug}" }) { anime ->
                MobileAnimeCard(
                    anime = anime,
                    isBookmarked = anime.cleanSlug in watchlistSlugs,
                    onClick = { onShowTitle(anime) }
                )
            }
        }
    }
}

/**
 * Filme- bzw. Serien-Tab: Kategorie-Chips, Übersicht oder Raster einer Kategorie.
 * Mit [loadMoreLabel] gibt es am Rasterende einen Button, der die nächste Seite nachlädt.
 */
@Composable
private fun MobileCatalogTab(
    catalog: MobileCatalogState,
    categories: List<Pair<String, String>>,
    searchLabel: String,
    emptyText: String,
    watchlist: List<AnimeCard>,
    onOpenAnime: (AnimeCard) -> Unit,
    onShowTitle: (AnimeCard) -> Unit,
    onPlayTitle: MobilePlayTitle,
    onOpenSearch: () -> Unit,
    loadOverview: suspend () -> List<CatalogSection>,
    loadCategory: suspend (path: String, page: Int) -> List<AnimeCard>,
    loadMoreLabel: String?,
    /** Eigene Übersicht statt der Reihen aus [loadOverview] (Anime: Titelbild, Weiterschauen, Reihen). */
    overviewContent: (@Composable () -> Unit)? = null,
    /** Serien: Anime von S.to ausblenden, die stehen im Anime-Bereich. */
    hideStoAnime: Boolean = false
) {
    val selectedCat = catalog.selectedCat
    val stoAnimeSlugs by StoAnimeFilter.slugs.collectAsState()
    val overviewSections = remember(catalog.overviewSections, stoAnimeSlugs, hideStoAnime) {
        if (hideStoAnime) {
            catalog.overviewSections
                .map { it.copy(items = it.items.withoutStoAnime(stoAnimeSlugs)) }
                .filter { it.items.isNotEmpty() }
        } else {
            catalog.overviewSections
        }
    }
    val categoryItems = remember(catalog.categoryItems, stoAnimeSlugs, hideStoAnime) {
        if (hideStoAnime) catalog.categoryItems.withoutStoAnime(stoAnimeSlugs) else catalog.categoryItems
    }
    val watchlistSlugs = remember(watchlist) { watchlist.map { it.cleanSlug }.toSet() }
    // Titelbild: 6 zufällige Titel dieses Bereichs (Filme bzw. Serien)
    val heroItems = remember(overviewSections) { TitlePreview.randomPicks(overviewSections.flatMap { it.items }) }
    val scope = rememberCoroutineScope()

    // Bereits Geladenes (inkl. nachgeladener Seiten) nach der Detailseite nicht erneut laden
    fun needsLoad(cat: String): Boolean = if (cat == "OVERVIEW") {
        overviewContent == null && catalog.overviewSections.isEmpty()
    } else {
        catalog.loadedCat != cat || catalog.categoryItems.isEmpty()
    }

    var isLoading by remember { mutableStateOf(needsLoad(selectedCat)) }
    var reloadKey by remember { mutableStateOf(0) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var loadMoreFailed by remember { mutableStateOf(false) }

    LaunchedEffect(selectedCat, reloadKey) {
        val cat = selectedCat
        if (!needsLoad(cat)) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        if (cat == "OVERVIEW") {
            catalog.overviewSections = runCatching { loadOverview() }.getOrDefault(emptyList())
        } else {
            val firstPage = runCatching { loadCategory(cat, 1) }.getOrDefault(emptyList())
            catalog.categoryItems = firstPage.distinctBy { it.cleanSlug }
            catalog.loadedCat = cat
            catalog.page = 1
            catalog.canLoadMore = loadMoreLabel != null && firstPage.isNotEmpty()
            loadMoreFailed = false
        }
        isLoading = false
    }

    fun loadMore() {
        if (isLoadingMore || !catalog.canLoadMore) return
        val cat = catalog.loadedCat ?: return
        val nextPage = catalog.page + 1
        isLoadingMore = true
        loadMoreFailed = false
        scope.launch {
            val result = runCatching { loadCategory(cat, nextPage) }
            // Inzwischen andere Kategorie gewählt: Ergebnis verwerfen
            if (catalog.loadedCat == cat) {
                result.onSuccess { more ->
                    // Doppelte Titel würden die Schlüssel im Raster sprengen; keine neuen Titel = letzte Seite
                    val known = catalog.categoryItems.mapTo(HashSet()) { it.cleanSlug }
                    val fresh = more.filter { known.add(it.cleanSlug) }
                    catalog.categoryItems = catalog.categoryItems + fresh
                    catalog.page = nextPage
                    catalog.canLoadMore = fresh.isNotEmpty()
                }.onFailure {
                    loadMoreFailed = true
                }
            }
            isLoadingMore = false
        }
    }

    val showLoading = isLoading || (selectedCat != "OVERVIEW" && catalog.loadedCat != selectedCat)
    val isEmpty = if (selectedCat == "OVERVIEW") overviewContent == null && overviewSections.isEmpty() else categoryItems.isEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        // Horizontale Kategorie-Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                CatChip(searchLabel, false) { onOpenSearch() }
            }
            item {
                CatChip("🔥 Highlights", selectedCat == "OVERVIEW") { catalog.selectedCat = "OVERVIEW" }
            }
            items(categories, key = { it.first }) { (path, label) ->
                CatChip(label, selectedCat == path) { catalog.selectedCat = path }
            }
        }

        if (showLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentCyan)
            }
        } else if (isEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = emptyText, color = TextSecondary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { reloadKey++ },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                    ) {
                        Text("Erneut versuchen", color = Color(0xFF060911), fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (selectedCat == "OVERVIEW" && overviewContent != null) {
            overviewContent()
        } else if (selectedCat == "OVERVIEW") {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                if (heroItems.isNotEmpty()) {
                    item(key = "catalog_hero") {
                        MobileHeroCarousel(
                            items = heroItems,
                            watchlistSlugs = watchlistSlugs,
                            onOpenDetail = onOpenAnime,
                            onPlayTitle = onPlayTitle
                        )
                    }
                }
                items(overviewSections, key = { it.id }) { sec ->
                    MobileCatalogRow(
                        rowKey = sec.id,
                        title = sec.title,
                        items = sec.items,
                        watchlistSlugs = watchlistSlugs,
                        onShowTitle = onShowTitle
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(categoryItems, key = { it.cleanSlug }) { movie ->
                    MobileAnimeCard(
                        anime = movie,
                        width = Dp.Unspecified,
                        isBookmarked = movie.cleanSlug in watchlistSlugs,
                        onClick = { onShowTitle(movie) }
                    )
                }
                if (loadMoreLabel != null && catalog.canLoadMore) {
                    item(key = "load_more", span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoadingMore) {
                                CircularProgressIndicator(
                                    color = AccentCyan,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(28.dp)
                                )
                            } else {
                                OutlinedButton(onClick = { loadMore() }) {
                                    Text(
                                        text = if (loadMoreFailed) "Laden fehlgeschlagen – erneut versuchen" else loadMoreLabel,
                                        color = AccentCyan,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) AccentCyan else BgSurface)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) Color(0xFF050811) else TextPrimary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 12.sp
        )
    }
}

/** Filmpalast führt auch einzelne Serienfolgen als "Film" ("Spider-Noir S01E08") – in der Filmsuche weglassen. */
private val SingleEpisodeTitle = Regex("""\bS\d{1,2}E\d{1,3}\b""", RegexOption.IGNORE_CASE)

/** Vorschläge der Suche pro App-Start merken (Reiterwechsel lädt nicht neu). */
private var searchSuggestionMovies: List<AnimeCard> = emptyList()
private var searchSuggestionSeries: List<AnimeCard> = emptyList()
private val searchCoverCache = java.util.concurrent.ConcurrentHashMap<String, String>()

/** Suche wie auf TV und PC: Vorschläge bei leerer Suche, Treffer nach Wortanfängen, Filter unter dem Feld. */
@Composable
private fun MobileSearchTab(
    state: MobileHomeState,
    watchlist: List<AnimeCard>,
    animeSections: List<CatalogSection>,
    onShowTitle: (AnimeCard) -> Unit
) {
    val query = state.searchQuery
    val searchScope = state.searchScope
    val results = state.searchResults
    val focusManager = LocalFocusManager.current
    var isSearching by remember { mutableStateOf(false) }
    val watchlistSlugs = remember(watchlist) { watchlist.map { it.cleanSlug }.toSet() }
    val stoAnimeSlugs by StoAnimeFilter.slugs.collectAsState()

    // Vorschläge bei leerer Suche: zufällige Titel des Reiters
    var suggestionMovies by remember { mutableStateOf(searchSuggestionMovies) }
    var suggestionSeries by remember { mutableStateOf(searchSuggestionSeries) }
    LaunchedEffect(Unit) {
        coroutineScope {
            if (suggestionMovies.isEmpty()) launch {
                val loaded = state.startTopMovies.ifEmpty {
                    runCatching { FilmpalastScraper.fetchCategory("/movies/top") }.getOrDefault(emptyList())
                } + state.movies.overviewSections.flatMap { it.items }
                suggestionMovies = loaded.distinctBy { it.cleanSlug }
                searchSuggestionMovies = suggestionMovies
            }
            if (suggestionSeries.isEmpty()) launch {
                val loaded = state.startPopularSeries.ifEmpty {
                    runCatching { SerienStreamScraper.fetchCategory("/beliebte-serien") }.getOrDefault(emptyList())
                } + state.series.overviewSections.flatMap { it.items }
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
        }.filter { it.coverUrl.isNotBlank() }.distinctBy { it.cleanSlug }
    }

    LaunchedEffect(query, searchScope) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            state.searchResults = emptyList()
            state.searchResultsKey = null
            isSearching = false
            return@LaunchedEffect
        }
        // Zurück von der Detailseite: Treffer sind noch da, nicht erneut suchen
        val resultsKey = "${searchScope.name}|$trimmed"
        if (state.searchResultsKey == resultsKey) {
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(300L)

        // Seiten können nur "Titel enthält" – darum mit Teilbegriffen abfragen und danach selbst
        // nach Wortanfängen filtern (TitleSearch): "sp" → "Marvel's Spider-Man", "spiderman" → "Spider-Man"
        suspend fun animes() = runCatching { AniWorldScraper.searchCatalog(trimmed) }.getOrDefault(emptyList())
        suspend fun movies(): List<AnimeCard> = coroutineScope {
            val found = TitleSearch.siteQueries(trimmed)
                .map { q -> async { runCatching { FilmpalastScraper.searchMovies(q) }.getOrDefault(emptyList()) } }
                .awaitAll()
                .flatten()
                .filterNot { it.title.contains("*ENGLISH*", ignoreCase = true) || SingleEpisodeTitle.containsMatchIn(it.title) }
            TitleSearch.filterAndRank(found, trimmed)
        }
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
        // Doppelte Treffer (z. B. aus mehreren Quellen) würden die Schlüssel im Ergebnisraster sprengen
        state.searchResults = combined.distinctBy { it.cleanSlug }.take(60)
        state.searchResultsKey = resultsKey
        isSearching = false

        // S.to-Treffer haben keine Cover (sie kommen aus der A–Z-Liste): für die ersten nachladen
        val missing = state.searchResults.filter { it.coverUrl.isBlank() }.take(24)
        if (missing.isNotEmpty()) {
            val slots = Semaphore(6)
            coroutineScope {
                missing.forEach { card ->
                    launch {
                        val cover = searchCoverCache[card.cleanSlug] ?: slots.withPermit {
                            TitlePreview.loadDetail(card.cleanSlug)?.coverUrl.orEmpty()
                        }.also { if (it.isNotBlank()) searchCoverCache[card.cleanSlug] = it }
                        if (cover.isNotBlank() && state.searchQuery.trim() == trimmed) {
                            state.searchResults = state.searchResults.map {
                                if (it.cleanSlug == card.cleanSlug) it.copy(coverUrl = cover) else it
                            }
                        }
                    }
                }
            }
        }
    }

    val placeholderText = when (searchScope) {
        SearchScope.ANIMES -> "Anime suchen (z. B. Solo Leveling)..."
        SearchScope.SERIES -> "Serien suchen (z. B. Chicago Fire)..."
        SearchScope.MOVIES -> "Filme suchen (z. B. Oppenheimer)..."
        SearchScope.ALL -> "Animes, Serien & Filme suchen..."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Suchfeld
        OutlinedTextField(
            value = query,
            onValueChange = { state.searchQuery = it },
            placeholder = { Text(placeholderText, color = TextMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { state.searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Löschen", tint = TextSecondary)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentCyan,
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Segmentierte Kategorie-Filter-Chips (scrollbar, damit vier Chips auch auf schmalen Handys passen)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (scopeOption in SearchScope.entries) {
                val isSelected = searchScope == scopeOption
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) AccentCyan else BgElevated)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) AccentCyan else Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { state.searchScope = scopeOption }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "${scopeOption.icon} ${scopeOption.label}",
                        color = if (isSelected) Color(0xFF050811) else TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val trimmedQuery = query.trim()
        when {
            isSearching -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentCyan)
            }

            trimmedQuery.length >= 2 && results.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Keine Treffer für „$trimmedQuery“ in ${searchScope.label} gefunden.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }

            else -> {
                val shown = if (trimmedQuery.length >= 2) results else suggestions
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = when {
                                trimmedQuery.length >= 2 -> "${results.size} Treffer für „$trimmedQuery“"
                                searchScope == SearchScope.MOVIES -> "Filme entdecken"
                                searchScope == SearchScope.SERIES -> "Serien entdecken"
                                searchScope == SearchScope.ANIMES -> "Anime entdecken"
                                else -> "Entdecken"
                            },
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(shown, key = { it.cleanSlug }) { item ->
                        MobileAnimeCard(
                            anime = item,
                            width = Dp.Unspecified,
                            isBookmarked = item.cleanSlug in watchlistSlugs,
                            onClick = { onShowTitle(item) }
                        )
                    }
                }
            }
        }
    }
}
