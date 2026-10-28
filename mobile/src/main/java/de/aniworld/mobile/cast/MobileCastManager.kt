package de.aniworld.mobile.cast

import android.content.Context
import android.content.SharedPreferences
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import de.aniworld.tv.AniWorldApp
import de.aniworld.tv.data.model.LiveTvChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

data class CastDevice(
    val name: String,
    val hostAddress: String,
    val port: Int = 8765,
    val model: String = "",
    // Stabile Kennung des Fernsehers (ab Protokoll 2), unter der das Kopplungs-Token liegt
    val tvId: String = "",
    // 0 = unbekannt, 1 = alte TV-App ohne Kopplung, 2 = mit Kopplung
    val protocol: Int = 0
) {
    val id: String get() = "$hostAddress:$port"
}

data class TvPlaybackStatus(
    val isPlaying: Boolean = false,
    val title: String = "",
    val episodeTitle: String = "",
    val seasonLabel: String = "",
    val episodeNumber: Int = 0,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

/** Kopplung läuft: Am TV wird ein Code angezeigt, der hier eingegeben werden muss. */
data class PairingUiState(
    val device: CastDevice,
    val isBusy: Boolean = false,
    val error: String? = null
)

object MobileCastManager {

    private const val TAG = "MobileCastManager"
    const val DEFAULT_PORT = 8765
    const val PROTOCOL_LIVE_TV = 3

    private const val PREFS_NAME = "aniworld_mobile_cast"
    private const val KEY_LAST_IP = "last_tv_ip"
    private const val KEY_LAST_NAME = "last_tv_name"
    private const val KEY_LAST_PORT = "last_tv_port"
    private const val KEY_LAST_ID = "last_tv_id"
    private const val KEY_LAST_PROTOCOL = "last_tv_protocol"
    private const val KEY_TOKEN_PREFIX = "token_"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jsonType = "application/json".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .build()

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(600, TimeUnit.MILLISECONDS)
        .readTimeout(600, TimeUnit.MILLISECONDS)
        .build()

    private val _discoveredDevices = MutableStateFlow<List<CastDevice>>(emptyList())
    val discoveredDevices = _discoveredDevices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<CastDevice?>(null)
    val connectedDevice = _connectedDevice.asStateFlow()

    private val _tvPlaybackStatus = MutableStateFlow<TvPlaybackStatus?>(null)
    val tvPlaybackStatus = _tvPlaybackStatus.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val _pairing = MutableStateFlow<PairingUiState?>(null)
    val pairing = _pairing.asStateFlow()

    private var multicastLock: WifiManager.MulticastLock? = null
    private var statusPollJob: Job? = null
    private var searchJob: Job? = null

    private val prefs: SharedPreferences
        get() = AniWorldApp.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val clientName: String
        get() = Build.MODEL.trim().ifBlank { "Handy" }

    fun init(context: Context) {
        // Zuletzt genutzten Fernseher nur übernehmen, wenn er noch gekoppelt ist
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastIp = p.getString(KEY_LAST_IP, null)
        if (lastIp != null) {
            val dev = CastDevice(
                name = p.getString(KEY_LAST_NAME, null) ?: "Fire TV Stick",
                hostAddress = lastIp,
                port = p.getInt(KEY_LAST_PORT, DEFAULT_PORT),
                tvId = p.getString(KEY_LAST_ID, null).orEmpty(),
                protocol = p.getInt(KEY_LAST_PROTOCOL, 0)
            )
            if (isUsable(dev)) {
                _connectedDevice.value = dev
                addDiscovered(dev)
                startStatusPolling(dev)
            }
        }

        // Direkt beim App-Start im Hintergrund nach Fernsehern suchen & IP verifizieren
        startDiscovery(context, autoConnect = true)
    }

    fun startDiscovery(context: Context, autoConnect: Boolean = true) {
        searchJob?.cancel()
        _isSearching.value = true

        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("AniWorldMobileNsd")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (_: Exception) {}

        searchJob = scope.launch {
            // 1. Gespeichertes Gerät vorab anpingen
            val saved = _connectedDevice.value
            if (saved != null) {
                checkHttpDevice(saved.hostAddress, saved.port)
            }

            // 2. Paralleler UDP-Broadcast auf alle Schnittstellen
            launch { sendUdpBroadcasts() }

            // 3. Schneller paralleler Subnetz-Scan (prüft das lokale WLAN-Segment z. B. 192.168.178.x)
            launch { scanLocalSubnet() }

            // Nach 8 Sekunden Suche beenden
            delay(8000L)
            _isSearching.value = false

            // Automatisch nur mit einem bereits gekoppelten TV verbinden, nie ungefragt eine Kopplung starten
            if (autoConnect && _connectedDevice.value == null) {
                _discoveredDevices.value.firstOrNull { isUsable(it) }?.let { finishConnect(it) }
            }
        }
    }

    fun stopDiscovery() {
        _isSearching.value = false
        searchJob?.cancel()
        try {
            if (multicastLock?.isHeld == true) multicastLock?.release()
        } catch (_: Exception) {}
        multicastLock = null
    }

    /**
     * Verbindet mit dem Fernseher. Ist das Handy dort noch nicht gekoppelt,
     * wird am TV ein Code angezeigt und [pairing] gesetzt.
     */
    fun connect(device: CastDevice) {
        scope.launch {
            val info = probe(device)
            if (info == null) {
                toast("${device.name} antwortet nicht. Ist ABI TV auf dem Fernseher geöffnet?")
                return@launch
            }
            if (info.protocol >= 2 && tokenFor(info) != null && !isTokenAccepted(info)) {
                prefs.edit().remove(KEY_TOKEN_PREFIX + info.tvId).apply()
            }
            if (isUsable(info)) {
                finishConnect(info)
            } else {
                requestPairing(info)
            }
        }
    }

    /** Fordert am Fernseher (erneut) einen Kopplungscode an. */
    fun requestPairing(device: CastDevice) {
        _pairing.value = PairingUiState(device, isBusy = true)
        scope.launch {
            try {
                val payload = JsonObject().apply { addProperty("clientName", clientName) }.toString()
                val req = Request.Builder()
                    .url("http://${device.hostAddress}:${device.port}/pair/start")
                    .post(payload.toRequestBody(jsonType))
                    .build()
                httpClient.newCall(req).execute().use { res ->
                    _pairing.value = when {
                        res.isSuccessful -> PairingUiState(device)
                        res.code == 429 -> PairingUiState(
                            device,
                            error = "Zu viele falsche Codes. Bitte 30 Sekunden warten und dann einen neuen Code anfordern."
                        )
                        else -> PairingUiState(device, error = "Der Fernseher hat die Kopplung abgelehnt (${res.code}).")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Kopplung anfordern fehlgeschlagen", e)
                _pairing.value = PairingUiState(device, error = "Fernseher nicht erreichbar.")
            }
        }
    }

    fun submitPairingCode(code: String) {
        val state = _pairing.value ?: return
        val device = state.device
        _pairing.value = state.copy(isBusy = true, error = null)
        scope.launch {
            try {
                val payload = JsonObject().apply {
                    addProperty("code", code.filter { it.isDigit() })
                    addProperty("clientName", clientName)
                }.toString()
                val req = Request.Builder()
                    .url("http://${device.hostAddress}:${device.port}/pair")
                    .post(payload.toRequestBody(jsonType))
                    .build()
                httpClient.newCall(req).execute().use { res ->
                    val obj = runCatching { JsonParser.parseString(res.body?.string().orEmpty()).asJsonObject }.getOrNull()
                    if (res.isSuccessful && obj != null) {
                        val token = obj.get("token")?.asString.orEmpty()
                        val tvId = obj.get("tvId")?.asString ?: device.tvId
                        if (token.isBlank() || tvId.isBlank()) {
                            _pairing.value = state.copy(error = "Ungültige Antwort vom Fernseher.")
                            return@use
                        }
                        prefs.edit().putString(KEY_TOKEN_PREFIX + tvId, token).apply()
                        _pairing.value = null
                        finishConnect(device.copy(tvId = tvId, protocol = 2))
                        return@use
                    }
                    _pairing.value = state.copy(
                        error = when (res.code) {
                            403 -> {
                                val left = obj?.get("attemptsLeft")?.asInt ?: 0
                                "Falscher Code. Noch $left ${if (left == 1) "Versuch" else "Versuche"}."
                            }
                            410 -> "Der Code ist abgelaufen. Fordere einen neuen an."
                            429 -> "Zu viele falsche Codes. Bitte 30 Sekunden warten."
                            else -> "Kopplung fehlgeschlagen (${res.code})."
                        }
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Kopplung fehlgeschlagen", e)
                _pairing.value = state.copy(error = "Fernseher nicht erreichbar.")
            }
        }
    }

    fun cancelPairing() {
        _pairing.value = null
    }

    fun disconnect() {
        _connectedDevice.value = null
        _tvPlaybackStatus.value = null
        statusPollJob?.cancel()
        // Kopplungs-Token behalten, damit ein erneutes Verbinden ohne Code klappt
        prefs.edit()
            .remove(KEY_LAST_IP)
            .remove(KEY_LAST_NAME)
            .remove(KEY_LAST_PORT)
            .remove(KEY_LAST_ID)
            .remove(KEY_LAST_PROTOCOL)
            .apply()

        toast("Verbindung zum TV getrennt")
    }

    /**
     * Spielt einen Live-Sender auf dem verbundenen Fernseher ab (braucht TV-Protokoll 3, ab ABI TV 1.0.4).
     */
    suspend fun playLiveOnTv(channel: LiveTvChannel): Boolean = withContext(Dispatchers.IO) {
        var device = _connectedDevice.value
        if (device == null) {
            toast("Bitte zuerst mit dem Fernseher verbinden.")
            return@withContext false
        }

        if (device.protocol < PROTOCOL_LIVE_TV) {
            // Gespeicherte Version kann veraltet sein, falls der TV inzwischen aktualisiert wurde
            val fresh = probe(device)
            if (fresh != null && fresh.protocol >= PROTOCOL_LIVE_TV) {
                device = device.copy(protocol = fresh.protocol)
                _connectedDevice.value = device
                prefs.edit().putInt(KEY_LAST_PROTOCOL, device.protocol).apply()
            } else {
                toast("Live TV braucht auf dem Fernseher ABI TV 1.0.4 oder neuer.")
                return@withContext false
            }
        }

        try {
            val payload = JsonObject().apply {
                addProperty("type", "live")
                addProperty("channelName", channel.name)
                addProperty("streamUrl", channel.streamUrl)
                addProperty("logoUrl", channel.logoUrl)
                add("headers", JsonObject().apply { channel.headers.forEach { (k, v) -> addProperty(k, v) } })
            }.toString()
            val req = authorizedRequest(device, "/play")
                .post(payload.toRequestBody(jsonType))
                .build()
            httpClient.newCall(req).execute().use { res ->
                when {
                    res.isSuccessful -> {
                        toast("${channel.name} läuft auf ${device.name}")
                        startStatusPolling(device)
                        true
                    }
                    res.code == 401 -> {
                        onUnauthorized(device)
                        false
                    }
                    else -> {
                        toast("Der Fernseher konnte den Sender nicht starten (${res.code}).")
                        false
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "playLiveOnTv fehlgeschlagen", e)
            toast("Fehler beim Senden an den TV: ${e.message}")
            false
        }
    }

    /**
     * Sendet einen Play-Befehl an den Fernseher.
     * Ohne verbundenen TV wird nur ein bereits gekoppelter genommen; sonst false (Aufrufer öffnet die Geräteauswahl).
     */
    suspend fun playOnTv(
        animeSlug: String,
        animeTitle: String,
        coverUrl: String,
        episodePath: String,
        episodeNumber: Int,
        seasonLabel: String,
        titleDe: String,
        preferredLangKey: Int
    ): Boolean = withContext(Dispatchers.IO) {
        var device = _connectedDevice.value ?: _discoveredDevices.value.firstOrNull { isUsable(it) }

        if (device == null) {
            // Schnelle Notfallsuche
            scanLocalSubnet()
            device = _discoveredDevices.value.firstOrNull { isUsable(it) }
        }

        if (device == null) {
            toast("Bitte zuerst mit dem Fernseher verbinden.")
            return@withContext false
        }
        if (_connectedDevice.value?.id != device.id) finishConnect(device)

        toast("Wird auf ${device.name} abgespielt...")

        try {
            val payload = JsonObject().apply {
                addProperty("animeSlug", animeSlug)
                addProperty("animeTitle", animeTitle)
                addProperty("coverUrl", coverUrl)
                addProperty("episodePath", episodePath)
                addProperty("episodeNumber", episodeNumber)
                addProperty("seasonLabel", seasonLabel)
                addProperty("titleDe", titleDe)
                addProperty("preferredLangKey", preferredLangKey)
            }.toString()

            val req = authorizedRequest(device, "/play")
                .post(payload.toRequestBody(jsonType))
                .build()

            httpClient.newCall(req).execute().use { res ->
                when {
                    res.isSuccessful -> {
                        startStatusPolling(device)
                        true
                    }
                    res.code == 401 -> {
                        onUnauthorized(device)
                        false
                    }
                    else -> false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "playOnTv fehlgeschlagen", e)
            toast("Fehler beim Senden an den TV: ${e.message}")
            false
        }
    }

    /**
     * Steuerung: "play", "pause", "toggle", "seek", "seek_by".
     */
    fun controlTv(action: String, arg: Long = 0L) {
        val device = _connectedDevice.value ?: return
        scope.launch {
            try {
                val payload = JsonObject().apply {
                    addProperty("action", action)
                    addProperty("arg", arg)
                }.toString()
                val req = authorizedRequest(device, "/control")
                    .post(payload.toRequestBody(jsonType))
                    .build()
                httpClient.newCall(req).execute().use { res ->
                    if (res.code == 401) onUnauthorized(device)
                }
            } catch (e: Exception) {
                Log.w(TAG, "controlTv fehlgeschlagen", e)
            }
        }
    }

    private fun finishConnect(device: CastDevice) {
        _connectedDevice.value = device
        addDiscovered(device)
        prefs.edit()
            .putString(KEY_LAST_IP, device.hostAddress)
            .putString(KEY_LAST_NAME, device.name)
            .putInt(KEY_LAST_PORT, device.port)
            .putString(KEY_LAST_ID, device.tvId)
            .putInt(KEY_LAST_PROTOCOL, device.protocol)
            .apply()
        startStatusPolling(device)

        toast("Mit ${device.name} verbunden")
    }

    /** Der TV kennt unser Token nicht mehr (z. B. "Kopplungen entfernen" am TV). */
    private fun onUnauthorized(device: CastDevice) {
        if (device.tvId.isNotEmpty()) prefs.edit().remove(KEY_TOKEN_PREFIX + device.tvId).apply()
        if (_connectedDevice.value?.id == device.id) {
            _connectedDevice.value = null
            _tvPlaybackStatus.value = null
            statusPollJob?.cancel()
        }
        toast("Die Kopplung mit ${device.name} wurde aufgehoben. Bitte neu verbinden.")
    }

    /** Ohne Kopplung nutzbar (alte TV-App) oder bereits gekoppelt. */
    private fun isUsable(device: CastDevice): Boolean =
        device.protocol == 1 || (device.protocol >= 2 && tokenFor(device) != null)

    private fun tokenFor(device: CastDevice): String? =
        device.tvId.takeIf { it.isNotEmpty() }?.let { prefs.getString(KEY_TOKEN_PREFIX + it, null) }

    private fun authorizedRequest(device: CastDevice, path: String): Request.Builder =
        Request.Builder()
            .url("http://${device.hostAddress}:${device.port}$path")
            .apply { tokenFor(device)?.let { header("Authorization", "Bearer $it") } }

    private fun toast(message: String) {
        scope.launch(Dispatchers.Main) {
            Toast.makeText(AniWorldApp.instance, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startStatusPolling(device: CastDevice) {
        statusPollJob?.cancel()
        statusPollJob = scope.launch {
            while (isActive && _connectedDevice.value?.id == device.id) {
                try {
                    val req = authorizedRequest(device, "/status").build()
                    httpClient.newCall(req).execute().use { res ->
                        if (res.code == 401) {
                            onUnauthorized(device)
                        } else if (res.isSuccessful) {
                            val json = res.body?.string().orEmpty()
                            val obj = JsonParser.parseString(json).asJsonObject
                            _tvPlaybackStatus.value = TvPlaybackStatus(
                                isPlaying = obj.get("isPlaying")?.asBoolean == true,
                                title = obj.get("title")?.asString.orEmpty(),
                                episodeTitle = obj.get("episodeTitle")?.asString.orEmpty(),
                                seasonLabel = obj.get("seasonLabel")?.asString.orEmpty(),
                                episodeNumber = obj.get("episodeNumber")?.asInt ?: 0,
                                positionMs = obj.get("positionMs")?.asLong ?: 0L,
                                durationMs = obj.get("durationMs")?.asLong ?: 0L
                            )
                        }
                    }
                } catch (_: Exception) {}
                delay(1200L)
            }
        }
    }

    /**
     * Sendet UDP-Discovery-Pakete an alle Broadcast-Adressen aller aktiven WLAN-Netzwerk-Interfaces.
     */
    private suspend fun sendUdpBroadcasts() = withContext(Dispatchers.IO) {
        try {
            val broadcastTargets = mutableSetOf<InetAddress>()
            broadcastTargets.add(InetAddress.getByName("255.255.255.255"))

            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                for (addr in iface.interfaceAddresses) {
                    addr.broadcast?.let { broadcastTargets.add(it) }
                }
            }

            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 1500
                val msg = "ANIWORLD_DISCOVER".toByteArray(Charsets.UTF_8)

                for (target in broadcastTargets) {
                    try {
                        val packet = DatagramPacket(msg, msg.size, target, 8766)
                        socket.send(packet)
                    } catch (_: Exception) {}
                }

                val buf = ByteArray(512)
                val recvPacket = DatagramPacket(buf, buf.size)
                val deadline = System.currentTimeMillis() + 2500L
                while (System.currentTimeMillis() < deadline) {
                    try {
                        socket.receive(recvPacket)
                        val text = String(recvPacket.data, 0, recvPacket.length, Charsets.UTF_8).trim()
                        if (text.startsWith("ANIWORLD_PONG")) {
                            val parts = text.split(":")
                            val port = parts.getOrNull(1)?.toIntOrNull() ?: DEFAULT_PORT
                            val ip = recvPacket.address.hostAddress ?: continue
                            // Per HTTP nachfragen, um Kennung und Protokollversion zu erfahren
                            if (!checkHttpDevice(ip, port)) {
                                val name = parts.getOrNull(2) ?: "Fire TV Stick"
                                val model = parts.getOrNull(3) ?: ""
                                addDiscovered(CastDevice(name, ip, port, model))
                            }
                        }
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Scannt parallel das lokale Subnetz (z.B. 192.168.178.1..254) nach dem offenen Cast-Port 8765 ab.
     * Dauert durch parallele Coroutines weniger als 1 Sekunde.
     */
    private suspend fun scanLocalSubnet() = withContext(Dispatchers.IO) {
        val localIps = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        localIps.add(addr.hostAddress)
                    }
                }
            }
        } catch (_: Exception) {}

        for (localIp in localIps) {
            val prefix = localIp.substringBeforeLast(".") + "."
            // Priorisiere typische TV-IP-Bereiche (.70, .20..150)
            val ipRange = (1..254).sortedBy { i ->
                when {
                    i == 70 -> 0 // Nutzer-IP des Fire TV Sticks
                    i in 20..100 -> 1
                    else -> 2
                }
            }

            // In Chunks von 32 IPs scannen, um Socket-Limits nicht zu überlasten
            ipRange.chunked(32).forEach { chunk ->
                chunk.map { hostNum ->
                    async {
                        val testIp = "$prefix$hostNum"
                        checkHttpDevice(testIp, DEFAULT_PORT)
                    }
                }.awaitAll()
            }
        }
    }

    /**
     * Prüft, ob auf der IP:Port ein AniWorld-Cast-Server antwortet, und nimmt ihn in die Liste auf.
     */
    fun checkHttpDevice(ip: String, port: Int = DEFAULT_PORT): Boolean {
        val device = probe(CastDevice(name = "Fire TV Stick", hostAddress = ip, port = port)) ?: return false
        addDiscovered(device)
        return true
    }

    /**
     * Fragt /ping ab. Liefert das Gerät mit Kennung und Protokollversion, oder null, wenn dort kein ABI TV läuft.
     */
    private fun probe(device: CastDevice): CastDevice? {
        return try {
            val req = Request.Builder()
                .url("http://${device.hostAddress}:${device.port}/ping")
                .build()
            probeClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return null
                val obj = JsonParser.parseString(res.body?.string().orEmpty()).asJsonObject
                if (obj.get("service")?.asString != "aniworld_tv_cast") return null
                device.copy(
                    name = obj.get("name")?.asString ?: device.name,
                    model = obj.get("model")?.asString.orEmpty(),
                    tvId = obj.get("tvId")?.asString.orEmpty(),
                    protocol = obj.get("version")?.asInt ?: 1
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Fragt mit Token nach, ob der TV die Kopplung noch kennt (er kann sie in den Einstellungen löschen). */
    private fun isTokenAccepted(device: CastDevice): Boolean {
        return try {
            val req = authorizedRequest(device, "/ping").build()
            probeClient.newCall(req).execute().use { res ->
                val obj = JsonParser.parseString(res.body?.string().orEmpty()).asJsonObject
                obj.get("paired")?.asBoolean != false
            }
        } catch (_: Exception) {
            true
        }
    }

    private fun addDiscovered(device: CastDevice) {
        val current = _discoveredDevices.value.toMutableList()
        val index = current.indexOfFirst { it.id == device.id }
        when {
            index < 0 -> {
                current.add(device)
                Log.i(TAG, "Gerät entdeckt: ${device.name} (${device.hostAddress}:${device.port})")
            }
            // Bekanntes Gerät mit neueren Infos (Kennung/Protokoll) aktualisieren
            device.protocol > 0 && current[index] != device -> current[index] = device
            else -> return
        }
        _discoveredDevices.value = current
    }
}
