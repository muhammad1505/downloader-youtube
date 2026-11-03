package id.local.tubeflow.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RowBadgesTest {

    @Test
    fun audioWithLyricFileShowsTheLyricsBadge() {
        val badges = rowBadges(audio = true, lyricUri = "content://media/external/audio/1", sidecarUris = null)

        assertTrue(badges.lyrics)
        assertEquals(0, badges.subtitles)
    }

    @Test
    fun audioWithoutAnyLyricShowsNothing() {
        assertEquals(RowBadges(), rowBadges(audio = true, lyricUri = null, sidecarUris = null))
        assertEquals(RowBadges(), rowBadges(audio = true, lyricUri = "", sidecarUris = null))
    }

    @Test
    fun audioLyricSidecarCountsAsLyricsEvenWithoutLyricUri() {
        // LRC hasil unduhan tersimpan sebagai baris `sub|` pada sidecarUris unduhan terkait.
        val badges = rowBadges(audio = true, lyricUri = null, sidecarUris = "sub|content://media/lyrics/1.lrc")

        assertTrue(badges.lyrics)
    }

    @Test
    fun videoCountsEverySubtitleSidecarAndIgnoresThumbnail() {
        val sidecars = listOf(
            "sub|content://media/subs/id.srt",
            "sub|content://media/subs/id.en.vtt",
            "thumb|content://media/thumbs/1.jpg",
        ).joinToString("\n")

        val badges = rowBadges(audio = false, lyricUri = null, sidecarUris = sidecars)

        assertEquals(2, badges.subtitles)
        assertFalse("Video tidak menampilkan ikon lirik", badges.lyrics)
    }

    @Test
    fun thumbnailOnlyDownloadShowsNoBadge() {
        assertEquals(RowBadges(), rowBadges(audio = false, lyricUri = null, sidecarUris = "thumb|content://media/thumbs/1.jpg"))
        assertEquals(RowBadges(), rowBadges(audio = false, lyricUri = null, sidecarUris = null))
    }

    @Test
    fun fileNameAppearsOnlyWhenItDoesNotRepeatTheTitle() {
        // Judul kosong: nama file satu-satunya identitas yang tersedia.
        assertTrue(showFileName("", "Judul Tanpa Judul.mp3"))
        // Nama berbeda dari judul (mis. "Band - Judul") layak ditampilkan.
        assertTrue(showFileName("Judul Lagu", "Band - Judul Lagu.mp3"))
        // Nama hanya mengulang judul — jangan dobel informasi di baris.
        assertFalse(showFileName("Judul Lagu", "Judul Lagu.mp3"))
        assertFalse(showFileName("Judul Lagu", "Judul Lagu (Live).mp3"))
        assertFalse(showFileName("Judul Lagu", "judul lagu.MP3"))
    }
}
