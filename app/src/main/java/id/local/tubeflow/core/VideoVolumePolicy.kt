package id.local.tubeflow.core

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

object VideoVolumePolicy {
    val steps = listOf(0, 3, 6, 9)

    fun boostDb(value: Int): Int = value.coerceIn(0, 9) / 3 * 3
    fun gain(value: Int): Float = 10.0.pow(boostDb(value) / 20.0).toFloat()

    fun amplify(sample: Float, gain: Float): Float {
        if (!sample.isFinite()) return 0f
        val amplified = sample.toDouble() * gain
        val level = abs(amplified)
        if (level <= 0.9) return amplified.toFloat()
        // A continuous knee preserves quiet dialogue and rounds peaks instead of wrapping PCM.
        val excess = level - 0.9
        return (sign(amplified) * (0.9 + 0.1 * excess / (0.1 + excess))).toFloat()
    }
}
