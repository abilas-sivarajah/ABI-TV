package de.aniworld.mobile.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.aniworld.mobile.cast.MobileCastManager
import de.aniworld.mobile.ui.theme.AccentCyan
import de.aniworld.mobile.ui.theme.BgElevated
import de.aniworld.mobile.ui.theme.BgPrimary
import de.aniworld.mobile.ui.theme.BgSurface
import de.aniworld.mobile.ui.theme.TextMuted
import de.aniworld.mobile.ui.theme.TextPrimary
import de.aniworld.mobile.ui.theme.TextSecondary
import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.model.LiveTvChannel
import de.aniworld.tv.data.model.LiveTvNode
import de.aniworld.tv.data.remote.LiveTvRepository
import de.aniworld.tv.platform.M3uFolderAndroid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LiveRed = Color(0xFFEF4444)
private const val CUSTOM_CATEGORY = "custom"
private const val TREE_PREFIX = "tree:"
/** Bis zu so vielen Unterreitern als Knöpfe, darüber als Liste (z. B. alle Serien einer Gruppe). */
private const val CHIP_LIMIT = 60
private const val SEARCH_NODE_LIMIT = 40
private const val SEARCH_CHANNEL_LIMIT = 300
private val EPISODE_NAME = Regex("""^S\d+ E\d+.*""")

/** Eine Ebene der Unterreiter: Knöpfe (wenige) oder "Zurück" (wenn aus einer Liste gewählt wurde). */
private sealed interface TreeLevel {
    val depth: Int
    data class Chips(override val depth: Int, val children: List<LiveTvNode>, val selectedId: String) : TreeLevel
    data class Back(override val depth: Int, val parent: LiveTvNode, val selected: LiveTvNode) : TreeLevel
}

private class TreeView(
    val levels: List<TreeLevel>,
    val list: List<LiveTvNode>?,
    val listDepth: Int,
    val leaf: LiveTvNode?,
    val trail: List<LiveTvNode>
)

private fun resolveTree(root: LiveTvNode, path: List<String>): TreeView {
    val levels = mutableListOf<TreeLevel>()
    val trail = mutableListOf(root)
    var node = root
    var depth = 0
    while (true) {
        val children = node.children
        if (children.isEmpty()) return TreeView(levels, null, depth, node, trail)
        val chosen = children.firstOrNull { it.id == path.getOrNull(depth) }
        if (children.size <= CHIP_LIMIT) {
            val selected = chosen ?: children.first()
            levels += TreeLevel.Chips(depth, children, selected.id)
            node = selected
        } else {
            if (chosen == null) return TreeView(levels, children, depth, null, trail)
            levels += TreeLevel.Back(depth, node, chosen)
            node = chosen
        }
        trail += node
        depth++
    }
}

/** Serienfolgen heißen im Raster nur "S01 E05": für Player und Suche den Serientitel davorsetzen. */
private fun namedChannel(channel: LiveTvChannel, trail: List<LiveTvNode>): LiveTvChannel {
    if (!EPISODE_NAME.matches(channel.name)) return channel
    val show = trail.lastOrNull { !it.title.startsWith("Staffel ") && it.title != "Weitere" } ?: return channel
    return channel.copy(name = "${show.title} · ${channel.name}")
}

private class SearchHit(val channel: LiveTvChannel, val folderId: String)
private class SearchResults(val query: String, val nodes: List<Pair<List<String>, LiveTvNode>>, val channels: List<SearchHit>)

/** Suche im ganzen Land/Ordner: passende Reiter (z. B. Serien) und passende Sender/Filme. */
private fun searchTree(root: LiveTvNode, query: String): SearchResults {
    val nodes = mutableListOf<Pair<List<String>, LiveTvNode>>()
    val channels = mutableListOf<SearchHit>()
    fun walk(node: LiveTvNode, path: List<String>, trail: List<LiveTvNode>) {
        for (channel in node.channels) {
            if (channels.size >= SEARCH_CHANNEL_LIMIT) break
            val named = namedChannel(channel, trail)
            if (named.name.contains(query, ignoreCase = true)) channels += SearchHit(named, node.id)
        }
        for (child in node.children) {
            val childPath = path + child.id
            if (nodes.size < SEARCH_NODE_LIMIT && child.title.contains(query, ignoreCase = true)) nodes += childPath to child
            walk(child, childPath, trail + child)
        }
    }
    walk(root, emptyList(), listOf(root))
    return SearchResults(query, nodes, channels)
}

