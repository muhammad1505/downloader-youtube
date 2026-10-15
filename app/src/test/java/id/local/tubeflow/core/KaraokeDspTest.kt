package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class KaraokeDspTest {
    @Test fun mixedRadixMatchesDirectDftForComplexStereoComponents() {
        for (size in listOf(8, 15, 24)) {
            val re = FloatArray(size) { sin(it * 0.37).toFloat() }
            val im = FloatArray(size) { cos(it * 0.17).toFloat() }
            val originalRe = re.copyOf(); val originalIm = im.copyOf()
            MixedRadixFft(size).transform(re, im)
            for (k in 0 until size) {
                var expectedRe = 0.0; var expectedIm = 0.0
                for (j in 0 until size) {
                    val c = cos(2 * PI * j * k / size); val s = -sin(2 * PI * j * k / size)
                    expectedRe += originalRe[j] * c - originalIm[j] * s
                    expectedIm += originalRe[j] * s + originalIm[j] * c
                }
                assertEquals(expectedRe.toFloat(), re[k], 0.00001f)
                assertEquals(expectedIm.toFloat(), im[k], 0.00001f)
            }
        }
    }
    @Test fun modelFftSizeRoundTripsWithoutChangingAmplitudeOrPhase() {
        val re = FloatArray(6144) { (sin(it * 0.21) * 0.6 + cos(it * 0.07) * 0.2).toFloat() }
        val original = re.copyOf(); val im = FloatArray(re.size); val fft = MixedRadixFft(re.size)
        fft.transform(re, im); fft.transform(re, im, true)
        assertTrue(re.indices.maxOf { abs(re[it] - original[it]) } < 0.000002f)
        assertTrue(im.maxOf { abs(it) } < 0.000002f)
    }
    @Test fun stftUsesSeparateChannelsAndPreservesLengthAndTiming() {
        val dsp = KaraokeDsp()
        val wave = Array(2) { channel -> FloatArray(dsp.samples) { sin(2 * PI * (if (channel == 0) 37 else 61) * it / dsp.fftSize).toFloat() * 0.3f } }
        val tensor = dsp.forward(wave, false)
        for (channel in 0..1) {
            val peak = (0 until dsp.bins).maxBy { bin ->
                val re = tensor[((channel * 2) * dsp.bins + bin) * dsp.frames + 32]
                val im = tensor[((channel * 2 + 1) * dsp.bins + bin) * dsp.frames + 32]
                re * re + im * im
            }
            assertEquals(if (channel == 0) 37 else 61, peak)
        }
        val restored = dsp.inverse(tensor)
        assertEquals(dsp.samples, restored[0].size)
        for (channel in 0..1) assertTrue((dsp.trim until dsp.samples - dsp.trim).maxOf { abs(restored[channel][it] - wave[channel][it]) } < 0.00002f)
        assertEquals(58368, dsp.stride)
    }
    @Test fun invalidTensorOrPcmShapeFailsBeforeProducingAudio() {
        val dsp = KaraokeDsp()
        assertThrows(IllegalArgumentException::class.java) { dsp.forward(arrayOf(FloatArray(1))) }
        assertThrows(IllegalArgumentException::class.java) { dsp.inverse(FloatArray(10)) }
    }
}
