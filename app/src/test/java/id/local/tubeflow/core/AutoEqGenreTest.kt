package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEqGenreTest {
    @Test
    fun genreKeywordsPickMatchingProfile() {
        assertEquals("Rock", AutoEq.profileFor("Alternative Rock")?.label)
        assertEquals("Bass", AutoEq.profileFor("Hip-Hop/Rap")?.label)
        assertEquals("Elektronik", AutoEq.profileFor("EDM")?.label)
        assertEquals("Vokal", AutoEq.profileFor("Vocal Ballad")?.label)
    }

    @Test
    fun unknownGenreFallsBackToAutomaticCurve() {
        assertNull(AutoEq.profileFor(""))
        assertNull(AutoEq.profileFor("Polka"))
        assertEquals(AutoEq.autoCurve(), AutoEq.curveFor("Polka"))
    }

    @Test
    fun bassProfileLiftsLowFrequencyMoreThanMid() {
        val bass = AutoEq.levelDb(60_000, -12f, 12f, AutoEq.curveFor("rap"))
        val mid = AutoEq.levelDb(1_000_000, -12f, 12f, AutoEq.curveFor("rap"))
        assertTrue("bass $bass should exceed mid $mid", bass > mid + 3f)
    }
}
