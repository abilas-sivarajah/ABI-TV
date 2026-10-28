package de.aniworld.tv.cast

import android.os.Build
import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
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
    private const val PROTOCOL_VERSION = 2
    private const val MAX_BODY_BYTES = 64 * 1024
    private const val MAX_LINE_BYTES = 8 * 1024

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
                        executor.execute {
                            // Ein Fehler in einem Pool-Thread würde sonst die ganze App beenden
                            // (z. B. Timeout, wenn ein Gerät im WLAN den Port öffnet und nichts sendet)
                            try {
                                handleClient(client)
                            } catch (e: Exception) {
                                Log.w(TAG, "Anfrage abgebrochen: ${e.javaClass.simpleName}")
                            }
                        }
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
            val input = BufferedInputStream(s.getInputStream())
            val out = s.getOutputStream()

            val requestLine = readHttpLine(input) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val path = parts[1]

            // HTTP Header lesen
            var contentLength = 0
            var token: String? = null
            while (true) {
                val line = readHttpLine(input) ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                } else if (line.startsWith("Authorization:", ignoreCase = true)) {
                    token = line.substringAfter(":").trim().removePrefix("Bearer ").trim()
                }
            }

            if (contentLength !in 0..MAX_BODY_BYTES) {
                sendJson(out, 413, "Payload Too Large", errorJson("too_large"))
                return
            }

            // HTTP Body lesen (falls vorhanden). Content-Length zählt Bytes, nicht Zeichen:
            // als Bytes lesen, sonst blockieren Umlaute im Titel bis zum Timeout.
            val body = if (contentLength > 0) {
                val bytes = ByteArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val r = input.read(bytes, readTotal, contentLength - readTotal)
                    if (r == -1) break
                    readTotal += r
                }
                String(bytes, 0, readTotal, Charsets.UTF_8)
            } else ""

            // Nur Suche und Kopplung sind offen; alles andere braucht ein gekoppeltes Handy
            val isPublic = path.startsWith("/ping") || path.startsWith("/pair")
            if (!isPublic && !CastPairing.isAuthorized(token)) {
                sendJson(out, 401, "Unauthorized", errorJson("pairing_required"))
                return
            }

            when {
                method == "GET" && path.startsWith("/ping") -> {
                    val json = JsonObject().apply {
                        addProperty("status", "ok")
                        addProperty("service", "aniworld_tv_cast")
                        addProperty("name", TvCastManager.deviceName)
                        addProperty("model", Build.MODEL)
                        addProperty("version", PROTOCOL_VERSION)
                        addProperty("tvId", CastPairing.tvId)
                        addProperty("paired", CastPairing.isAuthorized(token))
                    }.toString()
                    sendJson(out, 200, "OK", json)
                }

                method == "POST" && path.startsWith("/pair/start") -> {
                    val clientName = parseObject(body)?.get("clientName")?.asString.orEmpty()
                    if (CastPairing.startPairing(clientName)) {
                        sendJson(out, 200, "OK", JsonObject().apply { addProperty("success", true) }.toString())
                    } else {
                        sendJson(out, 429, "Too Many Requests", errorJson("blocked"))
                    }
                }

                method == "POST" && path.startsWith("/pair") -> {
                    val obj = parseObject(body)
                    val code = obj?.get("code")?.asString.orEmpty()
                    val clientName = obj?.get("clientName")?.asString.orEmpty()
                    when (val result = CastPairing.completePairing(code, clientName)) {
                        is PairResult.Success -> sendJson(
                            out, 200, "OK",
                            JsonObject().apply {
                                addProperty("token", result.token)
                                addProperty("tvId", CastPairing.tvId)
                            }.toString()
                        )
                        is PairResult.WrongCode -> sendJson(
                            out, 403, "Forbidden",
                            JsonObject().apply {
                                addProperty("error", "wrong_code")
                                addProperty("attemptsLeft", result.attemptsLeft)
                            }.toString()
                        )
                        PairResult.Expired -> sendJson(out, 410, "Gone", errorJson("expired"))
                        PairResult.Blocked -> sendJson(out, 429, "Too Many Requests", errorJson("blocked"))
                    }
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
                        sendJson(out, 400, "Bad Request", errorJson("bad_request"))
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
                        sendJson(out, 400, "Bad Request", errorJson("bad_request"))
                    }
                }

                else -> {
                    sendResponse(out, 404, "Not Found", "text/plain", "Endpoint not found")
                }
            }
        }
    }

    /** Liest eine Zeile bis LF als UTF-8, ohne über das Zeilenende hinaus zu puffern. */
    private fun readHttpLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) {
                if (buf.size() == 0) return null
                break
            }
            if (b == '\n'.code) break
            if (buf.size() >= MAX_LINE_BYTES) return null
            buf.write(b)
        }
        return buf.toString("UTF-8").trimEnd('\r')
    }

    private fun parseObject(body: String): JsonObject? =
        runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()

    private fun errorJson(code: String): String =
        JsonObject().apply { addProperty("error", code) }.toString()

    private fun sendJson(out: OutputStream, statusCode: Int, statusText: String, json: String) =
        sendResponse(out, statusCode, statusText, "application/json", json)

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
            append("\r\n")
        }
        out.write(header.toByteArray(Charsets.UTF_8))
        if (bytes.isNotEmpty()) {
            out.write(bytes)
        }
        out.flush()
    }
}
