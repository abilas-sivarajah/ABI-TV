package de.aniworld.pc.platform

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.model.AnimeCard
import de.aniworld.tv.data.remote.LetterboxdListSync
import de.aniworld.tv.platform.AppLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.cef.browser.CefBrowser
import org.cef.callback.CefCookieVisitor
import org.cef.misc.BoolRef
import org.cef.network.CefCookie
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.io.File
import java.util.Date
import java.util.UUID
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

/**
 * Letterboxd-Konto des App-Besitzers.
 *
 * - Anmeldung im eingebauten Chromium auf der echten Letterboxd-Seite; die App sieht das Passwort nie.
 *   Die Sitzungs-Cookies werden danach lokal gespeichert, damit man nach einem Neustart angemeldet bleibt.
 * - Ist der Besitzer der Empfehlungsliste angemeldet, landet jeder Film, den er in der App auf die
 *   Watchlist setzt, auch in dieser Letterboxd-Liste. Alle anderen sehen die Liste nur.
 */
object LetterboxdAccount {

    /** Feste Empfehlungsliste im Watchlist-Reiter (gemeinsam mit Handy und TV). */
    val FeaturedList = de.aniworld.tv.data.remote.LetterboxdLists.FeaturedList

    private const val TAG = "LetterboxdAccount"
    private const val HOME_URL = "https://letterboxd.com/"
    private const val SIGN_IN_URL = "https://letterboxd.com/sign-in/"

    /** Wird in Main.kt gesetzt. */
    lateinit var chromium: ChromiumStreamExtractor

    private val _username = MutableStateFlow<String?>(null)
    /** Angemeldeter Letterboxd-Name oder null. */
    val username: StateFlow<String?> = _username.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    /** Kurzer Hinweis für die Einstellungen (z. B. "„Oppenheimer“ zur Liste hinzugefügt"). */
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _listVersion = MutableStateFlow(0)
    /** Steigt, wenn die App etwas zur Empfehlungsliste hinzugefügt hat – die Reihe lädt dann neu. */
    val listVersion: StateFlow<Int> = _listVersion.asStateFlow()

    /** Darf dieser Nutzer die Empfehlungsliste füllen? */
    val isListOwner: Boolean get() = _username.value?.equals(FeaturedList.OWNER, ignoreCase = true) == true

    @Volatile
    private var window: JFrame? = null

    // ------------------- Gespeicherte Sitzung -------------------

    private data class SavedCookie(
        val name: String, val value: String, val domain: String, val path: String,
        val secure: Boolean, val httponly: Boolean, val expires: Long?
    )

    private data class Session(val username: String, val cookies: List<SavedCookie>)

    private val gson = Gson()
    private val sessionFile by lazy { File(AppDirs.dataDir, "letterboxd-session.json") }

    /** Beim Start: gespeicherte Anmeldung laden und den Watchlist-Abgleich starten. */
    fun start(scope: CoroutineScope) {
        val saved = runCatching { gson.fromJson(sessionFile.readText(Charsets.UTF_8), Session::class.java) }.getOrNull()
        if (saved != null && saved.cookies.isNotEmpty()) {
            _username.value = saved.username
            scope.launch { restoreCookies(saved.cookies) }
        }
        startWatchlistSync(scope)
    }

    private suspend fun restoreCookies(cookies: List<SavedCookie>) {
        val manager = runCatching { chromium.cookieManager() }.getOrElse {
            AppLog.w(TAG, "Chromium nicht verfügbar, Letterboxd-Anmeldung nicht wiederhergestellt: ${it.message}")
            return
        }
        val now = Date()
        cookies.forEach { c ->
            val cookie = CefCookie(
                c.name, c.value, c.domain, c.path, c.secure, c.httponly,
                now, now, c.expires != null, c.expires?.let(::Date) ?: now
            )
            manager.setCookie(HOME_URL, cookie)
        }
        AppLog.i(TAG, "Letterboxd-Anmeldung wiederhergestellt (${cookies.size} Cookies)")
    }

