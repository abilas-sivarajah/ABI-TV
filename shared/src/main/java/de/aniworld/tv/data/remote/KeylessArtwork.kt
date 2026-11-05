package de.aniworld.tv.data.remote

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import de.aniworld.tv.platform.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Hintergrundbilder fürs Titelbild ohne API-Schlüssel (Alternative zu TMDB):
 * - Serien: TVmaze (Bilder unter CC BY-SA)
 * - Anime: Kitsu, notfalls AniList (breite Banner)
 * - Filme: Wikidata (deutscher Titel + Jahr → IMDb-Nummer) → Letterboxd-Filmseite (Bild + Bewertung)
 * Treffer werden 30 Tage, Nicht-Treffer 3 Tage zwischengespeichert.
 */
object KeylessArtwork {

    enum class Kind { MOVIE, TV, ANIME }

    private const val TAG = "KeylessArtwork"
    private const val HIT_REFRESH_MS = 30L * 24 * 60 * 60 * 1000
    private const val MISS_RETRY_MS = 3L * 24 * 60 * 60 * 1000
    /** Wikidata und Co. wünschen einen aussagekräftigen Programmnamen (keine Kontaktdaten nötig). */
    private const val USER_AGENT = "ABI-TV/1.0 (private desktop app)"

    private data class Entry(val backdrop: String?, val logo: String?, val imdb: String?, val time: Long)

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

    fun configure(cacheDir: File) {
        cacheFile = File(cacheDir, "keyless-artwork.json")
    }

    suspend fun find(title: String, year: Int?, kind: Kind): TmdbArtwork.Artwork? {
        if (title.isBlank()) return null
        val key = "$kind|${normalize(title)}|${year ?: ""}"
        cache[key]?.let { entry ->
            val age = System.currentTimeMillis() - entry.time
            val isMiss = entry.backdrop == null && entry.imdb == null
            if (!isMiss && age < HIT_REFRESH_MS) return entry.toArtwork(kind)
            if (isMiss && age < MISS_RETRY_MS) return null
        }
        return withContext(Dispatchers.IO) {
            val found = runCatching {
                when (kind) {
                    Kind.TV -> tvmaze(title, year)
                    // Fällt Kitsu aus, trotzdem AniList versuchen
                    Kind.ANIME -> runCatching { kitsu(title, year) }.getOrNull() ?: anilist(title)
                    Kind.MOVIE -> movie(title, year)
                }
            }.onFailure { AppLog.w(TAG, "Suche nach \"$title\" fehlgeschlagen: ${it.message}") }
                // Netzwerkfehler nicht als "nicht gefunden" merken
                .getOrElse { return@withContext null }
            val entry = Entry(found?.backdropUrl, found?.logoUrl, found?.imdbId, System.currentTimeMillis())
            cache[key] = entry
            save()
            if (found != null) AppLog.i(TAG, "\"$title\" ($kind): Hintergrund ${found.backdropUrl != null}, IMDb ${found.imdbId}")
            found
        }
    }

    private fun Entry.toArtwork(kind: Kind) = TmdbArtwork.Artwork(
        backdropUrl = backdrop,
        logoUrl = logo,
        kind = if (kind == Kind.MOVIE) TmdbArtwork.Kind.MOVIE else TmdbArtwork.Kind.TV,
        imdbId = imdb
    )

    // ------------------- Serien: TVmaze -------------------

    private fun tvmaze(title: String, year: Int?): TmdbArtwork.Artwork? {
        val results = getJson("https://api.tvmaze.com/search/shows", mapOf("q" to title)).asArraySafe()
            .mapNotNull { it.objOrNull()?.get("show")?.objOrNull() }
        val wanted = normalize(title)
        fun yearOf(show: JsonObject) = show.string("premiered")?.take(4)?.toIntOrNull()
        val show = results.firstOrNull { normalize(it.string("name").orEmpty()) == wanted && (year == null || yearOf(it) == null || kotlin.math.abs(yearOf(it)!! - year) <= 1) }
            ?: results.firstOrNull { normalize(it.string("name").orEmpty()) == wanted }
            // S.to führt viele Serien unter dem deutschen Titel ("Dexter: Wiedererwachen"), TVmaze unter dem
            // englischen: dann über Wikidata die IMDb-Nummer suchen und damit bei TVmaze nachschlagen
            ?: wikidataImdb(title, year, film = false)?.let { imdb ->
                runCatching { getJson("https://api.tvmaze.com/lookup/shows", mapOf("imdb" to imdb)).objOrNull() }.getOrNull()
            }
            ?: return null
        val id = show.get("id")?.asInt ?: return null
        val images = getJson("https://api.tvmaze.com/shows/$id/images", emptyMap()).asArraySafe().mapNotNull { it.objOrNull() }
        fun original(img: JsonObject) = img.get("resolutions")?.objOrNull()?.get("original")?.objOrNull()
        val background = images.filter { it.string("type") == "background" }
            .maxByOrNull { (original(it)?.get("width")?.asInt ?: 0).coerceAtMost(3840) }
        val logo = images.firstOrNull { it.string("type") == "typography" }
        val backdropUrl = background?.let { original(it)?.string("url") }
        val imdb = show.get("externals")?.objOrNull()?.string("imdb")
        if (backdropUrl == null && imdb == null) return null
        return TmdbArtwork.Artwork(backdropUrl, logo?.let { original(it)?.string("url") }, kind = TmdbArtwork.Kind.TV, imdbId = imdb)
    }

