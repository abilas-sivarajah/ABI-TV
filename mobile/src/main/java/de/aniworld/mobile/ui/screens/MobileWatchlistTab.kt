package de.aniworld.mobile.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.aniworld.mobile.ui.components.MobileContinueCard
import de.aniworld.mobile.ui.theme.AccentCyan
import de.aniworld.mobile.ui.theme.BgPrimary
import de.aniworld.mobile.ui.theme.BgSurface
import de.aniworld.mobile.ui.theme.TextMuted
import de.aniworld.mobile.ui.theme.TextPrimary
import de.aniworld.mobile.ui.theme.TextSecondary
import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.model.AnimeCard
import de.aniworld.tv.data.model.WatchProgressEntry
import de.aniworld.tv.data.remote.LetterboxdLists
import de.aniworld.tv.platform.LetterboxdAccountAndroid
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private val LetterboxdGreen = Color(0xFF00E054)
private val WarningAmber = Color(0xFFF59E0B)

/** Beliebte Listen und geladene Letterboxd-Filme pro Sitzung merken (Reiterwechsel lädt nicht neu). */
private var popularListsCache: List<LetterboxdLists.ListInfo>? = null
private val filmsCache = ConcurrentHashMap<String, List<LetterboxdLists.Film>>()

/**
 * Watchlist-Reiter wie auf TV und PC: Weiterschauen, eigene Merkliste, Letterboxd-Watchlists,
 * "Ilkems Empfehlungen" und beliebte Listen – nur Filme, die es hier auch zu sehen gibt.
 */
