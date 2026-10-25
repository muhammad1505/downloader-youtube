package id.local.tubeflow.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrubberGeometryTest {

    private val radius = 5f
    private val duration = 60_000f

    @Test
    fun tapAtTrackEndsMapsToStartAndEndOfDuration() {
        assertEquals(0f, scrubberPositionAt(radius, 300f, radius, duration), 0.01f)
        assertEquals(duration, scrubberPositionAt(300f - radius, 300f, radius, duration), 0.01f)
    }

    @Test
    fun sameTapUsesCurrentWidthAfterResize() {
        // Satu titik yang sama pada bar yang lebih lebar berarti posisi yang lebih awal.
        val portrait = scrubberPositionAt(200f, 300f, radius, duration)
        val landscape = scrubberPositionAt(200f, 600f, radius, duration)

        assertEquals(40_344.83f, portrait, 0.5f)
        assertEquals(19_830.51f, landscape, 0.5f)
        assertTrue("Lebar baru harus menghasilkan posisi lebih awal", landscape < portrait)
    }

    @Test
    fun middleOfEachWidthMapsToHalfDuration() {
        assertEquals(duration / 2f, scrubberPositionAt(150f, 300f, radius, duration), 0.01f)
        assertEquals(duration / 2f, scrubberPositionAt(300f, 600f, radius, duration), 0.01f)
    }

    @Test
    fun touchesOutsideTrackAreClamped() {
        assertEquals(0f, scrubberPositionAt(-100f, 300f, radius, duration), 0.01f)
        assertEquals(duration, scrubberPositionAt(1_000f, 300f, radius, duration), 0.01f)
    }

    @Test
    fun degenerateSizeAndDurationStayFinite() {
        listOf(0f, 8f).forEach { width ->
            val position = scrubberPositionAt(4f, width, radius, duration)
            assertTrue("Posisi $position untuk lebar $width", position.isFinite())
            assertTrue(position in 0f..duration)
        }
        assertEquals(0f, scrubberPositionAt(150f, 300f, radius, 0f), 0f)
        assertEquals(0f, scrubberPositionAt(150f, 300f, radius, -1f), 0f)
    }
}