    /** Aktuelle Letterboxd-Cookies aus Chromium lesen und mit dem Namen speichern. */
    private suspend fun saveSession(username: String) {
        val manager = runCatching { chromium.cookieManager() }.getOrNull() ?: return
        val done = CompletableDeferred<List<SavedCookie>>()
        val collected = mutableListOf<SavedCookie>()
        val started = manager.visitUrlCookies(HOME_URL, true, object : CefCookieVisitor {
            override fun visit(cookie: CefCookie, count: Int, total: Int, delete: BoolRef?): Boolean {
                collected += SavedCookie(
                    cookie.name, cookie.value, cookie.domain, cookie.path, cookie.secure, cookie.httponly,
                    cookie.expires?.time?.takeIf { cookie.hasExpires }
                )
                if (count + 1 >= total) done.complete(collected.toList())
                return true
            }
        })
        val cookies = if (started) withTimeoutOrNull(5_000) { done.await() } ?: collected.toList() else emptyList()
        if (cookies.isEmpty()) return
        runCatching {
            sessionFile.parentFile?.mkdirs()
            sessionFile.writeText(gson.toJson(Session(username, cookies)), Charsets.UTF_8)
        }.onFailure { AppLog.w(TAG, "Letterboxd-Sitzung nicht gespeichert: ${it.message}") }
    }

    /** Abmelden: gespeicherte Sitzung und Letterboxd-Cookies löschen. */
    fun signOut(scope: CoroutineScope) {
        _username.value = null
        _status.value = "Abgemeldet."
        runCatching { sessionFile.delete() }
        scope.launch {
            runCatching { chromium.cookieManager().deleteCookies(HOME_URL, "") }
        }
    }

    // ------------------- Anmeldefenster -------------------

    /** Fragt die Seite, ob und als wer man angemeldet ist ("person" ist Letterboxds eigenes Seitenobjekt). */
    private val PROBE = """
        (function() {
            try {
                var p = window.person || {};
                window.cefQuery({ request: JSON.stringify({ probe: true, loggedIn: !!p.loggedIn, username: String(p.username || '') }),
                    onSuccess: function() {}, onFailure: function() {} });
            } catch (e) {}
        })();
    """.trimIndent()

    fun openSignIn(scope: CoroutineScope) {
        window?.let { open ->
            SwingUtilities.invokeLater { open.toFront() }
            return
        }
        _status.value = "Anmeldefenster wird geöffnet…"
        scope.launch {
            val listener = object : ChromiumStreamExtractor.PageListener {
                override fun onPageLoaded(browser: CefBrowser, url: String) {
                    if ("letterboxd.com" in url) browser.executeJavaScript(PROBE, url, 0)
                }

                override fun onMessage(browser: CefBrowser, message: String) {
                    val obj = runCatching { JsonParser.parseString(message).asJsonObject }.getOrNull() ?: return
                    if (obj.get("loggedIn")?.asBoolean != true) return
                    val name = obj.get("username")?.asString.orEmpty()
                    AppLog.i(TAG, "Bei Letterboxd angemeldet als $name")
                    _username.value = name
                    _status.value = if (isListOwner) {
                        "Filme, die du auf die Watchlist setzt, kommen jetzt auch in „${FeaturedList.NAME}“."
                    } else {
                        "Angemeldet. Nur ${FeaturedList.OWNER} kann „${FeaturedList.NAME}“ füllen."
                    }
                    scope.launch { saveSession(name) }
                    // Fenster kurz stehen lassen, damit man die Anmeldung sieht, dann schließen
                    SwingUtilities.invokeLater {
                        window?.title = "Letterboxd – angemeldet als $name"
                        javax.swing.Timer(1500) { closeWindow() }.apply { isRepeats = false; start() }
                    }
                }
            }
            val browser = runCatching { chromium.openVisibleBrowser(SIGN_IN_URL, listener) }.getOrElse {
                AppLog.e(TAG, "Anmeldefenster konnte nicht geöffnet werden", it)
                _status.value = "Browser nicht verfügbar: ${it.message}"
                return@launch
            }
            SwingUtilities.invokeLater { showWindow(browser) }
        }
    }

    private var windowBrowser: CefBrowser? = null

