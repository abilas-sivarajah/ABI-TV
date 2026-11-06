import Foundation

/// Live-TV-Senderliste (28 feste Sender + eigene M3U-Listen).
/// Entspricht 1:1 `LiveTvRepository.kt` aus `:shared`.
public enum LiveTvRepository {

    public static let categories: [LiveTvCategory] = [
        LiveTvCategory(id: "all", title: "Alle Sender", icon: "📺"),
        LiveTvCategory(id: "main", title: "Hauptsender", icon: "⭐"),
        LiveTvCategory(id: "sport", title: "Sport & Racing", icon: "🏎️"),
        LiveTvCategory(id: "news", title: "News & Dokus", icon: "📰"),
        LiveTvCategory(id: "at_ch", title: "Österreich / F1", icon: "🇦🇹"),
        LiveTvCategory(id: "custom", title: "Eigene M3U", icon: "📂")
    ]

    public static let presetChannels: [LiveTvChannel] = [
        // === HAUPTSENDER & PRIVAT ===
        LiveTvChannel(
            id: "prosieben",
            name: "ProSieben",
            logoUrl: "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/germany/pro-sieben-de.png",
            streamUrl: "https://dash4.antik.sk/live/test_pro_sieben_tizen/playlist.m3u8",
            category: "main",
            qualityBadge: "1080p"
        ),
        LiveTvChannel(
            id: "sat1",
            name: "SAT.1",
            logoUrl: "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/germany/sat-1-de.png",
            streamUrl: "https://dash4.antik.sk/live/test_sat_eins_tizen/playlist.m3u8",
            category: "main",
            qualityBadge: "1080p"
        ),
        LiveTvChannel(
            id: "rtl",
            name: "RTL",
            logoUrl: "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/germany/rtl-de.png",
            streamUrl: "https://dash4.antik.sk/live/test_rtl_ger_tizen/playlist.m3u8",
            category: "main",
            qualityBadge: "720p"
        ),
        LiveTvChannel(
            id: "vox",
            name: "VOX",
            logoUrl: "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/germany/vox-de.png",
            streamUrl: "https://dash4.antik.sk/live/test_vox_tizen/playlist.m3u8",
            category: "main",
            qualityBadge: "720p"
        ),
        LiveTvChannel(
            id: "kabeleins",
            name: "kabel eins",
            logoUrl: "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/germany/kabel-eins-de.png",
            streamUrl: "https://dash4.antik.sk/live/test_kabel_eins_tizen/playlist.m3u8",
            category: "main",
            qualityBadge: "720p"
        ),
        LiveTvChannel(
            id: "rtl2",
            name: "RTL Zwei",
            logoUrl: "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/germany/rtl-zwei-de.png",
            streamUrl: "https://dash4.antik.sk/live/test_rtl_zwei_tizen/playlist.m3u8",
            category: "main",
            qualityBadge: "720p"
        ),
        LiveTvChannel(
            id: "superrtl",
            name: "Super RTL",
            logoUrl: "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/germany/rtl-super-de.png",
            streamUrl: "https://dash4.antik.sk/live/test_super_rtl_tizen/playlist.m3u8",
            category: "main",
            qualityBadge: "720p"
        ),
        LiveTvChannel(
            id: "nitro",
            name: "Nitro",
            logoUrl: "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/germany/nitro-de.png",
            streamUrl: "https://dash4.antik.sk/live/test_rtl_nitro_tizen/playlist.m3u8",
            category: "main",
            qualityBadge: "720p"
        ),
        LiveTvChannel(
            id: "prosieben_maxx",
            name: "ProSieben MAXX",
            logoUrl: "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/germany/pro-sieben-maxx-de.png",
            streamUrl: "https://s6.hopslan.com/pro7maxxX/index.m3u8",
            category: "main",
            qualityBadge: "HD"
        ),
        LiveTvChannel(
            id: "sixx",
            name: "sixx",
            logoUrl: "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/germany/sixx-de.png",
            streamUrl: "https://dash4.antik.sk/live/test_sixx_tizen/playlist.m3u8",
            category: "main",
            qualityBadge: "720p"
        ),
        LiveTvChannel(
            id: "sat1_gold",
            name: "SAT.1 Gold",
            logoUrl: "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/germany/sat-1-gold-de.png",
            streamUrl: "https://dash4.antik.sk/live/test_sat_eins_gold_tizen/playlist.m3u8",
            category: "main",
            qualityBadge: "576p"
        ),
        LiveTvChannel(
            id: "daserste",
            name: "Das Erste HD (ARD)",
            logoUrl: "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/daserstehd.png",
            streamUrl: "https://daserste-live.ard-mcdn.de/daserste/live/hls/de/master.m3u8",
            category: "main",
            qualityBadge: "1080p"
        ),
        LiveTvChannel(
            id: "zdf",
            name: "ZDF HD",
            logoUrl: "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/zdfhd.png",
            streamUrl: "https://zdf-hls-15.akamaized.net/hls/live/2016498/de/veryhigh/master.m3u8",
            category: "main",
            qualityBadge: "1080p"
        ),
        LiveTvChannel(
            id: "arte",
            name: "Arte HD",
            logoUrl: "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/artehd.png",
            streamUrl: "https://artesimulcast.akamaized.net/hls/live/2030993/artelive_de/master.m3u8",
            category: "main",
            qualityBadge: "1080p"
        ),
        LiveTvChannel(
            id: "drei_sat",
            name: "3sat HD",
            logoUrl: "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/3sat.png",
            streamUrl: "https://zdf-hls-18.akamaized.net/hls/live/2016501/dach/veryhigh/master.m3u8",
            category: "main",
            qualityBadge: "1080p"
        ),

        // === SPORT & RACING ===
        LiveTvChannel(
            id: "df1",
            name: "DF1 HD (DAZN & Motorsport)",
            logoUrl: "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/df1.png",
            streamUrl: "https://d287776cjxyfeq.cloudfront.net/out/v1/08823cd7fba74508a5a577fd51dc41f6/index.m3u8",
            category: "sport",
            qualityBadge: "Full HD"
        ),
        LiveTvChannel(
            id: "redbull_tv",
            name: "Red Bull TV (Motorsport & WRC)",
            logoUrl: "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/redbulltv.png",
            streamUrl: "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8",
            category: "sport",
            qualityBadge: "1080p"
        ),
        LiveTvChannel(
            id: "more_than_sports",
            name: "More Than Sports TV",
            logoUrl: "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/morethansports.png",
            streamUrl: "https://mts1.iptv-playoutcenter.de/mts/mts-web/playlist.m3u8",
            category: "sport",
            qualityBadge: "1080p"
        ),
        LiveTvChannel(
            id: "dazn_combat",
            name: "DAZN Combat (Kampfsport)",
            logoUrl: "https://i.postimg.cc/VsW3Jsrz/logo-DAZN-Combat.png",
            streamUrl: "https://jmp2.uk/plu-64d626ac9b414d000820e2fc.m3u8",
            category: "sport",
            qualityBadge: "HD"
        ),
        LiveTvChannel(
            id: "dazn_darts",
            name: "DAZN Darts x Pluto TV",
            logoUrl: "https://images.pluto.tv/channels/64b67f0424ade50008a3be17/colorLogoPNG.png",
            streamUrl: "https://jmp2.uk/plu-64b67f0424ade50008a3be17.m3u8",
            category: "sport",
            qualityBadge: "HD"
        ),
        LiveTvChannel(
            id: "dazn_heldinnen",
            name: "DAZN Heldinnen",
            logoUrl: "https://images.pluto.tv/channels/64afe50c5dc16600087f3227/colorLogoPNG.png",
            streamUrl: "https://jmp2.uk/plu-64afe50c5dc16600087f3227.m3u8",
            category: "sport",
            qualityBadge: "HD"
        ),
        LiveTvChannel(
            id: "mav_tv",
            name: "MavTV Motorsports",
            logoUrl: "https://upload.wikimedia.org/wikipedia/en/7/74/Racer_Network.jpg",
            streamUrl: "https://amg00378-mavtv-amg00378c2-rakuten-us-1048.playouts.now.amagi.tv/playlist/amg00378-mavtvfast-motorsportsnetwork-rakutenus/playlist.m3u8",
            category: "sport",
            qualityBadge: "1080p"
        ),
        LiveTvChannel(
            id: "flo_racing",
            name: "FloRacing Worldwide",
            logoUrl: "https://images.fubo.tv/channel-config-ui/station-logos/on-dark/floracing-white.png",
            streamUrl: "https://amg02278-amg02278c1-flosports-worldwide-7592.playouts.now.amagi.tv/playlist.m3u8",
            category: "sport",
            qualityBadge: "1080p"
        ),
        LiveTvChannel(
            id: "cbs_golazo",
            name: "CBS Sports Golazo (Fußball)",
            logoUrl: "https://i.imgur.com/eMjutHS.png",
            streamUrl: "https://dai.google.com/linear/hls/event/7f3Wv6f7QEKfQna22jHqLQ/master.m3u8",
            category: "sport",
            qualityBadge: "720p"
        ),
        LiveTvChannel(
            id: "bein_xtra",
            name: "beIN SPORTS XTRA",
            logoUrl: "https://i.ibb.co/HT49GPmB/XTRA-2.png",
            streamUrl: "https://bein-xtra-bein.amagi.tv/playlist.m3u8",
            category: "sport",
            qualityBadge: "1080p"
        ),

        // === NACHRICHTEN & DOKUS ===
        LiveTvChannel(
            id: "tagesschau24",
            name: "tagesschau24 HD",
            logoUrl: "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/tagesschau24.png",
            streamUrl: "https://tagesschau.akamaized.net/hls/live/2020115/tagesschau/tagesschau_1/master.m3u8",
            category: "news",
            qualityBadge: "1080p"
        ),
        LiveTvChannel(
            id: "welt",
            name: "WELT Live",
            logoUrl: "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/welt.png",
            streamUrl: "https://dash4.antik.sk/live/test_welt_tizen/playlist.m3u8",
            category: "news",
            qualityBadge: "720p"
        ),
        LiveTvChannel(
            id: "ntv",
            name: "n-tv Live",
            logoUrl: "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/ntv.png",
            streamUrl: "https://dash4.antik.sk/live/test_ntv_tizen/playlist.m3u8",
            category: "news",
            qualityBadge: "720p"
        ),
        LiveTvChannel(
            id: "zdfinfo",
            name: "ZDFinfo HD",
            logoUrl: "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/zdfinfohd.png",
            streamUrl: "https://zdf-hls-17.akamaized.net/hls/live/2016500/de/veryhigh/master.m3u8",
            category: "news",
            qualityBadge: "1080p"
        ),
        LiveTvChannel(
            id: "zdfneo",
            name: "ZDFneo HD",
            logoUrl: "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/zdfneohd.png",
            streamUrl: "https://zdf-hls-16.akamaized.net/hls/live/2016499/de/veryhigh/master.m3u8",
            category: "news",
            qualityBadge: "1080p"
        ),
        LiveTvChannel(
            id: "kika",
            name: "KiKA HD",
            logoUrl: "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/kika.png",
            streamUrl: "https://dash4.antik.sk/live/test_kika_tizen/playlist.m3u8",
            category: "news",
            qualityBadge: "720p"
        ),
        LiveTvChannel(
            id: "wdr",
            name: "WDR Fernsehen HD",
            logoUrl: "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/wdrhd.png",
            streamUrl: "https://wdr-live.ard-mcdn.de/wdr/live/hls/de/master.m3u8",
            category: "news",
            qualityBadge: "1080p"
        ),
        LiveTvChannel(
            id: "swr",
            name: "SWR Fernsehen HD",
            logoUrl: "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/swrhd.png",
            streamUrl: "https://dash4.antik.sk/live/test_swr_tizen/playlist.m3u8",
            category: "news",
            qualityBadge: "720p"
        ),

        // === ÖSTERREICH / F1 ===
        LiveTvChannel(
            id: "servustv_at",
            name: "ServusTV Österreich (F1 Live)",
            logoUrl: "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/servustvhd.png",
            streamUrl: "https://s6.hopslan.com/servuz1/index.m3u8",
            category: "at_ch",
            qualityBadge: "HD"
        ),
        LiveTvChannel(
            id: "orf1",
            name: "ORF 1 (F1 Live)",
            logoUrl: "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/orf1hd.png",
            streamUrl: "https://test02.mdn.ors.at/orf/orf1/qxa/manifest.m3u8",
            category: "at_ch",
            qualityBadge: "HD"
        )
    ]

