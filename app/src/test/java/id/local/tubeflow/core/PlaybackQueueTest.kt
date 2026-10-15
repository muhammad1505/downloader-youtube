package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PlaybackQueueTest {
    @Test fun startAndJumpClampToValidPositions() {
        val queue = PlaybackQueue(listOf("a", "b", "c"), 1)
        assertEquals("b", queue.current)
        assertEquals(1, queue.index)
        assertEquals("c", queue.jumpTo(9).current)
        assertEquals("a", queue.jumpTo(-2).current)
    }

    @Test fun nextMovesForwardAndStopsAtEndUnlessRepeatAll() {
        val queue = PlaybackQueue(listOf("a", "b"))
        val second = queue.next()!!
        assertEquals("b", second.current)
        assertNull(second.next())
        assertEquals("a", second.cycleTo(PlaybackQueue.Repeat.ALL).next()!!.current)
    }

    @Test fun previousMovesBackAndWrapsOnlyWithRepeatAll() {
        val queue = PlaybackQueue(listOf("a", "b", "c"), 1)
        assertEquals("a", queue.previous()!!.current)
        assertNull(queue.previous()!!.previous())
        assertEquals("c", queue.jumpTo(0).cycleTo(PlaybackQueue.Repeat.ALL).previous()!!.current)
    }

    @Test fun repeatOneReplaysSameTrackButManualSkipStillMoves() {
        val queue = PlaybackQueue(listOf("a", "b"), 1).cycleTo(PlaybackQueue.Repeat.ONE)
        val completed = queue.onCompleted()!!
        assertEquals("b", completed.current)
        assertEquals(1, completed.index)
        // Skip manual tetap berpindah lagu walau repeat ONE.
        assertEquals("b", queue.jumpTo(0).next()!!.current)
    }

    @Test fun addNextInsertsRightAfterActiveWithoutMovingIt() {
        val queue = PlaybackQueue(listOf("a", "b", "c"), 1).addNext("x")
        assertEquals(listOf("a", "b", "x", "c"), queue.tracks)
        assertEquals("b", queue.current)
        assertEquals(1, queue.index)
    }

    @Test fun addAppendsAndKeepsCurrent() {
        val queue = PlaybackQueue(listOf("a", "b"), 0).add("z")
        assertEquals(listOf("a", "b", "z"), queue.tracks)
        assertEquals("a", queue.current)
    }

    @Test fun removeKeepsIndexCorrectBeforeAfterAndOnActiveItem() {
        val base = PlaybackQueue(listOf("a", "b", "c", "d"), 2) // aktif "c"
        val before = base.removeAt(0)!!
        assertEquals("c", before.current); assertEquals(1, before.index); assertEquals(3, before.size)
        val after = base.removeAt(3)!!
        assertEquals("c", after.current); assertEquals(2, after.index)
        val active = base.removeAt(2)!!
        assertEquals("d", active.current); assertEquals(2, active.index)
        val lastActive = PlaybackQueue(listOf("a", "b"), 1).removeAt(1)!!
        assertEquals("a", lastActive.current); assertEquals(0, lastActive.index)
        assertNull(PlaybackQueue(listOf("only")).removeAt(0))
    }

    @Test fun shuffleKeepsActiveFirstPreservesItemsAndRestoresOriginalOrder() {
        val queue = PlaybackQueue(listOf("a", "b", "c", "d"), 2)
        val shuffled = queue.toggleShuffle(Random(42))
        assertTrue(shuffled.shuffled)
        assertEquals("c", shuffled.current)
        assertEquals(0, shuffled.index)
        assertEquals(queue.tracks.sorted(), shuffled.tracks.sorted())
        val back = shuffled.toggleShuffle()
        assertFalse(back.shuffled)
        assertEquals(listOf("a", "b", "c", "d"), back.tracks)
        assertEquals("c", back.current)
        assertEquals(2, back.index)
    }

    @Test fun queueOperationsNeverDuplicateOrLoseItems() {
        val queue = PlaybackQueue(listOf("a", "a", "b"), 0).addNext("a").add("b")
        assertEquals(listOf("a", "a", "a", "b", "b"), queue.tracks)
        assertEquals(4, queue.removeAt(2)!!.size)
    }

    @Test fun cycleRepeatVisitsEveryMode() {
        val queue = PlaybackQueue(listOf("a"))
        assertEquals(PlaybackQueue.Repeat.ALL, queue.cycleRepeat().repeat)
        assertEquals(PlaybackQueue.Repeat.ONE, queue.cycleRepeat().cycleRepeat().repeat)
        assertEquals(PlaybackQueue.Repeat.OFF, queue.cycleRepeat().cycleRepeat().cycleRepeat().repeat)
        assertFalse(queue.hasNext)
        assertTrue(queue.cycleRepeat().hasNext)
    }

    @Test fun newQueueInheritsUserShuffleAndRepeatPreferences() {
        // Preferensi pengguna berlaku pada antrean berikutnya, bukan hanya antrean saat ini: inilah
        // sebabnya tombol acak/ulangi tidak lagi kembali mati setiap lagu atau antrean baru dimulai.
        val next = PlaybackQueue(listOf("a", "b", "c", "d"), 0)
            .withPreferences(shuffled = true, repeat = PlaybackQueue.Repeat.ALL, random = Random(7))
        assertTrue(next.shuffled)
        assertEquals(PlaybackQueue.Repeat.ALL, next.repeat)
        assertEquals("a", next.current)
        assertEquals(listOf("a", "b", "c", "d"), next.tracks.sorted())
        assertTrue(next.hasNext)
        // Preferensi nonaktif dikembalikan tanpa mengubah urutan asli.
        val plain = next.withPreferences(shuffled = false, repeat = PlaybackQueue.Repeat.OFF)
        assertFalse(plain.shuffled)
        assertEquals(PlaybackQueue.Repeat.OFF, plain.repeat)
        assertEquals(listOf("a", "b", "c", "d"), plain.tracks)
        assertEquals("a", plain.current)
    }

    @Test fun moveReordersQueueWithoutChangingThePlayingTrack() {
        val base = PlaybackQueue(listOf("a", "b", "c", "d"), 1) // aktif "b"
        val down = base.move(1, 3)!!
        assertEquals(listOf("a", "c", "d", "b"), down.tracks)
        assertEquals("b", down.current)
        assertEquals(3, down.index)
        val up = base.move(2, 0)!!
        assertEquals(listOf("c", "a", "b", "d"), up.tracks)
        assertEquals("b", up.current)
        assertEquals(2, up.index)
        assertNull(base.move(1, 1))
        assertNull(base.move(0, 9))
    }

    @Test fun onlyCurrentKeepsThePlayingTrackAndIsNullForASingleItem() {
        val only = PlaybackQueue(listOf("a", "b", "c"), 2).onlyCurrent()!!
        assertEquals(listOf("c"), only.tracks)
        assertEquals("c", only.current)
        assertEquals(0, only.index)
        assertNull(PlaybackQueue(listOf("a")).onlyCurrent())
    }

    @Test fun movingRepeatedTracksPreservesTheActiveEntryAndPlaybackModes() {
        val base = PlaybackQueue(listOf("same", "same", "other"), 1)
            .withPreferences(shuffled = true, repeat = PlaybackQueue.Repeat.ONE, random = Random(3))
        val moved = base.move(base.index, 2)!!
        assertEquals("same", moved.current)
        assertEquals(2, moved.index)
        assertTrue(moved.shuffled)
        assertEquals(PlaybackQueue.Repeat.ONE, moved.repeat)
        assertEquals(2, moved.tracks.count { it == "same" })
        val cleared = moved.onlyCurrent()!!
        assertEquals(listOf("same"), cleared.tracks)
        assertEquals(PlaybackQueue.Repeat.ONE, cleared.repeat)
        assertTrue(cleared.shuffled)
    }

    @Test fun moveNextSupportsItemsBeforeAndAfterTheActiveTrack() {
        val before = PlaybackQueue(listOf("a", "playing", "b", "c"), 1).move(0, 1)!!
        assertEquals("playing", before.current)
        assertEquals("a", before.next()!!.current)
        val after = PlaybackQueue(listOf("a", "playing", "b", "c"), 1).move(3, 2)!!
        assertEquals("playing", after.current)
        assertEquals("c", after.next()!!.current)
    }

    private fun PlaybackQueue<String>.cycleTo(target: PlaybackQueue.Repeat): PlaybackQueue<String> {
        var current = this
        while (current.repeat != target) current = current.cycleRepeat()
        return current
    }
}
