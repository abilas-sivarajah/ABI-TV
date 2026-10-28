package de.aniworld.tv.data.remote

import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.model.LiveTvCategory
import de.aniworld.tv.data.model.LiveTvChannel
import de.aniworld.tv.data.model.LiveTvLoadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.IOException

object LiveTvRepository {

    val CATEGORIES = listOf(
        LiveTvCategory("all", "Alle Sender", "📺"),
        LiveTvCategory("main", "Hauptsender", "⭐"),
        LiveTvCategory("sport", "Sport & Racing", "🏎️"),
        LiveTvCategory("news", "News & Dokus", "📰"),
        LiveTvCategory("at_ch", "Österreich / F1", "🇦🇹"),
        LiveTvCategory("custom", "Eigene M3U", "📂")
    )

    val PRESET_CHANNELS = listOf(
        // === HAUPTSENDER & PRIVAT ===
        LiveTvChannel(
            id = "prosieben",
            name = "ProSieben",
            logoUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/germany/pro-sieben-de.png",
            streamUrl = "https://dash4.antik.sk/live/test_pro_sieben_tizen/playlist.m3u8",
            category = "main",
            qualityBadge = "1080p"
        ),
        LiveTvChannel(
            id = "sat1",
            name = "SAT.1",
            logoUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/germany/sat-1-de.png",
            streamUrl = "https://dash4.antik.sk/live/test_sat_eins_tizen/playlist.m3u8",
            category = "main",
            qualityBadge = "1080p"
        ),
        LiveTvChannel(
            id = "rtl",
            name = "RTL",
            logoUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/germany/rtl-de.png",
            streamUrl = "https://dash4.antik.sk/live/test_rtl_ger_tizen/playlist.m3u8",
            category = "main",
            qualityBadge = "720p"
        ),
        LiveTvChannel(
            id = "vox",
            name = "VOX",
            logoUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/germany/vox-de.png",
            streamUrl = "https://dash4.antik.sk/live/test_vox_tizen/playlist.m3u8",
            category = "main",
            qualityBadge = "720p"
        ),
        LiveTvChannel(
            id = "kabeleins",
            name = "kabel eins",
            logoUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/germany/kabel-eins-de.png",
            streamUrl = "https://dash4.antik.sk/live/test_kabel_eins_tizen/playlist.m3u8",
            category = "main",
            qualityBadge = "720p"
        ),
        LiveTvChannel(
            id = "rtl2",
            name = "RTL Zwei",
            logoUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/germany/rtl-zwei-de.png",
            streamUrl = "https://dash4.antik.sk/live/test_rtl_zwei_tizen/playlist.m3u8",
            category = "main",
            qualityBadge = "720p"
        ),
        LiveTvChannel(
            id = "superrtl",
            name = "Super RTL",
            logoUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/germany/rtl-super-de.png",
            streamUrl = "https://dash4.antik.sk/live/test_super_rtl_tizen/playlist.m3u8",
            category = "main",
            qualityBadge = "720p"
        ),
        LiveTvChannel(
            id = "nitro",
            name = "Nitro",
            logoUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/germany/nitro-de.png",
            streamUrl = "https://dash4.antik.sk/live/test_rtl_nitro_tizen/playlist.m3u8",
            category = "main",
            qualityBadge = "720p"
        ),
        LiveTvChannel(
            id = "prosieben_maxx",
            name = "ProSieben MAXX",
            logoUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/germany/pro-sieben-maxx-de.png",
            streamUrl = "https://s6.hopslan.com/pro7maxxX/index.m3u8",
            category = "main",
            qualityBadge = "HD"
        ),
        LiveTvChannel(
            id = "sixx",
            name = "sixx",
            logoUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/germany/sixx-de.png",
            streamUrl = "https://dash4.antik.sk/live/test_sixx_tizen/playlist.m3u8",
            category = "main",
            qualityBadge = "720p"
        ),
        LiveTvChannel(
            id = "sat1_gold",
            name = "SAT.1 Gold",
            logoUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/germany/sat-1-gold-de.png",
            streamUrl = "https://dash4.antik.sk/live/test_sat_eins_gold_tizen/playlist.m3u8",
            category = "main",
            qualityBadge = "576p"
        ),
        LiveTvChannel(
            id = "daserste",
            name = "Das Erste HD (ARD)",
            logoUrl = "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/daserstehd.png",
            streamUrl = "https://daserste-live.ard-mcdn.de/daserste/live/hls/de/master.m3u8",
            category = "main",
            qualityBadge = "1080p"
        ),
        LiveTvChannel(
            id = "zdf",
            name = "ZDF HD",
            logoUrl = "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/zdfhd.png",
            streamUrl = "https://zdf-hls-15.akamaized.net/hls/live/2016498/de/veryhigh/master.m3u8",
            category = "main",
            qualityBadge = "1080p"
        ),
        LiveTvChannel(
            id = "arte",
            name = "Arte HD",
            logoUrl = "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/artehd.png",
            streamUrl = "https://artesimulcast.akamaized.net/hls/live/2030993/artelive_de/master.m3u8",
            category = "main",
            qualityBadge = "1080p"
        ),
        LiveTvChannel(
            id = "drei_sat",
            name = "3sat HD",
            logoUrl = "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/3sat.png",
            streamUrl = "https://zdf-hls-18.akamaized.net/hls/live/2016501/dach/veryhigh/master.m3u8",
            category = "main",
            qualityBadge = "1080p"
        ),

        // === SPORT & RACING ===
        LiveTvChannel(
            id = "df1",
            name = "DF1 HD (DAZN & Motorsport)",
            logoUrl = "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/df1.png",
            streamUrl = "https://d287776cjxyfeq.cloudfront.net/out/v1/08823cd7fba74508a5a577fd51dc41f6/index.m3u8",
            category = "sport",
            qualityBadge = "Full HD"
        ),
        LiveTvChannel(
            id = "redbull_tv",
            name = "Red Bull TV (Motorsport & WRC)",
            logoUrl = "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/redbulltv.png",
            streamUrl = "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8",
            category = "sport",
            qualityBadge = "1080p"
        ),
        LiveTvChannel(
            id = "more_than_sports",
            name = "More Than Sports TV",
            logoUrl = "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/morethansports.png",
            streamUrl = "https://mts1.iptv-playoutcenter.de/mts/mts-web/playlist.m3u8",
            category = "sport",
            qualityBadge = "1080p"
        ),
        LiveTvChannel(
            id = "dazn_combat",
            name = "DAZN Combat (Kampfsport)",
            logoUrl = "https://i.postimg.cc/VsW3Jsrz/logo-DAZN-Combat.png",
            streamUrl = "https://jmp2.uk/plu-64d626ac9b414d000820e2fc.m3u8",
            category = "sport",
            qualityBadge = "HD"
        ),
        LiveTvChannel(
            id = "dazn_darts",
            name = "DAZN Darts x Pluto TV",
            logoUrl = "https://images.pluto.tv/channels/64b67f0424ade50008a3be17/colorLogoPNG.png",
            streamUrl = "https://jmp2.uk/plu-64b67f0424ade50008a3be17.m3u8",
            category = "sport",
            qualityBadge = "HD"
        ),
        LiveTvChannel(
            id = "dazn_heldinnen",
            name = "DAZN Heldinnen",
            logoUrl = "https://images.pluto.tv/channels/64afe50c5dc16600087f3227/colorLogoPNG.png",
            streamUrl = "https://jmp2.uk/plu-64afe50c5dc16600087f3227.m3u8",
            category = "sport",
            qualityBadge = "HD"
        ),
        LiveTvChannel(
            id = "mav_tv",
            name = "MavTV Motorsports",
            logoUrl = "https://upload.wikimedia.org/wikipedia/en/7/74/Racer_Network.jpg",
            streamUrl = "https://amg00378-mavtv-amg00378c2-rakuten-us-1048.playouts.now.amagi.tv/playlist/amg00378-mavtvfast-motorsportsnetwork-rakutenus/playlist.m3u8",
            category = "sport",
            qualityBadge = "1080p"
        ),
        LiveTvChannel(
            id = "flo_racing",
            name = "FloRacing Worldwide",
            logoUrl = "https://images.fubo.tv/channel-config-ui/station-logos/on-dark/floracing-white.png",
            streamUrl = "https://amg02278-amg02278c1-flosports-worldwide-7592.playouts.now.amagi.tv/playlist.m3u8",
            category = "sport",
            qualityBadge = "1080p"
        ),
        LiveTvChannel(
            id = "cbs_golazo",
            name = "CBS Sports Golazo (Fußball)",
            logoUrl = "https://i.imgur.com/eMjutHS.png",
            streamUrl = "https://dai.google.com/linear/hls/event/7f3Wv6f7QEKfQna22jHqLQ/master.m3u8",
            category = "sport",
            qualityBadge = "720p"
        ),
        LiveTvChannel(
            id = "bein_xtra",
            name = "beIN SPORTS XTRA",
            logoUrl = "https://i.ibb.co/HT49GPmB/XTRA-2.png",
            streamUrl = "https://bein-xtra-bein.amagi.tv/playlist.m3u8",
            category = "sport",
            qualityBadge = "1080p"
        ),

        // === NACHRICHTEN & DOKUS ===
        LiveTvChannel(
            id = "tagesschau24",
            name = "tagesschau24 HD",
            logoUrl = "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/tagesschau24.png",
            streamUrl = "https://tagesschau.akamaized.net/hls/live/2020115/tagesschau/tagesschau_1/master.m3u8",
            category = "news",
            qualityBadge = "1080p"
        ),
        LiveTvChannel(
            id = "welt",
            name = "WELT Live",
            logoUrl = "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/welt.png",
            streamUrl = "https://dash4.antik.sk/live/test_welt_tizen/playlist.m3u8",
            category = "news",
            qualityBadge = "720p"
        ),
        LiveTvChannel(
            id = "ntv",
            name = "n-tv Live",
            logoUrl = "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/ntv.png",
            streamUrl = "https://dash4.antik.sk/live/test_ntv_tizen/playlist.m3u8",
            category = "news",
            qualityBadge = "720p"
        ),
        LiveTvChannel(
            id = "zdfinfo",
            name = "ZDFinfo HD",
            logoUrl = "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/zdfinfohd.png",
            streamUrl = "https://zdf-hls-17.akamaized.net/hls/live/2016500/de/veryhigh/master.m3u8",
            category = "news",
            qualityBadge = "1080p"
        ),
        LiveTvChannel(
            id = "zdfneo",
            name = "ZDFneo HD",
            logoUrl = "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/zdfneohd.png",
            streamUrl = "https://zdf-hls-16.akamaized.net/hls/live/2016499/de/veryhigh/master.m3u8",
            category = "news",
            qualityBadge = "1080p"
        ),
        LiveTvChannel(
            id = "kika",
            name = "KiKA HD",
            logoUrl = "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/kika.png",
            streamUrl = "https://dash4.antik.sk/live/test_kika_tizen/playlist.m3u8",
            category = "news",
            qualityBadge = "720p"
        ),
        LiveTvChannel(
            id = "wdr",
            name = "WDR Fernsehen HD",
            logoUrl = "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/wdrhd.png",
            streamUrl = "https://wdr-live.ard-mcdn.de/wdr/live/hls/de/master.m3u8",
            category = "news",
            qualityBadge = "1080p"
        ),
        LiveTvChannel(
            id = "swr",
            name = "SWR Fernsehen HD",
            logoUrl = "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/swrhd.png",
            streamUrl = "https://dash4.antik.sk/live/test_swr_tizen/playlist.m3u8",
            category = "news",
            qualityBadge = "720p"
        ),

        // === ÖSTERREICH / F1 ===
        LiveTvChannel(
            id = "servustv_at",
            name = "ServusTV Österreich (F1 Live)",
            logoUrl = "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/servustvhd.png",
            streamUrl = "https://s6.hopslan.com/servuz1/index.m3u8",
            category = "at_ch",
            qualityBadge = "HD"
        ),
        LiveTvChannel(
            id = "orf1",
            name = "ORF 1 (F1 Live)",
            logoUrl = "https://raw.githubusercontent.com/jnk22/kodinerds-iptv/master/logos/tv/orf1hd.png",
            streamUrl = "https://test02.mdn.ors.at/orf/orf1/qxa/manifest.m3u8",
            category = "at_ch",
            qualityBadge = "HD"
        )
    )

