package de.aniworld.tv.ui.screens

import androidx.activity.compose.BackHandler
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
import coil.compose.AsyncImage
import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.model.LiveTvChannel
import de.aniworld.tv.data.model.LiveTvNode
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import de.aniworld.tv.platform.M3uFolderAndroid
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import de.aniworld.tv.data.remote.LiveTvRepository
import de.aniworld.tv.ui.components.TvFocusableButton
import de.aniworld.tv.ui.theme.AccentCyan
import de.aniworld.tv.ui.theme.BgElevated
import de.aniworld.tv.ui.theme.BgPrimary
import de.aniworld.tv.ui.theme.BgSurface
import de.aniworld.tv.ui.theme.FocusBorder
import de.aniworld.tv.ui.theme.TextMuted
import de.aniworld.tv.ui.theme.TextPrimary
import de.aniworld.tv.ui.theme.TextSecondary
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
    var userPickedTab by remember { mutableStateOf(false) }
    // Gewählte Unterreiter je Ebene unterhalb des Land-/Ordner-Reiters
    var treePath by remember { mutableStateOf<List<String>>(emptyList()) }
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
            // Beim ersten Öffnen gleich die erste eigene Liste zeigen
            if (selectedCategory == "all" && trees.isNotEmpty() && !userPickedTab) {
                selectedCategory = TREE_PREFIX + trees.first().id
                treePath = emptyList()
            }
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

    /** Sender starten und den Ordner merken: OK im Player öffnet dann die Senderwahl dieses Ordners. */
    fun play(channel: LiveTvChannel, folderId: String, context: List<LiveTvNode>) {
        val showTitle = context.lastOrNull { !it.title.startsWith("Staffel ") && it.title != "Weitere" }?.title
        val named = if (showTitle != null && EPISODE_NAME.matches(channel.name)) {
            channel.copy(name = "$showTitle · ${channel.name}")
        } else {
            channel
        }
        LiveTvRepository.playFolderId = folderId
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
                    text = focusedChannel?.name ?: "Wähle einen Sender mit dem Steuerkreuz",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            TvFocusableButton(
                text = "🔄 Liste aktualisieren",
                onClick = { reload(force = true) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Hauptreiter: zuerst je Land bzw. Ordner der eigenen Listen, danach die festen Kategorien
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(trees, key = { TREE_PREFIX + it.id }) { root ->
                val isSelected = selectedCategory == TREE_PREFIX + root.id
                TvFocusableButton(
                    text = "${root.icon} ${root.title}".trim(),
                    isSelected = isSelected,
                    isPrimary = isSelected,
                    onClick = {
                        userPickedTab = true
                        if (!isSelected) {
                            selectedCategory = TREE_PREFIX + root.id
                            treePath = emptyList()
                        }
                    }
                )
            }
            items(LiveTvRepository.CATEGORIES, key = { it.id }) { cat ->
                val isSelected = selectedCategory == cat.id
                TvFocusableButton(
                    text = "${cat.icon} ${cat.title}",
                    isSelected = isSelected,
                    isPrimary = isSelected,
                    onClick = {
                        userPickedTab = true
                        selectedCategory = cat.id
                    }
                )
            }
        }

        // Eigene M3U-Liste: Adresse eingeben und Ladefehler anzeigen
        if (selectedCategory == CUSTOM_CATEGORY) {
            Spacer(modifier = Modifier.height(12.dp))
            CustomM3uInput(onSaved = { reload(force = true) })
            Spacer(modifier = Modifier.height(10.dp))
            val folderTrees = trees.filter { it.id.startsWith("folder-tree:") }
            M3uFolderRow(
                folderTabCount = folderTrees.sumOf { it.children.size.coerceAtLeast(1) },
                folderChannelCount = folderTrees.sumOf { it.total },
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
                onPathChange = { treePath = it },
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
                        selectedCategory == CUSTOM_CATEGORY && customGrouped -> "Die Sender stehen in den Länder-Reitern ganz vorne."
                        selectedCategory == CUSTOM_CATEGORY -> "Noch keine eigene Liste. Gib oben den Link zu deiner M3U-Datei ein."
                        else -> "Keine Sender in dieser Kategorie gefunden."
                    },
                    color = TextMuted,
                    fontSize = 15.sp
                )
            }
        } else {
            ChannelGrid(
                channels = filteredChannels,
                onPlay = { play(it, LiveTvRepository.PRESET_FOLDER_PREFIX + selectedCategory, emptyList()) },
                onFocused = { focusedChannel = it }
            )
        }
    }
}

