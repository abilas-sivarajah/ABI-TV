import Foundation

/// Vollständiger Scraper für https://filmpalast.to inkl. Zweitquelle über HD-Filme (DeVideoSRC).
/// Entspricht 1:1 `FilmpalastScraper.kt` aus `:shared`.
public enum FilmpalastScraper {

    public static let baseUrl = "https://filmpalast.to"
    public static let prefix = "filmpalast:"
    private static let episodePrefix = "filmpalast_stream:"

    private static let lock = NSLock()
    private static var detailCache: [String: AnimeDetail] = [:]
    private static var categoryCache: [String: [AnimeCard]] = [:]

    public static let categories: [(String, String)] = [
        ("/movies/top", "🔥 Top-Filme"),
        ("/movies/new", "✨ Neu hinzugefügt"),
        ("/search/genre/Action", "💥 Action"),
        ("/search/genre/Krimi", "🔍 Krimi & Thriller"),
        ("/search/genre/Sci-Fi", "🚀 Sci-Fi"),
        ("/search/genre/Komödie", "😂 Komödie"),
        ("/search/genre/Abenteuer", "🗺️ Abenteuer"),
        ("/search/genre/Horror", "👻 Horror"),
        ("/search/genre/Fantasy", "🧙 Fantasy"),
        ("/search/genre/Animation", "🎨 Animation"),
        ("/search/genre/Drama", "🎭 Drama"),
        ("/search/genre/Familie", "👨‍👩‍👧 Familie")
    ]

    public static func isFilmpalastSlug(_ slug: String) -> Bool {
        slug.hasPrefix(prefix) || slug.contains("filmpalast.to")
    }

    public static func isFilmpalastEpisodePath(_ path: String) -> Bool {
        path.hasPrefix(episodePrefix)
    }

    public static func normalizePath(_ raw: String) -> String {
        var clean = raw
        for p in [prefix, baseUrl, "//filmpalast.to", episodePrefix] {
            if clean.hasPrefix(p) {
                clean = String(clean.dropFirst(p.count))
            }
        }
        clean = clean.trimmingCharacters(in: .whitespacesAndNewlines)
        if !clean.hasPrefix("/") {
            clean = "/\(clean)"
        }
        return clean
    }

    private static func buildPageUrl(basePath: String, page: Int) -> String {
        let clean = normalizePath(basePath)
        let encodedPath = clean.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? clean
        if page <= 1 {
            return "\(baseUrl)\(encodedPath)"
        }
        if clean.hasPrefix("/search/") {
            return "\(baseUrl)\(encodedPath)/\(page)"
        } else {
            return "\(baseUrl)\(encodedPath)/page/\(page)"
        }
    }

    public static func fetchHomeSections() async -> [CatalogSection] {
        async let top = fetchCategory("/movies/top", page: 1)
        async let newMovies = fetchCategory("/movies/new", page: 1)
        async let action = fetchCategory("/search/genre/Action", page: 1)
        async let thriller = fetchCategory("/search/genre/Krimi", page: 1)
        async let comedy = fetchCategory("/search/genre/Komödie", page: 1)

        let (topList, newList, actList, thrList, comList) = await (top, newMovies, action, thriller, comedy)
        var sections: [CatalogSection] = []
        if !topList.isEmpty {
            sections.append(CatalogSection(id: "filmpalast_top", title: "🔥 Beliebte Top-Filme auf Filmpalast", items: topList))
        }
        if !newList.isEmpty {
            sections.append(CatalogSection(id: "filmpalast_new", title: "✨ Neu hinzugefügte Filme", items: newList))
        }
        if !actList.isEmpty {
            sections.append(CatalogSection(id: "filmpalast_action", title: "💥 Action & Abenteuer Highlights", items: actList))
        }
        if !thrList.isEmpty {
            sections.append(CatalogSection(id: "filmpalast_thriller", title: "🔍 Krimi, Thriller & Spannung", items: thrList))
        }
        if !comList.isEmpty {
            sections.append(CatalogSection(id: "filmpalast_comedy", title: "😂 Beliebte Komödien", items: comList))
        }
        return sections
    }

    public static func fetchCategory(_ categoryPath: String, page: Int = 1) async -> [AnimeCard] {
        let cacheKey = "\(normalizePath(categoryPath))_page_\(page)"
        if page == 1, let cached = lock.withLock({ categoryCache[cacheKey] }) {
            return cached
        }

        let url = buildPageUrl(basePath: categoryPath, page: page)
        guard let (_, html) = try? await HttpClient.getHtml(url, referer: "\(baseUrl)/") else {
            return []
        }

        let items = parseArticles(html)
        if !items.isEmpty {
            lock.withLock { categoryCache[cacheKey] = items }
        }
        return items
    }

