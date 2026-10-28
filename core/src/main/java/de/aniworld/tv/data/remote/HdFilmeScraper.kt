package de.aniworld.tv.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import de.aniworld.tv.data.model.AnimeCard
import de.aniworld.tv.data.model.AnimeDetail
import de.aniworld.tv.data.model.CatalogSection
import de.aniworld.tv.data.model.EpisodeItem
import de.aniworld.tv.data.model.HosterLink
import de.aniworld.tv.data.model.LanguageOption
import de.aniworld.tv.data.model.SeasonLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.ConcurrentHashMap

/**
 * Vollständiger Parser & Resolver für https://hd-filme.one/ (Filme & Serien)
 * inkl. DeVideoSRC-API (/api/resolve & /api/embed-links).
 */
object HdFilmeScraper {

    const val BASE_URL = "https://hd-filme.one"
    const val PREFIX = "hdfilme:"
    private const val DEVIDEOSRC_BASE = "https://devideosrc.co"

    private val detailCache = ConcurrentHashMap<String, AnimeDetail>()
    // Speichert Serien-Hoster-Listen pro Episode-Key ("hdfilme_ep:<imdbId>:<season>:<episode>")
    private val episodeSourcesCache = ConcurrentHashMap<String, List<HosterLink>>()

    val CATEGORIES: List<Pair<String, String>> = listOf(
        "/filme1/" to "🎬 Alle Filme (Neu)",
        "/kinofilme/" to "🍿 Aktuelle Kinofilme",
        "/serien/" to "📺 Beliebte Serien",
        "/action/" to "💥 Action",
        "/thriller/" to "🔪 Thriller",
        "/horror/" to "👻 Horror",
        "/komodie/" to "😂 Komödie",
        "/sci-fi/" to "🚀 Sci-Fi",
        "/drama/" to "🎭 Drama",
        "/abenteuer/" to "🗺️ Abenteuer",
        "/fantasy/" to "🪄 Fantasy",
        "/krimi/" to "🕵️ Krimi",
        "/mystery/" to "🔍 Mystery",
        "/romantik/" to "❤️ Romantik",
        "/animation/" to "✨ Animation",
        "/familie/" to "👨‍👩‍👧 Familie"
    )

    fun isHdFilmeSlug(slugOrPath: String): Boolean =
        slugOrPath.startsWith(PREFIX) || slugOrPath.contains("hd-filme.one")

    fun toRelativePath(slugOrPath: String): String {
        val raw = slugOrPath.removePrefix(PREFIX).removePrefix(BASE_URL).trim()
        return if (raw.startsWith("/")) raw else "/$raw"
    }

    /**
     * Lädt die Startseiten-Sektionen für den Tab „🎬 Filme & Serien“,
     * inklusive des vom Nutzer gewünschten Films „Obsession - Du sollst mich lieben“ als Direkt-Highlight.
     */
    suspend fun fetchHomeSections(): List<CatalogSection> = withContext(Dispatchers.IO) {
        val sections = mutableListOf<CatalogSection>()

        val featuredObsession = AnimeCard(
            slug = "${PREFIX}/filme1/42002-obsession-du-sollst-mich-lieben-streaming-stream.html",
            title = "Obsession - Du sollst mich lieben",
            coverUrl = "$BASE_URL/uploads/thumb/300x450-0-80/2026-07/1782883603-406978104.jpg",
            genreOrSubtitle = "HD Stream • Deutsch"
        )
        val featuredBatman = AnimeCard(
            slug = "${PREFIX}/filme1/39122-batman-stream.html",
            title = "Batman",
            coverUrl = "$BASE_URL/uploads/thumb/300x450-0-80/2025-07/1751481353-536120953.jpg",
            genreOrSubtitle = "Serie • HD Stream • Deutsch"
        )

        val newMovies = runCatching { fetchCategory("/filme1/") }.getOrDefault(emptyList())
        val cinemaMovies = runCatching { fetchCategory("/kinofilme/") }.getOrDefault(emptyList())
        val seriesList = runCatching { fetchCategory("/serien/") }.getOrDefault(emptyList())
        val actionMovies = runCatching { fetchCategory("/action/") }.getOrDefault(emptyList())

        val combinedNew = buildList {
            add(featuredObsession)
            add(featuredBatman)
            addAll(newMovies.filterNot { it.slug == featuredObsession.slug || it.slug == featuredBatman.slug })
        }

        if (combinedNew.isNotEmpty()) {
            sections.add(CatalogSection("hdf_new", "🎬 Neu auf HD-Filme.one", combinedNew))
        }
        if (cinemaMovies.isNotEmpty()) {
            sections.add(CatalogSection("hdf_cinema", "🍿 Aktuelle Kinofilme", cinemaMovies))
        }
        if (seriesList.isNotEmpty()) {
            sections.add(CatalogSection("hdf_series", "📺 Serien-Highlights", seriesList))
        }
        if (actionMovies.isNotEmpty()) {
            sections.add(CatalogSection("hdf_action", "💥 Action & Blockbuster", actionMovies))
        }

        sections
    }

