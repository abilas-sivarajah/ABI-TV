package de.aniworld.pc.platform

import com.sun.jna.platform.win32.Crypt32Util
import de.aniworld.tv.platform.AppLog
import de.aniworld.tv.platform.SecretCodec
import java.util.Base64

/**
 * Verschlüsselt Geheimnisse mit der Windows-Datenschutz-API (DPAPI) des angemeldeten Benutzers:
 * Nur dieses Windows-Konto auf diesem PC kann sie wieder lesen – eine kopierte Einstellungsdatei nützt nichts.
 */
class WindowsSecretCodec private constructor() : SecretCodec {

    override fun encrypt(plain: String): String =
        Base64.getEncoder().encodeToString(Crypt32Util.cryptProtectData(plain.toByteArray(Charsets.UTF_8)))

    override fun decrypt(stored: String): String? = runCatching {
        String(Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(stored)), Charsets.UTF_8)
    }.onFailure { AppLog.w("SecretCodec", "Gespeicherter Link nicht lesbar (anderes Windows-Konto?)") }.getOrNull()

    companion object {
        /** Nur unter Windows; sonst bleibt es beim Klartext. */
        fun createOrNull(): SecretCodec? {
            if (!System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) return null
            return runCatching {
                WindowsSecretCodec().also { codec ->
                    // Einmal hin und zurück, damit ein kaputtes JNA nicht erst beim Speichern auffällt
                    check(codec.decrypt(codec.encrypt("abi")) == "abi")
                }
            }.onFailure { AppLog.w("SecretCodec", "Windows-Verschlüsselung nicht verfügbar", it) }.getOrNull()
        }
    }
}
