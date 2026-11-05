import Foundation

/// Breites Hintergrundbild (und ggf. Logo) zu einem Titel; bei Filmen mit IMDb-Nummer für die Letterboxd-Bewertung.
public struct TitleArtwork: Codable, Hashable, Sendable {
    public let backdropUrl: String?
    public let logoUrl: String?
    public let isMovie: Bool
    public let imdbId: String?

    public init(backdropUrl: String?, logoUrl: String?, isMovie: Bool, imdbId: String?) {
        self.backdropUrl = backdropUrl
        self.logoUrl = logoUrl
        self.isMovie = isMovie
        self.imdbId = imdbId
    }
}

/// Kleiner Zwischenspeicher auf der Platte (Caches-Ordner), threadsicher.
actor DiskCache<Value: Codable & Sendable> {
    private let fileURL: URL
    private var entries: [String: Value]

    init(fileName: String) {
        let dir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSTemporaryDirectory())
        fileURL = dir.appendingPathComponent(fileName)
        entries = (try? Data(contentsOf: fileURL)).flatMap { try? JSONDecoder().decode([String: Value].self, from: $0) } ?? [:]
    }

    func get(_ key: String) -> Value? { entries[key] }

    func set(_ key: String, _ value: Value) {
        entries[key] = value
        if let data = try? JSONEncoder().encode(entries) {
            try? data.write(to: fileURL, options: .atomic)
        }
    }
}

/// JSON-Hilfen für die Bild- und Bewertungsquellen.
enum JsonFetch {
    /// Wikidata und Co. wünschen einen aussagekräftigen Programmnamen (keine Kontaktdaten nötig).
    static let userAgent = "ABI-TV/1.0 (private iOS app)"

    static func get(_ base: String, params: [(String, String)] = [], accept: String = "application/json") async throws -> Any {
        guard var components = URLComponents(string: base) else { throw URLError(.badURL) }
        if !params.isEmpty { components.queryItems = params.map { URLQueryItem(name: $0.0, value: $0.1) } }
        guard let url = components.url else { throw URLError(.badURL) }
        var request = URLRequest(url: url)
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue(accept, forHTTPHeaderField: "Accept")
        request.timeoutInterval = 20
        let (data, response) = try await HttpClient.perform(request)
        if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            throw URLError(.badServerResponse)
        }
        return try JSONSerialization.jsonObject(with: data)
    }

    static func post(_ urlString: String, body: Any) async throws -> Any {
        guard let url = URL(string: urlString) else { throw URLError(.badURL) }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        request.timeoutInterval = 20
        let (data, _) = try await HttpClient.perform(request)
        return try JSONSerialization.jsonObject(with: data)
    }
}

extension Dictionary where Key == String, Value == Any {
    func string(_ key: String) -> String? {
        (self[key] as? String).flatMap { $0.isEmpty ? nil : $0 }
    }
    func dict(_ key: String) -> [String: Any]? { self[key] as? [String: Any] }
    func array(_ key: String) -> [Any] { self[key] as? [Any] ?? [] }
}

/// Hintergrundbilder ohne API-Schlüssel (wie auf PC und TV):
/// - Serien: TVmaze (Bilder unter CC BY-SA)
/// - Anime: Kitsu, notfalls AniList
/// - Filme: Wikidata (deutscher Titel + Jahr → IMDb-Nummer) → Letterboxd-Filmseite (Bild + Bewertung)
/// Treffer werden 30 Tage, Nicht-Treffer 3 Tage gemerkt.
public enum KeylessArtwork {

    public enum Kind: String, Sendable { case movie, tv, anime }

    private struct Entry: Codable, Sendable {
        let backdrop: String?
        let logo: String?
        let imdb: String?
        let time: TimeInterval
    }

    private static let hitRefresh: TimeInterval = 30 * 24 * 60 * 60
    private static let missRetry: TimeInterval = 3 * 24 * 60 * 60
    private static let cache = DiskCache<Entry>(fileName: "keyless-artwork.json")

    public static func find(title: String, year: Int?, kind: Kind) async -> TitleArtwork? {
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        let key = "\(kind.rawValue)|\(normalize(trimmed))|\(year.map(String.init) ?? "")"
        let now = Date().timeIntervalSince1970
        if let entry = await cache.get(key) {
            let isMiss = entry.backdrop == nil && entry.imdb == nil
            if !isMiss && now - entry.time < hitRefresh { return artwork(entry, kind: kind) }
            if isMiss && now - entry.time < missRetry { return nil }
        }
        let found: TitleArtwork?
        do {
            switch kind {
            case .tv: found = try await tvmaze(trimmed, year: year)
            case .anime:
                // Fällt Kitsu aus, trotzdem AniList versuchen
                if let fromKitsu = try? await kitsu(trimmed, year: year) { found = fromKitsu } else { found = try await anilist(trimmed) }
            case .movie: found = try await movie(trimmed, year: year)
            }
        } catch {
            return nil // Netzwerkfehler nicht als "nicht gefunden" merken
        }
        await cache.set(key, Entry(backdrop: found?.backdropUrl, logo: found?.logoUrl, imdb: found?.imdbId, time: now))
        return found
    }

