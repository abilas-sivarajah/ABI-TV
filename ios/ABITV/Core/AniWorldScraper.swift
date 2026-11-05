import Foundation

/// Vollständiger Scraper für https://aniworld.to (entspricht `AniWorldScraper.kt` aus `:shared`).
public enum AniWorldScraper {

    private static let lock = NSLock()
    private static var detailCache: [String: AnimeDetail] = [:]
    private static var coverCache: [String: String] = [:]

    public static let genreCategories: [(String, String)] = [
        ("beliebt", "🔥 Beliebte Animes"),
        ("neu", "✨ Neue Animes"),
        ("action", "⚔️ Action"),
        ("abenteuer", "🗺️ Abenteuer"),
        ("fantasy", "🪄 Fantasy"),
        ("romanze", "❤️ Romanze"),
        ("komoedie", "😂 Komödie"),
        ("drama", "🎭 Drama"),
        ("mystery", "🕵️ Mystery"),
        ("scifi", "🚀 Sci-Fi"),
        ("isekai", "🌌 Isekai / Andere Welt"),
        ("fighting-shounen", "🥊 Fighting-Shounen"),
        ("thriller", "🔪 Thriller"),
        ("horror", "👻 Horror"),
        ("sport", "⚽ Sport")
    ]

    public static func fetchHomeCatalog() async throws -> [CatalogSection] {
        let (_, html) = try await HttpClient.getHtml(HttpClient.baseUrl)
        var sections = parseHomeSections(html)
        if sections.isEmpty {
            let popular = (try? await fetchCategoryOrGenre("beliebt")) ?? []
            if !popular.isEmpty {
                sections.append(CatalogSection(id: "popular", title: "Beliebte Animes", items: popular))
            }
        }
        return sections
    }

