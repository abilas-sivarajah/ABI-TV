package de.aniworld.pc.ui.screens

import de.aniworld.pc.ui.compat.ScrollLazyRow
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import de.aniworld.pc.ui.compat.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.aniworld.pc.ui.compat.AsyncImage
import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.model.LiveTvNode
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import de.aniworld.tv.data.model.LiveTvChannel
import de.aniworld.tv.data.remote.LiveTvRepository
import de.aniworld.pc.ui.components.TvFocusableButton
import de.aniworld.pc.platform.AppDirs
import de.aniworld.pc.ui.compat.LocalComposeWindow
import java.awt.FileDialog
import javax.swing.JFileChooser
import java.io.File
import java.net.URI
import de.aniworld.pc.ui.theme.AccentCyan
import de.aniworld.pc.ui.theme.BgElevated
import de.aniworld.pc.ui.theme.BgPrimary
import de.aniworld.pc.ui.theme.BgSurface
import de.aniworld.pc.ui.theme.FocusBorder
import de.aniworld.pc.ui.theme.TextMuted
import de.aniworld.pc.ui.theme.TextPrimary
import de.aniworld.pc.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val LiveRed = Color(0xFFEF4444)
private const val CUSTOM_CATEGORY = "custom"

@Composable
fun LiveTvScreen(
    onPlayChannel: (LiveTvChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    var allChannels by remember { mutableStateOf<List<LiveTvChannel>>(emptyList()) }
    var customError by remember { mutableStateOf<String?>(null) }
    var trees by remember { mutableStateOf<List<LiveTvNode>>(emptyList()) }
    var customGrouped by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("all") }
    // Gewählte Unterreiter je Ebene unterhalb des Land-/Ordner-Reiters
    var treePath by remember { mutableStateOf<List<String>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var focusedChannel by remember { mutableStateOf<LiveTvChannel?>(null) }
    val scope = rememberCoroutineScope()

    fun reload(force: Boolean) {
        scope.launch {
            isLoading = true
            val result = LiveTvRepository.loadChannels(forceRefresh = force)
            allChannels = result.channels
            customError = result.customError
            trees = result.trees
            customGrouped = LiveTvRepository.isCustomListGrouped
            // Reiter eines entfernten Ordners/einer entfernten Liste gibt es nicht mehr
            val selectedTree = selectedCategory.removePrefix(TREE_PREFIX)
            if (selectedCategory.startsWith(TREE_PREFIX) && trees.none { it.id == selectedTree }) {
                selectedCategory = CUSTOM_CATEGORY
                treePath = emptyList()
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { reload(force = false) }

    val selectedRoot = trees.firstOrNull { TREE_PREFIX + it.id == selectedCategory }

    // Flache Reiter: feste Sender und eine Liste ohne Gruppen; sortierte Listen und Ordner haben eigene Reiter
    val filteredChannels = remember(allChannels, selectedCategory, customGrouped, trees) {
        val flat = allChannels.filter { channel ->
            !channel.category.startsWith("folder:") && !(customGrouped && channel.category == CUSTOM_CATEGORY)
        }
        if (selectedCategory == "all") flat else flat.filter { it.category == selectedCategory }
    }

    fun play(channel: LiveTvChannel, context: List<LiveTvNode>) {
        // Serienfolgen heißen im Raster nur "S01 E05": für den Player den Serientitel davorsetzen
        val showTitle = context.lastOrNull { !it.title.startsWith("Staffel ") && it.title != "Weitere" }?.title
        val named = if (showTitle != null && EPISODE_NAME.matches(channel.name)) {
            channel.copy(name = "$showTitle · ${channel.name}")
        } else {
            channel
        }
        onPlayChannel(named)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPrimary)
            .padding(top = 12.dp, start = 28.dp, end = 28.dp, bottom = 12.dp)
    ) {
        // Obere Informationszeile & Fokussierter Sender
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(LiveRed)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LIVE TV & SPORT CHANNELS",
                        color = LiveRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = focusedChannel?.name ?: "Wähle einen Sender",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (selectedRoot != null) {
                TreeSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "In ${selectedRoot.title} suchen …"
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            TvFocusableButton(
                text = "🔄 Liste aktualisieren",
                onClick = { reload(force = true) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Hauptreiter: feste Kategorien, danach je Land bzw. Ordner der eigenen Listen
        ScrollLazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(LiveTvRepository.CATEGORIES, key = { it.id }) { cat ->
                val isSelected = selectedCategory == cat.id
                TvFocusableButton(
                    text = "${cat.icon} ${cat.title}",
                    isSelected = isSelected,
                    isPrimary = isSelected,
                    onClick = {
                        selectedCategory = cat.id
                        query = ""
                    }
                )
            }
            items(trees, key = { TREE_PREFIX + it.id }) { root ->
                val isSelected = selectedCategory == TREE_PREFIX + root.id
                TvFocusableButton(
                    text = "${root.icon} ${root.title}".trim(),
                    isSelected = isSelected,
                    isPrimary = isSelected,
                    onClick = {
                        if (!isSelected) {
                            selectedCategory = TREE_PREFIX + root.id
                            treePath = emptyList()
                            query = ""
                        }
                    }
                )
            }
        }

        // Eigene M3U-Liste: Adresse eingeben und Ladefehler anzeigen
        if (selectedCategory == CUSTOM_CATEGORY) {
            Spacer(modifier = Modifier.height(12.dp))
            CustomM3uInput(onSaved = { reload(force = true) })
            Spacer(modifier = Modifier.height(10.dp))
            M3uFolderRow(
                folderTabCount = trees.filter { it.id.startsWith("folder-tree:") }.sumOf { it.children.size.coerceAtLeast(1) },
                folderChannelCount = trees.filter { it.id.startsWith("folder-tree:") }.sumOf { it.total },
                onChanged = { reload(force = true) }
            )
            if (customGrouped) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Deine Liste ist nach Ländern sortiert: " +
                        trees.filter { it.id.startsWith("iptv:") }.joinToString(" · ") { "${it.icon} ${it.title} (${it.total})" },
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
        if (customError != null && (selectedCategory == CUSTOM_CATEGORY || selectedCategory == "all" || selectedRoot != null)) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = customError.orEmpty(), color = LiveRed, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isLoading && allChannels.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentCyan)
            }
        } else if (selectedRoot != null) {
            TreeBrowser(
                root = selectedRoot,
                path = treePath,
                query = query,
                onPathChange = {
                    treePath = it
                    query = ""
                },
                onPlay = ::play,
                onFocused = { focusedChannel = it }
            )
        } else if (filteredChannels.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        selectedCategory == CUSTOM_CATEGORY && customGrouped -> "Die Sender stehen in den Länder-Reitern oben."
                        selectedCategory == CUSTOM_CATEGORY ->
                            "Noch keine eigene Liste. Gib oben den Link zu deiner M3U-Liste ein oder wähle eine M3U-Datei aus."
                        else -> "Keine Sender in dieser Kategorie gefunden."
                    },
                    color = TextMuted,
                    fontSize = 15.sp
                )
            }
        } else {
            ChannelGrid(filteredChannels, onPlay = { onPlayChannel(it) }, onFocused = { focusedChannel = it })
        }
    }
}

