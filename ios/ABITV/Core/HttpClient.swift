import Foundation
#if canImport(Network)
import Network
#endif

/// Gemeinsamer HTTP-Client mit Cookie-Speicher, Dropload-Domain-Umschreibung und
/// verschlüsselter DNS-Auflösung (DNS-over-HTTPS gegen CUII-Netzsperren von Telekom, Vodafone & O2).
public final class HttpClient: @unchecked Sendable {

    public static let baseUrl = "https://aniworld.to"
    public static let userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    public static let shared = HttpClient()

    public let session: URLSession
    private let redirectHandler = RedirectHandler()

    private final class RedirectHandler: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
        func urlSession(
            _ session: URLSession,
            task: URLSessionTask,
            willPerformHTTPRedirection response: HTTPURLResponse,
            newRequest request: URLRequest,
            completionHandler: @escaping (URLRequest?) -> Void
        ) {
            var redirected = request
            if let rawUrl = request.url?.absoluteString,
               let rewritten = HttpClient.rewriteUrl(rawUrl),
               rewritten != request.url {
                redirected.url = rewritten
            }
            redirected.setValue(HttpClient.userAgent, forHTTPHeaderField: "User-Agent")
            completionHandler(redirected)
        }
    }

    private init() {
        // DNS-over-HTTPS (Cloudflare 1.1.1.1 / 1.0.0.1) als primären Resolver konfigurieren, damit
        // CUII-DNS-Sperren im Mobilfunk und WLAN automatisch umgangen werden (wie UnblockedDns in HttpClient.kt),
        // aber bei blockiertem DoH-Port sauber auf System-DNS zurückgefallen werden kann.
        #if canImport(Network)
        if let dohUrl = URL(string: "https://cloudflare-dns.com/dns-query") {
            NWParameters.PrivacyContext.default.requireEncryptedNameResolution(
                false,
                fallbackResolver: .https(
                    dohUrl,
                    serverAddresses: [
                        .hostPort(host: "1.1.1.1", port: 443),
                        .hostPort(host: "1.0.0.1", port: 443),
                        .hostPort(host: "2606:4700:4700::1111", port: 443)
                    ]
                )
            )
        }
        #endif

        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 15
        config.timeoutIntervalForResource = 25
        config.httpCookieAcceptPolicy = .always
        config.httpShouldSetCookies = true
        config.httpCookieStorage = HTTPCookieStorage.shared
        config.httpAdditionalHeaders = [
            "User-Agent": Self.userAgent,
            "Accept-Language": "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7"
        ]
        self.session = URLSession(configuration: config, delegate: redirectHandler, delegateQueue: nil)
    }

    /// Schreibt `dropload.io` auf `dr0pstream.com` um (wie der OkHttp-Interceptor auf Android/PC).
    public static func rewriteUrl(_ rawUrl: String) -> URL? {
        guard var components = URLComponents(string: rawUrl) else { return nil }
        if let host = components.host?.lowercased(),
           host == "dropload.io" || host == "www.dropload.io" {
            components.host = "dr0pstream.com"
        }
        return components.url
    }

    public static func getHtml(
        _ urlOrPath: String,
        referer: String = "\(baseUrl)/",
        headers: [String: String] = [:]
    ) async throws -> (finalUrl: String, body: String) {
        let fullUrl: String
        if urlOrPath.lowercased().hasPrefix("http") {
            fullUrl = urlOrPath
        } else {
            let slash = urlOrPath.hasPrefix("/") ? "" : "/"
            fullUrl = "\(baseUrl)\(slash)\(urlOrPath)"
        }
        guard let url = rewriteUrl(fullUrl) else {
            throw URLError(.badURL)
        }

        var req = URLRequest(url: url)
        req.httpMethod = "GET"
        req.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        req.setValue("de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7", forHTTPHeaderField: "Accept-Language")
        req.setValue(referer, forHTTPHeaderField: "Referer")
        for (k, v) in headers {
            req.setValue(v, forHTTPHeaderField: k)
        }

        let (data, response) = try await shared.session.data(for: req)
        let finalUrl = response.url?.absoluteString ?? fullUrl
        let body = String(data: data, encoding: .utf8)
            ?? String(data: data, encoding: .isoLatin1)
            ?? ""
        return (finalUrl, body)
    }

    public static func postForm(
        _ urlString: String,
        fields: [(String, String)],
        referer: String,
        extraHeaders: [String: String] = [:]
    ) async throws -> String {
        guard let url = rewriteUrl(urlString) else {
            throw URLError(.badURL)
        }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        req.setValue("application/x-www-form-urlencoded; charset=UTF-8", forHTTPHeaderField: "Content-Type")
        req.setValue(referer, forHTTPHeaderField: "Referer")
        for (k, v) in extraHeaders {
            req.setValue(v, forHTTPHeaderField: k)
        }

        let formString = fields.map { key, value in
            "\(urlEncodeFormComponent(key))=\(urlEncodeFormComponent(value))"
        }.joined(separator: "&")
        req.httpBody = formString.data(using: .utf8)

        let (data, _) = try await shared.session.data(for: req)
        return String(data: data, encoding: .utf8) ?? ""
    }

    public static func postJson(
        _ urlString: String,
        jsonBody: String,
        referer: String
    ) async throws -> String {
        guard let url = rewriteUrl(urlString) else {
            throw URLError(.badURL)
        }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        req.setValue(referer, forHTTPHeaderField: "Referer")
        req.httpBody = jsonBody.data(using: .utf8)

        let (data, _) = try await shared.session.data(for: req)
        return String(data: data, encoding: .utf8) ?? ""
    }

    public static func normalizeImageUrl(_ raw: String?) -> String {
        guard let raw = raw?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else {
            return ""
        }
        let upgraded = raw.replacingOccurrences(of: "_150x225.", with: "_220x330.")
        if upgraded.hasPrefix("http") {
            return upgraded
        } else if upgraded.hasPrefix("//") {
            return "https:\(upgraded)"
        } else if upgraded.hasPrefix("/") {
            return "\(baseUrl)\(upgraded)"
        } else {
            return "\(baseUrl)/\(upgraded)"
        }
    }

    private static func urlEncodeFormComponent(_ value: String) -> String {
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._*")
        return value.addingPercentEncoding(withAllowedCharacters: allowed)?
            .replacingOccurrences(of: "%20", with: "+") ?? value
    }
}
