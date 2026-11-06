package de.aniworld.pc.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import java.awt.Rectangle

/**
 * Vollbild als randloses Fenster über den ganzen Bildschirm, auf dem das Fenster gerade liegt.
 * Nicht das "echte" Vollbild von Windows (WindowPlacement.Fullscreen): Das minimiert sich, sobald man
 * auf einen anderen Bildschirm klickt.
 */
object AppFullscreen {
    var active by mutableStateOf(false)
        private set

    private var savedPlacement = WindowPlacement.Maximized
    private var savedBounds: Rectangle? = null

    fun enter(window: ComposeWindow, state: WindowState) {
        if (active) return
        // Bildschirm merken, bevor sich das Fenster beim Ent-Maximieren evtl. verschiebt
        val screen = window.graphicsConfiguration.bounds
        savedPlacement = state.placement
        savedBounds = if (state.placement == WindowPlacement.Floating) window.bounds else null
        if (window.placement != WindowPlacement.Floating) window.placement = WindowPlacement.Floating
        state.placement = WindowPlacement.Floating
        window.bounds = screen
        active = true
        window.toFront()
    }

    fun exit(window: ComposeWindow?, state: WindowState) {
        if (!active) return
        active = false
        // App wird gerade geschlossen: Fenster ist schon weg, dann nichts mehr umschalten
        if (window == null || !window.isDisplayable) return
        savedBounds?.let { window.bounds = it }
        if (savedPlacement == WindowPlacement.Maximized) {
            window.placement = WindowPlacement.Maximized
            state.placement = WindowPlacement.Maximized
        }
    }
}
