package de.aniworld.tv.data.remote

import de.aniworld.tv.data.model.AnimeCard
import de.aniworld.tv.data.model.AnimeDetail
import de.aniworld.tv.data.model.CatalogSection
import de.aniworld.tv.data.model.EpisodeItem
import de.aniworld.tv.data.model.HosterLink
import de.aniworld.tv.data.model.LanguageOption
import de.aniworld.tv.data.model.SeasonLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Vollständiger Scraper für https://filmpalast.to
 * Liefert deutsche Spielfilme, Kinofilme und Klassiker in HD mit direkten
 * VOE-, Streamtape- und Web-Stream-Links.
 */
object FilmpalastScraper {

    const val BASE_URL = "https://filmpalast.to"
    const val PREFIX = "filmpalast:"

    private val detailCache = ConcurrentHashMap<String, AnimeDetail>()
    private val categoryCache = ConcurrentHashMap<String, List<AnimeCard>>()

    val CATEGORIES: List<Pair<String, String>> = listOf(
        "/movies/top" to "🔥 Top-Filme",
        "/movies/new" to "✨ Neu hinzugefügt",
        "/search/genre/Action" to "💥 Action",
        "/search/genre/Krimi" to "🔍 Krimi & Thriller",
        "/search/genre/Sci-Fi" to "🚀 Sci-Fi",
        "/search/genre/Komödie" to "😂 Komödie",
        "/search/genre/Abenteuer" to "🗺️ Abenteuer",
        "/search/genre/Horror" to "👻 Horror",
        "/search/genre/Fantasy" to "🧙 Fantasy",
        "/search/genre/Animation" to "🎨 Animation",
        "/search/genre/Drama" to "🎭 Drama",
        "/search/genre/Familie" to "👨‍👩‍👧 Familie"
    )

    fun isFilmpalastSlug(slug: String): Boolean =
        slug.startsWith(PREFIX) || slug.contains("filmpalast.to") || slug.startsWith("/stream/")

    private fun normalizePath(raw: String): String {
        var clean = raw
            .removePrefix(PREFIX)
            .removePrefix(BASE_URL)
            .removePrefix("//filmpalast.to")
            .removePrefix("filmpalast_stream:")
            .trim()
        if (!clean.startsWith("/")) clean = "/$clean"
        return clean
    }

    private fun buildPageUrl(basePath: String, page: Int): String {
        val clean = normalizePath(basePath)
        if (page <= 1) return "$BASE_URL$clean"
        return if (clean.startsWith("/search/")) {
            "$BASE_URL$clean/$page"
        } else {
            "$BASE_URL$clean/page/$page"
        }
    }

