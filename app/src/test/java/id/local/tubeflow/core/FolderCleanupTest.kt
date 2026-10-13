package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class FolderCleanupTest {
    private val root = "TubeFlow"
    @Test fun deletesNestedContentsBottomUpAndPreservesRootAndSibling() {
        val children = mutableMapOf(root to mutableListOf(FolderCleanup.Entry("mix", true)),
            "mix" to mutableListOf(FolderCleanup.Entry("nested", true), FolderCleanup.Entry("song", false)),
            "nested" to mutableListOf(FolderCleanup.Entry("lyrics", false)),
            "TubeFlowBackup" to mutableListOf(FolderCleanup.Entry("private", false)))
        val order = mutableListOf<String>()
        val result = FolderCleanup.clear(root, { children.getValue(it).toList() }, { id ->
            order += id; children.values.forEach { it.removeAll { entry -> entry.id == id } }; children.remove(id); true
        })
        assertTrue(result.clean)
        assertEquals(2, result.files)
        assertEquals(2, result.folders)
        assertEquals(listOf("lyrics", "nested", "song", "mix"), order)
        assertTrue(children.getValue(root).isEmpty())
        assertEquals("private", children.getValue("TubeFlowBackup").single().id)
    }
    @Test fun unreadableDirectoryIsPreservedAndReported() {
        val deleted = mutableListOf<String>()
        val result = FolderCleanup.clear(root, { if (it == root) listOf(FolderCleanup.Entry("denied", true)) else error("Permission denied") }, { deleted.add(it) })
        assertFalse(result.clean)
        assertEquals(1, result.failures)
        assertTrue(deleted.isEmpty())
    }
    @Test fun failedFileDeletionDoesNotDeleteItsParent() {
        val deleted = mutableListOf<String>()
        val result = FolderCleanup.clear(root, { listOf(FolderCleanup.Entry(if (it == root) "mix" else "song", it == root)) }, { deleted += it; false })
        assertFalse(result.clean)
        assertEquals(listOf("song"), deleted)
        assertEquals(1, result.failures)
    }
    @Test fun concurrentAdditionPreventsDeletingNonemptyDirectory() {
        var reads = 0
        val deleted = mutableListOf<String>()
        val result = FolderCleanup.clear(root, {
            if (it == root) listOf(FolderCleanup.Entry("mix", true))
            else if (++reads == 1) emptyList() else listOf(FolderCleanup.Entry("new", false))
        }, { deleted.add(it) })
        assertFalse(result.clean)
        assertTrue(deleted.isEmpty())
    }
    @Test fun cyclesAndTraversalLimitsNeverDeleteRootOrUninspectedParents() {
        for (limit in listOf(0, 8)) {
            val deleted = mutableListOf<String>()
            val result = FolderCleanup.clear(root, { listOf(FolderCleanup.Entry(root, true)) }, { deleted.add(it) }, maxDepth=limit)
            assertFalse(result.clean)
            assertTrue(deleted.isEmpty())
        }
        val result = FolderCleanup.clear(root, { listOf(FolderCleanup.Entry("child-$it", true)) }, { fail("Uninspected directory deleted"); true }, maxEntries=2)
        assertFalse(result.clean)
        assertTrue(result.failures > 0)
    }
    @Test fun emptyRootNeedsNoDeletion() {
        assertEquals(FolderCleanup.Result(0, 0, 0, true), FolderCleanup.clear(root, { emptyList() }, { fail("Root deleted"); false }))
    }
}
