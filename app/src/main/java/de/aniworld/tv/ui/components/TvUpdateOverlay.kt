package de.aniworld.tv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import de.aniworld.tv.data.update.AppRelease
import de.aniworld.tv.data.update.UpdateManager
import de.aniworld.tv.data.update.UpdateState
import de.aniworld.tv.ui.theme.AccentCyan
import de.aniworld.tv.ui.theme.BgElevated
import de.aniworld.tv.ui.theme.BgSurface
import de.aniworld.tv.ui.theme.TextPrimary
import de.aniworld.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.util.Locale

private val ErrorRed = Color(0xFFFF5252)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvUpdateOverlay(
    release: AppRelease,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentVersion = remember { UpdateManager.getCurrentVersionName(context) }
    val state by UpdateManager.state.collectAsState()

    // Bei jedem Zustandswechsel tauschen die Buttons: Fokus neu setzen, sonst reagiert die Fernbedienung nicht
    val primaryFocus = remember { FocusRequester() }
    LaunchedEffect(state::class) {
        delay(150)
        runCatching { primaryFocus.requestFocus() }
    }

    val dismissLater = {
        UpdateManager.snooze(release)
        UpdateManager.resetIfFinished()
        onDismiss()
    }

    BackHandler {
        when (state) {
            is UpdateState.Downloading, UpdateState.Verifying -> UpdateManager.cancelUpdate()
            UpdateState.Installing -> onDismiss()
            else -> dismissLater()
        }
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
                // Fokus im Dialog halten, damit das Steuerkreuz nicht in die Seite dahinter springt
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup()
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
                            text = buildString {
                                append("Version ${release.versionName}")
                                if (release.fileSize > 0) append(" · ${formatMegabytes(release.fileSize)}")
                                append(" · installiert: $currentVersion")
                            },
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
                        .heightIn(max = 160.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BgSurface)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = release.changelog,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                when (val s = state) {
                    is UpdateState.Downloading -> {
                        ProgressBlock(
                            progress = s.progress.takeIf { s.totalBytes > 0 },
                            label = if (s.totalBytes > 0) {
                                "Lade herunter… ${formatMegabytes(s.downloadedBytes)} von " +
                                    "${formatMegabytes(s.totalBytes)} (${(s.progress * 100).toInt()} %)"
                            } else {
                                "Lade herunter… ${formatMegabytes(s.downloadedBytes)}"
                            }
                        )
                        ButtonRow {
                            TvFocusableButton(
                                text = "Abbrechen",
                                modifier = Modifier.focusRequester(primaryFocus),
                                onClick = { UpdateManager.cancelUpdate() }
                            )
                        }
                    }

                    UpdateState.Verifying -> {
                        ProgressBlock(progress = null, label = "Update wird geprüft…")
                    }

                    UpdateState.NeedsInstallPermission -> {
                        Text(
                            text = "Damit ABI TV sich selbst aktualisieren kann, erlaube einmalig " +
                                "„Unbekannte Apps installieren“ für ABI TV und kehre dann mit Zurück hierher zurück.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        ButtonRow {
                            TvFocusableButton(
                                text = "Abbrechen",
                                onClick = dismissLater
                            )
                            TvFocusableButton(
                                text = "Einstellungen öffnen",
                                isPrimary = true,
                                modifier = Modifier.focusRequester(primaryFocus),
                                onClick = { UpdateManager.openInstallPermissionSettings(context) }
                            )
                        }
                    }

                    UpdateState.Installing -> {
                        ProgressBlock(
                            progress = null,
                            label = "Installation läuft – bitte im System-Dialog bestätigen."
                        )
                        ButtonRow {
                            TvFocusableButton(
                                text = "Schließen",
                                modifier = Modifier.focusRequester(primaryFocus),
                                onClick = onDismiss
                            )
                        }
                    }

                    UpdateState.Idle, is UpdateState.Failed -> {
                        if (s is UpdateState.Failed) {
                            Text(
                                text = s.message,
                                color = ErrorRed,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                        ButtonRow {
                            TvFocusableButton(
                                text = "Später",
                                onClick = dismissLater
                            )
                            TvFocusableButton(
                                text = if (s is UpdateState.Failed) "Erneut versuchen" else "Jetzt aktualisieren",
                                isPrimary = true,
                                modifier = Modifier.focusRequester(primaryFocus),
                                onClick = { UpdateManager.startUpdate(context, release) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressBlock(progress: Float?, label: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val barModifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = barModifier,
                color = AccentCyan,
                trackColor = BgSurface
            )
        } else {
            LinearProgressIndicator(
                modifier = barModifier,
                color = AccentCyan,
                trackColor = BgSurface
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(text = label, color = TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun ButtonRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

private fun formatMegabytes(bytes: Long): String =
    String.format(Locale.GERMANY, "%.1f MB", bytes / (1024.0 * 1024.0))
