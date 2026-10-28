package de.aniworld.tv

import android.app.Application
import android.content.Context

object AppContext {
    lateinit var context: Context
}

class AniWorldApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        AppContext.context = this
    }

    companion object {
        lateinit var instance: AniWorldApp
            private set
    }
}
