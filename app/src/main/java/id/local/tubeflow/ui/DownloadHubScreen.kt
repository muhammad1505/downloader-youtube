package id.local.tubeflow.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.local.tubeflow.R
import id.local.tubeflow.core.DownloadJob
import id.local.tubeflow.core.DownloadOptions
import id.local.tubeflow.core.DownloadSections
import id.local.tubeflow.core.DownloadTarget
import id.local.tubeflow.core.EngineUpdateState
import id.local.tubeflow.core.EngineUpdater
import id.local.tubeflow.core.FailureDetails
import id.local.tubeflow.core.JobState
import id.local.tubeflow.core.PlaylistEntry
import id.local.tubeflow.core.PlaylistMetadata
import id.local.tubeflow.core.SettingsStore
import id.local.tubeflow.core.SpotifyCandidate
import id.local.tubeflow.core.SpotifyCollectionPreview
import id.local.tubeflow.core.SpotifyPreview
import id.local.tubeflow.core.SpotifyTrack
import id.local.tubeflow.core.VideoMetadata
import kotlinx.coroutines.launch

data class QueueActions(
    val cancel: (String) -> Unit,
    val retry: (DownloadJob) -> Unit,
    val retryAll: () -> Unit,
    val remove: (String) -> Unit,
    val pause: (String) -> Unit,
    val resume: (String) -> Unit,
    val pauseAll: () -> Unit,
    val resumeAll: () -> Unit,
    val cancelAll: () -> Unit,
    val clearUnsuccessful: () -> Unit,
)

data class HistoryActions(
    val redownload: (DownloadJob) -> Unit,
    val deleteFile: (String) -> Unit,
    val refetchLyrics: (String) -> Unit,
    val searchLyrics: (String) -> Unit,
    val play: (List<DownloadJob>, Int) -> Unit,
    val clearCompleted: () -> Unit,
    val enqueue: (DownloadJob) -> Unit,
    val playNext: (DownloadJob) -> Unit,
    val editLyricTiming: (String) -> Unit,
)

