package de.aniworld.mobile.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import de.aniworld.tv.data.remote.LiveTvRepository
import kotlinx.coroutines.launch

private val LiveRed = Color(0xFFEF4444)
private const val CUSTOM_CATEGORY = "custom"

@Composable
fun MobileLiveTvScreen(
    onPlayChannel: (LiveTvChannel) -> Unit
) {
    val scope = rememberCoroutineScope()
    val connectedDevice by MobileCastManager.connectedDevice.collectAsState()
    var allChannels by remember { mutableStateOf<List<LiveTvChannel>>(emptyList()) }
    var customError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedCategory by rememberSaveable { mutableStateOf("all") }

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
        if (selectedCategory == "all") allChannels else allChannels.filter { it.category == selectedCategory }
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
                            "Sender antippen zum Abspielen"
                        },
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { reload(force = true) }, enabled = !isLoading) {
                    Icon(Icons.Default.Refresh, contentDescription = "Liste aktualisieren", tint = AccentCyan)
                }
            }
        }

        // Kategorien
        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LiveTvRepository.CATEGORIES, key = { it.id }) { cat ->
                    val isSelected = selectedCategory == cat.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isSelected) AccentCyan.copy(alpha = 0.18f) else BgSurface)
                            .border(
                                1.dp,
                                if (isSelected) AccentCyan else Color.White.copy(alpha = 0.1f),
                                RoundedCornerShape(18.dp)
                            )
                            .clickable { selectedCategory = cat.id }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "${cat.icon} ${cat.title}",
                            color = if (isSelected) AccentCyan else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (selectedCategory == CUSTOM_CATEGORY) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                CustomM3uCard(onSaved = { reload(force = true) })
            }
        }

        if (customError != null && (selectedCategory == CUSTOM_CATEGORY || selectedCategory == "all")) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(text = customError.orEmpty(), color = LiveRed, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }

        when {
            isLoading -> item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentCyan)
                }
            }

            filteredChannels.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = if (selectedCategory == CUSTOM_CATEGORY) {
                        "Noch keine eigene Liste. Gib oben den Link zu deiner M3U-Datei ein."
                    } else {
                        "Keine Sender in dieser Kategorie."
                    },
                    color = TextMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 32.dp)
                )
            }

            else -> items(filteredChannels, key = { it.id }) { channel ->
                MobileChannelCard(
                    channel = channel,
                    showTvButton = connectedDevice != null,
                    onClick = { onPlayChannel(channel) },
                    onPlayOnTv = { scope.launch { MobileCastManager.playLiveOnTv(channel) } }
                )
            }
        }
    }
}

@Composable
private fun CustomM3uCard(onSaved: () -> Unit) {
    val focusManager = LocalFocusManager.current
    var savedUrl by remember { mutableStateOf(LocalStorage.customM3uUrl) }
    var draft by remember { mutableStateOf(savedUrl) }
    var inputError by remember { mutableStateOf<String?>(null) }

    fun save(value: String) {
        focusManager.clearFocus()
        if (LiveTvRepository.saveCustomM3uUrl(value)) {
            savedUrl = LocalStorage.customM3uUrl
            draft = savedUrl
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
        Text(text = "📂 Eigene M3U-Liste", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "Link zu einer M3U- oder M3U8-Senderliste. Sie wird zusätzlich zu den festen Sendern geladen.",
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
            placeholder = { Text("http://server.de/liste.m3u", color = TextMuted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save(draft) }),
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
            if (savedUrl.isNotBlank()) {
                TextButton(onClick = { save("") }) {
                    Text("Entfernen", color = TextSecondary, fontSize = 13.sp)
                }
            }
            Button(
                onClick = { save(draft) },
                enabled = draft.trim() != savedUrl,
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
