package de.aniworld.tv.ui.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import de.aniworld.tv.cast.PlayCommand
import de.aniworld.tv.cast.TvCastManager
import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.model.EpisodeItem
import de.aniworld.tv.data.model.HosterLink
import de.aniworld.tv.data.model.LanguageOption
import de.aniworld.tv.data.model.ResolvedStream
import de.aniworld.tv.data.model.WatchProgressEntry
import de.aniworld.tv.data.remote.AniWorldScraper
import de.aniworld.tv.data.remote.FilmpalastScraper
import de.aniworld.tv.data.remote.HdFilmeScraper
import de.aniworld.tv.data.remote.SerienStreamScraper
import de.aniworld.tv.data.remote.HttpClient
import de.aniworld.tv.data.remote.LiveTvRepository
import de.aniworld.tv.data.remote.StreamResolver
import de.aniworld.tv.ui.components.LanguageBadge
import de.aniworld.tv.ui.components.TvFocusableButton
import de.aniworld.tv.ui.components.TvPairingOverlay
import de.aniworld.tv.ui.theme.AccentCyan
import de.aniworld.tv.ui.theme.AniWorldTvTheme
import de.aniworld.tv.ui.theme.BgElevated
import de.aniworld.tv.ui.theme.BgSurface
import de.aniworld.tv.ui.theme.TextMuted
import de.aniworld.tv.ui.theme.TextPrimary
import de.aniworld.tv.ui.theme.TextSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@UnstableApi
class PlayerActivity : ComponentActivity() {

    // Als Compose-Zustand, damit die Videofläche nach Neuverbinden/Hoster-Wechsel den neuen Player bekommt
    private var exoPlayer by mutableStateOf<ExoPlayer?>(null)

    private var animeSlug: String = ""
    private var animeTitle: String = ""
    private var coverUrl: String = ""
    private var seasonEpisodes: ArrayList<EpisodeItem> = arrayListOf()

    private var currentEpisode by mutableStateOf<EpisodeItem?>(null)
    private var availableHosters by mutableStateOf<List<HosterLink>>(emptyList())
    private var activeHoster by mutableStateOf<HosterLink?>(null)
    private var selectedLangKey by mutableStateOf(LanguageOption.GER_DUB.langKey)
    private val failedHosterPaths = mutableSetOf<String>()

    private var isLiveStream by mutableStateOf(false)
    private var directStreamUrl: String? = null
    private var streamHeaders: Map<String, String> = emptyMap()
    private var liveRetryCount = 0

    private var isResolving by mutableStateOf(true)
    private var statusMessage by mutableStateOf("Lade verfügbare Streams...")
    private var errorMessage by mutableStateOf<String?>(null)

    private var isBuffering by mutableStateOf(false)
    private var isPlaying by mutableStateOf(true)
    private var currentPositionMs by mutableLongStateOf(0L)
    private var durationMs by mutableLongStateOf(0L)
    private var controlsVisible by mutableStateOf(true)
    private var lastInteractionTime by mutableLongStateOf(System.currentTimeMillis())
    private var resolveJob: Job? = null
    private var releasedInBackground = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterFullscreen()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        handleIntent(intent)

        // Smartphone-Fernbedienungs-Befehle entgegennehmen
        TvCastManager.activePlayerControl = { action, arg ->
            when (action.lowercase()) {
                "play" -> exoPlayer?.play()
                "pause" -> exoPlayer?.pause()
                "toggle" -> exoPlayer?.let { if (it.isPlaying) it.pause() else it.play() }
                "seek" -> if (arg >= 0) exoPlayer?.seekTo(arg)
                "seek_by" -> seekBy(arg)
            }
            keepControlsAlive()
        }

