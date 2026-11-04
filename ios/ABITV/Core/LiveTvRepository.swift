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
        let customUrl = LocalStorage.shared.customM3uUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !customUrl.isEmpty else {
            return LiveTvLoadResult(channels: presetChannels)
        }

        let cached = lock.withLock { customCache }
        if !forceRefresh, let cached, cached.url == customUrl {
            return LiveTvLoadResult(channels: presetChannels + cached.channels)
        }

        do {
            let (_, body) = try await HttpClient.getHtml(customUrl, referer: customUrl)
            let custom = parseM3u(body)
            lock.withLock { customCache = (customUrl, custom) }
            return LiveTvLoadResult(
                channels: presetChannels + custom,
                customError: custom.isEmpty ? "Die eigene Liste enthält keine abspielbaren Sender." : nil
            )
        } catch {
            return LiveTvLoadResult(
                channels: presetChannels,
                customError: "Eigene Liste nicht erreichbar (\(error.localizedDescription))."
            )
        }
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

    public static func parseM3u(_ content: String) -> [LiveTvChannel] {
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
                if !currentName.isEmpty {
                    channels.append(
                        LiveTvChannel(
                            id: "custom_\(channels.count)",
                            name: currentName,
                            logoUrl: currentLogo,
                            streamUrl: trimmed,
                            category: "custom",
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
