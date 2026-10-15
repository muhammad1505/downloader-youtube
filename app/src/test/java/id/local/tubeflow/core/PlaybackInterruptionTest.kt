package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class PlaybackInterruptionTest {
    @Test fun repeatedTransientLossDoesNotEraseResumeIntent() {
        val policy = PlaybackInterruption()
        policy.lost(false, true)
        policy.lost(false, false)
        assertTrue(policy.takeFocusGain())
        assertFalse(policy.takeFocusGain())
    }

    @Test fun permanentLossWaitsForObservedAudioThenQuietAndResumesOnlyOnce() {
        val policy = PlaybackInterruption()
        policy.lost(true, true)
        assertFalse(policy.playbackChanged(false))
        assertFalse(policy.takeAfterQuiet(false, true))
        assertFalse(policy.playbackChanged(true))
        assertTrue(policy.playbackChanged(false))
        assertTrue(policy.takeAfterQuiet(false, true))
        assertFalse(policy.takeAfterQuiet(false, true))
    }

    @Test fun blockerStartingAgainDuringDebouncePreventsResume() {
        val policy = PlaybackInterruption()
        policy.lost(true, true)
        policy.playbackChanged(true)
        assertTrue(policy.playbackChanged(false))
        assertFalse(policy.takeAfterQuiet(true, true))
        policy.playbackChanged(true)
        assertTrue(policy.playbackChanged(false))
        assertTrue(policy.takeAfterQuiet(false, true))
    }

    @Test fun transientInterruptionWaitsForFocusRatherThanTakingItDuringSilence() {
        val policy = PlaybackInterruption()
        policy.lost(false, true)
        policy.playbackChanged(true)
        assertFalse(policy.playbackChanged(false))
        assertFalse(policy.takeAfterQuiet(false, true))
        assertTrue(policy.takeFocusGain())
    }

    @Test fun permanentLossAfterTransientPreservesIntent() {
        val policy = PlaybackInterruption()
        policy.lost(false, true)
        policy.lost(true, false)
        policy.playbackChanged(true)
        assertTrue(policy.playbackChanged(false))
        assertTrue(policy.takeAfterQuiet(false, true))
    }

    @Test fun manualPauseStopTrackChangeAndHeadsetRemovalCancelBothKindsOfResume() {
        for (permanent in listOf(false, true)) {
            val policy = PlaybackInterruption()
            policy.lost(permanent, true)
            policy.playbackChanged(true)
            policy.cancel()
            assertFalse(policy.playbackChanged(false))
            assertFalse(policy.takeAfterQuiet(false, true))
            assertFalse(policy.takeFocusGain())
        }
    }

    @Test fun alreadyManuallyPausedTrackNeverResumesOnOtherAppsEvents() {
        val policy = PlaybackInterruption()
        policy.lost(true, false)
        policy.playbackChanged(true)
        assertFalse(policy.playbackChanged(false))
        assertFalse(policy.takeAfterQuiet(false, true))
        assertFalse(policy.takeFocusGain())
    }

    @Test fun callOrCommunicationModeDoesNotPermitQuietResume() {
        val policy = PlaybackInterruption()
        policy.lost(true, true)
        policy.playbackChanged(true)
        policy.playbackChanged(false)
        assertFalse(policy.takeAfterQuiet(false, false))
        assertTrue(policy.takeAfterQuiet(false, true))
    }
}
