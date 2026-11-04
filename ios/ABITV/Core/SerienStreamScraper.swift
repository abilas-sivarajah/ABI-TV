import Foundation

/// Vollständiger Scraper für https://serienstream.to (S.to).
/// Entspricht 1:1 `SerienStreamScraper.kt` aus `:shared`.
public enum SerienStreamScraper {

    public static let baseUrl = "https://serienstream.to"
    public static let prefix = SerienStreamRef.prefix

    private static let lock = NSLock()
    private static var detailCache: [String: AnimeDetail] = [:]
    private static var cachedAtoZSeries: [AnimeCard]?
    private static var atoZFailedAt: Int64 = 0
    private static let atoZRetryDelayMs: Int64 = 30_000

    public static let categories: [(String, String)] = [
        ("/beliebte-serien", "🔥 Beliebte Serien"),
        ("/neu", "✨ Neue Serien"),
        ("/genre/action", "💥 Action"),
        ("/genre/drama", "🎭 Drama"),
        ("/genre/krimi", "🕵️ Krimi"),
        ("/genre/science-fiction", "🚀 Sci-Fi"),
        ("/genre/comedy", "😂 Comedy"),
        ("/genre/fantasy", "🪄 Fantasy"),
        ("/genre/thriller", "🔪 Thriller"),
        ("/genre/horror", "👻 Horror"),
        ("/genre/abenteuer", "🗺️ Abenteuer"),
        ("/genre/mystery", "🔍 Mystery"),
        ("/genre/animation", "🎨 Animation"),
        ("/genre/dokumentation", "📚 Doku")
    ]

    public static func isStoSlug(_ slugOrPath: String) -> Bool {
        SerienStreamRef.matches(slugOrPath)
    }

    public static func toCleanSlug(_ slugOrPath: String) -> String {
        SerienStreamRef.bareSlug(slugOrPath)
    }

    public static func fetchHomeSections() async -> [CatalogSection] {
        async let popular = (try? fetchCategory("/beliebte-serien")) ?? []
        async let action = (try? fetchCategory("/genre/action")) ?? []
        async let scifi = (try? fetchCategory("/genre/science-fiction")) ?? []
        async let drama = (try? fetchCategory("/genre/drama")) ?? []
        async let krimi = (try? fetchCategory("/genre/krimi")) ?? []

        let (popList, actList, sciList, draList, kriList) = await (popular, action, scifi, drama, krimi)
        var sections: [CatalogSection] = []
        if !popList.isEmpty {
            sections.append(CatalogSection(id: "sto_popular", title: "🔥 Beliebte Serien (S.to)", items: Array(popList.prefix(24))))
        }
        if !actList.isEmpty {
            sections.append(CatalogSection(id: "sto_action", title: "💥 Action & Spannung", items: Array(actList.prefix(24))))
        }
        if !sciList.isEmpty {
            sections.append(CatalogSection(id: "sto_scifi", title: "🚀 Sci-Fi & Fantasy", items: Array(sciList.prefix(24))))
        }
        if !draList.isEmpty {
            sections.append(CatalogSection(id: "sto_drama", title: "🎭 Drama & Emotionen", items: Array(draList.prefix(24))))
        }
        if !kriList.isEmpty {
            sections.append(CatalogSection(id: "sto_krimi", title: "🕵️ Krimi & Thriller", items: Array(kriList.prefix(24))))
        }
        return sections
    }

    public static func fetchCategory(_ categoryPath: String) async throws -> [AnimeCard] {
        let fullUrl = categoryPath.hasPrefix("http") ? categoryPath : "\(baseUrl)\(categoryPath)"
        let (_, html) = try await HttpClient.getHtml(fullUrl, referer: "\(baseUrl)/")
        return parseSeriesCards(html)
    }

