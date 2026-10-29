package de.aniworld.pc.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.WindowState

/** Fensterzustand, z. B. damit der Player in den Vollbildmodus wechseln kann. */
val LocalWindowState = staticCompositionLocalOf<WindowState> { error("Kein Fenster") }

/**
 * Tastenkürzel für das ganze Fenster: Bildschirme wie der Player melden sich hier an,
 * damit Leertaste & Co. auch dann ankommen, wenn gerade das Videobild angeklickt wurde.
 * Esc löst – wenn niemand sonst reagiert – "Zurück" aus.
 */
object KeyDispatcher {

    private val handlers = mutableListOf<(KeyEvent) -> Boolean>()

    internal fun register(handler: (KeyEvent) -> Boolean) {
        handlers += handler
    }

    internal fun unregister(handler: (KeyEvent) -> Boolean) {
        handlers -= handler
    }

    fun dispatch(event: KeyEvent): Boolean {
        for (handler in handlers.asReversed()) {
            if (handler(event)) return true
        }
        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
            return BackDispatcher.dispatch()
        }
        return false
    }
}

@Composable
fun WindowKeyHandler(onKey: (KeyEvent) -> Boolean) {
    val currentOnKey by rememberUpdatedState(onKey)
    val handler = remember { { event: KeyEvent -> currentOnKey(event) } }
    DisposableEffect(handler) {
        KeyDispatcher.register(handler)
        onDispose { KeyDispatcher.unregister(handler) }
    }
}