    // ------------------- Anime: Kitsu, AniList -------------------

    private fun kitsu(title: String, year: Int?): TmdbArtwork.Artwork? {
        val data = getJson("https://kitsu.io/api/edge/anime", mapOf("filter[text]" to title, "page[limit]" to "5"), accept = "application/vnd.api+json")
            .objOrNull()?.get("data").asArraySafe().mapNotNull { it.objOrNull()?.get("attributes")?.objOrNull() }
        val wanted = normalize(title)
        fun names(a: JsonObject) = buildList {
            a.string("canonicalTitle")?.let { add(it) }
            a.get("titles")?.objOrNull()?.entrySet()?.forEach { (_, v) -> v.takeIf { it.isJsonPrimitive }?.asString?.let { add(it) } }
        }.map { normalize(it) }
        val match = data.firstOrNull { wanted in names(it) && (year == null || it.string("startDate")?.take(4)?.toIntOrNull()?.let { y -> kotlin.math.abs(y - year) <= 1 } != false) }
            ?: data.firstOrNull { wanted in names(it) }
            ?: return null
        val cover = match.get("coverImage")?.objOrNull()?.string("original") ?: return null
        return TmdbArtwork.Artwork(cover, null, kind = TmdbArtwork.Kind.TV)
    }

    private fun anilist(title: String): TmdbArtwork.Artwork? {
        val query = """{"query":"query(${'$'}s:String){Media(search:${'$'}s,type:ANIME){bannerImage}}","variables":{"s":${gson.toJson(title)}}}"""
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .post(query.toRequestBody("application/json".toMediaType()))
            .build()
        val json = HttpClient.okHttp.newCall(request).execute().use { JsonParser.parseString(it.body?.string().orEmpty()) }
        val banner = json.objOrNull()?.get("data")?.objOrNull()?.get("Media")?.objOrNull()?.string("bannerImage") ?: return null
        return TmdbArtwork.Artwork(banner, null, kind = TmdbArtwork.Kind.TV)
    }

    // ------------------- Filme: Wikidata → IMDb → Letterboxd -------------------

    private suspend fun movie(title: String, year: Int?): TmdbArtwork.Artwork? {
        // Filmpalast-Titel haben oft einen Zusatz ("Riddick - Überleben ist seine Rache"): notfalls ohne ihn suchen
        val candidates = listOf(title, title.split(" - ", " – ", ": ").first()).map { it.trim() }.filter { it.length >= 2 }.distinct()
        val imdb = candidates.firstNotNullOfOrNull { wikidataImdb(it, year, film = true) } ?: return null
        val letterboxd = LetterboxdRatings.byImdb(imdb)
        return TmdbArtwork.Artwork(letterboxd?.backdropUrl, null, kind = TmdbArtwork.Kind.MOVIE, imdbId = imdb)
    }

    /** IMDb-Nummer eines Films bzw. einer Serie über Wikidata (deutsche Bezeichnungen, Jahr ±1). */
    private fun wikidataImdb(title: String, year: Int?, film: Boolean): String? {
        val ids = getJson(
            "https://www.wikidata.org/w/api.php",
            mapOf("action" to "wbsearchentities", "format" to "json", "language" to "de", "uselang" to "de",
                "type" to "item", "limit" to "7", "search" to title)
        ).objOrNull()?.get("search").asArraySafe().mapNotNull { it.objOrNull()?.string("id") }
        if (ids.isEmpty()) return null
        val entities = getJson(
            "https://www.wikidata.org/w/api.php",
            mapOf("action" to "wbgetentities", "format" to "json", "props" to "claims", "ids" to ids.joinToString("|"))
        ).objOrNull()?.get("entities")?.objOrNull() ?: return null
        // In Suchreihenfolge: erster Eintrag mit IMDb-Filmnummer (tt…) und passendem Jahr
        return ids.firstNotNullOfOrNull { id ->
            val claims = entities.get(id)?.objOrNull()?.get("claims")?.objOrNull() ?: return@firstNotNullOfOrNull null
            val imdb = claims.get("P345").asArraySafe().firstNotNullOfOrNull {
                it.objOrNull()?.get("mainsnak")?.objOrNull()?.get("datavalue")?.objOrNull()?.string("value")
            }?.takeIf { it.startsWith("tt") } ?: return@firstNotNullOfOrNull null
            // Filme: Veröffentlichung (P577); Serien: Start (P580) bzw. Veröffentlichung
            val released = (claims.get(if (film) "P577" else "P580").asArraySafe() + claims.get("P577").asArraySafe()).mapNotNull {
                it.objOrNull()?.get("mainsnak")?.objOrNull()?.get("datavalue")?.objOrNull()
                    ?.get("value")?.objOrNull()?.string("time")?.removePrefix("+")?.take(4)?.toIntOrNull()
            }
            if (year != null && released.isNotEmpty() && released.none { kotlin.math.abs(it - year) <= 1 }) null else imdb
        }
    }

    // ------------------- Helfer -------------------

    private fun getJson(url: String, params: Map<String, String>, accept: String = "application/json"): JsonElement {
        val full = url.toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val request = Request.Builder().url(full).header("User-Agent", USER_AGENT).header("Accept", accept).build()
        HttpClient.okHttp.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            return JsonParser.parseString(response.body?.string().orEmpty())
        }
    }

    private fun JsonElement?.asArraySafe(): JsonArray = this?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
    private fun JsonElement.objOrNull(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    private fun normalize(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

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
