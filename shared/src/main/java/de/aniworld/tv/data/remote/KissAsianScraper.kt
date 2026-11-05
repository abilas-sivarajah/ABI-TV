package de.aniworld.tv.data.remote

import de.aniworld.tv.data.model.AnimeCard
import de.aniworld.tv.data.model.AnimeDetail
import de.aniworld.tv.data.model.CatalogSection
import de.aniworld.tv.data.model.EpisodeItem
import de.aniworld.tv.data.model.HosterLink
import de.aniworld.tv.data.model.KissAsianRef
import de.aniworld.tv.data.model.LanguageOption
import de.aniworld.tv.data.model.SeasonLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Vollständiger Scraper für https://kissasian.cam (KissAsian)
 * Liefert asiatische Serien / Dramas (K-Drama, C-Drama, J-Drama, Thai-Drama)
 * mit englischen Untertiteln und direkten Video-Hostern (Firestream, Streamtape, Doodstream, JustPlay).
 */
object KissAsianScraper {

    const val BASE_URL = "https://kissasian.cam"
    const val PREFIX = KissAsianRef.PREFIX
    const val EPISODE_PREFIX = KissAsianRef.EPISODE_PREFIX

    private val detailCache = ConcurrentHashMap<String, AnimeDetail>()
    private val thumbResizeRegex = Regex("""-\d+x\d+(?=\.\w+$)""")

    val CATEGORIES: List<Pair<String, String>> = listOf(
        "/series/?status=&type=Drama&order=popular" to "🌏 Asian Dramas: Beliebt",
        "/series/?status=&type=Drama&order=update" to "🌏 Asian Dramas: Neue Folgen",
        "/series/?status=ongoing&type=Drama&order=update" to "🌏 Asian Dramas: Laufend",
        "/series/?status=&type=Drama&order=rating" to "🌏 Asian Dramas: Top Bewertet",
        "/genres/romance/" to "🌏 Asian Romance",
        "/genres/drama/" to "🌏 Asian Drama",
        "/genres/comedy/" to "🌏 Asian Comedy",
        "/genres/action/" to "🌏 Asian Action",
        "/genres/thriller/" to "🌏 Asian Thriller",
        "/genres/mystery/" to "🌏 Asian Mystery",
        "/genres/fantasy/" to "🌏 Asian Fantasy",
        "/genres/historical/" to "🌏 Asian Historical",
        "/genres/melodrama/" to "🌏 Asian Melodrama",
        "/genres/youth/" to "🌏 Asian Youth & School"
    )

    fun isKissAsianSlug(slugOrPath: String): Boolean = KissAsianRef.matches(slugOrPath)

    fun isKissAsianEpisodePath(path: String): Boolean =
        path.startsWith(EPISODE_PREFIX) || KissAsianRef.isKissAsianUrl(path)

    fun toCleanSlug(slugOrPath: String): String = KissAsianRef.bareSlug(slugOrPath)

    suspend fun fetchHomeSections(): List<CatalogSection> = withContext(Dispatchers.IO) {
        val sections = mutableListOf<CatalogSection>()

        fun load(path: String) = async { runCatching { fetchCategory(path) }.getOrDefault(emptyList()) }
        val popularJob = load("/series/?status=&type=Drama&order=popular")
        val updateJob = load("/series/?status=&type=Drama&order=update")
        val romanceJob = load("/genres/romance/")
        val thrillerJob = load("/genres/thriller/")

        val popular = popularJob.await()
        val updates = updateJob.await()
        val romance = romanceJob.await()
        val thriller = thrillerJob.await()

        if (popular.isNotEmpty()) {
            sections.add(CatalogSection("kissasian_popular", "🌏 Beliebte Asian Dramas (KissAsian)", popular.take(24)))
        }
        if (updates.isNotEmpty()) {
            sections.add(CatalogSection("kissasian_updates", "✨ Neue Asian Drama-Folgen (KissAsian)", updates.take(24)))
        }
        if (romance.isNotEmpty()) {
            sections.add(CatalogSection("kissasian_romance", "❤️ Asian Romance (KissAsian)", romance.take(24)))
        }
        if (thriller.isNotEmpty()) {
            sections.add(CatalogSection("kissasian_thriller", "🔪 Asian Thriller & Mystery (KissAsian)", thriller.take(24)))
        }

        sections
    }

