package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class VideoViewportTest {
    @Test fun fitLetterboxesFillCropsAndZoomCropsFurtherInPortrait() {
        assertEquals(360f to 202.5f, VideoViewport.size(360f, 800f, 1920, 1080, VideoAspect.FIT))

        val fill = VideoViewport.size(360f, 800f, 1920, 1080, VideoAspect.FILL)
        assertEquals(800f, fill.second, .01f)
        assertEquals(16f / 9f, fill.first / fill.second, .001f)
        assertTrue("FILL harus lebih lebar dari layar agar crop tanpa distorsi", fill.first > 360f)

        val zoom = VideoViewport.size(360f, 800f, 1920, 1080, VideoAspect.ZOOM)
        assertEquals(800f * VideoViewport.ZOOM_FACTOR, zoom.second, .01f)
        assertTrue(zoom.first > fill.first)
    }
    @Test fun rotationRecomputesGeometryAndHandlesPortraitVideo() {
        val fit = VideoViewport.size(800f, 360f, 1080, 1920, VideoAspect.FIT)
        assertEquals(202.5f to 360f, fit)
        val fill = VideoViewport.size(800f, 360f, 1080, 1920, VideoAspect.FILL)
        assertEquals(800f, fill.first, .01f)
        assertEquals(1080f / 1920f, fill.first / fill.second, .001f)
        assertEquals(800f to 360f, VideoViewport.size(800f, 360f, 0, 0, VideoAspect.ZOOM))
    }
    @Test fun decodedFormatProvidesGeometryEvenWhenGlDoesNotReportVideoSize() {
        assertEquals(Triple(1920, 1080, 1f), VideoViewport.sourceSize(1920, 1080, 0, 1f))
        assertEquals(Triple(1080, 1920, .5f), VideoViewport.sourceSize(1920, 1080, 90, 2f))
        assertEquals(Triple(1080, 1920, 1f), VideoViewport.sourceSize(1920, 1080, -90, Float.NaN))
        assertNull(VideoViewport.sourceSize(-1, 1080, 0, 1f))
        // Fallback VideoSize harus memakai rotation yang belum diterapkan, bukan sumbu mentah.
        assertEquals(Triple(1080, 1920, 1f), VideoViewport.sourceSize(1920, 1080, 90, 1f))
    }

    @Test fun pixelAspectIsAppliedAndInvalidValuesAreSafe() {
        val nonSquare = VideoViewport.size(400f, 400f, 720, 540, VideoAspect.FIT, 2f)
        assertEquals(400f, nonSquare.first, .001f)
        assertEquals(150f, nonSquare.second, .001f)
        assertEquals(VideoViewport.size(400f, 400f, 720, 540, VideoAspect.FIT),
            VideoViewport.size(400f, 400f, 720, 540, VideoAspect.FIT, Float.NaN))
    }

    /**
     * Mode Pas tidak boleh sekali pun menghasilkan permukaan lebih besar dari wadah. Kalau invariant
     * ini bocor, `requiredSize` di layar pemutar memotong tepi video — termasuk hard sub yang
     * menempel di pinggir frame.
     */
    @Test fun fitNeverExceedsTheViewportForAnySourceGeometry() {
        val sources = listOf(
            Triple(1920, 1080, 1f),   // 16:9 biasa
            Triple(1080, 1920, 1f),   // video potret
            Triple(3840, 1600, 1f),   // ultrawide
            Triple(720, 576, 1.5f),   // PAR non-persegi
            Triple(1440, 1080, 0.5f), // PAR < 1
            Triple(1920, 1088, 1f),   // tinggi berpadding, bukan 1080
            Triple(640, 480, 1f),     // 4:3
        )
        for ((videoWidth, videoHeight, pixelRatio) in sources) {
            for ((containerWidth, containerHeight) in listOf(360f to 800f, 800f to 360f, 411f to 731f)) {
                val (surfaceWidth, surfaceHeight) = VideoViewport.size(
                    containerWidth, containerHeight, videoWidth, videoHeight, VideoAspect.FIT, pixelRatio,
                )
                val label = "video=${videoWidth}x$videoHeight par=$pixelRatio wadah=${containerWidth}x$containerHeight"
                assertTrue("lebar FIT melebihi wadah: $label", surfaceWidth <= containerWidth + 0.01f)
                assertTrue("tinggi FIT melebihi wadah: $label", surfaceHeight <= containerHeight + 0.01f)
                assertTrue("sisi FIT harus positif: $label", surfaceWidth > 0f && surfaceHeight > 0f)
            }
        }
    }

    @Test fun fillAlwaysCoversAndZoomStaysLargerThanFill() {
        val containers = listOf(360f to 800f, 800f to 360f)
        for ((containerWidth, containerHeight) in containers) {
            val fill = VideoViewport.size(containerWidth, containerHeight, 1920, 1080, VideoAspect.FILL)
            assertTrue("FILL harus menutup lebar wadah", fill.first >= containerWidth - 0.01f)
            assertTrue("FILL harus menutup tinggi wadah", fill.second >= containerHeight - 0.01f)
            assertEquals(16f / 9f, fill.first / fill.second, .001f)

            val zoom = VideoViewport.size(containerWidth, containerHeight, 1920, 1080, VideoAspect.ZOOM)
            assertTrue("ZOOM harus lebih besar dari FILL", zoom.second > fill.second)
        }
    }
}
