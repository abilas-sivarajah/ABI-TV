package de.aniworld.tv.platform

/**
 * Schnittstellen zur jeweiligen Plattform (Android oder PC), damit Scraper, Speicher
 * und Stream-Auflösung als reiner JVM-Code von TV-, Handy- und PC-App geteilt werden.
 */

/** Logging: Android leitet an android.util.Log weiter, der PC schreibt auf die Konsole. */
object AppLog {
    enum class Level { DEBUG, INFO, WARN, ERROR }

    @Volatile
    var sink: (level: Level, tag: String, message: String, error: Throwable?) -> Unit = { level, tag, message, error ->
        System.err.println("${level.name.first()}/$tag: $message")
        error?.printStackTrace()
    }

    fun d(tag: String, message: String) = sink(Level.DEBUG, tag, message, null)
    fun i(tag: String, message: String) = sink(Level.INFO, tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) = sink(Level.WARN, tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = sink(Level.ERROR, tag, message, error)
}

/** Einfacher Schlüssel-Wert-Speicher (Android: SharedPreferences, PC: JSON-Datei). */
interface KeyValueStore {
    fun getString(key: String, default: String?): String?
    fun putString(key: String, value: String)
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
    fun getLong(key: String, default: Long): Long
    fun putLong(key: String, value: Long)
    fun getStringSet(key: String, default: Set<String>): Set<String>
    fun putStringSet(key: String, value: Set<String>)
}

/**
 * Unsichtbarer Browser (Android: WebView, PC: Chromium), der eine Hoster-Seite lädt und die
 * erste .m3u8/.mp4-Adresse abgreift. Liefert (Stream-Adresse, Seiten-Adresse) oder null.
 */
fun interface BrowserStreamExtractor {
    suspend fun extract(targetUrl: String, referer: String): Pair<String, String>?
}

/** Gemeinsame Regeln für beide Browser-Extraktoren. */
object BrowserStreamHooks {

    const val TIMEOUT_MS = 16_000L

    /** Name des JS-Objekts, über das der Hook gefundene Streams meldet. */
    const val BRIDGE_NAME = "AndroidBridge"

    val JS_PLAYER_HOOK = """
        (function() {
            if (window.__awHooked) return;
            window.__awHooked = true;
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
                                        if (cfg.sources[i].file) AndroidBridge.onStream(cfg.sources[i].file, location.href);
                                    }
                                }
                                if (cfg && cfg.file) AndroidBridge.onStream(cfg.file, location.href);
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
            setInterval(function() {
                try {
                    var vids = document.getElementsByTagName('video');
                    for (var i = 0; i < vids.length; i++) {
                        if (vids[i].src && vids[i].src.indexOf('http') === 0) {
                            AndroidBridge.onStream(vids[i].src, location.href);
                        }
                    }
                } catch (e) {}
                // JW Player, der erst nach diesem Hook geladen wurde: Quelle direkt aus der Playlist lesen
                try {
                    if (typeof window.jwplayer === 'function') {
                        var jp = window.jwplayer();
                        var pl = jp && jp.getPlaylist ? jp.getPlaylist() : null;
                        if (pl && pl.length) {
                            var item = pl[0];
                            if (item.file && String(item.file).indexOf('http') === 0) AndroidBridge.onStream(item.file, location.href);
                            if (item.sources) {
                                for (var k = 0; k < item.sources.length; k++) {
                                    var f = item.sources[k].file;
                                    if (f && String(f).indexOf('http') === 0) AndroidBridge.onStream(f, location.href);
                                }
                            }
                        }
                    }
                } catch (e) {}
            }, 400);
        })();
    """.trimIndent()

    fun isMediaStreamUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.contains("slides") || lower.contains("preview") || lower.contains("thumb")) return false
        return lower.contains(".m3u8") ||
            lower.contains("/hls2/") ||
            (lower.contains(".mp4") && !lower.contains("ad"))
    }

    fun isBlockedAdOrAsset(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") ||
            lower.endsWith(".png") ||
            lower.endsWith(".gif") ||
            lower.endsWith(".webp") ||
            lower.endsWith(".woff2") ||
            lower.contains("doubleclick") ||
            lower.contains("googlesyndication") ||
            lower.contains("popads") ||
            lower.contains("adskeeper")
    }
}
