package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsManualTextTest {
    @Test
    fun timedLrcGetsManualSourceHeader() {
        val text = Lyrics.manualText("[00:01.00]Baris pertama\n[00:02.50]Baris kedua", 4_000)

        assertTrue(text.startsWith("[re:TubeFlow / manual]\n"))
        assertTrue(text.contains("[00:01.00]Baris pertama"))
    }

    @Test
    fun metadataAndBomArePreserved() {
        val text = Lyrics.manualText("\uFEFF[ar:Ipank]\n[00:01.00]Mutiara", 4_000)

        assertTrue(text.contains("[ar:Ipank]"))
        assertTrue(text.contains("Mutiara"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun lyricsWithoutTimestampsAreRejected() {
        Lyrics.manualText("Baris lirik tanpa waktu", 4_000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyLyricsAreRejected() {
        Lyrics.manualText("   ", 4_000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun timestampPastTheAudioEndIsRejected() {
        Lyrics.manualText("[00:10.00]Terlalu jauh", 4_000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedTimestampIsRejected() {
        Lyrics.manualText("[00:99.00]Detik tidak valid", 4_000)
    }

    @Test
    fun textBelowTheLimitIsAccepted() {
        val text = Lyrics.manualText(
            (0 until 100).joinToString("\n") { index -> "[00:01.%03d]".format(index) + "a".repeat(200) },
            10_000,
        )

        assertTrue(text.toByteArray().size < 512 * 1024)
        assertEquals(100, LrcLines.parse(text).count { it.text.isNotBlank() })
    }
}
