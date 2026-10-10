package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regresi BUG-013 (pencocokan identitas/versi/durasi) dan BUG-014 (status lirik). */
class LyricTest {
    private fun query(title: String, artist: String = "", duration: Long = 0L) = LyricMatch.Query(title, artist, duration)
    private fun candidate(title: String, artist: String = "", duration: Long = -1L, instrumental: Boolean = false) =
        LyricMatch.Candidate(title, artist, duration, instrumental, syncedLyrics = null)

    @Test
    fun exactMetadataIsAccepted() {
        val score = LyricMatch.score(query("Kuwi Teri", "Denny Caknan", 210), candidate("Kuwi Teri", "Denny Caknan", 208))
        assertTrue(score.toString(), score >= LyricMatch.ACCEPT)
    }

    @Test
    fun sameTitleWrongArtistIsRejected() {
        val score = LyricMatch.score(query("Kuwi Teri", "Denny Caknan"), candidate("Kuwi Teri", "Siti Nurhaliza"))
        assertTrue(score.toString(), score < 0.0)
    }

    @Test
    fun differentVersionIsRejected() {
        // Judul identik tetapi versi berbeda: timestamp tidak akan sinkron (BUG-013).
        assertTrue(LyricMatch.score(query("Bohemian Rhapsody", "Queen"), candidate("Bohemian Rhapsody (Live Aid)", "Queen")) < 0.0)
        assertTrue(LyricMatch.score(query("Song (Live)", "Artist", 200), candidate("Song", "Artist", 200)) < 0.0)
        assertTrue(LyricMatch.score(query("Song", "Artist", 200), candidate("Song (Acoustic)", "Artist", 200)) < 0.0)
    }

    @Test
    fun durationMismatchIsRejected() {
        // Durasi meleset jauh = rekaman/versi berbeda (toleransi 12 dtk atau 10% — BUG-013/R3).
        assertTrue(LyricMatch.score(query("Song", "Artist", 180), candidate("Song", "Artist", 240)) < 0.0)
        assertTrue(LyricMatch.score(query("Song", "Artist", 300), candidate("Song", "Artist", 200)) < 0.0)
        // Probe R3: 200 vs 229 detik dulu diterima dengan skor 1.0 — sekarang wajib ditolak.
        assertTrue(LyricMatch.score(query("Song", "Artist", 200), candidate("Song", "Artist", 229)) < 0.0)
        // Selisih wajar (intro/outro video) tetap diterima (batas 10% dari 200 = 20 dtk).
        val close = LyricMatch.score(query("Song", "Artist", 200), candidate("Song", "Artist", 210))
        assertTrue(close.toString(), close >= LyricMatch.ACCEPT)
    }

    @Test
    fun unverifiedIdentityRequiresExactTitle() {
        // Query tanpa artis DAN durasi: tak ada yang bisa diverifikasi — hanya judul persis yang lolos,
        // judul mirip otomatis ditolak (BUG-013/R3; kasus lain butuh pemilihan kandidat OPT-003).
        assertTrue(LyricMatch.score(query("Song", "", 0), candidate("Song 2019", "", 0)) < 0.0)
        val exact = LyricMatch.score(query("Song", "", 0), candidate("Song", "Artist", 200))
        assertTrue(exact.toString(), exact >= LyricMatch.ACCEPT)
    }

    @Test
    fun videoJunkMarkersDoNotBlockMatch() {
        val score = LyricMatch.score(query("Song (Official Video)", "Artist"), candidate("Song", "Artist"))
        assertTrue(score.toString(), score >= LyricMatch.ACCEPT)
        assertTrue(LyricMatch.score(query("Song - Official Lyric Video", "Artist"), candidate("Song", "Artist")) >= LyricMatch.ACCEPT)
    }

    @Test
    fun blankArtistDoesNotGrantFullCredit() {
        // Artis kosong: tidak ada yang membantah, TAPI versi dan durasi tetap harus aman —
        // dulu skor artis memakai skor judul sehingga semua kandidat "cocok penuh".
        assertTrue(LyricMatch.score(query("Song (Live)", "", 200), candidate("Song", "", 200)) < 0.0)
        assertTrue(LyricMatch.score(query("Song", "", 180), candidate("Song", "", 300)) < 0.0)
        val accepted = LyricMatch.score(query("Song", "", 180), candidate("Song", "", 181))
        assertTrue(accepted.toString(), accepted >= LyricMatch.ACCEPT)
    }

    @Test
    fun minorTitleVariantsStillAccepted() {
        val score = LyricMatch.score(query("Song feat. Someone", "Artist"), candidate("Song", "Artist"))
        assertTrue(score.toString(), score >= LyricMatch.ACCEPT)
    }

    @Test
    fun unrelatedTitleIsRejected() {
        assertTrue(LyricMatch.score(query("Kuwi Teri", "Denny Caknan"), candidate("Gangnam Style", "PSY")) < 0.0)
    }

    @Test
    fun lyricStatusReportingAndRetryRules() {
        assertEquals("Lirik tersimpan", LyricStatus.label(LyricStatus.SAVED))
        assertEquals("Lirik tidak ditemukan", LyricStatus.label(LyricStatus.NOT_FOUND))
        assertEquals("Lagu instrumental — tanpa lirik", LyricStatus.label(LyricStatus.INSTRUMENTAL))
        assertTrue(LyricStatus.retryable(LyricStatus.NETWORK))
        assertTrue(LyricStatus.retryable(LyricStatus.SAVE_FAILED))
        assertTrue(LyricStatus.retryable(LyricStatus.NOT_FOUND))
        assertFalse(LyricStatus.retryable(LyricStatus.SAVED))
        assertFalse(LyricStatus.retryable(LyricStatus.INSTRUMENTAL))
    }
}