    suspend fun fetchCategory(categoryPath: String, page: Int = 1): List<AnimeCard> = withContext(Dispatchers.IO) {
        val cleanPath = categoryPath.removePrefix(PREFIX)
        val baseUrl = if (cleanPath.startsWith("http")) cleanPath else "$BASE_URL${if (cleanPath.startsWith("/")) cleanPath else "/$cleanPath"}"
        val fullUrl = buildPagedUrl(baseUrl, page)
        val (_, html) = HttpClient.getHtml(fullUrl, "$BASE_URL/")
        val doc = Jsoup.parse(html, BASE_URL)
        parseSeriesCards(doc)
    }

    private fun buildPagedUrl(baseUrl: String, page: Int): String {
        if (page <= 1) return baseUrl
        return if ('?' in baseUrl) {
            val beforeQuery = baseUrl.substringBefore('?')
            val query = baseUrl.substringAfter('?')
            "$beforeQuery?page=$page&$query"
        } else {
            val normalized = baseUrl.trimEnd('/')
            "$normalized/page/$page/"
        }
    }

    private fun parseSeriesCards(doc: Document): List<AnimeCard> {
        val cards = mutableListOf<AnimeCard>()
        val seen = HashSet<String>()

        val elements = doc.select(".listupd article.bs .bsx a, article.bs a[href]")
        for (a in elements) {
            val href = a.attr("href").trim()
            if (href.isBlank()) continue

            val rawSlug = extractSeriesSlugFromCardHref(href)
            if (rawSlug.isBlank() || !seen.add(rawSlug)) continue

            val img = a.selectFirst("img")
            val rawCover = img?.attr("src")?.takeIf { !it.startsWith("data:") && it.isNotBlank() }
                ?: img?.attr("data-src")?.takeIf { it.isNotBlank() }
                ?: ""

            val coverUrl = if (rawCover.isNotBlank()) {
                val abs = if (rawCover.startsWith("http")) rawCover else "$BASE_URL$rawCover"
                thumbResizeRegex.replace(abs, "")
            } else ""

            val title = a.selectFirst(".tt h2")?.text()?.trim()
                ?: a.attr("title").trim().ifBlank { null }
                ?: a.selectFirst(".tt")?.ownText()?.trim()
                ?: img?.attr("alt")?.trim()
                ?: ""

            if (title.isBlank()) continue

            val statusOrEp = a.selectFirst(".bt .epx")?.text()?.trim().orEmpty()
            val typez = a.selectFirst(".typez")?.text()?.trim().orEmpty()
            val subtitleParts = buildList {
                add(typez.ifBlank { "Drama" })
                if (statusOrEp.isNotBlank()) add(statusOrEp)
                add("KissAsian")
            }

            cards.add(
                AnimeCard(
                    slug = "$PREFIX$rawSlug",
                    title = title,
                    coverUrl = coverUrl,
                    genreOrSubtitle = subtitleParts.joinToString(" • ")
                )
            )
        }
        return cards
    }

    private fun extractSeriesSlugFromCardHref(href: String): String {
        val path = if (href.startsWith("http://", true) || href.startsWith("https://", true)) {
            runCatching { URI(href).path }.getOrNull().orEmpty()
        } else {
            href
        }
        val trimmed = path.trim('/')
        if (trimmed.isBlank()) return ""
        return if (trimmed.startsWith("series/")) {
            trimmed.removePrefix("series/").substringBefore('/').trim()
        } else {
            // Falls eine Karte direkt auf eine Episode verlinkt (z.B. /love-in-disguise-episode-6/)
            trimmed.substringBefore('/')
                .replace(Regex("""-episode-\d+.*$""", RegexOption.IGNORE_CASE), "")
                .trim()
        }
    }

