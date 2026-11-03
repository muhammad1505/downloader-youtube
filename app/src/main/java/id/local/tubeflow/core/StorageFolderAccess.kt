package id.local.tubeflow.core

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/** Reuse a persisted grant only when it names the exact default Downloads folder. */
object StorageFolderAccess {
    /** Android 10 may already grant full access; readable catalogue rows alone do not prove this. */
    @Suppress("DEPRECATION")
    fun hasLegacyWriteAccess(context: Context): Boolean = legacyWritable(
        android.os.Build.VERSION.SDK_INT,
        android.os.Environment.isExternalStorageLegacy(),
        androidx.core.content.ContextCompat.checkSelfPermission(context,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED,
    )

    internal fun legacyWritable(api: Int, legacy: Boolean, writeGranted: Boolean): Boolean =
        api == 29 && legacy && writeGranted

    fun canClearDefault(context: Context, folder: String): Boolean =
        hasLegacyWriteAccess(context) || defaultTree(context, folder, requireWrite = true)?.let { canWriteTree(context, it) } == true

    /**
     * Grant persisten bisa terlihat ada tetapi sudah tidak berlaku (folder dihapus/dibuat ulang,
     * izin dicabut sistem). Uji dengan membuat lalu menghapus berkas kecil agar pemanggil meminta
     * akses baru sebelum pembersihan, bukan gagal di tengah jalan.
     */
    fun canWriteTree(context: Context, uri: Uri): Boolean {
        return runCatching {
            val resolver = context.contentResolver
            val root = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
            val probe = DocumentsContract.createDocument(
                resolver, root, "application/octet-stream", ".tubeflow-write-probe",
            ) ?: return@runCatching false
            DocumentsContract.deleteDocument(resolver, probe)
            true
        }.getOrDefault(false)
    }

    /**
     * Grant SAF persisten yang tepat menunjuk folder bawaan Download/[folder], bila pernah dipilih
     * pengguna. Dipakai bersama untuk ukuran folder, pembersihan, dan pemindaian koleksi — grant
     * tidak mengubah tujuan output unduhan.
     */
    fun defaultTree(context: Context, folder: String, requireWrite: Boolean = false): Uri? {
        val resolver = context.contentResolver
        return resolver.persistedUriPermissions.firstOrNull { permission ->
            permission.isReadPermission && (!requireWrite || permission.isWritePermission) && runCatching {
                matchesDefault(permission.uri.authority, DocumentsContract.getTreeDocumentId(permission.uri), folder)
            }.getOrDefault(false)
        }?.uri
    }

    fun matchesDefault(authority: String?, documentId: String, folder: String): Boolean {
        if (Selection.safeFolder(folder) != folder) return false
        val id = documentId.trimEnd('/')
        return when (authority) {
            "com.android.externalstorage.documents" -> id == "primary:Download/$folder"
            "com.android.providers.downloads.documents" ->
                id.matches(Regex("raw:/storage/emulated/[0-9]+/Download/${Regex.escape(folder)}"))
            else -> false
        }
    }
}
