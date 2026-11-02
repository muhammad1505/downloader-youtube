package id.local.tubeflow.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.local.tubeflow.R
import id.local.tubeflow.core.*

/**
 * Kelola playlist: satu baris pembuat di atas, lalu daftar playlist dengan jumlah lagunya.
 * Nama dan aksi ikon mengikuti baris kartu Koleksi; pencarian baru muncul saat daftar panjang
 * agar tampilan tetap tenang untuk pengguna dengan sedikit playlist.
 */
@Composable
fun PersonalPlaylistDialog(model: MediaLibraryViewModel, dismiss: () -> Unit) {
    val playlists by model.playlists.collectAsStateWithLifecycle()
    val counts by model.playlistCounts.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val entries = playlists.filter { it.id != PersonalLibrary.FAVORITES }
    var editing by remember { mutableStateOf<PersonalPlaylist?>(null) }
    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<PersonalPlaylist?>(null) }
    val list = rememberLazyListState()
    val filtered = entries.filter { it.name.contains(query, ignoreCase = true) }
    val searchVisible = entries.size > 4 || query.isNotBlank()
    LaunchedEffect(editing?.id) { if (editing != null) list.animateScrollToItem(0) }

    fun submit() {
        val result: (String?) -> Unit = { error ->
            formError = error
            if (error == null) { name = ""; editing = null }
        }
        val old = editing
        if (old == null) model.createPlaylist(name, result) else model.renamePlaylist(old.id, name, result)
    }
    fun startEditing(entry: PersonalPlaylist) {
        editing = entry
        name = entry.name
        formError = null
    }
    fun cancelEditing() {
        editing = null
        name = ""
        formError = null
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = {
            DialogTitle(
                stringResource(R.string.library_manage_playlists),
                subtitle = pluralStringResource(R.plurals.playlist_count, entries.size, entries.size),
                icon = Icons.AutoMirrored.Outlined.QueueMusic,
            )
        },
        text = {
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 520.dp),
                state = list,
                verticalArrangement = Arrangement.spacedBy(UiSpacing.row),
            ) {
                item(key = "form") {
                    Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.row)) {
                        if (editing == null) {
                            AdaptivePair(
                                modifier = Modifier.padding(top = UiSpacing.row),
                                minInlineWidth = 360.dp,
                                leading = { available ->
                                    OutlinedTextField(
                                        name, { name = it.take(80); formError = null },
                                        label = { Text(stringResource(R.string.library_playlist_name)) },
                                        enabled = !busy, isError = formError != null, singleLine = true,
                                        modifier = available,
                                    )
                                },
                                trailing = {
                                    ActionButton(
                                        stringResource(R.string.library_create_playlist), ::submit,
                                        icon = Icons.Outlined.Add, emphasis = ActionEmphasis.Primary,
                                        enabled = name.isNotBlank() && !busy,
                                    )
                                },
                            )
                        } else {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.fillMaxWidth().padding(top = UiSpacing.row),
                            ) {
                                Column(
                                    Modifier.padding(UiSpacing.item),
                                    verticalArrangement = Arrangement.spacedBy(UiSpacing.row),
                                ) {
                                    Text(
                                        stringResource(R.string.playlist_editing, editing?.name.orEmpty()),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    OutlinedTextField(
                                        name, { name = it.take(80); formError = null },
                                        label = { Text(stringResource(R.string.library_playlist_name)) },
                                        enabled = !busy, isError = formError != null, singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.row)) {
                                        ActionButton(
                                            stringResource(R.string.library_save_playlist_name), ::submit,
                                            icon = Icons.Outlined.Save, emphasis = ActionEmphasis.Primary,
                                            enabled = name.isNotBlank() && !busy,
                                        )
                                        ActionButton(
                                            stringResource(R.string.playlist_cancel_edit), ::cancelEditing,
                                            emphasis = ActionEmphasis.Quiet, enabled = !busy,
                                        )
                                    }
                                }
                            }
                        }
                        formError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            stringResource(R.string.library_playlist_scope),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (searchVisible) item(key = "search") {
                    OutlinedTextField(
                        query, { query = it }, singleLine = true,
                        label = { Text(stringResource(R.string.playlist_search)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = {
                            if (query.isNotBlank()) IconButton(onClick = { query = "" }) {
                                Icon(Icons.Outlined.Close, stringResource(R.string.playlist_clear_search))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (entries.isEmpty()) item(key = "empty") {
                    Text(
                        stringResource(R.string.playlist_none),
                        Modifier.padding(vertical = UiSpacing.item),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (filtered.isEmpty()) item(key = "no-results") {
                    Text(
                        stringResource(R.string.playlist_no_results),
                        Modifier.padding(vertical = UiSpacing.item),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(filtered, key = { it.id }) { entry ->
                    PlaylistRow(
                        entry = entry,
                        count = counts[entry.id] ?: 0,
                        enabled = !busy,
                        onEdit = { startEditing(entry) },
                        onDelete = { deleting = entry },
                    )
                }
            }
        },
        confirmButton = { ActionButton(stringResource(R.string.karaoke_close), dismiss, emphasis = ActionEmphasis.Quiet) },
    )
    deleting?.let { entry -> ConfirmDialog(
        title = stringResource(R.string.library_delete_playlist),
        text = stringResource(R.string.library_delete_playlist_help, entry.name),
        confirmLabel = stringResource(R.string.library_delete_playlist),
        onConfirm = {
            model.deletePlaylist(entry.id) { error ->
                formError = error
                if (error == null && editing?.id == entry.id) cancelEditing()
            }
            deleting = null
        },
        onDismiss = { deleting = null }, destructive = true,
    ) }
}

/** Baris playlist: sampul ikon tonal, nama, jumlah lagu, dan aksi Ubah/Hapus 48dp. */
@Composable
private fun PlaylistRow(
    entry: PersonalPlaylist,
    count: Int,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        AdaptivePair(
            Modifier.padding(start = UiSpacing.item, end = UiSpacing.row, top = UiSpacing.row, bottom = UiSpacing.row),
            // Lebar ambang lebih kecil dari bawaan: nama playlist dan dua aksi ikon masih nyaman
            // sebaris di layar 360dp, sehingga baris tidak terbelah tanpa alasan.
            minInlineWidth = 240.dp,
            leading = { available ->
                Row(available, verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(UiSpacing.item)) {
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Outlined.QueueMusic, null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            entry.name, style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            pluralStringResource(R.plurals.playlist_song_count, count, count),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            trailing = {
                Row {
                    IconAction(
                        Icons.Outlined.Edit, stringResource(R.string.playlist_rename_action, entry.name),
                        onEdit, enabled = enabled,
                    )
                    IconAction(
                        Icons.Outlined.Delete, stringResource(R.string.playlist_delete_action, entry.name),
                        onDelete, destructive = true, enabled = enabled,
                    )
                }
            },
        )
    }
}
