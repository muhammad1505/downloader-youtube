package id.local.tubeflow.core

/** Explicit encoding of a derived instrumental only; the original is never transcoded. */
enum class KaraokeOutput(val extension: String, val mime: String, val labelRes: Int, val bitrate: Int?) {
    FLAC("flac", "audio/flac", id.local.tubeflow.R.string.karaoke_format_flac, null),
    MP3("mp3", "audio/mpeg", id.local.tubeflow.R.string.karaoke_format_mp3, 192_000),
    AAC("m4a", "audio/mp4", id.local.tubeflow.R.string.karaoke_format_aac, 192_000);

    fun estimate(durationMs: Long, flacBytes: Long): Long = bitrate?.let { durationMs.coerceAtLeast(0) * it / 8000 } ?: flacBytes
    fun arguments(input: String, output: String): List<String> = listOf("-i", input, "-vn") + when (this) {
        FLAC -> listOf("-c:a", "copy")
        MP3 -> listOf("-c:a", "libmp3lame", "-b:a", "192k")
        AAC -> listOf("-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart")
    } + output
}
