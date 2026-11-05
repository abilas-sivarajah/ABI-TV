package de.aniworld.pc.ui.player

import de.aniworld.pc.ui.compat.ScrollLazyRow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import de.aniworld.tv.data.model.EpisodeItem
import de.aniworld.tv.data.model.LanguageOption
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import de.aniworld.tv.data.model.SeasonLink
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import de.aniworld.pc.cast.PcCastManager
import de.aniworld.pc.ui.compat.BackHandler
import de.aniworld.pc.ui.compat.LocalComposeWindow
import de.aniworld.pc.ui.compat.LocalWindowState
import de.aniworld.pc.ui.compat.WindowKeyHandler
import de.aniworld.pc.ui.components.LanguageBadge
import de.aniworld.pc.ui.components.TvFocusableButton
import de.aniworld.pc.ui.theme.AccentCyan
import de.aniworld.pc.ui.theme.BgElevated
import de.aniworld.pc.ui.theme.BgSurface
import de.aniworld.pc.ui.theme.FocusBorder
import de.aniworld.pc.ui.theme.TextMuted
import de.aniworld.pc.ui.theme.TextPrimary
import de.aniworld.pc.ui.theme.TextSecondary
import de.aniworld.tv.cast.CastPairing
import kotlinx.coroutines.delay
import java.awt.Cursor
import java.awt.Point
import java.awt.Toolkit
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.image.BufferedImage
import javax.swing.Timer

/**
 * PC-Player: Videobild von VLC (eingebettetes AWT-Canvas), darüber und darunter die Leisten
 * des TV-Players. Im Vollbild blenden sich die Leisten nach ein paar Sekunden aus.
 */
