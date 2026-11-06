import Foundation

/// Ein Reiter im Baum: entweder mit Unterreitern (`children`) oder mit Sendern/Titeln (`channels`).
public final class LiveTvNode: Identifiable, Hashable, @unchecked Sendable {
    public let id: String
    public let title: String
    public let icon: String
    public let children: [LiveTvNode]
    public let channels: [LiveTvChannel]
    /// Anzahl aller Sender/Titel darunter.
    public let total: Int

    public init(id: String, title: String, icon: String = "", children: [LiveTvNode] = [], channels: [LiveTvChannel] = []) {
        self.id = id
        self.title = title
        self.icon = icon
        self.children = children
        self.channels = channels
        self.total = channels.count + children.reduce(0) { $0 + $1.total }
    }

    public static func == (lhs: LiveTvNode, rhs: LiveTvNode) -> Bool { lhs.id == rhs.id }
    public func hash(into hasher: inout Hasher) { hasher.combine(id) }

    /// Alle Sender/Titel darunter (für die Suche).
    public func allChannels() -> [LiveTvChannel] {
        channels + children.flatMap { $0.allChannels() }
    }
}

/// Baut aus eigenen M3U-Listen Reiter mit Unterreitern (wie am PC und TV):
/// IPTV-Liste mit Gruppen → je Land ein Reiter (Türkiye vor Deutsch) mit Sendergruppen, "Filme (VOD)"
/// und "Serien" (Gruppe › Serie › Staffel); eigener Ordner → Ordnerstruktur.
public enum LiveTvTree {

    private enum Kind { case live, movie, series }

    private struct Country {
        let key: String
        let title: String
        let icon: String
        let order: Int
    }

    private static let vodExtensions = [".mkv", ".mp4", ".avi", ".m4v", ".mov", ".wmv", ".flv", ".mpg", ".mpeg"]

    /// Hat die Liste genug Gruppen, um sie nach Ländern und Gruppen zu sortieren?
    public static func hasGroups(_ channels: [LiveTvChannel]) -> Bool {
        guard !channels.isEmpty else { return false }
        return channels.filter { !($0.group ?? "").isEmpty }.count * 2 >= channels.count
    }

    /// Film oder Serienfolge (zum Spulen), kein Live-Sender?
    public static func isVod(_ url: String) -> Bool {
        let path = (url.components(separatedBy: "?").first ?? url).lowercased()
        return path.contains("/movie/") || path.contains("/series/") || vodExtensions.contains { path.hasSuffix($0) }
    }

    private static func kind(_ channel: LiveTvChannel) -> Kind {
        let path = (channel.streamUrl.components(separatedBy: "?").first ?? channel.streamUrl).lowercased()
        if path.contains("/series/") { return .series }
        if path.contains("/movie/") { return .movie }
        if (channel.group ?? "").trimmingCharacters(in: .whitespaces).uppercased().hasPrefix("(S|") { return .series }
        if vodExtensions.contains(where: { path.hasSuffix($0) }) { return .movie }
        return .live
    }

    /// Ländercode aus der Gruppe ("TR SPOR", "(V|DE) Filme") oder dem Namen ("DE: …", "(TR-) …").
    private static func countryCode(_ channel: LiveTvChannel) -> String? {
        let group = (channel.group ?? "").trimmingCharacters(in: .whitespaces)
        if group.hasPrefix("("), let bar = group.firstIndex(of: "|") {
            let code = group[group.index(after: bar)...].prefix(2)
            if code.count == 2, code.allSatisfy(\.isLetter) { return code.uppercased() }
        }
        let head = group.prefix(3)
        if head.count >= 2, head.prefix(2).allSatisfy(\.isLetter),
           head.count == 2 || [" ", ":", "|"].contains(String(head.last!)) {
            return head.prefix(2).uppercased()
        }
        let name = channel.name
        if name.count >= 3, name.prefix(2).allSatisfy(\.isLetter), name.dropFirst(2).first == ":" {
            return name.prefix(2).uppercased()
        }
        if name.hasPrefix("("), name.count >= 4 {
            let code = name.dropFirst().prefix(2)
            if code.allSatisfy(\.isLetter) { return code.uppercased() }
        }
        return nil
    }

    private static func country(_ channel: LiveTvChannel) -> Country {
        switch countryCode(channel) {
        case "TR": return Country(key: "TR", title: "Türkiye", icon: "🇹🇷", order: 0)
        case "DE", "AT", "CH": return Country(key: "DE", title: "Deutsch", icon: "🇩🇪", order: 1)
        case nil: return Country(key: "XX", title: "Weitere", icon: "🌐", order: 99)
        case let code?: return Country(key: code, title: code, icon: "🌐", order: 50)
        }
    }

