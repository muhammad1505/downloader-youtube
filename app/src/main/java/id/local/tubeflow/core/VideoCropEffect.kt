package id.local.tubeflow.core

import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlMatrixTransformation

/**
 * Efek potong-isi yang menjaga tepi bawah frame.
 *
 * Media3 `Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP` memotong dari tengah. Pada layar 20:9,
 * video 16:9 dengan hardsub di 1–3% terbawah kehilangan baris teks. Efek ini memakai skala
 * cover yang sama, tetapi menggeser area yang terlihat ke bawah sehingga baris terakhir tetap
 * tampil. Efek tetap menghasilkan frame seukuran viewport, jadi permukaan tidak perlu diperbesar.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal class VideoCropEffect(
    private val outputWidth: Int,
    private val outputHeight: Int,
    private val zoom: Float = 1f,
) : GlMatrixTransformation {

    @Volatile
    private var matrix: FloatArray = IDENTITY

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        matrix = VideoViewport.cropTransform(
            inputWidth = inputWidth,
            inputHeight = inputHeight,
            outputWidth = outputWidth,
            outputHeight = outputHeight,
            zoom = zoom,
        ).toGlMatrix()
        return Size(outputWidth, outputHeight)
    }

    override fun getGlMatrixArray(presentationTimeUs: Long): FloatArray = matrix

    private companion object {
        val IDENTITY = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )
    }
}
