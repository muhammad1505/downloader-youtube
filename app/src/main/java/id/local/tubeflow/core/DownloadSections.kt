package id.local.tubeflow.core

/** Satu sumber kebenaran untuk pembagian antrean dan riwayat di tab Download. */
object DownloadSections {
    fun queue(jobs: List<DownloadJob>): List<DownloadJob> = jobs.filter { it.state != JobState.COMPLETED }

    fun history(jobs: List<DownloadJob>): List<DownloadJob> = jobs.filter { it.state == JobState.COMPLETED }

    fun attention(jobs: List<DownloadJob>): List<DownloadJob> =
        jobs.filter { it.state == JobState.FAILED || it.state == JobState.CANCELLED }

    fun running(jobs: List<DownloadJob>): Int = jobs.count { it.state == JobState.RUNNING || it.state == JobState.QUEUED }
}
