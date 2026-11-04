package id.local.tubeflow.core

/**
 * Aturan murni untuk mengganti nama folder fisik. Label folder katalog berupa jalur relatif
 * (`Download/TubeFlow/Artis`) atau label sumber SAF; helper ini tidak menyentuh provider.
 */
object FolderRename {
    fun normalize(folder: String): String = folder.trim().trimEnd('/')

    fun leaf(folder: String): String = normalize(folder).substringAfterLast('/')

    fun parent(folder: String): String = normalize(folder).substringBeforeLast('/', "")

    /** Jalur folder setelah segmen terakhir diganti [name]; induk tidak pernah ikut berubah. */
    fun target(folder: String, name: String): String {
        val parent = parent(folder)
        val clean = name.trim()
        return if (parent.isBlank()) clean else "$parent/$clean"
    }

    /** Memetakan folder itu sendiri dan seluruh subfoldernya; nilai lain dikembalikan apa adanya. */
    fun remap(value: String, from: String, to: String): String {
        val old = normalize(from)
        val replacement = normalize(to)
        val key = normalize(value)
        return when {
            old.isBlank() || key.isBlank() -> value
            key == old -> replacement
            key.startsWith("$old/") -> replacement + key.removePrefix(old)
            else -> value
        }
    }

    /** Pola LIKE aman untuk folder dan seluruh isinya (`%`/`_` milik nama folder di-escape). */
    fun likePrefix(folder: String): String =
        normalize(folder).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "/%"

    /**
     * Document id turunan setelah folder induk diganti nama. Null bila dokumen berada di luar
     * folder itu, supaya pemanggil tidak memetakan URI milik folder lain.
     */
    fun documentIdAfter(documentId: String, oldFolderId: String, newFolderId: String): String? {
        if (oldFolderId.isBlank() || newFolderId.isBlank()) return null
        return when {
            documentId == oldFolderId -> newFolderId
            documentId.startsWith("$oldFolderId/") -> newFolderId + documentId.removePrefix(oldFolderId)
            else -> null
        }
    }
}
