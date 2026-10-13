package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class CollectionFolderTest {
    private val url = "https://www.youtube.com/watch?v=rEjSkBl47OY&list=RDrEjSkBl47OY&start_radio=1"
    private val playlist = PlaylistMetadata("Campuran - SATRU 2", url, emptyList(), "DC. PRODUCTION")
    private val seed = VideoMetadata("DENNY CAKNAN X HAPPY ASMARA - SATRU 2 (Live)", "DC. PRODUCTION", 394, "",
        "https://www.youtube.com/watch?v=rEjSkBl47OY", artist="DENNY CAKNAN FT HAPPY ASMARA", track="SATRU 2")
    @Test fun localizedMixUsesVerifiedSeedMainArtist() {
        assertEquals("mix-denny-caknan", Selection.collectionFolder(playlist, seed))
    }
    @Test fun all25SongsKeepCollectionFolderThroughRefreshNativeTagsAndRetry() {
        val folder = Selection.collectionFolder(playlist, seed)
        for (i in 1..25) {
            val job = DownloadJob("id$i", "https://youtu.be/example$i", "Video", MediaMode.AUDIO, "mp3", 0, 128,
                subfolder=folder, autoFolder="collection").withMetadata(VideoMetadata("Song $i", "Label $i", 200, "", "https://youtu.be/example$i", artist="Artist $i", track="Song $i"))
            assertEquals("Song $i - Artist $i", job.title)
            for (state in JobState.entries) {
                assertEquals(folder, job.copy(state=state).withNativeAudioMetadata("Song $i", "Other Artist", false).subfolder)
                assertEquals("Artist $i", job.artist)
            }
        }
    }
    @Test fun unverifiedChannelAndUnrelatedSeedCannotDetermineArtistFolder() {
        val unknown = seed.copy(artist="", title="SATRU 2", track="", uploader="DENNY CAKNAN")
        assertEquals("mix-rejskbl47oy", Selection.collectionFolder(playlist, unknown))
        assertEquals("mix-rejskbl47oy", Selection.collectionFolder(playlist, seed.copy(url="https://youtu.be/otherseed00")))
    }
    @Test fun remixerCanNameCollectionWithoutInventingOriginalArtist() {
        val other = playlist.copy(url="https://www.youtube.com/watch?v=3J1Opi7MVHw&list=RD3J1Opi7MVHw")
        val dj = seed.copy(url="https://youtu.be/3J1Opi7MVHw", title="DJ Song - MAMAN FVNDY RMX", artist="", track="", uploader="MAMAN FVNDY RMX")
        assertEquals("mix-maman-fvndy-rmx", Selection.collectionFolder(other, dj))
    }
    @Test fun staticAlbumUsesActualPlaylistTitleRegardlessOfOwnerOrTrackArtists() {
        val album = playlist.copy(url="https://youtube.com/playlist?list=PLexample", title="Album Kenangan (Live)")
        assertEquals("album-kenangan-live", Selection.collectionFolder(album, seed))
        val folder = Selection.collectionFolder(album.copy(title="///"))
        assertTrue(folder.startsWith("playlist-"))
        assertEquals(folder, Selection.safeFolder(folder))
    }
}
