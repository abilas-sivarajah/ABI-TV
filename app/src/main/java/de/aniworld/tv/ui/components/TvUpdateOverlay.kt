package de.aniworld.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.update.AppRelease
import de.aniworld.tv.data.update.UpdateManager
import de.aniworld.tv.ui.theme.AccentCyan
import de.aniworld.tv.ui.theme.BgElevated
import de.aniworld.tv.ui.theme.BgSurface
import de.aniworld.tv.ui.theme.TextMuted
import de.aniworld.tv.ui.theme.TextPrimary
import de.aniworld.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TvUpdateOverlay(
    release: AppRelease,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentVersion = remember { UpdateManager.getCurrentVersionName(context) }

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val updateButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { updateButtonFocusRequester.requestFocus() }
    }

    // Vollbild-Abdeckung mit Dimmung
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Klicks abfangen */ },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(580.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(BgElevated)
                .border(2.dp, AccentCyan.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                .padding(28.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = "🚀", fontSize = 28.sp)
                    Column {
                        Text(
                            text = "Neues Update verfügbar!",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Version ${release.versionName} bereit (Installiert: $currentVersion)",
                            color = AccentCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Changelog / Was ist neu
                Text(
                    text = "Was ist neu:",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BgSurface)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = release.changelog.ifBlank { "Allgemeine Verbesserungen und Stabilitätsupdates." },
                        color = TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Fortschritt oder Buttons
                if (isDownloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = AccentCyan,
                            trackColor = BgSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Lade Update herunter... ${(downloadProgress * 100).toInt()}%",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFFFF5252),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TvFocusableButton(
                            text = "Später",
                            onClick = {
                                LocalStorage.ignoredUpdateTag = release.tagName
                                onDismiss()
                            }
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        TvFocusableButton(
                            text = "Jetzt aktualisieren",
                            isPrimary = true,
                            modifier = Modifier.focusRequester(updateButtonFocusRequester),
                            onClick = {
                                isDownloading = true
                                errorMessage = null
                                scope.launch {
                                    UpdateManager.downloadAndInstall(
                                        context = context,
                                        release = release,
                                        onProgress = { downloadProgress = it },
                                        onError = {
                                            isDownloading = false
                                            errorMessage = it
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
