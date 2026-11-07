package id.local.tubeflow.core

/** Satu sumber kebenaran untuk pembagian antrean dan riwayat di tab Download. */
object DownloadSections {
    fun queue(jobs: List<DownloadJob>): List<DownloadJob> = jobs.filter { it.state != JobState.COMPLETED }

    fun history(jobs: List<DownloadJob>): List<DownloadJob> = jobs.filter { it.state == JobState.COMPLETED }

    fun attention(jobs: List<DownloadJob>): List<DownloadJob> =
        jobs.filter { it.state == JobState.FAILED || it.state == JobState.CANCELLED }

    fun running(jobs: List<DownloadJob>): Int = jobs.count { it.state == JobState.RUNNING || it.state == JobState.QUEUED }
}

/** Keadaan pekerjaan WorkManager yang relevan bagi penjaga antrean, tanpa tipe Android. */
enum class QueueWork { NONE, WAITING, RUNNING }

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

    /**
     * Pekerjaan yang masih tercatat menunggu tetapi tidak kunjung mulai saat syaratnya sudah
     * terpenuhi. WorkManager dapat kehilangan job dari scheduler OEM, atau memegang syarat lama
     * setelah pengguna mematikan "Hanya lewat WiFi"/"Hanya saat mengisi daya". WorkInfo yang hidup
     * saja bukan bukti pekerjaan akan berjalan, jadi setelah masa tenggang jadwalnya diganti
     * (`REPLACE`) dengan syarat terbaru. Saat syarat belum terpenuhi, menunggu adalah perilaku
     * yang benar dan tidak boleh memicu penggantian berulang.
     */
    fun replaceWaiting(
        state: JobState,
        work: QueueWork,
        engineRunning: Boolean,
        constraintsMet: Boolean,
        otherWorkRunning: Boolean,
        waitingMillis: Long,
        graceMillis: Long = 45_000L,
    ): Boolean = state == JobState.QUEUED && !engineRunning && work == QueueWork.WAITING &&
        constraintsMet && !otherWorkRunning && waitingMillis >= graceMillis
}

/**
 * Kalimat status untuk baris QUEUED. Tanpa syarat yang sedang tertahan, null berarti pemanggil
 * memakai teks umum "Menunggu giliran". Syarat dicek terhadap kondisi perangkat saat ini, bukan
 * sekadar toggle di Pengaturan, supaya kartu tidak menyuruh menunggu WiFi ketika WiFi sudah aktif.
 */
object QueueWait {
    fun hint(wifiOnly: Boolean, chargingOnly: Boolean, networkReady: Boolean, charging: Boolean): String? {
        val power = chargingOnly && !charging
        return when {
            !networkReady && power ->
                if (wifiOnly) "Menunggu WiFi dan pengisian daya · mulai otomatis"
                else "Menunggu koneksi internet dan pengisian daya · mulai otomatis"
            !networkReady ->
                if (wifiOnly) "Menunggu koneksi WiFi tidak berbayar · mulai otomatis"
                else "Menunggu koneksi internet · mulai otomatis"
            power -> "Menunggu pengisian daya · mulai otomatis"
            else -> null
        }
    }
}
