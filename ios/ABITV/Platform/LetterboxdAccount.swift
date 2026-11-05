import Foundation
import Combine
import SwiftUI
import UIKit
import WebKit

/// Letterboxd-Konto auf dem iPhone (wie auf PC und TV): Anmeldung auf der echten Letterboxd-Seite
/// in einer `WKWebView`, die App sieht das Passwort nie. WebKit behält die Anmeldung über Neustarts.
/// Ist der Besitzer der Empfehlungsliste angemeldet, kommt jeder Film, den er auf die Watchlist
/// setzt, auch in "Ilkems Empfehlungen".
@MainActor
final class LetterboxdAccount: ObservableObject {

    static let shared = LetterboxdAccount()

    private static let keyUser = "letterboxd_username"

    /// Angemeldeter Letterboxd-Name oder nil.
    @Published private(set) var username: String?
    /// Kurzer Hinweis für die Einstellungen.
    @Published private(set) var status: String?
    /// Steigt, wenn die App etwas zur Empfehlungsliste hinzugefügt hat – die Reihe lädt dann neu.
    @Published private(set) var listVersion = 0

    var isListOwner: Bool { LetterboxdListSync.isListOwner(username) }

    private var watchlistSubscription: AnyCancellable?
    private var queue: Task<Void, Never>?

    private init() {
        username = UserDefaults.standard.string(forKey: Self.keyUser)
    }

    /// Beim App-Start: Watchlist beobachten und neue Filme in die Empfehlungsliste eintragen.
    func start(storage: LocalStorage) {
        guard watchlistSubscription == nil else { return }
        var previous: Set<String>?
        watchlistSubscription = storage.$watchlist
            .receive(on: DispatchQueue.main)
            .sink { [weak self] list in
                let slugs = Set(list.map(\.cleanSlug))
                let before = previous
                previous = slugs
                // Beim Start nur den Stand merken; danach zählen nur neu hinzugefügte Titel
                guard let self, let before, self.isListOwner else { return }
                for card in list where !before.contains(card.cleanSlug) {
                    self.enqueue(card)
                }
            }
    }

    func onSignedIn(_ name: String) {
        guard username != name else { return }
        username = name
        UserDefaults.standard.set(name, forKey: Self.keyUser)
        let list = LetterboxdLists.FeaturedList.name
        status = isListOwner
            ? "Filme, die du auf die Watchlist setzt, kommen jetzt auch in „\(list)“."
            : "Angemeldet. Nur \(LetterboxdLists.FeaturedList.owner) kann „\(list)“ füllen."
    }

    /// Abmelden: gemerkten Namen und die Letterboxd-Daten von WebKit löschen (andere Seiten bleiben).
    func signOut() {
        username = nil
        status = "Abgemeldet."
        UserDefaults.standard.removeObject(forKey: Self.keyUser)
        let store = WKWebsiteDataStore.default()
        let types = WKWebsiteDataStore.allWebsiteDataTypes()
        store.fetchDataRecords(ofTypes: types) { records in
            let letterboxd = records.filter { $0.displayName.localizedCaseInsensitiveContains("letterboxd") }
            store.removeData(ofTypes: types, for: letterboxd) {}
        }
    }

    /// Gleiche Einstellungen für Anmeldung und unsichtbare Seite: Safari-Kennung, dauerhafte Cookies.
    static func configuration() -> WKWebViewConfiguration {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        // Ohne "Version/… Safari/…" hält Letterboxd die Ansicht für einen eingebetteten Browser
        let os = UIDevice.current.systemVersion.split(separator: ".").prefix(2).joined(separator: ".")
        config.applicationNameForUserAgent = "Version/\(os) Mobile/15E148 Safari/604.1"
        return config
    }

    // ------------------- Watchlist → Empfehlungsliste -------------------

    /// Filme nacheinander eintragen (nie zwei Letterboxd-Seiten gleichzeitig).
    private func enqueue(_ card: AnimeCard) {
        let previous = queue
        queue = Task { [weak self] in
            await previous?.value
            await self?.addToFeaturedList(card)
        }
    }

    private func addToFeaturedList(_ card: AnimeCard) async {
        let title: String
        let filmPath: String
        switch await LetterboxdListSync.lookupFilm(card) {
        case .notAMovie:
            return // Letterboxd kennt nur Filme
        case .notFound(let name):
            status = "„\(name)“ wurde auf Letterboxd nicht gefunden."
            return
        case .found(let name, let path):
            title = name
            filmPath = path
        }
        let json = await LetterboxdPageRunner.run(script: LetterboxdListSync.addScript(filmPath: filmPath))
        let outcome = LetterboxdListSync.parseResult(json, fallbackTitle: title)
        status = LetterboxdListSync.message(outcome, title: title)
        switch outcome {
        case .loggedOut:
            username = nil
            UserDefaults.standard.removeObject(forKey: Self.keyUser)
        case .added:
            listVersion += 1
        default:
            break
        }
    }
}

// MARK: - Unsichtbare, angemeldete Letterboxd-Seite

/// Öffnet letterboxd.com unsichtbar, führt dort ein Skript aus und liefert sein Ergebnis als JSON-Text.
@MainActor
private final class LetterboxdPageRunner: NSObject, WKNavigationDelegate, WKScriptMessageHandler {

    private static let handlerName = "abiLetterboxd"

