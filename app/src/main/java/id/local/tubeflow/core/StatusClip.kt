package id.local.tubeflow.core

import java.io.File
import java.util.Locale

/**
 * Perencanaan klip Status yang murni dan dapat diuji. Pekerjaan Android/FFmpeg ada di
 * [StatusClipper]; kelas ini hanya memilih titik potong dan menyusun argumen.
 */
object StatusClip {
    const val DURATION_MS = 30_000L

    /** Toleransi durasi hasil stream-copy sebelum dianggap gagal dan dienkode ulang. */
    const val COPY_SLOP_MS = 1_750L

    /** Awal klip dijepit agar hasil tidak melewati akhir video. */
    fun startMs(durationMs: Long, requestedMs: Long): Long {
        require(durationMs > 0) { "Durasi video belum diketahui." }
        return requestedMs.coerceIn(0, (durationMs - DURATION_MS).coerceAtLeast(0))
    }

    /** Video yang lebih pendek dari 30 detik tetap dipotong sampai habis, bukan ditolak. */
    fun expectedDurationMs(durationMs: Long, startMs: Long): Long =
        minOf(DURATION_MS, durationMs - startMs).coerceAtLeast(0)

    /**
     * Stream-copy: cepat dan tidak mengubah kualitas. `-ss` sebelum `-i` memakai keyframe
     * terdekat, jadi hasil bisa bergeser sedikit; [StatusClipper] memeriksa durasinya.
     */
    fun copyArguments(input: File, output: File, startMs: Long, durationMs: Long): List<String> = listOf(
        "-ss", seconds(startMs),
        "-i", input.path,
        "-t", seconds(expectedDurationMs(durationMs, startMs)),
        "-map", "0:v:0", "-map", "0:a?",
        "-c", "copy",
        "-avoid_negative_ts", "make_zero",
        "-movflags", "+faststart",
        output.path,
    )

    /** Fallback kompatibel WhatsApp: H.264 + AAC dalam MP4. */
    fun encodeArguments(input: File, output: File, startMs: Long, durationMs: Long): List<String> = listOf(
        "-ss", seconds(startMs),
        "-i", input.path,
        "-t", seconds(expectedDurationMs(durationMs, startMs)),
        "-map", "0:v:0", "-map", "0:a?",
        "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-pix_fmt", "yuv420p",
        "-c:a", "aac", "-b:a", "128k",
        "-movflags", "+faststart",
        output.path,
    )

    /** Selalu memakai titik desimal, bukan koma locale perangkat. */
    fun seconds(ms: Long): String = String.format(Locale.ROOT, "%.3f", ms.coerceAtLeast(0) / 1000.0)
}
