package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlaybackTest {

    private fun media(
        uri: String,
        title: String = uri.substringAfterLast('/'),
        kind: String = "VIDEO",
        availability: String = "AVAILABLE",
        location: String = uri,
    ) = MediaItem(
        uri = uri,
        kind = kind,
        title = title,
        displayName = "$title.mp4",
        mime = if (kind == "VIDEO") "video/mp4" else "audio/mpeg",
        folder = "Download/TubeFlow",
        availability = availability,
        accessUri = location,
    )

    private fun job(
        id: String,
        title: String,
        mode: MediaMode = MediaMode.VIDEO,
        state: JobState = JobState.COMPLETED,
        output: String? = "content://media/video/$id",
        sidecars: String? = null,
    ) = DownloadJob(
        id = id,
        url = "https://youtu.be/$id",
        title = title,
        mode = mode,
        format = "auto",
        quality = 0,
        bitrate = 0,
        state = state,
        outputUri = output,
        sidecarUris = sidecars,
    )

    @Test
    fun timeLabelUsesHoursOnlyWhenNeeded() {
        assertEquals("0:00", VideoPlayback.timeLabel(0))
        assertEquals("1:05", VideoPlayback.timeLabel(65_000))
        assertEquals("1:00:05", VideoPlayback.timeLabel(3_605_000))
        assertEquals("0:00", VideoPlayback.timeLabel(-5_000))
    }

    @Test
    fun speedLabelUsesIndonesianDecimalComma() {
        assertEquals("1×", VideoPlayback.speedLabel(1f))
        assertEquals("0,25×", VideoPlayback.speedLabel(0.25f))
        assertEquals("1,5×", VideoPlayback.speedLabel(1.5f))
        assertEquals("2×", VideoPlayback.speedLabel(2f))
    }

    @Test
    fun seekTargetNeverLeavesTheVideo() {
        assertEquals(15_000, VideoPlayback.seekTarget(10_000, 5_000, 60_000))
        assertEquals(0, VideoPlayback.seekTarget(5_000, -10_000, 60_000))
        assertEquals(60_000, VideoPlayback.seekTarget(58_000, 10_000, 60_000))
        assertEquals(12_000, VideoPlayback.seekTarget(10_000, 2_000, 0))
    }

    @Test
    fun resumeOnlyForMeaningfulMiddlePositions() {
        assertFalse(VideoPlayback.shouldResume(null, 600_000))
        assertFalse("Terlalu dekat awal", VideoPlayback.shouldResume(9_000, 600_000))
        assertTrue(VideoPlayback.shouldResume(60_000, 600_000))
        assertFalse("Bagian akhir dianggap selesai", VideoPlayback.shouldResume(597_000, 600_000))
        assertTrue("Durasi belum diketahui tetap boleh lanjut", VideoPlayback.shouldResume(60_000, 0))
    }

    @Test
    fun subtitleMimeIgnoresAudioLyrics() {
        assertEquals("application/x-subrip", VideoPlayback.subtitleMime("film.id.srt"))
        assertEquals("text/vtt", VideoPlayback.subtitleMime("content://sub/film.en.vtt"))
        assertEquals("text/x-ssa", VideoPlayback.subtitleMime("film.ass"))
        assertEquals("application/ttml+xml", VideoPlayback.subtitleMime("film.ttml"))
        assertNull("LRC lirik bukan subtitle video", VideoPlayback.subtitleMime("lagu.lrc"))
        assertNull(VideoPlayback.subtitleMime("tanpa-ekstensi"))
    }

    @Test
    fun sidecarsBecomeDeduplicatedSubtitleChoices() {
        val sidecars = listOf(
            "thumb|content://thumb/1",
            "sub|content://sub/Judul.id.srt",
            "sub|content://sub/Judul.en.vtt",
            "sub|content://sub/Judul.id.srt",
            "sub|content://sub/lagu.lrc",
            "sub|",
        ).joinToString("\n")

        val subtitles = VideoPlayback.subtitlesFromSidecars(sidecars)

        assertEquals(2, subtitles.size)
        assertEquals("content://sub/Judul.id.srt", subtitles[0].uri)
        assertEquals("id", subtitles[0].language)
        assertEquals("Indonesia", subtitles[0].label)
        assertEquals("application/x-subrip", subtitles[0].mime)
        assertEquals("Inggris", subtitles[1].label)
        assertEquals("text/vtt", subtitles[1].mime)
    }

    @Test
    fun languageTokensAreReadWithoutGuessingFromOrdinaryWords() {
        assertEquals("id", VideoPlayback.languageFrom("Film.id.srt"))
        assertEquals("en", VideoPlayback.languageFrom("Film.en-US.vtt"))
        assertEquals("jv", VideoPlayback.languageFrom("film_jv.srt"))
        assertEquals("", VideoPlayback.languageFrom("film subtitle.srt"))
        assertEquals("Indonesia", VideoPlayback.languageName("id"))
        assertEquals("", VideoPlayback.languageName("xx"))
    }

    @Test
    fun trackLabelFallsBackWhenContainerHasNoMetadata() {
        assertEquals("Indonesia", VideoPlayback.trackLabel(null, "id", 0, "Subtitle"))
        assertEquals("Komentar · Inggris", VideoPlayback.trackLabel("Komentar", "en", 0, "Audio"))
        assertEquals("Audio 2", VideoPlayback.trackLabel(null, null, 1, "Audio"))
    }

    @Test
    fun libraryQueueKeepsOrderAndStartsAtTheTappedVideo() {
        val items = listOf(
            media("content://media/video/1", "Satu"),
            media("content://media/video/2", "Dua", availability = "UNAVAILABLE"),
            media("content://media/audio/3", "Lagu", kind = "AUDIO"),
            media("content://media/video/4", "Empat"),
        )

        val (entries, start) = VideoPlayback.videoEntries(items, "content://media/video/4")

        assertEquals(listOf("Satu", "Empat"), entries.map { it.title })
        assertEquals(1, start)
    }

    @Test
    fun libraryQueueUsesAccessUriAndLinkedSubtitle() {
        val item = media(
            "content://media/video/7",
            "Tujuh",
            location = "content://media/external/video/7",
        )

        val (entries, start) = VideoPlayback.videoEntries(listOf(item), item.uri) {
            "sub|content://sub/Tujuh.id.srt"
        }

        assertEquals(0, start)
        assertEquals("content://media/external/video/7", entries.single().uri)
        assertEquals("Indonesia", entries.single().subtitles.single().label)
    }

    @Test
    fun historyQueueSkipsUnfinishedAndAudioRows() {
        val jobs = listOf(
            job("v1", "Video satu"),
            job("a1", "Lagu", mode = MediaMode.AUDIO),
            job("v2", "Video gagal", state = JobState.FAILED),
            job("v3", "Video tiga", output = "content://media/video/three"),
        )

        val (entries, start) = VideoPlayback.videoEntries(jobs, "v3")

        assertEquals(listOf("Video satu", "Video tiga"), entries.map { it.title })
        assertEquals("content://media/video/three", entries[1].uri)
        assertEquals(1, start)
    }

    @Test
    fun resumeCodecRoundTripsAndDropsExpiredEntries() {
        val now = 2_000_000_000_000L
        val raw = VideoResumeCodec.encode(
            mapOf(
                "content://video/baru" to VideoResumeCodec.Entry(70_000, 600_000, now),
                "content://video/lama" to VideoResumeCodec.Entry(80_000, 600_000, now - VideoResumeCodec.MAX_AGE_MS - 1),
                "content://video/rusak" to VideoResumeCodec.Entry(-1, 0, now),
            ),
        )

        val decoded = VideoResumeCodec.decode(raw, now)

        assertEquals(setOf("content://video/baru"), decoded.keys)
        assertEquals(70_000, decoded.getValue("content://video/baru").positionMs)
    }

    @Test
    fun resumeCodecKeepsOnlyNewestEntriesWithinLimit() {
        val now = 5_000L
        val raw = VideoResumeCodec.encode(
            mapOf(
                "a" to VideoResumeCodec.Entry(10_000, 100_000, now - 30),
                "b" to VideoResumeCodec.Entry(10_000, 100_000, now - 20),
                "c" to VideoResumeCodec.Entry(10_000, 100_000, now - 10),
            ),
        )

        val decoded = VideoResumeCodec.decode(raw, now, limit = 2)

        assertEquals(listOf("c", "b"), decoded.keys.toList())
    }

    @Test
    fun resumeCodecSurvivesMissingOrMalformedStorage() {
        assertTrue(VideoResumeCodec.decode(null).isEmpty())
        assertTrue(VideoResumeCodec.decode("bukan json").isEmpty())
        assertTrue(VideoResumeCodec.decode("[]").isEmpty())
    }

    @Test
    fun pipAspectStaysInsideAndroidLimits() {
        assertEquals(16 to 9, VideoPlayback.pipAspect(0, 0))
        assertEquals(1920 to 1080, VideoPlayback.pipAspect(1920, 1080))
        assertEquals(1080 to 2400, VideoPlayback.pipAspect(1080, 2400))
        assertEquals("Ultra-wide dipetakan ke 2,39", 239 to 100, VideoPlayback.pipAspect(3840, 1080))
        assertEquals("Vertikal ekstrem dipetakan ke 0,4184", 4184 to 10_000, VideoPlayback.pipAspect(1000, 3000))
    }

    @Test
    fun aspectAndRepeatCycleThroughAllChoices() {
        assertEquals(VideoAspect.FILL, VideoAspect.FIT.next())
        assertEquals(VideoAspect.ZOOM, VideoAspect.FILL.next())
        assertEquals(VideoAspect.FIT, VideoAspect.ZOOM.next())
        assertEquals(VideoRepeat.ALL, VideoRepeat.OFF.next())
        assertEquals(VideoRepeat.ONE, VideoRepeat.ALL.next())
        assertEquals(VideoRepeat.OFF, VideoRepeat.ONE.next())
    }
}
