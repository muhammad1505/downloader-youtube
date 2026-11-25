package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSweepTest {
    private fun item(name: String, folder: String) = MediaItem(
        uri = "content://media/$name",
        kind = "AUDIO",
        title = name,
        displayName = name,
        mime = "audio/mpeg",
        folder = folder,
    )

    @Test
    fun rowWhoseFolderWasScannedButNameDisappearedIsACandidate() {
        val kept = item("kept.mp3", "Download/TubeFlow")
        val missing = item("missing.mp3", "Download/TubeFlow")
        val candidates = MediaSweep.unconfirmed(
            catalog = listOf(kept, missing),
            scannedFolders = setOf("Download/TubeFlow"),
            discovered = setOf("Download/TubeFlow" to "kept.mp3"),
        )
        assertEquals(listOf("missing.mp3"), candidates.map { it.displayName })
    }

    @Test
    fun unscannedFolderNeverProducesCandidates() {
        val row = item("offline.mp3", "Download/Lain")
        val candidates = MediaSweep.unconfirmed(
            catalog = listOf(row),
            scannedFolders = setOf("Download/TubeFlow"),
            discovered = emptySet(),
        )
        assertEquals(emptyList<MediaItem>(), candidates)
    }

    @Test
    fun rowInUnreadableTreeIsStillChecked() {
        val access = "content://com.android.externalstorage.documents/tree/primary%3AMusik/document/primary%3AMusik%2Fhidden.mp3"
        val row = item("hidden.mp3", "Other").copy(accessUri = access, uri = access)
        val prefixes = listOf("content://com.android.externalstorage.documents/tree/primary%3AMusik/document/")
        assertTrue(MediaSweep.needsCheck(row, emptySet(), emptySet(), prefixes))
    }

    @Test
    fun confirmedRowOutsideFailureIsNotChecked() {
        val row = item("kept.mp3", "Download/TubeFlow")
        assertFalse(MediaSweep.needsCheck(
            item = row,
            scannedFolders = setOf("Download/TubeFlow"),
            discovered = setOf("Download/TubeFlow" to "kept.mp3"),
            unreadablePrefixes = listOf("content://other/document/"),
        ))
    }

    @Test
    fun projectedRowMatchesFailedSourceThroughAccessUri() {
        val access = "content://com.android.externalstorage.documents/tree/primary%3AMusik/document/primary%3AMusik%2Fx.mp3"
        assertTrue(MediaSweep.needsCheck(
            uri = "content://media/external/file/7",
            folder = "Musik",
            displayName = "x.mp3",
            access = access,
            scannedFolders = emptySet(),
            discovered = emptySet(),
            unreadablePrefixes = listOf("content://com.android.externalstorage.documents/tree/primary%3AMusik/document/"),
        ))
    }

    @Test
    fun sameNameInAnotherFolderDoesNotCountAsDiscovered() {
        val row = item("same.mp3", "Download/TubeFlow")
        val candidates = MediaSweep.unconfirmed(
            catalog = listOf(row),
            scannedFolders = setOf("Download/TubeFlow"),
            discovered = setOf("Download/Other" to "same.mp3"),
        )
        assertEquals(listOf(row), candidates)
    }
}
