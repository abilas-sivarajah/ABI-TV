package de.aniworld.tv.platform

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.model.AnimeCard
import de.aniworld.tv.data.remote.LetterboxdListSync
import de.aniworld.tv.data.remote.LetterboxdLists
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Letterboxd-Konto auf Handy und TV (wie am PC): Anmeldung auf der echten Letterboxd-Seite in
 * einer WebView, die App sieht das Passwort nie. Die WebView behält die Anmeldung über Neustarts.
 * Ist der Besitzer der Empfehlungsliste angemeldet, kommt jeder Film, den er auf die Watchlist
 * setzt, auch in "Ilkems Empfehlungen".
 */
object LetterboxdAccountAndroid {

    private const val TAG = "LetterboxdAccount"
    private const val PREFS = "letterboxd_account"
    private const val KEY_USER = "username"

    private lateinit var appContext: Context

    private val _username = MutableStateFlow<String?>(null)
    /** Angemeldeter Letterboxd-Name oder null. */
    val username: StateFlow<String?> = _username.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    /** Kurzer Hinweis für die Einstellungen. */
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _listVersion = MutableStateFlow(0)
    /** Steigt, wenn die App etwas zur Empfehlungsliste hinzugefügt hat – die Reihe lädt dann neu. */
    val listVersion: StateFlow<Int> = _listVersion.asStateFlow()

    val isListOwner: Boolean get() = LetterboxdListSync.isListOwner(_username.value)

    private val prefs get() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Beim App-Start: gemerkte Anmeldung laden und den Watchlist-Abgleich starten. */
    fun start(context: Context, scope: CoroutineScope) {
        appContext = context.applicationContext
        _username.value = prefs.getString(KEY_USER, null)
        startWatchlistSync(scope)
    }

    fun signInIntent(context: Context): Intent = Intent(context, LetterboxdSignInActivity::class.java)

    internal fun onSignedIn(name: String) {
        if (_username.value == name) return
        AppLog.i(TAG, "Bei Letterboxd angemeldet als $name")
        _username.value = name
        prefs.edit().putString(KEY_USER, name).apply()
        CookieManager.getInstance().flush()
        _status.value = if (isListOwner) {
            "Filme, die du auf die Watchlist setzt, kommen jetzt auch in „${LetterboxdLists.FeaturedList.NAME}“."
        } else {
            "Angemeldet. Nur ${LetterboxdLists.FeaturedList.OWNER} kann „${LetterboxdLists.FeaturedList.NAME}“ füllen."
        }
    }

    /** Abmelden: gemerkten Namen und die Letterboxd-Cookies löschen (andere Cookies bleiben). */
    fun signOut() {
        _username.value = null
        _status.value = "Abgemeldet."
        prefs.edit().remove(KEY_USER).apply()
        val manager = CookieManager.getInstance()
        manager.getCookie(LetterboxdListSync.HOME_URL)?.split(';')?.forEach { part ->
            val name = part.substringBefore('=').trim()
            if (name.isNotEmpty()) {
                manager.setCookie(LetterboxdListSync.HOME_URL, "$name=; Max-Age=0; Path=/")
                manager.setCookie(LetterboxdListSync.HOME_URL, "$name=; Max-Age=0; Path=/; Domain=.letterboxd.com")
            }
        }
        manager.flush()
    }

    /**
     * Kennung wie ein normaler Chrome: Ohne "; wv" und "Version/4.0" lehnt Letterboxd die Anmeldung
     * nicht als eingebetteten Browser ab.
     */
    @SuppressLint("SetJavaScriptEnabled")
    internal fun configure(web: WebView) {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = WebSettings.getDefaultUserAgent(web.context)
                .replace("; wv", "")
                .replace(Regex("""Version/\d+(\.\d+)* """), "")
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
    }

    // ------------------- Watchlist → Empfehlungsliste -------------------

