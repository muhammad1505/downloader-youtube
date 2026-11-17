package id.local.tubeflow.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.basicMarquee
import androidx.core.net.toUri
import id.local.tubeflow.core.songIdentity
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import id.local.tubeflow.ui.theme.LocalCyberpunkTheme
import id.local.tubeflow.ui.theme.LocalTransformersTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.local.tubeflow.R
import id.local.tubeflow.core.BstationUrls
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

/** Sampul membawa warna; permukaan daftar tetap netral agar koleksi terlihat satu kesatuan. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun mediaCardColors(accent: Color): CardColors = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
)

@Composable
fun cyberpunkCardBorder(): BorderStroke? =
    if (LocalCyberpunkTheme.current) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)) else null

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
    val cyberpunk = LocalCyberpunkTheme.current
    val transformers = LocalTransformersTheme.current
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.medium,
        border = when {
            cyberpunk -> BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
            transformers -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f))
        },
        content = content,
    )
}

/** Panel ringkasan bagian: satu susunan untuk Antrean dan Riwayat di semua tema. */
@Composable
fun SectionSummary(
    icon: ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    SectionCard(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = UiSpacing.panel, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
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
    icon: ImageVector? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            DialogTitle(
                title = title,
                icon = icon ?: if (destructive) Icons.Outlined.Warning else Icons.Outlined.Info,
                destructive = destructive,
            )
        },
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

/**
 * Lencana ikon bersama untuk folder, alat, dan pengaturan. Transformers memakai sudut terpotong
 * dengan garis gradien biru-merah; Cyberpunk memakai isi turquoise-cyan dengan outline cyan;
 * tema lain tetap memakai wadah lembut yang lama.
 */
@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    description: String? = null,
    tint: Color? = null,
) {
    val transformers = LocalTransformersTheme.current
    val cyberpunk = LocalCyberpunkTheme.current
    val scheme = MaterialTheme.colorScheme
    val shape = if (transformers) CutCornerShape(topStart = size * 0.26f, bottomEnd = size * 0.26f) else MaterialTheme.shapes.small
    val fill = when {
        transformers -> Brush.linearGradient(listOf(scheme.primary.copy(alpha = 0.30f), scheme.secondary.copy(alpha = 0.22f)))
        // Cyberpunk: turquoise ke cyan dengan outline cyan; magenta tetap aksen sekunder saja.
        cyberpunk -> Brush.linearGradient(listOf(scheme.primary.copy(alpha = 0.26f), scheme.secondary.copy(alpha = 0.14f)))
        else -> Brush.linearGradient(listOf(scheme.primaryContainer, scheme.primaryContainer))
    }
    Box(
        modifier.size(size).clip(shape).background(fill).then(
            when {
                transformers -> Modifier.border(
                    1.dp,
                    Brush.linearGradient(listOf(scheme.primary.copy(alpha = 0.85f), scheme.secondary.copy(alpha = 0.75f))),
                    shape,
                )
                cyberpunk -> Modifier.border(1.dp, scheme.secondary.copy(alpha = 0.55f), shape)
                else -> Modifier
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(size * 0.48f), tint = tint ?: scheme.primary)
    }
}

/** Lambang perisai geometris untuk identitas tema Transformers (bukan logo pihak ketiga). */
@Composable
fun TransformerEmblem(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val shield = Path().apply {
            moveTo(w * 0.5f, 0f)
            lineTo(w, h * 0.22f)
            lineTo(w * 0.82f, h * 0.76f)
            lineTo(w * 0.5f, h)
            lineTo(w * 0.18f, h * 0.76f)
            lineTo(0f, h * 0.22f)
            close()
        }
        drawPath(shield, tint, style = Stroke(width = w * 0.09f))
        drawLine(tint, Offset(w * 0.28f, h * 0.36f), Offset(w * 0.5f, h * 0.56f), strokeWidth = w * 0.09f)
        drawLine(tint, Offset(w * 0.72f, h * 0.36f), Offset(w * 0.5f, h * 0.56f), strokeWidth = w * 0.09f)
        drawLine(tint, Offset(w * 0.5f, h * 0.56f), Offset(w * 0.5f, h * 0.82f), strokeWidth = w * 0.09f)
    }
}

