package id.local.tubeflow.core

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/** Reuse a persisted grant only when it names the exact default Downloads folder. */
object StorageFolderAccess {
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
