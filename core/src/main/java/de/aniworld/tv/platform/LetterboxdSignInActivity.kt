package de.aniworld.tv.platform

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import de.aniworld.tv.data.remote.LetterboxdListSync

/**
 * Echte Letterboxd-Anmeldeseite in einer WebView (Handy und TV). Benutzername und Passwort tippt
 * man dort selbst ein; sobald die Seite einen angemeldeten Nutzer meldet, schließt sich das Fenster.
 */
class LetterboxdSignInActivity : Activity() {

    private lateinit var web: WebView
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        LetterboxdAccountAndroid.configure(web)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                if (url?.contains("letterboxd.com") != true) return
                // Nach jeder geladenen Seite fragen, ob man angemeldet ist
                view.evaluateJavascript(LetterboxdListSync.PROBE_EXPRESSION) { value ->
                    val name = LetterboxdListSync.parseProbe(value) ?: return@evaluateJavascript
                    if (done) return@evaluateJavascript
                    done = true
                    LetterboxdAccountAndroid.onSignedIn(name)
                    Toast.makeText(this@LetterboxdSignInActivity, "Bei Letterboxd angemeldet als $name", Toast.LENGTH_LONG).show()
                    view.postDelayed({ finish() }, 1_500)
                }
            }
        }
        setContentView(web)
        web.loadUrl(LetterboxdListSync.SIGN_IN_URL)
        web.requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Zurück: erst innerhalb der Seite zurück, dann das Fenster schließen
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && web.canGoBack()) {
            web.goBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
