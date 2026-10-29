package de.aniworld.pc.platform

import com.google.gson.JsonParser
import de.aniworld.tv.data.remote.HttpClient
import de.aniworld.tv.platform.AppLog
import de.aniworld.tv.platform.BrowserStreamExtractor
import de.aniworld.tv.platform.BrowserStreamHooks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.EnumProgress
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter
import org.cef.CefApp
import org.cef.CefClient
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefCallback
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.network.CefRequest
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import kotlin.coroutines.resume

/**
 * PC-Gegenstück zur Android-WebView: ein unsichtbarer Chromium (JCEF) lädt die Hoster-Seite und
 * meldet die erste .m3u8/.mp4-Adresse. Chromium selbst (~100 MB) wird beim ersten Start nachgeladen.
 */
class ChromiumStreamExtractor(private val installDir: File) : BrowserStreamExtractor {

    sealed interface Status {
        data object NotStarted : Status
        data class Preparing(val text: String) : Status
        data object Ready : Status
        data class Failed(val message: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.NotStarted)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val initMutex = Mutex()
    private var cefApp: CefApp? = null
    private var client: CefClient? = null

    /** Ein Eintrag pro gerade offener Hoster-Seite. */
    private class Session(val targetUrl: String, val referer: String, val onFound: (String, String) -> Unit) {
        @Volatile var lastPageUrl: String = targetUrl
        @Volatile var refererSet = false
        @Volatile var mainLoaded = false
        @Volatile var kickJobStarted = false
        /** Seiten des Hosters (inkl. Weiterleitungen beim Start, z. B. firestream.to -> firestream.site). */
        val siteLabels: MutableSet<String> = ConcurrentHashMap.newKeySet<String>().apply { siteLabel(targetUrl)?.let(::add) }
    }

    private val sessions = ConcurrentHashMap<CefBrowser, Session>()

    /** Lädt und startet Chromium schon beim App-Start, damit der erste Film nicht darauf warten muss. */
    fun prepareInBackground(scope: CoroutineScope) {
        scope.launch { runCatching { ensureClient() } }
    }

    override suspend fun extract(targetUrl: String, referer: String): Pair<String, String>? {
        val cefClient = runCatching { ensureClient() }.getOrElse {
            AppLog.e(TAG, "Chromium ist nicht verfügbar", it)
            return null
        }
        return withTimeoutOrNull(BrowserStreamHooks.TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val done = AtomicBoolean(false)
                var browser: CefBrowser? = null

                fun closeBrowser() {
                    val b = browser ?: return
                    sessions.remove(b)
                    SwingUtilities.invokeLater {
                        runCatching {
                            b.stopLoad()
                            b.setCloseAllowed()
                            b.close(true)
                        }
                    }
                }

                val session = Session(targetUrl, referer) { streamUrl, pageUrl ->
                    if (done.compareAndSet(false, true)) {
                        closeBrowser()
                        if (cont.isActive) cont.resume(streamUrl to pageUrl)
                    }
                }

                if (DEBUG) AppLog.d(TAG, "CefApp-Zustand: ${CefApp.getState()}")
                val created = cefClient.createBrowser(targetUrl, true, false)
                browser = created
                sessions[created] = session
                cont.invokeOnCancellation {
                    if (done.compareAndSet(false, true)) closeBrowser()
                }
                SwingUtilities.invokeLater {
                    runCatching { created.createImmediately() }
                        .onFailure { AppLog.e(TAG, "Browser konnte nicht erzeugt werden", it) }
                }
            }
        }
    }

    fun dispose() {
        runCatching { client?.dispose() }
        runCatching { cefApp?.dispose() }
    }

    private suspend fun ensureClient(): CefClient = initMutex.withLock {
        client?.let { return it }
        withContext(Dispatchers.IO) {
            try {
                _status.value = Status.Preparing("Browser-Komponente wird vorbereitet…")
                val builder = CefAppBuilder().apply {
                    setInstallDir(installDir)
                    setProgressHandler { state, percent -> _status.value = Status.Preparing(progressText(state, percent)) }
                    // Eigenes Profil pro Prozess: Chromium verweigert den Start, wenn ein anderer
                    // Prozess (z. B. eine zweite ABI-TV-Instanz) dasselbe Profil benutzt
                    cefSettings.root_cache_path = profileDir().absolutePath
                    cefSettings.windowless_rendering_enabled = true
                    cefSettings.user_agent = HttpClient.USER_AGENT
                    cefSettings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_DISABLE
                    addJcefArgs("--mute-audio", "--autoplay-policy=no-user-gesture-required")
                    setAppHandler(object : MavenCefAppHandlerAdapter() {})
                }
                val app = builder.build()
                if (CefApp.getState() == CefApp.CefAppState.INITIALIZATION_FAILED) {
                    error("Chromium konnte nicht initialisiert werden")
                }
                val newClient = app.createClient()
                installHandlers(newClient)
                cefApp = app
                client = newClient
                _status.value = Status.Ready
                newClient
            } catch (e: Throwable) {
                _status.value = Status.Failed(e.message ?: e.javaClass.simpleName)
                throw e
            }
        }
    }

