package de.aniworld.pc.ui.player

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
import de.aniworld.pc.ui.compat.LocalWindowState
import de.aniworld.pc.ui.compat.WindowKeyHandler
import de.aniworld.pc.ui.components.LanguageBadge
import de.aniworld.pc.ui.components.TvFocusableButton
import de.aniworld.pc.ui.theme.AccentCyan
import de.aniworld.pc.ui.theme.BgElevated
import de.aniworld.pc.ui.theme.BgSurface
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
fun PlayerScreen(request: PlayRequest, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val controller = remember { PlayerController(scope) }
    val windowState = LocalWindowState.current
    val isFullscreen = windowState.placement == WindowPlacement.Fullscreen
    var placementBeforeFullscreen by remember { mutableStateOf(WindowPlacement.Maximized) }

    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val pairingNotice by CastPairing.notice.collectAsState()

    fun keepControlsAlive() {
        controlsVisible = true
        lastInteraction = System.currentTimeMillis()
    }

    fun toggleFullscreen() {
        if (windowState.placement == WindowPlacement.Fullscreen) {
            windowState.placement = placementBeforeFullscreen
        } else {
            placementBeforeFullscreen = windowState.placement
            windowState.placement = WindowPlacement.Fullscreen
        }
        keepControlsAlive()
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
                windowState.placement = placementBeforeFullscreen
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
        if (windowState.placement == WindowPlacement.Fullscreen) toggleFullscreen() else onClose()
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

    val showBars = !isFullscreen || controlsVisible || controller.isResolving || controller.errorMessage != null
    // Mauszeiger im Vollbild mit den Leisten ausblenden
    LaunchedEffect(showBars) {
        controller.videoCanvas.cursor = if (showBars) Cursor.getDefaultCursor() else BLANK_CURSOR
    }

    // Compose kann nicht über das VLC-Bild zeichnen: solange geladen wird, ein Fehler oder der
    // Kopplungscode angezeigt wird, schrumpft das Bild und die Meldung erscheint an seiner Stelle
    val showVideo = !controller.isResolving && controller.errorMessage == null && pairingNotice == null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (showBars) {
            PlayerTopBar(controller = controller, onClose = onClose)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            SwingPanel(
                background = Color.Black,
                factory = { controller.videoCanvas },
                modifier = if (showVideo) Modifier.fillMaxSize() else Modifier.size(1.dp)
            )

            if (controller.isResolving) {
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

        if (showBars) {
            PlayerBottomBar(
                controller = controller,
                isFullscreen = isFullscreen,
                onToggleFullscreen = { toggleFullscreen() },
                onInteraction = { keepControlsAlive() }
            )
        }
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

@Composable
private fun PlayerTopBar(controller: PlayerController, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF05070B), Color.Black)))
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TvFocusableButton(text = "← Zurück", onClick = onClose)
        Spacer(modifier = Modifier.width(18.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (controller.isLiveStream) "Live TV" else controller.animeTitle,
                color = AccentCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            controller.currentEpisode?.let { ep ->
                val epLabel = when {
                    controller.isLiveStream -> ep.displayTitle
                    ep.seasonLabel.equals("Film", true) -> ep.displayTitle
                    ep.seasonLabel.equals("Filme", true) -> "Film ${ep.number}: ${ep.displayTitle}"
                    else -> "Staffel ${ep.seasonLabel} • Folge ${ep.number}: ${ep.displayTitle}"
                }
                Text(
                    text = epLabel,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (controller.isBuffering && controller.isPlaying) {
                Text(text = "Puffere…", color = TextSecondary, fontSize = 12.sp)
            }
            Pill(text = "🔊 ${controller.volume} %")
            if (controller.isLiveStream) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFEF4444))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(text = "🔴 LIVE SENDER", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                controller.activeHoster?.let { h ->
                    LanguageBadge(h.language)
                    Pill(text = "Hoster: ${h.hosterName}")
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(BgElevated)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = text, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PlayerBottomBar(
    controller: PlayerController,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onInteraction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black, Color(0xFF05070B))))
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        if (!controller.isLiveStream) {
            var dragFraction by remember { mutableStateOf<Float?>(null) }
            val duration = controller.durationMs
            val fraction = if (duration > 0) (controller.currentPositionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
            val shownFraction = dragFraction ?: fraction

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = formatDuration(if (dragFraction != null) (shownFraction * duration).toLong() else controller.currentPositionMs),
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
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
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = AccentCyan,
                        activeTrackColor = AccentCyan,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    )
                )
                Text(text = formatDuration(duration), color = TextSecondary, fontSize = 13.sp)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFEF4444)))
                Text(text = "LIVE ÜBERTRAGUNG", color = Color(0xFFEF4444), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text(text = controller.animeTitle, color = TextSecondary, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvFocusableButton(
                    text = if (controller.isPlaying) "⏸ Pause" else "▶ Play",
                    isPrimary = true,
                    onClick = { controller.togglePause(); onInteraction() }
                )
                if (controller.isLiveStream) {
                    TvFocusableButton(text = "🔄 Neu verbinden", onClick = { controller.reconnectLive(); onInteraction() })
                } else {
                    TvFocusableButton(text = "⏪ -10s", onClick = { controller.seekBy(-10_000L); onInteraction() })
                    TvFocusableButton(text = "⏩ +10s", onClick = { controller.seekBy(10_000L); onInteraction() })
                    TvFocusableButton(text = "+85s Intro Skip", onClick = { controller.seekBy(85_000L); onInteraction() })
                    controller.findNextEpisode()?.let { nextEp ->
                        TvFocusableButton(text = "⏭ Folge ${nextEp.number}", onClick = { controller.playNextEpisode(); onInteraction() })
                    }
                }
                TvFocusableButton(
                    text = if (isFullscreen) "🗗 Fenster" else "⛶ Vollbild",
                    onClick = onToggleFullscreen
                )
            }

            // Verfügbare Hoster / Sprachen für diese Folge direkt umschaltbar
            if (!controller.isLiveStream) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 16.dp)
                ) {
                    items(controller.availableHosters, key = { it.redirectPath }) { hoster ->
                        TvFocusableButton(
                            text = "${hoster.language.shortLabel} • ${hoster.hosterName}",
                            isSelected = controller.activeHoster?.redirectPath == hoster.redirectPath,
                            badgeColor = Color(hoster.language.badgeColorHex),
                            onClick = { controller.selectHoster(hoster); onInteraction() }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (controller.isLiveStream) {
                "Leertaste: Play/Pause • ↑/↓: Lautstärke • F / Doppelklick: Vollbild • Esc: Zurück"
            } else {
                "Leertaste: Play/Pause • ←/→: 10 s spulen • ↑/↓: Lautstärke • I: Intro überspringen • " +
                    "N: Nächste Folge • F / Doppelklick: Vollbild • Esc: Zurück"
            },
            color = TextMuted,
            fontSize = 11.sp
        )
    }
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
