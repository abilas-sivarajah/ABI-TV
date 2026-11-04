import Foundation

/// Sprach-Option bei AniWorld & S.to:
/// - key = 1: Deutsch (GerDub)
/// - key = 2: Englisch Sub (EngSub)
/// - key = 3: Deutsch Sub (GerSub)
public enum LanguageOption: Int, Codable, CaseIterable, Hashable, Sendable {
    case gerDub = 1
    case gerSub = 3
    case engSub = 2

    public var langKey: Int { rawValue }

    public var shortLabel: String {
        switch self {
        case .gerDub: return "GER DUB"
        case .gerSub: return "GER SUB"
        case .engSub: return "ENG SUB"
        }
    }

    public var fullLabel: String {
        switch self {
        case .gerDub: return "Deutsch (Synchronisation)"
        case .gerSub: return "Japanisch mit deutschem Untertitel"
        case .engSub: return "Japanisch mit englischem Untertitel"
        }
    }

    public var badgeColorHex: UInt64 {
        switch self {
        case .gerDub: return 0xFF10B981
        case .gerSub: return 0xFF3B82F6
        case .engSub: return 0xFFF59E0B
        }
    }

    public var next: LanguageOption {
        switch self {
        case .gerDub: return .gerSub
        case .gerSub: return .engSub
        case .engSub: return .gerDub
        }
    }

    public static func fromKey(_ key: Int) -> LanguageOption? {
        LanguageOption(rawValue: key)
    }

    public static func fromImgOrTitle(_ raw: String) -> LanguageOption? {
        let lower = raw.lowercased()
        if lower.contains("japanese-german") || lower.contains("deutschem untertitel") {
            return .gerSub
        } else if lower.contains("japanese-english") || lower.contains("englisch") {
            return .engSub
        } else if lower.contains("german") || lower.contains("deutsch") {
            return .gerDub
        } else {
            return nil
        }
    }
}

/// Erkennt S.to-Slugs und -Adressen an einer Stelle, damit Modell, Scraper und Stream-Resolver gleich entscheiden.
public enum SerienStreamRef {
    public static let prefix = "sto:"
    private static let hosts: Set<String> = ["serienstream.to", "s.to"]

    public static func matches(_ value: String) -> Bool {
        let v = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return v.hasPrefix(prefix) || v.hasPrefix("/serie/") || isStoUrl(v)
    }

    public static func isStoUrl(_ url: String) -> Bool {
        let lower = url.lowercased()
        guard lower.hasPrefix("http://") || lower.hasPrefix("https://"),
              let parsed = URL(string: url),
              var host = parsed.host?.lowercased() else {
            return false
        }
        if host.hasPrefix("www.") {
            host = String(host.dropFirst(4))
        }
        return hosts.contains(host)
    }

