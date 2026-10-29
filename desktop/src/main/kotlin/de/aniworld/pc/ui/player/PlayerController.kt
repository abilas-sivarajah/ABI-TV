package de.aniworld.pc.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.aniworld.pc.cast.PcCastManager
import de.aniworld.pc.platform.VlcSupport
import de.aniworld.tv.cast.CastStatus
import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.model.EpisodeItem
import de.aniworld.tv.data.model.HosterLink
import de.aniworld.tv.data.model.LanguageOption
import de.aniworld.tv.data.model.ResolvedStream
import de.aniworld.tv.data.model.WatchProgressEntry
import de.aniworld.tv.data.remote.AniWorldScraper
import de.aniworld.tv.data.remote.FilmpalastScraper
import de.aniworld.tv.data.remote.HdFilmeScraper
import de.aniworld.tv.data.remote.HttpClient
import de.aniworld.tv.data.remote.LiveTvRepository
import de.aniworld.tv.data.remote.SerienStreamScraper
import de.aniworld.tv.data.remote.StreamResolver
import de.aniworld.tv.platform.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import java.awt.Canvas
import javax.swing.SwingUtilities

/** Was der Player abspielen soll – vom Detailbildschirm, aus „Weiterschauen“, Live TV oder vom Handy. */
sealed interface PlayRequest {
    data class Episode(
        val animeSlug: String,
        val animeTitle: String,
        val coverUrl: String,
        val episode: EpisodeItem,
        val seasonEpisodes: List<EpisodeItem>,
        val preferredLangKey: Int
    ) : PlayRequest

    data class Live(
        val channelName: String,
        val streamUrl: String,
        val logoUrl: String,
        val headers: Map<String, String>
    ) : PlayRequest
}

/**
 * Logik des PC-Players, übernommen aus der TV-PlayerActivity (Hoster-Auswahl, Fallback-Kette,
 * Fortschritt, Live-Wiederverbindung); statt ExoPlayer spielt VLC ab.
 */
class PlayerController(private val scope: CoroutineScope) {

    var animeSlug by mutableStateOf("")
        private set
    var animeTitle by mutableStateOf("")
        private set
    private var coverUrl = ""
    var seasonEpisodes by mutableStateOf<List<EpisodeItem>>(emptyList())
        private set

    var currentEpisode by mutableStateOf<EpisodeItem?>(null)
        private set
    var availableHosters by mutableStateOf<List<HosterLink>>(emptyList())
        private set
    var activeHoster by mutableStateOf<HosterLink?>(null)
        private set
    private var selectedLangKey = LanguageOption.GER_DUB.langKey
    private val failedHosterPaths = mutableSetOf<String>()

    var isLiveStream by mutableStateOf(false)
        private set
    private var directStreamUrl: String? = null
    private var streamHeaders: Map<String, String> = emptyMap()
    private var liveRetryCount = 0

    var isResolving by mutableStateOf(true)
        private set
    var statusMessage by mutableStateOf("Lade verfügbare Streams...")
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    var isBuffering by mutableStateOf(false)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var currentPositionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var volume by mutableIntStateOf(100)
        private set

    private var resolveJob: Job? = null
    private var startWatchdog: Job? = null
    private var pendingSeekMs = 0L

    /** Fläche, in die VLC das Bild zeichnet (wird per SwingPanel eingebettet). */
    val videoCanvas = Canvas().apply {
        background = java.awt.Color.BLACK
        // Tastatur bleibt bei Compose, damit Leertaste & Co. funktionieren
        isFocusable = false
    }

