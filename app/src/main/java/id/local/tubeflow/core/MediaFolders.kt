package id.local.tubeflow.core

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Satu berkas media sejenis di folder yang sama dengan berkas yang dibuka dari aplikasi lain. */
data class FolderMedia(val uri: String, val title: String)

/**
 * Antrean untuk berkas luar: TubeFlow memutar berkas yang dikirim aplikasi lain beserta media
 * sejenis di folder yang sama, sehingga tombol berikut/sebelumnya tetap berguna walau berkas bukan
 * hasil unduhan TubeFlow. Perencanaannya murni agar dapat diuji tanpa perangkat.
 */
object MediaFolderQueue {
    /**
     * Urutan berkas menurut judul, berkas yang dibuka selalu ikut walau tidak muncul di hasil
     * pemindaian (mis. baru dibuat). Null berarti antrean folder tidak dapat dipakai — pemanggil
     * kembali ke perilaku satu berkas.
     */
    fun plan(siblings: List<FolderMedia>, opened: FolderMedia): Pair<List<FolderMedia>, Int>? {
        val unique = LinkedHashMap<String, FolderMedia>()
        (siblings + opened).forEach { if (it.uri.isNotBlank()) unique.putIfAbsent(it.uri, it) }
        if (unique.size <= 1) return null
        val ordered = unique.values.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { it.title.ifBlank { it.uri } },
        )
        val index = ordered.indexOfFirst { it.uri == opened.uri }
        if (index < 0) return null
        return ordered to index
    }
}

/**
 * Membaca daftar media sejenis di folder sebuah URI lewat MediaStore. URI dari provider lain
 * (mis. folder sementara aplikasi pesan) biasanya tidak punya kolom folder; pemanggil lalu memakai
 * antrean satu berkas seperti sebelumnya.
 */
class MediaFolders(private val context: Context) {
    suspend fun siblings(uri: String, kind: IncomingMedia.Kind): List<FolderMedia>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val parsed = uri.toUri()
                val resolver = context.contentResolver
                var openedId = -1L
                val folder = resolver.query(
                    parsed,
                    arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns._ID),
                    null, null, null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        openedId = cursor.getLong(1)
                        cursor.getString(0)
                    } else null
                }?.takeIf { it.isNotBlank() } ?: return@runCatching null
                // Koleksi diambil dari URI yang dikirim, bukan dari nama volume: "external" dan
                // "external_primary" menunjuk baris yang sama tetapi menghasilkan URI berbeda,
                // sehingga berkas yang dibuka bisa muncul dua kali di antrean.
                val segments = parsed.pathSegments
                val collection = when {
                    parsed.scheme == "content" && segments.size > 1 ->
                        "content://${parsed.authority}/" + segments.dropLast(1).joinToString("/")
                    kind == IncomingMedia.Kind.AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY).toString()
                    else -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY).toString()
                }.toUri()
                val selection = buildString {
                    append("${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.IS_PENDING} = 0")
                    if (openedId >= 0) append(" AND ${MediaStore.MediaColumns._ID} != ?")
                }
                val arguments = buildList {
                    add(folder.trimEnd('/') + "/")
                    if (openedId >= 0) add(openedId.toString())
                }.toTypedArray()
                val entries = mutableListOf<FolderMedia>()
                resolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                    selection, arguments,
                    "${MediaStore.MediaColumns.DISPLAY_NAME} ASC",
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val name = cursor.getString(1).orEmpty()
                        val title = MediaIntent.title(name).ifBlank { name }
                        entries += FolderMedia(ContentUris.withAppendedId(collection, id).toString(), title)
                    }
                }
                entries.takeIf { it.isNotEmpty() }
            }.getOrNull()
        }
}