    private static func artwork(_ entry: Entry, kind: Kind) -> TitleArtwork {
        TitleArtwork(backdropUrl: entry.backdrop, logoUrl: entry.logo, isMovie: kind == .movie, imdbId: entry.imdb)
    }

    // ------------------- Serien: TVmaze -------------------

    private static func tvmaze(_ title: String, year: Int?) async throws -> TitleArtwork? {
        let results = (try await JsonFetch.get("https://api.tvmaze.com/search/shows", params: [("q", title)]) as? [Any] ?? [])
            .compactMap { ($0 as? [String: Any])?.dict("show") }
        let wanted = normalize(title)
        func yearOf(_ show: [String: Any]) -> Int? { show.string("premiered").flatMap { Int($0.prefix(4)) } }
        var show = results.first { s in
            normalize(s.string("name") ?? "") == wanted && (year == nil || yearOf(s) == nil || abs(yearOf(s)! - year!) <= 1)
        } ?? results.first { normalize($0.string("name") ?? "") == wanted }
        if show == nil, let imdb = try? await wikidataImdb(title, year: year, film: false) {
            // S.to führt viele Serien unter dem deutschen Titel, TVmaze unter dem englischen
            show = (try? await JsonFetch.get("https://api.tvmaze.com/lookup/shows", params: [("imdb", imdb)])) as? [String: Any]
        }
        guard let show, let id = show["id"] as? Int else { return nil }
        let images = (try await JsonFetch.get("https://api.tvmaze.com/shows/\(id)/images") as? [Any] ?? [])
            .compactMap { $0 as? [String: Any] }
        func original(_ img: [String: Any]) -> [String: Any]? { img.dict("resolutions")?.dict("original") }
        let background = images
            .filter { $0.string("type") == "background" }
            .max { min(original($0)?["width"] as? Int ?? 0, 3840) < min(original($1)?["width"] as? Int ?? 0, 3840) }
        let logo = images.first { $0.string("type") == "typography" }
        let backdropUrl = background.flatMap { original($0)?.string("url") }
        let imdb = show.dict("externals")?.string("imdb")
        if backdropUrl == nil && imdb == nil { return nil }
        return TitleArtwork(backdropUrl: backdropUrl, logoUrl: logo.flatMap { original($0)?.string("url") }, isMovie: false, imdbId: imdb)
    }

    // ------------------- Anime: Kitsu, AniList -------------------

    private static func kitsu(_ title: String, year: Int?) async throws -> TitleArtwork? {
        let root = try await JsonFetch.get(
            "https://kitsu.io/api/edge/anime",
            params: [("filter[text]", title), ("page[limit]", "5")],
            accept: "application/vnd.api+json"
        ) as? [String: Any]
        let data = (root?.array("data") ?? []).compactMap { ($0 as? [String: Any])?.dict("attributes") }
        let wanted = normalize(title)
        func names(_ a: [String: Any]) -> [String] {
            var list: [String] = []
            if let canonical = a.string("canonicalTitle") { list.append(canonical) }
            if let titles = a.dict("titles") { list += titles.values.compactMap { $0 as? String } }
            return list.map(normalize)
        }
        func yearOk(_ a: [String: Any]) -> Bool {
            guard let year, let start = a.string("startDate").flatMap({ Int($0.prefix(4)) }) else { return true }
            return abs(start - year) <= 1
        }
        guard let match = data.first(where: { names($0).contains(wanted) && yearOk($0) }) ?? data.first(where: { names($0).contains(wanted) }),
              let cover = match.dict("coverImage")?.string("original") else { return nil }
        return TitleArtwork(backdropUrl: cover, logoUrl: nil, isMovie: false, imdbId: nil)
    }

    private static func anilist(_ title: String) async throws -> TitleArtwork? {
        let body: [String: Any] = [
            "query": "query($s:String){Media(search:$s,type:ANIME){bannerImage}}",
            "variables": ["s": title]
        ]
        let json = try await JsonFetch.post("https://graphql.anilist.co", body: body) as? [String: Any]
        guard let banner = json?.dict("data")?.dict("Media")?.string("bannerImage") else { return nil }
        return TitleArtwork(backdropUrl: banner, logoUrl: nil, isMovie: false, imdbId: nil)
    }

    // ------------------- Filme: Wikidata → IMDb → Letterboxd -------------------

    private static func movie(_ title: String, year: Int?) async throws -> TitleArtwork? {
        // Filmpalast-Titel haben oft einen Zusatz ("Riddick - Überleben ist seine Rache"): notfalls ohne ihn suchen
        var candidates = [title]
        for separator in [" - ", " – ", ": "] where title.contains(separator) {
            if let first = title.components(separatedBy: separator).first?.trimmingCharacters(in: .whitespaces), first.count >= 2 {
                candidates.append(first)
                break
            }
        }
        var imdb: String?
        for candidate in candidates {
            if let found = try await wikidataImdb(candidate, year: year, film: true) {
                imdb = found
                break
            }
        }
        guard let imdb else { return nil }
        let letterboxd = await LetterboxdRatings.byImdb(imdb)
        return TitleArtwork(backdropUrl: letterboxd?.backdropUrl, logoUrl: nil, isMovie: true, imdbId: imdb)
    }

