package de.aniworld.pc.update

import com.google.gson.JsonParser
import de.aniworld.pc.platform.AppDirs
import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.remote.HttpClient
import de.aniworld.tv.data.update.AppRelease
import de.aniworld.tv.data.update.UpdateCheckResult
import de.aniworld.tv.data.update.UpdateState
import de.aniworld.tv.data.update.UpdateUtils
import de.aniworld.tv.platform.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.system.exitProcess

/**
 * Updater der PC-App: prüft dasselbe GitHub-Release wie TV und Handy, lädt aber die
 * Windows-Installer-Datei (ABI_PC.msi). Der Installer ist pro Benutzer, braucht also keine
 * Admin-Rechte; danach startet ABI TV von selbst neu.
 */
object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val USER_AGENT = "ABI-TV-Updater"
    private const val SNOOZE_DURATION_MS = 24L * 60 * 60 * 1000
    const val ASSET_NAME = "ABI_PC.msi"

    var defaultRepo: String = "abilas-sivarajah/ABI-TV"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val downloadDir: File
        get() = File(AppDirs.cacheDir, "updates").apply { mkdirs() }

    fun getCurrentVersionName(): String = AppDirs.appVersion

    fun getTargetRepo(): String {
        val configured = runCatching { LocalStorage.githubRepo }.getOrNull()
        return if (!configured.isNullOrBlank() && !configured.contains("YOUR_GITHUB_USER")) configured else defaultRepo
    }

    suspend fun checkForUpdate(forceCheck: Boolean = false): UpdateCheckResult = withContext(Dispatchers.IO) {
        cleanupDownloads()

        val repo = getTargetRepo()
        if (repo.isBlank() || !repo.contains("/")) {
            return@withContext UpdateCheckResult.Failed("Kein gültiges GitHub-Repo konfiguriert ($repo).")
        }

        val request = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", USER_AGENT)
            .build()

        val bodyStr = try {
            HttpClient.okHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val message = when (response.code) {
                        404 -> "Noch kein Release auf GitHub veröffentlicht."
                        403, 429 -> "GitHub-Anfragelimit erreicht. Bitte später erneut versuchen."
                        else -> "GitHub antwortet nicht wie erwartet (HTTP ${response.code})."
                    }
                    return@withContext UpdateCheckResult.Failed(message)
                }
                response.body?.string()
            }
        } catch (e: IOException) {
            AppLog.w(TAG, "Verbindung zu GitHub Releases fehlgeschlagen.", e)
            return@withContext UpdateCheckResult.Failed("Keine Verbindung zu GitHub. Bitte Internetverbindung prüfen.")
        } ?: return@withContext UpdateCheckResult.Failed("Leere Antwort von GitHub.")

        val json = runCatching { JsonParser.parseString(bodyStr).asJsonObject }.getOrNull()
            ?: return@withContext UpdateCheckResult.Failed("Antwort von GitHub konnte nicht gelesen werden.")

        val tagName = json.get("tag_name")?.takeUnless { it.isJsonNull }?.asString
            ?: return@withContext UpdateCheckResult.Failed("Release ohne Versionsnummer gefunden.")
        val title = json.get("name")?.takeUnless { it.isJsonNull }?.asString?.ifBlank { null } ?: tagName
        val changelog = UpdateUtils.cleanChangelog(json.get("body")?.takeUnless { it.isJsonNull }?.asString.orEmpty())

        val remoteVersion = tagName.trim().removePrefix("v").removePrefix("V")
        if (!UpdateUtils.isNewerVersion(remoteVersion, getCurrentVersionName())) {
            return@withContext UpdateCheckResult.UpToDate
        }
        if (!forceCheck && isSnoozed(tagName)) {
            return@withContext UpdateCheckResult.UpToDate
        }

        val asset = json.getAsJsonArray("assets")
            ?.map { it.asJsonObject }
            ?.firstOrNull { it.get("name")?.asString.equals(ASSET_NAME, ignoreCase = true) }
            ?: return@withContext UpdateCheckResult.Failed("Release $tagName enthält noch keinen PC-Installer.")

        UpdateCheckResult.Available(
            AppRelease(
                tagName = tagName,
                versionName = remoteVersion,
                title = title,
                changelog = changelog,
                downloadUrl = asset.get("browser_download_url").asString,
                assetName = asset.get("name").asString,
                fileSize = asset.get("size")?.asLong ?: 0L,
                sha256 = asset.get("digest")?.takeUnless { it.isJsonNull }?.asString
                    ?.takeIf { it.startsWith("sha256:", ignoreCase = true) }
                    ?.substringAfter(':')
                    ?.lowercase()
            )
        )
    }

    fun snooze(release: AppRelease) {
        LocalStorage.ignoredUpdateTag = release.tagName
        LocalStorage.updateSnoozedUntil = System.currentTimeMillis() + SNOOZE_DURATION_MS
    }

    private fun isSnoozed(tagName: String): Boolean =
        LocalStorage.ignoredUpdateTag == tagName && System.currentTimeMillis() < LocalStorage.updateSnoozedUntil

    fun startUpdate(release: AppRelease) {
        if (downloadJob?.isActive == true) return
        _state.value = UpdateState.Downloading(0L, release.fileSize)
        downloadJob = scope.launch {
            try {
                val msi = downloadVerified(release)
                _state.value = UpdateState.Installing
                installAndRestart(msi)
            } catch (e: CancellationException) {
                _state.value = UpdateState.Idle
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "Update fehlgeschlagen", e)
                val reason = if (e is IOException) "Verbindung unterbrochen" else (e.message ?: e.javaClass.simpleName)
                _state.value = UpdateState.Failed("Update fehlgeschlagen: $reason")
            }
        }
    }

    fun cancelUpdate() {
        downloadJob?.cancel()
        downloadJob = null
        _state.value = UpdateState.Idle
    }

    fun resetIfFinished() {
        if (_state.value is UpdateState.Failed) _state.value = UpdateState.Idle
    }

    private suspend fun downloadVerified(release: AppRelease): File {
        val target = File(downloadDir, "ABI_PC-${release.versionName}.msi")
        val partial = File(downloadDir, "${target.name}.part")
        val request = Request.Builder().url(release.downloadUrl).header("User-Agent", USER_AGENT).build()

        HttpClient.okHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Leere Antwort")
            val total = body.contentLength().takeIf { it > 0 } ?: release.fileSize
            body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        done += read
                        _state.value = UpdateState.Downloading(done, total)
                    }
                }
            }
        }

        _state.value = UpdateState.Verifying
        if (release.fileSize > 0 && partial.length() != release.fileSize) {
            partial.delete()
            throw IllegalStateException("Download unvollständig.")
        }
        release.sha256?.let { expected ->
            if (sha256Of(partial) != expected) {
                partial.delete()
                throw IllegalStateException("Prüfsumme stimmt nicht – Datei verworfen.")
            }
        }
        target.delete()
        if (!partial.renameTo(target)) throw IOException("Installer konnte nicht abgelegt werden.")
        return target
    }

    /**
     * Startet den Installer über ein kleines Skript, das nach der Installation ABI TV wieder öffnet,
     * und beendet die App, damit Windows ihre Dateien ersetzen kann.
     */
    private fun installAndRestart(msi: File) {
        val appExe = System.getProperty("jpackage.app-path")?.takeIf { File(it).isFile }
        val script = File(downloadDir, "install-update.cmd")
        script.writeText(
            buildString {
                appendLine("@echo off")
                appendLine("timeout /t 2 /nobreak >nul")
                appendLine("msiexec /i \"${msi.absolutePath}\" /passive /norestart")
                if (appExe != null) appendLine("start \"\" \"$appExe\"")
            },
            Charsets.UTF_8
        )
        ProcessBuilder("cmd.exe", "/c", "start", "\"ABI TV Update\"", "/min", script.absolutePath)
            .directory(downloadDir)
            .start()
        AppLog.i(TAG, "Installer gestartet: ${msi.name}")
        exitProcess(0)
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun cleanupDownloads() {
        downloadDir.listFiles()?.forEach { it.delete() }
    }
}
