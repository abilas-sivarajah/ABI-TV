package de.aniworld.pc.cast

import de.aniworld.pc.platform.AppDirs
import de.aniworld.pc.platform.JsonFileStore
import de.aniworld.tv.cast.CastHost
import de.aniworld.tv.cast.CastPairing
import de.aniworld.tv.cast.CastServer
import de.aniworld.tv.cast.CastStatus
import de.aniworld.tv.cast.PlayCommand
import de.aniworld.tv.platform.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

/**
 * PC-Gegenstück zum TvCastManager: nimmt Befehle gekoppelter Handys an (gleiches Protokoll
 * und gleiche Ports wie der Fernseher) und meldet den PC per mDNS im WLAN an.
 */
object PcCastManager : CastHost {

    private const val TAG = "PcCastManager"
    const val HTTP_PORT = 8765
    const val UDP_PORT = 8766

    /** Gleicher Dienst-Typ wie NsdHelper auf dem Fernseher ("_aniworldtv._tcp."). */
    private const val SERVICE_TYPE = "_aniworldtv._tcp.local."

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mdnsInstances = mutableListOf<JmDNS>()
    private lateinit var pairingStore: JsonFileStore

    private val _castStatus = MutableStateFlow(CastStatus())
    override val castStatus: StateFlow<CastStatus> = _castStatus.asStateFlow()

    @Volatile
    var isRunning = false
        private set

    // Werden von der Oberfläche gesetzt (Navigation bzw. laufender Player)
    var onPlayCommand: ((PlayCommand) -> Unit)? = null
    var onLiveCommand: ((channelName: String, streamUrl: String, logoUrl: String, headers: Map<String, String>) -> Unit)? = null
    var activePlayerControl: ((action: String, arg: Long) -> Unit)? = null

    override val deviceModel: String
        get() = "${System.getProperty("os.name")} PC"

    override val deviceName: String
        get() {
            val host = System.getenv("COMPUTERNAME")?.trim()?.ifBlank { null }
                ?: runCatching { InetAddress.getLocalHost().hostName }.getOrNull()?.trim()?.ifBlank { null }
            return if (host != null) "PC ($host)" else "ABI TV PC"
        }

    fun start() {
        pairingStore = JsonFileStore(File(AppDirs.dataDir, "${CastPairing.PREFS_NAME}.json"))
        CastPairing.init(pairingStore)
        scope.launch {
            try {
                CastServer.start(HTTP_PORT, UDP_PORT, this@PcCastManager)
                isRunning = CastServer.boundHttpPort != 0
                if (isRunning) registerMdns()
                AppLog.i(TAG, "Cast-Dienst gestartet auf Port ${CastServer.boundHttpPort} für '$deviceName'")
            } catch (e: Exception) {
                AppLog.e(TAG, "Fehler beim Starten des Cast-Dienstes", e)
            }
        }
    }

    fun stop() {
        synchronized(mdnsInstances) {
            mdnsInstances.forEach { runCatching { it.unregisterAllServices(); it.close() } }
            mdnsInstances.clear()
        }
        CastServer.stop()
        isRunning = false
        if (::pairingStore.isInitialized) pairingStore.flush()
    }

    fun updatePlaybackStatus(status: CastStatus) {
        _castStatus.value = status
    }

    fun resetPlaybackStatus() {
        _castStatus.value = CastStatus()
    }

    override fun handlePlayCommand(cmd: PlayCommand) {
        scope.launch(Dispatchers.Main) {
            onPlayCommand?.invoke(cmd) ?: AppLog.w(TAG, "Play-Befehl ignoriert: Oberfläche noch nicht bereit")
        }
    }

    override fun handleLiveCommand(channelName: String, streamUrl: String, logoUrl: String, headers: Map<String, String>) {
        scope.launch(Dispatchers.Main) {
            onLiveCommand?.invoke(channelName, streamUrl, logoUrl, headers)
        }
    }

    override fun handleControlCommand(action: String, arg: Long): Boolean {
        val control = activePlayerControl ?: return false
        scope.launch(Dispatchers.Main) { control.invoke(action, arg) }
        return true
    }

    /**
     * Meldet den Dienst auf jeder echten LAN-Adresse an. Virtuelle Adapter (Hyper-V, VirtualBox,
     * VPN …) werden übersprungen, sonst sieht das Handy eine Adresse, die es nicht erreicht.
     */
    private fun registerMdns() {
        val addresses = lanAddresses()
        if (addresses.isEmpty()) {
            AppLog.w(TAG, "Keine LAN-Adresse gefunden – Handy findet den PC nur über die UDP-Suche")
            return
        }
        for (address in addresses) {
            runCatching {
                val jmdns = JmDNS.create(address, deviceName)
                jmdns.registerService(ServiceInfo.create(SERVICE_TYPE, deviceName, CastServer.boundHttpPort, "ABI TV"))
                synchronized(mdnsInstances) { mdnsInstances += jmdns }
                AppLog.i(TAG, "mDNS angemeldet auf ${address.hostAddress}")
            }.onFailure { AppLog.w(TAG, "mDNS auf ${address.hostAddress} fehlgeschlagen", it) }
        }
    }

    private fun lanAddresses(): List<InetAddress> {
        val virtualMarkers = listOf("virtual", "vethernet", "hyper-v", "vmware", "vbox", "loopback", "tap", "tun", "vpn", "wsl", "docker")
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { nic ->
                    val name = "${nic.name} ${nic.displayName}".lowercase()
                    nic.isUp && !nic.isLoopback && !nic.isVirtual && nic.supportsMulticast() &&
                        virtualMarkers.none { name.contains(it) }
                }
                .flatMap { nic -> nic.inetAddresses.toList() }
                .filter { it is Inet4Address && it.isSiteLocalAddress }
        }.getOrDefault(emptyList())
    }
}
