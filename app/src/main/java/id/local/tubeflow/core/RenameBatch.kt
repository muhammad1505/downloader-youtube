package id.local.tubeflow.core

/** Providers cannot atomically rename two documents. Roll back every attempted member on failure. */
object RenameBatch {
    data class Entry(val uri: String, val before: String, val after: String)
    fun apply(entries: List<Entry>, read: (String) -> String, rename: (String, String) -> Unit) {
        require(entries.map { it.uri }.distinct().size == entries.size)
        entries.forEach { require(read(it.uri) == it.before) { "Nama file berubah; buka ulang koleksi." } }
        val attempted = mutableListOf<Entry>()
        try {
            entries.forEach { entry ->
                attempted += entry
                rename(entry.uri, entry.after)
                require(read(entry.uri) == entry.after) { "Provider mengubah nama yang diminta." }
            }
        } catch (failure: Exception) {
            var restored = true
            attempted.asReversed().forEach { entry ->
                try { if (read(entry.uri) != entry.before) rename(entry.uri, entry.before); require(read(entry.uri) == entry.before) }
                catch (_: Exception) { restored = false }
            }
            if (!restored) throw IllegalStateException("Rename gagal; pemulihan belum selesai. Jurnal dipertahankan.", failure)
            throw IllegalStateException("Rename gagal; nama sebelumnya dipulihkan.", failure)
        }
    }
}
