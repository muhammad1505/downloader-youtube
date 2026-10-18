package id.local.tubeflow.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.compose.foundation.BorderStroke
import androidx.core.net.toUri
import androidx.compose.ui.graphics.compositeOver
import id.local.tubeflow.core.songIdentity
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

/**
 * Bahasa kartu media (gaya pemutar musik modern): latar datar dengan nuansa aksen tipis dari sampul,
 * tanpa garis tepi — dipakai kartu unduhan, kartu koleksi, dan mini player agar konsisten.
 */
@Composable
fun mediaCardColors(accent: Color): CardColors = CardDefaults.cardColors(
    containerColor = accent.copy(alpha = 0.07f).compositeOver(MaterialTheme.colorScheme.surfaceContainer),
)

object UiSpacing {
    val screen = 16.dp
    val panel = 16.dp
    val item = 12.dp
    val row = 8.dp
    val group = 24.dp
}

/** Peran aksi: satu utama per layar, sisanya lebih tenang; destruktif tidak pernah hijau. */
enum class ActionEmphasis { Primary, Secondary, Quiet }

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.medium, content = content)
}

/** Panel isi bersama: padding 16dp, judul `titleMedium`, deskripsi `bodySmall` redup. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionCard(modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(UiSpacing.panel),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.item),
        ) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    emphasis: ActionEmphasis = ActionEmphasis.Secondary,
    destructive: Boolean = false,
    fillWidth: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val base = if (fillWidth) modifier.fillMaxWidth() else modifier
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
    when (emphasis) {
        ActionEmphasis.Primary -> Button(
            onClick, base.heightIn(min = 48.dp), enabled = enabled, shape = MaterialTheme.shapes.small,
            colors = if (destructive) ButtonDefaults.buttonColors(containerColor = scheme.errorContainer, contentColor = scheme.onErrorContainer)
                else ButtonDefaults.buttonColors(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), content = content,
        )
        ActionEmphasis.Secondary -> OutlinedButton(
            onClick, base.heightIn(min = 40.dp), enabled = enabled, shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (destructive) scheme.error else scheme.onSurfaceVariant),
            border = BorderStroke(1.dp, if (!enabled) scheme.onSurface.copy(alpha = 0.12f) else scheme.outlineVariant),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), content = content,
        )
        ActionEmphasis.Quiet -> TextButton(
            onClick, base.heightIn(min = 40.dp), enabled = enabled, shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.textButtonColors(contentColor = if (destructive) scheme.error else scheme.primary),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), content = content,
        )
    }
}

/** Local width and font determine when content and actions need separate lines. */
@Composable
fun AdaptivePair(
    modifier: Modifier = Modifier,
    minInlineWidth: androidx.compose.ui.unit.Dp = 320.dp,
    leading: @Composable (Modifier) -> Unit,
    trailing: @Composable () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < minInlineWidth || fontScale >= 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.row)) {
                leading(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) { trailing() }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UiSpacing.row), verticalAlignment = Alignment.CenterVertically) {
                leading(Modifier.weight(1f))
                trailing()
            }
        }
    }
}

/** Baris kontrol penuh: satu fokus TalkBack, target 48dp, status tidak hanya warna. */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
            value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.item),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun CheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
            value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.row),
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Aksi ikon untuk baris kartu: label panjang memakan ruang daftar, jadi ikon dipakai di sini dan
 * nama aksinya tetap dibaca TalkBack lewat `contentDescription`.
 */
@Composable
fun IconAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    IconButton(onClick = onClick, modifier = modifier.size(48.dp), enabled = enabled) {
        Icon(icon, description, tint = tint)
    }
}

/** Chip dropdown: bentuk dan tinggi sama dengan `FilterChip` di deretan filter. */
@Composable
fun <T> MenuChip(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(options.firstOrNull { it.first == selected }?.second ?: label, maxLines = 1) },
            trailingIcon = { Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
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

/** Aksi berbentuk chip agar sejajar dengan filter di sekitarnya. */
@Composable
fun ActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    AssistChip(
        onClick, modifier = modifier, enabled = enabled,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = icon?.let { vector -> { Icon(vector, null, Modifier.size(18.dp)) } },
        colors = if (destructive) AssistChipDefaults.assistChipColors(
            labelColor = MaterialTheme.colorScheme.error,
            leadingIconContentColor = MaterialTheme.colorScheme.error,
        ) else AssistChipDefaults.assistChipColors(),
    )
}

