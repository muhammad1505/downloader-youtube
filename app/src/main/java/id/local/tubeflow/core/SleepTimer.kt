package id.local.tubeflow.core

/** Monotonic deadline: wall-clock changes and paused playback do not extend the timer. */
class SleepTimer {
    var deadlineMs: Long = 0; private set
    var afterTrack: Boolean = false; private set
    fun minutes(minutes: Int, nowMs: Long) {
        require(minutes in 1..180)
        deadlineMs = nowMs + minutes * 60_000L; afterTrack = false
    }
    fun endOfTrack() { deadlineMs = 0; afterTrack = true }
    fun finishTrack(): Boolean {
        if (!afterTrack) return false
        clear(); return true
    }
    fun clear() { deadlineMs = 0; afterTrack = false }
    fun remaining(nowMs: Long): Long = if (deadlineMs > 0) (deadlineMs - nowMs).coerceAtLeast(0) else 0
    fun due(nowMs: Long) = deadlineMs > 0 && nowMs >= deadlineMs
    fun gain(nowMs: Long): Float = if (deadlineMs > 0) (remaining(nowMs) / 10_000f).coerceIn(0f, 1f) else 1f
}

object KaraokeComparison {
    fun target(positionMs: Long, fromInstrumental: Boolean, toInstrumental: Boolean, startMs: Long, durationMs: Long): Long {
        require(startMs >= 0 && durationMs > 0)
        val sourcePosition = positionMs.coerceAtLeast(0) + if (fromInstrumental) startMs else 0
        val relative = sourcePosition - startMs
        val local = if (relative in 0 until durationMs) relative else 0
        return local + if (toInstrumental) 0 else startMs
    }
}
