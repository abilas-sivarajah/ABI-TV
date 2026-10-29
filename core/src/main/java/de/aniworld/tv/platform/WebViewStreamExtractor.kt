package de.aniworld.tv.platform

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import de.aniworld.tv.data.remote.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Unsichtbare WebView als zweite Stufe des StreamResolvers: lädt die Hoster-Seite und wird
 * sofort nach dem Abgreifen der .m3u8/.mp4 URL wieder geschlossen.
 */
class WebViewStreamExtractor(context: Context) : BrowserStreamExtractor {

    private val appContext = context.applicationContext

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override suspend fun extract(targetUrl: String, referer: String): Pair<String, String>? =
        withContext(Dispatchers.Main) {
            var webView: WebView? = null
            try {
                withTimeoutOrNull(BrowserStreamHooks.TIMEOUT_MS) {
                    suspendCancellableCoroutine { cont ->
                        val done = AtomicBoolean(false)
                        val mainHandler = Handler(Looper.getMainLooper())
                        var lastPageUrl = targetUrl

                        fun finishWith(streamUrl: String?, pageUrl: String) {
                            if (done.compareAndSet(false, true)) {
                                mainHandler.post {
                                    try {
                                        webView?.stopLoading()
                                        webView?.loadUrl("about:blank")
                                        webView?.destroy()
                                        webView = null
                                    } catch (_: Throwable) {}
                                }
                                if (cont.isActive) {
                                    cont.resume(streamUrl?.let { it to pageUrl })
                                }
                            }
                        }

                        val wv = WebView(appContext).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                blockNetworkImage = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                userAgentString = HttpClient.USER_AGENT
                            }

                            addJavascriptInterface(
                                object {
                                    @JavascriptInterface
                                    fun onStream(url: String?, ref: String?) {
                                        if (!url.isNullOrBlank() && url.startsWith("http") && !url.contains("slides")) {
                                            finishWith(url.trim(), ref ?: lastPageUrl)
                                        }
                                    }
                                },
                                BrowserStreamHooks.BRIDGE_NAME
                            )

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    if (!url.isNullOrBlank()) lastPageUrl = url
                                    view?.evaluateJavascript(BrowserStreamHooks.JS_PLAYER_HOOK, null)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    if (!url.isNullOrBlank()) lastPageUrl = url
                                    view?.evaluateJavascript(BrowserStreamHooks.JS_PLAYER_HOOK, null)
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val reqUrl = request?.url?.toString() ?: return null
                                    if (BrowserStreamHooks.isMediaStreamUrl(reqUrl)) {
                                        finishWith(reqUrl, lastPageUrl)
                                        return emptyResponse()
                                    }
                                    if (BrowserStreamHooks.isBlockedAdOrAsset(reqUrl)) {
                                        return emptyResponse()
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }

                                @SuppressLint("WebViewClientOnReceivedSslError")
                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: SslErrorHandler?,
                                    error: SslError?
                                ) {
                                    handler?.proceed()
                                }
                            }
                        }

                        webView = wv
                        cont.invokeOnCancellation {
                            mainHandler.post {
                                try {
                                    webView?.stopLoading()
                                    webView?.loadUrl("about:blank")
                                    webView?.destroy()
                                    webView = null
                                } catch (_: Throwable) {}
                            }
                        }

                        wv.loadUrl(targetUrl, mapOf("Referer" to referer))
                    }
                }
            } finally {
                try {
                    webView?.stopLoading()
                    webView?.loadUrl("about:blank")
                    webView?.destroy()
                } catch (_: Throwable) {}
            }
        }

    private fun emptyResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
}
