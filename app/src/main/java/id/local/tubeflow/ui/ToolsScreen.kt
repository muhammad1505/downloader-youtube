package id.local.tubeflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import id.local.tubeflow.R
import id.local.tubeflow.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hub alat: operasi massal pada file yang sudah ada. Aksi per kartu di Koleksi/Riwayat tetap
 * menjadi pintu utama untuk satu berkas; layar ini tidak menggandakan aksi tersebut.
 */
@Composable
fun ToolsScreen(
    library: MediaLibraryViewModel,
    player: PlayerViewModel,
    karaoke: KaraokeViewModel,
    onEditTiming: (MediaItem) -> Unit,
) {
    val model: ToolsViewModel = viewModel()
    val items by model.audio.collectAsStateWithLifecycle()
    val progress by model.progress.collectAsStateWithLifecycle()
    var renameOpen by rememberSaveable { mutableStateOf(false) }
    var tagOpen by rememberSaveable { mutableStateOf(false) }
    var lyricOpen by rememberSaveable { mutableStateOf(false) }
    var instrumentalOpen by rememberSaveable { mutableStateOf(false) }
    var instrumentalBatch by remember { mutableStateOf<List<MediaItem>?>(null) }
    var tapItem by remember { mutableStateOf<MediaItem?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(UiSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.item),
    ) {
        PageHeading(stringResource(R.string.tools_title), stringResource(R.string.tools_subtitle))
        ToolHubCard(
            title = stringResource(R.string.tools_lyrics_title),
            description = stringResource(R.string.tools_lyrics_description),
            icon = Icons.Outlined.Lyrics,
        ) {
            ActionButton(
                stringResource(R.string.tools_lyrics_action),
                { lyricOpen = true },
                icon = Icons.Outlined.Lyrics,
                enabled = items.any { it.lyricUri != null } && !model.busy,
                fillWidth = true,
            )
        }
        ToolHubCard(
            title = stringResource(R.string.tools_rename_title),
            description = stringResource(R.string.tools_rename_description),
            icon = Icons.Outlined.DriveFileRenameOutline,
        ) {
            ActionButton(
                stringResource(R.string.tools_rename_action),
                { renameOpen = true },
                icon = Icons.Outlined.DriveFileRenameOutline,
                enabled = items.isNotEmpty() && !model.busy,
                fillWidth = true,
            )
        }
        ToolHubCard(
            title = stringResource(R.string.tools_karaoke_title),
            description = stringResource(R.string.tools_karaoke_description),
            icon = Icons.Outlined.MicOff,
        ) {
            ActionButton(
                stringResource(R.string.tools_karaoke_action),
                { instrumentalOpen = true },
                icon = Icons.Outlined.MicOff,
                enabled = items.isNotEmpty() && !model.busy,
                fillWidth = true,
            )
        }
        ToolHubCard(
            title = stringResource(R.string.tools_tag_title),
            description = stringResource(R.string.tools_tag_description),
            icon = Icons.Outlined.Sell,
        ) {
            ActionButton(
                stringResource(R.string.tools_tag_action),
                { tagOpen = true },
                icon = Icons.Outlined.Sell,
                enabled = items.isNotEmpty() && !model.busy,
                fillWidth = true,
            )
        }
        progress?.let { state -> ToolWorkPanel(state, onCancel = model::cancel, onDismiss = model::dismissProgress) }
        if (items.isEmpty()) {
            EmptyState(
                Icons.Outlined.Build,
                stringResource(R.string.tools_empty),
                stringResource(R.string.tools_empty_help),
                actionLabel = stringResource(R.string.tools_refresh),
                onAction = { library.scan() },
            )
        }
    }

    if (lyricOpen) LyricPickerDialog(
        items = items.filter { it.lyricUri != null },
        onOffset = { lyricOpen = false; onEditTiming(it) },
        onTap = { lyricOpen = false; tapItem = it },
        onDismiss = { lyricOpen = false },
    )
    if (renameOpen) RenameToolDialog(
        items = items,
        onApply = { plan -> renameOpen = false; model.rename(plan) },
        onDismiss = { renameOpen = false },
    )
    if (tagOpen) TagToolDialog(
        items = items,
        onApply = { plan -> tagOpen = false; model.tag(plan) },
        onDismiss = { tagOpen = false },
    )
    if (instrumentalOpen) InstrumentalToolDialog(
        items = items,
        onSingle = { item ->
            instrumentalOpen = false
            player.trackFor(item)?.let(karaoke::open)
        },
        onBatch = { chosen -> instrumentalOpen = false; instrumentalBatch = chosen },
        onDismiss = { instrumentalOpen = false },
    )
    instrumentalBatch?.let { chosen ->
        ConfirmDialog(
            title = stringResource(R.string.tools_karaoke_confirm_title),
            text = pluralStringResource(R.plurals.tools_karaoke_confirm_text, chosen.size, chosen.size),
            confirmLabel = stringResource(R.string.tools_karaoke_batch),
            onConfirm = { instrumentalBatch = null; model.instrumental(chosen) },
            onDismiss = { instrumentalBatch = null },
        )
    }
    tapItem?.let { item ->
        TapSyncDialog(item, library, player, onDismiss = { tapItem = null })
    }
}