    /// IMDb-Nummer eines Films bzw. einer Serie über Wikidata (deutsche Bezeichnungen, Jahr ±1).
    private static func wikidataImdb(_ title: String, year: Int?, film: Bool) async throws -> String? {
        let search = try await JsonFetch.get("https://www.wikidata.org/w/api.php", params: [
            ("action", "wbsearchentities"), ("format", "json"), ("language", "de"), ("uselang", "de"),
            ("type", "item"), ("limit", "7"), ("search", title)
        ]) as? [String: Any]
        let ids = (search?.array("search") ?? []).compactMap { ($0 as? [String: Any])?.string("id") }
        guard !ids.isEmpty else { return nil }
        let entitiesRoot = try await JsonFetch.get("https://www.wikidata.org/w/api.php", params: [
            ("action", "wbgetentities"), ("format", "json"), ("props", "claims"), ("ids", ids.joined(separator: "|"))
        ]) as? [String: Any]
        guard let entities = entitiesRoot?.dict("entities") else { return nil }

        func values(_ claims: [String: Any], _ property: String) -> [[String: Any]] {
            claims.array(property).compactMap { ($0 as? [String: Any])?.dict("mainsnak")?.dict("datavalue") }
        }
        // In Suchreihenfolge: erster Eintrag mit IMDb-Nummer (tt…) und passendem Jahr
        for id in ids {
            guard let claims = entities.dict(id)?.dict("claims") else { continue }
            guard let imdb = values(claims, "P345").compactMap({ $0["value"] as? String }).first, imdb.hasPrefix("tt") else { continue }
            // Filme: Veröffentlichung (P577); Serien: Start (P580) bzw. Veröffentlichung
            let released = (values(claims, film ? "P577" : "P580") + values(claims, "P577")).compactMap { value -> Int? in
                guard let time = (value["value"] as? [String: Any])?.string("time") else { return nil }
                return Int(time.replacingOccurrences(of: "+", with: "").prefix(4))
            }
            if let year, !released.isEmpty, !released.contains(where: { abs($0 - year) <= 1 }) { continue }
            return imdb
        }
        return nil
    }

    static func normalize(_ text: String) -> String {
        String(text.lowercased().unicodeScalars.filter { CharacterSet.alphanumerics.contains($0) })
    }
}

/// Durchschnittsbewertung, Link und Hintergrundbild eines Films auf Letterboxd (über die IMDb-Nummer).
public enum LetterboxdRatings {

    public struct Rating: Codable, Hashable, Sendable {
        public let average: Double?
        public let url: String
        public let backdropUrl: String?

        /// z. B. "3,9"
        public var shortLabel: String? {
            average.map { String(format: "%.1f", $0).replacingOccurrences(of: ".", with: ",") }
        }
    }

    private struct Entry: Codable, Sendable {
        let rating: Rating?
        let time: TimeInterval
    }

    private static let refresh: TimeInterval = 3 * 24 * 60 * 60
    private static let cache = DiskCache<Entry>(fileName: "letterboxd-films.json")
    static let browserUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"

    /// Film über die IMDb-Nummer ("tt0111161"); nil bei Netzwerkfehler oder unbekanntem Film.
    public static func byImdb(_ imdbId: String) async -> Rating? {
        let key = "imdb:\(imdbId)"
        if let entry = await cache.get(key), Date().timeIntervalSince1970 - entry.time < refresh { return entry.rating }
        guard let url = URL(string: "https://letterboxd.com/imdb/\(imdbId)/") else { return nil }
        var request = URLRequest(url: url)
        request.setValue(browserUserAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("de-DE,de;q=0.9,en;q=0.8", forHTTPHeaderField: "Accept-Language")
        request.timeoutInterval = 20
        guard let (data, response) = try? await HttpClient.perform(request) else {
            return await cache.get(key)?.rating
        }
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        var rating: Rating?
        if status == 200 {
            let html = String(data: data, encoding: .utf8) ?? ""
            let average = HtmlParser.firstMatch(in: html, pattern: #"<meta\s+name="twitter:data2"\s+content="([0-9.]+) out of 5""#).flatMap(Double.init)
            let backdrop = (HtmlParser.firstMatch(in: html, pattern: #"data-backdrop2x="([^"]+)""#)
                ?? HtmlParser.firstMatch(in: html, pattern: #"data-backdrop="([^"]+)""#))?
                .replacingOccurrences(of: "&amp;", with: "&")
            rating = Rating(average: average, url: response.url?.absoluteString ?? url.absoluteString, backdropUrl: backdrop)
        } else if status != 404 {
            return await cache.get(key)?.rating
        }
        await cache.set(key, Entry(rating: rating, time: Date().timeIntervalSince1970))
        return rating
    }
}
