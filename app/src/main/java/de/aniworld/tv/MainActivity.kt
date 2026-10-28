package de.aniworld.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import de.aniworld.tv.data.model.AnimeCard
import de.aniworld.tv.data.model.AnimeDetail
import de.aniworld.tv.data.model.CatalogSection
import de.aniworld.tv.data.model.EpisodeItem
import de.aniworld.tv.data.model.LanguageOption
import de.aniworld.tv.data.model.WatchProgressEntry
import de.aniworld.tv.data.remote.AniWorldScraper
import de.aniworld.tv.data.update.AppRelease
import de.aniworld.tv.data.update.UpdateManager
import de.aniworld.tv.ui.components.TvUpdateOverlay
import de.aniworld.tv.ui.player.PlayerActivity
import de.aniworld.tv.ui.screens.DetailScreen
import de.aniworld.tv.ui.screens.HomeScreen
import de.aniworld.tv.ui.screens.HomeUiState
import de.aniworld.tv.ui.theme.AniWorldTvTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val homeUiState = HomeUiState()
    private var detailStack by mutableStateOf<List<AnimeCard>>(emptyList())
    private var homeSections by mutableStateOf<List<CatalogSection>>(emptyList())
    private var isLoadingHome by mutableStateOf(true)
    private var homeError by mutableStateOf<String?>(null)
    private var availableRelease by mutableStateOf<AppRelease?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadHomeCatalog()

        // Prüfe im Hintergrund automatisch auf GitHub-Updates
        lifecycleScope.launch {
            availableRelease = UpdateManager.checkForUpdate(this@MainActivity, isTvApp = true)
        }

        setContent {
            AniWorldTvTheme {
                val activeDetail = detailStack.lastOrNull()

                if (activeDetail == null) {
                    HomeScreen(
                        uiState = homeUiState,
                        sections = homeSections,
                        isLoading = isLoadingHome,
                        errorMessage = homeError,
                        onRetry = { loadHomeCatalog() },
                        onOpenAnime = { card ->
                            detailStack = detailStack + card
                        },
                        onResumeWatch = { entry ->
                            resumeFromProgress(entry)
                        },
                        onUpdateFound = { release ->
                            availableRelease = release
                        }
                    )
                } else {
                    BackHandler(enabled = true) {
                        detailStack = detailStack.dropLast(1)
                    }
                    key(activeDetail.cleanSlug) {
                        DetailScreen(
                            animeSlug = activeDetail.cleanSlug,
                            initialTitle = activeDetail.title,
                            initialCover = activeDetail.coverUrl,
                            onBack = {
                                detailStack = detailStack.dropLast(1)
                            },
                            onPlayEpisode = { detail, episode, episodeList, lang ->
                                launchPlayer(detail, episode, episodeList, lang)
                            }
                        )
                    }
                }

                // Update-Dialog als Overlay auf dem TV
                availableRelease?.let { release ->
                    TvUpdateOverlay(
                        release = release,
                        onDismiss = { availableRelease = null }
                    )
                }
            }
        }
    }

    private fun loadHomeCatalog() {
        isLoadingHome = true
        homeError = null
        lifecycleScope.launch {
            runCatching {
                AniWorldScraper.fetchHomeCatalog()
            }.onSuccess { sections ->
                homeSections = sections
                isLoadingHome = false
            }.onFailure { err ->
                homeError = err.message ?: "Netzwerkfehler"
                isLoadingHome = false
            }
        }
    }

    private fun launchPlayer(
        detail: AnimeDetail,
        episode: EpisodeItem,
        episodeList: List<EpisodeItem>,
        preferredLanguage: LanguageOption
    ) {
        startActivity(
            PlayerActivity.intent(
                context = this,
                animeSlug = detail.slug,
                animeTitle = detail.title,
                coverUrl = detail.coverUrl,
                episode = episode,
                seasonEpisodes = ArrayList(episodeList),
                preferredLangKey = preferredLanguage.langKey
            )
        )
    }

    private fun resumeFromProgress(entry: WatchProgressEntry) {
        val ep = EpisodeItem(
            number = entry.episodeNumber,
            seasonLabel = entry.seasonLabel,
            path = entry.episodePath,
            titleDe = entry.episodeTitle,
            titleEn = "",
            languages = listOfNotNull(LanguageOption.fromKey(entry.preferredLangKey))
        )
        startActivity(
            PlayerActivity.intent(
                context = this,
                animeSlug = entry.animeSlug,
                animeTitle = entry.animeTitle,
                coverUrl = entry.coverUrl,
                episode = ep,
                seasonEpisodes = arrayListOf(ep),
                preferredLangKey = entry.preferredLangKey
            )
        )
    }
}
