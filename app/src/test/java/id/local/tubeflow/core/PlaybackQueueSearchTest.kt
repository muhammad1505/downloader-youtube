package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackQueueSearchTest {
    private val tracks = listOf("Lamunan Sasya Arkhisna", "SATRU 2 Denny Caknan", "Nemen Happy Asmara", "SATRU 2 Live Happy Asmara")

    @Test fun blankSearchKeepsTheEntireQueueInOrder() {
        assertEquals(listOf(0, 1, 2, 3), PlaybackQueueSearch.positions(tracks, "  ") { it })
    }

    @Test fun matchingReturnsOriginalPositionsForFilteredActions() {
        assertEquals(listOf(1, 3), PlaybackQueueSearch.positions(tracks, "satru") { it })
        val queue = PlaybackQueue(tracks)
        val result = PlaybackQueueSearch.positions(tracks, "happy live") { it }.single()
        assertEquals(tracks[3], queue.jumpTo(result).current)
        assertEquals(listOf(tracks[0], tracks[1], tracks[2]), queue.removeAt(result)!!.tracks)
    }

    @Test fun tokensMatchTitleAndArtistRegardlessOfCaseOrExtraWhitespace() {
        assertEquals(listOf(1), PlaybackQueueSearch.positions(tracks, "  DENNY   satru ") { it })
        assertEquals(listOf(2, 3), PlaybackQueueSearch.positions(tracks, "happy asmara") { it })
    }

    @Test fun duplicateTracksRemainSeparateQueueEntries() {
        assertEquals(listOf(0, 2), PlaybackQueueSearch.positions(listOf("Same", "Other", "Same"), "same") { it })
    }

    @Test fun noMatchAndEmptyQueueHaveNoVisibleEntries() {
        assertEquals(emptyList<Int>(), PlaybackQueueSearch.positions(tracks, "no such song") { it })
        assertEquals(emptyList<Int>(), PlaybackQueueSearch.positions(emptyList<String>(), "satru") { it })
    }
}
