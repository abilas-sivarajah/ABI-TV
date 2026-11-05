package de.aniworld.tv.data.remote

import de.aniworld.tv.data.model.AnimeCard
import java.text.Normalizer

/**
 * Suche nach Wortanfängen: Ein Titel passt, wenn eines seiner Wörter mit der Eingabe beginnt.
 * Satzzeichen, Bindestriche und Leerzeichen zählen nicht – "sp" findet "Marvel's Spider-Man",
 * "spiderman" findet "Spider-Man", "john wick" findet "John Wick: Kapitel 4".
 */
object TitleSearch {

    private val nonWord = Regex("[^\\p{L}\\p{Nd}]+")
    private val marks = Regex("\\p{M}+")

    /** Kleinbuchstaben, ohne Akzente (é → e), ß → ss. */
    private fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(marks, "").replace("ß", "ss")

    private fun words(title: String): List<String> = fold(title).split(nonWord).filter { it.isNotEmpty() }

    private fun normalizedQuery(query: String): String = fold(query).filter { it.isLetterOrDigit() }

    /**
     * Wort, an dem der Treffer beginnt (0 = am Titelanfang), oder null, wenn der Titel nicht passt.
     * Ab diesem Wort wird der Rest des Titels ohne Trennzeichen mit der Eingabe verglichen.
     */
    fun matchPosition(title: String, query: String): Int? {
        val wanted = normalizedQuery(query)
        if (wanted.isEmpty()) return null
        val titleWords = words(title)
        for (start in titleWords.indices) {
            val rest = StringBuilder()
            for (i in start until titleWords.size) {
                rest.append(titleWords[i])
                if (rest.length >= wanted.length) break
            }
            if (rest.startsWith(wanted)) return start
        }
        return null
    }

    fun matches(title: String, query: String): Boolean = matchPosition(title, query) != null

    /** Nur passende Titel; Treffer am Titelanfang zuerst, dann kürzere Titel. */
    fun filterAndRank(items: List<AnimeCard>, query: String): List<AnimeCard> =
        items.asSequence()
            .mapNotNull { card -> matchPosition(card.title, query)?.let { card to it } }
            .sortedWith(compareBy<Pair<AnimeCard, Int>>({ it.second }, { it.first.title.length }))
            .map { it.first }
            .distinctBy { it.cleanSlug }
            .toList()

    /**
     * Suchbegriffe für Seiten, die nur "Titel enthält" können: die Eingabe selbst und ihr längstes
     * Wort, gekürzt auf 6 Zeichen ("spiderman" → "spider"), damit auch zusammengeschriebene oder
     * anders getrennte Titel gefunden werden. Gefiltert wird danach mit [filterAndRank].
     */
    fun siteQueries(query: String): List<String> {
        val full = query.trim()
        val longest = full.split(nonWord).filter { it.length >= 2 }.maxByOrNull { it.length }?.take(6)
        return listOfNotNull(full.takeIf { it.length >= 2 }, longest).distinct()
    }
}
