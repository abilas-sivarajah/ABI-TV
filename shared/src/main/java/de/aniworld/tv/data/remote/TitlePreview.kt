package de.aniworld.tv.data.remote

import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.model.AnimeCard
import de.aniworld.tv.data.model.AnimeDetail
import de.aniworld.tv.data.model.EpisodeItem
import de.aniworld.tv.data.model.LanguageOption
import java.util.concurrent.ConcurrentHashMap

/**
 * Daten für Titelbild und Info-Feld der TV- und Handy-App: Details und Bilder eines Titels,
 * Beschriftungen und was "Abspielen" startet. Die Darstellung liegt in den jeweiligen Apps.
 */
object TitlePreview {

    /** Anzahl der Vorschläge im Titelbild. */
    const val HERO_COUNT = 6

    /** So lange bleibt ein Vorschlag stehen, bevor das Titelbild weiterblättert. */
    const val HERO_ROTATE_MS = 9_000L

    private val detailCache = ConcurrentHashMap<String, AnimeDetail>()
    private val artworkCache = ConcurrentHashMap<String, TmdbArtwork.Artwork>()

    sealed interface PlayTarget {
        data class Ready(val detail: AnimeDetail, val episode: EpisodeItem, val language: LanguageOption) : PlayTarget
        data class Failed(val message: String) : PlayTarget
    }

    fun cachedDetail(slug: String): AnimeDetail? = detailCache[slug]

    fun cachedArtwork(slug: String): TmdbArtwork.Artwork? = artworkCache[slug]

    /** Dieselbe Quelle wie die Detailseite, je nach Anbieter des Titels. Null bei Netzwerkfehlern. */
    suspend fun loadDetail(slug: String): AnimeDetail? {
        detailCache[slug]?.let { return it }
        return runCatching {
            when {
                FilmpalastScraper.isFilmpalastSlug(slug) -> FilmpalastScraper.fetchDetail(slug)
                HdFilmeScraper.isHdFilmeSlug(slug) -> HdFilmeScraper.fetchDetail(slug)
                SerienStreamScraper.isStoSlug(slug) -> SerienStreamScraper.fetchDetail(slug)
                KissAsianScraper.isKissAsianSlug(slug) -> KissAsianScraper.fetchDetail(slug)
                else -> AniWorldScraper.fetchAnimeDetail(slug)
            }
        }.getOrNull()?.also { detailCache[slug] = it }
    }

    /** Bilder von TMDB; null, wenn TMDB nicht eingerichtet ist oder den Titel nicht kennt. */
    suspend fun loadArtwork(anime: AnimeCard, detail: AnimeDetail?): TmdbArtwork.Artwork? {
        if (!TmdbArtwork.isConfigured) return null
        val slug = anime.cleanSlug
        artworkCache[slug]?.let { return it }
        val title = detail?.title?.takeIf { it.isNotBlank() } ?: anime.title
        val year = detail?.years?.let { Regex("""(19|20)\d{2}""").find(it)?.value?.toInt() }
        return TmdbArtwork.find(title, year, tmdbKind(anime, detail))?.also { artworkCache[slug] = it }
    }

    /** Wahrscheinlicher Typ für die TMDB-Suche; der andere wird notfalls auch probiert. */
    private fun tmdbKind(anime: AnimeCard, detail: AnimeDetail?): TmdbArtwork.Kind = when {
        anime.isSto || anime.isKissAsian -> TmdbArtwork.Kind.TV
        anime.isFilmpalast -> TmdbArtwork.Kind.MOVIE
        anime.isHdFilme -> if (detail?.seasons?.isNotEmpty() == true) TmdbArtwork.Kind.TV else TmdbArtwork.Kind.MOVIE
        else -> TmdbArtwork.Kind.TV // AniWorld: meist Serien
    }

    /** Art des Titels: "Film", "Serie" oder "Anime". */
    fun kindLabel(anime: AnimeCard, detail: AnimeDetail?): String = when {
        anime.isSto || anime.isKissAsian -> "Serie"
        anime.isFilmpalast -> "Film"
        anime.isHdFilme -> if (detail?.seasons?.isNotEmpty() == true) "Serie" else "Film"
        else -> "Anime"
    }

