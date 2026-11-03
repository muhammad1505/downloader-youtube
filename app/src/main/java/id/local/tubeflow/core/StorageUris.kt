package id.local.tubeflow.core

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toUri

/**
 * Penghapusan URI penyimpanan tunggal untuk worker, riwayat, dan recovery (BUG-003).
 *
 * Dulu tiap tempat memakai pemeriksaan sendiri (`scheme == "document"` yang tak pernah cocok untuk
 * `content://`), sehingga file SAF gagal dihapus sementara UI melaporkan berhasil.
 * Sekarang memakai [DocumentsContract.isDocumentUri] untuk memilih jalur yang benar.
 */
object StorageUris {

    /** URI dokumen SAF (`content://…/document/…` atau `/tree/…/document/…`), bukan baris MediaStore. */
    fun isDocument(context: Context, uri: Uri): Boolean = DocumentsContract.isDocumentUri(context, uri)

    /**
     * Menghapus URI dan mengembalikan true bila file/baris benar-benar hilang atau memang sudah tidak ada.
     * false berarti provider/izin menolak — pemanggil wajib menganggap file MASIH ADA (jangan melapor sukses).
     * Folder koleksi yang diimpor dengan grant baca saja akan selalu sampai ke cabang false ini.
     */
    fun delete(context: Context, raw: String): Boolean {
        val uri = runCatching { raw.toUri() }.getOrNull() ?: return false
        return delete(context, uri)
    }

    /** Lihat [delete]. */
    fun delete(context: Context, uri: Uri): Boolean {
        val removed = runCatching {
            if (uri.scheme == "content" && isDocument(context, uri)) {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } else {
                context.contentResolver.delete(uri, null, null) > 0
            }
        }.getOrDefault(false)
        // Provider sering hanya mengembalikan false/0 tanpa pesan; bedakan "sudah hilang" dari "ditolak".
        return removed || !exists(context, uri)
    }

    /**
     * True bila [uri] berada di tree SAF yang grant tersimpannya hanya baca. Penghapusan dokumen pada
     * grant seperti itu pasti ditolak provider, jadi pemanggil perlu memberi petunjuk yang bisa ditindak.
     */
    fun lacksWriteGrant(context: Context, uri: Uri): Boolean = runCatching {
        val treeId = DocumentsContract.getTreeDocumentId(uri)
        context.contentResolver.persistedUriPermissions.any { grant ->
            grant.isReadPermission && !grant.isWritePermission && grant.uri.authority == uri.authority &&
                runCatching { DocumentsContract.getTreeDocumentId(grant.uri) }.getOrNull() == treeId
        }
    }.getOrDefault(false)

    /**
     * Masih tersedia di provider? Gagal query dianggap MASIH ADA (konservatif) supaya izin yang dicabut
     * tidak terbaca sebagai "berhasil dihapus".
     */
    fun exists(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            cursor.count > 0 || cursor.moveToFirst()
        } ?: true
    }.getOrDefault(true)
}
