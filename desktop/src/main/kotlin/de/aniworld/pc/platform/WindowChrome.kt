package de.aniworld.pc.platform

import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import de.aniworld.tv.platform.AppLog
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

/**
 * Hilfen für das Fenster ohne Windows-Titelleiste (die App zeichnet ihre eigene):
 * - "Maximiert" darf die Taskleiste nicht verdecken (sonst tut Java das bei rahmenlosen Fenstern)
 * - Ein Klick auf das Taskleisten-Symbol soll das Fenster weiterhin minimieren
 */
object WindowChrome {

    private const val TAG = "WindowChrome"
    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    fun install(frame: Frame) {
        updateMaximizedBounds(frame)
        // Beim Wechsel auf einen anderen Bildschirm gelten andere Grenzen
        frame.addComponentListener(object : ComponentAdapter() {
            override fun componentMoved(e: ComponentEvent) = updateMaximizedBounds(frame)
        })
        frame.addWindowStateListener {
            updateMaximizedBounds(frame)
            AppLog.i(TAG, "Fensterzustand ${frame.extendedState}: ${frame.bounds} (Arbeitsbereich ${frame.maximizedBounds})")
        }
        if (isWindows) allowTaskbarMinimize(frame)
    }

    /**
     * Arbeitsbereich des aktuellen Bildschirms (ohne Taskleiste). Windows liest die Position relativ zum
     * Hauptbildschirm: Auf dem Hauptbildschirm ist das dessen Ecke (0,0), auf einem zweiten Bildschirm
     * muss sie absolut angegeben werden – sonst landete das maximierte Fenster auf dem Hauptbildschirm.
     */
    private fun updateMaximizedBounds(frame: Frame) {
        val config = frame.graphicsConfiguration ?: return
        val screen = config.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
        val isPrimary = config.device == GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        val originX = if (isPrimary) 0 else screen.x
        val originY = if (isPrimary) 0 else screen.y
        val target = Rectangle(
            originX + insets.left,
            originY + insets.top,
            screen.width - insets.left - insets.right,
            screen.height - insets.top - insets.bottom
        )
        if (frame.maximizedBounds != target) frame.maximizedBounds = target
    }

    /** Rahmenlose Java-Fenster haben unter Windows kein Minimieren-Flag – dann reagiert die Taskleiste nicht. */
    private fun allowTaskbarMinimize(frame: Frame) {
        fun apply() = runCatching {
            val hwnd = WinDef.HWND(Native.getComponentPointer(frame))
            val style = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_STYLE)
            val wanted = style or WinUser.WS_MINIMIZEBOX
            if (wanted != style) User32.INSTANCE.SetWindowLong(hwnd, WinUser.GWL_STYLE, wanted)
        }.onFailure { AppLog.w(TAG, "Minimieren über die Taskleiste nicht aktivierbar: ${it.message}") }

        if (frame.isDisplayable) apply()
        frame.addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowOpened(e: java.awt.event.WindowEvent) {
                apply()
            }
        })
        // Java setzt die Fenster-Flags neu, wenn "Größe ändern" an/aus geht (maximiert ↔ normal)
        frame.addWindowStateListener { java.awt.EventQueue.invokeLater { apply() } }
        frame.addPropertyChangeListener("resizable") { java.awt.EventQueue.invokeLater { apply() } }
    }
}
