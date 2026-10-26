package id.local.tubeflow.core

import kotlin.math.ln

object PlaybackPolicy {
    const val SYSTEM = "system"
    const val ON = "on"
    const val OFF = "off"

    fun saving(mode: String, systemSaving: Boolean): Boolean = when (mode) {
        ON -> true
        OFF -> false
        else -> systemSaving
    }

    fun audioTickMs(saving: Boolean, hasLyrics: Boolean = false): Long = if (saving && !hasLyrics) 500L else 200L
    fun videoTickMs(saving: Boolean): Long = if (saving) 1_000L else 500L

    /** Window-only fallback when Android adaptive brightness is disabled. */
    fun ambientBrightness(lux: Float): Float? {
        if (!lux.isFinite() || lux < 0f) return null
        return (0.08f + 0.92f * (ln(1f + lux.coerceAtMost(10_000f)) / ln(10_001f))).coerceIn(0.08f, 1f)
    }
}
