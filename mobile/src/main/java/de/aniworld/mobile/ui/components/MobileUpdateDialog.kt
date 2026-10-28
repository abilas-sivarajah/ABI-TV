package de.aniworld.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.aniworld.mobile.ui.theme.AccentCyan
import de.aniworld.mobile.ui.theme.BgElevated
import de.aniworld.mobile.ui.theme.BgSurface
import de.aniworld.mobile.ui.theme.TextPrimary
import de.aniworld.mobile.ui.theme.TextSecondary
import de.aniworld.tv.data.update.AppRelease
import de.aniworld.tv.data.update.UpdateManager
import de.aniworld.tv.data.update.UpdateState
import java.util.Locale

private val ErrorRed = Color(0xFFFF5252)
private val OnAccent = Color(0xFF060911)

@Composable
fun MobileUpdateDialog(
    release: AppRelease,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentVersion = remember { UpdateManager.getCurrentVersionName(context) }
    val state by UpdateManager.state.collectAsState()
    val isBusy = state is UpdateState.Downloading || state == UpdateState.Verifying

    val dismissLater = {
        UpdateManager.snooze(release)
        UpdateManager.resetIfFinished()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = {
            when (state) {
                is UpdateState.Downloading, UpdateState.Verifying -> Unit
                UpdateState.Installing -> onDismiss()
                else -> dismissLater()
            }
        },
        containerColor = BgElevated,
        title = {
            Column {
                Text(
                    text = "🚀 Neues Update verfügbar!",
                    color = TextPrimary,
                    fontSize = 18.sp,
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
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Was ist neu:",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BgSurface)
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = release.changelog,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }

                when (val s = state) {
                    is UpdateState.Downloading -> ProgressBlock(
                        progress = s.progress.takeIf { s.totalBytes > 0 },
                        label = if (s.totalBytes > 0) {
                            "${formatMegabytes(s.downloadedBytes)} von ${formatMegabytes(s.totalBytes)} " +
                                "(${(s.progress * 100).toInt()} %)"
                        } else {
                            "Lade herunter… ${formatMegabytes(s.downloadedBytes)}"
                        }
                    )
                    UpdateState.Verifying -> ProgressBlock(progress = null, label = "Update wird geprüft…")
                    UpdateState.Installing -> ProgressBlock(
                        progress = null,
                        label = "Installation läuft – bitte im System-Dialog bestätigen."
                    )
                    UpdateState.NeedsInstallPermission -> StatusText(
                        text = "Damit ABI TV sich selbst aktualisieren kann, erlaube einmalig " +
                            "„Unbekannte Apps installieren“ und kehre dann zur App zurück.",
                        color = TextSecondary
                    )
                    is UpdateState.Failed -> StatusText(text = s.message, color = ErrorRed)
                    UpdateState.Idle -> Unit
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Downloading, UpdateState.Verifying -> Unit
                UpdateState.Installing -> OutlinedButton(onClick = onDismiss) {
                    Text(text = "Schließen", color = TextSecondary)
                }
                UpdateState.NeedsInstallPermission -> Button(
                    onClick = { UpdateManager.openInstallPermissionSettings(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                ) {
                    Text(text = "Einstellungen öffnen", color = OnAccent, fontWeight = FontWeight.Bold)
                }
                else -> Button(
                    onClick = { UpdateManager.startUpdate(context, release) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                ) {
                    Text(
                        text = if (state is UpdateState.Failed) "Erneut versuchen" else "Jetzt aktualisieren",
                        color = OnAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            when {
                isBusy -> OutlinedButton(onClick = { UpdateManager.cancelUpdate() }) {
                    Text(text = "Abbrechen", color = TextSecondary)
                }
                state == UpdateState.Installing -> Unit
                else -> OutlinedButton(onClick = dismissLater) {
                    Text(text = "Später", color = TextSecondary)
                }
            }
        }
    )
}

@Composable
private fun ProgressBlock(progress: Float?, label: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(16.dp))
        val barModifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = barModifier,
                color = AccentCyan,
                trackColor = BgSurface
            )
        } else {
            LinearProgressIndicator(modifier = barModifier, color = AccentCyan, trackColor = BgSurface)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun StatusText(text: String, color: Color) {
    Spacer(modifier = Modifier.height(10.dp))
    Text(text = text, color = color, fontSize = 12.sp, lineHeight = 17.sp)
}

private fun formatMegabytes(bytes: Long): String =
    String.format(Locale.GERMANY, "%.1f MB", bytes / (1024.0 * 1024.0))
