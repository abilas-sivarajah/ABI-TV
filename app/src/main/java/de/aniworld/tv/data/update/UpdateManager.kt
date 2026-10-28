package de.aniworld.tv.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.gson.JsonParser
import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.remote.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

data class AppRelease(
    val tagName: String,
    val versionName: String,
    val title: String,
    val changelog: String,
    val downloadUrl: String,
    val assetName: String,
    val fileSize: Long
)

object UpdateManager {

    private const val TAG = "UpdateManager"

    /**
     * Standard GitHub Repository (Format: "owner/repo").
     * Wird verwendet, wenn in LocalStorage nichts anderes hinterlegt ist.
     */
    var defaultRepo: String = "abilas-sivarajah/ABI-TV"

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
     * @param context Der Context zur Ermittlung der aktuell installierten Version
     * @param isTvApp true für TV-Version (AniWorld_TV.apk), false für Mobile (AniWorld_Mobile.apk)
     * @param forceCheck true ignoriert "Später"-Status und prüft in jedem Fall
     */
    suspend fun checkForUpdate(
        context: Context,
        isTvApp: Boolean = true,
        forceCheck: Boolean = false
    ): AppRelease? = withContext(Dispatchers.IO) {
        val repo = getTargetRepo()
        if (repo.isBlank() || repo.contains("YOUR_GITHUB_USER") || !repo.contains("/")) {
            Log.d(TAG, "Kein gültiges GitHub-Repo konfiguriert ($repo). Update-Check übersprungen.")
            return@withContext null
        }

        val url = "https://api.github.com/repos/$repo/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "AniWorld-App-Updater")
            .build()

        val response = runCatching {
            HttpClient.okHttp.newCall(request).execute()
        }.getOrNull() ?: run {
            Log.w(TAG, "Verbindung zu GitHub Releases fehlgeschlagen.")
            return@withContext null
        }

        if (!response.isSuccessful) {
            Log.w(TAG, "GitHub API HTTP Fehler: ${response.code}")
            return@withContext null
        }

        val bodyStr = response.body?.string() ?: return@withContext null
        val json = runCatching { JsonParser.parseString(bodyStr).asJsonObject }.getOrNull()
            ?: return@withContext null

        val tagName = json.get("tag_name")?.asString ?: return@withContext null
        val title = json.get("name")?.asString ?: tagName
        val changelog = json.get("body")?.asString?.trim() ?: "Neue Verbesserungen und Fehlerbehebungen."

        if (!forceCheck) {
            val ignoredTag = runCatching { LocalStorage.ignoredUpdateTag }.getOrDefault("")
            if (ignoredTag == tagName) {
                Log.d(TAG, "Release $tagName wurde vom Nutzer vorübergehend zurückgestellt.")
                return@withContext null
            }
        }

        val remoteVersion = tagName.trim().removePrefix("v").removePrefix("V")
        val currentVersion = getCurrentVersionName(context)

        Log.d(TAG, "Prüfe Version: Installiert=$currentVersion, Remote=$remoteVersion")

        if (!isNewerVersion(remoteVersion, currentVersion)) {
            Log.d(TAG, "App ist bereits auf dem neuesten Stand.")
            return@withContext null
        }

        // Passendes Asset für TV oder Mobile finden
        val assets = json.getAsJsonArray("assets") ?: return@withContext null
        var matchedUrl: String? = null
        var matchedName: String? = null
        var matchedSize: Long = 0L

        for (elem in assets) {
            val assetObj = elem.asJsonObject
            val name = assetObj.get("name")?.asString ?: continue
            val downloadUrl = assetObj.get("browser_download_url")?.asString ?: continue
            val size = assetObj.get("size")?.asLong ?: 0L

            if (!name.endsWith(".apk", ignoreCase = true)) continue

            val isTvAsset = name.contains("TV", ignoreCase = true)
            val isMobileAsset = name.contains("Mobile", ignoreCase = true)

            if (isTvApp && isTvAsset) {
                matchedUrl = downloadUrl
                matchedName = name
                matchedSize = size
                break
            } else if (!isTvApp && isMobileAsset) {
                matchedUrl = downloadUrl
                matchedName = name
                matchedSize = size
                break
            } else if (matchedUrl == null) {
                // Fallback: erste gefundene APK
                matchedUrl = downloadUrl
                matchedName = name
                matchedSize = size
            }
        }

        if (matchedUrl == null) {
            Log.w(TAG, "Keine passende APK-Datei im Release $tagName gefunden.")
            return@withContext null
        }

        AppRelease(
            tagName = tagName,
            versionName = remoteVersion,
            title = title,
            changelog = changelog,
            downloadUrl = matchedUrl,
            assetName = matchedName ?: "update.apk",
            fileSize = matchedSize
        )
    }

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

    /**
     * Lädt die APK mit Fortschrittsanzeige herunter und ruft nach Fertigstellung den Installer auf.
     */
    suspend fun downloadAndInstall(
        context: Context,
        release: AppRelease,
        onProgress: (Float) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val destFile = File(updatesDir, release.assetName)

            // Falls alte Datei existiert, entfernen
            if (destFile.exists()) {
                destFile.delete()
            }

            val request = Request.Builder()
                .url(release.downloadUrl)
                .header("User-Agent", "AniWorld-App-Updater")
                .build()

            val call = HttpClient.okHttp.newCall(request)
            val response = call.execute()
            if (!response.isSuccessful) {
                withContext(Dispatchers.Main) {
                    onError("Download fehlgeschlagen (HTTP ${response.code})")
                }
                return@withContext
            }

            val body = response.body ?: run {
                withContext(Dispatchers.Main) {
                    onError("Leere Antwort vom Update-Server.")
                }
                return@withContext
            }

            val totalBytes = if (release.fileSize > 0) release.fileSize else body.contentLength()
            var downloaded = 0L

            body.byteStream().use { input ->
                destFile.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var read: Int
                    var lastReportTime = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (totalBytes > 0 && (now - lastReportTime > 80 || downloaded == totalBytes)) {
                            lastReportTime = now
                            val progress = (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            withContext(Dispatchers.Main) {
                                onProgress(progress)
                            }
                        }
                    }
                    output.flush()
                }
            }

            withContext(Dispatchers.Main) {
                onProgress(1f)
                installApk(context, destFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Download der APK", e)
            withContext(Dispatchers.Main) {
                onError("Download-Fehler: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    /**
     * Startet den Android Package Installer mit der heruntergeladenen Datei.
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            // Android 8.0+: Berechtigungsprüfung für unbekannte Quellen
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Toast.makeText(
                        context,
                        "Bitte erlaube 'Apps aus dieser Quelle installieren' für AniWorld.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Konnte Paket-Installer nicht starten", e)
            Toast.makeText(
                context,
                "Installation konnte nicht gestartet werden: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
