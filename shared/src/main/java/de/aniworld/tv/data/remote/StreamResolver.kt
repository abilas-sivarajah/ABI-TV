package de.aniworld.tv.data.remote

import com.google.gson.JsonParser
import de.aniworld.tv.data.model.HosterLink
import de.aniworld.tv.data.model.ResolvedStream
import de.aniworld.tv.data.model.SerienStreamRef
import de.aniworld.tv.platform.AppLog
import de.aniworld.tv.platform.BrowserStreamExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import okio.ByteString.Companion.decodeBase64
import org.jsoup.Jsoup
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Zweistufiger Stream-Resolver:
 * 1. Schneller nativer OkHttp-Resolver für VOE (inkl. ROT13/Token/Base64-Entschlüsselung)
 *    und Vidmoly (direktes HLS-Parsing). Dauert < 1 Sekunde und benötigt 0 MB WebView-RAM.
 * 2. Automatischer Fallback über einen unsichtbaren Browser der Plattform ([browserExtractor]:
 *    WebView auf Android, Chromium am PC) für komplexe Hoster (Filemoon, Doodstream oder
 *    geänderte VOE-Skripte), der sofort nach dem Abgreifen der .m3u8/.mp4 URL geschlossen wird.
 */
object StreamResolver {

    private const val CACHE_TTL_MS = 90 * 60 * 1000L // 90 Minuten

    /** Wird von der Plattform beim Start gesetzt; ohne Browser entfällt Stufe 2. */
    @Volatile
    var browserExtractor: BrowserStreamExtractor? = null

    private val streamCache = ConcurrentHashMap<String, Pair<Long, ResolvedStream>>()

    // Hoster-Seite, auf der Stufe 1 nach allen Weiterleitungen gelandet ist (Schlüssel: redirectPath)
    private val hosterPages = ConcurrentHashMap<String, String>()

    private val VOE_JUNK_TOKENS = listOf("*~", "%?", "@$", "^^", "#&", "!!", "~@")

    // Nur der <title> wird geprüft: Doodstream hat z.B. ein verstecktes "Not Found"-Template im Body
    // VOE liefert je nach Accept-Language "404 - Not found" oder "404 - Nicht gefunden"
    private val DEAD_LINK_TITLE_MARKERS = listOf(
        "not found", "nicht gefunden", "file was deleted", "file is no longer available", "video not found"
    )

    private class DeadLinkException(message: String) : IllegalStateException(message)

