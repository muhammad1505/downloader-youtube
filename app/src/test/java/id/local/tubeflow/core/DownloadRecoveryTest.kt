package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRecoveryTest {
    private fun job(
        pendingUri: String? = null,
        sidecarPending: String? = null,
        sidecarUris: String? = null,
        outputUri: String? = null,
    ) = DownloadJob(
        id = "job-1",
        url = "https://example.com/v",
        title = "Judul",
        mode = MediaMode.AUDIO,
        format = "mp3",
        quality = 0,
        bitrate = 128,
        pendingUri = pendingUri,
        sidecarPending = sidecarPending,
        sidecarUris = sidecarUris,
        outputUri = outputUri,
    )

    @Test
    fun pendingJournalSurvivesRefusedDelete() {
        val deleted = mutableListOf<String>()
        val cleared = DownloadRecovery.clearPendingAfterDelete(job(pendingUri = "content://media/1")) { raw ->
            deleted += raw
            false
        }

        assertFalse(cleared)
        assertEquals(listOf("content://media/1"), deleted)
    }

    @Test
    fun pendingJournalSurvivesDeleteException() {
        val cleared = DownloadRecovery.clearPendingAfterDelete(job(pendingUri = "content://media/1")) {
            throw SecurityException("provider menolak")
        }

        assertFalse(cleared)
    }

    @Test
    fun pendingJournalClearsAfterProvenDelete() {
        assertTrue(DownloadRecovery.clearPendingAfterDelete(job(pendingUri = "content://media/1")) { true })
        assertFalse(DownloadRecovery.clearPendingAfterDelete(job()) { error("tidak boleh dipanggil") })
    }

    @Test
    fun sidecarJournalKeepsOnlyFailedEntries() {
        val failed = DownloadRecovery.retainedSidecarPending(
            job(sidecarPending = "content://media/a\ncontent://media/b\ncontent://media/c"),
        ) { raw -> raw != "content://media/b" }

        assertEquals("content://media/b", failed)
    }

    @Test
    fun sidecarJournalSurvivesDeleteException() {
        val failed = DownloadRecovery.retainedSidecarPending(
            job(sidecarPending = "content://media/a"),
        ) { throw IllegalStateException("provider error") }

        assertEquals("content://media/a", failed)
    }

    @Test
    fun sidecarJournalClearsWhenEveryEntryIsGone() {
        assertNull(
            DownloadRecovery.retainedSidecarPending(job(sidecarPending = "content://media/a")) { true },
        )
        assertNull(DownloadRecovery.retainedSidecarPending(job()) { error("tidak boleh dipanggil") })
    }

    @Test
    fun orphanSidecarKeepsFailedKindAndUriLines() {
        val failed = DownloadRecovery.retainedOrphanSidecars(
            job(sidecarUris = "sub|content://media/a\ncover|content://media/b"),
        ) { raw -> raw != "content://media/b" }

        assertEquals("cover|content://media/b", failed)
    }

    @Test
    fun orphanSidecarSurvivesDeleteException() {
        val failed = DownloadRecovery.retainedOrphanSidecars(
            job(sidecarUris = "sub|content://media/a"),
        ) { throw SecurityException("provider menolak") }

        assertEquals("sub|content://media/a", failed)
    }

    @Test
    fun orphanSidecarCleanupLeavesPublishedMediaAlone() {
        val uris = "sub|content://media/a"

        assertEquals(
            uris,
            DownloadRecovery.retainedOrphanSidecars(job(sidecarUris = uris, outputUri = "content://media/out")) {
                error("sidecar media terbit tidak boleh dihapus")
            },
        )
    }
}
