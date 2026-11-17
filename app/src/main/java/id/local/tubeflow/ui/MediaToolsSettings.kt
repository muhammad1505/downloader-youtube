package id.local.tubeflow.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    Panel(title = stringResource(R.string.karaoke_storage_title)) {
        fun label(size: PrivateStorage.Size?): String = size?.let { Format.bytes(it.bytes) + if (it.complete) "" else " · parsial" } ?: "…"
        Text(stringResource(R.string.karaoke_storage_model, label(usage?.model)))
        Text(stringResource(if (usage?.verified == true) R.string.karaoke_model_verified else R.string.karaoke_model_unverified), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.karaoke_storage_preview, label(usage?.preview)))
        Text(stringResource(R.string.karaoke_storage_temporary, label(usage?.temporary)))
        Text(stringResource(R.string.karaoke_storage_help), style = MaterialTheme.typography.bodySmall)
        if (storageBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        ActionButton(stringResource(R.string.media_refresh), storage::refresh, icon = Icons.Outlined.Refresh, emphasis = ActionEmphasis.Quiet, enabled = !storageBusy)
        ActionButton(stringResource(R.string.karaoke_discard), { discardPreview = true }, enabled = !storageBusy && !karaokeBusy, destructive = true, fillWidth = true)
        ActionButton(
            stringResource(R.string.karaoke_delete_model),
            { deleteModel = true },
            enabled = !storageBusy && !karaokeBusy && ((usage?.model?.bytes ?: 0) > 0 || (usage?.modelFragments ?: 0) > 0),
            destructive = true,
            fillWidth = true,
        )
        storageMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    Panel(title = stringResource(R.string.backup_title)) {
        Text(stringResource(R.string.backup_help), style = MaterialTheme.typography.bodySmall)
        if (backupBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        ActionButton(
            stringResource(R.string.backup_export),
            { export.launch("TubeFlow-backup-${java.time.LocalDate.now()}.json") },
            icon = Icons.Outlined.FileUpload,
            enabled = !backupBusy,
            fillWidth = true,
        )
        ActionButton(
            stringResource(R.string.backup_import),
            { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            icon = Icons.Outlined.FileDownload,
            enabled = !backupBusy,
            fillWidth = true,
        )
        backupMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    if (deleteModel) ConfirmDialog(
        title = stringResource(R.string.karaoke_delete_model),
        text = stringResource(R.string.karaoke_delete_model_help),
        confirmLabel = stringResource(R.string.karaoke_delete_model),
        onConfirm = { deleteModel = false; storage.deleteModel() },
        onDismiss = { deleteModel = false },
        destructive = true,
    )
    if (discardPreview) ConfirmDialog(
        title = stringResource(R.string.karaoke_discard),
        text = stringResource(R.string.karaoke_discard_description),
        confirmLabel = stringResource(R.string.karaoke_discard_confirm),
        onConfirm = { discardPreview = false; karaoke.clear(player::discardComparison); karaoke.openCurrent() },
        onDismiss = { discardPreview = false },
        destructive = true,
    )
    plan?.let { current ->
        var settings by remember(current) { mutableStateOf(true) }
        var playlists by remember(current) { mutableStateOf(true) }
        var metadata by remember(current) { mutableStateOf(false) }
        var lyrics by remember(current) { mutableStateOf(true) }
        var replace by remember(current) { mutableStateOf(false) }
        AlertDialog(onDismissRequest = backup::dismiss, title = { DialogTitle(stringResource(R.string.backup_restore_title), icon = Icons.Outlined.SettingsBackupRestore) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(pluralStringResource(R.plurals.backup_summary, current.matches.size, current.matches.size, current.missing, current.conflicts))
                Text(stringResource(R.string.backup_restore_help), style = MaterialTheme.typography.bodySmall)
                CheckRow(stringResource(R.string.backup_settings), settings, { settings = it }, enabled = !backupBusy)
                CheckRow(stringResource(R.string.backup_playlists), playlists, { playlists = it }, enabled = !backupBusy)
                CheckRow(stringResource(R.string.backup_metadata), metadata, { metadata = it }, enabled = !backupBusy)
                CheckRow(stringResource(R.string.backup_lyrics), lyrics, { lyrics = it }, enabled = !backupBusy)
                if (lyrics) CheckRow(stringResource(R.string.backup_replace_lyrics), replace, { replace = it }, enabled = !backupBusy)
                if (backupBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                backupMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }, confirmButton = { TextButton(onClick = { backup.restore(MediaBackup.Options(settings, playlists, metadata, lyrics, replace)) }, enabled = !backupBusy && (settings || playlists || metadata || lyrics)) { Text(stringResource(R.string.backup_restore)) } },
            dismissButton = { TextButton(onClick = backup::dismiss, enabled = !backupBusy) { Text(stringResource(R.string.action_cancel)) } })
    }
}
