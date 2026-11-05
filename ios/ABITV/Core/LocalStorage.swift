import Foundation
import Combine

public protocol KeyValueStore: AnyObject {
    func getString(_ key: String, default defaultValue: String?) -> String?
    func putString(_ key: String, value: String)
    func getInt(_ key: String, default defaultValue: Int) -> Int
    func putInt(_ key: String, value: Int)
    func getLong(_ key: String, default defaultValue: Int64) -> Int64
    func putLong(_ key: String, value: Int64)
    func getStringSet(_ key: String, default defaultValue: Set<String>) -> Set<String>
    func putStringSet(_ key: String, value: Set<String>)
}

public final class UserDefaultsStore: KeyValueStore {
    private let defaults: UserDefaults

    public init(suiteName: String = LocalStorage.prefsName) {
        self.defaults = UserDefaults(suiteName: suiteName) ?? .standard
    }

    public func getString(_ key: String, default defaultValue: String?) -> String? {
        defaults.string(forKey: key) ?? defaultValue
    }

    public func putString(_ key: String, value: String) {
        defaults.set(value, forKey: key)
    }

    public func getInt(_ key: String, default defaultValue: Int) -> Int {
        guard defaults.object(forKey: key) != nil else { return defaultValue }
        return defaults.integer(forKey: key)
    }

    public func putInt(_ key: String, value: Int) {
        defaults.set(value, forKey: key)
    }

    public func getLong(_ key: String, default defaultValue: Int64) -> Int64 {
        guard let num = defaults.object(forKey: key) as? NSNumber else { return defaultValue }
        return num.int64Value
    }

    public func putLong(_ key: String, value: Int64) {
        defaults.set(NSNumber(value: value), forKey: key)
    }

    public func getStringSet(_ key: String, default defaultValue: Set<String>) -> Set<String> {
        guard let arr = defaults.stringArray(forKey: key) else { return defaultValue }
        return Set(arr)
    }

    public func putStringSet(_ key: String, value: Set<String>) {
        defaults.set(Array(value), forKey: key)
    }
}

public final class InMemoryKeyValueStore: KeyValueStore {
    private var storage: [String: Any] = [:]

    public init() {}

    public func getString(_ key: String, default defaultValue: String?) -> String? {
        (storage[key] as? String) ?? defaultValue
    }

    public func putString(_ key: String, value: String) {
        storage[key] = value
    }

    public func getInt(_ key: String, default defaultValue: Int) -> Int {
        (storage[key] as? Int) ?? defaultValue
    }

    public func putInt(_ key: String, value: Int) {
        storage[key] = value
    }

    public func getLong(_ key: String, default defaultValue: Int64) -> Int64 {
        (storage[key] as? Int64) ?? defaultValue
    }

    public func putLong(_ key: String, value: Int64) {
        storage[key] = value
    }

    public func getStringSet(_ key: String, default defaultValue: Set<String>) -> Set<String> {
        (storage[key] as? Set<String>) ?? defaultValue
    }

    public func putStringSet(_ key: String, value: Set<String>) {
        storage[key] = value
    }
}

/// Lokaler Speicher für Merkliste, Wiedergabefortschritt, gesehene Folgen und Einstellungen.
/// Entspricht 1:1 `LocalStorage.kt` aus `:shared`.
public final class LocalStorage: ObservableObject {
    public static let shared = LocalStorage()

    public static let prefsName = "aniworld_tv_prefs"
    private static let keyWatchlist = "watchlist_v1"
    private static let keyProgress = "progress_v1"
    private static let keyWatchedEps = "watched_episodes_v1"
    private static let keyPreferredLang = "preferred_lang_key"
    private static let keyGithubRepo = "github_repo_slug"
    private static let keyIgnoredUpdateTag = "ignored_update_tag"
    private static let keyUpdateSnoozedUntil = "update_snoozed_until"
    private static let keyCustomM3uUrl = "custom_m3u_url_v1"
    private static let keyM3uFolderBookmark = "m3u_folder_bookmark_v1"
    private static let keyM3uFolderName = "m3u_folder_name_v1"
    private static let keyLetterboxdUsers = "letterboxd_users_v1"

    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    private var store: KeyValueStore

