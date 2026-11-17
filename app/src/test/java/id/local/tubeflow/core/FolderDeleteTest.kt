package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderDeleteTest {
    private val default = CollectionSource("content://tree/default", "Download/TubeFlow", removable = false)
    private val extra = CollectionSource("content://tree/extra", "Download/Musik", removable = true)

    @Test
    fun targetResolvesSubfoldersOfAKnownSource() {
        assertEquals("Artist", FolderDelete.target("Download/TubeFlow/Artist", listOf(default, extra))?.suffix)
        assertEquals("Artist/Live", FolderDelete.target("Download/TubeFlow/Artist/Live/", listOf(default, extra))?.suffix)
        assertEquals(extra, FolderDelete.target("Download/Musik/Album", listOf(default, extra))?.source)
    }

    @Test
    fun targetNeverResolvesSourceRootsOrUnrelatedFolders() {
        assertNull(FolderDelete.target("Download/TubeFlow", listOf(default, extra)))
        assertNull(FolderDelete.target("Download/Musik", listOf(default, extra)))
        assertNull(FolderDelete.target("File pilihan", listOf(default, extra)))
        assertNull(FolderDelete.target("Download/Lain/Album", listOf(default, extra)))
        assertNull(FolderDelete.target("", listOf(default, extra)))
    }

    @Test
    fun siblingPrefixIsNotTreatedAsChild() {
        assertNull(FolderDelete.target("Download/TubeFlowBackup/Album", listOf(default, extra)))
    }

    @Test
    fun sourceRootsAreDetectedForGuardMessage() {
        assertTrue(FolderDelete.isSourceRoot("Download/TubeFlow/", listOf(default, extra)))
        assertTrue(FolderDelete.isSourceRoot("Download/Musik", listOf(default, extra)))
        assertFalse(FolderDelete.isSourceRoot("Download/TubeFlow/Artist", listOf(default, extra)))
        assertFalse(FolderDelete.isSourceRoot("", listOf(default, extra)))
    }
}