    private static let lock = NSLock()
    private static var customCache: (url: String, channels: [LiveTvChannel])?
    private static let nonHlsExtensions = [".ts", ".mp4", ".mkv", ".mp3", ".aac", ".flv", ".mpd"]

    public static func loadChannels(forceRefresh: Bool = false) async -> LiveTvLoadResult {
        var errors: [String] = []
        let custom = await loadCustomList(forceRefresh: forceRefresh)
        if let error = custom.error { errors.append(error) }
        let folder = loadFolder(forceRefresh: forceRefresh)
        if let error = folder.error { errors.append(error) }
        return LiveTvLoadResult(
            channels: presetChannels + custom.channels + folder.channels,
            customError: errors.isEmpty ? nil : errors.joined(separator: " "),
            categories: categories + folder.categories
        )
    }

    private static func loadCustomList(forceRefresh: Bool) async -> (channels: [LiveTvChannel], error: String?) {
        let customUrl = LocalStorage.shared.customM3uUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !customUrl.isEmpty else { return ([], nil) }

        let cached = lock.withLock { customCache }
        if !forceRefresh, let cached, cached.url == customUrl { return (cached.channels, nil) }

        do {
            let (_, body) = try await HttpClient.getHtml(customUrl, referer: customUrl)
            let custom = parseM3u(body)
            lock.withLock { customCache = (customUrl, custom) }
            return (custom, custom.isEmpty ? "Die eigene Liste enthält keine abspielbaren Sender." : nil)
        } catch {
            return ([], "Eigene Liste nicht erreichbar (\(error.localizedDescription)).")
        }
    }

