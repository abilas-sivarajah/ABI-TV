import Foundation

/// Gemeinsame Schritte (wie auf PC und TV), um einen Film in "Ilkems Empfehlungen" auf Letterboxd
/// einzutragen. Ausgeführt wird das Skript in einer angemeldeten `WKWebView` der App –
/// Letterboxd lässt Schreibzugriffe nur aus der eigenen Seite zu.
public enum LetterboxdListSync {

    public static let homeUrl = "https://letterboxd.com/"
    public static let signInUrl = "https://letterboxd.com/sign-in/"

    /// Ergebnis der Suche nach dem passenden Letterboxd-Film.
    public enum Lookup: Equatable, Sendable {
        /// Serie oder Anime – Letterboxd kennt fast nur Filme.
        case notAMovie
        case notFound(title: String)
        /// `path` öffnet den Film auf Letterboxd (/imdb/…).
        case found(title: String, path: String)
    }

    /// Was beim Eintragen herauskam.
    public enum Outcome: Equatable, Sendable {
        case added(name: String)
        case alreadyInList(name: String)
        case loggedOut
        case failed(message: String)
    }

    /// Darf dieser Letterboxd-Nutzer die Empfehlungsliste füllen?
    public static func isListOwner(_ username: String?) -> Bool {
        username?.caseInsensitiveCompare(LetterboxdLists.FeaturedList.owner) == .orderedSame
    }

    /// Passenden Letterboxd-Film zu einem Titel der App finden (über die IMDb-Nummer).
    public static func lookupFilm(_ card: AnimeCard) async -> Lookup {
        let detail = await TitlePreview.loadDetail(card)
        let isMovie = card.isFilmpalast || (card.isHdFilme && (detail?.seasons.isEmpty ?? true))
        guard isMovie else { return .notAMovie }

        let title = (detail?.title).flatMap { $0.isEmpty ? nil : $0 } ?? card.title
        let year = detail.flatMap { HtmlParser.firstMatch(in: $0.years, pattern: #"((?:19|20)\d{2})"#) }.flatMap(Int.init)
        let artwork = await KeylessArtwork.find(title: title, year: year, kind: .movie)
        guard let imdb = artwork?.imdbId, artwork?.isMovie == true else { return .notFound(title: title) }
        return .found(title: title, path: "/imdb/\(imdb)/")
    }

    /// JavaScript-Ausdruck: liefert als JSON-Text, ob und als wer man auf der Seite angemeldet ist.
    public static let probeExpression =
        "(function(){try{var p=window.person||{};return JSON.stringify({loggedIn:!!p.loggedIn,username:String(p.username||'')});}" +
        "catch(e){return JSON.stringify({loggedIn:false,username:''});}})()"

    /// Aus der Antwort von `probeExpression` den angemeldeten Namen lesen (nil = nicht angemeldet).
    public static func parseProbe(_ json: String?) -> String? {
        guard let obj = parseObject(json), obj["loggedIn"] as? Bool == true,
              let name = obj["username"] as? String, !name.isEmpty else { return nil }
        return name
    }

    /// Skript (Promise mit Objekt): Film über `filmPath` öffnen, seine Kurzkennung (boxd.it/…)
    /// lesen, prüfen, ob er schon in der Liste steht, und ihn sonst über dieselbe Schnittstelle
    /// eintragen, die die Letterboxd-Seite selbst benutzt.
    public static func addScript(filmPath: String) -> String {
        let path = jsString(filmPath)
        let lid = jsString(LetterboxdLists.FeaturedList.lid)
        return """
        (async () => {
            const p = window.person || {};
            if (!p.loggedIn) return { ok: false, loggedOut: true };
            const film = await fetch(\(path), { credentials: 'include' });
            if (!film.ok) return { ok: false, error: 'Film nicht auf Letterboxd (HTTP ' + film.status + ')' };
            const html = await film.text();
            const lid = (html.match(/https:\\/\\/boxd\\.it\\/([A-Za-z0-9]+)/) || [])[1];
            if (!lid) return { ok: false, error: 'keine Letterboxd-Kennung gefunden' };
            const name = ((html.match(/<meta property="og:title" content="([^"]+)"/) || [])[1] || '').replace(/&amp;/g, '&');
            const lists = await fetch('/s/load-lists', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: new URLSearchParams({ listableLid: lid, __csrf: window.supermodelCSRF })
            }).then(x => x.json()).catch(() => null);
            const target = lists && [...(lists.publicLists || []), ...(lists.privateLists || [])]
                .find(l => l.boxdItCode === \(lid));
            if (target && target.position != null) return { ok: true, added: 0, name };
            const r = await fetch('/api/v0/lists', {
                method: 'PATCH',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json; charset=UTF-8', Accept: 'application/json', 'X-CSRF-TOKEN': window.supermodelCSRF },
                body: JSON.stringify({ lists: [\(lid)], listables: [lid] })
            });
            if (r.status !== 200) return { ok: false, error: 'Letterboxd antwortet mit HTTP ' + r.status };
            const body = await r.json();
            const added = (body.items || []).reduce((n, i) => n + (i.additions || 0), 0);
            return { ok: true, added, name };
        })()
        """
    }

    /// Antwort von `addScript` (als JSON-Text; nil = keine Antwort) auswerten.
    public static func parseResult(_ json: String?, fallbackTitle: String) -> Outcome {
        guard let obj = parseObject(json) else { return .failed(message: "Letterboxd nicht erreichbar") }
        if obj["loggedOut"] as? Bool == true { return .loggedOut }
        guard obj["ok"] as? Bool == true else {
            return .failed(message: (obj["error"] as? String) ?? "unbekannter Fehler")
        }
        let name = (obj["name"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? fallbackTitle
        let added = (obj["added"] as? NSNumber)?.intValue ?? 0
        return added > 0 ? .added(name: name) : .alreadyInList(name: name)
    }

    /// Kurzer Hinweis für die Einstellungen.
    public static func message(_ outcome: Outcome, title: String) -> String {
        let list = LetterboxdLists.FeaturedList.name
        switch outcome {
        case .added(let name): return "„\(name)“ zu „\(list)“ hinzugefügt."
        case .alreadyInList(let name): return "„\(name)“ ist schon in „\(list)“."
        case .loggedOut: return "Bei Letterboxd abgemeldet – bitte neu anmelden."
        case .failed(let message): return "„\(title)“ nicht hinzugefügt: \(message)"
        }
    }

    static func jsString(_ value: String) -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = .withoutEscapingSlashes
        guard let data = try? encoder.encode(value), let text = String(data: data, encoding: .utf8) else { return "''" }
        return text
    }

    private static func parseObject(_ json: String?) -> [String: Any]? {
        guard let data = json?.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed]) else { return nil }
        if let text = obj as? String { return parseObject(text) }
        return obj as? [String: Any]
    }
}
