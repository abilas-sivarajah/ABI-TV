package de.aniworld.tv.cast

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import de.aniworld.tv.AniWorldApp
import de.aniworld.tv.data.model.EpisodeItem
import de.aniworld.tv.data.model.LanguageOption
import de.aniworld.tv.platform.SharedPrefsStore
import de.aniworld.tv.ui.player.PlayerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object TvCastManager : CastHost {

    private const val TAG = "TvCastManager"
    const val HTTP_PORT = 8765
    const val UDP_PORT = 8766

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _castStatus = MutableStateFlow(CastStatus())
    override val castStatus: StateFlow<CastStatus> = _castStatus.asStateFlow()

    // Steuerungskanal für den aktiven Player
    var activePlayerControl: ((action: String, arg: Long) -> Unit)? = null
    var activePlayerPlayCommand: ((cmd: PlayCommand) -> Unit)? = null

    override val deviceModel: String
        get() = Build.MODEL

    override val deviceName: String
        get() {
            val model = Build.MODEL.trim()
            val product = Build.PRODUCT.trim()
            return when {
                model.contains("AFT", ignoreCase = true) -> "Fire TV Stick"
                model.isNotBlank() -> "TV ($model)"
                product.isNotBlank() -> "TV ($product)"
                else -> "ABI TV"
            }
        }

    fun start(context: Context) {
        scope.launch {
            try {
                CastPairing.init(SharedPrefsStore(context, CastPairing.PREFS_NAME))
                CastServer.start(HTTP_PORT, UDP_PORT, this@TvCastManager)
                NsdHelper.register(context, deviceName, HTTP_PORT)
                Log.i(TAG, "TvCastManager gestartet auf Port $HTTP_PORT für '$deviceName'")
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Starten des TvCastManager", e)
            }
        }
    }

    fun stop(context: Context) {
        try {
            NsdHelper.unregister(context)
            CastServer.stop()
            Log.i(TAG, "TvCastManager gestoppt")
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Stoppen des TvCastManager", e)
        }
    }

    fun updatePlaybackStatus(
        isPlaying: Boolean,
        title: String,
        episodeTitle: String,
        seasonLabel: String,
        episodeNumber: Int,
        positionMs: Long,
        durationMs: Long
    ) {
        _castStatus.value = CastStatus(
            isPlaying = isPlaying,
            title = title,
            episodeTitle = episodeTitle,
            seasonLabel = seasonLabel,
            episodeNumber = episodeNumber,
            positionMs = positionMs,
            durationMs = durationMs
        )
    }

    fun resetPlaybackStatus() {
        _castStatus.value = CastStatus()
    }

    /**
     * Startet den Player auf dem Fernseher mit den vom Smartphone übergebenen Parametern.
     */
    override fun handlePlayCommand(cmd: PlayCommand) {
        scope.launch(Dispatchers.Main) {
            val directHandler = activePlayerPlayCommand
            if (directHandler != null) {
                Log.i(TAG, "Direkte Uebergabe an laufenden Player: ${cmd.animeTitle} - ${cmd.titleDe}")
                directHandler.invoke(cmd)
                try {
                    val bringToFrontIntent = Intent(AniWorldApp.instance, PlayerActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    }
                    AniWorldApp.instance.startActivity(bringToFrontIntent)
                } catch (e: Exception) {
                    Log.w(TAG, "Konnte PlayerActivity nicht in den Vordergrund bringen", e)
                }
                return@launch
            }

            val ep = EpisodeItem(
                number = cmd.episodeNumber,
                seasonLabel = cmd.seasonLabel,
                path = cmd.episodePath,
                titleDe = cmd.titleDe.ifBlank { "Folge ${cmd.episodeNumber}" },
                titleEn = "",
                languages = listOfNotNull(LanguageOption.fromKey(cmd.preferredLangKey))
            )

            val intent = PlayerActivity.intent(
                context = AniWorldApp.instance,
                animeSlug = cmd.animeSlug,
                animeTitle = cmd.animeTitle,
                coverUrl = cmd.coverUrl,
                episode = ep,
                seasonEpisodes = arrayListOf(ep),
                preferredLangKey = cmd.preferredLangKey
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

            AniWorldApp.instance.startActivity(intent)
            Log.i(TAG, "Play-Befehl per Intent gestartet: ${cmd.animeTitle} - ${cmd.titleDe}")
        }
    }

    /** Startet einen Live-Sender vom Handy; ein laufender Player übernimmt ihn per onNewIntent. */
    override fun handleLiveCommand(channelName: String, streamUrl: String, logoUrl: String, headers: Map<String, String>) {
        scope.launch(Dispatchers.Main) {
            val intent = PlayerActivity.liveIntent(
                context = AniWorldApp.instance,
                channelName = channelName,
                streamUrl = streamUrl,
                logoUrl = logoUrl,
                headers = HashMap(headers)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            AniWorldApp.instance.startActivity(intent)
            Log.i(TAG, "Live-Sender vom Handy gestartet: $channelName")
        }
    }

    override fun handleControlCommand(action: String, arg: Long): Boolean {
        val control = activePlayerControl
        if (control != null) {
            scope.launch(Dispatchers.Main) {
                control.invoke(action, arg)
            }
            return true
        }
        return false
    }
}
