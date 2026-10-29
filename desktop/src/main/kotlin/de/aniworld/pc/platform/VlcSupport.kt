package de.aniworld.pc.platform

import de.aniworld.tv.platform.AppLog
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import java.io.File

/**
 * Findet libvlc: zuerst den im Installer mitgelieferten VLC (App-Ressourcen), sonst einen
 * auf dem PC installierten. Eine Fabrik für alle Player, da libvlc teuer zu starten ist.
 */
object VlcSupport {

    private const val TAG = "VlcSupport"

    /** VLC aus dem Installer (siehe prepareVlc in desktop/build.gradle.kts). */
    private val bundledDir: File?
        get() = System.getProperty("compose.application.resources.dir")
            ?.let { File(it, "vlc") }
            ?.takeIf { File(it, "libvlc.dll").isFile }

    private val factoryLazy = lazy {
        runCatching {
            // vlcj sucht zuerst in jna.library.path und setzt dann auch den Plugin-Pfad
            bundledDir?.let { System.setProperty("jna.library.path", it.absolutePath) }
            val discovery = NativeDiscovery()
            if (!discovery.discover()) {
                AppLog.e(TAG, "libvlc nicht gefunden (mitgeliefert: ${bundledDir != null})")
                return@runCatching null
            }
            AppLog.i(TAG, "libvlc gefunden (mitgeliefert: ${bundledDir != null})")
            MediaPlayerFactory(
                discovery,
                "--no-video-title-show",
                "--no-snapshot-preview",
                "--no-osd",
                "--quiet",
                "--network-caching=1500",
                "--http-reconnect"
            )
        }.onFailure { AppLog.e(TAG, "VLC konnte nicht gestartet werden", it) }.getOrNull()
    }

    val factory: MediaPlayerFactory? by factoryLazy

    fun release() {
        if (factoryLazy.isInitialized()) runCatching { factoryLazy.value?.release() }
    }
}
