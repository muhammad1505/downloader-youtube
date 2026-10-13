package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class SmartMediaTest {
    @Test fun automaticArtworkCannotChooseALossyCodecForNativeAudio() {
        assertEquals("best", Selection.audioTargetFormat("auto", 0))
        assertEquals("mp3", Selection.audioTargetFormat("auto", 128))
        assertEquals("mp3", Selection.audioTargetFormat("auto", 320))
        for (format in listOf("opus", "flac", "m4a", "mp3", "wav")) assertEquals(format, Selection.audioTargetFormat(format, 0))
    }
    @Test fun classificationUsesMimeAndRejectsSidecarsAndUnknownFormats() {
        assertEquals("AUDIO", SmartMedia.classify("audio/mp4", "track.m4a"))
        assertEquals("VIDEO", SmartMedia.classify("video/webm", "clip.webm"))
        assertEquals("AUDIO", SmartMedia.classify("application/octet-stream", "TRACK.FLAC"))
        assertEquals("VIDEO", SmartMedia.classify(null, "movie.mkv"))
        assertNull(SmartMedia.classify("text/plain", "lyrics.lrc"))
        assertNull(SmartMedia.classify("image/jpeg", "cover.mp3"))
        assertNull(SmartMedia.classify(null, "unknown.bin"))
    }
    @Test fun universalNamesNeverPromoteChannelAndKeepArtistAndVersionOnce() {
        val known = TitleSplit.identify("Artist - Song (Live)", "Uploader", "Artist", "Song (Live)")
        val audio = SmartMedia.fileName(known, "Artist - Song (Live)", "mp3", true)
        val video = SmartMedia.fileName(known, "Artist - Song (Live)", "mp4", true)
        assertEquals(audio.substringBeforeLast('.'), video.substringBeforeLast('.'))
        assertTrue(audio.contains("Artist")); assertTrue(audio.contains("Live")); assertFalse(audio.contains("Uploader"))
        val unknown = TitleSplit.identify("Mountains", "Tour Channel")
        assertEquals("Mountains.mp4", SmartMedia.fileName(unknown, "Mountains", "mp4", true))
        assertFalse(SmartMedia.fileName(unknown, "Mountains", "mp3", true).contains("Unknown"))
    }
    @Test fun originalNameOptionKeepsRawTitleAndRealExtension() {
        val identity = TitleSplit.identify("Song (Official Video)")
        assertEquals("Original Source.m4a", SmartMedia.fileName(identity, "Original Source", "m4a", false))
        assertTrue(SmartMedia.fileName(identity, "x".repeat(500), "mp3", false).length <= 184)
    }
    @Test fun manualRenamePreservesExtensionAndRejectsPaths() {
        assertEquals("New Title.mp3", SmartMedia.renameName("New Title", "Old.mp3"))
        assertEquals("Song | Live.mp3", SmartMedia.renameName("Song | Live", "Old.mp3"))
        assertEquals("New", SmartMedia.renameName("New", "NoExtension"))
        assertEquals("New.mp4", SmartMedia.renameName("New.mp4", "Old.mp4"))
        for (name in listOf("", "../track", "a\\b", "a:b", "x".repeat(241), "漢".repeat(90))) {
            try { SmartMedia.renameName(name, "Old.mp3"); fail(name) } catch (_: IllegalArgumentException) { }
        }
        assertEquals("%a\\_b\\%c%", SmartMedia.search("a_b%c"))
    }
    @org.junit.Test fun nativeTagsResolveArtistWithoutChangingSourceIdentity() {
        val original = DownloadJob(id="native", url="https://soundcloud.com/u/song", title="Upload caption", rawTitle="Upload caption", creator="Uploader", mode=MediaMode.AUDIO, format="mp3", quality=0, bitrate=128, autoFolder="artist")
        val updated = original.withNativeAudioMetadata("Song", "Performer", true)
        org.junit.Assert.assertEquals("Song - Performer", updated.title)
        org.junit.Assert.assertEquals("Upload caption", updated.rawTitle)
        org.junit.Assert.assertEquals("Uploader", updated.creator)
        org.junit.Assert.assertEquals("file_metadata", updated.artistEvidence)
        org.junit.Assert.assertEquals(Selection.artistFolder("Performer", false), updated.subfolder)
    }
    @org.junit.Test fun provenSourceIdentityWinsAndMissingNativeArtistStaysUnresolved() {
        val original = DownloadJob(id="native", url="https://soundcloud.com/u/song", title="Song", trackTitle="Song", artist="Performer", artistEvidence="metadata_artist", mode=MediaMode.AUDIO, format="mp3", quality=0, bitrate=128)
        org.junit.Assert.assertEquals("Performer", original.withNativeAudioMetadata("Other", "Different", false).artist)
        org.junit.Assert.assertEquals("Song", original.withNativeAudioMetadata("Other", "Different", false).trackTitle)
        org.junit.Assert.assertEquals("", original.copy(artist="",artistEvidence="").withNativeAudioMetadata("Song", "Unknown Artist", true).artist)
    }
}
