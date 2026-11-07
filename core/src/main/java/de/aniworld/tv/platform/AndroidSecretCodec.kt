package de.aniworld.tv.platform

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Verschlüsselt Geheimnisse mit einem Schlüssel im Android-Keystore: Der Schlüssel verlässt das
 * Gerät nie, eine kopierte oder aus einem Backup wiederhergestellte Einstellungsdatei nützt nichts.
 */
class AndroidSecretCodec private constructor() : SecretCodec {

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    override fun decrypt(stored: String): String? = runCatching {
        val sealed = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES), Charsets.UTF_8)
    }.onFailure { AppLog.w("SecretCodec", "Gespeicherter Link nicht lesbar (Backup von einem anderen Gerät?)") }.getOrNull()

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "abi_tv_secrets_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128

        /** Erst ab Android 6; ohne funktionierenden Keystore (manche TV-Boxen) bleibt es beim Klartext. */
        fun createOrNull(): SecretCodec? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
            return runCatching {
                AndroidSecretCodec().also { codec ->
                    // Einmal hin und zurück, damit ein kaputter Keystore nicht erst beim Lesen auffällt
                    check(codec.decrypt(codec.encrypt("abi")) == "abi")
                }
            }.onFailure { AppLog.w("SecretCodec", "Android-Keystore nicht verfügbar", it) }.getOrNull()
        }
    }
}
