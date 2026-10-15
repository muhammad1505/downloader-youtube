package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoPlayQueueTest {
    @Test fun fillsWholeCollectionNotSingleTrack() {
        // Regresi: auto-play dulu hanya memasukkan 1 lagu ke antrean sehingga diam setelah lagu pertama.
        val (tracks, startAt) = AutoPlayQueue.plan(listOf("a", "b", "c"), "b") { it }!!
        assertEquals(listOf("a", "b", "c"), tracks)
        assertEquals(1, startAt)
    }

    @Test fun startsAtLastPlayedTrack() {
        val (_, startAt) = AutoPlayQueue.plan(listOf("a", "b", "c"), "c") { it }!!
        assertEquals(2, startAt)
    }

    @Test fun missingLastTrackFallsBackToCollectionStart() {
        val (tracks, startAt) = AutoPlayQueue.plan(listOf("a", "b"), "dihapus") { it }!!
        assertEquals(listOf("a", "b"), tracks)
        assertEquals(0, startAt)
    }

    @Test fun nullStartBeginsAtFirstTrack() {
        val (_, startAt) = AutoPlayQueue.plan(listOf("a", "b"), null) { it }!!
        assertEquals(0, startAt)
    }

    @Test fun emptyCollectionHasNoPlan() {
        assertNull(AutoPlayQueue.plan(emptyList<String>(), "a") { it })
    }

    @Test fun matchesByIdSelector() {
        data class Item(val uri: String)
        val (_, startAt) = AutoPlayQueue.plan(listOf(Item("x"), Item("y")), "y") { it.uri }!!
        assertEquals(1, startAt)
    }
}
