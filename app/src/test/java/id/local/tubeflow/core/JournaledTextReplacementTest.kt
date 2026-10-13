package id.local.tubeflow.core

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Test

class JournaledTextReplacementTest {
    private class Fixture {
        var bytes = "old".toByteArray()
        var pending: JournaledTextReplacement.Pending? = null
        var writes = 0
        var commits = 0
        var failRead = false
        var failSave = false
        var failWrites = 0
        fun operation() = JournaledTextReplacement(
            read = { if (failRead) throw java.io.IOException("read failed") else bytes.copyOf() },
            write = { value ->
                writes++
                if (failWrites > 0) { failWrites--; bytes = value.copyOf(1); throw java.io.IOException("partial write/close") }
                bytes = value.copyOf()
            },
            load = { pending }, save = { if (failSave) error("backup full") else pending = it },
            clear = { pending = null }, committed = { commits++ },
        )
    }
    private fun fails(block: () -> Unit) {
        try { block(); fail("Expected failure") } catch (_: Exception) { }
    }
    @Test fun unreadableBackupOrJournalFailureNeverOpensWriter() {
        for (readFailure in listOf(true, false)) {
            val f = Fixture(); f.failRead = readFailure; f.failSave = !readFailure
            fails { f.operation().replace("new".toByteArray(), "old".toByteArray()) }
            assertEquals(0, f.writes); assertArrayEquals("old".toByteArray(), f.bytes)
        }
    }
    @Test fun partialWriteRestoresAndRollbackFailureKeepsBackupForNextProcess() {
        val f = Fixture(); f.failWrites = 1
        fails { f.operation().replace("new".toByteArray(), "old".toByteArray()) }
        assertArrayEquals("old".toByteArray(), f.bytes); assertNull(f.pending); assertEquals(0, f.commits)
        f.failWrites = 2
        fails { f.operation().replace("new".toByteArray(), "old".toByteArray()) }
        assertNotNull(f.pending)
        f.operation().recover()
        assertArrayEquals("old".toByteArray(), f.bytes); assertNull(f.pending)
    }
    @Test fun interruptedTargetCommitsButExternalChangesAreNeverOverwritten() {
        val f = Fixture(); f.pending = JournaledTextReplacement.Pending("old".toByteArray(), "new".toByteArray())
        f.bytes = "external".toByteArray(); fails { f.operation().recover() }
        assertEquals(0, f.writes); assertNotNull(f.pending)
        f.bytes = "new".toByteArray(); f.operation().recover()
        assertEquals(1, f.commits); assertNull(f.pending)
    }
    @Test fun changedSnapshotCancelsAndIdenticalResultPreservesTimingBaseline() {
        val f = Fixture(); fails { f.operation().replace("new".toByteArray(), "stale".toByteArray()) }
        assertEquals(0, f.writes)
        f.operation().replace("old".toByteArray(), "old".toByteArray())
        assertEquals(0, f.commits); assertEquals(0, f.writes)
        f.operation().replace("new".toByteArray(), "old".toByteArray())
        assertArrayEquals("new".toByteArray(), f.bytes); assertEquals(1, f.commits); assertNull(f.pending)
    }
    @Test fun mediaOperationsSerializeAndWaitingCancellationDoesNotWrite() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        val edit = launch { MediaFileOperations.mutex.withLock { calls += "edit"; entered.complete(Unit); release.await() } }
        entered.await()
        val refetch = launch { MediaFileOperations.mutex.withLock { calls += "refetch" } }
        yield(); assertEquals(listOf("edit"), calls)
        refetch.cancelAndJoin(); release.complete(Unit); edit.join()
        assertEquals(listOf("edit"), calls)
        MediaFileOperations.mutex.withLock { calls += "rename" }
        assertEquals(listOf("edit", "rename"), calls)
    }
}
