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

data class LiveTvCategory(
    val id: String,
    val title: String,
    val icon: String
) : Serializable