@Composable
private fun DownloadSearchHeader(query: String, onQueryChange: (String) -> Unit) {
    SearchField(
        value = query,
        onValueChange = onQueryChange,
        label = "Cari judul",
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadScreen(
    jobs: List<DownloadJob>,
    highlightedJobId: String?,
    url: String,
    onUrl: (String) -> Unit,
    metadata: VideoMetadata?,
    playlist: PlaylistMetadata?,
    busy: Boolean,
    inspectPhase: String?,
    inspectStartedAt: Long,
    inspectError: String?,
    inspectDetails: FailureDetails? = null,
    bstationSignedIn: Boolean = false,
    onBstationLogin: () -> Unit = {},
    inspect: () -> Unit,
    inspectPlaylist: () -> Unit,
    retryInspect: () -> Unit,
    clearPlaylist: () -> Unit,
    cancelInspect: () -> Unit,
    download: (DownloadOptions) -> Unit,
    downloadPlaylist: (List<PlaylistEntry>, DownloadOptions) -> Unit,
    spotify: SpotifyPreview = SpotifyPreview(),
    inspectSpotify: () -> Unit = {},
    clearSpotify: () -> Unit = {},
    downloadSpotify: (DownloadOptions, SpotifyCandidate) -> Unit = { _, _ -> },
    spotifyCollection: SpotifyCollectionPreview = SpotifyCollectionPreview(),
    inspectSpotifyCollection: () -> Unit = {},
    clearSpotifyCollection: () -> Unit = {},
    matchSpotifyTracks: (List<SpotifyTrack>) -> Unit = {},
    cancelSpotifyMatch: () -> Unit = {},
    downloadSpotifyCollection: (DownloadOptions, List<SpotifyTrack>) -> Unit = { _, _ -> },
    queueActions: QueueActions,
    historyActions: HistoryActions,
    downloadTarget: DownloadTarget? = null,
    onDownloadTargetHandled: () -> Unit = {},
    onEngineUpdate: () -> Unit = {},
) {
    var queueQuery by rememberSaveable { mutableStateOf("") }
    var historyQuery by rememberSaveable { mutableStateOf("") }
    var activeSection by rememberSaveable { mutableIntStateOf(0) }
    var historyMode by rememberSaveable { mutableIntStateOf(-1) }
    var historyPlatform by rememberSaveable { mutableStateOf("") }
    var confirmCancelAll by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DownloadJob?>(null) }
    var pendingRedownload by remember { mutableStateOf<DownloadJob?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val queueJobs = DownloadSections.queue(jobs)
    val historyJobs = DownloadSections.history(jobs)
    val hasQueue = queueJobs.isNotEmpty()
    val hasHistory = historyJobs.isNotEmpty()
    val historyOnly = downloadTarget == DownloadTarget.HISTORY
    val showSections = !historyOnly && (hasQueue || hasHistory)
    val engineState by EngineUpdater.state.collectAsStateWithLifecycle()
    // Alasan tunggu hanya dipantau saat ada baris QUEUED; teksnya mengikuti kondisi perangkat.
    val queuedHint = rememberQueuedHint(enabled = jobs.any { it.state == JobState.QUEUED })
    // Posisi baris tab: heading, banner pembaruan (opsional), lalu composer.
    val tabsIndex = if (engineState is EngineUpdateState.Available) 3 else 2
    var previousHistoryOnly by remember { mutableStateOf(false) }
    LaunchedEffect(historyOnly, downloadTarget) {
        val wasHistoryOnly = previousHistoryOnly
        previousHistoryOnly = historyOnly
        when {
            historyOnly -> {
                activeSection = 1
                if (!wasHistoryOnly) listState.scrollToItem(0)
            }
            downloadTarget == DownloadTarget.COMPOSER -> {
                activeSection = 0
                listState.animateScrollToItem(1)
            }
            wasHistoryOnly -> {
                activeSection = 0
                listState.scrollToItem(0)
            }
        }
    }
    BackHandler(enabled = historyOnly) { onDownloadTargetHandled() }
    val historyContent: LazyListScope.() -> Unit = {
        historySection(
            jobs = jobs,
            query = historyQuery,
            modeFilter = historyMode,
            platformFilter = historyPlatform,
            onQueryChange = { historyQuery = it },
            onModeFilterChange = { historyMode = it },
            onPlatformFilterChange = { historyPlatform = it },
            onClearRequest = { confirmClearHistory = true },
            onDeleteRequest = { pendingDelete = it },
            onRedownloadRequest = { pendingRedownload = it },
            onRefetchLyrics = historyActions.refetchLyrics,
            onSearchLyrics = historyActions.searchLyrics,
            onPlay = historyActions.play,
            onEnqueue = historyActions.enqueue,
            onPlayNext = historyActions.playNext,
            onEditLyricTiming = historyActions.editLyricTiming,
        )
    }

    if (confirmCancelAll) {
        val active = DownloadSections.queue(jobs)
        val pausable = active.count { it.state == JobState.RUNNING || it.state == JobState.QUEUED }
        val paused = active.count { it.state == JobState.PAUSED }
        ConfirmDialog(
            title = "Batalkan semua unduhan?",
            text = "Semua unduhan yang berjalan dan menunggu ($pausable) beserta yang dijeda ($paused) akan dibatalkan. Unduhan parsial tidak disimpan.",
            confirmLabel = "Batalkan semua",
            onConfirm = { confirmCancelAll = false; queueActions.cancelAll() },
            onDismiss = { confirmCancelAll = false },
            destructive = true,
        )
    }

    if (confirmClearHistory) {
        ConfirmDialog(
            title = "Bersihkan riwayat?",
            text = "Semua entri yang selesai akan dihapus dari daftar. File hasil download tetap tersimpan di Downloads/${SettingsStore.storageFolder}.",
            confirmLabel = "Bersihkan",
            onConfirm = { confirmClearHistory = false; historyActions.clearCompleted() },
            onDismiss = { confirmClearHistory = false },
            destructive = true,
        )
    }

    pendingDelete?.let { job ->
        ConfirmDialog(
            title = "Hapus file?",
            text = "\"${job.title}\" beserta file pendukungnya (lirik/thumbnail) akan dihapus dari penyimpanan, lalu entri riwayat ikut dihilangkan.",
            confirmLabel = "Hapus file",
            onConfirm = { pendingDelete = null; historyActions.deleteFile(job.id) },
            onDismiss = { pendingDelete = null },
            destructive = true,
        )
    }

    pendingRedownload?.let { job ->
        ConfirmDialog(
            title = "Unduh lagi?",
            text = "File lama \"${job.title}\" beserta file pendukungnya (lirik/thumbnail) akan dihapus, lalu unduhan yang sama diantrekan ulang sebagai penggantinya.",
            confirmLabel = "Unduh lagi",
            onConfirm = { pendingRedownload = null; historyActions.redownload(job) },
            onDismiss = { pendingRedownload = null },
            destructive = true,
        )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(UiSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.item),
    ) {
        if (historyOnly) {
            item(key = "history-heading") {
                PageHeading(
                    "Riwayat unduhan",
                    "Unduhan yang selesai. File tetap tersimpan di perangkat.",
                    Icons.Outlined.History,
                )
            }
            item(key = "history-back") {
                TextButton(
                    onClick = onDownloadTargetHandled,
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Kembali ke unduhan")
                }
            }
        }
        if (historyOnly && hasHistory) {
            stickyHeader(key = "history-search") {
                DownloadSearchHeader(historyQuery) { historyQuery = it }
            }
        }
        if (!historyOnly) item(key = "download-heading") {
            PageHeading(
                stringResource(R.string.page_download),
                stringResource(R.string.download_section_help),
                Icons.Outlined.Download,
            )
        }

        if (!historyOnly && engineState is EngineUpdateState.Available) item(key = "engine-update") {
            EngineUpdateBanner(onEngineUpdate)
        }

        if (!historyOnly) item(key = "download-composer") {
            DownloadComposer(
                url = url,
                onUrl = onUrl,
                metadata = metadata,
                playlist = playlist,
                busy = busy,
                inspectPhase = inspectPhase,
                inspectStartedAt = inspectStartedAt,
                inspectError = inspectError,
                inspectDetails = inspectDetails,
                bstationSignedIn = bstationSignedIn,
                onBstationLogin = onBstationLogin,
                inspect = inspect,
                inspectPlaylist = inspectPlaylist,
                retryInspect = retryInspect,
                clearPlaylist = clearPlaylist,
                cancelInspect = cancelInspect,
                download = download,
                downloadPlaylist = downloadPlaylist,
                spotify = spotify,
                inspectSpotify = inspectSpotify,
                clearSpotify = clearSpotify,
                downloadSpotify = downloadSpotify,
                spotifyCollection = spotifyCollection,
                inspectSpotifyCollection = inspectSpotifyCollection,
                clearSpotifyCollection = clearSpotifyCollection,
                matchSpotifyTracks = matchSpotifyTracks,
                cancelSpotifyMatch = cancelSpotifyMatch,
                downloadSpotifyCollection = downloadSpotifyCollection,
                focusComposer = downloadTarget == DownloadTarget.COMPOSER,
                onFocusHandled = onDownloadTargetHandled,
            )
        }

        if (showSections) {
            stickyHeader(key = "download-tabs") {
                DownloadSectionTabs(
                    selected = activeSection,
                    queueCount = queueJobs.size,
                    historyCount = historyJobs.size,
                    onSelect = { index ->
                        if (activeSection != index) {
                            activeSection = index
                            scope.launch {
                                if (listState.firstVisibleItemIndex > tabsIndex) {
                                    listState.animateScrollToItem(tabsIndex)
                                }
                            }
                        }
                    },
                )
            }
        }

        // Tiap tab punya kata kunci sendiri supaya pindah tab tidak menyaring daftar lainnya.
        val sectionHasItems = if (activeSection == 0) hasQueue else hasHistory
        if (!historyOnly && showSections && sectionHasItems) {
            item(key = "download-search") {
                if (activeSection == 0) DownloadSearchHeader(queueQuery) { queueQuery = it }
                else DownloadSearchHeader(historyQuery) { historyQuery = it }
            }
        }

        if (!historyOnly && activeSection == 0) {
            if (hasQueue) {
                queueSection(
                    jobs = jobs,
                    query = queueQuery,
                    highlightedJobId = highlightedJobId,
                    onCancel = queueActions.cancel,
                    onRetry = queueActions.retry,
                    onRetryAll = queueActions.retryAll,
                    onRemove = queueActions.remove,
                    onPause = queueActions.pause,
                    onResume = queueActions.resume,
                    onPauseAll = queueActions.pauseAll,
                    onResumeAll = queueActions.resumeAll,
                    onCancelAllRequest = { confirmCancelAll = true },
                    onClearUnsuccessful = queueActions.clearUnsuccessful,
                    queuedHint = queuedHint,
                )
            } else if (hasHistory) {
                item(key = "queue-empty") {
                    Text(
                        "Antrean kosong. Unduhan yang selesai ada di tab Riwayat.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (historyOnly || (!historyOnly && activeSection == 1)) {
            if (hasHistory) {
                historyContent()
            } else {
                item(key = "history-empty") {
                    Text(
                        "Belum ada unduhan yang selesai.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Dua tab isi unduhan: Antrean dan Riwayat tidak lagi ditumpuk dalam satu gulungan. */
@Composable
private fun DownloadSectionTabs(
    selected: Int,
    queueCount: Int,
    historyCount: Int,
    onSelect: (Int) -> Unit,
) {
    PrimaryTabRow(
        selectedTabIndex = selected,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Tab(
            selected = selected == 0,
            onClick = { onSelect(0) },
            text = { Text(if (queueCount > 0) "Antrean · $queueCount" else "Antrean") },
        )
        Tab(
            selected = selected == 1,
            onClick = { onSelect(1) },
            text = { Text(if (historyCount > 0) "Riwayat · $historyCount" else "Riwayat") },
        )
    }
}
