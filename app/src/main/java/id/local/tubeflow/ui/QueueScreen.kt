package id.local.tubeflow.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.local.tubeflow.core.DownloadJob
import id.local.tubeflow.core.JobState
import id.local.tubeflow.core.Selection

@Composable
fun QueueScreen(
    jobs: List<DownloadJob>,
    listState: LazyListState,
    onCancel: (String) -> Unit,
    onRetry: (DownloadJob) -> Unit,
    onRetryAll: () -> Unit,
    onRemove: (String) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onCancelAll: () -> Unit,
    onClearUnsuccessful: () -> Unit,
) {
    val active = jobs.filter { it.state != JobState.COMPLETED }
    val running = active.count { it.state == JobState.RUNNING }
    val queued = active.count { it.state == JobState.QUEUED }
    val paused = active.count { it.state == JobState.PAUSED }
    val failed = active.count { it.state == JobState.FAILED }
    val cancelled = active.count { it.state == JobState.CANCELLED }
    val pausable = running + queued
    var confirmCancelAll by remember { mutableStateOf(false) }

    if (confirmCancelAll) {
        AlertDialog(
            onDismissRequest = { confirmCancelAll = false },
            title = { Text("Batalkan semua unduhan?") },
            text = { Text("Semua unduhan yang berjalan dan menunggu ($pausable) beserta yang dijeda ($paused) akan dibatalkan. Unduhan parsial tidak disimpan.") },
            confirmButton = { TextButton(onClick = { confirmCancelAll = false; onCancelAll() }) { Text("Batalkan semua") } },
            dismissButton = { TextButton(onClick = { confirmCancelAll = false }) { Text("Kembali") } },
        )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(UiSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PageHeading(
                "Antrean download",
                "Progres berjalan dan tersimpan di perangkat ini." + if (paused > 0) " $paused unduhan dijeda." else "",
            )
        }

        if (active.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatPill("Berjalan", running.toString(), Icons.Outlined.Downloading, modifier = Modifier.weight(1f))
                    StatPill("Menunggu", queued.toString(), Icons.Outlined.Schedule, modifier = Modifier.weight(1f))
                    StatPill("Gagal", failed.toString(), Icons.Outlined.ErrorOutline, modifier = Modifier.weight(1f), tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (pausable > 0 || paused > 0) {
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (pausable > 0) {
                        OutlinedButton(onClick = onPauseAll, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                            Icon(Icons.Outlined.Pause, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Jeda semua")
                        }
                    }
                    if (paused > 0) {
                        OutlinedButton(onClick = onResumeAll, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                            Icon(Icons.Outlined.PlayArrow, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Lanjutkan semua")
                        }
                    }
                    if (pausable > 0 || paused > 0) {
                        TextButton(onClick = { confirmCancelAll = true }) {
                            Text("Batalkan semua", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        if (failed > 0 || cancelled > 0) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = if (failed > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Warning, null, Modifier.size(20.dp), tint = if (failed > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            (if (failed > 0) "$failed unduhan gagal" else "") +
                                (if (failed > 0 && cancelled > 0) " · " else "") +
                                (if (cancelled > 0) "$cancelled dibatalkan" else ""),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (failed > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (failed > 0) TextButton(onClick = onRetryAll) { Text("Ulangi semua", color = if (failed > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary) }
                        TextButton(onClick = onClearUnsuccessful) { Text("Bersihkan", color = if (failed > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary) }
                    }
                }
            }
        }

        if (active.isEmpty()) {
            item {
                EmptyState(
                    Icons.Outlined.Queue,
                    "Antrean masih kosong",
                    "Pilih audio atau video di tab Download untuk memulai. Unduhan tetap berjalan walau aplikasi ditutup.",
                )
            }
        } else {
            items(active, key = { it.id }) { job ->
                JobCard(
                    job = job,
                    cancel = { onCancel(job.id) },
                    retry = { onRetry(job) },
                    remove = if (job.state == JobState.FAILED || job.state == JobState.CANCELLED) ({ onRemove(job.id) }) else null,
                    pause = if (job.state == JobState.QUEUED || job.state == JobState.RUNNING) ({ onPause(job.id) }) else null,
                    resume = if (job.state == JobState.PAUSED) ({ onResume(job.id) }) else null,
                    // Platform sumber tampil di antrean DAN riwayat dari sumber data yang sama (P2/P3).
                    modifier = Modifier.animateItem(),
                )
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}
