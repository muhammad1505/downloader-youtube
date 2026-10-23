package id.local.tubeflow.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FeaturedPlayList
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import id.local.tubeflow.core.DownloadOptions
import id.local.tubeflow.core.Format
import id.local.tubeflow.core.InspectionPhase
import id.local.tubeflow.core.MediaMode
import id.local.tubeflow.core.PlaylistEntry
import id.local.tubeflow.core.PlaylistMetadata
import id.local.tubeflow.core.Selection
import id.local.tubeflow.core.SettingsStore
import id.local.tubeflow.core.TitleSplit
import id.local.tubeflow.core.VideoMetadata
import kotlinx.coroutines.delay

private data class Preset(val label: String, val mode: MediaMode, val format: String, val quality: Int, val bitrate: Int)

private val videoPresets = listOf(
    Preset("Otomatis", MediaMode.VIDEO, "auto", 0, 0),
    Preset("1080p MP4", MediaMode.VIDEO, "mp4", 1080, 0),
    Preset("720p MP4", MediaMode.VIDEO, "mp4", 720, 0),
    Preset("4K MP4", MediaMode.VIDEO, "mp4", 2160, 0),
)
private val audioPresets = listOf(
    Preset("Otomatis", MediaMode.AUDIO, "auto", 0, 0),
    Preset("MP3 320", MediaMode.AUDIO, "mp3", 0, 320),
    Preset("MP3 128", MediaMode.AUDIO, "mp3", 0, 128),
    Preset("M4A terbaik", MediaMode.AUDIO, "m4a", 0, 0),
    Preset("FLAC lossless", MediaMode.AUDIO, "flac", 0, 0),
)

