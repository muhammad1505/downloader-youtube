package id.local.tubeflow.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPausePolicyTest {

    @Test
    fun pauseIsSentOnlyWhileTheEngineIsRunning() {
        assertTrue(AudioPausePolicy.shouldPause(prepared = true, isPlaying = true))
    }

    @Test
    fun preparedButStoppedPlayerIsNeverPaused() {
        // Regresi: pause() saat PREPARED/PAUSED dilaporkan platform sebagai error (-38, 0).
        assertFalse(AudioPausePolicy.shouldPause(prepared = true, isPlaying = false))
        assertFalse(AudioPausePolicy.shouldPause(prepared = true, isPlaying = null))
    }

    @Test
    fun releasedEngineIsNeverPaused() {
        assertFalse(AudioPausePolicy.shouldPause(prepared = false, isPlaying = true))
        assertFalse(AudioPausePolicy.shouldPause(prepared = false, isPlaying = null))
    }
}
