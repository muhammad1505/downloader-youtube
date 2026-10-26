package id.local.tubeflow.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import id.local.tubeflow.R
import id.local.tubeflow.core.PlayerViewModel
import id.local.tubeflow.core.VideoPlayback
import id.local.tubeflow.core.VideoRenderersFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun StatusClipPreview(raw: String, startMs: Long, endMs: Long) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var failed by remember(raw) { mutableStateOf(false) }
    var playing by remember(raw) { mutableStateOf(false) }
    var ready by remember(raw) { mutableStateOf(false) }
    var position by remember(raw) { mutableLongStateOf(0L) }
    var scrub by remember(startMs, endMs) { mutableStateOf<Float?>(null) }
    val duration = (endMs - startMs).coerceAtLeast(1L)
    val preview = remember(raw) {
        ExoPlayer.Builder(context, VideoRenderersFactory(context, DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON) { 0L }).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            setHandleAudioBecomingNoisy(true)
        }
    }
    LaunchedEffect(preview, startMs, endMs) {
        preview.pause()
        ready = false
        position = 0
        delay(180)
        failed = false
        preview.setMediaItem(MediaItem.Builder().setUri(raw.toUri()).setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build(),
        ).build())
        preview.prepare()
    }
    LaunchedEffect(preview, playing) {
        while (isActive && playing) {
            position = preview.currentPosition.coerceIn(0, duration)
            delay(200)
        }
    }
    DisposableEffect(preview, owner) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { failed = true; ready = false }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                ready = state == Player.STATE_READY || state == Player.STATE_ENDED
                if (state == Player.STATE_ENDED) position = duration
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady) PlayerViewModel.active?.pause()
            }
        }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) preview.pause() }
        preview.addListener(listener)
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            preview.removeListener(listener)
            preview.release()
        }
    }
    Column {
        Text(stringResource(R.string.clip_preview), style = MaterialTheme.typography.labelLarge)
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { PlayerView(it).apply { player = preview; useController = false } },
                update = { if (it.player !== preview) it.player = preview; it.keepScreenOn = playing },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize(),
            )
            if (!ready && !failed) CircularProgressIndicator(Modifier.size(24.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(enabled = ready && !failed, onClick = {
                if (playing) preview.pause() else {
                    if (preview.playbackState == Player.STATE_ENDED) preview.seekTo(0)
                    preview.play()
                }
            }) {
                Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    stringResource(if (playing) R.string.clip_preview_pause else R.string.clip_preview_play))
            }
            Text(VideoPlayback.timeLabel((scrub?.toLong() ?: position)), style = MaterialTheme.typography.labelSmall)
            Scrubber(
                value = scrub ?: position.toFloat(), duration = duration.toFloat(),
                onScrub = { scrub = it },
                onScrubFinished = { scrub?.let { preview.seekTo(it.toLong()); position = it.toLong() }; scrub = null },
                contentColor = MaterialTheme.colorScheme.primary,
                enabled = ready && !failed,
                touchHeight = 48.dp,
                contentDescription = stringResource(R.string.clip_preview_position),
                modifier = Modifier.weight(1f),
            )
            Text(VideoPlayback.timeLabel(duration), style = MaterialTheme.typography.labelSmall)
        }
        if (failed) Text(stringResource(R.string.clip_preview_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.clip_preview_help), style = MaterialTheme.typography.bodySmall)
    }
}