    suspend fun fetchDetail(slugOrPath: String, requestedSeasonPath: String? = null): AnimeDetail =
        withContext(Dispatchers.IO) {
            val cleanSlug = toCleanSlug(slugOrPath)
            val cacheKey = "$cleanSlug:${requestedSeasonPath.orEmpty()}"
            detailCache[cacheKey]?.let { return@withContext it }

            val targetUrl = "$BASE_URL/series/$cleanSlug/"
            var (currentUrl, html) = HttpClient.getHtml(targetUrl, "$BASE_URL/")
            var doc = Jsoup.parse(html, BASE_URL)

            // Falls der übergebene Slug eine Episoden-URL war und /series/<slug>/ auf die Episode oder 404 zeigt
            if (doc.selectFirst("h1.entry-title") == null && doc.select(".eplister ul li a").isEmpty()) {
                val altUrl = "$BASE_URL/$cleanSlug/"
                val fallback = runCatching { HttpClient.getHtml(altUrl, "$BASE_URL/") }.getOrNull()
                if (fallback != null) {
                    currentUrl = fallback.first
                    html = fallback.second
                    doc = Jsoup.parse(html, BASE_URL)
                }
            }

            // Falls wir auf einer Episodenseite gelandet sind, zur übergeordneten /series/-Seite wechseln
            val parentSeriesHref = doc.selectFirst(
                ".ts-breadcrumb a[href*=\"/series/\"], .nvs.nvsc a[href*=\"/series/\"], #singlepisode .headlist a[href*=\"/series/\"]"
            )?.attr("href")?.trim()
            if (!parentSeriesHref.isNullOrBlank() && doc.select(".bixbox.bxcl.epcheck .eplister ul li a").isEmpty()) {
                val seriesUrl = if (parentSeriesHref.startsWith("http")) parentSeriesHref else "$BASE_URL$parentSeriesHref"
                val parentResp = runCatching { HttpClient.getHtml(seriesUrl, currentUrl) }.getOrNull()
                if (parentResp != null) {
                    currentUrl = parentResp.first
                    html = parentResp.second
                    doc = Jsoup.parse(html, BASE_URL)
                }
            }

            val title = doc.selectFirst("h1.entry-title")?.text()?.trim()
                ?: cleanSlug.replace("-", " ").replaceFirstChar { it.uppercase() }

            val description = doc.select(".bixbox.synp .entry-content p")
                .mapNotNull { it.text().trim().takeIf(String::isNotBlank) }
                .joinToString("\n\n")
                .ifBlank {
                    doc.selectFirst(".bixbox.synp .entry-content, .info-content .desc")?.text()?.trim().orEmpty()
                }

            val coverImg = doc.selectFirst(".thumbook .thumb img, .bigcover img")
            val rawCover = coverImg?.attr("src")?.takeIf { !it.startsWith("data:") && it.isNotBlank() }
                ?: coverImg?.attr("data-src")?.takeIf { it.isNotBlank() }
                ?: ""
            val coverUrl = if (rawCover.isNotBlank()) {
                val abs = if (rawCover.startsWith("http")) rawCover else "$BASE_URL$rawCover"
                thumbResizeRegex.replace(abs, "")
            } else ""

            val ratingVal = doc.selectFirst("meta[itemprop=\"ratingValue\"]")?.attr("content")?.trim().orEmpty()
                .ifBlank {
                    doc.selectFirst(".rating strong")?.text()
                        ?.replace("Rating", "", ignoreCase = true)
                        ?.trim().orEmpty()
                }
            val rating = if (ratingVal.isNotBlank()) "⭐ $ratingVal" else ""

            var year = ""
            var country = ""
            for (span in doc.select(".info-content .spe span")) {
                val text = span.text().trim()
                when {
                    text.startsWith("Released:", ignoreCase = true) -> {
                        year = Regex("""\b(19\d\d|20\d\d)\b""").find(text)?.value.orEmpty()
                    }
                    text.startsWith("Country:", ignoreCase = true) -> {
                        country = text.removePrefix("Country:").trim()
                    }
                }
            }
            if (year.isBlank()) {
                year = Regex("""\b(19\d\d|20\d\d)\b""").find(title)?.value.orEmpty()
            }

            val genres = buildList {
                if (country.isNotBlank()) add(country)
                addAll(
                    doc.select(".info-content .genxed a")
                        .map { it.text().trim() }
                        .filter { it.isNotBlank() }
                )
            }.distinct()

            val epLinks = doc.select(".bixbox.bxcl.epcheck .eplister ul li a, .eplister ul li a")
            val totalLinks = epLinks.size
            val parsedEpisodes = epLinks.mapIndexedNotNull { idx, a ->
                val href = a.attr("href").trim()
                if (href.isBlank()) return@mapIndexedNotNull null

                val relPath = if (href.startsWith("http://", true) || href.startsWith("https://", true)) {
                    runCatching { URI(href).path }.getOrNull().orEmpty()
                } else {
                    href
                }.ifBlank { return@mapIndexedNotNull null }

                val numText = a.selectFirst(".epl-num")?.text()?.trim().orEmpty()
                val epNumFromText = numText.toIntOrNull()
                    ?: Regex("""episode-(\d+)""", RegexOption.IGNORE_CASE).find(relPath)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: (totalLinks - idx).coerceAtLeast(1)

                val rawEpTitle = a.selectFirst(".epl-title")?.text()?.trim().orEmpty()
                val subBadge = a.selectFirst(".epl-sub")?.text()?.trim().orEmpty()
                val cleanEpTitle = rawEpTitle
                    .replace(title, "", ignoreCase = true)
                    .replace(Regex("""^[\s\-–:]+"""), "")
                    .trim()
                    .ifBlank { "Episode $epNumFromText" }

                val secondary = if (subBadge.isNotBlank()) "$subBadge • ENG SUB" else ""

                EpisodeItem(
                    number = epNumFromText,
                    seasonLabel = "1",
                    path = "$EPISODE_PREFIX$relPath",
                    titleDe = cleanEpTitle,
                    titleEn = secondary,
                    languages = listOf(LanguageOption.ENG_SUB)
                )
            }.sortedBy { it.number }

            val seasonPath = "/series/$cleanSlug/"
            val seasons = listOf(SeasonLink(label = "1", path = seasonPath, isMovies = false))

            val detail = AnimeDetail(
                slug = "$PREFIX$cleanSlug",
                title = title,
                description = description,
                coverUrl = coverUrl,
                backdropUrl = coverUrl,
                years = year,
                ageRating = "",
                rating = rating,
                genres = genres,
                seasons = seasons,
                currentSeasonPath = seasonPath,
                episodes = parsedEpisodes
            )

            if (parsedEpisodes.isNotEmpty()) {
                detailCache[cacheKey] = detail
            }
            detail
        }

