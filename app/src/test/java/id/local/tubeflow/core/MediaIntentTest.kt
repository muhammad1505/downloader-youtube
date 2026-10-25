package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaIntentTest {
    @Test
    fun mimeTypeDecidesAudioAndVideo() {
        assertEquals(
            IncomingMedia.Kind.AUDIO,
            MediaIntent.kind("audio/mpeg", "content://media/external/audio/media/42"),
        )
        assertEquals(
            IncomingMedia.Kind.VIDEO,
            MediaIntent.kind("video/x-matroska", "content://media/external/video/media/7"),
        )
    }

    @Test
    fun genericMimeFallsBackToFileExtension() {
        val name = "Konser 2026.MKV"
        assertEquals(IncomingMedia.Kind.VIDEO, MediaIntent.kind("application/octet-stream", name))
        assertEquals(IncomingMedia.Kind.AUDIO, MediaIntent.kind(null, "file:///storage/emulated/0/Music/Lagu.FLAC"))
    }

    @Test
    fun oggApplicationMimeIsAudio() {
        assertEquals(IncomingMedia.Kind.AUDIO, MediaIntent.kind("application/ogg", "content://x/1"))
        assertEquals(IncomingMedia.Kind.AUDIO, MediaIntent.kind("application/x-ogg", "content://x/1"))
    }

    @Test
    fun unknownContentWithoutMimeIsRejected() {
        assertNull(MediaIntent.kind(null, "content://media/external/file/1234"))
        assertNull(MediaIntent.kind("application/pdf", "content://docs/1"))
        assertNull(MediaIntent.kind("text/plain", "content://docs/notes.txt"))
    }

    @Test
    fun httpLinksStayOnTheDownloadPath() {
        assertNull(MediaIntent.from("https://youtu.be/abc", "video/mp4", "abc.mp4"))
        assertNull(MediaIntent.from("https://example.com/song.mp3", "audio/mpeg", "song.mp3"))
        assertFalse(MediaIntent.isLocalMediaUri("https://example.com/song.mp3"))
    }

    @Test
    fun localMediaUriBecomesPlayableMedia() {
        val audio = MediaIntent.from(
            "content://media/external/audio/media/99",
            "audio/mpeg",
            "Judul Lagu.mp3",
        )
        assertEquals(IncomingMedia.Kind.AUDIO, audio?.kind)
        assertEquals("content://media/external/audio/media/99", audio?.uri)
        assertEquals("Judul Lagu", audio?.title)

        val video = MediaIntent.from(
            "file:///storage/emulated/0/Download/TubeFlow/Klip%20Akhir.mp4",
            null,
            "Klip Akhir.mp4",
        )
        assertEquals(IncomingMedia.Kind.VIDEO, video?.kind)
        assertEquals("Klip Akhir", video?.title)
    }

    @Test
    fun titleFallsBackToUriNameAndDropsExtension() {
        assertEquals("rekaman", MediaIntent.title("rekaman.m4a"))
        assertEquals("tanpa ekstensi", MediaIntent.title("tanpa ekstensi"))
        // Nama berkas tersembunyi tidak punya batang nama; jangan mengosongkan judul pemutar.
        assertEquals(".mp3", MediaIntent.title(".mp3"))
        assertEquals("", MediaIntent.title("   "))
        val media = MediaIntent.from("file:///storage/emulated/0/Music/rekaman.flac", null, null)
        assertEquals("rekaman", media?.title)
    }

    @Test
    fun localSchemeIsRequired() {
        assertTrue(MediaIntent.isLocalMediaUri("content://media/external/video/media/1"))
        assertTrue(MediaIntent.isLocalMediaUri("file:///sdcard/Music/a.mp3"))
        assertNull(MediaIntent.from("/storage/emulated/0/Music/a.mp3", "audio/mpeg", "a.mp3"))
    }
}
