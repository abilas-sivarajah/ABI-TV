package de.aniworld.tv.data.remote

import de.aniworld.tv.data.model.AnimeCard
import de.aniworld.tv.data.model.AnimeDetail
import de.aniworld.tv.data.model.CatalogSection
import de.aniworld.tv.data.model.EpisodeItem
import de.aniworld.tv.data.model.HosterLink
import de.aniworld.tv.data.model.LanguageOption
import de.aniworld.tv.data.model.SeasonLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.ConcurrentHashMap

/**
 * Vollständiger Scraper für https://serienstream.to (S.to)
 * Liefert alle bekannten westlichen Serien (Netflix, HBO, Disney+, Prime etc.)
 * auf Deutsch & Englisch mit VOE-, Streamtape- und Vidoza-Hostern.
 */
object SerienStreamScraper {

    const val BASE_URL = "https://serienstream.to"
    const val PREFIX = "sto:"

    private val detailCache = ConcurrentHashMap<String, AnimeDetail>()
    private var cachedAtoZSeries: List<AnimeCard>? = null

    val CATEGORIES: List<Pair<String, String>> = listOf(
        "/beliebte-serien" to "🔥 Beliebte Serien",
        "/neu" to "✨ Neue Serien",
        "/genre/action" to "💥 Action",
        "/genre/drama" to "🎭 Drama",
        "/genre/krimi" to "🕵️ Krimi",
        "/genre/science-fiction" to "🚀 Sci-Fi",
        "/genre/comedy" to "😂 Comedy",
        "/genre/fantasy" to "🪄 Fantasy",
        "/genre/thriller" to "🔪 Thriller",
        "/genre/horror" to "👻 Horror",
        "/genre/abenteuer" to "🗺️ Abenteuer",
        "/genre/mystery" to "🔍 Mystery",
        "/genre/animation" to "🎨 Animation",
        "/genre/dokumentation" to "📚 Doku"
    )

    fun isStoSlug(slugOrPath: String): Boolean =
        slugOrPath.startsWith(PREFIX) ||
            slugOrPath.startsWith("/serie/") ||
            slugOrPath.contains("serienstream.to") ||
            slugOrPath.contains("s.to")

    fun toCleanSlug(slugOrPath: String): String {
        return slugOrPath
            .removePrefix(PREFIX)
            .removePrefix(BASE_URL)
            .removePrefix("https://s.to")
            .removePrefix("/serie/")
            .substringBefore("/")
            .trim()
    }

    suspend fun fetchHomeSections(): List<CatalogSection> = withContext(Dispatchers.IO) {
        val sections = mutableListOf<CatalogSection>()

        val popular = runCatching { fetchCategory("/beliebte-serien") }.getOrDefault(emptyList())
        val action = runCatching { fetchCategory("/genre/action") }.getOrDefault(emptyList())
        val scifi = runCatching { fetchCategory("/genre/science-fiction") }.getOrDefault(emptyList())
        val drama = runCatching { fetchCategory("/genre/drama") }.getOrDefault(emptyList())
        val krimi = runCatching { fetchCategory("/genre/krimi") }.getOrDefault(emptyList())

        if (popular.isNotEmpty()) {
            sections.add(CatalogSection("sto_popular", "🔥 Beliebte Serien (S.to)", popular.take(24)))
        }
        if (action.isNotEmpty()) {
            sections.add(CatalogSection("sto_action", "💥 Action & Spannung", action.take(24)))
        }
        if (scifi.isNotEmpty()) {
            sections.add(CatalogSection("sto_scifi", "🚀 Sci-Fi & Fantasy", scifi.take(24)))
        }
        if (drama.isNotEmpty()) {
            sections.add(CatalogSection("sto_drama", "🎭 Drama & Emotionen", drama.take(24)))
        }
        if (krimi.isNotEmpty()) {
            sections.add(CatalogSection("sto_krimi", "🕵️ Krimi & Thriller", krimi.take(24)))
        }

        sections
    }

    suspend fun fetchCategory(categoryPath: String): List<AnimeCard> = withContext(Dispatchers.IO) {
        val fullUrl = if (categoryPath.startsWith("http")) categoryPath else "$BASE_URL$categoryPath"
        val (_, html) = HttpClient.getHtml(fullUrl, "$BASE_URL/")
        val doc = Jsoup.parse(html, BASE_URL)
        parseSeriesCards(doc)
    }

