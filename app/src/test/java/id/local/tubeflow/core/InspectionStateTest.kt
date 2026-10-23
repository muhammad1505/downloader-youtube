package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class InspectionStateTest {
    private val video=VideoMetadata("Video","Creator",19,"","https://www.youtube.com/watch?v=jNQXAC9IVRw")
    private val details=FailureDetails.capture("unavailable","INSPECTION","test","test")

    @Test fun clearingFailedInputRemovesItsErrorAndRejectsLateResults() {
        val state=InspectionState()
        state.begin("old")
        state.fail("old","Unavailable",details)
        assertNotNull(state.state.value.details)
        assertEquals("old",state.clear())
        assertNull(state.state.value.details)
        assertNull(state.state.value.error)
        assertFalse(state.state.value.busy)
        assertFalse(state.fail("old","Late failure",details))
        assertFalse(state.video("old",video))
        assertNull(state.state.value.metadata)
    }

    @Test fun previousInspectionCannotOverwriteOrFinishTheNewOne() {
        val state=InspectionState()
        state.begin("old");state.begin("new")
        assertFalse(state.fail("old","Old error",details))
        state.finish("old")
        assertTrue(state.state.value.busy)
        assertTrue(state.video("new",video))
        state.finish("new")
        assertEquals(video,state.state.value.metadata)
        assertNull(state.state.value.error)
        assertFalse(state.state.value.busy)
    }

    @Test fun phaseUpdatesOnlyBelongToCurrentInspectionAndClearOnFinish() {
        val state=InspectionState()
        state.begin("old", InspectionPhase.PREPARING_ENGINE, 100L)
        assertEquals(InspectionPhase.PREPARING_ENGINE,state.state.value.phase)
        assertEquals(100L,state.state.value.startedAt)
        state.begin("new", InspectionPhase.FETCHING_METADATA, 200L)
        assertFalse(state.phase("old", InspectionPhase.READING_ENTRIES))
        assertTrue(state.phase("new", InspectionPhase.READING_ENTRIES))
        assertEquals(InspectionPhase.READING_ENTRIES,state.state.value.phase)
        state.finish("new")
        assertNull(state.state.value.phase)
    }

    @Test fun clearingAfterEnqueueRetainsCompletedVideoCacheButNewInspectionResetsIt() {
        val state=InspectionState()
        state.begin("a");state.video("a",video);state.finish("a")
        state.clear()
        assertEquals(video,state.state.value.metadata)
        state.begin("b")
        assertNull(state.state.value.metadata)
        assertTrue(state.state.value.busy)
    }
}
