package id.local.tubeflow.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionSkipTest {
    private fun job(subfolder: String = "", autoFolder: String = "") = DownloadJob(
        id = "job-1",
        url = "https://youtu.be/example",
        title = "Judul",
        mode = MediaMode.VIDEO,
        format = "mp4",
        quality = 0,
        bitrate = 0,
        subfolder = subfolder,
        autoFolder = autoFolder,
    )

    @Test
    fun unavailableVideoInCollectionIsSkipped() {
        assertTrue(CollectionSkip.shouldSkip(job(subfolder = "playlist-favorit"), "unavailable"))
        assertTrue(CollectionSkip.shouldSkip(job(autoFolder = "collection"), "unavailable"))
    }

    @Test
    fun standaloneVideoStillShowsTheFailure() {
        assertFalse(CollectionSkip.shouldSkip(job(), "unavailable"))
    }

    @Test
    fun otherFailureCategoriesAreNeverSkipped() {
        listOf("geo", "premium", "network", "dns", "rate_limit").forEach { category ->
            assertFalse(category, CollectionSkip.shouldSkip(job(subfolder = "playlist-favorit"), category))
        }
    }
}
