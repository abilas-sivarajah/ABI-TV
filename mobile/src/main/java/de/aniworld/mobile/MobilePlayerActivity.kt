package de.aniworld.mobile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import de.aniworld.mobile.cast.MobileCastManager
import de.aniworld.mobile.ui.components.CastBottomSheet
import de.aniworld.mobile.ui.theme.AccentCyan
import de.aniworld.mobile.ui.theme.AniWorldMobileTheme
import de.aniworld.mobile.ui.theme.BgSurface
import de.aniworld.mobile.ui.theme.TextMuted
import de.aniworld.mobile.ui.theme.TextPrimary
import de.aniworld.mobile.ui.theme.TextSecondary
import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.model.AnimeDetail
import de.aniworld.tv.data.model.EpisodeItem
import de.aniworld.tv.data.model.HosterLink
import de.aniworld.tv.data.model.LanguageOption
import de.aniworld.tv.data.model.LiveTvChannel
import de.aniworld.tv.data.model.LiveTvNode
import de.aniworld.tv.data.model.ResolvedStream
import de.aniworld.tv.data.model.SeasonLink
import de.aniworld.tv.data.model.WatchProgressEntry
import de.aniworld.tv.data.remote.AniWorldScraper
import de.aniworld.tv.data.remote.FilmpalastScraper
import de.aniworld.tv.data.remote.HdFilmeScraper
import de.aniworld.tv.data.remote.HttpClient
import de.aniworld.tv.data.remote.KissAsianScraper
import de.aniworld.tv.data.remote.LiveTvRepository
import de.aniworld.tv.data.remote.LiveTvTree
import de.aniworld.tv.data.remote.SerienStreamScraper
import de.aniworld.tv.data.remote.StreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LiveRed = Color(0xFFEF4444)
private val OnAccent = Color(0xFF050811)

/** Seitenleiste rechts über dem Bild. */
private enum class PlayerPanel { EPISODES, LANGUAGE, CHANNELS }

/**
 * Player im Streaming-Stil wie auf TV, PC und iPhone: Folgen und Sprache im Player, "Intro
 * überspringen", "Nächste Folge", Pausen-Ansicht. Live-Sender: Senderwahl des Ordners und
 * selbstständiges Neuverbinden. Filme aus IPTV-Listen: Zeitleiste und Spulen.
 */
@UnstableApi
class MobilePlayerActivity : ComponentActivity() {

    // Als Compose-Zustand, damit die Videofläche nach Neuverbinden/Hoster-Wechsel den neuen Player bekommt
    private var exoPlayer by mutableStateOf<ExoPlayer?>(null)

    private var animeSlug: String = ""
    private var animeTitle by mutableStateOf("")
    private var coverUrl: String = ""
    private var selectedLangKey by mutableIntStateOf(LanguageOption.GER_DUB.langKey)
    private var currentEpisode by mutableStateOf<EpisodeItem?>(null)
    private var seasonEpisodes by mutableStateOf<List<EpisodeItem>>(emptyList())
    private var seasons by mutableStateOf<List<SeasonLink>>(emptyList())
    private var titleDescription by mutableStateOf("")
    private var seasonsLoadedFor = ""
    private var introSkippedFor by mutableStateOf<String?>(null)
    private var availableHosters by mutableStateOf<List<HosterLink>>(emptyList())
    private var activeHoster by mutableStateOf<HosterLink?>(null)
    private val failedHosterPaths = mutableSetOf<String>()

    private var isResolving by mutableStateOf(true)
    private var statusMessage by mutableStateOf("Starte …")
    private var errorMessage by mutableStateOf<String?>(null)
    private var resolveJob: Job? = null
    private var isBuffering by mutableStateOf(false)
    private var isPlaying by mutableStateOf(true)
    /** Soll laufen (nicht von Hand angehalten)? Beim Puffern ist isPlaying kurz false, das hier nicht. */
    private var playWhenReady by mutableStateOf(true)
    private var currentPositionMs by mutableLongStateOf(0L)
    private var durationMs by mutableLongStateOf(0L)
    private var controlsVisible by mutableStateOf(true)
    private var lastInteractionTime by mutableLongStateOf(System.currentTimeMillis())
    /** Zählt jede Sekunde hoch, damit zeitabhängige Anzeigen (Pausen-Ansicht) neu geprüft werden. */
    private var idleTick by mutableIntStateOf(0)
    private var showCastSheet by mutableStateOf(false)
    private var sidePanel by mutableStateOf<PlayerPanel?>(null)

    // Live-Sender und Titel aus IPTV-Listen: direkte Adresse statt Hoster-Auflösung
    private var isLiveStream by mutableStateOf(false)
    private var isDirectVod by mutableStateOf(false)
    private var liveStreamUrl by mutableStateOf("")
    private var liveHeaders: Map<String, String> = emptyMap()
    private var liveRetryCount = 0
    /** Seit wann der Live-Sender puffert (0 = gerade nicht). */
    private var liveStallSince = 0L
    private var pausedInBackground = false

