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
    onOpenDownload: () -> Unit = {},
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
        ConfirmDialog(
            title = "Hapus file?",
            text = "\"${pendingDelete?.title}\" beserta file pendukungnya (lirik/thumbnail) akan dihapus dari penyimpanan, lalu entri riwayat ikut dihilangkan.",
            confirmLabel = "Hapus file",
            onConfirm = { pendingDelete?.let { onDeleteFile(it.id) }; pendingDelete = null },
            onDismiss = { pendingDelete = null },
            destructive = true,
        )
    }

    if (pendingRedownload != null) {
        ConfirmDialog(
            title = "Unduh lagi?",
            text = "File lama \"${pendingRedownload?.title}\" beserta file pendukungnya (lirik/thumbnail) akan dihapus, lalu unduhan yang sama diantrekan ulang sebagai penggantinya.",
            confirmLabel = "Unduh lagi",
            onConfirm = { pendingRedownload?.let { onRetry(it) }; pendingRedownload = null },
            onDismiss = { pendingRedownload = null },
            destructive = true,
        )
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Bersihkan riwayat?",
            text = "Semua entri yang selesai akan dihapus dari daftar. File hasil download tetap tersimpan di Downloads/${SettingsStore.storageFolder}.",
            confirmLabel = "Bersihkan",
            onConfirm = { confirmClear = false; onClearCompleted() },
            onDismiss = { confirmClear = false },
            destructive = true,
        )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(UiSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.item),
    ) {
        item { PageHeading(androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.page_history), androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.page_history_help)) }

        item {
            StatRow(listOf(
                StatSpec("Selesai", completed.size.toString(), Icons.Outlined.CheckCircle, MaterialTheme.colorScheme.secondary),
                StatSpec("Video", completed.count { it.mode == MediaMode.VIDEO }.toString(), Icons.Outlined.Videocam),
                StatSpec("Audio", completed.count { it.mode == MediaMode.AUDIO }.toString(), Icons.Outlined.LibraryMusic),
            ))
        }

        item { SearchField(query, { query = it }, label = "Cari judul atau link") }

        item {
            ScrollRow {
                FilterChip(selected = modeFilter == -1, onClick = { modeFilter = -1 }, label = { Text("Semua") })
                FilterChip(selected = modeFilter == 0, onClick = { modeFilter = 0 }, label = { Text("Video") })
                FilterChip(selected = modeFilter == 1, onClick = { modeFilter = 1 }, label = { Text("Audio") }) }
        }

        if (platforms.size > 1) {
            item {
                ScrollRow {
                    FilterChip(selected = platformFilter.isEmpty(), onClick = { platformFilter = "" }, label = { Text("Semua platform") })
                    platforms.forEach { name ->
                        FilterChip(selected = platformFilter == name, onClick = { platformFilter = name }, label = { Text(name) })
                    }
                }
            }
        }

        if (completed.isNotEmpty() || unsuccessful > 0) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${filtered.size} entri ditampilkan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ScrollRow {
                        if (unsuccessful > 0) ActionButton("Hapus gagal ($unsuccessful)", onClearUnsuccessful, emphasis = ActionEmphasis.Quiet, destructive = true)
                        if (completed.isNotEmpty()) ActionButton("Bersihkan", { confirmClear = true }, emphasis = ActionEmphasis.Quiet, destructive = true)
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
                    actionLabel = "Buka Download",
                    onAction = onOpenDownload,
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