/**
 * Live TV auf dem Handy wie auf TV, PC und iPhone: eigene Listen als Reiter je Land bzw. Ordner mit
 * Unterreitern (Sendergruppen, Filme, Serien › Staffeln), Suche im Reiter und favorisierte Sender.
 */
@Composable
fun MobileLiveTvScreen(
    state: MobileHomeState,
    onPlayChannel: (LiveTvChannel) -> Unit
) {
    val scope = rememberCoroutineScope()
    val connectedDevice by MobileCastManager.connectedDevice.collectAsState()
    val favorites by LocalStorage.favoriteChannelsFlow.collectAsState()
    // Die festen Sender sind sofort da, auch wenn eine große eigene Liste noch lädt
    var allChannels by remember { mutableStateOf(LiveTvRepository.PRESET_CHANNELS) }
    var trees by remember { mutableStateOf<List<LiveTvNode>>(emptyList()) }
    var customGrouped by remember { mutableStateOf(false) }
    var customError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val selectedCategory = state.liveCategory
    val treePath = state.liveTreePath

    fun reload(force: Boolean) {
        scope.launch {
            isLoading = true
            val result = LiveTvRepository.loadChannels(forceRefresh = force)
            allChannels = result.channels
            customError = result.customError
            trees = result.trees
            customGrouped = LiveTvRepository.isCustomListGrouped
            // Beim ersten Öffnen gleich die erste eigene Liste zeigen
            if (state.liveCategory == "all" && trees.isNotEmpty() && !state.liveUserPickedTab) {
                state.liveCategory = TREE_PREFIX + trees.first().id
                state.liveTreePath = emptyList()
            }
            // Reiter eines entfernten Ordners/einer entfernten Liste gibt es nicht mehr
            val selectedTree = state.liveCategory.removePrefix(TREE_PREFIX)
            if (state.liveCategory.startsWith(TREE_PREFIX) && trees.none { it.id == selectedTree }) {
                state.liveCategory = CUSTOM_CATEGORY
                state.liveTreePath = emptyList()
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { reload(force = false) }

    val selectedRoot = trees.firstOrNull { TREE_PREFIX + it.id == selectedCategory }

    // Flache Reiter: feste Sender und eine Liste ohne Gruppen; sortierte Listen und Ordner haben eigene Reiter
    val flatChannels = remember(allChannels, selectedCategory, customGrouped) {
        val flat = allChannels.filter { channel ->
            !channel.category.startsWith("folder:") && !(customGrouped && channel.category == CUSTOM_CATEGORY)
        }
        if (selectedCategory == "all") flat else flat.filter { it.category == selectedCategory }
    }

    val trimmedQuery = state.liveQuery.trim()
    var searchResults by remember { mutableStateOf<SearchResults?>(null) }
    LaunchedEffect(selectedRoot, trimmedQuery) {
        searchResults = null
        val root = selectedRoot
        if (root == null || trimmedQuery.length < 2) return@LaunchedEffect
        delay(250L)
        // Große Listen nicht beim Tippen im Hauptthread durchsuchen
        searchResults = withContext(Dispatchers.Default) { searchTree(root, trimmedQuery) }
    }

    fun play(channel: LiveTvChannel, folderId: String, trail: List<LiveTvNode>) {
        LiveTvRepository.playFolderId = folderId
        onPlayChannel(namedChannel(channel, trail))
    }

    fun playOnTv(channel: LiveTvChannel, trail: List<LiveTvNode>) {
        scope.launch { MobileCastManager.playLiveOnTv(namedChannel(channel, trail)) }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Kopfzeile
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(LiveRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LIVE TV & SPORT",
                            color = LiveRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    Text(
                        text = if (connectedDevice != null) {
                            "Antippen spielt am Handy, 📺 schickt an ${connectedDevice?.name}"
                        } else {
                            "Sender antippen zum Abspielen, ☆ merkt ihn ganz oben"
                        },
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isLoading) {
                    CircularProgressIndicator(
                        color = AccentCyan,
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(12.dp).size(24.dp)
                    )
                } else {
                    IconButton(onClick = { reload(force = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Liste aktualisieren", tint = AccentCyan)
                    }
                }
            }
        }

        // Hauptreiter: zuerst die eigenen Listen (Länder, Ordner), danach die festen Kategorien
        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(trees, key = { TREE_PREFIX + it.id }) { root ->
                    val id = TREE_PREFIX + root.id
                    LiveChip("${root.icon} ${root.title}".trim(), selectedCategory == id) {
                        state.liveUserPickedTab = true
                        if (selectedCategory != id) {
                            state.liveCategory = id
                            state.liveTreePath = emptyList()
                            state.liveQuery = ""
                        }
                    }
                }
                items(LiveTvRepository.CATEGORIES, key = { it.id }) { cat ->
                    LiveChip("${cat.icon} ${cat.title}", selectedCategory == cat.id) {
                        state.liveUserPickedTab = true
                        state.liveCategory = cat.id
                        state.liveQuery = ""
                    }
                }
            }
        }

        if (selectedCategory == CUSTOM_CATEGORY) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                CustomM3uCard(onSaved = { reload(force = true) })
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                val folderTrees = trees.filter { it.id.startsWith("folder-tree:") }
                M3uFolderCard(
                    folderTabCount = folderTrees.sumOf { it.children.size.coerceAtLeast(1) },
                    folderChannelCount = folderTrees.sumOf { it.total },
                    onChanged = { reload(force = true) }
                )
            }
            if (customGrouped) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "Deine Liste ist nach Ländern sortiert: " +
                            trees.filter { it.id.startsWith("iptv:") }.joinToString(" · ") { "${it.icon} ${it.title} (${it.total})" },
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        if (customError != null && (selectedCategory == CUSTOM_CATEGORY || selectedCategory == "all" || selectedRoot != null)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(text = customError.orEmpty(), color = LiveRed, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }

        if (selectedRoot != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                TreeSearchField(
                    value = state.liveQuery,
                    placeholder = "In ${selectedRoot.title} suchen …",
                    onValueChange = { state.liveQuery = it }
                )
            }

            if (trimmedQuery.length >= 2) {
                val results = searchResults
                when {
                    results == null -> item(span = { GridItemSpan(maxLineSpan) }) { LoadingBox() }

                    results.nodes.isEmpty() && results.channels.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(text = "Nichts gefunden für „$trimmedQuery“.", color = TextMuted, fontSize = 14.sp)
                    }

                    else -> {
                        items(results.nodes, key = { "hit:" + it.second.id }, span = { GridItemSpan(maxLineSpan) }) { (path, node) ->
                            NodeRow(node) {
                                state.liveTreePath = path
                                state.liveQuery = ""
                            }
                        }
                        items(results.channels, key = { "hit-ch:" + it.channel.id }) { hit ->
                            MobileChannelCard(
                                channel = hit.channel,
                                isFavorite = hit.channel.streamUrl in favorites,
                                showTvButton = connectedDevice != null,
                                onClick = { play(hit.channel, hit.folderId, emptyList()) },
                                onPlayOnTv = { playOnTv(hit.channel, emptyList()) }
                            )
                        }
                    }
                }
            } else {
                val view = resolveTree(selectedRoot, treePath)
                view.levels.forEach { level ->
                    when (level) {
                        is TreeLevel.Chips -> item(
                            key = "level:${level.depth}:${level.children.first().id}",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            val selectedIndex = level.children.indexOfFirst { it.id == level.selectedId }.coerceAtLeast(0)
                            LazyRow(
                                state = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 1).coerceAtLeast(0)),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(level.children, key = { it.id }) { child ->
                                    LiveChip(
                                        text = listOf(child.icon, child.title).filter { it.isNotBlank() }.joinToString(" "),
                                        isSelected = child.id == level.selectedId
                                    ) {
                                        state.liveTreePath = treePath.take(level.depth) + child.id
                                    }
                                }
                            }
                        }

                        is TreeLevel.Back -> item(
                            key = "back:${level.depth}:${level.selected.id}",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { state.liveTreePath = treePath.take(level.depth) }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Zurück zu ${level.parent.title}", tint = AccentCyan)
                                }
                                Text(
                                    text = level.selected.title,
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                val list = view.list
                val leaf = view.leaf
                when {
                    list != null -> items(list, key = { "node:" + it.id }, span = { GridItemSpan(maxLineSpan) }) { child ->
                        NodeRow(child) {
                            state.liveTreePath = treePath.take(view.listDepth) + child.id
                        }
                    }

                    leaf != null && leaf.channels.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(text = "Keine Sender in diesem Reiter.", color = TextMuted, fontSize = 14.sp)
                    }

                    leaf != null -> {
                        // Favoriten stehen in jedem Ordner ganz oben
                        val (favorite, rest) = leaf.channels.partition { it.streamUrl in favorites }
                        items(favorite + rest, key = { "ch:" + it.id }) { channel ->
                            MobileChannelCard(
                                channel = channel,
                                isFavorite = channel.streamUrl in favorites,
                                showTvButton = connectedDevice != null,
                                onClick = { play(channel, leaf.id, view.trail) },
                                onPlayOnTv = { playOnTv(channel, view.trail) }
                            )
                        }
                    }
                }
            }
        } else if (flatChannels.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                if (isLoading) {
                    LoadingBox()
                } else {
                    Text(
                        text = when {
                            selectedCategory == CUSTOM_CATEGORY && customGrouped -> "Die Sender stehen in den Länder-Reitern ganz vorne."
                            selectedCategory == CUSTOM_CATEGORY -> "Noch keine eigene Liste. Gib oben den Link zu deiner M3U-Datei ein."
                            else -> "Keine Sender in dieser Kategorie."
                        },
                        color = TextMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                }
            }
        } else {
            val (favorite, rest) = flatChannels.partition { it.streamUrl in favorites }
            items(favorite + rest, key = { "ch:" + it.id }) { channel ->
                MobileChannelCard(
                    channel = channel,
                    isFavorite = channel.streamUrl in favorites,
                    showTvButton = connectedDevice != null,
                    onClick = { play(channel, LiveTvRepository.PRESET_FOLDER_PREFIX + selectedCategory, emptyList()) },
                    onPlayOnTv = { playOnTv(channel, emptyList()) }
                )
            }
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = AccentCyan)
    }
}