    /** Ordner des laufenden Senders; darin wird über "Sender" umgeschaltet. */
    private var channelFolderId by mutableStateOf<String?>(null)
    private var channelPanelNode by mutableStateOf<LiveTvNode?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterFullscreen()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        animeSlug = intent.getStringExtra(EXTRA_ANIME_SLUG).orEmpty()
        animeTitle = intent.getStringExtra(EXTRA_ANIME_TITLE).orEmpty()
        coverUrl = intent.getStringExtra(EXTRA_COVER_URL).orEmpty()
        selectedLangKey = intent.getIntExtra(EXTRA_PREFERRED_LANG_KEY, LocalStorage.preferredLanguage.langKey)

        val ep = IntentCompat.getSerializableExtra(intent, EXTRA_EPISODE, EpisodeItem::class.java)
        val liveUrl = intent.getStringExtra(EXTRA_LIVE_STREAM_URL).orEmpty()

        if (liveUrl.isNotBlank()) {
            channelFolderId = intent.getStringExtra(EXTRA_FOLDER_ID)
            @Suppress("UNCHECKED_CAST")
            val headers = (intent.getSerializableExtra(EXTRA_LIVE_HEADERS) as? HashMap<String, String>).orEmpty()
            startDirect(animeTitle, liveUrl, coverUrl, headers)
        } else if (ep != null) {
            loadEpisodeAndPlay(ep)
        } else {
            errorMessage = "Keine Episode ausgewählt."
            isResolving = false
        }

