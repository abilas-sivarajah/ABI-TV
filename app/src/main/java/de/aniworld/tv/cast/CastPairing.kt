package de.aniworld.tv.cast

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import de.aniworld.tv.AniWorldApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID

/** Was der Fernseher gerade zur Kopplung anzeigt. */
sealed interface PairingNotice {
    data class ShowCode(val code: String, val clientName: String, val expiresAt: Long) : PairingNotice
    data class Paired(val clientName: String) : PairingNotice
    data object Blocked : PairingNotice
}

sealed interface PairResult {
    data class Success(val token: String) : PairResult
    data class WrongCode(val attemptsLeft: Int) : PairResult
    data object Expired : PairResult
    data object Blocked : PairResult
}

/**
 * Kopplung zwischen Handy und Fernseher: Nur Handys, die einmal den am TV angezeigten
 * Code eingegeben haben, dürfen Wiedergabe starten oder steuern.
 */
object CastPairing {

    private const val PREFS_NAME = "aniworld_tv_cast"
    private const val KEY_TV_ID = "tv_id"
    private const val KEY_PAIRED = "paired_clients_v1"

    private const val CODE_TTL_MS = 120_000L
    private const val MAX_ATTEMPTS = 5
    private const val BLOCK_MS = 30_000L
    private const val NOTICE_MS = 4_000L

    private data class PairedClient(val name: String, val pairedAt: Long)

    private val gson = Gson()
    private val random = SecureRandom()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()

    private val prefs by lazy {
        AniWorldApp.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Schlüssel ist der SHA-256 des Tokens, damit das Token selbst nicht im Speicher liegt
    private val pairedClients: MutableMap<String, PairedClient> by lazy { readPairedClients() }

    private val _notice = MutableStateFlow<PairingNotice?>(null)
    val notice = _notice.asStateFlow()

    private val _pairedCount = MutableStateFlow(0)
    val pairedCount = _pairedCount.asStateFlow()

    private var activeCode: PairingNotice.ShowCode? = null
    private var failedAttempts = 0
    private var blockedUntil = 0L
    private var hideJob: Job? = null

    /** Stabile Kennung dieses Fernsehers, damit das Handy die Kopplung auch nach IP-Wechsel wiederfindet. */
    val tvId: String by lazy {
        prefs.getString(KEY_TV_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_TV_ID, it).apply()
        }
    }

    fun init() {
        synchronized(lock) { _pairedCount.value = pairedClients.size }
    }

    fun isAuthorized(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        return synchronized(lock) { pairedClients.containsKey(hash(token)) }
    }

    /** Zeigt am TV einen neuen Code an. Gibt false zurück, solange nach Fehlversuchen gesperrt ist. */
    fun startPairing(clientName: String): Boolean = synchronized(lock) {
        val now = System.currentTimeMillis()
        if (now < blockedUntil) return false

        val existing = activeCode
        if (existing != null && existing.expiresAt > now) {
            // Läuft schon: Code nicht wechseln, sonst stimmt die Eingabe am Handy nicht mehr
            return true
        }

        val code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000))
        val prompt = PairingNotice.ShowCode(code, sanitizeName(clientName), now + CODE_TTL_MS)
        activeCode = prompt
        failedAttempts = 0
        show(prompt, CODE_TTL_MS)
        true
    }

    fun completePairing(code: String, clientName: String): PairResult = synchronized(lock) {
        val now = System.currentTimeMillis()
        if (now < blockedUntil) return PairResult.Blocked

        val prompt = activeCode
        if (prompt == null || prompt.expiresAt <= now) {
            activeCode = null
            return PairResult.Expired
        }

        val matches = MessageDigest.isEqual(
            prompt.code.toByteArray(Charsets.UTF_8),
            code.trim().toByteArray(Charsets.UTF_8)
        )
        if (!matches) {
            failedAttempts++
            if (failedAttempts >= MAX_ATTEMPTS) {
                activeCode = null
                blockedUntil = now + BLOCK_MS
                show(PairingNotice.Blocked, NOTICE_MS)
                return PairResult.Blocked
            }
            return PairResult.WrongCode(MAX_ATTEMPTS - failedAttempts)
        }

        val token = newToken()
        val name = sanitizeName(clientName).ifBlank { prompt.clientName }
        pairedClients[hash(token)] = PairedClient(name, now)
        savePairedClients()
        activeCode = null
        show(PairingNotice.Paired(name), NOTICE_MS)
        PairResult.Success(token)
    }

    fun clearPairedDevices() = synchronized(lock) {
        pairedClients.clear()
        savePairedClients()
    }

    private fun show(notice: PairingNotice, durationMs: Long) {
        hideJob?.cancel()
        _notice.value = notice
        hideJob = scope.launch {
            delay(durationMs)
            synchronized(lock) {
                if (_notice.value === notice) {
                    _notice.value = null
                    if (activeCode === notice) activeCode = null
                }
            }
        }
    }

    private fun newToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun sanitizeName(raw: String): String =
        raw.filter { !it.isISOControl() }.trim().take(40).ifBlank { "Handy" }

    private fun readPairedClients(): MutableMap<String, PairedClient> = runCatching {
        val raw = prefs.getString(KEY_PAIRED, null) ?: return mutableMapOf()
        val type = object : TypeToken<Map<String, PairedClient>>() {}.type
        gson.fromJson<Map<String, PairedClient>>(raw, type)?.toMutableMap() ?: mutableMapOf()
    }.getOrDefault(mutableMapOf())

    private fun savePairedClients() {
        prefs.edit().putString(KEY_PAIRED, gson.toJson(pairedClients)).apply()
        _pairedCount.value = pairedClients.size
    }
}
