package de.aniworld.tv.data.remote

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import de.aniworld.tv.platform.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Durchschnittsbewertung, Link und Hintergrundbild eines Films auf Letterboxd.
 *
 * Letterboxd hat keine offene API: "letterboxd.com/tmdb/<TMDB-Nummer>" bzw. "letterboxd.com/imdb/<IMDb-Nummer>"
 * leiten auf die Filmseite weiter. Dort stehen die Bewertung im Meta-Tag "twitter:data2" ("3.86 out of 5")
 * und das Hintergrundbild in "data-backdrop2x"/"data-backdrop". Ergebnisse werden auf der Platte
 * zwischengespeichert, damit jeder Film nur selten abgefragt wird.
 */
object LetterboxdRatings {

    /** [average] fehlt, wenn der Film noch zu wenige Bewertungen hat; [backdropUrl], wenn es keins gibt. */
    data class Rating(val average: Double?, val url: String, val backdropUrl: String? = null) {
        /** z. B. "3,9" */
        val shortLabel: String? get() = average?.let { String.format(Locale.GERMANY, "%.1f", it) }
    }

    private const val TAG = "Letterboxd"
    private const val REFRESH_MS = 3L * 24 * 60 * 60 * 1000
    private const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"
    private val ratingMeta = Regex("""<meta\s+name="twitter:data2"\s+content="([0-9.]+) out of 5"""")
    private val backdrop2x = Regex("""data-backdrop2x="([^"]+)"""")
    private val backdrop1x = Regex("""data-backdrop="([^"]+)"""")

    private data class Entry(val average: Double?, val url: String, val backdrop: String?, val time: Long)

    @Volatile
    private var cacheFile: File? = null
    private val gson = Gson()
    private val cache: ConcurrentHashMap<String, Entry> by lazy {
        val loaded = runCatching {
            val file = cacheFile
            if (file != null && file.isFile) {
                gson.fromJson<Map<String, Entry>>(file.readText(Charsets.UTF_8), object : TypeToken<Map<String, Entry>>() {}.type)
            } else null
        }.getOrNull().orEmpty()
        ConcurrentHashMap(loaded)
    }

    /** Beim Start aufrufen; ohne Aufruf wird nur im Speicher zwischengespeichert. */
    fun configure(cacheDir: File) {
        cacheFile = File(cacheDir, "letterboxd-films.json")
    }

    /** Link zur Letterboxd-Seite eines Films (Letterboxd leitet auf die richtige Filmseite weiter). */
    fun filmUrl(tmdbMovieId: Int): String = "https://letterboxd.com/tmdb/$tmdbMovieId/"

    /** Film über die TMDB-Filmnummer; null bei Netzwerkfehler oder unbekanntem Film. */
    suspend fun rating(tmdbMovieId: Int): Rating? = lookup("tmdb:$tmdbMovieId", filmUrl(tmdbMovieId))

    /** Film über die IMDb-Nummer ("tt0111161") – funktioniert ohne TMDB-Schlüssel. */
    suspend fun byImdb(imdbId: String): Rating? = lookup("imdb:$imdbId", "https://letterboxd.com/imdb/$imdbId/")

    private suspend fun lookup(key: String, url: String): Rating? {
        cache[key]?.let { entry ->
            if (System.currentTimeMillis() - entry.time < REFRESH_MS) return Rating(entry.average, entry.url, entry.backdrop)
        }
        return withContext(Dispatchers.IO) {
            val result = runCatching { fetch(url) }
                .onFailure { AppLog.w(TAG, "Letterboxd ($key) nicht abrufbar: ${it.message}") }
                .getOrNull() ?: return@withContext cache[key]?.let { Rating(it.average, it.url, it.backdrop) }
            cache[key] = Entry(result.average, result.url, result.backdropUrl, System.currentTimeMillis())
            save()
            result
        }
    }

    private fun fetch(url: String): Rating? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_UA)
            .header("Accept-Language", "de-DE,de;q=0.9,en;q=0.8")
            .build()
        HttpClient.okHttp.newCall(request).execute().use { response ->
            // 404: Letterboxd kennt den Film nicht
            if (response.code == 404) return null
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val finalUrl = response.request.url.toString()
            val html = response.body?.string().orEmpty()
            val average = ratingMeta.find(html)?.groupValues?.get(1)?.toDoubleOrNull()
            val backdrop = (backdrop2x.find(html) ?: backdrop1x.find(html))?.groupValues?.get(1)?.replace("&amp;", "&")
            return Rating(average, finalUrl, backdrop)
        }
    }

    private fun save() = runCatching {
        val file = cacheFile ?: return@runCatching
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(gson.toJson(HashMap(cache)), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
