package id.local.tubeflow.core

import java.io.File
import java.util.Locale

/**
 * Perencanaan klip Status yang murni dan dapat diuji. Pekerjaan Android/FFmpeg ada di
 * [StatusClipper]; kelas ini hanya memilih rentang potong dan menyusun argumen.
 */
object StatusClip {
    const val DURATION_MS = 30_000L
    const val MIN_DURATION_MS = 1_000L

    /** Toleransi durasi hasil stream-copy sebelum dianggap gagal dan dienkode ulang. */
    const val COPY_SLOP_MS = 1_750L

    data class ClipRange(val startMs: Long, val endMs: Long) {
        val durationMs: Long get() = endMs - startMs
    }

    /**
     * Menjepit titik awal dan akhir pilihan pengguna agar tetap di dalam video, berurutan,
     * serta berdurasi 1–30 detik. Video yang lebih pendek dari satu detik memakai seluruh
     * durasinya karena batas minimum tidak mungkin dipenuhi.
     */
    fun range(durationMs: Long, requestedStartMs: Long, requestedEndMs: Long): ClipRange {
        require(durationMs > 0) { "Durasi video belum diketahui." }
        val start = requestedStartMs.coerceIn(0, (durationMs - MIN_DURATION_MS).coerceAtLeast(0))
        val minEnd = (start + MIN_DURATION_MS).coerceAtMost(durationMs)
        val maxEnd = (start + DURATION_MS).coerceAtMost(durationMs)
        val end = requestedEndMs.coerceIn(minEnd, maxEnd)
        return ClipRange(start, end)
    }

    /**
     * Stream-copy: cepat dan tidak mengubah kualitas. `-ss` sebelum `-i` memakai keyframe
     * terdekat, jadi hasil bisa bergeser sedikit; [StatusClipper] memeriksa durasinya.
     */
    fun copyArguments(input: File, output: File, range: ClipRange): List<String> = listOf(
        "-ss", seconds(range.startMs),
        "-i", input.path,
        "-t", seconds(range.durationMs),
        "-map", "0:v:0", "-map", "0:a?",
        "-c", "copy",
        "-avoid_negative_ts", "make_zero",
        "-movflags", "+faststart",
        output.path,
    )

    /** Fallback kompatibel WhatsApp: H.264 + AAC dalam MP4. */
    fun encodeArguments(input: File, output: File, range: ClipRange): List<String> = listOf(
        "-ss", seconds(range.startMs),
        "-i", input.path,
        "-t", seconds(range.durationMs),
        "-map", "0:v:0", "-map", "0:a?",
        "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-pix_fmt", "yuv420p",
        "-c:a", "aac", "-b:a", "128k",
        "-movflags", "+faststart",
        output.path,
    )

    /** Selalu memakai titik desimal, bukan koma locale perangkat. */
    fun seconds(ms: Long): String = String.format(Locale.ROOT, "%.3f", ms.coerceAtLeast(0) / 1000.0)
}
