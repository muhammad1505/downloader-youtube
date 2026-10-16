package id.local.tubeflow.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import id.local.tubeflow.R
import id.local.tubeflow.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FailureDetailsDialog(details: FailureDetails, dismiss: () -> Unit) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.failure_details)) },
        text = { SelectionContainer { Text(details.text(), Modifier.verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) } },
        confirmButton = { TextButton(onClick = {
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("TubeFlow", details.text()))
            copied = true
        }) { Text(stringResource(if (copied) R.string.failure_copied else R.string.failure_copy)) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.action_close)) } })
}

@Composable
fun JobFailureDetails(job: DownloadJob) {
    val context = LocalContext.current
    var open by remember(job.id, job.generation) { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text(stringResource(R.string.failure_details)) }
    if (open) {
        val details by produceState<FailureDetails?>(null, job.id, job.generation) {
            value = withContext(Dispatchers.IO) {
                FailureDetailsStore(context).get(job.id,job.generation) ?: FailureDetails.capture(
                    job.error.orEmpty(), job.phase, "Tidak tercatat (riwayat lama)", "Tidak tercatat")
            }
        }
        if (details != null) FailureDetailsDialog(requireNotNull(details)) { open = false }
        else AlertDialog(onDismissRequest = { open = false }, text = { CircularProgressIndicator() }, confirmButton = {})
    }
}
