package id.local.tubeflow.core

/**
 * Ketuk-waktu lirik: satu ketukan per baris, timestamp diambil dari posisi pemutaran saat diketuk.
 * Modul ini tidak menebak isi baris, tidak menginterpolasi, dan tidak menggeser lirik lain.
 * Hasilnya tetap LRC biasa sehingga dapat disunting lagi lewat editor waktu yang sudah ada.
 */
object TapSync {
    /** Jeda minimum antar ketukan agar salah ketuk tidak menghasilkan dua baris bertumpuk. */
    const val MIN_GAP_MS = 250L

    private val timestamp = Regex("""^\s*(?:\[\d{1,6}:\d{2}(?:\.\d{1,3})?\])+""")
    private val metadata = Regex("""^\s*\[(?:ar|ti|al|by|re|ve|length|offset|[a-z]{2,8}):[^\]]*\]\s*$""", RegexOption.IGNORE_CASE)

    /** Baris lirik tanpa timestamp/metadata. Baris kosong dibuang karena tidak dapat diketuk. */
    fun lines(raw: String): List<String> = raw.lineSequence().mapNotNull { source ->
        val line = source.trim()
        when {
            line.isEmpty() -> null
            metadata.matches(line) -> null
            else -> line.replace(timestamp, "").trim().takeIf(String::isNotEmpty)
        }
    }.toList()

    /** Alasan ketukan ditolak, atau null bila boleh disimpan. */
    fun rejection(taps: List<Long>, atMs: Long): String? = when {
        atMs < 0 -> "Posisi pemutaran belum diketahui."
        taps.lastOrNull()?.let { atMs - it < MIN_GAP_MS } == true -> "Terlalu cepat; beri jeda minimal 0,25 detik antar baris."
        else -> null
    }

    fun stamp(atMs: Long): String {
        require(atMs >= 0) { "Timestamp tidak valid." }
        val centis = atMs % 1000 / 10
        return "%02d:%02d.%02d".format(java.util.Locale.ROOT, atMs / 60_000, atMs / 1000 % 60, centis)
    }

    fun build(lines: List<String>, taps: List<Long>): String {
        require(lines.isNotEmpty()) { "Lirik tidak memiliki baris untuk disinkronkan." }
        require(lines.size == taps.size) { "Ketuk setiap baris sebelum menyimpan (${taps.size}/${lines.size})." }
        taps.zipWithNext().forEach { (before, after) -> require(after - before >= MIN_GAP_MS) { "Timestamp tidak berurutan." } }
        return lines.mapIndexed { index, text -> "[${stamp(taps[index])}]$text" }.joinToString("\n") + "\n"
    }
}