@Composable
private fun LiveChip(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (isSelected) AccentCyan.copy(alpha = 0.18f) else BgSurface)
            .border(
                1.dp,
                if (isSelected) AccentCyan else Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = if (isSelected) AccentCyan else TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/** Unterreiter als Zeile (viele Serien, Suchtreffer). */
@Composable
private fun NodeRow(node: LiveTvNode, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BgElevated)
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = node.title,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (node.children.isEmpty()) "${node.total}" else "${node.children.size} St. · ${node.total}",
            color = TextMuted,
            fontSize = 12.sp
        )
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun TreeSearchField(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = TextMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Löschen", tint = TextSecondary)
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentCyan,
            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Eigener Ordner mit M3U-Dateien (auch in Unterordnern): Jeder Ordner wird ein eigener Reiter.
 * Gelesen wird direkt aus dem Ordner, neue Dateien erscheinen nach "Liste aktualisieren".
 */
@Composable
private fun M3uFolderCard(
    folderTabCount: Int,
    folderChannelCount: Int,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    var folder by remember { mutableStateOf(LocalStorage.m3uFolder) }
    var folderName by remember { mutableStateOf(LocalStorage.m3uFolderName) }
    var pickerError by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            M3uFolderAndroid.onFolderPicked(context, uri)
            folder = LocalStorage.m3uFolder
            folderName = LocalStorage.m3uFolderName
            onChanged()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgElevated)
            .padding(14.dp)
    ) {
        Text(
            text = if (folder.isBlank()) "📁 Eigener Ordner" else "📁 Ordner: $folderName",
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (folder.isBlank()) {
                "Ordner mit M3U-Dateien wählen. Unterordner werden eigene Reiter, die Dateien darin ihre Sender."
            } else {
                "$folderChannelCount Sender in $folderTabCount Reitern · steht ganz vorne · neue Dateien mit ↻ laden"
            },
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
        pickerError?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = it, color = LiveRed, fontSize = 12.sp)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (folder.isNotBlank()) {
                TextButton(onClick = {
                    M3uFolderAndroid.remove(context)
                    folder = ""
                    folderName = ""
                    onChanged()
                }) {
                    Text("Entfernen", color = TextSecondary, fontSize = 13.sp)
                }
            }
            Button(
                onClick = {
                    pickerError = null
                    runCatching { picker.launch(null) }.onFailure {
                        pickerError = "Auf diesem Gerät gibt es keine Ordnerauswahl."
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgPrimary)
            ) {
                Text(if (folder.isBlank()) "Ordner hinzufügen" else "Anderer Ordner", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Link zur eigenen Liste: nach dem Speichern nie wieder sichtbar (enthält Zugangsdaten). */
@Composable
private fun CustomM3uCard(onSaved: () -> Unit) {
    val focusManager = LocalFocusManager.current
    var hasSavedUrl by remember { mutableStateOf(LocalStorage.customM3uUrl.isNotBlank()) }
    var draft by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    fun save(value: String) {
        focusManager.clearFocus()
        if (LiveTvRepository.saveCustomM3uUrl(value)) {
            hasSavedUrl = LocalStorage.customM3uUrl.isNotBlank()
            draft = ""
            inputError = null
            onSaved()
        } else {
            inputError = "Das ist keine gültige Adresse (Beispiel: http://server.de/liste.m3u)."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgElevated)
            .padding(14.dp)
    ) {
        Text(
            text = if (hasSavedUrl) "🔒 Eigene Liste gespeichert" else "📂 Eigene M3U-Liste",
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (hasSavedUrl) {
                "Der Link wird nicht angezeigt. Zum Ersetzen einen neuen Link eingeben."
            } else {
                "Link zu einer M3U- oder M3U8-Senderliste. Sie wird zusätzlich zu den festen Sendern geladen."
            },
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = {
                draft = it
                inputError = null
            },
            placeholder = {
                Text(if (hasSavedUrl) "Neuer Link …" else "http://server.de/liste.m3u", color = TextMuted)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (draft.isNotBlank()) save(draft) else focusManager.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentCyan,
                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        )
        inputError?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = it, color = LiveRed, fontSize = 12.sp)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasSavedUrl) {
                TextButton(onClick = { save("") }) {
                    Text("Entfernen", color = TextSecondary, fontSize = 13.sp)
                }
            }
            Button(
                onClick = { save(draft) },
                enabled = draft.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgPrimary)
            ) {
                Text("Speichern", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MobileChannelCard(
    channel: LiveTvChannel,
    isFavorite: Boolean,
    showTvButton: Boolean,
    onClick: () -> Unit,
    onPlayOnTv: () -> Unit
) {
    var logoFailed by remember(channel.logoUrl) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgElevated)
            .border(0.8.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            if (channel.logoUrl.isNotBlank() && !logoFailed) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    onError = { logoFailed = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                )
            } else {
                Text(
                    text = channel.name.take(3).uppercase(),
                    color = AccentCyan,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(text = channel.qualityBadge, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }

            IconButton(
                onClick = { LocalStorage.toggleFavoriteChannel(channel.streamUrl) },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(34.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (isFavorite) "Aus Favoriten entfernen" else "Favorisieren",
                    tint = if (isFavorite) AccentCyan else Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = channel.name,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                lineHeight = 16.sp,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (showTvButton) {
                IconButton(
                    onClick = onPlayOnTv,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AccentCyan.copy(alpha = 0.15f))
                ) {
                    Icon(
                        Icons.Default.Tv,
                        contentDescription = "Auf TV abspielen",
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
