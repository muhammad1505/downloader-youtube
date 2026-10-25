package id.local.tubeflow.core

import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.ConvolutionFunction1D
import androidx.media3.effect.SeparableConvolution

/**
 * Unsharp-mask sederhana dengan kernel 3-tap yang dijalankan Media3 sebagai separable convolution.
 * Bobot: pusat `1 + 2a`, tetangga `-a`, sehingga total bobot tetap 1 (tidak mengubah kecerahan).
 * Nilai kecil dipakai supaya tepi tidak bercahaya berlebihan pada video resolusi rendah.
 */
@UnstableApi
class VideoSharpenEffect(amount: Float = 0.08f) : SeparableConvolution() {
    private val strength = amount.coerceIn(0f, 0.20f)
    private val kernel: ConvolutionFunction1D = object : ConvolutionFunction1D {
        override fun domainStart(): Float = -1.5f
        override fun domainEnd(): Float = 1.5f
        override fun value(samplePosition: Float): Float = when {
            samplePosition > -0.5f && samplePosition < 0.5f -> 1f + 2f * strength
            samplePosition > -1.5f && samplePosition < 1.5f -> -strength
            else -> 0f
        }
    }

    override fun getConvolution(presentationTimeUs: Long): ConvolutionFunction1D = kernel
}