    private fun startWatchlistSync(scope: CoroutineScope) {
        scope.launch {
            var previous: Set<String>? = null
            LocalStorage.watchlistFlow.collect { list ->
                val slugs = list.map { it.cleanSlug }.toSet()
                val before = previous
                previous = slugs
                // Beim Start nur den Stand merken; danach zählen nur neu hinzugefügte Titel
                if (before == null || !isListOwner) return@collect
                list.filter { it.cleanSlug !in before }.forEach { card ->
                    scope.launch {
                        runCatching { addToFeaturedList(card) }.onFailure {
                            AppLog.e(TAG, "„${card.title}“ nicht zur Empfehlungsliste hinzugefügt", it)
                            _status.value = "„${card.title}“ nicht hinzugefügt: ${it.message ?: it.javaClass.simpleName}"
                        }
                    }
                }
            }
        }
    }

    private val syncMutex = Mutex()

    private suspend fun addToFeaturedList(card: AnimeCard) = syncMutex.withLock {
        val (title, filmPath) = when (val lookup = LetterboxdListSync.lookupFilm(card)) {
            LetterboxdListSync.Lookup.NotAMovie -> return@withLock // Letterboxd kennt nur Filme
            is LetterboxdListSync.Lookup.NotFound -> {
                _status.value = "„${lookup.title}“ wurde auf Letterboxd nicht gefunden."
                return@withLock
            }
            is LetterboxdListSync.Lookup.Found -> lookup.title to lookup.path
        }
        val outcome = LetterboxdListSync.parseResult(runInLetterboxd(LetterboxdListSync.addScript(filmPath)), title)
        _status.value = LetterboxdListSync.message(outcome, title)
        AppLog.i(TAG, "Empfehlungsliste: ${_status.value}")
        when (outcome) {
            LetterboxdListSync.Outcome.LoggedOut -> {
                _username.value = null
                prefs.edit().remove(KEY_USER).apply()
            }
            is LetterboxdListSync.Outcome.Added -> _listVersion.value++
            else -> Unit
        }
    }

    /** Bridge für das Ergebnis des Skripts (WebView ruft sie aus JavaScript auf). */
    private class ResultBridge(private val result: CompletableDeferred<String?>) {
        @JavascriptInterface
        fun done(json: String?) {
            result.complete(json)
        }
    }

    /** Skript in einer unsichtbaren, angemeldeten Letterboxd-Seite ausführen und sein Ergebnis (JSON) liefern. */
    @SuppressLint("JavascriptInterface")
    private suspend fun runInLetterboxd(script: String): String? = withContext(Dispatchers.Main) {
        val result = CompletableDeferred<String?>()
        val web = WebView(appContext)
        configure(web)
        web.addJavascriptInterface(ResultBridge(result), "AbiLetterboxd")
        var starts = 0
        var sawChallenge = false
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                if (result.isCompleted || url?.contains("letterboxd.com") != true) return
                // Zeigt Cloudflare erst eine Sicherheitsprüfung, gibt es noch kein "person"-Objekt:
                // dann auf die nächste Seite warten (die Prüfung leitet nach Erfolg selbst weiter).
                // Lädt Letterboxd die Seite ein zweites Mal, wird das Skript dort erneut gestartet.
                view.evaluateJavascript("typeof window.person") { type ->
                    if (result.isCompleted) return@evaluateJavascript
                    if (type?.contains("object") != true) {
                        sawChallenge = true
                        return@evaluateJavascript
                    }
                    if (++starts > 4) return@evaluateJavascript
                    view.evaluateJavascript(
                        "($script).then(function(r){AbiLetterboxd.done(JSON.stringify(r));})" +
                            ".catch(function(e){AbiLetterboxd.done(JSON.stringify({ok:false,error:String(e)}));});",
                        null
                    )
                }
            }
        }
        web.loadUrl(LetterboxdListSync.HOME_URL)
        try {
            withTimeoutOrNull(60_000) { result.await() }
                ?: if (sawChallenge) {
                    """{"ok":false,"error":"Letterboxd verlangt eine Sicherheitsprüfung – bitte in den Einstellungen neu anmelden"}"""
                } else null
        } finally {
            web.stopLoading()
            web.destroy()
            CookieManager.getInstance().flush()
        }
    }
}
