package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class DownloadProgressTest {
    @Test fun downloadPercentUsesBytesAndActualTransferRate() {
        val update = DownloadProgress.parse("TUBEFLOW_DOWNLOAD|251|downloading|250|1000|NA|1048576|3|none")!!
        assertEquals(DownloadPhase.AUDIO, update.phase)
        assertEquals(25f, update.percent, 0f)
        assertEquals("1.00MiB/s", Format.speed(update.speed))
        assertEquals("3s", update.eta)
    }

    @Test fun unknownSizeIsIndeterminateAndEstimateCanBeRevised() {
        assertEquals(-1f, DownloadProgress.parse("TUBEFLOW_DOWNLOAD|137|downloading|250|NA|NA|NA|NA|avc1")!!.percent, 0f)
        assertEquals(50f, DownloadProgress.parse("TUBEFLOW_DOWNLOAD|137|downloading|250|NA|500|NA|NA|avc1")!!.percent, 0f)
        assertEquals(25f, DownloadProgress.parse("TUBEFLOW_DOWNLOAD|137|downloading|250|NA|1000|NA|NA|avc1")!!.percent, 0f)
    }

    @Test fun finishedTrackHasNoStaleSpeedOrEta() {
        val update = DownloadProgress.parse("TUBEFLOW_DOWNLOAD|137|finished|1000|NA|NA|1048576|9|avc1")!!
        assertEquals(100f, update.percent, 0f)
        assertEquals("", update.speed)
        assertEquals("", update.eta)
    }

    @Test fun processingHasNoInventedPercentageOrTransferMetrics() {
        val update = DownloadProgress.parse("TUBEFLOW_PROCESS|started|ExtractAudio")!!
        assertEquals(DownloadPhase.PROCESSING, update.phase)
        assertEquals(-1f, update.percent, 0f)
        assertEquals("", update.speed)
        assertEquals("", update.eta)
        assertEquals(DownloadPhase.COVER, DownloadProgress.parse("TUBEFLOW_PROCESS|started|ThumbnailsConvertor")!!.phase)
    }

    @Test fun separateTracksStartAtTheirActualPercent() {
        val video = DownloadProgress.parse("TUBEFLOW_DOWNLOAD|137|finished|1000|1000|NA|NA|NA|avc1")!!
        val audio = DownloadProgress.parse("TUBEFLOW_DOWNLOAD|140|downloading|10|1000|NA|NA|NA|none")!!
        assertEquals(DownloadPhase.VIDEO, video.phase)
        assertEquals(DownloadPhase.AUDIO, audio.phase)
        assertNotEquals(video.track, audio.track)
        assertEquals(1f, audio.percent, 0f)
    }

    @Test fun unrelatedLogsAndInvalidNumbersNeverBecomeProgress() {
        assertNull(DownloadProgress.parse("[ffmpeg] size=1000 speed=3x"))
        assertNull(DownloadProgress.parse("[download] 90.0% at 1MiB/s ETA 00:10"))
        assertNull(DownloadProgress.parse("TUBEFLOW_DOWNLOAD|broken"))
        val invalid = DownloadProgress.parse("TUBEFLOW_DOWNLOAD|140|downloading|NaN|Infinity|NA|NaN|Infinity|none")!!
        assertEquals(-1f, invalid.percent, 0f)
        assertEquals("", invalid.speed)
        assertEquals("", invalid.eta)
    }
}
