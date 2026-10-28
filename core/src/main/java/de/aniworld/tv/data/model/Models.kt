package de.aniworld.tv.data.model

import java.io.Serializable

/**
 * Sprach-Option bei AniWorld:
 * - key = 1: Deutsch (GerDub)
 * - key = 2: Englisch Sub (EngSub)
 * - key = 3: Deutsch Sub (GerSub)
 */
enum class LanguageOption(
    val langKey: Int,
    val shortLabel: String,
    val fullLabel: String,
    val badgeColorHex: Long
) : Serializable {
    GER_DUB(1, "GER DUB", "Deutsch (Synchronisation)", 0xFF10B981),
    GER_SUB(3, "GER SUB", "Japanisch mit deutschem Untertitel", 0xFF3B82F6),
    ENG_SUB(2, "ENG SUB", "Japanisch mit englischem Untertitel", 0xFFF59E0B);

    companion object {
        fun fromKey(key: Int): LanguageOption? = entries.find { it.langKey == key }

        fun fromImgOrTitle(raw: String): LanguageOption? {
            val lower = raw.lowercase()
            return when {
                lower.contains("japanese-german") || lower.contains("deutschem untertitel") -> GER_SUB
                lower.contains("japanese-english") || lower.contains("englisch") -> ENG_SUB
                lower.contains("german") || lower.contains("deutsch") -> GER_DUB
                else -> null
            }
        }
    }
}

data class AnimeCard(
    val slug: String,           // z.B. "solo-leveling" oder "hdfilme:/filme1/42002-..."
    val title: String,
    val coverUrl: String,
    val genreOrSubtitle: String = "",
    val rating: String = ""
) : Serializable {
    val isHdFilme: Boolean
        get() = slug.startsWith("hdfilme:") || slug.contains("hd-filme.one")

    val cleanSlug: String
        get() = if (isHdFilme) {
            val rel = slug.removePrefix("hdfilme:").removePrefix("https://hd-filme.one").trim()
            "hdfilme:${if (rel.startsWith("/")) rel else "/$rel"}"
        } else {
            slug
                .removePrefix("https://aniworld.to")
                .removePrefix("/anime/stream/")
                .substringBefore("/")
                .trim()
        }

    val streamPath: String
        get() = if (isHdFilme) cleanSlug else "/anime/stream/$cleanSlug"
}

data class CatalogSection(
    val id: String,
    val title: String,
    val items: List<AnimeCard>
) : Serializable

data class SeasonLink(
    val label: String,          // z.B. "1", "2", "Filme"
    val path: String,           // z.B. "/anime/stream/solo-leveling/staffel-1"
    val isMovies: Boolean = label.equals("Filme", ignoreCase = true)
) : Serializable

data class EpisodeItem(
    val number: Int,
    val seasonLabel: String,
    val path: String,           // z.B. "/anime/stream/solo-leveling/staffel-1/episode-1"
    val titleDe: String,
    val titleEn: String,
    val languages: List<LanguageOption>
) : Serializable {
    val displayTitle: String
        get() = titleDe.ifBlank { titleEn.ifBlank { "Folge $number" } }

    val secondaryTitle: String
        get() = if (titleDe.isNotBlank() && titleEn.isNotBlank() && titleDe != titleEn) titleEn else ""
}

data class AnimeDetail(
    val slug: String,
    val title: String,
    val description: String,
    val coverUrl: String,
    val backdropUrl: String,
    val years: String,
    val ageRating: String,
    val rating: String,
    val genres: List<String>,
    val seasons: List<SeasonLink>,
    val currentSeasonPath: String,
    val episodes: List<EpisodeItem>
) : Serializable

data class HosterLink(
    val hosterName: String,     // z.B. "VOE", "Vidmoly", "Filemoon", "Doodstream"
    val langKey: Int,
    val language: LanguageOption,
    val redirectPath: String    // z.B. "/redirect/3800363"
) : Serializable

data class ResolvedStream(
    val streamUrl: String,
    val isHls: Boolean,
    val hosterName: String,
    val language: LanguageOption,
    val headers: Map<String, String>
) : Serializable

data class WatchProgressEntry(
    val animeSlug: String,
    val animeTitle: String,
    val coverUrl: String,
    val seasonLabel: String,
    val episodeNumber: Int,
    val episodeTitle: String,
    val episodePath: String,
    val preferredLangKey: Int,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
) : Serializable {
    val progressFraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

enum class SearchScope(
    val label: String,
    val icon: String
) {
    ANIMES("Animes", "🍙"),
    MOVIES("Filme & Serien", "🎬"),
    ALL("Alle", "✨")
}
