package de.aniworld.mobile.cast

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import de.aniworld.tv.AniWorldApp
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
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.TimeUnit

data class CastDevice(
    val name: String,
    val hostAddress: String,
    val port: Int = 8765,
    val model: String = ""
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

object MobileCastManager {

    private const val TAG = "MobileCastManager"
    const val DEFAULT_PORT = 8765

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    private var multicastLock: WifiManager.MulticastLock? = null
    private var statusPollJob: Job? = null
    private var searchJob: Job? = null

    fun init(context: Context) {
        // Zuletzt genutztes Gerät aus Prefs laden oder Standard Fire TV IP nehmen
        val prefs = context.getSharedPreferences("aniworld_mobile_cast", Context.MODE_PRIVATE)
        val defaultKnownIp = "192.168.178.70"
        val lastIp = prefs.getString("last_tv_ip", defaultKnownIp) ?: defaultKnownIp
        val lastName = prefs.getString("last_tv_name", "Fire TV Stick") ?: "Fire TV Stick"
        val lastPort = prefs.getInt("last_tv_port", DEFAULT_PORT)

        val dev = CastDevice(name = lastName, hostAddress = lastIp, port = lastPort)
        _connectedDevice.value = dev
        addDiscovered(dev)
        startStatusPolling(dev)

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

            // Falls nach Suche Geräte da sind, aber noch keins verbunden ist: automatisch mit dem 1. TV verbinden
            if (autoConnect && _connectedDevice.value == null && _discoveredDevices.value.isNotEmpty()) {
                connect(_discoveredDevices.value.first())
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

    fun connect(device: CastDevice) {
        _connectedDevice.value = device
        addDiscovered(device)
        val prefs = AniWorldApp.instance.getSharedPreferences("aniworld_mobile_cast", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("last_tv_ip", device.hostAddress)
            .putString("last_tv_name", device.name)
            .putInt("last_tv_port", device.port)
            .apply()
        startStatusPolling(device)

        scope.launch(Dispatchers.Main) {
            Toast.makeText(AniWorldApp.instance, "Mit ${device.name} verbunden", Toast.LENGTH_SHORT).show()
        }
    }

    fun disconnect() {
        _connectedDevice.value = null
        _tvPlaybackStatus.value = null
        statusPollJob?.cancel()
        val prefs = AniWorldApp.instance.getSharedPreferences("aniworld_mobile_cast", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        scope.launch(Dispatchers.Main) {
            Toast.makeText(AniWorldApp.instance, "Verbindung zum TV getrennt", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Sendet einen Play-Befehl an den Fernseher.
     * Falls noch kein TV verbunden ist, wird der erstbeste gefundene genommen.
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
        var device = _connectedDevice.value
        if (device == null && _discoveredDevices.value.isNotEmpty()) {
            device = _discoveredDevices.value.first()
            connect(device)
        }

        if (device == null) {
            // Schnelle Notfallsuche
            scanLocalSubnet()
            device = _discoveredDevices.value.firstOrNull()
            if (device != null) connect(device)
        }

        if (device == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    AniWorldApp.instance,
                    "Kein Fernseher gefunden. Bitte mit TV verbinden.",
                    Toast.LENGTH_LONG
                ).show()
            }
            return@withContext false
        }

        withContext(Dispatchers.Main) {
            Toast.makeText(
                AniWorldApp.instance,
                "Wird auf ${device.name} abgespielt...",
                Toast.LENGTH_SHORT
            ).show()
        }

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

            val req = Request.Builder()
                .url("http://${device.hostAddress}:${device.port}/play")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(req).execute().use { res ->
                val ok = res.isSuccessful
                if (ok) {
                    startStatusPolling(device)
                }
                ok
            }
        } catch (e: Exception) {
            Log.e(TAG, "playOnTv fehlgeschlagen", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    AniWorldApp.instance,
                    "Fehler beim Senden an den TV: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
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
                val req = Request.Builder()
                    .url("http://${device.hostAddress}:${device.port}/control")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(req).execute().close()
            } catch (e: Exception) {
                Log.w(TAG, "controlTv fehlgeschlagen", e)
            }
        }
    }

    private fun startStatusPolling(device: CastDevice) {
        statusPollJob?.cancel()
        statusPollJob = scope.launch {
            while (isActive && _connectedDevice.value?.id == device.id) {
                try {
                    val req = Request.Builder()
                        .url("http://${device.hostAddress}:${device.port}/status")
                        .build()
                    httpClient.newCall(req).execute().use { res ->
                        if (res.isSuccessful) {
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
                            val name = parts.getOrNull(2) ?: "Fire TV Stick"
                            val model = parts.getOrNull(3) ?: ""
                            val ip = recvPacket.address.hostAddress ?: continue
                            addDiscovered(CastDevice(name, ip, port, model))
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
     * Prüft, ob auf der IP:Port ein AniWorld-Cast-Server antwortet.
     */
    fun checkHttpDevice(ip: String, port: Int = DEFAULT_PORT) {
        try {
            val req = Request.Builder()
                .url("http://$ip:$port/ping")
                .build()
            probeClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string().orEmpty()
                    val obj = JsonParser.parseString(body).asJsonObject
                    if (obj.get("service")?.asString == "aniworld_tv_cast") {
                        val name = obj.get("name")?.asString ?: "Fire TV Stick"
                        val model = obj.get("model")?.asString.orEmpty()
                        addDiscovered(CastDevice(name, ip, port, model))
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun addDiscovered(device: CastDevice) {
        val current = _discoveredDevices.value.toMutableList()
        if (current.none { it.id == device.id }) {
            current.add(device)
            _discoveredDevices.value = current
            Log.i(TAG, "Gerät entdeckt: ${device.name} (${device.hostAddress}:${device.port})")
        }
    }
}