@Composable
internal fun MobileWatchlistTab(
    watchlist: List<AnimeCard>,
    continueWatching: List<WatchProgressEntry>,
    onShowTitle: (AnimeCard) -> Unit,
    onResumeWatch: (WatchProgressEntry) -> Unit
) {
    val watchlistSlugs = remember(watchlist) { watchlist.map { it.cleanSlug }.toSet() }
    val letterboxdUsers by LocalStorage.letterboxdUsersFlow.collectAsState()
    // Empfehlungsliste bei jedem Öffnen frisch laden (sie ändert sich, wenn Ilkem am PC etwas hinzufügt)
    val featuredListVersion by LetterboxdAccountAndroid.listVersion.collectAsState()
    val featuredKey = remember(featuredListVersion) {
        filmsCache.keys.removeIf { it.startsWith("list:${LetterboxdLists.FeaturedList.PATH}#") }
        "list:${LetterboxdLists.FeaturedList.PATH}#${System.currentTimeMillis()}"
    }

    var popularLists by remember { mutableStateOf(popularListsCache) }
    var popularError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (popularLists != null) return@LaunchedEffect
        // Eine mehr laden, falls die eigene Empfehlungsliste darunter ist (sie steht ohnehin oben)
        runCatching { LetterboxdLists.popularLists(limit = 5).filter { it.path != LetterboxdLists.FeaturedList.PATH }.take(4) }
            .onSuccess {
                popularLists = it
                popularListsCache = it
            }
            .onFailure { popularError = "Letterboxd-Listen nicht erreichbar (${it.message})." }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        if (continueWatching.isNotEmpty()) {
            item(key = "wl_continue") {
                Column {
                    SectionTitle("▶ Weiterschauen")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(continueWatching, key = { it.animeSlug }) { entry ->
                            MobileContinueCard(entry = entry, onClick = { onResumeWatch(entry) })
                        }
                    }
                }
            }
        }

        item(key = "wl_own") {
            if (watchlist.isNotEmpty()) {
                MobileCatalogRow(
                    rowKey = "wl_own",
                    title = "★ Meine Merkliste (${watchlist.size})",
                    items = watchlist,
                    watchlistSlugs = watchlistSlugs,
                    onShowTitle = onShowTitle
                )
            } else {
                Column {
                    SectionTitle("★ Meine Merkliste")
                    Text(
                        text = "Noch leer – füge Titel über „+“ im Info-Feld hinzu.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }

        item(key = "wl_lb_header") {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(LetterboxdGreen))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Letterboxd-Watchlists", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Gib deinen oder einen anderen Letterboxd-Namen ein. Angezeigt werden die Filme der Watchlist, " +
                        "die es bei Filmpalast zum Streamen gibt.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                LetterboxdUserInput()
                if (letterboxdUsers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(letterboxdUsers, key = { it }) { user ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(BgSurface)
                                    .clickable { LocalStorage.removeLetterboxdUser(user) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(text = "@$user  ✕", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }

        items(letterboxdUsers, key = { "wl_lb_user_$it" }) { user ->
            LetterboxdFilmsRow(
                cacheKey = "user:$user",
                title = "🎬 Watchlist von @$user",
                watchlistSlugs = watchlistSlugs,
                onShowTitle = onShowTitle,
                load = { LetterboxdLists.watchlist(user, maxPages = 3) }
            )
        }

        item(key = "wl_lb_lists_header") {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(text = "Interessante Letterboxd-Listen", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = popularError ?: if (popularLists == null) {
                        "Lade beliebte Listen …"
                    } else {
                        "Beliebte Listen der Letterboxd-Community – nur Filme, die es hier gibt."
                    },
                    color = if (popularError != null) WarningAmber else TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }

        // Eigene Empfehlungsliste immer zuerst
        item(key = "wl_lb_featured") {
            LetterboxdFilmsRow(
                cacheKey = featuredKey,
                title = "⭐ ${LetterboxdLists.FeaturedList.NAME}",
                watchlistSlugs = watchlistSlugs,
                onShowTitle = onShowTitle,
                load = { LetterboxdLists.listFilms(LetterboxdLists.FeaturedList.PATH, maxPages = 2).take(100) }
            )
        }

        items(popularLists.orEmpty(), key = { "wl_lb_list_${it.path}" }) { list ->
            LetterboxdFilmsRow(
                cacheKey = "list:${list.path}",
                title = "📋 ${list.name} · von ${list.owner}",
                watchlistSlugs = watchlistSlugs,
                onShowTitle = onShowTitle,
                load = { LetterboxdLists.listFilms(list.path, maxPages = 1).take(60) }
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = TextPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

/** Eingabe für einen Letterboxd-Benutzernamen (oder Profil-Link). */
@Composable
private fun LetterboxdUserInput() {
    val focusManager = LocalFocusManager.current
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val name = LetterboxdLists.normalizeUsername(text)
        if (name == null) {
            error = "Bitte einen Letterboxd-Benutzernamen eingeben (z. B. dave) oder den Profil-Link."
            return
        }
        LocalStorage.addLetterboxdUser(name)
        text = ""
        error = null
        focusManager.clearFocus()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                error = null
            },
            placeholder = { Text("Benutzername, z. B. dave", color = TextMuted, fontSize = 14.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentCyan,
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = { submit() },
            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgPrimary)
        ) {
            Text("+", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
    error?.let {
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = it, color = WarningAmber, fontSize = 12.sp)
    }
}

/**
 * Reihe mit Filmen einer Letterboxd-Liste/-Watchlist. Lädt die Liste, gleicht jeden Film mit
 * Filmpalast ab und zeigt nach und nach die, die sich streamen lassen.
 */
@Composable
private fun LetterboxdFilmsRow(
    cacheKey: String,
    title: String,
    watchlistSlugs: Set<String>,
    onShowTitle: (AnimeCard) -> Unit,
    load: suspend () -> List<LetterboxdLists.Film>
) {
    var films by remember(cacheKey) { mutableStateOf(filmsCache[cacheKey]) }
    var error by remember(cacheKey) { mutableStateOf<String?>(null) }
    // Abgleich-Ergebnisse in Listen-Reihenfolge (null = noch offen oder nicht vorhanden)
    val matches = remember(cacheKey) { mutableStateMapOf<String, AnimeCard?>() }
    var checked by remember(cacheKey) { mutableIntStateOf(0) }

    LaunchedEffect(cacheKey) {
        val list = films ?: runCatching { load() }
            .onSuccess { filmsCache[cacheKey] = it }
            .getOrElse {
                error = when (it) {
                    is LetterboxdLists.UserNotFoundException -> "Diesen Letterboxd-Benutzer gibt es nicht."
                    else -> "Letterboxd nicht erreichbar (${it.message})."
                }
                return@LaunchedEffect
            }
        films = list
        coroutineScope {
            list.forEach { film ->
                launch {
                    matches[film.slug] = LetterboxdLists.findOnFilmpalast(film)
                    checked++
                }
            }
        }
    }

    val available = films.orEmpty().mapNotNull { matches[it.slug] }.distinctBy { it.cleanSlug }
    val total = films?.size ?: 0
    val currentError = error
    val status = when {
        currentError != null -> currentError
        films == null -> "Lade Liste …"
        total == 0 -> "Die Liste ist leer oder nicht öffentlich."
        checked < total -> "${available.size} von $total bei Filmpalast · prüfe ${total - checked} weitere …"
        available.isEmpty() -> "Keiner dieser Filme ist bei Filmpalast verfügbar."
        else -> "${available.size} von $total bei Filmpalast verfügbar"
    }

    if (available.isNotEmpty()) {
        Column {
            MobileCatalogRow(
                rowKey = cacheKey,
                title = title,
                items = available,
                watchlistSlugs = watchlistSlugs,
                onShowTitle = onShowTitle
            )
            Text(
                text = status,
                color = TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    } else {
        Column {
            SectionTitle(title)
            Text(
                text = status,
                color = if (currentError != null) WarningAmber else TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}