    /** Überschrift im Titelbild, z. B. "FILM  ·  FILMPALAST HIGHLIGHT". */
    fun heroLabel(anime: AnimeCard, detail: AnimeDetail?): String {
        val source = when {
            anime.isFilmpalast -> "FILMPALAST HIGHLIGHT"
            anime.isSto -> "SERIENSTREAM HIGHLIGHT"
            anime.isKissAsian -> "KISSASIAN HIGHLIGHT"
            anime.isHdFilme -> "HD-FILME HIGHLIGHT"
            else -> "ANIWORLD EMPFEHLUNG"
        }
        return kindLabel(anime, detail).uppercase() + "  ·  " + source
    }

    /** Eckdaten wie "Action", "Fantasy", "2023", "FSK 12". */
    fun metaLine(anime: AnimeCard, detail: AnimeDetail?): List<String> = buildList {
        val genres = detail?.genres.orEmpty().filter { it.isNotBlank() }.take(2)
        if (genres.isNotEmpty()) addAll(genres) else anime.genreOrSubtitle.takeIf { it.isNotBlank() }?.let { add(it) }
        detail?.years?.takeIf { it.isNotBlank() }?.let { add(it) }
        detail?.ageRating?.takeIf { it.isNotBlank() }?.let { add(ageLabel(it)) }
    }

    /** Eckdaten als kleine Kästchen im Info-Feld: Art, Jahr, Bewertung, FSK. */
    fun chips(anime: AnimeCard, detail: AnimeDetail?): List<String> = buildList {
        add(kindLabel(anime, detail))
        detail?.years?.takeIf { it.isNotBlank() }?.let { add(it) }
        detail?.rating?.takeIf { it.isNotBlank() }?.let { add("★ $it") }
        detail?.ageRating?.takeIf { it.isNotBlank() }?.let { add(ageLabel(it)) }
    }

    private fun ageLabel(raw: String): String = if (raw.all(Char::isDigit)) "FSK $raw" else raw

    /** Genres für das Info-Feld; ohne Details der Untertitel der Karte. */
    fun genreLine(anime: AnimeCard, detail: AnimeDetail?): String {
        val genres = detail?.genres.orEmpty().filter { it.isNotBlank() }.take(3)
        return if (genres.isNotEmpty()) genres.joinToString(", ") else anime.genreOrSubtitle
    }

    /** Zufällige Titel fürs Titelbild (nur mit Cover, ohne Doppelte). */
    fun randomPicks(pool: List<AnimeCard>, count: Int = HERO_COUNT): List<AnimeCard> =
        pool.filter { it.coverUrl.isNotBlank() }.distinctBy { it.cleanSlug }.shuffled().take(count)

    /** Je [perSource] zufällige Titel aus jeder Quelle, abwechselnd angeordnet (Film, Serie, Anime, Film, …). */
    fun interleavedPicks(sources: List<List<AnimeCard>>, perSource: Int): List<AnimeCard> {
        val picks = sources.map { randomPicks(it, perSource) }
        return (0 until perSource)
            .flatMap { round -> picks.mapNotNull { it.getOrNull(round) } }
            .distinctBy { it.cleanSlug }
    }

    /** Was "Abspielen" startet: den Film bzw. die zuletzt geschaute Folge, sonst Folge 1. */
    suspend fun resolvePlayback(slug: String): PlayTarget {
        val detail = loadDetail(slug) ?: return PlayTarget.Failed("Titel konnte nicht geladen werden.")
        val language = LocalStorage.preferredLanguage
        val episode = pickEpisode(slug, detail, language)
            ?: return PlayTarget.Failed("Keine Folge gefunden – bitte über „Details“ auswählen.")
        return PlayTarget.Ready(detail, episode, language)
    }

    private fun pickEpisode(slug: String, detail: AnimeDetail, language: LanguageOption): EpisodeItem? {
        val progress = LocalStorage.getProgressForAnime(slug) ?: return detail.episodes.firstOrNull()
        return detail.episodes.firstOrNull { it.path == progress.episodePath } ?: EpisodeItem(
            number = progress.episodeNumber,
            seasonLabel = progress.seasonLabel,
            path = progress.episodePath,
            titleDe = progress.episodeTitle,
            titleEn = "",
            languages = listOf(language)
        )
    }
}
