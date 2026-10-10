package id.local.tubeflow.core

import java.util.Locale

object Format {
    private val speedPattern = Regex("""\bat\s+(\S+/s)""")

    fun duration(seconds: Long): String {
        val total = seconds.coerceAtLeast(0)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val rest = total % 60
        return if (hours > 0) "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, rest)
        else "%d:%02d".format(Locale.ROOT, minutes, rest)
    }

    /** Accepts raw forms stored by the worker ("123s", "123", "-1", null). Blank when unknown. */
    fun eta(raw: String?): String {
        val seconds = raw?.trim()?.removeSuffix("s")?.toLongOrNull() ?: return ""
        if (seconds <= 0) return ""
        if (seconds < 60) return "$seconds dtk"
        if (seconds < 3600) {
            val minutes = seconds / 60
            val rest = seconds % 60
            return if (rest == 0L) "$minutes mnt" else "$minutes mnt $rest dtk"
        }
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return if (minutes == 0L) "$hours jam" else "$hours jam $minutes mnt"
    }

    /** Pulls the transfer rate out of a yt-dlp progress line, e.g. "... at 1.23MiB/s ETA 00:45". */
    fun speed(line: String?): String? = line?.let { speedPattern.find(it)?.groupValues?.get(1) }

    private val emoji = Regex("""[\p{So}\p{Cn}\uFE0F\u200D]+""")
    private val illegal = Regex("""[\\/:*?"<>|\p{Cntrl}]""")
    private val spaces = Regex("""\s+""")
    /** Short, tidy, filesystem-safe display name derived from the source title. */
    fun tidyFileName(raw: String): String {
        val cleaned = raw.replace(illegal, " ").replace(emoji, "").replace(spaces, " ").trim(' ', '.', '_', '-')
        if (cleaned.isEmpty()) return "TubeFlow"
        return if (cleaned.length > 60) cleaned.take(60).trimEnd(' ', '.', '-', '_') else cleaned
    }

    private val noiseWords = setOf(
        "official", "music", "video", "videos", "lyric", "lyrics", "audio", "with", "visualizer", "visualiser",
        "4k", "8k", "60fps", "hd", "uhd", "remaster", "remastered", "mv", "hq", "teaser", "explicit", "clean",
        "full", "album", "version", "clip", "klip", "musik", "resmi", "lirik",
    )
    private val trailingTag = Regex("""\s*[(\[][^)\]]*[)\]]\s*$""")
    private val trailingDash = Regex("""\s+-\s+[^-]+$""")
    private val wordSplit = Regex("""[\s,.&|]+""")

    /** "Artist - Title" yang rapi: buang embel-embel (Official Video, 4K, …) dan tambahkan artis bila perlu. */
    fun neatTitle(raw: String, artist: String? = null): String {
        var title = raw.trim()
        while (true) {
            val match = trailingTag.find(title) ?: break
            val words = match.value.trim().trim('(', ')', '[', ']').split(wordSplit).filter { it.isNotEmpty() }
            if (words.isEmpty() || words.any { it.lowercase() !in noiseWords }) break
            title = title.removeRange(match.range).trim()
        }
        trailingDash.find(title)?.let { match ->
            val words = match.value.trim().removePrefix("-").trim().split(wordSplit).filter { it.isNotEmpty() }
            if (words.isNotEmpty() && words.all { it.lowercase() in noiseWords }) title = title.removeRange(match.range).trim()
        }
        if (title.isBlank()) title = raw.trim()
        val name = artist?.trim()
        if (" - " !in title && !name.isNullOrEmpty() && !title.startsWith(name, ignoreCase = true)) title = "$name - $title"
        return title.trim()
    }

    // Format.toLrc (konversi caption SRT/VTT → LRC) dihapus: lirik audio kini hanya dari LRCLIB
    // (BUG-009) dan tak ada pemanggilnya — jalur konversi caption lama jadi dead code.

    fun bytes(value: Long): String {
        if (value < 1024) return "$value B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var size = value.toDouble() / 1024
        var unit = 0
        while (size >= 1024 && unit < units.lastIndex) { size /= 1024; unit++ }
        return String.format(Locale.forLanguageTag("id"), "%.1f %s", size, units[unit])
    }
}
