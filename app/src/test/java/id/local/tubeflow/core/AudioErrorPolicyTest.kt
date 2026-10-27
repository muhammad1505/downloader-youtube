package id.local.tubeflow.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioErrorPolicyTest {

    @Test
    fun transientMediaPlayerErrorsAreRetriedOnce() {
        assertTrue(AudioErrorPolicy.shouldRetry(what = 1, alreadyRetried = false)) // UNKNOWN
        assertTrue(AudioErrorPolicy.shouldRetry(what = 100, alreadyRetried = false)) // SERVER_DIED
        assertTrue(AudioErrorPolicy.shouldRetry(what = -1004, alreadyRetried = false)) // IO
        assertTrue(AudioErrorPolicy.shouldRetry(what = -110, alreadyRetried = false)) // TIMED_OUT
    }

    @Test
    fun codecAndMalformedErrorsAreNotRetried() {
        assertFalse(AudioErrorPolicy.shouldRetry(what = -1007, alreadyRetried = false)) // MALFORMED
        assertFalse(AudioErrorPolicy.shouldRetry(what = -1010, alreadyRetried = false)) // UNSUPPORTED
        assertFalse(AudioErrorPolicy.shouldRetry(what = 200, alreadyRetried = false)) // PROGRESSIVE
    }

    @Test
    fun aTrackIsNeverRetriedTwice() {
        assertFalse(AudioErrorPolicy.shouldRetry(what = -1004, alreadyRetried = true))
        assertFalse(AudioErrorPolicy.shouldRetry(what = 1, alreadyRetried = true))
    }

    @Test
    fun diagnosticLabelCarriesOnlyPlatformCodes() {
        val label = AudioErrorPolicy.label(what = -1004, extra = -100)
        assertTrue(label.contains("what=-1004"))
        assertTrue(label.contains("extra=-100"))
    }
}
