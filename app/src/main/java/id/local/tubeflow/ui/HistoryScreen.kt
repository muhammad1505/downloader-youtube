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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.local.tubeflow.core.sourcePlatform
import id.local.tubeflow.core.DownloadJob
import id.local.tubeflow.core.JobState
import id.local.tubeflow.core.MediaMode
import id.local.tubeflow.core.Selection
import id.local.tubeflow.core.SettingsStore

@Composable
fun HistoryScreen(
    jobs: List<DownloadJob>,
    listState: LazyListState,
    onRetry: (DownloadJob) -> Unit,
    onDeleteFile: (String) -> Unit,
    onRefetchLyrics: (String) -> Unit,
    onSearchLyrics: (String) -> Unit,
    onPlay: (List<DownloadJob>, Int) -> Unit = { _, _ -> },
    onClearCompleted: () -> Unit,
    onClearUnsuccessful: () -> Unit,
) {
    val completed = jobs.filter { it.state == JobState.COMPLETED }
    val unsuccessful = jobs.count { it.state == JobState.FAILED || it.state == JobState.CANCELLED }
    var query by rememberSaveable { mutableStateOf("") }
    var modeFilter by rememberSaveable { mutableIntStateOf(-1) }
    var platformFilter by rememberSaveable { mutableStateOf("") }
    // Kategori asal tautan (YouTube, TikTok, SoundCloud, …) — hanya platform yang benar-benar ada di riwayat.
    val platforms = completed.map { it.sourcePlatform.label }.distinct().sorted()
    var confirmClear by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DownloadJob?>(null) }
    var pendingRedownload by remember { mutableStateOf<DownloadJob?>(null) }

    val filtered = completed.filter { job ->
        val matchesQuery = query.isBlank() ||
            job.title.contains(query, ignoreCase = true) ||
            job.url.contains(query, ignoreCase = true)
        val matchesMode = when (modeFilter) {
            0 -> job.mode == MediaMode.VIDEO
            1 -> job.mode == MediaMode.AUDIO
            else -> true
        }
        val matchesPlatform = platformFilter.isEmpty() || job.sourcePlatform.label == platformFilter
        matchesQuery && matchesMode && matchesPlatform
    }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Hapus file?") },
            text = { Text("\"${pendingDelete?.title}\" beserta file pendukungnya (lirik/thumbnail) akan dihapus dari penyimpanan, lalu entri riwayat ikut dihilangkan.") },
            confirmButton = { TextButton(onClick = { pendingDelete?.let { onDeleteFile(it.id) }; pendingDelete = null }) { Text("Hapus file") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Batal") } },
        )
    }

    if (pendingRedownload != null) {
        AlertDialog(
            onDismissRequest = { pendingRedownload = null },
            title = { Text("Unduh lagi?") },
            text = { Text("File lama \"${pendingRedownload?.title}\" beserta file pendukungnya (lirik/thumbnail) akan dihapus, lalu unduhan yang sama diantrekan ulang sebagai penggantinya.") },
            confirmButton = { TextButton(onClick = { pendingRedownload?.let { onRetry(it) }; pendingRedownload = null }) { Text("Unduh lagi") } },
            dismissButton = { TextButton(onClick = { pendingRedownload = null }) { Text("Batal") } },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Bersihkan riwayat?") },
            text = { Text("Semua entri yang selesai akan dihapus dari daftar. File hasil download tetap tersimpan di Downloads/${SettingsStore.storageFolder}.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; onClearCompleted() }) { Text("Bersihkan") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Batal") } },
        )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(UiSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { PageHeading(androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.page_history), androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.page_history_help)) }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill("Selesai", completed.size.toString(), Icons.Outlined.CheckCircle, modifier = Modifier.weight(1f), tint = MaterialTheme.colorScheme.secondary)
                StatPill("Video", completed.count { it.mode == MediaMode.VIDEO }.toString(), Icons.Outlined.Videocam, modifier = Modifier.weight(1f))
                StatPill("Audio", completed.count { it.mode == MediaMode.AUDIO }.toString(), Icons.Outlined.LibraryMusic, modifier = Modifier.weight(1f))
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Cari judul atau link") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Bersihkan pencarian") }
                },
            )
        }

        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = modeFilter == -1, onClick = { modeFilter = -1 }, label = { Text("Semua") })
                FilterChip(selected = modeFilter == 0, onClick = { modeFilter = 0 }, label = { Text("Video") })
                FilterChip(selected = modeFilter == 1, onClick = { modeFilter = 1 }, label = { Text("Audio") }) }
        }

        if (platforms.size > 1) {
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = platformFilter.isEmpty(), onClick = { platformFilter = "" }, label = { Text("Semua platform") })
                    platforms.forEach { name ->
                        FilterChip(selected = platformFilter == name, onClick = { platformFilter = name }, label = { Text(name) })
                    }
                }
            }
        }

        if (completed.isNotEmpty() || unsuccessful > 0) {
            item {
                FlowRow(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${filtered.size} entri ditampilkan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row {
                        if (unsuccessful > 0) TextButton(onClick = onClearUnsuccessful) { Text("Hapus gagal ($unsuccessful)") }
                        if (completed.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Bersihkan") }
                    }
                }
            }
        }

        when {
            completed.isEmpty() && unsuccessful == 0 -> item {
                EmptyState(
                    Icons.Outlined.History,
                    "Belum ada download",
                    "Hasil download yang selesai akan muncul di sini beserta detail waktunya.",
                )
            }
            filtered.isEmpty() -> item {
                EmptyState(
                    Icons.Outlined.SearchOff,
                    "Tidak ada yang cocok",
                    "Coba kata kunci lain atau ubah filter di atas.",
                )
            }
            else -> items(filtered, key = { it.id }) { job ->
                JobCard(
                    job = job,
                    cancel = {},
                    retry = { pendingRedownload = job }, // konfirmasi dulu: unduh ulang mengganti file lama (BUG-026)
                    remove = { pendingDelete = job },
                    modifier = Modifier.animateItem(),
                    refetchLyrics = onRefetchLyrics,
                    searchLyrics = onSearchLyrics,
                    play = { onPlay(filtered, filtered.indexOf(job)) },
                )
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}
