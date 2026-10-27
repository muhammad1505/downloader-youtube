package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StatusClipTest {

    @Test
    fun rangeUsesTheSelectedStartAndEnd() {
        assertEquals(
            StatusClip.ClipRange(35_000, 50_000),
            StatusClip.range(60_000, 35_000, 50_000),
        )
    }

    @Test
    fun rangeClampsBothEndsToTheVideo() {
        assertEquals(
            StatusClip.ClipRange(0, 60_000),
            StatusClip.range(60_000, -5_000, 90_000),
        )
        assertEquals(
            StatusClip.ClipRange(0, 10_000),
            StatusClip.range(60_000, -5_000, 10_000),
        )
    }

    @Test
    fun rangeAllowsClipsLongerThanThirtySeconds() {
        assertEquals(
            StatusClip.ClipRange(10_000, 80_000),
            StatusClip.range(120_000, 10_000, 80_000),
        )
    }

    @Test
    fun wholeVideoCanBeSelectedForChat() {
        assertEquals(
            StatusClip.ClipRange(0, 180_000),
            StatusClip.range(180_000, 0, 180_000),
        )
    }

    @Test
    fun timeoutsGrowWithLongerClipsAndStayBounded() {
        assertEquals(150L, StatusClip.copyTimeoutSeconds(30_000))
        assertEquals(240L, StatusClip.copyTimeoutSeconds(120_000))
        assertEquals(900L, StatusClip.copyTimeoutSeconds(3_600_000))
        assertEquals(360L, StatusClip.encodeTimeoutSeconds(30_000))
        assertEquals(720L, StatusClip.encodeTimeoutSeconds(120_000))
        assertEquals(3_600L, StatusClip.encodeTimeoutSeconds(3_600_000))
    }

    @Test
    fun rangeKeepsAtLeastOneSecond() {
        assertEquals(
            StatusClip.ClipRange(20_000, 21_000),
            StatusClip.range(60_000, 20_000, 20_000),
        )
        assertEquals(
            StatusClip.ClipRange(20_000, 21_000),
            StatusClip.range(60_000, 20_000, 10_000),
        )
    }

    @Test
    fun rangeKeepsTheStartInsideTheLastSecond() {
        assertEquals(
            StatusClip.ClipRange(59_000, 60_000),
            StatusClip.range(60_000, 59_500, 60_000),
        )
    }

    @Test
    fun shortVideosKeepTheirWholeDuration() {
        assertEquals(
            StatusClip.ClipRange(0, 12_000),
            StatusClip.range(12_000, 0, 12_000),
        )
        assertEquals(
            StatusClip.ClipRange(10_000, 12_000),
            StatusClip.range(12_000, 10_000, 12_000),
        )
        assertEquals(
            StatusClip.ClipRange(0, 500),
            StatusClip.range(500, 0, 500),
        )
    }

    @Test
    fun copyPlanSeeksBeforeInputAndUsesTheSelectedSpan() {
        val range = StatusClip.ClipRange(5_000, 20_000)
        val args = StatusClip.copyArguments(File("/tmp/source.mp4"), File("/tmp/status.mp4"), range)

        assertTrue(args.indexOf("-ss") < args.indexOf("-i"))
        assertEquals("5.000", args[args.indexOf("-ss") + 1])
        assertEquals("15.000", args[args.indexOf("-t") + 1])
        assertEquals("copy", args[args.indexOf("-c") + 1])
        assertTrue(args.contains("+faststart"))
        assertEquals("/tmp/status.mp4", args.last())
    }

    @Test
    fun encodePlanUsesWhatsAppCompatibleCodecsAndTheSelectedSpan() {
        val range = StatusClip.ClipRange(60_000, 80_000)
        val args = StatusClip.encodeArguments(File("/tmp/source.webm"), File("/tmp/status.mp4"), range)

        assertEquals("libx264", args[args.indexOf("-c:v") + 1])
        assertEquals("aac", args[args.indexOf("-c:a") + 1])
        assertEquals("60.000", args[args.indexOf("-ss") + 1])
        assertEquals("20.000", args[args.indexOf("-t") + 1])
        assertFalse(args.contains("copy"))
    }

    @Test
    fun secondsUseADotRegardlessOfDeviceLocale() {
        assertEquals("1.500", StatusClip.seconds(1_500))
        assertEquals("0.000", StatusClip.seconds(-1))
    }
}
