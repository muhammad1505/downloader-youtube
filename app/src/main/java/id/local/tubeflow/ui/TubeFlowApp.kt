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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.core.net.toUri
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.local.tubeflow.BuildConfig
import id.local.tubeflow.core.*
import id.local.tubeflow.ui.theme.LocalCyberpunkTheme
import id.local.tubeflow.ui.theme.LocalTransformersTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TubeFlowApp(model: DownloadViewModel, library: MediaLibraryViewModel, player: PlayerViewModel, karaoke: KaraokeViewModel, video: VideoPlayerViewModel, incomingUrl: String, requestNotifications: () -> Unit = {}, navigation: AppNavigationRequest? = null, incomingMedia: IncomingMedia? = null, onMediaHandled: () -> Unit = {}, onNavigationHandled: (String) -> Unit = {}, incomingUrlId: Long = 0L) {
    val playback by player.state.collectAsStateWithLifecycle()
    var fullPlayer by rememberSaveable { mutableStateOf(false) }
    var previousTab by rememberSaveable { mutableIntStateOf(1) }
    val jobs by model.jobs.collectAsStateWithLifecycle()
    val metadata by model.metadata.collectAsStateWithLifecycle()
    val playlist by model.playlist.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val inspectPhase by model.inspectPhase.collectAsStateWithLifecycle()
    val inspectStartedAt by model.inspectStartedAt.collectAsStateWithLifecycle()
    val engineUpdateBlockedReason = when {
        busy -> "Tunggu pemeriksaan tautan selesai dulu."
        jobs.any { it.state == JobState.QUEUED || it.state == JobState.RUNNING || it.state == JobState.PAUSED } ->
            "Selesaikan atau hentikan unduhan aktif dulu."
        else -> null
    }
    val spotifyPreview by model.spotify.collectAsStateWithLifecycle()
    val spotifyCollectionPreview by model.spotifyCollection.collectAsStateWithLifecycle()
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
    // Antrean dan riwayat kini satu tab Download; Alat adalah tab keempat, Pengaturan tetap di luar bilah bawah.
    val toolsTab = 3
    val settingsTab = 4
    var tab by rememberSaveable { mutableIntStateOf(1) }
    // Target bagian Download dari shortcut/notifikasi; dikosongkan setelah aksi selesai.
    var downloadTarget by rememberSaveable { mutableStateOf<DownloadTarget?>(null) }
    // Kategori Pengaturan adalah layar terpisah; null berarti daftar kategori.
    var settingsSection by rememberSaveable { mutableStateOf<String?>(null) }
    var highlightedJobId by rememberSaveable { mutableStateOf<String?>(null) }
    var url by rememberSaveable { mutableStateOf("") }
    var bstationLoginOpen by rememberSaveable { mutableStateOf(false) }
    var youtubeLoginOpen by rememberSaveable { mutableStateOf(false) }
    val bstationSession = BstationAuth.session
    val youtubeSession = YouTubeAuth.session
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var releaseNotes by remember { mutableStateOf<ReleaseNotes?>(null) }
    LaunchedEffect(Unit) {
        val changelog = AppChangelog.load(context)
        if (AppChangelog.shouldOffer(ChangelogStore.lastSeen(context), BuildConfig.VERSION_CODE, changelog)) {
            releaseNotes = AppChangelog.forVersion(changelog, BuildConfig.VERSION_CODE)
        }
    }
    // Pemeriksaan rilis yt-dlp paling sering sekali sehari, bukan setiap kali aplikasi dibuka.
    LaunchedEffect(Unit) { EngineUpdater.checkIfDue(context) }
    val screenState = rememberSaveableStateHolder()
    // Dialog waktu lirik dari Riwayat dihost di sini karena butuh item katalog yang tertaut.
    var lyricTiming by remember { mutableStateOf<MediaItem?>(null) }
    val appScope = rememberCoroutineScope()

    // Audio dijeda (bukan dihentikan) agar antrean lagu tetap utuh saat video dibuka.
    fun playVideo(entries: List<VideoEntry>, index: Int) {
        if (entries.isEmpty()) {
            appScope.launch { snackbar.showSnackbar("Berkas video tidak tersedia.") }
            return
        }
        player.pause()
        video.open(entries, index)
    }

    LaunchedEffect(highlightedJobId) {
        if (highlightedJobId != null) {
            delay(3_000)
            highlightedJobId = null
        }
    }

    // Berkas dari "Buka dengan" aplikasi lain memakai pemutar yang sudah ada: audio ke pemutar lagu,
    // video ke Media3. Media sejenis di folder yang sama ikut masuk antrean supaya tombol
    // berikut/sebelumnya berguna untuk berkas di luar folder unduhan TubeFlow. State dikosongkan
    // setelah dibuka agar rotasi tidak memutar ulang berkas.
    LaunchedEffect(incomingMedia) {
        val media = incomingMedia ?: return@LaunchedEffect
        fullPlayer = false
        karaoke.close()
        val planned = MediaFolders(context).siblings(media.uri, media.kind)
            ?.let { MediaFolderQueue.plan(it, FolderMedia(media.uri, media.title)) }
        when (media.kind) {
            IncomingMedia.Kind.AUDIO -> {
                video.close()
                if (planned == null) player.play(PlayerViewModel.Track(media.uri, media.title))
                else player.playTracks(planned.first.map { PlayerViewModel.Track(it.uri, it.title) }, planned.second)
            }
            IncomingMedia.Kind.VIDEO -> {
                val entries = planned?.first?.map { VideoEntry(it.uri, it.title) }
                    ?: listOf(VideoEntry(media.uri, media.title))
                playVideo(entries, planned?.second ?: 0)
            }
        }
        onMediaHandled()
    }
    // Kunci memakai incomingUrlId supaya tautan yang sama tetap diproses saat dibagikan ulang.
    LaunchedEffect(incomingUrlId, incomingUrl) {
        if (incomingUrl.isNotBlank()) {
            fullPlayer = false
            video.close()
            karaoke.close()
            if (url != incomingUrl) model.clearPlaylist()
            url = incomingUrl
            tab = 1
            downloadTarget = null
            // Inspeksi diserahkan ke efek DownloadScreen (video vs playlist) — memanggil inspect langsung di sini
            // membuat tautan playlist murni memunculkan "tautan tidak valid" sesaat dan video diperiksa dua kali (BUG-022).
        }
    }
    LaunchedEffect(navigation?.id) {
        val request = navigation ?: return@LaunchedEffect
        fullPlayer = false
        // Shortcut video harus mempertahankan pemutar video yang sedang aktif; shortcut lain
        // menutupnya agar audio/shortcut tujuan tidak bertumpuk dengan layar video.
        if (request.destination != AppDestination.VIDEO_PLAYER) video.close()
        karaoke.close()
        library.closeLyrics()
        tab = request.destination.tab
        downloadTarget = request.destination.downloadTarget
        when (request.destination) {
            AppDestination.PLAYER -> {
                player.restorePlaybackSession()
                fullPlayer = player.state.value.track != null
            }
            AppDestination.KARAOKE -> karaoke.openCurrent()
            // Shortcut launcher langsung membuka pemutar, bukan sekadar filter Koleksi.
            AppDestination.AUDIO_PLAYER -> {
                library.filter(MediaLibraryViewModel.Filter(kind = "AUDIO"))
                player.restorePlaybackSession(allowLastTrack = true)
                fullPlayer = player.state.value.track != null
            }
            AppDestination.VIDEO_PLAYER -> {
                library.filter(MediaLibraryViewModel.Filter(kind = "VIDEO"))
                if (video.state.value.active) {
                    player.pause()
                } else if (video.openLastVideo()) {
                    player.pause()
                }
            }
            else -> Unit
        }
        onNavigationHandled(request.id)
    }
    // Koleksi harus pulih sendiri setelah data aplikasi dihapus: minta izin baca sekali, lalu pindai
    // otomatis bila koleksi kosong — tanpa pilih folder atau impor file. Izin baca juga membuat file
    // lama (owner MediaStore kosong/SAF) terbaca; grant SAF hanya fallback bila izin ditolak.
    val readPermissions = remember {
        MediaPermissions.requested(android.os.Build.VERSION.SDK_INT)
    }
    val requestRead = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) library.scanIfEmpty()
    }
    val libraryOwner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(libraryOwner) {
        val observer=androidx.lifecycle.LifecycleEventObserver { _,event ->
            if (event==androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                library.refreshCatalogIfStale()
                library.validateAvailability()
            }
        }
        libraryOwner.lifecycle.addObserver(observer)
        onDispose { libraryOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        if (library.hasReadPermission() || library.hasPartialVisualAccess() || library.hasFolderAccess()) library.scanIfEmpty() else requestRead.launch(readPermissions)
    }
    // Retensi riwayat: dijalankan saat aplikasi dibuka dan setiap pilihan di Pengaturan berubah.
    LaunchedEffect(SettingsStore.historyAutoDays) { model.pruneHistory(SettingsStore.historyAutoDays) }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); model.clearError() } }
    // Info "Terpakai …" di Pengaturan ikut terbaru tiap tab dibuka, bukan cuma pas aplikasi start.
    LaunchedEffect(tab) { if (tab == settingsTab) model.refreshStorage(); if (tab == 0) library.updateDeviceStorage() }
    LaunchedEffect(libraryMessage) { libraryMessage?.let { if (it.startsWith("Waktu lirik disimpan.") || it.startsWith("Lirik pilihan disimpan.")) player.refreshLyrics(); snackbar.showSnackbar(it); library.clearMessage() } }
    LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it); model.clearNotice() } }

    val karaokeRequest by karaoke.requestId.collectAsStateWithLifecycle()
    val activeCount = DownloadSections.running(jobs)

    BackHandler(enabled=tab == settingsTab && !fullPlayer) {
        if (settingsSection != null) settingsSection = null else tab = previousTab
    }
    LaunchedEffect(playback.track) { if (playback.track == null) fullPlayer=false else requestNotifications() }
    val accentCover=playback.track?.cover?.takeIf { it.isNotBlank() }
        ?: jobs.firstOrNull { it.state == JobState.COMPLETED && it.coverPath.isNotBlank() }?.coverPath.orEmpty()
    val cyberpunkMode = SettingsStore.themeMode == SettingsStore.MODE_CYBERPUNK
    val transformersMode = SettingsStore.themeMode == SettingsStore.MODE_TRANSFORMERS
    AccentTheme(accentCover, SettingsStore.artworkAccent && !cyberpunkMode && !transformersMode) {
    // Saat pemutar penuh terbuka, dialog lirik dihost di dalam jendela pemutar (z-order dialog).
    if (!fullPlayer) LyricsSearchDialog(library)
    lyricTiming?.let { LyricTimingDialog(it, library) { lyricTiming = null } }
    KaraokeDialog(karaoke, player, requestNotifications)
    if (bstationLoginOpen) BstationLoginDialog(
        onDismiss = { bstationLoginOpen = false },
        onSignedIn = {
            bstationLoginOpen = false
            appScope.launch { snackbar.showSnackbar("Akun Bstation tersimpan. Episode Premium sekarang bisa dipilih.") }
            // Katalog dimuat ulang dengan cookie: penanda akses Premium dapat berubah setelah login.
            if (BstationUrls.isSeasonOnly(url)) model.inspectPlaylist(url.trim())
        },
    )
    if (youtubeLoginOpen) BstationLoginDialog(
        onDismiss = { youtubeLoginOpen = false },
        onSignedIn = {
            youtubeLoginOpen = false
            appScope.launch { snackbar.showSnackbar("Akun YouTube tersimpan. Video yang butuh login sekarang bisa diunduh.") }
            // Ulangi pemeriksaan tautan YouTube yang sedang menunggu login.
            if (url.isNotBlank()) model.retryInspect(url.trim())
        },
        provider = LoginProvider.YOUTUBE,
    )
    if (fullPlayer && playback.track != null) FullPlayer(playback,player,library,karaoke={ playback.track?.let { karaoke.open(it, playback.positionMs) } }) { fullPlayer=false }
    val tabs = listOf("Ringkasan", "Download", "Koleksi", "Alat")
    val icons = listOf(Icons.Outlined.Dashboard, Icons.Outlined.Download, Icons.Outlined.LibraryMusic, Icons.Outlined.Build)
    val showLabels=androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.2f
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 840.dp
    Scaffold(
        topBar = {
            val cyberpunk = LocalCyberpunkTheme.current
            val transformers = LocalTransformersTheme.current
            Column {
                TopAppBar(
                    title = {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (transformers) {
                                val badgeShape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
                                Box(
                                    Modifier.size(38.dp).clip(badgeShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f),
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                                                ),
                                            ),
                                        )
                                        .border(
                                            1.dp,
                                            Brush.linearGradient(listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary)),
                                            badgeShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    TransformerEmblem(Modifier.size(22.dp))
                                }
                            } else {
                                Image(
                                    painter = painterResource(id.local.tubeflow.R.drawable.ic_launcher_foreground),
                                    contentDescription = null,
                                    modifier = Modifier.size(34.dp).background(
                                        colorResource(id.local.tubeflow.R.color.launcher_background),
                                        RoundedCornerShape(10.dp),
                                    ),
                                )
                            }
                            Column {
                                Text(
                                    "TubeFlow",
                                    fontWeight = if (transformers) FontWeight.Black else FontWeight.SemiBold,
                                    fontStyle = if (transformers) FontStyle.Italic else FontStyle.Normal,
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                val subtitle = when {
                                    activeCount > 0 -> "$activeCount unduhan sedang berjalan"
                                    transformers -> "MEDIA DOWNLOADER & MANAGER"
                                    else -> null
                                }
                                if (subtitle != null) Text(
                                    subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    letterSpacing = if (transformers && activeCount == 0) 1.1.sp else 0.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    navigationIcon = { if (tab == settingsTab) IconButton(onClick={ if (settingsSection != null) settingsSection = null else tab=previousTab }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, if (settingsSection != null) "Kembali ke daftar pengaturan" else "Kembali") } },
                    actions = {
                        if (karaokeRequest != null) IconButton(onClick=karaoke::openCurrent) { Icon(Icons.Outlined.MicOff,"Status dan hasil karaoke") }
                        if (tab != settingsTab) IconButton(
                            onClick={ previousTab=tab; settingsSection=null; tab=settingsTab },
                            modifier = if (transformers) Modifier
                                .padding(vertical = 6.dp)
                                .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f), CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)) else Modifier,
                        ) { Icon(Icons.Outlined.Settings,"Pengaturan") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = when {
                            transformers -> MaterialTheme.colorScheme.background
                            cyberpunk -> MaterialTheme.colorScheme.surface
                            else -> MaterialTheme.colorScheme.surfaceContainerLowest
                        },
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                if (transformers) Box(
                    Modifier.fillMaxWidth().height(1.dp).background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                Color.Transparent,
                            ),
                        ),
                    ),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val cyberpunk = LocalCyberpunkTheme.current
            val transformers = LocalTransformersTheme.current
            Column {
            if (cyberpunk) HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            if (playback.track != null) Box(Modifier.fillMaxWidth(), contentAlignment=Alignment.Center) {
                Box(Modifier.widthIn(max=840.dp)) { MiniPlayer(playback,player) { fullPlayer=true } }
            }
            if (!useRail) {
            if (transformers) {
                TransformersTabBar(
                    tabs = tabs,
                    icons = icons,
                    selected = tab,
                    showLabels = showLabels,
                    activeCount = activeCount,
                    onSelect = { index -> tab = index; downloadTarget = null },
                )
            } else {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                windowInsets = if (cyberpunk) WindowInsets.navigationBars else NavigationBarDefaults.windowInsets,
            ) {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index; downloadTarget = null },
                        icon = {
                            if (index == 1 && activeCount > 0) {
                                BadgedBox(badge = { Badge { Text(if (activeCount > 99) "99+" else "$activeCount") } }) {
                                    Icon(icons[index], contentDescription = if (showLabels) null else title)
                                }
                            } else {
                                Icon(icons[index], contentDescription = if (showLabels) null else title)
                            }
                        },
                        label = if (showLabels) ({ Text(title, maxLines=1, overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis) }) else null,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = if (cyberpunk) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
            }
            }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
        if (useRail) NavigationRail(
            Modifier.fillMaxHeight().verticalScroll(rememberScrollState()),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            tabs.forEachIndexed { index, title ->
                NavigationRailItem(
                    selected=tab==index, onClick={ tab=index; downloadTarget = null },
                    icon={ if (index==1 && activeCount>0) {
                        BadgedBox(badge={ Badge { Text(if (activeCount>99) "99+" else "$activeCount") } }) {
                            Icon(icons[index],if (showLabels) null else title)
                        }
                    } else Icon(icons[index],if (showLabels) null else title) },
                    label=if (showLabels) ({ Text(title) }) else null,
                    colors=NavigationRailItemDefaults.colors(
                        selectedIconColor=MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor=MaterialTheme.colorScheme.primary,
                        unselectedIconColor=MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor=MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor=if (LocalCyberpunkTheme.current || LocalTransformersTheme.current) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment=Alignment.TopCenter) {
        // Cahaya biru-merah tipis di belakang konten agar bahasa visual Transformers terasa di semua menu.
        if (transformersMode) Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                        Color.Transparent,
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f),
                    ),
                ),
            ),
        )
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.widthIn(max=840.dp).fillMaxSize(),
            transitionSpec = {
                (fadeIn(tween(220)) + slideInHorizontally(tween(240)) { it / 8 }) togetherWith
                    (fadeOut(tween(160)) + slideOutHorizontally(tween(240)) { -it / 8 })
            },
            label = "tab",
        ) { current ->
            screenState.SaveableStateProvider("screen-$current") {
            when (current) {
                1 -> DownloadScreen(
                    jobs = jobs,
                    highlightedJobId = highlightedJobId,
                    url = url,
                    onUrl = { if (url != it) model.clearPlaylist(); url = it },
                    metadata = metadata,
                    playlist = playlist,
                    busy = busy,
                    inspectPhase = inspectPhase,
                    inspectStartedAt = inspectStartedAt,
                    inspectError = inspectError,
                    inspectDetails = inspectDetails,
                    bstationSignedIn = bstationSession != null,
                    onBstationLogin = { bstationLoginOpen = true },
                    inspect = { model.inspect(url.trim()) },
                    inspectPlaylist = { model.inspectPlaylist(url.trim()) },
                    retryInspect = { model.retryInspect(url.trim()) },
                    clearPlaylist = { model.clearPlaylist() },
                    cancelInspect = { model.cancelInspection() },
                    download = { opts ->
                        requestNotifications()
                        // Isian hanya dibersihkan setelah baris antrean benar-benar dibuat. Bila
                        // validasi/duplikat/WorkManager gagal, kartu hasil inspeksi tetap tampil
                        // dan pengguna bisa memperbaiki pilihan tanpa mengulang inspeksi.
                        val submitted = url.trim()
                        model.enqueue(opts.copy(url = submitted), onQueued = { id ->
                            highlightedJobId = id
                            if (url.trim() == submitted) {
                                url = ""
                                model.clearPlaylist()
                            }
                            // Tetap di kartu unduhan bila pengantrean gagal, supaya pesan galat
                            // dan pilihan resolusi masih terlihat.
                            tab = 1
                        })
                    },
                    downloadPlaylist = { entries, opts ->
                        requestNotifications()
                        // Judul playlist diambil SEBELUM panel dibersihkan agar subfolder tetap benar.
                        val target = playlist
                        val submitted = url.trim()
                        if (target != null) model.queuePlaylist(target, entries, opts, onQueued = { id ->
                            highlightedJobId = id
                            if (url.trim() == submitted) {
                                url = ""
                                model.clearPlaylist()
                            }
                            tab = 1
                        })
                    },
                    spotify = spotifyPreview,
                    inspectSpotify = { model.inspectSpotify(url.trim()) },
                    clearSpotify = { model.clearSpotify() },
                    downloadSpotify = { opts, candidate ->
                        requestNotifications()
                        // Track diambil SEBELUM state dibersihkan; URL job memakai video YouTube pilihan.
                        val submitted = url.trim()
                        spotifyPreview.track?.let { track ->
                            model.enqueueSpotify(opts, track, candidate, onQueued = { id ->
                                highlightedJobId = id
                                if (url.trim() == submitted) {
                                    url = ""
                                    model.clearSpotify()
                                    model.clearPlaylist()
                                }
                                tab = 1
                            })
                        }
                    },
                    spotifyCollection = spotifyCollectionPreview,
                    inspectSpotifyCollection = { model.inspectSpotifyCollection(url.trim()) },
                    clearSpotifyCollection = { model.clearSpotifyCollection() },
                    matchSpotifyTracks = { tracks -> model.matchSpotifyTracks(tracks) },
                    cancelSpotifyMatch = { model.cancelSpotifyMatch() },
                    downloadSpotifyCollection = { opts, tracks ->
                        requestNotifications()
                        // Koleksi diambil SEBELUM state dibersihkan; tiap lagu memakai video YouTube hasil pencocokan.
                        val submitted = url.trim()
                        spotifyCollectionPreview.collection?.let { collection ->
                            model.enqueueSpotifyCollection(opts, collection, tracks, onQueued = { id ->
                                highlightedJobId = id
                                if (url.trim() == submitted) {
                                    url = ""
                                    model.clearSpotifyCollection()
                                    model.clearPlaylist()
                                }
                                tab = 1
                            })
                        }
                    },
                    queueActions = QueueActions(
                        cancel = { model.cancel(it) },
                        retry = { model.retry(it) },
                        retryAll = { model.retryFailed() },
                        remove = { model.remove(it) },
                        pause = { model.pause(it) },
                        resume = { model.resume(it) },
                        pauseAll = { model.pauseAll() },
                        resumeAll = { model.resumeAll() },
                        cancelAll = { model.cancelAll() },
                        clearUnsuccessful = { model.clearUnsuccessful() },
                    ),
                    historyActions = HistoryActions(
                        redownload = { model.retry(it) },
                        deleteFile = { model.removeFile(it) },
                        refetchLyrics = { model.refetchLyrics(it) },
                        searchLyrics = { library.openLyrics(it) },
                        play = { list, index ->
                            val job = list.getOrNull(index)
                            val uri = job?.outputUri
                            if (job != null && !uri.isNullOrBlank()) {
                                appScope.launch {
                                    // Riwayat dapat menunjuk berkas yang sudah dihapus. Periksa dulu
                                    // agar pemutar video/audio tidak dibuka dengan URI yang hilang.
                                    if (!library.canPlay(uri, if (job.mode == MediaMode.VIDEO) "VIDEO" else "AUDIO")) return@launch
                                    if (job.mode == MediaMode.VIDEO) {
                                        val (entries, start) = VideoPlayback.videoEntries(list, job.id, nameOf = { raw -> displayName(context, raw.toUri()) })
                                        playVideo(entries, start)
                                    } else player.playJobs(listOf(job), 0)
                                }
                            } else appScope.launch { snackbar.showSnackbar("Berkas ini tidak tersedia di penyimpanan.") }
                        },
                        clearCompleted = { model.clearCompleted() },
                        enqueue = { player.enqueue(it) },
                        playNext = { player.playNext(it) },
                        editLyricTiming = { jobId ->
                            appScope.launch {
                                val uri = jobs.firstOrNull { it.id == jobId }?.outputUri
                                val item = uri?.let { library.itemFor(it) }
                                if (item == null) snackbar.showSnackbar("Lirik belum ada di katalog Koleksi. Segarkan katalog lalu coba lagi.")
                                else lyricTiming = item
                            }
                        },
                    ),
                    downloadTarget = downloadTarget,
                    onDownloadTargetHandled = { downloadTarget = null },
                    onEngineUpdate = { previousTab = tab; settingsSection = "media"; tab = settingsTab },
                )
                0 -> MediaDashboardScreen(library, jobs, collection = { kind -> library.filter(MediaLibraryViewModel.Filter(kind = kind)); tab = 2 }, downloads = { tab = 1 })
                2 -> MediaLibraryScreen(
                    library, player, jobs = jobs,
                    onPlayVideo = { entries, index -> playVideo(entries, index) },
                    // Konfirmasi sudah dilakukan di kartu: Unduh lagi mengganti file lama (BUG-026).
                    onRedownload = { model.retry(it) },
                )
                // Alat memakai dialog waktu lirik yang sama dengan Koleksi/player, bukan salinannya.
                toolsTab -> ToolsScreen(library, player, karaoke, onEditTiming = { item -> lyricTiming = item })
                else -> if (settingsSection == "logs") AppLogScreen() else SettingsScreen(
                    karaoke = karaoke,
                    player = player,
                    library = library,
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
                    engineUpdateBlockedReason = engineUpdateBlockedReason,
                    bstationSignedIn = bstationSession != null,
                    bstationAccountId = bstationSession?.accountId.orEmpty(),
                    onBstationLogin = { bstationLoginOpen = true },
                    onBstationLogout = {
                        BstationAuth.clear(context)
                        appScope.launch { snackbar.showSnackbar("Sesi Bstation dihapus dari TubeFlow.") }
                    },
                    youtubeSignedIn = youtubeSession != null,
                    onYouTubeLogin = { youtubeLoginOpen = true },
                    onYouTubeLogout = {
                        YouTubeAuth.clear(context)
                        appScope.launch { snackbar.showSnackbar("Sesi YouTube dihapus dari TubeFlow.") }
                    },
                    section = settingsSection,
                    onSection = { settingsSection = it },
                    onShowReleaseNotes = {
                        releaseNotes = AppChangelog.latestAtOrBefore(AppChangelog.load(context), BuildConfig.VERSION_CODE)
                    },
                )
            }
            }
        }
        }
        }
    }
    }
    VideoPlayerScreen(video)
    }
    // "Apa yang baru" muncul sekali per versi: hanya bila versi berjalan lebih baru dari yang
    // pernah ditutup pengguna, dan bisa dibuka ulang dari Pengaturan → Tentang.
    releaseNotes?.let { notes ->
        ReleaseNotesDialog(notes) {
            ChangelogStore.markSeen(context, BuildConfig.VERSION_CODE)
            releaseNotes = null
        }
    }
}

