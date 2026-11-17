package id.local.tubeflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import id.local.tubeflow.R
import id.local.tubeflow.core.ReleaseNotes

/**
 * "Apa yang baru": daftar sorotan rilis berjalan. Muncul sekali per versi setelah update dan bisa
 * dibuka ulang dari Pengaturan → Tentang.
 */
@Composable
fun ReleaseNotesDialog(notes: ReleaseNotes, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle(stringResource(R.string.whats_new_title, notes.versionName), icon = Icons.Outlined.NewReleases) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.item),
            ) {
                notes.highlights.forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.row), verticalAlignment = Alignment.Top) {
                        Text("•", style = MaterialTheme.typography.bodyMedium)
                        Text(line, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    stringResource(R.string.whats_new_footer),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = UiSpacing.row),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.whats_new_done)) } },
    )
}
