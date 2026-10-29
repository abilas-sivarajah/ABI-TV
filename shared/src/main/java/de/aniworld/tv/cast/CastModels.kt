package de.aniworld.tv.cast

import kotlinx.coroutines.flow.StateFlow

data class PlayCommand(
    val animeSlug: String,
    val animeTitle: String,
    val coverUrl: String,
    val episodePath: String,
    val episodeNumber: Int,
    val seasonLabel: String,
    val titleDe: String,
    val preferredLangKey: Int
)

data class CastStatus(
    val isPlaying: Boolean = false,
    val title: String = "",
    val episodeTitle: String = "",
    val seasonLabel: String = "",
    val episodeNumber: Int = 0,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

/** Das Gerät, das Befehle vom Handy ausführt (Fernseher oder PC). */
interface CastHost {
    /** Name, den das Handy in der Geräteliste anzeigt. */
    val deviceName: String
    val deviceModel: String
    val castStatus: StateFlow<CastStatus>

    fun handlePlayCommand(cmd: PlayCommand)
    fun handleLiveCommand(channelName: String, streamUrl: String, logoUrl: String, headers: Map<String, String>)

    /** Gibt false zurück, wenn gerade kein Player läuft. */
    fun handleControlCommand(action: String, arg: Long = 0L): Boolean
}
