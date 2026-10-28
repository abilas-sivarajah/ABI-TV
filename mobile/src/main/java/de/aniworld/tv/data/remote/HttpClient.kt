package de.aniworld.tv.data.remote

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object HttpClient {

    const val BASE_URL = "https://aniworld.to"
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    val okHttp: OkHttpClient = OkHttpClient.Builder()
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
