package de.aniworld.tv.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import de.aniworld.tv.data.model.AnimeCard

/**
 * Gemeinsame Schritte (PC und Android), um einen Film in "Ilkems Empfehlungen" auf Letterboxd
 * einzutragen. Ausgeführt wird das Skript jeweils in einem angemeldeten Browser der App
 * (PC: Chromium, Android: WebView) – Letterboxd lässt Schreibzugriffe nur aus der eigenen Seite zu.
 */
object LetterboxdListSync {

    const val HOME_URL = "https://letterboxd.com/"
    const val SIGN_IN_URL = "https://letterboxd.com/sign-in/"

    private val gson = Gson()

    /** Ergebnis der Suche nach dem passenden Letterboxd-Film. */
    sealed interface Lookup {
        /** Serie oder Anime – Letterboxd kennt fast nur Filme. */
        data object NotAMovie : Lookup
        data class NotFound(val title: String) : Lookup
        /** [path] öffnet den Film auf Letterboxd (/tmdb/… bzw. /imdb/…). */
        data class Found(val title: String, val path: String) : Lookup
    }

    /** Was beim Eintragen herauskam. */
    sealed interface Outcome {
        data class Added(val name: String) : Outcome
        data class AlreadyInList(val name: String) : Outcome
        data object LoggedOut : Outcome
        data class Failed(val message: String) : Outcome
    }

    /** Darf dieser Letterboxd-Nutzer die Empfehlungsliste füllen? */
    fun isListOwner(username: String?): Boolean =
        username?.equals(LetterboxdLists.FeaturedList.OWNER, ignoreCase = true) == true

    /** Passenden Letterboxd-Film zu einem Titel der App finden (über TMDB bzw. IMDb-Nummer). */
    suspend fun lookupFilm(card: AnimeCard): Lookup {
        val detail = TitlePreview.loadDetail(card.cleanSlug)
        val isMovie = card.isFilmpalast || (card.isHdFilme && detail?.seasons.isNullOrEmpty())
        if (!isMovie) return Lookup.NotAMovie

        val title = detail?.title?.takeIf { it.isNotBlank() } ?: card.title
        val year = detail?.years?.let { Regex("""(19|20)\d{2}""").find(it)?.value?.toInt() }
        val artwork = if (TmdbArtwork.isConfigured) {
            TmdbArtwork.find(title, year, TmdbArtwork.Kind.MOVIE)
        } else {
            KeylessArtwork.find(title, year, KeylessArtwork.Kind.MOVIE)
        }?.takeIf { it.kind == TmdbArtwork.Kind.MOVIE }
        val path = artwork?.tmdbId?.let { "/tmdb/$it/" } ?: artwork?.imdbId?.let { "/imdb/$it/" }
        return if (path == null) Lookup.NotFound(title) else Lookup.Found(title, path)
    }

    /** JavaScript-Ausdruck: liefert als JSON-Text, ob und als wer man auf der Seite angemeldet ist. */
    const val PROBE_EXPRESSION =
        "(function(){try{var p=window.person||{};return JSON.stringify({loggedIn:!!p.loggedIn,username:String(p.username||'')});}" +
            "catch(e){return JSON.stringify({loggedIn:false,username:''});}})()"

    /** Aus der Antwort von [PROBE_EXPRESSION] den angemeldeten Namen lesen (null = nicht angemeldet). */
    fun parseProbe(json: String?): String? {
        val obj = parseObject(json) ?: return null
        if (obj.get("loggedIn")?.asBoolean != true) return null
        return obj.get("username")?.asString?.takeIf { it.isNotBlank() }
    }

    /**
     * Skript (async, liefert ein Objekt): Film über [filmPath] öffnen, seine Kurzkennung (boxd.it/…)
     * lesen, prüfen, ob er schon in der Liste steht, und ihn sonst über dieselbe Schnittstelle
     * eintragen, die die Letterboxd-Seite selbst benutzt.
     */
    fun addScript(filmPath: String): String = """
        (async () => {
            const p = window.person || {};
            if (!p.loggedIn) return { ok: false, loggedOut: true };
            const film = await fetch(${gson.toJson(filmPath)}, { credentials: 'include' });
            if (!film.ok) return { ok: false, error: 'Film nicht auf Letterboxd (HTTP ' + film.status + ')' };
            const html = await film.text();
            const lid = (html.match(/https:\/\/boxd\.it\/([A-Za-z0-9]+)/) || [])[1];
            if (!lid) return { ok: false, error: 'keine Letterboxd-Kennung gefunden' };
            const name = ((html.match(/<meta property="og:title" content="([^"]+)"/) || [])[1] || '').replace(/&amp;/g, '&');
            // Steht der Film schon in der Liste? ("position" ist dann gesetzt) – keine doppelten Einträge
            const lists = await fetch('/s/load-lists', {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: new URLSearchParams({ listableLid: lid, __csrf: window.supermodelCSRF })
            }).then(x => x.json()).catch(() => null);
            const target = lists && [...(lists.publicLists || []), ...(lists.privateLists || [])]
                .find(l => l.boxdItCode === ${gson.toJson(LetterboxdLists.FeaturedList.LID)});
            if (target && target.position != null) return { ok: true, added: 0, name };
            const r = await fetch('/api/v0/lists', {
                method: 'PATCH',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json; charset=UTF-8', Accept: 'application/json', 'X-CSRF-TOKEN': window.supermodelCSRF },
                body: JSON.stringify({ lists: [${gson.toJson(LetterboxdLists.FeaturedList.LID)}], listables: [lid] })
            });
            if (r.status !== 200) return { ok: false, error: 'Letterboxd antwortet mit HTTP ' + r.status };
            const body = await r.json();
            const added = (body.items || []).reduce((n, i) => n + (i.additions || 0), 0);
            return { ok: true, added, name };
        })()
    """.trimIndent()

    /** Antwort von [addScript] (als JSON-Text; null = keine Antwort) auswerten. */
    fun parseResult(json: String?, fallbackTitle: String): Outcome {
        val obj = parseObject(json) ?: return Outcome.Failed("Letterboxd nicht erreichbar")
        if (obj.get("loggedOut")?.asBoolean == true) return Outcome.LoggedOut
        if (obj.get("ok")?.asBoolean != true) {
            return Outcome.Failed(obj.get("error")?.takeIf { it.isJsonPrimitive }?.asString ?: "unbekannter Fehler")
        }
        val name = obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() } ?: fallbackTitle
        return if ((obj.get("added")?.asInt ?: 0) > 0) Outcome.Added(name) else Outcome.AlreadyInList(name)
    }

    /** Kurzer Hinweis für die Einstellungen. */
    fun message(outcome: Outcome, title: String): String = when (outcome) {
        is Outcome.Added -> "„${outcome.name}“ zu „${LetterboxdLists.FeaturedList.NAME}“ hinzugefügt."
        is Outcome.AlreadyInList -> "„${outcome.name}“ ist schon in „${LetterboxdLists.FeaturedList.NAME}“."
        Outcome.LoggedOut -> "Bei Letterboxd abgemeldet – bitte neu anmelden."
        is Outcome.Failed -> "„$title“ nicht hinzugefügt: ${outcome.message}"
    }

    private fun parseObject(json: String?): JsonObject? = runCatching {
        var element = JsonParser.parseString(json ?: return null)
        // Android liefert das Ergebnis von evaluateJavascript als JSON-kodierten Text
        if (element.isJsonPrimitive && element.asJsonPrimitive.isString) element = JsonParser.parseString(element.asString)
        element.takeIf { it.isJsonObject }?.asJsonObject
    }.getOrNull()
}
