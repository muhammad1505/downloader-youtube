package id.local.tubeflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.local.tubeflow.R
import id.local.tubeflow.core.EngineUpdateState
import id.local.tubeflow.core.EngineUpdater

/** In-app notice when a verified yt-dlp release is available; install stays in Settings › Media. */
@Composable
fun EngineUpdateBanner(onOpen: () -> Unit) {
    val state by EngineUpdater.state.collectAsStateWithLifecycle()
    val release = (state as? EngineUpdateState.Available)?.release ?: return
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.SystemUpdate, null, Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.engine_update_banner_title), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(R.string.engine_update_banner_text, release.version),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onOpen) { Text(stringResource(R.string.engine_update_banner_open)) }
        }
    }
}