    private fun parseSeriesCards(doc: Document): List<AnimeCard> {
        val cards = mutableListOf<AnimeCard>()
        val seen = HashSet<String>()

        // 1. Suche nach Elementen mit Bild und /serie/ Link
        val links = doc.select("a[href^=\"/serie/\"]")
        for (a in links) {
            val href = a.attr("href").trim()
            if (href.isBlank() || href.contains("/staffel-")) continue

            val slug = href.removePrefix("/serie/").substringBefore("/").trim()
            if (slug.isBlank() || !seen.add(slug)) continue

            val img = a.selectFirst("img") ?: a.parent()?.selectFirst("img")
            val rawCover = img?.attr("src")?.takeIf { !it.startsWith("data:") && it.isNotBlank() }
                ?: img?.attr("data-src")?.takeIf { it.isNotBlank() }
                ?: ""

            val title = a.selectFirst("h3")?.text()?.trim()
                ?: img?.attr("alt")?.trim()
                ?: a.text().trim()

            val coverUrl = if (rawCover.isNotBlank()) {
                if (rawCover.startsWith("http")) rawCover else "$BASE_URL$rawCover"
            } else ""

            if (title.isNotBlank()) {
                cards.add(
                    AnimeCard(
                        slug = "$PREFIX$slug",
                        title = title,
                        coverUrl = coverUrl,
                        genreOrSubtitle = "Serie • S.to"
                    )
                )
            }
        }
        return cards
    }

    suspend fun fetchDetail(slugOrPath: String, requestedSeasonPath: String? = null): AnimeDetail =
        withContext(Dispatchers.IO) {
            val cleanSlug = toCleanSlug(slugOrPath)
            val cacheKey = "$cleanSlug:${requestedSeasonPath.orEmpty()}"
            detailCache[cacheKey]?.let { return@withContext it }

            val targetPath = if (!requestedSeasonPath.isNullOrBlank()) {
                if (requestedSeasonPath.startsWith("http")) requestedSeasonPath else "$BASE_URL$requestedSeasonPath"
            } else {
                "$BASE_URL/serie/$cleanSlug/staffel-1"
            }

            var (currentUrl, html) = HttpClient.getHtml(targetPath, "$BASE_URL/")
            var doc = Jsoup.parse(html, BASE_URL)

            // Falls staffel-1 nicht existiert (z.B. nur Filme / staffel-0), Basis-URL laden
            if (doc.select(".episode-row").isEmpty() && requestedSeasonPath.isNullOrBlank()) {
                val fallback = HttpClient.getHtml("$BASE_URL/serie/$cleanSlug", "$BASE_URL/")
                currentUrl = fallback.first
                html = fallback.second
                doc = Jsoup.parse(html, BASE_URL)
            }

            // Titel
            val title = doc.selectFirst("h1")?.text()?.trim()
                ?: doc.selectFirst(".series-title")?.text()?.trim()
                ?: cleanSlug.replace("-", " ").replaceFirstChar { it.uppercase() }

            // Beschreibung
            val description = doc.selectFirst(".series-description, [itemprop=\"description\"], p.text-white-50")
                ?.text()?.trim().orEmpty()

            // Cover & Backdrop
            val coverImg = doc.selectFirst("img.img-fluid[data-src*=\"/media/images/channel/\"], img[src*=\"/media/images/channel/\"]")
            val rawCover = coverImg?.attr("data-src")?.ifBlank { coverImg.attr("src") }.orEmpty()
            val coverUrl = if (rawCover.isNotBlank()) {
                if (rawCover.startsWith("http")) rawCover else "$BASE_URL$rawCover"
            } else ""

            val backdropImg = doc.selectFirst("img[data-src*=\"/media/images/backdrop/\"], img[src*=\"/media/images/backdrop/\"]")
            val rawBackdrop = backdropImg?.attr("data-src")?.ifBlank { backdropImg.attr("src") }.orEmpty()
            val backdropUrl = if (rawBackdrop.isNotBlank()) {
                if (rawBackdrop.startsWith("http")) rawBackdrop else "$BASE_URL$rawBackdrop"
            } else coverUrl

            // Staffeln (Seasons)
            val seasons = mutableListOf<SeasonLink>()
            val seasonLinks = doc.select("a[href*=\"/serie/$cleanSlug/staffel-\"]")
            val seenSeasons = HashSet<String>()
            for (sl in seasonLinks) {
                val href = sl.attr("href").trim()
                if (href.isBlank() || href.contains("/episode-")) continue
                val seasonPart = href.substringAfterLast("/")
                if (!seenSeasons.add(seasonPart)) continue

                val label = sl.text().trim().ifBlank {
                    if (seasonPart == "staffel-0") "Filme" else seasonPart.removePrefix("staffel-")
                }
                seasons.add(SeasonLink(label = label, path = href))
            }

            // Standard: falls keine Staffeln gefunden, Staffel 1 anlegen
            if (seasons.isEmpty()) {
                seasons.add(SeasonLink(label = "1", path = "/serie/$cleanSlug/staffel-1"))
            }

            // Episoden der ausgewählten Staffel
            val episodes = mutableListOf<EpisodeItem>()
            val episodeRows = doc.select(".episode-row")

            for (row in episodeRows) {
                val numText = row.selectFirst(".episode-number-cell")?.text()?.trim().orEmpty()
                val number = numText.toIntOrNull() ?: (episodes.size + 1)

                val titleDe = row.selectFirst(".episode-title-ger")?.text()?.trim().orEmpty()
                val titleEn = row.selectFirst(".episode-title-eng")?.text()?.trim().orEmpty()

                val onclick = row.attr("onclick").orEmpty()
                val pathMatch = Regex("""['"](/serie/[^'"]+/episode-[0-9]+)['"]""").find(onclick)
                val epPath = pathMatch?.groupValues?.getOrNull(1)
                    ?: row.selectFirst("a[href*=\"/episode-\"]")?.attr("href")
                    ?: "/serie/$cleanSlug/staffel-1/episode-$number"

                val seasonLabel = if (currentUrl.contains("staffel-0")) "Filme" else {
                    Regex("""staffel-([0-9]+)""").find(currentUrl)?.groupValues?.getOrNull(1) ?: "1"
                }

                // Generiere eindeutigen Episodenpfad für Hosterauflösung
                val finalPath = "sto_${cleanSlug}_s${seasonLabel}_e${number}_$epPath"

                episodes.add(
                    EpisodeItem(
                        number = number,
                        seasonLabel = seasonLabel,
                        path = finalPath,
                        titleDe = titleDe,
                        titleEn = titleEn,
                        languages = listOf(LanguageOption.GER_DUB, LanguageOption.ENG_SUB, LanguageOption.GER_SUB)
                    )
                )
            }

            val detail = AnimeDetail(
                slug = "$PREFIX$cleanSlug",
                title = title,
                description = description,
                coverUrl = coverUrl,
                backdropUrl = backdropUrl,
                years = "",
                ageRating = "16+",
                rating = "⭐ 8.5",
                genres = listOf("Serie", "S.to"),
                seasons = seasons,
                currentSeasonPath = currentUrl.removePrefix(BASE_URL),
                episodes = episodes
            )

            detailCache[cacheKey] = detail
            detail
        }

