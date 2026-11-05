package de.aniworld.tv.data.remote

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import de.aniworld.tv.data.model.AnimeCard
import de.aniworld.tv.platform.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Letterboxd-Listen (Watchlists von Benutzern, beliebte Listen) und ihr Abgleich mit Filmpalast,
 * damit in der App nur Filme erscheinen, die sich hier auch streamen lassen.
 *
 * Letterboxd hat keine offene API; gelesen werden die öffentlichen Seiten. Watchlists müssen
 * öffentlich sein (bei Letterboxd Standard).
 */
object LetterboxdLists {

    /** Ein Film auf Letterboxd ("Black Bag (2025)"). */
    data class Film(val name: String, val year: Int?, val slug: String)

    /** Eine Liste, z. B. "feeling lost in your 20's" von adaydreaming. */
    data class ListInfo(val name: String, val owner: String, val path: String)

    class UserNotFoundException(user: String) : IOException("Letterboxd-Benutzer \"$user\" nicht gefunden")

    private const val TAG = "LetterboxdLists"
    private const val BASE = "https://letterboxd.com"
    private const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"
    private const val MATCH_RETRY_MS = 3L * 24 * 60 * 60 * 1000
    private val nameWithYear = Regex("""^(.*)\s\((\d{4})\)$""")
    private val yearRegex = Regex("""(19|20)\d{2}""")

    // ------------------- Letterboxd lesen -------------------

