package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCropEffectTest {

    @Test
    fun configureReturnsViewportAndPinsTheBottomEdge() {
        val effect = VideoCropEffect(1520, 720)
        val size = effect.configure(848, 480)

        assertEquals(1520, size.width)
        assertEquals(720, size.height)

        val matrix = effect.getGlMatrixArray(0)
        assertEquals(1f, matrix[0], .001f)
        assertEquals((1520f / 720f) / (848f / 480f), matrix[5], .001f)
        assertEquals(0f, matrix[12], .001f)
        // Tepi bawah sumber (-1 di NDC) tetap berada di tepi bawah layar.
        assertEquals(-1f, matrix[5] * -1f + matrix[13], .001f)
    }

    @Test
    fun zoomOutputStaysViewportSizedWhileCroppingMore() {
        val fill = VideoCropEffect(720, 1520)
        fill.configure(848, 480)
        val zoom = VideoCropEffect(720, 1520, VideoViewport.ZOOM_FACTOR)
        val size = zoom.configure(848, 480)

        assertEquals(720, size.width)
        assertEquals(1520, size.height)
        val fillMatrix = fill.getGlMatrixArray(0)
        val zoomMatrix = zoom.getGlMatrixArray(0)
        assertTrue(zoomMatrix[5] > fillMatrix[5])
        assertEquals(-1f, zoomMatrix[5] * -1f + zoomMatrix[13], .001f)
    }
}