    suspend fun fetchEpisodeHosters(episodePath: String): List<HosterLink> = withContext(Dispatchers.IO) {
        val hosters = mutableListOf<HosterLink>()

        // Extrahiere den echten URL-Pfad (/serie/<slug>/staffel-<x>/episode-<y>)
        val realPath = if (episodePath.startsWith("sto_")) {
            val idx = episodePath.indexOf("/serie/")
            if (idx != -1) episodePath.substring(idx) else episodePath.removePrefix("sto_")
        } else {
            episodePath
        }

        val fullUrl = if (realPath.startsWith("http")) realPath else "$BASE_URL$realPath"
        val (_, html) = HttpClient.getHtml(fullUrl, "$BASE_URL/")
        val doc = Jsoup.parse(html, BASE_URL)

        val buttons = doc.select(".link-wrapper button.link-box")
        for (btn in buttons) {
            val playUrl = btn.attr("data-play-url").trim()
            if (playUrl.isBlank()) continue

            val providerName = btn.attr("data-provider-name").trim().ifBlank { "Stream" }
            val langId = btn.attr("data-language-id").toIntOrNull() ?: 1
            val lang = when (langId) {
                1 -> LanguageOption.GER_DUB
                2 -> LanguageOption.ENG_SUB
                3 -> LanguageOption.GER_SUB
                else -> LanguageOption.GER_DUB
            }

            val fullRedirectUrl = if (playUrl.startsWith("http")) playUrl else "$BASE_URL$playUrl"

            hosters.add(
                HosterLink(
                    hosterName = providerName,
                    langKey = lang.langKey,
                    language = lang,
                    redirectPath = fullRedirectUrl
                )
            )
        }

        hosters
    }

    suspend fun searchSeries(query: String): List<AnimeCard> = withContext(Dispatchers.IO) {
        val trimmed = query.trim().lowercase()
        if (trimmed.length < 2) return@withContext emptyList()

        if (cachedAtoZSeries == null) {
            runCatching {
                val (_, html) = HttpClient.getHtml("$BASE_URL/serien", "$BASE_URL/")
                val doc = Jsoup.parse(html, BASE_URL)
                val items = mutableListOf<AnimeCard>()
                val liElements = doc.select("li.series-item")
                for (li in liElements) {
                    val a = li.selectFirst("a") ?: continue
                    val href = a.attr("href").trim()
                    val slug = href.removePrefix("/serie/").trim()
                    val title = a.text().trim()
                    if (slug.isNotBlank() && title.isNotBlank()) {
                        items.add(
                            AnimeCard(
                                slug = "$PREFIX$slug",
                                title = title,
                                coverUrl = "",
                                genreOrSubtitle = "Serie • S.to"
                            )
                        )
                    }
                }
                cachedAtoZSeries = items
            }
        }

        val catalog = cachedAtoZSeries.orEmpty()
        catalog.filter {
            it.title.lowercase().contains(trimmed) || it.cleanSlug.lowercase().contains(trimmed)
        }.take(30)
    }
}
