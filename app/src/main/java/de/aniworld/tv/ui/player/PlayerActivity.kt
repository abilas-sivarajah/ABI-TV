package de.aniworld.tv.ui.player

import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import de.aniworld.tv.data.model.AnimeDetail
import de.aniworld.tv.data.model.SeasonLink
import de.aniworld.tv.ui.theme.FocusBorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import de.aniworld.tv.data.remote.KissAsianScraper
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
    private var seasonEpisodes by mutableStateOf<ArrayList<EpisodeItem>>(arrayListOf())

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

    /** Seitenleiste rechts (Folgen, Sprache) oder null. */
    private var sidePanel by mutableStateOf<PlayerPanel?>(null)
    /** Staffeln der Serie (für "Folgen" und "Nächste Staffel"); leer bei Filmen und Live TV. */
    private var seasons by mutableStateOf<List<SeasonLink>>(emptyList())
    /** Beschreibung des Titels (für die Pausen-Ansicht). */
    private var titleDescription by mutableStateOf("")
    private var seasonsLoadedFor = ""
    private var introSkippedFor by mutableStateOf<String?>(null)
    /** Zählt jede Sekunde hoch, damit zeitabhängige Anzeigen (Pausen-Ansicht) neu geprüft werden. */
    private var idleTick by mutableIntStateOf(0)
    /** Steht "Intro überspringen"/"Nächste Folge" im Bild? Dann geht OK an diesen Knopf. */
    private var overlayButtonVisible = false
    /** Wird die Pausen-Ansicht gezeigt? Dann spielt OK weiter. */
    private var pauseInfoVisible = false

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
                // Leisten nach kurzer Zeit ohne Eingabe ausblenden (nicht, solange eine Seitenleiste offen ist)
                if (controlsVisible && sidePanel == null && !isResolving && errorMessage == null) {
                    if (System.currentTimeMillis() - lastInteractionTime > CONTROLS_HIDE_MS) {
                        controlsVisible = false
                    }
                }
                idleTick++
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                persistCurrentProgress()
                releasePlayer()
            }
        }

        val videoReady = !isResolving && errorMessage == null && exoPlayer != null

        // Serien: "Intro überspringen" am Anfang, "Nächste Folge"/"Nächste Staffel" im Abspann
        val isSeries = !isLiveStream && (seasonEpisodes.size > 1 || seasons.size > 1)
        val episodePath = currentEpisode?.path
        val nextEpisode = if (isLiveStream) null else findNextEpisode()
        val nextSeason = if (isLiveStream || nextEpisode != null) null else findNextSeason()
        val longEnough = durationMs > 10 * 60_000L
        val showIntroSkip = isSeries && videoReady && longEnough &&
            currentPositionMs in 5_000L..INTRO_WINDOW_MS && introSkippedFor != episodePath
        val showNextUp = isSeries && videoReady && longEnough &&
            durationMs - currentPositionMs in 0L..CREDITS_WINDOW_MS && (nextEpisode != null || nextSeason != null)
        overlayButtonVisible = (showIntroSkip || showNextUp) && sidePanel == null

        // Pausen-Ansicht wie bei Netflix: nach kurzer Pause ohne Eingabe Titel und Beschreibung über dem Bild
        // (idleTick zählt jede Sekunde hoch, damit diese Zeitbedingung neu geprüft wird)
        val pausedIdle = idleTick >= 0 && !isPlaying && videoReady && sidePanel == null &&
            System.currentTimeMillis() - lastInteractionTime > PAUSE_INFO_DELAY_MS
        pauseInfoVisible = pausedIdle

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

            // Laden: nur "Starte …" statt Hoster-Namen
            if ((isResolving || isBuffering) && errorMessage == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    StatusCard {
                        CircularProgressIndicator(color = AccentCyan)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isResolving) statusMessage else "Lädt …",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Pausen-Ansicht
            AnimatedVisibility(
                visible = pausedIdle,
                enter = fadeIn(tween(600)),
                exit = fadeOut(tween(200)),
                modifier = Modifier.fillMaxSize()
            ) {
                PauseInfo(isSeries = isSeries)
            }

            // Leisten unten (Fortschritt, Bedienung, Titel)
            AnimatedVisibility(
                visible = controlsVisible && !pausedIdle && errorMessage == null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                PlayerBottomBar(nextEpisode = nextEpisode, nextSeason = nextSeason)
            }

            // "Intro überspringen" / "Nächste Folge" unten rechts im Bild
            if (overlayButtonVisible && !pausedIdle) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(showNextUp, controlsVisible) {
                    // Ohne Leisten bekommt der Knopf den Fokus: OK löst ihn direkt aus
                    if (!controlsVisible) {
                        delay(50)
                        runCatching { focus.requestFocus() }
                    }
                }
                VideoOverlayButton(
                    label = when {
                        showNextUp && nextEpisode != null -> "Nächste Folge"
                        showNextUp -> "Nächste Staffel"
                        else -> "Intro überspringen"
                    },
                    icon = if (showNextUp) Icons.Filled.SkipNext else Icons.Filled.FastForward,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 48.dp, bottom = if (controlsVisible) 150.dp else 56.dp)
                        .focusRequester(focus),
                    onClick = {
                        if (showNextUp) {
                            if (nextEpisode != null) playNextEpisode() else playNextSeason()
                        } else {
                            seekBy(INTRO_SKIP_MS)
                            introSkippedFor = episodePath
                        }
                        keepControlsAlive()
                    }
                )
            }

            // Seitenleisten rechts über dem Bild
            if (sidePanel != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                )
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    when (sidePanel) {
                        PlayerPanel.EPISODES -> EpisodesPanel(
                            onPick = { episode, episodes ->
                                sidePanel = null
                                playEpisodeOf(episode, episodes)
                            }
                        )
                        PlayerPanel.LANGUAGE -> LanguagePanel(
                            onPick = { language ->
                                sidePanel = null
                                selectLanguage(language)
                            }
                        )
                        null -> Unit
                    }
                }
            }

            // Fehler
            errorMessage?.let { message ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    val retryFocus = remember { FocusRequester() }
                    LaunchedEffect(message) {
                        delay(50)
                        runCatching { retryFocus.requestFocus() }
                    }
                    StatusCard {
                        Text(text = "Wiedergabefehler", color = AccentCyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = message, color = TextSecondary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TvFocusableButton(
                                text = "Erneut versuchen",
                                isPrimary = true,
                                modifier = Modifier.focusRequester(retryFocus),
                                onClick = {
                                    if (isLiveStream) reconnectLive() else currentEpisode?.let { loadEpisodeAndPlay(it) }
                                }
                            )
                            TvFocusableButton(text = "Zurück", onClick = { finish() })
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun StatusCard(content: @Composable () -> Unit) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(BgSurface.copy(alpha = 0.92f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(horizontal = 28.dp, vertical = 20.dp)
        ) {
            content()
        }
    }

    /**
     * Pausen-Ansicht: Bild abgedunkelt, links "Sie sehen gerade", Titel, Staffel, Folge und
     * Beschreibung, rechts unten "Angehalten". OK spielt weiter, jede andere Taste zeigt die Leisten.
     */
    @Composable
    private fun PauseInfo(isSeries: Boolean) {
        val episode = currentEpisode
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.82f),
                        0.6f to Color.Black.copy(alpha = 0.58f),
                        1f to Color.Black.copy(alpha = 0.45f)
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 64.dp, end = 48.dp)
                    .widthIn(max = 620.dp)
            ) {
                Text(text = "Sie sehen gerade", color = TextSecondary, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isLiveStream) "Live TV" else animeTitle,
                    color = Color.White,
                    fontSize = 38.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (isSeries && episode != null) {
                    episode.seasonLabel.takeIf { it.isNotBlank() }?.let { season ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (season.equals("Filme", true)) "Filme & Specials" else "Staffel $season",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = episode.displayTitle.takeIf { it.isNotBlank() && !it.startsWith("Folge ") }
                            ?.let { "$it: Flg. ${episode.number}" } ?: "Folge ${episode.number}",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (isLiveStream) {
                    episode?.displayTitle?.takeIf { it.isNotBlank() }?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = it, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                titleDescription.takeIf { it.isNotBlank() && !isLiveStream }?.let { description ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = description,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = "Angehalten",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 15.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 48.dp, bottom = 36.dp)
            )
        }
    }

    /**
     * Unten: Fortschritt über die ganze Breite mit Restzeit (fokussiert: Links/Rechts spult),
     * darunter links Play/Spulen, in der Mitte der Titel, rechts nächste Folge, Folgen und Sprache.
     */
    @Composable
    private fun PlayerBottomBar(nextEpisode: EpisodeItem?, nextSeason: SeasonLink?) {
        val hasEpisodes = !isLiveStream && (seasonEpisodes.size > 1 || seasons.size > 1)
        val playFocus = remember { FocusRequester() }
        val episodesFocus = remember { FocusRequester() }
        val languageFocus = remember { FocusRequester() }
        // Beim Einblenden steht der Fokus auf Play/Pause
        LaunchedEffect(Unit) {
            delay(60)
            runCatching { playFocus.requestFocus() }
        }
        // Nach dem Schließen einer Seitenleiste zurück auf den Knopf, der sie geöffnet hat
        var lastPanel by remember { mutableStateOf<PlayerPanel?>(null) }
        LaunchedEffect(sidePanel) {
            val closed = lastPanel
            lastPanel = sidePanel
            if (sidePanel == null && closed != null) {
                delay(60)
                runCatching {
                    when (closed) {
                        PlayerPanel.EPISODES -> episodesFocus.requestFocus()
                        PlayerPanel.LANGUAGE -> languageFocus.requestFocus()
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))))
                .padding(start = 36.dp, end = 36.dp, top = 40.dp, bottom = 18.dp)
        ) {
            if (!isLiveStream) {
                var barFocused by remember { mutableStateOf(false) }
                val fraction = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(22.dp)
                            .onFocusChanged { barFocused = it.isFocused }
                            .onKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                                when (event.key) {
                                    Key.DirectionLeft -> { seekBy(-10_000L); keepControlsAlive(); true }
                                    Key.DirectionRight -> { seekBy(10_000L); keepControlsAlive(); true }
                                    else -> false
                                }
                            }
                            .focusable(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (barFocused) 8.dp else 5.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = AccentCyan,
                            trackColor = Color.White.copy(alpha = 0.25f),
                            // Ohne Lücke und ohne Punkt am Ende (schlichte Leiste wie bei Streaming-Apps)
                            gapSize = 0.dp,
                            drawStopIndicator = {}
                        )
                        if (barFocused) {
                            // Punkt an der aktuellen Stelle
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = (maxWidth * fraction) - 8.dp)
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    // Restzeit wie bei Streaming-Apps
                    Text(
                        text = if (durationMs > 0) formatDuration(durationMs - currentPositionMs) else "--:--",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "LIVE", color = Color(0xFFEF4444), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                // Links: Play, Spulen
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PlayerIconButton(
                        icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        label = if (isPlaying) "Pause" else "Abspielen",
                        large = true,
                        modifier = Modifier.focusRequester(playFocus),
                        onClick = { togglePause(); keepControlsAlive() }
                    )
                    if (!isLiveStream) {
                        PlayerIconButton(icon = Icons.Filled.Replay10, label = "−10 s", onClick = { seekBy(-10_000L); keepControlsAlive() })
                        PlayerIconButton(icon = Icons.Filled.Forward10, label = "+10 s", onClick = { seekBy(10_000L); keepControlsAlive() })
                    } else {
                        PlayerIconButton(icon = Icons.Filled.Refresh, label = "Neu verbinden", onClick = { reconnectLive(); keepControlsAlive() })
                    }
                }

                // Mitte: Titel – Serie fett, dann "St. 1 · Flg. 22  Episodentitel"
                val episode = currentEpisode
                val title = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(if (isLiveStream) "Live TV" else animeTitle) }
                    if (episode != null && !isLiveStream) {
                        val isMovie = episode.seasonLabel.equals("Film", true) || (seasonEpisodes.size <= 1 && seasons.size <= 1)
                        if (!isMovie) {
                            val season = episode.seasonLabel.takeIf { it.isNotBlank() && !it.equals("Filme", true) }
                            append("   ")
                            append(if (season != null) "St. $season · Flg. ${episode.number}" else "Flg. ${episode.number}")
                            episode.displayTitle.takeIf { it.isNotBlank() && it != animeTitle && !it.startsWith("Folge ") }
                                ?.let { append("   $it") }
                        }
                    } else if (isLiveStream) {
                        episode?.displayTitle?.takeIf { it.isNotBlank() }?.let { append("   $it") }
                    }
                }
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1.4f)
                        .padding(top = 14.dp)
                )

                // Rechts: nächste Folge, Folgen, Sprache
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)
                ) {
                    if (nextEpisode != null) {
                        PlayerIconButton(icon = Icons.Filled.SkipNext, label = "Nächste Folge", onClick = { playNextEpisode(); keepControlsAlive() })
                    } else if (nextSeason != null) {
                        PlayerIconButton(icon = Icons.Filled.SkipNext, label = "Nächste Staffel", onClick = { playNextSeason(); keepControlsAlive() })
                    }
                    if (hasEpisodes) {
                        PlayerIconButton(
                            icon = Icons.Filled.VideoLibrary,
                            label = "Folgen",
                            selected = sidePanel == PlayerPanel.EPISODES,
                            modifier = Modifier.focusRequester(episodesFocus),
                            onClick = { sidePanel = PlayerPanel.EPISODES; keepControlsAlive() }
                        )
                    }
                    if (!isLiveStream && availableLanguages.isNotEmpty()) {
                        PlayerIconButton(
                            icon = Icons.Filled.Subtitles,
                            label = "Sprache",
                            selected = sidePanel == PlayerPanel.LANGUAGE,
                            modifier = Modifier.focusRequester(languageFocus),
                            onClick = { sidePanel = PlayerPanel.LANGUAGE; keepControlsAlive() }
                        )
                    }
                }
            }
        }
    }

    /** Rundes Symbol; mit Fokus hell hinterlegt, darunter steht dann, was es tut. */
    @Composable
    private fun PlayerIconButton(
        icon: ImageVector,
        label: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        large: Boolean = false,
        selected: Boolean = false
    ) {
        var focused by remember { mutableStateOf(false) }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
            Box(
                modifier = modifier
                    .size(if (large) 50.dp else 44.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            focused -> Color.White
                            selected -> Color.White.copy(alpha = 0.22f)
                            else -> Color.Transparent
                        }
                    )
                    .onFocusChanged { focused = it.isFocused }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (focused) Color.Black else Color.White,
                    modifier = Modifier.size(if (large) 34.dp else 28.dp)
                )
            }
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .padding(top = 3.dp)
                    .alpha(if (focused) 1f else 0f)
            )
        }
    }

    /** Knopf über dem Bild ("Intro überspringen", "Nächste Folge"). */
    @Composable
    private fun VideoOverlayButton(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
        var focused by remember { mutableStateOf(false) }
        val shape = RoundedCornerShape(8.dp)
        Row(
            modifier = modifier
                .clip(shape)
                .background(if (focused) Color.White else Color(0xFF1C1C1C).copy(alpha = 0.85f))
                .border(1.dp, Color.White.copy(alpha = if (focused) 1f else 0.5f), shape)
                .onFocusChanged { focused = it.isFocused }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = if (focused) Color.Black else Color.White, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, color = if (focused) Color.Black else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }

    /** Rahmen der Seitenleisten (Folgen, Sprache). */
    @Composable
    private fun SidePanelFrame(content: @Composable ColumnScope.() -> Unit) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .fillMaxHeight()
                .background(BgSurface)
                .padding(16.dp),
            content = content
        )
    }

    /**
     * Folgen-Leiste wie bei Netflix: oben "‹ Staffel N", darunter die Folgen. OK auf "‹ Staffel"
     * zeigt alle Staffeln (und "Filme & Specials") untereinander; die gewählte trägt ein Häkchen.
     */
    @Composable
    private fun EpisodesPanel(onPick: (EpisodeItem, List<EpisodeItem>) -> Unit) {
        val scope = rememberCoroutineScope()
        val currentPath = currentEpisode?.path
        var viewSeason by remember { mutableStateOf(currentSeason) }
        var viewEpisodes by remember { mutableStateOf<List<EpisodeItem>>(seasonEpisodes) }
        var choosingSeason by remember { mutableStateOf(false) }
        var loading by remember { mutableStateOf(false) }
        val listState = rememberLazyListState()
        val itemFocus = remember { FocusRequester() }
        var focusTarget by remember { mutableStateOf(currentPath) }

        fun seasonLabel(season: SeasonLink?): String = when {
            season == null -> "Folgen"
            season.isMovies -> "Filme & Specials"
            else -> "Staffel ${season.label}"
        }

        fun showSeason(season: SeasonLink) {
            choosingSeason = false
            viewSeason = season
            if (season == currentSeason) {
                viewEpisodes = seasonEpisodes
                focusTarget = currentPath
                return
            }
            scope.launch {
                loading = true
                viewEpisodes = runCatching { fetchSeasonEpisodes(season) }.getOrDefault(emptyList())
                loading = false
                focusTarget = viewEpisodes.firstOrNull()?.path
            }
        }

        // Fokus auf die laufende (bzw. erste) Folge oder die gewählte Staffel
        LaunchedEffect(choosingSeason, focusTarget, loading, viewEpisodes) {
            if (loading) return@LaunchedEffect
            val index = if (choosingSeason) {
                seasons.indexOf(viewSeason).coerceAtLeast(0)
            } else {
                viewEpisodes.indexOfFirst { it.path == focusTarget }.coerceAtLeast(0)
            }
            runCatching { listState.scrollToItem((index - 2).coerceAtLeast(0)) }
            delay(80)
            runCatching { itemFocus.requestFocus() }
        }

        SidePanelFrame {
            // ---------- Kopf ----------
            if (choosingSeason) {
                Text(
                    text = animeTitle,
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                val canChoose = seasons.size > 1
                PanelRow(
                    selected = false,
                    enabled = canChoose,
                    onClick = { if (canChoose) choosingSeason = true }
                ) {
                    if (canChoose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Staffel wählen",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = seasonLabel(viewSeason),
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            // ---------- Inhalt ----------
            when {
                choosingSeason -> LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(seasons, key = { it.path }) { season ->
                        val selected = season == viewSeason
                        PanelRow(
                            selected = selected,
                            modifier = if (selected) Modifier.focusRequester(itemFocus) else Modifier,
                            onClick = { showSeason(season) }
                        ) {
                            Box(modifier = Modifier.width(30.dp), contentAlignment = Alignment.CenterStart) {
                                if (selected) {
                                    Icon(imageVector = Icons.Filled.Check, contentDescription = "Gewählt", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                            Text(
                                text = seasonLabel(season),
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            if (season == currentSeason) {
                                Text(text = "läuft", color = AccentCyan, fontSize = 12.sp)
                            }
                        }
                    }
                }

                loading -> Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentCyan)
                }

                else -> LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(viewEpisodes, key = { it.path }) { episode ->
                        val isCurrent = episode.path == currentPath
                        PanelRow(
                            selected = isCurrent,
                            modifier = if (episode.path == focusTarget) Modifier.focusRequester(itemFocus) else Modifier,
                            onClick = { if (!isCurrent) onPick(episode, viewEpisodes) }
                        ) {
                            Text(
                                text = "${episode.number}",
                                color = if (isCurrent) AccentCyan else TextSecondary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(38.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = episode.displayTitle.ifBlank { "Folge ${episode.number}" },
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isCurrent) Text(text = "Läuft gerade", color = AccentCyan, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    /** Sprachen der aktuellen Folge; die gewählte wird auch für die nächsten Titel gemerkt. */
    @Composable
    private fun LanguagePanel(onPick: (LanguageOption) -> Unit) {
        val active = activeHoster?.language
        val firstFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(80)
            runCatching { firstFocus.requestFocus() }
        }
        SidePanelFrame {
            Text(text = "Sprache", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(10.dp))
            val languages = availableLanguages
            val focusIndex = languages.indexOf(active).coerceAtLeast(0)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                languages.forEachIndexed { index, language ->
                    PanelRow(
                        selected = language == active,
                        modifier = if (index == focusIndex) Modifier.focusRequester(firstFocus) else Modifier,
                        onClick = { onPick(language) }
                    ) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(language.badgeColorHex)))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = language.shortLabel, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(text = language.fullLabel, color = TextSecondary, fontSize = 12.sp)
                        }
                        if (language == active) {
                            Icon(imageVector = Icons.Filled.Check, contentDescription = "Aktiv", tint = AccentCyan, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun PanelRow(
        selected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        content: @Composable RowScope.() -> Unit
    ) {
        var focused by remember { mutableStateOf(false) }
        val shape = RoundedCornerShape(10.dp)
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    when {
                        focused -> Color.White.copy(alpha = 0.16f)
                        selected -> Color.White.copy(alpha = 0.10f)
                        else -> Color.Transparent
                    }
                )
                .border(2.dp, if (focused) FocusBorder else Color.Transparent, shape)
                .onFocusChanged { focused = it.isFocused }
                .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }

    // ------------------- Sprache, Staffeln, nächste Folge -------------------

    /** Sprachen, in denen es die aktuelle Folge gibt (aus den Hostern). */
    private val availableLanguages: List<LanguageOption>
        get() = availableHosters.map { it.language }.distinct().sortedBy { it.ordinal }

    /**
     * Sprache im Player wechseln: ersten funktionierenden Hoster dieser Sprache nehmen, an der
     * aktuellen Stelle weiterspielen und die Sprache als Standard für die nächsten Titel merken.
     */
    private fun selectLanguage(language: LanguageOption) {
        LocalStorage.preferredLanguage = language
        val ep = currentEpisode ?: return
        if (activeHoster?.language == language) return
        val candidates = availableHosters.filter { it.language == language }
        if (candidates.isEmpty()) return
        persistCurrentProgress()
        currentPositionMs = exoPlayer?.currentPosition ?: currentPositionMs
        resolveJob?.cancel()
        selectedLangKey = language.langKey
        failedHosterPaths.removeAll(candidates.map { it.redirectPath }.toSet())
        isResolving = true
        errorMessage = null
        statusMessage = "Starte …"
        resolveJob = lifecycleScope.launch {
            if (!playFirstWorkingHoster(ep, candidates)) {
                isResolving = false
                errorMessage = "In dieser Sprache lässt sich die Folge gerade nicht abspielen."
            }
        }
    }

    private fun loadSeasonsIfNeeded() {
        if (isLiveStream || animeSlug.isBlank()) return
        val slug = animeSlug
        val needSeasons = seasonsLoadedFor != slug
        // Aus "Weiterschauen" oder vom Handy kommt nur die eine Folge: die ganze Staffel dazuladen,
        // sonst fehlen Folgenliste und "Nächste Folge"
        val needEpisodes = needsSeasonEpisodes()
        if (!needSeasons && !needEpisodes) return
        if (needSeasons) {
            seasonsLoadedFor = slug
            seasons = emptyList()
            titleDescription = ""
        }
        lifecycleScope.launch {
            val detail = runCatching { fetchDetailFor(slug, null) }.getOrNull() ?: return@launch
            if (animeSlug != slug) return@launch
            if (needSeasons) {
                seasons = detail.seasons
                titleDescription = detail.description
            }
            val episode = currentEpisode ?: return@launch
            if (!needsSeasonEpisodes()) return@launch
            val episodes = if (detail.episodes.any { it.path == episode.path }) {
                detail.episodes
            } else {
                val season = detail.seasons.firstOrNull { it.label == episode.seasonLabel } ?: return@launch
                runCatching { fetchDetailFor(slug, season.path).episodes }.getOrDefault(emptyList())
            }
            if (animeSlug == slug && currentEpisode?.path == episode.path && episodes.any { it.path == episode.path }) {
                seasonEpisodes = ArrayList(episodes)
            }
        }
    }

    /** Fehlt die Staffel zur laufenden Folge (nur die Folge selbst bekannt)? */
    private fun needsSeasonEpisodes(): Boolean {
        val episode = currentEpisode ?: return false
        return seasonEpisodes.size <= 1 || seasonEpisodes.none { it.path == episode.path }
    }

    /** Staffel der laufenden Folge. */
    private val currentSeason: SeasonLink?
        get() = currentEpisode?.seasonLabel?.let { label -> seasons.firstOrNull { it.label == label } }

    /** Nächste reguläre Staffel nach der laufenden (ohne "Filme"). */
    private fun findNextSeason(): SeasonLink? {
        val current = currentSeason ?: return null
        return seasons.drop(seasons.indexOf(current) + 1).firstOrNull { !it.isMovies }
    }

    private suspend fun fetchSeasonEpisodes(season: SeasonLink): List<EpisodeItem> =
        fetchDetailFor(animeSlug, season.path).episodes

    private suspend fun fetchDetailFor(slug: String, seasonPath: String?): AnimeDetail = withContext(Dispatchers.IO) {
        when {
            FilmpalastScraper.isFilmpalastSlug(slug) -> FilmpalastScraper.fetchDetail(slug)
            HdFilmeScraper.isHdFilmeSlug(slug) -> HdFilmeScraper.fetchDetail(slug, seasonPath)
            SerienStreamScraper.isStoSlug(slug) -> SerienStreamScraper.fetchDetail(slug, seasonPath)
            KissAsianScraper.isKissAsianSlug(slug) -> KissAsianScraper.fetchDetail(slug, seasonPath)
            else -> AniWorldScraper.fetchAnimeDetail(slug, seasonPath)
        }
    }

    /** Folge aus einer (evtl. anderen) Staffel spielen. */
    private fun playEpisodeOf(episode: EpisodeItem, episodes: List<EpisodeItem>) {
        persistCurrentProgress()
        seasonEpisodes = ArrayList(episodes)
        loadEpisodeAndPlay(episode)
    }

    private fun playNextEpisode() {
        val next = findNextEpisode() ?: return
        persistCurrentProgress()
        loadEpisodeAndPlay(next)
    }

    /** Erste Folge der nächsten Staffel spielen. */
    private fun playNextSeason() {
        val next = findNextSeason() ?: return
        lifecycleScope.launch {
            val episodes = runCatching { fetchSeasonEpisodes(next) }.getOrDefault(emptyList())
            episodes.firstOrNull()?.let { playEpisodeOf(it, episodes) }
        }
    }

    private fun togglePause() {
        exoPlayer?.let { if (it.isPlaying) it.pause() else it.play() }
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
        statusMessage = "Lade Folge ${episode.number} …"
        sidePanel = null
        loadSeasonsIfNeeded()

        resolveJob = lifecycleScope.launch {
            val hosters = runCatching {
                when {
                    // Nur das eindeutige Präfix: AniWorld-Pfade (/anime/stream/...) enthalten ebenfalls "/stream/"
                    FilmpalastScraper.isFilmpalastEpisodePath(episode.path) -> {
                        FilmpalastScraper.fetchHostersForFilm(episode.path)
                    }
                    episode.path.startsWith("sto_") || episode.path.startsWith("/serie/") -> {
                        SerienStreamScraper.fetchEpisodeHosters(episode.path)
                    }
                    KissAsianScraper.isKissAsianEpisodePath(episode.path) -> {
                        KissAsianScraper.fetchEpisodeHosters(episode.path)
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

            if (!playFirstWorkingHoster(episode, orderedCandidates)) {
                isResolving = false
                errorMessage = "Dieser Titel lässt sich gerade nicht abspielen. Bitte später erneut versuchen."
            }
        }
    }

    /**
     * Probiert die Hoster der Reihe nach durch und startet den ersten, der sich auflösen lässt.
     * Nicht auflösbare Hoster werden gemerkt, damit ein späterer Fallback sie überspringt.
     */
    private suspend fun playFirstWorkingHoster(episode: EpisodeItem, candidates: List<HosterLink>): Boolean {
        for (candidate in candidates) {
            if (candidate.redirectPath in failedHosterPaths) continue
            statusMessage = "Starte …"
            val resolved = runCatching { StreamResolver.resolveHoster(candidate) }.getOrNull()
            if (resolved != null) {
                activeHoster = candidate
                selectedLangKey = candidate.langKey
                isResolving = false
                startExoPlayer(episode, resolved)
                return true
            }
            failedHosterPaths.add(candidate.redirectPath)
        }
        return false
    }

    /** Automatischer Fallback nach einem Wiedergabefehler: alle übrigen Hoster nacheinander probieren. */
    private fun fallbackToRemainingHosters(error: PlaybackException) {
        val ep = currentEpisode ?: return
        val remaining = availableHosters
            .filter { it.redirectPath !in failedHosterPaths }
            .sortedWith(compareBy<HosterLink> { if (it.langKey == selectedLangKey) 0 else 1 })
        if (remaining.isEmpty()) {
            isResolving = false
            errorMessage = "Wiedergabe abgebrochen (${error.errorCodeName}). Bitte erneut versuchen."
            return
        }

        persistCurrentProgress()
        isResolving = true
        errorMessage = null
        resolveJob?.cancel()
        resolveJob = lifecycleScope.launch {
            if (!playFirstWorkingHoster(ep, remaining)) {
                isResolving = false
                errorMessage = "Wiedergabe abgebrochen (${error.errorCodeName}). Bitte erneut versuchen."
            }
        }
    }

    private fun resolveAndPlaySingleHoster(hoster: HosterLink) {
        val ep = currentEpisode ?: return
        persistCurrentProgress()
        isResolving = true
        errorMessage = null
        statusMessage = "Starte …"

        lifecycleScope.launch {
            runCatching {
                StreamResolver.resolveHoster(hoster)
            }.onSuccess { resolved ->
                activeHoster = hoster
                isResolving = false
                startExoPlayer(ep, resolved)
            }.onFailure { err ->
                isResolving = false
                errorMessage = "Wiedergabe fehlgeschlagen. Bitte erneut versuchen."
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
                            val next = findNextEpisode()
                            if (next != null) {
                                currentPositionMs = 0L
                                loadEpisodeAndPlay(next)
                            } else if (findNextSeason() != null) {
                                currentPositionMs = 0L
                                playNextSeason()
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
                        fallbackToRemainingHosters(error)
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
            val wasPauseInfo = pauseInfoVisible
            lastInteractionTime = System.currentTimeMillis()

            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_HEADSETHOOK -> {
                    togglePause()
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
                    // Zuerst eine offene Seitenleiste schließen, sonst den Player beenden
                    if (sidePanel != null) {
                        sidePanel = null
                        keepControlsAlive()
                        return true
                    }
                    persistCurrentProgress()
                    finish()
                    return true
                }
            }

            // Pausen-Ansicht: OK spielt weiter, jede andere Taste zeigt die Leisten
            if (wasPauseInfo) {
                if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER) {
                    exoPlayer?.play()
                }
                keepControlsAlive()
                return true
            }

            // Leisten ausgeblendet: Steuerkreuz steuert direkt Play/Pause und Spulen
            if (!controlsVisible && sidePanel == null && errorMessage == null) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER -> {
                        // "Intro überspringen"/"Nächste Folge" hat dann den Fokus und bekommt das OK
                        if (overlayButtonVisible) return super.dispatchKeyEvent(event)
                        togglePause()
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
            } else {
                keepControlsAlive()
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

        /** Leisten nach so langer Zeit ohne Eingabe ausblenden. */
        private const val CONTROLS_HIDE_MS = 5_000L
        /** Pausen-Ansicht erst nach kurzer Pause zeigen (kurzes Anhalten soll das Bild nicht verdecken). */
        private const val PAUSE_INFO_DELAY_MS = 2_500L
        /** "Intro überspringen" in den ersten Minuten; springt so weit vor wie bisher "+85s Intro Skip". */
        private const val INTRO_WINDOW_MS = 5 * 60_000L
        private const val INTRO_SKIP_MS = 85_000L
        /** "Nächste Folge" erscheint in den letzten 100 Sekunden (Abspann). */
        private const val CREDITS_WINDOW_MS = 100_000L

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

/** Seitenleiste rechts über dem Bild. */
private enum class PlayerPanel { EPISODES, LANGUAGE }
