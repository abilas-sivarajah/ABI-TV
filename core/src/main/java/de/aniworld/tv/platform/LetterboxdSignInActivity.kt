package de.aniworld.tv.platform

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import de.aniworld.tv.data.remote.LetterboxdListSync

/**
 * Echte Letterboxd-Anmeldeseite in einer WebView (Handy und TV). Benutzername und Passwort tippt
 * man dort selbst ein; sobald die Seite einen angemeldeten Nutzer meldet, schließt sich das Fenster.
 *
 * Auf dem TV gibt es einen Mauszeiger: Die Pfeiltasten bewegen ihn, OK klickt, am Rand scrollt die
 * Seite. So lässt sich auch die Sicherheitsprüfung von Cloudflare anklicken, die per Steuerkreuz
 * allein nicht erreichbar ist.
 */
class LetterboxdSignInActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var cursor: CursorView
    private var done = false
    private var pressDownTime = 0L

    private val isTv: Boolean
        get() = packageManager.hasSystemFeature("android.software.leanback") ||
            packageManager.hasSystemFeature("amazon.hardware.fire_tv")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
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

        cursor = CursorView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            visibility = if (isTv) View.VISIBLE else View.GONE
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            addView(web)
            addView(cursor)
            if (isTv) {
                addView(
                    TextView(this@LetterboxdSignInActivity).apply {
                        text = "Pfeiltasten: Zeiger bewegen  •  OK: klicken  •  Zurück: schließen"
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setBackgroundColor(Color.argb(200, 20, 24, 28))
                        setPadding(dp(14), dp(6), dp(14), dp(6))
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                        ).apply { bottomMargin = dp(12) }
                    }
                )
            }
        }
        setContentView(root)
        web.loadUrl(LetterboxdListSync.SIGN_IN_URL)
        web.requestFocus()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Zurück: erst innerhalb der Seite zurück, dann das Fenster schließen
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP && web.canGoBack()) {
                web.goBack()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
        if (isTv && handleCursorKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    /** Steuerkreuz bewegt den Zeiger (schneller, je länger man drückt), OK klickt an seiner Stelle. */
    private fun handleCursorKey(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        val step = (dp(10) + event.repeatCount * dp(4)).coerceAtMost(dp(48)).toFloat()
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (down) moveCursor(-step, 0f)
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (down) moveCursor(step, 0f)
            KeyEvent.KEYCODE_DPAD_UP -> if (down) moveCursor(0f, -step)
            KeyEvent.KEYCODE_DPAD_DOWN -> if (down) moveCursor(0f, step)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (down && event.repeatCount == 0) {
                    pressDownTime = SystemClock.uptimeMillis()
                    sendTouch(MotionEvent.ACTION_DOWN, pressDownTime)
                    cursor.isClicking = true
                } else if (!down) {
                    sendTouch(MotionEvent.ACTION_UP, pressDownTime)
                    cursor.isClicking = false
                }
            }
            else -> return false
        }
        return true
    }

    /** Zeiger verschieben; stößt er an den Rand, scrollt stattdessen die Seite. */
    private fun moveCursor(dx: Float, dy: Float) {
        val margin = dp(6).toFloat()
        val maxX = (cursor.width - margin).coerceAtLeast(margin)
        val maxY = (cursor.height - margin).coerceAtLeast(margin)
        val newX = cursor.pointerX + dx
        val newY = cursor.pointerY + dy
        if (newY < margin) web.scrollBy(0, (newY - margin).toInt())
        if (newY > maxY) web.scrollBy(0, (newY - maxY).toInt())
        if (newX < margin) web.scrollBy((newX - margin).toInt(), 0)
        if (newX > maxX) web.scrollBy((newX - maxX).toInt(), 0)
        cursor.moveTo(newX.coerceIn(margin, maxX), newY.coerceIn(margin, maxY))
    }

    /** Wie ein Fingertipp an der Zeigerstelle – die Seite erhält ihn als echte Berührung. */
    private fun sendTouch(action: Int, downTime: Long) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, cursor.pointerX, cursor.pointerY, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        web.dispatchTouchEvent(event)
        event.recycle()
        if (action == MotionEvent.ACTION_UP) web.requestFocus()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    /** Zeichnet den Mauszeiger (Pfeil mit dunklem Rand) über der Seite; fängt selbst keine Eingaben ab. */
    @SuppressLint("ViewConstructor")
    private class CursorView(context: Context) : View(context) {
        var pointerX = -1f
            private set
        var pointerY = -1f
            private set
        var isClicking = false
            set(value) {
                field = value
                invalidate()
            }

        private val density = context.resources.displayMetrics.density
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
            strokeJoin = Paint.Join.ROUND
        }
        private val pressRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 33, 150, 243)
            style = Paint.Style.FILL
        }
        private val arrow = Path()

        init {
            isFocusable = false
            isClickable = false
        }

        fun moveTo(x: Float, y: Float) {
            pointerX = x
            pointerY = y
            invalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            // Startposition: etwas links oben in der Bildmitte
            if (pointerX < 0) {
                pointerX = w * 0.3f
                pointerY = h * 0.45f
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (pointerX < 0) return
            val s = density * 1.6f
            if (isClicking) canvas.drawCircle(pointerX, pointerY, 12f * s, pressRing)
            arrow.reset()
            arrow.moveTo(pointerX, pointerY)
            arrow.lineTo(pointerX, pointerY + 17f * s)
            arrow.lineTo(pointerX + 4.5f * s, pointerY + 13f * s)
            arrow.lineTo(pointerX + 8f * s, pointerY + 20.5f * s)
            arrow.lineTo(pointerX + 10.5f * s, pointerY + 19.5f * s)
            arrow.lineTo(pointerX + 7f * s, pointerY + 12f * s)
            arrow.lineTo(pointerX + 12.5f * s, pointerY + 12f * s)
            arrow.close()
            canvas.drawPath(arrow, fill)
            canvas.drawPath(arrow, outline)
        }
    }
}
