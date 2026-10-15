package id.local.tubeflow.core

/**
 * Perencana antrean auto-play, murni tanpa Android agar keputusannya dapat diuji di host.
 *
 * Aturan: SELURUH koleksi masuk antrean (bukan satu lagu) dan mulai dari lagu terakhir yang
 * diputar, sehingga lagu berikutnya tetap tersedia setelah lagu pertama selesai. Bila lagu
 * terakhir sudah tidak ada di koleksi (file dihapus), mulai dari awal koleksi.
 */
object AutoPlayQueue {
    /**
     * Rencana antrean auto-play: daftar lengkap [collection] berpasangan dengan posisi mulai,
     * atau null bila koleksi kosong. [startUri] adalah lagu terakhir/lagu aktif yang ingin
     * dilanjutkan; [idOf] mengekstrak identitas pembanding (biasanya URI) dari tiap item.
     */
    fun <T> plan(collection: List<T>, startUri: String?, idOf: (T) -> String): Pair<List<T>, Int>? {
        if (collection.isEmpty()) return null
        val wanted = startUri?.let { uri -> collection.indexOfFirst { idOf(it) == uri } } ?: -1
        return collection to if (wanted >= 0) wanted else 0
    }
}
