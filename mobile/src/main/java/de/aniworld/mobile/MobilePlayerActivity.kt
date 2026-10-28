package de.aniworld.mobile

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import de.aniworld.mobile.cast.MobileCastManager
import de.aniworld.mobile.ui.components.CastBottomSheet
import de.aniworld.mobile.ui.theme.AccentCyan
import de.aniworld.mobile.ui.theme.AniWorldMobileTheme
import de.aniworld.mobile.ui.theme.TextMuted
import de.aniworld.mobile.ui.theme.TextPrimary
import de.aniworld.mobile.ui.theme.TextSecondary
import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.model.EpisodeItem
import de.aniworld.tv.data.model.HosterLink
import de.aniworld.tv.data.model.LanguageOption
import de.aniworld.tv.data.model.ResolvedStream
import de.aniworld.tv.data.model.WatchProgressEntry
import de.aniworld.tv.data.remote.AniWorldScraper
import de.aniworld.tv.data.remote.HdFilmeScraper
import de.aniworld.tv.data.remote.HttpClient
import de.aniworld.tv.data.remote.StreamResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@UnstableApi
class MobilePlayerActivity : ComponentActivity() {

    private var exoPlayer: ExoPlayer? = null

    private var animeSlug: String = ""
    private var animeTitle: String = ""
    private var coverUrl: String = ""
    private var selectedLangKey: Int = LanguageOption.GER_DUB.langKey
    private var currentEpisode: EpisodeItem? = null
    private var availableHosters = emptyList<HosterLink>()
    private var activeHoster: HosterLink? = null
    private val failedHosterPaths = mutableSetOf<String>()

    private var isResolving by mutableStateOf(true)
    private var statusMessage by mutableStateOf("Lade Stream...")
    private var errorMessage by mutableStateOf<String?>(null)
    private var resolveJob: Job? = null
    private var isPlaying by mutableStateOf(true)
    private var currentPositionMs by mutableLongStateOf(0L)
    private var durationMs by mutableLongStateOf(0L)
    private var controlsVisible by mutableStateOf(true)
    private var showCastSheet by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterFullscreen()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        animeSlug = intent.getStringExtra(EXTRA_ANIME_SLUG).orEmpty()
        animeTitle = intent.getStringExtra(EXTRA_ANIME_TITLE).orEmpty()
        coverUrl = intent.getStringExtra(EXTRA_COVER_URL).orEmpty()
        selectedLangKey = intent.getIntExtra(EXTRA_PREFERRED_LANG_KEY, LanguageOption.GER_DUB.langKey)

        val ep = IntentCompat.getSerializableExtra(intent, EXTRA_EPISODE, EpisodeItem::class.java)
        currentEpisode = ep

        if (ep != null) {
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

    @Composable
    private fun PlayerScreen() {
        val connectedDevice by MobileCastManager.connectedDevice.collectAsState()

        LaunchedEffect(Unit) {
            while (true) {
                delay(1000L)
                exoPlayer?.let { p ->
                    currentPositionMs = p.currentPosition.coerceAtLeast(0L)
                    durationMs = p.duration.coerceAtLeast(0L)
                    isPlaying = p.isPlaying
                }
                if (controlsVisible && isPlaying && !isResolving && errorMessage == null) {
                    delay(4000L)
                    controlsVisible = false
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
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    controlsVisible = !controlsVisible
                }
        ) {
            val playerInstance = exoPlayer
            if (playerInstance != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                            this.player = playerInstance
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // OSD Overlay
            AnimatedVisibility(
                visible = controlsVisible || isResolving || errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(16.dp)
                ) {
                    // Top Bar: Back-Button, Titel, Cast-Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { finish() }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Zurück", tint = TextPrimary)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = animeTitle,
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                currentEpisode?.let { ep ->
                                    val sText = if (ep.seasonLabel.equals("Filme", true)) "Film" else "S${ep.seasonLabel} E${ep.number}"
                                    Text(
                                        text = "$sText • ${ep.displayTitle}",
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Cast-Button oben rechts
                        IconButton(onClick = {
                            if (connectedDevice != null) {
                                // Direkt an TV übergeben!
                                currentEpisode?.let { ep ->
                                    lifecycleScope.launch {
                                        val ok = MobileCastManager.playOnTv(
                                            animeSlug = animeSlug,
                                            animeTitle = animeTitle,
                                            coverUrl = coverUrl,
                                            episodePath = ep.path,
                                            episodeNumber = ep.number,
                                            seasonLabel = ep.seasonLabel,
                                            titleDe = ep.displayTitle,
                                            preferredLangKey = selectedLangKey
                                        )
                                        if (ok) {
                                            exoPlayer?.pause()
                                            finish()
                                        }
                                    }
                                }
                            } else {
                                showCastSheet = true
                            }
                        }) {
                            Icon(
                                imageVector = if (connectedDevice != null) Icons.Default.CastConnected else Icons.Default.Cast,
                                contentDescription = "Auf TV abspielen",
                                tint = if (connectedDevice != null) AccentCyan else TextPrimary
                            )
                        }
                    }

                    // Mitte: Loading / Error / Play-Pause
                    if (isResolving) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = AccentCyan)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = statusMessage, color = TextPrimary, fontSize = 13.sp)
                        }
                    } else if (errorMessage != null) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = errorMessage.orEmpty(), color = Color(0xFFFF5252), fontSize = 14.sp)
                        }
                    } else {
                        // Play/Pause & 10s Seek in der Mitte
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(36.dp)
                        ) {
                            IconButton(onClick = { seekBy(-10_000L) }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = TextPrimary, modifier = Modifier.size(32.dp))
                            }