/** Deretan kontrol yang dapat digulir: satu jarak 8dp untuk chip, filter, dan aksi. */
@Composable
fun ScrollRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.row),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }) { Icon(Icons.Outlined.Close, "Bersihkan pencarian") }
        },
    )
}

/** Konfirmasi bersama; aksi berisiko memakai warna galat pada tombol konfirmasi. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    dismissLabel: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = { onConfirm() },
                enabled = confirmEnabled,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                ),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel ?: stringResource(id.local.tubeflow.R.string.action_cancel))
            }
        },
    )
}

data class StatSpec(val label: String, val value: String, val icon: ImageVector, val tint: Color? = null)

/** Metrik ringkas: tiga kolom pada font normal, menumpuk vertikal agar tidak terpotong pada font besar. */
@Composable
fun StatRow(stats: List<StatSpec>, modifier: Modifier = Modifier) {
    val stacked = LocalDensity.current.fontScale > 1.3f
    if (stacked) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(UiSpacing.row)) {
            stats.forEach { stat -> StatPill(stat.label, stat.value, stat.icon, Modifier.fillMaxWidth(), stat.tint ?: MaterialTheme.colorScheme.primary) }
        }
    } else {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            stats.forEach { stat -> StatPill(stat.label, stat.value, stat.icon, Modifier.weight(1f), stat.tint ?: MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
fun PageHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    SectionCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(UiSpacing.item),
        ) {
            Box(
                Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(4.dp))
                ActionButton(actionLabel, onAction, emphasis = ActionEmphasis.Primary, icon = Icons.Outlined.ArrowForward)
            }
        }
    }
}

@Composable
fun StatPill(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    SectionCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(20.dp), tint = tint)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Surface(color = container, shape = CircleShape) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = content, fontWeight = FontWeight.SemiBold)
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
fun CoverThumb(coverPath: String, thumbnailUrl: String, mode: MediaMode, modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.small) {
    val model: Any? = coverPath.takeIf { it.isNotBlank() && java.io.File(it).isFile }?.let { java.io.File(it) }
        ?: thumbnailUrl.takeIf { it.isNotBlank() }
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
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
fun JobCard(job: DownloadJob, cancel: () -> Unit, retry: () -> Unit, modifier: Modifier = Modifier, remove: (() -> Unit)? = null, pause: (() -> Unit)? = null, resume: (() -> Unit)? = null, refetchLyrics: ((String) -> Unit)? = null, play: (() -> Unit)? = null, searchLyrics: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    var openError by remember { mutableStateOf<String?>(null) }
    val accent=rememberCoverAccent(job.coverPath)
    val song=job.songIdentity()
    Card(modifier.fillMaxWidth(), colors=mediaCardColors(accent)) {
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
                    JobFailureDetails(job)
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
            if (job.state == JobState.COMPLETED && id.local.tubeflow.core.FailureDetails.isLyricFailure(job.lyricStatus)) JobFailureDetails(job, lyrics = true)
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
                        IconAction(Icons.Outlined.Refresh, "Cari ulang", { refetchLyrics(job.id) })
                    }
                }
            }
            openError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                when (job.state) {
                    JobState.QUEUED, JobState.RUNNING -> {
                        pause?.let { onClick -> IconAction(Icons.Outlined.Pause, "Jeda", onClick) }
                        IconAction(Icons.Outlined.Close, "Batalkan", cancel, destructive = true)
                    }
                    JobState.PAUSED -> {
                        resume?.let { onClick -> IconAction(Icons.Outlined.PlayArrow, "Lanjutkan", onClick) }
                        IconAction(Icons.Outlined.Close, "Batalkan", cancel, destructive = true)
                    }
                    JobState.FAILED, JobState.CANCELLED -> {
                        IconAction(Icons.Outlined.Refresh, "Coba lagi", retry)
                        remove?.let { IconAction(Icons.Outlined.Delete, "Hapus", it, destructive = true) }
                    }
                    JobState.COMPLETED -> {
                        if (job.mode == MediaMode.AUDIO && searchLyrics != null) {
                            IconAction(Icons.Outlined.Lyrics, "Cari lirik", { searchLyrics(job.id) }, enabled = job.outputUri != null)
                        }
                        if (job.mode == MediaMode.AUDIO && play != null) {
                            IconAction(Icons.Outlined.PlayArrow, "Putar", play, enabled = job.outputUri != null)
                        }
                        IconAction(Icons.Outlined.OpenInNew, "Buka", { openError = launchFile(context, job, false) }, enabled = job.outputUri != null)
                        IconAction(Icons.Outlined.Share, "Bagikan", { openError = launchFile(context, job, true) }, enabled = job.outputUri != null)
                        IconAction(Icons.Outlined.Refresh, "Unduh lagi", retry)
                        remove?.let { IconAction(Icons.Outlined.Delete, "Hapus file", it, destructive = true) }
                    }
                }
            }
            // Lampiran hanya untuk thumbnail dan subtitle. Berkas lirik tidak lagi ditawarkan di sini:
            // pencarian lirik sudah punya aksinya sendiri dan pemutar membaca LRC langsung dari koleksi.
            if (job.state == JobState.COMPLETED && !job.sidecarUris.isNullOrBlank() && job.mode == MediaMode.VIDEO) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    job.sidecarUris.lines().forEach { line ->
                        val kind = line.substringBefore('|')
                        val uri = line.substringAfter('|', "")
                        if (uri.isBlank()) return@forEach
                        val label = if (kind == "thumb") "Thumbnail" else "Subtitle (CC)"
                        val icon = if (kind == "thumb") Icons.Outlined.Image else Icons.Outlined.ClosedCaption
                        IconAction(icon, "Buka $label", { openError = openSidecar(context, uri, kind) })
                    }
                }
            }
        }
    }
}