    /// "DE: Das Erste" → "Das Erste", "(TR-) Film - 2024" → "Film - 2024".
    static func cleanName(_ name: String) -> String {
        var s = Substring(name)
        if s.count >= 3, s.prefix(2).allSatisfy(\.isLetter), s.dropFirst(2).first == ":" {
            s = s.dropFirst(3)
        } else if s.hasPrefix("("), let close = s.firstIndex(of: ")"), s.distance(from: s.startIndex, to: close) <= 4 {
            s = s[s.index(after: close)...]
        }
        let trimmed = s.trimmingCharacters(in: .whitespaces)
        return trimmed.isEmpty ? name : trimmed
    }

    /// "(V|TR) Yerli Film" → "Yerli Film".
    private static func stripVodPrefix(_ group: String) -> String {
        guard group.hasPrefix("("), let close = group.firstIndex(of: ")") else { return group }
        let rest = group[group.index(after: close)...].trimmingCharacters(in: .whitespaces)
        return rest.isEmpty ? group : rest
    }

    private static func isSeparator(_ name: String) -> Bool {
        let t = name.trimmingCharacters(in: .whitespaces)
        let marks: Set<Character> = ["-", "=", "#", "*", "~", "_"]
        return t.count >= 6 && t.prefix(3).allSatisfy { marks.contains($0) } && t.suffix(3).allSatisfy { marks.contains($0) }
    }

    private static func renamed(_ c: LiveTvChannel, _ name: String) -> LiveTvChannel {
        LiveTvChannel(id: c.id, name: name, logoUrl: c.logoUrl, streamUrl: c.streamUrl, category: c.category,
                      qualityBadge: c.qualityBadge, headers: c.headers, group: c.group)
    }

    /// IPTV-Liste nach Land › Gruppe › (Filme/Serien) sortieren; Reihenfolge wie in der Liste.
    public static func buildGrouped(_ channels: [LiveTvChannel], idPrefix: String) -> [LiveTvNode] {
        var countries: [String: Country] = [:]
        var countryOrder: [String] = []
        var groups: [String: [Kind: [String]]] = [:]          // Land → Art → Gruppen in Reihenfolge
        var members: [String: [LiveTvChannel]] = [:]           // "Land|Art|Gruppe" → Sender
        for channel in channels where !isSeparator(channel.name) {
            let c = country(channel)
            if countries[c.key] == nil {
                countries[c.key] = c
                countryOrder.append(c.key)
            }
            let k = kind(channel)
            let group = (channel.group ?? "").trimmingCharacters(in: .whitespaces)
            let groupName = group.isEmpty ? "Ohne Gruppe" : group
            let key = "\(c.key)|\(k)|\(groupName)"
            if members[key] == nil {
                groups[c.key, default: [:]][k, default: []].append(groupName)
            }
            members[key, default: []].append(channel)
        }

        return countryOrder.compactMap { countries[$0] }.sorted { $0.order < $1.order }.map { c in
            let base = "\(idPrefix)\(c.key)"
            var children: [LiveTvNode] = []
            for g in groups[c.key]?[.live] ?? [] {
                let list = members["\(c.key)|\(Kind.live)|\(g)"] ?? []
                children.append(LiveTvNode(id: "\(base)/live/\(g)", title: g, channels: list.map { renamed($0, cleanName($0.name)) }))
            }
            if let movieGroups = groups[c.key]?[.movie], !movieGroups.isEmpty {
                children.append(LiveTvNode(id: "\(base)/movies", title: "Filme (VOD)", icon: "🎬", children: movieGroups.map { g in
                    let list = members["\(c.key)|\(Kind.movie)|\(g)"] ?? []
                    return LiveTvNode(id: "\(base)/movies/\(g)", title: stripVodPrefix(g), channels: list.map { renamed($0, cleanName($0.name)) })
                }))
            }
            if let seriesGroups = groups[c.key]?[.series], !seriesGroups.isEmpty {
                children.append(LiveTvNode(id: "\(base)/series", title: "Serien", icon: "📺", children: seriesGroups.map { g in
                    let groupId = "\(base)/series/\(g)"
                    return LiveTvNode(id: groupId, title: stripVodPrefix(g), children: buildShows(members["\(c.key)|\(Kind.series)|\(g)"] ?? [], groupId: groupId))
                }))
            }
            return LiveTvNode(id: base, title: c.title, icon: c.icon, children: children)
        }
    }