@Composable
fun PlayerScreen(request: PlayRequest, onClose: () -> Unit, startFullscreen: Boolean = false) {
    val scope = rememberCoroutineScope()
    val controller = remember { PlayerController(scope) }
    val windowState = LocalWindowState.current
    val composeWindow = LocalComposeWindow.current
    val isFullscreen = windowState.placement == WindowPlacement.Fullscreen
    var placementBeforeFullscreen by remember { mutableStateOf(WindowPlacement.Maximized) }

    var controlsVisible by remember { mutableStateOf(true) }
    var sidePanel by remember { mutableStateOf<PlayerPanel?>(null) }
    // Zuletzt gemessene Höhe der Leisten (bleibt beim Ausblenden als schwarzer Platz stehen)
    var topBarHeightPx by remember { mutableStateOf(0) }
    var bottomBarHeightPx by remember { mutableStateOf(0) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val pairingNotice by CastPairing.notice.collectAsState()

    fun keepControlsAlive() {
        controlsVisible = true
        lastInteraction = System.currentTimeMillis()
    }

    /**
     * Compose Desktop beendet das Vollbild nur beim Wechsel auf "Floating": "Maximized" setzt
     * lediglich das Maximiert-Flag – das ist bei unserem Fenster aber schon gesetzt, also blieb
     * das Vollbild bestehen. Darum am Fenster selbst erst auf Floating, dann aufs Ziel wechseln.
     */
    fun exitFullscreen() {
        val target = placementBeforeFullscreen.takeIf { it != WindowPlacement.Fullscreen } ?: WindowPlacement.Maximized
        val window = composeWindow
        if (window == null) {
            windowState.placement = WindowPlacement.Floating
            return
        }
        // App wird gerade geschlossen: Fenster ist schon weg, dann nichts mehr umschalten
        if (!window.isDisplayable) return
        window.placement = WindowPlacement.Floating
        if (target == WindowPlacement.Maximized) window.placement = WindowPlacement.Maximized
        windowState.placement = target
    }

    fun toggleFullscreen() {
        if (windowState.placement == WindowPlacement.Fullscreen) {
            exitFullscreen()
        } else {
            placementBeforeFullscreen = windowState.placement
            windowState.placement = WindowPlacement.Fullscreen
        }
        keepControlsAlive()
    }

    // Direkt aus einem Banner gestartet: gleich ins Vollbild
    LaunchedEffect(Unit) {
        if (startFullscreen && windowState.placement != WindowPlacement.Fullscreen) toggleFullscreen()
    }

    // Neuer Auftrag (auch vom Handy, während der Player schon läuft)
    LaunchedEffect(request) {
        controller.handle(request)
        keepControlsAlive()
    }

    DisposableEffect(Unit) {
        // Befehle der Handy-Fernbedienung
        PcCastManager.activePlayerControl = { action, arg ->
            when (action.lowercase()) {
                "play" -> controller.play()
                "pause" -> controller.pause()
                "toggle" -> controller.togglePause()
                "seek" -> if (arg >= 0) controller.seekTo(arg)
                "seek_by" -> controller.seekBy(arg)
            }
            keepControlsAlive()
        }
        onDispose {
            PcCastManager.activePlayerControl = null
            controller.release()
            if (windowState.placement == WindowPlacement.Fullscreen) {
                exitFullscreen()
            }
        }
    }

    // Jede Sekunde: Status ans Handy, Fortschritt sichern, Leisten im Vollbild ausblenden
    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            delay(1000L)
            tick++
            controller.onTick(tick)
            val idle = System.currentTimeMillis() - lastInteraction > 3_500L
            if (controlsVisible && idle && controller.isPlaying && !controller.isResolving && controller.errorMessage == null) {
                controlsVisible = false
            }
        }
    }

    BackHandler {
        when {
            sidePanel != null -> sidePanel = null
            windowState.placement == WindowPlacement.Fullscreen -> toggleFullscreen()
            else -> onClose()
        }
    }

    WindowKeyHandler { event ->
        if (event.type != KeyEventType.KeyDown) return@WindowKeyHandler false
        val handled = when (event.key) {
            Key.Spacebar, Key.K, Key.MediaPlayPause -> { controller.togglePause(); true }
            Key.DirectionLeft, Key.J -> { controller.seekBy(-10_000L); true }
            Key.DirectionRight, Key.L -> { controller.seekBy(10_000L); true }
            Key.DirectionUp -> { controller.changeVolume(5); true }
            Key.DirectionDown -> { controller.changeVolume(-5); true }
            Key.F, Key.F11 -> { toggleFullscreen(); true }
            Key.I -> { controller.seekBy(85_000L); true }
            Key.N -> { controller.playNextEpisode(); true }
            else -> false
        }
        if (handled) keepControlsAlive()
        handled
    }

    // Maus auf dem Videobild: Klick = Pause, Doppelklick = Vollbild, Rad = Lautstärke, Bewegung = Leisten zeigen
    DisposableEffect(controller.videoCanvas) {
        val canvas = controller.videoCanvas
        val singleClick = Timer(250) { controller.togglePause(); keepControlsAlive() }.apply { isRepeats = false }
        val mouse = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button != MouseEvent.BUTTON1) return
                if (e.clickCount >= 2) {
                    singleClick.stop()
                    toggleFullscreen()
                } else {
                    singleClick.restart()
                }
            }

            override fun mouseMoved(e: MouseEvent) = keepControlsAlive()

            override fun mouseWheelMoved(e: MouseWheelEvent) {
                controller.changeVolume(if (e.wheelRotation < 0) 5 else -5)
                keepControlsAlive()
            }
        }
        canvas.addMouseListener(mouse)
        canvas.addMouseMotionListener(mouse)
        canvas.addMouseWheelListener(mouse)
        onDispose {
            singleClick.stop()
            canvas.removeMouseListener(mouse)
            canvas.removeMouseMotionListener(mouse)
            canvas.removeMouseWheelListener(mouse)
        }
    }

    val showBars = !isFullscreen || controlsVisible || controller.isResolving || controller.isStarting ||
        controller.errorMessage != null || sidePanel != null
    // Mauszeiger im Vollbild mit den Leisten ausblenden
    LaunchedEffect(showBars) {
        controller.videoCanvas.cursor = if (showBars) Cursor.getDefaultCursor() else BLANK_CURSOR
    }

    // Compose kann nicht über das VLC-Bild zeichnen: solange geladen wird, ein Fehler oder der
    // Kopplungscode angezeigt wird, schrumpft das Bild und die Meldung erscheint an seiner Stelle.
    // Aus demselben Grund liegen die Leisten über/unter dem Bild und Folgen/Sprache rechts daneben.
    val isLoading = controller.isResolving || controller.isStarting
    val videoReady = !isLoading && controller.errorMessage == null && pairingNotice == null

    // Pausen-Ansicht wie bei Netflix: nach kurzer Pause ein Standbild mit Titel und Beschreibung.
    // (Über das laufende VLC-Bild kann Compose nicht zeichnen – darum wird es durch das Standbild ersetzt.)
    // Ablauf ohne Flackern: Standbild erst hinter dem (noch sichtbaren) Video zeichnen, dann das Video
    // ausblenden – nicht verkleinern, das ließe kurz Schwarz durchblitzen. Beim Weiterspielen umgekehrt.
    var pauseFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    var videoHidden by remember { mutableStateOf(false) }
    LaunchedEffect(controller.isPlaying, videoReady, controller.currentEpisode?.path) {
        val paused = !controller.isPlaying && videoReady && (controller.durationMs > 0L || controller.isLiveStream)
        if (!paused) {
            if (videoHidden) {
                controller.videoCanvas.isVisible = true
                videoHidden = false
                // Standbild noch kurz liegen lassen, bis VLC wieder Bilder liefert
                delay(250)
            }
            pauseFrame = null
            return@LaunchedEffect
        }
        delay(PAUSE_INFO_DELAY_MS)
        val frame = withContext(Dispatchers.IO) { controller.snapshot() }?.toComposeImageBitmap() ?: return@LaunchedEffect
        pauseFrame = frame
        // Zwei Bilder abwarten, damit das Standbild sicher gezeichnet ist
        withFrameNanos { }
        withFrameNanos { }
        controller.videoCanvas.isVisible = false
        videoHidden = true
    }
    DisposableEffect(Unit) {
        onDispose { controller.videoCanvas.isVisible = true }
    }
    val showVideo = videoReady && !videoHidden

    // Lage des Videobilds auf dem Bildschirm – für die Knöpfe über dem Bild (eigenes kleines Fenster)
    val density = LocalDensity.current.density
    val videoCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    var videoBounds by remember { mutableStateOf<Rectangle?>(null) }
    fun updateVideoBounds() {
        val coords = videoCoords[0]?.takeIf { it.isAttached } ?: return
        val origin = runCatching { composeWindow?.contentPane?.locationOnScreen }.getOrNull() ?: return
        val pos = coords.positionInWindow()
        val bounds = Rectangle(
            origin.x + (pos.x / density).toInt(),
            origin.y + (pos.y / density).toInt(),
            (coords.size.width / density).toInt(),
            (coords.size.height / density).toInt()
        )
        // Nur bei echter Änderung setzen (Rectangle vergleicht nach Werten)
        if (bounds != videoBounds) videoBounds = bounds
    }
    DisposableEffect(composeWindow) {
        val window = composeWindow
        val listener = object : ComponentAdapter() {
            override fun componentMoved(e: ComponentEvent) = updateVideoBounds()
            override fun componentResized(e: ComponentEvent) = updateVideoBounds()
        }
        window?.addComponentListener(listener)
        onDispose { window?.removeComponentListener(listener) }
    }

    // Serien: "Intro überspringen" am Anfang, "Nächste Folge"/"Nächste Staffel" im Abspann
    val isSeries = !controller.isLiveStream && (controller.seasonEpisodes.size > 1 || controller.seasons.size > 1)
    val position = controller.currentPositionMs
    val duration = controller.durationMs
    val episodePath = controller.currentEpisode?.path
    var introSkippedFor by remember { mutableStateOf<String?>(null) }
    val nextEpisode = controller.findNextEpisode()
    val nextSeason = if (nextEpisode == null) controller.findNextSeason() else null
    val longEnough = duration > 10 * 60_000L
    val showIntroSkip = isSeries && showVideo && longEnough &&
        position in 5_000L..INTRO_WINDOW_MS && introSkippedFor != episodePath
    val showNextUp = isSeries && showVideo && longEnough &&
        duration - position in 0L..CREDITS_WINDOW_MS && (nextEpisode != null || nextSeason != null)
    VideoOverlayButton(
        visible = showIntroSkip || showNextUp,
        label = when {
            showNextUp && nextEpisode != null -> "Nächste Folge"
            showNextUp -> "Nächste Staffel"
            else -> "Intro überspringen"
        },
        icon = if (showNextUp) Icons.Filled.SkipNext else Icons.Filled.FastForward,
        videoBounds = videoBounds,
        onClick = {
            if (showNextUp) {
                if (nextEpisode != null) controller.playNextEpisode() else controller.playNextSeason()
            } else {
                controller.seekBy(INTRO_SKIP_MS)
                introSkippedFor = episodePath
            }
            keepControlsAlive()
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Leisten ausblenden, ihren Platz aber schwarz freihalten: Ändert das Videobild seine Größe,
        // füllt Windows die neue Fläche kurz weiß, bis VLC dort zeichnet – das wirkte wie Flackern.
        if (showBars) {
            Box(modifier = Modifier.onSizeChanged { topBarHeightPx = it.height }) {
                PlayerTopBar(onClose = onClose)
            }
        } else {
            Spacer(modifier = Modifier.fillMaxWidth().height(with(LocalDensity.current) { topBarHeightPx.toDp() }))
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onGloballyPositioned { videoCoords[0] = it; updateVideoBounds() },
                contentAlignment = Alignment.Center
            ) {
                // Standbild liegt hinter dem Video und wird sichtbar, sobald das Video ausgeblendet ist
                pauseFrame?.let { frame ->
                    PauseInfo(
                        frame = frame,
                        revealed = videoHidden,
                        controller = controller,
                        onResume = { controller.togglePause(); keepControlsAlive() }
                    )
                }

                SwingPanel(
                    background = Color.Black,
                    factory = { controller.videoCanvas },
                    // Erst wegnehmen, wenn das Standbild dahinter schon gezeichnet ist (kein Schwarzblitz).
                    // Nicht nur verkleinern: ein 1-Pixel-Fenster bliebe als heller Punkt in der Mitte stehen –
                    // darum aus dem sichtbaren Bereich hinausschieben.
                    modifier = if (showVideo) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.size(1.dp).offset(x = (-4000).dp, y = (-4000).dp)
                    }
                )

                if (isLoading && controller.errorMessage == null) {
                    StatusCard {
                        CircularProgressIndicator(color = AccentCyan)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = controller.statusMessage,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                controller.errorMessage?.let { message ->
                    StatusCard {
                        Text(text = "Wiedergabefehler", color = AccentCyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = message, color = TextSecondary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TvFocusableButton(text = "Erneut versuchen", isPrimary = true, onClick = { controller.retry() })
                            TvFocusableButton(text = "Zurück", onClick = onClose)
                        }
                    }
                }
            }

            when (sidePanel) {
                PlayerPanel.EPISODES -> EpisodesPanel(
                    controller = controller,
                    onPick = { episode, episodes ->
                        sidePanel = null
                        controller.playEpisodeOf(episode, episodes)
                    },
                    onClose = { sidePanel = null }
                )
                PlayerPanel.LANGUAGE -> LanguagePanel(
                    controller = controller,
                    onPick = { language ->
                        sidePanel = null
                        controller.selectLanguage(language)
                    },
                    onClose = { sidePanel = null }
                )
                null -> Unit
            }
        }

        if (showBars) {
            Box(modifier = Modifier.onSizeChanged { bottomBarHeightPx = it.height }) {
                PlayerBottomBar(
                    controller = controller,
                    isFullscreen = isFullscreen,
                    activePanel = sidePanel,
                    onTogglePanel = { panel -> sidePanel = if (sidePanel == panel) null else panel },
                    onToggleFullscreen = { toggleFullscreen() },
                    onInteraction = { keepControlsAlive() }
                )
            }
        } else {
            Spacer(modifier = Modifier.fillMaxWidth().height(with(LocalDensity.current) { bottomBarHeightPx.toDp() }))
        }
    }
}