    // Eigene M3U-Liste nur einmal pro Adresse laden; "Liste aktualisieren" erzwingt ein Neuladen
    @Volatile
    private var customCache: Pair<String, List<LiveTvChannel>>? = null

    private val logoAttr = Regex("""tvg-logo="([^"]*)"""", RegexOption.IGNORE_CASE)
    private val nameAttr = Regex("""tvg-name="([^"]*)"""", RegexOption.IGNORE_CASE)
    private val nonHlsExtensions = listOf(".ts", ".mp4", ".mkv", ".mp3", ".aac", ".flv", ".mpd")

    suspend fun loadChannels(forceRefresh: Boolean = false): LiveTvLoadResult = withContext(Dispatchers.IO) {
        val customUrl = LocalStorage.customM3uUrl.trim()
        if (customUrl.isBlank()) return@withContext LiveTvLoadResult(PRESET_CHANNELS)

        val cached = customCache
        if (!forceRefresh && cached != null && cached.first == customUrl) {
            return@withContext LiveTvLoadResult(PRESET_CHANNELS + cached.second)
        }

        try {
            val custom = fetchM3u(customUrl)
            customCache = customUrl to custom
            LiveTvLoadResult(
                channels = PRESET_CHANNELS + custom,
                customError = if (custom.isEmpty()) "Die eigene Liste enthält keine abspielbaren Sender." else null
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LiveTvLoadResult(
                channels = PRESET_CHANNELS,
                customError = "Eigene Liste nicht erreichbar (${e.message ?: e.javaClass.simpleName})."
            )
        }
    }

    /**
     * Speichert die Adresse der eigenen M3U-Liste (leer = entfernen).
     * Ergänzt ein fehlendes http:// und gibt false zurück, wenn es keine gültige Adresse ist.
     */
    fun saveCustomM3uUrl(input: String): Boolean {
        val trimmed = input.trim()
        val normalized = when {
            trimmed.isEmpty() -> ""
            trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) ->
                trimmed.toHttpUrlOrNull()?.toString() ?: return false
            else -> "http://$trimmed".toHttpUrlOrNull()?.toString() ?: return false
        }
        LocalStorage.customM3uUrl = normalized
        customCache = null
        return true
    }

    /** HLS erkennen; MPEG-TS-, MP4- oder DASH-Streams aus IPTV-Listen dürfen nicht als HLS geöffnet werden. */
    fun isLikelyHls(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            path.endsWith(".m3u8") || path.endsWith(".m3u") -> true
            nonHlsExtensions.any { path.endsWith(it) } -> false
            else -> true
        }
    }

    private fun fetchM3u(url: String): List<LiveTvChannel> {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", HttpClient.USER_AGENT)
            .build()
        HttpClient.okHttp.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
            return parseM3u(res.body?.string().orEmpty())
        }
    }

