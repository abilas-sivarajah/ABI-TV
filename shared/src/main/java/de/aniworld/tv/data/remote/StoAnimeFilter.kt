package de.aniworld.tv.data.remote

import com.google.gson.Gson
import de.aniworld.tv.data.model.AnimeCard
import de.aniworld.tv.platform.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Welche S.to-Serien Anime sind – damit der Serien-Bereich ohne Anime auskommt (die gibt es im
 * Anime-Reiter von AniWorld). Grundlage ist das S.to-Genre "Anime" (rund 80 Seiten); die Liste
 * wird auf der Platte gespeichert und einmal pro Woche erneuert.
 */
object StoAnimeFilter {

    private const val TAG = "StoAnimeFilter"
    private const val GENRE_PATH = "/genre/anime"
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    private const val MAX_PAGES = 250
    private const val PARALLEL_PAGES = 4

    private data class Cache(val time: Long, val slugs: List<String>)

    private val gson = Gson()
    @Volatile
    private var cacheFile: File? = null

    /** Beim Start aufrufen: Ordner für die gespeicherte Liste (ohne Aufruf wird jedes Mal neu geladen). */
    fun configure(cacheDir: File) {
        cacheFile = File(cacheDir, "sto-anime-slugs.json")
    }

    private val _slugs = MutableStateFlow<Set<String>>(emptySet())
    /** Kurzname (Slug) aller S.to-Serien mit Genre "Anime". */
    val slugs: StateFlow<Set<String>> = _slugs

    /** Beim Start einmal aufrufen: gespeicherte Liste sofort nutzen, bei Bedarf neu laden. */
    suspend fun load() = withContext(Dispatchers.IO) {
        val file = cacheFile
        val cached = runCatching {
            if (file != null && file.isFile) gson.fromJson(file.readText(Charsets.UTF_8), Cache::class.java) else null
        }.getOrNull()
        val cachedSlugs = cached?.slugs.orEmpty().toSet()
        if (cachedSlugs.isNotEmpty()) _slugs.value = cachedSlugs
        if (cached != null && cachedSlugs.isNotEmpty() && System.currentTimeMillis() - cached.time < MAX_AGE_MS) return@withContext

        val (fresh, complete) = crawl(cachedSlugs)
        if (complete && fresh.isNotEmpty()) {
            _slugs.value = fresh
            if (file != null) runCatching {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, "${file.name}.tmp")
                tmp.writeText(gson.toJson(Cache(System.currentTimeMillis(), fresh.sorted())), Charsets.UTF_8)
                if (!tmp.renameTo(file)) {
                    file.delete()
                    tmp.renameTo(file)
                }
            }
            AppLog.i(TAG, "${fresh.size} Anime-Serien von S.to geladen")
        } else {
            AppLog.w(TAG, "Anime-Liste von S.to unvollständig (${fresh.size} Titel) – wird beim nächsten Start erneut geladen")
        }
    }

    /** Alle Seiten des Genres laden, bis keine neuen Serien mehr kommen (S.to wiederholt danach die letzte Seite). */
    private suspend fun crawl(known: Set<String>): Pair<Set<String>, Boolean> = coroutineScope {
        val found = HashSet<String>()
        var page = 1
        while (page <= MAX_PAGES) {
            val batch = (page until page + PARALLEL_PAGES).map { p ->
                async { runCatching { SerienStreamScraper.fetchCategory(GENRE_PATH, p) } }
            }.awaitAll()
            if (batch.any { it.isFailure }) {
                // Teilergebnis schon nutzen, aber nicht als vollständig speichern
                _slugs.value = known + found
                return@coroutineScope found to false
            }
            val before = found.size
            batch.forEach { result -> result.getOrNull().orEmpty().forEach { found += SerienStreamScraper.toCleanSlug(it.slug) } }
            // Schon während des Ladens filtern
            _slugs.value = known + found
            if (found.size == before) return@coroutineScope found to true
            page += PARALLEL_PAGES
        }
        found to true
    }

    /** Ist diese Karte eine Anime-Serie von S.to? */
    fun isStoAnime(card: AnimeCard, animeSlugs: Set<String> = _slugs.value): Boolean =
        animeSlugs.isNotEmpty() && card.isSto && SerienStreamScraper.toCleanSlug(card.slug) in animeSlugs
}

/** Liste ohne S.to-Anime (alle anderen Titel bleiben unverändert). */
fun List<AnimeCard>.withoutStoAnime(animeSlugs: Set<String>): List<AnimeCard> =
    if (animeSlugs.isEmpty()) this else filterNot { StoAnimeFilter.isStoAnime(it, animeSlugs) }