private const val TREE_PREFIX = "tree:"
/** Bis zu so vielen Unterreitern als Knöpfe (umbrechend), darüber als Kacheln (z. B. Serien). */
private const val CHIP_LIMIT = 60
private const val SEARCH_LIMIT = 400
private val EPISODE_NAME = Regex("""^S\d+ E\d+.*""")

@Composable
private fun ChannelGrid(
    channels: List<LiveTvChannel>,
    onPlay: (LiveTvChannel) -> Unit,
    onFocused: (LiveTvChannel) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 180.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(channels, key = { it.id }) { channel ->
            TvChannelCard(
                channel = channel,
                onClick = { onPlay(channel) },
                onFocused = { onFocused(channel) }
            )
        }
    }
}

/** Eine Ebene der Unterreiter: Knöpfe (wenige) oder "Zurück" (wenn aus Kacheln gewählt wurde). */
private sealed interface TreeLevel {
    val depth: Int
    data class Chips(override val depth: Int, val children: List<LiveTvNode>, val selectedId: String) : TreeLevel
    data class Back(override val depth: Int, val parent: LiveTvNode, val selected: LiveTvNode) : TreeLevel
}

/** Was im Baum gerade zu sehen ist: Ebenen oben, darunter Kacheln oder die Sender des gewählten Reiters. */
private class TreeView(
    val levels: List<TreeLevel>,
    val tiles: List<LiveTvNode>?,
    val tilesDepth: Int,
    val leaf: LiveTvNode?,
    /** Gewählte Reiter von oben nach unten (für Serientitel beim Abspielen). */
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
            // Wenige Unterreiter: als Knöpfe, ohne Auswahl der erste
            val selected = chosen ?: children.first()
            levels += TreeLevel.Chips(depth, children, selected.id)
            node = selected
        } else {
            // Viele (z. B. Serien einer Gruppe): als Kacheln; gewählt mit "Zurück" darüber
            if (chosen == null) return TreeView(levels, children, depth, null, trail)
            levels += TreeLevel.Back(depth, node, chosen)
            node = chosen
        }
        trail += node
        depth++
    }
}

