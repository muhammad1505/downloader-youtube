package id.local.tubeflow.core

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS

/** Adapter for an already-authorized filesystem root; never follow links or delete the root. */
internal object LegacyFolderCleanup {
    fun clear(root: File, deleteFile: (File) -> Boolean = { it.delete() }): FolderCleanup.Result {
        val base = root.absoluteFile
        require(base == base.canonicalFile) { "Folder tujuan berubah; pembersihan dibatalkan." }
        fun checked(raw: String): File {
            val file = File(raw).absoluteFile
            require(file == base || file.toPath().startsWith(base.toPath())) { "File berada di luar folder tujuan." }
            require(!Files.isSymbolicLink(file.toPath()) && file == file.canonicalFile &&
                base == base.canonicalFile) { "Tautan folder tidak diikuti saat pembersihan." }
            return file
        }
        return FolderCleanup.clear(base.path, list = { raw ->
            val folder = checked(raw)
            if (folder == base && Files.notExists(folder.toPath(), NOFOLLOW_LINKS)) emptyList()
            else {
                val children = folder.listFiles() ?: error("Folder belum dapat dibaca.")
                require(children.size <= 4000) { "Folder terlalu besar untuk dibersihkan sekaligus." }
                children.map { child ->
                    val file = checked(child.path)
                    FolderCleanup.Entry(file.path, file.isDirectory)
                }
            }
        }, delete = { raw ->
            val file = checked(raw)
            require(file != base) { "Folder utama tidak boleh dihapus." }
            if (file.isDirectory) file.delete() else deleteFile(file)
        })
    }
}