    suspend fun fetchEpisodeHosters(episodePath: String): List<HosterLink> = withContext(Dispatchers.IO) {
        val rawPath = episodePath.removePrefix(EPISODE_PREFIX).trim()
        val fullUrl = if (rawPath.startsWith("http://", true) || rawPath.startsWith("https://", true)) {
            rawPath
        } else {
            "$BASE_URL/${rawPath.trimStart('/')}"
        }

        val (resolvedEpisodeUrl, html) = HttpClient.getHtml(fullUrl, "$BASE_URL/")
        val doc = Jsoup.parse(html, BASE_URL)

        val mirrorValues = doc.select("select.mirror option[value]")
            .map { it.attr("value").trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val collectedEmbeds = mutableListOf<String>()

        // 1. Embed der bereits geladenen Standardseite (/v/1/) auslesen
        extractEmbedFromEpisodeDoc(doc)?.let { collectedEmbeds.add(it) }

        // 2. Weitere Mirror-Tabs (/v/2/, /v/3/ oder Base64-Optionen) parallel auslesen
        val extraMirrors = mirrorValues.filter { value ->
            val normalizedVal = value.trimEnd('/')
            val normalizedCur = resolvedEpisodeUrl.trimEnd('/')
            normalizedVal != normalizedCur && !normalizedVal.endsWith("/v/1")
        }

        if (extraMirrors.isNotEmpty()) {
            val extraEmbeds = coroutineScope {
                extraMirrors.map { mirrorVal ->
                    async {
                        runCatching {
                            if (mirrorVal.startsWith("http://", true) || mirrorVal.startsWith("https://", true) || mirrorVal.startsWith("/")) {
                                val mirrorUrl = if (mirrorVal.startsWith("http")) mirrorVal else "$BASE_URL$mirrorVal"
                                val (_, mirrorHtml) = HttpClient.getHtml(mirrorUrl, resolvedEpisodeUrl)
                                extractEmbedFromEpisodeDoc(Jsoup.parse(mirrorHtml, BASE_URL))
                            } else {
                                // Falls das Option-Value Base64-kodiertes HTML mit <iframe> ist
                                val decoded = String(Base64.getDecoder().decode(mirrorVal))
                                Jsoup.parse(decoded).selectFirst("iframe[src]")?.attr("src")?.trim()
                            }
                        }.getOrNull()
                    }
                }.awaitAll()
            }
            for (embed in extraEmbeds) {
                if (!embed.isNullOrBlank()) {
                    collectedEmbeds.add(embed)
                }
            }
        }

        val seenUrls = HashSet<String>()
        val hosters = mutableListOf<Pair<Int, HosterLink>>()

        for (rawEmbed in collectedEmbeds) {
            val embedUrl = when {
                rawEmbed.startsWith("//") -> "https:$rawEmbed"
                rawEmbed.startsWith("/") -> "$BASE_URL$rawEmbed"
                else -> rawEmbed
            }.trim()
            if (embedUrl.isBlank() || !seenUrls.add(embedUrl)) continue

            val (name, priority) = classifyHoster(embedUrl)
            hosters.add(
                priority to HosterLink(
                    hosterName = name,
                    langKey = LanguageOption.ENG_SUB.langKey,
                    language = LanguageOption.ENG_SUB,
                    redirectPath = embedUrl
                )
            )
        }

        hosters.sortedBy { it.first }.map { it.second }
    }

    private fun extractEmbedFromEpisodeDoc(doc: Document): String? {
        val iframe = doc.selectFirst("#pembed iframe[src], .player-embed iframe[src], #embed_holder iframe[src], iframe[src*=\"/e/\"]")
        val src = iframe?.attr("src")?.trim().orEmpty()
        return src.takeIf { it.isNotBlank() && !it.startsWith("about:") }
    }

    private fun classifyHoster(embedUrl: String): Pair<String, Int> {
        val lower = embedUrl.lowercase()
        return when {
            lower.contains("frstream.") || lower.contains("firestream.") -> "Firestream" to 0
            lower.contains("strcloud.") || lower.contains("streamtape.") || lower.contains("shavetape.") -> "Streamtape" to 1
            lower.contains("voe.") || lower.contains("jilliandescribecompany") -> "VOE" to 2
            lower.contains("vidmoly") -> "Vidmoly" to 3
            lower.contains("justplay.") || lower.contains("n1mwq.") -> "JustPlay" to 4
            lower.contains("d000d.") || lower.contains("dood") || lower.contains("ds2play") -> "Doodstream" to 5
            else -> {
                val host = runCatching { URI(embedUrl).host }.getOrNull()
                    ?.removePrefix("www.")
                    ?.substringBefore('.')
                    ?.replaceFirstChar { it.uppercase() }
                    ?: "Stream"
                host to 6
            }
        }
    }

    suspend fun searchSeries(query: String): List<AnimeCard> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val searchUrl = "$BASE_URL/?s=$encoded"
        val (_, html) = HttpClient.getHtml(searchUrl, "$BASE_URL/")
        val doc = Jsoup.parse(html, BASE_URL)
        val rawCards = parseSeriesCards(doc)
        val ranked = TitleSearch.filterAndRank(rawCards, trimmed)
        (if (ranked.isNotEmpty()) ranked else rawCards).take(40)
    }
}
