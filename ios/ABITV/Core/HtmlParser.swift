import Foundation

/// Ein über seine CSS-Klasse gefundenes HTML-Element.
public struct HtmlElement: Sendable {
    /// Nur der öffnende Tag. Attribute hier lesen, damit nicht versehentlich ein Kind-Element antwortet.
    public let openTag: String
    /// Das komplette Element inklusive aller Kinder und des schließenden Tags.
    public let html: String

    /// Sichtbarer Text des Elements (wie `text()` bei Jsoup).
    public var text: String { HtmlParser.stripTagsAndDecode(html) }
}

/// Ein einzelner Tag aus dem HTML-Quelltext in Dokument-Reihenfolge.
public struct HtmlTag: Sendable {
    /// Tag-Name in Kleinbuchstaben.
    public let name: String
    /// Der Tag im Wortlaut, z. B. `<a href="/x" title="y">`.
    public let text: String
    public let isClosing: Bool
    /// `false` bei schließenden, selbstschließenden (`<br/>`) und leeren Tags (`<img>`).
    public let opensElement: Bool
}

/// Leichtgewichtiger, abhängigkeitsfreier HTML-Parser für AniWorld, Filmpalast, S.to und HD-Filme.
public enum HtmlParser {

    private static let voidElements: Set<String> = [
        "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"
    ]

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

        // Zuletzt, sonst würde aus "&amp;lt;" (Text: "&lt;") fälschlich "<"
        return s.replacingOccurrences(of: "&amp;", with: "&")
    }

    /// Liest den Wert eines HTML-Attributs (z. B. `href`, `data-src`, `title`) aus einem Tag oder Fragment.
    /// Die Prüfung davor sorgt dafür, dass die Suche nach `src` nicht auf `data-src` anspringt.
    public static func attr(_ tagOrHtml: String, _ name: String) -> String {
        let escaped = NSRegularExpression.escapedPattern(for: name)
        let patterns = [
            "(?<![\\w-])\(escaped)\\s*=\\s*\"([^\"]*)\"",
            "(?<![\\w-])\(escaped)\\s*=\\s*'([^']*)'",
            "(?<![\\w-])\(escaped)\\s*=\\s*([^\\s\"'>]+)"
        ]
        for pat in patterns {
            if let value = firstMatch(in: tagOrHtml, pattern: pat, group: 1) {
                return decodeEntities(value).trimmingCharacters(in: .whitespacesAndNewlines)
            }
        }
        return ""
    }

    /// Prüft, ob ein Tag genau diese CSS-Klasse trägt (`_ep` passt also nicht auf `_ept`).
    public static func hasClass(_ tag: String, _ className: String) -> Bool {
        attr(tag, "class").components(separatedBy: .whitespacesAndNewlines).contains(className)
    }

    /// Findet alle Elemente mit genau dieser CSS-Klasse samt ihrem kompletten Inhalt, wie `.klasse` bei Jsoup.
    public static func elements(in html: String, withClass className: String) -> [HtmlElement] {
        let cls = NSRegularExpression.escapedPattern(for: className)
        let pattern = "<([a-zA-Z][a-zA-Z0-9]*)\\b[^>]*?(?<![\\w-])class\\s*=\\s*"
            + "(?:\"(?:[^\"]*\\s)?\(cls)(?:\\s[^\"]*)?\"|'(?:[^']*\\s)?\(cls)(?:\\s[^']*)?')[^>]*>"
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) else { return [] }

        let ns = html as NSString
        return regex.matches(in: html, range: NSRange(location: 0, length: ns.length)).map { match -> HtmlElement in
            let openTag = ns.substring(with: match.range)
            let tagName = ns.substring(with: match.range(at: 1))
            if openTag.hasSuffix("/>") || voidElements.contains(tagName.lowercased()) {
                return HtmlElement(openTag: openTag, html: openTag)
            }
            let end = endOfElement(named: tagName, in: html, startingAt: NSMaxRange(match.range))
            let range = NSRange(location: match.range.location, length: end - match.range.location)
            return HtmlElement(openTag: openTag, html: ns.substring(with: range))
        }
    }

    /// Sucht das zum öffnenden Tag gehörende Ende, indem gleichnamige verschachtelte Tags mitgezählt werden.
    private static func endOfElement(named tagName: String, in html: String, startingAt start: Int) -> Int {
        let ns = html as NSString
        let escaped = NSRegularExpression.escapedPattern(for: tagName)
        guard let regex = try? NSRegularExpression(pattern: "<(/?)\(escaped)\\b[^>]*>", options: [.caseInsensitive]) else {
            return ns.length
        }

        var depth = 1
        var end = ns.length
        regex.enumerateMatches(in: html, options: [], range: NSRange(location: start, length: ns.length - start)) { match, _, stop in
            guard let match else { return }
            if match.range(at: 1).length > 0 {
                depth -= 1
                if depth == 0 {
                    end = NSMaxRange(match.range)
                    stop.pointee = true
                }
            } else if !ns.substring(with: match.range).hasSuffix("/>") {
                depth += 1
            }
        }
        return end
    }

    /// Zerlegt das HTML in seine Tags, um Eltern und Kinder eines Elements bestimmen zu können.
    public static func allTags(in html: String) -> [HtmlTag] {
        guard let regex = try? NSRegularExpression(pattern: "<(/?)([a-zA-Z][a-zA-Z0-9]*)\\b[^>]*>") else { return [] }
        let ns = html as NSString
        return regex.matches(in: html, range: NSRange(location: 0, length: ns.length)).map { match -> HtmlTag in
            let text = ns.substring(with: match.range)
            let name = ns.substring(with: match.range(at: 2)).lowercased()
            let isClosing = match.range(at: 1).length > 0
            let opensElement = !isClosing && !text.hasSuffix("/>") && !voidElements.contains(name)
            return HtmlTag(name: name, text: text, isClosing: isClosing, opensElement: opensElement)
        }
    }

    /// Indizes der umschließenden Elemente eines Tags, vom direkten Elternelement nach außen.
    public static func ancestorIndices(ofTagAt index: Int, in tags: [HtmlTag], limit: Int = 16) -> [Int] {
        var result: [Int] = []
        var depth = 0
        var i = index - 1
        while i >= 0 && result.count < limit {
            let tag = tags[i]
            if tag.isClosing {
                depth += 1
            } else if tag.opensElement {
                if depth == 0 {
                    result.append(i)
                } else {
                    depth -= 1
                }
            }
            i -= 1
        }
        return result
    }

    /// Alle Tags innerhalb des Elements, das beim Tag `index` beginnt.
    public static func descendants(ofTagAt index: Int, in tags: [HtmlTag]) -> ArraySlice<HtmlTag> {
        let first = index + 1
        var depth = 0
        var i = first
        while i < tags.count {
            let tag = tags[i]
            if tag.isClosing {
                if depth == 0 { break }
                depth -= 1
            } else if tag.opensElement {
                depth += 1
            }
            i += 1
        }
        return tags[first..<i]
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
