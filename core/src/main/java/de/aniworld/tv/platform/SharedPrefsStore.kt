package de.aniworld.tv.platform

import android.content.Context
import android.content.SharedPreferences

/** [KeyValueStore] auf Basis der SharedPreferences (gleiche Dateinamen wie bisher). */
class SharedPrefsStore(context: Context, name: String) : KeyValueStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun getString(key: String, default: String?): String? = prefs.getString(key, default)
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()

    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    override fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()

    override fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)
    override fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()

    override fun getStringSet(key: String, default: Set<String>): Set<String> =
        prefs.getStringSet(key, default)?.toSet() ?: default

    override fun putStringSet(key: String, value: Set<String>) = prefs.edit().putStringSet(key, value).apply()
}