    @Published public private(set) var watchlist: [AnimeCard] = []
    @Published public private(set) var progressList: [WatchProgressEntry] = []
    @Published public private(set) var watchedEpisodes: Set<String> = []
    /// Letterboxd-Benutzer, deren Watchlists im Reiter "Watchlist" erscheinen.
    @Published public private(set) var letterboxdUsers: [String] = []

    public init(store: KeyValueStore = UserDefaultsStore()) {
        self.store = store
        reloadFromStore()
    }

    public func configure(store: KeyValueStore) {
        self.store = store
        reloadFromStore()
    }

    private func reloadFromStore() {
        watchlist = readWatchlist()
        progressList = readProgressList()
        watchedEpisodes = readWatchedEpisodes()
        letterboxdUsers = (store.getString(Self.keyLetterboxdUsers, default: "[]")?.data(using: .utf8))
            .flatMap { try? decoder.decode([String].self, from: $0) } ?? []
    }

    // ------------------- Letterboxd-Benutzer -------------------

    public func addLetterboxdUser(_ name: String) {
        guard !letterboxdUsers.contains(name) else { return }
        saveLetterboxdUsers(letterboxdUsers + [name])
    }

    public func removeLetterboxdUser(_ name: String) {
        saveLetterboxdUsers(letterboxdUsers.filter { $0 != name })
    }

    private func saveLetterboxdUsers(_ list: [String]) {
        letterboxdUsers = list
        if let data = try? encoder.encode(list), let text = String(data: data, encoding: .utf8) {
            store.putString(Self.keyLetterboxdUsers, value: text)
        }
    }

    // ------------------- Bevorzugte Sprache -------------------

    public var preferredLanguage: LanguageOption {
        get {
            let key = store.getInt(Self.keyPreferredLang, default: LanguageOption.gerDub.langKey)
            return LanguageOption.fromKey(key) ?? .gerDub
        }
        set {
            store.putInt(Self.keyPreferredLang, value: newValue.langKey)
            objectWillChange.send()
        }
    }

    // ------------------- GitHub Auto-Update -------------------

    public var githubRepo: String {
        get { store.getString(Self.keyGithubRepo, default: "abilas-sivarajah/ABI-TV") ?? "abilas-sivarajah/ABI-TV" }
        set { store.putString(Self.keyGithubRepo, value: newValue) }
    }

    public var ignoredUpdateTag: String {
        get { store.getString(Self.keyIgnoredUpdateTag, default: "") ?? "" }
        set { store.putString(Self.keyIgnoredUpdateTag, value: newValue) }
    }

    public var updateSnoozedUntil: Int64 {
        get { store.getLong(Self.keyUpdateSnoozedUntil, default: 0) }
        set { store.putLong(Self.keyUpdateSnoozedUntil, value: newValue) }
    }

    // ------------------- Live TV / M3U -------------------

    public var customM3uUrl: String {
        get { store.getString(Self.keyCustomM3uUrl, default: "") ?? "" }
        set { store.putString(Self.keyCustomM3uUrl, value: newValue) }
    }

    /// Eigener Ordner mit M3U-Dateien als Lesezeichen (Base64), damit die App ihn nach Neustarts weiter lesen darf.
    public var m3uFolderBookmark: String {
        get { store.getString(Self.keyM3uFolderBookmark, default: "") ?? "" }
        set { store.putString(Self.keyM3uFolderBookmark, value: newValue) }
    }

    /// Anzeigename des Ordners (z. B. "IPTV").
    public var m3uFolderName: String {
        get { store.getString(Self.keyM3uFolderName, default: "") ?? "" }
        set { store.putString(Self.keyM3uFolderName, value: newValue) }
    }

    // ------------------- Merkliste (Watchlist) -------------------

    public func isInWatchlist(_ slug: String) -> Bool {
        let clean = cleanSlug(slug)
        return watchlist.contains { $0.cleanSlug == clean }
    }

