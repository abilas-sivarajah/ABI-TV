package de.aniworld.pc.platform

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Letterboxd-Benutzer, deren Watchlists im Reiter "Watchlist" angezeigt werden (eigener und andere). */
object LetterboxdUsers {

    private val gson = Gson()
    private val file by lazy { File(AppDirs.dataDir, "letterboxd-users.json") }

    private val _users = MutableStateFlow(load())
    val users: StateFlow<List<String>> = _users

    private fun load(): List<String> = runCatching {
        if (file.isFile) gson.fromJson<List<String>>(file.readText(Charsets.UTF_8), object : TypeToken<List<String>>() {}.type) else null
    }.getOrNull().orEmpty()

    /** Benutzernamen wie bei Letterboxd: Buchstaben, Ziffern, Unterstrich. Auch ein Profil-Link wird akzeptiert. */
    fun normalize(input: String): String? {
        val raw = input.trim()
            .removePrefix("https://").removePrefix("http://").removePrefix("www.")
            .removePrefix("letterboxd.com/")
            .trim('/', '@', ' ')
            .substringBefore('/')
            .lowercase()
        return raw.takeIf { it.isNotBlank() && it.all { c -> c.isLetterOrDigit() || c == '_' } }
    }

    fun add(name: String) {
        if (name in _users.value) return
        save(_users.value + name)
    }

    fun remove(name: String) = save(_users.value - name)

    private fun save(list: List<String>) {
        _users.value = list
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(gson.toJson(list), Charsets.UTF_8)
        }
    }
}
