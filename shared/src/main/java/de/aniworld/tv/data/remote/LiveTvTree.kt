package de.aniworld.tv.data.remote

import de.aniworld.tv.data.model.LiveTvChannel
import de.aniworld.tv.data.model.LiveTvNode
import de.aniworld.tv.data.model.M3uFolderFile

/**
 * Baut aus eigenen M3U-Listen Reiter mit Unterreitern:
 * - IPTV-Liste mit Gruppen (group-title): je Land ein Reiter (Türkiye vor Deutsch), darin die
 *   Sendergruppen in Listenreihenfolge, dann "Filme (VOD)" und "Serien" (Serien › Show › Staffel).
 * - Eigener Ordner: Ordnerstruktur wie auf der Platte.
 */
object LiveTvTree {

    private enum class Kind { LIVE, MOVIE, SERIES }

    private data class Country(val key: String, val title: String, val icon: String, val order: Int)

    private val liveCode = Regex("""^([A-Za-z]{2})(?=[\s:|]|$)""")
    private val vodCode = Regex("""^\(\s*[VS]\s*\|\s*([A-Za-z]{2})\s*\)""")
    private val vodPrefix = Regex("""^\(\s*[VS]\s*\|\s*[A-Za-z]{2}\s*\)\s*""")
    private val namePrefix = Regex("""^(\([A-Za-z]{2}-?\)|[A-Za-z]{2}:)\s*""")
    private val episodePattern = Regex("""^(.*?)\s*[Ss](\d{1,3})\s*[Ee](\d{1,4})\b(.*)$""")
    /** Trennzeilen in IPTV-Listen ("----- DEUTSCHLAND -----"), kein echter Sender. */
    private val separatorName = Regex("""^\s*[-=#*~_]{3,}.*[-=#*~_]{3,}\s*$""")
    private val vodExtensions = listOf(".mkv", ".mp4", ".avi", ".m4v", ".mov", ".wmv", ".flv", ".mpg", ".mpeg")

    /** Hat die Liste genug Gruppen, um sie nach Ländern und Gruppen zu sortieren? */
    fun hasGroups(channels: List<LiveTvChannel>): Boolean =
        channels.isNotEmpty() && channels.count { it.group.isNotBlank() } * 2 >= channels.size

    /** Film oder Serienfolge (zum Spulen), kein Live-Sender? */
    fun isVod(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return "/movie/" in path || "/series/" in path || vodExtensions.any { path.endsWith(it) }
    }

    private fun kindOf(channel: LiveTvChannel): Kind {
        val path = channel.streamUrl.substringBefore('?').lowercase()
        return when {
            "/series/" in path -> Kind.SERIES
            "/movie/" in path -> Kind.MOVIE
            channel.group.trim().startsWith("(S|", ignoreCase = true) -> Kind.SERIES
            vodExtensions.any { path.endsWith(it) } -> Kind.MOVIE
            else -> Kind.LIVE
        }
    }

    private fun countryOf(channel: LiveTvChannel): Country {
        val group = channel.group.trim()
        val code = (vodCode.find(group)?.groupValues?.get(1)
            ?: liveCode.find(group)?.groupValues?.get(1)
            ?: namePrefix.find(channel.name)?.value?.filter { it.isLetter() }?.take(2))
            ?.uppercase()
        return when (code) {
            "TR" -> Country("TR", "Türkiye", "🇹🇷", 0)
            "DE", "AT", "CH" -> Country("DE", "Deutsch", "🇩🇪", 1)
            null, "" -> Country("XX", "Weitere", "🌐", 99)
            else -> Country(code, code, "🌐", 50)
        }
    }

    private fun cleanName(name: String): String = name.replace(namePrefix, "").trim().ifBlank { name }

