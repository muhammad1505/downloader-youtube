package id.local.tubeflow.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import id.local.tubeflow.R
import id.local.tubeflow.core.KaraokeOutput
import id.local.tubeflow.core.Format
import id.local.tubeflow.core.KaraokeInterval
import id.local.tubeflow.core.KaraokeProcessPolicy
import id.local.tubeflow.core.KaraokeViewModel
import id.local.tubeflow.core.PlayerViewModel

@Composable
fun KaraokeDialog(model: KaraokeViewModel, player: PlayerViewModel, notifications: () -> Unit) {
    val open by model.dialogOpen.collectAsStateWithLifecycle()
    if (!open) return
    val track by model.selected.collectAsStateWithLifecycle()
    val requestId by model.requestId.collectAsStateWithLifecycle()
    val observed by model.info.collectAsStateWithLifecycle()
    val work = observed?.takeIf { it.id == requestId }
    val source by model.source.collectAsStateWithLifecycle()
    val comparing by player.comparing.collectAsStateWithLifecycle()
    val comparisonPlayback by remember(player) { player.state.map { it.track?.uri to it.playing }.distinctUntilChanged() }.collectAsStateWithLifecycle(initialValue = null to false)
    val playingUri = comparisonPlayback.first
    val busy by model.busy.collectAsStateWithLifecycle()
    val stopping by model.stopping.collectAsStateWithLifecycle()
    val cleanupPending by model.cleanupPending.collectAsStateWithLifecycle()
    val actionsEnabled = !busy && !cleanupPending
    val sample by model.sample.collectAsStateWithLifecycle()
    val startText by model.startText.collectAsStateWithLifecycle()
    val position = KaraokeInterval.parseStart(startText)
    val message by model.message.collectAsStateWithLifecycle()
    val saved by model.saved.collectAsStateWithLifecycle()
    val active = work?.state?.isFinished == false
    val ready = work?.state == WorkInfo.State.SUCCEEDED
    val resultSample = work?.outputData?.getBoolean("sample", false) ?: false
    val beginning = work?.outputData?.getLong("startMs", 0) ?: 0
    val length = work?.outputData?.getLong("durationMs", 0) ?: 0
    val matches = ready && KaraokeProcessPolicy.matchesSelection(source?.uri, track?.uri, resultSample, sample, beginning, position)
    var format by remember(requestId) { mutableStateOf(KaraokeOutput.FLAC) }
    var confirmSampleSave by remember(requestId, track?.uri) { mutableStateOf(false) }
    var confirmDiscard by remember(requestId, track?.uri) { mutableStateOf(false) }
    val discardPlayback: (String) -> Unit = player::discardComparison
    val startProcessing = { notifications(); model.start {
        if (comparing) { player.finishComparison(); player.pause() }
        else if (model.isPrivatePreview(player.state.value.track?.uri)) player.stop() else player.pause()
    } }

    AlertDialog(onDismissRequest = model::close, title = { Text(stringResource(R.string.karaoke_title)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(track?.title ?: stringResource(R.string.karaoke_source_loading), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.karaoke_local_description), style = MaterialTheme.typography.bodySmall)
            if (stopping) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.karaoke_stopping))
            } else if (active) {
                if (source?.uri != track?.uri) Text(stringResource(R.string.karaoke_other_running, source?.title ?: stringResource(R.string.karaoke_source_loading)), style = MaterialTheme.typography.bodySmall)
                val percent = work.progress.getInt("percent", -1)
                Text(work.progress.getString("phase") ?: stringResource(R.string.karaoke_waiting))
                if (percent >= 0) {
                    LinearProgressIndicator(progress = { percent.coerceIn(0,100) / 100f }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.karaoke_percent, percent.coerceIn(0,100)))
                } else LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedButton(onClick = { model.cancel(discardPlayback) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.karaoke_cancel)) }
            } else if (ready) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(if (matches) R.string.karaoke_preview_ready else R.string.karaoke_previous_result), style = MaterialTheme.typography.titleSmall)
                        Text(source?.title ?: stringResource(R.string.karaoke_source_loading), style = MaterialTheme.typography.bodySmall)
                        Text(if (resultSample) stringResource(R.string.karaoke_sample_range, KaraokeInterval.formatStart(beginning), KaraokeInterval.formatStart(beginning + length)) else stringResource(R.string.karaoke_full_ready), style = MaterialTheme.typography.bodySmall)
                        if (matches) {
                            Text(stringResource(R.string.karaoke_compare_help), style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = comparing && playingUri == source?.uri, onClick = { model.compare(player, false) }, enabled = actionsEnabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp), label = { Text(stringResource(R.string.karaoke_compare_original)) })
                                FilterChip(selected = comparing && playingUri == work.outputData.getString("path")?.let { android.net.Uri.fromFile(java.io.File(it)).toString() }, onClick = { model.compare(player, true) }, enabled = actionsEnabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp), label = { Text(stringResource(R.string.karaoke_compare_instrumental)) })
                            }
                            if (comparing) {
                                Button(onClick = { player.toggle() }, enabled = actionsEnabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (comparisonPlayback.second) R.string.karaoke_compare_pause else R.string.karaoke_compare_play)) }
                                TextButton(onClick = { player.finishComparison() }, enabled = actionsEnabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.karaoke_compare_done)) }
                            }
                            if (saved == null) {
                                KaraokeOutput.entries.forEach { option ->
                                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = format == option, enabled = actionsEnabled, role = Role.RadioButton, onValueChange = { format = option }), verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(format == option, null, enabled = actionsEnabled)
                                        Text(stringResource(option.labelRes), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Text(stringResource(R.string.karaoke_output_estimate, if (format == KaraokeOutput.FLAC && work.outputData.getLong("resultBytes", 0) <= 0) stringResource(R.string.size_unavailable) else Format.bytes(format.estimate(length, work.outputData.getLong("resultBytes", 0)))), style = MaterialTheme.typography.bodySmall)
                            }
                            if (resultSample) {
                                Button(onClick = { model.sample(false); startProcessing() }, enabled = actionsEnabled, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.karaoke_make_full)) }
                                if (saved == null) Text(stringResource(R.string.karaoke_sample_private), style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(onClick = { if (resultSample) confirmSampleSave = true else model.save(work.id, format) }, enabled = actionsEnabled && saved == null, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(if (saved != null) R.string.karaoke_saved else if (resultSample) R.string.karaoke_save_sample else R.string.karaoke_save_full))
                            }
                        } else {
                            Text(stringResource(R.string.karaoke_result_mismatch), style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = model::openCurrent, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.karaoke_open_previous)) }
                        }
                        TextButton(onClick = { confirmDiscard = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.karaoke_discard)) }
                    }
                }
            } else if (work?.state == WorkInfo.State.FAILED) {
                Text(work.outputData.getString("error") ?: stringResource(R.string.karaoke_failed), color = MaterialTheme.colorScheme.error)
            } else if (work?.state == WorkInfo.State.CANCELLED) {
                Text(stringResource(R.string.karaoke_cancelled))
            } else if (requestId != null && !busy) {
                Text(stringResource(R.string.karaoke_status_missing), style = MaterialTheme.typography.bodySmall)
            }
            if (!active && !stopping && requestId != null && !ready) {
                TextButton(onClick = { confirmDiscard = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.karaoke_discard)) }
            }
            if (!active && !stopping && track?.uri?.startsWith("content://") == true) {
                Text(stringResource(R.string.karaoke_limits), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = sample,
                    enabled = actionsEnabled, role = Role.Checkbox, onValueChange = model::sample), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = sample, onCheckedChange = null, enabled = actionsEnabled)
                    Text(stringResource(R.string.karaoke_sample_mode), Modifier.weight(1f))
                }
                if (sample) OutlinedTextField(value = startText, onValueChange = model::startText,
                    label = { Text(stringResource(R.string.karaoke_start_label)) },
                    supportingText = { Text(stringResource(if (position != null) R.string.karaoke_start_help else R.string.karaoke_start_invalid)) },
                    isError = position == null, singleLine = true, enabled = actionsEnabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done), modifier = Modifier.fillMaxWidth())
                if (!sample) Text(stringResource(R.string.karaoke_full_warning), style = MaterialTheme.typography.bodySmall)
                if (!matches) Button(onClick = startProcessing, enabled = actionsEnabled && (!sample || position != null), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (sample) R.string.karaoke_process_sample else R.string.karaoke_make_full))
                }
            }
            if (cleanupPending && !busy) Text(stringResource(R.string.karaoke_cleanup_pending), color = MaterialTheme.colorScheme.error)
            if (busy && !stopping) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(stringResource(R.string.karaoke_close_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = { TextButton(onClick = model::close) { Text(stringResource(R.string.karaoke_close)) } })

    if (confirmSampleSave && matches && resultSample) AlertDialog(onDismissRequest = { confirmSampleSave = false },
        title = { Text(stringResource(R.string.karaoke_save_sample_title)) },
        text = { Text(stringResource(R.string.karaoke_save_sample_description, KaraokeInterval.formatStart(beginning), KaraokeInterval.formatStart(beginning + length), format.extension.uppercase(java.util.Locale.ROOT))) },
        confirmButton = { TextButton(onClick = { confirmSampleSave = false; model.save(work.id, format) }, enabled = !busy) { Text(stringResource(R.string.karaoke_save_sample_confirm, format.extension.uppercase(java.util.Locale.ROOT))) } },
        dismissButton = { TextButton(onClick = { confirmSampleSave = false }) { Text(stringResource(R.string.karaoke_back)) } })
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false },
        title = { Text(stringResource(R.string.karaoke_discard)) }, text = { Text(stringResource(R.string.karaoke_discard_description)) },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; model.clear(discardPlayback) }, enabled = !busy) { Text(stringResource(R.string.karaoke_discard_confirm)) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.karaoke_back)) } })
}