                            IconButton(
                                onClick = { exoPlayer?.let { if (it.isPlaying) it.pause() else it.play() } },
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(AccentCyan)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color(0xFF050811),
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            IconButton(onClick = { seekBy(10_000L) }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = TextPrimary, modifier = Modifier.size(32.dp))
                            }
                        }
                    }

                    // Unten: Zeitleiste
                    if (!isResolving && errorMessage == null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = formatDuration(currentPositionMs), color = TextPrimary, fontSize = 11.sp)
                                Text(text = formatDuration(durationMs), color = TextMuted, fontSize = 11.sp)
                            }

                            val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                            Slider(
                                value = progress,
                                onValueChange = { frac ->
                                    if (durationMs > 0) {
                                        exoPlayer?.seekTo((frac * durationMs).toLong())
                                    }
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = AccentCyan,
                                    activeTrackColor = AccentCyan,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                }
            }

            if (showCastSheet) {
                CastBottomSheet(onDismiss = { showCastSheet = false })
            }
        }
    }

    private fun loadEpisodeAndPlay(episode: EpisodeItem) {
        resolveJob?.cancel()
        currentPositionMs = 0L
        durationMs = 0L
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()

        currentEpisode = episode
        failedHosterPaths.clear()
        isResolving = true
        errorMessage = null
        statusMessage = "Lade Stream..."

        resolveJob = lifecycleScope.launch {
            val hosters = runCatching {
                if (episode.path.startsWith("hdfilme_")) {
                    HdFilmeScraper.fetchHostersForEpisode(episode.path)
                } else {
                    AniWorldScraper.fetchEpisodeHosters(episode.path)
                }
            }.getOrDefault(emptyList())

            if (hosters.isEmpty()) {
                isResolving = false
                errorMessage = "Keine Streams gefunden."
                return@launch
            }

            availableHosters = hosters
            val ordered = hosters.sortedWith(compareBy<HosterLink> { if (it.langKey == selectedLangKey) 0 else 1 })

            for (candidate in ordered) {
                statusMessage = "Verbinde mit ${candidate.hosterName}..."
                val resolved = runCatching { StreamResolver.resolveHoster(candidate) }.getOrNull()
                if (resolved != null) {
                    activeHoster = candidate
                    isResolving = false
                    startExoPlayer(resolved)
                    return@launch
                }
            }

            isResolving = false
            errorMessage = "Keiner der Hoster konnte geladen werden."
        }
    }

    private fun startExoPlayer(stream: ResolvedStream) {
        releasePlayer()
        val resumePos = LocalStorage.getSavedPositionForEpisode(currentEpisode?.path.orEmpty())

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
                    override fun onPlayerError(error: PlaybackException) {
                        val current = activeHoster
                        if (current != null) {
                            failedHosterPaths.add(current.redirectPath)
                            StreamResolver.invalidateCache(current.redirectPath)
                        }
                        val next = availableHosters.firstOrNull { it.redirectPath !in failedHosterPaths }
                        if (next != null) {
                            activeHoster = next
                            lifecycleScope.launch {
                                val res = runCatching { StreamResolver.resolveHoster(next) }.getOrNull()
                                if (res != null) startExoPlayer(res)
                            }
                        } else {
                            errorMessage = "Wiedergabefehler (${error.errorCodeName})"
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
    }

    private fun seekBy(deltaMs: Long) {
        val p = exoPlayer ?: return
        val dur = p.duration
        val target = (p.currentPosition + deltaMs).coerceAtLeast(0L)
        p.seekTo(if (dur != C.TIME_UNSET && dur > 0) target.coerceAtMost(dur) else target)
    }

    private fun persistCurrentProgress() {
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
        val m = totalSec / 60
        val s = totalSec % 60
        return "%02d:%02d".format(m, s)
    }

    companion object {
        private const val EXTRA_ANIME_SLUG = "extra_anime_slug"
        private const val EXTRA_ANIME_TITLE = "extra_anime_title"
        private const val EXTRA_COVER_URL = "extra_cover_url"
        private const val EXTRA_EPISODE = "extra_episode"
        private const val EXTRA_PREFERRED_LANG_KEY = "extra_preferred_lang_key"

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
