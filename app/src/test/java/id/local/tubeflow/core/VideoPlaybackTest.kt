package id.local.tubeflow.core

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    fun reopenPositionKeepsLastKnownPositionAfterDecoderError() {
        assertEquals("Posisi pemutar 0 setelah error tidak boleh menghapus posisi UI", 120_000, VideoPlayback.reopenPosition(120_000, 0))
        assertEquals("Posisi pemutar yang lebih maju tetap dipakai", 120_500, VideoPlayback.reopenPosition(120_000, 120_500))
        assertEquals(0, VideoPlayback.reopenPosition(-1_000, 0))
    }

    @Test
    fun retryRestartsPlayerAfterAutomaticSoftwareFallback() {
        assertTrue(VideoPlayback.needsDecoderRestart(hasPlayer = false, softwareFallbackUsed = false))
        assertTrue(VideoPlayback.needsDecoderRestart(hasPlayer = true, softwareFallbackUsed = true))
        assertFalse(VideoPlayback.needsDecoderRestart(hasPlayer = true, softwareFallbackUsed = false))
    }

    @Test
    fun pipOnLeaveNeedsPlayingVideoAndNoPicker() {
        assertTrue(VideoPlayback.shouldEnterPipOnLeave(active = true, playing = true, suppressed = false))
        assertFalse(VideoPlayback.shouldEnterPipOnLeave(active = true, playing = false, suppressed = false))
        assertFalse("Video tidak aktif tidak boleh masuk PiP", VideoPlayback.shouldEnterPipOnLeave(active = false, playing = true, suppressed = false))
        assertFalse("Picker subtitle tidak boleh memicu PiP", VideoPlayback.shouldEnterPipOnLeave(active = true, playing = true, suppressed = true))
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
    fun mediaStoreSidecarsUseResolvedDisplayNameForFormatAndLanguage() {
        // URI MediaStore hanya berisi ID; tanpa nama tampilan ekstensi .srt tidak bisa ditebak.
        val sidecars = "sub|content://media/external/downloads/197934"
        val names = mapOf(
            "content://media/external/downloads/197934" to "E01 - The Battle Begins (1).id.srt",
        )

        val subtitles = VideoPlayback.subtitlesFromSidecars(sidecars, nameOf = { names[it] })

        assertTrue("sidecar MediaStore harus terbaca", VideoPlayback.subtitlesFromSidecars(sidecars).isEmpty())
        assertEquals(1, subtitles.size)
        assertEquals("application/x-subrip", subtitles.single().mime)
        assertEquals("id", subtitles.single().language)
        assertEquals("Indonesia", subtitles.single().label)
    }

    @Test
    fun languageTokensAreReadWithoutGuessingFromOrdinaryWords() {
        assertEquals("id", VideoPlayback.languageFrom("Film.id.srt"))
        assertEquals("en", VideoPlayback.languageFrom("Film.en-US.vtt"))
        assertEquals("jv", VideoPlayback.languageFrom("film_jv.srt"))
        assertEquals("", VideoPlayback.languageFrom("film subtitle.srt"))
        assertEquals("Indonesia", VideoPlayback.languageName("id"))
        assertEquals("Indonesia", VideoPlayback.languageName("ind"))
        assertEquals("Inggris", VideoPlayback.languageName("ENG"))
        assertEquals("", VideoPlayback.languageName("xx"))
    }

    @Test
    fun downloadedSubtitleMatchesDeviceLanguage() {
        // Satu subtitle unduhan: langsung dipakai.
        assertEquals(0, VideoPlayback.preferredSubtitleIndex(listOf("id"), listOf("id"), listOf("id-ID")))
        // Dua subtitle unduhan: bahasa ponsel menang walau bukan track pertama.
        assertEquals(1, VideoPlayback.preferredSubtitleIndex(listOf("en", "id"), listOf("en", "id"), listOf("id")))
        // Tag ISO 639-2 dan tag berwilayah dianggap bahasa yang sama.
        assertEquals(0, VideoPlayback.preferredSubtitleIndex(listOf("ind"), listOf("id"), listOf("id-ID")))
    }

    @Test
    fun downloadedSubtitleWinsOverEmbeddedTrack() {
        // Video MKV dengan track Inggris bawaan + subtitle Indonesia hasil unduhan:
        // subtitle unduhan menang walau bahasa ponsel Inggris.
        assertEquals(1, VideoPlayback.preferredSubtitleIndex(listOf("en", "id"), listOf("id"), listOf("en")))
        // Tanpa unduhan, bahasa perangkat tetap dipilih dari track bawaan.
        assertEquals(1, VideoPlayback.preferredSubtitleIndex(listOf("en", "id"), emptyList(), listOf("id")))
        // Tidak ada yang cocok: track pertama, bukan nonaktif.
        assertEquals(0, VideoPlayback.preferredSubtitleIndex(listOf("ja", "ko"), listOf("id"), listOf("id")))
    }

    @Test
    fun noTextTrackMeansNoAutomaticSelection() {
        assertEquals(-1, VideoPlayback.preferredSubtitleIndex(emptyList(), listOf("id"), listOf("id")))
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

        val (entries, start) = VideoPlayback.videoEntries(listOf(item), item.uri, sidecars = {
            "sub|content://sub/Tujuh.id.srt"
        })

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

    @Test
    fun orientationCyclesAutoLandscapePortrait() {
        assertEquals(VideoOrientation.LANDSCAPE, VideoOrientation.AUTO.next())
        assertEquals(VideoOrientation.PORTRAIT, VideoOrientation.LANDSCAPE.next())
        assertEquals(VideoOrientation.AUTO, VideoOrientation.PORTRAIT.next())
        assertEquals("Otomatis", VideoOrientation.AUTO.label)
    }

    @Test
    fun subtitleDelayIsClampedAndLabeledWithSign() {
        assertEquals(250, VideoPlayback.adjustSubtitleDelay(0, 250))
        assertEquals(10_000, VideoPlayback.adjustSubtitleDelay(9_900, 500))
        assertEquals(-10_000, VideoPlayback.adjustSubtitleDelay(-9_900, -500))
        assertEquals("0,00 dtk", VideoPlayback.subtitleDelayLabel(0))
        assertEquals("+0,25 dtk", VideoPlayback.subtitleDelayLabel(250))
        assertEquals("−1,50 dtk", VideoPlayback.subtitleDelayLabel(-1_500))
    }

    @Test
    fun codecFailuresAreRetriedWithSoftwareDecoder() {
        assertTrue(VideoPlayback.isCodecFailure(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED))
        assertTrue(VideoPlayback.isCodecFailure(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED))
        assertTrue(VideoPlayback.isCodecFailure(PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED))
        assertTrue(VideoPlayback.isCodecFailure(PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES))
        assertTrue(VideoPlayback.isCodecFailure(PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED))
        assertTrue(VideoPlayback.isCodecFailure(PlaybackException.ERROR_CODE_DECODING_FAILED))
        // Renderer MediaCodec yang macet pada berkas lokal memakai TIMEOUT; coba software.
        assertTrue(VideoPlayback.isCodecFailure(PlaybackException.ERROR_CODE_TIMEOUT))
        assertFalse(
            "Masalah file/jaringan tidak perlu membuka ulang pemutar",
            VideoPlayback.isCodecFailure(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND),
        )
        assertFalse(VideoPlayback.isCodecFailure(PlaybackException.ERROR_CODE_UNSPECIFIED))
    }

    @Test
    fun automaticSoftwareFallbackRunsOnceAndRespectsManualHardwareMode() {
        // Timeout AV1 10-bit/AC3 pada perangkat uji memakai jalur ini.
        assertTrue(
            VideoPlayback.shouldUseSoftwareFallback(
                PlaybackException.ERROR_CODE_TIMEOUT,
                VideoDecoderMode.AUTO,
                alreadyTried = false,
            ),
        )
        assertTrue(
            VideoPlayback.shouldUseSoftwareFallback(
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                VideoDecoderMode.SOFTWARE,
                alreadyTried = false,
            ),
        )
        assertFalse(
            "Fallback software tidak boleh berulang tanpa batas",
            VideoPlayback.shouldUseSoftwareFallback(
                PlaybackException.ERROR_CODE_TIMEOUT,
                VideoDecoderMode.AUTO,
                alreadyTried = true,
            ),
        )
        assertFalse(
            "Mode Perangkat adalah pilihan manual dan tidak boleh diganti diam-diam",
            VideoPlayback.shouldUseSoftwareFallback(
                PlaybackException.ERROR_CODE_TIMEOUT,
                VideoDecoderMode.HARDWARE,
                alreadyTried = false,
            ),
        )
        assertFalse(
            "Error I/O bukan kegagalan dekoder",
            VideoPlayback.shouldUseSoftwareFallback(
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                VideoDecoderMode.AUTO,
                alreadyTried = false,
            ),
        )
    }

    @Test
    fun quickButtonsCycleThroughEveryChoiceAndWrapAtTheEnd() {
        assertEquals(1.25f, VideoPlayback.nextSpeed(1f))
        assertEquals(0.25f, VideoPlayback.nextSpeed(2f))
        assertEquals(VideoDecoderMode.HARDWARE, VideoDecoderMode.AUTO.next())
        assertEquals(VideoDecoderMode.SOFTWARE, VideoDecoderMode.HARDWARE.next())
        assertEquals(VideoDecoderMode.AUTO, VideoDecoderMode.SOFTWARE.next())
        assertEquals(VideoEnhance.LIGHT, VideoEnhance.OFF.next())
        assertEquals(VideoEnhance.OFF, VideoEnhance.SHARP.next())
        assertEquals(VideoEqualizer.BASS, VideoEqualizer.OFF.next())
        assertEquals(VideoEqualizer.OFF, VideoEqualizer.ROCK.next())
        assertEquals(VideoAudioEffect.BASS_BOOST, VideoAudioEffect.OFF.next())
        assertEquals(VideoAudioEffect.OFF, VideoAudioEffect.STUDIO.next())
    }

    @Test
    fun equalizerCurvesOnlyBoostTheIntendedFrequencies() {
        assertEquals(0f, VideoEqualizer.OFF.levelDb(80), 0.001f)
        assertTrue(VideoEqualizer.BASS.levelDb(80) > VideoEqualizer.BASS.levelDb(2_000))
        assertTrue(VideoEqualizer.VOCAL.levelDb(1_500) > VideoEqualizer.VOCAL.levelDb(80))
        assertTrue(VideoEqualizer.CINEMA.levelDb(80) > 0f)
        assertTrue(VideoEqualizer.ROCK.levelDb(10_000) > 0f)
    }

    @Test
    fun subtitleTypesFillGapsForFilesWithoutExtensions() {
        assertEquals("application/x-subrip", VideoPlayback.subtitleMimeFromType("application/x-subrip; charset=utf-8"))
        assertEquals("text/vtt", VideoPlayback.subtitleMimeFromType("text/vtt"))
        assertEquals("text/x-ssa", VideoPlayback.subtitleMimeFromType("text/x-ass"))
        assertEquals("application/ttml+xml", VideoPlayback.subtitleMimeFromType("application/ttml+xml"))
        assertNull(VideoPlayback.subtitleMimeFromType("video/mp4"))
        assertNull(VideoPlayback.subtitleMimeFromType(null))
    }

    @Test
    fun externalSubtitlesRoundTripAndDropInvalidEntries() {
        val subtitles = listOf(
            VideoSubtitle("content://sub/film.id.srt", "application/x-subrip", "id", "Indonesia"),
            VideoSubtitle("content://sub/film.en.vtt", "text/vtt", "en", "Inggris"),
            VideoSubtitle("content://sub/film.id.srt", "application/x-subrip", "id", "Duplikat"),
            VideoSubtitle("", "text/vtt", "", "Tanpa URI"),
            VideoSubtitle("content://sub/rusak", "", "", "Tanpa MIME"),
        )

        val decoded = VideoExternalSubtitleCodec.decode(VideoExternalSubtitleCodec.encode(subtitles))

        assertEquals(listOf("content://sub/film.id.srt", "content://sub/film.en.vtt"), decoded.map { it.uri })
        assertEquals("Indonesia", decoded[0].label)
        assertEquals("text/vtt", decoded[1].mime)
        assertTrue(VideoExternalSubtitleCodec.decode(null).isEmpty())
        assertTrue(VideoExternalSubtitleCodec.decode("bukan json").isEmpty())
    }

    @Test
    fun screenshotNamesAreSafeAndTimestamped() {
        val titled = VideoPlayback.screenshotFileName("Video / Satu", 1_793_000_400_000L)
        assertTrue(titled.startsWith("Video-Satu-"))
        assertTrue(titled.endsWith(".png"))
        assertEquals(68, VideoPlayback.screenshotFileName("a".repeat(80), 1_793_000_400_000L).length)
        assertTrue(VideoPlayback.screenshotFileName("///", 1_793_000_400_000L).startsWith("video-"))
    }

    @Test
    fun bufferingIndicatorIgnoresNormalBackgroundPrefetch() {
        // Prefetch latar dapat membuat isLoading true saat STATE_READY; spinner tidak boleh ikut.
        assertTrue(VideoPlayback.shouldShowBuffering(Player.STATE_BUFFERING, hasError = false))
        assertFalse(VideoPlayback.shouldShowBuffering(Player.STATE_READY, hasError = false))
        assertFalse(VideoPlayback.shouldShowBuffering(Player.STATE_BUFFERING, hasError = true))
    }

    @Test
    fun subtitleSizesGrowFromSmallToHuge() {
        assertTrue(VideoSubtitleSize.SMALL.scale < VideoSubtitleSize.NORMAL.scale)
        assertTrue(VideoSubtitleSize.NORMAL.scale < VideoSubtitleSize.LARGE.scale)
        assertTrue(VideoSubtitleSize.LARGE.scale < VideoSubtitleSize.HUGE.scale)
    }

    @Test
    fun subtitlePositionAndBackgroundIncreaseSafely() {
        assertTrue(VideoSubtitlePosition.BOTTOM.bottomPaddingFraction < VideoSubtitlePosition.RAISED.bottomPaddingFraction)
        assertTrue(VideoSubtitlePosition.RAISED.bottomPaddingFraction < VideoSubtitlePosition.HIGH.bottomPaddingFraction)
        assertTrue(VideoSubtitlePosition.HIGH.bottomPaddingFraction < VideoSubtitlePosition.TOP.bottomPaddingFraction)
        assertTrue(VideoSubtitlePosition.TOP.bottomPaddingFraction < 0.5f)
        assertEquals(0, VideoSubtitleBackground.TRANSPARENT.alphaPercent)
        assertTrue(VideoSubtitleBackground.LIGHT.alphaPercent < VideoSubtitleBackground.MEDIUM.alphaPercent)
        assertTrue(VideoSubtitleBackground.MEDIUM.alphaPercent < VideoSubtitleBackground.DARK.alphaPercent)
        assertTrue(VideoSubtitleBackground.DARK.alphaPercent <= 100)
    }

    @Test
    fun effectsKeyOnlyChangesWhenPipelineInputsChange() {
        // Tick progres dan recomposition biasa tidak boleh membangun ulang pipeline GL.
        assertEquals(
            VideoPlayback.effectsKey(VideoEnhance.LIGHT, false, VideoAspect.FIT, 1080, 1920, 3),
            VideoPlayback.effectsKey(VideoEnhance.LIGHT, false, VideoAspect.FIT, 1080, 1920, 3),
        )
        // Setiap input yang benar-benar berubah menghasilkan kunci berbeda.
        assertNotEquals(
            VideoPlayback.effectsKey(VideoEnhance.LIGHT, false, VideoAspect.FIT, 1080, 1920, 3),
            VideoPlayback.effectsKey(VideoEnhance.VIVID, false, VideoAspect.FIT, 1080, 1920, 3),
        )
        assertNotEquals(
            VideoPlayback.effectsKey(VideoEnhance.LIGHT, false, VideoAspect.FIT, 1080, 1920, 3),
            VideoPlayback.effectsKey(VideoEnhance.LIGHT, false, VideoAspect.FILL, 1080, 1920, 3),
        )
        assertNotEquals(
            VideoPlayback.effectsKey(VideoEnhance.LIGHT, false, VideoAspect.FIT, 1080, 1920, 3),
            VideoPlayback.effectsKey(VideoEnhance.LIGHT, false, VideoAspect.FIT, 720, 1280, 3),
        )
        assertNotEquals(
            "Pemutar baru wajib menerima efek lagi",
            VideoPlayback.effectsKey(VideoEnhance.LIGHT, false, VideoAspect.FIT, 1080, 1920, 3),
            VideoPlayback.effectsKey(VideoEnhance.LIGHT, false, VideoAspect.FIT, 1080, 1920, 4),
        )
        // Mode hemat baterai melepas efek, jadi kuncinya sama dengan enhance OFF.
        assertEquals(
            VideoPlayback.effectsKey(VideoEnhance.OFF, false, VideoAspect.FIT, 1080, 1920, 3),
            VideoPlayback.effectsKey(VideoEnhance.SHARP, true, VideoAspect.FIT, 1080, 1920, 3),
        )
    }
}