    /// "sto:breaking-bad", "/serie/breaking-bad/staffel-1" oder volle Adresse -> "breaking-bad"
    public static func bareSlug(_ value: String) -> String {
        var v = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if v.hasPrefix(prefix) {
            v = String(v.dropFirst(prefix.count))
        }
        let path: String
        if isStoUrl(v) {
            path = URL(string: v)?.path ?? ""
        } else {
            path = v
        }
        var stripped = path
        if stripped.hasPrefix("/serie/") {
            stripped = String(stripped.dropFirst("/serie/".count))
        }
        if let slashIdx = stripped.firstIndex(of: "/") {
            stripped = String(stripped[..<slashIdx])
        }
        return stripped.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

public struct AnimeCard: Codable, Hashable, Identifiable, Sendable {
    public let slug: String
    public let title: String
    public let coverUrl: String
    public let genreOrSubtitle: String
    public let rating: String

    public var id: String { cleanSlug }

    public init(
        slug: String,
        title: String,
        coverUrl: String,
        genreOrSubtitle: String = "",
        rating: String = ""
    ) {
        self.slug = slug
        self.title = title
        self.coverUrl = coverUrl
        self.genreOrSubtitle = genreOrSubtitle
        self.rating = rating
    }

    public var isFilmpalast: Bool {
        slug.hasPrefix("filmpalast:") || slug.contains("filmpalast.to")
    }

    public var isHdFilme: Bool {
        slug.hasPrefix("hdfilme:") || slug.contains("hd-filme.one")
    }

    public var isSto: Bool {
        SerienStreamRef.matches(slug)
    }

    public var cleanSlug: String {
        if isFilmpalast {
            var rel = slug
            for p in ["filmpalast:", "https://filmpalast.to", "//filmpalast.to"] {
                if rel.hasPrefix(p) { rel = String(rel.dropFirst(p.count)) }
            }
            rel = rel.trimmingCharacters(in: .whitespacesAndNewlines)
            return "filmpalast:\(rel.hasPrefix("/") ? rel : "/\(rel)")"
        } else if isHdFilme {
            var rel = slug
            for p in ["hdfilme:", "https://hd-filme.one"] {
                if rel.hasPrefix(p) { rel = String(rel.dropFirst(p.count)) }
            }
            rel = rel.trimmingCharacters(in: .whitespacesAndNewlines)
            return "hdfilme:\(rel.hasPrefix("/") ? rel : "/\(rel)")"
        } else if isSto {
            return "\(SerienStreamRef.prefix)\(SerienStreamRef.bareSlug(slug))"
        } else {
            var s = slug
            if s.hasPrefix("https://aniworld.to") {
                s = String(s.dropFirst("https://aniworld.to".count))
            }
            if s.hasPrefix("/anime/stream/") {
                s = String(s.dropFirst("/anime/stream/".count))
            }
            if let slashIdx = s.firstIndex(of: "/") {
                s = String(s[..<slashIdx])
            }
            return s.trimmingCharacters(in: .whitespacesAndNewlines)
        }
    }

    public var streamPath: String {
        if isFilmpalast || isHdFilme {
            return cleanSlug
        } else if isSto {
            return "/serie/\(SerienStreamRef.bareSlug(slug))"
        } else {
            return "/anime/stream/\(cleanSlug)"
        }
    }
}

public struct CatalogSection: Codable, Hashable, Identifiable, Sendable {
    public let id: String
    public let title: String
    public let items: [AnimeCard]

    public init(id: String, title: String, items: [AnimeCard]) {
        self.id = id
        self.title = title
        self.items = items
    }
}

public struct SeasonLink: Codable, Hashable, Identifiable, Sendable {
    public let label: String
    public let path: String
    public let isMovies: Bool

    public var id: String { path }

    public init(label: String, path: String, isMovies: Bool? = nil) {
        self.label = label
        self.path = path
        self.isMovies = isMovies ?? (label.caseInsensitiveCompare("Filme") == .orderedSame)
    }
}

public struct EpisodeItem: Codable, Hashable, Identifiable, Sendable {
    public let number: Int
    public let seasonLabel: String
    public let path: String
    public let titleDe: String
    public let titleEn: String
    public let languages: [LanguageOption]

    public var id: String { path }

    public init(
        number: Int,
        seasonLabel: String,
        path: String,
        titleDe: String,
        titleEn: String,
        languages: [LanguageOption]
    ) {
        self.number = number
        self.seasonLabel = seasonLabel
        self.path = path
        self.titleDe = titleDe
        self.titleEn = titleEn
        self.languages = languages
    }

    public var displayTitle: String {
        if !titleDe.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return titleDe
        }
        if !titleEn.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return titleEn
        }
        return "Folge \(number)"
    }

    public var secondaryTitle: String {
        if !titleDe.isEmpty && !titleEn.isEmpty && titleDe != titleEn {
            return titleEn
        }
        return ""
    }

    /// Kurzform für Karten und Buttons ("S2 E5"); Filme haben keine Staffel, also kein "SFilm E1".
    public static func shortLabel(season: String, number: Int) -> String {
        let lower = season.lowercased()
        if lower == "film" || lower == "filme" {
            return "Film"
        }
        return "S\(season) E\(number)"
    }
}

public struct AnimeDetail: Codable, Hashable, Sendable {
    public let slug: String
    public let title: String
    public let description: String
    public let coverUrl: String
    public let backdropUrl: String
    public let years: String
    public let ageRating: String
    public let rating: String
    public let genres: [String]
    public let seasons: [SeasonLink]
    public let currentSeasonPath: String
    public let episodes: [EpisodeItem]