private val subtitleLabels = listOf("" to "Tanpa subtitle", "asli" to "Bahasa asli", "id" to "Bahasa Indonesia", "en" to "Bahasa Inggris", ".*" to "Semua bahasa")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    url: String,
    onUrl: (String) -> Unit,
    metadata: VideoMetadata?,
    playlist: PlaylistMetadata?,
    busy: Boolean,
    inspectPhase: String?,
    inspectStartedAt: Long,
    inspectError: String?,
    inspectDetails: id.local.tubeflow.core.FailureDetails? = null,
    listState: LazyListState,
    inspect: () -> Unit,
    inspectPlaylist: () -> Unit,
    retryInspect: () -> Unit,
    clearPlaylist: () -> Unit,
    cancelInspect: () -> Unit,
    download: (DownloadOptions) -> Unit,
    downloadPlaylist: (List<PlaylistEntry>, DownloadOptions) -> Unit,
) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(MediaMode.VIDEO) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var videoFormat by rememberSaveable { mutableStateOf("mp4") }
    var audioFormat by rememberSaveable { mutableStateOf("mp3") }
    var quality by rememberSaveable { mutableIntStateOf(0) }
    var bitrate by rememberSaveable { mutableIntStateOf(0) }
    var preset by rememberSaveable { mutableStateOf("") }
    var selectedIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var playlistQuery by rememberSaveable { mutableStateOf("") }
    var showFailureDetails by remember(url.trim()) { mutableStateOf(false) }
    if (showFailureDetails && inspectDetails != null) FailureDetailsDialog(inspectDetails) { showFailureDetails = false }
    var inspectedUrl by rememberSaveable { mutableStateOf("") }

    var subtitleLang by rememberSaveable { mutableStateOf("") }
    // Lirik audio hanya on/off dari penyedia lirik — pilihan bahasa berlaku untuk subtitle video (BUG-009).
    var lyrics by rememberSaveable { mutableStateOf(SettingsStore.autoLyrics) }
    var embedSubs by rememberSaveable { mutableStateOf(false) }
    var embedThumb by rememberSaveable { mutableStateOf(SettingsStore.autoArtwork) }
    LaunchedEffect(SettingsStore.autoLyrics) { lyrics = SettingsStore.autoLyrics }
    LaunchedEffect(SettingsStore.autoArtwork) { embedThumb = SettingsStore.autoArtwork }

    val trimmed = url.trim()
    val isVideo = Selection.validUrl(trimmed)
    val isPlaylistLink = Selection.validPlaylistUrl(trimmed)
    val hasList = Selection.hasListParam(trimmed)
    val shownPlaylist = playlist?.takeIf { it.url == trimmed }
    val shownVideo = metadata?.takeIf { it.url == trimmed && (shownPlaylist == null) }
    var inspectElapsed by remember(busy, inspectStartedAt) { mutableLongStateOf(0L) }
    LaunchedEffect(busy, inspectStartedAt) {
        if (!busy) { inspectElapsed = 0L; return@LaunchedEffect }
        while (true) {
            inspectElapsed = inspectStartedAt.takeIf { it > 0 }
                ?.let { ((System.currentTimeMillis() - it) / 1000).coerceAtLeast(0) } ?: 0L
            delay(1000)
        }
    }
    val inspectProgressText = (inspectPhase?.let(InspectionPhase::label) ?: "Membaca detail link…") +
        if (inspectElapsed >= 3) " · ${inspectElapsed} dtk" else ""

    // Auto-baca detail: jeda singkat lalu inspect tanpa tombol "Cek link".
    LaunchedEffect(trimmed) {
        if (!isVideo && !isPlaylistLink) { inspectedUrl = ""; return@LaunchedEffect }
        delay(700)
        if (shownVideo?.url == trimmed || shownPlaylist?.url == trimmed) { inspectedUrl = trimmed; return@LaunchedEffect }
        if (inspectedUrl == trimmed) return@LaunchedEffect
        inspectedUrl = trimmed
        // Tautan yang juga video (watch/shorts/youtu.be + list) tetap membuka video; buka playlist lewat tombol (BUG-007).
        if (isPlaylistLink && !isVideo) inspectPlaylist() else inspect()
    }

    fun applyPreset(p: Preset) {
        mode = p.mode; preset = p.label
        if (p.mode == MediaMode.VIDEO) { videoFormat = p.format; quality = p.quality }
        else { audioFormat = p.format; bitrate = p.bitrate }
    }

    val presets = if (mode == MediaMode.VIDEO) videoPresets else audioPresets
    val activePreset = presets.find { it.label == preset }

    fun buildOptions(target: String) = DownloadOptions(
        url = target,
        mode = mode,
        format = if (mode == MediaMode.VIDEO) videoFormat else audioFormat,
        quality = if (mode == MediaMode.VIDEO) quality else 0,
        bitrate = if (mode == MediaMode.AUDIO && audioFormat !in listOf("flac", "wav", "vorbis")) bitrate else 0,
        subtitles = if (mode == MediaMode.VIDEO) subtitleLang else if (lyrics) "asli" else "",
        embedSubs = embedSubs && subtitleLang.isNotEmpty() && mode == MediaMode.VIDEO,
        thumbnail = false,
        embedThumb = embedThumb && mode == MediaMode.AUDIO && Selection.coverSupported(mode, audioFormat),
    )

    val entries = shownPlaylist?.entries.orEmpty()
    val visibleEntries = if (playlistQuery.isBlank()) entries else entries.filter { it.title.contains(playlistQuery.trim(), ignoreCase = true) }
    val allSelected = visibleEntries.isNotEmpty() && visibleEntries.all { it.id in selectedIds }
    val selectionCount = entries.count { it.id in selectedIds }

    LaunchedEffect(shownPlaylist?.url) { selectedIds = shownPlaylist?.entries?.map { it.id }?.toSet().orEmpty(); playlistQuery = "" }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(UiSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.item),
    ) {
        item {
            PageHeading(stringResource(id.local.tubeflow.R.string.page_download), stringResource(id.local.tubeflow.R.string.page_download_help))
        }

        item {
            OutlinedTextField(
                value = url,
                onValueChange = { onUrl(it); if (shownPlaylist != null) clearPlaylist() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Link video atau playlist") },
                placeholder = { Text("https://youtu.be/… atau playlist?list=…") },
                singleLine = true,
                isError = url.isNotBlank() && !isVideo && !isPlaylistLink,
                supportingText = {
                    when {
                        busy -> Text(inspectProgressText)
                        url.isNotBlank() && !isVideo && !isPlaylistLink -> Text("Link belum didukung — gunakan https dari platform yang tersedia.")
                        isPlaylistLink -> Text("Terdeteksi link playlist.")
                        hasList -> Text("Link ini bagian dari sebuah playlist.")
                        else -> Text("Contoh: youtube.com, youtu.be, tiktok.com, soundcloud.com.")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else if (url.isEmpty()) {
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val text = runCatching { clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()
                                val link = text?.let(::extractLink).orEmpty()
                                onUrl(link)
                                clearPlaylist()
                            }) { Icon(Icons.Outlined.ContentPaste, "Tempel link dari papan klip") }
                        }
                        if (url.isNotEmpty()) {
                            IconButton(onClick = { onUrl(""); clearPlaylist() }) { Icon(Icons.Outlined.Close, "Hapus link") }
                        }
                    }
                },
            )
            if (busy) {
                TextButton(onClick = cancelInspect, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Icon(Icons.Outlined.Close, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Batalkan pemeriksaan")
                }
            }
            if (hasList && isVideo) {
                ActionButton(
                    "Link ini punya playlist — buka sebagai playlist",
                    inspectPlaylist,
                    icon = Icons.AutoMirrored.Outlined.FeaturedPlayList,
                    emphasis = ActionEmphasis.Quiet,
                    enabled = !busy,
                    fillWidth = true,
                )
            }
            // Inspeksi gagal untuk URL ini: sediakan coba lagi tanpa perlu mengganti tautan (BUG-006).
            if (inspectDetails != null && !busy && trimmed.isNotBlank()) {
                ActionButton(
                    androidx.compose.ui.res.stringResource(id.local.tubeflow.R.string.failure_details),
                    { showFailureDetails = true },
                    icon = Icons.Outlined.Info,
                    emphasis = ActionEmphasis.Quiet,
                )
            }
            if (inspectError != null && !busy && trimmed.isNotBlank() && (isVideo || isPlaylistLink)) {
                AdaptivePair(leading = { available -> Row(available, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        inspectError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                } }, trailing = {
                    ActionButton(
                        "Coba lagi",
                        {
                            inspectedUrl = ""
                            // Retry mengulang jenis inspeksi terakhir — playlist yang gagal tidak diulang sebagai video (BUG-006/R6).
                            retryInspect()
                        },
                        icon = Icons.Outlined.Refresh,
                        emphasis = ActionEmphasis.Quiet,
                    )
                })
            }
        }

        if (shownVideo != null) {
            item {
                AnimatedVisibility(visible = true, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    VideoPreviewCard(shownVideo, if (mode == MediaMode.VIDEO) subtitleLang else "")
                }
            }
        }

        if (shownPlaylist != null) {
            item {
                SectionCard {
                    Column(Modifier.padding(UiSpacing.panel), verticalArrangement = Arrangement.spacedBy(UiSpacing.row)) {
                        AdaptivePair(leading = { available -> Row(available, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Outlined.FeaturedPlayList, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(shownPlaylist.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                                Text(if (Selection.isMix(shownPlaylist.url)) "${entries.size} video dalam antrean awal" else "${entries.size} video · mengikuti isi link (maks 500)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } }, trailing = {
                            ActionButton(
                                if (allSelected) "Kosongkan" else "Pilih semua",
                                { selectedIds = if (allSelected) emptySet() else selectedIds + visibleEntries.map { it.id } },
                                emphasis = ActionEmphasis.Quiet,
                            )
                        })
                        if (Selection.isMix(shownPlaylist.url)) {
                            Text(
                                "Antrean awal Mix dari YouTube (± video terkait yang sedang diputar) — tanpa memuat halaman lanjutan. Daftar dapat berubah dan berbeda dari akun YouTube Anda.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        SearchField(playlistQuery, { playlistQuery = it }, label = "Cari video dalam playlist")
                        if (entries.isEmpty()) {
                            Text("Playlist kosong atau tidak bisa dibaca.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("Pilih video yang mau diantrekan:", style = MaterialTheme.typography.labelLarge)
                            Column(
                                Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                visibleEntries.forEach { entry ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(
                                            checked = entry.id in selectedIds,
                                            onCheckedChange = { checked ->
                                                selectedIds = if (checked) selectedIds + entry.id else selectedIds - entry.id
                                            },
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(entry.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                            if (entry.durationSeconds > 0) Text(Format.duration(entry.durationSeconds), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                            if (visibleEntries.isEmpty()) {
                                Text("Tidak ada video yang cocok dengan \"$playlistQuery\".", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                if (playlistQuery.isBlank()) "$selectionCount / ${entries.size} terpilih"
                                else "Menampilkan ${visibleEntries.size} dari ${entries.size} · $selectionCount terpilih",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        if (shownVideo != null || shownPlaylist != null) {
        item {
            Panel(
                title = if (shownPlaylist != null) "Opsi untuk semua video terpilih" else "Pilih download",
                description = if (shownPlaylist != null) "Semua pilihan masuk satu folder: ${shownPlaylist.folderName}. Nama file tetap memakai judul dan artis/remixer masing-masing lagu." else null,
            ) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = mode == MediaMode.VIDEO,
                            onClick = { mode = MediaMode.VIDEO; preset = "" },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            label = { Text("Video") },
                            icon = { Icon(Icons.Outlined.Videocam, null, Modifier.size(18.dp)) },
                        )
                        SegmentedButton(
                            selected = mode == MediaMode.AUDIO,
                            onClick = { mode = MediaMode.AUDIO; preset = "" },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            label = { Text("Audio saja") },
                            icon = { Icon(Icons.Outlined.Headphones, null, Modifier.size(18.dp)) },
                        )
                    }

                    Text("Pilihan cepat", style = MaterialTheme.typography.labelLarge)
                    ScrollRow {
                        presets.forEach { p ->
                            FilterChip(
                                selected = activePreset?.label == p.label,
                                onClick = { applyPreset(p) },
                                label = { Text(p.label) },
                                leadingIcon = if (activePreset?.label == p.label) {
                                    { Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) }
                                } else null,
                            )
                        }
                    }

                    AnimatedVisibility(advanced, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (mode == MediaMode.VIDEO) {
                                OptionMenu("Resolusi", listOf(0 to "Auto · terbaik", 4320 to "4320p · 8K", 2160 to "2160p · 4K", 1440 to "1440p", 1080 to "1080p · Full HD", 720 to "720p · HD", 480 to "480p", 360 to "360p"), quality, onSelect = { quality = it; preset = "" })
                                OptionMenu("Format video", listOf("auto" to "Auto · format asli", "mp4" to "MP4 · kompatibel", "mkv" to "MKV", "webm" to "WebM", "mov" to "MOV · Apple", "avi" to "AVI · lama"), videoFormat, onSelect = { videoFormat = it; preset = "" })
                            } else {
                                if (audioFormat !in listOf("flac", "wav", "vorbis")) OptionMenu("Bitrate audio", listOf(0 to "Auto · terbaik", 320 to "320 kbps", 256 to "256 kbps", 192 to "192 kbps", 128 to "128 kbps", 64 to "64 kbps"), bitrate, onSelect = { bitrate = it; preset = "" })
                                OptionMenu("Format audio", listOf("auto" to "Auto · format asli", "mp3" to "MP3", "m4a" to "M4A", "aac" to "AAC", "opus" to "Opus", "vorbis" to "OGG Vorbis", "flac" to "FLAC", "wav" to "WAV"), audioFormat, onSelect = { audioFormat = it; preset = ""; if (it !in Selection.coverFormats) embedThumb = false })
                                if (audioFormat == "opus" && bitrate > 0) Text("Bitrate Opus adalah target nominal; hasil dapat bervariasi sesuai konten.", style = MaterialTheme.typography.bodySmall)
                                if (audioFormat == "auto" && bitrate > 0) Text("Bitrate manual dengan format Auto dikonversi ke MP3.", style = MaterialTheme.typography.bodySmall)
                                if (audioFormat in listOf("flac", "wav")) Text("FLAC/WAV tidak meningkatkan kualitas sumber dan menghasilkan file lebih besar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            HorizontalDivider()

                            ToggleRow(
                                title = if (mode == MediaMode.VIDEO) "Download subtitle" else "Download lirik",
                                description = if (mode == MediaMode.VIDEO) "Simpan teks subtitle (.srt) di samping file video."
                                else "Simpan lirik tersinkron (.lrc) dari penyedia lirik (LRCLIB).",
                                checked = if (mode == MediaMode.VIDEO) subtitleLang.isNotEmpty() else lyrics,
                                onCheckedChange = { enabled ->
                                    if (mode == MediaMode.VIDEO) {
                                        subtitleLang = if (enabled) "asli" else ""
                                        if (!enabled) embedSubs = false
                                    } else {
                                        lyrics = enabled
                                    }
                                },
                            )
                            AnimatedVisibility(if (mode == MediaMode.VIDEO) subtitleLang.isNotEmpty() else lyrics, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Bahasa subtitle hanya untuk video — lirik audio selalu dari LRCLIB, tanpa pilihan bahasa (BUG-009).
                                    if (mode == MediaMode.VIDEO) {
                                        ScrollRow {
                                            subtitleLabels.drop(1).forEach { (value, label) ->
                                                FilterChip(selected = subtitleLang == value, onClick = { subtitleLang = value }, label = { Text(label) })
                                            }
                                        }
                                    }
                                    if (mode == MediaMode.VIDEO) {
                                        ToggleRow(
                                            title = "Sematkan ke file video",
                                            description = "Subtitle jadi soft-sub di pemutar (MP4/MKV).",
                                            checked = embedSubs,
                                            onCheckedChange = { embedSubs = it },
                                        )
                                    } else {
                                        Text("File lirik (.lrc) disimpan di folder yang sama dengan file audio — nama file sama, langsung terbaca di AIMP/Poweramp. Kalau lagu tidak ada di penyedia lirik, file liriknya tidak ditulis.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            if (mode == MediaMode.AUDIO) {
                                HorizontalDivider()
                                if (Selection.coverSupported(mode, audioFormat)) {
                                    ToggleRow(
                                        title = "Sampul album (cover)",
                                        description = "Sampul disematkan ke file audio — tidak ada file gambar terpisah.",
                                        checked = embedThumb,
                                        onCheckedChange = { embedThumb = it },
                                    )
                                    if (embedThumb && audioFormat == "auto") Text("Auto mengikuti format asli jika bitrate tidak dipilih. Sampul disematkan bila format dan engine mendukung; preview tetap tersedia.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Text("Format ${audioFormat.uppercase()} belum mendukung sampul album — pilih Auto, MP3, atau M4A.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    ActionButton(
                        if (advanced) "Tutup pengaturan lanjutan" else "Pengaturan lanjutan",
                        { advanced = !advanced },
                        icon = if (advanced) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        emphasis = ActionEmphasis.Quiet,
                    )
            }
        }
        }

        if (shownVideo != null || (shownPlaylist != null && entries.isNotEmpty())) {
            item {
                val summary = buildSummary(mode, videoFormat, audioFormat, quality, bitrate, if (mode == MediaMode.VIDEO) subtitleLang else if (lyrics) "on" else "", embedThumb && mode == MediaMode.AUDIO && Selection.coverSupported(mode, audioFormat))
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Tune, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(summary, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        if (shownPlaylist != null) Text("$selectionCount video", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }

                Spacer(Modifier.height(14.dp))

                ActionButton(
                    text = when {
                        shownPlaylist != null -> "Antrekan $selectionCount video"
                        mode == MediaMode.VIDEO -> "Download video"
                        else -> "Download audio"
                    },
                    onClick = {
                        if (shownPlaylist != null) {
                            downloadPlaylist(entries.filter { it.id in selectedIds }, buildOptions(shownPlaylist.url))
                        } else {
                            download(buildOptions(trimmed))
                        }
                    },
                    icon = if (shownPlaylist != null) Icons.Outlined.Queue else Icons.Outlined.Download,
                    emphasis = ActionEmphasis.Primary,
                    enabled = !busy && if (shownPlaylist != null) selectionCount > 0 && entries.isNotEmpty() else isVideo,
                    fillWidth = true,
                )
            }
        }

        item {
            Text(
                "Download hanya konten yang kamu miliki atau diizinkan untuk disimpan. Hasil tersimpan di Downloads/${SettingsStore.storageFolder}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun VideoPreviewCard(meta: VideoMetadata, subtitleLang: String) {
    SectionCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)) {
                AsyncImage(
                    model = meta.thumbnail,
                    contentDescription = "Thumbnail video",
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f),
                    contentScale = ContentScale.Crop,
                )
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent,androidx.compose.ui.graphics.Color.Black.copy(alpha=0.6f)))))
                Surface(
                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.68f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                ) {
                    Text(
                        Format.duration(meta.durationSeconds),
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = androidx.compose.ui.graphics.Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Text(meta.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Person, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(meta.uploader, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(6.dp))
                Text("Siap diunduh · ${Format.duration(meta.durationSeconds)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
            }
            if (meta.subtitleLangs.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.ClosedCaption, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "Subtitle tersedia: ${meta.subtitleLangs.take(6).joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (subtitleLang.isNotEmpty()) {
                    Text(
                        when {
                            subtitleLang == ".*" -> "Semua bahasa diambil, termasuk hasil terjemahan mesin."
                            subtitleLang == "asli" -> "Hanya bahasa asli video yang diambil — tanpa terjemahan mesin."
                            else -> "Subtitle '$subtitleLang' diambil; bila tidak tersedia, hasilnya terjemahan mesin."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Text(
                    "Video ini tidak punya subtitle/CC tercatat.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun buildSummary(mode: MediaMode, videoFormat: String, audioFormat: String, quality: Int, bitrate: Int, subtitleLang: String, cover: Boolean): String {
    val base = if (mode == MediaMode.VIDEO)
        "${if (quality == 0) "Kualitas terbaik" else "${quality}p"} · ${videoFormat.uppercase()}"
    else
        "${if (bitrate == 0 || audioFormat in listOf("flac", "wav", "vorbis")) "Kualitas sumber" else "$bitrate kbps"} · ${audioFormat.uppercase()}"
    val extras = buildList {
        if (subtitleLang.isNotEmpty()) {
            if (mode == MediaMode.AUDIO) add("Lirik") // lirik selalu dari penyedia lirik — tanpa pilihan bahasa (BUG-009)
            else {
                val all = subtitleLang == ".*"
                val lang = if (subtitleLang == "asli") "bahasa asli" else subtitleLang
                add(if (all) "Semua CC" else "CC $lang")
            }
        }
        if (cover) add("Sampul")
    }
    return if (extras.isEmpty()) base else "$base · ${extras.joinToString(" · ")}"
}
