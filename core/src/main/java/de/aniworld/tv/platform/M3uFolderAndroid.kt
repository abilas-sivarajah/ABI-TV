package de.aniworld.tv.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import de.aniworld.tv.data.local.LocalStorage
import de.aniworld.tv.data.model.M3uFolderFile
import de.aniworld.tv.data.remote.LiveTvRepository
import java.io.IOException

/**
 * Eigener M3U-Ordner auf Handy und TV: Der Ordner wird über die Ordnerauswahl von Android
 * freigegeben (content://…), die App darf ihn danach dauerhaft lesen – samt Unterordnern.
 */
object M3uFolderAndroid {

    private const val TAG = "M3uFolder"

    /** Beim App-Start: Live TV liest den Ordner über die Freigabe statt über einen Dateipfad. */
    fun install(context: Context) {
        val app = context.applicationContext
        LiveTvRepository.folderReader = { folder -> read(app, folder) }
    }

    /** Gewählten Ordner übernehmen: Freigabe dauerhaft behalten, die des alten Ordners zurückgeben. */
    fun onFolderPicked(context: Context, tree: Uri) {
        val resolver = context.contentResolver
        runCatching { resolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            .onFailure { AppLog.w(TAG, "Freigabe nicht dauerhaft", it) }
        releaseOld(context, except = tree)
        LiveTvRepository.saveM3uFolder(tree.toString(), displayName(context, tree))
    }

    fun remove(context: Context) {
        releaseOld(context, except = null)
        LiveTvRepository.saveM3uFolder("", "")
    }

    private fun releaseOld(context: Context, except: Uri?) {
        val old = LocalStorage.m3uFolder.takeIf { it.startsWith("content:") }?.let(Uri::parse) ?: return
        if (old == except) return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun displayName(context: Context, tree: Uri): String {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val fromProvider = runCatching {
            context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
        // Sonst den letzten Teil der Kennung ("primary:Download/IPTV" -> "IPTV")
        return fromProvider?.takeIf { it.isNotBlank() }
            ?: DocumentsContract.getTreeDocumentId(tree).substringAfterLast(':').substringAfterLast('/').ifBlank { "Eigener Ordner" }
    }

    private fun read(context: Context, folder: String): List<M3uFolderFile> {
        val tree = Uri.parse(folder)
        val resolver = context.contentResolver
        val result = mutableListOf<M3uFolderFile>()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )

        fun walk(docId: String, relative: String, depth: Int) {
            if (depth > LiveTvRepository.FOLDER_MAX_DEPTH || result.size >= LiveTvRepository.FOLDER_MAX_FILES) return
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            data class Entry(val id: String, val name: String, val isDir: Boolean, val size: Long)
            val entries = mutableListOf<Entry>()
            val cursor = resolver.query(children, projection, null, null, null)
                ?: throw IOException("keine Berechtigung für den Ordner")
            cursor.use {
                while (it.moveToNext()) {
                    val name = it.getString(1).orEmpty()
                    if (name.startsWith(".")) continue
                    entries += Entry(
                        id = it.getString(0),
                        name = name,
                        isDir = it.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                        size = if (it.isNull(3)) 0L else it.getLong(3)
                    )
                }
            }
            entries.sortBy { it.name.lowercase() }
            entries.filter { !it.isDir && LiveTvRepository.isM3uFileName(it.name) && it.size <= LiveTvRepository.FOLDER_MAX_FILE_BYTES }
                .forEach { entry ->
                    if (result.size >= LiveTvRepository.FOLDER_MAX_FILES) return@forEach
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, entry.id)
                    val text = resolver.openInputStream(uri)?.use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
                    if (text != null) result += M3uFolderFile(relative, entry.name, text)
                }
            entries.filter { it.isDir }.forEach { sub ->
                walk(sub.id, if (relative.isEmpty()) sub.name else "$relative/${sub.name}", depth + 1)
            }
        }

        walk(DocumentsContract.getTreeDocumentId(tree), "", 0)
        return result
    }
}
