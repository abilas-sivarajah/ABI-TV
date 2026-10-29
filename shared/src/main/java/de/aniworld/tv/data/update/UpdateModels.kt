package de.aniworld.tv.data.update

data class AppRelease(
    val tagName: String,
    val versionName: String,
    val title: String,
    val changelog: String,
    val downloadUrl: String,
    val assetName: String,
    val fileSize: Long,
    /** SHA-256 (hex) aus dem "digest"-Feld der GitHub API, falls vorhanden. */
    val sha256: String?
)

sealed interface UpdateCheckResult {
    data class Available(val release: AppRelease) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : UpdateState {
        val progress: Float
            get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }
    data object Verifying : UpdateState
    /** Android 8+: Die App darf noch keine APKs installieren ("Unbekannte Apps installieren"). */
    data object NeedsInstallPermission : UpdateState
    /** Installation an das System übergeben, wartet ggf. auf Bestätigung durch den Nutzer. */
    data object Installing : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** Gemeinsame Hilfen für die Updater von TV, Handy und PC. */
object UpdateUtils {

    /** Entfernt Markdown-Syntax und GitHub-Boilerplate, damit die Notizen auf dem TV lesbar sind. */
    fun cleanChangelog(raw: String): String {
        val cleaned = raw.lineSequence()
            .map { it.trimEnd() }
            .filterNot { it.trimStart().startsWith("**Full Changelog**", ignoreCase = true) }
            .filterNot { it.trimStart().startsWith("<!--") }
            .map { line ->
                line.replace(Regex("^\\s*#{1,6}\\s*"), "")
                    .replace(Regex("^\\s*[-*+]\\s+"), "• ")
                    .replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")
                    .replace(Regex(" by @\\S+ in https?://\\S+"), "")
                    .replace("**", "")
                    .replace("__", "")
                    .replace("`", "")
            }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        return cleaned.ifBlank { "Allgemeine Verbesserungen und Fehlerbehebungen." }
    }

    /**
     * Vergleicht zwei semantische Versionen (z. B. "1.2.0" mit "1.1.9").
     */
    fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteParts = remote.split(".")
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        val currentParts = current.split(".")
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
