package id.local.tubeflow.core

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.room.withTransaction
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest

/** One URI catalogue in the existing Room database; scanning never copies or transcodes media. */
/** Ringkasan pindai: diperiksa, gagal dibaca, dan baris yang benar-benar terlihat dari semua sumber. */
data class ScanSummary(val checked: Int, val failed: Int, val discovered: Int)

class MediaRepository(private val context: Context) {
    private val resolver = context.contentResolver
    private val database = DownloadDatabase.get(context)
    private val dao = database.media()
    companion object { private val operations get() = MediaFileOperations.mutex }
    private val renameJournal = AtomicFile(File(context.noBackupFilesDir, "library-rename.json"))
    private fun displayName(raw: String): String = resolver.query(raw.toUri(), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        require(c.moveToFirst()) { "File tidak tersedia." }; c.getString(0).orEmpty()
    } ?: error("Nama file tidak dapat dibaca.")
    private fun mediaRename(raw: String, name: String) {
        require(resolver.update(raw.toUri(), ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name) }, null, null) > 0) { "Rename ditolak provider." }
    }
    /**
     * Baris katalog setelah rename berhasil. Nama file baru sekaligus menjadi `title` supaya judul
     * di kartu dan pemutar ikut nama yang dipilih pengguna (bukan hanya baris kedua kartu).
     * Waktu modifikasi diambil ulang dari provider agar jalur "tidak berubah" pada pemindaian
     * berikutnya mempertahankan judul ini, bukan mengembalikannya ke metadata lama.
     */
    private fun renamedRow(item: MediaItem, access: String, name: String): MediaItem {
        val target = access.toUri()
        val column = if (target.authority == "media") MediaStore.MediaColumns.DATE_MODIFIED else DocumentsContract.Document.COLUMN_LAST_MODIFIED
        val modified = runCatching {
            resolver.query(target, arrayOf(column), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
        }.getOrNull()
        return item.copy(title = name.substringBeforeLast('.'), displayName = name,
            modifiedAt = modified ?: item.modifiedAt, checkedAt = System.currentTimeMillis())
    }
    private fun saveRename(entries: List<RenameBatch.Entry>) {
        val data = JSONArray(); entries.forEach { data.put(JSONObject().put("uri", it.uri).put("before", it.before).put("after", it.after)) }
        val stream = renameJournal.startWrite()
        try { stream.write(data.toString().toByteArray()); renameJournal.finishWrite(stream) } catch (error: Exception) { renameJournal.failWrite(stream); throw error }
    }
    private suspend fun recoverRename() {
        if (!renameJournal.baseFile.exists() && !File(renameJournal.baseFile.path + ".bak").exists()) return
        val data = JSONArray(renameJournal.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
        require(data.length() <= 2) { "Jurnal rename tidak valid." }
        val entries = (0 until data.length()).map { i -> data.getJSONObject(i).let { RenameBatch.Entry(it.getString("uri"), it.getString("before"), it.getString("after")) } }
        // Stable MediaStore IDs permit recovery without searching or replacing user documents.
        for (entry in entries.asReversed()) {
            val current = displayName(entry.uri)
            require(current == entry.before || current == entry.after) { "File berubah di luar aplikasi; jurnal rename dipertahankan." }
            if (current != entry.before) {
                val item = dao.get(canonical(entry.uri))
                val folder = item?.folder ?: mediaFolder(entry.uri)
                checkMediaCollision(entry.uri, folder, entry.before)
                mediaRename(entry.uri, entry.before)
                require(displayName(entry.uri) == entry.before) { "Pemulihan nama belum berhasil." }
            }
            dao.get(canonical(entry.uri))?.let { dao.put(it.copy(displayName = entry.before, checkedAt = 0)) }
        }
        renameJournal.delete()
    }
    private fun mediaFolder(raw: String): String = resolver.query(raw.toUri(), arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)?.use { c ->
        require(c.moveToFirst()); c.getString(0).orEmpty().trimEnd('/')
    } ?: error("Lokasi file tidak dapat dibaca.")
    private fun checkMediaCollision(raw: String, folder: String, name: String) {
        val uri = raw.toUri()
        resolver.query(MediaStore.Files.getContentUri(uri.pathSegments.first()), arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?", arrayOf(folder.trimEnd('/') + "/", name), null)?.use { c ->
            while (c.moveToNext()) require(c.getLong(0).toString() == uri.lastPathSegment) { "File dengan nama tersebut sudah ada." }
        } ?: error("Konflik nama belum dapat diperiksa.")
    }

    fun canonical(raw: String): String {
        val uri = raw.toUri()
        val media = if (DocumentsContract.isDocumentUri(context, uri))
            runCatching { MediaStore.getMediaUri(context, uri) }.getOrNull() ?: uri else uri
        if (media.authority == "media" && media.lastPathSegment?.toLongOrNull() != null) {
            val volume = runCatching {
                resolver.query(media, arrayOf(MediaStore.MediaColumns.VOLUME_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0)?.takeIf(String::isNotBlank) else null
                }
            }.getOrNull() ?: media.pathSegments.first()
            return "content://media/$volume/file/${media.lastPathSegment}"
        }
        return media.toString()
    }

    suspend fun indexDownload(id: String, manual: Boolean = false) = withContext(Dispatchers.IO) {
        operations.withLock {
            recoverRename()
            val job = database.downloads().get(id) ?: return@withLock
            if (job.state != JobState.COMPLETED || (!job.autoLibrary && !manual) || job.outputUri == null) return@withLock
            index(job.outputUri, job, job.subfolder.ifBlank { "Downloads/${SettingsStore.storageFolder}" })
        }
    }

    suspend fun import(raw: String) = withContext(Dispatchers.IO) { operations.withLock { recoverRename(); index(raw, null, "File pilihan") } }

    private suspend fun index(raw: String, job: DownloadJob?, fallbackFolder: String, lrc: String? = null) {
        val computedKey = canonical(raw)
        var old = dao.get(computedKey) ?: dao.getByAccess(raw)
        try {
            // Retain a working owned MediaStore location when a picker supplies another alias.
            val access = old?.location?.takeIf { candidate -> candidate.toUri().authority == "media" &&
                (candidate == raw || runCatching { resolver.openFileDescriptor(candidate.toUri(), "r")?.use { } != null }.getOrDefault(false))
            } ?: raw
            val uri = access.toUri()
            var name = ""; var size: Long? = null; var modified: Long? = null
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (!c.moveToFirst()) throw FileNotFoundException("File tidak tersedia.")
                name = c.getString(0).orEmpty(); if (!c.isNull(1)) size = c.getLong(1).takeIf { it >= 0 }
            } ?: throw FileNotFoundException()
            resolver.openFileDescriptor(uri, "r")?.use { } ?: throw FileNotFoundException()
            val mime = resolver.getType(uri).orEmpty()
            var kind = SmartMedia.classify(mime, name) ?: return
            var folder = fallbackFolder
            if (uri.authority == "media") runCatching {
                resolver.query(uri, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DATE_MODIFIED), null, null, null)?.use { c ->
                    if (c.moveToFirst()) { folder = c.getString(0).orEmpty().trimEnd('/'); if (!c.isNull(1)) modified = c.getLong(1) }
                }
            } else if (DocumentsContract.isDocumentUri(context, uri)) runCatching {
                resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { c ->
                    if (c.moveToFirst() && !c.isNull(0)) modified = c.getLong(0)
                }
            }
            // Satu file fisik dapat tercatat dua kali (baris MediaStore + baris dokumen SAF). Sebelum
            // membuat baris baru, cocokkan folder+nama+ukuran agar koleksi tidak menduplikasi file.
            if (old == null) old = dao.findByName(folder, name)?.takeIf { it.sizeBytes == null || size == null || it.sizeBytes == size }
            val key = old?.uri ?: computedKey
            val lyricCandidate = job?.sidecarUris?.lines()?.firstOrNull { line -> line.startsWith("sub|") &&
                runCatching { displayName(line.substringAfter('|')).endsWith(".lrc", true) }.getOrDefault(false)
            }?.substringAfter('|') ?: lrc ?: old?.lyricUri
            val lyric = lyricCandidate?.takeIf { runCatching { resolver.openFileDescriptor(it.toUri(), "r")?.use { } != null }.getOrDefault(false) }
            val lyricStatus = if (lyric != null) LyricStatus.SAVED else if ((job?.lyricStatus ?: old?.lyricStatus) == LyricStatus.SAVED) LyricStatus.SAVE_FAILED else job?.lyricStatus ?: old?.lyricStatus.orEmpty()
            if (old != null && old.modifiedAt != null && old.modifiedAt == modified && old.sizeBytes == size &&
                (old.coverPath.isBlank() || File(old.coverPath).isFile)) {
                dao.put(old.copy(jobId = job?.id ?: old.jobId, displayName = name, folder = folder,
                    availability = "AVAILABLE", checkedAt = System.currentTimeMillis(), accessUri = access, lyricUri = lyric,
                    lyricStatus = lyricStatus))
                return
            }
            var title = job?.title ?: name.substringBeforeLast('.')
            var artist = job?.artist.orEmpty(); var album = ""; var albumArtist = ""; var genre = ""; var track = ""; var year = ""
            var duration: Long? = job?.durationSeconds?.takeIf { it > 0 }?.times(1000)
            var width: Int? = null; var height: Int? = null; var metadata = "UNCHECKED"; var artwork = "UNCHECKED"
            var cover = job?.coverPath?.takeIf { File(it).isFile } ?: old?.coverPath?.takeIf { File(it).isFile }.orEmpty()
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                fun tag(key: Int) = retriever.extractMetadata(key)?.trim().orEmpty()
                duration = tag(MediaMetadataRetriever.METADATA_KEY_DURATION).toLongOrNull()?.takeIf { it > 0 } ?: duration
                // Embedded artwork is not a video track; trust the container MIME for audio.
                if (mime.startsWith("video/") || (!mime.startsWith("audio/") && tag(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes")) kind = "VIDEO"
                if (kind == "AUDIO") {
                    title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE).ifBlank { title }
                    artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST).ifBlank { artist }
                    album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    albumArtist = tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    genre = tag(MediaMetadataRetriever.METADATA_KEY_GENRE)
                    track = tag(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                    year = tag(MediaMetadataRetriever.METADATA_KEY_YEAR)
                    metadata = if (title.isNotBlank() && artist.isNotBlank()) "READY" else "PARTIAL"
                } else {
                    width = tag(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toIntOrNull()
                    height = tag(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toIntOrNull()
                    metadata = "READY"
                    artist = ""
                }
                if (cover.isBlank()) {
                    val bitmap = if (kind == "VIDEO") retriever.getScaledFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 180)
                    else retriever.embeddedPicture?.takeIf { it.size <= 5 * 1024 * 1024 }?.let { bytes ->
                        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        val options = android.graphics.BitmapFactory.Options().apply {
                            inSampleSize = 1
                            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 800) inSampleSize *= 2
                        }
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    }
                    bitmap?.let {
                        val dir = File(context.noBackupFilesDir, "library-covers").apply { mkdirs() }
                        val nameHash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { b -> "%02x".format(b) }
                        val file = File(dir, "$nameHash.jpg")
                        try { file.outputStream().use { stream -> require(it.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, stream)) }; cover = file.absolutePath }
                        finally { it.recycle() }
                        // Preview cache only; final audio/video files are never touched.
                        CacheBudget.prune(dir, 24L * 1024 * 1024, keep = setOf(file))
                    }
                }
                artwork = if (cover.isNotBlank()) "PRESENT" else "ABSENT"
            } catch (_: Exception) { metadata = "FAILED" }
            finally { retriever.release() }
            if (job?.processingNotice?.startsWith("Metadata") == true) metadata = "FAILED"
            val item = MediaItem(key, job?.id ?: old?.jobId, kind, title, name, mime, folder, size, duration,
                artist, album, albumArtist, genre, track, year, job?.creator ?: old?.creator.orEmpty(),
                job?.platformId ?: old?.platformId.orEmpty(), cover, width, height, "AVAILABLE", metadata, artwork,
                lyric, lyricStatus,
                old?.addedAt ?: System.currentTimeMillis(), System.currentTimeMillis(), modified, access)
            dao.put(item)
            if (old == null) dao.event(MediaEvent(jobId = job?.id.orEmpty(), action = "ADDED", kind = kind, platformId = item.platformId, at = item.addedAt))
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (old != null) {
                if (MediaAvailability.probe(context,old) == MediaAvailability.State.MISSING) {
                    dao.remove(old.uri)
                    dao.event(MediaEvent(jobId=old.jobId.orEmpty(),action="UNAVAILABLE",kind=old.kind,platformId=old.platformId,at=System.currentTimeMillis()))
                    return
                }
                val availability = if (error is SecurityException) "ACCESS_DENIED" else "UNAVAILABLE"
                dao.put(old.copy(availability = availability, checkedAt = System.currentTimeMillis()))
                if (old.availability != availability) dao.event(MediaEvent(jobId = old.jobId.orEmpty(), action = availability, kind = old.kind, platformId = old.platformId, at = System.currentTimeMillis()))
            }
            else throw error
        }
    }

    suspend fun scan(): ScanSummary = withContext(Dispatchers.IO) { operations.withLock {
        recoverRename()
        var checked = 0; var failed = 0
        val rows = mutableListOf<Triple<String, String, String>>()
        fun add(uri: String, name: String, folder: String) {
            if (rows.none { it.second == name && it.third == folder }) {
                require(rows.size < 4000) { "Folder terlalu besar; impor file pilihan." }
                rows += Triple(uri, name, folder)
            }
        }
        // Dua sumber digabung: MediaStore (perlu izin baca untuk file lama milik versi/SAF lain) dan
        // grant SAF bila ada. Beberapa perangkat tetap menyembunyikan baris MediaStore owner-kosong
        // meski izin baca diberikan, jadi jalur dokumen tidak boleh saling meniadakan.
        var storeError: Exception? = null
        try {
            val folder = "Download/${SettingsStore.storageFolder}/"
            val escaped = folder.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.RELATIVE_PATH),
                "(${MediaStore.Downloads.RELATIVE_PATH} = ? OR ${MediaStore.Downloads.RELATIVE_PATH} LIKE ? ESCAPE '\\') AND ${MediaStore.Downloads.IS_PENDING} = 0",
                arrayOf(folder, "$escaped%"), null)?.use { c ->
                while (c.moveToNext()) add(ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0)).toString(), c.getString(1), c.getString(2).orEmpty().trimEnd('/'))
            } ?: throw FileNotFoundException("Folder belum dapat dibaca.")
        } catch (error: Exception) { storeError = error }
        val tree = SettingsStore.storageTree ?: StorageFolderAccess.defaultTree(context, SettingsStore.storageFolder)?.toString()
        var treeError: Exception? = null
        if (tree != null) {
            val root = tree.toUri()
            fun walk(id: String, folder: String, depth: Int) {
                require(depth <= 8 && rows.size < 4000) { "Batas pemindaian folder tercapai." }
                resolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(root, id), arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) walk(c.getString(0), "$folder/${c.getString(1)}", depth + 1)
                        else add(DocumentsContract.buildDocumentUriUsingTree(root, c.getString(0)).toString(), c.getString(1), folder)
                    }
                } ?: error("Akses folder tidak tersedia.")
            }
            val rootLabel = if (SettingsStore.storageTree != null) "Folder pilihan" else "Download/${SettingsStore.storageFolder}"
            try { walk(DocumentsContract.getTreeDocumentId(root), rootLabel, 0) } catch (error: Exception) { treeError = error }
        }
        if (rows.isEmpty() && (treeError != null || storeError != null)) throw (treeError ?: storeError!!)
        val jobs = database.downloads().observe().let { flow -> flow.first() }
        val jobsByUri = jobs.filter { it.state == JobState.COMPLETED && it.outputUri != null }.associateBy { canonical(it.outputUri!!) }
        val lyrics = rows.filter { it.second.endsWith(".lrc", true) }.associate { (uri, name, folder) -> (folder to name.substringBeforeLast('.')) to uri }
        for ((uri, name, folder) in rows) {
            if (SmartMedia.classify(resolver.getType(uri.toUri()), name) == null) continue
            val job = jobsByUri[canonical(uri)]
            try { index(uri, job, folder, lyrics[folder to name.substringBeforeLast('.')]); checked++ } catch (_: Exception) { failed++ }
        }
        for (item in dao.oldest(4000)) if (item.checkedAt < System.currentTimeMillis() - 1000) {
            try { index(item.location, item.jobId?.let { database.downloads().get(it) }, item.folder); } catch (_: Exception) { failed++ }
        }
        ScanSummary(checked, failed, rows.size)
    } }

    /** Cheap presence checks; no metadata extraction/LRCLIB request for unchanged media. */
    suspend fun validateRecent(force: Boolean = false) = withContext(Dispatchers.IO) { operations.withLock {
        recoverRename()
        val checked = mutableListOf<String>()
        for (item in dao.oldest(if (force) 4000 else 100)) {
            checked += item.uri
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            when (MediaAvailability.probe(context,item)) {
                MediaAvailability.State.MISSING -> {
                    dao.remove(item.uri)
                    dao.event(MediaEvent(jobId=item.jobId.orEmpty(),action="UNAVAILABLE",kind=item.kind,platformId=item.platformId,at=System.currentTimeMillis()))
                }
                MediaAvailability.State.DENIED -> if (item.availability != "ACCESS_DENIED")
                    dao.put(item.copy(availability="ACCESS_DENIED",checkedAt=System.currentTimeMillis()))
                MediaAvailability.State.PRESENT -> if (item.availability != "AVAILABLE")
                    index(item.location,item.jobId?.let { database.downloads().get(it) },item.folder)

                MediaAvailability.State.UNKNOWN -> Unit // Failure to inspect is never proof of deletion.
            }
        }
        // Rotate the bounded window without invalidating observers once per healthy file.
        checked.chunked(500).forEach { dao.checked(it,System.currentTimeMillis()) }
    } }

    suspend fun refresh(raw: String) = withContext(Dispatchers.IO) { operations.withLock {
        (dao.get(canonical(raw)) ?: dao.getByAccess(raw))?.let { index(it.location, it.jobId?.let { id -> database.downloads().get(id) }, it.folder) }
    } }
    suspend fun refreshAllAvailability() = withContext(Dispatchers.IO) { operations.withLock {
        dao.oldest(4000).forEach { item -> runCatching { index(item.location, item.jobId?.let { database.downloads().get(it) }, item.folder) } }
    } }

    suspend fun rename(item: MediaItem, rawName: String): MediaItem = withContext(Dispatchers.IO) { operations.withLock {
        recoverRename()
        require(item.availability == "AVAILABLE") { "File tidak dapat diakses." }
        val name = SmartMedia.renameName(rawName, item.displayName)
        if (name == item.displayName) return@withLock item
        require(dao.collision(item.folder, name, item.uri) == null) { "Nama tersebut sudah digunakan." }
        val uri = item.location.toUri()
        if (uri.authority == "media") {
            val entries = mutableListOf<RenameBatch.Entry>()
            item.lyricUri?.let { raw ->
                // Catalogue keys are not operation URIs. Android10 rejects movement via Files.
                val lyric = raw
                require(lyric.toUri().authority == "media" && mediaFolder(lyric) == item.folder) { "Rename pasangan lirik pada provider ini belum didukung; file tetap aman." }
                val before = displayName(lyric)
                require(before.substringBeforeLast('.') == item.displayName.substringBeforeLast('.')) { "Nama lirik berbeda dari audio; rename dibatalkan." }
                val after = name.substringBeforeLast('.') + "." + before.substringAfterLast('.')
                checkMediaCollision(lyric, item.folder, after)
                entries += RenameBatch.Entry(lyric, before, after)
            }
            checkMediaCollision(item.location, item.folder, name)
            entries += RenameBatch.Entry(item.location, item.displayName, name)
            saveRename(entries)
            try { RenameBatch.apply(entries, ::displayName, ::mediaRename) }
            catch (error: Exception) {
                if (entries.all { runCatching { displayName(it.uri) == it.before }.getOrDefault(false) }) renameJournal.delete()
                throw error
            }
            val changed = renamedRow(item, item.location, name)
            // Stable URIs preserve the lyric baseline and download identity.
            database.withTransaction { dao.put(changed); dao.event(MediaEvent(jobId = item.jobId.orEmpty(), action = "RENAMED", kind = item.kind, platformId = item.platformId, at = System.currentTimeMillis())) }
            renameJournal.delete()
            changed
        } else if (DocumentsContract.isDocumentUri(context, uri) && item.lyricUri == null && DocumentsContract.isTreeUri(uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            require(docId.contains('/')) { "Provider tidak menyediakan lokasi induk untuk rename aman." }
            val parent = docId.substringBeforeLast('/')
            resolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(uri, parent), arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { c ->
                while (c.moveToNext()) require(c.getString(0) != name || c.getString(1) == docId) { "Nama tersebut sudah digunakan." }
            } ?: error("Konflik nama belum dapat diperiksa.")
            val renamed = DocumentsContract.renameDocument(resolver, uri, name)?.toString() ?: error("Rename ditolak provider.")
            if (displayName(renamed) != name) {
                val restored = runCatching { DocumentsContract.renameDocument(resolver, renamed.toUri(), item.displayName)?.toString() }.getOrNull() ?: renamed
                val actual = displayName(restored)
                val repaired = item.copy(uri = canonical(restored), accessUri = restored, displayName = actual, checkedAt = 0)
                database.withTransaction { dao.put(repaired); if (repaired.uri != item.uri) dao.remove(item.uri); item.jobId?.let { database.downloads().renamedOutput(it, restored) } }
                error(if (actual == item.displayName) "Provider mengubah nama; nama lama dipulihkan." else "Provider menolak pemulihan nama; file tetap tersedia dengan nama aktual di koleksi.")
            }
            val changed = renamedRow(item, renamed, name).copy(uri = canonical(renamed), accessUri = renamed)
            database.withTransaction { dao.put(changed); if (changed.uri != item.uri) dao.remove(item.uri); item.jobId?.let { database.downloads().renamedOutput(it, renamed) } }
            changed
        } else error("Provider ini belum mendukung rename aman, terutama pasangan media dan lirik. File tetap tersedia.")
    } }

    suspend fun delete(item: MediaItem) = withContext(Dispatchers.IO) { operations.withLock {
        require(StorageUris.delete(context, item.location)) { "File gagal dihapus; periksa izin penyimpanan." }
        var sidecarFailed = false
        val sidecars = item.jobId?.let { database.downloads().get(it)?.sidecarUris?.lines()?.mapNotNull { line -> line.substringAfter('|', "").takeIf(String::isNotBlank) } }.orEmpty()
        (sidecars + listOfNotNull(item.lyricUri)).distinct().forEach { if (!StorageUris.delete(context, it)) sidecarFailed = true }
        // Baris ikut dihapus: file sudah tiada dan pengguna memilih Hapus, jadi tampilan harus
        // bersih. Pencegahan duplikat tetap lewat riwayat download, bukan baris media yang tersisa.
        dao.remove(item.uri)
        dao.event(MediaEvent(jobId = item.jobId.orEmpty(), action = "DELETED", kind = item.kind, platformId = item.platformId, at = System.currentTimeMillis()))
        require(!sidecarFailed) { "Media terhapus, tetapi lirik gagal dihapus." }
    } }
}
