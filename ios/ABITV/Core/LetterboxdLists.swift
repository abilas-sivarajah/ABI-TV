import Foundation

/// Watchlists und Listen von Letterboxd lesen (öffentliche Seiten, keine Anmeldung) und die Filme
/// mit Filmpalast abgleichen – wie auf PC und TV.
public enum LetterboxdLists {

    /// Ein Film auf Letterboxd ("Black Bag (2025)").
    public struct Film: Codable, Hashable, Sendable {
        public let name: String
        public let year: Int?
        public let slug: String
    }

    public struct ListInfo: Hashable, Sendable {
        public let name: String
        public let owner: String
        public let path: String
    }

    /// Feste Empfehlungsliste im Watchlist-Reiter.
    public enum FeaturedList {
        public static let name = "Ilkems Empfehlungen"
        public static let owner = "nerofreyz"
        public static let path = "/nerofreyz/list/ilkems-empfehlungen-1/"
    }

    public enum ListError: Error, Sendable {
        case userNotFound
        case http(Int)
    }

    private static let base = "https://letterboxd.com"
    private static let matchRetry: TimeInterval = 3 * 24 * 60 * 60

    /// Benutzernamen wie bei Letterboxd (Buchstaben, Ziffern, Unterstrich); auch ein Profil-Link wird akzeptiert.
    public static func normalizeUsername(_ input: String) -> String? {
        var raw = input.trimmingCharacters(in: .whitespacesAndNewlines)
        for prefix in ["https://", "http://", "www.", "letterboxd.com/"] where raw.lowercased().hasPrefix(prefix) {
            raw = String(raw.dropFirst(prefix.count))
        }
        raw = raw.trimmingCharacters(in: CharacterSet(charactersIn: "/@ "))
        raw = String(raw.split(separator: "/").first ?? "").lowercased()
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "_"))
        guard !raw.isEmpty, raw.unicodeScalars.allSatisfy({ allowed.contains($0) }) else { return nil }
        return raw
    }

    // ------------------- Letterboxd lesen -------------------

    private static func fetchHtml(_ path: String) async throws -> (Int, String) {
        guard let url = URL(string: path.hasPrefix("http") ? path : base + path) else { throw URLError(.badURL) }
        var request = URLRequest(url: url)
        request.setValue(LetterboxdRatings.browserUserAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("text/html,application/xhtml+xml", forHTTPHeaderField: "Accept")
        request.setValue("de-DE,de;q=0.9,en;q=0.8", forHTTPHeaderField: "Accept-Language")
        request.timeoutInterval = 20
        let (data, response) = try await HttpClient.perform(request)
        return ((response as? HTTPURLResponse)?.statusCode ?? 0, String(data: data, encoding: .utf8) ?? "")
    }

    static func parseFilms(_ html: String) -> [Film] {
        var seen = Set<String>()
        var films: [Film] = []
        for tag in HtmlParser.allMatches(in: html, pattern: #"<[a-z]+\b[^>]*data-item-name="[^"]*"[^>]*>"#).compactMap({ $0.first }) {
            let raw = HtmlParser.decodeEntities(HtmlParser.attr(tag, "data-item-name")).trimmingCharacters(in: .whitespaces)
            let slug = HtmlParser.attr(tag, "data-item-slug").trimmingCharacters(in: .whitespaces)
            guard !raw.isEmpty, !slug.isEmpty, seen.insert(slug).inserted else { continue }
            if let match = HtmlParser.allMatches(in: raw, pattern: #"^(.*)\s\((\d{4})\)$"#).first, match.count == 3 {
                films.append(Film(name: match[1].trimmingCharacters(in: .whitespaces), year: Int(match[2]), slug: slug))
            } else {
                films.append(Film(name: raw, year: nil, slug: slug))
            }
        }
        return films
    }

    /// Watchlist eines Benutzers (die ersten `maxPages` Seiten à 28 Filme).
    public static func watchlist(user: String, maxPages: Int = 2) async throws -> [Film] {
        let name = user.trimmingCharacters(in: CharacterSet(charactersIn: "@ ")).lowercased()
        var films: [Film] = []
        for page in 1...maxPages {
            let path = page == 1 ? "/\(name)/watchlist/" : "/\(name)/watchlist/page/\(page)/"
            let (code, html) = try await fetchHtml(path)
            if code == 404 && page == 1 { throw ListError.userNotFound }
            if code != 200 {
                if page == 1 { throw ListError.http(code) }
                break
            }
            let pageFilms = parseFilms(html)
            if pageFilms.isEmpty { break }
            films += pageFilms
        }
        return unique(films)
    }

    /// Beliebte Listen auf Letterboxd.
    public static func popularLists(limit: Int = 6) async throws -> [ListInfo] {
        let (code, html) = try await fetchHtml("/lists/popular/")
        guard code == 200 else { throw ListError.http(code) }
        var seen = Set<String>()
        var lists: [ListInfo] = []
        for match in HtmlParser.allMatches(in: html, pattern: #"<a\b([^>]*)href="(/[^"/]+/list/[^"/]+/)"([^>]*)>(.*?)</a>"#) {
            guard match.count >= 5 else { continue }
            let attributes = match[1] + match[3]
            if attributes.contains("poster-list-link") { continue }
            let path = match[2]
            let name = HtmlParser.stripTagsAndDecode(match[4]).trimmingCharacters(in: .whitespacesAndNewlines)
            let parts = path.split(separator: "/")
            guard parts.count == 3, !name.isEmpty, seen.insert(path).inserted else { continue }
            lists.append(ListInfo(name: name, owner: String(parts[0]), path: path))
            if lists.count >= limit { break }
        }
        return lists
    }

    /// Filme einer Liste (die ersten `maxPages` Seiten).
    public static func listFilms(path: String, maxPages: Int = 1) async throws -> [Film] {
        var films: [Film] = []
        for page in 1...maxPages {
            let pagePath = page == 1 ? path : "\(path.hasSuffix("/") ? String(path.dropLast()) : path)/page/\(page)/"
            let (code, html) = try await fetchHtml(pagePath)
            if code != 200 {
                if page == 1 { throw ListError.http(code) }
                break
            }
            let pageFilms = parseFilms(html)
            if pageFilms.isEmpty { break }
            films += pageFilms
        }
        return unique(films)
    }

    private static func unique(_ films: [Film]) -> [Film] {
        var seen = Set<String>()
        return films.filter { seen.insert($0.slug).inserted }
    }

    // ------------------- Abgleich mit Filmpalast -------------------

    private struct MatchEntry: Codable, Sendable {
        let card: AnimeCard?
        let time: TimeInterval
    }

    private static let matchCache = DiskCache<MatchEntry>(fileName: "letterboxd-matches.json")

    /// Den Film auf Filmpalast finden (nil = dort nicht vorhanden). Ergebnisse werden gemerkt.
    public static func findOnFilmpalast(_ film: Film) async -> AnimeCard? {
        if let entry = await matchCache.get(film.slug),
           entry.card != nil || Date().timeIntervalSince1970 - entry.time < matchRetry {
            return entry.card
        }
        let results = await FilmpalastScraper.searchMovies(film.name)
            .filter { !$0.title.localizedCaseInsensitiveContains("*ENGLISH*") }
        let card = pickBest(results, query: film.name, year: film.year)
        await matchCache.set(film.slug, MatchEntry(card: card, time: Date().timeIntervalSince1970))
        return card
    }

    /// Exakter Titel (und passendes Jahr, falls bekannt); sonst nur ein eindeutiger Treffer mit Untertitel.
    private static func pickBest(_ results: [AnimeCard], query: String, year: Int?) -> AnimeCard? {
        guard !results.isEmpty else { return nil }
        let wanted = normalize(query)
        func yearOk(_ card: AnimeCard) -> Bool {
            guard let year, let text = HtmlParser.firstMatch(in: card.genreOrSubtitle, pattern: #"((?:19|20)\d{2})"#),
                  let cardYear = Int(text) else { return true }
            return abs(cardYear - year) <= 1
        }
        if let exact = results.first(where: { normalize($0.title) == wanted && yearOk($0) }) { return exact }
        let lowered = query.lowercased()
        let prefixed = results.filter { card in
            let title = card.title.lowercased()
            return [" - ", ": ", " – "].contains { title.hasPrefix(lowered + $0) } && yearOk(card)
        }
        return prefixed.count == 1 ? prefixed[0] : nil
    }

    private static func normalize(_ text: String) -> String {
        KeylessArtwork.normalize(text.replacingOccurrences(of: "&", with: "und"))
    }
}
