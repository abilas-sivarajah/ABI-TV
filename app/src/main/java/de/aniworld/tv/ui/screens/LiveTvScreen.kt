package de.aniworld.tv.ui.screens

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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.launch

@Composable
fun LiveTvScreen(
    onPlayChannel: (LiveTvChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    var allChannels by remember { mutableStateOf<List<LiveTvChannel>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf("all") }
    var isLoading by remember { mutableStateOf(true) }
    var focusedChannel by remember { mutableStateOf<LiveTvChannel?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        allChannels = LiveTvRepository.loadChannels()
        isLoading = false
    }

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
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFFEF4444))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LIVE TV & SPORT CHANNELS",
                        color = Color(0xFFEF4444),
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

            // M3U Reload / Info Badge
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvFocusableButton(
                    text = "🔄 Liste aktualisieren",
                    onClick = {
                        scope.launch {
                            isLoading = true
                            allChannels = LiveTvRepository.loadChannels()
                            isLoading = false
                        }
                    }
                )
            }
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
                    text = "Keine Sender in dieser Kategorie gefunden.",
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

@Composable
fun TvChannelCard(
    channel: LiveTvChannel,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
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
            .focusable()
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
            if (channel.logoUrl.isNotBlank()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
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
