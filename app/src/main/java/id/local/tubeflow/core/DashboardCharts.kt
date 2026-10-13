package id.local.tubeflow.core

import java.time.LocalDate
import java.time.ZoneId

object DashboardCharts {
    data class Point(val day: LocalDate, val completed: Long?, val cumulative: Long?)
    fun points(rows: List<DailyCount>, days: Int, today: LocalDate, analyticsSince: Long, zone: ZoneId): List<Point> {
        val length = days.coerceIn(1, 90)
        val start = today.minusDays(length - 1L)
        val firstRecorded = if (analyticsSince > 0) java.time.Instant.ofEpochMilli(analyticsSince).atZone(zone).toLocalDate() else null
        val values = rows.mapNotNull { row ->
            val date = runCatching { LocalDate.parse(row.day) }.getOrNull() ?: return@mapNotNull null
            if (date < start || date > today || row.count < 0) null else date to row.count
        }.groupBy({ it.first }, { it.second }).mapValues { (_, counts) -> counts.sum() }
        var total = 0L
        return List(length) { index ->
            val date = start.plusDays(index.toLong())
            if (firstRecorded == null || date < firstRecorded) Point(date, null, null) else {
                val count = values[date] ?: 0L
                total += count
                Point(date, count, total)
            }
        }
    }

    fun since(days: Int, today: LocalDate, zone: ZoneId): Long =
        today.minusDays(days.coerceIn(1, 90) - 1L).atStartOfDay(zone).toInstant().toEpochMilli()
}