    // MARK: - Eigener Ordner mit M3U-Dateien

    public static let folderMaxFiles = 500
    public static let folderMaxDepth = 8
    public static let folderMaxFileBytes = 8 * 1024 * 1024
    private static let folderPrefix = "folder:"
    private static var folderCache: (key: String, categories: [LiveTvCategory], channels: [LiveTvChannel])?

    public enum FolderError: LocalizedError {
        case unreadable
        public var errorDescription: String? { "Ordner nicht lesbar" }
    }

    private static func loadFolder(forceRefresh: Bool) -> (categories: [LiveTvCategory], channels: [LiveTvChannel], error: String?) {
        let bookmark = LocalStorage.shared.m3uFolderBookmark
        guard !bookmark.isEmpty else { return ([], [], nil) }

        let cached = lock.withLock { folderCache }
        if !forceRefresh, let cached, cached.key == bookmark { return (cached.categories, cached.channels, nil) }

        guard let data = Data(base64Encoded: bookmark), let url = resolveFolderBookmark(data) else {
            return ([], [], "Ordner nicht mehr erreichbar – bitte neu auswählen.")
        }
        let accessing = url.startAccessingSecurityScopedResource()
        defer { if accessing { url.stopAccessingSecurityScopedResource() } }
        guard let files = try? readFolder(url) else {
            return ([], [], "Ordner nicht lesbar – bitte neu auswählen.")
        }
        let rootName = LocalStorage.shared.m3uFolderName.isEmpty ? "Eigener Ordner" : LocalStorage.shared.m3uFolderName
        let result = buildFolderChannels(rootName: rootName, files: files)
        lock.withLock { folderCache = (bookmark, result.categories, result.channels) }
        let error: String?
        if files.isEmpty {
            error = "Im Ordner wurden keine M3U-Dateien gefunden."
        } else if result.channels.isEmpty {
            error = "Die M3U-Dateien im Ordner enthalten keine abspielbaren Sender."
        } else {
            error = nil
        }
        return (result.categories, result.channels, error)
    }

