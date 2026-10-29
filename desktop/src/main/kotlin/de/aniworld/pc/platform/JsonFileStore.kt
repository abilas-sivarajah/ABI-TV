package de.aniworld.pc.platform

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import de.aniworld.tv.platform.AppLog
import de.aniworld.tv.platform.KeyValueStore
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors

/**
 * [KeyValueStore] als JSON-Datei – das PC-Gegenstück zu den SharedPreferences.
 * Schreibt im Hintergrund und ersetzt die Datei atomar, damit ein Absturz nichts zerstört.
 */
class JsonFileStore(private val file: File) : KeyValueStore {

    private val lock = Any()
    private val data: JsonObject = load()
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "store-${file.nameWithoutExtension}").apply { isDaemon = true }
    }

    override fun getString(key: String, default: String?): String? =
        read(key)?.takeIf { it.isJsonPrimitive }?.asString ?: default

    override fun putString(key: String, value: String) = write(key, JsonPrimitive(value))

    override fun getInt(key: String, default: Int): Int =
        runCatching { read(key)?.asInt }.getOrNull() ?: default

    override fun putInt(key: String, value: Int) = write(key, JsonPrimitive(value))

    override fun getLong(key: String, default: Long): Long =
        runCatching { read(key)?.asLong }.getOrNull() ?: default

    override fun putLong(key: String, value: Long) = write(key, JsonPrimitive(value))

    override fun getStringSet(key: String, default: Set<String>): Set<String> {
        val array = read(key) as? JsonArray ?: return default
        return array.mapNotNull { runCatching { it.asString }.getOrNull() }.toSet()
    }

    override fun putStringSet(key: String, value: Set<String>) =
        write(key, JsonArray().apply { value.forEach { add(it) } })

    /** Schreibt sofort (beim Beenden der App). */
    fun flush() = save()

    private fun read(key: String): JsonElement? = synchronized(lock) { data.get(key) }

    private fun write(key: String, value: JsonElement) {
        synchronized(lock) { data.add(key, value) }
        writer.execute { save() }
    }

    private fun save() {
        val json = synchronized(lock) { gson.toJson(data) }
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(json, Charsets.UTF_8)
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure { AppLog.e("JsonFileStore", "Speichern von ${file.name} fehlgeschlagen", it) }
    }

    private fun load(): JsonObject = runCatching {
        if (!file.isFile) return JsonObject()
        JsonParser.parseString(file.readText(Charsets.UTF_8)).asJsonObject
    }.getOrElse {
        AppLog.e("JsonFileStore", "${file.name} ist beschädigt – starte leer", it)
        // Kaputte Datei aufheben statt überschreiben, falls man sie retten will
        runCatching { file.copyTo(File(file.parentFile, "${file.name}.defekt"), overwrite = true) }
        JsonObject()
    }
}