    private suspend fun fetchDoc(url: String): Document = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", HttpClient.USER_AGENT)
            .header("Referer", "$BASE_URL/")
            .header("Accept-Language", "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7")
            .build()
        val html = HttpClient.okHttp.newCall(req).execute().use { res ->
            res.body?.string().orEmpty()
        }
        Jsoup.parse(html, BASE_URL)
    }

    private fun parseArticle(article: Element): AnimeCard? {
        val a = article.selectFirst("a[href*=/stream/]") ?: return null
        val rawHref = a.attr("href").trim()
        val href = normalizePath(rawHref)
        var title = a.attr("title").ifBlank { a.text().trim() }
        if (title.isBlank()) {
            title = article.selectFirst("h2")?.text()?.trim().orEmpty()
        }
        if (title.isBlank() || href.isBlank()) return null

        val imgEl = article.selectFirst("img")
        var cover = imgEl?.attr("src")?.trim().orEmpty()
        if (cover.startsWith("//")) cover = "https:$cover"
        else if (cover.startsWith("/")) cover = "$BASE_URL$cover"

        val imdbText = article.selectFirst("span:contains(Imdb:)")?.text()
            ?.substringAfter("Imdb:")?.trim().orEmpty()

        val yearText = article.selectFirst("span.coverDetails:contains(Jahr:)")?.text()?.trim().orEmpty()

        val subtitle = when {
            yearText.isNotBlank() -> yearText
            imdbText.isNotBlank() -> "IMDb: $imdbText"
            else -> ""
        }

        return AnimeCard(
            slug = "$PREFIX$href",
            title = title,
            coverUrl = cover,
            genreOrSubtitle = subtitle,
            rating = imdbText
        )
    }

    suspend fun fetchHomeSections(): List<CatalogSection> = coroutineScope {
        val topDeferred = async { fetchCategory("/movies/top", page = 1) }
        val newDeferred = async { fetchCategory("/movies/new", page = 1) }
        val actionDeferred = async { fetchCategory("/search/genre/Action", page = 1) }
        val thrillerDeferred = async { fetchCategory("/search/genre/Krimi", page = 1) }
        val comedyDeferred = async { fetchCategory("/search/genre/Komödie", page = 1) }

        val top = topDeferred.await()
        val newMovies = newDeferred.await()
        val action = actionDeferred.await()
        val thriller = thrillerDeferred.await()
        val comedy = comedyDeferred.await()

        val sections = mutableListOf<CatalogSection>()
        if (top.isNotEmpty()) {
            sections.add(CatalogSection("filmpalast_top", "🔥 Beliebte Top-Filme auf Filmpalast", top))
        }
        if (newMovies.isNotEmpty()) {
            sections.add(CatalogSection("filmpalast_new", "✨ Neu hinzugefügte Filme", newMovies))
        }
        if (action.isNotEmpty()) {
            sections.add(CatalogSection("filmpalast_action", "💥 Action & Abenteuer Highlights", action))
        }
        if (thriller.isNotEmpty()) {
            sections.add(CatalogSection("filmpalast_thriller", "🔍 Krimi, Thriller & Spannung", thriller))
        }
        if (comedy.isNotEmpty()) {
            sections.add(CatalogSection("filmpalast_comedy", "😂 Beliebte Komödien", comedy))
        }
        sections
    }

    suspend fun fetchCategory(categoryPath: String, page: Int = 1): List<AnimeCard> {
        val cacheKey = "${normalizePath(categoryPath)}_page_$page"
        if (page == 1) {
            categoryCache[cacheKey]?.let { return it }
        }

        val url = buildPageUrl(categoryPath, page)
        val doc = runCatching { fetchDoc(url) }.getOrNull() ?: return emptyList()

        val items = doc.select("article").mapNotNull { parseArticle(it) }
        if (items.isNotEmpty()) {
            categoryCache[cacheKey] = items
        }
        return items
    }

    suspend fun fetchDetail(slug: String): AnimeDetail {
        val path = normalizePath(slug)
        detailCache[path]?.let { return it }

        val url = "$BASE_URL$path"
        val doc = fetchDoc(url)

        var title = doc.selectFirst("em[itemprop=name]")?.text()?.trim().orEmpty()
        if (title.isBlank()) {
            title = doc.selectFirst("h2.header-single")?.text()?.trim().orEmpty()
        }
        if (title.isBlank()) {
            val rawTitle = doc.title().trim()
            title = rawTitle.substringBefore("Stream").substringBefore("kostenlos").trim()
        }

        var cover = doc.selectFirst("img.cover2")?.attr("src")?.trim().orEmpty()
        if (cover.isBlank()) {
            cover = doc.selectFirst("img[itemprop=image]")?.attr("src")?.trim().orEmpty()
        }
        if (cover.isBlank()) {
            cover = doc.selectFirst("img[src*=/files/movies/]")?.attr("src")?.trim().orEmpty()
        }
        if (cover.startsWith("//")) cover = "https:$cover"
        else if (cover.startsWith("/")) cover = "$BASE_URL$cover"

        var desc = ""
        val descEl = doc.selectFirst("p:has(em[itemprop=name]) + p")
            ?: doc.selectFirst("div.text-justify")
            ?: doc.selectFirst("div.description")
        if (descEl != null) {
            desc = descEl.text().trim()
        }

        val yearText = Regex("""(\d{4})""").find(
            doc.selectFirst("span:contains(Jahr:)")?.text().orEmpty()
        )?.value.orEmpty()

        val imdbRating = doc.selectFirst("span:contains(Imdb:)")?.text()
            ?.substringAfter("Imdb:")?.trim().orEmpty()

        val genres = doc.select("a[href*=/search/genre/]")
            .map { it.text().trim() }
            .filter { it.isNotBlank() && !it.equals("Englisch", ignoreCase = true) }
            .distinct()

        val dummySeason = SeasonLink(
            label = "Film",
            path = "filmpalast_stream:$path",
            isMovies = true
        )

        val dummyEpisode = EpisodeItem(
            number = 1,
            seasonLabel = "Film",
            path = "filmpalast_stream:$path",
            titleDe = title.ifBlank { "Hauptfilm" },
            titleEn = "",
            languages = listOf(LanguageOption.GER_DUB)
        )

        val detail = AnimeDetail(
            slug = "$PREFIX$path",
            title = title,
            description = desc,
            coverUrl = cover,
            backdropUrl = cover,
            years = yearText,
            ageRating = "",
            rating = imdbRating,
            genres = genres,
            seasons = listOf(dummySeason),
            currentSeasonPath = dummySeason.path,
            episodes = listOf(dummyEpisode)
        )

        detailCache[path] = detail
        return detail
    }

    suspend fun fetchHostersForFilm(streamPath: String): List<HosterLink> {
        val path = normalizePath(streamPath)
        val url = "$BASE_URL$path"
        val doc = runCatching { fetchDoc(url) }.getOrNull() ?: return emptyList()

        val hosterLinks = mutableListOf<HosterLink>()

        for (ul in doc.select("ul.currentStreamLinks")) {
            val hostName = ul.selectFirst(".hostName")?.text()?.trim() ?: "Stream"
            val playA = ul.selectFirst("a.iconPlay") ?: ul.selectFirst("a[href^=http]") ?: continue
            val href = playA.attr("href").trim()
            if (href.startsWith("http")) {
                val cleanName = when {
                    hostName.contains("VOE", ignoreCase = true) || href.contains("voe.sx") -> "VOE"
                    hostName.contains("VIDARA", ignoreCase = true) || href.contains("vidara") -> "Vidara"
                    hostName.contains("Streamtape", ignoreCase = true) || href.contains("streamtape") -> "Streamtape"
                    hostName.contains("Firestream", ignoreCase = true) || href.contains("firestream") -> "Firestream"
                    hostName.contains("Vixeo", ignoreCase = true) || href.contains("vixeo") -> "Vixeo"
                    else -> hostName.replace("HD", "").trim()
                }
                hosterLinks.add(
                    HosterLink(
                        hosterName = cleanName,
                        langKey = LanguageOption.GER_DUB.langKey,
                        language = LanguageOption.GER_DUB,
                        redirectPath = href
                    )
                )
            }
        }

        // VOE an die erste Stelle setzen
        hosterLinks.sortBy { if (it.hosterName == "VOE") 0 else 1 }
        return hosterLinks
    }

    suspend fun searchMovies(query: String): List<AnimeCard> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        val encoded = runCatching { URLEncoder.encode(trimmed, "UTF-8") }.getOrDefault(trimmed)
        val url = "$BASE_URL/search/title/$encoded"
        val doc = runCatching { fetchDoc(url) }.getOrNull() ?: return emptyList()
        return doc.select("article").mapNotNull { parseArticle(it) }
    }
}
