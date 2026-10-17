package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class KaraokeOutputTest {
    @Test fun compressedEstimateUsesDurationAndBitrate() {
        assertEquals(720_000L, KaraokeOutput.MP3.estimate(30_000, 5_000_000))
        assertEquals(5_000_000L, KaraokeOutput.FLAC.estimate(30_000, 5_000_000))
        assertEquals("audio/mp4", KaraokeOutput.AAC.mime)
        assertEquals("m4a", KaraokeOutput.AAC.extension)
    }
    @Test fun outputCodecAndPathDoNotAlterInput() {
        val args = KaraokeOutput.MP3.arguments("preview.flac", "derived.mp3")
        assertEquals(listOf("-i", "preview.flac"), args.take(2))
        assertTrue("libmp3lame" in args); assertEquals("derived.mp3", args.last())
        assertTrue("aac" in KaraokeOutput.AAC.arguments("preview.flac", "derived.m4a"))
    }
    @Test fun playlistNamesAreTrimmedAndBounded() {
        assertEquals("Untuk Kerja", PersonalLibrary.name("  Untuk Kerja  "))
        assertThrows(IllegalArgumentException::class.java) { PersonalLibrary.name("  ") }
        assertThrows(IllegalArgumentException::class.java) { PersonalLibrary.name("a\nb") }
        assertThrows(IllegalArgumentException::class.java) { PersonalLibrary.name("a".repeat(81)) }
    }
}