    public static func parseHomeSections(_ html: String) -> [CatalogSection] {
        var sections: [CatalogSection] = []

        // 1. Carousels auf der Startseite ("Beliebt bei AniWorld", "Neue Animes", "Derzeit beliebt")
        for (idx, carousel) in HtmlParser.elements(in: html, withClass: "carousel").enumerated() {
            let heading = HtmlParser.firstMatch(in: carousel.html, pattern: #"<h2[^>]*>(.*?)</h2>"#)
                .map { HtmlParser.stripTagsAndDecode($0) } ?? ""
            let cards = parseCoverListItems(carousel.html)
            if !heading.isEmpty && !cards.isEmpty {
                sections.append(
                    CatalogSection(
                        id: "home_carousel_\(idx)",
                        title: heading,
                        items: cards
                    )
                )
            }
        }

        // 2. "Das sehen andere AniWorld Nutzer"
        if let lastContainer = HtmlParser.elements(in: html, withClass: "seriesListContainer").last {
            let watchingNow = parseCoverListItems(lastContainer.html)
            if !watchingNow.isEmpty {
                sections.append(
                    CatalogSection(
                        id: "home_watching_now",
                        title: "👀 Das sehen andere Nutzer gerade",
                        items: watchingNow
                    )
                )
            }
        }

        if sections.isEmpty {
            let fallbackCards = parseCoverListItems(html)
            if !fallbackCards.isEmpty {
                sections.append(CatalogSection(id: "home_all", title: "🔥 Beliebte Animes", items: fallbackCards))
            }
        }
        return sections
    }

    /// Titel einer Kategorie bzw. eines Genres; Genres blättern mit "/genre/action/2" …
    public static func fetchCategoryOrGenre(_ categoryKey: String, page: Int = 1) async throws -> [AnimeCard] {
        let lower = categoryKey.lowercased()
        let path: String
        switch lower {
        case "beliebt", "popular": path = "/beliebte-animes"
        case "neu", "new": path = "/neu"
        case "isekai": path = "/genre/fantasy"
        default: path = "/genre/\(lower)"
        }
        let pagedPath = page > 1 && path.hasPrefix("/genre/") ? "\(path)/\(page)" : path
        let (_, html) = try await HttpClient.getHtml(pagedPath)
        return parseCoverListItems(html)
    }

    public static func parseCoverListItems(_ html: String) -> [AnimeCard] {
        var seen = Set<String>()
        var result: [AnimeCard] = []

        let anchorMatches = HtmlParser.allMatches(
            in: html,
            pattern: #"(<a\b[^>]*href=["'](?:/anime/stream/|https://aniworld\.to/anime/stream/)[^"']+["'][^>]*>.*?</a>)"#
        )

        for match in anchorMatches {
            guard let anchorHtml = match.first else { continue }
            let href = HtmlParser.attr(anchorHtml, "href")
            guard !href.isEmpty else { continue }

            let cleanSlug = AnimeCard(slug: href, title: "", coverUrl: "").cleanSlug
            guard !cleanSlug.isEmpty, seen.insert(cleanSlug).inserted else { continue }

            let imgTag = HtmlParser.firstMatch(in: anchorHtml, pattern: #"(<img\b[^>]*>)"#) ?? ""
            let dataSrc = HtmlParser.attr(imgTag, "data-src")
            let src = HtmlParser.attr(imgTag, "src")
            let rawImg = !dataSrc.isEmpty ? dataSrc : src
            let coverUrl = HttpClient.normalizeImageUrl(rawImg)
            guard !coverUrl.isEmpty else { continue }

            lock.withLock { coverCache[cleanSlug] = coverUrl }

            let h3Text = HtmlParser.firstMatch(in: anchorHtml, pattern: #"<h3[^>]*>(.*?)</h3>"#)
                .map { HtmlParser.stripTagsAndDecode($0) } ?? ""
            var rawTitle = h3Text
            if rawTitle.isEmpty {
                let attrTitle = HtmlParser.attr(anchorHtml, "title")
                rawTitle = attrTitle
                    .components(separatedBy: " Stream").first?
                    .components(separatedBy: " Anime").first?
                    .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            }
            if rawTitle.isEmpty {
                rawTitle = cleanSlug.replacingOccurrences(of: "-", with: " ").capitalized
            }

            let genre = HtmlParser.firstMatch(in: anchorHtml, pattern: #"<small[^>]*>(.*?)</small>"#)
                .map { HtmlParser.stripTagsAndDecode($0) } ?? ""

            result.append(
                AnimeCard(
                    slug: cleanSlug,
                    title: cleanTitle(rawTitle),
                    coverUrl: coverUrl,
                    genreOrSubtitle: genre
                )
            )
        }
        return result
    }

    public static func searchAnimes(_ query: String) async -> [AnimeCard] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }

        guard let jsonText = try? await HttpClient.postForm(
            "\(HttpClient.baseUrl)/ajax/search",
            fields: [("keyword", trimmed)],
            referer: "\(HttpClient.baseUrl)/search",
            extraHeaders: ["X-Requested-With": "XMLHttpRequest"]
        ),
        let data = jsonText.data(using: .utf8),
        let arr = (try? JSONSerialization.jsonObject(with: data)) as? [[String: Any]] else {
            return []
        }

        var results: [AnimeCard] = []
        var seen = Set<String>()

        for obj in arr {
            guard let link = obj["link"] as? String,
                  link.hasPrefix("/anime/stream/") else { continue }
            let slug = AnimeCard(slug: link, title: "", coverUrl: "").cleanSlug
            guard !slug.isEmpty, seen.insert(slug).inserted else { continue }

            let rawTitle = (obj["title"] as? String) ?? ""
            let title = HtmlParser.stripTagsAndDecode(rawTitle)
            let rawDesc = (obj["description"] as? String) ?? ""
            let desc = HtmlParser.stripTagsAndDecode(rawDesc)

            let cachedCover = lock.withLock { coverCache[slug] ?? "" }

            results.append(
                AnimeCard(
                    slug: slug,
                    title: cleanTitle(title),
                    coverUrl: cachedCover,
                    genreOrSubtitle: desc
                )
            )
        }

        // Für die ersten 14 Suchergebnisse die echten Cover parallel nachladen
        let limit = min(14, results.count)
        if limit > 0 {
            await withTaskGroup(of: (Int, String).self) { group in
                for index in 0..<limit {
                    let card = results[index]
                    if card.coverUrl.isEmpty {
                        group.addTask {
                            let cover = await fetchCoverOnly(card.cleanSlug)
                            return (index, cover)
                        }
                    }
                }
                for await (index, cover) in group {
                    if !cover.isEmpty && index < results.count {
                        let old = results[index]
                        results[index] = AnimeCard(
                            slug: old.slug,
                            title: old.title,
                            coverUrl: cover,
                            genreOrSubtitle: old.genreOrSubtitle,
                            rating: old.rating
                        )
                    }
                }
            }
        }

        return results
    }

    private static func fetchCoverOnly(_ slug: String) async -> String {
        if let existing = lock.withLock({ coverCache[slug] }), !existing.isEmpty {
            return existing
        }

        guard let (_, html) = try? await HttpClient.getHtml("/anime/stream/\(slug)") else {
            return ""
        }
        let boxHtml = HtmlParser.firstMatch(in: html, pattern: #"class=["'][^"']*seriesCoverBox[^"']*["'][^>]*>(.*?)</div"#) ?? html
        let imgTag = HtmlParser.firstMatch(in: boxHtml, pattern: #"(<img\b[^>]*>)"#) ?? ""
        let raw = !HtmlParser.attr(imgTag, "data-src").isEmpty
            ? HtmlParser.attr(imgTag, "data-src")
            : HtmlParser.attr(imgTag, "src")
        let url = HttpClient.normalizeImageUrl(raw)
        if !url.isEmpty {
            lock.withLock { coverCache[slug] = url }
        }
        return url
    }

    public static func fetchAnimeDetail(
        _ slugOrPath: String,
        seasonPathOverride: String? = nil
    ) async throws -> AnimeDetail {
        let cleanSlug = AnimeCard(slug: slugOrPath, title: "", coverUrl: "").cleanSlug
        let targetPath = seasonPathOverride ?? "/anime/stream/\(cleanSlug)"

        if let cached = lock.withLock({ detailCache[targetPath] }) {
            return cached
        }

        let (_, html) = try await HttpClient.getHtml(targetPath)
        let detail = parseAnimeDetail(slug: cleanSlug, currentPath: targetPath, html: html)

        lock.withLock { detailCache[targetPath] = detail }
        return detail
    }

    public static func parseAnimeDetail(slug: String, currentPath: String, html: String) -> AnimeDetail {
        let titleBlock = HtmlParser.firstMatch(
            in: html,
            pattern: #"class=["'][^"']*series-title[^"']*["'][^>]*>.*?<h1[^>]*>(.*?)</h1>"#
        ) ?? ""
        let rawTitle = HtmlParser.firstMatch(in: titleBlock, pattern: #"<span[^>]*>(.*?)</span>"#)
            .map { HtmlParser.stripTagsAndDecode($0) }
            ?? HtmlParser.stripTagsAndDecode(titleBlock)
        let title = rawTitle.isEmpty ? slug : cleanTitle(rawTitle)

        let descTag = HtmlParser.firstMatch(in: html, pattern: #"(<p\b[^>]*class=["'][^"']*seri_des[^"']*["'][^>]*>.*?</p>)"#) ?? ""
        let fullDescAttr = HtmlParser.attr(descTag, "data-full-description")
        let description = !fullDescAttr.isEmpty ? fullDescAttr : HtmlParser.stripTagsAndDecode(descTag)

        let coverBox = HtmlParser.firstMatch(in: html, pattern: #"class=["'][^"']*seriesCoverBox[^"']*["'][^>]*>(.*?)</div"#) ?? ""
        let coverImg = HtmlParser.firstMatch(in: coverBox, pattern: #"(<img\b[^>]*>)"#) ?? ""
        let rawCover = !HtmlParser.attr(coverImg, "data-src").isEmpty
            ? HtmlParser.attr(coverImg, "data-src")
            : HtmlParser.attr(coverImg, "src")
        let coverUrl = HttpClient.normalizeImageUrl(rawCover)
        if !coverUrl.isEmpty {
            lock.withLock { coverCache[slug] = coverUrl }
        }

        let backdropTag = HtmlParser.firstMatch(in: html, pattern: #"(<[^>]*class=["'][^"']*seriesBackdrop[^"']*["'][^>]*>)"#) ?? ""
        let backdropStyle = HtmlParser.attr(backdropTag, "style")
        let backdropRaw = HtmlParser.firstMatch(in: backdropStyle, pattern: #"url\(['"]?([^'")]+)['"]?\)"#)
        let normalizedBackdrop = HttpClient.normalizeImageUrl(backdropRaw)
        let backdropUrl = normalizedBackdrop.isEmpty ? coverUrl : normalizedBackdrop

        let startYear = HtmlParser.firstMatch(in: html, pattern: #"itemprop=["']startDate["'][^>]*>.*?<a[^>]*>(.*?)</a>"#)
            .map { HtmlParser.stripTagsAndDecode($0) } ?? ""
        let endYear = HtmlParser.firstMatch(in: html, pattern: #"itemprop=["']endDate["'][^>]*>.*?<a[^>]*>(.*?)</a>"#)
            .map { HtmlParser.stripTagsAndDecode($0) } ?? ""
        let years = !startYear.isEmpty ? "\(startYear) – \(endYear.isEmpty ? "Heute" : endYear)" : ""

        let fskVal = HtmlParser.firstMatch(in: html, pattern: #"data-fsk=["'](\d+)["']"#) ?? ""
        let fsk = !fskVal.isEmpty ? "FSK \(fskVal)" : ""

        let ratingVal = HtmlParser.firstMatch(in: html, pattern: #"itemprop=["']ratingValue["'][^>]*>(.*?)</span>"#)
            .map { HtmlParser.stripTagsAndDecode($0) } ?? ""
        let rating = !ratingVal.isEmpty ? "\(ratingVal) / 5" : ""

        let genresBlock = HtmlParser.firstMatch(in: html, pattern: #"class=["'][^"']*genres[^"']*["'][^>]*>.*?<ul[^>]*>(.*?)</ul>"#) ?? ""
        let genres = HtmlParser.allMatches(in: genresBlock, pattern: #"<a\b[^>]*>(.*?)</a>"#)
            .compactMap { $0.count > 1 ? HtmlParser.stripTagsAndDecode($0[1]) : nil }
            .filter { g in
                !g.isEmpty &&
                g.caseInsensitiveCompare("Ger") != .orderedSame &&
                g.caseInsensitiveCompare("GerSub") != .orderedSame &&
                g.caseInsensitiveCompare("EngSub") != .orderedSame
            }

        // Staffeln aus #stream ul:first-of-type
        var seasons: [SeasonLink] = []
        let streamDiv = HtmlParser.firstMatch(in: html, pattern: #"id=["']stream["'][^>]*>.*?<ul[^>]*>(.*?)</ul>"#) ?? ""
        var activeSeasonLabel: String?
        for m in HtmlParser.allMatches(in: streamDiv, pattern: #"(<a\b[^>]*>(.*?)</a>)"#) {
            guard m.count > 2 else { continue }
            let aTag = m[1]
            let label = HtmlParser.stripTagsAndDecode(m[2])
            let href = HtmlParser.attr(aTag, "href")
            if !label.isEmpty && !href.isEmpty {
                seasons.append(SeasonLink(label: label, path: href))
                if HtmlParser.attr(aTag, "class").contains("active") {
                    activeSeasonLabel = label
                }
            }
        }

        let resolvedSeasonLabel = activeSeasonLabel
            ?? seasons.first(where: { currentPath.hasSuffix($0.path) })?.label
            ?? "1"

        // Episoden der ausgewählten Staffel
        var episodes: [EpisodeItem] = []
        let tableBody = HtmlParser.firstMatch(
            in: html,
            pattern: #"table\b[^>]*seasonEpisodesList[^>]*>.*?<tbody[^>]*>(.*?)</tbody>"#
        ) ?? ""
        let trBlocks = HtmlParser.blocks(in: tableBody, tag: "tr")
        for (idx, tr) in trBlocks.enumerated() {
            guard let titleTd = HtmlParser.firstMatch(
                in: tr,
                pattern: #"<td\b[^>]*seasonEpisodeTitle[^>]*>(.*?)</td>"#
            ),
            let aTag = HtmlParser.firstMatch(in: titleTd, pattern: #"(<a\b[^>]*>.*?</a>)"#) else {
                continue
            }
            let epPath = HtmlParser.attr(aTag, "href")
            guard !epPath.isEmpty else { continue }

            let metaEpNum = HtmlParser.firstMatch(in: tr, pattern: #"(<meta\b[^>]*itemprop=["']episodeNumber["'][^>]*>)"#)
                .flatMap { Int(HtmlParser.attr($0, "content")) }
            let dataEpNum = Int(HtmlParser.attr(tr, "data-episode-season-id"))
            let epNum = metaEpNum ?? dataEpNum ?? (idx + 1)

            let titleDe = HtmlParser.firstMatch(in: aTag, pattern: #"<strong[^>]*>(.*?)</strong>"#)
                .map { HtmlParser.stripTagsAndDecode($0) } ?? ""
            let titleEn = HtmlParser.firstMatch(in: aTag, pattern: #"<span[^>]*>(.*?)</span>"#)
                .map { HtmlParser.stripTagsAndDecode($0) } ?? ""

            let editTd = HtmlParser.firstMatch(in: tr, pattern: #"<td\b[^>]*editFunctions[^>]*>(.*?)</td>"#) ?? tr
            var langs: [LanguageOption] = []
            for imgMatch in HtmlParser.allMatches(in: editTd, pattern: #"(<img\b[^>]*>)"#) {
                guard let imgTag = imgMatch.first else { continue }
                let flagInfo = "\(HtmlParser.attr(imgTag, "title")) \(HtmlParser.attr(imgTag, "src"))"
                if let lang = LanguageOption.fromImgOrTitle(flagInfo), !langs.contains(lang) {
                    langs.append(lang)
                }
            }

            episodes.append(
                EpisodeItem(
                    number: epNum,
                    seasonLabel: resolvedSeasonLabel,
                    path: epPath,
                    titleDe: titleDe,
                    titleEn: titleEn,
                    languages: langs
                )
            )
        }

        return AnimeDetail(
            slug: slug,
            title: title,
            description: description,
            coverUrl: coverUrl,
            backdropUrl: backdropUrl,
            years: years,
            ageRating: fsk,
            rating: rating,
            genres: genres,
            seasons: seasons,
            currentSeasonPath: currentPath,
            episodes: episodes
        )
    }

    public static func fetchEpisodeHosters(_ episodePath: String) async throws -> [HosterLink] {
        let (_, html) = try await HttpClient.getHtml(episodePath)
        return parseEpisodeHosters(html)
    }

    public static func parseEpisodeHosters(_ html: String) -> [HosterLink] {
        var hosters: [HosterLink] = []
        let liMatches = HtmlParser.allMatches(
            in: html,
            pattern: #"(<li\b[^>]*data-lang-key=["']\d+["'][^>]*>.*?</li>)"#
        )
        for m in liMatches {
            guard let li = m.first,
                  let langKey = Int(HtmlParser.attr(li, "data-lang-key")),
                  let language = LanguageOption.fromKey(langKey) else { continue }

            var redirect = HtmlParser.attr(li, "data-link-target")
            if redirect.isEmpty {
                if let watchA = HtmlParser.firstMatch(in: li, pattern: #"(<a\b[^>]*watchEpisode[^>]*>)"#) {
                    redirect = HtmlParser.attr(watchA, "href")
                }
            }
            guard !redirect.isEmpty else { continue }

            let h4 = HtmlParser.firstMatch(in: li, pattern: #"<h4[^>]*>(.*?)</h4>"#)
                .map { HtmlParser.stripTagsAndDecode($0) } ?? ""
            let hosterName = h4.isEmpty ? "Stream" : h4

            hosters.append(
                HosterLink(
                    hosterName: hosterName,
                    langKey: langKey,
                    language: language,
                    redirectPath: redirect
                )
            )
        }

        return hosters.sorted { a, b in
            let pa = hosterPriority(a.hosterName)
            let pb = hosterPriority(b.hosterName)
            if pa != pb { return pa < pb }
            return a.langKey < b.langKey
        }
    }

    private static func hosterPriority(_ name: String) -> Int {
        let lower = name.lowercased()
        if lower.contains("voe") { return 0 }
        if lower.contains("vidmoly") { return 1 }
        if lower.contains("speedfiles") || lower.contains("vidoza") { return 2 }
        if lower.contains("filemoon") { return 3 }
        if lower.contains("dood") { return 4 }
        return 5
    }

    private static func cleanTitle(_ raw: String) -> String {
        raw.replacingOccurrences(of: " Stream anschauen", with: "", options: .caseInsensitive)
            .replacingOccurrences(of: " kostenlos", with: "", options: .caseInsensitive)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
