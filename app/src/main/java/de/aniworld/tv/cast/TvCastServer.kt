package de.aniworld.tv.cast

import android.os.Build
import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object TvCastServer {

    private const val TAG = "TvCastServer"

    private var serverSocket: ServerSocket? = null
    private var udpSocket: DatagramSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool()

    fun start(httpPort: Int, udpPort: Int) {
        if (isRunning.getAndSet(true)) return

        // 1. HTTP Server starten
        executor.execute {
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress("0.0.0.0", httpPort))
                serverSocket = ss
                Log.i(TAG, "HTTP Cast-Server lauscht auf Port $httpPort")

                while (isRunning.get()) {
                    try {
                        val client = ss.accept()
                        executor.execute { handleClient(client) }
                    } catch (e: Exception) {
                        if (!isRunning.get()) break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fehler im HTTP Server-Thread", e)
            }
        }

        // 2. UDP Discovery Listener starten (als Ausfallsicherung zu mDNS)
        executor.execute {
            try {
                val udp = DatagramSocket(null)
                udp.reuseAddress = true
                udp.bind(InetSocketAddress("0.0.0.0", udpPort))
                udpSocket = udp
                Log.i(TAG, "UDP Discovery-Server lauscht auf Port $udpPort")

                val buffer = ByteArray(512)
                while (isRunning.get()) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        udp.receive(packet)
                        val msg = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                        if (msg.startsWith("ANIWORLD_DISCOVER")) {
                            val respStr = "ANIWORLD_PONG:$httpPort:${TvCastManager.deviceName}:${Build.MODEL}"
                            val respBytes = respStr.toByteArray(Charsets.UTF_8)
                            val respPacket = DatagramPacket(
                                respBytes,
                                respBytes.size,
                                packet.address,
                                packet.port
                            )
                            udp.send(respPacket)
                            Log.d(TAG, "Discovery-Anfrage von ${packet.address} beantwortet")
                        }
                    } catch (e: Exception) {
                        if (!isRunning.get()) break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fehler im UDP Discovery-Thread", e)
            }
        }
    }

    fun stop() {
        isRunning.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        try {
            udpSocket?.close()
        } catch (_: Exception) {}
        udpSocket = null
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 8000
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val out = s.getOutputStream()

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val path = parts[1]

            // HTTP Header lesen
            var contentLength = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            // HTTP Body lesen (falls vorhanden)
            val body = if (contentLength > 0) {
                val chars = CharArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val r = reader.read(chars, readTotal, contentLength - readTotal)
                    if (r == -1) break
                    readTotal += r
                }
                String(chars, 0, readTotal)
            } else ""

            when {
                method == "OPTIONS" -> {
                    sendResponse(out, 204, "No Content", "text/plain", "")
                }

                method == "GET" && path.startsWith("/ping") -> {
                    val json = JsonObject().apply {
                        addProperty("status", "ok")
                        addProperty("service", "aniworld_tv_cast")
                        addProperty("name", TvCastManager.deviceName)
                        addProperty("model", Build.MODEL)
                        addProperty("version", 1)
                    }.toString()
                    sendResponse(out, 200, "OK", "application/json", json)
                }

                method == "GET" && path.startsWith("/status") -> {
                    val status = TvCastManager.castStatus.value
                    val json = JsonObject().apply {
                        addProperty("isPlaying", status.isPlaying)
                        addProperty("title", status.title)
                        addProperty("episodeTitle", status.episodeTitle)
                        addProperty("seasonLabel", status.seasonLabel)
                        addProperty("episodeNumber", status.episodeNumber)
                        addProperty("positionMs", status.positionMs)
                        addProperty("durationMs", status.durationMs)
                    }.toString()
                    sendResponse(out, 200, "OK", "application/json", json)
                }

                method == "POST" && path.startsWith("/play") -> {
                    try {
                        val obj = JsonParser.parseString(body).asJsonObject
                        val cmd = PlayCommand(
                            animeSlug = obj.get("animeSlug")?.asString.orEmpty(),
                            animeTitle = obj.get("animeTitle")?.asString.orEmpty(),
                            coverUrl = obj.get("coverUrl")?.asString.orEmpty(),
                            episodePath = obj.get("episodePath")?.asString.orEmpty(),
                            episodeNumber = obj.get("episodeNumber")?.asInt ?: 1,
                            seasonLabel = obj.get("seasonLabel")?.asString ?: "1",
                            titleDe = obj.get("titleDe")?.asString.orEmpty(),
                            preferredLangKey = obj.get("preferredLangKey")?.asInt ?: 1
                        )
                        TvCastManager.handlePlayCommand(cmd)
                        val resp = JsonObject().apply {
                            addProperty("success", true)
                            addProperty("message", "Playback gestartet auf TV")
                        }.toString()
                        sendResponse(out, 200, "OK", "application/json", resp)
                    } catch (e: Exception) {
                        Log.e(TAG, "Fehler beim Verarbeiten von /play", e)
                        sendResponse(out, 400, "Bad Request", "application/json", "{\"error\":\"${e.message}\"}")
                    }
                }

                method == "POST" && path.startsWith("/control") -> {
                    try {
                        val obj = JsonParser.parseString(body).asJsonObject
                        val action = obj.get("action")?.asString.orEmpty()
                        val arg = obj.get("arg")?.asLong ?: 0L
                        val handled = TvCastManager.handleControlCommand(action, arg)
                        val resp = JsonObject().apply {
                            addProperty("success", handled)
                            addProperty("action", action)
                        }.toString()
                        sendResponse(out, 200, "OK", "application/json", resp)
                    } catch (e: Exception) {
                        sendResponse(out, 400, "Bad Request", "application/json", "{\"error\":\"${e.message}\"}")
                    }
                }

                else -> {
                    sendResponse(out, 404, "Not Found", "text/plain", "Endpoint not found")
                }
            }
        }
    }

    private fun sendResponse(
        out: OutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        body: String
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 $statusCode $statusText\r\n")
            append("Content-Type: $contentType; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Content-Type\r\n")
            append("\r\n")
        }
        out.write(header.toByteArray(Charsets.UTF_8))
        if (bytes.isNotEmpty()) {
            out.write(bytes)
        }
        out.flush()
    }
}