    private fun installHandlers(cefClient: CefClient) {
        val router = CefMessageRouter.create(CefMessageRouter.CefMessageRouterConfig("cefQuery", "cefQueryCancel"))
        router.addHandler(object : CefMessageRouterHandlerAdapter() {
            override fun onQuery(
                browser: CefBrowser?,
                frame: CefFrame?,
                queryId: Long,
                request: String?,
                persistent: Boolean,
                callback: CefQueryCallback?
            ): Boolean {
                val session = browser?.let { sessions[it] } ?: return false
                if (DEBUG) AppLog.d(TAG, "JS meldet: ${request?.take(400)}")
                val parts = runCatching { JsonParser.parseString(request.orEmpty()).asJsonArray }.getOrNull()
                val url = parts?.get(0)?.asString?.trim().orEmpty()
                val ref = parts?.takeIf { it.size() > 1 }?.get(1)?.asString?.ifBlank { null }
                if (url.startsWith("http") && !url.contains("slides")) {
                    session.onFound(url, ref ?: session.lastPageUrl)
                }
                callback?.success("")
                return true
            }
        }, true)
        cefClient.addMessageRouter(router)

        val resourceHandler = object : CefResourceRequestHandlerAdapter() {
            override fun onBeforeResourceLoad(browser: CefBrowser?, frame: CefFrame?, request: CefRequest?): Boolean {
                val session = browser?.let { sessions[it] } ?: return false
                val url = request?.url ?: return false
                if (DEBUG) AppLog.d(TAG, "Anfrage ${request.resourceType}: ${url.take(160)}")
                if (request.resourceType == CefRequest.ResourceType.RT_MAIN_FRAME) {
                    session.lastPageUrl = url
                    // Wie auf Android: die Hoster-Seite mit Referer laden
                    if (!session.refererSet && url == session.targetUrl) {
                        session.refererSet = true
                        request.setReferrer(session.referer, CefRequest.ReferrerPolicy.REFERRER_POLICY_DEFAULT)
                    }
                }
                if (BrowserStreamHooks.isMediaStreamUrl(url)) {
                    session.onFound(url, session.lastPageUrl)
                    return true
                }
                return BrowserStreamHooks.isBlockedAdOrAsset(url)
            }
        }

        cefClient.addRequestHandler(object : CefRequestHandlerAdapter() {
            override fun onBeforeBrowse(
                browser: CefBrowser?,
                frame: CefFrame?,
                request: CefRequest?,
                userGesture: Boolean,
                isRedirect: Boolean
            ): Boolean {
                val session = browser?.let { sessions[it] } ?: return false
                if (frame?.isMain != true) return false
                val label = siteLabel(request?.url.orEmpty()) ?: return false
                // Weiterleitungen beim Laden gehören zum Hoster; danach wechselt nur noch Werbung die Seite
                if (!session.mainLoaded || isRedirect) {
                    session.siteLabels += label
                    return false
                }
                val foreign = label !in session.siteLabels
                if (foreign && DEBUG) AppLog.d(TAG, "Werbe-Weiterleitung blockiert: ${request?.url?.take(120)}")
                return foreign
            }

            override fun getResourceRequestHandler(
                browser: CefBrowser?,
                frame: CefFrame?,
                request: CefRequest?,
                isNavigation: Boolean,
                isDownload: Boolean,
                requestInitiator: String?,
                disableDefaultHandling: BoolRef?
            ): CefResourceRequestHandler = resourceHandler

            override fun onCertificateError(
                browser: CefBrowser?,
                certError: CefLoadHandler.ErrorCode?,
                requestUrl: String?,
                callback: CefCallback?
            ): Boolean {
                // Wie auf Android (handler.proceed()): manche Hoster haben abgelaufene Zertifikate
                callback?.Continue()
                return true
            }
        })

        cefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadStart(browser: CefBrowser?, frame: CefFrame?, transitionType: CefRequest.TransitionType?) {
                if (DEBUG) AppLog.d(TAG, "Ladestart (main=${frame?.isMain}): ${frame?.url}")
                injectHook(browser, frame)
            }

            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (DEBUG) AppLog.d(TAG, "Geladen HTTP $httpStatusCode (main=${frame?.isMain}): ${frame?.url}")
                injectHook(browser, frame)
                if (frame?.isMain == true && browser != null) {
                    sessions[browser]?.let { session ->
                        session.mainLoaded = true
                        startPlayKicks(browser, session)
                    }
                }
            }
        })

        // Werbe-Popups gar nicht erst öffnen
        cefClient.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onBeforePopup(browser: CefBrowser?, frame: CefFrame?, targetUrl: String?, targetFrameName: String?): Boolean = true
        })
    }

    /**
     * Viele Player (Plyr, JW Player) laden den Stream erst nach einem Klick auf Play: ein paar Mal
     * stumm "drücken", bis eine .m3u8/.mp4 angefragt wird oder die Sitzung endet.
     */
    private fun startPlayKicks(browser: CefBrowser, session: Session) {
        if (session.kickJobStarted) return
        session.kickJobStarted = true
        Thread({
            repeat(6) {
                Thread.sleep(1_500L)
                if (sessions[browser] !== session) return@Thread
                runCatching { browser.executeJavaScript(PLAY_KICK, session.lastPageUrl, 0) }
                if (DEBUG) runCatching { browser.executeJavaScript(DEBUG_PROBE, session.lastPageUrl, 0) }
            }
        }, "chromium-play-kick").apply { isDaemon = true }.start()
    }

    /** Profilordner dieses Prozesses; Ordner beendeter Prozesse werden dabei aufgeräumt. */
    private fun profileDir(): File {
        val root = File(installDir.parentFile, "jcef-profiles").apply { mkdirs() }
        root.listFiles()?.forEach { dir ->
            val pid = dir.name.toLongOrNull()
            val alive = pid != null && ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
            if (!alive) dir.deleteRecursively()
        }
        return File(root, ProcessHandle.current().pid().toString()).apply { mkdirs() }
    }

    private fun injectHook(browser: CefBrowser?, frame: CefFrame?) {
        val b = browser ?: return
        val session = sessions[b] ?: return
        if (frame != null && !frame.isMain) return
        frame?.url?.takeIf { it.startsWith("http") }?.let { session.lastPageUrl = it }
        b.executeJavaScript(BRIDGE_SHIM + "\n" + BrowserStreamHooks.JS_PLAYER_HOOK, session.lastPageUrl, 0)
    }

    private fun progressText(state: EnumProgress, percent: Float): String {
        val pct = if (percent >= 0f) " ${percent.toInt()} %" else ""
        return when (state) {
            EnumProgress.LOCATING -> "Browser-Komponente wird gesucht…"
            EnumProgress.DOWNLOADING -> "Browser-Komponente wird geladen…$pct"
            EnumProgress.EXTRACTING -> "Browser-Komponente wird entpackt…$pct"
            EnumProgress.INSTALL -> "Browser-Komponente wird installiert…"
            EnumProgress.INITIALIZING -> "Browser-Komponente startet…"
            EnumProgress.INITIALIZED -> "Browser-Komponente bereit"
        }
    }

    private companion object {
        const val TAG = "ChromiumExtractor"

        /** Mit -Dabi.debugBrowser=true jede Anfrage des unsichtbaren Browsers loggen. */
        val DEBUG = System.getProperty("abi.debugBrowser") == "true"

        /** Klickt typische Play-Knöpfe und startet Videos stumm. */
        val PLAY_KICK = """
            (function() {
                try {
                    var selectors = ['.plyr__control--overlaid', '.jw-icon-display', '.jw-display-icon-container',
                        '.vjs-big-play-button', '.play-button', '.btn-play', '#play', 'button[aria-label*="play" i]'];
                    for (var i = 0; i < selectors.length; i++) {
                        var el = document.querySelector(selectors[i]);
                        if (el) { el.click(); break; }
                    }
                    var vids = document.getElementsByTagName('video');
                    for (var j = 0; j < vids.length; j++) {
                        vids[j].muted = true;
                        var p = vids[j].play();
                        if (p && p.catch) p.catch(function() {});
                    }
                } catch (e) {}
            })();
        """.trimIndent()

        /** Nur im Debug-Modus: meldet Player-Zustand als Text. */
        val DEBUG_PROBE = """
            (function() {
                try {
                    var info = { jw: typeof window.jwplayer, videos: document.getElementsByTagName('video').length };
                    if (typeof window.jwplayer === 'function') {
                        var jp = window.jwplayer();
                        info.state = jp.getState ? jp.getState() : null;
                        info.playlist = jp.getPlaylist ? jp.getPlaylist() : null;
                    }
                    var v = document.getElementsByTagName('video')[0];
                    if (v) info.videoSrc = v.currentSrc || v.src;
                    window.cefQuery({ request: 'DEBUG ' + JSON.stringify(info).substring(0, 380), onSuccess: function() {}, onFailure: function() {} });
                } catch (e) {
                    window.cefQuery({ request: 'DEBUG error ' + e, onSuccess: function() {}, onFailure: function() {} });
                }
            })();
        """.trimIndent()

        /** "firestream.site" -> "firestream": gleiche Marke, andere Endung zählt als derselbe Hoster. */
        fun siteLabel(url: String): String? {
            val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase()?.removePrefix("www.") ?: return null
            val parts = host.split('.')
            return if (parts.size >= 2) parts[parts.size - 2] else host
        }

        /** Stellt das Android-Objekt nach, über das der gemeinsame JS-Hook gefundene Streams meldet. */
        val BRIDGE_SHIM = """
            window.${BrowserStreamHooks.BRIDGE_NAME} = window.${BrowserStreamHooks.BRIDGE_NAME} || {
                onStream: function(u, r) {
                    try {
                        window.cefQuery({ request: JSON.stringify([String(u), String(r || '')]), onSuccess: function() {}, onFailure: function() {} });
                    } catch (e) {}
                }
            };
        """.trimIndent()
    }
}
