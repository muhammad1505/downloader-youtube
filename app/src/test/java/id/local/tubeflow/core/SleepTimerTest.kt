package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class SleepTimerTest {
    @Test fun deadlineUsesElapsedTimeAndDoesNotDependOnPlaybackTicks() {
        val timer = SleepTimer(); timer.minutes(15, 9000)
        assertEquals(909000L, timer.deadlineMs)
        assertEquals(1000L, timer.remaining(908000))
        assertFalse(timer.due(908999)); assertTrue(timer.due(909000))
        assertEquals(0L, timer.remaining(Long.MAX_VALUE))
    }
    @Test fun fadeAndCancellationRestoreFullGain() {
        val timer = SleepTimer(); timer.minutes(1, 0)
        assertEquals(1f, timer.gain(49000), 0f)
        assertEquals(0.5f, timer.gain(55000), 0.001f)
        assertEquals(0f, timer.gain(60000), 0f)
        timer.clear(); assertEquals(1f, timer.gain(60000), 0f); assertFalse(timer.due(60000))
    }
    @Test fun endOfTrackReplacesDeadlineAndNewDeadlineReplacesEndOfTrack() {
        val timer = SleepTimer(); timer.minutes(1, 0); timer.endOfTrack()
        assertTrue(timer.afterTrack); assertEquals(0L, timer.deadlineMs)
        timer.minutes(30, 100); assertFalse(timer.afterTrack)
    }
    @Test fun trackBoundaryConsumesOnlyEndOfTrackTimer() {
        val timer = SleepTimer(); timer.minutes(1, 0)
        assertFalse(timer.finishTrack()); assertEquals(60_000L, timer.deadlineMs)
        timer.endOfTrack(); assertTrue(timer.finishTrack()); assertFalse(timer.finishTrack())
    }
    @Test(expected = IllegalArgumentException::class) fun refusesUnboundedDuration() { SleepTimer().minutes(181, 0) }
    @Test fun comparisonKeepsAbsolutePositionAcrossBothDirections() {
        assertEquals(5_500L, KaraokeComparison.target(65_500, false, true, 60_000, 30_000))
        assertEquals(65_500L, KaraokeComparison.target(5_500, true, false, 60_000, 30_000))
        assertEquals(5_500L, KaraokeComparison.target(5_500, true, true, 60_000, 30_000))
    }
    @Test fun comparisonClampsBeforeRangeAndRestartsAfterRange() {
        assertEquals(0L, KaraokeComparison.target(0, false, true, 60_000, 30_000))
        assertEquals(0L, KaraokeComparison.target(95_000, false, true, 60_000, 30_000))
    }
}
