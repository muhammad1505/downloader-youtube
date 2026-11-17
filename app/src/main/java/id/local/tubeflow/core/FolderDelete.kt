package id.local.tubeflow.core

/**
 * Aturan murni untuk hapus folder Koleksi. Folder hanya dipetakan ke direktori fisik bila jalurnya
 * berasal dari sumber koleksi yang dikenal (label sumber + subfolder). Folder hasil impor di luar
 * sumber itu tetap dihapus berkasnya, tetapi direktorinya tidak disentuh dari TubeFlow.
 */
object FolderDelete {
    /** Direktori tujuan: sumber SAF pemilik folder dan jalur relatifnya di dalam sumber. */
    data class Target(val source: CollectionSource, val suffix: String)

    /** True bila [folder] adalah akar sumber koleksi; akar tidak boleh dihapus. */
    fun isSourceRoot(folder: String, sources: List<CollectionSource>): Boolean {
        val key = FolderRename.normalize(folder)
        if (key.isBlank()) return false
        return sources.any { FolderRename.normalize(it.label) == key }
    }

    /**
     * Direktori fisik untuk [folder]; null bila folder adalah akar sumber atau bukan bagian dari
     * sumber mana pun. Subfolder cocok memakai batas `/` supaya `Download/A` tidak menelan
     * `Download/AB`.
     */
    fun target(folder: String, sources: List<CollectionSource>): Target? {
        val key = FolderRename.normalize(folder)
        if (key.isBlank()) return null
        for (source in sources) {
            val label = FolderRename.normalize(source.label)
            if (label.isBlank() || key == label) continue
            if (!key.startsWith("$label/")) continue
            val suffix = key.removePrefix(label).trim('/')
            if (suffix.isNotBlank()) return Target(source, suffix)
        }
        return null
    }
}
