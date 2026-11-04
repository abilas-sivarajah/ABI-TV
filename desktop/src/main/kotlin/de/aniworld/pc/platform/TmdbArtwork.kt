package de.aniworld.pc.platform

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import de.aniworld.tv.data.remote.HttpClient
import de.aniworld.tv.platform.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.File
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/**
 * Hochauflösende Bilder von TMDB (themoviedb.org) fürs Titelbild: breites Hintergrundbild und
 * Titel-Logo. Die Streaming-Seiten liefern nur kleine Hochformat-Cover.
 *
 * Der Schlüssel kommt aus der Umgebungsvariable TMDB_API_KEY oder wird beim Bauen aus
 * local.properties bzw. dem GitHub-Secret eingebaut (abi-tmdb.properties). Ohne Schlüssel
 * liefert [find] einfach nichts.
 */
object TmdbArtwork {

    data class Artwork(val backdropUrl: String?, val logoUrl: String?)

    enum class Kind(val path: String) { MOVIE("movie"), TV("tv") }

    private const val TAG = "TMDB"
    private const val API = "https://api.themoviedb.org/3"
    private const val IMAGES = "https://image.tmdb.org/t/p"
    /** Nicht gefundene Titel erst nach einer Woche erneut suchen. */
    private const val MISS_RETRY_MS = 7L * 24 * 60 * 60 * 1000

    private val apiKey: String by lazy {
        System.getenv("TMDB_API_KEY")?.trim()?.takeIf { it.isNotEmpty() }
            ?: runCatching {
                javaClass.classLoader.getResourceAsStream("abi-tmdb.properties")?.use { input ->
                    Properties().apply { load(input) }.getProperty("apiKey")?.trim()
                }
            }.getOrNull()
            ?: ""
    }

    val isConfigured: Boolean get() = apiKey.isNotEmpty()

    // ------------------- Zwischenspeicher auf der Platte -------------------

    private data class Entry(val backdrop: String?, val logo: String?, val time: Long)

    private val gson = Gson()
    private val cacheFile by lazy { File(AppDirs.cacheDir, "tmdb-artwork.json") }
    private val cache: ConcurrentHashMap<String, Entry> by lazy {
        val loaded = runCatching {
            if (cacheFile.isFile) {
                gson.fromJson<Map<String, Entry>>(
                    cacheFile.readText(Charsets.UTF_8),
                    object : TypeToken<Map<String, Entry>>() {}.type
                )
            } else null
        }.getOrNull().orEmpty()
        ConcurrentHashMap(loaded)
    }

    private fun saveCache() = runCatching {
        val tmp = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
        tmp.writeText(gson.toJson(HashMap(cache)), Charsets.UTF_8)
        if (!tmp.renameTo(cacheFile)) {
            cacheFile.delete()
            tmp.renameTo(cacheFile)
        }
    }

    // ------------------- Suche -------------------

    /**
     * Bilder zu einem Titel. [kind] ist der wahrscheinliche Typ (Film/Serie); der andere wird
     * notfalls auch probiert. Liefert null, wenn TMDB nicht eingerichtet ist oder nichts passt.
     */
    suspend fun find(title: String, year: Int?, kind: Kind?): Artwork? {
        if (!isConfigured || title.isBlank()) return null
        val key = "${kind ?: "any"}|${normalize(title)}|${year ?: ""}"
        cache[key]?.let { entry ->
            val isMiss = entry.backdrop == null && entry.logo == null
            if (!isMiss) return Artwork(entry.backdrop, entry.logo)
            if (System.currentTimeMillis() - entry.time < MISS_RETRY_MS) return null
        }
        return withContext(Dispatchers.IO) {
            val result = runCatching { lookup(title, year, kind) }
                .onFailure { AppLog.w(TAG, "Suche nach \"$title\" fehlgeschlagen: ${it.message}") }
                // Netzwerkfehler nicht als "nicht gefunden" merken
                .getOrElse { return@withContext null }
            cache[key] = Entry(result?.backdropUrl, result?.logoUrl, System.currentTimeMillis())
            saveCache()
            result
        }
    }

