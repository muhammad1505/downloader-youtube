package id.local.tubeflow.core

import org.junit.Assert.assertEquals
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
}
