package id.local.tubeflow.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SourceMetadataTest {
    @Test fun mixGroupsByProvenRemixerWithoutPromotingUploaderToArtist() {
        val raw = "DJ JAUH KO PERGI | SA CERITAKAN PADA BINTANG BINTANG SLOW VIRAL TIKTOK FULL SONG MAMAN FVNDY 2026"
        val meta = SourceMetadata.parse(JSONObject().put("title", raw).put("uploader", "Maman Fvndy Rmx"),
            "https://www.youtube.com/watch?v=3J1Opi7MVHw")
        val job = DownloadJob("id", meta.url, "Video", MediaMode.AUDIO, "mp3", 0, 128, autoFolder = "performer").withMetadata(meta)
        assertEquals("", job.artist)
        assertEquals("Maman Fvndy Rmx", job.remixer)
        assertEquals("maman-fvndy-rmx", job.subfolder)
        assertTrue(job.title.endsWith(" - Maman Fvndy Rmx"))
        assertEquals("", job.songIdentity().artist.orEmpty())
        assertEquals(job.subfolder, job.withNativeAudioMetadata("", "", false).subfolder)
    }

    @Test fun playlistUsesPerItemIdentityAndUnknownCreditKeepsRoot() {
        fun job(raw: String, channel: String, artist: String = "") = DownloadJob("id", "https://youtu.be/example", "Video",
            MediaMode.AUDIO, "mp3", 0, 128, autoFolder = "performer")
            .withMetadata(VideoMetadata(raw, channel, 200, "", "https://youtu.be/example", artist = artist))
        assertEquals("artist-a", job("Song A", "Playlist owner", "Artist A").subfolder)
        assertEquals("artist-b", job("Song B", "Playlist owner", "Artist B").subfolder)
        assertEquals("", job("DJ CURI CURI PANDANG", "Maman Fvndy Rmx").subfolder)
        val remix = job("DJ Song - CREATOR RMX", "CREATOR RMX")
        assertEquals("creator-rmx", remix.subfolder)
        assertEquals("performer", remix.copy(state = JobState.PAUSED).autoFolder)
        assertEquals("creator-rmx", remix.withNativeAudioMetadata("", "", false).subfolder)
        assertEquals("recorded-artist", remix.withNativeAudioMetadata("", "Recorded Artist", false).subfolder)
    }

    @Test fun explicitIndonesianDuetCreditUsesPerformersAndKeepsLiveVersion() {
        val raw = "DENNY CAKNAN X HAPPY ASMARA - SATRU 2 ( Offical Live Music ) DC MUSIK"
        val meta = SourceMetadata.parse(JSONObject().put("title", raw).put("uploader", "DC. PRODUCTION")
            .put("description", "Title: SATRU 2\nVoc : DENNY CAKNAN FT HAPPY ASMARA\nCipt : DENNY CAKNAN\nProd. Denny caknan"),
            "https://www.youtube.com/watch?v=rEjSkBl47OY")
        val job = DownloadJob("id", meta.url, "Video", MediaMode.AUDIO, "mp3", 0, 128, autoFolder = "artist").withMetadata(meta)
        assertEquals("DENNY CAKNAN FT HAPPY ASMARA", job.artist)
        assertEquals("SATRU 2", job.trackTitle)
        assertEquals("Live", job.versionTag)
        assertEquals("SATRU 2 | Live - DENNY CAKNAN FT HAPPY ASMARA", job.title)
        assertFalse(job.title.contains("DC MUSIK"))
        assertEquals("DC. PRODUCTION", job.creator)
        assertEquals("source_description_credit", job.artistEvidence)
        assertEquals("denny-caknan-ft-happy-asmara", job.subfolder)
        assertEquals(raw, job.rawTitle)
    }

    @Test fun labelledCreditsRejectIncompleteNamesAmbiguityAndNonPerformers() {
        fun parse(title: String, description: String) = SourceMetadata.parse(
            JSONObject().put("title", title).put("description", description), "https://youtu.be/example")
        assertEquals("", parse("Artist - Song", "Title: Song\nCipt: Artist\nProducer: Artist").artist)
        assertEquals("", parse("Artist - Songbook", "Title: Song\nVoc: Artist").artist)
        assertEquals("", parse("Artist - Song", "Title: Song\nVoc: Artist Extended").artist)
        assertEquals("", parse("A X B - Song", "Title: Song\nVoc: A FT C").artist)
        assertEquals("", parse("Artist - Song - Other", "Title: Song\nTitle: Other\nVoc: Artist").artist)
        assertEquals("", parse("A - Song - B", "Title: Song\nVoc: A\nVocal: B").artist)
        assertEquals("Artist", parse("Song - Artist", "Judul Lagu: Song\nPenyanyi: Artist").artist)
    }

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

    @Test fun structuredTrackDoesNotBlockMissingArtistCreditFromDescription() {
        val meta=SourceMetadata.parse(JSONObject().put("title","Artist - Song").put("track","Song")
            .put("description","Song: Song\nArtist: Artist"),"https://youtu.be/example")
        assertEquals("Artist",meta.artist)
        assertEquals("Song",meta.track)
        assertEquals("source_description_credit",meta.artistEvidence)
    }
    @Test fun placeholderArtistAndCreditsAfterFiftyLinesCanStillResolve() {
        val meta=SourceMetadata.parse(JSONObject().put("title","Artist - Song").put("artist","Unknown Artist")
            .put("description","intro\n".repeat(60)+"Judul: Song\nArtis: Artist"),"https://youtu.be/example")
        assertEquals("Artist",meta.artist)
    }
    @Test fun reportedVideoHeightsAreDeduplicatedAndIgnoreAudioOrUnknownFormats() {
        val formats = org.json.JSONArray()
            .put(JSONObject().put("format_id", "137").put("vcodec", "avc1").put("height", 1080))
            .put(JSONObject().put("format_id", "136").put("vcodec", "avc1").put("height", 720))
            .put(JSONObject().put("format_id", "135").put("vcodec", "avc1").put("height", 720))
            .put(JSONObject().put("format_id", "140").put("vcodec", "none").put("height", 0))
            .put(JSONObject().put("format_id", "sb0").put("vcodec", "none"))
            .put(JSONObject().put("format_id", "ig").put("vcodec", "avc1"))
        val meta = SourceMetadata.parse(
            JSONObject().put("title", "Reel").put("formats", formats),
            "https://www.instagram.com/reel/ABC/",
        )
        assertEquals(listOf(1080, 720), meta.resolutions)
    }

    @Test fun missingFormatsMeansNoReportedResolutions() {
        val meta = SourceMetadata.parse(JSONObject().put("title", "Audio"), "https://soundcloud.com/a/b")
        assertEquals(emptyList<Int>(), meta.resolutions)
    }

    @Test fun rootHeightCoversExtractorsWithoutPerFormatHeights() {
        val meta = SourceMetadata.parse(
            JSONObject().put("title", "Reel").put("height", 1920).put("formats", org.json.JSONArray()),
            "https://www.instagram.com/reel/ABC/",
        )
        assertEquals(listOf(1920), meta.resolutions)
    }

    @Test fun conflictingStructuredTrackDoesNotAcquireAnUnrelatedPerformer() {
        val meta=SourceMetadata.parse(JSONObject().put("title","Artist - Song - Other").put("track","Other")
            .put("description","Title: Song\nVoc: Artist"),"https://youtu.be/example")
        assertEquals("",meta.artist)
        assertEquals("Other",meta.track)
    }
}
