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

    @Test fun actualLaunchFailureIsPreserved() {
        assertEquals("Pemutar tidak tersedia", launchFile(job(MediaMode.VIDEO, "content://media/42"), false) { _, _, _ ->
            "Pemutar tidak tersedia"
        })
    }
}
