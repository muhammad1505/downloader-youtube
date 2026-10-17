package id.local.tubeflow.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.sleep_title)) }, text = {
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.sleep_help), style = MaterialTheme.typography.bodySmall)
            if (timer.remainingMs > 0) Text(stringResource(R.string.sleep_remaining, duration(timer.remainingMs)))
            if (timer.afterTrack) Text(stringResource(R.string.sleep_after_track))
            listOf(15, 30, 60).forEach { minutes ->
                OutlinedButton(onClick = { player.setSleepTimer(minutes); dismiss() }, modifier = Modifier.fillMaxWidth()) { Text(pluralStringResource(R.plurals.sleep_minutes, minutes, minutes)) }
            }
            OutlinedTextField(custom, { custom = it.take(3) }, label = { Text(stringResource(R.string.sleep_custom)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                isError = custom.isNotBlank() && minutes == null, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { minutes?.let { player.setSleepTimer(it) }; dismiss() }, enabled = minutes != null, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sleep_start)) }
            OutlinedButton(onClick = { player.setSleepTimer(null, afterTrack = true); dismiss() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sleep_after_track)) }
            TextButton(onClick = { player.setSleepTimer(null); dismiss() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sleep_cancel)) }
        }
    }, confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.karaoke_close)) } })
}