    private static func resolveFolderBookmark(_ data: Data) -> URL? {
        var stale = false
        guard let url = try? URL(resolvingBookmarkData: data, options: [], relativeTo: nil, bookmarkDataIsStale: &stale) else { return nil }
        // Veraltetes Lesezeichen (Ordner verschoben/umbenannt) gleich erneuern
        if stale, let fresh = try? url.bookmarkData(options: [], includingResourceValuesForKeys: nil, relativeTo: nil) {
            LocalStorage.shared.m3uFolderBookmark = fresh.base64EncodedString()
        }
        return url
    }

    /// Gewählten Ordner merken (die Freigabe aus der Dateien-App muss dabei aktiv sein). false = ging nicht.
    @discardableResult
    public static func saveM3uFolder(_ url: URL) -> Bool {
        let accessing = url.startAccessingSecurityScopedResource()
        defer { if accessing { url.stopAccessingSecurityScopedResource() } }
        guard let data = try? url.bookmarkData(options: [], includingResourceValuesForKeys: nil, relativeTo: nil) else { return false }
        LocalStorage.shared.m3uFolderBookmark = data.base64EncodedString()
        LocalStorage.shared.m3uFolderName = url.lastPathComponent
        lock.withLock { folderCache = nil }
        return true
    }

    public static func removeM3uFolder() {
        LocalStorage.shared.m3uFolderBookmark = ""
        LocalStorage.shared.m3uFolderName = ""
        lock.withLock { folderCache = nil }
    }