        // Direkte Uebergabe bei Cast vom Smartphone während der Player bereits laeuft
        TvCastManager.activePlayerPlayCommand = { cmd ->
            val ep = EpisodeItem(
                number = cmd.episodeNumber,
                seasonLabel = cmd.seasonLabel,
                path = cmd.episodePath,
                titleDe = cmd.titleDe.ifBlank { "Folge ${cmd.episodeNumber}" },
                titleEn = "",
                languages = listOfNotNull(LanguageOption.fromKey(cmd.preferredLangKey))
            )
            animeSlug = cmd.animeSlug
            animeTitle = cmd.animeTitle
            coverUrl = cmd.coverUrl
            selectedLangKey = cmd.preferredLangKey
            seasonEpisodes = arrayListOf(ep)
            loadEpisodeAndPlay(ep)
        }

        setContent {
            AniWorldTvTheme {
                PlayerScreenContent()
                TvPairingOverlay()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val live = intent.getBooleanExtra(EXTRA_IS_LIVE, false)
        val directUrl = intent.getStringExtra(EXTRA_DIRECT_STREAM_URL)
        @Suppress("UNCHECKED_CAST")
        val headers = intent.getSerializableExtra(EXTRA_STREAM_HEADERS) as? HashMap<String, String>

        val newSlug = intent.getStringExtra(EXTRA_ANIME_SLUG).orEmpty()
        val newTitle = intent.getStringExtra(EXTRA_ANIME_TITLE).orEmpty()
        val newCover = intent.getStringExtra(EXTRA_COVER_URL).orEmpty()
        val newLangKey = intent.getIntExtra(
            EXTRA_PREFERRED_LANG_KEY,
            LocalStorage.preferredLanguage.langKey
        )

        if (newSlug.isNotBlank()) animeSlug = newSlug
        if (newTitle.isNotBlank()) animeTitle = newTitle
        if (newCover.isNotBlank()) coverUrl = newCover
        selectedLangKey = newLangKey

        if (live && !directUrl.isNullOrBlank()) {
            // Läuft gerade eine Folge (z. B. per Handy umgeschaltet), deren Fortschritt vorher sichern
            persistCurrentProgress()
            resolveJob?.cancel()
            isLiveStream = true
            directStreamUrl = directUrl
            streamHeaders = headers ?: emptyMap()
            activeHoster = null
            availableHosters = emptyList()
            seasonEpisodes = arrayListOf()
            currentPositionMs = 0L
            durationMs = 0L
            liveRetryCount = 0
            currentEpisode = EpisodeItem(
                number = 1,
                seasonLabel = LIVE_SEASON_LABEL,
                path = "live_stream",
                titleDe = newTitle.ifBlank { "Live TV" },
                titleEn = "",
                languages = emptyList()
            )
            statusMessage = "Starte Live Stream..."
            playLive()
            return
        }

        val ep = IntentCompat.getSerializableExtra(intent, EXTRA_EPISODE, EpisodeItem::class.java)
        @Suppress("UNCHECKED_CAST")
        val eps = IntentCompat.getSerializableExtra(
            intent,
            EXTRA_SEASON_EPISODES,
            ArrayList::class.java
        ) as? ArrayList<EpisodeItem>

        if (eps != null) seasonEpisodes = eps

        if (ep != null) {
            loadEpisodeAndPlay(ep)
        } else if (currentEpisode == null) {
            errorMessage = "Keine Episode ausgewählt."
            isResolving = false
        }
    }

    @Composable
    private fun PlayerScreenContent() {
        // Regelmäßiges Aktualisieren der Zeitleiste & automatisches Speichern des Fortschritts
        LaunchedEffect(Unit) {
            var tick = 0
            while (true) {
                delay(1000L)
                exoPlayer?.let { p ->
                    currentPositionMs = p.currentPosition.coerceAtLeast(0L)
                    durationMs = p.duration.coerceAtLeast(0L)
                    isPlaying = p.isPlaying

                    currentEpisode?.let { currentEp ->
                        TvCastManager.updatePlaybackStatus(
                            isPlaying = p.isPlaying,
                            title = animeTitle,
                            episodeTitle = currentEp.displayTitle,
                            seasonLabel = currentEp.seasonLabel,
                            episodeNumber = currentEp.number,
                            positionMs = currentPositionMs,
                            durationMs = durationMs
                        )
                    }
                }
                tick++
                if (tick % 8 == 0) {
                    persistCurrentProgress()
                }
                if (controlsVisible && isPlaying && !isResolving && errorMessage == null) {
                    if (System.currentTimeMillis() - lastInteractionTime > 5_000L) {
                        controlsVisible = false
                    }
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                persistCurrentProgress()
                releasePlayer()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // ExoPlayer Video-Surface
            val playerInstance = exoPlayer
            if (playerInstance != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            player = playerInstance
                        }
                    },
                    update = { view ->
                        if (view.player !== playerInstance) {
                            view.player = playerInstance
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Lade-/Auflöse-Indikator
            if (isResolving || isBuffering) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(BgSurface.copy(alpha = 0.9f))
                            .padding(horizontal = 28.dp, vertical = 20.dp)
                    ) {
                        CircularProgressIndicator(color = AccentCyan)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isResolving) statusMessage else "Puffere Video...",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Fehler-Overlay mit Hoster-Wechsel
            if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(BgSurface)
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                            .padding(28.dp)
                    ) {
                        Text(
                            text = "Wiedergabefehler",
                            color = AccentCyan,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage.orEmpty(),
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TvFocusableButton(
                                text = "Erneut versuchen",
                                isPrimary = true,
                                onClick = {
                                    if (isLiveStream) {
                                        reconnectLive()
                                    } else {
                                        currentEpisode?.let { loadEpisodeAndPlay(it) }
                                    }
                                }
                            )
                            TvFocusableButton(
                                text = "Zurück",
                                onClick = { finish() }
                            )
                        }
                    }
                }
            }

            // TV OSD-Steuerleiste (Oben: Titel & Sprache/Hoster, Unten: Seekbar, Steuerung & Nächste Folge)
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Oberer Verlauf + Episodeninfo
                    val ep = currentEpisode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.88f), Color.Transparent)
                                )
                            )
                            .padding(horizontal = 36.dp, vertical = 22.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isLiveStream) "Live TV" else animeTitle,
                                color = AccentCyan,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (ep != null) {
                                val epLabel = when {
                                    isLiveStream -> ep.displayTitle
                                    ep.seasonLabel.equals("Filme", true) -> "Film ${ep.number}: ${ep.displayTitle}"
                                    else -> "Staffel ${ep.seasonLabel} • Folge ${ep.number}: ${ep.displayTitle}"
                                }
                                Text(
                                    text = epLabel,
                                    color = TextPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isLiveStream) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFEF4444))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "🔴 LIVE SENDER",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                activeHoster?.let { h ->
                                    LanguageBadge(h.language)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(BgElevated)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "Hoster: ${h.hosterName}",
                                            color = TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Unteres Steuer-Panel
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))
                                )
                            )
                            .padding(horizontal = 36.dp, vertical = 22.dp)
                    ) {
                        if (isLiveStream) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFEF4444))
                                )
                                Text(
                                    text = "LIVE ÜBERTRAGUNG",
                                    color = Color(0xFFEF4444),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = animeTitle,
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            // Fortschrittsbalken & Zeit
                            val fraction = if (durationMs > 0) {
                                (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                            } else 0f

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = formatDuration(currentPositionMs),
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                LinearProgressIndicator(
                                    progress = { fraction },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = AccentCyan,
                                    trackColor = Color.White.copy(alpha = 0.25f)
                                )
                                Text(
                                    text = formatDuration(durationMs),
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Steuerbuttons + Sprach-/Hosterwahl + Nächste Episode
                        if (isLiveStream) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TvFocusableButton(
                                    text = if (isPlaying) "⏸ Pause" else "▶ Play",
                                    isPrimary = true,
                                    onClick = {
                                        keepControlsAlive()
                                        exoPlayer?.let { if (it.isPlaying) it.pause() else it.play() }
                                    }
                                )
                                TvFocusableButton(
                                    text = "🔄 Neu verbinden",
                                    onClick = {
                                        keepControlsAlive()
                                        reconnectLive()
                                    }
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    TvFocusableButton(
                                        text = if (isPlaying) "⏸ Pause" else "▶ Play",
                                        isPrimary = true,
                                        onClick = {
                                            keepControlsAlive()
                                            exoPlayer?.let { if (it.isPlaying) it.pause() else it.play() }
                                        }
                                    )
                                    TvFocusableButton(
                                        text = "⏪ -10s",
                                        onClick = {
                                            keepControlsAlive()
                                            seekBy(-10_000L)
                                        }
                                    )
                                    TvFocusableButton(
                                        text = "⏩ +10s",
                                        onClick = {
                                            keepControlsAlive()
                                            seekBy(10_000L)
                                        }
                                    )
                                    TvFocusableButton(
                                        text = "+85s Intro Skip",
                                        onClick = {
                                            keepControlsAlive()
                                            seekBy(85_000L)
                                        }
                                    )

                                    val nextEp = findNextEpisode()
                                    if (nextEp != null) {
                                        TvFocusableButton(
                                            text = "⏭ Folge ${nextEp.number}",
                                            onClick = {
                                                keepControlsAlive()
                                                persistCurrentProgress()
                                                loadEpisodeAndPlay(nextEp)
                                            }
                                        )
                                    }
                                }

                                // Verfügbare Hoster / Sprachen für diese Folge direkt umschaltbar
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(availableHosters, key = { it.redirectPath }) { hoster ->
                                        val isCurrent = activeHoster?.redirectPath == hoster.redirectPath
                                        TvFocusableButton(
                                            text = "${hoster.language.shortLabel} • ${hoster.hosterName}",
                                            isSelected = isCurrent,
                                            badgeColor = Color(hoster.language.badgeColorHex),
                                            onClick = {
                                                keepControlsAlive()
                                                selectedLangKey = hoster.langKey
                                                resolveAndPlaySingleHoster(hoster)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (isLiveStream) {
                                "OK: Play/Pause • Hoch/Runter: Steuerung • Zurück: Sender beenden"
                            } else {
                                "Steuerkreuz Links/Rechts: Spulen • OK: Play/Pause • Hoch/Runter: Hoster & Intro-Skip • Zurück: Ausblenden/Beenden"
                            },
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }

    /** Startet den Live-Sender (neu) am aktuellen Live-Punkt. */
    private fun playLive() {
        val url = directStreamUrl ?: return
        val ep = currentEpisode ?: return
        errorMessage = null
        isResolving = false
        startExoPlayer(
            ep,
            ResolvedStream(
                streamUrl = url,
                isHls = LiveTvRepository.isLikelyHls(url),
                hosterName = "Live TV",
                language = LanguageOption.GER_DUB,
                headers = streamHeaders
            )
        )
    }

    private fun reconnectLive() {
        resolveJob?.cancel()
        liveRetryCount = 0
        playLive()
    }

    private fun handleLiveError(error: PlaybackException) {
        // Nach Pause oder Hänger zu weit hinter dem Live-Punkt: einfach zum aktuellen Live-Punkt springen
        val player = exoPlayer
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW && player != null) {
            player.seekToDefaultPosition()
            player.prepare()
            return
        }
        // Kurze Aussetzer der Quelle selbst abfangen, statt sofort einen Fehler zu zeigen
        if (liveRetryCount < MAX_LIVE_RETRIES) {
            liveRetryCount++
            isResolving = true
            statusMessage = "Verbindung unterbrochen – verbinde neu ($liveRetryCount/$MAX_LIVE_RETRIES)..."
            resolveJob?.cancel()
            resolveJob = lifecycleScope.launch {
                delay(2_000L * liveRetryCount)
                playLive()
            }
            return
        }
        isResolving = false
        errorMessage = "Der Sender ist gerade nicht erreichbar (${error.errorCodeName})."
    }

    private fun loadEpisodeAndPlay(episode: EpisodeItem) {
        resolveJob?.cancel()
        persistCurrentProgress()
        isLiveStream = false
        directStreamUrl = null
        currentPositionMs = 0L
        durationMs = 0L
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()

        currentEpisode = episode
        failedHosterPaths.clear()
        isResolving = true
        errorMessage = null
        statusMessage = "Lade Hoster für Folge ${episode.number}..."

        resolveJob = lifecycleScope.launch {
            val hosters = runCatching {
                when {
                    episode.path.startsWith("filmpalast") || episode.path.contains("/stream/") -> {
                        FilmpalastScraper.fetchHostersForFilm(episode.path)
                    }
                    episode.path.startsWith("sto_") || episode.path.startsWith("/serie/") -> {
                        SerienStreamScraper.fetchEpisodeHosters(episode.path)
                    }
                    episode.path.startsWith("hdfilme_") -> {
                        HdFilmeScraper.fetchHostersForEpisode(episode.path)
                    }
                    else -> {
                        AniWorldScraper.fetchEpisodeHosters(episode.path)
                    }
                }
            }.getOrDefault(emptyList())

            if (hosters.isEmpty()) {
                isResolving = false
                errorMessage = "Keine Streams für diese Episode gefunden."
                return@launch
            }

            availableHosters = hosters

            // Zuerst Hoster mit der bevorzugten Sprache probieren, danach andere Sprachen als Fallback
            val orderedCandidates = hosters.sortedWith(
                compareBy<HosterLink> { if (it.langKey == selectedLangKey) 0 else 1 }
            )

            for (candidate in orderedCandidates) {
                statusMessage = "Verbinde mit ${candidate.hosterName} (${candidate.language.shortLabel})..."
                val resolved = runCatching { StreamResolver.resolveHoster(candidate) }.getOrNull()
                if (resolved != null) {
                    activeHoster = candidate
                    selectedLangKey = candidate.langKey
                    isResolving = false
                    startExoPlayer(episode, resolved)
                    return@launch
                }
            }

            isResolving = false
            errorMessage = "Keiner der Hoster (${hosters.joinToString { it.hosterName }}) konnte aufgelöst werden."
        }
    }

    private fun resolveAndPlaySingleHoster(hoster: HosterLink) {
        val ep = currentEpisode ?: return
        persistCurrentProgress()
        isResolving = true
        errorMessage = null
        statusMessage = "Wechsle zu ${hoster.hosterName} (${hoster.language.shortLabel})..."

        lifecycleScope.launch {
            runCatching {
                StreamResolver.resolveHoster(hoster)
            }.onSuccess { resolved ->
                activeHoster = hoster
                isResolving = false
                startExoPlayer(ep, resolved)
            }.onFailure { err ->
                isResolving = false
                errorMessage = "${hoster.hosterName} fehlgeschlagen: ${err.message}"
            }
        }
    }

    private fun startExoPlayer(episode: EpisodeItem, stream: ResolvedStream) {
        Log.e("PlayerActivity", "Starting ExoPlayer url=${stream.streamUrl}, headers=${stream.headers}")
        val resumePos = if (isLiveStream) {
            0L
        } else if (currentPositionMs > 5_000L) {
            currentPositionMs
        } else {
            LocalStorage.getSavedPositionForEpisode(episode.path)
        }

        releasePlayer()

        val nonUaHeaders = stream.headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) }
        val httpFactory = OkHttpDataSource.Factory(HttpClient.okHttp)
            .setUserAgent(stream.headers["User-Agent"] ?: HttpClient.USER_AGENT)
            .setDefaultRequestProperties(nonUaHeaders)

        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)

        val renderersFactory = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)

        val player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setAudioAttributes(AudioAttributes.DEFAULT, true)
                setHandleAudioBecomingNoisy(true)
                setWakeMode(C.WAKE_MODE_NETWORK)
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isBuffering = playbackState == Player.STATE_BUFFERING
                        if (playbackState == Player.STATE_READY) liveRetryCount = 0
                        if (!isLiveStream && playbackState == Player.STATE_ENDED) {
                            LocalStorage.markEpisodeWatched(episode.path)
                            findNextEpisode()?.let { next ->
                                currentPositionMs = 0L
                                loadEpisodeAndPlay(next)
                            }
                        }
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        this@PlayerActivity.isPlaying = playing
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        if (isLiveStream) {
                            handleLiveError(error)
                            return
                        }
                        // Automatischer Hoster-Fallback bei Stream-Abbruch
                        val current = activeHoster
                        if (current != null) {
                            failedHosterPaths.add(current.redirectPath)
                            StreamResolver.invalidateCache(current.redirectPath)
                        }
                        val nextCandidate = availableHosters.firstOrNull {
                            it.redirectPath !in failedHosterPaths && it.langKey == selectedLangKey
                        } ?: availableHosters.firstOrNull {
                            it.redirectPath !in failedHosterPaths
                        }
                        if (nextCandidate != null) {
                            resolveAndPlaySingleHoster(nextCandidate)
                        } else {
                            errorMessage = "Wiedergabefehler (${error.errorCodeName})"
                        }
                    }
                })
            }

        val mediaItemBuilder = MediaItem.Builder().setUri(stream.streamUrl)
        if (stream.isHls) {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }

        player.setMediaItem(mediaItemBuilder.build())
        if (!isLiveStream && resumePos > 0L) {
            player.seekTo(resumePos)
        }
        player.playWhenReady = true
        player.prepare()

        exoPlayer = player
        keepControlsAlive()
    }

    private fun findNextEpisode(): EpisodeItem? {
        val current = currentEpisode ?: return null
        val idx = seasonEpisodes.indexOfFirst { it.path == current.path }
        return if (idx >= 0 && idx + 1 < seasonEpisodes.size) {
            seasonEpisodes[idx + 1]
        } else null
    }

    private fun seekBy(deltaMs: Long) {
        // Live-Sender haben keine Zeitleiste: Spulen (auch vom Handy) ignorieren
        if (isLiveStream) return
        val p = exoPlayer ?: return
        val dur = p.duration
        val target = (p.currentPosition + deltaMs).coerceAtLeast(0L)
        p.seekTo(if (dur != C.TIME_UNSET && dur > 0) target.coerceAtMost(dur) else target)
        currentPositionMs = p.currentPosition
    }

    private fun keepControlsAlive() {
        controlsVisible = true
        lastInteractionTime = System.currentTimeMillis()
    }

    private fun persistCurrentProgress() {
        if (isLiveStream) return
        val ep = currentEpisode ?: return
        val p = exoPlayer ?: return
        val pos = p.currentPosition
        val dur = p.duration
        if (pos < 5_000L || dur <= 0L) return

        LocalStorage.saveWatchProgress(
            WatchProgressEntry(
                animeSlug = animeSlug,
                animeTitle = animeTitle,
                coverUrl = coverUrl,
                seasonLabel = ep.seasonLabel,
                episodeNumber = ep.number,
                episodeTitle = ep.displayTitle,
                episodePath = ep.path,
                preferredLangKey = selectedLangKey,
                positionMs = pos,
                durationMs = dur,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            lastInteractionTime = System.currentTimeMillis()

            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_HEADSETHOOK -> {
                    exoPlayer?.let { if (it.isPlaying) it.pause() else it.play() }
                    keepControlsAlive()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    exoPlayer?.play()
                    keepControlsAlive()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    exoPlayer?.pause()
                    keepControlsAlive()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    seekBy(30_000L)
                    keepControlsAlive()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    seekBy(-15_000L)
                    keepControlsAlive()
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    persistCurrentProgress()
                    finish()
                    return true
                }
            }

            // Wenn das OSD ausgeblendet ist, steuert das Steuerkreuz direkt Play/Pause & Spulen
            if (!controlsVisible) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER -> {
                        exoPlayer?.let { if (it.isPlaying) it.pause() else it.play() }
                        keepControlsAlive()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        seekBy(-10_000L)
                        keepControlsAlive()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        seekBy(10_000L)
                        keepControlsAlive()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        keepControlsAlive()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        super.onPause()
        persistCurrentProgress()
        exoPlayer?.pause()
    }

    override fun onStart() {
        super.onStart()
        // Nach Home-Taste/Bildschirmschoner wurde der Player in onStop freigegeben: an gleicher Stelle neu starten
        if (releasedInBackground) {
            releasedInBackground = false
            when {
                isLiveStream -> reconnectLive()
                activeHoster != null -> resolveAndPlaySingleHoster(activeHoster!!)
                else -> currentEpisode?.let { loadEpisodeAndPlay(it) }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        persistCurrentProgress()
        if (exoPlayer != null && !isFinishing) {
            releasedInBackground = true
        }
        releasePlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        TvCastManager.activePlayerControl = null
        TvCastManager.activePlayerPlayCommand = null
        TvCastManager.resetPlaybackStatus()
        resolveJob?.cancel()
        releasePlayer()
    }

    private fun releasePlayer() {
        exoPlayer?.release()
        exoPlayer = null
    }

    private fun enterFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
    }

    companion object {
        /** Kennzeichnet Live-Sender im Status für die Handy-Fernbedienung. */
        const val LIVE_SEASON_LABEL = "LIVE"
        private const val MAX_LIVE_RETRIES = 3

        const val EXTRA_IS_LIVE = "extra_is_live"
        const val EXTRA_DIRECT_STREAM_URL = "extra_direct_stream_url"
        const val EXTRA_STREAM_HEADERS = "extra_stream_headers"
        private const val EXTRA_ANIME_SLUG = "extra_anime_slug"
        private const val EXTRA_ANIME_TITLE = "extra_anime_title"
        private const val EXTRA_COVER_URL = "extra_cover_url"
        private const val EXTRA_EPISODE = "extra_episode"
        private const val EXTRA_SEASON_EPISODES = "extra_season_episodes"
        private const val EXTRA_PREFERRED_LANG_KEY = "extra_preferred_lang_key"

        fun liveIntent(
            context: Context,
            channelName: String,
            streamUrl: String,
            logoUrl: String = "",
            headers: HashMap<String, String> = HashMap()
        ): Intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra(EXTRA_IS_LIVE, true)
            putExtra(EXTRA_ANIME_TITLE, channelName)
            putExtra(EXTRA_COVER_URL, logoUrl)
            putExtra(EXTRA_DIRECT_STREAM_URL, streamUrl)
            putExtra(EXTRA_STREAM_HEADERS, headers)
        }

        fun intent(
            context: Context,
            animeSlug: String,
            animeTitle: String,
            coverUrl: String,
            episode: EpisodeItem,
            seasonEpisodes: ArrayList<EpisodeItem>,
            preferredLangKey: Int
        ): Intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra(EXTRA_ANIME_SLUG, animeSlug)
            putExtra(EXTRA_ANIME_TITLE, animeTitle)
            putExtra(EXTRA_COVER_URL, coverUrl)
            putExtra(EXTRA_EPISODE, episode)
            putExtra(EXTRA_SEASON_EPISODES, seasonEpisodes)
            putExtra(EXTRA_PREFERRED_LANG_KEY, preferredLangKey)
        }
    }
}