    private fun fetchHtml(path: String): Pair<Int, String> {
        val request = Request.Builder()
            .url(if (path.startsWith("http")) path else "$BASE$path")
            .header("User-Agent", BROWSER_UA)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "de-DE,de;q=0.9,en;q=0.8")
            .build()
        HttpClient.okHttp.newCall(request).execute().use { response ->
            return response.code to response.body?.string().orEmpty()
        }
    }

    private fun parseFilms(html: String): List<Film> =
        Jsoup.parse(html, BASE).select("[data-item-name][data-item-slug]").mapNotNull { el ->
            val raw = el.attr("data-item-name").trim()
            val slug = el.attr("data-item-slug").trim()
            if (raw.isBlank() || slug.isBlank()) return@mapNotNull null
            val match = nameWithYear.find(raw)
            Film(
                name = match?.groupValues?.get(1)?.trim() ?: raw,
                year = match?.groupValues?.get(2)?.toIntOrNull(),
                slug = slug
            )
        }.distinctBy { it.slug }

    /** Watchlist eines Benutzers (die ersten [maxPages] Seiten à 28 Filme). */
    suspend fun watchlist(user: String, maxPages: Int = 2): List<Film> = withContext(Dispatchers.IO) {
        val name = user.trim().trimStart('@').lowercase()
        val films = mutableListOf<Film>()
        for (page in 1..maxPages) {
            val path = if (page == 1) "/$name/watchlist/" else "/$name/watchlist/page/$page/"
            val (code, html) = fetchHtml(path)
            if (code == 404 && page == 1) throw UserNotFoundException(name)
            if (code != 200) {
                if (page == 1) throw IOException("Letterboxd antwortet mit HTTP $code")
                break
            }
            val pageFilms = parseFilms(html)
            if (pageFilms.isEmpty()) break
            films += pageFilms
        }
        films.distinctBy { it.slug }
    }

    /** Beliebte Listen auf Letterboxd. */
    suspend fun popularLists(limit: Int = 6): List<ListInfo> = withContext(Dispatchers.IO) {
        val (code, html) = fetchHtml("/lists/popular/")
        if (code != 200) throw IOException("Letterboxd antwortet mit HTTP $code")
        Jsoup.parse(html, BASE)
            .select("a[href]:not(.poster-list-link)")
            .mapNotNull { a ->
                val href = a.attr("href")
                val parts = href.trim('/').split('/')
                if (parts.size != 3 || parts[1] != "list") return@mapNotNull null
                val name = a.text().trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ListInfo(name = name, owner = parts[0], path = "/${parts[0]}/list/${parts[2]}/")
            }
            .distinctBy { it.path }
            .take(limit)
    }

    /** Filme einer Liste (die ersten [maxPages] Seiten). */
    suspend fun listFilms(path: String, maxPages: Int = 1): List<Film> = withContext(Dispatchers.IO) {
        val films = mutableListOf<Film>()
        for (page in 1..maxPages) {
            val pagePath = if (page == 1) path else "${path.trimEnd('/')}/page/$page/"
            val (code, html) = fetchHtml(pagePath)
            if (code != 200) {
                if (page == 1) throw IOException("Letterboxd antwortet mit HTTP $code")
                break
            }
            val pageFilms = parseFilms(html)
            if (pageFilms.isEmpty()) break
            films += pageFilms
        }
        films.distinctBy { it.slug }
    }

    // ------------------- Abgleich mit Filmpalast -------------------

    private data class MatchEntry(val card: AnimeCard?, val time: Long)

    @Volatile
    private var matchFile: File? = null
    private val gson = Gson()
    private val matchCache: ConcurrentHashMap<String, MatchEntry> by lazy {
        val loaded = runCatching {
            val file = matchFile
            if (file != null && file.isFile) {
                gson.fromJson<Map<String, MatchEntry>>(file.readText(Charsets.UTF_8), object : TypeToken<Map<String, MatchEntry>>() {}.type)
            } else null
        }.getOrNull().orEmpty()
        ConcurrentHashMap(loaded)
    }
    private val searchSlots = Semaphore(4)

    /** Beim Start aufrufen, damit Abgleiche auf der Platte gespeichert werden. */
    fun configure(cacheDir: File) {
        matchFile = File(cacheDir, "letterboxd-matches.json")
    }

    /** Den Film auf Filmpalast finden (null = dort nicht vorhanden). Ergebnisse werden zwischengespeichert. */
    suspend fun findOnFilmpalast(film: Film): AnimeCard? {
        matchCache[film.slug]?.let { entry ->
            if (entry.card != null || System.currentTimeMillis() - entry.time < MATCH_RETRY_MS) return entry.card
        }
        val result = runCatching { searchSlots.withPermit { match(film) } }
            .onFailure { AppLog.w(TAG, "Abgleich von \"${film.name}\" fehlgeschlagen: ${it.message}") }
            .getOrElse { return null } // Netzwerkfehler nicht als "nicht vorhanden" merken
        matchCache[film.slug] = MatchEntry(result, System.currentTimeMillis())
        saveMatches()
        return result
    }

    private suspend fun match(film: Film): AnimeCard? {
        // Filmpalast führt meist den deutschen Titel; den liefert TMDB
        val germanTitle = TmdbArtwork.germanMovieTitle(film.name, film.year)
        val queries = listOfNotNull(germanTitle, film.name).distinctBy { normalize(it) }
        for (query in queries) {
            val results = FilmpalastScraper.searchMovies(query)
                .filterNot { it.title.contains("*ENGLISH*", ignoreCase = true) }
            pickBest(results, query, film.year)?.let { return it }
        }
        return null
    }

    /** Exakter Titel (und passendes Jahr, falls bekannt); sonst nur ein eindeutiger Treffer mit Untertitel. */
    private fun pickBest(results: List<AnimeCard>, query: String, year: Int?): AnimeCard? {
        if (results.isEmpty()) return null
        val wanted = normalize(query)
        fun yearOk(card: AnimeCard): Boolean {
            val cardYear = yearRegex.find(card.genreOrSubtitle)?.value?.toIntOrNull() ?: return true
            return year == null || kotlin.math.abs(cardYear - year) <= 1
        }
        results.firstOrNull { normalize(it.title) == wanted && yearOk(it) }?.let { return it }
        val prefixed = results.filter { card ->
            val title = card.title.lowercase()
            listOf(" - ", ": ", " – ").any { sep -> title.startsWith(query.lowercase() + sep) } && yearOk(card)
        }
        return prefixed.singleOrNull()
    }

    private fun normalize(text: String): String =
        text.lowercase().replace("&", "und").filter { it.isLetterOrDigit() }

    private fun saveMatches() = runCatching {
        val file = matchFile ?: return@runCatching
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(gson.toJson(HashMap(matchCache)), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
