package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolsNoticeTest {

    @Test
    fun waitsForFirstDatabaseEmissionInsteadOfClaimingEmpty() {
        assertEquals(ToolsNotice.NONE, ToolAvailability.notice(null, null, scanning = false))
        assertEquals(ToolsNotice.NONE, ToolAvailability.notice(null, 7L, scanning = false))
        assertEquals(ToolsNotice.NONE, ToolAvailability.notice(0, null, scanning = false))
    }

    @Test
    fun staysQuietWhileCatalogScanIsRunning() {
        assertEquals(ToolsNotice.NONE, ToolAvailability.notice(0, 0L, scanning = true))
    }

    @Test
    fun audioAvailableKeepsTheToolsScreenClean() {
        assertEquals(ToolsNotice.NONE, ToolAvailability.notice(3, 10L, scanning = false))
    }

    @Test
    fun videoOnlyCatalogIsReportedAsMissingAudioNotAsEmptyCatalog() {
        assertEquals(ToolsNotice.NO_AUDIO, ToolAvailability.notice(0, 4L, scanning = false))
    }

    @Test
    fun unavailableAudioAlsoReportsMissingAudio() {
        // Audio yang hilang/tidak terbaca tetap terhitung di katalog, jadi bukan katalog kosong.
        assertEquals(ToolsNotice.NO_AUDIO, ToolAvailability.notice(0, 5L, scanning = false))
    }

    @Test
    fun trulyEmptyCatalogIsReportedOnlyAfterDatabaseAnswers() {
        assertEquals(ToolsNotice.EMPTY_CATALOG, ToolAvailability.notice(0, 0L, scanning = false))
    }
}
