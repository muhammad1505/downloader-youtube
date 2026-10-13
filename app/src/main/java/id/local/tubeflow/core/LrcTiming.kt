package id.local.tubeflow.core

/** Shared by refetch, timing edits and catalogue rename/delete. Never nest this lock. */
object MediaFileOperations {
    val mutex = kotlinx.coroutines.sync.Mutex()
}

/** A per-recording translation, never a synthesized timeline or a global offset. */
object LrcTiming {
    const val LIMIT_MS = 30_000L
    /** Signed decimal seconds; reject rounding, exponent notation and invalid bounds. */
    fun parseSeconds(value: String): Long? {
        val normalized=value.trim().replace(',', '.').replace('−', '-')
        if (!Regex("[+-]?[0-9]+(?:\\.[0-9]{1,3})?").matches(normalized)) return null
        return runCatching { java.math.BigDecimal(normalized).movePointRight(3).longValueExact() }
            .getOrNull()?.takeIf { it in -LIMIT_MS..LIMIT_MS }
    }
    fun secondsLabel(ms: Long): String = java.math.BigDecimal.valueOf(ms,3).stripTrailingZeros().toPlainString().replace('.', ',')
    private val time = Regex("""\[(\d+):(\d{2})(?:\.(\d{1,3}))?\]""")
    private val metadata = Regex("""\[(?:ar|ti|al|by|re|ve|length|offset):[^\]]*\]""", RegexOption.IGNORE_CASE)
    fun shift(original: String, deltaMs: Long, durationMs: Long?): String {
        require(deltaMs in -LIMIT_MS..LIMIT_MS) { "Koreksi maksimum 30 detik." }
        var timed = 0
        val translated = original.lineSequence().map { raw ->
            val line = raw.trim()
            if (line.isEmpty()) raw else if (metadata.matches(line)) {
                if (line.startsWith("[offset:", true)) require(line.substringAfter(':').dropLast(1).trim().toLongOrNull() != null) { "Offset LRC tidak valid." }
                raw
            } else {
                var cursor = 0
                val prefix = StringBuilder()
                while (true) {
                    val match = time.find(line, cursor)?.takeIf { it.range.first == cursor } ?: break
                    val minutes = match.groupValues[1].toLongOrNull() ?: error("Timestamp tidak valid.")
                    val seconds = match.groupValues[2].toLong()
                    require(seconds < 60 && minutes <= 100_000) { "Timestamp tidak valid." }
                    val fraction = match.groupValues[3].padEnd(3, '0').ifEmpty { "000" }.toLong()
                    val value = minutes * 60_000 + seconds * 1000 + fraction
                    val shifted = (value + deltaMs).coerceAtLeast(0)
                    require(durationMs == null || shifted <= durationMs + 2000) { "Koreksi melewati akhir audio." }
                    val precision = maxOf(2, match.groupValues[3].length, if (deltaMs % 10 != 0L) 3 else 2)
                    val decimals = if (precision == 3) "%03d".format(java.util.Locale.ROOT, shifted % 1000) else "%02d".format(java.util.Locale.ROOT, shifted % 1000 / 10)
                    prefix.append("[%02d:%02d.%s]".format(java.util.Locale.ROOT, shifted / 60_000, shifted / 1000 % 60, decimals))
                    cursor = match.range.last + 1
                    
                }
                require(cursor > 0 && !line.substring(cursor).startsWith('[')) { "Format LRC tidak valid." }
                if (line.substring(cursor).isNotBlank()) timed++
                prefix.toString() + line.substring(cursor)
            }
        }.joinToString("\n")
        require(timed > 0) { "Lirik tidak memiliki timestamp." }
        // Reset is byte-for-byte original (including metadata and newline precision).
        return if (deltaMs == 0L) original else translated
    }
}

/** Write-ahead replacement used by refetch; a failed rollback retains a durable backup. */
class JournaledTextReplacement(
    private val read: () -> ByteArray,
    private val write: (ByteArray) -> Unit,
    private val load: () -> Pending?,
    private val save: (Pending) -> Unit,
    private val clear: () -> Unit,
    private val committed: () -> Unit = {},
) {
    data class Pending(val before: ByteArray, val next: ByteArray)

    fun recover() {
        val pending = load() ?: return
        val current = read()
        when {
            current.contentEquals(pending.next) -> committed()
            current.contentEquals(pending.before) -> Unit
            current.isPrefixOf(pending.next) || current.isPrefixOf(pending.before) -> {
                write(pending.before)
                require(read().contentEquals(pending.before)) { "Pemulihan LRC belum berhasil; cadangan dipertahankan." }
            }
            else -> error("LRC berubah di luar aplikasi; cadangan dipertahankan dan file tidak ditimpa.")
        }
        clear()
    }

    fun replace(next: ByteArray, expected: ByteArray) {
        recover()
        val previous = read() // No truncation if backup cannot be read.
        require(previous.contentEquals(expected)) { "LRC berubah selama pencarian; buka ulang dan coba lagi." }
        if (previous.contentEquals(next)) return // Preserve an existing correction baseline on a no-op.
        save(Pending(previous, next)) // Failure here must not touch the provider.
        try {
            VerifiedTextWrite.replace(previous, next, read, write)
        } catch (failure: Exception) {
            if (runCatching { read().contentEquals(previous) }.getOrDefault(false)) clear()
            throw failure
        }
        committed() // Reset timing baseline only after verified publication.
        clear()
    }

    private fun ByteArray.isPrefixOf(other: ByteArray): Boolean =
        size < other.size && indices.all { this[it] == other[it] }
}

/** Verify a write and restore the previous bytes on write/close/read-back failure. */
object VerifiedTextWrite {
    fun replace(previous: ByteArray, next: ByteArray, read: () -> ByteArray, write: (ByteArray) -> Unit) {
        try {
            write(next)
            require(read().contentEquals(next)) { "Verifikasi file gagal." }
        } catch (failure: Exception) {
            try {
                write(previous)
                require(read().contentEquals(previous)) { "Verifikasi pemulihan gagal." }
            } catch (restore: Exception) {
                throw IllegalStateException("Penulisan dan pemulihan gagal; cadangan asli dipertahankan.", restore)
            }
            throw IllegalStateException("Perubahan gagal; file sebelumnya dipulihkan.", failure)
        }
    }
}
