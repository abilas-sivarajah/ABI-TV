import Foundation

/// Vollständiger Parser & Resolver für https://hd-filme.one/ (Filme & Serien) inkl. DeVideoSRC-API.
/// Entspricht 1:1 `HdFilmeScraper.kt` aus `:shared`.
public enum HdFilmeScraper {

    public static let baseUrl = "https://hd-filme.one"
    public static let prefix = "hdfilme:"
    private static let deVideoSrcBase = "https://devideosrc.co"

    private static let lock = NSLock()
    private static var detailCache: [String: AnimeDetail] = [:]
    private static var episodeSourcesCache: [String: [HosterLink]] = [:]

    public static let categories: [(label: String, path: String)] = [
        ("🔥 Neuerscheinungen", "/kinofilme-online"),
        ("📺 Serien-Streams", "/serien-stream"),
        ("💥 Action", "/action"),
        ("😂 Komödie", "/komodie"),
        ("🔪 Horror", "/horror"),
        ("🚀 Sci-Fi", "/sci-fi"),
        ("🔍 Thriller", "/thriller"),
        ("🎭 Drama", "/drama"),
        ("⚔️ Abenteuer", "/abenteuer"),
        ("🎨 Animation", "/animation"),
        ("🧙 Fantasy", "/fantasy"),
        ("🕵️ Krimi", "/krimi")
    ]

    public static func isHdFilmeSlug(_ slugOrPath: String) -> Bool {
        slugOrPath.hasPrefix(prefix) || slugOrPath.contains("hd-filme.one")
    }

    public static func toRelativePath(_ slugOrPath: String) -> String {
        var raw = slugOrPath
        for p in [prefix, baseUrl] {
            if raw.hasPrefix(p) {
                raw = String(raw.dropFirst(p.count))
            }
        }
        raw = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return raw.hasPrefix("/") ? raw : "/\(raw)"
    }

    public static func fetchHomeSections() async throws -> [CatalogSection] {
        async let homeTask = HttpClient.getHtml("\(baseUrl)/", referer: "\(baseUrl)/")
        async let kinoTask: [AnimeCard] = (try? await fetchCategory("/kinofilme-online")) ?? []
        async let serienTask: [AnimeCard] = (try? await fetchCategory("/serien-stream")) ?? []
        async let actionTask: [AnimeCard] = (try? await fetchCategory("/action")) ?? []

        let (_, homeHtml) = try await homeTask
        let homeCards = parseMovieCards(homeHtml)
        let kino = await kinoTask
        let serien = await serienTask
        let action = await actionTask

        var sections: [CatalogSection] = []
        if !homeCards.isEmpty {
            sections.append(CatalogSection(id: "hdfilme_featured", title: "🔥 Beliebt auf HD-Filme", items: Array(homeCards.prefix(24))))
        }
        if !kino.isEmpty {
            sections.append(CatalogSection(id: "hdfilme_kino", title: "🎬 Aktuelle Kinofilme (HD)", items: kino))
        }
        if !serien.isEmpty {
            sections.append(CatalogSection(id: "hdfilme_serien", title: "📺 Serien auf HD-Filme", items: serien))
        }
        if !action.isEmpty {
            sections.append(CatalogSection(id: "hdfilme_action", title: "💥 Action-Kracher", items: action))
        }
        return sections
    }

    public static func fetchCategory(_ categoryPath: String, page: Int = 1) async throws -> [AnimeCard] {
        let rel = categoryPath.hasPrefix("/") ? categoryPath : "/\(categoryPath)"
        let url = page <= 1
            ? "\(baseUrl)\(rel)/"
            : "\(baseUrl)\(rel.trimmingCharacters(in: CharacterSet(charactersIn: "/")))/page/\(page)/"
        let (_, html) = try await HttpClient.getHtml(url, referer: "\(baseUrl)/")
        return parseMovieCards(html)
    }

