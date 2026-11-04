import Foundation

/// Unsichtbarer Browser (WKWebView auf iOS), der eine Hoster-Seite lädt und die
/// erste .m3u8/.mp4-Adresse abgreift. Liefert (Stream-Adresse, Seiten-Adresse) oder nil.
public protocol BrowserStreamExtractor: Sendable {
    func extract(targetUrl: String, referer: String) async -> (streamUrl: String, pageUrl: String)?
}

/// Gemeinsame Regeln für den Browser-Extraktor (entspricht `BrowserStreamHooks` in `Platform.kt`).
public enum BrowserStreamHooks {
    public static let timeoutMs: UInt64 = 16_000
    public static let bridgeName = "AndroidBridge"

    public static let jsPlayerHook = """
    (function() {
        if (window.__awHooked) return;
        window.__awHooked = true;
        function reportStream(url, href) {
            try {
                if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.AndroidBridge) {
                    window.webkit.messageHandlers.AndroidBridge.postMessage({ url: String(url || ''), href: String(href || location.href) });
                } else if (window.AndroidBridge && window.AndroidBridge.onStream) {
                    window.AndroidBridge.onStream(url, href || location.href);
                }
            } catch (e) {}
        }
        var origJw = window.jwplayer;
        function wrapJw(val) {
            if (typeof val !== 'function') return val;
            var fn = function() {
                var inst = val.apply(this, arguments);
                if (inst && inst.setup && !inst.__awSetup) {
                    var origSetup = inst.setup;
                    inst.setup = function(cfg) {
                        try {
                            if (cfg && cfg.sources && cfg.sources.length) {
                                for (var i = 0; i < cfg.sources.length; i++) {
                                    if (cfg.sources[i].file) reportStream(cfg.sources[i].file, location.href);
                                }
                            }
                            if (cfg && cfg.file) reportStream(cfg.file, location.href);
                        } catch (e) {}
                        return origSetup.apply(this, arguments);
                    };
                    inst.__awSetup = true;
                }
                return inst;
            };
            return fn;
        }
        if (origJw) { window.jwplayer = wrapJw(origJw); }
        // WKWebView meldet nur Seitenaufrufe an die App, keine Netzwerk-Anfragen aus JavaScript
        // (anders als shouldInterceptRequest auf Android). Darum XHR und fetch hier mitlesen.
        function checkRequestUrl(url) {
            try {
                if (!url) return;
                var text = String(url);
                var lower = text.toLowerCase();
                if (lower.indexOf('.m3u8') === -1 && lower.indexOf('/hls2/') === -1 && lower.indexOf('.mp4') === -1) return;
                reportStream(new URL(text, location.href).href, location.href);
            } catch (e) {}
        }
        try {
            var origOpen = XMLHttpRequest.prototype.open;
            XMLHttpRequest.prototype.open = function(method, url) {
                checkRequestUrl(url);
                return origOpen.apply(this, arguments);
            };
        } catch (e) {}
        try {
            if (window.fetch) {
                var origFetch = window.fetch;
                window.fetch = function(input) {
                    checkRequestUrl(typeof input === 'string' ? input : (input && input.url));
                    return origFetch.apply(this, arguments);
                };
            }
        } catch (e) {}
        setInterval(function() {
            try {
                var vids = document.getElementsByTagName('video');
                for (var i = 0; i < vids.length; i++) {
                    var vidSrc = vids[i].currentSrc || vids[i].src;
                    if (vidSrc && vidSrc.indexOf('http') === 0) {
                        reportStream(vidSrc, location.href);
                    }
                }
                var sources = document.querySelectorAll('video source');
                for (var s = 0; s < sources.length; s++) {
                    if (sources[s].src && sources[s].src.indexOf('http') === 0) {
                        reportStream(sources[s].src, location.href);
                    }
                }
            } catch (e) {}
            try {
                if (typeof window.jwplayer === 'function') {
                    var jp = window.jwplayer();
                    var pl = jp && jp.getPlaylist ? jp.getPlaylist() : null;
                    if (pl && pl.length) {
                        var item = pl[0];
                        if (item.file && String(item.file).indexOf('http') === 0) reportStream(item.file, location.href);
                        if (item.sources) {
                            for (var k = 0; k < item.sources.length; k++) {
                                var f = item.sources[k].file;
                                if (f && String(f).indexOf('http') === 0) reportStream(f, location.href);
                            }
                        }
                    }
                }
            } catch (e) {}
        }, 400);
    })();
    """

