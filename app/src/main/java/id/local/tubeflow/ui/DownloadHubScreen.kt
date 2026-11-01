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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    var historyQuery by rememberSaveable { mutableStateOf("") }
    var historyMode by rememberSaveable { mutableIntStateOf(-1) }
    var historyPlatform by rememberSaveable { mutableStateOf("") }
    var confirmCancelAll by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DownloadJob?>(null) }
    var pendingRedownload by remember { mutableStateOf<DownloadJob?>(null) }
    val listState = rememberLazyListState()
    val hasQueue = DownloadSections.queue(jobs).isNotEmpty()
    val hasHistory = DownloadSections.history(jobs).isNotEmpty()
    val historyOnly = downloadTarget == DownloadTarget.HISTORY
    val engineState by EngineUpdater.state.collectAsStateWithLifecycle()
    var previousHistoryOnly by remember { mutableStateOf(false) }
    LaunchedEffect(historyOnly, downloadTarget) {
        val wasHistoryOnly = previousHistoryOnly
        previousHistoryOnly = historyOnly
        when {
            downloadTarget == DownloadTarget.COMPOSER -> listState.animateScrollToItem(1)
            historyOnly != wasHistoryOnly -> listState.scrollToItem(0)
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

        if (!historyOnly && (hasQueue || hasHistory)) {
            stickyHeader(key = "download-search") {
                DownloadSearchHeader(historyQuery) { historyQuery = it }
            }
        }

        if (!historyOnly && hasQueue) {
            queueSection(
                jobs = jobs,
                query = historyQuery,
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
            )
        }

        if (!historyOnly && hasQueue && hasHistory) {
            item(key = "download-divider") { HorizontalDivider() }
        }

        if (hasHistory) {
            historyContent()
        } else if (historyOnly) {
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
