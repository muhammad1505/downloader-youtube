package id.local.tubeflow.core

enum class VideoBrightnessMode { AUTO, MANUAL }

object VideoBrightnessPolicy {
    const val MINIMUM = 0.04f

    fun clamp(value: Float): Float = if (value.isFinite()) value.coerceIn(MINIMUM, 1f) else 0.5f

    fun initial(windowOverride: Float, systemLevel: Int): Float =
        if (windowOverride.isFinite() && windowOverride in 0f..1f) clamp(windowOverride)
        else clamp(systemLevel.coerceIn(0, 255) / 255f)
}