    public static func isMediaStreamUrl(_ url: String) -> Bool {
        let lower = url.lowercased()
        if lower.contains("slides") || lower.contains("preview") || lower.contains("thumb") {
            return false
        }
        return lower.contains(".m3u8") ||
            lower.contains("/hls2/") ||
            (lower.contains(".mp4") && !lower.contains("ad"))
    }

    public static func isBlockedAdOrAsset(_ url: String) -> Bool {
        let lower = url.lowercased()
        return lower.hasSuffix(".jpg") ||
            lower.hasSuffix(".jpeg") ||
            lower.hasSuffix(".png") ||
            lower.hasSuffix(".gif") ||
            lower.hasSuffix(".webp") ||
            lower.hasSuffix(".woff2") ||
            lower.contains("doubleclick") ||
            lower.contains("googlesyndication") ||
            lower.contains("popads") ||
            lower.contains("adskeeper")
    }
}

public enum StreamResolverError: LocalizedError {
    case deadLink(String)
    case extractionFailed(String)

    public var errorDescription: String? {
        switch self {
        case .deadLink(let msg), .extractionFailed(let msg):
            return msg
        }
    }
}

/// Zweistufiger Stream-Resolver (entspricht 1:1 `StreamResolver.kt` aus `:shared`).
/// 1. Schneller nativer URLSession-Resolver für VOE (inkl. ROT13/Token/Base64), Vidmoly, Dropload, Doodstream, Mixdrop, Filemoon.
/// 2. Automatischer Fallback über `browserExtractor` (unsichtbare WKWebView auf iOS).
public enum StreamResolver {

    private static let cacheTtlMs: Int64 = 90 * 60 * 1000
    private static let lock = NSLock()
    private static var streamCache: [String: (timestamp: Int64, stream: ResolvedStream)] = [:]
    private static var hosterPages: [String: String] = [:]

    public static var browserExtractor: BrowserStreamExtractor?

    private static let voeJunkTokens = ["*~", "%?", "@$", "^^", "#&", "!!", "~@"]
    private static let deadLinkTitleMarkers = [
        "not found", "nicht gefunden", "file was deleted", "file is no longer available", "video not found"
    ]

