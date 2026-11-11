package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class VideoViewportTest {
    @Test fun fitLetterboxesWhileStretchFillsAndCropCoversInPortrait() {
        assertEquals(360f to 202.5f, VideoViewport.size(360f, 800f, 1920, 1080, VideoAspect.FIT))

        // Rentangkan mengisi wadah; layer FIT diskalakan pada sumbu yang kurang.
        assertEquals(360f to 800f, VideoViewport.size(360f, 800f, 1920, 1080, VideoAspect.STRETCH))
        assertEquals(1f to (800f / 202.5f), VideoViewport.modeScale(360f, 800f, 1920, 1080, VideoAspect.STRETCH))

        val crop = VideoViewport.size(360f, 800f, 1920, 1080, VideoAspect.CROP)
        assertEquals(800f, crop.second, .01f)
        assertEquals(16f / 9f, crop.first / crop.second, .001f)
        assertTrue("Potong harus lebih lebar dari layar agar crop tanpa distorsi", crop.first > 360f)
    }

    @Test fun rotationRecomputesGeometryAndHandlesPortraitVideo() {
        val fit = VideoViewport.size(800f, 360f, 1080, 1920, VideoAspect.FIT)
        assertEquals(202.5f to 360f, fit)
        val crop = VideoViewport.size(800f, 360f, 1080, 1920, VideoAspect.CROP)
        assertEquals(800f, crop.first, .01f)
        assertEquals(1080f / 1920f, crop.first / crop.second, .001f)
        assertEquals(800f to 360f, VideoViewport.size(800f, 360f, 0, 0, VideoAspect.CROP))
        assertEquals(800f to 360f, VideoViewport.size(800f, 360f, 0, 0, VideoAspect.STRETCH))
        assertEquals(800f to 360f, VideoViewport.size(800f, 360f, 0, 0, VideoAspect.PERCENT_100))
        assertEquals(1f to 1f, VideoViewport.modeScale(800f, 360f, 0, 0, VideoAspect.STRETCH))
    }

    @Test fun percentHundredMapsSourcePixelsOneToOne() {
        // Sumber 1920x1080 pada layar density 2 berarti 960x540 dp = 1920x1080 px.
        assertEquals(960f to 540f, VideoViewport.size(400f, 800f, 1920, 1080, VideoAspect.PERCENT_100, 1f, 2f))
        // PAR non-persegi ikut memengaruhi lebar piksel efektif.
        val nonSquare = VideoViewport.size(400f, 800f, 720, 540, VideoAspect.PERCENT_100, 2f, 1f)
        assertEquals(1440f to 540f, nonSquare)
        // Density tidak valid kembali ke pemetaan 1:1 dp.
        assertEquals(400f to 300f, VideoViewport.size(400f, 800f, 400, 300, VideoAspect.PERCENT_100, 1f, Float.NaN))
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
     * ini bocor, layer video akan terpotong — termasuk hard sub yang menempel di pinggir frame.
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

    @Test fun modeScaleMapsTheFitSurfaceToEachModesTarget() {
        val containers = listOf(360f to 800f, 800f to 360f, 1520f to 720f)
        val sources = listOf(1920 to 1080, 1080 to 1920, 3840 to 1600, 640 to 480)
        for ((containerWidth, containerHeight) in containers) {
            for ((videoWidth, videoHeight) in sources) {
                val (fitWidth, fitHeight) = VideoViewport.size(
                    containerWidth, containerHeight, videoWidth, videoHeight, VideoAspect.FIT,
                )
                val (scaleX, scaleY) = VideoViewport.modeScale(
                    containerWidth, containerHeight, videoWidth, videoHeight, VideoAspect.STRETCH,
                )
                assertEquals("lebar Rentangkan harus pas", containerWidth, fitWidth * scaleX, 0.01f)
                assertEquals("tinggi Rentangkan harus pas", containerHeight, fitHeight * scaleY, 0.01f)
                assertTrue("skala tidak boleh negatif", scaleX > 0f && scaleY > 0f)
                assertEquals("hanya satu sumbu yang berubah",
                    true, scaleX == 1f || scaleY == 1f,
                )
            }
        }
        // Pas ke layar tidak mengubah apa pun.
        assertEquals(1f to 1f, VideoViewport.modeScale(1520f, 720f, 848, 480, VideoAspect.FIT))
        // Potong memperbesar layer FIT sampai menutup wadah; 16:9 di 1520x720 butuh sekitar 1,195x.
        val (fitW, fitH) = VideoViewport.size(1520f, 720f, 848, 480, VideoAspect.FIT)
        val (cropW, cropH) = VideoViewport.size(1520f, 720f, 848, 480, VideoAspect.CROP)
        val (cropScaleX, cropScaleY) = VideoViewport.modeScale(1520f, 720f, 848, 480, VideoAspect.CROP)
        assertEquals(cropW / fitW, cropScaleX, 0.0001f)
        assertEquals(cropH / fitH, cropScaleY, 0.0001f)
        assertTrue("Potong harus memperbesar", cropScaleX > 1f && cropScaleY > 1f)
        // 100% mengecilkan layer FIT sehingga 848x480 px sumber tampil 1:1 di layar density 1,7.
        val (percentX, percentY) = VideoViewport.modeScale(
            894f, 423f, 848, 480, VideoAspect.PERCENT_100, 1f, 1.7f,
        )
        assertTrue("100% harus mengecil di layar ini", percentX < 1f && percentY < 1f)
        assertEquals("skala 100% harus seragam", percentX, percentY, 0.002f)
    }

    @Test fun cropAlwaysCoversAndPreservesSourceAspect() {
        val containers = listOf(360f to 800f, 800f to 360f)
        for ((containerWidth, containerHeight) in containers) {
            val crop = VideoViewport.size(containerWidth, containerHeight, 1920, 1080, VideoAspect.CROP)
            assertTrue("Potong harus menutup lebar wadah", crop.first >= containerWidth - 0.01f)
            assertTrue("Potong harus menutup tinggi wadah", crop.second >= containerHeight - 0.01f)
            assertEquals(16f / 9f, crop.first / crop.second, .001f)
        }
    }

    /**
     * Potong tidak memakai efek GL dan tidak menempel ke bawah; seluruh crop diurus skala layer
     * yang dipusatkan. Target harus tetap rasio sumber dan hanya boleh melebihi wadah pada sumbu
     * yang dipotong.
     */
    @Test fun cropSurfacesKeepSourceAspectForLandscapeAndPortrait() {
        val sources = listOf(1920 to 1080, 1080 to 1920, 3840 to 1600, 1920 to 1088)
        for ((videoWidth, videoHeight) in sources) {
            val label = "video=${videoWidth}x$videoHeight"
            val crop = VideoViewport.size(1520f, 720f, videoWidth, videoHeight, VideoAspect.CROP)
            assertEquals("$label Potong merusak rasio", videoWidth.toFloat() / videoHeight, crop.first / crop.second, .001f)
            assertTrue("$label Potong tidak menutup lebar", crop.first >= 1520f - .01f)
            assertTrue("$label Potong tidak menutup tinggi", crop.second >= 720f - .01f)
        }
    }
}
