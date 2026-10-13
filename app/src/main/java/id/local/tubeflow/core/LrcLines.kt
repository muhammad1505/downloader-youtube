package id.local.tubeflow.core

/** Display only real LRC timestamps; positive offset delays a line, negative offset advances it. */
object LrcLines {
    data class Line(val atMs: Long, val text: String)
    private val timestamp = Regex("""\[(\d{1,6}):(\d{2})(?:\.(\d{1,3}))?\]""")
    private val offset = Regex("""(?im)^\s*\[offset:([+-]?\d{1,9})\]\s*$""")
    fun parse(raw: String): List<Line> {
        require(raw.toByteArray().size <= 1024 * 1024) { "LRC terlalu besar." }
        val text = raw.removePrefix("\uFEFF")
        val offsets = offset.findAll(text).map { it.groupValues[1].toLong() }.distinct().toList()
        require(offsets.size <= 1) { "Offset LRC bertentangan." }
        text.lineSequence().filter { it.trim().startsWith("[offset:", ignoreCase=true) }.forEach {
            require(offset.matches(it)) { "Offset LRC tidak valid." }
        }
        val shift = offsets.singleOrNull() ?: 0L
        val lines = mutableListOf<Line>()
        var rows = 0
        text.lineSequence().forEach { source ->
            require(++rows <= 20000) { "LRC terlalu panjang." }
            val row = source.trim()
            var cursor = 0
            val times = mutableListOf<Long>()
            while (true) {
                val match = timestamp.find(row, cursor)?.takeIf { it.range.first == cursor } ?: break
                val seconds = match.groupValues[2].toLong()
                require(seconds < 60) { "Timestamp LRC tidak valid." }
                val fraction = match.groupValues[3].padEnd(3, '0').ifEmpty { "000" }.toLong()
                times += (match.groupValues[1].toLong() * 60000 + seconds * 1000 + fraction + shift).coerceAtLeast(0)
                cursor = match.range.last + 1
            }
            if (cursor > 0) {
                require(!row.substring(cursor).startsWith('[')) { "Timestamp LRC tidak valid." }
                times.forEach { lines += Line(it, row.substring(cursor).trim()) }
            }
        }
        return lines.sortedBy { it.atMs }.distinct()
    }
    fun activeIndex(lines: List<Line>, positionMs: Long): Int {
        var low = 0; var high = lines.size
        while (low < high) { val middle = (low + high) / 2; if (lines[middle].atMs <= positionMs) low = middle + 1 else high = middle }
        return low - 1
    }
}
