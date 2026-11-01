package id.local.tubeflow.core

/**
 * Kegagalan "video tidak tersedia" pada entri playlist/album bukan kegagalan unduhan pengguna:
 * baris itu dilewati agar antrean tidak dipenuhi entri mati. Video tunggal tetap ditampilkan
 * beserta detailnya supaya pengguna bisa memutuskan sendiri.
 */
internal object CollectionSkip {
    fun shouldSkip(job: DownloadJob, failureCategory: String): Boolean =
        failureCategory == "unavailable" &&
            (job.subfolder.isNotBlank() || job.autoFolder.isNotBlank())
}
