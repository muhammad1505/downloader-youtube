package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class PublishedLyricIdentityTest {
    private val job=DownloadJob("id","https://youtu.be/example","Song - Artist",MediaMode.AUDIO,"mp3",0,128,
        rawTitle="Artist - Song",trackTitle="Song",artist="Artist",artistEvidence="metadata_artist")

    @Test fun originalAndProviderCollisionKeepVerifiedIdentity() {
        assertEquals(job.songIdentity(),PublishedLyricIdentity.fromName(job,"Song - Artist.mp3"))
        assertEquals(job.songIdentity(),PublishedLyricIdentity.fromName(job,"Song - Artist (1).mp3"))
    }
    @Test fun changedTitleUsesCurrentFilenameWithCorroboratedArtist() {
        val query=PublishedLyricIdentity.fromName(job,"New Song - Artist.mp3")
        assertEquals("New Song",query.title)
        assertEquals("Artist",query.artist)
    }
    @Test fun changedCreditIsNotTrustedOrReplacedWithStaleArtist() {
        val query=PublishedLyricIdentity.fromName(job,"New Song - Someone Else.mp3")
        assertEquals("New Song - Someone Else",query.title)
        assertNull(query.artist)
    }
    @Test fun originalNamePreferenceDoesNotLoseItsVerifiedCredit() {
        assertEquals(job.songIdentity(),PublishedLyricIdentity.fromName(job.copy(autoRename=false),"Artist - Song.mp3"))
    }
}