private const val TREE_PREFIX = "tree:"
/** Bis zu so vielen Unterreitern als Knöpfe (umbrechend), darüber als Kacheln (z. B. Serien). */
private const val CHIP_LIMIT = 60
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

private class TreeView(
    val levels: List<TreeLevel>,
    val tiles: List<LiveTvNode>?,
    val tilesDepth: Int,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TreeBrowser(
    root: LiveTvNode,
    path: List<String>,
    onPathChange: (List<String>) -> Unit,
    onPlay: (LiveTvChannel, String, List<LiveTvNode>) -> Unit,
    onFocused: (LiveTvChannel) -> Unit
) {
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
            tiles != null -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 220.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(tiles, key = { it.id }) { child ->
                    val seasons = child.children.size
                    TvFocusableButton(
                        text = child.title + if (seasons > 0) "  ·  $seasons St." else "  ·  ${child.total}",
                        onClick = { onPathChange(path.take(view.tilesDepth) + child.id) }
                    )
                }
            }
            leaf != null && leaf.channels.isEmpty() ->
                Text("Keine Sender in diesem Reiter.", color = TextMuted, fontSize = 15.sp)
            leaf != null -> ChannelGrid(
                channels = leaf.channels,
                onPlay = { onPlay(it, leaf.id, view.trail) },
                onFocused = onFocused
            )
        }
    }
}

/**
 * Eigener Ordner mit M3U-Dateien (auch in Unterordnern, z. B. auf einem USB-Stick):
 * Jeder Ordner wird ein eigener Reiter. Neue Dateien erscheinen nach "Liste aktualisieren".
 */
@Composable
private fun M3uFolderRow(
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

    Column {
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
                text = if (folder.isBlank()) "📁 Ordner hinzufügen" else "📁 Anderer Ordner",
                isPrimary = folder.isBlank(),
                onClick = {
                    pickerError = null
                    runCatching { picker.launch(null) }.onFailure {
                        pickerError = "Auf diesem Gerät gibt es keine Ordnerauswahl."
                    }
                }
            )
            if (folder.isNotBlank()) {
                TvFocusableButton(
                    text = "✕ Ordner entfernen",
                    onClick = {
                        M3uFolderAndroid.remove(context)
                        folder = ""
                        folderName = ""
                        onChanged()
                    }
                )
            }
        }
        pickerError?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = it, color = LiveRed, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CustomM3uInput(onSaved: () -> Unit) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val textFieldFocusRequester = remember { FocusRequester() }
    val boxFocusRequester = remember { FocusRequester() }
    var savedUrl by remember { mutableStateOf(LocalStorage.customM3uUrl) }
    // Der gespeicherte Link (mit Benutzername und Passwort) wird nie wieder angezeigt: das Feld startet leer
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    var isEditing by remember { mutableStateOf(false) }
    var isBoxFocused by remember { mutableStateOf(false) }
    var inputError by remember { mutableStateOf<String?>(null) }

    fun save(value: String) {
        isEditing = false
        keyboardController?.hide()
        // Fokus aufs Feld holen, solange das Textfeld noch da ist (sonst Absturz beim nächsten OK, wie im Suchfeld)
        runCatching { boxFocusRequester.requestFocus() }
        // Leeres Feld bestätigt: nichts ändern (Entfernen nur über den Knopf)
        if (value.isBlank() && savedUrl.isNotBlank()) {
            draft = TextFieldValue("")
            return
        }
        if (LiveTvRepository.saveCustomM3uUrl(value)) {
            savedUrl = LocalStorage.customM3uUrl
            draft = TextFieldValue("")
            inputError = null
            onSaved()
        } else {
            inputError = "Das ist keine gültige Adresse (Beispiel: http://server.de/liste.m3u)."
        }
    }

    BackHandler(enabled = isEditing) {
        isEditing = false
        draft = TextFieldValue("")
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
                            text = if (savedUrl.isBlank()) "📂 Link zur eigenen M3U-Liste eingeben" else "🔒 Eigene Liste gespeichert",
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

            if (savedUrl.isNotBlank()) {
                TvFocusableButton(
                    text = "✕ Entfernen",
                    onClick = {
                        LiveTvRepository.saveCustomM3uUrl("")
                        savedUrl = ""
                        draft = TextFieldValue("")
                        inputError = null
                        onSaved()
                    }
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
    var isFocused by remember { mutableStateOf(false) }
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
            .onFocusChanged { state ->
                isFocused = state.isFocused
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
