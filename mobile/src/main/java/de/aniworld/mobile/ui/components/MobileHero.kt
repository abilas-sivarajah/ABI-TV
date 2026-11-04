package de.aniworld.mobile.ui.components

import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.aniworld.mobile.ui.theme.AccentCyan
import de.aniworld.mobile.ui.theme.BgElevated
import de.aniworld.mobile.ui.theme.BgPrimary
import de.aniworld.mobile.ui.theme.BgSurface
import de.aniworld.mobile.ui.theme.TextPrimary
import de.aniworld.mobile.ui.theme.TextSecondary
import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.model.AnimeCard
import de.aniworld.tv.data.model.AnimeDetail
import de.aniworld.tv.data.model.EpisodeItem
import de.aniworld.tv.data.model.LanguageOption
import de.aniworld.tv.data.remote.TitlePreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Startet einen Titel direkt im Handy-Player (Details, Folge, Sprache). */
typealias MobilePlayTitle = (AnimeDetail, EpisodeItem, LanguageOption) -> Unit

private val ErrorAmber = Color(0xFFF59E0B)

/**
 * Titelbild als Karussell im Hochformat: zum Wischen, blättert alle 9 s von selbst weiter,
 * solange der Finger nicht darauf liegt. Die Punkte zeigen die Position.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MobileHeroCarousel(
    items: List<AnimeCard>,
    watchlistSlugs: Set<String>,
    onOpenDetail: (AnimeCard) -> Unit,
    onPlayTitle: MobilePlayTitle,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()

    // Nicht von currentPage abhängig machen: Das wechselt mitten im Blättern, der neu gestartete
    // Effekt würde die laufende Animation abbrechen und das Bild bliebe zwischen zwei Seiten stehen
    LaunchedEffect(items, isDragged) {
        if (items.size < 2 || isDragged) return@LaunchedEffect
        while (true) {
            delay(TitlePreview.HERO_ROTATE_MS)
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % items.size)
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            key = { items[it].cleanSlug },
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val anime = items[page]
            MobileHeroPage(
                anime = anime,
                isBookmarked = anime.cleanSlug in watchlistSlugs,
                onOpenDetail = { onOpenDetail(anime) },
                onPlayTitle = onPlayTitle
            )
        }

        if (items.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.indices.forEach { i ->
                    val active = i == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .width(if (active) 18.dp else 6.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (active) Color.White else Color.White.copy(alpha = 0.35f))
                    )
                }
            }
        }
    }
}

/**
 * Eine Seite des Titelbilds: oben das breite Hintergrundbild, darunter mittig Titel, Eckdaten und
 * Knöpfe. Ohne breites Bild wird das Cover weichgezeichnet als Hintergrund und scharf als Poster gezeigt.
 */
