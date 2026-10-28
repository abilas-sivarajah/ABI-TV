package de.aniworld.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import de.aniworld.tv.AniWorldApp
import de.aniworld.tv.data.model.AnimeCard
import de.aniworld.tv.data.model.LanguageOption
import de.aniworld.tv.data.model.WatchProgressEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object LocalStorage {

    private const val PREFS_NAME = "aniworld_tv_prefs"
    private const val KEY_WATCHLIST = "watchlist_v1"
    private const val KEY_PROGRESS = "progress_v1"
    private const val KEY_WATCHED_EPS = "watched_episodes_v1"
    private const val KEY_PREFERRED_LANG = "preferred_lang_key"
    private const val KEY_GITHUB_REPO = "github_repo_slug"
    private const val KEY_IGNORED_UPDATE_TAG = "ignored_update_tag"
    private const val KEY_UPDATE_SNOOZED_UNTIL = "update_snoozed_until"
    private const val KEY_CUSTOM_M3U_URL = "custom_m3u_url_v1"

    private val gson = Gson()
    private val prefs: SharedPreferences by lazy {
        AniWorldApp.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _watchlistFlow = MutableStateFlow(readWatchlist())
    val watchlistFlow: StateFlow<List<AnimeCard>> = _watchlistFlow.asStateFlow()

    private val _progressFlow = MutableStateFlow(readProgressList())
    val progressFlow: StateFlow<List<WatchProgressEntry>> = _progressFlow.asStateFlow()

    private val _watchedEpisodesFlow = MutableStateFlow(readWatchedEpisodes())
    val watchedEpisodesFlow: StateFlow<Set<String>> = _watchedEpisodesFlow.asStateFlow()

    // ------------------- Bevorzugte Sprache -------------------

    var preferredLanguage: LanguageOption
        get() {
            val key = prefs.getInt(KEY_PREFERRED_LANG, LanguageOption.GER_DUB.langKey)
            return LanguageOption.fromKey(key) ?: LanguageOption.GER_DUB
        }
        set(value) {
            prefs.edit().putInt(KEY_PREFERRED_LANG, value.langKey).apply()
        }

    // ------------------- GitHub Auto-Update -------------------

    var githubRepo: String
        get() = prefs.getString(KEY_GITHUB_REPO, "abilas-sivarajah/ABI-TV") ?: "abilas-sivarajah/ABI-TV"
        set(value) = prefs.edit().putString(KEY_GITHUB_REPO, value).apply()

    var ignoredUpdateTag: String
        get() = prefs.getString(KEY_IGNORED_UPDATE_TAG, "") ?: ""
        set(value) = prefs.edit().putString(KEY_IGNORED_UPDATE_TAG, value).apply()

    var updateSnoozedUntil: Long
        get() = prefs.getLong(KEY_UPDATE_SNOOZED_UNTIL, 0L)
        set(value) = prefs.edit().putLong(KEY_UPDATE_SNOOZED_UNTIL, value).apply()

    // ------------------- Live TV / M3U -------------------

    var customM3uUrl: String
        get() = prefs.getString(KEY_CUSTOM_M3U_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_M3U_URL, value).apply()

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
        prefs.edit().putString(KEY_WATCHLIST, gson.toJson(current)).apply()
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
        prefs.edit().putString(KEY_PROGRESS, gson.toJson(trimmed)).apply()
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
        prefs.edit().putString(KEY_PROGRESS, gson.toJson(updated)).apply()
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
            prefs.edit().putStringSet(KEY_WATCHED_EPS, updated).apply()
            _watchedEpisodesFlow.value = updated
        }
    }

    fun isEpisodeWatched(episodePath: String): Boolean =
        episodePath in _watchedEpisodesFlow.value

    private fun readWatchedEpisodes(): Set<String> =
        prefs.getStringSet(KEY_WATCHED_EPS, emptySet())?.toSet() ?: emptySet()

    private fun cleanSlug(raw: String): String =
        if (raw.startsWith("hdfilme:") || raw.contains("hd-filme.one")) {
            val rel = raw.removePrefix("hdfilme:").removePrefix("https://hd-filme.one").trim()
            "hdfilme:${if (rel.startsWith("/")) rel else "/$rel"}"
        } else {
            raw.removePrefix("https://aniworld.to")
                .removePrefix("/anime/stream/")
                .substringBefore("/")
                .trim()
        }
}
