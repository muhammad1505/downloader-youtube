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
