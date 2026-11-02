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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.local.tubeflow.R
import id.local.tubeflow.core.PlayerViewModel

@Composable
fun SleepTimerDialog(player: PlayerViewModel, dismiss: () -> Unit) {
    val timer by player.sleep.collectAsStateWithLifecycle()
    var custom by remember { mutableStateOf("") }
    val minutes = custom.toIntOrNull()?.takeIf { it in 1..180 }
    AlertDialog(onDismissRequest = dismiss, title = { DialogTitle(stringResource(R.string.sleep_title), icon = Icons.Outlined.Bedtime) }, text = {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.sleep_help), style = MaterialTheme.typography.bodySmall)
            if (timer.remainingMs > 0) Text(stringResource(R.string.sleep_remaining, duration(timer.remainingMs)))
            if (timer.afterTrack) Text(stringResource(R.string.sleep_after_track))
            listOf(15, 30, 60).forEach { minutes ->
                ActionButton(pluralStringResource(R.plurals.sleep_minutes, minutes, minutes), { player.setSleepTimer(minutes); dismiss() }, icon = Icons.Outlined.Timer, fillWidth = true)
            }
            OutlinedTextField(custom, { custom = it.take(3) }, label = { Text(stringResource(R.string.sleep_custom)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                isError = custom.isNotBlank() && minutes == null, modifier = Modifier.fillMaxWidth())
            ActionButton(stringResource(R.string.sleep_start), { minutes?.let { player.setSleepTimer(it) }; dismiss() }, icon = Icons.Outlined.PlayArrow, emphasis = ActionEmphasis.Primary, enabled = minutes != null, fillWidth = true)
            ActionButton(stringResource(R.string.sleep_after_track), { player.setSleepTimer(null, afterTrack = true); dismiss() }, icon = Icons.Outlined.SkipNext, fillWidth = true)
            ActionButton(stringResource(R.string.sleep_cancel), { player.setSleepTimer(null); dismiss() }, icon = Icons.Outlined.TimerOff, emphasis = ActionEmphasis.Quiet, destructive = true, fillWidth = true)
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.karaoke_close)) } })
}