    fun parseM3u(content: String): List<LiveTvChannel> {
        val channels = mutableListOf<LiveTvChannel>()
        var currentName = ""
        var currentLogo = ""
        val currentHeaders = mutableMapOf<String, String>()

        for (line in content.lineSequence()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("#EXTINF", ignoreCase = true) -> {
                    currentName = extinfTitle(trimmed).ifBlank {
                        nameAttr.find(trimmed)?.groupValues?.get(1).orEmpty()
                    }
                    currentLogo = logoAttr.find(trimmed)?.groupValues?.get(1).orEmpty()
                    currentHeaders.clear()
                }

                // VLC-Optionen: manche Sender liefern nur mit passendem User-Agent/Referer
                trimmed.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val option = trimmed.substringAfter(':')
                    val value = option.substringAfter('=', "").trim()
                    if (value.isNotEmpty()) {
                        when (option.substringBefore('=').trim().lowercase()) {
                            "http-user-agent" -> currentHeaders["User-Agent"] = value
                            "http-referrer", "http-referer" -> currentHeaders["Referer"] = value
                            "http-origin" -> currentHeaders["Origin"] = value
                        }
                    }
                }

                trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> {
                    if (currentName.isNotBlank()) {
                        channels.add(
                            LiveTvChannel(
                                id = "custom_" + channels.size,
                                name = currentName,
                                logoUrl = currentLogo,
                                streamUrl = trimmed,
                                category = "custom",
                                qualityBadge = "M3U",
                                headers = HashMap(currentHeaders)
                            )
                        )
                    }
                    currentName = ""
                    currentLogo = ""
                    currentHeaders.clear()
                }
            }
        }
        return channels
    }

    /** Sendername nach dem ersten Komma außerhalb von Anführungszeichen (Attribute dürfen Kommas enthalten). */
    private fun extinfTitle(line: String): String {
        var inQuotes = false
        line.forEachIndexed { i, c ->
            when (c) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) return line.substring(i + 1).trim()
            }
        }
        return ""
    }
}