    private fun isDeadLinkPage(html: String): Boolean {
        val title = Regex("""<title>([^<]*)</title>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)?.lowercase().orEmpty()
        return DEAD_LINK_TITLE_MARKERS.any { title.contains(it) }
    }

    fun invalidateCache(redirectPath: String) {
        streamCache.remove(redirectPath)
    }

    suspend fun resolveHoster(hoster: HosterLink): ResolvedStream {
        val now = System.currentTimeMillis()
        streamCache[hoster.redirectPath]?.let { (ts, cached) ->
            if (now - ts < CACHE_TTL_MS) return cached
        }

        // Stufe 1: Schnell-Auflösung über OkHttp
        val fastAttempt = withContext(Dispatchers.IO) {
            runCatching { resolveDirect(hoster) }
                .onFailure { AppLog.e("StreamResolver", "resolveDirect failed for ${hoster.redirectPath}", it) }
        }
        val hosterPage = hosterPages.remove(hoster.redirectPath)
        // Bei gelöschten Dateien bringt auch die WebView nichts
        fastAttempt.exceptionOrNull()?.let { if (it is DeadLinkException) throw it }
        val fastResult = fastAttempt.getOrNull()
        if (fastResult != null) {
            AppLog.e("StreamResolver", "Fast resolved: ${fastResult.streamUrl} headers=${fastResult.headers}")
            streamCache[hoster.redirectPath] = now to fastResult
            return fastResult
        }

        // Stufe 2: Unsichtbarer Browser als Fallback
        val redirectUrl = if (hoster.redirectPath.startsWith("http")) {
            hoster.redirectPath
        } else {
            "${HttpClient.BASE_URL}${hoster.redirectPath}"
        }
        // Der Browser nutzt den DNS des Netzes, in dem S.to per CUII gesperrt ist – deshalb die schon
        // über OkHttp (DNS-over-HTTPS) gefundene Hoster-Seite öffnen statt der S.to-Weiterleitung
        val browserUrl = hosterPage ?: redirectUrl

        val webViewPair = browserExtractor?.extract(browserUrl, "${HttpClient.BASE_URL}/")
            ?: throw IllegalStateException("Stream konnte bei ${hoster.hosterName} nicht extrahiert werden.")

        val (streamUrl, pageUrl) = webViewPair
        val origin = runCatching {
            val u = URI(pageUrl)
            "${u.scheme}://${u.host}/"
        }.getOrDefault("${HttpClient.BASE_URL}/")

        val resolved = ResolvedStream(
            streamUrl = streamUrl,
            isHls = streamUrl.contains(".m3u8", ignoreCase = true) || streamUrl.contains("/hls", ignoreCase = true),
            hosterName = hoster.hosterName,
            language = hoster.language,
            headers = mapOf(
                "User-Agent" to HttpClient.USER_AGENT,
                "Referer" to origin
            )
        )
        streamCache[hoster.redirectPath] = now to resolved
        return resolved
    }

    /**
     * Versucht unter Berücksichtigung aller JS-Weiterleitungen die .m3u8/.mp4 URL
     * direkt aus dem HTML-Quelltext zu berechnen.
     */
    private suspend fun resolveDirect(hoster: HosterLink): ResolvedStream? {
        // 0a. Direkter Dropload / dr0pstream Kurzpfad (vermeidet unnötigen /e/ Turnstile-Request)
        if (hoster.redirectPath.contains("dr0pstream", ignoreCase = true) ||
            hoster.redirectPath.contains("dropload", ignoreCase = true)
        ) {
            val directCode = Regex("""(?:/e/|embed-|/d/)([a-zA-Z0-9]{10,16})""")
                .find(hoster.redirectPath)?.groupValues?.getOrNull(1)
            if (!directCode.isNullOrBlank()) {
                val droploadStream = runCatching {
                    resolveDroploadDirect(directCode, "https://dr0pstream.com")
                }.getOrNull()
                if (droploadStream != null) {
                    return ResolvedStream(
                        streamUrl = droploadStream,
                        isHls = droploadStream.contains(".m3u8", ignoreCase = true),
                        hosterName = hoster.hosterName,
                        language = hoster.language,
                        headers = mapOf(
                            "User-Agent" to HttpClient.USER_AGENT,
                            "Referer" to "https://dr0pstream.com/"
                        )
                    )
                }
            }
        }

        val initialReferer = if (SerienStreamRef.isStoUrl(hoster.redirectPath)) {
            "${SerienStreamScraper.BASE_URL}/"
        } else {
            "${HttpClient.BASE_URL}/"
        }
        var (currentUrl, html) = HttpClient.getHtml(hoster.redirectPath, initialReferer)

        // Bis zu 3 JS-Weiterleitungen (window.location.href = '...') folgen
        repeat(3) {
            val jsRedirect = Regex("""window\.location\.href\s*=\s*['"](https?://[^'"]+)['"]""")
                .find(html)?.groupValues?.getOrNull(1)
            if (!jsRedirect.isNullOrBlank() && jsRedirect != currentUrl) {
                val next = HttpClient.getHtml(jsRedirect, currentUrl)
                currentUrl = next.first
                html = next.second
            }
        }
        hosterPages[hoster.redirectPath] = currentUrl

        // Gelöschte Dateien (z.B. VOE "404 - Not found") sofort melden, statt 16 s im WebView zu warten
        if (isDeadLinkPage(html)) {
            throw DeadLinkException("${hoster.hosterName}: Datei nicht mehr verfügbar ($currentUrl)")
        }

        val originReferer = runCatching {
            val u = URI(currentUrl)
            "${u.scheme}://${u.host}/"
        }.getOrDefault(currentUrl)

        // 0b. Dropload / dr0pstream nach Redirect (z.B. über aniworld.to/redirect/...)
        if (currentUrl.contains("dr0pstream", ignoreCase = true) ||
            currentUrl.contains("dropload", ignoreCase = true) ||
            hoster.hosterName.equals("Dropload", ignoreCase = true)
        ) {
            val fileCode = Regex("""(?:/e/|embed-|/d/)([a-zA-Z0-9]{10,16})""")
                .find(currentUrl)?.groupValues?.getOrNull(1)
                ?: Regex("""name=["']file_code["']\s+value=["']([a-zA-Z0-9]+)["']""")
                    .find(html)?.groupValues?.getOrNull(1)

            if (!fileCode.isNullOrBlank()) {
                val droploadStream = runCatching {
                    resolveDroploadDirect(fileCode, "https://dr0pstream.com")
                }.getOrNull()
                if (droploadStream != null) {
                    return ResolvedStream(
                        streamUrl = droploadStream,
                        isHls = droploadStream.contains(".m3u8", ignoreCase = true),
                        hosterName = hoster.hosterName,
                        language = hoster.language,
                        headers = mapOf(
                            "User-Agent" to HttpClient.USER_AGENT,
                            "Referer" to "https://dr0pstream.com/"
                        )
                    )
                }
            }
        }

        // 1. VOE JSON-Payload-Entschlüsselung (<script type="application/json">["..."]</script>)
        val jsonScriptMatch = Regex(
            """<script\s+type=["']application/json["']>\s*(\[\s*".*?"\s*\])\s*</script>""",
            RegexOption.DOT_MATCHES_ALL
        ).find(html)

        if (jsonScriptMatch != null) {
            val decodedUrl = runCatching { decodeVoePayload(jsonScriptMatch.groupValues[1]) }.getOrNull()
            if (!decodedUrl.isNullOrBlank()) {
                return ResolvedStream(
                    streamUrl = decodedUrl,
                    isHls = decodedUrl.contains(".m3u8", ignoreCase = true),
                    hosterName = hoster.hosterName,
                    language = hoster.language,
                    headers = mapOf(
                        "User-Agent" to HttpClient.USER_AGENT,
                        "Referer" to originReferer
                    )
                )
            }
        }

        // 2. Klassische VOE Base64-Variante ('hls': 'aHR0c...')
        val b64HlsMatch = Regex("""['"]hls['"]\s*:\s*['"]([A-Za-z0-9+/=]{20,})['"]""").find(html)
        if (b64HlsMatch != null) {
            val raw = b64HlsMatch.groupValues[1]
            val decoded = if (raw.startsWith("http")) {
                raw
            } else {
                runCatching { decodeBase64Text(raw) }.getOrNull()
            }
            if (!decoded.isNullOrBlank() && decoded.startsWith("http")) {
                return ResolvedStream(
                    streamUrl = decoded,
                    isHls = true,
                    hosterName = hoster.hosterName,
                    language = hoster.language,
                    headers = mapOf(
                        "User-Agent" to HttpClient.USER_AGENT,
                        "Referer" to originReferer
                    )
                )
            }
        }

        // 3. Direkte .m3u8 URL im Quellcode (z.B. Vidmoly file: 'https://.../master.m3u8...')
        val directM3u8 = Regex("""['"](https?://[^'"\s]+\.m3u8[^'"\s]*)['"]""")
            .findAll(html)
            .map { it.groupValues[1] }
            .firstOrNull { !it.contains("slides") }

        if (!directM3u8.isNullOrBlank()) {
            return ResolvedStream(
                streamUrl = directM3u8,
                isHls = true,
                hosterName = hoster.hosterName,
                language = hoster.language,
                headers = mapOf(
                    "User-Agent" to HttpClient.USER_AGENT,
                    "Referer" to originReferer
                )
            )
        }

        // 4. Doodstream / Playmogo (/pass_md5/...)
        val doodPassMatch = Regex("""(/pass_md5/[^'"\s]+)""").find(html)
        if (doodPassMatch != null) {
            val passPath = doodPassMatch.groupValues[1]
            val origin = originReferer.trimEnd('/')
            val token = passPath.substringAfterLast("/")
            val (_, baseStream) = HttpClient.getHtml("$origin$passPath", currentUrl)
            if (baseStream.startsWith("http")) {
                val finalMp4 = "${baseStream.trim()}1234567890?token=$token&expiry=${System.currentTimeMillis()}"
                return ResolvedStream(
                    streamUrl = finalMp4,
                    isHls = false,
                    hosterName = hoster.hosterName,
                    language = hoster.language,
                    headers = mapOf(
                        "User-Agent" to HttpClient.USER_AGENT,
                        "Referer" to originReferer
                    )
                )
            }
        }

        // 5. Gepacktes JavaScript (Mixdrop / Filemoon / Dropload: eval(function(p,a,c,k,e,d)...))
        val packedMatch = Regex(
            """eval\(function\(p,a,c,k,e,d\)\{.*?\}\('(.*?)',(\d+),(\d+),'(.*?)'\.split\('\|'\)""",
            RegexOption.DOT_MATCHES_ALL
        ).find(html)
        if (packedMatch != null) {
            val unpacked = runCatching {
                unpackJs(
                    p = packedMatch.groupValues[1],
                    radix = packedMatch.groupValues[2].toInt(),
                    count = packedMatch.groupValues[3].toInt(),
                    keywords = packedMatch.groupValues[4].split("|")
                )
            }.getOrNull().orEmpty()

            // Mixdrop MDCore.wurl
            val wurlMatch = Regex("""MDCore\.wurl\s*=\s*["']([^"']+)["']""").find(unpacked)
            if (wurlMatch != null) {
                var wurl = wurlMatch.groupValues[1].trim()
                if (wurl.startsWith("//")) wurl = "https:$wurl"
                if (wurl.startsWith("http")) {
                    return ResolvedStream(
                        streamUrl = wurl,
                        isHls = wurl.contains(".m3u8", ignoreCase = true),
                        hosterName = hoster.hosterName,
                        language = hoster.language,
                        headers = mapOf(
                            "User-Agent" to HttpClient.USER_AGENT,
                            "Referer" to originReferer
                        )
                    )
                }
            }

            // Filemoon / Dropload unpacked .m3u8
            val unpackedM3u8 = Regex("""['"](https?://[^'"\s]+\.m3u8[^'"\s]*)['"]""")
                .find(unpacked)?.groupValues?.getOrNull(1)
            if (!unpackedM3u8.isNullOrBlank()) {
                return ResolvedStream(
                    streamUrl = unpackedM3u8,
                    isHls = true,
                    hosterName = hoster.hosterName,
                    language = hoster.language,
                    headers = mapOf(
                        "User-Agent" to HttpClient.USER_AGENT,
                        "Referer" to originReferer
                    )
                )
            }
        }

        // 6. Direkte .mp4 URL im Quellcode (z.B. SpeedFiles / Vidoza)
        val directMp4 = Regex("""['"](https?://[^'"\s]+\.mp4[^'"\s]*)['"]""")
            .findAll(html)
            .map { it.groupValues[1] }
            .firstOrNull { !it.contains("sample") && !it.contains("preview") }

        if (!directMp4.isNullOrBlank()) {
            return ResolvedStream(
                streamUrl = directMp4,
                isHls = false,
                hosterName = hoster.hosterName,
                language = hoster.language,
                headers = mapOf(
                    "User-Agent" to HttpClient.USER_AGENT,
                    "Referer" to originReferer
                )
            )
        }

        return null
    }

