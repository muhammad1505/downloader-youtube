package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DashboardChartsTest {
    private val zone = ZoneId.of("Asia/Makassar")
    private val today = LocalDate.of(2026, 10, 13)
    @Test fun inactiveRecordedDaysAreZeroButPreUpgradeDaysRemainUnknown() {
        val since = today.minusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val result = DashboardCharts.points(listOf(DailyCount("2026-10-11", 2), DailyCount("2026-10-13", 3)), 7, today, since, zone)
        assertEquals(7, result.size)
        assertEquals(4, result.count { it.completed == null })
        assertEquals(listOf(2L, 0L, 3L), result.takeLast(3).map { it.completed })
        assertEquals(listOf(2L, 2L, 5L), result.takeLast(3).map { it.cumulative })
    }
    @Test fun periodRejectsMalformedFutureAndOutsideRowsAndHasNinetyPointBound() {
        val since = today.minusDays(100).atStartOfDay(zone).toInstant().toEpochMilli()
        val rows = listOf(DailyCount("bad", 1), DailyCount("2026-10-14", 9), DailyCount("2020-01-01", 8), DailyCount("2026-10-13", -2))
        val result = DashboardCharts.points(rows, 1000, today, since, zone)
        assertEquals(90, result.size); assertTrue(result.all { it.completed == 0L && it.cumulative == 0L })
        assertTrue(DashboardCharts.points(emptyList(), 7, today, 0, zone).all { it.completed == null })
    }
    @Test fun ninetyDayAggregationBenchmarkUsesRepresentativeGroupedRows() {
        val since = today.minusDays(89).atStartOfDay(zone).toInstant().toEpochMilli()
        val rows = List(90) { i -> DailyCount(today.minusDays(89L - i).toString(), (i % 5).toLong()) }
        repeat(100) { DashboardCharts.points(rows, 90, today, since, zone) }
        val samples = LongArray(500) {
            val start = System.nanoTime()
            val result = DashboardCharts.points(rows, 90, today, since, zone)
            assertEquals(rows.sumOf { it.count }, result.last().cumulative)
            System.nanoTime() - start
        }.sorted()
        println("Dashboard 90-day aggregation, 500 samples: p50=${samples[250] / 1000}us p95=${samples[475] / 1000}us (host, not Android frame timing)")
    }
    @Test fun midnightBoundaryUsesCalendarDaysIncludingDaylightSaving() {
        val dst = ZoneId.of("America/New_York")
        val end = LocalDate.of(2026, 3, 9)
        assertEquals(end.minusDays(6).atStartOfDay(dst).toInstant().toEpochMilli(), DashboardCharts.since(7, end, dst))
        assertEquals(today.atStartOfDay(zone).toInstant().toEpochMilli(), DashboardCharts.since(1, today, zone))
    }
}
