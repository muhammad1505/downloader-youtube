package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class MediaAttachmentsTest {
    @Test fun sidecarsRequireExactStemIncludingCollisionSuffix() {
        assertTrue(MediaAttachments.belongsTo("E01 - Episode.mp4", "E01 - Episode.id.srt"))
        assertTrue(MediaAttachments.belongsTo("Song (1).mp3", "Song (1).lrc"))
        assertFalse(MediaAttachments.belongsTo("Song (1).mp3", "Song.lrc"))
        assertFalse(MediaAttachments.belongsTo("Song.mp4", "Song 2.id.srt"))
        assertFalse(MediaAttachments.belongsTo("Song.mp4", "Song.mp3"))
        assertFalse(MediaAttachments.belongsTo("Song.mp4", "Song.live.en.srt"))
        assertTrue(MediaAttachments.belongsTo("Song.mp4", "Song.en-orig.srt"))
        assertTrue(MediaAttachments.belongsTo("Song.mp4", "Song.srt"))
    }
    @Test fun lrcAndArtworkAreNotVideoSubtitleTracks() {
        assertFalse(MediaAttachments.isSubtitle("Song.lrc"))
        assertFalse(MediaAttachments.isSubtitle("Song.jpg"))
        assertTrue(MediaAttachments.isSubtitle("Episode.ID.SRT"))
        assertTrue(MediaAttachments.isSubtitle("Episode.ass"))
    }
}
