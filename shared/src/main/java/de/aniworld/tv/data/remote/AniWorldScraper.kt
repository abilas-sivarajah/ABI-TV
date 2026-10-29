package de.aniworld.tv.data.remote

import com.google.gson.JsonParser
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
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.ConcurrentHashMap

object AniWorldScraper {

    private val detailCache = ConcurrentHashMap<String, AnimeDetail>()
    private val coverCache = ConcurrentHashMap<String, String>()

    val GENRE_CATEGORIES: List<Pair<String, String>> = listOf(
        "beliebt" to "🔥 Beliebte Animes",
        "neu" to "✨ Neue Animes",
        "action" to "⚔️ Action",
        "abenteuer" to "🗺️ Abenteuer",
        "fantasy" to "🪄 Fantasy",
        "romanze" to "❤️ Romanze",
        "komoedie" to "😂 Komödie",
        "drama" to "🎭 Drama",
        "mystery" to "🕵️ Mystery",
        "scifi" to "🚀 Sci-Fi",
        "isekai" to "🌌 Isekai / Andere Welt",
        "fighting-shounen" to "🥊 Fighting-Shounen",
        "thriller" to "🔪 Thriller",
        "horror" to "👻 Horror",
        "sport" to "⚽ Sport"
    )

    suspend fun fetchHomeCatalog(): List<CatalogSection> = withContext(Dispatchers.IO) {
        val (_, html) = HttpClient.getHtml(HttpClient.BASE_URL)
        val doc = Jsoup.parse(html, HttpClient.BASE_URL)
        val sections = mutableListOf<CatalogSection>()

        // 1. Carousels auf der Startseite ("Beliebt bei AniWorld", "Neue Animes", "Derzeit beliebt")
        doc.select(".carousel").forEachIndexed { idx, carousel ->
            val heading = carousel.selectFirst("h2")?.text()?.trim().orEmpty()
            val cards = parseCoverListItems(carousel)
            if (heading.isNotBlank() && cards.isNotEmpty()) {
                sections.add(
                    CatalogSection(
                        id = "home_carousel_$idx",
                        title = heading,
                        items = cards
                    )
                )
            }
        }

        // 2. "Das sehen andere AniWorld Nutzer"
        val watchingNow = doc.select(".seriesListContainer").lastOrNull()?.let { container ->
            parseCoverListItems(container)
        }.orEmpty()
        if (watchingNow.isNotEmpty()) {
            sections.add(
                CatalogSection(
                    id = "home_watching_now",
                    title = "👀 Das sehen andere Nutzer gerade",
                    items = watchingNow
                )
            )
        }

        // Falls auf der Startseite etwas leer war, Beliebte Animes nachladen
        if (sections.isEmpty()) {
            val popular = fetchCategoryOrGenre("beliebt")
            if (popular.isNotEmpty()) {
                sections.add(CatalogSection("popular", "Beliebte Animes", popular))
            }
        }

        sections
    }

    suspend fun fetchCategoryOrGenre(categoryKey: String): List<AnimeCard> = withContext(Dispatchers.IO) {
        val path = when (categoryKey.lowercase()) {
            "beliebt", "popular" -> "/beliebte-animes"
            "neu", "new" -> "/neu"
            "isekai" -> "/genre/fantasy"
            else -> "/genre/${categoryKey.lowercase()}"
        }
        val (_, html) = HttpClient.getHtml(path)
        val doc = Jsoup.parse(html, HttpClient.BASE_URL)
        parseCoverListItems(doc)
    }

    private fun parseCoverListItems(root: org.jsoup.nodes.Element): List<AnimeCard> {
        val seen = HashSet<String>()
        val result = mutableListOf<AnimeCard>()

        val anchors = root.select("a[href^=\"/anime/stream/\"]")
        for (a in anchors) {
            val href = a.attr("href").trim()
            if (href.isBlank()) continue
            val cleanSlug = href
                .removePrefix("https://aniworld.to")
                .removePrefix("/anime/stream/")
                .substringBefore("/")
                .trim()
            if (cleanSlug.isBlank() || !seen.add(cleanSlug)) continue

            val img = a.selectFirst("img") ?: a.parent()?.selectFirst("img")
            val rawImg = img?.attr("data-src")?.takeIf { it.isNotBlank() }
                ?: img?.attr("src").orEmpty()
            val coverUrl = HttpClient.normalizeImageUrl(rawImg)
            if (coverUrl.isNotBlank()) {
                coverCache[cleanSlug] = coverUrl
            }

            val title = a.selectFirst("h3")?.text()?.trim()
                ?: a.attr("title").substringBefore(" Stream").substringBefore(" Anime").trim()
                    .ifBlank { cleanSlug.replace("-", " ").replaceFirstChar { it.uppercase() } }

            val genre = a.selectFirst("small")?.text()?.trim().orEmpty()

            if (coverUrl.isNotBlank()) {
                result.add(
                    AnimeCard(
                        slug = cleanSlug,
                        title = cleanTitle(title),
                        coverUrl = coverUrl,
                        genreOrSubtitle = genre
                    )
                )
            }
        }
        return result
    }

