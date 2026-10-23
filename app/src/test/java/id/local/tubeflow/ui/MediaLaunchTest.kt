package id.local.tubeflow.ui

import id.local.tubeflow.core.DownloadJob
import id.local.tubeflow.core.MediaMode
import org.junit.Assert.*
import org.junit.Test

class MediaLaunchTest {
    private fun job(mode: MediaMode, uri: String?) = DownloadJob(
        id = "fixture", url = "https://example.org/media", title = "Media", mode = mode,
        format = if (mode == MediaMode.AUDIO) "mp3" else "mp4", quality = 720, bitrate = 192,
        outputUri = uri,
    )

    @Test fun successfulOpenAndShareDoNotReportMissingFile() {
        for (mode in MediaMode.entries) for (share in listOf(false, true)) {
            var calls = 0
            val result = launchFile(job(mode, "content://media/external/file/42"), share) { uri, audio, sharing ->
                calls++
                assertEquals("content://media/external/file/42", uri)
                assertEquals(mode == MediaMode.AUDIO, audio)
                assertEquals(share, sharing)
                null
            }
            assertNull(result)
            assertEquals(1, calls)
        }
    }

    @Test fun absentOrBlankUriNeverLaunchesAnotherApp() {
        for (uri in listOf(null, "", "  ")) {
            assertEquals("File tidak tersedia.", launchFile(job(MediaMode.VIDEO, uri), false) { _, _, _ ->
                error("Missing URI must not launch a player")
            })
        }
    }

    @Test fun shareCaptionUsesFileNameWithoutExtension() {
        assertEquals("Ipank - Mutiara", shareCaptionFrom("Ipank - Mutiara.mp3"))
        assertEquals("Artist - Song (Live) [320]", shareCaptionFrom("Artist - Song (Live) [320].mp3"))
        assertEquals("Rekaman", shareCaptionFrom("Rekaman"))
    }

    @Test fun shareCaptionIgnoresMissingOrBlankNames() {
        for (name in listOf(null, "", "   ", ".nomedia")) {
            assertNull("Nama tanpa judul tidak boleh jadi caption", shareCaptionFrom(name))
        }
    }

    @Test fun shareCaptionFallsBackToTitleWhenProviderHasNoName() {
        assertEquals("Ipank - Mutiara", shareCaption("Ipank - Mutiara.mp3", "Judul lain"))
        assertEquals("Mr. Brightside", shareCaption(null, "Mr. Brightside"))
        assertEquals("Mr. Brightside", shareCaption("", "Mr. Brightside"))
        assertNull("Tidak ada caption tanpa nama dan judul", shareCaption(null, "  "))
    }

    @Test fun actualLaunchFailureIsPreserved() {
        assertEquals("Pemutar tidak tersedia", launchFile(job(MediaMode.VIDEO, "content://media/42"), false) { _, _, _ ->
            "Pemutar tidak tersedia"
        })
    }
}