    public static func isM3uFileName(_ name: String) -> Bool {
        let lower = name.lowercased()
        return lower.hasSuffix(".m3u") || lower.hasSuffix(".m3u8")
    }

    /// Ordner samt Unterordnern lesen (versteckte Dateien und Ordner ausgelassen).
    public static func readFolder(_ root: URL) throws -> [M3uFolderFile] {
        let fm = FileManager.default
        var isDir: ObjCBool = false
        guard fm.fileExists(atPath: root.path, isDirectory: &isDir), isDir.boolValue else { throw FolderError.unreadable }
        var result: [M3uFolderFile] = []

        func walk(_ dir: URL, _ relative: String, _ depth: Int) {
            guard depth <= folderMaxDepth, result.count < folderMaxFiles else { return }
            let keys: [URLResourceKey] = [.isDirectoryKey, .fileSizeKey]
            guard let entries = try? fm.contentsOfDirectory(at: dir, includingPropertiesForKeys: keys, options: []) else { return }
            let sorted = entries.sorted { $0.lastPathComponent.lowercased() < $1.lastPathComponent.lowercased() }
            var subdirs: [URL] = []
            for entry in sorted {
                let name = entry.lastPathComponent
                // iCloud-Dateien, die noch nicht auf dem Gerät sind, heißen ".Name.m3u.icloud": herunterladen lassen
                if name.hasPrefix(".") && name.hasSuffix(".icloud") {
                    try? fm.startDownloadingUbiquitousItem(at: entry)
                    continue
                }
                if name.hasPrefix(".") { continue }
                let values = try? entry.resourceValues(forKeys: Set(keys))
                if values?.isDirectory == true {
                    subdirs.append(entry)
                } else if isM3uFileName(name), (values?.fileSize ?? 0) <= folderMaxFileBytes, result.count < folderMaxFiles,
                          let data = try? Data(contentsOf: entry) {
                    let text = String(data: data, encoding: .utf8) ?? String(decoding: data, as: UTF8.self)
                    result.append(M3uFolderFile(dir: relative, fileName: name, content: text))
                }
            }
            for sub in subdirs {
                walk(sub, relative.isEmpty ? sub.lastPathComponent : "\(relative)/\(sub.lastPathComponent)", depth + 1)
            }
        }

        walk(root, "", 0)
        return result
    }

