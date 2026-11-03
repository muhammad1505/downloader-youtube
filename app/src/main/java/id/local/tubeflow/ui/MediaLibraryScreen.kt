package id.local.tubeflow.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.core.net.toUri
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import id.local.tubeflow.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.local.tubeflow.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MediaLibraryScreen(
    model: MediaLibraryViewModel,
    player: PlayerViewModel,
    jobs: List<DownloadJob> = emptyList(),
    onPlayVideo: (List<VideoEntry>, Int) -> Unit = { _, _ -> },
    onRedownload: (DownloadJob) -> Unit = {},
) {
    LaunchedEffect(Unit) {
        model.refreshCatalogIfStale()
        model.validateAvailability()
    }
    val media by model.items.collectAsStateWithLifecycle()
    val playlists by model.playlists.collectAsStateWithLifecycle()
    val favorites by model.favorites.collectAsStateWithLifecycle()
    var managePlaylists by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }
    var addPlaylist by remember { mutableStateOf<MediaItem?>(null) }
    val filter by model.selectedFilter.collectAsStateWithLifecycle()
    val folders by model.folders.collectAsStateWithLifecycle()
    val artists by model.artists.collectAsStateWithLifecycle()
    val albums by model.albums.collectAsStateWithLifecycle()
    val folderCounts by model.folderCounts.collectAsStateWithLifecycle()
    val folderAliases = SettingsStore.folderAliases
    // Kartu folder memakai hitungan database penuh; halaman pertama hanya cadangan saat hitungan
    // belum tiba supaya grid tidak berkedip kosong.
    val folderCards = remember(media, folderCounts, folderAliases) {
        val totals = folderCounts.ifEmpty {
            media.groupingBy { it.folder }.eachCount().mapValues { it.value.toLong() }
        }
        totals.entries.filter { it.value > 0 }
            .sortedBy { FolderAliases.label(it.key, folderAliases).lowercase() }
    }
    // Grid folder mengikuti lebar jendela: tiga kolom di ponsel, empat kolom di layar lebar.
    val windowWidth = LocalWindowInfo.current.containerSize.width
    val folderColumns = CatalogGrid.folderColumns(with(LocalDensity.current) { windowWidth.toDp().value })
    val browsingFolders = filter.folder.isBlank() && filter.playlistId.isBlank() &&
        filter.query.isBlank() && filter.artist.isBlank() && filter.album.isBlank()
    val busy by model.busy.collectAsStateWithLifecycle()
    // Peta sekali per perubahan daftar job; jangan memindai seluruh riwayat untuk tiap kartu.
    val completedJobs = remember(jobs) { jobs.filter { it.state == JobState.COMPLETED }.associateBy { it.id } }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    // Back dari isi folder atau filter kembali ke daftar folder; tanpa ini sistem langsung keluar.
    val filterActive = filter.folder.isNotBlank() || filter.playlistId.isNotBlank() ||
        filter.query.isNotBlank() || filter.kind.isNotBlank()
    BackHandler(enabled = filterActive || searchOpen) {
        when {
            filter.folder.isNotBlank() -> model.filter(filter.copy(folder = ""))
            filter.playlistId.isNotBlank() -> model.filter(filter.copy(playlistId = ""))
            filter.query.isNotBlank() -> model.filter(filter.copy(query = ""))
            searchOpen -> searchOpen = false
            else -> model.filter(MediaLibraryViewModel.Filter())
        }
    }
    var detail by remember { mutableStateOf<MediaItem?>(null) }
    var rename by remember { mutableStateOf<MediaItem?>(null) }
    var delete by remember { mutableStateOf<MediaItem?>(null) }
    var timing by remember { mutableStateOf<MediaItem?>(null) }
    var clipStatus by remember { mutableStateOf<MediaItem?>(null) }
    // Aksi folder Koleksi: tekan lama membuka lembar aksi, bukan langsung menghapus.
    var folderMenu by remember { mutableStateOf<String?>(null) }
    var folderDetail by remember { mutableStateOf<FolderSummary?>(null) }
    var folderRename by remember { mutableStateOf<String?>(null) }
    var folderDelete by remember { mutableStateOf<String?>(null) }
    // "Unduh lagi" mengganti file yang ada; pasangkan job sumber dengan item yang dikonfirmasi.
    var redownload by remember { mutableStateOf<Pair<DownloadJob, MediaItem>?>(null) }
    // Favorit punya chip sendiri, jadi tidak dihitung dua kali di label Filter.
    val favoritesOnly = filter.playlistId == PersonalLibrary.FAVORITES
    val activeFilters = listOf(
        filter.playlistId.isNotBlank() && !favoritesOnly, filter.folder.isNotBlank(),
        filter.artist.isNotBlank(), filter.album.isNotBlank(), filter.sort != "recent",
    ).count { it }
    val hasAnyFilter = favoritesOnly || activeFilters > 0
    val playlistOptions = listOf("" to stringResource(R.string.library_all_playlists), PersonalLibrary.FAVORITES to stringResource(R.string.library_favorites)) +
        playlists.filter { it.id != PersonalLibrary.FAVORITES }.map { it.id to it.name }
    val filterLabel = if (filter.kind != "VIDEO" && filter.playlistId.isNotBlank() && !favoritesOnly) {
        (playlistOptions.firstOrNull { it.first == filter.playlistId }?.second ?: "Filter") +
            if (activeFilters > 1) " · $activeFilters" else ""
    } else if (activeFilters == 0) "Filter" else "Filter · $activeFilters"
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var openError by remember { mutableStateOf<String?>(null) }
    fun open(item: MediaItem, share: Boolean = false) { scope.launch {
        // Judul menjadi caption cadangan bila provider tidak menyediakan nama file.
        openError = withContext(Dispatchers.IO) {
            launchMedia(context, item.location, item.kind == "AUDIO", share, item.title.ifBlank { item.displayName })
        }
    } }
    // Video diputar di pemutar Media3 dalam aplikasi; audio tetap memakai PlayerViewModel.
    fun playVideo(item: MediaItem) {
        val (entries, index) = VideoPlayback.videoEntries(media, item.uri, sidecars = { entry ->
            entry.jobId?.let { completedJobs[it]?.sidecarUris }
        }, nameOf = { raw -> displayName(context, raw.toUri()) })
        if (entries.isEmpty()) open(item) else onPlayVideo(entries, index)
    }
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(UiSpacing.screen), verticalArrangement = Arrangement.spacedBy(UiSpacing.item),
    ) {
        item { PageHeading(if (filter.kind == "AUDIO") "Koleksi musik" else if (filter.kind == "VIDEO") "Koleksi video" else "Koleksi media", "File di perangkat, terpisah dari riwayat download.", Icons.Outlined.Folder) }
        val playable = media.filter { it.kind == "AUDIO" && it.availability == "AVAILABLE" }
        // Dua baris ringkas: jenis + aksi utama, lalu favorit + satu pintu filter.
        // Urutan/folder/artis/album tidak lagi memenuhi layar; semuanya ada di dialog Filter.
        item { ScrollRow {
            listOf("" to "Semua", "AUDIO" to "Musik", "VIDEO" to "Video").forEach { (kind, name) ->
                FilterChip(filter.kind == kind, onClick = { model.filter(MediaLibraryViewModel.Filter(kind = kind)) }, label = { Text(name) })
            }
            if (filter.kind != "VIDEO") {
                ActionChip("Putar", { model.playSelection(player, shuffle = false) }, icon = Icons.Outlined.PlayArrow, enabled = playable.isNotEmpty())
                ActionChip("Acak", { model.playSelection(player, shuffle = true) }, icon = Icons.Outlined.Shuffle, enabled = playable.size > 1)
            }
        } }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Memeriksa media…") }
        if (searchOpen) item {
            SearchField(filter.query, { model.filter(filter.copy(query = it)) }, label = "Cari judul, artis, nama file")
        }
        item { ScrollRow {
            if (filter.kind != "VIDEO") {
                FilterChip(favoritesOnly, onClick = {
                    model.filter(MediaLibraryViewModel.Filter(kind = "AUDIO", playlistId = if (filter.playlistId == PersonalLibrary.FAVORITES) "" else PersonalLibrary.FAVORITES))
                }, label = { Text(stringResource(R.string.library_favorites)) })
            }
            ActionChip(
                filterLabel,
                { filterOpen = true },
                icon = Icons.Outlined.Tune,
            )
            ActionChip(
                if (searchOpen) "Tutup" else "Cari",
                { if (searchOpen) model.filter(filter.copy(query = "")); searchOpen = !searchOpen },
                icon = if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search,
            )
            if (filter.kind != "VIDEO") {
                ActionChip(stringResource(R.string.library_manage_playlists), { managePlaylists = true }, icon = Icons.Outlined.PlaylistAdd)
            }
        } }
        openError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        if (filter.folder.isNotBlank()) item {
            LibraryBreadcrumb(FolderAliases.label(filter.folder, folderAliases)) {
                model.filter(filter.copy(folder = ""))
            }
        }
        if (media.isEmpty() && !busy && (!browsingFolders || folderCards.isEmpty())) item {
            if (filter.playlistId.isNotBlank()) EmptyState(Icons.Outlined.LibraryMusic, stringResource(if (filter.playlistId == PersonalLibrary.FAVORITES) R.string.library_favorites_empty else R.string.library_playlist_empty), stringResource(R.string.library_playlist_empty_help))
            else EmptyState(
                Icons.Outlined.Folder,
                "Belum ada media yang cocok",
                "Folder Download/TubeFlow dipindai otomatis saat aplikasi dibuka. File tidak disalin.",
                actionLabel = "Segarkan katalog",
                onAction = { model.scan() },
            )
        }
        if (browsingFolders) {
            if (folderCards.isNotEmpty()) {
                item { FolderSectionHeading(folderCards.size) }
                items(folderCards.chunked(folderColumns), key = { row -> "folders:" + row.joinToString("|") { it.key } }) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (folder, total) ->
                            FolderTile(
                                folder = folder,
                                alias = FolderAliases.label(folder, folderAliases),
                                total = total,
                                modifier = Modifier.weight(1f),
                                onOpen = { model.filter(filter.copy(folder = folder)) },
                                onLongClick = { folderMenu = folder },
                            )
                        }
                        // Slot kosong menjaga lebar kartu sama meski baris terakhir tidak penuh.
                        repeat(folderColumns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        } else {
            items(media, key = { it.uri }) { item ->
                val accent=rememberCoverAccent(item.coverPath)
                // Satu baris ringkas untuk semua daftar Koleksi; aksi pindah ke menu titik tiga.
                val sourceJob = completedJobs[item.jobId]
                val subtitle = buildList {
                    // Nama file tampil hanya bila tidak mengulang judul — info berkas tanpa baris tambahan.
                    if (showFileName(item.title, item.displayName)) add(item.displayName)
                    if (item.kind == "AUDIO") add(item.artist.ifBlank { "Artis belum diketahui" })
                    item.sizeBytes?.let { add(Format.bytes(it)) }
                    item.durationMs?.let { add(duration(it)) }
                    if (item.platformId.isNotBlank()) add(SourcePlatforms.fromId(item.platformId).label)
                }.joinToString(" · ")
                val available = item.availability == "AVAILABLE"
                val favorite = item.uri in favorites
                val onAction: (MediaActionKind) -> Unit = { kind ->
                    when (kind) {
                        MediaActionKind.PLAY -> if (item.kind == "AUDIO") model.playSelection(player, item.uri) else playVideo(item)
                        MediaActionKind.OPEN -> open(item)
                        MediaActionKind.SHARE -> open(item, share = true)
                        MediaActionKind.FAVORITE_ADD -> model.addToPlaylist(item, PersonalLibrary.FAVORITES)
                        MediaActionKind.FAVORITE_REMOVE -> model.removeFromPlaylist(item, PersonalLibrary.FAVORITES)
                        MediaActionKind.ENQUEUE -> player.enqueue(item)
                        MediaActionKind.PLAY_NEXT -> player.playNext(item)
                        MediaActionKind.ADD_PLAYLIST -> addPlaylist = item
                        MediaActionKind.REMOVE_PLAYLIST -> model.removeFromPlaylist(item, filter.playlistId)
                        MediaActionKind.FIND_LYRICS -> model.openLyrics(item)
                        MediaActionKind.EDIT_LYRIC_TIMING -> timing = item
                        MediaActionKind.DETAIL -> detail = item
                        MediaActionKind.RENAME -> rename = item
                        MediaActionKind.REDOWNLOAD -> sourceJob?.let { redownload = it to item }
                        MediaActionKind.CLIP_STATUS -> clipStatus = item
                        MediaActionKind.DELETE -> delete = item
                        else -> Unit
                    }
                }
                MediaListRow(
                    coverPath = item.coverPath,
                    mode = if (item.kind == "AUDIO") MediaMode.AUDIO else MediaMode.VIDEO,
                    title = (if (item.kind == "AUDIO") Format.musicLabel(TitleSplit.identify(item.title, metadataArtist=item.artist, recognizeCollaboration=false)) else item.title)
                        .ifBlank { item.displayName.substringBeforeLast('.') },
                    subtitle = subtitle,
                    accent = accent,
                    onClickLabel = if (item.kind == "AUDIO") "Putar lagu" else "Putar video",
                    enabled = available,
                    errorText = if (!available) "File tidak tersedia atau izin akses berubah" else null,
                    // Audio memakai tombol hati di baris; penanda hati hanya untuk video agar tidak dobel.
                    badges = rowBadges(item.kind == "AUDIO", item.lyricUri, sourceJob?.sidecarUris, favorite = favorite && item.kind != "AUDIO"),
                    // Aksi cepat di samping ikon lirik supaya favorit/bagikan tidak perlu buka menu ⋮.
                    quickActions = {
                        rowQuickActions(item.kind == "AUDIO", available, favorite).forEach { kind ->
                            RowIconAction(
                                MediaCardPlan.icon(kind),
                                MediaCardPlan.label(kind, item.kind == "AUDIO"),
                                onClick = { onAction(kind) },
                                tint = if (kind == MediaActionKind.FAVORITE_REMOVE) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    // Satu tap = seluruh daftar masuk antrean audio, mulai dari item ini; video memutar antrean video.
                    onClick = { if (item.kind == "AUDIO") model.playSelection(player, item.uri) else playVideo(item) },
                ) {
                    MediaActionBar(
                        audio = item.kind == "AUDIO",
                        compact = true,
                        specs = MediaCardPlan.library(
                            audio = item.kind == "AUDIO",
                            favorite = item.uri in favorites,
                            inPlaylist = filter.playlistId.isNotBlank(),
                            hasLyrics = item.lyricUri != null,
                            canRedownload = sourceJob != null,
                        ),
                        enabled = { kind ->
                            when (kind) {
                                MediaActionKind.PLAY, MediaActionKind.OPEN, MediaActionKind.SHARE,
                                MediaActionKind.ENQUEUE, MediaActionKind.PLAY_NEXT -> available
                                MediaActionKind.FIND_LYRICS -> !busy
                                MediaActionKind.REDOWNLOAD -> !busy
                                MediaActionKind.EDIT_LYRIC_TIMING, MediaActionKind.RENAME,
                                MediaActionKind.DELETE -> !busy && available
                                MediaActionKind.CLIP_STATUS -> !busy && available
                                else -> true
                            }
                        },
                        onClick = onAction,
                    )
                }
            }
            if (media.size >= filter.limit && filter.limit < 3000) item {
                ActionButton("Muat lebih banyak", model::more, emphasis = ActionEmphasis.Quiet, fillWidth = true)
            }
        }
    }
    if (filterOpen) AlertDialog(
        onDismissRequest = { filterOpen = false },
        title = { DialogTitle("Filter koleksi", icon = Icons.Outlined.FilterList) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OptionMenu(
                    label = "Urutkan",
                    options = listOf("recent" to "Terbaru", "name" to "Nama", "size" to "Ukuran", "duration" to "Durasi"),
                    selected = filter.sort,
                    onSelect = { model.filter(filter.copy(sort = it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OptionMenu(
                    label = "Folder",
                    options = listOf("" to "Semua folder") + folders.map { it to FolderAliases.label(it, folderAliases) },
                    selected = filter.folder,
                    onSelect = { model.filter(filter.copy(folder = it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (filter.kind != "VIDEO") {
                    OptionMenu(
                        label = stringResource(R.string.library_playlists),
                        options = playlistOptions,
                        selected = filter.playlistId,
                        onSelect = { model.filter(filter.copy(playlistId = it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OptionMenu(
                        label = "Artis",
                        options = listOf("" to "Semua artis") + artists.map { it to it },
                        selected = filter.artist,
                        onSelect = { model.filter(filter.copy(artist = it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OptionMenu(
                        label = "Album",
                        options = listOf("" to "Semua album") + albums.map { it to it },
                        selected = filter.album,
                        onSelect = { model.filter(filter.copy(album = it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    "Filter berlaku langsung pada daftar di belakang. Urutan dan folder juga tersedia untuk video.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { filterOpen = false }) { Text("Selesai") } },
        dismissButton = {
            TextButton(
                enabled = hasAnyFilter,
                onClick = { model.filter(MediaLibraryViewModel.Filter(kind = filter.kind, query = filter.query)) },
            ) { Text("Reset filter") }
        },
    )
    if (managePlaylists) PersonalPlaylistDialog(model) { managePlaylists = false }
    addPlaylist?.let { selected -> AlertDialog(onDismissRequest = { addPlaylist = null },
        title = { DialogTitle(stringResource(R.string.library_add_playlist), icon = Icons.Outlined.PlaylistAdd, subtitle = selected.title) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(selected.title)
                playlists.filter { it.id != PersonalLibrary.FAVORITES }.forEach { entry ->
                    ActionButton(entry.name, { model.addToPlaylist(selected, entry.id); addPlaylist = null }, fillWidth = true)
                }
                ActionButton(stringResource(R.string.library_create_playlist), { addPlaylist = null; managePlaylists = true }, icon = Icons.Outlined.PlaylistAdd, emphasis = ActionEmphasis.Quiet)
            }
        }, confirmButton = { TextButton(onClick = { addPlaylist = null }) { Text(stringResource(R.string.action_cancel)) } }) }
    detail?.let { item -> AlertDialog(onDismissRequest = { detail = null }, title = { DialogTitle("Detail media", icon = Icons.Outlined.Info) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.displayName); Text(item.folder); Text("MIME: ${item.mime.ifBlank { "Belum diketahui" }}")
            item.sizeBytes?.let { Text("Ukuran: ${Format.bytes(it)}") }; item.durationMs?.let { Text("Durasi: ${duration(it)}") }
            if (item.width != null && item.height != null) Text("Resolusi: ${item.width}×${item.height}")
            if (item.kind == "AUDIO") {
                listOf("Artis" to item.artist, "Album" to item.album, "Album Artist" to item.albumArtist, "Genre" to item.genre, "Track" to item.track, "Tahun" to item.year).filter { it.second.isNotBlank() }.forEach { Text("${it.first}: ${it.second}") }
                Text("Metadata: ${mediaStatus(item.metadataStatus)}"); Text("Artwork: ${mediaStatus(item.artworkStatus)}")
                Text(if (item.lyricStatus.isBlank()) "Lirik belum diperiksa" else LyricStatus.label(item.lyricStatus))
            }
            if (item.creator.isNotBlank()) Text("Kreator: ${item.creator}")
            Text("Ditambahkan: ${date(item.addedAt)}")
            Text("Ketersediaan: ${mediaStatus(item.availability)}")
        }
    }, confirmButton = { TextButton(onClick = { detail = null }) { Text("Tutup") } }) }
    rename?.let { item ->
        var name by remember(item.uri) { mutableStateOf(item.displayName) }
        var searchAfter by remember(item.uri) { mutableStateOf(item.kind == "AUDIO") }
        AlertDialog(onDismissRequest = { rename = null }, title = { DialogTitle("Ubah nama file", icon = Icons.Outlined.DriveFileRenameOutline) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nama file") }, singleLine = true, keyboardOptions = KeyboardOptions(autoCorrectEnabled = false))
                if (item.kind == "AUDIO") Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(searchAfter,{ searchAfter=it }); Text("Cari lirik dengan nama baru") }
                Text("Ekstensi tetap sama. File lain tidak akan ditimpa.", style = MaterialTheme.typography.bodySmall)
                ActionButton("Gunakan nama standar", { name = SmartMedia.fileName(TitleSplit.identify(item.title, metadataArtist = item.artist, metadataTrack = item.title, recognizeCollaboration=item.kind == "AUDIO"), item.title, item.displayName.substringAfterLast('.'), true) }, icon = Icons.Outlined.AutoFixHigh, emphasis = ActionEmphasis.Quiet)
            }
        }, confirmButton = { TextButton(onClick = { model.rename(item, name, searchAfter); rename = null }) { Text("Simpan") } }, dismissButton = { TextButton(onClick = { rename = null }) { Text("Batal") } })
    }
    delete?.let { item -> ConfirmDialog(
        title = "Hapus file permanen?",
        text = "${item.displayName} dan file pendamping yang tercatat akan dihapus dari penyimpanan.",
        confirmLabel = "Hapus file",
        onConfirm = { model.delete(item); delete = null },
        onDismiss = { delete = null },
        destructive = true,
    ) }
    redownload?.let { (job, item) -> ConfirmDialog(
        title = "Unduh lagi?",
        text = "\"${item.displayName}\" akan dihapus dari penyimpanan, lalu unduhan yang sama diantrekan ulang. Lirik dan lampiran lama ikut diganti.",
        confirmLabel = "Unduh lagi",
        onConfirm = { onRedownload(job); redownload = null },
        onDismiss = { redownload = null },
        destructive = true,
    ) }
    timing?.let { LyricTimingDialog(it, model) { timing = null } }
    clipStatus?.let { StatusClipDialog(it) { clipStatus = null } }

    // Tekan lama folder: semua aksi folder dikumpulkan di satu lembar agar tidak ada aksi
    // destruktif yang terpicu oleh ketukan biasa.
    folderMenu?.let { folder ->
        val label = FolderAliases.label(folder, folderAliases)
        ModalBottomSheet(
            onDismissRequest = { folderMenu = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Outlined.Folder, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            folder,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                ActionButton("Buka folder", { folderMenu = null; model.filter(filter.copy(folder = folder)) }, icon = Icons.Outlined.FolderOpen, emphasis = ActionEmphasis.Quiet, fillWidth = true)
                ActionButton("Putar semua lagu", { folderMenu = null; model.playFolder(player, folder) }, icon = Icons.Outlined.PlayArrow, emphasis = ActionEmphasis.Quiet, fillWidth = true)
                ActionButton("Detail folder", { folderMenu = null; model.folderDetail(folder) { folderDetail = it } }, icon = Icons.Outlined.Info, emphasis = ActionEmphasis.Quiet, fillWidth = true)
                ActionButton("Ganti nama tampilan", { folderMenu = null; folderRename = folder }, icon = Icons.Outlined.DriveFileRenameOutline, emphasis = ActionEmphasis.Quiet, fillWidth = true)
                ActionButton("Hapus isi folder", { folderMenu = null; folderDelete = folder }, icon = Icons.Outlined.DeleteForever, emphasis = ActionEmphasis.Quiet, destructive = true, fillWidth = true)
                Text(
                    "Ganti nama hanya mengubah label di TubeFlow. Nama folder di penyimpanan tidak berubah agar berkas, lirik, dan riwayat unduhan tetap terhubung.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    folderDetail?.let { summary ->
        val label = FolderAliases.label(summary.folder, folderAliases)
        AlertDialog(
            onDismissRequest = { folderDetail = null },
            title = { DialogTitle("Detail folder", icon = Icons.Outlined.Info, subtitle = label) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Lokasi: ${summary.folder}")
                    Text("Item: ${summary.total} · ${summary.audio} audio · ${summary.video} video")
                    Text("Tersedia: ${summary.available} · Tidak tersedia: ${summary.unavailable}")
                    Text(
                        "Ukuran: ${Format.bytes(summary.bytes)}" +
                            if (summary.unknownSizes > 0) " + ${summary.unknownSizes} berkas tanpa ukuran" else "",
                    )
                    Text(
                        "Nama tampilan hanya label TubeFlow; folder di penyimpanan tetap memakai lokasi di atas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { folderDetail = null }) { Text("Tutup") } },
        )
    }

    folderRename?.let { folder ->
        val stored = SettingsStore.folderAliases[FolderAliases.normalize(folder)]
        var name by remember(folder, stored) {
            mutableStateOf(stored ?: FolderAliases.label(folder, folderAliases).substringAfterLast('/'))
        }
        val problem = FolderAliases.validate(name, folder, folderAliases)
        AlertDialog(
            onDismissRequest = { folderRename = null },
            title = { DialogTitle("Ganti nama tampilan", icon = Icons.Outlined.DriveFileRenameOutline, subtitle = folder) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nama folder") },
                        singleLine = true,
                        isError = problem != null,
                        supportingText = { Text(problem ?: "Hanya label di TubeFlow; nama di penyimpanan tidak berubah.") },
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    )
                    if (stored != null) {
                        ActionButton(
                            "Kembalikan nama penyimpanan",
                            { model.resetFolderName(folder); folderRename = null },
                            emphasis = ActionEmphasis.Quiet,
                            fillWidth = true,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = problem == null, onClick = { model.renameFolder(folder, name); folderRename = null }) {
                    Text("Simpan")
                }
            },
            dismissButton = { TextButton(onClick = { folderRename = null }) { Text("Batal") } },
        )
    }

    folderDelete?.let { folder ->
        val label = FolderAliases.label(folder, folderAliases)
        ConfirmDialog(
            title = "Hapus isi folder?",
            text = "Semua file tersedia di \"$label\" (termasuk lirik dan thumbnail terkait) akan dihapus permanen. Folder fisik dan nama folder di penyimpanan tetap ada.",
            confirmLabel = "Hapus isi",
            onConfirm = { model.deleteFolder(folder); folderDelete = null },
            onDismiss = { folderDelete = null },
            destructive = true,
        )
    }
}

@Composable
private fun FolderSectionHeading(count: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionBanner("Daftar folder", icon = Icons.Outlined.Folder, trailing = "$count folder")
        Text(
            "Tekan lama folder untuk detail, ganti nama tampilan, atau hapus isinya.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Folder menampilkan ikon, nama tampilan, letak, dan jumlah file — tanpa kartu dan tanpa panah.
 * Ketuk membuka folder; tekan lama membuka aksi folder (detail, nama tampilan, hapus isi).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderTile(
    folder: String,
    alias: String,
    total: Long,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onLongClick: () -> Unit,
) {
    val label = alias
    val name = label.substringAfterLast('/').ifBlank { label }
    val parent = label.substringBeforeLast('/', "").trim().ifBlank { null }
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth()
            .heightIn(min = 112.dp)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClickLabel = "Buka folder $label",
                onLongClickLabel = "Aksi folder $label",
                onClick = onOpen,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconBadge(Icons.Outlined.Folder, size = 48.dp)
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (parent != null) Text(
            parent,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Outlined.FolderOpen, null, Modifier.size(13.dp), tint = scheme.primary)
            Text("$total item", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
        }
    }
}

/** Breadcrumb dua tingkat: akar Koleksi selalu bisa diketuk untuk kembali ke grid folder. */
@Composable
private fun LibraryBreadcrumb(path: String, onRoot: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onRoot,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) { Text("Koleksi", style = MaterialTheme.typography.labelLarge) }
            Icon(
                Icons.Outlined.ChevronRight,
                null,
                Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                path,
                modifier = Modifier.weight(1f).padding(start = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Dialog atur waktu lirik; dipakai Koleksi DAN pemutar penuh supaya tidak bolak-balik. */
@Composable
fun LyricTimingDialog(item: MediaItem, model: MediaLibraryViewModel, dismiss: () -> Unit) {
    var preview by remember { mutableStateOf<LyricEditor.Preview?>(null) }
    var seconds by remember { mutableStateOf("0") }
    val offset=LrcTiming.parseSeconds(seconds)
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val busy by model.busy.collectAsStateWithLifecycle()
    LaunchedEffect(item.uri) {
        try { preview = model.lyricEditor.preview(item).also { seconds = LrcTiming.secondsLabel(it.offsetMs) } }
        catch (failure: Exception) { error = failure.message }
    }
    LaunchedEffect(preview, offset) { preview?.let { original ->
        if (offset == null) { error="Masukkan angka −30 sampai 30 detik, maksimal tiga desimal."; return@LaunchedEffect }
        try { text = withContext(Dispatchers.IO) { LrcTiming.shift(original.original, offset, item.durationMs).lineSequence().take(8).joinToString("\n") }; error = null }
        catch (failure: Exception) { error = failure.message }
    } }
    AlertDialog(onDismissRequest = dismiss, title = { DialogTitle("Waktu lirik", subtitle = item.title, icon = Icons.Outlined.EditNote) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sorotan terlambat: majukan lirik. Koreksi hanya lagu ini; perubahan tersimpan pada LRC.")
            OutlinedTextField(seconds, { seconds=it }, label={ Text("Koreksi waktu (detik)") }, supportingText={ Text("Negatif = majukan, positif = mundurkan. Contoh: −2 atau 0,5. Total dari waktu asli.") }, keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Text), singleLine=true, isError=offset == null, enabled=preview != null)
            ScrollRow {
                ActionButton("Majukan 0,5 detik", { seconds = LrcTiming.secondsLabel(((offset ?: 0) - 500).coerceAtLeast(-LrcTiming.LIMIT_MS)) }, emphasis = ActionEmphasis.Quiet, enabled = preview != null)
                ActionButton("Mundurkan 0,5 detik", { seconds = LrcTiming.secondsLabel(((offset ?: 0) + 500).coerceAtMost(LrcTiming.LIMIT_MS)) }, emphasis = ActionEmphasis.Quiet, enabled = preview != null)
            }
            ActionButton("Reset ke asli", { seconds = "0" }, icon = Icons.Outlined.RestartAlt, emphasis = ActionEmphasis.Quiet, enabled = preview != null)
            if (preview == null && error == null) CircularProgressIndicator(Modifier.size(24.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
            Text("Preview timestamp. Buka ulang lirik di pemutar setelah simpan. Selisih yang makin berubah memerlukan rekaman lirik lain.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = preview != null && offset != null && error == null && !busy, onClick = { preview?.let { offset?.let { value -> model.correct(item, it, value) } }; dismiss() }) { Text("Simpan") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Batal") } })
}

fun duration(ms: Long) = "%d:%02d".format(java.util.Locale.ROOT, ms / 60000, ms / 1000 % 60)
fun date(at: Long): String = java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.forLanguageTag("id")).format(java.util.Date(at))

private fun mediaStatus(status: String) = when (status) {
    "READY" -> "Judul dan artis tersedia"; "PARTIAL" -> "Belum lengkap"; "UNCHECKED" -> "Belum diperiksa"; "FAILED" -> "Gagal diproses"
    "PRESENT" -> "Tersedia"; "ABSENT" -> "Belum tersedia"; "AVAILABLE" -> "File dapat diakses"; "ACCESS_DENIED" -> "Izin akses berubah"; "UNAVAILABLE" -> "File tidak ditemukan"; else -> "Belum diketahui"
}