/** Pfad (Reiter-Kennungen ab der ersten Ebene) zu allen Reitern, deren Titel [query] enthält. */
private fun searchNodes(root: LiveTvNode, query: String, limit: Int): List<Pair<List<String>, LiveTvNode>> {
    val result = mutableListOf<Pair<List<String>, LiveTvNode>>()
    fun walk(node: LiveTvNode, path: List<String>) {
        for (child in node.children) {
            if (result.size >= limit) return
            val childPath = path + child.id
            if (child.title.contains(query, ignoreCase = true)) result += childPath to child
            walk(child, childPath)
        }
    }
    walk(root, emptyList())
    return result
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TreeBrowser(
    root: LiveTvNode,
    path: List<String>,
    query: String,
    onPathChange: (List<String>) -> Unit,
    onPlay: (LiveTvChannel, List<LiveTvNode>) -> Unit,
    onFocused: (LiveTvChannel) -> Unit
) {
    val trimmed = query.trim()
    if (trimmed.length >= 2) {
        // Suche im ganzen Land/Ordner: passende Reiter (z. B. Serien) und passende Sender/Filme
        val nodes = remember(root, trimmed) { searchNodes(root, trimmed, 60) }
        val channels = remember(root, trimmed) {
            root.allChannels().filter { it.name.contains(trimmed, ignoreCase = true) }.take(SEARCH_LIMIT).toList()
        }
        Column(modifier = Modifier.fillMaxSize()) {
            if (nodes.isNotEmpty()) {
                Text("Reiter", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    nodes.forEach { (nodePath, node) ->
                        TvFocusableButton(
                            text = "${node.title} (${node.total})",
                            onClick = { onPathChange(nodePath) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (channels.isEmpty() && nodes.isEmpty()) {
                Text("Nichts gefunden für „$trimmed“.", color = TextMuted, fontSize = 15.sp)
            } else if (channels.isNotEmpty()) {
                Text(
                    if (channels.size >= SEARCH_LIMIT) "Die ersten $SEARCH_LIMIT Treffer" else "${channels.size} Treffer",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                ChannelGrid(channels, onPlay = { onPlay(it, emptyList()) }, onFocused = onFocused)
            }
        }
        return
    }

    val view = remember(root, path) { resolveTree(root, path) }
    Column(modifier = Modifier.fillMaxSize()) {
        view.levels.forEach { level ->
            when (level) {
                is TreeLevel.Chips -> FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    level.children.forEach { child ->
                        val selected = child.id == level.selectedId
                        TvFocusableButton(
                            text = listOf(child.icon, child.title).filter { it.isNotBlank() }.joinToString(" "),
                            isSelected = selected,
                            isPrimary = selected,
                            onClick = { onPathChange(path.take(level.depth) + child.id) }
                        )
                    }
                }
                is TreeLevel.Back -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    TvFocusableButton(
                        text = "‹ ${level.parent.title}",
                        onClick = { onPathChange(path.take(level.depth)) }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = level.selected.title,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        val tiles = view.tiles
        val leaf = view.leaf
        when {
            tiles != null -> NodeTileGrid(tiles) { child -> onPathChange(path.take(view.tilesDepth) + child.id) }
            leaf != null && leaf.channels.isEmpty() ->
                Text("Keine Sender in diesem Reiter.", color = TextMuted, fontSize = 15.sp)
            leaf != null -> ChannelGrid(leaf.channels, onPlay = { onPlay(it, view.trail) }, onFocused = onFocused)
        }
    }
}

/** Kacheln für viele Unterreiter (z. B. alle Serien einer Gruppe). */
@Composable
private fun NodeTileGrid(nodes: List<LiveTvNode>, onOpen: (LiveTvNode) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 200.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(nodes, key = { it.id }) { node ->
            val hoverSource = remember { MutableInteractionSource() }
            val hovered by hoverSource.collectIsHoveredAsState()
            Column(
                modifier = Modifier
                    .hoverable(hoverSource)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (hovered) BgSurface else BgElevated)
                    .border(
                        width = if (hovered) 2.dp else 1.dp,
                        color = if (hovered) FocusBorder else Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onOpen(node) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = node.title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                val seasons = node.children.size
                Text(
                    text = if (seasons > 0) "$seasons Staffeln · ${node.total} Folgen" else "${node.total} Titel",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/** Suchfeld über dem Baum (Maus/Tastatur am PC). */
@Composable
private fun TreeSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Box(
        modifier = Modifier
            .width(280.dp)
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(BgSurface)
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = TextMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
            cursorBrush = SolidColor(AccentCyan),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Eigener Ordner mit M3U-Dateien (auch in Unterordnern): Jeder Ordner wird ein eigener Reiter.
 * Gelesen wird direkt vom Ordner, neue Dateien erscheinen nach "Liste aktualisieren".
 */
@Composable
private fun M3uFolderRow(
    folderTabCount: Int,
    folderChannelCount: Int,
    onChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var folder by remember { mutableStateOf(LocalStorage.m3uFolder) }
    var folderName by remember { mutableStateOf(LocalStorage.m3uFolderName) }

    fun pickFolder() {
        scope.launch {
            // Swing-Dialog: Der Windows-Dateidialog von AWT kann keine Ordner auswählen
            val chosen = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                var picked: File? = null
                javax.swing.SwingUtilities.invokeAndWait {
                    val chooser = JFileChooser(folder.ifBlank { null }?.let { File(it).parentFile }).apply {
                        dialogTitle = "Ordner mit M3U-Dateien auswählen"
                        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                        isAcceptAllFileFilterUsed = false
                    }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) picked = chooser.selectedFile
                }
                picked
            } ?: return@launch
            LiveTvRepository.saveM3uFolder(chosen.absolutePath, chosen.name)
            folder = LocalStorage.m3uFolder
            folderName = LocalStorage.m3uFolderName
            onChanged()
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (folder.isBlank()) "📁 Eigener Ordner mit M3U-Dateien" else "📁 Ordner: $folderName",
                color = if (folder.isBlank()) TextMuted else TextPrimary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (folder.isBlank()) {
                    "Unterordner werden eigene Reiter, die Dateien darin ihre Sender."
                } else {
                    "$folderChannelCount Sender in $folderTabCount Reitern · neue Dateien mit „Liste aktualisieren“ laden"
                },
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TvFocusableButton(
            text = if (folder.isBlank()) "📁 Ordner hinzufügen" else "📁 Anderen Ordner",
            isPrimary = folder.isBlank(),
            onClick = { pickFolder() }
        )
        if (folder.isNotBlank()) {
            TvFocusableButton(
                text = "✕ Ordner entfernen",
                onClick = {
                    LiveTvRepository.saveM3uFolder("", "")
                    folder = ""
                    folderName = ""
                    onChanged()
                }
            )
        }
    }
}

/** Eingabefeld für den Link zur eigenen M3U-Liste (Bedienung wie das Suchfeld: OK zum Tippen). */
@Composable
private fun CustomM3uInput(onSaved: () -> Unit) {
    val window = LocalComposeWindow.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val textFieldFocusRequester = remember { FocusRequester() }
    val boxFocusRequester = remember { FocusRequester() }
    var savedUrl by remember { mutableStateOf(LocalStorage.customM3uUrl) }
    // Mit Cursor am Textende: Das Feld wird bei jedem Öffnen neu angelegt und hätte ihn sonst am Anfang (wie im Suchfeld)
    var draft by remember { mutableStateOf(TextFieldValue(savedUrl, TextRange(savedUrl.length))) }
    var isEditing by remember { mutableStateOf(false) }
    var isBoxFocused by remember { mutableStateOf(false) }
    var inputError by remember { mutableStateOf<String?>(null) }

    fun save(value: String) {
        isEditing = false
        keyboardController?.hide()
        // Fokus aufs Feld holen, solange das Textfeld noch da ist (sonst Absturz beim nächsten OK, wie im Suchfeld)
        runCatching { boxFocusRequester.requestFocus() }
        if (LiveTvRepository.saveCustomM3uUrl(value)) {
            savedUrl = LocalStorage.customM3uUrl
            draft = TextFieldValue(savedUrl, TextRange(savedUrl.length))
            inputError = null
            onSaved()
        } else {
            inputError = "Das ist keine gültige Adresse (Beispiel: http://server.de/liste.m3u)."
        }
    }

    // M3U-Datei vom PC wählen: wird in den App-Ordner kopiert, damit sie auch nach Verschieben/Löschen
    // des Originals funktioniert (Änderungen am Original: Datei einfach erneut auswählen)
    fun pickFile() {
        val dialog = FileDialog(window, "M3U-Liste auswählen", FileDialog.LOAD).apply {
            file = "*.m3u;*.m3u8"
            setFilenameFilter { _, name -> name.endsWith(".m3u", true) || name.endsWith(".m3u8", true) }
            isVisible = true
        }
        val chosen = dialog.file?.let { File(dialog.directory, it) } ?: return
        runCatching {
            val targetDir = File(AppDirs.dataDir, "eigene-m3u").apply { mkdirs() }
            targetDir.listFiles()?.forEach { it.delete() }
            val copy = File(targetDir, chosen.name)
            chosen.copyTo(copy, overwrite = true)
            copy
        }.onSuccess { copy ->
            save(copy.toURI().toString())
        }.onFailure {
            inputError = "Datei konnte nicht übernommen werden (${it.message})."
        }
    }

    BackHandler(enabled = isEditing) {
        isEditing = false
        draft = TextFieldValue(savedUrl, TextRange(savedUrl.length))
        keyboardController?.hide()
        runCatching { boxFocusRequester.requestFocus() }
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            delay(50L)
            textFieldFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .focusRequester(boxFocusRequester)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BgSurface)
                    .border(
                        width = if (isBoxFocused || isEditing) 2.dp else 1.dp,
                        color = if (isBoxFocused || isEditing) FocusBorder else Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .onFocusChanged { isBoxFocused = it.isFocused }
                    // Nur clickable als Fokusziel, damit der Fokus nach dem Tippen aufs Feld zurückkommt (wie im Suchfeld)
                    .clickable { isEditing = true }
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp &&
                            (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                        ) {
                            isEditing = true
                            true
                        } else false
                    }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (!isEditing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = when {
                                savedUrl.isBlank() -> "📂 Link zur eigenen M3U-Liste eingeben"
                                savedUrl.startsWith("file:", true) ->
                                    "📄 Datei: " + runCatching { File(URI(savedUrl)).name }.getOrDefault(savedUrl)
                                else -> savedUrl
                            },
                            color = if (savedUrl.isBlank()) TextMuted else TextPrimary,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isBoxFocused) AccentCyan.copy(alpha = 0.2f) else BgElevated)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "OK zum Tippen",
                                color = if (isBoxFocused) AccentCyan else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(AccentCyan),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { save(draft.text) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(textFieldFocusRequester)
                            .onPreviewKeyEvent { event ->
                                // Hardware-Enter erst beim Loslassen auswerten: sonst landet das Loslassen
                                // auf dem nächsten fokussierten Element und löst dort einen Klick aus
                                if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
                                    if (event.type == KeyEventType.KeyUp) save(draft.text)
                                    true
                                } else false
                            }
                    )
                }
            }

            TvFocusableButton(
                text = "📁 Datei auswählen",
                isPrimary = savedUrl.isBlank(),
                onClick = { pickFile() }
            )
            if (savedUrl.isNotBlank()) {
                TvFocusableButton(
                    text = "✕ Entfernen",
                    onClick = { save("") }
                )
            }
        }

        inputError?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = it, color = LiveRed, fontSize = 13.sp)
        }
    }
}

@Composable
fun TvChannelCard(
    channel: LiveTvChannel,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    var hasFocus by remember { mutableStateOf(false) }
    // Am PC hebt auch die Maus Karten und Buttons hervor (auf dem TV nur der Fokus)
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()
    val isFocused = hasFocus || isHovered
    var logoFailed by remember(channel.logoUrl) { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1.0f,
        animationSpec = tween(150),
        label = "channelScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) FocusBorder else Color.Transparent,
        animationSpec = tween(150),
        label = "channelBorder"
    )

    Column(
        modifier = modifier
            .scale(scale)
            .hoverable(hoverSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .onFocusChanged { state ->
                hasFocus = state.isFocused
                if (state.isFocused) onFocused()
            }
            .clip(RoundedCornerShape(12.dp))
            .background(BgElevated)
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) borderColor else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp)
            )
            // clickable ist selbst fokussierbar; ein zusätzliches focusable() erzeugt einen zweiten
            // Fokusknoten, auf dem OK nichts auslöst
            .clickable { onClick() }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
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
                        .padding(8.dp)
                )
            } else {
                Text(
                    text = channel.name.take(3).uppercase(),
                    color = AccentCyan,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // Quality Badge (oben rechts)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = channel.qualityBadge,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Sender Name
        Text(
            text = channel.name,
            color = if (isFocused) Color.White else TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
