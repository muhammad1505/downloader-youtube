package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class KaraokeIntervalTest {
    @Test fun manualMinutesOrSecondsSelectTheSamePosition() {
        assertEquals(90000L, KaraokeInterval.parseStart("01:30"))
        assertEquals(90000L, KaraokeInterval.parseStart("90"))
        assertEquals(1500L, KaraokeInterval.parseStart("1,5"))
        assertEquals(90768L, KaraokeInterval.parseStart("1:30.768"))
    }
    @Test fun invalidAmbiguousOrOverflowingPositionsAreRejected() {
        for (value in listOf("", "-1", "1:60", "1:2:3", "0.0001", "NaN", "1..5", "600.001", "99999999999999")) assertNull(value, KaraokeInterval.parseStart(value))
    }
    @Test fun capturedPlaybackPositionRoundTripsAtMillisecondPrecision() {
        for (position in listOf(0L, 768L, 1500L, 90000L, 599999L, 600000L)) assertEquals(position, KaraokeInterval.parseStart(KaraokeInterval.formatStart(position)))
    }
    @Test fun endOfSongIsShorterWithoutMovingRequestedStartBackwards() {
        assertEquals(KaraokeInterval.Segment(393000, 1008), KaraokeInterval.select(394008, 393000, true))
        assertEquals(KaraokeInterval.Segment(90000, 30000), KaraokeInterval.select(394008, 90000, true))
    }
    @Test fun explicitEndKeepsRequestedRangeAndClipsToSongEnd() {
        assertEquals(KaraokeInterval.Segment(90000, 45000), KaraokeInterval.select(394008, 90000, 135000, true))
        assertEquals(KaraokeInterval.Segment(390000, 4008), KaraokeInterval.select(394008, 390000, 450000, true))
        assertEquals(KaraokeInterval.Segment(0, 30000), KaraokeInterval.select(394008, 0, 30000, true))
    }
    @Test fun emptyEndFallsBackToThirtySecondsAndReversedRangeFails() {
        assertEquals(KaraokeInterval.Segment(90000, 30000), KaraokeInterval.select(394008, 90000, null, true))
        assertThrows(IllegalArgumentException::class.java) { KaraokeInterval.select(394008, 90000, 90000, true) }
        assertThrows(IllegalArgumentException::class.java) { KaraokeInterval.select(394008, 90000, 30000, true) }
    }
    @Test fun outOfRangePositionFailsAndFullSongStartsAtZero() {
        assertThrows(IllegalArgumentException::class.java) { KaraokeInterval.select(394008, 394008, true) }
        assertThrows(IllegalArgumentException::class.java) { KaraokeInterval.select(394008, -1, true) }
        assertEquals(KaraokeInterval.Segment(0, 394008), KaraokeInterval.select(394008, 599999, false))
    }
}
