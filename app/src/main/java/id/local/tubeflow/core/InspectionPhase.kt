package id.local.tubeflow.core

object InspectionPhase {
    const val PREPARING_ENGINE = "menyiapkan_engine"
    const val FETCHING_METADATA = "mengambil_metadata"
    const val FETCHING_PLAYLIST = "mengambil_playlist"
    const val FETCHING_COLLECTION = "mengambil_koleksi"
    const val FETCHING_SEASON = "mengambil_season"
    const val READING_ENTRIES = "membaca_entri"
    const val SEARCHING = "mencari_sumber"

    fun label(phase: String): String = when (phase) {
        PREPARING_ENGINE -> "Menyiapkan mesin download…"
        FETCHING_METADATA -> "Mengambil detail video…"
        FETCHING_PLAYLIST -> "Mengambil daftar playlist…"
        FETCHING_COLLECTION -> "Mengambil daftar lagu…"
        FETCHING_SEASON -> "Mengambil daftar episode…"
        READING_ENTRIES -> "Membaca pilihan episode…"
        SEARCHING -> "Mencari audio yang cocok di YouTube…"
        else -> "Membaca detail link…"
    }
}