/** Seitenleiste rechts neben dem Bild. */
private enum class PlayerPanel { EPISODES, LANGUAGE }

/** Pausen-Ansicht erst nach kurzer Pause zeigen (kurzes Anhalten soll das Bild nicht verdecken). */
private const val PAUSE_INFO_DELAY_MS = 2_500L

/** "Intro überspringen" wird in den ersten Minuten angeboten und springt so weit vor wie bisher die Taste I. */
private const val INTRO_WINDOW_MS = 5 * 60_000L
private const val INTRO_SKIP_MS = 85_000L
/** "Nächste Folge" erscheint in den letzten 100 Sekunden (Abspann). */
private const val CREDITS_WINDOW_MS = 100_000L

/**
 * Pausen-Ansicht: abgedunkeltes Standbild, links "Sie sehen gerade", Titel, Staffel,
 * Folge und Beschreibung, rechts unten "Angehalten". Klick spielt weiter.
 */
@Composable
private fun PauseInfo(frame: ImageBitmap, revealed: Boolean, controller: PlayerController, onResume: () -> Unit) {
    // Abdunkeln und Texte sanft einblenden (das Standbild selbst gleicht dem letzten Videobild)
    val reveal by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "pauseReveal"
    )
    val episode = controller.currentEpisode
    val isLive = controller.isLiveStream
    val isSeries = !isLive && (controller.seasonEpisodes.size > 1 || controller.seasons.size > 1)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onResume)
    ) {
        Image(
            bitmap = frame,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        // Abdunkeln, links etwas stärker für die Schrift
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(reveal)
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.80f),
                        0.6f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Black.copy(alpha = 0.45f)
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .alpha(reveal)
                .padding(start = 96.dp, end = 48.dp)
                .widthIn(max = 900.dp)
        ) {
            Text(text = "Sie sehen gerade", color = TextSecondary, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = controller.animeTitle,
                color = Color.White,
                fontSize = 52.sp,
                lineHeight = 56.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (isSeries && episode != null) {
                val season = episode.seasonLabel.takeIf { it.isNotBlank() }
                if (season != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (season.equals("Filme", true)) "Filme & Specials" else "Staffel $season",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(22.dp))
                Text(
                    text = episode.displayTitle.takeIf { it.isNotBlank() && !it.startsWith("Folge ") }
                        ?.let { "$it: Flg. ${episode.number}" } ?: "Folge ${episode.number}",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (isLive) {
                episode?.displayTitle?.takeIf { it.isNotBlank() && it != controller.animeTitle }?.let {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = it, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
            controller.titleDescription.takeIf { it.isNotBlank() && !isLive }?.let { description ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = description,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Text(
            text = "Angehalten",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 18.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .alpha(reveal)
                .padding(end = 72.dp, bottom = 48.dp)
        )
    }
}

@Composable
private fun StatusCard(content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(BgSurface.copy(alpha = 0.95f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(horizontal = 28.dp, vertical = 22.dp)
    ) {
        content()
    }
}

/** Oben nur der Pfeil zurück (wie bei Streaming-Apps). */
@Composable
private fun PlayerTopBar(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, description = "Zurück", onClick = onClose)
    }
}

/**
 * Unten: Fortschritt über die ganze Breite mit Restzeit, darunter links Play/Spulen/Lautstärke,
 * in der Mitte der Titel, rechts nächste Folge, Folgen, Sprache und Vollbild.
 */
@Composable
private fun PlayerBottomBar(
    controller: PlayerController,
    isFullscreen: Boolean,
    activePanel: PlayerPanel?,
    onTogglePanel: (PlayerPanel) -> Unit,
    onToggleFullscreen: () -> Unit,
    onInteraction: () -> Unit
) {
    val isLive = controller.isLiveStream
    val hasEpisodes = !isLive && (controller.seasonEpisodes.size > 1 || controller.seasons.size > 1)
    val nextEpisode = if (isLive) null else controller.findNextEpisode()
    val nextSeason = if (isLive || nextEpisode != null) null else controller.findNextSeason()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
    ) {
        // ---------- Fortschritt ----------
        if (!isLive) {
            var dragFraction by remember { mutableStateOf<Float?>(null) }
            val duration = controller.durationMs
            val fraction = if (duration > 0) (controller.currentPositionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
            val shownFraction = dragFraction ?: fraction
            val shownPosition = if (dragFraction != null) (shownFraction * duration).toLong() else controller.currentPositionMs
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = shownFraction,
                    onValueChange = {
                        dragFraction = it
                        onInteraction()
                    },
                    onValueChangeFinished = {
                        dragFraction?.let { controller.seekTo((it * duration).toLong()) }
                        dragFraction = null
                    },
                    enabled = duration > 0,
                    modifier = Modifier.weight(1f).height(28.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = AccentCyan,
                        activeTrackColor = AccentCyan,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    )
                )
                Spacer(modifier = Modifier.width(14.dp))
                // Restzeit wie bei Streaming-Apps
                Text(
                    text = if (duration > 0) formatDuration(duration - shownPosition) else "--:--",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFEF4444)))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "LIVE", color = Color(0xFFEF4444), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ---------- Bedienung ----------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Links: Play, Spulen, Lautstärke
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PlayerIconButton(
                    icon = if (controller.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    description = if (controller.isPlaying) "Pause" else "Abspielen",
                    large = true,
                    onClick = { controller.togglePause(); onInteraction() }
                )
                if (!isLive) {
                    PlayerIconButton(icon = Icons.Filled.Replay10, description = "10 Sekunden zurück", onClick = { controller.seekBy(-10_000L); onInteraction() })
                    PlayerIconButton(icon = Icons.Filled.Forward10, description = "10 Sekunden vor", onClick = { controller.seekBy(10_000L); onInteraction() })
                } else {
                    PlayerIconButton(icon = Icons.Filled.Refresh, description = "Neu verbinden", onClick = { controller.reconnectLive(); onInteraction() })
                }
                PlayerIconButton(
                    icon = when {
                        controller.volume == 0 -> Icons.AutoMirrored.Filled.VolumeOff
                        controller.volume < 50 -> Icons.AutoMirrored.Filled.VolumeDown
                        else -> Icons.AutoMirrored.Filled.VolumeUp
                    },
                    description = if (controller.volume == 0) "Ton an" else "Ton aus",
                    onClick = { controller.toggleMute(); onInteraction() }
                )
                Slider(
                    value = controller.volume / 150f,
                    onValueChange = { controller.setVolumeLevel((it * 150).toInt()); onInteraction() },
                    modifier = Modifier.width(110.dp).height(28.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    )
                )
            }

            // Mitte: Titel – Serie fett, dann "Flg. 22  Episodentitel"
            val episode = controller.currentEpisode
            val title = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(controller.animeTitle) }
                if (episode != null && !isLive) {
                    val isMovie = episode.seasonLabel.equals("Film", true) || controller.seasonEpisodes.size <= 1
                    if (!isMovie) {
                        val season = episode.seasonLabel.takeIf { it.isNotBlank() && !it.equals("Filme", true) }
                        append("   ")
                        append(if (season != null) "St. $season · Flg. ${episode.number}" else "Flg. ${episode.number}")
                        episode.displayTitle.takeIf { it.isNotBlank() && it != controller.animeTitle }?.let { append("   $it") }
                    }
                } else if (isLive) {
                    episode?.displayTitle?.takeIf { it.isNotBlank() && it != controller.animeTitle }?.let { append("   $it") }
                }
            }
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1.4f)
            )

            // Rechts: nächste Folge, Folgen, Sprache, Vollbild
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)
            ) {
                if (nextEpisode != null) {
                    PlayerIconButton(
                        icon = Icons.Filled.SkipNext,
                        description = "Nächste Folge (${nextEpisode.number})",
                        onClick = { controller.playNextEpisode(); onInteraction() }
                    )
                } else if (nextSeason != null) {
                    PlayerIconButton(
                        icon = Icons.Filled.SkipNext,
                        description = "Nächste Staffel (${nextSeason.label})",
                        onClick = { controller.playNextSeason(); onInteraction() }
                    )
                }
                if (hasEpisodes) {
                    PlayerIconButton(
                        icon = Icons.Filled.VideoLibrary,
                        description = "Folgen",
                        selected = activePanel == PlayerPanel.EPISODES,
                        onClick = { onTogglePanel(PlayerPanel.EPISODES); onInteraction() }
                    )
                }
                if (!isLive && controller.availableLanguages.isNotEmpty()) {
                    PlayerIconButton(
                        icon = Icons.Filled.Subtitles,
                        description = "Sprache",
                        selected = activePanel == PlayerPanel.LANGUAGE,
                        onClick = { onTogglePanel(PlayerPanel.LANGUAGE); onInteraction() }
                    )
                }
                PlayerIconButton(
                    icon = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    description = if (isFullscreen) "Vollbild beenden" else "Vollbild",
                    onClick = onToggleFullscreen
                )
            }
        }
    }
}

