package id.local.tubeflow.core

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LegacyFolderCleanupTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun clearsFilesSidecarsAndEmptyDirectoriesButPreservesRootAndSibling() {
        val root = temp.newFolder("TubeFlow")
        val nested = File(root, "mix-denny-caknan/nested").apply { mkdirs() }
        File(nested, "Lagu - Artis.mp3").writeText("audio")
        File(nested, "Lagu - Artis.lrc").writeText("lyrics")
        File(root, "empty").mkdir()
        val sibling = temp.newFolder("TubeFlowBackup")
        File(sibling, "keep.mp3").writeText("unchanged")
        val result = LegacyFolderCleanup.clear(root)
        assertEquals(FolderCleanup.Result(2, 3, 0, true), result)
        assertTrue(root.isDirectory)
        assertTrue(root.listFiles()!!.isEmpty())
        assertEquals("unchanged", File(sibling, "keep.mp3").readText())
    }

    @Test fun failedFileRemovalKeepsContainingFolderAndReportsIncomplete() {
        val root = temp.newFolder("TubeFlow")
        val nested = File(root, "mix").apply { mkdir() }
        val song = File(nested, "song.mp3").apply { writeText("keep") }
        val result = LegacyFolderCleanup.clear(root) { false }
        assertFalse(result.clean)
        assertEquals(1, result.failures)
        assertEquals(0, result.folders)
        assertTrue(song.exists())
    }

    @Test fun refusesLinksToSiblingFolderAndKeepsOutsideFiles() {
        val root = temp.newFolder("TubeFlow")
        val outside = temp.newFolder("private")
        val song = File(outside, "keep.mp3").apply { writeText("keep") }
        Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())
        val result = LegacyFolderCleanup.clear(root)
        assertFalse(result.clean)
        assertTrue(result.failures > 0)
        assertEquals("keep", song.readText())
    }

    @Test fun refusesLinkedRootBeforeAnyDeletion() {
        val outside = temp.newFolder("outside")
        val song = File(outside, "keep").apply { writeText("keep") }
        val root = File(temp.root, "TubeFlow")
        Files.createSymbolicLink(root.toPath(), outside.toPath())
        assertThrows(IllegalArgumentException::class.java) { LegacyFolderCleanup.clear(root) }
        assertTrue(song.exists())
    }

    @Test fun concurrentFileAddedDuringDeletionPreventsRemovingParent() {
        val root = temp.newFolder("TubeFlow")
        val nested = File(root, "mix").apply { mkdir() }
        File(nested, "old.mp3").writeText("old")
        val result = LegacyFolderCleanup.clear(root) {
            it.delete().also { File(nested, "new.mp3").writeText("new") }
        }
        assertFalse(result.clean)
        assertTrue(nested.isDirectory)
        assertTrue(File(nested, "new.mp3").exists())
    }

    @Test fun absentRootIsEmptyAndNoSiblingIsDeleted() {
        val sibling = temp.newFolder("TubeFlowBackup")
        val result = LegacyFolderCleanup.clear(File(temp.root, "TubeFlow"))
        assertEquals(FolderCleanup.Result(0, 0, 0, true), result)
        assertTrue(sibling.exists())
    }
}
