package de.aniworld.pc.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.aniworld.pc.ui.compat.LocalComposeWindow
import java.awt.Rectangle
import java.awt.geom.RoundRectangle2D
import javax.swing.JWindow

/**
 * Knopf über dem Videobild (z. B. "Intro überspringen", "Nächste Folge").
 *
 * Über das VLC-Bild kann Compose nicht zeichnen. Darum ist der Knopf ein eigenes kleines Fenster,
 * das genau über der rechten unteren Ecke des Bildes liegt. Es gehört zum App-Fenster (bleibt also
 * nur über diesem, nicht über anderen Programmen) und nimmt keinen Tastaturfokus.
 *
 * @param videoBounds Lage des Videobilds auf dem Bildschirm (AWT-Koordinaten); null = ausblenden
 */
@Composable
fun VideoOverlayButton(
    visible: Boolean,
    label: String,
    icon: ImageVector,
    videoBounds: Rectangle?,
    onClick: () -> Unit
) {
    val owner = LocalComposeWindow.current ?: return
    // Inhalt des Fensters liest diese Zustände (eigene Komposition im ComposePanel)
    val labelState = remember { mutableStateOf(label) }
    val iconState = remember { mutableStateOf(icon) }
    val clickState = remember { mutableStateOf(onClick) }
    labelState.value = label
    iconState.value = icon
    clickState.value = onClick

    val window = remember {
        JWindow(owner).apply {
            focusableWindowState = false
            background = java.awt.Color(0x0B, 0x0E, 0x14)
            val panel = ComposePanel().apply {
                setContent {
                    OverlayContent(label = labelState.value, icon = iconState.value, onClick = { clickState.value() })
                }
            }
            contentPane.add(panel)
        }
    }
    DisposableEffect(Unit) {
        onDispose { window.dispose() }
    }

    LaunchedEffect(visible, videoBounds, label) {
        val bounds = videoBounds
        if (!visible || bounds == null || bounds.width < 300 || bounds.height < 200) {
            window.isVisible = false
            return@LaunchedEffect
        }
        val width = (label.length * 9 + 90).coerceIn(200, 320)
        val height = 54
        window.setSize(width, height)
        window.shape = RoundRectangle2D.Double(0.0, 0.0, width.toDouble(), height.toDouble(), 14.0, 14.0)
        window.setLocation(bounds.x + bounds.width - width - 40, bounds.y + bounds.height - height - 40)
        if (!window.isVisible) window.isVisible = true
    }
}

@Composable
private fun OverlayContent(label: String, icon: ImageVector, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(if (hovered) Color(0xFFE6E6E6) else Color.White)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF0B0E14), modifier = Modifier.size(26.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = label, color = Color(0xFF0B0E14), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
