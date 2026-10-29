package de.aniworld.tv.data.update

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.IntentCompat
import com.google.gson.JsonParser
import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.remote.HttpClient
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

object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val USER_AGENT = "ABI-TV-Updater"
    private const val SNOOZE_DURATION_MS = 24L * 60 * 60 * 1000
    internal const val ACTION_INSTALL_STATUS = "de.aniworld.tv.UPDATE_INSTALL_STATUS"

    /**
     * Standard GitHub Repository (Format: "owner/repo").
     * Wird verwendet, wenn in LocalStorage nichts anderes hinterlegt ist.
     */
    var defaultRepo: String = "abilas-sivarajah/ABI-TV"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private var pendingApk: File? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun getTargetRepo(): String {
        val configured = runCatching { LocalStorage.githubRepo }.getOrNull()
        return if (!configured.isNullOrBlank() && !configured.contains("YOUR_GITHUB_USER")) {
            configured
        } else {
            defaultRepo
        }
    }

    /**
     * Prüft bei GitHub Releases, ob eine neuere Version verfügbar ist.
     * @param isTvApp true für die TV-APK (ABI_TV.apk), false für die Mobile-APK (ABI_Mobile.apk)
     * @param forceCheck true ignoriert ein "Später" des Nutzers
     */
    suspend fun checkForUpdate(
        context: Context,
        isTvApp: Boolean,
        forceCheck: Boolean = false
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        cleanupDownloads(context)

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
                    Log.w(TAG, "GitHub API HTTP Fehler: ${response.code}")
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
            Log.w(TAG, "Verbindung zu GitHub Releases fehlgeschlagen.", e)
            return@withContext UpdateCheckResult.Failed("Keine Verbindung zu GitHub. Bitte Internetverbindung prüfen.")
        } ?: return@withContext UpdateCheckResult.Failed("Leere Antwort von GitHub.")

        val json = runCatching { JsonParser.parseString(bodyStr).asJsonObject }.getOrNull()
            ?: return@withContext UpdateCheckResult.Failed("Antwort von GitHub konnte nicht gelesen werden.")

        val tagName = json.get("tag_name")?.takeUnless { it.isJsonNull }?.asString
            ?: return@withContext UpdateCheckResult.Failed("Release ohne Versionsnummer gefunden.")
        val title = json.get("name")?.takeUnless { it.isJsonNull }?.asString?.ifBlank { null } ?: tagName
        val changelog = cleanChangelog(json.get("body")?.takeUnless { it.isJsonNull }?.asString.orEmpty())

        val remoteVersion = tagName.trim().removePrefix("v").removePrefix("V")
        val currentVersion = getCurrentVersionName(context)
        Log.d(TAG, "Prüfe Version: Installiert=$currentVersion, Remote=$remoteVersion")

        if (!isNewerVersion(remoteVersion, currentVersion)) {
            return@withContext UpdateCheckResult.UpToDate
        }

        if (!forceCheck && isSnoozed(tagName)) {
            Log.d(TAG, "Release $tagName wurde vom Nutzer zurückgestellt.")
            return@withContext UpdateCheckResult.UpToDate
        }

        val assets = json.getAsJsonArray("assets")?.mapNotNull { elem ->
            val obj = elem.asJsonObject
            val name = obj.get("name")?.asString ?: return@mapNotNull null
            val url = obj.get("browser_download_url")?.asString ?: return@mapNotNull null
            val size = obj.get("size")?.asLong ?: 0L
            val digest = obj.get("digest")?.takeUnless { it.isJsonNull }?.asString
            ReleaseAsset(name, url, size, digest)
        }.orEmpty()

        val asset = pickAsset(assets, isTvApp)
            ?: return@withContext UpdateCheckResult.Failed(
                "Release $tagName enthält keine ${if (isTvApp) "TV" else "Mobile"}-APK."
            )

        UpdateCheckResult.Available(
            AppRelease(
                tagName = tagName,
                versionName = remoteVersion,
                title = title,
                changelog = changelog,
                downloadUrl = asset.url,
                assetName = asset.name,
                fileSize = asset.size,
                sha256 = asset.digest
                    ?.takeIf { it.startsWith("sha256:", ignoreCase = true) }
                    ?.substringAfter(':')
                    ?.lowercase()
            )
        )
    }

    private data class ReleaseAsset(val name: String, val url: String, val size: Long, val digest: String?)

    /**
     * Wählt die APK für diese App. Kein Fallback auf die jeweils andere App, da TV und
     * Mobile verschiedene Paketnamen haben und sonst eine zweite App installiert würde.
     */
    private fun pickAsset(assets: List<ReleaseAsset>, isTvApp: Boolean): ReleaseAsset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        val preferred = if (isTvApp) listOf("ABI_TV.apk", "AniWorld_TV.apk") else listOf("ABI_Mobile.apk", "AniWorld_Mobile.apk")
        preferred.forEach { wanted ->
            apks.firstOrNull { it.name.equals(wanted, ignoreCase = true) }?.let { return it }
        }
        val tvToken = Regex("(^|[^a-z])tv([^a-z]|$)", RegexOption.IGNORE_CASE)
        val mobileToken = Regex("mobile", RegexOption.IGNORE_CASE)
        return apks.firstOrNull {
            if (isTvApp) tvToken.containsMatchIn(it.name) && !mobileToken.containsMatchIn(it.name)
            else mobileToken.containsMatchIn(it.name) && !tvToken.containsMatchIn(it.name)
        }
    }

    internal fun cleanChangelog(raw: String): String = UpdateUtils.cleanChangelog(raw)

    fun getCurrentVersionName(context: Context): String {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }
        }.getOrNull()?.trim()?.removePrefix("v")?.removePrefix("V") ?: "1.0.0"
    }

    fun isNewerVersion(remote: String, current: String): Boolean = UpdateUtils.isNewerVersion(remote, current)

    // ------------------- "Später" -------------------

    /** Blendet den Hinweis für dieses Release 24 Stunden lang aus. */
    fun snooze(release: AppRelease) {
        LocalStorage.ignoredUpdateTag = release.tagName
        LocalStorage.updateSnoozedUntil = System.currentTimeMillis() + SNOOZE_DURATION_MS
    }

    private fun isSnoozed(tagName: String): Boolean {
        val tag = runCatching { LocalStorage.ignoredUpdateTag }.getOrDefault("")
        val until = runCatching { LocalStorage.updateSnoozedUntil }.getOrDefault(0L)
        return tag == tagName && System.currentTimeMillis() < until
    }

    // ------------------- Download & Installation -------------------

    /**
     * Lädt die APK herunter, prüft Größe, SHA-256 und Paketnamen und übergibt sie an den
     * Android PackageInstaller. Der Fortschritt wird über [state] gemeldet.
     */
    fun startUpdate(context: Context, release: AppRelease) {
        if (downloadJob?.isActive == true) return
        val appContext = context.applicationContext
        _state.value = UpdateState.Downloading(0L, release.fileSize)
        downloadJob = scope.launch {
            try {
                val apk = downloadVerified(appContext, release)
                pendingApk = apk
                installOrRequestPermission(appContext, apk)
            } catch (e: CancellationException) {
                _state.value = UpdateState.Idle
                throw e
            } catch (e: UpdateException) {
                Log.w(TAG, "Update fehlgeschlagen: ${e.message}")
                _state.value = UpdateState.Failed(e.message ?: "Update fehlgeschlagen.")
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Update", e)
                val reason = if (e is IOException) "Verbindung unterbrochen" else (e.localizedMessage ?: e.javaClass.simpleName)
                _state.value = UpdateState.Failed("Download fehlgeschlagen: $reason")
            }
        }
    }

    fun cancelUpdate() {
        downloadJob?.cancel()
        downloadJob = null
        pendingApk = null
        _state.value = UpdateState.Idle
    }

    /** Setzt Fehler- und Wartezustände zurück, z. B. wenn der Dialog geschlossen wird. */
    fun resetIfFinished() {
        if (_state.value is UpdateState.Failed || _state.value is UpdateState.NeedsInstallPermission) {
            pendingApk = null
            _state.value = UpdateState.Idle
        }
    }

    /** Aus onResume aufrufen: setzt die Installation fort, nachdem der Nutzer die Berechtigung erteilt hat. */
    fun resumePendingInstall(context: Context) {
        val apk = pendingApk ?: return
        if (_state.value == UpdateState.NeedsInstallPermission && canInstallPackages(context)) {
            scope.launch { installApk(context.applicationContext, apk) }
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Manche TV-Geräte haben diese Einstellungsseite nicht: direkt den System-Installer fragen lassen
            Log.w(TAG, "Einstellung für unbekannte Quellen nicht verfügbar", e)
            pendingApk?.let { apk -> scope.launch { installApk(context.applicationContext, apk) } }
        }
    }

    private class UpdateException(message: String) : Exception(message)

    private suspend fun downloadVerified(context: Context, release: AppRelease): File {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val destFile = File(updatesDir, release.assetName)

        // Bereits vollständig heruntergeladen (z. B. beim erneuten Versuch nach einem Abbruch)?
        if (destFile.exists() && isValidDownload(destFile, release)) {
            return destFile
        }
        destFile.delete()

        val partFile = File(updatesDir, "${release.assetName}.part")
        partFile.delete()

        val request = Request.Builder()
            .url(release.downloadUrl)
            .header("User-Agent", USER_AGENT)
            .build()

        HttpClient.okHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw UpdateException("Download fehlgeschlagen (HTTP ${response.code}).")
            }
            val body = response.body ?: throw UpdateException("Leere Antwort vom Update-Server.")
            val totalBytes = if (release.fileSize > 0) release.fileSize else body.contentLength()
            var downloaded = 0L
            var lastReport = 0L

            body.byteStream().use { input ->
                partFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastReport > 100) {
                            lastReport = now
                            _state.value = UpdateState.Downloading(downloaded, totalBytes)
                        }
                    }
                }
            }
            _state.value = UpdateState.Downloading(downloaded, totalBytes)
        }

        _state.value = UpdateState.Verifying
        if (!isValidDownload(partFile, release)) {
            partFile.delete()
            throw UpdateException("Die heruntergeladene Datei ist beschädigt. Bitte erneut versuchen.")
        }
        if (!partFile.renameTo(destFile)) {
            throw UpdateException("Update konnte nicht gespeichert werden.")
        }

        val archivePackage = readArchivePackageName(context, destFile)
        if (archivePackage != context.packageName) {
            destFile.delete()
            throw UpdateException("Die APK gehört zu einer anderen App ($archivePackage).")
        }
        return destFile
    }

    private fun isValidDownload(file: File, release: AppRelease): Boolean {
        if (release.fileSize > 0 && file.length() != release.fileSize) return false
        val expected = release.sha256 ?: return file.length() > 0
        return sha256Of(file) == expected
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

    @Suppress("DEPRECATION")
    private fun readArchivePackageName(context: Context, apk: File): String? =
        runCatching { context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)?.packageName }.getOrNull()

    private fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    private fun installOrRequestPermission(context: Context, apk: File) {
        if (!canInstallPackages(context)) {
            _state.value = UpdateState.NeedsInstallPermission
            return
        }
        installApk(context, apk)
    }

    /**
     * Installiert die APK über eine PackageInstaller-Session. Das funktioniert ab Android 5
     * ohne FileProvider und liefert ein Ergebnis zurück (siehe [UpdateInstallReceiver]).
     * Ab Android 12 kann sich die App so auch ohne Rückfrage selbst aktualisieren.
     */
    private fun installApk(context: Context, apk: File) {
        _state.value = UpdateState.Installing
        val installer = context.packageManager.packageInstaller
        // Reste früherer, nicht abgeschlossener Versuche verwerfen
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }

        var sessionId = -1
        try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(apk.length())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setInstallReason(PackageManager.INSTALL_REASON_USER)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out, 64 * 1024) }
                    session.fsync(out)
                }
                val statusIntent = Intent(context, UpdateInstallReceiver::class.java)
                    .setAction(ACTION_INSTALL_STATUS)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val pendingIntent = PendingIntent.getBroadcast(context, sessionId, statusIntent, flags)
                session.commit(pendingIntent.intentSender)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Konnte Installation nicht starten", e)
            if (sessionId != -1) runCatching { installer.abandonSession(sessionId) }
            _state.value = UpdateState.Failed(
                "Installation konnte nicht gestartet werden: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
        }
    }

    internal fun onInstallStatus(status: Int, systemMessage: String?) {
        Log.d(TAG, "Installationsstatus $status: $systemMessage")
        _state.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                pendingApk = null
                UpdateState.Idle
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateState.Failed("Installation abgebrochen.")
            PackageInstaller.STATUS_FAILURE_BLOCKED -> UpdateState.Failed("Die Installation wurde vom System blockiert.")
            PackageInstaller.STATUS_FAILURE_CONFLICT -> UpdateState.Failed(
                "Das Update ist anders signiert als die installierte App. " +
                    "Bitte die App einmal deinstallieren und die neue Version manuell installieren."
            )
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> UpdateState.Failed("Das Update ist mit diesem Gerät nicht kompatibel.")
            PackageInstaller.STATUS_FAILURE_INVALID -> UpdateState.Failed("Die heruntergeladene APK ist ungültig.")
            PackageInstaller.STATUS_FAILURE_STORAGE -> UpdateState.Failed("Nicht genug Speicherplatz für das Update.")
            else -> UpdateState.Failed("Installation fehlgeschlagen${systemMessage?.let { ": $it" } ?: "."}")
        }
    }

    /** Löscht alte Update-Dateien, sofern gerade kein Update läuft. */
    private fun cleanupDownloads(context: Context) {
        if (_state.value != UpdateState.Idle) return
        File(context.cacheDir, "updates").listFiles()?.forEach { it.delete() }
    }
}

/** Empfängt das Ergebnis der PackageInstaller-Session. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UpdateManager.ACTION_INSTALL_STATUS) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmIntent = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
            if (confirmIntent == null) {
                UpdateManager.onInstallStatus(PackageInstaller.STATUS_FAILURE, "Bestätigungsdialog fehlt")
                return
            }
            try {
                context.startActivity(confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                UpdateManager.onInstallStatus(PackageInstaller.STATUS_FAILURE, e.localizedMessage)
            }
            return
        }
        UpdateManager.onInstallStatus(status, message)
    }
}
