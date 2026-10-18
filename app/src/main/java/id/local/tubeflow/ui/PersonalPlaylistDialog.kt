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
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.local.tubeflow.R
import id.local.tubeflow.core.*

@Composable
fun PersonalPlaylistDialog(model: MediaLibraryViewModel, dismiss: () -> Unit) {
    val playlists by model.playlists.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val entries = playlists.filter { it.id != PersonalLibrary.FAVORITES }
    var editing by remember { mutableStateOf<PersonalPlaylist?>(null) }
    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<PersonalPlaylist?>(null) }
    val list = rememberLazyListState()
    val filtered = entries.filter { it.name.contains(query, ignoreCase = true) }
    LaunchedEffect(editing?.id) { if (editing != null) list.animateScrollToItem(0) }

    fun submit() {
        val result: (String?) -> Unit = { error ->
            formError = error
            if (error == null) { name = ""; editing = null }
        }
        val old = editing
        if (old == null) model.createPlaylist(name, result) else model.renamePlaylist(old.id, name, result)
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.library_manage_playlists)) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), state = list,
                verticalArrangement = Arrangement.spacedBy(UiSpacing.item)) {
                item(key = "form") {
                    Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.row)) {
                        Text(stringResource(R.string.library_playlist_scope), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        editing?.let { Text(stringResource(R.string.playlist_editing, it.name), style = MaterialTheme.typography.titleSmall) }
                        OutlinedTextField(name, { name = it.take(80); formError = null },
                            label = { Text(stringResource(R.string.library_playlist_name)) },
                            enabled = !busy, isError = formError != null, singleLine = true,
                            modifier = Modifier.fillMaxWidth())
                        formError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        ActionButton(
                            stringResource(if (editing == null) R.string.library_create_playlist else R.string.library_save_playlist_name),
                            ::submit, icon = if (editing == null) Icons.Outlined.PlaylistAdd else Icons.Outlined.Save,
                            emphasis = ActionEmphasis.Primary, enabled = name.isNotBlank() && !busy, fillWidth = true)
                        if (editing != null) ActionButton(stringResource(R.string.playlist_cancel_edit),
                            { editing = null; name = ""; formError = null }, emphasis = ActionEmphasis.Quiet, enabled = !busy)
                        HorizontalDivider(Modifier.padding(top = UiSpacing.row))
                        Text(pluralStringResource(R.plurals.playlist_count, entries.size, entries.size),
                            style = MaterialTheme.typography.titleSmall)
                        if (entries.isNotEmpty()) OutlinedTextField(query, { query = it }, singleLine = true,
                            label = { Text(stringResource(R.string.playlist_search)) },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) {
                                Icon(Icons.Outlined.Close, stringResource(R.string.playlist_clear_search))
                            } }, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (filtered.isEmpty()) item(key = "empty") {
                    Text(stringResource(if (entries.isEmpty()) R.string.playlist_none else R.string.playlist_no_results),
                        Modifier.padding(vertical = UiSpacing.item), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(filtered, key = { it.id }) { entry ->
                    Card(Modifier.fillMaxWidth(), colors = mediaCardColors(MaterialTheme.colorScheme.primary)) {
                        Row(Modifier.fillMaxWidth().padding(UiSpacing.item),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(UiSpacing.row)) {
                            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Outlined.QueueMusic, null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                            Text(entry.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row {
                                IconAction(Icons.Outlined.Edit, stringResource(R.string.playlist_rename_action, entry.name),
                                    { editing = entry; name = entry.name; formError = null },
                                    enabled = !busy)
                                IconAction(Icons.Outlined.Delete, stringResource(R.string.playlist_delete_action, entry.name),
                                    { deleting = entry }, destructive = true, enabled = !busy)
                            }
                        }
                    }
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
                if (error == null && editing?.id == entry.id) { editing = null; name = "" }
            }
            deleting = null
        },
        onDismiss = { deleting = null }, destructive = true,
    ) }
}
