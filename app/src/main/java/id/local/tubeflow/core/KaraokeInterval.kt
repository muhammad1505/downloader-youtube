package id.local.tubeflow.core

import java.math.BigDecimal
import java.util.Locale

/** Explicit clip position; a request near the end becomes shorter, never silently starts earlier. */
object KaraokeInterval {
    data class Segment(val startMs: Long, val durationMs: Long)
    fun parseStart(value: String): Long? {
        val text = value.trim().replace(',', '.')
        val parts = text.split(':')
        if (parts.size !in 1..2) return null
        val secondsText = parts.last()
        if (!secondsText.matches(Regex("[0-9]{1,3}(?:\\.[0-9]{1,3})?"))) return null
        return runCatching {
            val seconds = BigDecimal(secondsText)
            val minutes = if (parts.size == 2) {
                require(parts[0].matches(Regex("[0-9]{1,2}")) && seconds < BigDecimal(60))
                parts[0].toInt()
            } else 0
            val result = (BigDecimal(minutes * 60) + seconds).movePointRight(3).longValueExact()
            result.takeIf { it in 0..600000 }
        }.getOrNull()
    }
    fun formatStart(ms: Long): String {
        val safe = ms.coerceIn(0, 600000)
        val clock = "%d:%02d".format(Locale.ROOT, safe / 60000, (safe / 1000) % 60)
        val fraction = if (safe % 1000 == 0L) "" else ".%03d".format(Locale.ROOT, safe % 1000).trimEnd('0')
        return clock + fraction
    }
    fun select(durationMs: Long, startMs: Long, sample: Boolean): Segment {
        require(durationMs in 1..600000) { "Pilih audio berdurasi paling lama 10 menit." }
        if (!sample) return Segment(0, durationMs)
        require(startMs in 0 until durationMs) { "Posisi mulai melewati akhir lagu. Pilih menit/detik yang lebih awal." }
        return Segment(startMs, minOf(30000, durationMs - startMs))
    }
}
