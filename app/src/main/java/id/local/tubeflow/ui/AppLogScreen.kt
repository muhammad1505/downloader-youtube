package id.local.tubeflow.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.local.tubeflow.core.AppLog
import id.local.tubeflow.core.LogCategory
import id.local.tubeflow.core.LogEntry
import id.local.tubeflow.core.LogFilter
import id.local.tubeflow.core.LogFormat
import id.local.tubeflow.core.LogLevel

/**
 * Layar Log aplikasi: ringkasan, filter, dan daftar terbaru lebih dulu.
 * Catatan berasal dari [AppLog] yang sama dengan yang ditulis worker dan layar lain.
 */
@Composable
fun AppLogScreen() {
    val context = LocalContext.current
    val entries by AppLog.entries.collectAsStateWithLifecycle()
    var levelName by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryName by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    val level = levelName?.let { LogLevel.fromName(it) }
    val category = categoryName?.let { LogCategory.fromName(it) }
    val filter = remember(level, category, query) { LogFilter(level, category, query) }
    val visible = remember(entries, filter) { entries.asReversed().filter(filter::matches) }
    val errors = remember(entries) { entries.count { it.level == LogLevel.ERROR } }
    val warnings = remember(entries) { entries.count { it.level == LogLevel.WARN } }
    val exportText = { AppLog.exportText() }

    fun resetFilters() {
        levelName = null
        categoryName = null
        query = ""
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { DialogTitle("Bersihkan log?", icon = Icons.Outlined.DeleteSweep, destructive = true) },
        text = {
            Text("Semua catatan di layar ini dan berkas log lokal akan dihapus. Tindakan ini tidak memengaruhi unduhan, koleksi, atau pengaturan.")
        },
        confirmButton = {
            TextButton(
                onClick = {
                    confirmClear = false
                    AppLog.clear()
                    Toast.makeText(context, "Log aplikasi dibersihkan.", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Bersihkan") }
        },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Batal") } },
    )

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(UiSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.item),
    ) {
        item {
            PageHeading(
                "Log aplikasi",
                "Aktivitas, peringatan, dan galat semua proses TubeFlow di perangkat ini.",
                Icons.AutoMirrored.Outlined.Article,
            )
        }
        item {
            Panel(
                title = "Ringkasan",
                description = "Catatan disimpan lokal, dibatasi ukurannya, dan disanitasi sebelum ditulis. URL, path, token, dan cookie tidak ikut tersimpan.",
            ) {
                Text(
                    "${entries.size} catatan · $errors galat · $warnings peringatan",
                    style = MaterialTheme.typography.titleSmall,
                )
                ScrollRow {
                    ActionChip(
                        "Salin",
                        {
                            val copied = runCatching {
                                context.getSystemService(ClipboardManager::class.java)
                                    ?.setPrimaryClip(ClipData.newPlainText("Log TubeFlow", exportText()))
                            }.isSuccess
                            Toast.makeText(
                                context,
                                if (copied) "Log disalin." else "Log belum dapat disalin.",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        icon = Icons.Outlined.ContentCopy,
                        enabled = entries.isNotEmpty(),
                    )
                    ActionChip(
                        "Bagikan",
                        { shareLog(context, exportText()) },
                        icon = Icons.Outlined.Share,
                        enabled = entries.isNotEmpty(),
                    )
                    ActionChip(
                        "Bersihkan",
                        { confirmClear = true },
                        icon = Icons.Outlined.DeleteSweep,
                        enabled = entries.isNotEmpty(),
                        destructive = true,
                    )
                }
            }
        }
        item {
            SearchField(
                value = query,
                onValueChange = { query = it },
                label = "Cari pesan, kategori, atau detail",
            )
        }
        item {
            ScrollRow {
                MenuChip(
                    label = "Kategori",
                    options = listOf<Pair<LogCategory?, String>>(null to "Semua kategori") +
                        LogCategory.entries.map { it to it.label },
                    selected = category,
                    onSelect = { categoryName = it?.name },
                )
                FilterChip(
                    selected = level == null,
                    onClick = { levelName = null },
                    label = { Text("Semua") },
                )
                LogLevel.entries.forEach { item ->
                    FilterChip(
                        selected = level == item,
                        onClick = { levelName = item.name },
                        label = { Text(item.label) },
                    )
                }
            }
        }
        if (visible.isEmpty()) {
            item {
                Panel(
                    title = if (entries.isEmpty()) "Belum ada catatan" else "Tidak ada catatan yang cocok",
                    description = if (entries.isEmpty()) {
                        "Aktivitas aplikasi akan muncul di sini, termasuk kegagalan mesin, unduhan, pemutar, dan karaoke."
                    } else {
                        "Ubah kata kunci, tingkat, atau kategori untuk melihat catatan lain."
                    },
                ) {
                    if (entries.isNotEmpty()) {
                        ActionButton("Tampilkan semua", ::resetFilters, icon = Icons.Outlined.FilterAltOff)
                    }
                }
            }
        }
        items(visible, key = { it.seq }) { entry ->
            LogRow(entry)
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    var expanded by rememberSaveable(entry.seq) { mutableStateOf(false) }
    val color = logLevelColor(entry.level)
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                entry.level.badge,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(color.copy(alpha = 0.14f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .semantics { contentDescription = entry.level.label },
            )
            Text(
                LogFormat.time(entry.at),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                entry.category.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Text(entry.message, style = MaterialTheme.typography.bodySmall)
        entry.detail?.let { detail ->
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    null,
                    Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(if (expanded) "Tutup detail" else "Detail", style = MaterialTheme.typography.labelMedium)
            }
            if (expanded) Text(
                detail,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(10.dp),
            )
        }
    }
}

@Composable
private fun logLevelColor(level: LogLevel): Color = when (level) {
    LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
    LogLevel.INFO -> MaterialTheme.colorScheme.primary
    LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
    LogLevel.ERROR -> MaterialTheme.colorScheme.error
}

private fun shareLog(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "Log TubeFlow")
        .putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(intent, "Bagikan log")) }
        .onFailure {
            Toast.makeText(context, "Tidak ada aplikasi untuk membagikan log.", Toast.LENGTH_SHORT).show()
        }
}
