package id.local.tubeflow.core

import android.content.Context
import android.content.ContentValues
import android.provider.MediaStore
import android.util.AtomicFile
import androidx.core.net.toUri
import org.json.JSONObject
import java.io.File

/** Shared publication path for automatic refetch and explicit selections. Caller holds media mutex. */
class LyricPublisher(private val context: Context) {
    private val database=DownloadDatabase.get(context)
    private val dao=database.downloads()
    private val journal=AtomicFile(File(context.noBackupFilesDir,"manual-lyric-publication.json"))
    private data class Target(val outputUri: String?, val sidecarUris: String?, val durationSeconds: Long)
    private fun displayNameOf(uri: android.net.Uri): String? = context.contentResolver.query(uri,
        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if (it.moveToFirst()) it.getString(0) else null }

    suspend fun recover() {
        if (!journal.baseFile.exists() && !File(journal.baseFile.path+".bak").exists()) return
        val data=JSONObject(journal.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
        val uri=data.getString("uri")
        val media=database.media()
        val output=data.getString("output")
        val recorded=(media.getByAccess(output) ?: media.get(output))?.lyricUri == uri
        require(recorded || StalePublicationCleanup.delete(context,uri)) { "Lirik parsial lama belum dapat dihapus." }
        journal.delete()
    }
    private fun pending(item: MediaItem, uri: String?) {
        if (uri == null) { journal.delete(); return }
        val data=JSONObject().put("uri",uri).put("output",item.location).toString().toByteArray()
        val stream=journal.startWrite()
        try { stream.write(data); journal.finishWrite(stream) }
        catch (failure: Exception) { journal.failWrite(stream); throw failure }
    }
    suspend fun publish(job: DownloadJob, text: String, expected: Map<String, ByteArray>): Boolean =
        write(Target(job.outputUri,job.sidecarUris,job.durationSeconds),text,expected,
            pending={ dao.sidecarPending(job.id,it) }, sidecars={ dao.sidecars(job.id,it) },
            commit={ dao.commitSidecar(job.id,"sub|$it",null) })

    suspend fun publish(item: MediaItem, text: String, expected: Map<String, ByteArray>): String {
        recover()
        val linked=item.jobId?.let { dao.get(it) }?.takeIf { it.state == JobState.COMPLETED && it.outputUri == item.location }
        if (linked != null) {
            require(publish(linked,text,expected)) { "Lirik tidak dapat disimpan; file audio tetap tersedia." }
            val lines=dao.get(linked.id)?.sidecarUris?.lines().orEmpty()
            return lines.filter { it.startsWith("sub|") }.map { it.substringAfter('|') }
                .single { displayNameOf(it.toUri())?.endsWith(".lrc",true) == true }
        }
        var result=item.lyricUri
        require(write(Target(item.location,item.lyricUri?.let { "sub|$it" },(item.durationMs ?: 0)/1000),text,expected,
            pending={ pending(item,it) }, sidecars={}, commit={ raw ->
                database.media().put(item.copy(lyricUri=raw,lyricStatus=LyricStatus.SAVED))
                result=raw; journal.delete()
            })) { "Lirik tidak dapat disimpan; file audio tetap tersedia." }
        return requireNotNull(result)
    }
    private suspend fun write(target: Target, text: String, expected: Map<String, ByteArray>,
            pending: suspend (String?) -> Unit, sidecars: suspend (String?) -> Unit,
            commit: suspend (String) -> Unit): Boolean {
        val app=context
        val resolver=app.contentResolver
        val bytes=text.toByteArray(Charsets.UTF_8)
        val output=(target.outputUri ?: return false).toUri()
        fun writeTo(target: android.net.Uri, mode: String): Boolean = runCatching {
            val stream=resolver.openOutputStream(target, mode) ?: return@runCatching false
            stream.use { it.write(bytes) }
            require(LyricEditor(app).captureForRefetch(target.toString()).contentEquals(bytes)) { "Verifikasi lirik gagal." }
            true
        }.getOrDefault(false)
        /** Lepas jurnal pending setelah penghapusan terbukti berhasil; gagal = jejak tetap untuk recovery (BUG-008/R2). */
        suspend fun dropJournal(target: android.net.Uri) {
            if(StorageUris.delete(app, target)) pending(null)
        }
        // Sidecar lama dinilai dari NAMA TAMPILAN aktual, bukan akhiran URI — provider bisa menyisipkan
        // ekstensi mime ("X.lrc.txt"). Yang benar .lrc jadi target timpa; yang salah ekstensi dibuang
        // setelah pengganti terbit (BUG-015).
        val subs=target.sidecarUris?.lines()?.filter { it.startsWith("sub|") }?.map { it.substringAfter('|') }
            .orEmpty().filter { it.isNotBlank() }.map { it.toUri() }
        val stem=displayNameOf(output)?.substringBeforeLast('.') ?: return false
        val requested="$stem.lrc"
        val validOld=subs.filter { displayNameOf(it)?.endsWith(".lrc", true) == true }
        require(validOld.map { it.toString() }.toSet() == expected.keys) { "Lirik berubah selama pencarian; coba lagi." }
        require(validOld.size <= 1) { "Beberapa file lirik tercatat; penggantian dibatalkan." }
        val stale=subs.filter { displayNameOf(it)?.endsWith(".lrc", true) != true }
        suspend fun dropStale() {
            if(stale.isEmpty()) return
            val staleRaw=stale.filter { StorageUris.delete(app, it) }.map { it.toString() }.toSet()
            val kept=target.sidecarUris?.lines()?.filter { it.isNotBlank() && it.substringAfter('|',"") !in staleRaw }.orEmpty()
            sidecars(kept.joinToString("\n").ifBlank { null })
        }
        if(validOld.isNotEmpty()) {
            val old=validOld.first()
            require(displayNameOf(old) == requested) { "Nama lirik tidak sesuai audio; file lama tidak diubah." }
            LyricEditor(app).replaceForRefetch(old.toString(), text, requireNotNull(expected[old.toString()]), target.durationSeconds.takeIf { it > 0 }?.times(1000))
            dropStale()
            return true
        }
        val uri = if(android.provider.DocumentsContract.isDocumentUri(app, output)) {
            // Verify the actual parent; opaque providers without parent information fail closed.
            // ExternalStorageProvider document IDs supply a path fallback, not an arbitrary tree root.
            val parentId=runCatching {
                resolver.query(output, arrayOf("parent_document_id"), null, null, null)?.use { c -> if(c.moveToFirst()) c.getString(0) else null }
            }.getOrNull()
            // Fail closed for opaque providers rather than publishing beside the wrong audio.
            val documentId = android.provider.DocumentsContract.getDocumentId(output)
            val pathParent = if (output.authority == "com.android.externalstorage.documents" && documentId.contains('/')) documentId.substringBeforeLast('/') else null
            val resolvedParent = parentId ?: pathParent ?: error("Induk audio tidak dapat diverifikasi; file tidak diubah.")
            val parent=android.provider.DocumentsContract.buildDocumentUriUsingTree(output, resolvedParent)
            // BUG-015: paksa kembali nama yang diminta bila provider menyisipkan ekstensi mime.
            val made=runCatching { android.provider.DocumentsContract.createDocument(resolver, parent, "text/plain", requested) }.getOrNull() ?: return false
            // Jurnal sebelum menulis: proses mati di tengah penulisan tetap terlacak (BUG-008/R2).
            pending(made.toString())
            val actual=displayNameOf(made)
            val final=if(actual != null && actual != requested) runCatching { android.provider.DocumentsContract.renameDocument(resolver, made, requested) }.getOrNull() ?: made else made
            // Setelah rename document ID bisa berubah — jurnal ikut diperbarui (R2).
            pending(final.toString())
            if(!writeTo(final, "wt")) { dropJournal(final); return false }
            // R5: verifikasi nama tampilan akhir — rename yang ditolak provider tidak boleh dilaporkan sukses ("X.lrc.txt").
            if(displayNameOf(final) != requested) { dropJournal(final); return false }
            final
        } else {
            val relative=runCatching {
                resolver.query(output, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)?.use { c -> if(c.moveToFirst()) c.getString(0) else null }
            }.getOrNull() ?: return false
            val created=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "$stem.lrc")
                put(MediaStore.Downloads.MIME_TYPE, "text/*") // sengaja wildcard seperti worker: ekstensi ikut DISPLAY_NAME, bukan tebakan provider
                put(MediaStore.Downloads.RELATIVE_PATH, relative)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }) ?: return false
            pending(created.toString())
            if(!writeTo(created, "w")) { dropJournal(created); return false }
            if(resolver.update(created, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null) <= 0) { dropJournal(created); return false }
            // Nama MediaStore ikut DISPLAY_NAME; verifikasi sama seperti jalur SAF (R5).
            if(displayNameOf(created) != requested) { dropJournal(created); return false }
            created
        }
        // Perbarui daftar sidecar: buang baris lirik lama/salah ekstensi dan catat pengganti baru.
        dropStale()
        commit(uri.toString())
        return true
    }
}
