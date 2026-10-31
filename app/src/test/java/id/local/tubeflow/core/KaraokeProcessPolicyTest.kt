package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class KaraokeProcessPolicyTest {
    @Test fun untrackedRunningWorkBlocksNewStartInsteadOfBeingDroppedSilently() {
        assertTrue(KaraokeProcessPolicy.startBlocked(trackedUnfinished = false, uniqueUnfinished = true))
        assertTrue(KaraokeProcessPolicy.startBlocked(trackedUnfinished = true, uniqueUnfinished = true))
        assertTrue(KaraokeProcessPolicy.startBlocked(trackedUnfinished = true, uniqueUnfinished = false))
        assertFalse(KaraokeProcessPolicy.startBlocked(trackedUnfinished = false, uniqueUnfinished = false))
    }
    @Test fun switchingSongModeOrClipPositionDoesNotOfferToSaveThePreviousResultAsTheNewSelection() {
        fun matches(source: String? = "a", selected: String? = "a", sample: Boolean = true,
            requestedSample: Boolean = true, start: Long = 90000, requestedStart: Long? = 90000,
            end: Long = 120000, requestedEnd: Long? = 120000) =
            KaraokeProcessPolicy.matchesSelection(source, selected, sample, requestedSample, start, requestedStart, end, requestedEnd)
        assertTrue(matches())
        assertFalse(matches(selected = "b")); assertFalse(matches(requestedSample = false))
        assertFalse(matches(requestedStart = 120000)); assertFalse(matches(requestedStart = null))
        assertFalse(matches(requestedEnd = 150000)); assertFalse(matches(requestedEnd = null))
        assertFalse(matches(source = null)); assertFalse(matches(source = "", selected = ""))
    }
    @Test fun fullSongDoesNotDependOnTheUnusedClipInput() {
        assertTrue(KaraokeProcessPolicy.matchesSelection("a", "a", false, false, 0, null, 0, null))
        assertFalse(KaraokeProcessPolicy.matchesSelection("a", "a", false, true, 0, 0, 0, 0))
    }
}
