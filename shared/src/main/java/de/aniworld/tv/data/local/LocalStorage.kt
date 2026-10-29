package de.aniworld.tv.data.local

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import de.aniworld.tv.data.model.AnimeCard
import de.aniworld.tv.data.model.LanguageOption
import de.aniworld.tv.data.model.WatchProgressEntry
import de.aniworld.tv.platform.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object LocalStorage {

    /** Name der SharedPreferences auf Android bzw. der Datei am PC – nicht ändern, sonst sind Merkliste & Verlauf weg. */
    const val PREFS_NAME = "aniworld_tv_prefs"
    private const val KEY_WATCHLIST = "watchlist_v1"
    private const val KEY_PROGRESS = "progress_v1"
    private const val KEY_WATCHED_EPS = "watched_episodes_v1"
    private const val KEY_PREFERRED_LANG = "preferred_lang_key"
    private const val KEY_GITHUB_REPO = "github_repo_slug"
    private const val KEY_IGNORED_UPDATE_TAG = "ignored_update_tag"
    private const val KEY_UPDATE_SNOOZED_UNTIL = "update_snoozed_until"
    private const val KEY_CUSTOM_M3U_URL = "custom_m3u_url_v1"

    private val gson = Gson()

    @Volatile
    private var storeRef: KeyValueStore? = null
    private val prefs: KeyValueStore
        get() = storeRef ?: error("LocalStorage.init() wurde beim App-Start nicht aufgerufen.")

    // Erst in init() befüllen: Beim Laden des Objekts gibt es noch keinen Speicher, und eine
    // leer gelesene Merkliste würde beim nächsten Speichern die echte überschreiben
    private val _watchlistFlow = MutableStateFlow<List<AnimeCard>>(emptyList())
    val watchlistFlow: StateFlow<List<AnimeCard>> = _watchlistFlow.asStateFlow()

    private val _progressFlow = MutableStateFlow<List<WatchProgressEntry>>(emptyList())
    val progressFlow: StateFlow<List<WatchProgressEntry>> = _progressFlow.asStateFlow()

    private val _watchedEpisodesFlow = MutableStateFlow<Set<String>>(emptySet())
    val watchedEpisodesFlow: StateFlow<Set<String>> = _watchedEpisodesFlow.asStateFlow()

    /** Muss beim App-Start vor dem ersten Zugriff aufgerufen werden. */
    fun init(store: KeyValueStore) {
        storeRef = store
        _watchlistFlow.value = readWatchlist()
        _progressFlow.value = readProgressList()
        _watchedEpisodesFlow.value = readWatchedEpisodes()
    }

    // ------------------- Bevorzugte Sprache -------------------

    var preferredLanguage: LanguageOption
        get() {
            val key = prefs.getInt(KEY_PREFERRED_LANG, LanguageOption.GER_DUB.langKey)
            return LanguageOption.fromKey(key) ?: LanguageOption.GER_DUB
        }
        set(value) {
            prefs.putInt(KEY_PREFERRED_LANG, value.langKey)
        }

    // ------------------- GitHub Auto-Update -------------------

    var githubRepo: String
        get() = prefs.getString(KEY_GITHUB_REPO, "abilas-sivarajah/ABI-TV") ?: "abilas-sivarajah/ABI-TV"
        set(value) = prefs.putString(KEY_GITHUB_REPO, value)

    var ignoredUpdateTag: String
        get() = prefs.getString(KEY_IGNORED_UPDATE_TAG, "") ?: ""
        set(value) = prefs.putString(KEY_IGNORED_UPDATE_TAG, value)

    var updateSnoozedUntil: Long
        get() = prefs.getLong(KEY_UPDATE_SNOOZED_UNTIL, 0L)
        set(value) = prefs.putLong(KEY_UPDATE_SNOOZED_UNTIL, value)

    // ------------------- Live TV / M3U -------------------

    var customM3uUrl: String
        get() = prefs.getString(KEY_CUSTOM_M3U_URL, "") ?: ""
        set(value) = prefs.putString(KEY_CUSTOM_M3U_URL, value)

    // ------------------- Merkliste (Watchlist) -------------------

    fun isInWatchlist(slug: String): Boolean {
        val clean = cleanSlug(slug)
        return _watchlistFlow.value.any { it.cleanSlug == clean }
    }

    fun toggleWatchlist(card: AnimeCard): Boolean {
        val current = _watchlistFlow.value.toMutableList()
        val clean = card.cleanSlug
        val existingIdx = current.indexOfFirst { it.cleanSlug == clean }
        val added = if (existingIdx >= 0) {
            current.removeAt(existingIdx)
            false
        } else {
            current.add(0, card.copy(slug = clean))
            true
        }
        prefs.putString(KEY_WATCHLIST, gson.toJson(current))
        _watchlistFlow.value = current
        return added
    }

    private fun readWatchlist(): List<AnimeCard> = runCatching {
        val raw = prefs.getString(KEY_WATCHLIST, null) ?: return emptyList()
        val type = object : TypeToken<List<AnimeCard>>() {}.type
        gson.fromJson<List<AnimeCard>>(raw, type) ?: emptyList()
    }.getOrDefault(emptyList())

    // ------------------- Weiterschauen & Episoden-Fortschritt -------------------

    fun saveWatchProgress(entry: WatchProgressEntry) {
        val current = _progressFlow.value.toMutableList()
        current.removeAll { it.animeSlug == entry.animeSlug }
        current.add(0, entry)
        val trimmed = current.take(40)
        prefs.putString(KEY_PROGRESS, gson.toJson(trimmed))
        _progressFlow.value = trimmed

        // Ab 85 % Fortschritt Folge automatisch als gesehen markieren
        if (entry.durationMs > 0 && entry.positionMs.toFloat() / entry.durationMs >= 0.85f) {
            markEpisodeWatched(entry.episodePath)
        }
    }

    fun getProgressForAnime(slug: String): WatchProgressEntry? {
        val clean = cleanSlug(slug)
        return _progressFlow.value.firstOrNull { it.animeSlug == clean }
    }

    fun getSavedPositionForEpisode(episodePath: String): Long {
        val entry = _progressFlow.value.firstOrNull { it.episodePath == episodePath } ?: return 0L
        // Wenn bereits zu > 93 % beendet, wieder von vorne starten
        if (entry.durationMs > 0 && entry.positionMs.toFloat() / entry.durationMs >= 0.93f) {
            return 0L
        }
        return entry.positionMs
    }

    fun removeFromContinueWatching(slug: String) {
        val clean = cleanSlug(slug)
        val updated = _progressFlow.value.filterNot { it.animeSlug == clean }
        prefs.putString(KEY_PROGRESS, gson.toJson(updated))
        _progressFlow.value = updated
    }

    private fun readProgressList(): List<WatchProgressEntry> = runCatching {
        val raw = prefs.getString(KEY_PROGRESS, null) ?: return emptyList()
        val type = object : TypeToken<List<WatchProgressEntry>>() {}.type
        gson.fromJson<List<WatchProgressEntry>>(raw, type) ?: emptyList()
    }.getOrDefault(emptyList())

    // ------------------- Gesehene Folgen -------------------

    fun markEpisodeWatched(episodePath: String) {
        val updated = _watchedEpisodesFlow.value.toMutableSet()
        if (updated.add(episodePath)) {
            prefs.putStringSet(KEY_WATCHED_EPS, updated)
            _watchedEpisodesFlow.value = updated
        }
    }

    fun isEpisodeWatched(episodePath: String): Boolean =
        episodePath in _watchedEpisodesFlow.value

    private fun readWatchedEpisodes(): Set<String> =
        prefs.getStringSet(KEY_WATCHED_EPS, emptySet()).toSet()

    // Gleiche Regeln wie die Karten selbst – eine eigene Kopie hatte Filmpalast-Slugs zu "filmpalast:" gekürzt
    private fun cleanSlug(raw: String): String = AnimeCard(slug = raw, title = "", coverUrl = "").cleanSlug
}
