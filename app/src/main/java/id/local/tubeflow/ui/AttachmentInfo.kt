package id.local.tubeflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import id.local.tubeflow.R
import id.local.tubeflow.core.MediaAttachments

/** Shared by history, library and playback queues; never infers availability from download options. */
@Composable
fun AttachmentInfo(raw: String, audio: Boolean, lyricUri: String? = null, sidecars: String? = null, revision: Any? = null) {
    val context = LocalContext.current
    var resume by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resume++
        onPauseOrDispose { }
    }
    val summary by produceState<MediaAttachments.Summary?>(null, raw, audio, lyricUri, sidecars, revision, resume) {
        value = null
        value = MediaAttachments.inspect(context.applicationContext, raw, audio, lyricUri, sidecars)
    }
    val info = summary
    val text = when {
        info == null -> stringResource(R.string.attachments_checking)
        audio && info.lyrics -> stringResource(R.string.lyrics_available)
        !audio && info.subtitles > 0 -> pluralStringResource(R.plurals.subtitles_available, info.subtitles, info.subtitles)
        !info.checked -> stringResource(R.string.attachments_unreadable)
        audio -> stringResource(R.string.lyrics_absent)
        else -> stringResource(R.string.subtitles_absent)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(if (audio) Icons.Outlined.Lyrics else Icons.Outlined.ClosedCaption, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
