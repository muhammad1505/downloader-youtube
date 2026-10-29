package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class RenamePlanTest {
    private fun item(name: String, title: String = "Judul", artist: String = "Artis", album: String = "", track: String = "") =
        RenamePlan.Item("uri:$name", name, title, artist, album, track)

    @Test fun patternTokensExpandFromCatalogValues() {
        val result = RenamePlan.expand("{artis} - {judul}", item("lama.mp3", "SATRU 2", "Denny Caknan"))
        assertEquals("Denny Caknan - SATRU 2", result)
        assertEquals("Lagu Lama", RenamePlan.expand("{nama}", item("Lagu Lama.mp3")))
        assertEquals("1 Judul", RenamePlan.expand("{track} {judul}", item("x.mp3", "Judul", track = "1")))
    }

    @Test fun blankArtistDoesNotLeaveDanglingSeparator() {
        assertEquals("Judul", RenamePlan.expand("{artis} - {judul}", item("x.mp3", "Judul", artist = "")))
        assertEquals("Judul", RenamePlan.expand("{judul} - {artis}", item("x.mp3", "Judul", artist = "")))
    }

    @Test fun unknownTokensAreDroppedWithTheirEmptyBrackets() {
        assertEquals("Judul", RenamePlan.expand("{judul} ({bitrate})", item("x.mp3")))
    }

    @Test fun forbiddenCharactersAreReplacedInsteadOfFailingTheProvider() {
        assertEquals("AC DC Judul", RenamePlan.expand("AC/DC: {judul}", item("x.mp3")))
        assertEquals("Judul", RenamePlan.expand("  ..{judul}..  ", item("x.mp3")))
    }

    @Test fun planKeepsExtensionAndSkipsUnchangedNames() {
        val plan = RenamePlan.plan(listOf(item("Judul - Artis.mp3", "Judul", "Artis"), item("Lain.mp3", "Lain", "Artis")), "{judul} - {artis}")
        assertEquals("Judul - Artis.mp3", plan.proposals[0].after)
        assertFalse(plan.proposals[0].applies)
        assertEquals(1, plan.changes)
        assertEquals(1, plan.kept)
        assertEquals("Lain - Artis.mp3", plan.proposals[1].after)
    }

    @Test fun planRejectsDuplicateTargetsInsteadOfOverwritingSiblings() {
        val plan = RenamePlan.plan(listOf(item("a.mp3", "Judul"), item("b.mp3", "Judul")), "{judul}")
        assertTrue(plan.proposals[0].applies)
        assertFalse(plan.proposals[1].applies)
        assertEquals("Nama bentrok dengan item lain", plan.proposals[1].skip)
    }

    @Test fun findAndReplaceModeWorksOnTheOriginalName() {
        val plan = RenamePlan.plan(listOf(item("Lagu Lama.mp3", "Lagu Lama")), pattern = "", find = "Lama", replace = "Baru")
        assertEquals("Lagu Baru.mp3", plan.proposals.single().after)
    }

    @Test fun emptyResultAndOverlongNamesAreHandledExplicitly() {
        val empty = RenamePlan.plan(listOf(item("a.mp3", "Judul", artist = "")), "{artis}")
        assertFalse(empty.proposals.single().applies)
        assertEquals("Pola tidak menghasilkan nama", empty.proposals.single().skip)

        val longName = RenamePlan.plan(listOf(item("a.mp3", "j".repeat(400))), "{judul}")
        val after = longName.proposals.single().after
        assertTrue(after.endsWith(".mp3"))
        assertTrue(after.toByteArray(Charsets.UTF_8).size <= RenamePlan.MAX_NAME_BYTES)
        assertFalse(after.removeSuffix(".mp3").endsWith("."))
    }

    @Test fun namesWithoutExtensionAreNotSplitOnDotsInsideWords() {
        assertEquals("v1.2 song", RenamePlan.stem("v1.2 song"))
        assertEquals("Lagu", RenamePlan.stem("Lagu"))
        assertEquals(".mp3", RenamePlan.extension("Lagu.mp3"))
        assertEquals("", RenamePlan.extension("Lagu"))
    }
}
