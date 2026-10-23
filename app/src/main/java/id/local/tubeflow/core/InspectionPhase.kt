package id.local.tubeflow.core

object InspectionPhase {
    const val PREPARING_ENGINE = "menyiapkan_engine"
    const val FETCHING_METADATA = "mengambil_metadata"
    const val FETCHING_PLAYLIST = "mengambil_playlist"
    const val READING_ENTRIES = "membaca_entri"

    fun label(phase: String): String = when (phase) {
        PREPARING_ENGINE -> "Menyiapkan mesin download…"
        FETCHING_METADATA -> "Mengambil detail video…"
        FETCHING_PLAYLIST -> "Mengambil daftar playlist…"
        READING_ENTRIES -> "Membaca pilihan playlist…"
        else -> "Membaca detail link…"
    }
}