    /// Je Ordner ein Reiter ("Sport › DAZN") mit den Sendern aller M3U-Dateien darin; die oberste Ebene heißt wie der Ordner.
    public static func buildFolderChannels(rootName: String, files: [M3uFolderFile]) -> (categories: [LiveTvCategory], channels: [LiveTvChannel]) {
        var cats: [LiveTvCategory] = []
        var channels: [LiveTvChannel] = []
        let groups = Dictionary(grouping: files) { $0.dir.trimmingCharacters(in: CharacterSet(charactersIn: "/")) }
        for dir in groups.keys.sorted(by: { $0.lowercased() < $1.lowercased() }) {
            let categoryId = folderPrefix + dir
            let before = channels.count
            for file in (groups[dir] ?? []).sorted(by: { $0.fileName.lowercased() < $1.fileName.lowercased() }) {
                let base = (file.fileName as NSString).deletingPathExtension
                channels += parseM3u(file.content, category: categoryId, idPrefix: "folder_\(channels.count)_", fallbackName: base)
            }
            if channels.count > before {
                let title = dir.isEmpty ? rootName : dir.split(separator: "/").joined(separator: " › ")
                cats.append(LiveTvCategory(id: categoryId, title: title, icon: "📁"))
            }
        }
        return (cats, channels)
    }

    /// Speichert die Adresse der eigenen M3U-Liste (leer = entfernen).
    public static func saveCustomM3uUrl(_ input: String) -> Bool {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalized: String
        if trimmed.isEmpty {
            normalized = ""
        } else {
            let withScheme: String
            let lower = trimmed.lowercased()
            if lower.hasPrefix("http://") || lower.hasPrefix("https://") {
                withScheme = trimmed
            } else {
                withScheme = "http://\(trimmed)"
            }
            guard let url = URL(string: withScheme),
                  let host = url.host,
                  !host.isEmpty else {
                return false
            }
            normalized = url.absoluteString
        }
        LocalStorage.shared.customM3uUrl = normalized
        lock.withLock { customCache = nil }
        return true
    }

    public static func isLikelyHls(_ url: String) -> Bool {
        let path = url
            .components(separatedBy: "?").first?
            .components(separatedBy: "#").first?
            .lowercased() ?? ""
        if path.hasSuffix(".m3u8") || path.hasSuffix(".m3u") {
            return true
        }
        if nonHlsExtensions.contains(where: { path.hasSuffix($0) }) {
            return false
        }
        return true
    }