    public init(
        slug: String,
        title: String,
        description: String,
        coverUrl: String,
        backdropUrl: String,
        years: String,
        ageRating: String,
        rating: String,
        genres: [String],
        seasons: [SeasonLink],
        currentSeasonPath: String,
        episodes: [EpisodeItem]
    ) {
        self.slug = slug
        self.title = title
        self.description = description
        self.coverUrl = coverUrl
        self.backdropUrl = backdropUrl
        self.years = years
        self.ageRating = ageRating
        self.rating = rating
        self.genres = genres
        self.seasons = seasons
        self.currentSeasonPath = currentSeasonPath
        self.episodes = episodes
    }
}

public struct HosterLink: Codable, Hashable, Sendable {
    public let hosterName: String
    public let langKey: Int
    public let language: LanguageOption
    public let redirectPath: String

    public init(hosterName: String, langKey: Int, language: LanguageOption, redirectPath: String) {
        self.hosterName = hosterName
        self.langKey = langKey
        self.language = language
        self.redirectPath = redirectPath
    }
}

public struct ResolvedStream: Codable, Hashable, Sendable {
    public let streamUrl: String
    public let isHls: Bool
    public let hosterName: String
    public let language: LanguageOption
    public let headers: [String: String]

    public init(
        streamUrl: String,
        isHls: Bool,
        hosterName: String,
        language: LanguageOption,
        headers: [String: String]
    ) {
        self.streamUrl = streamUrl
        self.isHls = isHls
        self.hosterName = hosterName
        self.language = language
        self.headers = headers
    }
}

public struct WatchProgressEntry: Codable, Hashable, Identifiable, Sendable {
    public let animeSlug: String
    public let animeTitle: String
    public let coverUrl: String
    public let seasonLabel: String
    public let episodeNumber: Int
    public let episodeTitle: String
    public let episodePath: String
    public let preferredLangKey: Int
    public let positionMs: Int64
    public let durationMs: Int64
    public let updatedAt: Int64

    public var id: String { animeSlug }

    public init(
        animeSlug: String,
        animeTitle: String,
        coverUrl: String,
        seasonLabel: String,
        episodeNumber: Int,
        episodeTitle: String,
        episodePath: String,
        preferredLangKey: Int,
        positionMs: Int64,
        durationMs: Int64,
        updatedAt: Int64
    ) {
        self.animeSlug = animeSlug
        self.animeTitle = animeTitle
        self.coverUrl = coverUrl
        self.seasonLabel = seasonLabel
        self.episodeNumber = episodeNumber
        self.episodeTitle = episodeTitle
        self.episodePath = episodePath
        self.preferredLangKey = preferredLangKey
        self.positionMs = positionMs
        self.durationMs = durationMs
        self.updatedAt = updatedAt
    }

    public var progressFraction: Double {
        guard durationMs > 0 else { return 0.0 }
        return min(max(Double(positionMs) / Double(durationMs), 0.0), 1.0)
    }
}

public enum SearchScope: String, CaseIterable, Identifiable, Sendable {
    case animes = "ANIMES"
    case series = "SERIES"
    case movies = "MOVIES"
    case all = "ALL"

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .animes: return "Animes"
        case .series: return "Serien"
        case .movies: return "Filme"
        case .all: return "Alle"
        }
    }

    public var icon: String {
        switch self {
        case .animes: return "🍙"
        case .series: return "📺"
        case .movies: return "🎬"
        case .all: return "✨"
        }
    }
}

public struct LiveTvChannel: Codable, Hashable, Identifiable, Sendable {
    public let id: String
    public let name: String
    public let logoUrl: String
    public let streamUrl: String
    public let category: String
    public let qualityBadge: String
    public let headers: [String: String]

    public init(
        id: String,
        name: String,
        logoUrl: String,
        streamUrl: String,
        category: String,
        qualityBadge: String = "HD",
        headers: [String: String] = [:]
    ) {
        self.id = id
        self.name = name
        self.logoUrl = logoUrl
        self.streamUrl = streamUrl
        self.category = category
        self.qualityBadge = qualityBadge
        self.headers = headers
    }
}

public struct LiveTvLoadResult: Sendable {
    public let channels: [LiveTvChannel]
    public let customError: String?

    public init(channels: [LiveTvChannel], customError: String? = nil) {
        self.channels = channels
        self.customError = customError
    }
}

public struct LiveTvCategory: Codable, Hashable, Identifiable, Sendable {
    public let id: String
    public let title: String
    public let icon: String

    public init(id: String, title: String, icon: String) {
        self.id = id
        self.title = title
        self.icon = icon
    }
}
