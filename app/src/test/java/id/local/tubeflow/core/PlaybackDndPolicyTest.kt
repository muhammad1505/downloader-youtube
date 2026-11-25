package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackDndPolicyTest {
    @Test
    fun restoreFallsBackToAllForUnknownFilter() {
        assertEquals(DndPolicy.FILTER_ALL, DndPolicy.restore(DndPolicy.FILTER_UNKNOWN))
        assertEquals(DndPolicy.FILTER_ALL, DndPolicy.restore(99))
    }

    @Test
    fun restoreKeepsUserChosenDndFilter() {
        assertEquals(DndPolicy.FILTER_NONE, DndPolicy.restore(DndPolicy.FILTER_NONE))
        assertEquals(DndPolicy.FILTER_PRIORITY, DndPolicy.restore(DndPolicy.FILTER_PRIORITY))
        assertEquals(DndPolicy.FILTER_ALARMS, DndPolicy.restore(DndPolicy.FILTER_ALARMS))
    }

    @Test
    fun applyOnlyWhenFilterActuallyChanges() {
        assertTrue(DndPolicy.shouldApply(DndPolicy.FILTER_ALL, DndPolicy.FILTER_ALARMS))
        assertFalse(DndPolicy.shouldApply(DndPolicy.FILTER_ALARMS, DndPolicy.FILTER_ALARMS))
    }

    @Test
    fun unknownFilterIsNeverOverwritten() {
        assertFalse(DndPolicy.shouldApply(DndPolicy.FILTER_UNKNOWN, DndPolicy.FILTER_ALARMS))
    }

    @Test
    fun releaseOnlyRestoresFilterStillOwnedByPlayback() {
        assertTrue(DndPolicy.shouldRestore(DndPolicy.FILTER_ALARMS, DndPolicy.FILTER_ALARMS))
        assertFalse(DndPolicy.shouldRestore(DndPolicy.FILTER_ALL, DndPolicy.FILTER_ALARMS))
        assertFalse(DndPolicy.shouldRestore(DndPolicy.FILTER_NONE, DndPolicy.FILTER_ALARMS))
    }
}
