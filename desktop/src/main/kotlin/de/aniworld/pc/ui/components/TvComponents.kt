package de.aniworld.pc.ui.components

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.aniworld.pc.ui.compat.AsyncImage
import de.aniworld.tv.data.model.AnimeCard
import de.aniworld.tv.data.model.LanguageOption
import de.aniworld.tv.data.model.WatchProgressEntry
import de.aniworld.pc.ui.theme.AccentBlue
import de.aniworld.pc.ui.theme.AccentCyan
import de.aniworld.pc.ui.theme.BgElevated
import de.aniworld.pc.ui.theme.BgSurface
import de.aniworld.pc.ui.theme.FocusBorder
import de.aniworld.pc.ui.theme.TextMuted
import de.aniworld.pc.ui.theme.TextPrimary
import de.aniworld.pc.ui.theme.TextSecondary

/**
 * TV-optimierte Anime-Posterkarte mit 10-Foot-D-Pad-Fokus-Vergrößerung,
 * Leuchtrahmen und optionalem Favoriten-Indikator.
 */
@Composable
fun TvAnimeCard(
    anime: AnimeCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 152.dp,
    isBookmarked: Boolean = false,
    onFocused: (() -> Unit)? = null
) {
    var hasFocus by remember { mutableStateOf(false) }
    // Am PC hebt auch die Maus Karten und Buttons hervor (auf dem TV nur der Fokus)
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()
    val isFocused = hasFocus || isHovered
    // Wie der Fokus auf dem TV: das Hero-Banner zeigt die Karte unter der Maus
    LaunchedEffect(isHovered) { if (isHovered) onFocused?.invoke() }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.07f else 1.0f,
        animationSpec = tween(160),
        label = "cardScale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) FocusBorder else Color.Transparent,
        animationSpec = tween(160),
        label = "cardBorder"
    )

    Column(
        modifier = modifier
            .width(width)
            .scale(scale)
            .hoverable(hoverSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .onFocusChanged { state ->
                hasFocus = state.isFocused
                if (state.isFocused) onFocused?.invoke()
            }
            .clip(RoundedCornerShape(12.dp))
            .focusable()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(BgSurface)
                .border(
                    width = if (isFocused) 3.dp else 1.dp,
                    color = if (isFocused) borderColor else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            if (anime.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = anime.coverUrl,
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(BgElevated, BgSurface)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = anime.title.take(2).uppercase(),
                        color = AccentCyan,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Dezenter Verlauf unten für bessere Lesbarkeit
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )

            if (isBookmarked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(AccentBlue.copy(alpha = 0.92f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "★ MERKLISTE",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = anime.title,
            color = if (isFocused) AccentCyan else TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        if (anime.genreOrSubtitle.isNotBlank()) {
            Text(
                text = anime.genreOrSubtitle,
                color = TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
        }
    }
}

/**
 * Breite Weiterschauen-Karte mit Staffel-/Folgenanzeige und Fortschrittsbalken.
 */
@Composable
fun TvContinueWatchingCard(
    entry: WatchProgressEntry,
    onClick: () -> Unit,
    onDetailClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var hasFocus by remember { mutableStateOf(false) }
    // Am PC hebt auch die Maus Karten und Buttons hervor (auf dem TV nur der Fokus)
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()
    val isFocused = hasFocus || isHovered
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(160),
        label = "continueScale"
    )

    Row(
        modifier = modifier
            .width(300.dp)
            .height(110.dp)
            .scale(scale)
            .hoverable(hoverSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .onFocusChanged { hasFocus = it.isFocused }
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) BgElevated else BgSurface)
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) FocusBorder else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp)
            )
            .focusable()
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(64.dp)
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(BgElevated)
        ) {
            if (entry.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = entry.coverUrl,
                    contentDescription = entry.animeTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = entry.animeTitle,
                color = if (isFocused) AccentCyan else TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val seasonPrefix = if (entry.seasonLabel.equals("Filme", true)) {
                "Film ${entry.episodeNumber}"
            } else {
                "S${entry.seasonLabel} • E${entry.episodeNumber}"
            }

            Text(
                text = "$seasonPrefix: ${entry.episodeTitle}",
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                LanguageOption.fromKey(entry.preferredLangKey)?.let { lang ->
                    LanguageBadge(lang)
                }
                val minsLeft = ((entry.durationMs - entry.positionMs).coerceAtLeast(0L) / 60000L)
                if (entry.durationMs > 0) {
                    Text(
                        text = if (minsLeft > 0) "noch $minsLeft Min." else "Fast fertig",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { entry.progressFraction.coerceIn(0.05f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = AccentCyan,
                trackColor = Color.White.copy(alpha = 0.15f)
            )
        }
    }
}

/**
 * TV-Button / Chip mit klar sichtbarem Fokus-Zustand für die Fernbedienung.
 */
@Composable
fun TvFocusableButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isPrimary: Boolean = false,
    badgeColor: Color? = null
) {
    var hasFocus by remember { mutableStateOf(false) }
    // Am PC hebt auch die Maus Karten und Buttons hervor (auf dem TV nur der Fokus)
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()
    val isFocused = hasFocus || isHovered
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1.0f,
        animationSpec = tween(140),
        label = "btnScale"
    )

    val bgColor = when {
        isFocused -> AccentCyan
        isPrimary -> AccentBlue
        isSelected -> BgElevated
        else -> BgSurface
    }
    val contentColor = when {
        isFocused -> Color(0xFF060911)
        isPrimary || isSelected -> TextPrimary
        else -> TextSecondary
    }
    val borderStroke = when {
        isFocused -> BorderStroke(2.5.dp, Color.White)
        isSelected -> BorderStroke(1.5.dp, AccentCyan)
        else -> BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    }

    Surface(
        modifier = modifier
            .scale(scale)
            .hoverable(hoverSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .onFocusChanged { hasFocus = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            // clickable ist selbst fokussierbar; ein zusätzliches focusable() davor würde einen zweiten
            // Fokus-Knoten erzeugen, auf dem OK/Enter nichts auslöst (z. B. nach focusRequester)
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = borderStroke
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (badgeColor != null) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isFocused) Color(0xFF060911) else badgeColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                color = contentColor,
                fontSize = 13.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isFocused || isSelected || isPrimary) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun LanguageBadge(language: LanguageOption, modifier: Modifier = Modifier) {
    val color = Color(language.badgeColorHex)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.22f))
            .border(1.dp, color.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = language.shortLabel,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
