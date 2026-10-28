package de.aniworld.tv.data.remote

import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.model.LiveTvCategory
import de.aniworld.tv.data.model.LiveTvChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

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
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/z/z7/ProSieben_Logo_2015.svg/512px-ProSieben_Logo_2015.svg.png",
            streamUrl = "https://dash4.antik.sk/live/test_pro_sieben_tizen/playlist.m3u8",
            category = "main",
            qualityBadge = "1080p"
        ),
        LiveTvChannel(
            id = "sat1",
            name = "SAT.1",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6f/Sat.1_Logo_2022.svg/512px-Sat.1_Logo_2022.svg.png",
            streamUrl = "https://dash4.antik.sk/live/test_sat_eins_tizen/playlist.m3u8",
            category = "main",
            qualityBadge = "1080p"
        ),
        LiveTvChannel(
            id = "rtl",
            name = "RTL",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/66/RTL_Group_2021_Logo.svg/512px-RTL_Group_2021_Logo.svg.png",
            streamUrl = "https://dash4.antik.sk/live/test_rtl_ger_tizen/playlist.m3u8",
            category = "main",
            qualityBadge = "720p"
        ),
        LiveTvChannel(
            id = "vox",
            name = "VOX",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4b/VOX_Logo_2020.svg/512px-VOX_Logo_2020.svg.png",
            streamUrl = "https://dash4.antik.sk/live/test_vox_tizen/playlist.m3u8",
            category = "main",
            qualityBadge = "720p"
        ),
        LiveTvChannel(
            id = "kabeleins",
            name = "kabel eins",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a2/Kabel_Eins_Logo_2015.svg/512px-Kabel_Eins_Logo_2015.svg.png",
            streamUrl = "https://dash4.antik.sk/live/test_kabel_eins_tizen/playlist.m3u8",
            category = "main",
            qualityBadge = "720p"
        ),
        LiveTvChannel(
            id = "rtl2",
            name = "RTL Zwei",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f1/RTLZWEI_Logo_2019.svg/512px-RTLZWEI_Logo_2019.svg.png",
            streamUrl = "https://dash4.antik.sk/live/test_rtl_zwei_tizen/playlist.m3u8",
            category = "main",
            qualityBadge = "720p"
        ),
        LiveTvChannel(
            id = "superrtl",
            name = "Super RTL",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cd/Super_RTL_Logo_2019.svg/512px-Super_RTL_Logo_2019.svg.png",
            streamUrl = "https://dash4.antik.sk/live/test_super_rtl_tizen/playlist.m3u8",
            category = "main",
            qualityBadge = "720p"
        ),
        LiveTvChannel(
            id = "nitro",
            name = "Nitro",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/87/Nitro_Logo.svg/512px-Nitro_Logo.svg.png",
            streamUrl = "https://dash4.antik.sk/live/test_rtl_nitro_tizen/playlist.m3u8",
            category = "main",
            qualityBadge = "720p"
        ),
        LiveTvChannel(
            id = "prosieben_maxx",
            name = "ProSieben MAXX",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e2/ProSieben_MAXX_Logo.svg/512px-ProSieben_MAXX_Logo.svg.png",
            streamUrl = "https://s6.hopslan.com/pro7maxxX/index.m3u8",
            category = "main",
            qualityBadge = "HD"
        ),
        LiveTvChannel(
            id = "sixx",
            name = "sixx",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/12/Sixx_Logo.svg/512px-Sixx_Logo.svg.png",
            streamUrl = "https://dash4.antik.sk/live/test_sixx_tizen/playlist.m3u8",
            category = "main",
            qualityBadge = "720p"
        ),
        LiveTvChannel(
            id = "sat1_gold",
            name = "SAT.1 Gold",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6d/Sat.1_Gold_Logo_2019.svg/512px-Sat.1_Gold_Logo_2019.svg.png",
            streamUrl = "https://dash4.antik.sk/live/test_sat_eins_gold_tizen/playlist.m3u8",
            category = "main",
            qualityBadge = "576p"
        ),
        LiveTvChannel(
            id = "daserste",
            name = "Das Erste HD (ARD)",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/30/Das_Erste_Logo_2015.svg/512px-Das_Erste_Logo_2015.svg.png",
            streamUrl = "https://daserste-live.ard-mcdn.de/daserste/live/hls/de/master.m3u8",
            category = "main",
            qualityBadge = "1080p"
        ),
        LiveTvChannel(
            id = "zdf",
            name = "ZDF HD",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/ZDF_logo1.svg/512px-ZDF_logo1.svg.png",
            streamUrl = "https://zdf-hls-15.akamaized.net/hls/live/2016498/de/veryhigh/master.m3u8",
            category = "main",
            qualityBadge = "1080p"
        ),
        LiveTvChannel(
            id = "arte",
            name = "Arte HD",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/1/19/Arte_Logo_2017.svg/512px-Arte_Logo_2017.svg.png",
            streamUrl = "https://artesimulcast.akamaized.net/hls/live/2030993/artelive_de/master.m3u8",
            category = "main",
            qualityBadge = "1080p"
        ),
        LiveTvChannel(
            id = "drei_sat",
            name = "3sat HD",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/81/3sat_2019.svg/512px-3sat_2019.svg.png",
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
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b3/Tagesschau24_logo_2014.svg/512px-Tagesschau24_logo_2014.svg.png",
            streamUrl = "https://tagesschau.akamaized.net/hls/live/2020115/tagesschau/tagesschau_1/master.m3u8",
            category = "news",
            qualityBadge = "1080p"
        ),
        LiveTvChannel(
            id = "welt",
            name = "WELT Live",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d4/WELT_Logo_2018.svg/512px-WELT_Logo_2018.svg.png",
            streamUrl = "https://dash4.antik.sk/live/test_welt_tizen/playlist.m3u8",
            category = "news",
            qualityBadge = "720p"
        ),
        LiveTvChannel(
            id = "ntv",
            name = "n-tv Live",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f6/N-tv_logo-september2017.svg/512px-N-tv_logo-september2017.svg.png",
            streamUrl = "https://dash4.antik.sk/live/test_ntv_tizen/playlist.m3u8",
            category = "news",
            qualityBadge = "720p"
        ),
        LiveTvChannel(
            id = "zdfinfo",
            name = "ZDFinfo HD",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/ca/Zdfinfo_logo_2021.svg/512px-Zdfinfo_logo_2021.svg.png",
            streamUrl = "https://zdf-hls-17.akamaized.net/hls/live/2016500/de/veryhigh/master.m3u8",
            category = "news",
            qualityBadge = "1080p"
        ),
        LiveTvChannel(
            id = "zdfneo",
            name = "ZDFneo HD",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/8/87/ZDFneo_logo_2017.svg/512px-ZDFneo_logo_2017.svg.png",
            streamUrl = "https://zdf-hls-16.akamaized.net/hls/live/2016499/de/veryhigh/master.m3u8",
            category = "news",
            qualityBadge = "1080p"
        ),
        LiveTvChannel(
            id = "kika",
            name = "KiKA HD",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b3/KiKa_Logo_2012.svg/512px-KiKa_Logo_2012.svg.png",
            streamUrl = "https://dash4.antik.sk/live/test_kika_tizen/playlist.m3u8",
            category = "news",
            qualityBadge = "720p"
        ),
        LiveTvChannel(
            id = "wdr",
            name = "WDR Fernsehen HD",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e0/WDR_Fernsehen_Logo_2016.svg/512px-WDR_Fernsehen_Logo_2016.svg.png",
            streamUrl = "https://wdr-live.ard-mcdn.de/wdr/live/hls/de/master.m3u8",
            category = "news",
            qualityBadge = "1080p"
        ),
        LiveTvChannel(
            id = "swr",
            name = "SWR Fernsehen HD",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/2/26/SWR_Logo_2023.svg/512px-SWR_Logo_2023.svg.png",
            streamUrl = "https://dash4.antik.sk/live/test_swr_tizen/playlist.m3u8",
            category = "news",
            qualityBadge = "720p"
        ),

        // === ÖSTERREICH / F1 ===
        LiveTvChannel(
            id = "servustv_at",
            name = "ServusTV Österreich (F1 Live)",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/4/46/Servus_TV_Logo_2019.svg/512px-Servus_TV_Logo_2019.svg.png",
            streamUrl = "https://s6.hopslan.com/servuz1/index.m3u8",
            category = "at_ch",
            qualityBadge = "HD"
        ),
        LiveTvChannel(
            id = "orf1",
            name = "ORF 1 (F1 Live)",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4c/ORF1_Logo_2019.svg/512px-ORF1_Logo_2019.svg.png",
            streamUrl = "https://test02.mdn.ors.at/orf/orf1/qxa/manifest.m3u8",
            category = "at_ch",
            qualityBadge = "HD"
        )
    )

    suspend fun loadChannels(): List<LiveTvChannel> = withContext(Dispatchers.IO) {
        val list = PRESET_CHANNELS.toMutableList()
        val customUrl = LocalStorage.customM3uUrl.trim()
        if (customUrl.isNotBlank()) {
            val custom = runCatching { fetchM3u(customUrl) }.getOrDefault(emptyList())
            list.addAll(custom)
        }
        list
    }

    private fun fetchM3u(url: String): List<LiveTvChannel> {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", HttpClient.USER_AGENT)
            .build()
        val body = HttpClient.okHttp.newCall(req).execute().body?.string().orEmpty()
        return parseM3u(body)
    }

    fun parseM3u(content: String): List<LiveTvChannel> {
        val lines = content.lines()
        val channels = mutableListOf<LiveTvChannel>()
        var currentName = ""
        var currentLogo = ""
        var currentGroup = "custom"

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF")) {
                currentName = trimmed.substringAfterLast(",").trim()
                val logoMatch = Regex("""tvg-logo="([^"]+)"""").find(trimmed)
                currentLogo = logoMatch?.groupValues?.get(1).orEmpty()
                val groupMatch = Regex("""group-title="([^"]+)"""").find(trimmed)
                currentGroup = groupMatch?.groupValues?.get(1)?.lowercase() ?: "custom"
            } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                if (currentName.isNotBlank()) {
                    val id = "custom_" + channels.size
                    channels.add(
                        LiveTvChannel(
                            id = id,
                            name = currentName,
                            logoUrl = currentLogo,
                            streamUrl = trimmed,
                            category = "custom",
                            qualityBadge = "M3U"
                        )
                    )
                    currentName = ""
                    currentLogo = ""
                }
            }
        }
        return channels
    }
}
