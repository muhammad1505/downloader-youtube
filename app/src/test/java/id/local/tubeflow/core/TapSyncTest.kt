package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class TapSyncTest {
    @Test fun timestampsUseCentisecondsAndCarryIntoMinutes() {
        assertEquals("00:00.00", TapSync.stamp(0))
        assertEquals("01:05.43", TapSync.stamp(65_432))
        assertEquals("00:59.99", TapSync.stamp(59_999))
    }

    @Test fun linesDropTimestampsAndMetadataSoExistingLyricsCanBeReTimed() {
        val raw = "[ar:Artis]\n[offset:+200]\n[00:12.10]Baris satu\n[00:15.00]Baris dua\n\n"
        assertEquals(listOf("Baris satu", "Baris dua"), TapSync.lines(raw))
    }

    @Test fun buildRequiresOneTapPerLineAndProducesPlainLrc() {
        assertEquals("[00:01.00]A\n[00:02.50]B\n", TapSync.build(listOf("A", "B"), listOf(1_000, 2_500)))
        try {
            TapSync.build(listOf("A", "B"), listOf(1_000)); fail()
        } catch (_: IllegalArgumentException) {
        }
        try {
            TapSync.build(emptyList(), emptyList()); fail()
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test fun outOfOrderTimestampsAreRejected() {
        try {
            TapSync.build(listOf("A", "B"), listOf(2_000, 1_500)); fail()
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test fun tapsCloserThanTheMinimumGapAreRejectedBeforeTheyAreRecorded() {
        assertNull(TapSync.rejection(emptyList(), 0))
        assertNotNull(TapSync.rejection(emptyList(), -1))
        assertNotNull(TapSync.rejection(listOf(1_000), 1_100))
        assertNull(TapSync.rejection(listOf(1_000), 1_250))
    }
}
