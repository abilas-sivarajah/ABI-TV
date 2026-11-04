import Foundation

/// Leichtgewichtiger, abhängigkeitsfreier HTML-Parser für AniWorld, Filmpalast, S.to und HD-Filme.
public enum HtmlParser {

    /// Dekodiert HTML-Entities (&amp;, &quot;, &#039;, &auml;, &#8211;, &#x27; usw.) und entfernt Tags.
    public static func stripTagsAndDecode(_ html: String) -> String {
        let withoutTags = html.replacingOccurrences(
            of: #"<[^>]+>"#,
            with: " ",
            options: .regularExpression
        )
        return decodeEntities(withoutTags)
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    public static func decodeEntities(_ text: String) -> String {
        guard text.contains("&") else { return text }
        var s = text
        let namedEntities: [(String, String)] = [
            ("&amp;", "&"),
            ("&quot;", "\""),
            ("&apos;", "'"),
            ("&#039;", "'"),
            ("&#39;", "'"),
            ("&lt;", "<"),
            ("&gt;", ">"),
            ("&nbsp;", " "),
            ("&ndash;", "–"),
            ("&mdash;", "—"),
            ("&hellip;", "…"),
            ("&auml;", "ä"),
            ("&ouml;", "ö"),
            ("&uuml;", "ü"),
            ("&Auml;", "Ä"),
            ("&Ouml;", "Ö"),
            ("&Uuml;", "Ü"),
            ("&szlig;", "ß")
        ]
        for (entity, replacement) in namedEntities {
            s = s.replacingOccurrences(of: entity, with: replacement)
        }

        // Numerische Dezimal-Entities: &#1234;
        if let decRegex = try? NSRegularExpression(pattern: #"&#(\d+);"#) {
            let ns = s as NSString
            let matches = decRegex.matches(in: s, range: NSRange(location: 0, length: ns.length)).reversed()
            for m in matches {
                if let code = UInt32(ns.substring(with: m.range(at: 1))),
                   let scalar = UnicodeScalar(code) {
                    s = (s as NSString).replacingCharacters(in: m.range, with: String(Character(scalar)))
                }
            }
        }

        // Numerische Hex-Entities: &#x1F600;
        if let hexRegex = try? NSRegularExpression(pattern: #"&#[xX]([0-9a-fA-F]+);"#) {
            let ns = s as NSString
            let matches = hexRegex.matches(in: s, range: NSRange(location: 0, length: ns.length)).reversed()
            for m in matches {
                if let code = UInt32(ns.substring(with: m.range(at: 1)), radix: 16),
                   let scalar = UnicodeScalar(code) {
                    s = (s as NSString).replacingCharacters(in: m.range, with: String(Character(scalar)))
                }
            }
        }
        return s
    }

    /// Liest den Wert eines HTML-Attributs (z. B. `href`, `data-src`, `title`) aus einem Tag oder Fragment.
    public static func attr(_ tagOrHtml: String, _ name: String) -> String {
        let escaped = NSRegularExpression.escapedPattern(for: name)
        let patterns = [
            "\\b\(escaped)\\s*=\\s*\"([^\"]*)\"",
            "\\b\(escaped)\\s*=\\s*'([^']*)'"
        ]
        for pat in patterns {
            if let value = firstMatch(in: tagOrHtml, pattern: pat, group: 1) {
                return decodeEntities(value).trimmingCharacters(in: .whitespacesAndNewlines)
            }
        }
        return ""
    }

    /// Liefert den ersten Regex-Treffer (Gruppe `group`).
    public static func firstMatch(
        in text: String,
        pattern: String,
        group: Int = 1,
        dotMatchesAll: Bool = true
    ) -> String? {
        var opts: NSRegularExpression.Options = [.caseInsensitive]
        if dotMatchesAll { opts.insert(.dotMatchesLineSeparators) }
        guard let regex = try? NSRegularExpression(pattern: pattern, options: opts) else { return nil }
        let ns = text as NSString
        guard let match = regex.firstMatch(in: text, range: NSRange(location: 0, length: ns.length)),
              group < match.numberOfRanges else { return nil }
        let range = match.range(at: group)
        guard range.location != NSNotFound else { return nil }
        return ns.substring(with: range)
    }

    /// Liefert alle Regex-Treffer (jeweils Array der Gruppen 0..N).
    public static func allMatches(
        in text: String,
        pattern: String,
        dotMatchesAll: Bool = true
    ) -> [[String]] {
        var opts: NSRegularExpression.Options = [.caseInsensitive]
        if dotMatchesAll { opts.insert(.dotMatchesLineSeparators) }
        guard let regex = try? NSRegularExpression(pattern: pattern, options: opts) else { return [] }
        let ns = text as NSString
        let matches = regex.matches(in: text, range: NSRange(location: 0, length: ns.length))
        return matches.map { m in
            (0..<m.numberOfRanges).map { idx in
                let r = m.range(at: idx)
                return r.location != NSNotFound ? ns.substring(with: r) : ""
            }
        }
    }

    /// Extrahiert alle nicht-verschachtelten Blöcke `<tag ...>...</tag>`.
    public static func blocks(in html: String, tag: String) -> [String] {
        let escaped = NSRegularExpression.escapedPattern(for: tag)
        let pattern = "<\(escaped)\\b[^>]*>.*?</\(escaped)>"
        return allMatches(in: html, pattern: pattern).compactMap { $0.first }
    }
}
