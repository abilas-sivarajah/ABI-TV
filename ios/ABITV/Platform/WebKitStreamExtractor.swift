import Foundation
#if canImport(WebKit)
import WebKit
#if canImport(UIKit)
import UIKit
#endif

/// Unsichtbare `WKWebView` für Hoster, die ihren Stream erst nach JavaScript-Ausführung preisgeben
/// (entspricht `WebViewStreamExtractor.kt` auf Android).
public final class WebKitStreamExtractor: BrowserStreamExtractor, @unchecked Sendable {

    public init() {}

    public func extract(targetUrl: String, referer: String) async -> (streamUrl: String, pageUrl: String)? {
        await ExtractionSession.extract(targetUrl: targetUrl, referer: referer)
    }
}

@MainActor
private final class ExtractionSession: NSObject, WKNavigationDelegate, WKScriptMessageHandler {
    private let targetUrl: String
    private let referer: String
    private var webView: WKWebView?
    private var continuation: CheckedContinuation<(streamUrl: String, pageUrl: String)?, Never>?
    private var timeoutTask: Task<Void, Never>?
    private var finished = false

    static func extract(targetUrl: String, referer: String) async -> (streamUrl: String, pageUrl: String)? {
        let session = ExtractionSession(targetUrl: targetUrl, referer: referer)
        return await session.run()
    }

    init(targetUrl: String, referer: String) {
        self.targetUrl = targetUrl
        self.referer = referer
        super.init()
    }

    func run() async -> (streamUrl: String, pageUrl: String)? {
        await withCheckedContinuation { cont in
            self.continuation = cont
            startWebView()
        }
    }

    private func startWebView() {
        guard let url = URL(string: targetUrl) else {
            complete(with: nil)
            return
        }

        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []

        let contentController = WKUserContentController()
        let userScript = WKUserScript(
            source: BrowserStreamHooks.jsPlayerHook,
            injectionTime: .atDocumentStart,
            forMainFrameOnly: false
        )
        contentController.addUserScript(userScript)
        contentController.add(self, name: BrowserStreamHooks.bridgeName)
        config.userContentController = contentController

        let wv = WKWebView(frame: CGRect(x: 0, y: 0, width: 320, height: 180), configuration: config)
        wv.customUserAgent = HttpClient.userAgent
        wv.navigationDelegate = self
        self.webView = wv
        attachToWindow(wv)

        var req = URLRequest(url: url)
        req.setValue(referer, forHTTPHeaderField: "Referer")
        wv.load(req)

        timeoutTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: BrowserStreamHooks.timeoutMs * 1_000_000)
            self?.complete(with: nil)
        }
    }

    /// Ohne Fenster bremst iOS JavaScript-Timer und Player der WebView aus, der Stream käme nie zustande.
    /// Deshalb unsichtbar hinter die App-Oberfläche legen statt frei schweben zu lassen.
    private func attachToWindow(_ webView: WKWebView) {
        #if canImport(UIKit)
        let windows = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
        guard let window = windows.first(where: { $0.isKeyWindow }) ?? windows.first else { return }
        webView.isUserInteractionEnabled = false
        webView.alpha = 0.01
        window.insertSubview(webView, at: 0)
        #endif
    }

    func userContentController(
        _ userContentController: WKUserContentController,
        didReceive message: WKScriptMessage
    ) {
        if let dict = message.body as? [String: Any],
           let url = dict["url"] as? String {
            let href = (dict["href"] as? String) ?? self.webView?.url?.absoluteString ?? self.targetUrl
            self.checkCandidate(url: url, pageUrl: href)
        } else if let url = message.body as? String {
            let href = self.webView?.url?.absoluteString ?? self.targetUrl
            self.checkCandidate(url: url, pageUrl: href)
        }
    }

    func webView(
        _ webView: WKWebView,
        decidePolicyFor navigationAction: WKNavigationAction,
        decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
    ) {
        let reqUrl = navigationAction.request.url?.absoluteString ?? ""
        if BrowserStreamHooks.isBlockedAdOrAsset(reqUrl) {
            decisionHandler(.cancel)
            return
        }
        if BrowserStreamHooks.isMediaStreamUrl(reqUrl) {
            let page = self.webView?.url?.absoluteString ?? self.targetUrl
            self.complete(with: (reqUrl, page))
            decisionHandler(.cancel)
            return
        }
        // Popup-Fenster (target="_blank" / window.open) von Werbe-Skripten blockieren
        if navigationAction.targetFrame == nil {
            decisionHandler(.cancel)
            return
        }
        decisionHandler(.allow)
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        self.webView?.evaluateJavaScript(BrowserStreamHooks.jsPlayerHook, completionHandler: nil)
    }

    private func checkCandidate(url: String, pageUrl: String) {
        let trimmed = url.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.hasPrefix("http"), BrowserStreamHooks.isMediaStreamUrl(trimmed) else { return }
        complete(with: (trimmed, pageUrl))
    }

    private func complete(with result: (streamUrl: String, pageUrl: String)?) {
        guard !finished else { return }
        finished = true
        timeoutTask?.cancel()
        timeoutTask = nil
        webView?.stopLoading()
        webView?.configuration.userContentController.removeScriptMessageHandler(forName: BrowserStreamHooks.bridgeName)
        webView?.navigationDelegate = nil
        #if canImport(UIKit)
        webView?.removeFromSuperview()
        #endif
        webView = nil
        continuation?.resume(returning: result)
        continuation = nil
    }
}
#endif