    public static func parseArticles(_ html: String) -> [AnimeCard] {
        // Doppelte Filme würden in den SwiftUI-Listen mit derselben ID kollidieren
        var seen = Set<String>()
        return HtmlParser.blocks(in: html, tag: "article")
            .compactMap { parseArticle($0) }
            .filter { seen.insert($0.slug).inserted }
    }

    private static func parseArticle(_ article: String) -> AnimeCard? {
        guard let aTag = HtmlParser.firstMatch(in: article, pattern: #"(<a\b[^>]*href=["'][^"']*/stream/[^"']+["'][^>]*>.*?</a>)"#) else {
            return nil
        }
        let rawHref = HtmlParser.attr(aTag, "href")
        guard !rawHref.isEmpty else { return nil }
        let href = normalizePath(rawHref)

        var title = HtmlParser.attr(aTag, "title")
        if title.isEmpty {
            title = HtmlParser.stripTagsAndDecode(aTag)
        }
        if title.isEmpty {
            title = HtmlParser.firstMatch(in: article, pattern: #"<h2[^>]*>(.*?)</h2>"#)
                .map { HtmlParser.stripTagsAndDecode($0) } ?? ""
        }
        guard !title.isEmpty, !href.isEmpty else { return nil }

        // Das erste <img> im Artikel ist oft die Bewertungsgrafik (star_on.png), nicht das Cover
        var rawCover = ""
        if let coverImg = HtmlParser.firstMatch(in: article, pattern: #"(<img\b[^>]*class=["'][^"']*cover-opacity[^"']*["'][^>]*>)"#) {
            rawCover = HtmlParser.attr(coverImg, "src")
        }
        if rawCover.isEmpty {
            for imgMatch in HtmlParser.allMatches(in: article, pattern: #"(<img\b[^>]*>)"#) {
                guard let imgTag = imgMatch.first else { continue }
                let src = HtmlParser.attr(imgTag, "src")
                if !src.isEmpty && !src.contains("/images/star_") {
                    rawCover = src
                    break
                }
            }
        }
        var cover = rawCover
        if cover.hasPrefix("//") {
            cover = "https:\(cover)"
        } else if cover.hasPrefix("/") {
            cover = "\(baseUrl)\(cover)"
        }

        let imdbText = HtmlParser.firstMatch(in: article, pattern: #"Imdb:\s*([0-9.]+(?:/10)?)"#) ?? ""
        let yearText = HtmlParser.firstMatch(in: article, pattern: #"(Jahr:\s*\d{4})"#) ?? ""

        let subtitle: String
        if title.range(of: "*ENGLISH*", options: .caseInsensitive) != nil {
            subtitle = "Englisch (OV)"
        } else if !yearText.isEmpty {
            subtitle = yearText
        } else if !imdbText.isEmpty {
            subtitle = "IMDb: \(imdbText)"
        } else {
            subtitle = ""
        }

        return AnimeCard(
            slug: "\(prefix)\(href)",
            title: title,
            coverUrl: cover,
            genreOrSubtitle: subtitle,
            rating: imdbText
        )
    }

    public static func fetchDetail(_ slug: String) async throws -> AnimeDetail {
        let path = normalizePath(slug)
        if let cached = lock.withLock({ detailCache[path] }) {
            return cached
        }

        let url = "\(baseUrl)\(path)"
        let (_, html) = try await HttpClient.getHtml(url, referer: "\(baseUrl)/")
        let detail = parseDetail(path: path, html: html)

        lock.withLock { detailCache[path] = detail }
        return detail
    }

    public static func parseDetail(path: String, html: String) -> AnimeDetail {
        let title = parseTitle(html)

        var cover = ""
        for pat in [
            #"(<img\b[^>]*class=["'][^"']*cover2[^"']*["'][^>]*>)"#,
            #"(<img\b[^>]*itemprop=["']image["'][^>]*>)"#,
            #"(<img\b[^>]*src=["'][^"']*/files/movies/[^"']*["'][^>]*>)"#
        ] {
            if let imgTag = HtmlParser.firstMatch(in: html, pattern: pat) {
                let src = HtmlParser.attr(imgTag, "src")
                if !src.isEmpty {
                    cover = src
                    break
                }
            }
        }
        if cover.hasPrefix("//") {
            cover = "https:\(cover)"
        } else if cover.hasPrefix("/") {
            cover = "\(baseUrl)\(cover)"
        }

        let descHtml = HtmlParser.firstMatch(in: html, pattern: #"<span\b[^>]*itemprop=["']description["'][^>]*>(.*?)</span>"#)
            ?? HtmlParser.firstMatch(in: html, pattern: #"<em\b[^>]*itemprop=["']name["'][^>]*>.*?</p>\s*<p\b[^>]*>(.*?)</p>"#)
            ?? ""
        let desc = HtmlParser.stripTagsAndDecode(descHtml)
        let yearText = parseYear(html)
        let imdbRating = HtmlParser.firstMatch(in: html, pattern: #"Imdb:\s*([0-9.]+(?:/10)?)"#) ?? ""

        // Nur der Block "Kategorien, Genre" des Films, sonst kämen alle Genres aus dem Seitenmenü mit
        var genres: [String] = []
        var seenGenres = Set<String>()
        let genreBlock = HtmlParser.firstMatch(
            in: html,
            pattern: #"<li\b[^>]*>(?:(?!</li>).)*?<p\b[^>]*>[^<]*Genre(?:(?!</li>).)*</li>"#,
            group: 0
        ) ?? ""
        for m in HtmlParser.allMatches(in: genreBlock, pattern: #"<a\b[^>]*href=["'][^"']*/search/genre/[^"']+["'][^>]*>(.*?)</a>"#) {
            guard m.count > 1 else { continue }
            let g = HtmlParser.stripTagsAndDecode(m[1])
            if !g.isEmpty && g.caseInsensitiveCompare("Englisch") != .orderedSame && seenGenres.insert(g).inserted {
                genres.append(g)
            }
        }

        let dummyEpisode = EpisodeItem(
            number: 1,
            seasonLabel: "Film",
            path: "\(episodePrefix)\(path)",
            titleDe: title.isEmpty ? "Hauptfilm" : title,
            titleEn: "",
            languages: [.gerDub]
        )

        return AnimeDetail(
            slug: "\(prefix)\(path)",
            title: title,
            description: desc,
            coverUrl: cover,
            backdropUrl: cover,
            years: yearText,
            ageRating: "",
            rating: imdbRating,
            genres: genres,
            seasons: [],
            currentSeasonPath: path,
            episodes: [dummyEpisode]
        )
    }

    private static func parseTitle(_ html: String) -> String {
        if let em = HtmlParser.firstMatch(in: html, pattern: #"<em\b[^>]*itemprop=["']name["'][^>]*>(.*?)</em>"#) {
            let t = HtmlParser.stripTagsAndDecode(em)
            if !t.isEmpty { return t }
        }
        if let h2 = HtmlParser.firstMatch(in: html, pattern: #"<h2\b[^>]*header-single[^>]*>(.*?)</h2>"#) {
            let t = HtmlParser.stripTagsAndDecode(h2)
            if !t.isEmpty { return t }
        }
        if let rawTitle = HtmlParser.firstMatch(in: html, pattern: #"<title>(.*?)</title>"#) {
            return HtmlParser.stripTagsAndDecode(rawTitle)
                .components(separatedBy: "Stream").first?
                .components(separatedBy: "kostenlos").first?
                .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        }
        return ""
    }

    private static func parseYear(_ html: String) -> String {
        // Auf dem sichtbaren Text suchen: Im Quelltext stehen oft Tags oder &ouml; dazwischen
        HtmlParser.firstMatch(in: HtmlParser.stripTagsAndDecode(html), pattern: #"Veröffentlicht:\s*(\d{4})"#) ?? ""
    }

    public static func fetchHostersForFilm(_ streamPath: String) async -> [HosterLink] {
        let path = normalizePath(streamPath)
        let cached = lock.withLock { detailCache[path] }

        // Wenn der Filmtitel bereits im Cache liegt, Filmpalast und HD-Filme parallel abfragen (wie in FilmpalastScraper.kt)
        let own: [HosterLink]
        let fallback: [HosterLink]
        if let cachedTitle = cached?.title, !cachedTitle.isEmpty {
            let cachedYear = cached?.years ?? ""
            async let htmlTask: String? = (try? await HttpClient.getHtml("\(baseUrl)\(path)", referer: "\(baseUrl)/"))?.body
            async let hdTask: [HosterLink] = fetchHdFilmeHosters(title: cachedTitle, year: cachedYear)
            let html = await htmlTask
            own = html.map { parseStreamLinks($0) } ?? []
            fallback = await hdTask
        } else {
            let html = (try? await HttpClient.getHtml("\(baseUrl)\(path)", referer: "\(baseUrl)/"))?.body
            own = html.map { parseStreamLinks($0) } ?? []
            let fallbackTitle = html.map { parseTitle($0) } ?? ""
            let fallbackYear = html.map { parseYear($0) } ?? ""
            if !fallbackTitle.isEmpty {
                fallback = await fetchHdFilmeHosters(title: fallbackTitle, year: fallbackYear)
            } else {
                fallback = []
            }
        }

        var seen = Set<String>()
        var combined: [HosterLink] = []
        for h in own + fallback {
            if seen.insert(h.redirectPath).inserted {
                combined.append(h)
            }
        }
        return combined.enumerated().sorted { a, b in
            let ra = hosterRank(a.element)
            let rb = hosterRank(b.element)
            return ra != rb ? ra < rb : a.offset < b.offset
        }.map(\.element)
    }

    private static func hosterRank(_ hoster: HosterLink) -> Int {
        switch hoster.hosterName.lowercased() {
        case "voe": return 0
        case "firestream": return 1
        case "vidmoly": return 2
        case "mixdrop": return 8
        case "doodstream": return 9
        default: return 5
        }
    }

    private static func fetchHdFilmeHosters(title: String, year: String) async -> [HosterLink] {
        let wanted = normalizeTitle(title)
        let candidates = await HdFilmeScraper.searchMovies(cleanTitle(title))
            .filter { normalizeTitle($0.title) == wanted }
            .prefix(3)

        let wantedYear = Int(year)
        for card in candidates {
            guard let detail = try? await HdFilmeScraper.fetchDetail(card.slug) else { continue }
            let foundYear = Int(detail.years)
            if let wy = wantedYear, let fy = foundYear, abs(wy - fy) > 1 { continue }
            guard let moviePath = detail.episodes.first?.path,
                  moviePath.hasPrefix("hdfilme_movie:") else { continue }
            let hosters = await HdFilmeScraper.fetchHostersForEpisode(moviePath)
            if !hosters.isEmpty { return hosters }
        }
        return []
    }

    private static func cleanTitle(_ title: String) -> String {
        title.replacingOccurrences(of: #"\*[^*]*\*"#, with: "", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func normalizeTitle(_ title: String) -> String {
        cleanTitle(title).lowercased().filter { $0.isLetter || $0.isNumber }
    }

    public static func parseStreamLinks(_ html: String) -> [HosterLink] {
        var hosterLinks: [HosterLink] = []
        let ulMatches = HtmlParser.allMatches(
            in: html,
            pattern: #"(<ul\b[^>]*class=["'][^"']*currentStreamLinks[^"']*["'][^>]*>.*?</ul>)"#
        )
        for m in ulMatches {
            guard let ul = m.first else { continue }
            let hostName = HtmlParser.firstMatch(in: ul, pattern: #"class=["'][^"']*hostName[^"']*["'][^>]*>(.*?)</"#)
                .map { HtmlParser.stripTagsAndDecode($0) } ?? "Stream"
            let playATag = HtmlParser.firstMatch(in: ul, pattern: #"(<a\b[^>]*class=["'][^"']*iconPlay[^"']*["'][^>]*>)"#)
                ?? HtmlParser.firstMatch(in: ul, pattern: #"(<a\b[^>]*href=["']https?://[^"']+["'][^>]*>)"#)
                ?? ""
            guard !playATag.isEmpty else { continue }

            let rawHref = HtmlParser.attr(playATag, "href")
            let href = !rawHref.isEmpty ? rawHref : HtmlParser.attr(playATag, "data-player-url")
            guard href.hasPrefix("http") else { continue }

            let cleanName: String
            if hostName.range(of: "VOE", options: .caseInsensitive) != nil || href.contains("voe.sx") {
                cleanName = "VOE"
            } else if hostName.range(of: "VIDARA", options: .caseInsensitive) != nil || href.contains("vidara") {
                cleanName = "Vidara"
            } else if hostName.range(of: "Streamtape", options: .caseInsensitive) != nil || href.contains("streamtape") {
                cleanName = "Streamtape"
            } else if hostName.range(of: "Firestream", options: .caseInsensitive) != nil || href.contains("firestream") {
                cleanName = "Firestream"
            } else if hostName.range(of: "Vixeo", options: .caseInsensitive) != nil || href.contains("vixeo") {
                cleanName = "Vixeo"
            } else {
                cleanName = hostName.replacingOccurrences(of: "HD", with: "").trimmingCharacters(in: .whitespacesAndNewlines)
            }

            hosterLinks.append(
                HosterLink(
                    hosterName: cleanName,
                    langKey: LanguageOption.gerDub.langKey,
                    language: .gerDub,
                    redirectPath: href
                )
            )
        }
        return hosterLinks
    }

    public static func searchMovies(_ query: String) async -> [AnimeCard] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 2 else { return [] }
        // Steht als ein Pfadsegment in der Adresse: Auch "/" und "&" im Suchbegriff müssen kodiert werden
        let encoded = HttpClient.percentEncodeStrict(trimmed)
        let url = "\(baseUrl)/search/title/\(encoded)"
        guard let (_, html) = try? await HttpClient.getHtml(url, referer: "\(baseUrl)/") else {
            return []
        }
        return parseArticles(html)
    }
}