    /**
     * Umgeht das Cloudflare-Turnstile-Captcha von Dropload (/e/<code>), indem stattdessen
     * der captcha-freie Download-Endpunkt (/d/<code> -> /d/<code>_n -> POST download_orig)
     * genutzt wird, der direkt den signierten dropcdn.io MP4-Stream liefert.
     */
    private suspend fun resolveDroploadDirect(fileCode: String, originHost: String): String? {
        val baseHost = if (originHost.contains("dr0pstream", ignoreCase = true)) {
            originHost
        } else {
            "https://dr0pstream.com"
        }

        val dUrl = "$baseHost/d/$fileCode"
        val (resolvedBaseUrl, dHtml) = HttpClient.getHtml(dUrl, "$baseHost/")
        val actualHost = runCatching {
            val u = URI(resolvedBaseUrl)
            "${u.scheme}://${u.host}"
        }.getOrDefault(baseHost)

        val dDoc = Jsoup.parse(dHtml, actualHost)
        val stepPath = dDoc.select("a[href^=\"/d/${fileCode}_\"]")
            .firstOrNull()?.attr("href")
            ?: "/d/${fileCode}_n"

        val stepUrl = "$actualHost$stepPath"
        val (_, stepHtml) = HttpClient.getHtml(stepUrl, dUrl)
        val stepDoc = Jsoup.parse(stepHtml, actualHost)
        val form = stepDoc.selectFirst("form#F1") ?: return null

        val formBuilder = FormBody.Builder()
        for (inp in form.select("input[name]")) {
            formBuilder.add(inp.attr("name"), inp.attr("value"))
        }

        // Dropload prüft serverseitig, ob mindestens ~1 Sekunde zwischen Formular-Abruf und POST vergangen ist
        delay(1250L)

        val postReq = Request.Builder()
            .url(stepUrl)
            .post(formBuilder.build())
            .header("Referer", stepUrl)
            .header("Origin", actualHost)
            .build()

        val postHtml = HttpClient.okHttp.newCall(postReq).execute().use { it.body?.string().orEmpty() }
        val postDoc = Jsoup.parse(postHtml, actualHost)

        val found = postDoc.select("a[href*=\".mp4\"], a[href*=\"dropcdn\"]")
            .map { it.attr("href").trim() }
            .firstOrNull { it.startsWith("http") }
        AppLog.e("StreamResolver", "Dropload resolved fileCode=$fileCode -> $found")
        return found
    }

