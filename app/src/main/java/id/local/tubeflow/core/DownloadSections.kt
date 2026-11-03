package id.local.tubeflow.core

/** Satu sumber kebenaran untuk pembagian antrean dan riwayat di tab Download. */
object DownloadSections {
    fun queue(jobs: List<DownloadJob>): List<DownloadJob> = jobs.filter { it.state != JobState.COMPLETED }

    fun history(jobs: List<DownloadJob>): List<DownloadJob> = jobs.filter { it.state == JobState.COMPLETED }

    fun attention(jobs: List<DownloadJob>): List<DownloadJob> =
        jobs.filter { it.state == JobState.FAILED || it.state == JobState.CANCELLED }

    fun running(jobs: List<DownloadJob>): Int = jobs.count { it.state == JobState.RUNNING || it.state == JobState.QUEUED }
}

/**
 * Baris antrean yang tertinggal: masih QUEUED padahal tidak ada pekerjaan WorkManager maupun proses
 * mesin yang hidup. Keadaan ini membuat kartu "Menunggu giliran" bertahan selamanya — misalnya
 * playlist besar yang enqueue-nya terputus atau pekerjaannya dihentikan sistem — sehingga jadwalnya
 * harus dipasang ulang. Status lain (dijeda, dibatalkan, gagal, selesai) memang tidak punya
 * pekerjaan aktif, jadi tidak pernah dijadwalkan ulang dari sini.
 */
object QueueWatchdog {
    fun stalled(state: JobState, hasLiveWork: Boolean, engineRunning: Boolean): Boolean =
        state == JobState.QUEUED && !hasLiveWork && !engineRunning
}