@Composable
private fun ToolHubCard(
    title: String,
    description: String,
    icon: ImageVector,
    action: @Composable ColumnScope.() -> Unit,
) {
    val cyberpunk = id.local.tubeflow.ui.theme.LocalCyberpunkTheme.current
    SectionCard {
        Column(Modifier.padding(UiSpacing.panel), verticalArrangement = Arrangement.spacedBy(UiSpacing.row)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .then(if (cyberpunk) Modifier.border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f), MaterialTheme.shapes.small) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.secondary)
            }
            action()
        }
    }
}

@Composable
private fun ToolWorkPanel(state: ToolsViewModel.Progress, onCancel: () -> Unit, onDismiss: () -> Unit) {
    val kind = stringResource(
        when (state.kind) {
            ToolsViewModel.Kind.RENAME -> R.string.tools_kind_rename
            ToolsViewModel.Kind.TAG -> R.string.tools_kind_tag
            ToolsViewModel.Kind.KARAOKE -> R.string.tools_kind_karaoke
        },
    )
    Panel(title = stringResource(R.string.tools_progress_title), description = kind) {
        if (state.running) {
            LinearProgressIndicator(
                progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(pluralStringResource(R.plurals.tools_progress_running, state.total, state.done, state.total), style = MaterialTheme.typography.bodySmall)
            if (state.label.isNotBlank()) Text(state.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ActionButton(stringResource(R.string.tools_progress_cancel), onCancel, destructive = true, fillWidth = true)
        } else {
            Text(
                if (state.cancelled) pluralStringResource(R.plurals.tools_progress_cancelled, state.total, state.done, state.total)
                else pluralStringResource(R.plurals.tools_progress_done, state.total, state.succeeded, state.failures.size),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.failures.isNotEmpty()) {
                Text(pluralStringResource(R.plurals.tools_progress_failed, state.failures.size, state.failures.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                state.failures.take(3).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            ActionButton(stringResource(R.string.tools_progress_close), onDismiss, emphasis = ActionEmphasis.Quiet, fillWidth = true)
        }
    }
}

@Composable
private fun ToolPicker(
    items: List<MediaItem>,
    selected: Set<String>,
    multi: Boolean,
    onToggle: (String) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
) {
    SearchField(query, onQuery, label = stringResource(R.string.tools_picker_search))
    val shown = items.filter {
        query.isBlank() || it.title.contains(query, true) || it.displayName.contains(query, true) || it.artist.contains(query, true)
    }
    if (shown.isEmpty()) {
        Text(stringResource(R.string.tools_picker_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyColumn(Modifier.fillMaxWidth().height(240.dp)) {
        items(shown, key = { it.uri }) { item ->
            PickerRow(item, item.uri in selected, multi) { onToggle(item.uri) }
        }
    }
}

@Composable
private fun PickerRow(item: MediaItem, checked: Boolean, multi: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.row),
    ) {
        if (multi) Checkbox(checked, null) else RadioButton(checked, null)
        Column(Modifier.weight(1f)) {
            Text(
                item.title.ifBlank { item.displayName },
                style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (item.artist.isNotBlank()) Text(
                item.artist, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LyricPickerDialog(
    items: List<MediaItem>,
    onOffset: (MediaItem) -> Unit,
    onTap: (MediaItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<String?>(null) }
    val selected = items.firstOrNull { it.uri == chosen }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tools_lyrics_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.item)) {
                Text(stringResource(R.string.tools_lyrics_mode_help), style = MaterialTheme.typography.bodySmall)
                if (items.isEmpty()) Text(stringResource(R.string.tools_lyrics_empty), style = MaterialTheme.typography.bodySmall)
                else ToolPicker(items, setOfNotNull(chosen), multi = false, onToggle = { chosen = it }, query = query, onQuery = { query = it })
                Text(stringResource(R.string.tools_lyrics_offset_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.tools_lyrics_tap_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.row)) {
                TextButton(onClick = { chosen?.let { uri -> items.firstOrNull { it.uri == uri }?.let(onTap) } }, enabled = selected != null) {
                    Text(stringResource(R.string.tools_lyrics_tap))
                }
                TextButton(onClick = { selected?.let(onOffset) }, enabled = selected != null) {
                    Text(stringResource(R.string.tools_lyrics_offset))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun RenameToolDialog(
    items: List<MediaItem>,
    onApply: (RenamePlan.Plan) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var pattern by rememberSaveable { mutableStateOf("{judul} - {artis}") }
    var find by rememberSaveable { mutableStateOf("") }
    var replace by rememberSaveable { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    val chosen = items.filter { it.uri in selected }
    val plan = remember(chosen, pattern, find, replace) {
        RenamePlan.plan(chosen.map { RenamePlan.Item(it.uri, it.displayName, it.title, it.artist, it.album, it.track) }, pattern, find, replace)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tools_rename_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.item), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(pluralStringResource(R.plurals.tools_picker_selected, selected.size, selected.size), style = MaterialTheme.typography.labelLarge)
                    Row {
                        TextButton(onClick = { selected = items.map { it.uri }.toSet() }) { Text(stringResource(R.string.tools_picker_all)) }
                        TextButton(onClick = { selected = emptySet() }, enabled = selected.isNotEmpty()) { Text(stringResource(R.string.tools_picker_none)) }
                    }
                }
                ToolPicker(items, selected, multi = true, onToggle = { uri -> selected = if (uri in selected) selected - uri else selected + uri }, query = query, onQuery = { query = it })
                OutlinedTextField(
                    value = pattern, onValueChange = { pattern = it },
                    label = { Text(stringResource(R.string.tools_rename_pattern)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.row)) {
                    OutlinedTextField(
                        value = find, onValueChange = { find = it },
                        label = { Text(stringResource(R.string.tools_rename_find)) },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = replace, onValueChange = { replace = it },
                        label = { Text(stringResource(R.string.tools_rename_replace)) },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
                Text(stringResource(R.string.tools_rename_pattern_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(pluralStringResource(R.plurals.tools_rename_preview, plan.changes, plan.changes, plan.kept), style = MaterialTheme.typography.labelLarge)
                plan.proposals.filter { it.applies }.take(4).forEach { proposal ->
                    Text("${proposal.before}\n→ ${proposal.after}", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { confirming = true }, enabled = plan.changes > 0) { Text(stringResource(R.string.tools_rename_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (confirming) ConfirmDialog(
        title = stringResource(R.string.tools_rename_confirm_title),
        text = pluralStringResource(R.plurals.tools_rename_confirm_text, plan.changes, plan.changes),
        confirmLabel = stringResource(R.string.tools_rename_apply),
        onConfirm = { confirming = false; onApply(plan) },
        onDismiss = { confirming = false },
    )
}

@Composable
private fun TagToolDialog(
    items: List<MediaItem>,
    onApply: (List<TagSuggestion.Proposal>) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var fillTitle by rememberSaveable { mutableStateOf(true) }
    var fillArtist by rememberSaveable { mutableStateOf(true) }
    val chosen = items.filter { it.uri in selected }
    val plan = remember(chosen, fillTitle, fillArtist) {
        TagSuggestion.plan(chosen.map { TagSuggestion.Item(it.uri, it.displayName, it.title, it.artist, it.folder) }, fillTitle, fillArtist)
    }
    val changes = plan.count { it.changed }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tools_tag_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.item), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(pluralStringResource(R.plurals.tools_picker_selected, selected.size, selected.size), style = MaterialTheme.typography.labelLarge)
                    Row {
                        TextButton(onClick = { selected = items.map { it.uri }.toSet() }) { Text(stringResource(R.string.tools_picker_all)) }
                        TextButton(onClick = { selected = emptySet() }, enabled = selected.isNotEmpty()) { Text(stringResource(R.string.tools_picker_none)) }
                    }
                }
                ToolPicker(items, selected, multi = true, onToggle = { uri -> selected = if (uri in selected) selected - uri else selected + uri }, query = query, onQuery = { query = it })
                ToggleRow(stringResource(R.string.tools_tag_fill_title), fillTitle, { fillTitle = it })
                ToggleRow(stringResource(R.string.tools_tag_fill_artist), fillArtist, { fillArtist = it })
                Text(pluralStringResource(R.plurals.tools_tag_preview, changes, changes), style = MaterialTheme.typography.labelLarge)
                plan.filter { it.changed }.take(4).forEach { proposal ->
                    Text(
                        "${proposal.displayName}\n→ ${proposal.title}" + if (proposal.artist.isNotBlank()) " · ${proposal.artist}" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(stringResource(R.string.tools_tag_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(plan) }, enabled = changes > 0) { Text(stringResource(R.string.tools_tag_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Satu lagu membuka pratinjau karaoke yang sudah ada (mulai, dengarkan, simpan). Beberapa lagu
 * masuk antrean massal: hasil dipublikasikan otomatis sebagai FLAC tanpa pratinjau per lagu.
 */
@Composable
private fun InstrumentalToolDialog(
    items: List<MediaItem>,
    onSingle: (MediaItem) -> Unit,
    onBatch: (List<MediaItem>) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val chosen = items.filter { it.uri in selected }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tools_karaoke_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.item), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(pluralStringResource(R.plurals.tools_picker_selected, selected.size, selected.size), style = MaterialTheme.typography.labelLarge)
                    Row {
                        TextButton(onClick = { selected = items.map { it.uri }.toSet() }) { Text(stringResource(R.string.tools_picker_all)) }
                        TextButton(onClick = { selected = emptySet() }, enabled = selected.isNotEmpty()) { Text(stringResource(R.string.tools_picker_none)) }
                    }
                }
                ToolPicker(items, selected, multi = true, onToggle = { uri -> selected = if (uri in selected) selected - uri else selected + uri }, query = query, onQuery = { query = it })
                Text(stringResource(R.string.tools_karaoke_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.row)) {
                if (chosen.size > 1) {
                    TextButton(onClick = { onBatch(chosen) }) { Text(stringResource(R.string.tools_karaoke_batch)) }
                } else {
                    TextButton(onClick = { chosen.singleOrNull()?.let(onSingle) }, enabled = chosen.size == 1) {
                        Text(stringResource(R.string.tools_karaoke_single))
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Ketuk-waktu memakai engine player yang sama. Timestamp diambil dari posisi saat diketuk, jadi
 * pengguna mendengar sendiri hasilnya; tidak ada interpolasi atau pergeseran lirik lain.
 */
@Composable
private fun TapSyncDialog(
    item: MediaItem,
    library: MediaLibraryViewModel,
    player: PlayerViewModel,
    onDismiss: () -> Unit,
) {
    val playback by player.state.collectAsStateWithLifecycle()
    val busy by library.busy.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var lines by remember(item.uri) { mutableStateOf<List<String>?>(null) }
    var error by remember(item.uri) { mutableStateOf<String?>(null) }
    var warning by remember(item.uri) { mutableStateOf<String?>(null) }
    var taps by remember(item.uri) { mutableStateOf(listOf<Long>()) }
    val playingThis = playback.track?.uri == item.location

    LaunchedEffect(item.uri) {
        try {
            lines = withContext(Dispatchers.IO) { TapSync.lines(library.lyricText(item)) }
        } catch (failure: Exception) {
            error = failure.message ?: "Lirik tidak dapat dibaca."
        }
    }

    val all = lines.orEmpty()
    val complete = all.isNotEmpty() && taps.size == all.size
    fun tap() {
        if (!playingThis) { warning = "Putar lagu ini dulu, lalu ketuk mengikuti lirik."; return }
        val rejection = TapSync.rejection(taps, playback.positionMs)
        if (rejection != null) { warning = rejection; return }
        warning = null
        taps = taps + playback.positionMs
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tools_tap_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.item), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(item.title.ifBlank { item.displayName }, style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.tools_tap_position, Format.duration(playback.positionMs / 1000)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!playingThis) ActionButton(
                    stringResource(R.string.tools_tap_play),
                    {
                        player.play(PlayerViewModel.Track(item.location, item.title.ifBlank { item.displayName }, item.artist, item.coverPath, item.lyricUri))
                    },
                    icon = Icons.Outlined.Lyrics, fillWidth = true,
                )
                when {
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    lines == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    all.isEmpty() -> Text(stringResource(R.string.tools_lyrics_empty), style = MaterialTheme.typography.bodySmall)
                    else -> {
                        Text(pluralStringResource(R.plurals.tools_tap_line, all.size, (taps.size + 1).coerceAtMost(all.size), all.size), style = MaterialTheme.typography.labelLarge)
                        if (complete) Text(stringResource(R.string.tools_tap_done), style = MaterialTheme.typography.bodySmall)
                        else {
                            Text(stringResource(R.string.tools_tap_next), style = MaterialTheme.typography.bodySmall)
                            Text(all[taps.size], style = MaterialTheme.typography.titleMedium)
                        }
                        Text(stringResource(R.string.tools_tap_wait), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!complete) ActionButton(stringResource(R.string.tools_tap_button), ::tap, icon = Icons.Outlined.Lyrics, emphasis = ActionEmphasis.Primary, fillWidth = true)
                        warning?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.row)) {
                            ActionButton(stringResource(R.string.tools_tap_undo), { taps = taps.dropLast(1) }, enabled = taps.isNotEmpty(), emphasis = ActionEmphasis.Quiet)
                            ActionButton(stringResource(R.string.tools_tap_reset), { taps = emptyList(); warning = null }, enabled = taps.isNotEmpty(), emphasis = ActionEmphasis.Quiet)
                        }
                        taps.takeLast(3).forEachIndexed { index, value ->
                            val line = all.getOrNull(taps.size - taps.takeLast(3).size + index).orEmpty()
                            Text("[${TapSync.stamp(value)}] $line", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val text = try { TapSync.build(all, taps) } catch (failure: Exception) { error = failure.message; return@TextButton }
                    scope.launch {
                        library.publishLyricsText(item, text) { failure -> if (failure != null) error = failure else onDismiss() }
                    }
                },
                enabled = complete && !busy,
            ) { Text(stringResource(R.string.tools_tap_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