    /** IPTV-Liste nach Land › Gruppe › (Filme/Serien) sortieren; [idPrefix] macht die Reiter eindeutig. */
    fun buildGrouped(channels: List<LiveTvChannel>, idPrefix: String): List<LiveTvNode> {
        // Reihenfolge wie in der Liste: Land und Gruppe in der Reihenfolge ihres ersten Auftretens
        val countries = LinkedHashMap<String, Country>()
        val live = LinkedHashMap<String, LinkedHashMap<String, MutableList<LiveTvChannel>>>()
        val movies = LinkedHashMap<String, LinkedHashMap<String, MutableList<LiveTvChannel>>>()
        val series = LinkedHashMap<String, LinkedHashMap<String, MutableList<LiveTvChannel>>>()
        for (channel in channels) {
            if (separatorName.matches(channel.name) || separatorName.matches(cleanName(channel.name))) continue
            val country = countryOf(channel)
            countries.putIfAbsent(country.key, country)
            val group = channel.group.trim().ifBlank { "Ohne Gruppe" }
            val target = when (kindOf(channel)) {
                Kind.LIVE -> live
                Kind.MOVIE -> movies
                Kind.SERIES -> series
            }
            target.getOrPut(country.key) { LinkedHashMap() }.getOrPut(group) { mutableListOf() } += channel
        }

        return countries.values.sortedBy { it.order }.map { country ->
            val base = "$idPrefix${country.key}"
            val children = mutableListOf<LiveTvNode>()
            live[country.key]?.forEach { (group, list) ->
                children += LiveTvNode(
                    id = "$base/live/$group",
                    title = group,
                    channels = list.map { it.copy(name = cleanName(it.name)) }
                )
            }
            movies[country.key]?.let { groups ->
                children += LiveTvNode(
                    id = "$base/movies",
                    title = "Filme (VOD)",
                    icon = "🎬",
                    children = groups.map { (group, list) ->
                        LiveTvNode(
                            id = "$base/movies/$group",
                            title = group.replace(vodPrefix, "").ifBlank { group },
                            channels = list.map { it.copy(name = cleanName(it.name)) }
                        )
                    }
                )
            }
            series[country.key]?.let { groups ->
                children += LiveTvNode(
                    id = "$base/series",
                    title = "Serien",
                    icon = "📺",
                    children = groups.map { (group, list) ->
                        val groupId = "$base/series/$group"
                        LiveTvNode(
                            id = groupId,
                            title = group.replace(vodPrefix, "").ifBlank { group },
                            children = buildShows(list, groupId)
                        )
                    }
                )
            }
            LiveTvNode(id = base, title = country.title, icon = country.icon, children = children)
        }
    }

    /** Serienfolgen nach Show (alphabetisch) und Staffel ordnen. */
    private fun buildShows(episodes: List<LiveTvChannel>, groupId: String): List<LiveTvNode> {
        data class Ep(val show: String, val season: Int, val episode: Int, val rest: String, val channel: LiveTvChannel)
        val parsed = episodes.map { channel ->
            val name = cleanName(channel.name)
            val m = episodePattern.find(name)
            if (m != null) {
                Ep(m.groupValues[1].trim().ifBlank { name }, m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.groupValues[4].trim(), channel)
            } else {
                Ep(name, 0, 0, "", channel)
            }
        }
        return parsed.groupBy { it.show }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .map { (show, eps) ->
                val showId = "$groupId/$show"
                fun episodeChannel(ep: Ep) = ep.channel.copy(
                    name = if (ep.season > 0) {
                        "S%02d E%02d".format(ep.season, ep.episode) + (if (ep.rest.isNotBlank()) " ${ep.rest}" else "")
                    } else {
                        ep.show
                    }
                )
                val bySeason = eps.sortedWith(compareBy({ it.season }, { it.episode })).groupBy { it.season }
                if (bySeason.size <= 1) {
                    LiveTvNode(id = showId, title = show, channels = eps.sortedWith(compareBy({ it.season }, { it.episode })).map(::episodeChannel))
                } else {
                    LiveTvNode(
                        id = showId,
                        title = show,
                        children = bySeason.map { (season, list) ->
                            LiveTvNode(
                                id = "$showId/$season",
                                title = if (season > 0) "Staffel $season" else "Weitere",
                                channels = list.map(::episodeChannel)
                            )
                        }
                    )
                }
            }
    }

    /**
     * Ordnerstruktur als Baum. Liegen in einem Ordner Dateien und Unterordner, kommen die Sender
     * der Dateien in einen eigenen Unterreiter, damit jeder Reiter entweder Unterreiter oder Sender hat.
     */
    fun buildFolder(rootName: String, files: List<M3uFolderFile>, idPrefix: String): LiveTvNode {
        class Dir(val name: String, val path: String) {
            val subdirs = sortedMapOf<String, Dir>(String.CASE_INSENSITIVE_ORDER)
            val channels = mutableListOf<LiveTvChannel>()
        }
        val root = Dir(rootName, "")
        var counter = 0
        files.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.dir + "/" + it.fileName }).forEach { file ->
            var dir = root
            file.dir.trim('/').split('/').filter { it.isNotBlank() }.forEach { part ->
                dir = dir.subdirs.getOrPut(part) { Dir(part, if (dir.path.isEmpty()) part else "${dir.path}/$part") }
            }
            val parsed = LiveTvRepository.parseM3u(
                content = file.content,
                category = "folder:${file.dir.trim('/')}",
                idPrefix = "tree_${counter}_",
                fallbackName = file.fileName.substringBeforeLast('.')
            )
            counter += parsed.size
            dir.channels += parsed
        }

        fun toNode(dir: Dir): LiveTvNode {
            val id = "$idPrefix${dir.path}"
            val subs = dir.subdirs.values.map(::toNode).filter { it.total > 0 }
            return when {
                subs.isEmpty() -> LiveTvNode(id = id, title = dir.name, channels = dir.channels)
                dir.channels.isEmpty() -> LiveTvNode(id = id, title = dir.name, children = subs)
                else -> LiveTvNode(
                    id = id,
                    title = dir.name,
                    children = listOf(LiveTvNode(id = "$id#files", title = "Alle in ${dir.name}", channels = dir.channels)) + subs
                )
            }
        }
        return toNode(root).copy(icon = "📁")
    }
}
