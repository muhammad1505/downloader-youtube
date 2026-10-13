package id.local.tubeflow.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SourceMetadataTest {
    @Test fun soundcloudStructuredTrackDoesNotRepeatSlowedVersionOrPromoteUploader() {
        val meta = SourceMetadata.parse(JSONObject().put("title", "Hysteria-MUSE (slowed)")
            .put("track", "Hysteria-MUSE (slowed)").put("uploader", "sleep_shady").put("duration", 280.183),
            "https://soundcloud.com/user-428356631/hysteria-muse-slowed-reverb")
        val job = DownloadJob("id", meta.url, "Video", MediaMode.AUDIO, "mp3", 0, 128, autoFolder="artist").withMetadata(meta)
        assertEquals("Hysteria-MUSE | slowed", job.title)
        assertEquals("Hysteria-MUSE", job.trackTitle)
        assertEquals("slowed", job.versionTag)
        assertEquals("", job.artist)
        assertEquals("sleep_shady", job.creator)
        assertEquals("", job.subfolder)
        assertEquals("SoundCloud", job.sourcePlatform.label)
    }
    @Test fun liveDescriptionCreditCorroboratesTitleWithoutPromotingLabel() {
        val raw = "LAMUNAN - SASYA ARKHISNA (Official Music Live) Pindha samudra pasang"
        val json = JSONObject().put("title", raw).put("uploader", "Music Interactive")
            .put("description", "Official Music Live from Sasya Arkhisna - Lamunan\nSongwritter by Composer\nArrangement by Music Interactive")
        val meta = SourceMetadata.parse(json, "https://youtu.be/8uWb5QvR95s")
        val job = DownloadJob("id", meta.url, "Video", MediaMode.AUDIO, "mp3", 0, 128, autoFolder="artist").withMetadata(meta)
        assertEquals(raw, job.rawTitle)
        assertEquals("Lamunan | Live - Sasya Arkhisna", job.title)
        assertEquals("Sasya Arkhisna", job.artist)
        assertEquals("Music Interactive", job.creator)
        assertEquals("source_description_credit", job.artistEvidence)
        assertEquals("sasya-arkhisna", job.subfolder)
    }

    @Test fun descriptionCreditsRequireCompleteNamesInTitleAndUniqueIdentity() {
        fun parse(title: String, description: String) = SourceMetadata.parse(
            JSONObject().put("title",title).put("description",description),"https://youtube.com/watch?v=x")
        assertEquals("",parse("Artist - Song", "Official Music from Someone - Song").artist)
        assertEquals("",parse("Artist - Songbook", "Official Music from Artist - Song").artist)
        assertEquals("",parse("Artist - Song", "Songwriter by Artist\nArrangement by Artist").artist)
        assertEquals("",parse("Artist - Song - Another", "Official Music from Artist - Song\nOfficial Music from Artist - Another").artist)
    }

    @Test fun structuredPlatformArtistWinsOverDescriptionCredit() {
        val meta=SourceMetadata.parse(JSONObject().put("title","A - Song").put("artist","B").put("track","Original")
            .put("description","Official Music from A - Song"),"https://youtube.com/watch?v=x")
        assertEquals("B",meta.artist)
        assertEquals("Original",meta.track)
        assertEquals("",meta.artistEvidence)
    }
    @Test fun creatorAndUploaderAreNotArtistMetadata() {
        val meta=SourceMetadata.parse(JSONObject("""{"id":"source","title":"Song","creator":"Creator","uploader":"Label","thumbnail":"https://cdn.example/cover.jpg"}"""), "https://music.youtube.com/watch?v=source")
        assertEquals("", meta.artist)
        assertEquals("Creator", meta.creator)
        assertEquals("youtube_music", meta.platformId)
        assertEquals("https://cdn.example/cover.jpg", meta.thumbnail)
    }
    @Test fun structuredSongSnapshotSurvivesStateChanges() {
        val meta=SourceMetadata.parse(JSONObject("""{"id":"source","title":"ARTIST - SONG (Live)","artist":"ARTIST","uploader":"Label","track":"SONG","thumbnail":"https://cdn.example/c.jpg"}"""), "https://www.youtube.com/watch?v=source")
        val job=DownloadJob("id",meta.url,"Video",MediaMode.AUDIO,"mp3",0,128,autoFolder="artist").withMetadata(meta)
        assertEquals("ARTIST - SONG (Live)", job.rawTitle)
        assertEquals("SONG", job.trackTitle)
        assertEquals("ARTIST", job.artist)
        assertEquals("artist", job.subfolder)
        for (state in JobState.entries) {
            val changed=job.copy(state=state)
            assertEquals(job.songIdentity(), changed.songIdentity())
            assertEquals("YouTube", changed.sourcePlatform.label)
            assertEquals(meta.thumbnail, changed.thumbnailUrl)
        }
    }
    @Test fun unknownArtistKeepsRootAndCreatorRemainsSeparate() {
        val job=DownloadJob("id","https://www.tiktok.com/@user/video/123","Video",MediaMode.AUDIO,"mp3",0,128,autoFolder="artist")
            .withMetadata(VideoMetadata("DJ Song - CREATOR RMX","CREATOR RMX",200,"","https://www.tiktok.com/@user/video/123"))
        assertEquals("",job.artist)
        assertEquals("",job.subfolder)
        assertEquals("CREATOR RMX",job.remixer)
    }
    @Test fun aliasesUseOneRegistryAndUnknownSourceHasFallback() {
        assertEquals("youtube_music",SourcePlatforms.fromUrl("https://music.youtube.com/watch?v=x").id)
        assertEquals("tiktok",SourcePlatforms.fromUrl("https://vm.tiktok.com/abc").id)
        assertEquals("x",SourcePlatforms.fromUrl("https://twitter.com/person/status/1").id)
        assertEquals("unknown",SourcePlatforms.fromUrl("not a URL").id)
        assertEquals("unknown",SourcePlatforms.fromUrl("https://youtube.com.evil.test/video").id)
        assertEquals("globe",SourcePlatforms.fromId("future_platform").icon)
    }
}
