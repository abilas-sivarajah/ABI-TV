package de.aniworld.tv.data.model

import java.io.Serializable

data class LiveTvChannel(
    val id: String,
    val name: String,
    val logoUrl: String,
    val streamUrl: String,
    val category: String,
    val qualityBadge: String = "HD",
    val headers: Map<String, String> = emptyMap(),
    /** Gruppe aus der M3U-Liste (group-title), z. B. "TR SPOR" oder "(V|DE) Filme - 2024". */
    val group: String = ""
) : Serializable

/** Senderliste plus ggf. Fehler beim Laden der eigenen M3U-Liste (die festen Sender gibt es immer). */
data class LiveTvLoadResult(
    val channels: List<LiveTvChannel>,
    val customError: String? = null,
    /** Reiter: die festen Kategorien plus je ein Reiter pro Ordner der eigenen M3U-Sammlung. */
    val categories: List<LiveTvCategory> = emptyList(),
    /**
     * Eigene Listen als Baum (PC): je Land bzw. Ordner ein Reiter mit Unterreitern
     * (z. B. Türkiye › Serien › Yerli Diziler › Show › Staffel 1).
     */
    val trees: List<LiveTvNode> = emptyList()
)

/** Ein Reiter im Baum: entweder mit Unterreitern ([children]) oder mit Sendern/Titeln ([channels]). */
data class LiveTvNode(
    val id: String,
    val title: String,
    val icon: String = "",
    val children: List<LiveTvNode> = emptyList(),
    val channels: List<LiveTvChannel> = emptyList()
) {
    /** Anzahl aller Sender/Titel darunter. */
    val total: Int by lazy { channels.size + children.sumOf { it.total } }

    /** Alle Sender/Titel darunter (für die Suche). */
    fun allChannels(): Sequence<LiveTvChannel> = channels.asSequence() + children.asSequence().flatMap { it.allChannels() }
}

/** Eine M3U-Datei aus dem eigenen Ordner; [dir] ist der Unterordner relativ zum gewählten Ordner ("" = oberste Ebene, Trenner "/"). */
data class M3uFolderFile(
    val dir: String,
    val fileName: String,
    val content: String
)

data class LiveTvCategory(
    val id: String,
    val title: String,
    val icon: String
) : Serializable
