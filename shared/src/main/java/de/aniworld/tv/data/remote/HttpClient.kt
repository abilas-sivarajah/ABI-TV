package de.aniworld.tv.data.remote

import de.aniworld.tv.platform.AppLog
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object HttpClient {

    const val BASE_URL = "https://aniworld.to"
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    /**
     * Telekom, Vodafone, O2 & Co. sperren Filmpalast und S.to auf Empfehlung der CUII per DNS –
     * im Mobilfunk fast immer, im WLAN je nach Router. Namen werden deshalb verschlüsselt über
     * DNS-over-HTTPS (Cloudflare, Google) aufgelöst; nur wenn beide nicht erreichbar sind, fragt
     * die App wie bisher den DNS-Server des Netzes.
     */
    private object UnblockedDns : Dns {

        private const val CACHE_TTL_MS = 5 * 60 * 1000L
        private const val DOH_RETRY_DELAY_MS = 2 * 60 * 1000L

        private val bootstrapClient = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()

        private val resolvers = listOf(
            doh("https://cloudflare-dns.com/dns-query", "1.1.1.1", "1.0.0.1", "2606:4700:4700::1111"),
            doh("https://dns.google/dns-query", "8.8.8.8", "8.8.4.4", "2001:4860:4860::8888")
        )

        private val cache = ConcurrentHashMap<String, Pair<Long, List<InetAddress>>>()

        // Blockt ein Netz die DoH-Server selbst, nicht vor jeder neuen Verbindung erneut warten
        @Volatile
        private var dohUnreachableUntil = 0L

        private fun doh(url: String, vararg bootstrapIps: String): DnsOverHttps =
            DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url(url.toHttpUrl())
                .bootstrapDnsHosts(bootstrapIps.map { InetAddress.getByName(it) })
                .build()

        override fun lookup(hostname: String): List<InetAddress> {
            val now = System.currentTimeMillis()
            cache[hostname]?.let { (ts, addresses) ->
                if (now - ts < CACHE_TTL_MS) return addresses
            }
            if (now >= dohUnreachableUntil) {
                var reachable = false
                for (resolver in resolvers) {
                    val result = runCatching { resolver.lookup(hostname) }
                    // Ohne cause hat der Server geantwortet (z. B. NXDOMAIN, private Adresse)
                    if (result.isSuccess || result.exceptionOrNull()?.cause == null) reachable = true
                    val addresses = result.getOrNull()
                    if (!addresses.isNullOrEmpty()) {
                        // IPv4 zuerst: OkHttp probiert die Adressen der Reihe nach durch
                        val sorted = addresses.sortedBy { it is Inet6Address }
                        cache[hostname] = now to sorted
                        return sorted
                    }
                }
                if (!reachable) {
                    AppLog.w("HttpClient", "DNS-over-HTTPS nicht erreichbar, nutze DNS des Netzes")
                    dohUnreachableUntil = now + DOH_RETRY_DELAY_MS
                }
            }
            return Dns.SYSTEM.lookup(hostname)
        }
    }

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .dns(UnblockedDns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val list = cookieStore.getOrPut(url.host) { mutableListOf() }
                synchronized(list) {
                    cookies.forEach { newCookie ->
                        list.removeAll { it.name == newCookie.name }
                        list.add(newCookie)
                    }
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                val list = cookieStore[url.host] ?: return emptyList()
                synchronized(list) {
                    return list.toList()
                }
            }
        })
        .addInterceptor { chain ->
            val orig = chain.request()
            val effectiveUrl = if (
                orig.url.host.equals("dropload.io", ignoreCase = true) ||
                orig.url.host.equals("www.dropload.io", ignoreCase = true)
            ) {
                orig.url.newBuilder().host("dr0pstream.com").build()
            } else {
                orig.url
            }
            val ua = orig.header("User-Agent") ?: USER_AGENT
            val req = orig.newBuilder()
                .url(effectiveUrl)
                .header("User-Agent", ua)
                .header("Accept-Language", "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()
            chain.proceed(req)
        }
        .build()

    fun getHtml(url: String, referer: String = "$BASE_URL/"): Pair<String, String> {
        val fullUrl = if (url.startsWith("http")) url else "$BASE_URL${if (url.startsWith("/")) "" else "/"}$url"
        val req = Request.Builder()
            .url(fullUrl)
            .header("Referer", referer)
            .build()
        okHttp.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            return res.request.url.toString() to body
        }
    }

    fun normalizeImageUrl(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim()
        // Hochauflösendes Cover bevorzugen, falls 150x225 im Pfad steht
        val upgraded = trimmed.replace("_150x225.", "_220x330.")
        return when {
            upgraded.startsWith("http") -> upgraded
            upgraded.startsWith("//") -> "https:$upgraded"
            upgraded.startsWith("/") -> "$BASE_URL$upgraded"
            else -> "$BASE_URL/$upgraded"
        }
    }
}
