package id.local.tubeflow.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
fun TubeFlowApp(model: DownloadViewModel, library: MediaLibraryViewModel, player: PlayerViewModel, karaoke: KaraokeViewModel, incomingUrl: String, requestNotifications: () -> Unit = {}) {
    val playback by player.state.collectAsStateWithLifecycle()
    var fullPlayer by rememberSaveable { mutableStateOf(false) }
    var previousTab by rememberSaveable { mutableIntStateOf(1) }
    val jobs by model.jobs.collectAsStateWithLifecycle()
    val metadata by model.metadata.collectAsStateWithLifecycle()
    val playlist by model.playlist.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val inspectError by model.inspectError.collectAsStateWithLifecycle()
    val inspectDetails by model.inspectDetails.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val notice by model.notice.collectAsStateWithLifecycle()
    val storageBytes by model.storageBytes.collectAsStateWithLifecycle()
    val storageError by model.storageError.collectAsStateWithLifecycle()
    val storageFolderAccess by model.storageFolderAccess.collectAsStateWithLifecycle()
    val storageCatalogue by model.storageCatalogue.collectAsStateWithLifecycle()
    val storageUnknownSizes by model.storageUnknownSizes.collectAsStateWithLifecycle()
    val clearingFiles by model.clearing.collectAsStateWithLifecycle()
    val temporaryUsage by model.temporaryUsage.collectAsStateWithLifecycle()
    val cleaningPartial by model.cleaningPartial.collectAsStateWithLifecycle()
    val libraryMessage by library.message.collectAsStateWithLifecycle()
    // Riwayat adalah tujuan navigasi sendiri (tab 4), bukan chip di dalam Koleksi; Pengaturan
    // tetap di luar bilah bawah lewat ikon di app bar sehingga punya indeks terpisah.
    val settingsTab = 5
    var tab by rememberSaveable { mutableIntStateOf(1) }
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
            tab = 1
            // Inspeksi diserahkan ke efek DownloadScreen (video vs playlist) — memanggil inspect langsung di sini
            // membuat tautan playlist murni memunculkan "tautan tidak valid" sesaat dan video diperiksa dua kali (BUG-022).
        }
    }
    // Koleksi harus pulih sendiri setelah data aplikasi dihapus: minta izin baca sekali, lalu pindai
    // otomatis bila koleksi kosong — tanpa pilih folder atau impor file. Izin baca juga membuat file
    // lama (owner MediaStore kosong/SAF) terbaca; grant SAF hanya fallback bila izin ditolak.
    val readPermissions = remember {
        when {
            android.os.Build.VERSION.SDK_INT >= 33 -> arrayOf(
                android.Manifest.permission.READ_MEDIA_AUDIO, android.Manifest.permission.READ_MEDIA_VIDEO,
            )
            // API 29 dengan mode legacy memerlukan izin baca/tulis klasik untuk membaca file lama.
            android.os.Build.VERSION.SDK_INT <= 29 -> arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            )
            else -> arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    val requestRead = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) library.scanIfEmpty()
    }
    val libraryOwner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(libraryOwner) {
        val observer=androidx.lifecycle.LifecycleEventObserver { _,event ->
            if (event==androidx.lifecycle.Lifecycle.Event.ON_RESUME) library.validateAvailability()
        }
        libraryOwner.lifecycle.addObserver(observer)
        onDispose { libraryOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        if (library.hasReadPermission() || library.hasFolderAccess()) library.scanIfEmpty() else requestRead.launch(readPermissions)
    }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); model.clearError() } }
    // Info "Terpakai …" di Pengaturan ikut terbaru tiap tab dibuka, bukan cuma pas aplikasi start.
    LaunchedEffect(tab) { if (tab == settingsTab) model.refreshStorage(); if (tab == 0) library.updateDeviceStorage() }
    LaunchedEffect(libraryMessage) { libraryMessage?.let { if (it.startsWith("Waktu lirik disimpan.") || it.startsWith("Lirik pilihan disimpan.")) player.refreshLyrics(); snackbar.showSnackbar(it); library.clearMessage() } }
    LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it); model.clearNotice() } }

    val karaokeSource by karaoke.source.collectAsStateWithLifecycle()
    val activeCount = jobs.count { it.state == JobState.RUNNING || it.state == JobState.QUEUED }

    BackHandler(enabled=tab == settingsTab && !fullPlayer) { tab=previousTab }
    LaunchedEffect(playback.track) { if (playback.track == null) fullPlayer=false else requestNotifications() }
    val accentCover=playback.track?.cover?.takeIf { it.isNotBlank() }
        ?: jobs.firstOrNull { it.state == JobState.COMPLETED && it.coverPath.isNotBlank() }?.coverPath.orEmpty()
    AccentTheme(accentCover, SettingsStore.artworkAccent) {
    // Saat pemutar penuh terbuka, dialog lirik dihost di dalam jendela pemutar (z-order dialog).
    if (!fullPlayer) LyricsSearchDialog(library)
    KaraokeDialog(karaoke, player, requestNotifications)
    if (fullPlayer && playback.track != null) FullPlayer(playback,player,library,karaoke={ playback.track?.let { karaoke.open(it, playback.positionMs) } }) { fullPlayer=false }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (tab == settingsTab) "Pengaturan" else "TubeFlow", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (activeCount > 0) "$activeCount unduhan sedang berjalan" else "Simpan momen. Putar kapan saja.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = { if (tab == settingsTab) IconButton(onClick={ tab=previousTab }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Kembali") } },
                actions = {
                    if (karaokeSource != null) IconButton(onClick=karaoke::openCurrent) { Icon(Icons.Outlined.MicOff,"Status dan hasil karaoke") }
                    if (tab != settingsTab) IconButton(onClick={ previousTab=tab; tab=settingsTab }) { Icon(Icons.Outlined.Settings,"Pengaturan") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
            if (playback.track != null) MiniPlayer(playback,player) { fullPlayer=true }
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val tabs = listOf("Ringkasan", "Download", "Antrean", "Koleksi", "Riwayat")
                val icons = listOf(Icons.Outlined.Dashboard, Icons.Outlined.Download, Icons.Outlined.Queue, Icons.Outlined.LibraryMusic, Icons.Outlined.History)
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = {
                            if (index == 2 && activeCount > 0) {
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
                1 -> DownloadScreen(
                    url = url,
                    onUrl = { url = it },
                    metadata = metadata,
                    playlist = playlist,
                    busy = busy,
                    inspectError = inspectError,
                    inspectDetails = inspectDetails,
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
                        tab = 2
                    },
                    downloadPlaylist = { entries, opts ->
                        requestNotifications()
                        // Judul playlist ikut diambil SEBELUM panel dibersihkan — jadi nama subfolder otomatis.
                        playlist?.let { model.queuePlaylist(it, entries, opts) }
                        url = ""
                        model.clearPlaylist()
                        tab = 2
                    },
                )
                2 -> QueueScreen(
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
                    onClearUnsuccessful = { model.clearUnsuccessful() },
                )
                0 -> MediaDashboardScreen(library, jobs, collection = { kind -> library.filter(MediaLibraryViewModel.Filter(kind = kind)); tab = 3 }, downloads = { tab = 1 })
                3 -> MediaLibraryScreen(library, player)
                4 -> HistoryScreen(
                    jobs = jobs,
                    listState = historyList,
                    onRetry = { model.retry(it) },
                    onDeleteFile = { model.removeFile(it) },
                    onRefetchLyrics = { model.refetchLyrics(it) },
                    onSearchLyrics = { library.openLyrics(it) },
                    // Riwayat hanya memutar lagu yang diketuk; daftar riwayat tidak boleh membanjiri antrean.
                    onPlay = { jobs, index -> jobs.getOrNull(index)?.let { player.playJobs(listOf(it), 0) } },
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
                    storageCatalogue = storageCatalogue,
                    storageUnknownSizes = storageUnknownSizes,
                    onRefreshStorage = { model.refreshStorage() },
                    temporaryUsage = temporaryUsage,
                    cleaningPartial = cleaningPartial,
                    onClearPartial = model::clearPartialFiles,
                    clearingFiles = clearingFiles,
                    onClearStorage = { model.clearStorageFolder() },
                )
            }
            }
        }
    }
    }
}
