package id.local.tubeflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.local.tubeflow.ui.theme.LocalTransformersTheme
import id.local.tubeflow.core.DownloadJob
import id.local.tubeflow.core.DownloadSections
import id.local.tubeflow.core.JobState

/** Isi tab Download bagian Antrean, dipakai di dalam satu LazyColumn host. */
fun LazyListScope.queueSection(
    jobs: List<DownloadJob>,
    highlightedJobId: String?,
    onCancel: (String) -> Unit,
    onRetry: (DownloadJob) -> Unit,
    onRetryAll: () -> Unit,
    onRemove: (String) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onCancelAllRequest: () -> Unit,
    onClearUnsuccessful: () -> Unit,
    query: String = "",
) {
    val all = DownloadSections.queue(jobs)
    val active = if (query.isBlank()) all else all.filter { it.title.contains(query.trim(), ignoreCase = true) }
    val running = all.count { it.state == JobState.RUNNING }
    val queued = all.count { it.state == JobState.QUEUED }
    val paused = all.count { it.state == JobState.PAUSED }
    val failed = all.count { it.state == JobState.FAILED }
    val cancelled = all.count { it.state == JobState.CANCELLED }
    val pausable = running + queued
    val summary = buildList {
        if (running > 0) add("$running berjalan")
        if (queued > 0) add("$queued menunggu")
        if (paused > 0) add("$paused dijeda")
    }.joinToString(" · ")

    if (summary.isNotEmpty()) {
        item(key = "queue-summary") {
            QueueSummary(
                summary = "Antrean · $summary",
                pausable = pausable,
                paused = paused,
                showBulkActions = all.size > 1,
                onPauseAll = onPauseAll,
                onResumeAll = onResumeAll,
                onCancelAllRequest = onCancelAllRequest,
            )
        }
    }

    if (failed > 0 || cancelled > 0) {
        item(key = "queue-attention") {
            AttentionCard(
                failed = failed,
                cancelled = cancelled,
                prefix = if (summary.isEmpty()) "Antrean · " else "",
                onRetryAll = onRetryAll,
                onClearUnsuccessful = onClearUnsuccessful,
            )
        }
    }

    items(active, key = { it.id }) { job ->
        JobCard(
            job = job,
            cancel = { onCancel(job.id) },
            retry = { onRetry(job) },
            remove = if (job.state == JobState.FAILED || job.state == JobState.CANCELLED) ({ onRemove(job.id) }) else null,
            pause = if (job.state == JobState.QUEUED || job.state == JobState.RUNNING) ({ onPause(job.id) }) else null,
            resume = if (job.state == JobState.PAUSED) ({ onResume(job.id) }) else null,
            modifier = Modifier.animateItem(),
            highlighted = job.id == highlightedJobId,
            // Antrean unduhan memakai baris ringkas yang sama dengan Riwayat dan Koleksi.
            compact = true,
        )
    }
    if (query.isNotBlank() && active.isEmpty() && all.isNotEmpty()) {
        item(key = "queue-no-match") {
            Text(
                "Tidak ada antrean yang cocok dengan pencarian.",
                Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    item(key = "queue-spacer") { Spacer(Modifier.height(4.dp)) }
}

@Composable
private fun QueueSummary(
    summary: String,
    pausable: Int,
    paused: Int,
    showBulkActions: Boolean,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onCancelAllRequest: () -> Unit,
) {
    if (LocalTransformersTheme.current) {
        val scheme = MaterialTheme.colorScheme
        val shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
        Row(
            Modifier.fillMaxWidth()
                .clip(shape)
                .background(
                    Brush.horizontalGradient(
                        listOf(scheme.secondary.copy(alpha = 0.28f), scheme.surfaceContainerHigh, scheme.primary.copy(alpha = 0.14f)),
                    ),
                )
                .border(
                    1.dp,
                    Brush.horizontalGradient(listOf(scheme.secondary.copy(alpha = 0.65f), scheme.primary.copy(alpha = 0.45f))),
                    shape,
                )
                .padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Download, null, Modifier.size(18.dp), tint = scheme.primary)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Antrean", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    summary.removePrefix("Antrean · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (showBulkActions) {
                if (pausable > 0) IconAction(Icons.Outlined.Pause, "Jeda semua", onPauseAll)
                if (paused > 0) IconAction(Icons.Outlined.PlayArrow, "Lanjutkan semua", onResumeAll)
                IconAction(Icons.Outlined.Close, "Batalkan semua", onCancelAllRequest, destructive = true)
            }
        }
        return
    }
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
        if (showBulkActions) {
            if (pausable > 0) IconAction(Icons.Outlined.Pause, "Jeda semua", onPauseAll)
            if (paused > 0) IconAction(Icons.Outlined.PlayArrow, "Lanjutkan semua", onResumeAll)
            IconAction(Icons.Outlined.Close, "Batalkan semua", onCancelAllRequest, destructive = true)
        }
    }
}

@Composable
private fun AttentionCard(
    failed: Int,
    cancelled: Int,
    prefix: String,
    onRetryAll: () -> Unit,
    onClearUnsuccessful: () -> Unit,
) {
    val hasFailure = failed > 0
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (hasFailure) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Outlined.Warning,
                null,
                Modifier.size(18.dp),
                tint = if (hasFailure) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                prefix + buildList {
                    if (failed > 0) add("$failed gagal")
                    if (cancelled > 0) add("$cancelled dibatalkan")
                }.joinToString(" · "),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = if (hasFailure) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hasFailure) IconAction(Icons.Outlined.Refresh, "Ulangi semua", onRetryAll)
            IconAction(Icons.Outlined.DeleteSweep, "Bersihkan", onClearUnsuccessful, destructive = true)
        }
    }
}