    private let script: String
    private var webView: WKWebView?
    private var continuation: CheckedContinuation<String?, Never>?
    private var timeoutTask: Task<Void, Never>?
    private var starts = 0
    private var sawChallenge = false

    static func run(script: String) async -> String? {
        let runner = LetterboxdPageRunner(script: script)
        return await runner.start()
    }

    private init(script: String) {
        self.script = script
        super.init()
    }

    private func start() async -> String? {
        await withCheckedContinuation { cont in
            continuation = cont
            let config = LetterboxdAccount.configuration()
            config.userContentController.add(self, name: Self.handlerName)
            let web = WKWebView(frame: CGRect(x: 0, y: 0, width: 360, height: 640), configuration: config)
            web.navigationDelegate = self
            web.isHidden = true
            // In ein Fenster hängen, sonst bremst iOS Skripte und Timer der Seite aus
            let windows = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows)
            (windows.first(where: \.isKeyWindow) ?? windows.first)?.insertSubview(web, at: 0)
            webView = web
            web.load(URLRequest(url: URL(string: LetterboxdListSync.homeUrl)!))
            timeoutTask = Task { [weak self] in
                try? await Task.sleep(nanoseconds: 60_000_000_000)
                guard let self, !Task.isCancelled else { return }
                self.finish(self.sawChallenge
                    ? #"{"ok":false,"error":"Letterboxd verlangt eine Sicherheitsprüfung – bitte in den Einstellungen neu anmelden"}"#
                    : nil)
            }
        }
    }

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        guard continuation != nil, webView.url?.host?.contains("letterboxd.com") == true else { return }
        // Zeigt Cloudflare erst eine Prüfung, gibt es noch kein "person"-Objekt: auf die nächste Seite
        // warten. Lädt Letterboxd die Seite ein zweites Mal, wird das Skript dort erneut gestartet.
        webView.evaluateJavaScript("typeof window.person") { [weak self] value, _ in
            guard let self, self.continuation != nil else { return }
            guard (value as? String) == "object" else {
                self.sawChallenge = true
                return
            }
            self.starts += 1
            guard self.starts <= 4 else { return }
            let handler = "window.webkit.messageHandlers.\(Self.handlerName)"
            webView.evaluateJavaScript(
                "(\(self.script)).then(function(r){\(handler).postMessage(JSON.stringify(r));})" +
                ".catch(function(e){\(handler).postMessage(JSON.stringify({ok:false,error:String(e)}));});void 0;"
            )
        }
    }

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        finish(message.body as? String)
    }

    private func finish(_ result: String?) {
        guard let cont = continuation else { return }
        continuation = nil
        timeoutTask?.cancel()
        webView?.stopLoading()
        webView?.configuration.userContentController.removeScriptMessageHandler(forName: Self.handlerName)
        webView?.navigationDelegate = nil
        webView?.removeFromSuperview()
        webView = nil
        cont.resume(returning: result)
    }
}

// MARK: - Anmeldung

/// Echte Letterboxd-Anmeldeseite; schließt sich selbst, sobald man angemeldet ist.
struct LetterboxdSignInView: View {
    let onClose: () -> Void

    var body: some View {
        NavigationView {
            LetterboxdWebView(onSignedIn: { name in
                LetterboxdAccount.shared.onSignedIn(name)
                onClose()
            })
            .ignoresSafeArea(edges: .bottom)
            .navigationTitle("Bei Letterboxd anmelden")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Abbrechen", action: onClose)
                }
            }
        }
        .navigationViewStyle(.stack)
    }
}

private struct LetterboxdWebView: UIViewRepresentable {
    let onSignedIn: (String) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(onSignedIn: onSignedIn) }

    func makeUIView(context: Context) -> WKWebView {
        let web = WKWebView(frame: .zero, configuration: LetterboxdAccount.configuration())
        web.navigationDelegate = context.coordinator
        web.allowsBackForwardNavigationGestures = true
        context.coordinator.attach(web)
        web.load(URLRequest(url: URL(string: LetterboxdListSync.signInUrl)!))
        return web
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    static func dismantleUIView(_ uiView: WKWebView, coordinator: Coordinator) {
        coordinator.stop()
    }

    @MainActor
    final class Coordinator: NSObject, WKNavigationDelegate {
        private let onSignedIn: (String) -> Void
        private weak var webView: WKWebView?
        private var poll: Timer?
        private var done = false

        init(onSignedIn: @escaping (String) -> Void) {
            self.onSignedIn = onSignedIn
        }

        func attach(_ web: WKWebView) {
            webView = web
            // Letterboxd meldet teils ohne neue Seite an – deshalb zusätzlich regelmäßig nachsehen
            poll = Timer.scheduledTimer(withTimeInterval: 2, repeats: true) { [weak self] _ in
                Task { @MainActor in self?.probe() }
            }
        }

        func stop() {
            poll?.invalidate()
            poll = nil
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            probe()
        }

        private func probe() {
            guard !done, let web = webView, web.url?.host?.contains("letterboxd.com") == true else { return }
            web.evaluateJavaScript(LetterboxdListSync.probeExpression) { [weak self] value, _ in
                guard let self, !self.done, let name = LetterboxdListSync.parseProbe(value as? String) else { return }
                self.done = true
                self.stop()
                self.onSignedIn(name)
            }
        }
    }
}
