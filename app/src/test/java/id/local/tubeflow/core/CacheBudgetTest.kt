package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CacheBudgetTest {
    @Test fun byteBudgetEvictsOldEntriesAndRetainsCurrentArtwork() {
        val dir = Files.createTempDirectory("cover-budget").toFile()
        try {
            val old = File(dir, "old.jpg").apply { writeBytes(ByteArray(3)); setLastModified(1000) }
            val newer = File(dir, "newer.jpg").apply { writeBytes(ByteArray(3)); setLastModified(2000) }
            val active = File(dir, "current.jpg").apply { writeBytes(ByteArray(4)); setLastModified(3000) }
            assertEquals(4L, CacheBudget.prune(dir, 6, keep = setOf(active)))
            assertFalse(old.exists()); assertFalse(newer.exists()); assertTrue(active.exists())
        } finally { dir.deleteRecursively() }
    }
    @Test fun entryBudgetLeavesNewestResponsesAndDoesNotRemoveFreshAtomicWriter() {
        val dir = Files.createTempDirectory("response-budget").toFile()
        try {
            repeat(5) { File(dir, "$it.json").apply { writeText("x"); setLastModified(1000L + it) } }
            val writer = File(dir, "writing.tmp").apply { writeText("pending"); setLastModified(System.currentTimeMillis()) }
            assertEquals(2L, CacheBudget.prune(dir, 100, entriesLimit = 2))
            assertEquals(setOf("3.json", "4.json", "writing.tmp"), dir.listFiles()!!.map { it.name }.toSet())
            assertTrue(writer.exists())
        } finally { dir.deleteRecursively() }
    }
    @Test fun staleTemporaryIsReclaimedWhileDirectoriesAndProtectedEntriesRemain() {
        val dir = Files.createTempDirectory("orphan-budget").toFile()
        try {
            val folder = File(dir, "nested").apply { mkdir() }
            val stale = File(dir, "orphan.tmp").apply { writeText("partial"); setLastModified(1000) }
            val kept = File(dir, "current.jpg").apply { writeText("image") }
            CacheBudget.prune(dir, 100, keep = setOf(kept))
            assertFalse(stale.exists()); assertTrue(folder.isDirectory); assertTrue(kept.isFile)
        } finally { dir.deleteRecursively() }
    }
}
