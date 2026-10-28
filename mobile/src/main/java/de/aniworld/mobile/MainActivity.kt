package de.aniworld.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import de.aniworld.mobile.cast.MobileCastManager
import de.aniworld.mobile.ui.components.CastBottomSheet
import de.aniworld.mobile.ui.screens.MobileDetailScreen
import de.aniworld.mobile.ui.screens.MobileHomeScreen
import de.aniworld.mobile.ui.theme.AniWorldMobileTheme
import de.aniworld.tv.data.model.AnimeCard
import de.aniworld.tv.data.model.AnimeDetail
import de.aniworld.tv.data.model.CatalogSection
import de.aniworld.tv.data.model.EpisodeItem
import de.aniworld.tv.data.model.LanguageOption
import de.aniworld.tv.data.model.WatchProgressEntry
import de.aniworld.tv.data.remote.AniWorldScraper
import de.aniworld.tv.data.update.AppRelease
import de.aniworld.tv.data.update.UpdateCheckResult
import de.aniworld.tv.data.update.UpdateManager
import de.aniworld.mobile.ui.components.MobileUpdateDialog
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var homeSections by mutableStateOf<List<CatalogSection>>(emptyList())
    private var isLoadingHome by mutableStateOf(true)
    private var homeError by mutableStateOf<String?>(null)

    private var detailStack by mutableStateOf<List<AnimeCard>>(emptyList())
    private var showCastSheet by mutableStateOf(false)
    private var availableRelease by mutableStateOf<AppRelease?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        MobileCastManager.init(this)
        loadHomeCatalog()

        // Prüfe im Hintergrund automatisch auf GitHub-Updates
        lifecycleScope.launch {
            val result = UpdateManager.checkForUpdate(this@MainActivity, isTvApp = false)
            if (result is UpdateCheckResult.Available) availableRelease = result.release
        }

        setContent {
            AniWorldMobileTheme {
                val activeDetail = detailStack.lastOrNull()

                Box(modifier = Modifier.fillMaxSize()) {
                    if (activeDetail == null) {
                        MobileHomeScreen(
                            sections = homeSections,
                            isLoading = isLoadingHome,
                            errorMessage = homeError,
                            onRetry = { loadHomeCatalog() },
                            onOpenAnime = { card -> detailStack = detailStack + card },
                            onOpenCastSheet = {
                                MobileCastManager.startDiscovery(this@MainActivity)
                                showCastSheet = true
                            },
                            onResumeWatch = { entry -> resumeFromProgress(entry) },
                            onPlayLiveChannel = { channel ->
                                startActivity(
                                    MobilePlayerActivity.liveIntent(
                                        context = this@MainActivity,
                                        channelName = channel.name,
                                        streamUrl = channel.streamUrl,
                                        logoUrl = channel.logoUrl,
                                        headers = HashMap(channel.headers)
                                    )
                                )
                            },
                            onUpdateFound = { release -> availableRelease = release }
                        )
                    } else {
                        BackHandler(enabled = true) {
                            detailStack = detailStack.dropLast(1)
                        }
                        key(activeDetail.cleanSlug) {
                            MobileDetailScreen(
                                animeSlug = activeDetail.cleanSlug,
                                initialTitle = activeDetail.title,
                                initialCover = activeDetail.coverUrl,
                                onBack = { detailStack = detailStack.dropLast(1) },
                                onOpenCastSheet = {
                                    MobileCastManager.startDiscovery(this@MainActivity)
                                    showCastSheet = true
                                },
                                onPlayOnPhone = { detail, episode, lang ->
                                    launchPhonePlayer(detail, episode, lang)
                                }
                            )
                        }
                    }

                    if (showCastSheet) {
                        CastBottomSheet(onDismiss = { showCastSheet = false })
                    }

                    // Mobile Update Dialog
                    availableRelease?.let { release ->
                        MobileUpdateDialog(
                            release = release,
                            onDismiss = { availableRelease = null }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Nach "Unbekannte Apps installieren" erlaubt: Update-Installation fortsetzen
        UpdateManager.resumePendingInstall(this)
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

    private fun launchPhonePlayer(
        detail: AnimeDetail,
        episode: EpisodeItem,
        preferredLanguage: LanguageOption
    ) {
        startActivity(
            MobilePlayerActivity.intent(
                context = this,
                animeSlug = detail.slug,
                animeTitle = detail.title,
                coverUrl = detail.coverUrl,
                episode = episode,
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
            MobilePlayerActivity.intent(
                context = this,
                animeSlug = entry.animeSlug,
                animeTitle = entry.animeTitle,
                coverUrl = entry.coverUrl,
                episode = ep,
                preferredLangKey = entry.preferredLangKey
            )
        )
    }
}
