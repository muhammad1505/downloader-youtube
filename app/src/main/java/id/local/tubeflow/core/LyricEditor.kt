package id.local.tubeflow.core

import android.content.Context
import android.util.AtomicFile
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class LyricEditor(private val context: Context) {
    data class Preview(val offsetMs: Long, val original: String, val uri: String, val currentHash: String)
    companion object { private val edits get() = MediaFileOperations.mutex }
    private val dir = File(context.noBackupFilesDir, "lyric-edits")
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun state(uri: String) = AtomicFile(File(dir, hash(uri.toByteArray()) + ".json"))
    private fun read(uri: String): ByteArray = context.contentResolver.openInputStream(uri.toUri())?.use { input ->
        input.readBytesBounded(512 * 1024)
    } ?: error("File lirik tidak dapat dibaca.")
    private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) { val n = read(buffer); if (n < 0) break; require(out.size() + n <= limit) { "LRC terlalu besar." }; out.write(buffer, 0, n) }
        return out.toByteArray()
    }
    private fun write(uri: String, bytes: ByteArray) {
        context.contentResolver.openOutputStream(uri.toUri(), "wt")?.use { it.write(bytes) } ?: error("Lirik tidak dapat ditulis.")
    }
    private fun save(file: AtomicFile, json: JSONObject) {
        val stream = file.startWrite()
        try { stream.write(json.toString().toByteArray()); file.finishWrite(stream) } catch (error: Exception) { file.failWrite(stream); throw error }
    }
    private fun load(file: AtomicFile): JSONObject? = if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) JSONObject(file.openRead().use { it.readBytes().toString(Charsets.UTF_8) }) else null

    /** Capture before the network request. Caller holds MediaFileOperations.mutex. */
    fun captureForRefetch(uri: String): ByteArray {
        replacement(uri).recover()
        require(load(state(uri))?.has("pending") != true) { "Koreksi waktu belum pulih; buka Atur waktu lirik sebelum mencari ulang." }
        return read(uri)
    }

    /** Caller holds the shared media lock; backup and journal precede truncation. */
    fun replaceForRefetch(uri: String, text: String, expected: ByteArray, durationMs: Long?) {
        LrcTiming.shift(text, 0, durationMs)
        replacement(uri).replace(text.toByteArray(Charsets.UTF_8), expected)
    }

    private fun replacement(uri: String): JournaledTextReplacement {
        val journal = AtomicFile(File(dir, hash(uri.toByteArray()) + ".refetch.json"))
        return JournaledTextReplacement(
            read = { read(uri) }, write = { write(uri, it) },
            load = { load(journal)?.let { json ->
                require(json.getString("uri") == uri) { "Jurnal LRC tidak cocok." }
                JournaledTextReplacement.Pending(
                    android.util.Base64.decode(json.getString("before"), android.util.Base64.NO_WRAP),
                    android.util.Base64.decode(json.getString("next"), android.util.Base64.NO_WRAP),
                )
            } },
            save = { pending ->
                val json = JSONObject().put("uri", uri)
                    .put("before", android.util.Base64.encodeToString(pending.before, android.util.Base64.NO_WRAP))
                    .put("next", android.util.Base64.encodeToString(pending.next, android.util.Base64.NO_WRAP))
                val required = json.toString().toByteArray().size - journal.baseFile.length()
                require((dir.listFiles()?.sumOf(File::length) ?: 0) + required <= 32L * 1024 * 1024) { "Cadangan lirik penuh; file belum diubah." }
                save(journal, json)
            },
            clear = { journal.delete() },
            committed = { state(uri).delete() },
        )
    }

    suspend fun preview(item: MediaItem): Preview = withContext(Dispatchers.IO) { edits.withLock { previewLocked(item) } }
    private fun previewLocked(item: MediaItem): Preview {
        require(item.kind == "AUDIO" && item.availability == "AVAILABLE") { "Pilih audio yang tersedia." }
        val uri = item.lyricUri ?: error("Lirik tersinkron belum tersedia.")
        replacement(uri).recover()
        val file = state(uri); var stored = load(file); var current = read(uri)
        // Resolve an interrupted edit only when the file is a known target, previous value or prefix.
        if (stored?.has("pending") == true) {
            val target = stored.getString("pending").toByteArray()
            val before = stored.getString("before").toByteArray()
            if (current.contentEquals(target)) {
                stored.put("offset", stored.getLong("pendingOffset")).put("hash", hash(target))
            } else if (!current.contentEquals(before)) {
                require(current.size < target.size && target.copyOf(current.size).contentEquals(current)) { "LRC berubah di luar aplikasi; cadangan masih tersimpan." }
                write(uri, before); require(read(uri).contentEquals(before)); current = before
            }
            stored.remove("pending"); stored.remove("before"); stored.remove("pendingOffset"); save(file, stored)
        }
        if (stored == null) {
            require((dir.listFiles()?.sumOf(File::length) ?: 0) + current.size * 4L < 32 * 1024 * 1024) { "Penyimpanan cadangan lirik penuh." }
            val original = current.toString(Charsets.UTF_8)
            require(original.toByteArray().contentEquals(current)) { "LRC bukan UTF-8 yang valid; file asli tidak diubah." }
            LrcTiming.shift(original, 0, item.durationMs)
            stored = JSONObject().put("uri", uri).put("original", original).put("hash", hash(current)).put("offset", 0)
            save(file, stored)
        }
        require(stored.getString("uri") == uri && stored.getString("hash") == hash(current)) { "LRC telah berubah di luar aplikasi; koreksi dibatalkan agar file tidak tertimpa." }
        return Preview(stored.getLong("offset"), stored.getString("original"), uri, hash(current))
    }
    suspend fun apply(item: MediaItem, preview: Preview, offset: Long) = withContext(Dispatchers.IO) { edits.withLock {
        val checked = previewLocked(item)
        require(checked.currentHash == preview.currentHash && checked.uri == preview.uri) { "LRC berubah; buka ulang pengaturan waktu." }
        val next = LrcTiming.shift(checked.original, offset, item.durationMs).toByteArray()
        val previous = read(checked.uri)
        val file = state(checked.uri); val stored = requireNotNull(load(file))
        stored.put("pending", next.toString(Charsets.UTF_8)).put("before", previous.toString(Charsets.UTF_8)).put("pendingOffset", offset)
        val required = stored.toString().toByteArray().size - file.baseFile.length()
        require((dir.listFiles()?.sumOf(File::length) ?: 0) + required <= 32L * 1024 * 1024) { "Penyimpanan cadangan lirik penuh; file belum diubah." }
        save(file, stored) // Write-ahead journal and original remain if the process/storage dies.
        try { VerifiedTextWrite.replace(previous, next, { read(checked.uri) }, { write(checked.uri, it) }) }
        catch (error: Exception) {
            if (runCatching { read(checked.uri).contentEquals(previous) }.getOrDefault(false)) {
                stored.remove("pending"); stored.remove("before"); stored.remove("pendingOffset"); save(file, stored)
            }
            throw error
        }
        stored.put("offset", offset).put("hash", hash(next)); stored.remove("pending"); stored.remove("before"); stored.remove("pendingOffset"); save(file, stored)
    } }
}