        setContent {
            AniWorldMobileTheme {
                PlayerScreen()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Nach Dialogen, Benachrichtigungsleiste usw. wieder das volle Bild
        if (hasFocus) enterFullscreen()
    }

    // ------------------- Oberfläche -------------------

    private fun touch() {
        lastInteractionTime = System.currentTimeMillis()
    }

    private fun showControls() {
        controlsVisible = true
        touch()
    }

    @Composable
    private fun PlayerScreen() {
        val connectedDevice by MobileCastManager.connectedDevice.collectAsState()

        LaunchedEffect(Unit) {
            var tick = 0
            while (true) {
                delay(1000L)
                exoPlayer?.let { p ->
                    currentPositionMs = p.currentPosition.coerceAtLeast(0L)
                    durationMs = p.duration.coerceAtLeast(0L)
                    isPlaying = p.isPlaying
                    checkLiveStall(p)
                }
                tick++
                if (tick % 8 == 0) persistCurrentProgress()
                if (controlsVisible && sidePanel == null && !isResolving && errorMessage == null &&
                    playWhenReady && System.currentTimeMillis() - lastInteractionTime > CONTROLS_HIDE_MS
                ) {
                    controlsVisible = false
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

        BackHandler(enabled = sidePanel != null) { sidePanel = null }

        val videoReady = !isResolving && errorMessage == null && exoPlayer != null
        val isSeries = seasonEpisodes.size > 1 || seasons.size > 1
        val nextEpisode = findNextEpisode()
        val nextSeason = if (nextEpisode != null) null else findNextSeason()
        val longEnough = durationMs > 10 * 60_000L
        val showIntroSkip = isSeries && videoReady && longEnough &&
            currentPositionMs in 5_000L..INTRO_WINDOW_MS && introSkippedFor != currentEpisode?.path
        val showNextUp = isSeries && videoReady && longEnough &&
            durationMs - currentPositionMs <= CREDITS_WINDOW_MS && (nextEpisode != null || nextSeason != null)

        // Pausen-Ansicht nur nach einer echten Pause (nicht bei Live und nicht beim Puffern)
        val pausedIdle = idleTick >= 0 && !isLiveStream && !playWhenReady && videoReady && sidePanel == null &&
            System.currentTimeMillis() - lastInteractionTime > PAUSE_INFO_DELAY_MS

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    when {
                        sidePanel != null -> sidePanel = null
                        pausedIdle -> showControls()
                        else -> {
                            controlsVisible = !controlsVisible
                            touch()
                        }
                    }
                }
        ) {
            val playerInstance = exoPlayer
            if (playerInstance != null && errorMessage == null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                            this.player = playerInstance
                        }
                    },
                    update = { view ->
                        if (view.player !== playerInstance) view.player = playerInstance
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (pausedIdle) PauseInfo(isSeries)

            AnimatedVisibility(
                // Bei offener Seitenleiste nur das Bild: Sonst ragt die Bedienung halb unter die Leiste
                visible = (controlsVisible || isResolving || errorMessage != null) && !pausedIdle && sidePanel == null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                ControlsOverlay(
                    connected = connectedDevice != null,
                    isSeries = isSeries,
                    nextEpisode = nextEpisode,
                    nextSeason = nextSeason
                )
            }

            // Lädt nach dem Start oder hängt kurz
            if ((isResolving || (isBuffering && errorMessage == null)) && !pausedIdle) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = AccentCyan)
                    if (isResolving) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = statusMessage, color = TextPrimary, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                }
            }

            errorMessage?.let { ErrorCard(it, Modifier.align(Alignment.Center)) }

            if ((showIntroSkip || showNextUp) && !pausedIdle && sidePanel == null) {
                val label = when {
                    showNextUp && nextEpisode != null -> "Nächste Folge"
                    showNextUp -> "Nächste Staffel"
                    else -> "Intro überspringen"
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .displayCutoutPadding()
                        .padding(end = 20.dp, bottom = if (controlsVisible) 104.dp else 32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .clickable {
                            touch()
                            if (showNextUp) {
                                if (nextEpisode != null) playNextEpisode() else playNextSeason()
                            } else {
                                seekBy(INTRO_SKIP_MS)
                                introSkippedFor = currentEpisode?.path
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (showNextUp) Icons.Filled.SkipNext else Icons.Filled.FastForward,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = label, color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            AnimatedVisibility(
                visible = sidePanel != null,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                SidePanel()
            }

            if (showCastSheet) {
                CastBottomSheet(onDismiss = { showCastSheet = false })
            }
        }
    }

    @Composable
    private fun ControlsOverlay(
        connected: Boolean,
        isSeries: Boolean,
        nextEpisode: EpisodeItem?,
        nextSeason: SeasonLink?
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.75f),
                        0.3f to Color.Transparent,
                        0.6f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.88f)
                    )
                )
                .displayCutoutPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Oben: zurück, Titel, auf dem TV abspielen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { finish() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück", tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = animeTitle,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    currentEpisode?.takeIf { isSeries }?.let { ep ->
                        val season = when {
                            ep.seasonLabel.equals("Filme", true) -> "Filme & Specials"
                            ep.seasonLabel.isBlank() -> ""
                            else -> "St. ${ep.seasonLabel} · "
                        }
                        val name = ep.displayTitle.takeIf { !it.startsWith("Folge ") }?.let { "  $it" }.orEmpty()
                        Text(
                            text = "${season}Flg. ${ep.number}$name",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = {
                    touch()
                    handOffToTv(connected)
                }) {
                    Icon(
                        imageVector = if (connected) Icons.Filled.CastConnected else Icons.Filled.Cast,
                        contentDescription = "Auf TV abspielen",
                        tint = if (connected) AccentCyan else TextPrimary
                    )
                }
            }

            // Mitte: 10 s zurück, Play/Pause, 10 s vor
            if (!isResolving && errorMessage == null && !isBuffering) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(36.dp)
                ) {
                    if (!isLiveStream) {
                        IconButton(onClick = { seekBy(-10_000L); touch() }, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Filled.Replay10, contentDescription = "10 Sekunden zurück", tint = TextPrimary, modifier = Modifier.size(34.dp))
                        }
                    }
                    IconButton(
                        onClick = { togglePause(); touch() },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(AccentCyan)
                    ) {
                        Icon(
                            imageVector = if (playWhenReady) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playWhenReady) "Pause" else "Abspielen",
                            tint = OnAccent,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    if (!isLiveStream) {
                        IconButton(onClick = { seekBy(10_000L); touch() }, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Filled.Forward10, contentDescription = "10 Sekunden vor", tint = TextPrimary, modifier = Modifier.size(34.dp))
                        }
                    }
                }
            }

            // Unten: Zeitleiste bzw. LIVE, darunter die Knöpfe
            if (!isResolving && errorMessage == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 4.dp)
                ) {
                    if (isLiveStream) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(LiveRed))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "LIVE", color = LiveRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        TimeBar()
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSeries) {
                            BarButton(Icons.Filled.VideoLibrary, "Folgen") { sidePanel = PlayerPanel.EPISODES }
                        }
                        if (availableLanguages.isNotEmpty()) {
                            BarButton(Icons.Filled.Subtitles, "Sprache") { sidePanel = PlayerPanel.LANGUAGE }
                        }
                        if (channelFolderId != null) {
                            BarButton(Icons.Filled.PlaylistPlay, if (isDirectVod) "Liste" else "Sender") { openChannelPanel() }
                        }
                        if (isLiveStream) {
                            BarButton(Icons.Filled.Refresh, "Neu verbinden") { reconnectLive() }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (nextEpisode != null) {
                            BarButton(Icons.Filled.SkipNext, "Nächste Folge") { playNextEpisode() }
                        } else if (nextSeason != null) {
                            BarButton(Icons.Filled.SkipNext, "Nächste Staffel") { playNextSeason() }
                        }
                    }
                }
            }
        }
    }

    /** Zeitleiste: gespult wird erst beim Loslassen, sonst springt das Bild bei jeder Fingerbewegung. */
    @Composable
    private fun TimeBar() {
        var scrubbing by remember { mutableStateOf(false) }
        var scrubFraction by remember { mutableFloatStateOf(0f) }
        val fraction = when {
            scrubbing -> scrubFraction
            durationMs > 0 -> (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
            else -> 0f
        }
        val shownPosition = if (scrubbing) (scrubFraction * durationMs).toLong() else currentPositionMs
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = formatDuration(shownPosition), color = TextPrimary, fontSize = 12.sp)
            Slider(
                value = fraction,
                enabled = durationMs > 0,
                onValueChange = {
                    scrubbing = true
                    scrubFraction = it
                    touch()
                },
                onValueChangeFinished = {
                    if (scrubbing && durationMs > 0) {
                        val target = (scrubFraction * durationMs).toLong()
                        exoPlayer?.seekTo(target)
                        currentPositionMs = target
                    }
                    scrubbing = false
                    touch()
                },
                colors = SliderDefaults.colors(
                    thumbColor = AccentCyan,
                    activeTrackColor = AccentCyan,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            )
            // Restzeit wie bei Streaming-Apps
            Text(
                text = if (durationMs > 0) "-${formatDuration(durationMs - shownPosition)}" else "--:--",
                color = TextPrimary,
                fontSize = 12.sp
            )
        }
    }

    @Composable
    private fun BarButton(icon: ImageVector, label: String, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    touch()
                    onClick()
                }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }

    /** Pausen-Ansicht wie bei Netflix: nach kurzer Pause ohne Eingabe Titel und Beschreibung über dem Bild. */
    @Composable
    private fun PauseInfo(isSeries: Boolean) {
        val episode = currentEpisode
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.88f), Color.Black.copy(alpha = 0.45f)))
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    showControls()
                    exoPlayer?.play()
                }
                .displayCutoutPadding()
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 32.dp, end = 32.dp)
                    .widthIn(max = 520.dp)
            ) {
                Text(text = "Sie sehen gerade", color = TextSecondary, fontSize = 13.sp)
                Text(
                    text = animeTitle,
                    color = Color.White,
                    fontSize = 28.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (isSeries && episode != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (episode.seasonLabel.equals("Filme", true)) "Filme & Specials" else "Staffel ${episode.seasonLabel}",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = episode.displayTitle.takeIf { it.isNotBlank() && !it.startsWith("Folge ") }
                            ?.let { "$it: Flg. ${episode.number}" } ?: "Folge ${episode.number}",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (titleDescription.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = titleDescription,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = "Angehalten",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 20.dp)
            )
        }
    }

    @Composable
    private fun ErrorCard(message: String, modifier: Modifier) {
        Column(
            modifier = modifier
                .padding(24.dp)
                .widthIn(max = 460.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(BgSurface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Wiedergabefehler", color = AccentCyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = message, color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { finish() }) {
                    Text("Zurück", color = TextPrimary)
                }
                Button(
                    onClick = {
                        if (liveStreamUrl.isNotBlank()) {
                            reconnectLive()
                        } else {
                            currentEpisode?.let { loadEpisodeAndPlay(it) }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = OnAccent)
                ) {
                    Text("Erneut versuchen", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // ------------------- Seitenleiste: Folgen, Sprache, Sender -------------------

    @Composable
    private fun SidePanel() {
        BoxWithConstraints {
            Column(
                modifier = Modifier
                    .width((maxWidth * 0.52f).coerceIn(280.dp, 420.dp))
                    .fillMaxHeight()
                    .background(BgSurface.copy(alpha = 0.97f))
                    // Tippen in die Leiste darf sie nicht schließen
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { touch() }
                    .displayCutoutPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                when (sidePanel) {
                    PlayerPanel.EPISODES -> EpisodesPanel()
                    PlayerPanel.LANGUAGE -> LanguagePanel()
                    PlayerPanel.CHANNELS -> ChannelsPanel()
                    null -> Unit
                }
            }
        }
    }

    @Composable
    private fun PanelHeader(title: String, onBack: (() -> Unit)? = null) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück", tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { sidePanel = null }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Schließen", tint = TextSecondary)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
    }

    @Composable
    private fun EpisodesPanel() {
        val current = currentEpisode
        val watched by LocalStorage.watchedEpisodesFlow.collectAsState()
        var viewSeason by remember { mutableStateOf(currentSeason) }
        var episodes by remember { mutableStateOf(seasonEpisodes) }
        var choosingSeason by remember { mutableStateOf(false) }
        var loading by remember { mutableStateOf(false) }

        fun seasonLabel(season: SeasonLink?): String = when {
            season == null -> "Folgen"
            season.isMovies -> "Filme & Specials"
            else -> "Staffel ${season.label}"
        }

        if (choosingSeason) {
            PanelHeader(animeTitle)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(seasons, key = { it.path }) { season ->
                    PanelRow(selected = season == viewSeason, onClick = {
                        choosingSeason = false
                        viewSeason = season
                        if (season == currentSeason) {
                            episodes = seasonEpisodes
                        } else {
                            loading = true
                            lifecycleScope.launch {
                                episodes = runCatching { fetchSeasonEpisodes(season) }.getOrDefault(emptyList())
                                loading = false
                            }
                        }
                    }) {
                        Text(
                            text = seasonLabel(season),
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (season == currentSeason) {
                            Text(text = "läuft", color = AccentCyan, fontSize = 12.sp)
                        }
                    }
                }
            }
            return
        }

        PanelHeader(
            title = seasonLabel(viewSeason),
            onBack = if (seasons.size > 1) ({ choosingSeason = true }) else null
        )
        if (loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentCyan)
            }
            return
        }
        val initialIndex = remember(episodes) { episodes.indexOfFirst { it.path == current?.path }.coerceAtLeast(0) }
        LazyColumn(
            state = rememberLazyListState(initialFirstVisibleItemIndex = (initialIndex - 1).coerceAtLeast(0)),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(episodes, key = { it.path }) { episode ->
                val isCurrent = episode.path == current?.path
                PanelRow(selected = isCurrent, onClick = {
                    if (!isCurrent) playEpisodeOf(episode, episodes)
                }) {
                    Text(
                        text = episode.number.toString(),
                        color = if (isCurrent) AccentCyan else TextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(34.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = episode.displayTitle.ifBlank { "Folge ${episode.number}" },
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isCurrent) {
                            Text(text = "Läuft gerade", color = AccentCyan, fontSize = 11.sp)
                        }
                    }
                    if (episode.path in watched) {
                        Icon(Icons.Filled.Check, contentDescription = "Gesehen", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun LanguagePanel() {
        PanelHeader("Sprache")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(availableLanguages, key = { it.langKey }) { language ->
                val active = activeHoster?.language == language
                PanelRow(selected = active, onClick = {
                    sidePanel = null
                    selectLanguage(language)
                }) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(language.badgeColorHex))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = language.shortLabel, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(text = language.fullLabel, color = TextSecondary, fontSize = 12.sp)
                    }
                    if (active) {
                        Icon(Icons.Filled.Check, contentDescription = "Aktiv", tint = AccentCyan, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    private fun openChannelPanel() {
        val node = channelFolderId?.let { LiveTvRepository.findFolder(it) } ?: return
        channelPanelNode = node
        sidePanel = PlayerPanel.CHANNELS
    }

    /** Senderwahl: Ordner wie in Live TV, zurück in den übergeordneten Ordner, Favoriten oben. */
    @Composable
    private fun ChannelsPanel() {
        val node = channelPanelNode ?: return
        val favorites by LocalStorage.favoriteChannelsFlow.collectAsState()
        val channels = remember(node, favorites) {
            val (fav, rest) = node.channels.partition { it.streamUrl in favorites }
            fav + rest
        }
        val parent = remember(node) { LiveTvRepository.parentFolder(node.id) }
        PanelHeader(
            title = listOf(node.icon, node.title).filter { it.isNotBlank() }.joinToString(" "),
            onBack = parent?.let { p -> { channelPanelNode = p } }
        )
        val initialIndex = remember(node) {
            (node.children.size + channels.indexOfFirst { it.streamUrl == liveStreamUrl }).coerceAtLeast(0)
        }
        LazyColumn(
            state = remember(node) { androidx.compose.foundation.lazy.LazyListState((initialIndex - 1).coerceAtLeast(0)) },
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(node.children, key = { "n:" + it.id }) { child ->
                PanelRow(selected = false, onClick = { channelPanelNode = child }) {
                    Text(
                        text = listOf(child.icon, child.title).filter { it.isNotBlank() }.joinToString(" "),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(text = child.total.toString(), color = TextMuted, fontSize = 12.sp)
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }
            items(channels, key = { "c:" + it.id }) { channel ->
                val playing = channel.streamUrl == liveStreamUrl
                val isFavorite = channel.streamUrl in favorites
                PanelRow(selected = playing, onClick = {
                    sidePanel = null
                    switchChannel(channel, node)
                }) {
                    Text(
                        text = channel.name,
                        color = if (playing) AccentCyan else TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = if (playing) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { LocalStorage.toggleFavoriteChannel(channel.streamUrl) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = if (isFavorite) "Aus Favoriten entfernen" else "Favorisieren",
                            tint = if (isFavorite) AccentCyan else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun PanelRow(selected: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent)
                .clickable {
                    touch()
                    onClick()
                }
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }

    // ------------------- Auf dem TV abspielen -------------------

    private fun handOffToTv(connected: Boolean) {
        if (!connected) {
            MobileCastManager.startDiscovery(this)
            showCastSheet = true
            return
        }
        if (liveStreamUrl.isNotBlank()) {
            lifecycleScope.launch {
                val ok = MobileCastManager.playLiveOnTv(
                    LiveTvChannel(
                        id = "live",
                        name = animeTitle,
                        logoUrl = coverUrl,
                        streamUrl = liveStreamUrl,
                        category = "live",
                        headers = liveHeaders
                    )
                )
                if (ok) {
                    exoPlayer?.pause()
                    finish()
                }
            }
            return
        }
        val ep = currentEpisode ?: return
        lifecycleScope.launch {
            val ok = MobileCastManager.playOnTv(
                animeSlug = animeSlug,
                animeTitle = animeTitle,
                coverUrl = coverUrl,
                episodePath = ep.path,
                episodeNumber = ep.number,
                seasonLabel = ep.seasonLabel,
                titleDe = ep.displayTitle,
                preferredLangKey = selectedLangKey,
                positionMs = exoPlayer?.currentPosition ?: -1L,
                durationMs = exoPlayer?.duration?.coerceAtLeast(0L) ?: 0L
            )
            if (ok) {
                exoPlayer?.pause()
                finish()
            }
        }
    }

    // ------------------- Live-Sender und Titel aus IPTV-Listen -------------------

    private fun startDirect(title: String, url: String, logo: String, headers: Map<String, String>) {
        resolveJob?.cancel()
        animeTitle = title
        coverUrl = logo
        liveStreamUrl = url
        liveHeaders = headers
        isDirectVod = LiveTvTree.isVod(url)
        isLiveStream = !isDirectVod
        currentEpisode = null
        animeSlug = ""
        seasons = emptyList()
        seasonEpisodes = emptyList()
        availableHosters = emptyList()
        activeHoster = null
        titleDescription = ""
        currentPositionMs = 0L
        durationMs = 0L
        liveRetryCount = 0
        liveStallSince = 0L
        playLive()
    }

    /** Sender aus der Senderwahl starten (Serienfolgen bekommen den Serientitel davor). */
    private fun switchChannel(channel: LiveTvChannel, folder: LiveTvNode) {
        val parent = LiveTvRepository.parentFolder(folder.id)
        val showTitle = if (folder.title.startsWith("Staffel ") || folder.title == "Weitere") parent?.title else folder.title
        val title = if (showTitle != null && EPISODE_NAME.matches(channel.name)) {
            "$showTitle · ${channel.name}"
        } else {
            channel.name
        }
        channelFolderId = folder.id
        startDirect(title, channel.streamUrl, channel.logoUrl, channel.headers)
        showControls()
    }

    /** Startet den Sender am Live-Punkt bzw. den Film an der letzten Stelle. */
    private fun playLive() {
        errorMessage = null
        isResolving = false
        startExoPlayer(
            ResolvedStream(
                streamUrl = liveStreamUrl,
                isHls = LiveTvRepository.isLikelyHls(liveStreamUrl),
                hosterName = "Live TV",
                language = LanguageOption.GER_DUB,
                headers = liveHeaders
            )
        )
    }

    private fun reconnectLive() {
        resolveJob?.cancel()
        liveRetryCount = 0
        liveStallSince = 0L
        playLive()
    }

    private fun handleLiveError(error: PlaybackException) {
        // Zu weit hinter dem Live-Punkt (z. B. nach Pause): zum aktuellen Live-Punkt springen
        val player = exoPlayer
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW && player != null) {
            player.seekToDefaultPosition()
            player.prepare()
            return
        }
        retryLive(error.errorCodeName)
    }

    /** Kurze Aussetzer der Quelle selbst abfangen, statt sofort einen Fehler zu zeigen. */
    private fun retryLive(reason: String) {
        liveStallSince = 0L
        if (liveRetryCount < MAX_LIVE_RETRIES) {
            liveRetryCount++
            isResolving = true
            statusMessage = "Verbindung unterbrochen – verbinde neu ($liveRetryCount/$MAX_LIVE_RETRIES) …"
            resolveJob?.cancel()
            resolveJob = lifecycleScope.launch {
                delay(2_000L * liveRetryCount)
                playLive()
            }
            return
        }
        isResolving = false
        errorMessage = if (isDirectVod) {
            "Der Titel ist gerade nicht erreichbar ($reason)."
        } else {
            "Der Sender ist gerade nicht erreichbar ($reason)."
        }
    }

    /** Live-Sender hängt (puffert ohne Fortschritt): nach einer Weile selbst neu verbinden. */
    private fun checkLiveStall(player: ExoPlayer) {
        val stalled = isLiveStream && !isResolving && errorMessage == null && player.playWhenReady &&
            player.playbackState == Player.STATE_BUFFERING
        if (!stalled) {
            liveStallSince = 0L
            return
        }
        val now = System.currentTimeMillis()
        if (liveStallSince == 0L) {
            liveStallSince = now
        } else if (now - liveStallSince > LIVE_STALL_MS) {
            retryLive("Sender hängt")
        }
    }

    // ------------------- Folgen und Hoster -------------------

    private fun loadEpisodeAndPlay(episode: EpisodeItem) {
        resolveJob?.cancel()
        persistCurrentProgress()
        currentPositionMs = 0L
        durationMs = 0L
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()

        currentEpisode = episode
        failedHosterPaths.clear()
        availableHosters = emptyList()
        activeHoster = null
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
                errorMessage = "Keine Streams für diese Folge gefunden."
                return@launch
            }

            availableHosters = hosters
            val ordered = hosters.sortedWith(compareBy<HosterLink> { if (it.langKey == selectedLangKey) 0 else 1 })

            if (!playFirstWorkingHoster(ordered)) {
                isResolving = false
                errorMessage = "Dieser Titel lässt sich gerade nicht abspielen. Bitte später erneut versuchen."
            }
        }
    }

    /**
     * Probiert die Hoster der Reihe nach durch und startet den ersten, der sich auflösen lässt.
     * Nicht auflösbare Hoster werden gemerkt, damit ein späterer Fallback sie überspringt.
     */
    private suspend fun playFirstWorkingHoster(candidates: List<HosterLink>): Boolean {
        for (candidate in candidates) {
            if (candidate.redirectPath in failedHosterPaths) continue
            statusMessage = "Starte …"
            val resolved = runCatching { StreamResolver.resolveHoster(candidate) }.getOrNull()
            if (resolved != null) {
                activeHoster = candidate
                selectedLangKey = candidate.langKey
                isResolving = false
                startExoPlayer(resolved)
                return true
            }
            failedHosterPaths.add(candidate.redirectPath)
        }
        return false
    }

    private fun fallbackToRemainingHosters(error: PlaybackException) {
        val current = activeHoster
        if (current != null) {
            failedHosterPaths.add(current.redirectPath)
            StreamResolver.invalidateCache(current.redirectPath)
        }
        persistCurrentProgress()
        // Alle übrigen Hoster nacheinander probieren, statt nach einem stehen zu bleiben
        val remaining = availableHosters
            .filter { it.redirectPath !in failedHosterPaths }
            .sortedWith(compareBy<HosterLink> { if (it.langKey == selectedLangKey) 0 else 1 })
        if (remaining.isEmpty()) {
            isResolving = false
            errorMessage = "Wiedergabefehler (${error.errorCodeName})."
            return
        }
        isResolving = true
        errorMessage = null
        resolveJob?.cancel()
        resolveJob = lifecycleScope.launch {
            if (!playFirstWorkingHoster(remaining)) {
                isResolving = false
                errorMessage = "Wiedergabefehler (${error.errorCodeName}) – kein weiterer Hoster verfügbar."
            }
        }
    }

    private val availableLanguages: List<LanguageOption>
        get() = availableHosters.map { it.language }.distinct().sortedBy { it.ordinal }

    /**
     * Sprache im Player wechseln: ersten funktionierenden Hoster dieser Sprache nehmen, an der
     * aktuellen Stelle weiterspielen und die Sprache als Standard für die nächsten Titel merken.
     */
    private fun selectLanguage(language: LanguageOption) {
        LocalStorage.preferredLanguage = language
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
            if (!playFirstWorkingHoster(candidates)) {
                isResolving = false
                errorMessage = "In dieser Sprache lässt sich die Folge gerade nicht abspielen."
            }
        }
    }

    // ------------------- Staffeln und nächste Folge -------------------

    private fun loadSeasonsIfNeeded() {
        if (animeSlug.isBlank()) return
        val slug = animeSlug
        val needSeasons = seasonsLoadedFor != slug
        // Aus "Weiterschauen" kommt nur die eine Folge: die ganze Staffel dazuladen,
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
                seasonEpisodes = episodes
            }
        }
    }

    private fun needsSeasonEpisodes(): Boolean {
        val episode = currentEpisode ?: return false
        return seasonEpisodes.size <= 1 || seasonEpisodes.none { it.path == episode.path }
    }

    private val currentSeason: SeasonLink?
        get() = currentEpisode?.seasonLabel?.let { label -> seasons.firstOrNull { it.label == label } }

    private fun findNextEpisode(): EpisodeItem? {
        val current = currentEpisode ?: return null
        val index = seasonEpisodes.indexOfFirst { it.path == current.path }
        return if (index >= 0) seasonEpisodes.getOrNull(index + 1) else null
    }

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

    private fun playEpisodeOf(episode: EpisodeItem, episodes: List<EpisodeItem>) {
        seasonEpisodes = episodes
        loadEpisodeAndPlay(episode)
    }

    private fun playNextEpisode() {
        findNextEpisode()?.let { loadEpisodeAndPlay(it) }
    }

    private fun playNextSeason() {
        val next = findNextSeason() ?: return
        lifecycleScope.launch {
            val episodes = runCatching { fetchSeasonEpisodes(next) }.getOrDefault(emptyList())
            episodes.firstOrNull()?.let { playEpisodeOf(it, episodes) }
        }
    }

    // ------------------- Abspielen -------------------

    private fun startExoPlayer(stream: ResolvedStream) {
        // Sprach- oder Hoster-Wechsel und Neuverbinden: an derselben Stelle weiter
        val resumePos = when {
            isLiveStream -> 0L
            currentPositionMs > 5_000L -> currentPositionMs
            else -> LocalStorage.getSavedPositionForEpisode(currentEpisode?.path.orEmpty())
        }
        releasePlayer()

        val nonUaHeaders = stream.headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) }
        val httpFactory = OkHttpDataSource.Factory(HttpClient.okHttp)
            .setUserAgent(stream.headers["User-Agent"] ?: HttpClient.USER_AGENT)
            .setDefaultRequestProperties(nonUaHeaders)

        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)
        val renderersFactory = DefaultRenderersFactory(this).setEnableDecoderFallback(true)

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
                        if (playbackState == Player.STATE_ENDED) onPlaybackEnded()
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        this@MobilePlayerActivity.isPlaying = playing
                    }

                    override fun onPlayWhenReadyChanged(ready: Boolean, reason: Int) {
                        this@MobilePlayerActivity.playWhenReady = ready
                        // Kopfhörer abgezogen, Anruf …: Bedienung zeigen
                        if (!ready) touch()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        if (liveStreamUrl.isNotBlank()) {
                            handleLiveError(error)
                        } else {
                            fallbackToRemainingHosters(error)
                        }
                    }
                })
            }

        val item = MediaItem.Builder().setUri(stream.streamUrl).apply {
            if (stream.isHls) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()

        player.setMediaItem(item)
        if (resumePos > 5_000L) player.seekTo(resumePos)
        player.playWhenReady = true
        player.prepare()
        exoPlayer = player
        playWhenReady = true
        showControls()
    }

    private fun onPlaybackEnded() {
        if (isLiveStream) {
            retryLive("Sender unterbrochen")
            return
        }
        if (isDirectVod) {
            showControls()
            return
        }
        // Ende der Folge: als gesehen merken, nächste Folge bzw. Staffel starten
        currentEpisode?.let { LocalStorage.markEpisodeWatched(it.path) }
        when {
            findNextEpisode() != null -> playNextEpisode()
            findNextSeason() != null -> playNextSeason()
            else -> showControls()
        }
    }

    private fun togglePause() {
        exoPlayer?.let { if (it.playWhenReady) it.pause() else it.play() }
    }

    private fun seekBy(deltaMs: Long) {
        if (isLiveStream) return
        val p = exoPlayer ?: return
        val dur = p.duration
        val target = (p.currentPosition + deltaMs).coerceAtLeast(0L)
        p.seekTo(if (dur != C.TIME_UNSET && dur > 0) target.coerceAtMost(dur) else target)
        currentPositionMs = p.currentPosition
    }

    private fun persistCurrentProgress() {
        // Live-Sender und Titel aus eigenen Listen haben keine Folge und kommen nicht in "Weiterschauen"
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

    override fun onStart() {
        super.onStart()
        // Live-Sender nach der Rückkehr am aktuellen Live-Punkt weiterlaufen lassen
        if (pausedInBackground && isLiveStream) {
            exoPlayer?.let {
                it.seekToDefaultPosition()
                it.play()
            }
        }
        pausedInBackground = false
    }

    override fun onStop() {
        super.onStop()
        // Ohne Hintergrund-Wiedergabe (keine Mediensession) beim Verlassen pausieren statt unsichtbar weiterzuspielen
        persistCurrentProgress()
        pausedInBackground = exoPlayer?.playWhenReady == true
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
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
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    companion object {
        private const val MAX_LIVE_RETRIES = 3
        /** So lange darf ein Live-Sender puffern, bevor neu verbunden wird. */
        private const val LIVE_STALL_MS = 15_000L
        private const val CONTROLS_HIDE_MS = 5_000L
        private const val PAUSE_INFO_DELAY_MS = 2_500L
        /** "Intro überspringen" in den ersten Minuten, springt 85 s vor (wie auf dem TV). */
        private const val INTRO_WINDOW_MS = 5 * 60_000L
        private const val INTRO_SKIP_MS = 85_000L
        /** "Nächste Folge" erscheint in den letzten 100 Sekunden (Abspann). */
        private const val CREDITS_WINDOW_MS = 100_000L
        private val EPISODE_NAME = Regex("^S\\d+ E\\d+.*")

        private const val EXTRA_LIVE_STREAM_URL = "extra_live_stream_url"
        private const val EXTRA_LIVE_HEADERS = "extra_live_headers"
        private const val EXTRA_FOLDER_ID = "extra_folder_id"
        private const val EXTRA_ANIME_SLUG = "extra_anime_slug"
        private const val EXTRA_ANIME_TITLE = "extra_anime_title"
        private const val EXTRA_COVER_URL = "extra_cover_url"
        private const val EXTRA_EPISODE = "extra_episode"
        private const val EXTRA_PREFERRED_LANG_KEY = "extra_preferred_lang_key"

        fun liveIntent(
            context: Context,
            channelName: String,
            streamUrl: String,
            logoUrl: String = "",
            headers: HashMap<String, String> = HashMap(),
            folderId: String? = null
        ): Intent = Intent(context, MobilePlayerActivity::class.java).apply {
            putExtra(EXTRA_ANIME_TITLE, channelName)
            putExtra(EXTRA_COVER_URL, logoUrl)
            putExtra(EXTRA_LIVE_STREAM_URL, streamUrl)
            putExtra(EXTRA_LIVE_HEADERS, headers)
            putExtra(EXTRA_FOLDER_ID, folderId)
        }

        fun intent(
            context: Context,
            animeSlug: String,
            animeTitle: String,
            coverUrl: String,
            episode: EpisodeItem,
            preferredLangKey: Int
        ): Intent = Intent(context, MobilePlayerActivity::class.java).apply {
            putExtra(EXTRA_ANIME_SLUG, animeSlug)
            putExtra(EXTRA_ANIME_TITLE, animeTitle)
            putExtra(EXTRA_COVER_URL, coverUrl)
            putExtra(EXTRA_EPISODE, episode)
            putExtra(EXTRA_PREFERRED_LANG_KEY, preferredLangKey)
        }
    }
}
