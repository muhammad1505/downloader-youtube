package id.local.tubeflow.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.NoSuchFileException

/** Only proven absence removes a catalogue row; an inaccessible provider is not a deleted file. */
internal object MediaAvailability {
    enum class State { PRESENT, MISSING, DENIED, UNKNOWN }

    internal fun decide(rowPresent: Boolean?, readAllowed: Boolean, physicalMissing: Boolean = false): State = when {
        !readAllowed -> State.DENIED
        rowPresent == false || physicalMissing -> State.MISSING
        rowPresent == true -> State.PRESENT
        else -> State.UNKNOWN
    }

    /**
     * Provider SAF membungkus dokumen yang sudah hilang sebagai `IllegalArgumentException`, dengan
     * `FileNotFoundException` di rantai penyebab. Hanya bukti itu yang boleh menghapus baris katalog;
     * kegagalan provider lain tetap UNKNOWN dan izin yang dicabut tetap DENIED.
     */
    internal fun classifyFailure(error: Throwable, readAllowed: Boolean): State {
        if (!readAllowed) return State.DENIED
        var current: Throwable? = error
        val seen = mutableSetOf<Throwable>()
        while (current != null && seen.add(current)) {
            when (current) {
                is SecurityException -> return State.DENIED
                is FileNotFoundException, is NoSuchFileException -> return State.MISSING
            }
            if (current.message?.contains("Missing file", ignoreCase = true) == true) return State.MISSING
            current = current.cause
        }
        return State.UNKNOWN
    }

    fun probe(context: Context, item: MediaItem): State = probe(context, item.location, item.kind)

    @Suppress("DEPRECATION")
    fun probe(context: Context, raw: String, kind: String): State {
        val uri = raw.toUri()
        if (uri.scheme == "file") {
            val path = uri.path ?: return State.UNKNOWN
            return if (File(path).exists()) State.PRESENT else State.MISSING
        }
        val granted = if (uri.authority == "media") {
            val permission = if (Build.VERSION.SDK_INT >= 33) {
                if (kind == "AUDIO") android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_MEDIA_VIDEO
            } else android.Manifest.permission.READ_EXTERNAL_STORAGE
            val broadRead = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            // Downloads owned by another installation may be hidden even with media permission.
            val downloads = uri.pathSegments.getOrNull(1) == "downloads"
            broadRead && (!downloads || Build.VERSION.SDK_INT == 29 && Environment.isExternalStorageLegacy()) ||
                context.checkUriPermission(uri,android.os.Process.myPid(),android.os.Process.myUid(),
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED
        } else context.checkUriPermission(uri, android.os.Process.myPid(), android.os.Process.myUid(),
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED
        return try {
            val projection = if (uri.authority == "media") arrayOf(OpenableColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATA)
                else arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return decide(false, granted)
                val path = if (uri.authority == "media" && Build.VERSION.SDK_INT == 29 &&
                    Environment.isExternalStorageLegacy() && granted) cursor.getString(1) else null
                val missing = path?.takeIf { it.startsWith("/") }?.let { Files.notExists(File(it).toPath()) } == true
                // A visible row can be owned by this app even without broad read permission.
                if (missing) State.MISSING else {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { }
                        ?: return State.UNKNOWN
                    State.PRESENT
                }
            } ?: State.UNKNOWN
        } catch (error: Exception) { classifyFailure(error, granted) }
    }
}

/** Keputusan murni untuk merekonsiliasi baris katalog dengan isi folder yang baru dipindai. */
internal object MediaSweep {
    /**
     * Baris yang foldernya selesai dipindai tetapi nama berkasnya tidak terlihat. Kandidat ini masih
     * harus diperiksa kehadirannya; folder yang tidak dapat dipindai tidak boleh masuk karena
     * ketidakhadiran hasil bukan bukti file dihapus.
     */
    fun unconfirmed(
        catalog: List<MediaItem>,
        scannedFolders: Set<String>,
        discovered: Set<Pair<String, String>>,
    ): List<MediaItem> = catalog.filter { isUnconfirmed(it, scannedFolders, discovered) }

    /** Satu baris yang namanya tidak terlihat di folder yang pemindaiannya selesai. */
    fun isUnconfirmed(
        item: MediaItem,
        scannedFolders: Set<String>,
        discovered: Set<Pair<String, String>>,
    ): Boolean = item.folder in scannedFolders && (item.folder to item.displayName) !in discovered

    /**
     * Baris yang perlu diperiksa pada penyegaran otomatis: namanya tidak terlihat di folder yang
     * selesai dipindai, atau lokasinya berada di sumber yang baru gagal dipindai — hasil kosong di
     * sumber gagal bukan bukti file hilang, tetapi barisnya tetap harus dipastikan kehadirannya.
     */
    fun needsCheck(
        item: MediaItem,
        scannedFolders: Set<String>,
        discovered: Set<Pair<String, String>>,
        unreadablePrefixes: List<String>,
    ): Boolean {
        if (isUnconfirmed(item, scannedFolders, discovered)) return true
        if (unreadablePrefixes.isEmpty()) return false
        val access = CollectionFolders.normalize(item.location)
        return unreadablePrefixes.any { access.startsWith(it) || item.uri.startsWith(it) }
    }
}
