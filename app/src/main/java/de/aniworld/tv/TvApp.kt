package de.aniworld.tv

import de.aniworld.tv.cast.TvCastManager

class TvApp : AniWorldApp() {
    override fun onCreate() {
        super.onCreate()
        TvCastManager.start(this)
    }

    override fun onTerminate() {
        TvCastManager.stop(this)
        super.onTerminate()
    }
}
