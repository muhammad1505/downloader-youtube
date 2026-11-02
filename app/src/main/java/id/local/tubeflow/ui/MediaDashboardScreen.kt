package id.local.tubeflow.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.local.tubeflow.R
import id.local.tubeflow.core.*

/**
 * Ringkasan media: empat blok saja — kondisi koleksi, kesehatan pemrosesan, aktivitas unduhan,
 * dan aktivitas terbaru. Angka rinci serta penjelasan panjang dibuka lewat tombol "Lihat detail"
 * dan ikon penjelasan supaya layar tetap tenang tanpa menyembunyikan informasi.
 */
@Composable
fun MediaDashboardScreen(model: MediaLibraryViewModel, jobs: List<DownloadJob>, collection: (String) -> Unit, downloads: () -> Unit) {
    val totals by model.totals.collectAsStateWithLifecycle()
    val counts by model.eventCounts.collectAsStateWithLifecycle()
    val daily by model.daily.collectAsStateWithLifecycle()
    val platforms by model.platforms.collectAsStateWithLifecycle()
    val recent by model.recent.collectAsStateWithLifecycle()
    val storage by model.deviceStorage.collectAsStateWithLifecycle()
    val lastScan by model.lastScan.collectAsStateWithLifecycle()
    val pending by model.pendingIndex.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableIntStateOf(7) }
    var cumulative by rememberSaveable { mutableStateOf(false) }
    var healthDetail by rememberSaveable { mutableStateOf(false) }
    var collectionDetail by rememberSaveable { mutableStateOf(false) }
    var notesOpen by rememberSaveable { mutableStateOf(false) }
    val today = java.time.LocalDate.now()
    val points = remember(daily, period, today, SettingsStore.analyticsSince) {
        DashboardCharts.points(daily, period, today, SettingsStore.analyticsSince, java.time.ZoneId.systemDefault())
    }
    val jobCounts = remember(jobs) { jobs.groupingBy { it.state }.eachCount() }
    LaunchedEffect(Unit) { model.validateAvailability() }
    LaunchedEffect(period) { model.period(period) }
    val finished = counts.firstOrNull { it.action == "COMPLETED" }?.count ?: 0
    val failed = counts.firstOrNull { it.action == "FAILED" }?.count ?: 0
    val cancelled = counts.firstOrNull { it.action == "CANCELLED" }?.count ?: 0
    val recorded = finished + failed
    val order = listOf(
        "Periode grafik = jumlah hari terakhir yang ditampilkan (7/30/90).",
        "Selesai/gagal/dibatalkan menghitung transisi hasil aktivitas dalam periode, termasuk retry; pencatatan dimulai ${date(SettingsStore.analyticsSince)} dan tidak merekonstruksi aktivitas sebelum upgrade.",
        "Batang = transisi selesai per tanggal; Akumulasi = jumlah menumpuk dalam periode. Keduanya bukan jumlah file atau riwayat ukuran penyimpanan.",
        "Sumber download dihitung dari platform yang tercatat untuk aktivitas selesai.",
        "Statistik tidak menjalankan tagging atau pencarian lirik; album/genre/tahun opsional dan status lirik tersimpan tidak menjamin sinkronisasi.",
    )

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(UiSpacing.screen), verticalArrangement = Arrangement.spacedBy(UiSpacing.item)) {
        item { PageHeading("Ringkasan media", "Koleksi di perangkat dan aktivitas yang benar-benar tercatat.", Icons.Outlined.Dashboard) }
        item { ScrollRow {
            AssistChip(onClick = { collection("AUDIO") }, label = { Text("Musik") }, leadingIcon = { Icon(Icons.Outlined.LibraryMusic, null, Modifier.size(18.dp)) })
            AssistChip(onClick = { collection("VIDEO") }, label = { Text("Video") }, leadingIcon = { Icon(Icons.Outlined.Videocam, null, Modifier.size(18.dp)) })
            AssistChip(onClick = downloads, label = { Text("Download") }, leadingIcon = { Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)) })
        } }

        // Blok 1 — kondisi koleksi.
        item {
            if (totals == null) Panel { LinearProgressIndicator(Modifier.fillMaxWidth()) } else totals?.let { t ->
                Panel(title = "Koleksi di perangkat") {
                    StatRow(listOf(
                        StatSpec("Audio", t.audio.toString(), Icons.Outlined.LibraryMusic),
                        StatSpec("Video", t.video.toString(), Icons.Outlined.Videocam, MaterialTheme.colorScheme.tertiary),
                    ))
                    if (t.audio + t.video == 0L) {
                        Text("Koleksi masih kosong", style = MaterialTheme.typography.titleMedium)
                        Text("Download media baru atau pindai folder yang sudah berisi file.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ActionButton("Mulai download", downloads, icon = Icons.Outlined.Download, emphasis = ActionEmphasis.Primary)
                        ActionButton("Segarkan katalog", model::scan, icon = Icons.Outlined.Refresh, emphasis = ActionEmphasis.Quiet, enabled = !busy)
                    } else {
                        CollectionDonut(t.audio, t.video)
                    }
                    storage?.let { s ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Penyimpanan internal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${Format.bytes(s.total - s.free)} dari ${Format.bytes(s.total)}", style = MaterialTheme.typography.labelMedium)
                        }
                        LinearProgressIndicator(progress = { if (s.total > 0) ((s.total - s.free).toFloat() / s.total).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
                        if (s.free < 512L * 1024 * 1024) Text("Ruang internal rendah", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                    } ?: Text("Informasi penyimpanan internal belum tersedia.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (t.unavailable == 0L) "Semua media dapat diakses." else "${t.unavailable} file tidak dapat diakses dan tidak dihitung di grafik.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ActionButton(stringResource(R.string.ringkasan_detail), { collectionDetail = !collectionDetail },
                        icon = if (collectionDetail) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        emphasis = ActionEmphasis.Quiet, fillWidth = true)
                    AnimatedVisibility(collectionDetail) {
                        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.row)) {
                            Metric("Audio", if (t.audio == 0L) "0 B" else t.audioBytes?.let(Format::bytes) ?: "Belum diketahui")
                            Metric("Video", if (t.video == 0L) "0 B" else t.videoBytes?.let(Format::bytes) ?: "Belum diketahui")
                            Metric("Artis / album", "${t.artists} / ${t.albums}")
                            Metric("Tidak dapat diakses", t.unavailable.toString())
                            if (t.unknownSizes > 0) Text("Ukuran parsial: ${t.unknownSizes} file belum memiliki ukuran.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            storage?.let { s -> Metric("Internal: kapasitas / tersedia", "${Format.bytes(s.total)} / ${Format.bytes(s.free)}") }
                            Text("Ukuran koleksi dihitung dari media terindeks pada pemeriksaan terakhir, tanpa LRC/cache. Kapasitas internal adalah volume aplikasi, bukan ukuran koleksi atau kartu SD.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Blok 2 — kesehatan pemrosesan; hanya angka yang perlu tindakan tampil di depan.
        item { totals?.let { t -> Panel(title = "Kesehatan Smart Audio") {
            Metric("Menunggu indeks media", pending.toString())
            Metric("Metadata belum diperiksa", t.unchecked.toString())
            Metric("Artwork belum tersedia", t.artworkAbsent.toString())
            Metric("Lirik belum diperiksa", t.lyricsUnchecked.toString())
            Text(if (lastScan == 0L) "Pemindaian manual belum dijalankan." else "Pindai terakhir: ${date(lastScan)}", style = MaterialTheme.typography.labelSmall)
            ActionButton(stringResource(if (healthDetail) R.string.ringkasan_sembunyikan else R.string.ringkasan_detail),
                { healthDetail = !healthDetail },
                icon = if (healthDetail) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                emphasis = ActionEmphasis.Quiet, fillWidth = true)
            AnimatedVisibility(healthDetail) {
                Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.row)) {
                    Metric("Metadata judul + artis", t.tagged.toString())
                    Metric("Metadata belum lengkap", t.partial.toString())
                    Metric("Pemrosesan metadata gagal", t.processingFailed.toString())
                    Metric("Artwork tersedia", t.artwork.toString())
                    Metric("Lirik tersimpan", t.lyrics.toString())
                    Metric("Lirik tidak ditemukan / tidak cocok", t.noLyrics.toString())
                    ActionButton("Segarkan katalog", model::scan, icon = Icons.Outlined.Refresh, emphasis = ActionEmphasis.Quiet, enabled = !busy)
                }
            }
        } } }

        // Blok 3 — aktivitas unduhan: satu grafik, satu baris ringkas, sumber dipadatkan.
        item { Panel(title = stringResource(R.string.ringkasan_aktivitas)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.row)) {
                ScrollRow(Modifier.weight(1f)) {
                    listOf(7, 30, 90).forEach { days -> FilterChip(period == days, onClick = { period = days }, label = { Text("$days hari") }) }
                }
                IconAction(Icons.Outlined.Info, stringResource(R.string.ringkasan_penjelasan), { notesOpen = true })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.row)) {
                FilterChip(!cumulative, onClick = { cumulative = false }, label = { Text(stringResource(R.string.ringkasan_batang)) })
                FilterChip(cumulative, onClick = { cumulative = true }, label = { Text(stringResource(R.string.ringkasan_akumulasi)) })
            }
            ActivityChart(points, cumulative = cumulative)
            Text("Selesai $finished · gagal $failed · dibatalkan $cancelled" + if (recorded > 0) "  ·  ${100 * finished / recorded}% berhasil" else "", style = MaterialTheme.typography.titleSmall)
            if (finished == 0L) Text("Belum ada download selesai dalam periode tercatat.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val sources = platforms.take(3).joinToString(" · ") { "${SourcePlatforms.fromId(it.platformId).label} ${it.count}" }
            if (platforms.isNotEmpty()) Text(if (platforms.size > 3) "$sources · lainnya ${platforms.drop(3).sumOf { it.count }}" else sources,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }

        // Blok 4 — aktivitas terbaru, dipendekkan.
        item { Panel(title = stringResource(R.string.ringkasan_terbaru)) {
            if (recent.isEmpty()) Text("Belum ada aktivitas sejak upgrade.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            recent.take(3).forEach { event ->
                Text("${date(event.at)} · ${eventLabel(event.action)} · ${if (event.kind == "AUDIO") "Audio" else "Video"}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (recent.size > 3 || jobCounts.isNotEmpty()) {
                ActionButton("Lihat Riwayat", downloads, icon = Icons.Outlined.History, emphasis = ActionEmphasis.Quiet, fillWidth = true)
            }
        } }
    }
    if (notesOpen) AlertDialog(
        onDismissRequest = { notesOpen = false },
        title = { DialogTitle(stringResource(R.string.ringkasan_penjelasan), icon = Icons.Outlined.Info) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.row)) { order.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) } } },
        confirmButton = { TextButton(onClick = { notesOpen = false }) { Text(stringResource(R.string.karaoke_close)) } },
    )
}

@Composable
private fun CollectionDonut(audio: Long, video: Long) {
    val scheme = MaterialTheme.colorScheme
    val total = audio + video
    val description = "Komposisi koleksi: $audio audio, $video video, total $total media tersedia."
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val width = 16.dp.toPx()
                val inset = width / 2
                val ring = Size(size.width - width, size.height - width)
                drawArc(scheme.surfaceVariant, 0f, 360f, false, Offset(inset, inset), ring, style = Stroke(width))
                if (total > 0) {
                    val audioAngle = (360.0 * audio / total).toFloat()
                    if (audio > 0) drawArc(scheme.primary, -90f, audioAngle, false, Offset(inset, inset), ring, style = Stroke(width))
                    if (video > 0) drawArc(scheme.tertiary, -90f + audioAngle, 360f - audioAngle, false, Offset(inset, inset), ring, style = Stroke(width))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(total.toString(), style = MaterialTheme.typography.headlineMedium)
                Text("media tersedia", style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.item)) {
            Legend("Audio · $audio", scheme.primary)
            Legend("Video · $video", scheme.tertiary)
        }
        if (total == 0L) Text("Belum ada media tersedia", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Legend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ActivityChart(points: List<DashboardCharts.Point>, cumulative: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val values = remember(points, cumulative) { points.map { if (cumulative) it.cumulative else it.completed } }
    val max = (values.filterNotNull().maxOrNull() ?: 0L).coerceAtLeast(1)
    val summary = remember(points, cumulative) {
        points.mapNotNull { point ->
            val value = if (cumulative) point.cumulative else point.completed
            value?.let { "${point.day}: $it" }
        }.joinToString("; ").ifBlank { "Belum ada hari dalam periode pencatatan." }
    }
    Text(if (cumulative) "Total periode: ${values.lastOrNull() ?: 0}" else "Puncak: ${values.filterNotNull().maxOrNull() ?: 0} selesai / hari", style = MaterialTheme.typography.titleSmall)
    Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = (if (cumulative) "Grafik garis akumulasi. " else "Grafik batang harian. ") + summary }) {
        val margin = 6.dp.toPx()
        val height = size.height - margin * 2
        val width = size.width - margin * 2
        repeat(4) { line ->
            val y = margin + height * line / 3
            drawLine(scheme.outlineVariant, Offset(margin, y), Offset(size.width - margin, y), 1.dp.toPx())
        }
        val slots = points.size.coerceAtLeast(1)
        val slot = width / slots
        val path = Path()
        var started = false
        values.forEachIndexed { index, value ->
            val x = margin + slot * (index + 0.5f)
            if (value == null) {
                drawCircle(scheme.outlineVariant, 1.5.dp.toPx(), Offset(x, margin + height))
                started = false
            } else {
                val y = margin + height * (1 - value.toFloat() / max)
                if (cumulative) {
                    if (started) path.lineTo(x, y) else path.moveTo(x, y)
                    started = true
                    if (value > 0) drawCircle(scheme.primary, 2.5.dp.toPx(), Offset(x, y))
                } else if (value > 0) {
                    drawRoundRect(scheme.primary, Offset(x - slot * 0.34f, y), Size(slot * 0.68f, margin + height - y), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                }
            }
        }
        if (cumulative) drawPath(path, scheme.primary, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(points.firstOrNull()?.day.toString(), style = MaterialTheme.typography.labelSmall)
        Text(points.lastOrNull()?.day.toString(), style = MaterialTheme.typography.labelSmall)
    }
    if (points.any { it.completed == null }) Text("Titik abu-abu: belum ada pencatatan", style = MaterialTheme.typography.labelSmall)
}

@Composable private fun Metric(label: String, value: String) {
    val largeText=androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    if (largeText) Column(Modifier.fillMaxWidth(), verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(label, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style=MaterialTheme.typography.titleSmall)
    } else Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.widthIn(max=160.dp), style=MaterialTheme.typography.labelLarge)
    }
}
private fun eventLabel(action: String) = when (action) {
    "COMPLETED" -> "Download selesai"; "FAILED" -> "Download gagal"; "CANCELLED" -> "Download dibatalkan"; "QUEUED" -> "Diantrekan";
    "ADDED" -> "Media ditambahkan"; "RENAMED" -> "Nama diperbarui"; "DELETED" -> "Media dihapus"; "INDEX_FAILED" -> "Pengindeksan media gagal"; "UNAVAILABLE" -> "File tidak dapat diakses"; "ACCESS_DENIED" -> "Izin akses file berubah"; "PROCESSING_FAILED" -> "Pemrosesan tambahan gagal"; "LYRIC_TIMING" -> "Waktu lirik diperbarui"; "LYRICS_SELECTED" -> "Lirik pilihan disimpan"; "KARAOKE_SAVED" -> "Instrumental disimpan"; else -> action
}
