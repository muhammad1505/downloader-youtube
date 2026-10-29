package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class TagSuggestionTest {
    private fun item(name: String, title: String = "", artist: String = "", folder: String = "Download/TubeFlow") =
        TagSuggestion.Item("uri:$name", name, title, artist, folder)

    @Test fun artistFollowsFolderEvidenceBeforeFilenameOrder() {
        val reading = TagSuggestion.read("SATRU 2 - Denny Caknan.mp3", "Download/TubeFlow/denny-caknan")
        assertEquals("Denny Caknan", reading.artist)
        assertEquals("SATRU 2", reading.title)
        assertEquals("Artis cocok dengan folder", reading.note)
    }

    @Test fun reversedFilenameIsCorrectedWhenFolderMatchesTheFirstSegment() {
        val reading = TagSuggestion.read("Denny Caknan - SATRU 2.mp3", "Download/TubeFlow/denny-caknan")
        assertEquals("Denny Caknan", reading.artist)
        assertEquals("SATRU 2", reading.title)
    }

    @Test fun withoutFolderEvidenceTheTubeFlowOrderIsUsed() {
        val reading = TagSuggestion.read("Judul - Artis.mp3", "File pilihan")
        assertEquals("Artis", reading.artist)
        assertEquals("Judul", reading.title)
        assertEquals("Urutan dibaca Judul - Artis", reading.note)
    }

    @Test fun versionTagIsKeptWhilePromoDecorationsAreDropped() {
        assertEquals("Judul (Live)", TagSuggestion.read("Judul (Live) - Artis.mp3", "").title)
        assertEquals("Judul", TagSuggestion.read("Judul (Official Music Video) - Artis.mp3", "").title)
    }

    @Test fun filenameWithoutArtistNeverInventsOne() {
        val reading = TagSuggestion.read("Judul Saja.mp3", "Download/TubeFlow")
        assertEquals("Judul Saja", reading.title)
        assertEquals("", reading.artist)
        assertFalse(reading.title.contains("-"))
    }

    @Test fun provenArtistIsNeverOverwrittenAndBlankArtistIsFilled() {
        val keeps = TagSuggestion.plan(listOf(item("Judul - Artis.mp3", "Judul", "Artis Terbukti")), fillTitle = true, fillArtist = true)
        assertEquals("Artis Terbukti", keeps.single().artist)
        assertFalse(keeps.single().changed)

        val fills = TagSuggestion.plan(listOf(item("Judul - Artis.mp3", "Judul")), fillTitle = true, fillArtist = true)
        assertEquals("Artis", fills.single().artist)
        assertTrue(fills.single().changed)
    }

    @Test fun titleCanBeLeftUntouchedWhileArtistIsFilled() {
        val plan = TagSuggestion.plan(listOf(item("Judul - Artis.mp3", "Judul Lama")), fillTitle = false, fillArtist = true)
        assertEquals("Judul Lama", plan.single().title)
        assertEquals("Artis", plan.single().artist)
    }
}
