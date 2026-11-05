import Foundation

/// Suche nach Wortanfängen (wie auf PC und TV): Ein Titel passt, wenn eines seiner Wörter mit der
/// Eingabe beginnt. Satzzeichen, Bindestriche und Leerzeichen zählen nicht – "sp" findet
/// "Marvel's Spider-Man", "spiderman" findet "Spider-Man", "john wick" findet "John Wick: Kapitel 4".
public enum TitleSearch {

    /// Kleinbuchstaben, ohne Akzente (é → e), ß → ss.
    private static func fold(_ text: String) -> String {
        text.lowercased()
            .folding(options: [.diacriticInsensitive], locale: Locale(identifier: "de_DE"))
            .replacingOccurrences(of: "ß", with: "ss")
    }

    private static func words(_ title: String) -> [String] {
        fold(title)
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
    }

    private static func normalizedQuery(_ query: String) -> String {
        String(fold(query).unicodeScalars.filter { CharacterSet.alphanumerics.contains($0) })
    }

    /// Wort, an dem der Treffer beginnt (0 = am Titelanfang), oder nil, wenn der Titel nicht passt.
    public static func matchPosition(title: String, query: String) -> Int? {
        let wanted = normalizedQuery(query)
        guard !wanted.isEmpty else { return nil }
        let titleWords = words(title)
        for start in titleWords.indices {
            var rest = ""
            for index in start..<titleWords.count {
                rest += titleWords[index]
                if rest.count >= wanted.count { break }
            }
            if rest.hasPrefix(wanted) { return start }
        }
        return nil
    }

    public static func matches(title: String, query: String) -> Bool {
        matchPosition(title: title, query: query) != nil
    }

    /// Nur passende Titel; Treffer am Titelanfang zuerst, dann kürzere Titel.
    public static func filterAndRank(_ items: [AnimeCard], query: String) -> [AnimeCard] {
        var seen = Set<String>()
        return items
            .compactMap { card in matchPosition(title: card.title, query: query).map { (card, $0) } }
            .sorted { lhs, rhs in
                lhs.1 != rhs.1 ? lhs.1 < rhs.1 : lhs.0.title.count < rhs.0.title.count
            }
            .map { $0.0 }
            .filter { seen.insert($0.cleanSlug).inserted }
    }

    /// Suchbegriffe für Seiten, die nur "Titel enthält" können: die Eingabe und ihr längstes Wort,
    /// gekürzt auf 6 Zeichen ("spiderman" → "spider"). Gefiltert wird danach mit `filterAndRank`.
    public static func siteQueries(_ query: String) -> [String] {
        let full = query.trimmingCharacters(in: .whitespacesAndNewlines)
        let longest = full
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { $0.count >= 2 }
            .max { $0.count < $1.count }
            .map { String($0.prefix(6)) }
        var result: [String] = []
        if full.count >= 2 { result.append(full) }
        if let longest, !result.contains(longest) { result.append(longest) }
        return result
    }
}
