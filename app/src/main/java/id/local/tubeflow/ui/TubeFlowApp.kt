package id.local.tubeflow.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.local.tubeflow.core.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TubeFlowApp(model: DownloadViewModel, incomingUrl: String, requestNotifications: () -> Unit = {}) {
    val jobs by model.jobs.collectAsStateWithLifecycle()
    val metadata by model.metadata.collectAsStateWithLifecycle()
    val playlist by model.playlist.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val inspectError by model.inspectError.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val notice by model.notice.collectAsStateWithLifecycle()
    val storageBytes by model.storageBytes.collectAsStateWithLifecycle()
    val storageError by model.storageError.collectAsStateWithLifecycle()
    val storageFolderAccess by model.storageFolderAccess.collectAsStateWithLifecycle()
    val clearingFiles by model.clearing.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var url by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val screenState = rememberSaveableStateHolder()
    val downloadList = rememberLazyListState()
    val queueList = rememberLazyListState()
    val historyList = rememberLazyListState()

    LaunchedEffect(incomingUrl) {
        if (incomingUrl.isNotBlank()) {
            url = incomingUrl
            tab = 0
            // Inspeksi diserahkan ke efek DownloadScreen (video vs playlist) — memanggil inspect langsung di sini
            // membuat tautan playlist murni memunculkan "tautan tidak valid" sesaat dan video diperiksa dua kali (BUG-022).
        }
    }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); model.clearError() } }
    // Info "Terpakai …" di Pengaturan ikut terbaru tiap tab dibuka, bukan cuma pas aplikasi start.
    LaunchedEffect(tab) { if (tab == 3) model.refreshStorage() }
    LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it); model.clearNotice() } }

    val activeCount = jobs.count { it.state == JobState.RUNNING || it.state == JobState.QUEUED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("TubeFlow", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (activeCount > 0) "$activeCount unduhan sedang berjalan" else "Simpan momen. Putar kapan saja.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = { },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val tabs = listOf("Download", "Antrean", "Riwayat", "Pengaturan")
                val icons = listOf(Icons.Outlined.Download, Icons.Outlined.Queue, Icons.Outlined.History, Icons.Outlined.Settings)
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = {
                            if (index == 1 && activeCount > 0) {
                                BadgedBox(badge = { Badge { Text(if (activeCount > 99) "99+" else "$activeCount") } }) {
                                    Icon(icons[index], contentDescription = null)
                                }
                            } else {
                                Icon(icons[index], contentDescription = null)
                            }
                        },
                        label = { Text(title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.padding(padding),
            transitionSpec = {
                (fadeIn(tween(220)) + slideInHorizontally(tween(240)) { it / 8 }) togetherWith
                    (fadeOut(tween(160)) + slideOutHorizontally(tween(240)) { -it / 8 })
            },
            label = "tab",
        ) { current ->
            screenState.SaveableStateProvider("screen-$current") {
            when (current) {
                0 -> DownloadScreen(
                    url = url,
                    onUrl = { url = it },
                    metadata = metadata,
                    playlist = playlist,
                    busy = busy,
                    inspectError = inspectError,
                    listState = downloadList,
                    inspect = { model.inspect(url.trim()) },
                    inspectPlaylist = { model.inspectPlaylist(url.trim()) },
                    retryInspect = { model.retryInspect(url.trim()) },
                    clearPlaylist = { model.clearPlaylist() },
                    download = { opts ->
                        requestNotifications()
                        model.enqueue(opts.copy(url = url.trim()))
                        url = ""
                        model.clearPlaylist()
                        tab = 1
                    },
                    downloadPlaylist = { entries, opts ->
                        requestNotifications()
                        // Judul playlist ikut diambil SEBELUM panel dibersihkan — jadi nama subfolder otomatis.
                        model.queuePlaylist(entries, opts, playlist?.title)
                        url = ""
                        model.clearPlaylist()
                        tab = 1
                    },
                )
                1 -> QueueScreen(
                    jobs = jobs,
                    listState = queueList,
                    onCancel = { model.cancel(it) },
                    onRetry = { model.retry(it) },
                    onRetryAll = { model.retryFailed() },
                    onRemove = { model.remove(it) },
                    onPause = { model.pause(it) },
                    onResume = { model.resume(it) },
                    onPauseAll = { model.pauseAll() },
                    onResumeAll = { model.resumeAll() },
                    onCancelAll = { model.cancelAll() },
                )
                2 -> HistoryScreen(
                    jobs = jobs,
                    listState = historyList,
                    onRetry = { model.retry(it) },
                    onDeleteFile = { model.removeFile(it) },
                    onRefetchLyrics = { model.refetchLyrics(it) },
                    onClearCompleted = { model.clearCompleted() },
                    onClearUnsuccessful = { model.clearUnsuccessful() },
                )
                else -> SettingsScreen(
                    themeMode = SettingsStore.themeMode,
                    onThemeMode = { SettingsStore.setThemeMode(context, it) },
                    dynamicColor = SettingsStore.dynamicColor,
                    onDynamicColor = { SettingsStore.setDynamicColor(context, it) },
                    storageBytes = storageBytes,
                    storageError = storageError,
                    storageFolderAccess = storageFolderAccess,
                    onRefreshStorage = { model.refreshStorage() },
                    clearingFiles = clearingFiles,
                    onClearStorage = { model.clearStorageFolder() },
                )
            }
            }
        }
    }
}
