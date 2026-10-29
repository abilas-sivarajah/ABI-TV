package de.aniworld.pc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.aniworld.pc.cast.PcCastManager
import de.aniworld.pc.platform.ChromiumStreamExtractor
import de.aniworld.pc.ui.compat.BackHandler
import de.aniworld.pc.ui.components.TvPairingOverlay
import de.aniworld.pc.ui.components.TvUpdateOverlay
import de.aniworld.pc.ui.player.PlayRequest
import de.aniworld.pc.ui.player.PlayerScreen
import de.aniworld.pc.ui.screens.DetailScreen
import de.aniworld.pc.ui.screens.HomeScreen
import de.aniworld.pc.ui.screens.HomeUiState
import de.aniworld.pc.ui.theme.BgElevated
import de.aniworld.pc.ui.theme.TextSecondary
import de.aniworld.pc.update.UpdateManager
import de.aniworld.tv.data.model.AnimeCard
import de.aniworld.tv.data.model.CatalogSection
import de.aniworld.tv.data.model.EpisodeItem
import de.aniworld.tv.data.model.LanguageOption
import de.aniworld.tv.data.remote.AniWorldScraper
import de.aniworld.tv.data.update.AppRelease
import de.aniworld.tv.data.update.UpdateCheckResult

/** Navigation der PC-App: Start, Details und Player – wie MainActivity + PlayerActivity auf dem TV. */
@Composable
fun DesktopApp(chromium: ChromiumStreamExtractor) {
    val homeUiState = remember { HomeUiState() }
    var detailStack by remember { mutableStateOf<List<AnimeCard>>(emptyList()) }
    var homeSections by remember { mutableStateOf<List<CatalogSection>>(emptyList()) }
    var isLoadingHome by remember { mutableStateOf(true) }
    var homeError by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var availableRelease by remember { mutableStateOf<AppRelease?>(null) }
    var playRequest by remember { mutableStateOf<PlayRequest?>(null) }

    LaunchedEffect(reloadKey) {
        isLoadingHome = true
        homeError = null
        runCatching { AniWorldScraper.fetchHomeCatalog() }
            .onSuccess { homeSections = it }
            .onFailure { homeError = it.message ?: "Netzwerkfehler" }
        isLoadingHome = false
    }

    // Prüfe im Hintergrund automatisch auf GitHub-Updates
    LaunchedEffect(Unit) {
        val result = UpdateManager.checkForUpdate()
        if (result is UpdateCheckResult.Available) availableRelease = result.release
    }

    // Befehle vom gekoppelten Handy öffnen den Player – auch wenn er schon läuft
    DisposableEffect(Unit) {
        PcCastManager.onPlayCommand = { cmd ->
            val ep = EpisodeItem(
                number = cmd.episodeNumber,
                seasonLabel = cmd.seasonLabel,
                path = cmd.episodePath,
                titleDe = cmd.titleDe.ifBlank { "Folge ${cmd.episodeNumber}" },
                titleEn = "",
                languages = listOfNotNull(LanguageOption.fromKey(cmd.preferredLangKey))
            )
            playRequest = PlayRequest.Episode(
                animeSlug = cmd.animeSlug,
                animeTitle = cmd.animeTitle,
                coverUrl = cmd.coverUrl,
                episode = ep,
                seasonEpisodes = listOf(ep),
                preferredLangKey = cmd.preferredLangKey
            )
        }
        PcCastManager.onLiveCommand = { name, url, logo, headers ->
            playRequest = PlayRequest.Live(name, url, logo, headers)
        }
        onDispose {
            PcCastManager.onPlayCommand = null
            PcCastManager.onLiveCommand = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val request = playRequest
        if (request != null) {
            PlayerScreen(request = request, onClose = { playRequest = null })
        } else {
            val activeDetail = detailStack.lastOrNull()
            if (activeDetail == null) {
                HomeScreen(
                    uiState = homeUiState,
                    sections = homeSections,
                    isLoading = isLoadingHome,
                    errorMessage = homeError,
                    onRetry = { reloadKey++ },
                    onOpenAnime = { card -> detailStack = detailStack + card },
                    onResumeWatch = { entry ->
                        val ep = EpisodeItem(
                            number = entry.episodeNumber,
                            seasonLabel = entry.seasonLabel,
                            path = entry.episodePath,
                            titleDe = entry.episodeTitle,
                            titleEn = "",
                            languages = listOfNotNull(LanguageOption.fromKey(entry.preferredLangKey))
                        )
                        playRequest = PlayRequest.Episode(
                            animeSlug = entry.animeSlug,
                            animeTitle = entry.animeTitle,
                            coverUrl = entry.coverUrl,
                            episode = ep,
                            seasonEpisodes = listOf(ep),
                            preferredLangKey = entry.preferredLangKey
                        )
                    },
                    onPlayLiveChannel = { channel ->
                        playRequest = PlayRequest.Live(channel.name, channel.streamUrl, channel.logoUrl, channel.headers)
                    },
                    onUpdateFound = { release -> availableRelease = release }
                )
            } else {
                BackHandler { detailStack = detailStack.dropLast(1) }
                key(activeDetail.cleanSlug) {
                    DetailScreen(
                        animeSlug = activeDetail.cleanSlug,
                        initialTitle = activeDetail.title,
                        initialCover = activeDetail.coverUrl,
                        onBack = { detailStack = detailStack.dropLast(1) },
                        onPlayEpisode = { detail, episode, episodeList, lang ->
                            playRequest = PlayRequest.Episode(
                                animeSlug = detail.slug,
                                animeTitle = detail.title,
                                coverUrl = detail.coverUrl,
                                episode = episode,
                                seasonEpisodes = episodeList,
                                preferredLangKey = lang.langKey
                            )
                        }
                    )
                }
            }
        }

        // Update-Dialog als Overlay
        availableRelease?.let { release ->
            TvUpdateOverlay(release = release, onDismiss = { availableRelease = null })
        }

        // Kopplungscode für die Handy-Fernbedienung
        TvPairingOverlay()

        ChromiumStatusBadge(chromium, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

/** Zeigt beim ersten Start, dass Chromium (für einige Hoster) noch geladen wird. */
@Composable
private fun ChromiumStatusBadge(chromium: ChromiumStreamExtractor, modifier: Modifier = Modifier) {
    val status by chromium.status.collectAsState()
    val text = when (val s = status) {
        is ChromiumStreamExtractor.Status.Preparing -> s.text
        is ChromiumStreamExtractor.Status.Failed -> "Browser-Komponente nicht verfügbar – einige Hoster gehen nicht"
        else -> return
    }
    Box(
        modifier = modifier
            .padding(16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(BgElevated.copy(alpha = 0.95f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text = text, color = TextSecondary, fontSize = 12.sp)
    }
}
