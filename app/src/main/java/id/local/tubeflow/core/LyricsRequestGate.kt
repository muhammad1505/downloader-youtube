package id.local.tubeflow.core

import org.json.JSONObject
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Serial admission for LRCLIB. Call from IO; cache hits never enter this gate. */
class LyricsRequestGate(
    private val minimumGapMs: Long = 1000,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = Thread::sleep,
) {
    @Volatile private var limitedUntil = 0L
    private var nextRequestAt = 0L
    private var strikes = 0
    private var stateFile: File? = null

    /** No URLs or queries are persisted. Clearing response cache must not clear provider backoff. */
    @Synchronized fun attach(file: File) {
        if (stateFile != null) return
        stateFile = file
        runCatching {
            val saved = JSONObject(file.readText())
            limitedUntil = maxOf(limitedUntil, saved.getLong("until"))
            strikes = saved.getInt("strikes").coerceIn(0, 7)
        }
    }

    fun remainingSeconds(): Long {
        val remaining = (limitedUntil - clock()).coerceAtLeast(0)
        return remaining / 1000 + if (remaining % 1000 > 0) 1 else 0
    }

    /** Null means locally blocked; the callback is never invoked during provider cooldown. */
    @Synchronized fun <T : Any> request(load: () -> T): T? {
        if (remainingSeconds() > 0) return null
        val wait = (nextRequestAt - clock()).coerceAtLeast(0)
        if (wait > 0) sleep(wait)
        if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException()
        nextRequestAt = clock() + minimumGapMs
        return load()
    }

    @Synchronized fun limited(retryAfter: String?, serverDate: String? = null) {
        strikes = (strikes + 1).coerceAtMost(7)
        // Back off repeated rejection even when the upstream advertises a very short retry interval.
        val backoff = minOf(300_000L, 5000L shl (strikes - 1))
        val wait = maxOf(backoff, retryMillis(retryAfter, serverDate, clock()))
        limitedUntil = if (wait > Long.MAX_VALUE - clock()) Long.MAX_VALUE else clock() + wait
        persist()
    }

    @Synchronized fun accepted() {
        limitedUntil = 0
        strikes = 0
        persist()
    }

    private fun persist() {
        val file = stateFile ?: return
        runCatching {
            file.parentFile?.mkdirs()
            val temporary = File.createTempFile("lyrics-limit-", ".tmp", file.parentFile)
            try {
                temporary.writeText(JSONObject().put("until", limitedUntil).put("strikes", strikes).toString())
                require(temporary.renameTo(file))
            } finally { temporary.delete() }
        }
    }

    companion object {
        /** Retry-After supports delta-seconds and HTTP dates. Never shorten a long provider delay. */
        internal fun retryMillis(value: String?, serverDate: String?, now: Long): Long {
            val text = value?.trim() ?: return 0
            text.toLongOrNull()?.takeIf { it >= 0 }?.let {
                return if (it > Long.MAX_VALUE / 1000) Long.MAX_VALUE else it * 1000
            }
            fun date(raw: String?) = runCatching {
                ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
            }.getOrNull()
            return date(text)?.let { (it - (date(serverDate) ?: now)).coerceAtLeast(0) } ?: 0
        }
    }
}