    suspend fun fetchCategory(categoryPath: String, page: Int = 1): List<AnimeCard> = withContext(Dispatchers.IO) {
        val path = if (page > 1) {
            val base = categoryPath.trimEnd('/')
            "$base/page/$page/"
        } else {
            categoryPath
        }
        val fullUrl = if (path.startsWith("http")) path else "$BASE_URL$path"
        val (_, html) = HttpClient.getHtml(fullUrl, "$BASE_URL/")
        val doc = Jsoup.parse(html, BASE_URL)
        parseMovieCards(doc)
    }

    suspend fun searchMovies(query: String): List<AnimeCard> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val form = FormBody.Builder()
            .add("do", "search")
            .add("subaction", "search")
            .add("story", trimmed)
            .build()

        val req = Request.Builder()
            .url("$BASE_URL/index.php?do=search")
            .post(form)
            .header("Referer", "$BASE_URL/")
            .build()

        val html = HttpClient.okHttp.newCall(req).execute().use { it.body?.string().orEmpty() }
        val doc = Jsoup.parse(html, BASE_URL)
        parseMovieCards(doc)
    }

    private fun parseMovieCards(doc: Document): List<AnimeCard> {
        val cards = mutableListOf<AnimeCard>()
        val seen = HashSet<String>()

        val imgs = doc.select("img[data-src*=\"/uploads/thumb/\"], img.default-poster")
        for (img in imgs) {
            val a = img.parents().firstOrNull { it.tagName() == "a" && it.attr("href").contains(".html") }
                ?: img.parent()?.parent()?.selectFirst("a[href*=\".html\"]")
                ?: continue

            val href = a.attr("href").trim()
            if (href.isBlank() || (!href.contains("/filme") && !href.contains("/serien"))) continue

            val relPath = href.removePrefix(BASE_URL)
            val slug = "$PREFIX$relPath"
            if (!seen.add(slug)) continue

            val rawImg = img.attr("data-src").ifBlank { img.attr("src") }
            if (rawImg.contains("default-poster.svg")) continue
            val coverUrl = normalizeCover(rawImg)

            val rawTitle = a.attr("title").ifBlank {
                img.attr("alt")
                    .removeSuffix("poster")
                    .removeSuffix("hdfilme stream online")
                    .removeSuffix("hdfilme")
            }.trim()

            if (rawTitle.isBlank()) continue

            cards.add(
                AnimeCard(
                    slug = slug,
                    title = rawTitle,
                    coverUrl = coverUrl,
                    genreOrSubtitle = "HD-Filme.one • Deutsch"
                )
            )
        }
        return cards
    }

    suspend fun fetchDetail(
        slugOrPath: String,
        seasonPathOverride: String? = null
    ): AnimeDetail = withContext(Dispatchers.IO) {
        val relPath = toRelativePath(slugOrPath)
        val cacheKey = "$relPath|${seasonPathOverride.orEmpty()}"
        detailCache[cacheKey]?.let { return@withContext it }

        val fullUrl = "$BASE_URL$relPath"
        val (_, html) = HttpClient.getHtml(fullUrl, "$BASE_URL/")
        val doc = Jsoup.parse(html, BASE_URL)

        val title = doc.selectFirst("h1")?.text()?.trim()
            ?: relPath.substringAfterLast("/").substringBefore(".html")

        val coverImg = doc.select("img[data-src*=\"/uploads/thumb/300x450\"]").firstOrNull()
        val backdropImg = doc.select("img[data-src*=\"/uploads/thumb/1280x720\"]").firstOrNull()

        val coverUrl = normalizeCover(coverImg?.attr("data-src").orEmpty())
        val backdropUrl = normalizeCover(backdropImg?.attr("data-src").orEmpty()).ifBlank { coverUrl }

        val desc = doc.select("p").firstOrNull { it.text().length > 80 }?.text()?.trim()
            ?: doc.selectFirst("meta[name=description]")?.attr("content")?.trim()
            .orEmpty()

        val genres = doc.select("a[href^=\"https://hd-filme.one/\"][title]")
            .map { it.text().trim() }
            .filter { it.length in 3..18 && it !in listOf("Home", "Alle Filme", "Serien", "Filme im Kino", "Jahres") }
            .distinct()
            .take(5)

        // Prüfen, ob es ein Film (#hd-player-frame) oder eine Serie (#serial_iframe) ist
        val movieFrameSrc = doc.selectFirst("#hd-player-frame")?.attr("data-src").orEmpty()
        val seriesImdbMatch = Regex("""var\s+imdb\s*=\s*['"](tt\d+)['"]""").find(html)
        val fallbackEmbedMatch = Regex("""var\s+FALLBACK\s*=\s*['"](https?://[^'"]+)['"]""").find(html)

        val detail = if (movieFrameSrc.isNotBlank()) {
            // --- FILM ---
            val epKey = "hdfilme_movie:$movieFrameSrc"
            val singleEpisode = EpisodeItem(
                number = 1,
                seasonLabel = "Film",
                path = epKey,
                titleDe = "$title (Kompletter Film)",
                titleEn = "HD Stream",
                languages = listOf(LanguageOption.GER_DUB)
            )
            AnimeDetail(
                slug = "$PREFIX$relPath",
                title = title,
                description = desc,
                coverUrl = coverUrl,
                backdropUrl = backdropUrl,
                years = "",
                ageRating = "HD",
                rating = "",
                genres = genres,
                seasons = emptyList(),
                currentSeasonPath = relPath,
                episodes = listOf(singleEpisode)
            )
        } else if (seriesImdbMatch != null) {
            // --- SERIE via DeVideoSRC ---
            val imdbId = seriesImdbMatch.groupValues[1]
            val fallbackEmbed = fallbackEmbedMatch?.groupValues?.getOrNull(1)
            parseSeriesFromDeVideoSrc(
                slug = "$PREFIX$relPath",
                title = title,
                desc = desc,
                coverUrl = coverUrl,
                backdropUrl = backdropUrl,
                genres = genres,
                imdbId = imdbId,
                fallbackEmbed = fallbackEmbed,
                selectedSeasonStr = seasonPathOverride
            )
        } else {
            // Allgemeiner Fallback, falls direkt ein Embed im HTML liegt
            val anyEmbed = fallbackEmbedMatch?.groupValues?.getOrNull(1).orEmpty()
            val epKey = "hdfilme_direct:$anyEmbed"
            AnimeDetail(
                slug = "$PREFIX$relPath",
                title = title,
                description = desc,
                coverUrl = coverUrl,
                backdropUrl = backdropUrl,
                years = "",
                ageRating = "HD",
                rating = "",
                genres = genres,
                seasons = emptyList(),
                currentSeasonPath = relPath,
                episodes = listOf(
                    EpisodeItem(
                        number = 1,
                        seasonLabel = "Film",
                        path = epKey,
                        titleDe = title,
                        titleEn = "",
                        languages = listOf(LanguageOption.GER_DUB)
                    )
                )
            )
        }

        detailCache[cacheKey] = detail
        detail
    }

    private fun parseSeriesFromDeVideoSrc(
        slug: String,
        title: String,
        desc: String,
        coverUrl: String,
        backdropUrl: String,
        genres: List<String>,
        imdbId: String,
        fallbackEmbed: String?,
        selectedSeasonStr: String?
    ): AnimeDetail {
        val serialUrl = "$DEVIDEOSRC_BASE/serial/$imdbId"
        val (_, serialHtml) = HttpClient.getHtml(serialUrl, "$BASE_URL/")
        val serialDoc = Jsoup.parse(serialHtml, DEVIDEOSRC_BASE)

        // Token für /api/embed-links extrahieren und alle Episoden-Hoster vorab laden
        val tokenMatch = Regex("""token:\s*["']([^"']+)["']""").find(serialHtml)
        if (tokenMatch != null) {
            val token = tokenMatch.groupValues[1]
            runCatching {
                val payload = JsonObject().apply {
                    addProperty("type", "tv")
                    addProperty("id", imdbId)
                    addProperty("token", token)
                }.toString()
                val req = Request.Builder()
                    .url("$DEVIDEOSRC_BASE/api/embed-links")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .header("Referer", serialUrl)
                    .build()
                val jsonStr = HttpClient.okHttp.newCall(req).execute().use { it.body?.string().orEmpty() }
                val root = JsonParser.parseString(jsonStr).asJsonObject
                if (root.get("ok")?.asBoolean == true && root.has("tv")) {
                    val tvObj = root.getAsJsonObject("tv")
                    val seasonsArr = tvObj.getAsJsonArray("seasons")
                    seasonsArr?.forEach { sEl ->
                        val sObj = sEl.asJsonObject
                        val sNum = sObj.get("season_number")?.asInt ?: 1
                        sObj.getAsJsonArray("episodes")?.forEach { eEl ->
                            val eObj = eEl.asJsonObject
                            val eNum = eObj.get("episode_number")?.asInt ?: 1
                            val hosters = parseSourcesArray(eObj.getAsJsonArray("sources"))
                            if (hosters.isNotEmpty()) {
                                episodeSourcesCache["hdfilme_tv:$imdbId:$sNum:$eNum"] = hosters
                            }
                        }
                    }
                }
            }
        }

        val seasons = serialDoc.select("._stab[data-season]").map { tab ->
            val sNum = tab.attr("data-season").trim()
            SeasonLink(
                label = sNum,
                path = sNum,
                isMovies = false
            )
        }

        val activeSeason = selectedSeasonStr?.takeIf { it.isNotBlank() }
            ?: seasons.firstOrNull()?.label
            ?: "1"

        val episodes = mutableListOf<EpisodeItem>()
        val grp = serialDoc.selectFirst("._grp[data-season=\"$activeSeason\"]")
        grp?.select("._ep")?.forEach { epEl ->
            val sNum = epEl.attr("data-season").ifBlank { activeSeason }
            val eNum = epEl.attr("data-episode").toIntOrNull() ?: 1
            val epTitle = epEl.selectFirst("._ept")?.text()?.trim().orEmpty()
            val epDesc = epEl.selectFirst("._epd")?.text()?.trim().orEmpty()
            val epKey = "hdfilme_tv:$imdbId:$sNum:$eNum"

            if (!fallbackEmbed.isNullOrBlank() && !episodeSourcesCache.containsKey(epKey)) {
                episodeSourcesCache[epKey] = listOf(
                    HosterLink(
                        hosterName = "Stream",
                        langKey = LanguageOption.GER_DUB.langKey,
                        language = LanguageOption.GER_DUB,
                        redirectPath = fallbackEmbed
                    )
                )
            }

            episodes.add(
                EpisodeItem(
                    number = eNum,
                    seasonLabel = sNum,
                    path = epKey,
                    titleDe = epTitle.ifBlank { "Folge $eNum" },
                    titleEn = epDesc.take(90),
                    languages = listOf(LanguageOption.GER_DUB)
                )
            )
        }

        return AnimeDetail(
            slug = slug,
            title = title,
            description = desc,
            coverUrl = coverUrl,
            backdropUrl = backdropUrl,
            years = "",
            ageRating = "Serie • HD",
            rating = "",
            genres = genres,
            seasons = seasons,
            currentSeasonPath = activeSeason,
            episodes = episodes
        )
    }

    /**
     * Löst die Hoster-Liste für einen Film (`hdfilme_movie:...`) oder eine Serien-Episode (`hdfilme_tv:...`) auf.
     */
    suspend fun fetchHostersForEpisode(episodePath: String): List<HosterLink> = withContext(Dispatchers.IO) {
        episodeSourcesCache[episodePath]?.let { if (it.isNotEmpty()) return@withContext it }

        if (episodePath.startsWith("hdfilme_direct:")) {
            val url = episodePath.removePrefix("hdfilme_direct:")
            if (url.isNotBlank()) {
                return@withContext listOf(
                    HosterLink("Direct", LanguageOption.GER_DUB.langKey, LanguageOption.GER_DUB, url)
                )
            }
        }

        if (episodePath.startsWith("hdfilme_tv:")) {
            // Format: hdfilme_tv:<imdbId>:<season>:<episode>
            val parts = episodePath.removePrefix("hdfilme_tv:").split(":")
            val imdbId = parts.getOrNull(0).orEmpty()
            if (imdbId.isNotBlank()) {
                val serialUrl = "$DEVIDEOSRC_BASE/serial/$imdbId"
                val (_, serialHtml) = HttpClient.getHtml(serialUrl, "$BASE_URL/")
                val tokenMatch = Regex("""token:\s*["']([^"']+)["']""").find(serialHtml)
                if (tokenMatch != null) {
                    val payload = JsonObject().apply {
                        addProperty("type", "tv")
                        addProperty("id", imdbId)
                        addProperty("token", tokenMatch.groupValues[1])
                    }.toString()
                    val req = Request.Builder()
                        .url("$DEVIDEOSRC_BASE/api/embed-links")
                        .post(payload.toRequestBody("application/json".toMediaType()))
                        .header("Referer", serialUrl)
                        .build()
                    val jsonStr = HttpClient.okHttp.newCall(req).execute().use { it.body?.string().orEmpty() }
                    val root = JsonParser.parseString(jsonStr).asJsonObject
                    if (root.get("ok")?.asBoolean == true && root.has("tv")) {
                        val tvObj = root.getAsJsonObject("tv")
                        tvObj.getAsJsonArray("seasons")?.forEach { sEl ->
                            val sObj = sEl.asJsonObject
                            val sNum = sObj.get("season_number")?.asInt ?: 1
                            sObj.getAsJsonArray("episodes")?.forEach { eEl ->
                                val eObj = eEl.asJsonObject
                                val eNum = eObj.get("episode_number")?.asInt ?: 1
                                val hosters = parseSourcesArray(eObj.getAsJsonArray("sources"))
                                if (hosters.isNotEmpty()) {
                                    episodeSourcesCache["hdfilme_tv:$imdbId:$sNum:$eNum"] = hosters
                                }
                            }
                        }
                    }
                }
                episodeSourcesCache[episodePath]?.let { if (it.isNotEmpty()) return@withContext it }
            }
        }

        if (episodePath.startsWith("hdfilme_movie:")) {
            val embedUrl = episodePath.removePrefix("hdfilme_movie:")
            val (_, embedHtml) = HttpClient.getHtml(embedUrl, "$BASE_URL/")
            val doc = Jsoup.parse(embedHtml, DEVIDEOSRC_BASE)
            val shell = doc.selectFirst(".player-shell") ?: return@withContext emptyList()

            val payload = JsonObject().apply {
                addProperty("type", shell.attr("data-type").ifBlank { "movie" })
                addProperty("id", shell.attr("data-id"))
                addProperty("season", shell.attr("data-season").toIntOrNull() ?: 1)
                addProperty("episode", shell.attr("data-episode").toIntOrNull() ?: 1)
                addProperty("mode", shell.attr("data-mode").ifBlank { "custom" })
                addProperty("token", shell.attr("data-resolve-token"))
            }.toString()

            val req = Request.Builder()
                .url("$DEVIDEOSRC_BASE/api/resolve")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .header("Referer", embedUrl)
                .header("Accept", "application/json")
                .build()

            val jsonStr = HttpClient.okHttp.newCall(req).execute().use { it.body?.string().orEmpty() }
            val root = JsonParser.parseString(jsonStr).asJsonObject
            val hosters = parseSourcesArray(root.getAsJsonArray("sources"))
            if (hosters.isNotEmpty()) {
                episodeSourcesCache[episodePath] = hosters
            }
            return@withContext hosters
        }

        emptyList()
    }

    private fun parseSourcesArray(sourcesArr: com.google.gson.JsonArray?): List<HosterLink> {
        if (sourcesArr == null) return emptyList()
        val list = mutableListOf<HosterLink>()
        for (el in sourcesArr) {
            if (!el.isJsonObject) continue
            val obj = el.asJsonObject
            val rawName = obj.get("name")?.asString.orEmpty()
            val url = obj.get("url")?.asString.orEmpty()
            if (url.isBlank()) continue

            val cleanName = when {
                rawName.contains("voe", true) -> "VOE"
                rawName.contains("dood", true) -> "Doodstream"
                rawName.contains("mixdrop", true) -> "Mixdrop"
                rawName.contains("dropload", true) -> "Dropload"
                rawName.contains("vidmoly", true) -> "Vidmoly"
                else -> rawName.substringBefore(".").replaceFirstChar { it.uppercase() }
            }

            list.add(
                HosterLink(
                    hosterName = cleanName,
                    langKey = LanguageOption.GER_DUB.langKey,
                    language = LanguageOption.GER_DUB,
                    redirectPath = url
                )
            )
        }

        // VOE -> Dropload -> Doodstream -> Mixdrop -> Vidmoly
        return list.sortedBy { h ->
            when (h.hosterName.lowercase()) {
                "voe" -> 0
                "dropload" -> 1
                "doodstream" -> 2
                "mixdrop" -> 3
                "vidmoly" -> 4
                else -> 5
            }
        }
    }

    private fun normalizeCover(raw: String): String {
        if (raw.isBlank()) return ""
        return when {
            raw.startsWith("http") -> raw
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("/") -> "$BASE_URL$raw"
            else -> "$BASE_URL/$raw"
        }
    }
}
