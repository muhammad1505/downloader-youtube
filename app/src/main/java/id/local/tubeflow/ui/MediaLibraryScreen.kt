package id.local.tubeflow.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.compositeOver
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.local.tubeflow.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MediaLibraryScreen(model: MediaLibraryViewModel, onPlay: (MediaItem) -> Unit, history: @Composable () -> Unit) {
    LaunchedEffect(Unit) { model.validateAvailability() }
    val media by model.items.collectAsStateWithLifecycle()
    val filter by model.selectedFilter.collectAsStateWithLifecycle()
    val folders by model.folders.collectAsStateWithLifecycle()
    val artists by model.artists.collectAsStateWithLifecycle()
    val albums by model.albums.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    var showHistory by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<MediaItem?>(null) }
    var rename by remember { mutableStateOf<MediaItem?>(null) }
    var delete by remember { mutableStateOf<MediaItem?>(null) }
    var timing by remember { mutableStateOf<MediaItem?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { model.import(it) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var openError by remember { mutableStateOf<String?>(null) }
    fun open(item: MediaItem) { scope.launch {
        openError = withContext(Dispatchers.IO) { launchMedia(context, item.location, item.kind == "AUDIO", false) }
    } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("" to "Semua", "AUDIO" to "Musik", "VIDEO" to "Video").forEach { (kind, name) ->
                FilterChip(!showHistory && filter.kind == kind, onClick = { showHistory = false; model.filter(MediaLibraryViewModel.Filter(kind = kind)) }, label = { Text(name) })
            }
            FilterChip(showHistory, onClick = { showHistory = true }, label = { Text("Riwayat") })
        }
        if (showHistory) history() else LazyColumn(
            Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PageHeading(if (filter.kind == "AUDIO") "Koleksi musik" else if (filter.kind == "VIDEO") "Koleksi video" else "Koleksi media", "File di perangkat, terpisah dari riwayat download.") }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = model::scan, enabled = !busy) { Text("Pindai folder") }
                TextButton(onClick = { picker.launch(arrayOf("audio/*", "video/*")) }, enabled = !busy) { Text("Impor file") }
            } }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Memeriksa media…") }
            item { OutlinedTextField(filter.query, onValueChange = { model.filter(filter.copy(query = it)) }, label = { Text("Cari judul, artis, nama file") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceMenu("Urut", listOf("recent" to "Terbaru", "name" to "Nama", "size" to "Ukuran", "duration" to "Durasi"), filter.sort) { model.filter(filter.copy(sort = it)) }
                ChoiceMenu("Folder", listOf("" to "Semua folder") + folders.map { it to it }, filter.folder) { model.filter(filter.copy(folder = it)) }
                if (filter.kind == "AUDIO") {
                    ChoiceMenu("Artis", listOf("" to "Semua artis") + artists.map { it to it }, filter.artist) { model.filter(filter.copy(artist = it)) }
                    ChoiceMenu("Album", listOf("" to "Semua album") + albums.map { it to it }, filter.album) { model.filter(filter.copy(album = it)) }
                }
            } }
            openError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            if (media.isEmpty() && !busy) item { EmptyState(Icons.Outlined.Folder, "Belum ada media yang cocok", "Pindai folder TubeFlow atau pilih file lewat Impor file. File tidak disalin.") }
            items(media, key = { it.uri }) { item ->
                val accent=rememberCoverAccent(item.coverPath)
                Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=accent.copy(alpha=0.06f).compositeOver(MaterialTheme.colorScheme.surface)),border=BorderStroke(1.dp,accent.copy(alpha=0.18f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CoverThumb(item.coverPath, "", if (item.kind == "AUDIO") MediaMode.AUDIO else MediaMode.VIDEO, Modifier.size(60.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (item.kind == "AUDIO") Format.musicLabel(TitleSplit.identify(item.title, metadataArtist=item.artist, recognizeCollaboration=false)) else item.title, fontWeight=FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            if (item.displayName.substringBeforeLast('.') != item.title) Text(item.displayName, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                            if (item.kind == "AUDIO") Text(item.artist.ifBlank { "Artis belum diketahui" }, style = MaterialTheme.typography.bodySmall)
                            Text(listOfNotNull(item.kind.let { if (it == "AUDIO") "Audio" else "Video" }, item.sizeBytes?.let(Format::bytes), item.durationMs?.let { duration(it) }).joinToString(" · "), style = MaterialTheme.typography.labelSmall)
                            if (item.platformId.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                PlatformIcon(SourcePlatforms.fromId(item.platformId), Modifier.size(14.dp)); Text(SourcePlatforms.fromId(item.platformId).label, style = MaterialTheme.typography.labelSmall)
                            }
                            if (item.availability != "AVAILABLE") Text("File tidak tersedia atau izin akses berubah", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { if (item.kind == "AUDIO") onPlay(item) else open(item) }, enabled = item.availability == "AVAILABLE") { Text("Putar") }
                        TextButton(onClick = { detail = item }) { Text("Detail") }
                        TextButton(onClick = { rename = item }, enabled = !busy && item.availability == "AVAILABLE") { Text("Ubah nama") }
                        IconButton(onClick = { delete = item }, enabled = !busy && item.availability == "AVAILABLE") { Icon(Icons.Outlined.Delete, "Hapus media") }
                    }
                    if (item.kind == "AUDIO" && item.lyricUri != null) TextButton(onClick = { timing = item }, enabled = !busy && item.availability == "AVAILABLE") { Text("Atur waktu lirik") }
                }
            } }
            if (media.size >= filter.limit && filter.limit < 3000) item { TextButton(onClick = model::more) { Text("Muat lebih banyak") } }
        }
    }
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
        AlertDialog(onDismissRequest = { rename = null }, title = { Text("Ubah nama file") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nama file") }, singleLine = true, keyboardOptions = KeyboardOptions(autoCorrectEnabled = false))
                Text("Ekstensi tetap sama. File lain tidak akan ditimpa.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { name = SmartMedia.fileName(TitleSplit.identify(item.title, metadataArtist = item.artist, metadataTrack = item.title, recognizeCollaboration=item.kind == "AUDIO"), item.title, item.displayName.substringAfterLast('.'), true) }) { Text("Gunakan nama standar") }
            }
        }, confirmButton = { TextButton(onClick = { model.rename(item, name); rename = null }) { Text("Simpan") } }, dismissButton = { TextButton(onClick = { rename = null }) { Text("Batal") } })
    }
    delete?.let { item -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("Hapus file permanen?") }, text = { Text("${item.displayName} dan file pendamping yang tercatat akan dihapus dari penyimpanan.") }, confirmButton = { TextButton(onClick = { model.delete(item); delete = null }) { Text("Hapus file") } }, dismissButton = { TextButton(onClick = { delete = null }) { Text("Batal") } }) }
    timing?.let { LyricTimingDialog(it, model) { timing = null } }
}