    /// Live-Sender eines IPTV-Servers im Xtream-Format (http://server:8080/benutzer/passwort/12345):
    /// liefert rohes MPEG-TS, das das iPhone nicht abspielt.
    public static func xtreamLiveParts(_ url: String) -> (base: String, user: String, pass: String, id: String)? {
        let clean = url.components(separatedBy: "?").first?.components(separatedBy: "#").first ?? url
        guard let m = HtmlParser.allMatches(in: clean, pattern: #"^(https?://[^/]+)(?:/live)?/([^/]+)/([^/]+)/(\d+)(?:\.ts)?$"#).first,
              m.count == 5 else { return nil }
        return (m[1], m[2], m[3], m[4])
    }

    /// Adressen, die das iPhone der Reihe nach probiert: Xtream-Live zuerst als HLS (/live/…/12345.m3u8),
    /// dann wie in der Liste angegeben.
    public static func playbackCandidates(_ url: String) -> [String] {
        guard let parts = xtreamLiveParts(url) else { return [url] }
        let hls = "\(parts.base)/live/\(parts.user)/\(parts.pass)/\(parts.id).m3u8"
        return hls == url ? [url] : [hls, url]
    }

    /// Formate, die AVPlayer auf dem iPhone nicht abspielen kann (MKV, AVI …).
    public static func unsupportedOnIos(_ url: String) -> String? {
        let path = (url.components(separatedBy: "?").first ?? url).lowercased()
        for ext in ["mkv", "avi", "flv", "wmv", "mpg", "mpeg"] where path.hasSuffix(".\(ext)") {
            return ext.uppercased()
        }
        return nil
    }

    /// Sender aus einer M3U-Liste; `fallbackName` benennt Adressen ohne #EXTINF-Zeile (Datei mit nur einem Sender).
    public static func parseM3u(_ content: String, category: String = "custom", idPrefix: String = "custom_", fallbackName: String = "") -> [LiveTvChannel] {
        var channels: [LiveTvChannel] = []
        var currentName = ""
        var currentLogo = ""
        var currentHeaders: [String: String] = [:]

        for rawLine in content.components(separatedBy: .newlines) {
            let trimmed = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.lowercased().hasPrefix("#extinf") {
                let title = extinfTitle(trimmed)
                currentName = !title.isEmpty
                    ? title
                    : (HtmlParser.firstMatch(in: trimmed, pattern: #"tvg-name="([^"]*)""#, dotMatchesAll: false) ?? "")
                currentLogo = HtmlParser.firstMatch(in: trimmed, pattern: #"tvg-logo="([^"]*)""#, dotMatchesAll: false) ?? ""
                currentHeaders.removeAll()
            } else if trimmed.lowercased().hasPrefix("#extvlcopt:") {
                let option = String(trimmed.dropFirst("#extvlcopt:".count))
                guard let eqIdx = option.firstIndex(of: "=") else { continue }
                let key = option[..<eqIdx].trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                let value = option[option.index(after: eqIdx)...].trimmingCharacters(in: .whitespacesAndNewlines)
                guard !value.isEmpty else { continue }
                switch key {
                case "http-user-agent": currentHeaders["User-Agent"] = value
                case "http-referrer", "http-referer": currentHeaders["Referer"] = value
                case "http-origin": currentHeaders["Origin"] = value
                default: break
                }
            } else if trimmed.lowercased().hasPrefix("http://") || trimmed.lowercased().hasPrefix("https://") {
                var name = currentName
                if name.isEmpty && !fallbackName.isEmpty {
                    name = channels.contains(where: { $0.name == fallbackName }) ? "\(fallbackName) \(channels.count + 1)" : fallbackName
                }
                if !name.isEmpty {
                    channels.append(
                        LiveTvChannel(
                            id: "\(idPrefix)\(channels.count)",
                            name: name,
                            logoUrl: currentLogo,
                            streamUrl: trimmed,
                            category: category,
                            qualityBadge: "M3U",
                            headers: currentHeaders
                        )
                    )
                }
                currentName = ""
                currentLogo = ""
                currentHeaders.removeAll()
            }
        }
        return channels
    }

    private static func extinfTitle(_ line: String) -> String {
        var inQuotes = false
        for idx in line.indices {
            let c = line[idx]
            if c == "\"" {
                inQuotes.toggle()
            } else if c == "," && !inQuotes {
                return line[line.index(after: idx)...].trimmingCharacters(in: .whitespacesAndNewlines)
            }
        }
        return ""
    }
}
