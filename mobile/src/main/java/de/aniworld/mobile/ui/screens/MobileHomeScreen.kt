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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.aniworld.mobile.ui.components.CastActionButton
import de.aniworld.mobile.ui.components.MiniRemoteBar
import de.aniworld.mobile.ui.components.MobileAnimeCard
import de.aniworld.mobile.ui.components.MobileContinueCard
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
import de.aniworld.tv.data.remote.HdFilmeScraper
import de.aniworld.tv.data.remote.SerienStreamScraper
import de.aniworld.tv.data.update.AppRelease
import de.aniworld.tv.data.update.UpdateCheckResult
import de.aniworld.tv.data.update.UpdateManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

enum class MobileTab(val label: String) {
    ANIMES("Animes"),
    MOVIES("Filme"),
    LIVE_TV("Live TV"),
    SEARCH("Suche"),
    WATCHLIST("Merkliste")
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
    onUpdateFound: (AppRelease) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentTab by remember { mutableStateOf(MobileTab.ANIMES) }
    var searchScope by remember { mutableStateOf(SearchScope.ANIMES) }
    val watchlist by LocalStorage.watchlistFlow.collectAsState()
    val progressList by LocalStorage.progressFlow.collectAsState()
    var showSettingsSheet by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateCheckStatus by remember { mutableStateOf<String?>(null) }
    var updateCheckFailed by remember { mutableStateOf(false) }

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
                    NavigationBarItem(
                        selected = currentTab == MobileTab.ANIMES,
                        onClick = { currentTab = MobileTab.ANIMES },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text(MobileTab.ANIMES.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AccentCyan,
                            selectedTextColor = AccentCyan,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = AccentCyan.copy(alpha = 0.15f)
                        )
                    )