    private fun closeWindow() {
        val frame = window ?: return
        windowBrowser?.let { chromium.closeBrowser(it) }
        windowBrowser = null
        frame.dispose()
        window = null
    }

    private fun showWindow(browser: CefBrowser) {
        val frame = JFrame("Bei Letterboxd anmelden").apply {
            defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
            contentPane.background = Color(0x14, 0x18, 0x1C)
            contentPane.layout = BorderLayout()
            contentPane.add(browser.uiComponent, BorderLayout.CENTER)
            size = Dimension(1000, 780)
            setLocationRelativeTo(null)
            addWindowListener(object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent?) {
                    closeWindow()
                    if (_username.value == null) _status.value = null
                }
            })
        }
        // Tastatur: die Browserfläche muss in Java den Fokus haben, dann leitet JCEF das Tippen weiter
        val surface = browser.uiComponent
        surface.isFocusable = true
        surface.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent?) {
                surface.requestFocusInWindow()
            }
        })
        surface.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent?) = browser.setFocus(true)
            override fun focusLost(e: FocusEvent?) = browser.setFocus(false)
        })
        frame.addWindowFocusListener(object : WindowFocusListener {
            override fun windowGainedFocus(e: WindowEvent?) {
                surface.requestFocusInWindow()
            }

            override fun windowLostFocus(e: WindowEvent?) {}
        })
        window = frame
        windowBrowser = browser
        _status.value = null
        frame.isVisible = true
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
                        // Fehler nie still schlucken: in den Einstellungen anzeigen und protokollieren
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
                AppLog.w(TAG, "„${lookup.title}“: kein Letterboxd-Film gefunden")
                _status.value = "„${lookup.title}“ wurde auf Letterboxd nicht gefunden."
                return@withLock
            }
            is LetterboxdListSync.Lookup.Found -> lookup.title to lookup.path
        }

        val outcome = LetterboxdListSync.parseResult(runInLetterboxd(LetterboxdListSync.addScript(filmPath))?.toString(), title)
        _status.value = LetterboxdListSync.message(outcome, title)
        AppLog.i(TAG, "Empfehlungsliste: ${_status.value}")
        when (outcome) {
            LetterboxdListSync.Outcome.LoggedOut -> {
                _username.value = null
                runCatching { sessionFile.delete() }
            }
            is LetterboxdListSync.Outcome.Added -> _listVersion.value++
            else -> Unit
        }
    }

    /** Führt ein Skript in einem unsichtbaren, angemeldeten Letterboxd-Fenster aus und liefert sein Ergebnis. */
    private suspend fun runInLetterboxd(script: String): JsonObject? {
        val id = UUID.randomUUID().toString()
        val result = CompletableDeferred<JsonObject?>()
        var started = false
        val wrapped = """
            ($script).then(function(r) {
                window.cefQuery({ request: JSON.stringify({ id: '$id', result: r }), onSuccess: function() {}, onFailure: function() {} });
            }).catch(function(e) {
                window.cefQuery({ request: JSON.stringify({ id: '$id', result: { ok: false, error: String(e) } }), onSuccess: function() {}, onFailure: function() {} });
            });
        """.trimIndent()
        val listener = object : ChromiumStreamExtractor.PageListener {
            override fun onPageLoaded(browser: CefBrowser, url: String) {
                if (started || "letterboxd.com" !in url) return
                started = true
                browser.executeJavaScript(wrapped, url, 0)
            }

            override fun onMessage(browser: CefBrowser, message: String) {
                val obj = runCatching { JsonParser.parseString(message).asJsonObject }.getOrNull() ?: return
                if (obj.get("id")?.asString != id) return
                result.complete(obj.get("result")?.takeIf { it.isJsonObject }?.asJsonObject)
            }
        }
        val browser = runCatching { chromium.openHiddenBrowser(HOME_URL, listener) }.getOrElse {
            AppLog.e(TAG, "Unsichtbares Letterboxd-Fenster nicht verfügbar", it)
            return null
        }
        return try {
            withTimeoutOrNull(45_000) { result.await() }
        } finally {
            chromium.closeBrowser(browser)
            _username.value?.let { saveSession(it) } // Letterboxd erneuert Cookies gelegentlich
        }
    }
}
