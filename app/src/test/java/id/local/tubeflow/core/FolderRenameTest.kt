package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FolderRenameTest {

    @Test
    fun targetChangesOnlyTheLastSegment() {
        assertEquals("Download/TubeFlow/Band", FolderRename.target("Download/TubeFlow/Artist", " Band "))
        assertEquals("Download/TubeFlow/Artist/Band", FolderRename.target("Download/TubeFlow/Artist/Album", "Band"))
        assertEquals("Baru", FolderRename.target("Lama", "Baru"))
        assertEquals("Download/TubeFlow", FolderRename.parent("Download/TubeFlow/Artist"))
        assertEquals("Artist", FolderRename.leaf("Download/TubeFlow/Artist/"))
    }

    @Test
    fun remapCoversFolderAndDescendantsOnly() {
        assertEquals(
            "Download/TubeFlow/Band",
            FolderRename.remap("Download/TubeFlow/Artist", "Download/TubeFlow/Artist", "Download/TubeFlow/Band"),
        )
        assertEquals(
            "Download/TubeFlow/Band/Live",
            FolderRename.remap("Download/TubeFlow/Artist/Live", "Download/TubeFlow/Artist", "Download/TubeFlow/Band"),
        )
        assertEquals(
            "Download/TubeFlow/Artist2",
            FolderRename.remap("Download/TubeFlow/Artist2", "Download/TubeFlow/Artist", "Download/TubeFlow/Band"),
        )
        assertEquals(
            "Download/Lain",
            FolderRename.remap("Download/Lain", "Download/TubeFlow/Artist", "Download/TubeFlow/Band"),
        )
    }

    @Test
    fun likePrefixEscapesWildcardsFromFolderNames() {
        assertEquals("Download/100\\%\\_Amat/%", FolderRename.likePrefix("Download/100%_Amat"))
        assertEquals("Download/A\\\\B/%", FolderRename.likePrefix("Download/A\\B"))
    }

    @Test
    fun documentIdsFollowTheRenamedFolder() {
        assertEquals(
            "primary:Download/Band/song.mp3",
            FolderRename.documentIdAfter(
                "primary:Download/Artist/song.mp3",
                "primary:Download/Artist",
                "primary:Download/Band",
            ),
        )
        assertNull(
            FolderRename.documentIdAfter(
                "primary:Download/Lain/song.mp3",
                "primary:Download/Artist",
                "primary:Download/Band",
            ),
        )
    }

    @Test
    fun storageNameRejectsUnsafeFoldersAndKeepsReadableNames() {
        assertNull(FolderAliases.validateStorageName("  Band Lama  "))
        assertNull(FolderAliases.validateStorageName("Album (2026) - Vol.2"))
        assertNotNull(FolderAliases.validateStorageName(" "))
        assertNotNull(FolderAliases.validateStorageName("x".repeat(FolderAliases.MAX_STORAGE_NAME + 1)))
        assertNotNull(FolderAliases.validateStorageName("A/B"))
        assertNotNull(FolderAliases.validateStorageName(".tersembunyi"))
        assertNotNull(FolderAliases.validateStorageName("Nama."))
    }
}
