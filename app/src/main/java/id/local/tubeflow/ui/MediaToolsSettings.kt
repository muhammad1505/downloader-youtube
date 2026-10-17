package id.local.tubeflow.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import id.local.tubeflow.R
import id.local.tubeflow.core.*

@Composable
fun MediaToolsSettings(karaoke: KaraokeViewModel, player: PlayerViewModel) {
    val storage: KaraokeStorageViewModel = viewModel()
    val backup: MediaBackupViewModel = viewModel()
    val usage by storage.usage.collectAsStateWithLifecycle()
    val storageBusy by storage.busy.collectAsStateWithLifecycle()
    val storageMessage by storage.message.collectAsStateWithLifecycle()
    val backupBusy by backup.busy.collectAsStateWithLifecycle()
    val backupMessage by backup.message.collectAsStateWithLifecycle()
    val plan by backup.plan.collectAsStateWithLifecycle()
    val karaokeBusy by karaoke.busy.collectAsStateWithLifecycle()
    var deleteModel by remember { mutableStateOf(false) }
    var discardPreview by remember { mutableStateOf(false) }
    LaunchedEffect(karaokeBusy) { if (!karaokeBusy) storage.refresh() }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(backup::export) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(backup::inspect) }
    SectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.karaoke_storage_title), style = MaterialTheme.typography.titleMedium)
            fun label(size: PrivateStorage.Size?): String = size?.let { Format.bytes(it.bytes) + if (it.complete) "" else " · parsial" } ?: "…"
            Text(stringResource(R.string.karaoke_storage_model, label(usage?.model)))
            Text(stringResource(if (usage?.verified == true) R.string.karaoke_model_verified else R.string.karaoke_model_unverified), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.karaoke_storage_preview, label(usage?.preview)))
            Text(stringResource(R.string.karaoke_storage_temporary, label(usage?.temporary)))
            Text(stringResource(R.string.karaoke_storage_help), style = MaterialTheme.typography.bodySmall)
            if (storageBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick = storage::refresh, enabled = !storageBusy) { Text(stringResource(R.string.media_refresh)) }
            OutlinedButton(onClick = { discardPreview = true }, enabled = !storageBusy && !karaokeBusy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.karaoke_discard)) }
            OutlinedButton(onClick = { deleteModel = true }, enabled = !storageBusy && !karaokeBusy && ((usage?.model?.bytes ?: 0) > 0 || (usage?.modelFragments ?: 0) > 0), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.karaoke_delete_model)) }
            storageMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
    SectionCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.backup_help), style = MaterialTheme.typography.bodySmall)
            if (backupBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = { export.launch("TubeFlow-backup-${java.time.LocalDate.now()}.json") }, enabled = !backupBusy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_export)) }
            OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !backupBusy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_import)) }
            backupMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (deleteModel) AlertDialog(onDismissRequest = { deleteModel = false }, title = { Text(stringResource(R.string.karaoke_delete_model)) },
        text = { Text(stringResource(R.string.karaoke_delete_model_help)) },
        confirmButton = { TextButton(onClick = { deleteModel = false; storage.deleteModel() }) { Text(stringResource(R.string.karaoke_delete_model)) } },
        dismissButton = { TextButton(onClick = { deleteModel = false }) { Text(stringResource(R.string.action_cancel)) } })
    if (discardPreview) AlertDialog(onDismissRequest = { discardPreview = false }, title = { Text(stringResource(R.string.karaoke_discard)) },
        text = { Text(stringResource(R.string.karaoke_discard_description)) },
        confirmButton = { TextButton(onClick = { discardPreview = false; karaoke.clear(player::discardComparison); karaoke.openCurrent() }) { Text(stringResource(R.string.karaoke_discard_confirm)) } },
        dismissButton = { TextButton(onClick = { discardPreview = false }) { Text(stringResource(R.string.action_cancel)) } })
    plan?.let { current ->
        var settings by remember(current) { mutableStateOf(true) }
        var playlists by remember(current) { mutableStateOf(true) }
        var metadata by remember(current) { mutableStateOf(false) }
        var lyrics by remember(current) { mutableStateOf(true) }
        var replace by remember(current) { mutableStateOf(false) }
        AlertDialog(onDismissRequest = backup::dismiss, title = { Text(stringResource(R.string.backup_restore_title)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(pluralStringResource(R.plurals.backup_summary, current.matches.size, current.matches.size, current.missing, current.conflicts))
                Text(stringResource(R.string.backup_restore_help), style = MaterialTheme.typography.bodySmall)
                BackupCheck(stringResource(R.string.backup_settings), settings, !backupBusy) { settings = it }
                BackupCheck(stringResource(R.string.backup_playlists), playlists, !backupBusy) { playlists = it }
                BackupCheck(stringResource(R.string.backup_metadata), metadata, !backupBusy) { metadata = it }
                BackupCheck(stringResource(R.string.backup_lyrics), lyrics, !backupBusy) { lyrics = it }
                if (lyrics) BackupCheck(stringResource(R.string.backup_replace_lyrics), replace, !backupBusy) { replace = it }
                if (backupBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                backupMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }, confirmButton = { TextButton(onClick = { backup.restore(MediaBackup.Options(settings, playlists, metadata, lyrics, replace)) }, enabled = !backupBusy && (settings || playlists || metadata || lyrics)) { Text(stringResource(R.string.backup_restore)) } },
            dismissButton = { TextButton(onClick = backup::dismiss, enabled = !backupBusy) { Text(stringResource(R.string.action_cancel)) } })
    }
}

@Composable
private fun BackupCheck(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange, enabled = enabled)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}