    /// "Dark S01 E05" → ("Dark", 1, 5, "").
    static func parseEpisode(_ name: String) -> (show: String, season: Int, episode: Int, rest: String)? {
        let chars = Array(name)
        var i = 0
        while i < chars.count {
            if (chars[i] == "S" || chars[i] == "s"), i == 0 || chars[i - 1] == " " {
                var j = i + 1
                var season = 0
                var sDigits = 0
                while j < chars.count, let d = chars[j].wholeNumberValue, sDigits < 3 { season = season * 10 + d; j += 1; sDigits += 1 }
                if sDigits > 0 {
                    while j < chars.count, chars[j] == " " { j += 1 }
                    if j < chars.count, chars[j] == "E" || chars[j] == "e" {
                        var k = j + 1
                        var episode = 0
                        var eDigits = 0
                        while k < chars.count, let d = chars[k].wholeNumberValue, eDigits < 4 { episode = episode * 10 + d; k += 1; eDigits += 1 }
                        if eDigits > 0 {
                            let show = String(chars[0..<i]).trimmingCharacters(in: .whitespaces)
                            let rest = String(chars[k...]).trimmingCharacters(in: .whitespaces)
                            return (show.isEmpty ? name : show, season, episode, rest)
                        }
                    }
                }
            }
            i += 1
        }
        return nil
    }

    private static func buildShows(_ episodes: [LiveTvChannel], groupId: String) -> [LiveTvNode] {
        struct Ep { let show: String; let season: Int; let episode: Int; let rest: String; let channel: LiveTvChannel }
        let parsed: [Ep] = episodes.map { ch in
            let name = cleanName(ch.name)
            if let p = parseEpisode(name) { return Ep(show: p.show, season: p.season, episode: p.episode, rest: p.rest, channel: ch) }
            return Ep(show: name, season: 0, episode: 0, rest: "", channel: ch)
        }
        let byShow = Dictionary(grouping: parsed, by: \.show)
        return byShow.keys.sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }.map { show in
            let showId = "\(groupId)/\(show)"
            let eps = (byShow[show] ?? []).sorted { ($0.season, $0.episode) < ($1.season, $1.episode) }
            func channel(_ ep: Ep) -> LiveTvChannel {
                let name = ep.season > 0
                    ? String(format: "S%02d E%02d", ep.season, ep.episode) + (ep.rest.isEmpty ? "" : " \(ep.rest)")
                    : ep.show
                return renamed(ep.channel, name)
            }
            let seasons = Dictionary(grouping: eps, by: \.season)
            if seasons.count <= 1 {
                return LiveTvNode(id: showId, title: show, channels: eps.map(channel))
            }
            return LiveTvNode(id: showId, title: show, children: seasons.keys.sorted().map { season in
                LiveTvNode(id: "\(showId)/\(season)", title: season > 0 ? "Staffel \(season)" : "Weitere",
                           channels: (seasons[season] ?? []).map(channel))
            })
        }
    }

    /// Ordnerstruktur als Baum; liegen in einem Ordner Dateien und Unterordner, kommen die Dateien
    /// in einen eigenen Unterreiter.
    public static func buildFolder(rootName: String, files: [M3uFolderFile], idPrefix: String) -> LiveTvNode {
        final class Dir {
            let name: String
            let path: String
            var subdirs: [String: Dir] = [:]
            var channels: [LiveTvChannel] = []
            init(name: String, path: String) { self.name = name; self.path = path }
        }
        let root = Dir(name: rootName, path: "")
        var counter = 0
        for file in files.sorted(by: { ($0.dir + "/" + $0.fileName).lowercased() < ($1.dir + "/" + $1.fileName).lowercased() }) {
            var dir = root
            for part in file.dir.split(separator: "/").map(String.init) where !part.isEmpty {
                if let sub = dir.subdirs[part] {
                    dir = sub
                } else {
                    let sub = Dir(name: part, path: dir.path.isEmpty ? part : "\(dir.path)/\(part)")
                    dir.subdirs[part] = sub
                    dir = sub
                }
            }
            let base = (file.fileName as NSString).deletingPathExtension
            let parsed = LiveTvRepository.parseM3u(file.content, category: "folder:\(file.dir)", idPrefix: "tree_\(counter)_", fallbackName: base)
            counter += parsed.count
            dir.channels += parsed
        }
        func node(_ dir: Dir) -> LiveTvNode {
            let id = "\(idPrefix)\(dir.path)"
            let subs = dir.subdirs.values.sorted { $0.name.lowercased() < $1.name.lowercased() }.map(node).filter { $0.total > 0 }
            if subs.isEmpty { return LiveTvNode(id: id, title: dir.name, channels: dir.channels) }
            if dir.channels.isEmpty { return LiveTvNode(id: id, title: dir.name, children: subs) }
            return LiveTvNode(id: id, title: dir.name,
                              children: [LiveTvNode(id: "\(id)#files", title: "Alle in \(dir.name)", channels: dir.channels)] + subs)
        }
        let built = node(root)
        return LiveTvNode(id: built.id, title: built.title, icon: "📁", children: built.children, channels: built.channels)
    }
}