    private fun unpackJs(p: String, radix: Int, count: Int, keywords: List<String>): String {
        val chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        fun baseN(num: Int, b: Int): String {
            if (num == 0) return "0"
            var n = num
            val sb = StringBuilder()
            while (n > 0) {
                sb.insert(0, chars[n % b])
                n /= b
            }
            return sb.toString()
        }
        var result = p
        for (i in (count - 1) downTo 0) {
            val replacement = keywords.getOrNull(i)
            if (!replacement.isNullOrEmpty()) {
                val token = baseN(i, radix)
                result = Regex("\\b${Regex.escape(token)}\\b").replace(result, replacement)
            }
        }
        return result
    }

    /**
     * Entschlüsselt den verschleierten VOE-Container:
     * 1. ROT13 auf Buchstaben
     * 2. Junk-Symbole (*~, %?, @$, ^^, #&, !!, ~@) entfernen
     * 3. Base64-Decode
     * 4. ASCII-Shift um -3 Zeichen
     * 5. String umkehren (reverse)
     * 6. Base64-Decode -> JSON mit Feld "source"
     */
    private fun decodeVoePayload(jsonArrayStr: String): String? {
        val arr = JsonParser.parseString(jsonArrayStr).asJsonArray
        if (arr.isEmpty) return null
        var step = rot13(arr[0].asString)
        for (token in VOE_JUNK_TOKENS) {
            step = step.replace(token, "")
        }
        val b64Step1 = decodeBase64Text(step)
        val shifted = buildString(b64Step1.length) {
            for (ch in b64Step1) {
                append((ch.code - 3).toChar())
            }
        }
        val reversed = shifted.reversed()
        val finalJson = decodeBase64Text(reversed)
        val obj = JsonParser.parseString(finalJson).asJsonObject
        return obj.get("source")?.asString?.takeIf { it.startsWith("http") }
            ?: obj.get("direct_access_url")?.asString?.takeIf { it.startsWith("http") }
    }

    private fun rot13(input: String): String = buildString(input.length) {
        for (c in input) {
            when (c) {
                in 'a'..'z' -> append(((c - 'a' + 13) % 26 + 'a'.code).toChar())
                in 'A'..'Z' -> append(((c - 'A' + 13) % 26 + 'A'.code).toChar())
                else -> append(c)
            }
        }
    }

    /** Base64 wie android.util.Base64.DEFAULT (Standard- oder URL-Alphabet, Zeilenumbrüche erlaubt). */
    private fun decodeBase64Text(input: String): String =
        input.decodeBase64()?.utf8() ?: throw IllegalArgumentException("Ungültiges Base64")
}
