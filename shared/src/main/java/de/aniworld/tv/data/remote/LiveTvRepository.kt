package de.aniworld.tv.data.remote

import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.model.LiveTvCategory
import de.aniworld.tv.data.model.LiveTvChannel
import de.aniworld.tv.data.model.LiveTvLoadResult
import de.aniworld.tv.data.model.LiveTvNode
import de.aniworld.tv.data.model.M3uFolderFile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import de.aniworld.tv.platform.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.File
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
    private var customCache: CustomList? = null

    /** Geladene eigene Liste; [trees] nur, wenn sie Gruppen hat (IPTV: je Land ein Reiter). */
    private class CustomList(val url: String, val channels: List<LiveTvChannel>, val trees: List<LiveTvNode>)

    private val groupAttr = Regex("""group-title="([^"]*)"""", RegexOption.IGNORE_CASE)

    // Start-Vorladen und Live-TV-Bildschirm sollen die (große) Liste nicht doppelt laden
    private val loadMutex = Mutex()

    private val logoAttr = Regex("""tvg-logo="([^"]*)"""", RegexOption.IGNORE_CASE)
    private val nameAttr = Regex("""tvg-name="([^"]*)"""", RegexOption.IGNORE_CASE)
    private val nonHlsExtensions = listOf(".ts", ".mp4", ".mkv", ".mp3", ".aac", ".flv", ".mpd")

    // ------------------- Eigener Ordner mit M3U-Dateien -------------------

    /** Grenzen beim Lesen eines Ordners (gelten auch für den Android-Leser). */
    const val FOLDER_MAX_FILES = 500
    const val FOLDER_MAX_DEPTH = 8
    const val FOLDER_MAX_FILE_BYTES = 8L * 1024 * 1024

    private const val TAG = "LiveTvRepository"
    private const val FOLDER_PREFIX = "folder:"

    /**
     * Liest den eigenen M3U-Ordner samt Unterordnern. Standard (PC): normaler Dateipfad;
     * Android setzt einen Leser für die Ordner-Freigabe (content://…).
     */
    @Volatile
    var folderReader: (folder: String) -> List<M3uFolderFile> = ::readFolderFromDisk

    /** Reiter und Sender aus dem eigenen Ordner. */
    data class FolderChannels(
        val categories: List<LiveTvCategory>,
        val channels: List<LiveTvChannel>,
        val tree: LiveTvNode? = null
    )

    // Gemerkt, bis "Liste aktualisieren" oder ein anderer Ordner kommt
    @Volatile
    private var folderCache: Pair<String, FolderChannels>? = null

    suspend fun loadChannels(forceRefresh: Boolean = false): LiveTvLoadResult = withContext(Dispatchers.IO) {
        loadMutex.withLock {
            val errors = mutableListOf<String>()
            val custom = loadCustomList(forceRefresh, errors)
            val folder = loadFolder(forceRefresh, errors)
            LiveTvLoadResult(
                channels = PRESET_CHANNELS + (custom?.channels.orEmpty()) + folder.channels,
                customError = errors.joinToString(" ").ifBlank { null },
                categories = CATEGORIES + folder.categories,
                trees = custom?.trees.orEmpty() + listOfNotNull(folder.tree)
            )
        }
    }

    /** Ist die eigene Liste nach Ländern sortiert (dann zeigt der PC sie nur in deren Reitern)? */
    val isCustomListGrouped: Boolean get() = customCache?.trees?.isNotEmpty() == true

    // ------------------- Senderwahl im Player (TV) -------------------

    /** Ordner des zuletzt in Live TV gestarteten Senders (TV: Senderwahl im Player); null = keiner. */
    @Volatile
    var playFolderId: String? = null

    /** Kennung für die festen Reiter ("preset:main" …) als Ordner der Senderwahl. */
    const val PRESET_FOLDER_PREFIX = "preset:"

    /** Zuletzt geladene Bäume (eigene Liste und Ordner), für die Senderwahl im Player. */
    val cachedTrees: List<LiveTvNode>
        get() = customCache?.trees.orEmpty() + listOfNotNull(folderCache?.second?.tree)

    /**
     * Ordner der Senderwahl: ein Reiter der eigenen Listen oder ein fester Reiter ("preset:all" usw.).
     * Feste Reiter enthalten nur ihre Sender; eigene Listen ihre Unterordner und Sender.
     */
    fun findFolder(id: String): LiveTvNode? {
        if (id.startsWith(PRESET_FOLDER_PREFIX)) {
            val category = id.removePrefix(PRESET_FOLDER_PREFIX)
            val info = CATEGORIES.firstOrNull { it.id == category } ?: return null
            val channels = when (category) {
                "all" -> PRESET_CHANNELS
                "custom" -> customCache?.takeIf { it.trees.isEmpty() }?.channels.orEmpty()
                else -> PRESET_CHANNELS.filter { it.category == category }
            }
            return LiveTvNode(id = id, title = info.title, icon = info.icon, channels = channels)
        }
        fun search(nodes: List<LiveTvNode>): LiveTvNode? {
            for (node in nodes) {
                if (node.id == id) return node
                search(node.children)?.let { return it }
            }
            return null
        }
        return search(cachedTrees)
    }

    /** Übergeordneter Ordner (null = schon ganz oben). */
    fun parentFolder(id: String): LiveTvNode? {
        if (id.startsWith(PRESET_FOLDER_PREFIX)) return null
        fun search(parent: LiveTvNode?, nodes: List<LiveTvNode>): LiveTvNode? {
            for (node in nodes) {
                if (node.id == id) return parent
                search(node, node.children)?.let { return it }
            }
            return null
        }
        return search(null, cachedTrees)
    }

    private fun loadCustomList(forceRefresh: Boolean, errors: MutableList<String>): CustomList? {
        val customUrl = LocalStorage.customM3uUrl.trim()
        if (customUrl.isBlank()) return null

        val cached = customCache
        if (!forceRefresh && cached != null && cached.url == customUrl) return cached

        return try {
            val started = System.currentTimeMillis()
            val custom = fetchM3u(customUrl)
            val trees = if (LiveTvTree.hasGroups(custom)) LiveTvTree.buildGrouped(custom, "iptv:") else emptyList()
            AppLog.i(TAG, "Eigene Liste: ${custom.size} Einträge, ${trees.size} Länder (${System.currentTimeMillis() - started} ms)")
            val list = CustomList(customUrl, custom, trees)
            customCache = list
            if (custom.isEmpty()) errors += "Die eigene Liste enthält keine abspielbaren Sender."
            list
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errors += "Eigene Liste nicht erreichbar (${e.message ?: e.javaClass.simpleName})."
            // Kurzer Aussetzer beim Start: die zuletzt geladene Liste dieser Adresse weiter zeigen
            customCache?.takeIf { it.url == customUrl }
        }
    }

    private fun loadFolder(forceRefresh: Boolean, errors: MutableList<String>): FolderChannels {
        val folder = LocalStorage.m3uFolder.trim()
        if (folder.isBlank()) return FolderChannels(emptyList(), emptyList())

        val cached = folderCache
        if (!forceRefresh && cached != null && cached.first == folder) return cached.second

        return try {
            val files = folderReader(folder)
            val rootName = LocalStorage.m3uFolderName.ifBlank { "Eigener Ordner" }
            val result = buildFolderChannels(rootName, files).copy(
                tree = LiveTvTree.buildFolder(rootName, files, "folder-tree:").takeIf { it.total > 0 }
            )
            folderCache = folder to result
            if (files.isEmpty()) {
                errors += "Im Ordner wurden keine M3U-Dateien gefunden."
            } else if (result.channels.isEmpty()) {
                errors += "Die M3U-Dateien im Ordner enthalten keine abspielbaren Sender."
            }
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(TAG, "M3U-Ordner nicht lesbar", e)
            errors += "Ordner nicht lesbar (${e.message ?: e.javaClass.simpleName}) – bitte neu auswählen."
            FolderChannels(emptyList(), emptyList())
        }
    }

    /**
     * Je Ordner ein Reiter ("Sport › DAZN"), darin die Sender aller M3U-Dateien dieses Ordners.
     * Dateien direkt im gewählten Ordner landen im Reiter mit dessen Namen ([rootName]).
     */
    fun buildFolderChannels(rootName: String, files: List<M3uFolderFile>): FolderChannels {
        val categories = mutableListOf<LiveTvCategory>()
        val channels = mutableListOf<LiveTvChannel>()
        files.groupBy { it.dir.trim('/') }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .forEach { (dir, dirFiles) ->
                val categoryId = FOLDER_PREFIX + dir
                val before = channels.size
                dirFiles.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.fileName }).forEach { file ->
                    channels += parseM3u(
                        content = file.content,
                        category = categoryId,
                        idPrefix = "folder_${channels.size}_",
                        fallbackName = file.fileName.substringBeforeLast('.')
                    )
                }
                if (channels.size > before) {
                    val title = if (dir.isEmpty()) rootName else dir.split('/').joinToString(" › ")
                    categories += LiveTvCategory(categoryId, title, "📁")
                }
            }
        return FolderChannels(categories, channels)
    }

    /** Merkt sich den eigenen M3U-Ordner ([folder] leer = entfernen); [name] wird in der App angezeigt. */
    fun saveM3uFolder(folder: String, name: String) {
        LocalStorage.m3uFolder = folder.trim()
        LocalStorage.m3uFolderName = if (folder.isBlank()) "" else name.trim()
        folderCache = null
    }

    fun isM3uFileName(name: String): Boolean =
        name.endsWith(".m3u", ignoreCase = true) || name.endsWith(".m3u8", ignoreCase = true)

    /** PC: Ordner samt Unterordnern lesen (versteckte Dateien und Ordner ausgelassen). */
    private fun readFolderFromDisk(folder: String): List<M3uFolderFile> {
        val root = if (folder.startsWith("file:", true)) File(java.net.URI(folder)) else File(folder)
        if (!root.isDirectory) throw IOException("Ordner nicht gefunden: ${root.name.ifBlank { folder }}")
        val result = mutableListOf<M3uFolderFile>()
        fun walk(dir: File, relative: String, depth: Int) {
            if (depth > FOLDER_MAX_DEPTH || result.size >= FOLDER_MAX_FILES) return
            val entries = dir.listFiles()?.filterNot { it.name.startsWith(".") }?.sortedBy { it.name.lowercase() } ?: return
            entries.filter { it.isFile && isM3uFileName(it.name) && it.length() <= FOLDER_MAX_FILE_BYTES }.forEach { file ->
                if (result.size < FOLDER_MAX_FILES) {
                    result += M3uFolderFile(relative, file.name, file.readText(Charsets.UTF_8))
                }
            }
            entries.filter { it.isDirectory }.forEach { sub ->
                walk(sub, if (relative.isEmpty()) sub.name else "$relative/${sub.name}", depth + 1)
            }
        }
        walk(root, "", 0)
        return result
    }

    /**
     * Speichert die Adresse der eigenen M3U-Liste (leer = entfernen).
     * Ergänzt ein fehlendes http:// und gibt false zurück, wenn es keine gültige Adresse ist.
     */
    fun saveCustomM3uUrl(input: String): Boolean {
        val trimmed = input.trim()
        val normalized = when {
            trimmed.isEmpty() -> ""
            // Lokale Datei (PC: "Datei auswählen")
            trimmed.startsWith("file:", true) -> trimmed
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
            // IPTV-Server (Xtream): /benutzer/passwort/12345 liefert rohes MPEG-TS, kein HLS
            isXtreamLivePath(path) -> false
            else -> true
        }
    }

    private val xtreamLive = Regex("""^https?://[^/]+(/live)?/[^/]+/[^/]+/\d+(\.ts)?$""", RegexOption.IGNORE_CASE)

    private val credentialPath = Regex("""(https?://[^/]+/(?:live/|movie/|series/)?)[^/?#]+/[^/?#]+/(?=[^/?#]+$)""", RegexOption.IGNORE_CASE)
    private val credentialQuery = Regex("""(?i)((?:username|password|user|pass)=)[^&#]*""")

    /** Adresse ohne Zugangsdaten (für Protokolle und Anzeigen). */
    fun maskCredentials(url: String): String =
        url.replace(credentialPath, "$1***/***/").replace(credentialQuery, "$1***")

    /** Nur der Server einer Adresse, z. B. "iptv.example.com" (ohne Zugangsdaten). */
    fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: "eigener Server"

    /** Live-Sender eines IPTV-Servers im Xtream-Format (http://server:8080/benutzer/passwort/12345). */
    fun isXtreamLivePath(url: String): Boolean = xtreamLive.matches(url.substringBefore('?').substringBefore('#'))

    private fun fetchM3u(url: String): List<LiveTvChannel> {
        if (url.startsWith("file:", ignoreCase = true)) {
            val file = java.io.File(java.net.URI(url))
            if (!file.isFile) throw IOException("Datei nicht gefunden: ${file.name}")
            return parseM3u(file.readText(Charsets.UTF_8))
        }
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", HttpClient.USER_AGENT)
            .build()
        HttpClient.okHttp.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
            return parseM3u(res.body?.string().orEmpty())
        }
    }

    /**
     * Sender aus einer M3U-Liste. [fallbackName] benennt Adressen ohne #EXTINF-Zeile
     * (z. B. Dateien mit nur einem Sender, die nach ihm benannt sind).
     */
    fun parseM3u(
        content: String,
        category: String = "custom",
        idPrefix: String = "custom_",
        fallbackName: String = ""
    ): List<LiveTvChannel> {
        val channels = mutableListOf<LiveTvChannel>()
        var currentName = ""
        var currentLogo = ""
        var currentGroup = ""
        val currentHeaders = mutableMapOf<String, String>()

        for (line in content.lineSequence()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("#EXTINF", ignoreCase = true) -> {
                    currentName = extinfTitle(trimmed).ifBlank {
                        nameAttr.find(trimmed)?.groupValues?.get(1).orEmpty()
                    }
                    currentLogo = logoAttr.find(trimmed)?.groupValues?.get(1).orEmpty()
                    currentGroup = groupAttr.find(trimmed)?.groupValues?.get(1).orEmpty()
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
                    val name = currentName.ifBlank {
                        when {
                            fallbackName.isBlank() -> ""
                            channels.none { it.name == fallbackName } -> fallbackName
                            else -> "$fallbackName ${channels.size + 1}"
                        }
                    }
                    if (name.isNotBlank()) {
                        channels.add(
                            LiveTvChannel(
                                id = idPrefix + channels.size,
                                name = name,
                                logoUrl = currentLogo,
                                streamUrl = trimmed,
                                category = category,
                                qualityBadge = "M3U",
                                headers = HashMap(currentHeaders),
                                group = currentGroup
                            )
                        )
                    }
                    currentName = ""
                    currentLogo = ""
                    currentGroup = ""
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