/** Judul bagian ringkas: baris biasa di tema lama, pita bersudut di tema Transformers. */
@Composable
fun SectionBanner(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: String? = null,
) {
    val transformers = LocalTransformersTheme.current
    val scheme = MaterialTheme.colorScheme
    if (!transformers) {
        Row(
            modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        }
        return
    }
    val shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
    Row(
        modifier.fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(scheme.secondary.copy(alpha = 0.30f), scheme.surfaceContainerHigh, scheme.primary.copy(alpha = 0.16f)),
                ),
            )
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(scheme.secondary.copy(alpha = 0.65f), scheme.primary.copy(alpha = 0.45f))),
                shape,
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, Modifier.size(18.dp), tint = scheme.primary) }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
    }
}

@Composable
fun PageHeading(title: String, subtitle: String, icon: ImageVector? = null) {
    val transformers = LocalTransformersTheme.current
    val scheme = MaterialTheme.colorScheme
    if (transformers) {
        val shape = CutCornerShape(topStart = 22.dp, bottomEnd = 22.dp)
        Row(
            Modifier.fillMaxWidth()
                .clip(shape)
                .background(
                    Brush.horizontalGradient(
                        listOf(scheme.secondary.copy(alpha = 0.30f), scheme.surfaceContainerHigh, scheme.primary.copy(alpha = 0.16f)),
                    ),
                )
                .border(
                    1.dp,
                    Brush.horizontalGradient(listOf(scheme.secondary.copy(alpha = 0.80f), scheme.primary.copy(alpha = 0.60f))),
                    shape,
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon ?: Icons.Outlined.Bolt, size = 46.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
        return
    }
    val cyberpunk = LocalCyberpunkTheme.current
    val accent = if (cyberpunk) scheme.secondary else scheme.primary
    val outline = if (cyberpunk) scheme.secondary.copy(alpha = 0.65f) else scheme.outlineVariant.copy(alpha = 0.45f)
    Surface(
        color = scheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, outline),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.width(3.dp).heightIn(min = 42.dp).background(accent, MaterialTheme.shapes.small),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Judul dialog bersama. Tema Transformers memakai lencana ikon dan garis aksen biru-merah;
 * tema lain tetap memakai judul teks seperti sebelumnya.
 */
@Composable
fun DialogTitle(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    destructive: Boolean = false,
) {
    if (!LocalTransformersTheme.current) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    val scheme = MaterialTheme.colorScheme
    val accent = if (destructive) scheme.error else scheme.primary
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon ?: Icons.Outlined.Bolt, size = 42.dp, tint = accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().height(2.dp).background(
                Brush.horizontalGradient(listOf(accent, scheme.secondary.copy(alpha = 0.45f), Color.Transparent)),
                CutCornerShape(topStart = 2.dp, bottomEnd = 2.dp),
            ),
        )
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
            // Lencana status kosong ikut bahasa bentuk tema: terpotong di Transformers, bulat di tema lain.
            val badgeShape = if (LocalTransformersTheme.current) CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp) else CircleShape
            Box(
                Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, badgeShape),
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
                ActionButton(actionLabel, onAction, emphasis = ActionEmphasis.Primary, icon = Icons.AutoMirrored.Outlined.ArrowForward)
            }
        }
    }
}

