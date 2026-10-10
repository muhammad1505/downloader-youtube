package id.local.tubeflow.core

import java.util.Locale

/** Percent belongs to the current phase/track, never to an estimated total duration. */
object DownloadPhase {
    const val PREPARING = "PREPARING"
    const val LYRICS = "LYRICS"
    const val AUDIO = "AUDIO"
    const val VIDEO = "VIDEO"
    const val PROCESSING = "PROCESSING"
    const val COVER = "COVER"
    const val SAVING = "SAVING"
    const val FINALIZING = "FINALIZING"

    fun label(phase: String): String = when (phase) {
        AUDIO -> "Mengunduh audio"
        VIDEO -> "Mengunduh video"
        PROCESSING -> "Memproses audio/video"
        COVER -> "Memproses sampul"
        SAVING -> "Menyimpan file"
        FINALIZING -> "Menyelesaikan penyimpanan"
        LYRICS -> "Mencari lirik"
        else -> "Menyiapkan unduhan"
    }

    fun isTransfer(phase: String) = phase == AUDIO || phase == VIDEO
}

data class DownloadProgress(
    val phase: String,
    val percent: Float = -1f,
    val speed: String = "",
    val eta: String = "",
    val track: String = "",
) {
    companion object {
        // Explicit yt-dlp templates bypass the library's stale percentage/ETA on other log lines.
        const val DOWNLOAD_TEMPLATE = "download:TUBEFLOW_DOWNLOAD|%(info.format_id)s|%(progress.status)s|%(progress.downloaded_bytes)s|%(progress.total_bytes)s|%(progress.total_bytes_estimate)s|%(progress.speed)s|%(progress.eta)s|%(info.vcodec)s"
        const val PROCESS_TEMPLATE = "postprocess:TUBEFLOW_PROCESS|%(progress.status)s|%(progress.postprocessor)s"

        fun parse(line: String): DownloadProgress? {
            val fields = line.trim().split('|')
            if (fields.firstOrNull() == "TUBEFLOW_PROCESS" && fields.size == 3) {
                val phase = if (fields[2].contains("Thumbnail", ignoreCase = true)) DownloadPhase.COVER else DownloadPhase.PROCESSING
                return DownloadProgress(phase)
            }
            if (fields.firstOrNull() != "TUBEFLOW_DOWNLOAD" || fields.size != 9) return null
            if (fields[2] !in setOf("downloading", "finished")) return null
            val downloaded = fields[3].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
            val total = sequenceOf(fields[4], fields[5]).mapNotNull { it.toDoubleOrNull() }
                .firstOrNull { it.isFinite() && it > 0 }
            val finished = fields[2] == "finished"
            // Estimated sizes can be revised: allow real progress to correct itself.
            val percent = when {
                finished -> 100f
                downloaded != null && total != null -> (downloaded / total * 100).toFloat().coerceIn(0f, 100f)
                else -> -1f
            }
            val rate = fields[6].toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
            val speed = if (!finished && rate != null) {
                val unit = if (rate >= 1024 * 1024) "MiB/s" else "KiB/s"
                val divisor = if (unit == "MiB/s") 1024.0 * 1024 else 1024.0
                // Format.speed also reads existing jobs' yt-dlp log lines.
                "at ${String.format(Locale.ROOT, "%.2f", rate / divisor)}$unit"
            } else ""
            val eta = if (finished) "" else fields[7].toDoubleOrNull()
                ?.takeIf { it.isFinite() && it > 0 }?.toLong()?.let { "${it}s" }.orEmpty()
            return DownloadProgress(
                if (fields[8] == "none") DownloadPhase.AUDIO else DownloadPhase.VIDEO,
                percent, speed, eta, fields[1],
            )
        }
    }
}
