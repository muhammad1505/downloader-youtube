package id.local.tubeflow.core

/**
 * Pesan status di bawah ubin Alat. Tab Alat tidak lagi meminjam kartu kosong milik Koleksi
 * (judul "Koleksi masih kosong" beserta tombol "Segarkan katalog"); statusnya cukup satu baris
 * redup yang menjelaskan mengapa ubin belum bisa dipakai.
 */
enum class ToolsNotice { NONE, EMPTY_CATALOG, NO_AUDIO }

object ToolAvailability {
    /**
     * @param audioCount jumlah audio tersedia; `null` selama database belum mengirim data pertama.
     * @param catalogCount jumlah seluruh baris katalog; `null` selama database belum mengirim data.
     * @param scanning true saat katalog sedang dipindai; pesan kosong ditahan agar tidak menuduh
     *   koleksi kosong padahal pemindaian masih berjalan.
     */
    fun notice(audioCount: Int?, catalogCount: Long?, scanning: Boolean): ToolsNotice = when {
        scanning -> ToolsNotice.NONE
        audioCount == null || catalogCount == null -> ToolsNotice.NONE
        audioCount > 0 -> ToolsNotice.NONE
        catalogCount == 0L -> ToolsNotice.EMPTY_CATALOG
        else -> ToolsNotice.NO_AUDIO
    }
}
