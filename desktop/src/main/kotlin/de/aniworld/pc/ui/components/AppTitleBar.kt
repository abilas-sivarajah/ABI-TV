package de.aniworld.pc.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import de.aniworld.pc.ui.compat.LocalComposeWindow
import de.aniworld.pc.ui.compat.LocalWindowState
import de.aniworld.pc.ui.theme.TextPrimary
import java.awt.MouseInfo

/** Gleicher Ton wie der obere Rand der Navigationsleiste, damit beides nahtlos ineinander übergeht. */
val TitleBarBackground = Color(0xFF05070B)
private val CloseHover = Color(0xFFE81123)

/**
 * Eigene Titelleiste statt der weißen von Windows: App-Logo und Name links, Fensterknöpfe rechts.
 * Ziehen verschiebt das Fenster, Doppelklick maximiert bzw. stellt wieder her.
 */
@Composable
fun AppTitleBar(icon: Painter?, onClose: () -> Unit) {
    val window = LocalComposeWindow.current
    val windowState = LocalWindowState.current
    val isMaximized = windowState.placement == WindowPlacement.Maximized

    fun toggleMaximize() {
        windowState.placement = if (isMaximized) WindowPlacement.Floating else WindowPlacement.Maximized
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(TitleBarBackground),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Ziehbereich: alles außer den Fensterknöpfen
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(window) {
                    if (window == null) return@pointerInput
                    // Position des Mauszeigers im Fenster als Anteil – so bleibt er auch beim
                    // Herausziehen aus "Maximiert" an der passenden Stelle der Leiste
                    var ratioX = 0.0
                    var offsetY = 0
                    detectDragGestures(
                        onDragStart = {
                            val mouse = MouseInfo.getPointerInfo()?.location ?: return@detectDragGestures
                            ratioX = (mouse.x - window.x).toDouble() / window.width.coerceAtLeast(1)
                            offsetY = mouse.y - window.y
                            if (windowState.placement == WindowPlacement.Maximized) {
                                // Direkt am Fenster, damit das Verschieben schon die normale Größe trifft
                                window.placement = WindowPlacement.Floating
                                windowState.placement = WindowPlacement.Floating
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (windowState.placement != WindowPlacement.Floating) return@detectDragGestures
                            val mouse = MouseInfo.getPointerInfo()?.location ?: return@detectDragGestures
                            window.setLocation(mouse.x - (ratioX * window.width).toInt(), mouse.y - offsetY)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { toggleMaximize() })
                }
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Nur das Logo – der Schriftzug "ABI TV" steht schon in der Navigationsleiste darunter
            if (icon != null) {
                Image(painter = icon, contentDescription = "ABI TV", modifier = Modifier.size(18.dp))
            }
        }

        TitleBarButton(
            icon = Icons.Filled.Remove,
            description = "Minimieren",
            onClick = { windowState.isMinimized = true }
        )
        TitleBarButton(
            icon = if (isMaximized) Icons.Filled.FilterNone else Icons.Filled.CropSquare,
            description = if (isMaximized) "Wiederherstellen" else "Maximieren",
            onClick = { toggleMaximize() }
        )
        TitleBarButton(
            icon = Icons.Filled.Close,
            description = "Schließen",
            hoverColor = CloseHover,
            onClick = onClose
        )
    }
}

@Composable
private fun TitleBarButton(
    icon: ImageVector,
    description: String,
    hoverColor: Color = Color.White.copy(alpha = 0.08f),
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .width(46.dp)
            .fillMaxHeight()
            .background(if (hovered) hoverColor else Color.Transparent)
            .hoverable(interaction)
            // Fensterknöpfe sollen der Tastatur-/Fernbedienungs-Navigation nicht im Weg sein
            .focusProperties { canFocus = false }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (hovered) Color.White else TextPrimary.copy(alpha = 0.85f),
            modifier = Modifier.size(16.dp)
        )
    }
}
