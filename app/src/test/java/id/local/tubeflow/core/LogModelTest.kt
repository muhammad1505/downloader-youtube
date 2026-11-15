package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogModelTest {

    private val entry = LogEntry(
        seq = 7,
        at = 1_700_000_000_000,
        level = LogLevel.WARN,
        category = LogCategory.DOWNLOAD,
        message = "Unduhan gagal pada tahap SAVING.",
        detail = "HTTP 429 dari penyedia.",
    )

    @Test
    fun entryRoundTripsThroughJsonWithoutLosingFields() {
        val parsed = LogEntry.parse(entry.json().toString())

        assertEquals(entry, parsed)
    }

    @Test
    fun missingDetailRoundTripsAsNull() {
        val withoutDetail = entry.copy(detail = null)

        assertEquals(withoutDetail, LogEntry.parse(withoutDetail.json().toString()))
    }

    @Test
    fun malformedLinesAreIgnored() {
        assertNull(LogEntry.parse("bukan json"))
        assertNull(LogEntry.parse("""{"level":"WARN","category":"DOWNLOAD"}"""))
        assertNull(LogEntry.parse("""{"level":"TIDAK","category":"DOWNLOAD","message":"x"}"""))
    }

    @Test
    fun filterMatchesLevelCategoryAndCaseInsensitiveQuery() {
        assertTrue(LogFilter(level = LogLevel.WARN).matches(entry))
        assertFalse(LogFilter(level = LogLevel.ERROR).matches(entry))
        assertTrue(LogFilter(category = LogCategory.DOWNLOAD).matches(entry))
        assertFalse(LogFilter(category = LogCategory.PLAYER).matches(entry))
        assertTrue(LogFilter(query = "http 429").matches(entry))
        assertTrue(LogFilter(query = "peringatan").matches(entry))
        assertTrue(LogFilter(query = "unduhan").matches(entry))
        assertFalse(LogFilter(query = "karaoke").matches(entry))
        assertTrue(LogFilter(query = "   ").matches(entry))
    }

    @Test
    fun exportKeepsChronologicalOrderAndIncludesHeader() {
        val newer = entry.copy(seq = 8, at = entry.at + 1_000, message = "Catatan lebih baru.")
        val text = LogFormat.export(listOf(newer, entry), "TubeFlow uji")

        assertTrue(text.startsWith("TubeFlow uji\n---\n"))
        assertTrue(text.indexOf(entry.message) < text.indexOf(newer.message))
        assertTrue(text.contains("[WARN] [Unduhan]"))
    }
}
