package id.local.tubeflow.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.local.tubeflow.core.*

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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(UiSpacing.screen), verticalArrangement = Arrangement.spacedBy(UiSpacing.item)) {
        item { PageHeading("Ringkasan media", "Koleksi di perangkat dan aktivitas yang benar-benar tercatat.") }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { collection("AUDIO") }) { Text("Musik") }
            OutlinedButton(onClick = { collection("VIDEO") }) { Text("Video") }
            OutlinedButton(onClick = downloads) { Text("Download") }
        } }
        if (totals == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) } else totals?.let { t ->
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill("Audio tersedia", t.audio.toString(), Icons.Outlined.LibraryMusic, Modifier.weight(1f))
                StatPill("Video tersedia", t.video.toString(), Icons.Outlined.Videocam, Modifier.weight(1f))
            } }
            item { DashboardCard("Komposisi koleksi") {
                if (t.audio + t.video == 0L) {
                    Text("Koleksi masih kosong",style=MaterialTheme.typography.titleMedium)
                    Text("Download media baru atau pindai folder yang sudah berisi file.")
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Button(onClick=downloads) { Text("Mulai download") }
                        TextButton(onClick=model::scan,enabled=!busy) { Text("Segarkan katalog") }
                    }
                } else CollectionDonut(t.audio, t.video)
                Text("Hanya media tersedia. ${t.unavailable} file tidak dapat diakses tidak masuk grafik.", style = MaterialTheme.typography.bodySmall)
            } }
            item { DashboardCard("Koleksi dan penyimpanan") {
                Metric("Audio", if (t.audio == 0L) "0 B" else t.audioBytes?.let(Format::bytes) ?: "Belum diketahui")
                Metric("Video", if (t.video == 0L) "0 B" else t.videoBytes?.let(Format::bytes) ?: "Belum diketahui")
                Metric("Artis / album", "${t.artists} / ${t.albums}")
                Metric("Tidak dapat diakses", t.unavailable.toString())
                if (t.unknownSizes > 0) Text("Ukuran parsial: ${t.unknownSizes} file belum memiliki ukuran.", style = MaterialTheme.typography.bodySmall)
                storage?.let { s ->
                    Metric("Internal: kapasitas / tersedia", "${Format.bytes(s.total)} / ${Format.bytes(s.free)}")
                    LinearProgressIndicator(progress = { if (s.total > 0) ((s.total - s.free).toFloat() / s.total).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
                    if (s.free < 512L * 1024 * 1024) Text("Ruang internal rendah", color = MaterialTheme.colorScheme.error)
                } ?: Text("Informasi penyimpanan internal belum tersedia.")
                Text("Ukuran koleksi berdasarkan pemeriksaan terakhir media terindeks, tanpa LRC/cache. Kapasitas internal adalah volume aplikasi, bukan ukuran koleksi atau kartu SD.", style = MaterialTheme.typography.bodySmall)
            } }
            item { DashboardCard("Kesehatan Smart Audio") {
                Metric("Menunggu indeks media", pending.toString())
                Metric("Metadata judul + artis", t.tagged.toString()); Metric("Metadata belum lengkap", t.partial.toString())
                Metric("Metadata belum diperiksa", t.unchecked.toString()); Metric("Artwork tersedia", t.artwork.toString())
                Metric("Pemrosesan metadata gagal", t.processingFailed.toString()); Metric("Artwork belum tersedia", t.artworkAbsent.toString())
                Metric("Lirik belum diperiksa", t.lyricsUnchecked.toString())
                Metric("Lirik tersimpan", t.lyrics.toString()); Metric("Lirik tidak ditemukan / tidak cocok", t.noLyrics.toString())
                Text("Album/genre/tahun opsional. Status lirik tersimpan tidak menjamin sinkronisasi. Statistik tidak menjalankan tagging atau pencarian lirik.", style = MaterialTheme.typography.bodySmall)
                Text(if (lastScan == 0L) "Pemindaian manual belum dijalankan" else "Pindai terakhir: ${date(lastScan)}", style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = model::scan, enabled = !busy) { Text("Segarkan katalog") }
            } }
        }
        item { DashboardCard("Tugas yang masih tercatat") {
            Metric("Total tugas", jobs.size.toString())
            Metric("Selesai / gagal / dibatalkan", "${jobCounts[JobState.COMPLETED] ?: 0} / ${jobCounts[JobState.FAILED] ?: 0} / ${jobCounts[JobState.CANCELLED] ?: 0}")
            Metric("Berjalan / antre / jeda", "${jobCounts[JobState.RUNNING] ?: 0} / ${jobCounts[JobState.QUEUED] ?: 0} / ${jobCounts[JobState.PAUSED] ?: 0}")
            Text("Riwayat lama yang telah dibersihkan tidak tersedia. Jumlah tugas berbeda dari jumlah file; retry memakai tugas yang sama.", style = MaterialTheme.typography.bodySmall)
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30, 90).forEach { days -> FilterChip(period == days, onClick = { period = days }, label = { Text("$days hari") }) }
        } }
        item { DashboardCard("Aktivitas download sejak upgrade") {
            Metric("Selesai / gagal / dibatalkan", "$finished / $failed / $cancelled")
            if (finished + failed > 0) Metric("Rasio selesai dari hasil selesai/gagal", "${(100 * finished / (finished + failed))}%")
            Text("Pencatatan dimulai ${date(SettingsStore.analyticsSince)}. Angka adalah transisi hasil aktivitas dalam periode, termasuk retry; tidak merekonstruksi aktivitas sebelum upgrade.", style = MaterialTheme.typography.bodySmall)
            if (finished == 0L) Text("Belum ada download selesai dalam periode tercatat.")
        } }
        item { DashboardCard("Download selesai per hari") {
            ActivityChart(points, cumulative = false)
            Text("Batang = jumlah transisi selesai per tanggal; retry bisa tercatat lagi. Hari sebelum pencatatan bukan nol.", style = MaterialTheme.typography.bodySmall)
        } }
        item { DashboardCard("Tren akumulasi selesai") {
            ActivityChart(points, cumulative = true)
            Text("Garis = akumulasi aktivitas selesai dalam $period hari, bukan jumlah file atau riwayat ukuran penyimpanan.", style = MaterialTheme.typography.bodySmall)
        } }
        item { DashboardCard("Sumber download selesai") {
            if (platforms.isEmpty()) Text("Belum ada sumber tercatat dalam periode ini.")
            platforms.forEach { platform -> Metric(SourcePlatforms.fromId(platform.platformId).label, platform.count.toString()) }
        } }
        item { DashboardCard("Aktivitas terbaru") {
            if (recent.isEmpty()) Text("Belum ada aktivitas sejak upgrade.")
            recent.forEach { event -> Text("${date(event.at)} · ${eventLabel(event.action)} · ${if (event.kind == "AUDIO") "Audio" else "Video"}", style = MaterialTheme.typography.bodySmall) }
        } }
    }
}

@Composable
private fun CollectionDonut(audio: Long, video: Long) {
    val scheme = MaterialTheme.colorScheme
    val total = audio + video
    val description = "Komposisi koleksi: $audio audio, $video video, total $total media tersedia."
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(164.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val width = 20.dp.toPx()
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
                Text(total.toString(), style = MaterialTheme.typography.headlineLarge)
                Text("media tersedia", style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(14.dp))
        Legend("Audio · $audio", scheme.primary)
        Legend("Video · $video", scheme.tertiary)
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
    Canvas(Modifier.fillMaxWidth().height(164.dp).semantics { contentDescription = (if (cumulative) "Grafik garis akumulasi. " else "Grafik batang harian. ") + summary }) {
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

@Composable
private fun DashboardCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(UiSpacing.panel), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); content() } }
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
