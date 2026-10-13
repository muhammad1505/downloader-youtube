package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class LrcTimingTest {
    @Test fun advanceOneSecondFromOriginalAndResetIsExact() {
        val original = "[ar:Artist]\r\n[offset:250]\r\n[00:05.67]first\r\n[01:01.234]next\r\n"
        val shifted = LrcTiming.shift(original, -1000, 90000)
        assertTrue(shifted.contains("[00:04.67]first")); assertTrue(shifted.contains("[01:00.234]next"))
        assertTrue(shifted.contains("[offset:250]"))
        assertEquals(original, LrcTiming.shift(original, 0, 90000))
        assertEquals(shifted, LrcTiming.shift(original, -1000, 90000))
    }
    @Test fun laterShiftHandlesMinuteBoundaryMultipleTimestampsAndNegativeClamping() {
        assertTrue(LrcTiming.shift("[00:59.75][01:02.1]line\n", 500, 70000).startsWith("[01:00.25][01:02.60]"))
        assertTrue(LrcTiming.shift("[00:00.25]line", -1000, 70000).startsWith("[00:00.00]"))
        assertTrue(LrcTiming.shift("[00:01]line", 1000, 70000).startsWith("[00:02.00]"))
    }
    @Test fun invalidOrOverEndLyricsAreRejectedBeforeWriting() {
        for (text in listOf("plain lyrics", "[00:60.00]bad", "[offset:no]\n[00:01]line", "[ar:Artist]", "[00:01]", "[99999999999:00]bad")) {
            try { LrcTiming.shift(text, -500, 30000); fail(text) } catch (_: IllegalArgumentException) { }
        }
        try { LrcTiming.shift("[00:31.00]line", 2000, 30000); fail() } catch (_: IllegalArgumentException) { }
        try { LrcTiming.shift("[00:01]line", 31000, null); fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun failedWriteRestoresPreviousBytesAndVerifiesRollback() {
        var current = "old".toByteArray(); var attempts = 0
        try {
            VerifiedTextWrite.replace(current.copyOf(), "new".toByteArray(), { current }, { bytes ->
                current = bytes.copyOf(); attempts++; if (attempts == 1) throw java.io.IOException("close failed")
            }); fail()
        } catch (_: IllegalStateException) { }
        assertEquals("old", current.toString(Charsets.UTF_8)); assertEquals(2, attempts)
    }
    @Test fun readBackMismatchAlsoRestoresAndSuccessfulWriteChangesOnlyText() {
        var current = "old".toByteArray(); var attempts = 0
        try {
            VerifiedTextWrite.replace(current.copyOf(), "new".toByteArray(), { current }, { bytes -> attempts++; current = if (attempts == 1) byteArrayOf() else bytes }); fail()
        } catch (_: IllegalStateException) { }
        assertEquals("old", current.toString(Charsets.UTF_8))
        VerifiedTextWrite.replace(current, "new".toByteArray(), { current }, { current = it })
        assertEquals("new", current.toString(Charsets.UTF_8))
    }
}
