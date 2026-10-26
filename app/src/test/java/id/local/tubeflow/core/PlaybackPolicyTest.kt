package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class PlaybackPolicyTest {
    @Test fun explicitChoiceOverridesSystemAndUnknownChoiceFollowsSystem() {
        assertTrue(PlaybackPolicy.saving("on", false))
        assertFalse(PlaybackPolicy.saving("off", true))
        assertTrue(PlaybackPolicy.saving("system", true))
        assertFalse(PlaybackPolicy.saving("system", false))
        assertTrue(PlaybackPolicy.saving("future", true))
    }

    @Test fun savingReducesPollingWithoutStoppingPositionUpdates() {
        assertEquals(500L, PlaybackPolicy.audioTickMs(true))
        assertEquals(200L, PlaybackPolicy.audioTickMs(true, hasLyrics = true))
        assertEquals(1_000L, PlaybackPolicy.videoTickMs(true))
        assertTrue(PlaybackPolicy.audioTickMs(true) > PlaybackPolicy.audioTickMs(false))
        assertTrue(PlaybackPolicy.videoTickMs(true) > PlaybackPolicy.videoTickMs(false))
    }

    @Test fun ambientCurveIsBoundedMonotonicAndRejectsInvalidSensorValues() {
        val values = listOf(0f, 10f, 100f, 1_000f, 10_000f).map { PlaybackPolicy.ambientBrightness(it)!! }
        assertTrue(values.zipWithNext().all { (a,b) -> a < b })
        assertEquals(0.08f, values.first(), 0.001f)
        assertEquals(1f, PlaybackPolicy.ambientBrightness(Float.MAX_VALUE)!!, 0.001f)
        assertNull(PlaybackPolicy.ambientBrightness(-1f))
        assertNull(PlaybackPolicy.ambientBrightness(Float.NaN))
        assertNull(PlaybackPolicy.ambientBrightness(Float.POSITIVE_INFINITY))
    }
}
