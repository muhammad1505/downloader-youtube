package id.local.tubeflow.core

/** Satu kelompok folder di tab Koleksi; [total] berasal dari database, bukan halaman yang termuat. */
data class CollectionGroup(val folder: String, val items: List<MediaItem>, val total: Long)

/**
 * Aturan pengelompokan murni supaya header lengket dan angka per folder bisa diuji di JVM.
 * Urutan grup mengikuti kemunculan pertama pada halaman agar urutan sort tetap terasa; item di
 * dalam grup tidak diurutkan ulang.
 */
object CollectionGroups {
    const val UNKNOWN_FOLDER = "Tanpa folder"

    fun label(folder: String): String = folder.trim().ifBlank { UNKNOWN_FOLDER }

    fun group(items: List<MediaItem>, totals: Map<String, Long>): List<CollectionGroup> =
        items.groupBy { it.folder }.map { (folder, rows) ->
            // Angka database menang; fallback ke baris termuat hanya selama hitungan belum tiba.
            CollectionGroup(folder, rows, maxOf(totals[folder] ?: 0L, rows.size.toLong()))
        }
}
