package de.aniworld.tv.data.model

import java.io.Serializable

data class LiveTvChannel(
    val id: String,
    val name: String,
    val logoUrl: String,
    val streamUrl: String,
    val category: String,
    val qualityBadge: String = "HD",
    val headers: Map<String, String> = emptyMap()
) : Serializable

/** Senderliste plus ggf. Fehler beim Laden der eigenen M3U-Liste (die festen Sender gibt es immer). */
data class LiveTvLoadResult(
    val channels: List<LiveTvChannel>,
    val customError: String? = null
)

data class LiveTvCategory(
    val id: String,
    val title: String,
    val icon: String
) : Serializable