/**
 * Pemilih satu nilai. `showLabel=false` dipakai di deretan filter Koleksi: hanya nilai aktif
 * yang tampil agar baris chip tetap ringkas dan tidak menumpuk label di setiap tombol.
 */
@Composable
fun <T> OptionMenu(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { expanded = true },
            modifier = modifier,
            label = {
                val value = options.firstOrNull { it.first == selected }?.second.orEmpty()
                Text(if (showLabel) "$label: $value" else value.ifBlank { label }, style = MaterialTheme.typography.labelLarge)
            },
            trailingIcon = { Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp)) },
        )
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
    launchFile(job, share) { raw, audio, sharing -> launchMedia(context, raw, audio, sharing) }

internal fun launchFile(job: DownloadJob, share: Boolean, launch: (String, Boolean, Boolean) -> String?): String? {
    val uri = job.outputUri?.takeIf { it.isNotBlank() } ?: return "File tidak tersedia."
    return launch(uri, job.mode == MediaMode.AUDIO, share)
}

fun launchMedia(context: Context, raw: String, audio: Boolean, share: Boolean): String? = try {
    val uri = raw.toUri()
    val mime = concreteMime(context, uri, if (audio) "audio/mpeg" else "video/mp4")
    val intent = if (share) Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri) else Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.clipData = ClipData.newRawUri("Download", uri)
    if (share) context.startActivity(Intent.createChooser(intent, "Bagikan file"))
    else try { context.startActivity(intent) } catch (_: android.content.ActivityNotFoundException) { context.startActivity(Intent.createChooser(intent, "Buka dengan")) }
    null
} catch (_: Exception) {
    if (share) "File tidak dapat dibagikan. Periksa akses file dan aplikasi tujuan."
    else "File tidak dapat dibuka. Pastikan ada aplikasi pemutar yang sesuai."
}

fun openSidecar(context: Context, uriString: String, kind: String): String? = try {
    val uri = uriString.toUri()
    val mime = concreteMime(context, uri, if (kind == "thumb") "image/jpeg" else "text/plain")
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    intent.clipData = ClipData.newRawUri("Sidecar", uri)
    try { context.startActivity(intent) } catch (_: android.content.ActivityNotFoundException) { context.startActivity(Intent.createChooser(intent, "Buka dengan")) }
    null
} catch (_: Exception) { "File tidak dapat dibuka di perangkat ini." }

fun extractLink(text: String): String = Regex("""https?://[^\s]+""").find(text)?.value ?: text.trim()
