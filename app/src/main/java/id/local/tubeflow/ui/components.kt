package id.local.tubeflow.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.compositeOver
import id.local.tubeflow.core.songIdentity
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.local.tubeflow.core.DownloadPhase
import id.local.tubeflow.core.sourcePlatform
import id.local.tubeflow.core.DownloadJob
import id.local.tubeflow.core.Format
import id.local.tubeflow.core.JobState
import id.local.tubeflow.core.LyricStatus
import id.local.tubeflow.core.MediaMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PageHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, description: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(72.dp).background(
                    Brush.radialGradient(
                        listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)),
                    ),
                    CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary) }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun StatPill(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    Card(modifier) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(34.dp).background(tint.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(18.dp), tint = tint) }
            Column {
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun StateChip(state: JobState) {
    val scheme = MaterialTheme.colorScheme
    val (label, container, content) = when (state) {
        JobState.QUEUED -> Triple("Menunggu", scheme.surfaceVariant, scheme.onSurfaceVariant)
        JobState.RUNNING -> Triple("Berjalan", scheme.primaryContainer, scheme.onPrimaryContainer)
        JobState.PAUSED -> Triple("Dijeda", scheme.tertiaryContainer, scheme.onTertiaryContainer)
        JobState.COMPLETED -> Triple("Selesai", scheme.secondaryContainer, scheme.onSecondaryContainer)
        JobState.FAILED -> Triple("Gagal", scheme.errorContainer, scheme.onErrorContainer)
        JobState.CANCELLED -> Triple("Dibatalkan", scheme.surfaceVariant, scheme.onSurfaceVariant)
    }
    Surface(color = container, shape = MaterialTheme.shapes.small) {        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = content, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Ikon platform sumber (P2) — peta ringkas nama platform ke ikon; platform baru di luar peta memakai
 * globe sehingga item tetap tampil tanpa perlu mengubah banyak bagian sistem.
 */
@Composable
fun PlatformIcon(platform: id.local.tubeflow.core.SourcePlatform, modifier: Modifier = Modifier) {
    val icon = when (platform.icon) {
        "play" -> Icons.Outlined.SmartDisplay
        "music" -> Icons.Outlined.MusicVideo
        "wave" -> Icons.Outlined.GraphicEq
        "camera" -> Icons.Outlined.CameraAlt
        "facebook" -> Icons.Outlined.ThumbUp
        "x" -> Icons.Outlined.Close
        "video" -> Icons.Outlined.Videocam
        "game" -> Icons.Outlined.SportsEsports
        else -> Icons.Outlined.Language
    }
    Icon(icon, platform.label, modifier, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Sampul hasil cache (P2): decode file covers/<id>.jpg di IO. Tanpa sampul/gagal decode = ikon mode
 * di atas gradien (fallback lama) — item tidak pernah gagal tampil karena gambar.
 */
@Composable
fun CoverThumb(coverPath: String, thumbnailUrl: String, mode: MediaMode, modifier: Modifier = Modifier) {
    val model: Any? = coverPath.takeIf { it.isNotBlank() && java.io.File(it).isFile }?.let { java.io.File(it) }
        ?: thumbnailUrl.takeIf { it.isNotBlank() }
    Box(modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Icon(if (mode == MediaMode.AUDIO) Icons.Outlined.Headphones else Icons.Outlined.Videocam,
            null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        if (model != null) coil.compose.AsyncImage(model = model, contentDescription = "Sampul sumber",
            modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    }
}
private fun formatMeta(job: DownloadJob): String {
    val base = if (job.mode == MediaMode.VIDEO)
        "${if (job.quality == 0) "Auto" else "${job.quality}p"} · ${job.format.uppercase(Locale.ROOT)}"
    else
        "${if (job.bitrate == 0) "Auto" else "${job.bitrate} kbps"} · ${job.format.uppercase(Locale.ROOT)}"
    val extras = buildList {
        val lang = if (job.subtitles == "asli") "bahasa asli" else job.subtitles
        // Lirik audio selalu dari penyedia lirik tanpa pilihan bahasa — jangan tampilkan bahasa yang tak dipakai (BUG-009).
        if (job.subtitles.isNotEmpty()) add(
            if (job.mode == MediaMode.AUDIO) "Lirik"
            else (if (job.subtitles == ".*") "Semua CC" else "CC $lang"),
        )
        if (job.embedSubs) add("CC tertanam")
        if (job.embedThumb) add(if (job.thumbnail) "Thumb+" else "Sampul")
        else if (job.thumbnail) add("Thumb")
    }
    return if (extras.isEmpty()) base else "$base · ${extras.joinToString(" · ")}"
}

@Composable
fun JobCard(job: DownloadJob, cancel: () -> Unit, retry: () -> Unit, modifier: Modifier = Modifier, remove: (() -> Unit)? = null, pause: (() -> Unit)? = null, resume: (() -> Unit)? = null, refetchLyrics: ((String) -> Unit)? = null, play: (() -> Unit)? = null) {
    val context = LocalContext.current
    var openError by remember { mutableStateOf<String?>(null) }
    val accent=rememberCoverAccent(job.coverPath)
    val song=job.songIdentity()
    Card(modifier.fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=accent.copy(alpha=0.06f).compositeOver(MaterialTheme.colorScheme.surface)), border=BorderStroke(1.dp,accent.copy(alpha=0.18f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverThumb(job.coverPath, job.thumbnailUrl, job.mode, Modifier.size(56.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(Format.musicLabel(song), style = MaterialTheme.typography.titleMedium, fontWeight=FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val credit=song.artist.orEmpty().ifBlank { song.remixer }
                    if (credit.isNotBlank()) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.Person,null,Modifier.size(14.dp)); Text(credit,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                    // Platform asal tautan (mis. SoundCloud, TikTok) + ikonnya — dikirim oleh Antrean & Riwayat (P2).
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PlatformIcon(job.sourcePlatform, Modifier.size(13.dp))
                        Text(
                            formatMeta(job) + " · ${job.sourcePlatform.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                StateChip(job.state)
            }
            when (job.state) {
                JobState.RUNNING -> {
                    if (job.progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { (job.progress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp), color=accent,
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(6.dp),color=accent)
                    }
                    val transferring = DownloadPhase.isTransfer(job.phase)
                    val speed = if (transferring) Format.speedLabel(job.speed) else null
                    val eta = if (transferring) Format.eta(job.eta) else ""
                    val parts = listOfNotNull(
                        DownloadPhase.label(job.phase),
                        job.progress.takeIf { it >= 0f }?.let { "${it.toInt()}%" },
                        speed,
                        eta.takeIf { it.isNotBlank() }?.let { "sisa $it" },
                    )
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                JobState.COMPLETED -> Text(
                    // Waktu publikasi sungguhan; baris lama (sebelum migrasi 5) jatuh ke createdAt (BUG-023).
                    "Selesai · ${SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.forLanguageTag("id")).format(Date(job.completedAt.takeIf { it > 0 } ?: job.createdAt))}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                JobState.FAILED -> {
                    job.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    if (job.partialBytes > 0) Text(
                        "${Format.bytes(job.partialBytes)} disimpan; Coba lagi memeriksa dukungan lanjut dari sumber.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (job.resumeNotice.isNotBlank()) Text(job.resumeNotice, style = MaterialTheme.typography.labelSmall)

                }
                JobState.QUEUED -> Text("Menunggu giliran · antrean berjalan otomatis", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                JobState.PAUSED -> {
                    if (job.progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { (job.progress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp), color=accent,
                        )
                    }
                    val position = if (job.progress >= 0f) " · ${job.progress.toInt()}%" else ""
                    Text("Dijeda · ${DownloadPhase.label(job.phase)}$position · ketuk Lanjutkan untuk melanjutkan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                JobState.CANCELLED -> Text("Dibatalkan oleh kamu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (job.state != JobState.FAILED && job.resumeNotice.isNotBlank())
                Text(job.resumeNotice, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Status lirik terpisah dari status audio: audio sukses tetap "Selesai" walau lirik tidak ada (BUG-014).
            if (job.processingNotice.isNotBlank()) Text(job.processingNotice, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            if (job.state == JobState.COMPLETED && job.mode == MediaMode.AUDIO && job.subtitles.isNotEmpty() && job.lyricStatus.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        if (job.lyricStatus == LyricStatus.SAVED) Icons.Outlined.Lyrics else Icons.Outlined.Warning,
                        null,
                        Modifier.size(15.dp),
                        tint = when (job.lyricStatus) {
                            LyricStatus.SAVED -> MaterialTheme.colorScheme.secondary
                            LyricStatus.NETWORK, LyricStatus.SAVE_FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        LyricStatus.label(job.lyricStatus),
                        style = MaterialTheme.typography.labelMedium,
                        color = when (job.lyricStatus) {
                            LyricStatus.SAVED -> MaterialTheme.colorScheme.secondary
                            LyricStatus.NETWORK, LyricStatus.SAVE_FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (refetchLyrics != null && LyricStatus.retryable(job.lyricStatus)) {
                        TextButton(onClick = { refetchLyrics(job.id) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Text("Cari ulang", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            openError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when (job.state) {
                    JobState.QUEUED, JobState.RUNNING -> {
                        pause?.let { onClick -> TextButton(onClick = onClick) { Icon(Icons.Outlined.Pause, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Jeda") } }
                        TextButton(onClick = cancel) { Text("Batalkan") }
                    }
                    JobState.PAUSED -> {
                        resume?.let { onClick -> OutlinedButton(onClick = onClick) { Icon(Icons.Outlined.PlayArrow, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Lanjutkan") } }
                        TextButton(onClick = cancel) { Text("Batalkan") }
                    }
                    JobState.FAILED, JobState.CANCELLED -> {
                        OutlinedButton(onClick = retry) { Icon(Icons.Outlined.Refresh, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Coba lagi") }
                        remove?.let { TextButton(onClick = it) { Icon(Icons.Outlined.Delete, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Hapus") } }
                    }
                    JobState.COMPLETED -> {
                        if (job.mode == MediaMode.AUDIO && play != null) OutlinedButton(onClick=play,enabled=job.outputUri != null) { Text("Putar") }
                        OutlinedButton(onClick = { openError = launchFile(context, job, false) }, enabled = job.outputUri != null) { Text("Buka") }
                        TextButton(onClick = { openError = launchFile(context, job, true) }, enabled = job.outputUri != null) { Icon(Icons.Outlined.Share, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Bagikan") }
                        TextButton(onClick = retry) { Icon(Icons.Outlined.Download, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Unduh lagi") }
                        remove?.let { TextButton(onClick = it) { Icon(Icons.Outlined.Delete, "Hapus file", Modifier.size(16.dp)) } }
                    }
                }
            }
            if (job.state == JobState.COMPLETED && !job.sidecarUris.isNullOrBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    job.sidecarUris.lines().forEach { line ->
                        val kind = line.substringBefore('|')
                        val uri = line.substringAfter('|', "")
                        if (uri.isBlank()) return@forEach
                        val label = when {
                            kind == "thumb" -> "Thumbnail"
                            job.mode == MediaMode.AUDIO -> "Lirik"
                            else -> "Subtitle (CC)"
                        }
                        val icon = when {
                            kind == "thumb" -> Icons.Outlined.Image
                            job.mode == MediaMode.AUDIO -> Icons.Outlined.Lyrics
                            else -> Icons.Outlined.ClosedCaption
                        }
                        OutlinedButton(onClick = { openError = openSidecar(context, uri, kind) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                            Icon(icon, null, Modifier.size(15.dp)); Spacer(Modifier.width(6.dp)); Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun <T> OptionMenu(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(options.firstOrNull { it.first == selected }?.second.orEmpty(), style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Outlined.ExpandMore, null)
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { onSelect(value); expanded = false },
                    trailingIcon = { if (value == selected) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary) },
                )
            }
        }
    }
}

/**
 * Mime konkret untuk intent buka file. MediaStore menyimpan .lrc jadi application/octet-stream dan fallback lama memakai
 * wildcard (audio sembarang, text sembarang) — keduanya bikin dialog "pilih pemutar" muncul terus walau sudah dicentang
 * "jangan tanya lagi", karena pilihan default tersimpan per tipe mime yang spesifik.
 */
fun concreteMime(context: Context, uri: Uri, fallback: String): String {
    val displayName = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
    val extension = displayName?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.length in 2..5 }
    val byExtension = extension?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
    val current = runCatching { context.contentResolver.getType(uri) }.getOrNull()
    val mime = byExtension ?: current
    return if (mime.isNullOrBlank() || mime.contains('*') || mime == "application/octet-stream") fallback else mime
}

fun launchFile(context: Context, job: DownloadJob, share: Boolean): String? =
    job.outputUri?.let { launchMedia(context, it, job.mode == MediaMode.AUDIO, share) } ?: "File tidak tersedia."

fun launchMedia(context: Context, raw: String, audio: Boolean, share: Boolean): String? = try {
    val uri = raw.toUri()
    val mime = concreteMime(context, uri, if (audio) "audio/mpeg" else "video/mp4")
    val intent = if (share) Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri) else Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.clipData = ClipData.newRawUri("Download", uri)
    if (share) context.startActivity(Intent.createChooser(intent, "Bagikan file"))
    else try { context.startActivity(intent) } catch (_: android.content.ActivityNotFoundException) { context.startActivity(Intent.createChooser(intent, "Buka dengan")) }
    null
} catch (_: Exception) { "File tidak dapat dibuka. Pastikan ada aplikasi pemutar yang sesuai." }

fun openSidecar(context: Context, uriString: String, kind: String): String? = try {
    val uri = uriString.toUri()
    val mime = concreteMime(context, uri, if (kind == "thumb") "image/jpeg" else "text/plain")
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.clipData = ClipData.newRawUri("Sidecar", uri)
    try { context.startActivity(intent) } catch (_: android.content.ActivityNotFoundException) { context.startActivity(Intent.createChooser(intent, "Buka dengan")) }
    null
} catch (_: Exception) { "File tidak dapat dibuka di perangkat ini." }

fun extractLink(text: String): String = Regex("""https?://[^\s]+""").find(text)?.value ?: text.trim()
