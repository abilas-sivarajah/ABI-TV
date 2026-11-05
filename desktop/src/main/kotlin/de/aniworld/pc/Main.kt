package de.aniworld.pc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.aniworld.pc.cast.PcCastManager
import de.aniworld.pc.platform.AppDirs
import de.aniworld.pc.platform.ChromiumStreamExtractor
import de.aniworld.pc.platform.JsonFileStore
import de.aniworld.pc.platform.StoAnimeFilter
import de.aniworld.pc.platform.VlcSupport
import de.aniworld.pc.platform.WindowChrome
import de.aniworld.pc.ui.DesktopApp
import de.aniworld.pc.ui.compat.BackDispatcher
import de.aniworld.pc.ui.compat.KeyDispatcher
import de.aniworld.pc.ui.compat.LocalComposeWindow
import de.aniworld.pc.ui.compat.LocalWindowState
import de.aniworld.pc.ui.components.AppTitleBar
import de.aniworld.pc.ui.components.IntroAnimation
import de.aniworld.pc.ui.theme.AniWorldTvTheme
import de.aniworld.pc.ui.theme.BgPrimary
import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.remote.StreamResolver
import de.aniworld.tv.data.remote.TmdbArtwork
import de.aniworld.tv.data.remote.LetterboxdRatings
import de.aniworld.tv.data.remote.LetterboxdLists
import de.aniworld.tv.platform.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.MouseEvent
import java.io.File
import java.io.PrintStream
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Properties
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** TMDB-Schlüssel aus der Umgebungsvariable oder der beim Bauen eingebauten abi-tmdb.properties. */
private fun tmdbApiKey(): String =
    System.getenv("TMDB_API_KEY")?.trim()?.takeIf { it.isNotEmpty() }
        ?: runCatching {
            AppDirs.javaClass.classLoader.getResourceAsStream("abi-tmdb.properties")?.use { input ->
                Properties().apply { load(input) }.getProperty("apiKey")?.trim()
            }
        }.getOrNull()
        ?: ""

fun main() {
    setupLogging()
    AppLog.i("Main", "ABI TV ${AppDirs.appVersion} startet (${System.getProperty("os.name")}, Java ${System.getProperty("java.version")})")

    // Gemeinsame JVM-Schicht (:shared) an den PC anbinden
    val mainStore = JsonFileStore(File(AppDirs.dataDir, "${LocalStorage.PREFS_NAME}.json"))
    LocalStorage.init(mainStore)
    val chromium = ChromiumStreamExtractor(File(AppDirs.cacheDir, "jcef"))
    StreamResolver.browserExtractor = chromium
    TmdbArtwork.configure(tmdbApiKey(), AppDirs.cacheDir)
    LetterboxdRatings.configure(AppDirs.cacheDir)
    LetterboxdLists.configure(AppDirs.cacheDir)
    chromium.prepareInBackground(backgroundScope)
    PcCastManager.start()
    backgroundScope.launch { AppDirs.pruneImageCache() }
    // Liste der Anime auf S.to, damit der Serien-Bereich ohne Anime auskommt
    backgroundScope.launch { StoAnimeFilter.load() }

    // Auch bei exitProcess (z. B. nach dem Update) Merkliste & Verlauf sicher schreiben
    Runtime.getRuntime().addShutdownHook(Thread {
        mainStore.flush()
        PcCastManager.stop()
    })

    // Zurück-Taste der Maus wie Esc
    Toolkit.getDefaultToolkit().addAWTEventListener({ event ->
        if (event is MouseEvent && event.id == MouseEvent.MOUSE_RELEASED && event.button == 4) {
            SwingUtilities.invokeLater { BackDispatcher.dispatch() }
        }
    }, AWTEvent.MOUSE_EVENT_MASK)

    val icon = Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")?.use { input ->
        BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(input.readBytes()).toComposeImageBitmap())
    }

    application {
        val windowState = rememberWindowState(placement = WindowPlacement.Maximized, size = DpSize(1440.dp, 900.dp))
        // Erst das Fenster schließen (dabei gibt der Player seinen VLC-Player frei), danach VLC und
        // Chromium aufräumen – andersherum blieb die App beim Schließen während der Wiedergabe hängen
        val closeApp = { exitApplication() }
        Window(
            onCloseRequest = closeApp,
            title = "ABI TV",
            icon = icon,
            state = windowState,
            // Keine weiße Windows-Titelleiste – die App zeichnet ihre eigene (AppTitleBar)
            undecorated = true,
            // Größe ändern (Ränder ziehen) nur im normalen Fenster, nicht maximiert oder im Vollbild
            resizable = windowState.placement == WindowPlacement.Floating,
            onPreviewKeyEvent = { KeyDispatcher.dispatch(it) }
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = java.awt.Dimension(1024, 640)
                // Dunkler Fensterhintergrund: beim Umbau des Layouts (z. B. Leisten im Vollbild-Player
                // aus-/einblenden) blitzte sonst für einen Moment der weiße Standard-Hintergrund auf
                val dark = java.awt.Color(0x0B, 0x0E, 0x14)
                window.background = dark
                window.contentPane.background = dark
                window.rootPane.background = dark
                WindowChrome.install(window)
            }
            CompositionLocalProvider(LocalWindowState provides windowState, LocalComposeWindow provides window) {
                AniWorldTvTheme {
                    var showIntro by remember { mutableStateOf(true) }
                    Column(modifier = Modifier.fillMaxSize().background(BgPrimary)) {
                        // Im Vollbild (Player) ohne Titelleiste
                        if (windowState.placement != WindowPlacement.Fullscreen) {
                            AppTitleBar(icon = icon, onClose = closeApp)
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            DesktopApp(chromium)
                            // Start-Animation; die Startseite lädt dahinter schon
                            if (showIntro) {
                                IntroAnimation(onFinished = { showIntro = false })
                            }
                        }
                    }
                }
            }
        }
    }
    // Aufräumen darf das Beenden nie blockieren: spätestens nach 5 s hart beenden (Daten vorher sichern)
    Thread {
        Thread.sleep(5_000)
        runCatching { mainStore.flush() }
        Runtime.getRuntime().halt(0)
    }.apply { isDaemon = true }.start()
    runCatching { chromium.dispose() }
    runCatching { VlcSupport.release() }
    // JCEF- und Netzwerk-Threads würden die JVM sonst am Leben halten
    exitProcess(0)
}

/** Log in %LOCALAPPDATA%\ABI TV\abi-tv.log (wird bei jedem Start neu begonnen) und auf die Konsole. */
private fun setupLogging() {
    val logFile = File(AppDirs.cacheDir, "abi-tv.log")
    val out = runCatching { PrintStream(logFile.outputStream(), true, Charsets.UTF_8) }.getOrNull()
    val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    AppLog.sink = { level, tag, message, error ->
        val line = "${LocalTime.now().format(timeFormat)} ${level.name.first()}/$tag: $message"
        System.err.println(line)
        error?.printStackTrace()
        out?.let { stream ->
            synchronized(stream) {
                stream.println(line)
                error?.printStackTrace(stream)
            }
        }
    }
}
