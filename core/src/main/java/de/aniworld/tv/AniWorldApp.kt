package de.aniworld.tv

import android.app.Application
import android.content.Context
import android.util.Log
import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.remote.StreamResolver
import de.aniworld.tv.platform.AppLog
import de.aniworld.tv.platform.SharedPrefsStore
import de.aniworld.tv.platform.WebViewStreamExtractor

object AppContext {
    lateinit var context: Context
}

open class AniWorldApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        AppContext.context = this

        // Gemeinsame JVM-Schicht (:shared) an Android anbinden
        AppLog.sink = { level, tag, message, error ->
            when (level) {
                AppLog.Level.DEBUG -> Log.d(tag, message, error)
                AppLog.Level.INFO -> Log.i(tag, message, error)
                AppLog.Level.WARN -> Log.w(tag, message, error)
                AppLog.Level.ERROR -> Log.e(tag, message, error)
            }
        }
        LocalStorage.init(SharedPrefsStore(this, LocalStorage.PREFS_NAME))
        StreamResolver.browserExtractor = WebViewStreamExtractor(this)
    }

    companion object {
        lateinit var instance: AniWorldApp
            private set
    }
}