    public static func searchMovies(_ query: String) async -> [AnimeCard] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 2 else { return [] }
        guard let html = try? await HttpClient.postForm(
            "\(baseUrl)/index.php?do=search",
            fields: [
                ("do", "search"),
                ("subaction", "search"),
                ("story", trimmed)
            ],
            referer: "\(baseUrl)/"
        ) else {
            return []
        }
        return parseMovieCards(html)
    }

    public static func parseMovieCards(_ html: String) -> [AnimeCard] {
        var cards: [AnimeCard] = []
        var seen = Set<String>()

        let aMatches = HtmlParser.allMatches(
            in: html,
            pattern: #"(<a\b[^>]*href=["'][^"']*\.html["'][^>]*>.*?</a>)"#
        )
        for m in aMatches {
            guard let aHtml = m.first else { continue }
            let href = HtmlParser.attr(aHtml, "href")
            if href.isEmpty || (!href.contains("/filme") && !href.contains("/serien")) { continue }

            guard let imgTag = HtmlParser.firstMatch(in: aHtml, pattern: #"(<img\b[^>]*>)"#) else { continue }
            let rawImg = !HtmlParser.attr(imgTag, "data-src").isEmpty
                ? HtmlParser.attr(imgTag, "data-src")
                : HtmlParser.attr(imgTag, "src")
            if rawImg.isEmpty || rawImg.contains("default-poster.svg") { continue }

            let relPath = href.replacingOccurrences(of: baseUrl, with: "")
            let slug = "\(prefix)\(relPath)"
            guard seen.insert(slug).inserted else { continue }

            var rawTitle = HtmlParser.attr(aHtml, "title")
            if rawTitle.isEmpty {
                rawTitle = HtmlParser.attr(imgTag, "alt")
                    .replacingOccurrences(of: "poster", with: "")
                    .replacingOccurrences(of: "hdfilme stream online", with: "")
                    .replacingOccurrences(of: "hdfilme", with: "")
                    .trimmingCharacters(in: .whitespacesAndNewlines)
            }
            if rawTitle.isEmpty {
                rawTitle = HtmlParser.stripTagsAndDecode(aHtml)
            }
            guard !rawTitle.isEmpty else { continue }

            cards.append(
                AnimeCard(
                    slug: slug,
                    title: rawTitle,
                    coverUrl: normalizeCover(rawImg),
                    genreOrSubtitle: "HD-Filme.one • Deutsch"
                )
            )
        }
        return cards
    }

    public static func fetchDetail(
        _ slugOrPath: String,
        seasonPathOverride: String? = nil
    ) async throws -> AnimeDetail {
        let relPath = toRelativePath(slugOrPath)
        let cacheKey = "\(relPath)|\(seasonPathOverride ?? "")"
        if let cached = lock.withLock({ detailCache[cacheKey] }) {
            return cached
        }

        let fullUrl = "\(baseUrl)\(relPath)"
        let (_, html) = try await HttpClient.getHtml(fullUrl, referer: "\(baseUrl)/")

        let title = HtmlParser.firstMatch(in: html, pattern: #"<h1[^>]*>(.*?)</h1>"#)
            .map { HtmlParser.stripTagsAndDecode($0) }
            ?? relPath.components(separatedBy: "/").last?.replacingOccurrences(of: ".html", with: "")
            ?? relPath

        let coverImg = HtmlParser.firstMatch(in: html, pattern: #"(<img\b[^>]*data-src=["'][^"']*/uploads/thumb/300x450[^"']*["'][^>]*>)"#) ?? ""
        let backdropImg = HtmlParser.firstMatch(in: html, pattern: #"(<img\b[^>]*data-src=["'][^"']*/uploads/thumb/1280x720[^"']*["'][^>]*>)"#) ?? ""

        let coverUrl = normalizeCover(HtmlParser.attr(coverImg, "data-src"))
        let normalizedBackdrop = normalizeCover(HtmlParser.attr(backdropImg, "data-src"))
        let backdropUrl = normalizedBackdrop.isEmpty ? coverUrl : normalizedBackdrop

        let metaDescTag = HtmlParser.firstMatch(in: html, pattern: #"(<meta\b[^>]*name=["']description["'][^>]*>)"#) ?? ""
        let metaDesc = HtmlParser.attr(metaDescTag, "content")
        let pDesc = HtmlParser.allMatches(in: html, pattern: #"<p[^>]*>(.*?)</p>"#)
            .compactMap { m -> String? in
                guard m.count > 1 else { return nil }
                let text = HtmlParser.stripTagsAndDecode(m[1])
                return text.count > 60 ? text : nil
            }
            .first
        let desc = pDesc ?? metaDesc

        let pageTitle = HtmlParser.firstMatch(in: html, pattern: #"<title>(.*?)</title>"#) ?? ""
        let releaseYear = HtmlParser.firstMatch(in: pageTitle, pattern: #"\((\d{4})\)"#) ?? ""

        var genres: [String] = []
        var seenGenres = Set<String>()
        for m in HtmlParser.allMatches(in: html, pattern: #"<span[^>]*>\s*Genre\s*:\s*</span>(.*?)</div"#) {
            guard m.count > 1 else { continue }
            for aMatch in HtmlParser.allMatches(in: m[1], pattern: #"<a[^>]*>(.*?)</a>"#) {
                guard aMatch.count > 1 else { continue }
                let g = HtmlParser.stripTagsAndDecode(aMatch[1])
                if !g.isEmpty && seenGenres.insert(g).inserted {
                    genres.append(g)
                }
            }
        }

        let movieFrameTag = HtmlParser.firstMatch(in: html, pattern: #"(<[^>]*id=["']hd-player-frame["'][^>]*>)"#) ?? ""
        let movieFrameSrc = HtmlParser.attr(movieFrameTag, "data-src")
        let seriesImdb = HtmlParser.firstMatch(in: html, pattern: #"var\s+imdb\s*=\s*['"](tt\d+)['"]"#)
        let fallbackEmbed = HtmlParser.firstMatch(in: html, pattern: #"var\s+FALLBACK\s*=\s*['"](https?://[^'"]+)['"]"#)

        let detail: AnimeDetail
        if !movieFrameSrc.isEmpty {
            let epKey = "hdfilme_movie:\(movieFrameSrc)"
            let singleEpisode = EpisodeItem(
                number: 1,
                seasonLabel: "Film",
                path: epKey,
                titleDe: "\(title) (Kompletter Film)",
                titleEn: "HD Stream",
                languages: [.gerDub]
            )
            detail = AnimeDetail(
                slug: "\(prefix)\(relPath)",
                title: title,
                description: desc,
                coverUrl: coverUrl,
                backdropUrl: backdropUrl,
                years: releaseYear,
                ageRating: "HD",
                rating: "",
                genres: genres,
                seasons: [],
                currentSeasonPath: relPath,
                episodes: [singleEpisode]
            )
        } else if let imdbId = seriesImdb {
            detail = await parseSeriesFromDeVideoSrc(
                slug: "\(prefix)\(relPath)",
                title: title,
                desc: desc,
                coverUrl: coverUrl,
                backdropUrl: backdropUrl,
                imdbId: imdbId,
                fallbackEmbed: fallbackEmbed,
                selectedSeasonStr: seasonPathOverride
            )
        } else {
            let epKey = "hdfilme_direct:\(fallbackEmbed ?? "")"
            detail = AnimeDetail(
                slug: "\(prefix)\(relPath)",
                title: title,
                description: desc,
                coverUrl: coverUrl,
                backdropUrl: backdropUrl,
                years: releaseYear,
                ageRating: "HD",
                rating: "",
                genres: genres,
                seasons: [],
                currentSeasonPath: relPath,
                episodes: [
                    EpisodeItem(
                        number: 1,
                        seasonLabel: "Film",
                        path: epKey,
                        titleDe: title,
                        titleEn: "",
                        languages: [.gerDub]
                    )
                ]
            )
        }

        lock.withLock { detailCache[cacheKey] = detail }
        return detail
    }

    private static func parseSeriesFromDeVideoSrc(
        slug: String,
        title: String,
        desc: String,
        coverUrl: String,
        backdropUrl: String,
        imdbId: String,
        fallbackEmbed: String?,
        selectedSeasonStr: String?
    ) async -> AnimeDetail {
        let serialUrl = "\(deVideoSrcBase)/serial/\(imdbId)"
        let serialHtml = (try? await HttpClient.getHtml(serialUrl, referer: "\(baseUrl)/"))?.body ?? ""

        if let token = HtmlParser.firstMatch(in: serialHtml, pattern: #"token:\s*["']([^"']+)["']"#) {
            await fetchAndCacheTvEmbedLinks(imdbId: imdbId, token: token, referer: serialUrl)
        }

        return parseDeVideoSrcSeriesHtml(
            slug: slug,
            title: title,
            desc: desc,
            coverUrl: coverUrl,
            backdropUrl: backdropUrl,
            imdbId: imdbId,
            fallbackEmbed: fallbackEmbed,
            selectedSeasonStr: selectedSeasonStr,
            serialHtml: serialHtml
        )
    }

    public static func parseDeVideoSrcSeriesHtml(
        slug: String,
        title: String,
        desc: String,
        coverUrl: String,
        backdropUrl: String,
        imdbId: String,
        fallbackEmbed: String?,
        selectedSeasonStr: String?,
        serialHtml: String
    ) -> AnimeDetail {
        var seasons: [SeasonLink] = []
        var seenSeasons = Set<String>()
        for m in HtmlParser.allMatches(in: serialHtml, pattern: #"(<[^>]*class=["'][^"']*_stab[^"']*["'][^>]*>)"#) {
            guard let tab = m.first else { continue }
            let sNum = HtmlParser.attr(tab, "data-season")
            if !sNum.isEmpty && seenSeasons.insert(sNum).inserted {
                seasons.append(SeasonLink(label: sNum, path: sNum, isMovies: false))
            }
        }

        let activeSeason = (selectedSeasonStr?.isEmpty == false ? selectedSeasonStr : nil)
            ?? seasons.first?.label
            ?? "1"

        // Isoliere die Episoden-Gruppe `_grp[data-season="<activeSeason>"]`, damit Episoden anderer
        // Staffeln nicht vermischt werden und verschachtelte `_ept` / `_epd` Divs erhalten bleiben.
        let grpParts = serialHtml.components(separatedBy: "_grp")
        var activeGroupChunk: String?
        if grpParts.count > 1 {
            for part in grpParts.dropFirst() {
                let openTagHeader = String(part.prefix(240))
                let grpSeason = HtmlParser.attr(openTagHeader, "data-season")
                if grpSeason == activeSeason {
                    activeGroupChunk = part
                    break
                }
            }
        }
        let targetChunk = activeGroupChunk ?? serialHtml

        var episodes: [EpisodeItem] = []
        let epRawParts = targetChunk.components(separatedBy: "_ep")
        if epRawParts.count > 1 {
            for part in epRawParts.dropFirst() {
                let trimmed = part.trimmingCharacters(in: .whitespacesAndNewlines)
                // Überspringe Unterklassen wie `_ept` oder `_epd`
                if let firstChar = trimmed.first,
                   firstChar.isLetter || firstChar.isNumber || firstChar == "-" || firstChar == "_" {
                    continue
                }
                let epChunk = String(part.prefix(2000))
                let sNumAttr = HtmlParser.attr(epChunk, "data-season")
                let sNum = sNumAttr.isEmpty ? activeSeason : sNumAttr
                guard sNum == activeSeason else { continue }

                let eNumAttr = HtmlParser.attr(epChunk, "data-episode")
                guard !eNumAttr.isEmpty || epChunk.contains("_ept") else { continue }
                let eNum = Int(eNumAttr) ?? (episodes.count + 1)

                let epTitle = HtmlParser.firstMatch(in: epChunk, pattern: #"class=["'][^"']*_ept[^"']*["'][^>]*>(.*?)</"#)
                    .map { HtmlParser.stripTagsAndDecode($0) } ?? ""
                let epDesc = HtmlParser.firstMatch(in: epChunk, pattern: #"class=["'][^"']*_epd[^"']*["'][^>]*>(.*?)</"#)
                    .map { HtmlParser.stripTagsAndDecode($0) } ?? ""
                let epKey = "hdfilme_tv:\(imdbId):\(sNum):\(eNum)"

                if let fb = fallbackEmbed, !fb.isEmpty {
                    lock.withLock {
                        if episodeSourcesCache[epKey] == nil {
                            episodeSourcesCache[epKey] = [
                                HosterLink(hosterName: "Stream", langKey: LanguageOption.gerDub.langKey, language: .gerDub, redirectPath: fb)
                            ]
                        }
                    }
                }

                episodes.append(
                    EpisodeItem(
                        number: eNum,
                        seasonLabel: sNum,
                        path: epKey,
                        titleDe: epTitle.isEmpty ? "Folge \(eNum)" : epTitle,
                        titleEn: String(epDesc.prefix(90)),
                        languages: [.gerDub]
                    )
                )
            }
        }

        return AnimeDetail(
            slug: slug,
            title: title,
            description: desc,
            coverUrl: coverUrl,
            backdropUrl: backdropUrl,
            years: "",
            ageRating: "Serie • HD",
            rating: "",
            genres: [],
            seasons: seasons,
            currentSeasonPath: activeSeason,
            episodes: episodes
        )
    }

    private static func fetchAndCacheTvEmbedLinks(imdbId: String, token: String, referer: String) async {
        let payloadDict: [String: Any] = ["type": "tv", "id": imdbId, "token": token]
        guard let payloadData = try? JSONSerialization.data(withJSONObject: payloadDict),
              let payloadStr = String(data: payloadData, encoding: .utf8),
              let jsonStr = try? await HttpClient.postJson("\(deVideoSrcBase)/api/embed-links", jsonBody: payloadStr, referer: referer),
              let rootData = jsonStr.data(using: .utf8),
              let root = (try? JSONSerialization.jsonObject(with: rootData)) as? [String: Any],
              (root["ok"] as? Bool) == true,
              let tvObj = root["tv"] as? [String: Any],
              let seasonsArr = tvObj["seasons"] as? [[String: Any]] else {
            return
        }

        for sObj in seasonsArr {
            let sNum = (sObj["season_number"] as? Int) ?? 1
            guard let episodesArr = sObj["episodes"] as? [[String: Any]] else { continue }
            for eObj in episodesArr {
                let eNum = (eObj["episode_number"] as? Int) ?? 1
                let sources = (eObj["sources"] as? [[String: Any]]) ?? []
                let hosters = parseSourcesArray(sources)
                if !hosters.isEmpty {
                    lock.withLock {
                        episodeSourcesCache["hdfilme_tv:\(imdbId):\(sNum):\(eNum)"] = hosters
                    }
                }
            }
        }
    }

    public static func fetchHostersForEpisode(_ episodePath: String) async -> [HosterLink] {
        if let cached = lock.withLock({ episodeSourcesCache[episodePath] }), !cached.isEmpty {
            return cached
        }

        if episodePath.hasPrefix("hdfilme_direct:") {
            let url = String(episodePath.dropFirst("hdfilme_direct:".count))
            if !url.isEmpty {
                return [HosterLink(hosterName: "Direct", langKey: LanguageOption.gerDub.langKey, language: .gerDub, redirectPath: url)]
            }
        }

        if episodePath.hasPrefix("hdfilme_tv:") {
            let parts = String(episodePath.dropFirst("hdfilme_tv:".count)).components(separatedBy: ":")
            let imdbId = parts.first ?? ""
            if !imdbId.isEmpty {
                let serialUrl = "\(deVideoSrcBase)/serial/\(imdbId)"
                if let (_, serialHtml) = try? await HttpClient.getHtml(serialUrl, referer: "\(baseUrl)/"),
                   let token = HtmlParser.firstMatch(in: serialHtml, pattern: #"token:\s*["']([^"']+)["']"#) {
                    await fetchAndCacheTvEmbedLinks(imdbId: imdbId, token: token, referer: serialUrl)
                }
                let found = lock.withLock { episodeSourcesCache[episodePath] }
                if let found, !found.isEmpty { return found }
            }
        }

        if episodePath.hasPrefix("hdfilme_movie:") {
            let embedUrl = String(episodePath.dropFirst("hdfilme_movie:".count))
            guard let (_, embedHtml) = try? await HttpClient.getHtml(embedUrl, referer: "\(baseUrl)/"),
                  let shellTag = HtmlParser.firstMatch(in: embedHtml, pattern: #"(<[^>]*class=["'][^"']*player-shell[^"']*["'][^>]*>)"#) else {
                return []
            }
            let typeAttr = HtmlParser.attr(shellTag, "data-type")
            let modeAttr = HtmlParser.attr(shellTag, "data-mode")
            let payloadDict: [String: Any] = [
                "type": typeAttr.isEmpty ? "movie" : typeAttr,
                "id": HtmlParser.attr(shellTag, "data-id"),
                "season": Int(HtmlParser.attr(shellTag, "data-season")) ?? 1,
                "episode": Int(HtmlParser.attr(shellTag, "data-episode")) ?? 1,
                "mode": modeAttr.isEmpty ? "custom" : modeAttr,
                "token": HtmlParser.attr(shellTag, "data-resolve-token")
            ]
            guard let payloadData = try? JSONSerialization.data(withJSONObject: payloadDict),
                  let payloadStr = String(data: payloadData, encoding: .utf8),
                  let jsonStr = try? await HttpClient.postJson("\(deVideoSrcBase)/api/resolve", jsonBody: payloadStr, referer: embedUrl),
                  let rootData = jsonStr.data(using: .utf8),
                  let root = (try? JSONSerialization.jsonObject(with: rootData)) as? [String: Any],
                  let sources = root["sources"] as? [[String: Any]] else {
                return []
            }
            let hosters = parseSourcesArray(sources)
            if !hosters.isEmpty {
                lock.withLock { episodeSourcesCache[episodePath] = hosters }
            }
            return hosters
        }

        return []
    }

    private static func parseSourcesArray(_ sourcesArr: [[String: Any]]) -> [HosterLink] {
        var list: [HosterLink] = []
        for obj in sourcesArr {
            let rawName = (obj["name"] as? String) ?? ""
            let url = (obj["url"] as? String) ?? ""
            guard !url.isEmpty else { continue }

            let lower = rawName.lowercased()
            let cleanName: String
            if lower.contains("voe") {
                cleanName = "VOE"
            } else if lower.contains("dood") {
                cleanName = "Doodstream"
            } else if lower.contains("mixdrop") {
                cleanName = "Mixdrop"
            } else if lower.contains("dropload") {
                cleanName = "Dropload"
            } else if lower.contains("vidmoly") {
                cleanName = "Vidmoly"
            } else if lower.contains("supervideo") {
                cleanName = "Supervideo"
            } else {
                cleanName = (rawName.components(separatedBy: ".").first ?? "Stream").capitalized
            }

            list.append(
                HosterLink(
                    hosterName: cleanName,
                    langKey: LanguageOption.gerDub.langKey,
                    language: .gerDub,
                    redirectPath: url
                )
            )
        }

        return list.enumerated().sorted { a, b in
            let ra = sourceRank(a.element.hosterName)
            let rb = sourceRank(b.element.hosterName)
            return ra != rb ? ra < rb : a.offset < b.offset
        }.map(\.element)
    }

    private static func sourceRank(_ name: String) -> Int {
        switch name.lowercased() {
        case "voe": return 0
        case "vidmoly": return 2
        case "dropload": return 3
        case "mixdrop": return 5
        case "doodstream": return 6
        default: return 4
        }
    }

    private static func normalizeCover(_ raw: String) -> String {
        guard !raw.isEmpty else { return "" }
        if raw.hasPrefix("http") { return raw }
        if raw.hasPrefix("//") { return "https:\(raw)" }
        if raw.hasPrefix("/") { return "\(baseUrl)\(raw)" }
        return "\(baseUrl)/\(raw)"
    }
}
