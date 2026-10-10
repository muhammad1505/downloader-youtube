package id.local.tubeflow.core

/** Reuse a persisted grant only when it names the exact default Downloads folder. */
object StorageFolderAccess {
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
