package de.aniworld.tv

import android.app.Application
import android.content.Context
import de.aniworld.tv.cast.TvCastManager

object AppContext {
    lateinit var context: Context
}

class AniWorldApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        AppContext.context = this
        TvCastManager.start(this)
    }

    override fun onTerminate() {
        TvCastManager.stop(this)
        super.onTerminate()
    }

    companion object {
        lateinit var instance: AniWorldApp
            private set
    }
}