/** Schlichtes Symbol ohne Rahmen; beim Überfahren/Fokus hell hinterlegt, mit Tooltip-Text. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    large: Boolean = false,
    selected: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    TooltipArea(
        tooltip = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(BgElevated)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(text = description, color = TextPrimary, fontSize = 12.sp)
            }
        },
        delayMillis = 500,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, (-36).dp))
    ) {
        Box(
            modifier = Modifier
                .size(if (large) 52.dp else 46.dp)
                .clip(CircleShape)
                .background(
                    when {
                        selected -> Color.White.copy(alpha = 0.18f)
                        hovered || focused -> Color.White.copy(alpha = 0.10f)
                        else -> Color.Transparent
                    }
                )
                .border(2.dp, if (focused) FocusBorder else Color.Transparent, CircleShape)
                .hoverable(interaction)
                .pointerHoverIcon(PointerIcon.Hand)
                .onFocusChanged { focused = it.isFocused }
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = Color.White,
                modifier = Modifier.size(if (large) 38.dp else 30.dp)
            )
        }
    }
}

/** Gemeinsamer Rahmen der Seitenleisten (Folgen, Sprache). */
@Composable
private fun SidePanel(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .width(380.dp)
            .fillMaxHeight()
            .background(BgSurface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            PlayerIconButton(icon = Icons.Filled.Close, description = "Schließen", onClick = onClose)
        }
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

/**
 * Folgen-Leiste wie bei Netflix: oben "‹ Staffel N", darunter die Folgen. Ein Klick auf "‹"
 * zeigt alle Staffeln (und "Filme & Specials") untereinander; die gewählte trägt ein Häkchen.
 */
@Composable
private fun EpisodesPanel(
    controller: PlayerController,
    onPick: (EpisodeItem, List<EpisodeItem>) -> Unit,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val currentPath = controller.currentEpisode?.path
    val seasons = controller.seasons
    var viewSeason by remember { mutableStateOf(controller.currentSeason) }
    var viewEpisodes by remember { mutableStateOf(controller.seasonEpisodes) }
    var choosingSeason by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val index = viewEpisodes.indexOfFirst { it.path == currentPath }
        if (index > 2) listState.scrollToItem(index - 2)
    }

    fun seasonLabel(season: SeasonLink?): String = when {
        season == null -> "Folgen"
        season.isMovies -> "Filme & Specials"
        else -> "Staffel ${season.label}"
    }

    fun showSeason(season: SeasonLink) {
        choosingSeason = false
        viewSeason = season
        if (season == controller.currentSeason) {
            viewEpisodes = controller.seasonEpisodes
            scope.launch {
                val index = viewEpisodes.indexOfFirst { it.path == currentPath }
                listState.scrollToItem((index - 2).coerceAtLeast(0))
            }
            return
        }
        scope.launch {
            loading = true
            viewEpisodes = runCatching { controller.fetchSeasonEpisodes(season) }.getOrDefault(emptyList())
            loading = false
            listState.scrollToItem(0)
        }
    }

    Column(
        modifier = Modifier
            .width(380.dp)
            .fillMaxHeight()
            .background(BgSurface)
            .padding(16.dp)
    ) {
        // ---------- Kopf ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (choosingSeason) {
                Text(
                    text = controller.animeTitle,
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                val canChoose = seasons.size > 1
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                            if (canChoose) {
                                Modifier
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable { choosingSeason = true }
                            } else Modifier
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canChoose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Staffel wählen",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = seasonLabel(viewSeason),
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            PlayerIconButton(icon = Icons.Filled.Close, description = "Schließen", onClick = onClose)
        }
        Spacer(modifier = Modifier.height(12.dp))

        // ---------- Inhalt ----------
        when {
            choosingSeason -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(seasons, key = { it.path }) { season ->
                    val selected = season == viewSeason
                    PanelRow(selected = selected, onClick = { showSeason(season) }) {
                        Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.CenterStart) {
                            if (selected) {
                                Icon(imageVector = Icons.Filled.Check, contentDescription = "Gewählt", tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                        Text(
                            text = seasonLabel(season),
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (season == controller.currentSeason) {
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
                    PanelRow(selected = isCurrent, onClick = { if (!isCurrent) onPick(episode, viewEpisodes) }) {
                        Text(
                            text = "${episode.number}",
                            color = if (isCurrent) AccentCyan else TextSecondary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(40.dp)
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
private fun LanguagePanel(controller: PlayerController, onPick: (LanguageOption) -> Unit, onClose: () -> Unit) {
    val active = controller.activeLanguage
    SidePanel(title = "Sprache", onClose = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            controller.availableLanguages.forEach { language ->
                PanelRow(selected = language == active, onClick = { onPick(language) }) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(language.badgeColorHex)))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = language.shortLabel, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(text = language.fullLabel, color = TextSecondary, fontSize = 12.sp)
                    }
                    if (language == active) {
                        Icon(imageVector = Icons.Filled.Check, contentDescription = "Aktiv", tint = AccentCyan, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelRow(selected: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    selected -> Color.White.copy(alpha = 0.12f)
                    hovered || focused -> Color.White.copy(alpha = 0.06f)
                    else -> Color.Transparent
                }
            )
            .border(2.dp, if (focused) FocusBorder else Color.Transparent, shape)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private val BLANK_CURSOR: Cursor = Toolkit.getDefaultToolkit().createCustomCursor(
    BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), Point(0, 0), "blank"
)