    private val events = object : MediaPlayerEventAdapter() {
        override fun playing(mediaPlayer: MediaPlayer) = onUi {
            isPlaying = true
            isBuffering = false
            applyPendingSeek()
        }

        override fun paused(mediaPlayer: MediaPlayer) = onUi { isPlaying = false }
        override fun stopped(mediaPlayer: MediaPlayer) = onUi { isPlaying = false }
        override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) = onUi { isBuffering = newCache < 100f }
        override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) = onUi { durationMs = newLength.coerceAtLeast(0L) }
        override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) = onUi { currentPositionMs = newTime.coerceAtLeast(0L) }
        override fun finished(mediaPlayer: MediaPlayer) = onUi { onFinished() }
        override fun error(mediaPlayer: MediaPlayer) = onUi { onPlaybackError("Stream lässt sich nicht öffnen") }
    }

    private val mediaPlayer: EmbeddedMediaPlayer? = VlcSupport.factory?.let { factory ->
        factory.mediaPlayers().newEmbeddedMediaPlayer().apply {
            videoSurface().set(factory.videoSurfaces().newVideoSurface(videoCanvas))
            // Maus & Tastatur nicht an VLC geben, sonst kommen Klicks nicht beim Canvas an
            input().enableMouseInputHandling(false)
            input().enableKeyInputHandling(false)
            events().addMediaPlayerEventListener(events)
        }
    }

    val vlcAvailable: Boolean get() = mediaPlayer != null

    // ------------------- Steuerung -------------------

    fun handle(request: PlayRequest) {
        if (mediaPlayer == null) {
            isResolving = false
            errorMessage = "VLC konnte nicht gestartet werden. Bitte ABI TV neu installieren."
            return
        }
        when (request) {
            is PlayRequest.Live -> {
                // Läuft gerade eine Folge (z. B. per Handy umgeschaltet), deren Fortschritt vorher sichern
                persistCurrentProgress()
                resolveJob?.cancel()
                isLiveStream = true
                directStreamUrl = request.streamUrl
                streamHeaders = request.headers
                activeHoster = null
                availableHosters = emptyList()
                seasonEpisodes = emptyList()
                currentPositionMs = 0L
                durationMs = 0L
                liveRetryCount = 0
                animeTitle = request.channelName
                coverUrl = request.logoUrl
                currentEpisode = EpisodeItem(
                    number = 1,
                    seasonLabel = LIVE_SEASON_LABEL,
                    path = "live_stream",
                    titleDe = request.channelName.ifBlank { "Live TV" },
                    titleEn = "",
                    languages = emptyList()
                )
                statusMessage = "Starte Live Stream..."
                playLive()
            }

            is PlayRequest.Episode -> {
                animeSlug = request.animeSlug
                animeTitle = request.animeTitle
                coverUrl = request.coverUrl
                selectedLangKey = request.preferredLangKey
                seasonEpisodes = request.seasonEpisodes
                loadEpisodeAndPlay(request.episode)
            }
        }
    }

    fun togglePause() {
        val mp = mediaPlayer ?: return
        if (isPlaying) mp.controls().setPause(true) else mp.controls().play()
    }

    fun play() = mediaPlayer?.controls()?.play()
    fun pause() = mediaPlayer?.controls()?.setPause(true)

    fun seekBy(deltaMs: Long) {
        // Live-Sender haben keine Zeitleiste: Spulen (auch vom Handy) ignorieren
        if (isLiveStream) return
        seekTo(currentPositionMs + deltaMs)
    }

    fun seekTo(positionMs: Long) {
        if (isLiveStream) return
        val mp = mediaPlayer ?: return
        val target = positionMs.coerceAtLeast(0L).let { if (durationMs > 0) it.coerceAtMost(durationMs) else it }
        mp.controls().setTime(target)
        currentPositionMs = target
    }

    fun changeVolume(delta: Int) {
        val mp = mediaPlayer ?: return
        volume = (volume + delta).coerceIn(0, 150)
        mp.audio().setVolume(volume)
    }

    fun findNextEpisode(): EpisodeItem? {
        val current = currentEpisode ?: return null
        val idx = seasonEpisodes.indexOfFirst { it.path == current.path }
        return if (idx >= 0 && idx + 1 < seasonEpisodes.size) seasonEpisodes[idx + 1] else null
    }

    fun playNextEpisode() {
        val next = findNextEpisode() ?: return
        persistCurrentProgress()
        loadEpisodeAndPlay(next)
    }

    fun retry() {
        if (isLiveStream) reconnectLive() else currentEpisode?.let { loadEpisodeAndPlay(it) }
    }

    /** Wird jede Sekunde vom Bildschirm aufgerufen (Handy-Status, Fortschritt sichern). */
    fun onTick(tick: Int) {
        val ep = currentEpisode ?: return
        PcCastManager.updatePlaybackStatus(
            CastStatus(
                isPlaying = isPlaying,
                title = animeTitle,
                episodeTitle = ep.displayTitle,
                seasonLabel = ep.seasonLabel,
                episodeNumber = ep.number,
                positionMs = currentPositionMs,
                durationMs = durationMs
            )
        )
        if (tick % 8 == 0) persistCurrentProgress()
    }

    fun release() {
        persistCurrentProgress()
        resolveJob?.cancel()
        startWatchdog?.cancel()
        PcCastManager.resetPlaybackStatus()
        mediaPlayer?.let { mp ->
            runCatching {
                mp.events().removeMediaPlayerEventListener(events)
                mp.controls().stop()
                mp.release()
            }
        }
    }

    // ------------------- Hoster & Wiedergabe -------------------

    fun loadEpisodeAndPlay(episode: EpisodeItem) {
        resolveJob?.cancel()
        startWatchdog?.cancel()
        persistCurrentProgress()
        isLiveStream = false
        directStreamUrl = null
        currentPositionMs = 0L
        durationMs = 0L
        mediaPlayer?.controls()?.stop()

        currentEpisode = episode
        failedHosterPaths.clear()
        isResolving = true
        errorMessage = null
        statusMessage = "Lade Hoster für Folge ${episode.number}..."

        resolveJob = scope.launch {
            val hosters = runCatching {
                withContext(Dispatchers.IO) {
                    when {
                        // Nur das eindeutige Präfix: AniWorld-Pfade (/anime/stream/...) enthalten ebenfalls "/stream/"
                        FilmpalastScraper.isFilmpalastEpisodePath(episode.path) -> FilmpalastScraper.fetchHostersForFilm(episode.path)
                        episode.path.startsWith("sto_") || episode.path.startsWith("/serie/") ->
                            SerienStreamScraper.fetchEpisodeHosters(episode.path)
                        episode.path.startsWith("hdfilme_") -> HdFilmeScraper.fetchHostersForEpisode(episode.path)
                        else -> AniWorldScraper.fetchEpisodeHosters(episode.path)
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
            val orderedCandidates = hosters.sortedWith(compareBy<HosterLink> { if (it.langKey == selectedLangKey) 0 else 1 })
            if (!playFirstWorkingHoster(episode, orderedCandidates)) {
                isResolving = false
                errorMessage = "Keiner der Hoster (${hosters.joinToString { it.hosterName }}) konnte aufgelöst werden."
            }
        }
    }

    /** Hoster per Hand gewählt (Leiste unten im Player). */
    fun selectHoster(hoster: HosterLink) {
        val ep = currentEpisode ?: return
        persistCurrentProgress()
        resolveJob?.cancel()
        selectedLangKey = hoster.langKey
        isResolving = true
        errorMessage = null
        statusMessage = "Wechsle zu ${hoster.hosterName} (${hoster.language.shortLabel})..."
        resolveJob = scope.launch {
            runCatching { StreamResolver.resolveHoster(hoster) }
                .onSuccess { resolved ->
                    activeHoster = hoster
                    isResolving = false
                    startPlayback(ep, resolved)
                }
                .onFailure { err ->
                    failedHosterPaths.add(hoster.redirectPath)
                    isResolving = false
                    errorMessage = "${hoster.hosterName} fehlgeschlagen: ${err.message}"
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
            statusMessage = "Verbinde mit ${candidate.hosterName} (${candidate.language.shortLabel})..."
            val resolved = runCatching { StreamResolver.resolveHoster(candidate) }.getOrNull()
            if (resolved != null) {
                activeHoster = candidate
                selectedLangKey = candidate.langKey
                isResolving = false
                startPlayback(episode, resolved)
                return true
            }
            failedHosterPaths.add(candidate.redirectPath)
        }
        return false
    }

    /** Automatischer Fallback nach einem Wiedergabefehler: alle übrigen Hoster nacheinander probieren. */
    private fun fallbackToRemainingHosters(reason: String) {
        val ep = currentEpisode ?: return
        activeHoster?.let { current ->
            failedHosterPaths.add(current.redirectPath)
            StreamResolver.invalidateCache(current.redirectPath)
        }
        val remaining = availableHosters
            .filter { it.redirectPath !in failedHosterPaths }
            .sortedWith(compareBy<HosterLink> { if (it.langKey == selectedLangKey) 0 else 1 })
        if (remaining.isEmpty()) {
            isResolving = false
            errorMessage = "Wiedergabefehler ($reason)"
            return
        }

        persistCurrentProgress()
        isResolving = true
        errorMessage = null
        resolveJob?.cancel()
        resolveJob = scope.launch {
            if (!playFirstWorkingHoster(ep, remaining)) {
                isResolving = false
                errorMessage = "Wiedergabefehler ($reason) – kein weiterer Hoster verfügbar."
            }
        }
    }

    private suspend fun startPlayback(episode: EpisodeItem, stream: ResolvedStream) {
        val mp = mediaPlayer ?: return
        AppLog.i(TAG, "Starte VLC url=${stream.streamUrl}, headers=${stream.headers}")
        pendingSeekMs = when {
            isLiveStream -> 0L
            currentPositionMs > 5_000L -> currentPositionMs
            else -> LocalStorage.getSavedPositionForEpisode(episode.path)
        }
        durationMs = 0L

        // Das Bild muss im Fenster hängen, sonst öffnet VLC ein eigenes Fenster
        withTimeoutOrNull(3_000L) { while (!videoCanvas.isDisplayable) delay(30L) }

        val options = buildList {
            add(":http-user-agent=${stream.headers["User-Agent"] ?: HttpClient.USER_AGENT}")
            stream.headers["Referer"]?.let { add(":http-referrer=$it") }
        }
        if (!mp.media().play(stream.streamUrl, *options.toTypedArray())) {
            onPlaybackError("Stream konnte nicht geladen werden")
            return
        }
        mp.audio().setVolume(volume)

        // Ist der Server hinter der Adresse nicht erreichbar, hängt VLC lange beim Öffnen:
        // nach einer Weile ohne Bild wie bei einem Fehler zum nächsten Hoster wechseln
        startWatchdog?.cancel()
        val startedAt = System.currentTimeMillis()
        startWatchdog = scope.launch {
            delay(START_TIMEOUT_MS)
            if (!isPlaying && currentPositionMs <= 0L && System.currentTimeMillis() - startedAt >= START_TIMEOUT_MS) {
                AppLog.w(TAG, "Kein Bild nach ${START_TIMEOUT_MS / 1000} s: ${stream.hosterName}")
                mp.controls().stop()
                onPlaybackError("${stream.hosterName} antwortet nicht")
            }
        }
    }

    private fun applyPendingSeek() {
        startWatchdog?.cancel()
        val target = pendingSeekMs
        if (target <= 0L) return
        pendingSeekMs = 0L
        // Nicht direkt aus dem VLC-Ereignis heraus in VLC zurückrufen (vlcj-Regel)
        mediaPlayer?.let { mp -> mp.submit { mp.controls().setTime(target) } }
    }

    private fun onFinished() {
        if (isLiveStream) {
            handleLiveError("Sender hat die Übertragung beendet")
            return
        }
        val ep = currentEpisode ?: return
        // VLC meldet auch kaputte Streams als "beendet": Ende ohne Laufzeit oder mitten im Film
        val endedEarly = durationMs <= 0L || currentPositionMs < durationMs - 60_000L
        if (endedEarly) {
            onPlaybackError("Stream abgebrochen")
            return
        }
        LocalStorage.markEpisodeWatched(ep.path)
        findNextEpisode()?.let { next ->
            currentPositionMs = 0L
            loadEpisodeAndPlay(next)
        }
    }

    private fun onPlaybackError(reason: String) {
        if (isLiveStream) handleLiveError(reason) else fallbackToRemainingHosters(reason)
    }

    // ------------------- Live TV -------------------

    /** Startet den Live-Sender (neu) am aktuellen Live-Punkt. */
    private fun playLive() {
        val url = directStreamUrl ?: return
        val ep = currentEpisode ?: return
        errorMessage = null
        isResolving = false
        resolveJob?.cancel()
        resolveJob = scope.launch {
            startPlayback(
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
    }

    fun reconnectLive() {
        resolveJob?.cancel()
        liveRetryCount = 0
        playLive()
    }

    private fun handleLiveError(reason: String) {
        // Kurze Aussetzer der Quelle selbst abfangen, statt sofort einen Fehler zu zeigen
        if (liveRetryCount < MAX_LIVE_RETRIES) {
            liveRetryCount++
            isResolving = true
            statusMessage = "Verbindung unterbrochen – verbinde neu ($liveRetryCount/$MAX_LIVE_RETRIES)..."
            resolveJob?.cancel()
            resolveJob = scope.launch {
                delay(2_000L * liveRetryCount)
                playLive()
            }
            return
        }
        isResolving = false
        errorMessage = "Der Sender ist gerade nicht erreichbar ($reason)."
    }

    // ------------------- Fortschritt -------------------

    fun persistCurrentProgress() {
        if (isLiveStream) return
        val ep = currentEpisode ?: return
        val pos = currentPositionMs
        val dur = durationMs
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

    /** VLC meldet Ereignisse auf eigenen Threads; Zustand nur im UI-Thread ändern. */
    private fun onUi(block: () -> Unit) = SwingUtilities.invokeLater(block)

    companion object {
        private const val TAG = "PcPlayer"

        /** Kennzeichnet Live-Sender im Status für die Handy-Fernbedienung (wie auf dem TV). */
        const val LIVE_SEASON_LABEL = "LIVE"
        private const val MAX_LIVE_RETRIES = 3
        private const val START_TIMEOUT_MS = 20_000L
    }
}