    suspend fun searchAnimes(query: String): List<AnimeCard> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        val formBody = FormBody.Builder()
            .add("keyword", trimmed)
            .build()

        val req = Request.Builder()
            .url("${HttpClient.BASE_URL}/ajax/search")
            .post(formBody)
            .header("Referer", "${HttpClient.BASE_URL}/search")
            .header("X-Requested-With", "XMLHttpRequest")
            .build()

        val jsonText = HttpClient.okHttp.newCall(req).execute().use { it.body?.string().orEmpty() }
        val results = mutableListOf<AnimeCard>()
        val seen = HashSet<String>()

        runCatching {
            val arr = JsonParser.parseString(jsonText).asJsonArray
            for (elem in arr) {
                if (!elem.isJsonObject) continue
                val obj = elem.asJsonObject
                val link = obj.get("link")?.asString.orEmpty()
                if (!link.startsWith("/anime/stream/")) continue

                val slug = link.removePrefix("/anime/stream/").substringBefore("/").trim()
                if (slug.isBlank() || !seen.add(slug)) continue

                val rawTitle = obj.get("title")?.asString.orEmpty()
                val title = Jsoup.parse(rawTitle).text().trim()
                val rawDesc = obj.get("description")?.asString.orEmpty()
                val desc = Jsoup.parse(rawDesc).text().trim()

                results.add(
                    AnimeCard(
                        slug = slug,
                        title = cleanTitle(title),
                        coverUrl = coverCache[slug].orEmpty(),
                        genreOrSubtitle = desc
                    )
                )
            }
        }

        // Für die ersten 14 Suchergebnisse die echten Cover parallel nachladen, falls noch nicht im Cache
        coroutineScope {
            results.take(14).mapIndexed { index, card ->
                async {
                    if (card.coverUrl.isBlank()) {
                        val cover = runCatching { fetchCoverOnly(card.cleanSlug) }.getOrDefault("")
                        if (cover.isNotBlank()) {
                            results[index] = card.copy(coverUrl = cover)
                        }
                    }
                }
            }.forEach { it.await() }
        }