    @discardableResult
    public func toggleWatchlist(_ card: AnimeCard) -> Bool {
        var current = watchlist
        let clean = card.cleanSlug
        let added: Bool
        if let idx = current.firstIndex(where: { $0.cleanSlug == clean }) {
            current.remove(at: idx)
            added = false
        } else {
            current.insert(
                AnimeCard(
                    slug: clean,
                    title: card.title,
                    coverUrl: card.coverUrl,
                    genreOrSubtitle: card.genreOrSubtitle,
                    rating: card.rating
                ),
                at: 0
            )
            added = true
        }
        if let data = try? encoder.encode(current),
           let json = String(data: data, encoding: .utf8) {
            store.putString(Self.keyWatchlist, value: json)
        }
        watchlist = current
        return added
    }

    private func readWatchlist() -> [AnimeCard] {
        guard let raw = store.getString(Self.keyWatchlist, default: nil),
              let data = raw.data(using: .utf8),
              let list = try? decoder.decode([AnimeCard].self, from: data) else {
            return []
        }
        return list
    }

    // ------------------- Weiterschauen & Episoden-Fortschritt -------------------

    public func saveWatchProgress(_ entry: WatchProgressEntry) {
        var current = progressList
        let cleanEntrySlug = cleanSlug(entry.animeSlug)
        let normalizedEntry = WatchProgressEntry(
            animeSlug: cleanEntrySlug,
            animeTitle: entry.animeTitle,
            coverUrl: entry.coverUrl,
            seasonLabel: entry.seasonLabel,
            episodeNumber: entry.episodeNumber,
            episodeTitle: entry.episodeTitle,
            episodePath: entry.episodePath,
            preferredLangKey: entry.preferredLangKey,
            positionMs: entry.positionMs,
            durationMs: entry.durationMs,
            updatedAt: entry.updatedAt
        )
        current.removeAll { cleanSlug($0.animeSlug) == cleanEntrySlug }
        current.insert(normalizedEntry, at: 0)
        let trimmed = Array(current.prefix(40))
        if let data = try? encoder.encode(trimmed),
           let json = String(data: data, encoding: .utf8) {
            store.putString(Self.keyProgress, value: json)
        }
        progressList = trimmed

        // Ab 85 % Fortschritt Folge automatisch als gesehen markieren
        if entry.durationMs > 0 && Double(entry.positionMs) / Double(entry.durationMs) >= 0.85 {
            markEpisodeWatched(entry.episodePath)
        }
    }

    public func getProgressForAnime(_ slug: String) -> WatchProgressEntry? {
        let clean = cleanSlug(slug)
        return progressList.first { cleanSlug($0.animeSlug) == clean }
    }

    public func getSavedPositionForEpisode(_ episodePath: String) -> Int64 {
        guard let entry = progressList.first(where: { $0.episodePath == episodePath }) else {
            return 0
        }
        // Wenn bereits zu > 93 % beendet, wieder von vorne starten
        if entry.durationMs > 0 && Double(entry.positionMs) / Double(entry.durationMs) >= 0.93 {
            return 0
        }
        return entry.positionMs
    }

    public func removeFromContinueWatching(_ slug: String) {
        let clean = cleanSlug(slug)
        let updated = progressList.filter { cleanSlug($0.animeSlug) != clean }
        if let data = try? encoder.encode(updated),
           let json = String(data: data, encoding: .utf8) {
            store.putString(Self.keyProgress, value: json)
        }
        progressList = updated
    }

    private func readProgressList() -> [WatchProgressEntry] {
        guard let raw = store.getString(Self.keyProgress, default: nil),
              let data = raw.data(using: .utf8),
              let list = try? decoder.decode([WatchProgressEntry].self, from: data) else {
            return []
        }
        return list
    }

    // ------------------- Gesehene Folgen -------------------

    public func markEpisodeWatched(_ episodePath: String) {
        var updated = watchedEpisodes
        if updated.insert(episodePath).inserted {
            store.putStringSet(Self.keyWatchedEps, value: updated)
            watchedEpisodes = updated
        }
    }

    public func isEpisodeWatched(_ episodePath: String) -> Bool {
        watchedEpisodes.contains(episodePath)
    }

    private func readWatchedEpisodes() -> Set<String> {
        store.getStringSet(Self.keyWatchedEps, default: [])
    }

    private func cleanSlug(_ raw: String) -> String {
        AnimeCard(slug: raw, title: "", coverUrl: "").cleanSlug
    }
}
