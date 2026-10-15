package id.local.tubeflow.core

import android.content.ContentValues
import android.content.Context
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.AtomicFile
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Separate derived media, with a pending-URI journal. No writes to the source audio or source LRC. */
class KaraokePublication(private val context: Context) {
    private val database = DownloadDatabase.get(context)
    private val journal = AtomicFile(File(context.noBackupFilesDir, "karaoke-publication.json"))
    private fun record(data: JSONObject) {
        val output = journal.startWrite()
        try { output.write(data.toString().toByteArray()); journal.finishWrite(output) }
        catch (failure: Exception) { journal.failWrite(output); throw failure }
    }
    private fun saved(directory: File, uri: String) {
        val atomic = AtomicFile(File(directory, "saved.json"))
        val output = atomic.startWrite()
        try { output.write(JSONObject().put("uri", uri).toString().toByteArray()); atomic.finishWrite(output) }
        catch (failure: Exception) { atomic.failWrite(output); throw failure }
    }
    suspend fun recover() {
        if (!journal.baseFile.exists() && !File(journal.baseFile.path + ".bak").exists()) return
        val data = JSONObject(journal.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
        val raw = data.getString("uri")
        val indexed = database.media().getByAccess(raw) ?: database.media().get(raw)
        if (indexed != null) saved(KaraokeWorker.directory(context, data.getString("workId")), raw)
        else require(StorageUris.delete(context, raw)) { "File karaoke parsial belum dapat dibersihkan. Periksa izin folder." }
        journal.delete()
    }
    suspend fun publish(workId: String, name: String, track: PlayerViewModel.Track, sample: Boolean = false): Pair<String, Boolean> = withContext(Dispatchers.IO) {
        KaraokeWorker.lock.withLock {
            recover()
            val directory = KaraokeWorker.directory(context, workId)
            val previous = File(directory, "saved.json").takeIf { it.isFile }?.let { JSONObject(it.readText()).getString("uri") }
            if (previous != null && StorageUris.exists(context, previous.toUri())) return@withLock previous to true
            val result = File(directory, "instrumental.flac")
            val proof = JSONObject(File(directory, "result.json").readText())
            require(result.isFile && KaraokeModel.digest(result) == proof.getString("resultSha256")) { "Pratinjau berubah atau hilang; proses ulang karaoke." }
            val requested = KaraokeNaming.fileName(track.title.ifBlank { name.substringBeforeLast('.') }, track.artist, sample)
            var pending: String? = null
            var indexed = false
            try {
                MediaFileOperations.mutex.withLock {
                    val resolver = context.contentResolver
                    val tree = SettingsStore.storageTree
                    val uri = if (tree != null) {
                        val selected = tree.toUri()
                        val parent = DocumentsContract.buildDocumentUriUsingTree(selected, DocumentsContract.getTreeDocumentId(selected))
                        DocumentsContract.createDocument(resolver, parent, "audio/flac", requested)
                    } else resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, requested); put(MediaStore.MediaColumns.MIME_TYPE, "audio/flac")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/${SettingsStore.storageFolder}/")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    })
                    pending = requireNotNull(uri) { "Tidak dapat membuat file karaoke." }.toString()
                    record(JSONObject().put("uri", pending).put("workId", workId))
                    resolver.openOutputStream(uri, if (tree != null) "wt" else "w")?.use { output -> result.inputStream().use { input ->
                        val buffer = ByteArray(65536)
                        while (true) { currentCoroutineContext().ensureActive(); val n = input.read(buffer); if (n < 0) break; output.write(buffer, 0, n) }
                    } } ?: error("File karaoke tidak dapat ditulis.")
                    val hash = MessageDigest.getInstance("SHA-256")
                    resolver.openInputStream(uri)?.use { input -> val buffer = ByteArray(65536); while (true) { currentCoroutineContext().ensureActive(); val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) } } ?: error("Hasil tidak dapat diverifikasi.")
                    require(hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == proof.getString("resultSha256")) { "Verifikasi hasil simpan karaoke gagal." }
                    if (tree == null) require(resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) > 0)
                }
                val raw = requireNotNull(pending)
                // Catalogue insertion takes the shared media lock itself; do not nest that non-reentrant mutex.
                withContext(NonCancellable) {
                    MediaRepository(context).import(raw)
                    val item = requireNotNull(database.media().getByAccess(raw) ?: database.media().get(raw))
                    indexed = true
                    saved(directory, raw)
                    journal.delete()
                    val source = database.media().getByAccess(track.uri) ?: database.media().get(track.uri)
                    database.media().put(item.copy(coverPath = track.cover.takeIf { File(it).isFile }.orEmpty(),
                        artworkStatus = if (File(track.cover).isFile) "PRESENT" else item.artworkStatus, platformId = source?.platformId.orEmpty()))
                    database.media().event(MediaEvent(jobId = "", action = "KARAOKE_SAVED", kind = "AUDIO", platformId = source?.platformId.orEmpty(), at = System.currentTimeMillis()))
                }
                var lyricCopied = track.lyricUri == null
                if (track.lyricUri != null) MediaFileOperations.mutex.withLock {
                    lyricCopied = runCatching {
                        val text = context.contentResolver.openInputStream(track.lyricUri.toUri())?.use { input ->
                            val bytes = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                            while (true) { val n = input.read(buffer); if (n < 0) break; require(bytes.size() + n <= 1024 * 1024); bytes.write(buffer, 0, n) }
                            bytes.toString("UTF-8")
                        } ?: error("LRC sumber tidak dapat dibaca.")
                        require(LrcLines.parse(text).isNotEmpty())
                        val item = requireNotNull(database.media().getByAccess(raw) ?: database.media().get(raw))
                        LyricPublisher(context).publish(item, text, emptyMap())
                        true
                    }.getOrDefault(false)
                }
                raw to lyricCopied
            } finally {
                if (!indexed && pending != null) withContext(NonCancellable) {
                    if (StorageUris.delete(context, requireNotNull(pending))) journal.delete()
                }
            }
        }
    }
}
