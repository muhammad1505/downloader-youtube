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

    /** Localized measured rate; invalid/unknown input remains absent rather than invented. */
    fun speedLabel(raw: String?): String? {
        val value = speed(raw) ?: raw?.trim() ?: return null
        val match = Regex("(?i)^([0-9]+(?:[.,][0-9]+)?)\\s*(B|[KMGT]i?B)/s$").matchEntire(value) ?: return null
        val number = match.groupValues[1].replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
        val unit = match.groupValues[2].uppercase(Locale.ROOT)
        val powers = mapOf("B" to 0, "KB" to 1, "KIB" to 1, "MB" to 2, "MIB" to 2, "GB" to 3, "GIB" to 3, "TB" to 4, "TIB" to 4)
        val power = powers[unit] ?: return null
        val bytes = number * Math.pow(if (unit.contains("I")) 1024.0 else 1000.0, power.toDouble())
        var amount = bytes; var index = 0
        val labels = listOf("B", "KB", "MB", "GB", "TB")
        while (amount >= 1000 && index < labels.lastIndex) { amount /= 1000; index++ }
        return String.format(Locale.forLanguageTag("id"), "%.1f %s/s", amount, labels[index])
    }

    private val emoji = Regex("""[\p{So}\p{Cn}\uFE0F\u200D]+""")
    private val illegal = Regex("""[\\/:*?"<>|\p{Cntrl}]""")
    private val spaces = Regex("""\s+""")
    /** Short, tidy, filesystem-safe display name derived from the source title. */
    fun tidyFileName(raw: String): String {
        val cleaned = raw.replace(illegal, " ").replace(emoji, "").replace(spaces, " ").trim(' ', '.', '_', '-')
        if (cleaned.isEmpty()) return "TubeFlow"
        if (cleaned.length <= 60) return cleaned
        // Nama "Judul - Artis": potong bagian judulnya saja supaya artis tetap terbaca di nama file.
        val dash = cleaned.lastIndexOf(" - ")
        if (dash > 0 && cleaned.length - dash - 3 <= 24) {
            val artist = cleaned.substring(dash + 3)
            val keep = (60 - artist.length - 3).coerceAtLeast(12)
            return cleaned.substring(0, dash).take(keep).trimEnd(' ', '.', '-', '_') + " - " + artist
        }
        return cleaned.take(60).trimEnd(' ', '.', '-', '_')
    }

    fun musicLabel(identity: SongIdentity): String = identity.title.ifBlank { "Tanpa judul" } +
        identity.versionTag.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()

    /** Keep version and credit when the main title is long; reserve bytes for extension/provider suffix. */
    fun musicFileName(identity: SongIdentity): String {
        fun component(value: String, budget: Int): String {
            val clean = value.replace(illegal, " ").replace(emoji, "").replace(spaces, " ").trim(' ', '.', '_', '-')
            val result = StringBuilder()
            var index = 0
            var bytes = 0
            while (index < clean.length) {
                val point = clean.codePointAt(index)
                val token = String(Character.toChars(point))
                val size = token.toByteArray(Charsets.UTF_8).size
                if (bytes + size > budget) break
                result.append(token); bytes += size; index += Character.charCount(point)
            }
            return result.toString().trimEnd()
        }
        val main = component(identity.title,100).ifBlank { "TubeFlow" }
        val version = component(identity.versionTag,60)
        val credit = component(identity.artist.orEmpty().ifBlank { identity.remixer },50)
        return main + (if (version.isBlank()) "" else " ($version)") + (if (credit.isBlank()) "" else " - $credit")
    }

    /**
     * "Judul - Artis" yang rapi (urutan konsisten: judul dulu). Delegasi ke [TitleSplit] agar
     * penamaan file, pencarian lirik, dan auto-folder SEMUA memakai identifikasi artis yang sama.
     * [artist] dipakai sebagai BUKTI artis bila muncul utuh di judul — bukan tebakan posisi.
     */
    fun neatTitle(raw: String, artist: String? = null): String =
        TitleSplit.display(TitleSplit.identify(raw, channel = artist))

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