@Composable
private fun ChoiceMenu(label: String, choices: List<Pair<String, String>>, selected: String, choose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(choices.firstOrNull { it.first == selected }?.second ?: label) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            choices.forEach { (id, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { choose(id); expanded = false }) }
        }
    }
}

@Composable
private fun LyricTimingDialog(item: MediaItem, model: MediaLibraryViewModel, dismiss: () -> Unit) {
    var preview by remember { mutableStateOf<LyricEditor.Preview?>(null) }
    var offset by remember { mutableLongStateOf(0) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val busy by model.busy.collectAsStateWithLifecycle()
    LaunchedEffect(item.uri) {
        try { preview = model.lyricEditor.preview(item).also { offset = it.offsetMs } }
        catch (failure: Exception) { error = failure.message }
    }
    LaunchedEffect(preview, offset) { preview?.let { original ->
        try { text = withContext(Dispatchers.IO) { LrcTiming.shift(original.original, offset, item.durationMs).lineSequence().take(8).joinToString("\n") }; error = null }
        catch (failure: Exception) { error = failure.message }
    } }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Waktu lirik · ${item.title}") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sorotan terlambat: majukan lirik. Koreksi hanya lagu ini; perubahan tersimpan pada LRC.")
            Text(if (offset == 0L) "Waktu asli" else "${if (offset < 0) "Dimajukan" else "Dimundurkan"} ${kotlin.math.abs(offset) / 1000.0} detik")
            Column {
                TextButton(onClick = { offset = (offset - 500).coerceAtLeast(-LrcTiming.LIMIT_MS) }, enabled = preview != null) { Text("Majukan 0,5 detik") }
                TextButton(onClick = { offset = (offset + 500).coerceAtMost(LrcTiming.LIMIT_MS) }, enabled = preview != null) { Text("Mundurkan 0,5 detik") }
            }
            TextButton(onClick = { offset = 0 }, enabled = preview != null) { Text("Reset ke asli") }
            if (preview == null && error == null) CircularProgressIndicator(Modifier.size(24.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
            Text("Preview timestamp. Buka ulang lirik di pemutar setelah simpan. Selisih yang makin berubah memerlukan rekaman lirik lain.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = preview != null && error == null && !busy, onClick = { preview?.let { model.correct(item, it, offset) }; dismiss() }) { Text("Simpan") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Batal") } })
}

fun duration(ms: Long) = "%d:%02d".format(java.util.Locale.ROOT, ms / 60000, ms / 1000 % 60)
fun date(at: Long): String = java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.forLanguageTag("id")).format(java.util.Date(at))

private fun mediaStatus(status: String) = when (status) {
    "READY" -> "Judul dan artis tersedia"; "PARTIAL" -> "Belum lengkap"; "UNCHECKED" -> "Belum diperiksa"; "FAILED" -> "Gagal diproses"
    "PRESENT" -> "Tersedia"; "ABSENT" -> "Belum tersedia"; "AVAILABLE" -> "File dapat diakses"; "ACCESS_DENIED" -> "Izin akses berubah"; "UNAVAILABLE" -> "File tidak ditemukan"; else -> "Belum diketahui"
}
