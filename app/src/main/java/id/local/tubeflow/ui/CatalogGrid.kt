package id.local.tubeflow.ui

/**
 * Jumlah kartu folder per baris di tab Koleksi. Ponsel memakai tiga kolom supaya sisa ruang di
 * kanan tidak kosong; layar lebar (tablet atau ponsel lanskap) memakai empat kolom.
 * Murni supaya bisa diuji di JVM tanpa Compose.
 */
object CatalogGrid {
    const val NARROW_COLUMNS = 3
    const val WIDE_COLUMNS = 4
    const val WIDE_WIDTH_DP = 600f

    fun folderColumns(screenWidthDp: Float): Int =
        if (screenWidthDp >= WIDE_WIDTH_DP) WIDE_COLUMNS else NARROW_COLUMNS
}
