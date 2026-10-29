package de.aniworld.pc

import androidx.compose.runtime.CompositionLocalProvider
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
import de.aniworld.pc.platform.VlcSupport
import de.aniworld.pc.ui.DesktopApp
import de.aniworld.pc.ui.compat.BackDispatcher
import de.aniworld.pc.ui.compat.KeyDispatcher
import de.aniworld.pc.ui.compat.LocalWindowState
import de.aniworld.pc.ui.theme.AniWorldTvTheme
import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.remote.StreamResolver
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
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

fun main() {
    setupLogging()
    AppLog.i("Main", "ABI TV ${AppDirs.appVersion} startet (${System.getProperty("os.name")}, Java ${System.getProperty("java.version")})")

    // Gemeinsame JVM-Schicht (:shared) an den PC anbinden
    val mainStore = JsonFileStore(File(AppDirs.dataDir, "${LocalStorage.PREFS_NAME}.json"))
    LocalStorage.init(mainStore)
    val chromium = ChromiumStreamExtractor(File(AppDirs.cacheDir, "jcef"))
    StreamResolver.browserExtractor = chromium
    chromium.prepareInBackground(backgroundScope)
    PcCastManager.start()
    backgroundScope.launch { AppDirs.pruneImageCache() }

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
        Window(
            onCloseRequest = {
                chromium.dispose()
                VlcSupport.release()
                exitApplication()
            },
            title = "ABI TV",
            icon = icon,
            state = windowState,
            onPreviewKeyEvent = { KeyDispatcher.dispatch(it) }
        ) {
            window.minimumSize = java.awt.Dimension(1024, 640)
            CompositionLocalProvider(LocalWindowState provides windowState) {
                AniWorldTvTheme {
                    DesktopApp(chromium)
                }
            }
        }
    }
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