@Composable
private fun MobileHeroPage(
    anime: AnimeCard,
    isBookmarked: Boolean,
    onOpenDetail: () -> Unit,
    onPlayTitle: MobilePlayTitle
) {
    val slug = anime.cleanSlug
    var detail by remember(slug) { mutableStateOf(TitlePreview.cachedDetail(slug)) }
    var artwork by remember(slug) { mutableStateOf(TitlePreview.cachedArtwork(slug)) }
    LaunchedEffect(slug) {
        if (detail == null) detail = TitlePreview.loadDetail(slug)
        if (artwork == null) artwork = TitlePreview.loadArtwork(anime, detail)
    }

    // Hintergrund: TMDB, sonst das Bild der Seite – aber nur, wenn es wirklich querformatig ist
    val backdropUrl = artwork?.backdropUrl
        ?: detail?.backdropUrl?.takeIf { it.isNotBlank() && it != anime.coverUrl }
    var isWideBackdrop by remember(backdropUrl) { mutableStateOf(false) }
    val logoUrl = artwork?.logoUrl
    var isLogoLoaded by remember(logoUrl) { mutableStateOf(false) }

    val play = rememberPlayAction(slug, onPlayTitle)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val heroHeight = (maxWidth * 1.28f).coerceIn(440.dp, 560.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .clickable { onOpenDetail() }
        ) {
            if (!isWideBackdrop && anime.coverUrl.isNotBlank()) {
                // blur gibt es erst ab Android 12; darunter das Bild stärker abdunkeln
                val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                AsyncImage(
                    model = anime.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = if (canBlur) 0.75f else 0.35f,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (canBlur) Modifier.blur(40.dp) else Modifier)
                )
            }
            if (backdropUrl != null) {
                AsyncImage(
                    model = backdropUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    alpha = if (isWideBackdrop) 1f else 0f,
                    onSuccess = { state ->
                        val image = state.result.drawable
                        isWideBackdrop = image.intrinsicWidth >= image.intrinsicHeight * 1.4f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.72f)
                )
            }

            // Nach unten in den Seitenhintergrund übergehen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to BgPrimary.copy(alpha = 0.15f),
                            0.42f to BgPrimary.copy(alpha = 0.25f),
                            // Deckend, bevor das Hintergrundbild endet, sonst bleibt eine harte Kante
                            0.70f to BgPrimary,
                            1f to BgPrimary
                        )
                    )
            )

            if (!isWideBackdrop && anime.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = anime.coverUrl,
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .height(heroHeight * 0.52f)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = TitlePreview.heroLabel(anime, detail),
                    color = AccentCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(6.dp))

                val heroTitle = detail?.title?.takeIf { it.isNotBlank() } ?: anime.title
                if (logoUrl != null) {
                    // Titel-Logo von TMDB statt Schrift (wie bei Streaming-Apps)
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = heroTitle,
                        contentScale = ContentScale.Fit,
                        onSuccess = { isLogoLoaded = true },
                        onError = { isLogoLoaded = false },
                        modifier = if (isLogoLoaded) {
                            Modifier.widthIn(max = 280.dp).heightIn(max = 84.dp)
                        } else {
                            Modifier.size(1.dp)
                        }
                    )
                }
                if (!isLogoLoaded) {
                    Text(
                        text = heroTitle,
                        color = TextPrimary,
                        fontSize = 26.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val meta = TitlePreview.metaLine(anime, detail)
                if (meta.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = meta.joinToString("  •  "),
                        color = TextPrimary.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoundIconButton(
                        icon = if (isBookmarked) Icons.Filled.Check else Icons.Filled.Add,
                        description = if (isBookmarked) "Auf der Merkliste" else "Zur Merkliste",
                        onClick = { LocalStorage.toggleWatchlist(anime) }
                    )
                    PlayPillButton(
                        label = play.label,
                        onClick = play.start
                    )
                    RoundIconButton(
                        icon = Icons.Outlined.Info,
                        description = "Details",
                        onClick = onOpenDetail
                    )
                }

                play.error?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = it, color = ErrorAmber, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/**
 * Info-Feld zu einem Titel. Am PC erscheint es als Kachel in der Reihe; auf dem schmalen
 * Handy-Bildschirm wäre dort kein Platz, deshalb kommt es als Blatt von unten.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MobileTitleSheet(
    anime: AnimeCard,
    isBookmarked: Boolean,
    onDismiss: () -> Unit,
    onOpenDetail: () -> Unit,
    onPlayTitle: MobilePlayTitle
) {
    val slug = anime.cleanSlug
    var detail by remember(slug) { mutableStateOf(TitlePreview.cachedDetail(slug)) }
    LaunchedEffect(slug) {
        if (detail == null) detail = TitlePreview.loadDetail(slug)
    }
    val play = rememberPlayAction(slug) { loaded, episode, language ->
        onDismiss()
        onPlayTitle(loaded, episode, language)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = BgElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
        ) {
            Row {
                AsyncImage(
                    model = detail?.coverUrl?.takeIf { it.isNotBlank() } ?: anime.coverUrl,
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(108.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgSurface)
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = detail?.title?.takeIf { it.isNotBlank() } ?: anime.title,
                        color = TextPrimary,
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TitlePreview.chips(anime, detail).forEach { chip ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(BgSurface)
                                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = chip,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    val genreLine = TitlePreview.genreLine(anime, detail)
                    if (genreLine.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = TextSecondary, fontWeight = FontWeight.Medium)) { append("Genre: ") }
                                withStyle(SpanStyle(color = AccentCyan, fontWeight = FontWeight.SemiBold)) { append(genreLine) }
                            },
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            detail?.description?.takeIf { it.isNotBlank() }?.let { description ->
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = description,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayPillButton(
                    label = play.label,
                    onClick = play.start,
                    modifier = Modifier.weight(1f)
                )
                RoundIconButton(
                    icon = if (isBookmarked) Icons.Filled.Check else Icons.Filled.Add,
                    description = if (isBookmarked) "Auf der Merkliste" else "Zur Merkliste",
                    onClick = { LocalStorage.toggleWatchlist(anime) }
                )
                RoundIconButton(
                    icon = Icons.Outlined.Info,
                    description = "Details",
                    onClick = {
                        onDismiss()
                        onOpenDetail()
                    }
                )
            }

            play.error?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = it, color = ErrorAmber, fontSize = 12.sp)
            }
        }
    }
}

/** Zustand des "Abspielen"-Knopfs: Beschriftung, Fehlermeldung und Start. */
private class PlayAction(val label: String, val error: String?, val start: () -> Unit)

/** Startet den Film bzw. die zuletzt geschaute Folge (sonst Folge 1) direkt im Player. */
@Composable
private fun rememberPlayAction(slug: String, onPlayTitle: MobilePlayTitle): PlayAction {
    val scope = rememberCoroutineScope()
    val progressList by LocalStorage.progressFlow.collectAsState()
    val savedProgress = remember(progressList, slug) { LocalStorage.getProgressForAnime(slug) }
    var isStarting by remember(slug) { mutableStateOf(false) }
    var error by remember(slug) { mutableStateOf<String?>(null) }

    return PlayAction(
        label = when {
            isStarting -> "Lädt …"
            savedProgress != null -> "Weiterschauen"
            else -> "Abspielen"
        },
        error = error,
        start = start@{
            if (isStarting) return@start
            scope.launch {
                isStarting = true
                error = null
                val target = TitlePreview.resolvePlayback(slug)
                isStarting = false
                when (target) {
                    is TitlePreview.PlayTarget.Ready -> onPlayTitle(target.detail, target.episode, target.language)
                    is TitlePreview.PlayTarget.Failed -> error = target.message
                }
            }
        }
    )
}

/** Weiß gefüllter Hauptknopf. */
@Composable
private fun PlayPillButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color(0xFF0B0E14),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = Color(0xFF0B0E14),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/** Halbtransparenter runder Knopf (Merkliste, Details). */
@Composable
private fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = TextPrimary,
            modifier = Modifier.size(22.dp)
        )
    }
}
