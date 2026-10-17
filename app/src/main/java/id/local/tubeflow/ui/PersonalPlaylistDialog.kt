package id.local.tubeflow.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.local.tubeflow.R
import id.local.tubeflow.core.*

@Composable
fun PersonalPlaylistDialog(model: MediaLibraryViewModel, dismiss: () -> Unit) {
    val playlists by model.playlists.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<PersonalPlaylist?>(null) }
    var name by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<PersonalPlaylist?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.library_manage_playlists)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.library_playlist_scope), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(R.string.library_playlist_name)) }, singleLine = true)
            Button(onClick = { val old = editing; if (old == null) model.createPlaylist(name) else model.renamePlaylist(old.id, name); name = ""; editing = null }, enabled = name.isNotBlank() && !busy) {
                Text(stringResource(if (editing == null) R.string.library_create_playlist else R.string.library_save_playlist_name))
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            playlists.filter { it.id != PersonalLibrary.FAVORITES }.forEach { entry ->
                Text(entry.name, style = MaterialTheme.typography.titleSmall)
                Row {
                    TextButton(onClick = { editing = entry; name = entry.name }, enabled = !busy) { Text(stringResource(R.string.library_rename_playlist)) }
                    TextButton(onClick = { deleting = entry }, enabled = !busy) { Text(stringResource(R.string.library_delete_playlist), color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.karaoke_close)) } })
    deleting?.let { entry -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.library_delete_playlist)) },
        text = { Text(stringResource(R.string.library_delete_playlist_help, entry.name)) },
        confirmButton = { TextButton(onClick = { model.deletePlaylist(entry.id); if (editing?.id == entry.id) { editing = null; name = "" }; deleting = null }) { Text(stringResource(R.string.library_delete_playlist)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } }) }
}