    public static func parseSeriesCards(_ html: String) -> [AnimeCard] {
        var cards: [AnimeCard] = []
        var seen = Set<String>()

        let linkMatches = HtmlParser.allMatches(
            in: html,
            pattern: #"(<a\b[^>]*href=["']/serie/[^"']+["'][^>]*>.*?</a>)"#
        )
        for m in linkMatches {
            guard let aHtml = m.first else { continue }
            let href = HtmlParser.attr(aHtml, "href")
            if href.isEmpty || href.contains("/staffel-") { continue }

            let slug = href
                .replacingOccurrences(of: "/serie/", with: "")
                .components(separatedBy: "/").first?
                .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard !slug.isEmpty, seen.insert(slug).inserted else { continue }

            let imgTag = HtmlParser.firstMatch(in: aHtml, pattern: #"(<img\b[^>]*>)"#) ?? ""
            let src = HtmlParser.attr(imgTag, "src")
            let dataSrc = HtmlParser.attr(imgTag, "data-src")
            let rawCover: String
            if !src.isEmpty && !src.hasPrefix("data:") {
                rawCover = src
            } else {
                rawCover = dataSrc
            }

            let h3Title = HtmlParser.firstMatch(in: aHtml, pattern: #"<h3[^>]*>(.*?)</h3>"#)
                .map { HtmlParser.stripTagsAndDecode($0) } ?? ""
            let altTitle = HtmlParser.attr(imgTag, "alt")
            let textTitle = HtmlParser.stripTagsAndDecode(aHtml)
            let title = !h3Title.isEmpty ? h3Title : (!altTitle.isEmpty ? altTitle : textTitle)

            let coverUrl: String
            if !rawCover.isEmpty {
                coverUrl = rawCover.hasPrefix("http") ? rawCover : "\(baseUrl)\(rawCover)"
            } else {
                coverUrl = ""
            }

            if !title.isEmpty {
                cards.append(
                    AnimeCard(
                        slug: "\(prefix)\(slug)",
                        title: title,
                        coverUrl: coverUrl,
                        genreOrSubtitle: "Serie • S.to"
                    )
                )
            }
        }
        return cards
    }

    public static func fetchDetail(_ slugOrPath: String, requestedSeasonPath: String? = nil) async throws -> AnimeDetail {
        let cleanSlug = toCleanSlug(slugOrPath)
        let cacheKey = "\(cleanSlug):\(requestedSeasonPath ?? "")"

        if let cached = lock.withLock({ detailCache[cacheKey] }) {
            return cached
        }

        let targetPath: String
        if let req = requestedSeasonPath, !req.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            targetPath = req.hasPrefix("http") ? req : "\(baseUrl)\(req)"
        } else {
            targetPath = "\(baseUrl)/serie/\(cleanSlug)/staffel-1"
        }

        var (currentUrl, html) = try await HttpClient.getHtml(targetPath, referer: "\(baseUrl)/")
        if !html.contains("episode-row") && (requestedSeasonPath ?? "").isEmpty {
            let fallback = try await HttpClient.getHtml("\(baseUrl)/serie/\(cleanSlug)", referer: "\(baseUrl)/")
            currentUrl = fallback.finalUrl
            html = fallback.body
        }

        let detail = parseDetail(cleanSlug: cleanSlug, currentUrl: currentUrl, html: html)
        lock.withLock { detailCache[cacheKey] = detail }
        return detail
    }

    public static func parseDetail(cleanSlug: String, currentUrl: String, html: String) -> AnimeDetail {
        let h1 = HtmlParser.firstMatch(in: html, pattern: #"<h1[^>]*>(.*?)</h1>"#)
            .map { HtmlParser.stripTagsAndDecode($0) } ?? ""
        let title = !h1.isEmpty ? h1 : cleanSlug.replacingOccurrences(of: "-", with: " ").capitalized

        let descHtml = HtmlParser.firstMatch(in: html, pattern: #"class=["'][^"']*series-description[^"']*["'][^>]*>(.*?)</"#)
            ?? HtmlParser.firstMatch(in: html, pattern: #"itemprop=["']description["'][^>]*>(.*?)</"#)
            ?? HtmlParser.firstMatch(in: html, pattern: #"<p\b[^>]*class=["'][^"']*text-white-50[^"']*["'][^>]*>(.*?)</p>"#)
            ?? ""
        let description = HtmlParser.stripTagsAndDecode(descHtml)

        let coverTag = HtmlParser.firstMatch(in: html, pattern: #"(<img\b[^>]*(?:data-src|src)=["'][^"']*/media/images/channel/[^"']*["'][^>]*>)"#) ?? ""
        let rawCover = !HtmlParser.attr(coverTag, "data-src").isEmpty
            ? HtmlParser.attr(coverTag, "data-src")
            : HtmlParser.attr(coverTag, "src")
        let coverUrl = !rawCover.isEmpty ? (rawCover.hasPrefix("http") ? rawCover : "\(baseUrl)\(rawCover)") : ""

        let backdropTag = HtmlParser.firstMatch(in: html, pattern: #"(<img\b[^>]*(?:data-src|src)=["'][^"']*/media/images/backdrop/[^"']*["'][^>]*>)"#) ?? ""
        let rawBackdrop = !HtmlParser.attr(backdropTag, "data-src").isEmpty
            ? HtmlParser.attr(backdropTag, "data-src")
            : HtmlParser.attr(backdropTag, "src")
        let backdropUrl = !rawBackdrop.isEmpty ? (rawBackdrop.hasPrefix("http") ? rawBackdrop : "\(baseUrl)\(rawBackdrop)") : coverUrl

        var seasons: [SeasonLink] = []
        var seenSeasons = Set<String>()
        let seasonPattern = #"(<a\b[^>]*href=["'][^"']*/serie/\#(NSRegularExpression.escapedPattern(for: cleanSlug))/staffel-[^"']+["'][^>]*>(.*?)</a>)"#
        for m in HtmlParser.allMatches(in: html, pattern: seasonPattern) {
            guard m.count > 2 else { continue }
            let aTag = m[1]
            let href = HtmlParser.attr(aTag, "href")
            if href.isEmpty || href.contains("/episode-") { continue }
            let seasonPart = href.components(separatedBy: "/").last ?? ""
            guard !seasonPart.isEmpty, seenSeasons.insert(seasonPart).inserted else { continue }
            let linkText = HtmlParser.stripTagsAndDecode(m[2])
            let label = !linkText.isEmpty
                ? linkText
                : (seasonPart == "staffel-0" ? "Filme" : seasonPart.replacingOccurrences(of: "staffel-", with: ""))
            seasons.append(SeasonLink(label: label, path: href))
        }
        if seasons.isEmpty {
            seasons.append(SeasonLink(label: "1", path: "/serie/\(cleanSlug)/staffel-1"))
        }

        let seasonNumber = HtmlParser.firstMatch(in: currentUrl, pattern: #"staffel-([0-9]+)"#) ?? "1"
        let seasonLabel = seasonNumber == "0" ? "Filme" : seasonNumber

        var episodes: [EpisodeItem] = []
        // Komplette Zeile samt öffnendem Tag: Das onclick mit dem Folgenpfad steht oft vor dem class-Attribut
        for row in HtmlParser.elements(in: html, withClass: "episode-row") {
            let rowChunk = row.html
            let numText = HtmlParser.firstMatch(in: rowChunk, pattern: #"episode-number-cell[^>]*>(.*?)</"#)
                .map { HtmlParser.stripTagsAndDecode($0) } ?? ""
            let number = Int(numText) ?? (episodes.count + 1)

            let titleDe = HtmlParser.firstMatch(in: rowChunk, pattern: #"episode-title-ger[^>]*>(.*?)</"#)
                .map { HtmlParser.stripTagsAndDecode($0) } ?? ""
            let titleEn = HtmlParser.firstMatch(in: rowChunk, pattern: #"episode-title-eng[^>]*>(.*?)</"#)
                .map { HtmlParser.stripTagsAndDecode($0) } ?? ""

            let epPath = HtmlParser.firstMatch(in: rowChunk, pattern: #"['"](/serie/[^'"]+/episode-[0-9]+)['"]"#)
                ?? "/serie/\(cleanSlug)/staffel-\(seasonNumber)/episode-\(number)"

            let finalPath = "sto_\(cleanSlug)_s\(seasonLabel)_e\(number)_\(epPath)"
            episodes.append(
                EpisodeItem(
                    number: number,
                    seasonLabel: seasonLabel,
                    path: finalPath,
                    titleDe: titleDe,
                    titleEn: titleEn,
                    languages: []
                )
            )
        }

        let currentSeasonPath = currentUrl.replacingOccurrences(of: baseUrl, with: "")
        return AnimeDetail(
            slug: "\(prefix)\(cleanSlug)",
            title: title,
            description: description,
            coverUrl: coverUrl,
            backdropUrl: backdropUrl,
            years: "",
            ageRating: "16+",
            rating: "⭐ 8.5",
            genres: ["Serie", "S.to"],
            seasons: seasons,
            currentSeasonPath: currentSeasonPath,
            episodes: episodes
        )
    }

    public static func fetchEpisodeHosters(_ episodePath: String) async throws -> [HosterLink] {
        let realPath: String
        if episodePath.hasPrefix("sto_") {
            if let range = episodePath.range(of: "/serie/") {
                realPath = String(episodePath[range.lowerBound...])
            } else {
                realPath = String(episodePath.dropFirst(4))
            }
        } else {
            realPath = episodePath
        }

        let fullUrl = realPath.hasPrefix("http") ? realPath : "\(baseUrl)\(realPath)"
        let (_, html) = try await HttpClient.getHtml(fullUrl, referer: "\(baseUrl)/")
        return parseEpisodeHosters(html)
    }

    public static func parseEpisodeHosters(_ html: String) -> [HosterLink] {
        var hosters: [HosterLink] = []
        let btnMatches = HtmlParser.allMatches(
            in: html,
            pattern: #"(<button\b[^>]*class=["'][^"']*link-box[^"']*["'][^>]*>)"#
        )
        for m in btnMatches {
            guard let btn = m.first else { continue }
            let playUrl = HtmlParser.attr(btn, "data-play-url")
            guard !playUrl.isEmpty else { continue }

            let providerName = HtmlParser.attr(btn, "data-provider-name")
            let hosterName = providerName.isEmpty ? "Stream" : providerName
            let langId = Int(HtmlParser.attr(btn, "data-language-id")) ?? 1
            let lang: LanguageOption
            switch langId {
            case 2: lang = .engSub
            case 3: lang = .gerSub
            default: lang = .gerDub
            }
            let fullRedirectUrl = playUrl.hasPrefix("http") ? playUrl : "\(baseUrl)\(playUrl)"
            hosters.append(
                HosterLink(
                    hosterName: hosterName,
                    langKey: lang.langKey,
                    language: lang,
                    redirectPath: fullRedirectUrl
                )
            )
        }
        return hosters
    }

    public static func searchSeries(_ query: String) async -> [AnimeCard] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard trimmed.count >= 2 else { return [] }

        let catalog = await loadAtoZSeries()
        return Array(
            catalog.filter {
                $0.title.lowercased().contains(trimmed) || $0.cleanSlug.lowercased().contains(trimmed)
            }.prefix(30)
        )
    }

    private static func loadAtoZSeries() async -> [AnimeCard] {
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        let cachedOrBlocked: [AnimeCard]? = lock.withLock {
            if let cached = cachedAtoZSeries {
                return cached
            }
            if nowMs - atoZFailedAt < atoZRetryDelayMs {
                return []
            }
            return nil
        }
        if let cachedOrBlocked {
            return cachedOrBlocked
        }

        guard let (_, html) = try? await HttpClient.getHtml("\(baseUrl)/serien", referer: "\(baseUrl)/") else {
            lock.withLock { atoZFailedAt = nowMs }
            return []
        }

        var items: [AnimeCard] = []
        var seen = Set<String>()
        let liMatches = HtmlParser.allMatches(
            in: html,
            pattern: #"<li\b[^>]*class=["'][^"']*series-item[^"']*["'][^>]*>.*?(<a\b[^>]*>(.*?)</a>).*?</li>"#
        )
        for m in liMatches {
            guard m.count > 2 else { continue }
            let aTag = m[1]
            let slug = toCleanSlug(HtmlParser.attr(aTag, "href"))
            let title = HtmlParser.stripTagsAndDecode(m[2])
            guard !slug.isEmpty, !title.isEmpty, seen.insert(slug).inserted else { continue }
            items.append(
                AnimeCard(
                    slug: "\(prefix)\(slug)",
                    title: title,
                    coverUrl: "",
                    genreOrSubtitle: "Serie • S.to"
                )
            )
        }

        lock.withLock {
            if items.isEmpty {
                atoZFailedAt = nowMs
            } else {
                cachedAtoZSeries = items
            }
        }
        return items
    }
}
