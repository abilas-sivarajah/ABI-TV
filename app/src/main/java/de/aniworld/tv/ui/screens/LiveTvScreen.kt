package de.aniworld.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.model.LiveTvChannel
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
    var selectedCategory by remember { mutableStateOf("all") }
    var isLoading by remember { mutableStateOf(true) }
    var focusedChannel by remember { mutableStateOf<LiveTvChannel?>(null) }
    val scope = rememberCoroutineScope()

    fun reload(force: Boolean) {
        scope.launch {
            isLoading = true
            val result = LiveTvRepository.loadChannels(forceRefresh = force)
            allChannels = result.channels
            customError = result.customError
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { reload(force = false) }

    val filteredChannels = remember(allChannels, selectedCategory) {
        if (selectedCategory == "all") {
            allChannels
        } else {
            allChannels.filter { it.category == selectedCategory }
        }
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

        // Kategorien-Auswahl (Tabs mit D-Pad Steuerbarkeit)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(LiveTvRepository.CATEGORIES, key = { it.id }) { cat ->
                val isSelected = selectedCategory == cat.id
                TvFocusableButton(
                    text = "${cat.icon} ${cat.title}",
                    isSelected = isSelected,
                    isPrimary = isSelected,
                    onClick = { selectedCategory = cat.id }
                )
            }
        }

        // Eigene M3U-Liste: Adresse eingeben und Ladefehler anzeigen
        if (selectedCategory == CUSTOM_CATEGORY) {
            Spacer(modifier = Modifier.height(12.dp))
            CustomM3uInput(onSaved = { reload(force = true) })
        }
        if (customError != null && (selectedCategory == CUSTOM_CATEGORY || selectedCategory == "all")) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = customError.orEmpty(), color = LiveRed, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Senderliste Grid
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentCyan)
            }
        } else if (filteredChannels.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (selectedCategory == CUSTOM_CATEGORY) {
                        "Noch keine eigene Liste. Gib oben den Link zu deiner M3U-Datei ein."
                    } else {
                        "Keine Sender in dieser Kategorie gefunden."
                    },
                    color = TextMuted,
                    fontSize = 15.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 180.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredChannels, key = { it.id }) { channel ->
                    TvChannelCard(
                        channel = channel,
                        onClick = { onPlayChannel(channel) },
                        onFocused = { focusedChannel = channel }
                    )
                }
            }
        }
    }
}

/** Eingabefeld für den Link zur eigenen M3U-Liste (Bedienung wie das Suchfeld: OK zum Tippen). */
@Composable
private fun CustomM3uInput(onSaved: () -> Unit) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val textFieldFocusRequester = remember { FocusRequester() }
    val boxFocusRequester = remember { FocusRequester() }
    var hasEdited by remember { mutableStateOf(false) }
    var savedUrl by remember { mutableStateOf(LocalStorage.customM3uUrl) }
    var draft by remember { mutableStateOf(savedUrl) }
    var isEditing by remember { mutableStateOf(false) }
    var isBoxFocused by remember { mutableStateOf(false) }
    var inputError by remember { mutableStateOf<String?>(null) }

    fun save(value: String) {
        isEditing = false
        keyboardController?.hide()
        if (LiveTvRepository.saveCustomM3uUrl(value)) {
            savedUrl = LocalStorage.customM3uUrl
            draft = savedUrl
            inputError = null
            onSaved()
        } else {
            inputError = "Das ist keine gültige Adresse (Beispiel: http://server.de/liste.m3u)."
        }
    }

    BackHandler(enabled = isEditing) {
        isEditing = false
        draft = savedUrl
        keyboardController?.hide()
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            hasEdited = true
            delay(50L)
            textFieldFocusRequester.requestFocus()
            keyboardController?.show()
        } else if (hasEdited) {
            // Nach dem Tippen den Fokus auf dem Feld lassen, statt ihn auf den ersten Tab springen zu lassen
            delay(50L)
            runCatching { boxFocusRequester.requestFocus() }
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
                    .focusable(!isEditing)
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
                            text = savedUrl.ifBlank { "📂 Link zur eigenen M3U-Liste eingeben" },
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
                        keyboardActions = KeyboardActions(onDone = { save(draft) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(textFieldFocusRequester)
                            .onPreviewKeyEvent { event ->
                                // Hardware-Enter erst beim Loslassen auswerten: sonst landet das Loslassen
                                // auf dem nächsten fokussierten Element und löst dort einen Klick aus
                                if (event.key == Key.Enter || event.key == Key.NumPadEnter) {
                                    if (event.type == KeyEventType.KeyUp) save(draft)
                                    true
                                } else false
                            }
                    )
                }
            }

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
