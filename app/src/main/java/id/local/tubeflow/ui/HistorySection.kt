package id.local.tubeflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.local.tubeflow.core.DownloadJob
import id.local.tubeflow.core.DownloadSections
import id.local.tubeflow.core.LyricStatus
import id.local.tubeflow.core.MediaMode
import id.local.tubeflow.core.sourcePlatform

/** Isi tab Download bagian Riwayat, dipakai di dalam satu LazyColumn host. */
fun LazyListScope.historySection(
    jobs: List<DownloadJob>,
    query: String,
    modeFilter: Int,
    platformFilter: String,
    onQueryChange: (String) -> Unit,
    onModeFilterChange: (Int) -> Unit,
    onPlatformFilterChange: (String) -> Unit,
    onClearRequest: () -> Unit,
    onDeleteRequest: (DownloadJob) -> Unit,
    onRedownloadRequest: (DownloadJob) -> Unit,
    onRefetchLyrics: (String) -> Unit,
    onSearchLyrics: (String) -> Unit,
    onPlay: (List<DownloadJob>, Int) -> Unit,
    onEnqueue: (DownloadJob) -> Unit,
    onPlayNext: (DownloadJob) -> Unit,
    onEditLyricTiming: (String) -> Unit,
) {
    val completed = DownloadSections.history(jobs)
    val platforms = completed.map { it.sourcePlatform.label }.distinct().sorted()
    val filtered = completed.filter { job ->
        val matchesQuery = query.isBlank() ||
            job.title.contains(query.trim(), ignoreCase = true)
        val matchesMode = when (modeFilter) {
            0 -> job.mode == MediaMode.VIDEO
            1 -> job.mode == MediaMode.AUDIO
            else -> true
        }
        val matchesPlatform = platformFilter.isEmpty() || job.sourcePlatform.label == platformFilter
        matchesQuery && matchesMode && matchesPlatform
    }

    val showTools = completed.size > 3 || query.isNotBlank() || modeFilter != -1 || platformFilter.isNotEmpty()

    item(key = "history-summary") {
        HistorySummary(
            summary = historySummary(completed, filtered),
            onClearRequest = onClearRequest,
        )
    }
    if (showTools) {
        item(key = "history-filters") {
            ScrollRow {
                FilterChip(selected = modeFilter == -1, onClick = { onModeFilterChange(-1) }, label = { Text("Semua") })
                FilterChip(selected = modeFilter == 0, onClick = { onModeFilterChange(0) }, label = { Text("Video") })
                FilterChip(selected = modeFilter == 1, onClick = { onModeFilterChange(1) }, label = { Text("Audio") })
                if (platforms.size > 1) {
                    Spacer(Modifier.width(8.dp))
                    platforms.forEach { name ->
                        FilterChip(
                            selected = platformFilter == name,
                            onClick = { onPlatformFilterChange(name) },
                            label = { Text(name) },
                        )
                    }
                }
            }
        }
    }
    if (filtered.isEmpty()) {
        item(key = "history-no-match") { HistoryNoMatch() }
    } else {
        items(filtered, key = { it.id }) { job ->
            JobCard(
                job = job,
                cancel = {},
                retry = { onRedownloadRequest(job) },
                remove = { onDeleteRequest(job) },
                modifier = Modifier.animateItem(),
                // Riwayat memakai pola baris ringkas; progres dan status lampiran tetap tampil.
                compact = true,
                refetchLyrics = onRefetchLyrics,
                searchLyrics = onSearchLyrics,
                play = { onPlay(filtered, filtered.indexOfFirst { it.id == job.id }) },
                enqueue = { onEnqueue(job) },
                playNext = { onPlayNext(job) },
                editLyricTiming = if (job.mode == MediaMode.AUDIO && job.lyricStatus == LyricStatus.SAVED) {
                    { onEditLyricTiming(job.id) }
                } else null,
            )
        }
    }
    item(key = "history-spacer") { Spacer(Modifier.height(4.dp)) }
}

private fun historySummary(completed: List<DownloadJob>, filtered: List<DownloadJob>): String {
    val detail = if (filtered.size != completed.size) {
        "${filtered.size} dari ${completed.size} entri"
    } else {
        val videos = completed.count { it.mode == MediaMode.VIDEO }
        val audios = completed.count { it.mode == MediaMode.AUDIO }
        buildList {
            add("${completed.size} selesai")
            if (videos > 0) add("$videos video")
            if (audios > 0) add("$audios audio")
        }.joinToString(" · ")
    }
    return "Riwayat · $detail"
}

@Composable
private fun HistorySummary(summary: String, onClearRequest: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            summary,
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = onClearRequest,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.Outlined.DeleteSweep, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Bersihkan")
        }
    }
}

@Composable
private fun HistoryNoMatch() {
    SectionCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.SearchOff, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Tidak ada yang cocok", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Coba kata kunci lain atau ubah filter di atas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