        results
    }

    private fun fetchCoverOnly(slug: String): String {
        coverCache[slug]?.let { if (it.isNotBlank()) return it }
        val (_, html) = HttpClient.getHtml("/anime/stream/$slug")
        val doc = Jsoup.parse(html, HttpClient.BASE_URL)
        val img = doc.selectFirst(".seriesCoverBox img")
        val raw = img?.attr("data-src")?.takeIf { it.isNotBlank() } ?: img?.attr("src").orEmpty()
        val url = HttpClient.normalizeImageUrl(raw)
        if (url.isNotBlank()) coverCache[slug] = url
        return url
    }

    suspend fun fetchAnimeDetail(
        slugOrPath: String,
        seasonPathOverride: String? = null
    ): AnimeDetail = withContext(Dispatchers.IO) {
        val cleanSlug = slugOrPath
            .removePrefix("https://aniworld.to")
            .removePrefix("/anime/stream/")
            .substringBefore("/")
            .trim()

        val targetPath = seasonPathOverride ?: "/anime/stream/$cleanSlug"
        val cacheKey = targetPath

        detailCache[cacheKey]?.let { return@withContext it }

        val (_, html) = HttpClient.getHtml(targetPath)
        val doc = Jsoup.parse(html, HttpClient.BASE_URL)
        val detail = parseAnimeDetail(cleanSlug, targetPath, doc)
        detailCache[cacheKey] = detail
        detail
    }

    private fun parseAnimeDetail(slug: String, currentPath: String, doc: Document): AnimeDetail {
        val title = doc.selectFirst(".series-title h1 span")?.text()?.trim()
            ?: doc.selectFirst(".series-title h1")?.text()?.trim()
            ?: slug

        val descEl = doc.selectFirst("p.seri_des")
        val description = descEl?.attr("data-full-description")?.takeIf { it.isNotBlank() }
            ?: descEl?.text()?.trim().orEmpty()

        val coverImg = doc.selectFirst(".seriesCoverBox img")
        val rawCover = coverImg?.attr("data-src")?.takeIf { it.isNotBlank() }
            ?: coverImg?.attr("src").orEmpty()
        val coverUrl = HttpClient.normalizeImageUrl(rawCover)
        if (coverUrl.isNotBlank()) coverCache[slug] = coverUrl

        val backdropStyle = doc.selectFirst(".seriesBackdrop")?.attr("style").orEmpty()
        val backdropUrl = Regex("""url\(['"]?([^'")]+)['"]?\)""")
            .find(backdropStyle)?.groupValues?.getOrNull(1)
            ?.let { HttpClient.normalizeImageUrl(it) }
            .orEmpty()
            .ifBlank { coverUrl }

        val startYear = doc.selectFirst("span[itemprop=startDate] a")?.text()?.trim().orEmpty()
        val endYear = doc.selectFirst("span[itemprop=endDate] a")?.text()?.trim().orEmpty()
        val years = if (startYear.isNotBlank()) "$startYear – ${endYear.ifBlank { "Heute" }}" else ""

        val fsk = doc.selectFirst(".fsk")?.attr("data-fsk")?.let { "FSK $it" }.orEmpty()
        val ratingVal = doc.selectFirst("span[itemprop=ratingValue]")?.text()?.trim().orEmpty()
        val rating = if (ratingVal.isNotBlank()) "$ratingVal / 5" else ""

        val genres = doc.select(".genres ul li a")
            .map { it.text().trim() }
            .filter { it.isNotBlank() && !it.equals("Ger", true) && !it.equals("GerSub", true) && !it.equals("EngSub", true) }

        // Staffeln aus #stream ul:first-of-type
        val seasons = mutableListOf<SeasonLink>()
        val seasonUl = doc.select("#stream ul").firstOrNull()
        seasonUl?.select("li a")?.forEach { a ->
            val label = a.text().trim()
            val href = a.attr("href").trim()
            if (label.isNotBlank() && href.isNotBlank()) {
                seasons.add(
                    SeasonLink(
                        label = label,
                        path = href,
                        isMovies = label.equals("Filme", ignoreCase = true)
                    )
                )
            }
        }

        val activeSeasonLabel = seasonUl?.selectFirst("li a.active")?.text()?.trim()
            ?: seasons.firstOrNull { currentPath.endsWith(it.path) }?.label
            ?: "1"

        // Episoden der ausgewählten Staffel
        val episodes = mutableListOf<EpisodeItem>()
        doc.select("table.seasonEpisodesList tbody tr").forEachIndexed { idx, tr ->
            val a = tr.selectFirst("td.seasonEpisodeTitle a") ?: return@forEachIndexed
            val epPath = a.attr("href").trim()
            if (epPath.isBlank()) return@forEachIndexed

            val epNum = tr.selectFirst("meta[itemprop=episodeNumber]")?.attr("content")?.toIntOrNull()
                ?: tr.attr("data-episode-season-id").toIntOrNull()
                ?: (idx + 1)

            val titleDe = a.selectFirst("strong")?.text()?.trim().orEmpty()
            val titleEn = a.selectFirst("span")?.text()?.trim().orEmpty()

            val langs = tr.select("td.editFunctions img")
                .mapNotNull { img ->
                    val flagInfo = "${img.attr("title")} ${img.attr("src")}"
                    LanguageOption.fromImgOrTitle(flagInfo)
                }
                .distinct()

            episodes.add(
                EpisodeItem(
                    number = epNum,
                    seasonLabel = activeSeasonLabel,
                    path = epPath,
                    titleDe = titleDe,
                    titleEn = titleEn,
                    languages = langs
                )
            )
        }

        return AnimeDetail(
            slug = slug,
            title = cleanTitle(title),
            description = description,
            coverUrl = coverUrl,
            backdropUrl = backdropUrl,
            years = years,
            ageRating = fsk,
            rating = rating,
            genres = genres,
            seasons = seasons,
            currentSeasonPath = currentPath,
            episodes = episodes
        )
    }

    /**
     * Lädt alle verfügbaren Video-Hoster und Sprachversionen für eine konkrete Episode.
     */
    suspend fun fetchEpisodeHosters(episodePath: String): List<HosterLink> = withContext(Dispatchers.IO) {
        val (_, html) = HttpClient.getHtml(episodePath)
        val doc = Jsoup.parse(html, HttpClient.BASE_URL)
        val hosters = mutableListOf<HosterLink>()

        for (li in doc.select("ul.row li[data-lang-key]")) {
            val langKey = li.attr("data-lang-key").toIntOrNull() ?: continue
            val language = LanguageOption.fromKey(langKey) ?: continue
            val redirect = li.attr("data-link-target").trim()
                .ifBlank { li.selectFirst("a.watchEpisode")?.attr("href")?.trim().orEmpty() }
            if (redirect.isBlank()) continue

            val hosterName = li.selectFirst("h4")?.text()?.trim().orEmpty().ifBlank { "Stream" }
            hosters.add(
                HosterLink(
                    hosterName = hosterName,
                    langKey = langKey,
                    language = language,
                    redirectPath = redirect
                )
            )
        }

        // Bevorzugte Reihenfolge der Hoster: VOE -> Vidmoly -> Filemoon -> Doodstream -> Rest
        hosters.sortedWith(
            compareBy<HosterLink> { hosterPriority(it.hosterName) }
                .thenBy { it.langKey }
        )
    }

    private fun hosterPriority(name: String): Int {
        val lower = name.lowercase()
        return when {
            lower.contains("voe") -> 0
            lower.contains("vidmoly") -> 1
            lower.contains("speedfiles") || lower.contains("vidoza") -> 2
            lower.contains("filemoon") -> 3
            lower.contains("dood") -> 4
            else -> 5
        }
    }

    private fun cleanTitle(raw: String): String =
        raw.replace(" Stream anschauen", "", ignoreCase = true)
            .replace(" kostenlos", "", ignoreCase = true)
            .trim()
}