/**
 * Bilah bawah tema Transformers: empat tombol bersudut dengan garis biru-merah. Tab aktif memakai
 * warna merah khas tema, sementara tab lain tetap biru elektrik agar arah navigasi mudah dibaca.
 */
@Composable
private fun TransformersTabBar(
    tabs: List<String>,
    icons: List<ImageVector>,
    selected: Int,
    showLabels: Boolean,
    activeCount: Int,
    onSelect: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.background) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(1.dp).background(
                    Brush.horizontalGradient(
                        listOf(
                            scheme.secondary.copy(alpha = 0.70f),
                            scheme.primary.copy(alpha = 0.70f),
                            scheme.secondary.copy(alpha = 0.70f),
                        ),
                    ),
                ),
            )
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tabs.forEachIndexed { index, title ->
                    val active = index == selected
                    val shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
                    val tint = if (active) scheme.onSecondaryContainer else scheme.primary
                    Column(
                        Modifier.weight(1f)
                            .heightIn(min = 52.dp)
                            .clip(shape)
                            .background(
                                if (active) Brush.verticalGradient(listOf(scheme.secondaryContainer, scheme.secondary.copy(alpha = 0.35f)))
                                else Brush.verticalGradient(listOf(scheme.surfaceContainerHigh, scheme.surfaceContainerLow)),
                            )
                            .border(
                                1.dp,
                                if (active) scheme.secondary.copy(alpha = 0.90f) else scheme.primary.copy(alpha = 0.25f),
                                shape,
                            )
                            .selectable(selected = active, role = Role.Tab, onClick = { onSelect(index) })
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (index == 1 && activeCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge(containerColor = scheme.secondary, contentColor = scheme.onSecondary) {
                                        Text(if (activeCount > 99) "99+" else "$activeCount")
                                    }
                                },
                            ) {
                                Icon(icons[index], if (showLabels) null else title, Modifier.size(22.dp), tint = tint)
                            }
                        } else {
                            Icon(icons[index], if (showLabels) null else title, Modifier.size(22.dp), tint = tint)
                        }
                        if (showLabels) Text(
                            title,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
