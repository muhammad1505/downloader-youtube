package id.local.tubeflow.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
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
            ActionButton(
                stringResource(if (editing == null) R.string.library_create_playlist else R.string.library_save_playlist_name),
                {
                    val old = editing
                    if (old == null) model.createPlaylist(name) else model.renamePlaylist(old.id, name)
                    name = ""; editing = null
                },
                icon = if (editing == null) Icons.Outlined.PlaylistAdd else Icons.Outlined.Save,
                emphasis = ActionEmphasis.Primary,
                enabled = name.isNotBlank() && !busy,
            )
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            playlists.filter { it.id != PersonalLibrary.FAVORITES }.forEach { entry ->
                Text(entry.name, style = MaterialTheme.typography.titleSmall)
                ScrollRow {
                    ActionButton(stringResource(R.string.library_rename_playlist), { editing = entry; name = entry.name }, icon = Icons.Outlined.Edit, emphasis = ActionEmphasis.Quiet, enabled = !busy)
                    ActionButton(stringResource(R.string.library_delete_playlist), { deleting = entry }, icon = Icons.Outlined.Delete, emphasis = ActionEmphasis.Quiet, destructive = true, enabled = !busy)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.karaoke_close)) } })
    deleting?.let { entry -> ConfirmDialog(
        title = stringResource(R.string.library_delete_playlist),
        text = stringResource(R.string.library_delete_playlist_help, entry.name),
        confirmLabel = stringResource(R.string.library_delete_playlist),
        onConfirm = {
            model.deletePlaylist(entry.id)
            if (editing?.id == entry.id) { editing = null; name = "" }
            deleting = null
        },
        onDismiss = { deleting = null },
        destructive = true,
    ) }
}
