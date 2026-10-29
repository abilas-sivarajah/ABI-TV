package de.aniworld.pc.ui.compat

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import de.aniworld.pc.platform.AppDirs
import de.aniworld.tv.data.remote.HttpClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Ersatz für Coils AsyncImage: lädt Cover über OkHttp, dekodiert mit Skia und hält sie
 * im Speicher und auf der Platte vor, damit Reihen beim Scrollen nicht neu laden.
 */
@Composable
fun AsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center,
    alpha: Float = 1f,
    onError: ((Throwable) -> Unit)? = null
) {
    val url = (model as? String)?.trim().orEmpty()
    var bitmap by remember(url) { mutableStateOf(ImageCache.peek(url)) }

    LaunchedEffect(url) {
        if (bitmap != null) return@LaunchedEffect
        if (url.isBlank()) {
            onError?.invoke(IllegalArgumentException("Keine Bild-Adresse"))
            return@LaunchedEffect
        }
        runCatching { ImageCache.load(url) }
            .onSuccess { bitmap = it }
            .onFailure { onError?.invoke(it) }
    }

    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            alignment = alignment,
            alpha = alpha
        )
    } else {
        // Platzhalter mit gleicher Größe, damit das Layout beim Nachladen nicht springt
        Box(modifier)
    }
}

private object ImageCache {

    private const val MAX_MEMORY_ENTRIES = 300

    private val memory = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
            size > MAX_MEMORY_ENTRIES
    }
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<ImageBitmap>>()
    private val downloads = Semaphore(6)
    private val diskDir by lazy { File(AppDirs.cacheDir, "images").apply { mkdirs() } }

    fun peek(url: String): ImageBitmap? = synchronized(memory) { memory[url] }

    suspend fun load(url: String): ImageBitmap {
        peek(url)?.let { return it }
        val own = CompletableDeferred<ImageBitmap>()
        val existing = inFlight.putIfAbsent(url, own)
        if (existing != null) return existing.await()
        try {
            val bitmap = withContext(Dispatchers.IO) { decode(readBytes(url)) }
            synchronized(memory) { memory[url] = bitmap }
            own.complete(bitmap)
            return bitmap
        } catch (e: Throwable) {
            own.completeExceptionally(e)
            throw e
        } finally {
            inFlight.remove(url)
        }
    }

    private suspend fun readBytes(url: String): ByteArray {
        val file = File(diskDir, sha1(url))
        if (file.isFile && file.length() > 0) return file.readBytes()

        val bytes = downloads.withPermit {
            val request = Request.Builder().url(url).build()
            HttpClient.okHttp.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                response.body?.bytes() ?: error("Leere Antwort")
            }
        }
        runCatching {
            val tmp = File(diskDir, "${file.name}.tmp")
            tmp.writeBytes(bytes)
            tmp.renameTo(file)
        }
        return bytes
    }

    private fun decode(bytes: ByteArray): ImageBitmap =
        org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()

    private fun sha1(text: String): String = MessageDigest.getInstance("SHA-1")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
