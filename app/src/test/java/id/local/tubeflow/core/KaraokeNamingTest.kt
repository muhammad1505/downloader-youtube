package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class KaraokeNamingTest {
    @Test fun derivedVersionPrecedesVerifiedArtist() {
        assertEquals("SATRU 2 (Live, Karaoke) - DENNY CAKNAN FT HAPPY ASMARA.flac", KaraokeNaming.fileName("SATRU 2 (Live)", "DENNY CAKNAN FT HAPPY ASMARA", false))
    }
    @Test fun previewIsExplicitAndUnknownArtistIsNotInvented() {
        assertEquals("Rekaman Suara (Karaoke cuplikan).flac", KaraokeNaming.fileName("Rekaman Suara", "", true))
    }
    @Test fun repeatedArtistAndRemixIdentityArePreserved() {
        val name = KaraokeNaming.fileName("Hysteria - Muse (Slowed + Reverb)", "Muse", false)
        assertEquals(1, Regex("Muse").findAll(name).count())
        assertTrue(name.contains("Slowed + Reverb")); assertTrue(name.contains("Karaoke"))
        assertFalse(name.contains('|'))
    }
    @Test fun invalidModelCannotBePreparedOrOverwriteItsSource() {
        val path = Files.createTempFile("karaoke-invalid-model", ".onnx").toFile()
        try {
            path.writeText("not a trusted model")
            val before = path.readBytes()
            assertThrows(IllegalArgumentException::class.java) { KaraokeModel.prepare(path) }
            assertArrayEquals(before, path.readBytes())
        } finally { path.delete() }
    }
}