    private fun lookup(title: String, year: Int?, kind: Kind?): Artwork? {
        val kinds = when (kind) {
            Kind.MOVIE -> listOf(Kind.MOVIE, Kind.TV)
            Kind.TV, null -> listOf(Kind.TV, Kind.MOVIE)
        }
        for (k in kinds) {
            val hit = search(k, title, year) ?: year?.let { search(k, title, null) } ?: continue
            val id = hit.get("id")?.asInt ?: continue
            val images = get("/${k.path}/$id/images", mapOf("include_image_language" to "de,en,null"))

            // Hintergrund ohne Text (Sprache null) bevorzugen, sonst irgendeiner, sonst der aus der Suche
            val backdrops = images.array("backdrops")
            val backdropPath = backdrops.bestPath { it.lang() == null }
                ?: backdrops.bestPath { true }
                ?: hit.string("backdrop_path")
            // Logo am liebsten auf Deutsch, sonst Englisch, sonst irgendeins
            val logos = images.array("logos")
            val logoPath = logos.bestPath { it.lang() == "de" }
                ?: logos.bestPath { it.lang() == "en" }
                ?: logos.bestPath { true }

            if (backdropPath == null && logoPath == null) continue
            AppLog.i(TAG, "\"$title\" → ${k.path}/$id (Hintergrund: ${backdropPath != null}, Logo: ${logoPath != null})")
            return Artwork(
                backdropUrl = backdropPath?.let { "$IMAGES/original$it" },
                logoUrl = logoPath?.let { "$IMAGES/w500$it" }
            )
        }
        return null
    }

    /** Bester Treffer: exakter Titel bevorzugt, sonst der beliebteste der ersten Ergebnisse. */
    private fun search(kind: Kind, title: String, year: Int?): JsonObject? {
        val params = buildMap {
            put("query", title)
            put("language", "de-DE")
            put("include_adult", "false")
            if (year != null) put(if (kind == Kind.MOVIE) "year" else "first_air_date_year", year.toString())
        }
        val results = get("/search/${kind.path}", params).array("results")
            .mapNotNull { it as? JsonObject }
            .take(8)
        if (results.isEmpty()) return null
        val wanted = normalize(title)
        return results.firstOrNull { r ->
            listOf("title", "original_title", "name", "original_name").any { normalize(r.string(it).orEmpty()) == wanted }
        } ?: results.first()
    }

    private fun get(path: String, params: Map<String, String>): JsonObject {
        val url = "$API$path".toHttpUrl().newBuilder().apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
            // v3-Schlüssel als Parameter, v4-Lesetoken (beginnt mit "eyJ") als Header
            if (!apiKey.startsWith("eyJ")) addQueryParameter("api_key", apiKey)
        }.build()
        val request = Request.Builder().url(url).apply {
            if (apiKey.startsWith("eyJ")) header("Authorization", "Bearer $apiKey")
            header("Accept", "application/json")
        }.build()
        HttpClient.okHttp.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            return JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
        }
    }

    // ------------------- JSON-Helfer -------------------

    private fun JsonObject.array(name: String): JsonArray =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()

    private fun JsonObject.string(name: String): String? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    private fun JsonObject.lang(): String? = string("iso_639_1")

    private fun JsonArray.bestPath(filter: (JsonObject) -> Boolean): String? =
        mapNotNull { it as? JsonObject }
            .filter(filter)
            .maxWithOrNull(compareBy<JsonObject>({ it.get("vote_average")?.asDouble ?: 0.0 }, { it.get("width")?.asInt ?: 0 }))
            ?.string("file_path")

    private fun normalize(text: String): String =
        text.lowercase().filter { it.isLetterOrDigit() }
}