@Composable
fun StatPill(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    Surface(
        modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
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

/** Logo merek resmi (Simple Icons, CC0) per platform; null = pakai ikon generik lama. */
@DrawableRes
private fun brandLogoRes(platformId: String): Int? = when (platformId) {
    "youtube_music" -> R.drawable.ic_brand_youtube_music
    "youtube" -> R.drawable.ic_brand_youtube
    "tiktok" -> R.drawable.ic_brand_tiktok
    "soundcloud" -> R.drawable.ic_brand_soundcloud
    "facebook" -> R.drawable.ic_brand_facebook
    "instagram" -> R.drawable.ic_brand_instagram
    "x" -> R.drawable.ic_brand_x
    "vimeo" -> R.drawable.ic_brand_vimeo
    "dailymotion" -> R.drawable.ic_brand_dailymotion
    "twitch" -> R.drawable.ic_brand_twitch
    "reddit" -> R.drawable.ic_brand_reddit
    "pinterest" -> R.drawable.ic_brand_pinterest
    "threads" -> R.drawable.ic_brand_threads
    "bandcamp" -> R.drawable.ic_brand_bandcamp
    "bilibili", BstationUrls.PLATFORM_ID -> R.drawable.ic_brand_bilibili
    "snapchat" -> R.drawable.ic_brand_snapchat
    else -> null
}

/**
 * Logo merek untuk chip domain. Warna asli dipakai apa adanya; merek yang resminya hitam atau
 * kuning terang (TikTok, X, Threads, Dailymotion, Snapchat) ditint mengikuti tema agar tidak hilang
 * di salah satu tema. Likee belum punya aset sehingga memakai ikon generik.
 */
@Composable
fun PlatformBrandLogo(
    platform: id.local.tubeflow.core.SourcePlatform,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val logo = brandLogoRes(platform.id)
    if (logo == null) {
        PlatformIcon(platform, modifier)
        return
    }
    if (platform.id in monochromeBrands) {
        Icon(painterResource(logo), contentDescription, modifier, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Image(painterResource(logo), contentDescription, modifier)
    }
}

private val monochromeBrands = setOf("tiktok", "x", "threads", "dailymotion", "snapchat")

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
/**
 * Baris media ringkas untuk semua daftar Koleksi: sampul 52 dp, satu baris judul, satu baris
 * keterangan, penanda kecil (favorit/lirik/subtitle), dan slot aksi di kanan (biasanya
 * menu titik tiga). Dipakai bersama supaya daftar musik, video, folder, favorit, dan playlist
 * punya tinggi item yang sama. Judul dan keterangan memakai [basicMarquee]: teks yang muat
 * diam, teks yang meluber bergulir kanan ke kiri supaya nama file terbaca penuh.
 */
@Composable
fun MediaListRow(
    coverPath: String,
    mode: MediaMode,
    title: String,
    subtitle: String,
    accent: Color,
    onClickLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorText: String? = null,
    /** Penanda lampiran kecil di ujung baris (lirik/subtitle) tanpa menambah baris teks. */
    badges: RowBadges = RowBadges(),
    /** Aksi cepat di samping penanda lampiran (mis. hati favorit dan bagikan). */
    quickActions: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit,
) {
    Card(
        modifier.fillMaxWidth().clickable(enabled = enabled, onClickLabel = onClickLabel, onClick = onClick),
        colors = mediaCardColors(accent),
        border = cyberpunkCardBorder(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CoverThumb(coverPath, "", mode, Modifier.size(52.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    errorText ?: subtitle,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (errorText != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Penanda lampiran, aksi cepat, dan menu berdekatan supaya teks tetap mendapat ruang.
            Row(verticalAlignment = Alignment.CenterVertically) {
                RowAttachmentIcons(badges)
                quickActions()
                trailing()
            }
        }
    }
}

/**
 * Tombol ikon ringkas di baris media (36 dp, ikon 20 dp). Ukuran ini menjaga satu baris tetap
 * memuat teks, penanda lampiran, dua aksi cepat, dan menu ⋮ tanpa menambah tinggi baris.
 */
@Composable
fun RowIconAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(20.dp), tint = tint)
    }
}

/**
 * Baris antrean putar bersama pemutar audio dan video: sampul 48 dp, judul, keterangan opsional,
 * status lampiran, dan label "Sedang diputar" bila kartu aktif (fill+border aksen). Lembar video
 * mengirim warnanya sendiri karena selalu tampil gelap di semua tema; pemutar audio memakai
 * warna tema lewat default. Aksi per item (menu ⋮) tetap milik pemanggil — jangan menyalin aksi
 * khusus audio ke video demi menyamakan jumlah tombol.
 */
@Composable
fun QueueRow(
    coverPath: String,
    mode: MediaMode,
    title: String,
    current: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    meta: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    metaColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    playingLabel: String = "Sedang diputar",
    onClickLabel: String = "Putar dari antrean",
    attachments: @Composable () -> Unit = {},
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (current) accent.copy(alpha = 0.12f).compositeOver(containerColor) else containerColor,
        border = if (current) BorderStroke(1.dp, accent.copy(alpha = 0.30f)) else null,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).clickable(onClickLabel = onClickLabel, onClick = onClick).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverThumb(coverPath, "", mode, Modifier.size(48.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        title,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (meta != null) Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = metaColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    attachments()
                    if (current) Text(
                        playingLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                }
            }
            trailing?.invoke()
        }
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

/**
 * Panel aksi baris unduhan: SATU menu ⋮ untuk Antrean maupun Riwayat. Aksi mengikuti status
 * (Jeda/Lanjutkan/Batalkan/Coba lagi/Hapus) atau daftar aksi hasil selesai dengan grup yang sama,
 * sehingga tinggi baris, ikon, dan susunan menunya tidak lagi berbeda antar-tab.
 */
@Composable
private fun JobRowMenu(
    job: DownloadJob,
    pause: (() -> Unit)?,
    resume: (() -> Unit)?,
    cancel: () -> Unit,
    retry: () -> Unit,
    remove: (() -> Unit)?,
    completedSpecs: List<MediaActionSpec>,
    completedEnabled: (MediaActionKind) -> Boolean,
    onCompletedAction: (MediaActionKind) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val destructive = MaterialTheme.colorScheme.error
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, "Aksi unduhan") }
        DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 420.dp)) {
            when (job.state) {
                JobState.QUEUED, JobState.RUNNING -> {
                    pause?.let { action ->
                        DropdownMenuItem(
                            text = { Text("Jeda") },
                            leadingIcon = { Icon(Icons.Outlined.Pause, null) },
                            onClick = { open = false; action() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Batalkan", color = destructive) },
                        leadingIcon = { Icon(Icons.Outlined.Close, null, tint = destructive) },
                        onClick = { open = false; cancel() },
                    )
                }
                JobState.PAUSED -> {
                    resume?.let { action ->
                        DropdownMenuItem(
                            text = { Text("Lanjutkan") },
                            leadingIcon = { Icon(Icons.Outlined.PlayArrow, null) },
                            onClick = { open = false; action() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Batalkan", color = destructive) },
                        leadingIcon = { Icon(Icons.Outlined.Close, null, tint = destructive) },
                        onClick = { open = false; cancel() },
                    )
                }
                JobState.FAILED, JobState.CANCELLED -> {
                    DropdownMenuItem(
                        text = { Text("Coba lagi") },
                        leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                        onClick = { open = false; retry() },
                    )
                    remove?.let { action ->
                        DropdownMenuItem(
                            text = { Text("Hapus", color = destructive) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = destructive) },
                            onClick = { open = false; action() },
                        )
                    }
                }
                JobState.COMPLETED -> {
                    // Grup dan urutan sama dengan menu baris Koleksi agar panel terbaca seragam.
                    var currentGroup: String? = null
                    completedSpecs.forEach { spec ->
                        if (spec.group != null && spec.group != currentGroup) {
                            Text(
                                spec.group,
                                Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            currentGroup = spec.group
                        }
                        val isDestructive = MediaCardPlan.destructive(spec.kind)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    MediaCardPlan.label(spec.kind, job.mode == MediaMode.AUDIO),
                                    color = if (isDestructive) destructive else Color.Unspecified,
                                )
                            },
                            onClick = { open = false; onCompletedAction(spec.kind) },
                            enabled = completedEnabled(spec.kind),
                            leadingIcon = {
                                Icon(
                                    MediaCardPlan.icon(spec.kind),
                                    null,
                                    tint = if (isDestructive) destructive else LocalContentColor.current,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun JobCard(job: DownloadJob, cancel: () -> Unit, retry: () -> Unit, modifier: Modifier = Modifier, remove: (() -> Unit)? = null, pause: (() -> Unit)? = null, resume: (() -> Unit)? = null, refetchLyrics: ((String) -> Unit)? = null, play: (() -> Unit)? = null, searchLyrics: ((String) -> Unit)? = null, enqueue: (() -> Unit)? = null, playNext: (() -> Unit)? = null, editLyricTiming: (() -> Unit)? = null, highlighted: Boolean = false, compact: Boolean = false, queuedHint: String? = null) {
    val context = LocalContext.current
    var openError by remember { mutableStateOf<String?>(null) }
    // Cyberpunk memakai palet tetapnya: aksen sampul tidak mengganti warna tema.
    val accent=if (LocalCyberpunkTheme.current) MaterialTheme.colorScheme.primary else rememberCoverAccent(job.coverPath)
    val song=job.songIdentity()
    val audio = job.mode == MediaMode.AUDIO
    val attachments = attachmentUris(job.sidecarUris)
    // Aksi kartu selesai dipakai di dua tempat: menu titik tiga baris ringkas atau deret bawah.
    val completedSpecs = MediaCardPlan.history(
        audio = audio,
        hasThumbnail = attachments.containsKey(ATTACHMENT_THUMBNAIL),
        hasSubtitle = hasSubtitleAttachment(audio, job.sidecarUris),
        hasLyrics = job.lyricStatus == LyricStatus.SAVED,
    ).filter { spec ->
        // Aksi hanya muncul bila pemanggil menyediakan jalurnya.
        when (spec.kind) {
            MediaActionKind.PLAY -> play != null
            MediaActionKind.FIND_LYRICS -> searchLyrics != null
            MediaActionKind.ENQUEUE -> enqueue != null
            MediaActionKind.PLAY_NEXT -> playNext != null
            MediaActionKind.EDIT_LYRIC_TIMING -> editLyricTiming != null
            MediaActionKind.DELETE -> remove != null
            else -> true
        }
    }
    val completedEnabled: (MediaActionKind) -> Boolean = { kind ->
        when (kind) {
            MediaActionKind.PLAY, MediaActionKind.OPEN, MediaActionKind.SHARE,
            MediaActionKind.FIND_LYRICS -> job.outputUri != null
            else -> true
        }
    }
    val onCompletedAction: (MediaActionKind) -> Unit = { kind ->
        when (kind) {
            MediaActionKind.PLAY -> play?.invoke()
            MediaActionKind.ENQUEUE -> enqueue?.invoke()
            MediaActionKind.PLAY_NEXT -> playNext?.invoke()
            MediaActionKind.OPEN -> openError = launchFile(context, job, false)
            MediaActionKind.SHARE -> openError = launchFile(context, job, true)
            MediaActionKind.FIND_LYRICS -> searchLyrics?.invoke(job.id)
            MediaActionKind.EDIT_LYRIC_TIMING -> editLyricTiming?.invoke()
            MediaActionKind.REDOWNLOAD -> retry()
            MediaActionKind.DELETE -> remove?.invoke()
            MediaActionKind.OPEN_THUMBNAIL -> attachments[ATTACHMENT_THUMBNAIL]?.let {
                openError = openSidecar(context, it, ATTACHMENT_THUMBNAIL)
            }
            MediaActionKind.OPEN_SUBTITLE -> attachments.entries
                .firstOrNull { it.key != ATTACHMENT_THUMBNAIL }
                ?.let { (sidecarKind, uri) -> openError = openSidecar(context, uri, sidecarKind) }
            else -> Unit
        }
    }
    val completedActions: @Composable () -> Unit = {
        MediaActionBar(
            audio = audio,
            compact = compact,
            specs = completedSpecs,
            enabled = completedEnabled,
            onClick = onCompletedAction,
        )
    }
    Card(
        modifier.fillMaxWidth().clickable(
            enabled = job.state == JobState.COMPLETED && !job.outputUri.isNullOrBlank() && play != null,
            onClickLabel = if (job.mode == MediaMode.AUDIO) "Putar lagu" else "Putar video",
        ) { play?.invoke() },
        colors = mediaCardColors(accent),
        border = if (highlighted) BorderStroke(2.dp, accent) else null,
    ) {
        Column(
            Modifier.padding(if (compact) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverThumb(job.coverPath, job.thumbnailUrl, job.mode, Modifier.size(if (compact) 52.dp else 56.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        Format.musicLabel(song),
                        style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = if (compact) FontWeight.SemiBold else FontWeight.Bold,
                        maxLines = if (compact) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val credit=song.artist.orEmpty().ifBlank { song.remixer }
                    if (compact) {
                        // Satu baris keterangan seragam dengan daftar Koleksi: artis · opsi unduhan · platform.
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            PlatformBrandLogo(job.sourcePlatform, Modifier.size(14.dp))
                            Text(
                                listOfNotNull(credit.takeIf { it.isNotBlank() }, formatMeta(job), job.sourcePlatform.label)
                                    .joinToString(" · "),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        if (credit.isNotBlank()) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Outlined.Person,null,Modifier.size(14.dp)); Text(credit,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                        // Platform asal tautan (mis. SoundCloud, TikTok) + ikonnya — dikirim oleh Antrean & Riwayat (P2).
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            PlatformBrandLogo(job.sourcePlatform, Modifier.size(14.dp))
                            Text(
                                formatMeta(job) + " · ${job.sourcePlatform.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                // Baris ringkas: satu panel ⋮ untuk semua status — Antrean, Riwayat, dan Koleksi seragam.
                when {
                    compact -> JobRowMenu(
                        job = job,
                        pause = pause,
                        resume = resume,
                        cancel = cancel,
                        retry = retry,
                        remove = remove,
                        completedSpecs = if (job.state == JobState.COMPLETED) completedSpecs else emptyList(),
                        completedEnabled = completedEnabled,
                        onCompletedAction = onCompletedAction,
                    )
                    else -> StateChip(job.state)
                }
            }
            when (job.state) {
                JobState.RUNNING -> {
                    if (job.progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { (job.progress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp), color=accent, trackColor=MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(6.dp),color=accent,trackColor=MaterialTheme.colorScheme.surfaceContainerHighest)
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
                JobState.QUEUED -> Text(queuedHint ?: "Menunggu giliran · antrean berjalan otomatis", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                JobState.PAUSED -> {
                    if (job.progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { (job.progress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp), color=accent, trackColor=MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                    }
                    val position = if (job.progress >= 0f) " · ${job.progress.toInt()}%" else ""
                    Text("Dijeda · ${DownloadPhase.label(job.phase)}$position · ketuk Lanjutkan untuk melanjutkan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                JobState.CANCELLED -> Text("Dibatalkan oleh kamu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (job.state != JobState.FAILED && job.resumeNotice.isNotBlank())
                Text(job.resumeNotice, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (job.state == JobState.COMPLETED) job.outputUri?.let { raw ->
                AttachmentInfo(raw, job.mode == MediaMode.AUDIO, sidecars = job.sidecarUris,
                    revision = job.lyricStatus to job.completedAt)
            }
            // Status lirik terpisah dari status audio: audio sukses tetap "Selesai" walau lirik tidak ada (BUG-014).
            if (job.processingNotice.isNotBlank()) Text(job.processingNotice, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            if (job.state == JobState.COMPLETED && job.mode == MediaMode.AUDIO && job.lyricStatus.isNotBlank() && job.lyricStatus != LyricStatus.SAVED) {
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
                    if (id.local.tubeflow.core.FailureDetails.isLyricFailure(job.lyricStatus)) {
                        JobFailureDetails(job, lyrics = true, compact = true)
                    }
                    if (refetchLyrics != null && LyricStatus.retryable(job.lyricStatus)) {
                        IconAction(Icons.Outlined.Refresh, "Cari ulang", { refetchLyrics(job.id) })
                    }
                }
            }
            openError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            // Baris ringkas menaruh semua aksi di ujung baris; hanya kartu besar memakai deret ikon bawah.
            if (!compact) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                    JobState.COMPLETED -> completedActions()
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
    val fileName = displayName(context, uri)
    val extension = fileName?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.length in 2..5 }
    val byExtension = extension?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
    val current = runCatching { context.contentResolver.getType(uri) }.getOrNull()
    val mime = byExtension ?: current
    return if (mime.isNullOrBlank() || mime.contains('*') || mime == "application/octet-stream") fallback else mime
}

fun launchFile(context: Context, job: DownloadJob, share: Boolean): String? =
    launchFile(job, share) { raw, audio, sharing -> launchMedia(context, raw, audio, sharing, job.title) }

internal fun launchFile(job: DownloadJob, share: Boolean, launch: (String, Boolean, Boolean) -> String?): String? {
    val uri = job.outputUri?.takeIf { it.isNotBlank() } ?: return "File tidak tersedia."
    return launch(uri, job.mode == MediaMode.AUDIO, share)
}

/**
 * Caption berbagi dari nama file. Ekstensi dibuang karena aplikasi tujuan sudah menampilkan
 * jenis berkasnya, sehingga caption berisi judul yang rapi (mis. "Ipank - Mutiara").
 */
internal fun shareCaptionFrom(displayName: String?): String? =
    displayName?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotBlank() }

/**
 * Caption berbagi: nama file tanpa ekstensi, atau judul lagu bila provider tidak memberi nama
 * (mis. URI SAF tanpa `DISPLAY_NAME`). Judul dipakai apa adanya karena titik di judul bukan ekstensi.
 */
internal fun shareCaption(fileName: String?, title: String?): String? =
    shareCaptionFrom(fileName) ?: title?.trim()?.takeIf { it.isNotBlank() }

/** Nama tampilan berkas dari MediaStore/SAF, dipakai untuk caption berbagi. */
internal fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()

fun launchMedia(
    context: Context,
    raw: String,
    audio: Boolean,
    share: Boolean,
    fallbackCaption: String? = null,
): String? = try {
    val uri = raw.toUri()
    val mime = concreteMime(context, uri, if (audio) "audio/mpeg" else "video/mp4")
    val intent = if (share) {
        val caption = shareCaption(displayName(context, uri), fallbackCaption)
        Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).apply {
            // Aplikasi yang mendukung caption memakai EXTRA_TEXT sebagai pesan bawaan.
            if (caption != null) putExtra(Intent.EXTRA_TEXT, caption)
        }.also {
            // WhatsApp tidak menyediakan kolom caption untuk audio, jadi teksnya disiapkan
            // di clipboard agar bisa ditempel; toast menjelaskan kenapa clipboard berubah.
            // Toast wajib dari main thread: pemanggil Koleksi berjalan di Dispatchers.IO.
            if (caption != null && audio) {
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("Caption", caption))
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Caption disalin: $caption", Toast.LENGTH_LONG).show()
                }
            }
        }
    } else Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
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
