package id.local.tubeflow.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import id.local.tubeflow.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.local.tubeflow.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MediaLibraryScreen(model: MediaLibraryViewModel, player: PlayerViewModel) {
    LaunchedEffect(Unit) { model.validateAvailability() }
    val media by model.items.collectAsStateWithLifecycle()
    val playlists by model.playlists.collectAsStateWithLifecycle()
    val favorites by model.favorites.collectAsStateWithLifecycle()
    var managePlaylists by remember { mutableStateOf(false) }
    var addPlaylist by remember { mutableStateOf<MediaItem?>(null) }
    val filter by model.selectedFilter.collectAsStateWithLifecycle()
    val folders by model.folders.collectAsStateWithLifecycle()
    val artists by model.artists.collectAsStateWithLifecycle()
    val albums by model.albums.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var detail by remember { mutableStateOf<MediaItem?>(null) }
    var rename by remember { mutableStateOf<MediaItem?>(null) }
    var delete by remember { mutableStateOf<MediaItem?>(null) }
    var timing by remember { mutableStateOf<MediaItem?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var openError by remember { mutableStateOf<String?>(null) }
    fun open(item: MediaItem) { scope.launch {
        openError = withContext(Dispatchers.IO) { launchMedia(context, item.location, item.kind == "AUDIO", false) }
    } }
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(UiSpacing.screen), verticalArrangement = Arrangement.spacedBy(UiSpacing.item),
    ) {
        item { PageHeading(if (filter.kind == "AUDIO") "Koleksi musik" else if (filter.kind == "VIDEO") "Koleksi video" else "Koleksi media", "File di perangkat, terpisah dari riwayat download.") }
        item { ScrollRow {
            listOf("" to "Semua", "AUDIO" to "Musik", "VIDEO" to "Video").forEach { (kind, name) ->
                FilterChip(filter.kind == kind, onClick = { model.filter(MediaLibraryViewModel.Filter(kind = kind)) }, label = { Text(name) })
            }
        } }
        val playable = media.filter { it.kind == "AUDIO" && it.availability == "AVAILABLE" }
        // Aksi koleksi berlabel supaya tujuan setiap tombol jelas tanpa menebak ikon.
        item { ScrollRow {
            ActionButton("Putar", { model.playSelection(player, shuffle = false) }, icon = Icons.Outlined.PlayArrow, enabled = playable.isNotEmpty())
            ActionButton("Acak", { model.playSelection(player, shuffle = true) }, icon = Icons.Outlined.Shuffle, enabled = playable.size > 1)
            ActionButton(
                if (searchOpen) "Tutup" else "Cari",
                { if (searchOpen) model.filter(filter.copy(query = "")); searchOpen = !searchOpen },
                icon = if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search,
                emphasis = ActionEmphasis.Quiet,
            )
        } }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Memeriksa media…") }
        if (searchOpen) item {
            SearchField(filter.query, { model.filter(filter.copy(query = it)) }, label = "Cari judul, artis, nama file")
        }            // Semua kontrol filter memakai satu bentuk chip: Favorit (aktif/tidak), dropdown playlist,
            // aksi kelola, dan dropdown urutan/folder/artis/album.
            item { ScrollRow {
                FilterChip(filter.playlistId == PersonalLibrary.FAVORITES, onClick = { model.filter(MediaLibraryViewModel.Filter(kind = "AUDIO", playlistId = if (filter.playlistId == PersonalLibrary.FAVORITES) "" else PersonalLibrary.FAVORITES)) }, label = { Text(stringResource(R.string.library_favorites)) })
                MenuChip(
                    label = stringResource(R.string.library_playlists),
                    options = listOf("" to stringResource(R.string.library_all_playlists)) + playlists.filter { it.id != PersonalLibrary.FAVORITES }.map { it.id to it.name },
                    selected = filter.playlistId,
                    onSelect = { model.filter(MediaLibraryViewModel.Filter(kind = "AUDIO", playlistId = it)) },
                )
                ActionChip(stringResource(R.string.library_manage_playlists), { managePlaylists = true }, icon = Icons.Outlined.PlaylistAdd)
            } }
            item { ScrollRow {
                MenuChip("Terbaru", listOf("recent" to "Terbaru", "name" to "Nama", "size" to "Ukuran", "duration" to "Durasi"), filter.sort, { model.filter(filter.copy(sort = it)) })
                MenuChip("Semua folder", listOf("" to "Semua folder") + folders.map { it to it }, filter.folder, { model.filter(filter.copy(folder = it)) })
                if (filter.kind == "AUDIO") {
                    MenuChip("Semua artis", listOf("" to "Semua artis") + artists.map { it to it }, filter.artist, { model.filter(filter.copy(artist = it)) })
                    MenuChip("Semua album", listOf("" to "Semua album") + albums.map { it to it }, filter.album, { model.filter(filter.copy(album = it)) })
                }
            } }
        openError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        if (media.isEmpty() && !busy) item {
            if (filter.playlistId.isNotBlank()) EmptyState(Icons.Outlined.LibraryMusic, stringResource(if (filter.playlistId == PersonalLibrary.FAVORITES) R.string.library_favorites_empty else R.string.library_playlist_empty), stringResource(R.string.library_playlist_empty_help))
            else EmptyState(
                Icons.Outlined.Folder,
                "Belum ada media yang cocok",
                "Folder Download/TubeFlow dipindai otomatis saat aplikasi dibuka. File tidak disalin.",
                actionLabel = "Segarkan katalog",
                onAction = { model.scan() },
            )
        }
        items(media, key = { it.uri }) { item ->
            val accent=rememberCoverAccent(item.coverPath)
            var menu by remember(item.uri) { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth().clickable(enabled = item.availability == "AVAILABLE", onClickLabel = if (item.kind == "AUDIO") "Putar lagu" else "Putar video") {
                // Satu tap = seluruh daftar masuk antrean, mulai dari item ini.
                if (item.kind == "AUDIO") model.playSelection(player, item.uri) else open(item)
            },colors=mediaCardColors(accent)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CoverThumb(item.coverPath, "", if (item.kind == "AUDIO") MediaMode.AUDIO else MediaMode.VIDEO, Modifier.size(60.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (item.kind == "AUDIO") Format.musicLabel(TitleSplit.identify(item.title, metadataArtist=item.artist, recognizeCollaboration=false)) else item.title, fontWeight=FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                        // Nama file hanya ditampilkan bila tidak mengulang judul — hierarki seperti daftar lagu.
                        val fileName = item.displayName.substringBeforeLast('.')
                        if (item.title.isBlank() || (!fileName.startsWith(item.title, ignoreCase = true) && fileName != item.title)) {
                            Text(item.displayName, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                        }
                        if (item.kind == "AUDIO") Text(item.artist.ifBlank { "Artis belum diketahui" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(listOfNotNull(item.kind.let { if (it == "AUDIO") "Audio" else "Video" }, item.sizeBytes?.let(Format::bytes), item.durationMs?.let { duration(it) }).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (item.platformId.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            PlatformIcon(SourcePlatforms.fromId(item.platformId), Modifier.size(14.dp)); Text(SourcePlatforms.fromId(item.platformId).label, style = MaterialTheme.typography.labelSmall)
                        }
                        if (item.availability != "AVAILABLE") Text("File tidak tersedia atau izin akses berubah", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                    // Menu tiga titik: aksi sekunder tidak memicu playback saat diketuk.
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Menu ${item.title}") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(if (item.kind == "AUDIO") "Putar sekarang" else "Buka di pemutar") },
                            leadingIcon = { Icon(Icons.Outlined.PlayArrow, null) },
                            onClick = { menu = false; if (item.kind == "AUDIO") model.playSelection(player, item.uri) else open(item) })
                        if (item.kind == "AUDIO") {
                            if (filter.playlistId.isNotBlank()) DropdownMenuItem(text = { Text(stringResource(R.string.library_remove_playlist)) }, onClick = { menu = false; model.removeFromPlaylist(item, filter.playlistId) })
                            DropdownMenuItem(text = { Text("Putar berikutnya") }, onClick = { menu = false; player.playNext(item) })
                        }
                        DropdownMenuItem(text = { Text("Detail") }, onClick = { menu = false; detail = item })
                        if (item.kind == "AUDIO") DropdownMenuItem(text = { Text("Cari lirik") }, enabled = !busy,
                            onClick = { menu = false; model.openLyrics(item) })
                        if (item.kind == "AUDIO" && item.lyricUri != null) DropdownMenuItem(text = { Text("Atur waktu lirik") }, enabled = !busy,
                            onClick = { menu = false; timing = item })
                        DropdownMenuItem(text = { Text("Ubah nama") }, enabled = !busy && item.availability == "AVAILABLE",
                            onClick = { menu = false; rename = item })
                        DropdownMenuItem(text = { Text("Hapus file", color = MaterialTheme.colorScheme.error) }, enabled = !busy && item.availability == "AVAILABLE",
                            onClick = { menu = false; delete = item })
                    }
                }
                // Aksi lagu yang sering dipakai tampil sebagai ikon langsung di kartu, bukan tersembunyi
                // di menu tiga titik; menu menyisakan aksi informasi dan berkas.
                if (item.kind == "AUDIO") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val favorite = item.uri in favorites
                        IconAction(
                            if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                            stringResource(if (favorite) R.string.library_unfavorite else R.string.library_favorite),
                            { if (favorite) model.removeFromPlaylist(item, PersonalLibrary.FAVORITES) else model.addToPlaylist(item, PersonalLibrary.FAVORITES) },
                        )
                        IconAction(Icons.Outlined.PlaylistAdd, stringResource(R.string.library_add_playlist), { addPlaylist = item })
                        IconAction(Icons.AutoMirrored.Outlined.QueueMusic, "Tambah ke antrean", { player.enqueue(item) })
                    }
                }
            }
        } }
        if (media.size >= filter.limit && filter.limit < 3000) item {
            ActionButton("Muat lebih banyak", model::more, emphasis = ActionEmphasis.Quiet, fillWidth = true)
        }
    }
    if (managePlaylists) PersonalPlaylistDialog(model) { managePlaylists = false }
    addPlaylist?.let { selected -> AlertDialog(onDismissRequest = { addPlaylist = null },
        title = { Text(stringResource(R.string.library_add_playlist)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(selected.title)
                playlists.filter { it.id != PersonalLibrary.FAVORITES }.forEach { entry ->
                    ActionButton(entry.name, { model.addToPlaylist(selected, entry.id); addPlaylist = null }, fillWidth = true)
                }
                ActionButton(stringResource(R.string.library_create_playlist), { addPlaylist = null; managePlaylists = true }, icon = Icons.Outlined.PlaylistAdd, emphasis = ActionEmphasis.Quiet)
            }
        }, confirmButton = { TextButton(onClick = { addPlaylist = null }) { Text(stringResource(R.string.action_cancel)) } }) }
    detail?.let { item -> AlertDialog(onDismissRequest = { detail = null }, title = { Text("Detail media") }, text = {
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
        AlertDialog(onDismissRequest = { rename = null }, title = { Text("Ubah nama file") }, text = {
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
    timing?.let { LyricTimingDialog(it, model) { timing = null } }
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
    AlertDialog(onDismissRequest = dismiss, title = { Text("Waktu lirik · ${item.title}") }, text = {
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
