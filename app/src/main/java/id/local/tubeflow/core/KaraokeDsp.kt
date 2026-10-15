package id.local.tubeflow.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Bounded stereo STFT for the verified UVR MDX model. Tensor order: L.real, L.imag, R.real, R.imag. */
class KaraokeDsp(val frames: Int = 64) {
    val fftSize = 6144
    val bins = 3072
    val hop = 1024
    val samples = (frames - 1) * hop
    val trim = fftSize / 2
    val stride = samples - fftSize
    private val fft = MixedRadixFft(fftSize)
    private val window = FloatArray(fftSize) { (0.5 - 0.5 * cos(2 * PI * it / fftSize)).toFloat() }
    private val real = FloatArray(fftSize)
    private val imaginary = FloatArray(fftSize)

    fun forward(wave: Array<FloatArray>, suppressLowBins: Boolean = true): FloatArray {
        require(wave.size == 2 && wave.all { it.size == samples })
        val tensor = FloatArray(4 * bins * frames)
        for (channel in 0..1) for (frame in 0 until frames) {
            for (i in 0 until fftSize) {
                var index = frame * hop + i - trim
                if (index < 0) index = -index
                if (index >= samples) index = 2 * samples - 2 - index
                real[i] = wave[channel][index] * window[i]
                imaginary[i] = 0f
            }
            fft.transform(real, imaginary)
            for (bin in (if (suppressLowBins) 3 else 0) until bins) {
                tensor[((channel * 2) * bins + bin) * frames + frame] = real[bin]
                tensor[((channel * 2 + 1) * bins + bin) * frames + frame] = imaginary[bin]
            }
        }
        return tensor
    }

    fun inverse(tensor: FloatArray): Array<FloatArray> {
        require(tensor.size == 4 * bins * frames)
        val length = samples + fftSize
        val weights = FloatArray(length)
        for (frame in 0 until frames) for (i in 0 until fftSize) weights[frame * hop + i] += window[i] * window[i]
        return Array(2) { channel ->
            val wave = FloatArray(length)
            for (frame in 0 until frames) {
                real.fill(0f); imaginary.fill(0f)
                for (bin in 0 until bins) {
                    real[bin] = tensor[((channel * 2) * bins + bin) * frames + frame]
                    imaginary[bin] = tensor[((channel * 2 + 1) * bins + bin) * frames + frame]
                    if (bin > 0) { real[fftSize - bin] = real[bin]; imaginary[fftSize - bin] = -imaginary[bin] }
                }
                fft.transform(real, imaginary, inverse = true)
                for (i in 0 until fftSize) wave[frame * hop + i] += real[i] * window[i]
            }
            FloatArray(samples) { i -> wave[i + trim] / weights[i + trim].coerceAtLeast(1e-12f) }
        }
    }
}

/** Mixed-radix Cooley–Tukey FFT; 6144 = 3 × 2048. No per-butterfly allocations. */
internal class MixedRadixFft(private val size: Int) {
    private val cosine = FloatArray(size) { cos(2 * PI * it / size).toFloat() }
    private val sine = FloatArray(size) { sin(2 * PI * it / size).toFloat() }
    private val outputReal = FloatArray(size)
    private val outputImaginary = FloatArray(size)
    private val scratchReal = FloatArray(size)
    private val scratchImaginary = FloatArray(size)
    fun transform(real: FloatArray, imaginary: FloatArray, inverse: Boolean = false) {
        require(real.size == size && imaginary.size == size)
        fun visit(offset: Int, step: Int, length: Int, target: Int) {
            if (length == 1) { outputReal[target] = real[offset]; outputImaginary[target] = imaginary[offset]; return }
            val radix = when { length % 2 == 0 -> 2; length % 3 == 0 -> 3; length % 5 == 0 -> 5; else -> length }
            val part = length / radix
            for (j in 0 until radix) visit(offset + j * step, step * radix, part, target + j * part)
            for (k in 0 until part) for (r in 0 until radix) {
                var re = 0f; var im = 0f
                for (j in 0 until radix) {
                    val angle = (j * (k + r * part) * (size / length)) % size
                    val c = cosine[angle]; val s = sine[angle] * if (inverse) 1f else -1f
                    val at = target + j * part + k
                    re += outputReal[at] * c - outputImaginary[at] * s
                    im += outputReal[at] * s + outputImaginary[at] * c
                }
                scratchReal[target + r * part + k] = re; scratchImaginary[target + r * part + k] = im
            }
            scratchReal.copyInto(outputReal, target, target, target + length)
            scratchImaginary.copyInto(outputImaginary, target, target, target + length)
        }
        visit(0, 1, size, 0)
        for (i in 0 until size) { real[i] = outputReal[i] / if (inverse) size.toFloat() else 1f; imaginary[i] = outputImaginary[i] / if (inverse) size.toFloat() else 1f }
    }
}
