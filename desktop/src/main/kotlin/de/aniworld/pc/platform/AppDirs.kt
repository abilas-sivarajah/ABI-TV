package de.aniworld.pc.platform

import java.io.File
import java.util.Properties

/** Ablageorte der PC-App: Einstellungen in %APPDATA%\ABI TV, Caches & Downloads in %LOCALAPPDATA%\ABI TV Cache. */
object AppDirs {

    val dataDir: File by lazy {
        val base = System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".config")
        File(base, "ABI TV").apply { mkdirs() }
    }

    // Nicht "%LOCALAPPDATA%\ABI TV": Dorthin installiert der (Pro-Benutzer-)Installer die App selbst
    val cacheDir: File by lazy {
        val base = System.getenv("LOCALAPPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".cache")
        File(base, "ABI TV Cache").apply { mkdirs() }
    }

    /** Version aus dem Build (gleich wie TV & Handy, im Release aus dem Git-Tag). */
    val appVersion: String by lazy {
        runCatching {
            AppDirs::class.java.getResourceAsStream("/abi-version.properties")?.use { input ->
                Properties().apply { load(input) }.getProperty("version")
            }
        }.getOrNull()?.trim()?.ifBlank { null } ?: "0.0.0"
    }

    /** Alte Cover aus dem Bild-Cache entfernen, damit er nicht endlos wächst. */
    fun pruneImageCache(maxAgeMs: Long = 21L * 24 * 60 * 60 * 1000) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        File(cacheDir, "images").listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) file.delete()
        }
    }
}
