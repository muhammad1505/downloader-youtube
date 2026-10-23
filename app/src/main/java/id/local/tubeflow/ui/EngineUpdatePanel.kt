package id.local.tubeflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.local.tubeflow.R
import id.local.tubeflow.core.EngineSource
import id.local.tubeflow.core.EngineUpdateState
import id.local.tubeflow.core.EngineUpdater
import id.local.tubeflow.core.Format

@Composable
fun EngineUpdatePanel(blockedReason: String? = null) {
    val context = LocalContext.current
    val state by EngineUpdater.state.collectAsStateWithLifecycle()
    var installed by remember { mutableStateOf(runCatching { EngineUpdater.installed(context) }.getOrNull()) }
    var confirmUpdate by rememberSaveable { mutableStateOf(false) }
    var confirmRollback by rememberSaveable { mutableStateOf(false) }
    val available = (state as? EngineUpdateState.Available)?.release
    val busy = state is EngineUpdateState.Checking || state is EngineUpdateState.Downloading ||
        state is EngineUpdateState.Verifying || state is EngineUpdateState.Installing

    LaunchedEffect(state) {
        if (state is EngineUpdateState.Updated || state is EngineUpdateState.RolledBack) {
            installed = runCatching { EngineUpdater.installed(context) }.getOrNull()
        }
    }

    if (confirmUpdate && available != null) {
        AlertDialog(
            onDismissRequest = { confirmUpdate = false },
            title = { Text(stringResource(R.string.engine_update_title)) },
            text = { Text(stringResource(R.string.engine_update_warning, available.version)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUpdate = false
                    EngineUpdater.install(context, available, blockedReason)
                }) { Text(stringResource(R.string.engine_update_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUpdate = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    if (confirmRollback) {
        AlertDialog(
            onDismissRequest = { confirmRollback = false },
            title = { Text(stringResource(R.string.engine_rollback_title)) },
            text = { Text(stringResource(R.string.engine_rollback_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRollback = false
                    EngineUpdater.rollback(context, blockedReason)
                }) { Text(stringResource(R.string.engine_rollback_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRollback = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    Panel(title = stringResource(R.string.engine_media_title)) {
        val current = installed
        if (current != null) {
            Text(
                stringResource(
                    if (current.source == EngineSource.UPDATED) R.string.engine_installed_updated
                    else R.string.engine_installed_bundled,
                    current.version,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(
                    if (current.source == EngineSource.UPDATED) R.string.engine_updated_description
                    else R.string.engine_bundled_description,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(
            Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (val update = state) {
                EngineUpdateState.Idle -> Unit
                EngineUpdateState.Checking -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.engine_checking), style = MaterialTheme.typography.bodySmall)
                }
                is EngineUpdateState.UpToDate -> Text(
                    stringResource(R.string.engine_up_to_date, update.version),
                    style = MaterialTheme.typography.bodySmall,
                )
                is EngineUpdateState.Available -> Text(
                    stringResource(R.string.engine_available, update.release.version, Format.bytes(update.release.size)),
                    style = MaterialTheme.typography.bodySmall,
                )
                is EngineUpdateState.Downloading -> {
                    val fraction = if (update.release.size > 0) (update.received.toFloat() / update.release.size).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.engine_downloading, (fraction * 100).toInt()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ActionButton(
                        stringResource(R.string.engine_cancel),
                        { EngineUpdater.cancel() },
                        emphasis = ActionEmphasis.Quiet,
                    )
                }
                is EngineUpdateState.Verifying -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.engine_verifying), style = MaterialTheme.typography.bodySmall)
                }
                is EngineUpdateState.Installing -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.engine_installing), style = MaterialTheme.typography.bodySmall)
                }
                is EngineUpdateState.Updated -> Text(
                    stringResource(R.string.engine_updated, update.version),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                is EngineUpdateState.RolledBack -> Text(
                    stringResource(R.string.engine_rolled_back, update.version),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                is EngineUpdateState.Failed -> Text(
                    update.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        if (available != null) {
            ActionButton(
                stringResource(R.string.engine_download, available.version),
                { confirmUpdate = true },
                icon = Icons.Outlined.Download,
                enabled = blockedReason == null && !busy,
                emphasis = ActionEmphasis.Primary,
                fillWidth = true,
            )
        }
        ActionButton(
            stringResource(if (state is EngineUpdateState.Checking) R.string.engine_checking else R.string.engine_check),
            { EngineUpdater.check(context) },
            icon = Icons.Outlined.Refresh,
            enabled = !busy,
            fillWidth = true,
        )
        if (installed?.source == EngineSource.UPDATED) {
            ActionButton(
                stringResource(R.string.engine_rollback),
                { confirmRollback = true },
                icon = Icons.Outlined.Restore,
                enabled = blockedReason == null && !busy,
                fillWidth = true,
            )
        }
        if (blockedReason != null) {
            Text(
                blockedReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(R.string.engine_security_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
