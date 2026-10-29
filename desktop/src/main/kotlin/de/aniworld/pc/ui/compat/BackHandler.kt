package de.aniworld.pc.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Ersatz für den Android-BackHandler. Ausgelöst wird "Zurück" am PC über Esc oder die
 * Zurück-Taste der Maus; wie auf Android gewinnt der zuletzt hinzugekommene aktive Handler.
 */
object BackDispatcher {

    internal class Entry {
        var enabled: Boolean = true
        var onBack: () -> Unit = {}
    }

    private val entries = mutableListOf<Entry>()

    internal fun register(entry: Entry) {
        entries += entry
    }

    internal fun unregister(entry: Entry) {
        entries -= entry
    }

    /** Gibt false zurück, wenn kein Bildschirm "Zurück" behandelt hat. */
    fun dispatch(): Boolean {
        val entry = entries.lastOrNull { it.enabled } ?: return false
        entry.onBack()
        return true
    }
}

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val currentOnBack by rememberUpdatedState(onBack)
    val entry = remember { BackDispatcher.Entry() }
    entry.enabled = enabled
    entry.onBack = { currentOnBack() }
    DisposableEffect(entry) {
        BackDispatcher.register(entry)
        onDispose { BackDispatcher.unregister(entry) }
    }
}