                    NavigationBarItem(
                        selected = currentTab == MobileTab.MOVIES,
                        onClick = { currentTab = MobileTab.MOVIES },
                        icon = { Icon(Icons.Default.Movie, contentDescription = null) },
                        label = { Text(MobileTab.MOVIES.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AccentCyan,
                            selectedTextColor = AccentCyan,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = AccentCyan.copy(alpha = 0.15f)
                        )
                    )

                    NavigationBarItem(
                        selected = currentTab == MobileTab.LIVE_TV,
                        onClick = { currentTab = MobileTab.LIVE_TV },
                        icon = { Icon(Icons.Default.LiveTv, contentDescription = null) },
                        label = { Text(MobileTab.LIVE_TV.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AccentCyan,
                            selectedTextColor = AccentCyan,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = AccentCyan.copy(alpha = 0.15f)
                        )
                    )

                    NavigationBarItem(
                        selected = currentTab == MobileTab.SEARCH,
                        onClick = {
                            if (currentTab == MobileTab.MOVIES) {
                                searchScope = SearchScope.MOVIES
                            } else if (currentTab == MobileTab.ANIMES) {
                                searchScope = SearchScope.ANIMES
                            }
                            currentTab = MobileTab.SEARCH
                        },
                        icon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text(MobileTab.SEARCH.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AccentCyan,
                            selectedTextColor = AccentCyan,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = AccentCyan.copy(alpha = 0.15f)
                        )
                    )

                    NavigationBarItem(
                        selected = currentTab == MobileTab.WATCHLIST,
                        onClick = { currentTab = MobileTab.WATCHLIST },
                        icon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                        label = { Text(MobileTab.WATCHLIST.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (currentTab) {
                MobileTab.ANIMES -> MobileAnimesTab(
                    sections = sections,
                    continueWatching = progressList,
                    watchlist = watchlist,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onOpenAnime = onOpenAnime,
                    onResumeWatch = onResumeWatch
                )

                MobileTab.MOVIES -> MobileMoviesTab(
                    watchlist = watchlist,
                    onOpenAnime = onOpenAnime,
                    onOpenSearch = {
                        searchScope = SearchScope.MOVIES
                        currentTab = MobileTab.SEARCH
                    }
                )

                MobileTab.LIVE_TV -> MobileLiveTvScreen(
                    onPlayChannel = onPlayLiveChannel
                )

                MobileTab.SEARCH -> MobileSearchTab(
                    searchScope = searchScope,
                    onScopeChange = { searchScope = it },
                    watchlist = watchlist,
                    onOpenAnime = onOpenAnime
                )

                MobileTab.WATCHLIST -> MobileWatchlistTab(
                    watchlist = watchlist,
                    continueWatching = progressList,
                    onOpenAnime = onOpenAnime,
                    onResumeWatch = onResumeWatch
                )
            }
        }
    }

    if (showSettingsSheet) {
        val currentVersion = remember { UpdateManager.getCurrentVersionName(context) }
        val currentRepo = remember { UpdateManager.getTargetRepo() }
        var preferredLang by remember { mutableStateOf(LocalStorage.preferredLanguage) }

        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = BgElevated,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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

                // Audiosprache
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgSurface)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Standard-Audiospur",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${preferredLang.shortLabel} (${preferredLang.fullLabel})",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val next = when (preferredLang) {
                                    LanguageOption.GER_DUB -> LanguageOption.GER_SUB
                                    LanguageOption.GER_SUB -> LanguageOption.ENG_SUB
                                    LanguageOption.ENG_SUB -> LanguageOption.GER_DUB
                                }
                                LocalStorage.preferredLanguage = next
                                preferredLang = next
                            }
                        ) {
                            Text("Wechseln", color = AccentCyan, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun MobileAnimesTab(
    sections: List<CatalogSection>,
    continueWatching: List<WatchProgressEntry>,
    watchlist: List<AnimeCard>,
    isLoading: Boolean,
    errorMessage: String?,
    onOpenAnime: (AnimeCard) -> Unit,
    onResumeWatch: (WatchProgressEntry) -> Unit
) {
    if (isLoading && sections.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AccentCyan)
        }
        return
    }

    val watchlistSlugs = remember(watchlist) { watchlist.map { it.cleanSlug }.toSet() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Weiterschauen
        if (continueWatching.isNotEmpty()) {
            item {
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
                        items(continueWatching, key = { it.animeSlug }) { entry ->
                            MobileContinueCard(entry = entry, onClick = { onResumeWatch(entry) })
                        }
                    }
                }
            }
        }

        // Alle Katalogbereiche
        items(sections, key = { it.id }) { sec ->
            Column {
                Text(
                    text = sec.title,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sec.items, key = { "${sec.id}_${it.cleanSlug}" }) { anime ->
                        MobileAnimeCard(
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

@Composable
private fun MobileMoviesTab(
    watchlist: List<AnimeCard>,
    onOpenAnime: (AnimeCard) -> Unit,
    onOpenSearch: () -> Unit
) {
    var selectedCat by remember { mutableStateOf("OVERVIEW") }
    var overviewSections by remember { mutableStateOf<List<CatalogSection>>(emptyList()) }
    var categoryItems by remember { mutableStateOf<List<AnimeCard>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val watchlistSlugs = remember(watchlist) { watchlist.map { it.cleanSlug }.toSet() }

    LaunchedEffect(selectedCat) {
        isLoading = true
        if (selectedCat == "OVERVIEW") {
            if (overviewSections.isEmpty()) {
                overviewSections = runCatching { HdFilmeScraper.fetchHomeSections() }.getOrDefault(emptyList())
            }
        } else {
            categoryItems = runCatching { HdFilmeScraper.fetchCategory(selectedCat) }.getOrDefault(emptyList())
        }
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Horizontale Kategorie-Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                CatChip("🔍 Filme suchen", false) { onOpenSearch() }
            }
            item {
                CatChip("🔥 Highlights", selectedCat == "OVERVIEW") { selectedCat = "OVERVIEW" }
            }
            items(HdFilmeScraper.CATEGORIES, key = { it.first }) { (path, label) ->
                CatChip(label, selectedCat == path) { selectedCat = path }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentCyan)
            }
        } else if (selectedCat == "OVERVIEW") {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(overviewSections, key = { it.id }) { sec ->
                    Column {
                        Text(
                            text = sec.title,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(sec.items, key = { "${sec.id}_${it.cleanSlug}" }) { movie ->
                                MobileAnimeCard(
                                    anime = movie,
                                    isBookmarked = movie.cleanSlug in watchlistSlugs,
                                    onClick = { onOpenAnime(movie) }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(categoryItems, key = { it.cleanSlug }) { movie ->
                    MobileAnimeCard(
                        anime = movie,
                        width = 110.dp,
                        isBookmarked = movie.cleanSlug in watchlistSlugs,
                        onClick = { onOpenAnime(movie) }
                    )
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

@Composable
private fun MobileSearchTab(
    searchScope: SearchScope,
    onScopeChange: (SearchScope) -> Unit,
    watchlist: List<AnimeCard>,
    onOpenAnime: (AnimeCard) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AnimeCard>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val watchlistSlugs = remember(watchlist) { watchlist.map { it.cleanSlug }.toSet() }

    LaunchedEffect(query, searchScope) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            results = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(300L)
        val combined = coroutineScope {
            when (searchScope) {
                SearchScope.ANIMES -> {
                    runCatching { AniWorldScraper.searchAnimes(trimmed) }.getOrDefault(emptyList())
                }
                SearchScope.SERIES -> {
                    runCatching { SerienStreamScraper.searchSeries(trimmed) }.getOrDefault(emptyList())
                }
                SearchScope.MOVIES -> {
                    runCatching { HdFilmeScraper.searchMovies(trimmed) }.getOrDefault(emptyList())
                }
                SearchScope.ALL -> {
                    val a = async { runCatching { AniWorldScraper.searchAnimes(trimmed) }.getOrDefault(emptyList()) }
                    val s = async { runCatching { SerienStreamScraper.searchSeries(trimmed) }.getOrDefault(emptyList()) }
                    val m = async { runCatching { HdFilmeScraper.searchMovies(trimmed) }.getOrDefault(emptyList()) }
                    a.await() + s.await() + m.await()
                }
            }
        }
        results = combined
        isSearching = false
    }

    val placeholderText = when (searchScope) {
        SearchScope.ANIMES -> "Anime suchen (z. B. Solo Leveling)..."
        SearchScope.SERIES -> "Serien suchen (z. B. Chicago Fire)..."
        SearchScope.MOVIES -> "Filme suchen (z. B. Oppenheimer)..."
        SearchScope.ALL -> "Animes, Serien & Filme suchen..."
    }

    val quickSuggestions = when (searchScope) {
        SearchScope.ANIMES -> listOf("Solo Leveling", "One Piece", "Naruto", "Attack on Titan", "Jujutsu Kaisen", "Demon Slayer")
        SearchScope.SERIES -> listOf("Chicago Fire", "Breaking Bad", "Game of Thrones", "Wednesday", "Stranger Things", "The Boys")
        SearchScope.MOVIES -> listOf("Deadpool", "Oppenheimer", "Spider-Man", "Batman", "Harry Potter", "Avatar")
        SearchScope.ALL -> listOf("Solo Leveling", "Chicago Fire", "One Piece", "Breaking Bad", "Attack on Titan")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Suchfeld
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(placeholderText, color = TextMuted, fontSize = 14.sp) },
            singleLine = true,
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
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

        // Segmentierte Kategorie-Filter-Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                        .clickable { onScopeChange(scopeOption) }
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

        Spacer(modifier = Modifier.height(10.dp))

        // Beliebte Vorschläge
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(quickSuggestions) { s ->
                val isSelected = query.equals(s, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) AccentBlue.copy(alpha = 0.25f) else BgSurface)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) AccentBlue else Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { query = s }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = s,
                        color = if (isSelected) AccentCyan else TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentCyan)
            }
        } else if (query.trim().length >= 2 && results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Keine Treffer für „$query“ in ${searchScope.label} gefunden.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        } else if (results.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(results, key = { it.cleanSlug }) { item ->
                    MobileAnimeCard(
                        anime = item,
                        width = 110.dp,
                        isBookmarked = item.cleanSlug in watchlistSlugs,
                        onClick = { onOpenAnime(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileWatchlistTab(
    watchlist: List<AnimeCard>,
    continueWatching: List<WatchProgressEntry>,
    onOpenAnime: (AnimeCard) -> Unit,
    onResumeWatch: (WatchProgressEntry) -> Unit
) {
    if (watchlist.isEmpty() && continueWatching.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Deine Merkliste ist noch leer", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Füge Animes oder Filme über „+ Zur Merkliste“ hinzu.", color = TextMuted, fontSize = 13.sp)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        if (continueWatching.isNotEmpty()) {
            item {
                Text(
                    text = "▶ Zuletzt geschaut",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(continueWatching, key = { it.animeSlug }) { entry ->
                        MobileContinueCard(entry = entry, onClick = { onResumeWatch(entry) })
                    }
                }
            }
        }

        if (watchlist.isNotEmpty()) {
            item {
                Text(
                    text = "★ Gespeicherte Titel (${watchlist.size})",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            item {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.height(500.dp)
                ) {
                    items(watchlist, key = { it.cleanSlug }) { anime ->
                        MobileAnimeCard(
                            anime = anime,
                            width = 110.dp,
                            isBookmarked = true,
                            onClick = { onOpenAnime(anime) }
                        )
                    }
                }
            }
        }
    }
}
