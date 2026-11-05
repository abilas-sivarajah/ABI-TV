import Foundation

/// Welche S.to-Serien Anime sind – damit der Serien-Bereich ohne Anime auskommt (die gibt es im
/// Anime-Reiter von AniWorld). Grundlage ist das S.to-Genre "Anime"; die Liste wird gespeichert
/// und einmal pro Woche erneuert (wie auf PC und TV).
public final class StoAnimeFilter: ObservableObject, @unchecked Sendable {

    public static let shared = StoAnimeFilter()

    private static let genrePath = "/genre/anime"
    private static let maxAge: TimeInterval = 7 * 24 * 60 * 60
    private static let maxPages = 250
    private static let parallelPages = 4
    private static let storeKey = "sto_anime_slugs_v1"

    /// Kurzname (Slug) aller S.to-Serien mit Genre "Anime".
    @Published public private(set) var slugs: Set<String> = []

    private struct Stored: Codable {
        let time: TimeInterval
        let slugs: [String]
    }

    private init() {}

    /// Beim Start einmal aufrufen: gespeicherte Liste sofort nutzen, bei Bedarf neu laden.
    public func load() async {
        let stored = UserDefaults.standard.data(forKey: Self.storeKey)
            .flatMap { try? JSONDecoder().decode(Stored.self, from: $0) }
        let cached = Set(stored?.slugs ?? [])
        if !cached.isEmpty { await publish(cached) }
        if let stored, !cached.isEmpty, Date().timeIntervalSince1970 - stored.time < Self.maxAge { return }

        let (fresh, complete) = await crawl(known: cached)
        if complete && !fresh.isEmpty {
            await publish(fresh)
            let data = try? JSONEncoder().encode(Stored(time: Date().timeIntervalSince1970, slugs: fresh.sorted()))
            UserDefaults.standard.set(data, forKey: Self.storeKey)
        }
    }

    private func publish(_ value: Set<String>) async {
        await MainActor.run { self.slugs = value }
    }

    /// Alle Seiten des Genres laden, bis keine neuen Serien mehr kommen (S.to wiederholt danach die letzte Seite).
    private func crawl(known: Set<String>) async -> (Set<String>, Bool) {
        var found = Set<String>()
        var page = 1
        let genrePath = Self.genrePath
        while page <= Self.maxPages {
            let pages = Array(page..<(page + Self.parallelPages))
            let results: [[AnimeCard]?] = await withTaskGroup(of: (Int, [AnimeCard]?).self) { group in
                for p in pages {
                    group.addTask { (p, try? await SerienStreamScraper.fetchCategory(genrePath, page: p)) }
                }
                var collected: [Int: [AnimeCard]?] = [:]
                for await (p, cards) in group { collected[p] = cards }
                return pages.map { collected[$0] ?? nil }
            }
            if results.contains(where: { $0 == nil }) {
                await publish(known.union(found))
                return (found, false)
            }
            let before = found.count
            for cards in results {
                for card in cards ?? [] { found.insert(SerienStreamScraper.toCleanSlug(card.slug)) }
            }
            await publish(known.union(found))
            if found.count == before { return (found, true) }
            page += Self.parallelPages
        }
        return (found, true)
    }

    /// Ist diese Karte eine Anime-Serie von S.to?
    public static func isStoAnime(_ card: AnimeCard, slugs: Set<String>) -> Bool {
        !slugs.isEmpty && card.isSto && slugs.contains(SerienStreamScraper.toCleanSlug(card.slug))
    }
}

public extension Array where Element == AnimeCard {
    /// Liste ohne S.to-Anime (alle anderen Titel bleiben unverändert).
    func withoutStoAnime(_ slugs: Set<String>) -> [AnimeCard] {
        slugs.isEmpty ? self : filter { !StoAnimeFilter.isStoAnime($0, slugs: slugs) }
    }
}
