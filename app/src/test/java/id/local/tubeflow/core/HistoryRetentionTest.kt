package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryRetentionTest {
    private val day = 86_400_000L

    @Test
    fun disabledRetentionProducesNoCutoff() {
        assertNull(historyCutoff(0, 1_000_000_000L))
        assertNull(historyCutoff(-5, 1_000_000_000L))
    }

    @Test
    fun cutoffCountsWholeDaysBackFromNow() {
        val now = 100 * day

        assertEquals(now - 7 * day, historyCutoff(7, now))
        assertEquals(now - 30 * day, historyCutoff(30, now))
    }

    @Test
    fun absurdDayCountIsClampedToOneYear() {
        val now = 1_000 * day

        assertEquals(now - 365 * day, historyCutoff(10_000, now))
    }

    @Test
    fun completedAtWinsButLegacyRowsFallBackToCreatedAt() {
        assertEquals(900L, historyTimestamp(completedAt = 900, createdAt = 100))
        assertEquals(100L, historyTimestamp(completedAt = 0, createdAt = 100))
    }
}