    public static func isDeadLinkPage(_ html: String) -> Bool {
        let title = (HtmlParser.firstMatch(in: html, pattern: #"<title>([^<]*)</title>"#) ?? "").lowercased()
        return deadLinkTitleMarkers.contains { title.contains($0) }
    }

    public static func invalidateCache(_ redirectPath: String) {
        _ = lock.withLock { streamCache.removeValue(forKey: redirectPath) }
    }

    public static func resolveHoster(_ hoster: HosterLink) async throws -> ResolvedStream {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        if let cached = lock.withLock({ streamCache[hoster.redirectPath] }), now - cached.timestamp < cacheTtlMs {
            return cached.stream
        }

        // Stufe 1: Schnell-Auflösung über URLSession
        do {
            if let fastResult = try await resolveDirect(hoster) {
                lock.withLock {
                    hosterPages.removeValue(forKey: hoster.redirectPath)
                    streamCache[hoster.redirectPath] = (now, fastResult)
                }
                return fastResult
            }
        } catch let err as StreamResolverError {
            _ = lock.withLock { hosterPages.removeValue(forKey: hoster.redirectPath) }
            if case .deadLink = err {
                throw err
            }
        } catch {
            // Weiter zu Stufe 2
        }

        let hosterPage = lock.withLock { hosterPages.removeValue(forKey: hoster.redirectPath) }

        // Stufe 2: Unsichtbarer Browser (WKWebView) als Fallback
        let redirectUrl = hoster.redirectPath.hasPrefix("http")
            ? hoster.redirectPath
            : "\(HttpClient.baseUrl)\(hoster.redirectPath)"
        let browserUrl = hosterPage ?? redirectUrl

        guard let extractor = browserExtractor,
              let webViewPair = await extractor.extract(targetUrl: browserUrl, referer: "\(HttpClient.baseUrl)/") else {
            throw StreamResolverError.extractionFailed("Stream konnte bei \(hoster.hosterName) nicht extrahiert werden.")
        }

        let origin: String
        if let u = URL(string: webViewPair.pageUrl), let scheme = u.scheme, let host = u.host {
            origin = "\(scheme)://\(host)/"
        } else {
            origin = "\(HttpClient.baseUrl)/"
        }

        let lowerStream = webViewPair.streamUrl.lowercased()
        let resolved = ResolvedStream(
            streamUrl: webViewPair.streamUrl,
            isHls: lowerStream.contains(".m3u8") || lowerStream.contains("/hls"),
            hosterName: hoster.hosterName,
            language: hoster.language,
            headers: [
                "User-Agent": HttpClient.userAgent,
                "Referer": origin
            ]
        )
        lock.withLock { streamCache[hoster.redirectPath] = (now, resolved) }
        return resolved
    }

    private static func resolveDirect(_ hoster: HosterLink) async throws -> ResolvedStream? {
        // 0a. Direkter Dropload / dr0pstream Kurzpfad
        let lowerPath = hoster.redirectPath.lowercased()
        if lowerPath.contains("dr0pstream") || lowerPath.contains("dropload") {
            if let directCode = HtmlParser.firstMatch(in: hoster.redirectPath, pattern: #"(?:/e/|embed-|/d/)([a-zA-Z0-9]{10,16})"#),
               let droploadStream = try? await resolveDroploadDirect(fileCode: directCode, originHost: "https://dr0pstream.com") {
                return ResolvedStream(
                    streamUrl: droploadStream,
                    isHls: droploadStream.lowercased().contains(".m3u8"),
                    hosterName: hoster.hosterName,
                    language: hoster.language,
                    headers: [
                        "User-Agent": HttpClient.userAgent,
                        "Referer": "https://dr0pstream.com/"
                    ]
                )
            }
        }

        let initialReferer = SerienStreamRef.isStoUrl(hoster.redirectPath)
            ? "\(SerienStreamScraper.baseUrl)/"
            : "\(HttpClient.baseUrl)/"
        var (currentUrl, html) = try await HttpClient.getHtml(hoster.redirectPath, referer: initialReferer)

        // Bis zu 3 JS-Weiterleitungen (window.location.href = '...') folgen
        for _ in 0..<3 {
            if let jsRedirect = HtmlParser.firstMatch(in: html, pattern: #"window\.location\.href\s*=\s*['"](https?://[^'"]+)['"]"#),
               !jsRedirect.isEmpty,
               jsRedirect != currentUrl {
                let next = try await HttpClient.getHtml(jsRedirect, referer: currentUrl)
                currentUrl = next.finalUrl
                html = next.body
            } else {
                break
            }
        }

        lock.withLock { hosterPages[hoster.redirectPath] = currentUrl }

        if isDeadLinkPage(html) {
            throw StreamResolverError.deadLink("\(hoster.hosterName): Datei nicht mehr verfügbar (\(currentUrl))")
        }

        let originReferer: String
        if let u = URL(string: currentUrl), let scheme = u.scheme, let host = u.host {
            originReferer = "\(scheme)://\(host)/"
        } else {
            originReferer = currentUrl
        }

        // 0b. Dropload / dr0pstream nach Redirect
        let lowerCurrent = currentUrl.lowercased()
        if lowerCurrent.contains("dr0pstream") ||
           lowerCurrent.contains("dropload") ||
           hoster.hosterName.caseInsensitiveCompare("Dropload") == .orderedSame {
            let fileCode = HtmlParser.firstMatch(in: currentUrl, pattern: #"(?:/e/|embed-|/d/)([a-zA-Z0-9]{10,16})"#)
                ?? HtmlParser.firstMatch(in: html, pattern: #"name=["']file_code["']\s+value=["']([a-zA-Z0-9]+)["']"#)
            if let fileCode, !fileCode.isEmpty,
               let droploadStream = try? await resolveDroploadDirect(fileCode: fileCode, originHost: "https://dr0pstream.com") {
                return ResolvedStream(
                    streamUrl: droploadStream,
                    isHls: droploadStream.lowercased().contains(".m3u8"),
                    hosterName: hoster.hosterName,
                    language: hoster.language,
                    headers: [
                        "User-Agent": HttpClient.userAgent,
                        "Referer": "https://dr0pstream.com/"
                    ]
                )
            }
        }

        // 1. VOE JSON-Payload-Entschlüsselung (<script type="application/json">["..."]</script>)
        if let jsonScript = HtmlParser.firstMatch(
            in: html,
            pattern: #"<script\s+type=["']application/json["']>\s*(\[\s*".*?"\s*\])\s*</script>"#
        ),
        let decodedUrl = decodeVoePayload(jsonScript),
        !decodedUrl.isEmpty {
            return ResolvedStream(
                streamUrl: decodedUrl,
                isHls: decodedUrl.lowercased().contains(".m3u8"),
                hosterName: hoster.hosterName,
                language: hoster.language,
                headers: [
                    "User-Agent": HttpClient.userAgent,
                    "Referer": originReferer
                ]
            )
        }

        // 2. Klassische VOE Base64-Variante ('hls': 'aHR0c...')
        if let rawHls = HtmlParser.firstMatch(in: html, pattern: #"['"]hls['"]\s*:\s*['"]([A-Za-z0-9+/=]{20,})['"]"#) {
            let decoded = rawHls.hasPrefix("http") ? rawHls : decodeBase64Text(rawHls)
            if let decoded, decoded.hasPrefix("http") {
                return ResolvedStream(
                    streamUrl: decoded,
                    isHls: true,
                    hosterName: hoster.hosterName,
                    language: hoster.language,
                    headers: [
                        "User-Agent": HttpClient.userAgent,
                        "Referer": originReferer
                    ]
                )
            }
        }

        // 3. Direkte .m3u8 URL im Quellcode (z.B. Vidmoly)
        let m3u8Matches = HtmlParser.allMatches(in: html, pattern: #"['"](https?://[^'"\s]+\.m3u8[^'"\s]*)['"]"#)
        if let directM3u8 = m3u8Matches.compactMap({ $0.count > 1 ? $0[1] : nil }).first(where: { !$0.contains("slides") }) {
            return ResolvedStream(
                streamUrl: directM3u8,
                isHls: true,
                hosterName: hoster.hosterName,
                language: hoster.language,
                headers: [
                    "User-Agent": HttpClient.userAgent,
                    "Referer": originReferer
                ]
            )
        }

        // 4. Doodstream / Playmogo (/pass_md5/...)
        if let passPath = HtmlParser.firstMatch(in: html, pattern: #"(/pass_md5/[^'"\s]+)"#) {
            let origin = originReferer.hasSuffix("/") ? String(originReferer.dropLast()) : originReferer
            let token = passPath.components(separatedBy: "/").last ?? ""
            if let (_, baseStream) = try? await HttpClient.getHtml("\(origin)\(passPath)", referer: currentUrl),
               baseStream.hasPrefix("http") {
                let expiry = Int64(Date().timeIntervalSince1970 * 1000)
                let finalMp4 = "\(baseStream.trimmingCharacters(in: .whitespacesAndNewlines))1234567890?token=\(token)&expiry=\(expiry)"
                return ResolvedStream(
                    streamUrl: finalMp4,
                    isHls: false,
                    hosterName: hoster.hosterName,
                    language: hoster.language,
                    headers: [
                        "User-Agent": HttpClient.userAgent,
                        "Referer": originReferer
                    ]
                )
            }
        }

        // 5. Gepacktes JavaScript (Mixdrop / Filemoon / Dropload: eval(function(p,a,c,k,e,d)...))
        let packedMatches = HtmlParser.allMatches(
            in: html,
            pattern: #"eval\(function\(p,a,c,k,e,d\)\{.*?\}\('(.*?)',(\d+),(\d+),'(.*?)'\.split\('\|'\)"#
        )
        if let packed = packedMatches.first, packed.count > 4,
           let radix = Int(packed[2]),
           let count = Int(packed[3]) {
            let unpacked = unpackJs(
                p: packed[1],
                radix: radix,
                count: count,
                keywords: packed[4].components(separatedBy: "|")
            )

            // Mixdrop MDCore.wurl
            if var wurl = HtmlParser.firstMatch(in: unpacked, pattern: #"MDCore\.wurl\s*=\s*["']([^"']+)["']"#) {
                wurl = wurl.trimmingCharacters(in: .whitespacesAndNewlines)
                if wurl.hasPrefix("//") { wurl = "https:\(wurl)" }
                if wurl.hasPrefix("http") {
                    return ResolvedStream(
                        streamUrl: wurl,
                        isHls: wurl.lowercased().contains(".m3u8"),
                        hosterName: hoster.hosterName,
                        language: hoster.language,
                        headers: [
                            "User-Agent": HttpClient.userAgent,
                            "Referer": originReferer
                        ]
                    )
                }
            }

            // Filemoon / Dropload unpacked .m3u8
            if let unpackedM3u8 = HtmlParser.firstMatch(in: unpacked, pattern: #"['"](https?://[^'"\s]+\.m3u8[^'"\s]*)['"]"#) {
                return ResolvedStream(
                    streamUrl: unpackedM3u8,
                    isHls: true,
                    hosterName: hoster.hosterName,
                    language: hoster.language,
                    headers: [
                        "User-Agent": HttpClient.userAgent,
                        "Referer": originReferer
                    ]
                )
            }
        }

        // 6. Direkte .mp4 URL im Quellcode (z.B. SpeedFiles / Vidoza)
        let mp4Matches = HtmlParser.allMatches(in: html, pattern: #"['"](https?://[^'"\s]+\.mp4[^'"\s]*)['"]"#)
        if let directMp4 = mp4Matches.compactMap({ $0.count > 1 ? $0[1] : nil }).first(where: { !$0.contains("sample") && !$0.contains("preview") }) {
            return ResolvedStream(
                streamUrl: directMp4,
                isHls: false,
                hosterName: hoster.hosterName,
                language: hoster.language,
                headers: [
                    "User-Agent": HttpClient.userAgent,
                    "Referer": originReferer
                ]
            )
        }

        return nil
    }

    private static func resolveDroploadDirect(fileCode: String, originHost: String) async throws -> String? {
        let baseHost = originHost.lowercased().contains("dr0pstream") ? originHost : "https://dr0pstream.com"
        let dUrl = "\(baseHost)/d/\(fileCode)"
        let (resolvedBaseUrl, dHtml) = try await HttpClient.getHtml(dUrl, referer: "\(baseHost)/")

        let actualHost: String
        if let u = URL(string: resolvedBaseUrl), let scheme = u.scheme, let host = u.host {
            actualHost = "\(scheme)://\(host)"
        } else {
            actualHost = baseHost
        }

        let stepPath = HtmlParser.firstMatch(in: dHtml, pattern: #"href=["'](/d/\#(NSRegularExpression.escapedPattern(for: fileCode))_[^"']+)["']"#)
            ?? "/d/\(fileCode)_n"

        let stepUrl = "\(actualHost)\(stepPath)"
        let (_, stepHtml) = try await HttpClient.getHtml(stepUrl, referer: dUrl)
        guard let formHtml = HtmlParser.firstMatch(in: stepHtml, pattern: #"(<form\b[^>]*id=["']F1["'][^>]*>.*?</form>)"#) else {
            return nil
        }

        var fields: [(String, String)] = []
        for m in HtmlParser.allMatches(in: formHtml, pattern: #"(<input\b[^>]*name=["'][^"']+["'][^>]*>)"#) {
            guard let inp = m.first else { continue }
            let name = HtmlParser.attr(inp, "name")
            let value = HtmlParser.attr(inp, "value")
            if !name.isEmpty {
                fields.append((name, value))
            }
        }

        try await Task.sleep(nanoseconds: 1_250_000_000)

        let postHtml = try await HttpClient.postForm(
            stepUrl,
            fields: fields,
            referer: stepUrl,
            extraHeaders: ["Origin": actualHost]
        )

        for m in HtmlParser.allMatches(in: postHtml, pattern: #"(<a\b[^>]*href=["']https?://[^"']+["'][^>]*>)"#) {
            guard let aTag = m.first else { continue }
            let href = HtmlParser.attr(aTag, "href")
            if href.contains(".mp4") || href.contains("dropcdn") {
                return href
            }
        }
        return nil
    }

    public static func unpackJs(p: String, radix: Int, count: Int, keywords: [String]) -> String {
        guard radix >= 2 && radix <= 62 else { return p }
        let chars = Array("0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
        func baseN(_ num: Int, _ b: Int) -> String {
            if num == 0 { return "0" }
            var n = num
            var out: [Character] = []
            while n > 0 {
                out.insert(chars[n % b], at: 0)
                n /= b
            }
            return String(out)
        }
        var result = p
        for i in stride(from: count - 1, through: 0, by: -1) {
            if i < keywords.count, !keywords[i].isEmpty {
                let token = baseN(i, radix)
                let escaped = NSRegularExpression.escapedPattern(for: token)
                if let regex = try? NSRegularExpression(pattern: "\\b\(escaped)\\b") {
                    let ns = result as NSString
                    let template = NSRegularExpression.escapedTemplate(for: keywords[i])
                    result = regex.stringByReplacingMatches(
                        in: result,
                        range: NSRange(location: 0, length: ns.length),
                        withTemplate: template
                    )
                }
            }
        }
        return result
    }

    /// Entschlüsselt den verschleierten VOE-Container:
    /// 1. ROT13 auf Buchstaben
    /// 2. Junk-Symbole (*~, %?, @$, ^^, #&, !!, ~@) entfernen
    /// 3. Base64-Decode
    /// 4. ASCII-Shift um -3 Zeichen
    /// 5. String umkehren (reverse)
    /// 6. Base64-Decode -> JSON mit Feld "source" oder "direct_access_url"
    public static func decodeVoePayload(_ jsonArrayStr: String) -> String? {
        guard let data = jsonArrayStr.data(using: .utf8),
              let arr = (try? JSONSerialization.jsonObject(with: data)) as? [String],
              let first = arr.first else {
            return nil
        }
        var step = rot13(first)
        for token in voeJunkTokens {
            step = step.replacingOccurrences(of: token, with: "")
        }
        guard let b64Step1 = decodeBase64Text(step) else { return nil }
        var shiftedScalars: [UnicodeScalar] = []
        shiftedScalars.reserveCapacity(b64Step1.unicodeScalars.count)
        for scalar in b64Step1.unicodeScalars {
            guard scalar.value >= 3, let s = UnicodeScalar(scalar.value - 3) else { return nil }
            shiftedScalars.append(s)
        }
        let reversed = String(String.UnicodeScalarView(shiftedScalars.reversed()))
        guard let finalJson = decodeBase64Text(reversed),
              let finalData = finalJson.data(using: .utf8),
              let obj = (try? JSONSerialization.jsonObject(with: finalData)) as? [String: Any] else {
            return nil
        }
        if let source = obj["source"] as? String, source.hasPrefix("http") {
            return source
        }
        if let direct = obj["direct_access_url"] as? String, direct.hasPrefix("http") {
            return direct
        }
        return nil
    }

    public static func rot13(_ input: String) -> String {
        var scalars: [UnicodeScalar] = []
        scalars.reserveCapacity(input.unicodeScalars.count)
        for s in input.unicodeScalars {
            let v = s.value
            if v >= 97 && v <= 122 {
                scalars.append(UnicodeScalar((v - 97 + 13) % 26 + 97)!)
            } else if v >= 65 && v <= 90 {
                scalars.append(UnicodeScalar((v - 65 + 13) % 26 + 65)!)
            } else {
                scalars.append(s)
            }
        }
        return String(String.UnicodeScalarView(scalars))
    }

    public static func decodeBase64Text(_ input: String) -> String? {
        var normalized = input
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let rem = normalized.count % 4
        if rem > 0 {
            normalized += String(repeating: "=", count: 4 - rem)
        }
        guard let data = Data(base64Encoded: normalized, options: .ignoreUnknownCharacters) else {
            return nil
        }
        return String(data: data, encoding: .utf8)
    }
}
