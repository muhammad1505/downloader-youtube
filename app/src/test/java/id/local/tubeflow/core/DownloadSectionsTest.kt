package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSectionsTest {
    private fun job(id: String, state: JobState) = DownloadJob(
        id = id,
        url = "https://youtu.be/$id",
        title = "Judul $id",
        mode = MediaMode.AUDIO,
        format = "mp3",
        quality = 0,
        bitrate = 128,
        state = state,
    )

    @Test
    fun queueOwnsEverythingExceptCompletedWhileHistoryOwnsOnlyCompleted() {
        val jobs = listOf(
            job("run", JobState.RUNNING),
            job("wait", JobState.QUEUED),
            job("pause", JobState.PAUSED),
            job("fail", JobState.FAILED),
            job("cancel", JobState.CANCELLED),
            job("done", JobState.COMPLETED),
        )

        assertEquals(listOf("run", "wait", "pause", "fail", "cancel"), DownloadSections.queue(jobs).map { it.id })
        assertEquals(listOf("done"), DownloadSections.history(jobs).map { it.id })
        assertEquals(listOf("fail", "cancel"), DownloadSections.attention(jobs).map { it.id })
        assertTrue(DownloadSections.queue(jobs).none { it.state == JobState.COMPLETED })
    }

    @Test
    fun runningBadgeIgnoresPausedFailedAndCompletedJobs() {
        val jobs = listOf(
            job("run", JobState.RUNNING),
            job("wait", JobState.QUEUED),
            job("pause", JobState.PAUSED),
            job("fail", JobState.FAILED),
            job("done", JobState.COMPLETED),
        )

        assertEquals(2, DownloadSections.running(jobs))
    }

    @Test
    fun watchdogOnlyReschedulesQueuedJobsWithoutLiveWork() {
        // Tertinggal: masih menunggu padahal WorkManager dan mesin sama-sama tidak memegangnya.
        assertTrue(QueueWatchdog.stalled(JobState.QUEUED, hasLiveWork = false, engineRunning = false))
        // Menunggu slot worker (pekerjaan WorkManager hidup) atau mesin sudah berjalan: jangan diganggu.
        assertFalse(QueueWatchdog.stalled(JobState.QUEUED, hasLiveWork = true, engineRunning = false))
        assertFalse(QueueWatchdog.stalled(JobState.QUEUED, hasLiveWork = false, engineRunning = true))
        // Status lain memang tidak punya pekerjaan aktif — jangan dijadwalkan ulang.
        listOf(JobState.RUNNING, JobState.PAUSED, JobState.FAILED, JobState.CANCELLED, JobState.COMPLETED)
            .forEach { assertFalse(it.name, QueueWatchdog.stalled(it, hasLiveWork = false, engineRunning = false)) }
    }
}
