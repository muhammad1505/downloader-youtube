package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StatusClipTest {

    @Test
    fun startIsClampedSoThirtySecondsNeverPassTheEnd() {
        assertEquals(0L, StatusClip.startMs(60_000, -5_000))
        assertEquals(10_000L, StatusClip.startMs(60_000, 10_000))
        assertEquals(30_000L, StatusClip.startMs(60_000, 90_000))
        assertEquals(0L, StatusClip.startMs(12_000, 5_000))
    }

    @Test
    fun shortVideosKeepTheirWholeRemainingDuration() {
        assertEquals(30_000L, StatusClip.expectedDurationMs(60_000, 10_000))
        assertEquals(12_000L, StatusClip.expectedDurationMs(12_000, 0))
        assertEquals(2_000L, StatusClip.expectedDurationMs(12_000, 10_000))
        assertEquals(0L, StatusClip.expectedDurationMs(12_000, 12_000))
    }

    @Test
    fun copyPlanSeeksBeforeInputAndStreamsBothTracks() {
        val args = StatusClip.copyArguments(File("/tmp/source.mp4"), File("/tmp/status.mp4"), 5_000, 60_000)

        assertTrue(args.indexOf("-ss") < args.indexOf("-i"))
        assertEquals("5.000", args[args.indexOf("-ss") + 1])
        assertEquals("30.000", args[args.indexOf("-t") + 1])
        assertEquals("copy", args[args.indexOf("-c") + 1])
        assertTrue(args.contains("+faststart"))
        assertEquals("/tmp/status.mp4", args.last())
    }

    @Test
    fun encodePlanUsesWhatsAppCompatibleCodecs() {
        val args = StatusClip.encodeArguments(File("/tmp/source.webm"), File("/tmp/status.mp4"), 0, 20_000)

        assertEquals("libx264", args[args.indexOf("-c:v") + 1])
        assertEquals("aac", args[args.indexOf("-c:a") + 1])
        assertEquals("20.000", args[args.indexOf("-t") + 1])
        assertFalse(args.contains("copy"))
    }

    @Test
    fun secondsUseADotRegardlessOfDeviceLocale() {
        assertEquals("1.500", StatusClip.seconds(1_500))
        assertEquals("0.000", StatusClip.seconds(-1))
    }
}
