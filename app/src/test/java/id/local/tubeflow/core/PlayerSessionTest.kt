package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PlayerSessionTest {
    private fun track(n: Int) = PlayerViewModel.Track("content://media/external/audio/media/$n", "Lagu $n")
    @Test fun roundTripKeepsShuffleOrderCurrentPositionAndOriginalOrder() {
        val original = PlaybackQueue((1..6).map(::track), 2).withPreferences(true, PlaybackQueue.Repeat.ALL, Random(4))
        val saved = PlayerSession.Saved(original.snapshot(), 123456L)
        val decoded = PlayerSession.decode(PlayerSession.encode(saved))
        val restored = requireNotNull(PlaybackQueue.restore(decoded.queue))
        assertEquals(original.tracks, restored.tracks)
        assertEquals(original.current, restored.current)
        assertEquals(123456L, decoded.positionMs)
        assertEquals(PlaybackQueue.Repeat.ALL, restored.repeat)
        assertEquals((1..6).map(::track), restored.toggleShuffle().tracks)
    }
    @Test fun missingCurrentSelectsFollowingOccurrenceAndRetainsDuplicateUris() {
        val a = track(1); val b = track(2)
        val queue = PlaybackQueue(listOf(a, b, a), 1)
        val restored = requireNotNull(PlaybackQueue.restore(queue.snapshot()) { it != b })
        assertEquals(listOf(a,a), restored.tracks)
        assertEquals(1,restored.index)
        assertNull(PlaybackQueue.restore(queue.snapshot()) { false })
    }
    @Test fun seekCheckpointCannotAttachToAnotherQueueRevision() {
        val position = PlayerSession.positionBytes("new",98765)
        assertEquals(98765L,PlayerSession.restorePosition(position,"new",12))
        assertEquals(12L,PlayerSession.restorePosition(position,"old",12))
        assertEquals(12L,PlayerSession.restorePosition("broken".toByteArray(),"new",12))
        assertEquals(12L,PlayerSession.restorePosition(ByteArray(513),"new",12))
    }
    @Test fun malformedAndRemoteSnapshotsAreRejected() {
        for (data in listOf("{}", "{\"version\":99}", "not-json")) {
            assertThrows(Exception::class.java) { PlayerSession.decode(data.toByteArray()) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            PlayerSession.encode(PlayerSession.Saved(PlaybackQueue(listOf(PlayerViewModel.Track("https://example.com/token", "remote"))).snapshot(),0))
        }
        assertThrows(IllegalArgumentException::class.java) { PlayerSession.decode(ByteArray(PlayerSession.MAX_BYTES+1)) }
    }
    @Test fun repeatedIdsAndInvalidIndexCannotRestoreAnotherTrack() {
        val queue = PlaybackQueue(listOf(track(1),track(2))).snapshot()
        assertThrows(IllegalArgumentException::class.java) { PlaybackQueue.restore(queue.copy(index=2)) }
        assertThrows(IllegalArgumentException::class.java) { PlaybackQueue.restore(queue.copy(entries=listOf(0L to track(1),0L to track(2)))) }
    }
}
