package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresi P1 — identifikasi judul/artis terpusat. Prinsip: TIDAK menebak nama artis tanpa bukti
 * (metadata `artist` atau nama channel yang muncul utuh di judul), format tampilan konsisten
 * "JUDUL - ARTIS", dan keterangan versi dipisah tanpa merusak judul.
 */
class TitleSplitTest {
    @Test fun structuredTrackUsesTheSameVersionAndCreditCleanupAsSourceTitle() {
        for (track in listOf("Song (Live)", "Artist - Song (Official Music Live)", "Song (Live) - Artist")) {
            val identity=TitleSplit.identify("Artist - Song (Live)", metadataArtist="Artist", metadataTrack=track)
            assertEquals("Song | Live - Artist", TitleSplit.display(identity))
        }
        assertEquals("Song | Remix - Artist", TitleSplit.display(TitleSplit.identify(
            "Artist - Song | Remix", metadataArtist="Artist", metadataTrack="Song | Remix")))
    }
    @Test fun noEvidenceLeavesArtistUnknown() {
        // Tanpa metadata dan tanpa channel yang muncul di judul → TIDAK menebak artis (P1).
        val id = TitleSplit.identify("SATRU 2")
        assertNull(id.artist)
        assertEquals("SATRU 2", TitleSplit.display(id))
    }

    @Test fun metadataArtistWinsOverChannelGuess() {
        // Metadata `artist` = bukti terkuat; channel yang tidak muncul di judul diabaikan.
        val id = TitleSplit.identify("SATRU 2", channel = "Random Channel", metadataArtist = "Denny Caknan")
        assertEquals("Denny Caknan", id.artist)
        assertEquals("SATRU 2 - Denny Caknan", TitleSplit.display(id))
    }

    @Test fun channelUsedOnlyWhenItAppearsInTitle() {
        // Channel dianggap artis hanya bila muncul utuh di judul — bukan sekadar sama nama channel.
        val withName = TitleSplit.identify("Denny Caknan - SATRU 2", channel = "Denny Caknan")
        assertEquals("Denny Caknan", withName.artist)
        assertEquals("SATRU 2 - Denny Caknan", TitleSplit.display(withName))
        // Channel yang TIDAK muncul di judul → tanpa bukti, artis tetap kosong.
        val noName = TitleSplit.identify("SATRU 2", channel = "Some Uploader")
        assertNull(noName.artist)
    }

    @Test fun topicChannelSuffixStripped() {
        // Channel "- Topic" (YouTube Music) dipakai sebagai artis tanpa embel-embel sufiks.
        val id = TitleSplit.identify("LAMUNAN", channel = "Sasya Arkhisna - Topic")
        assertEquals("Sasya Arkhisna", id.artist)
        assertEquals("LAMUNAN - Sasya Arkhisna", TitleSplit.display(id))
    }

    @Test fun versionAndPromoTagsSplitFromTitle() {
        // "(Official Video)" = promosi (dibuang); "(Live)" = versi (dipertahankan terpisah).
        val id = TitleSplit.identify("SATRU 2 (Official Video) Live", channel = "Denny Caknan")
        assertEquals("SATRU 2 Live", id.title)
        // Kata promosi "Official Video" yang menempel sebagai token dibuang dari judul yang dirapikan.
        val neat = TitleSplit.display(id)
        assertTrue(neat, !neat.contains("Official", ignoreCase = true))
    }

    @Test fun pipeSeparatorKeepsMainTitle() {
        // "JUDUL | KETERANGAN VERSI" → judul utama diambil, versi disimpan terpisah (P1/P3).
        val id = TitleSplit.identify("DJ JAUH KO PERGI | SA CERITAKAN PADA BINTANG", channel = "Maman Fvndy Rmx")
        assertEquals("JAUH KO PERGI", id.title)
        assertTrue(id.versionTag.contains("SA CERITAKAN", ignoreCase = true))
        // Kanal "Maman Fvndy Rmx" TIDAK muncul di judul ini → tidak ada bukti, jangan menebak artis.
        assertNull(id.artist)
        assertTrue(TitleSplit.display(id).startsWith("JAUH KO PERGI | "))
    }

    @Test fun channelEmbeddedInTitleCountsAsEvidence() {
        // Kasus nyata (Mix RD3J1Opi7MVHw): kreator "Maman Fvndy Rmx" menempel di ekor judul tanpa
        // tanda baca ("… FULL SONG MAMAN FVNDY 2026") → tetap terbukti berkat pencocokan token.
        val title = "DJ JAUH KO PERGI | SA CERITAKAN PADA BINTANG BINTANG SLOW VIRAL TIKTOK FULL SONG MAMAN FVNDY 2026"
        val id = TitleSplit.identify(title, channel = "Maman Fvndy Rmx")
        assertNull(id.artist)
        assertEquals("Maman Fvndy Rmx", id.remixer)
        assertEquals("JAUH KO PERGI", id.title)
    }

    @Test fun metadataSeparatesReversedNamesAndKeepsOriginalVersion() {
        for (raw in listOf("DENNY CAKNAN - NEGORO ANGIN", "NEGORO ANGIN - DENNY CAKNAN")) {
            val id = TitleSplit.identify(raw, "DC MUSIK", "DENNY CAKNAN")
            assertEquals("NEGORO ANGIN - DENNY CAKNAN", TitleSplit.display(id))
        }
        val id = TitleSplit.identify("DENNY CAKNAN - PINGAL (NGAWI DANGDUT SAMPEK TUWEK - DC MUSIK", "DC MUSIK", "DENNY CAKNAN")
        assertEquals("PINGAL - DENNY CAKNAN", TitleSplit.display(id))
    }
    @Test fun shortenedChannelAndAmbiguousDashDoNotProveArtist() {
        assertNull(TitleSplit.identify("Denny - Song", "Denny Caknan").artist)
        assertNull(TitleSplit.identify("Song - Unknown Name").artist)
        assertEquals("Song - Unknown Name", TitleSplit.identify("Song - Unknown Name").title)
    }
    @Test fun liveCoverAndRemixIdentityRemainDifferentFromStudio() {
        listOf("Live", "Cover", "Remix", "Slowed").forEach { tag ->
            val id=TitleSplit.identify("Song ($tag)", metadataArtist="Artist")
            assertTrue(id.versionTag.contains(tag))
            assertTrue(TitleSplit.display(id).contains(tag))
        }
    }
    @Test fun displayIsAlwaysTidy() {
        // Judul kosong tidak menghasilkan string kosong — aman untuk nama file.
        assertNotNull(TitleSplit.display(TitleSplit.identify("")))
    }

    @Test fun separatedLabelPromoIsRemovedBeforeEitherOrderIsResolved() {
        for (raw in listOf("DENNY CAKNAN - PINGAL - DC MUSIK", "PINGAL - DENNY CAKNAN - DC MUSIK")) {
            val song=TitleSplit.identify(raw,metadataArtist="DENNY CAKNAN")
            assertEquals("PINGAL",song.title)
            assertEquals("PINGAL - DENNY CAKNAN",Format.musicFileName(song))
        }
    }
    @Test fun originalArtistAndRemixerAreBothRemovedOnlyFromTitleBoundaries() {
        val song=TitleSplit.identify("Original Artist - Song - Creator RMX",metadataArtist="Original Artist")
        assertEquals("Song",song.title)
        assertEquals("Original Artist",song.artist)
        assertEquals("Creator RMX",song.remixer)
    }
}
