package id.local.tubeflow.core

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Mesin pemutar video (Media3 ExoPlayer). Terpisah total dari [PlayerViewModel] yang tetap
 * menangani audio dengan MediaPlayer, sehingga kontrol, notifikasi, dan antrean lagu tidak berubah.
 *
 * ExoPlayer hidup selama layar video terbuka dan dilepas saat ditutup. Activity-scoped ViewModel
 * membuat pemutaran bertahan saat rotasi tanpa memindahkan audio ke background service.
 */
class VideoPlayerViewModel(application: Application) : AndroidViewModel(application) {
    data class VideoTrack(
        val groupIndex: Int,
        val trackIndex: Int,
        val type: Int,
        val label: String,
        val language: String,
        val selected: Boolean,
    )

    data class State(
        val entries: List<VideoEntry> = emptyList(),
        val index: Int = 0,
        val playing: Boolean = false,
        val buffering: Boolean = false,
        val ready: Boolean = false,
        val ended: Boolean = false,
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        val bufferedMs: Long = 0,
        val speed: Float = 1f,
        val aspect: VideoAspect = VideoAspect.FIT,
        val repeat: VideoRepeat = VideoRepeat.OFF,
        val error: String? = null,
        val resumeFromMs: Long = 0,
        val audioTracks: List<VideoTrack> = emptyList(),
        val textTracks: List<VideoTrack> = emptyList(),
        val subtitlesDisabled: Boolean = true,
        val videoWidth: Int = 0,
        val videoHeight: Int = 0,
    ) {
        val active: Boolean get() = entries.isNotEmpty()
        val entry: VideoEntry? get() = entries.getOrNull(index)
        val hasNext: Boolean get() = index < entries.lastIndex
        val hasPrevious: Boolean get() = index > 0
    }

    private val preferences = application.getSharedPreferences("video-fx", Context.MODE_PRIVATE)
    private val resume = VideoResumeStore(application)

    private val _state = MutableStateFlow(preferencesState())
    val state = _state.asStateFlow()

    private var player: ExoPlayer? = null
    private var currentUri: String? = null
    private var lastPositionMs = 0L
    private var lastDurationMs = 0L
    private var lastResumeSaveAt = 0L
    private var ticker: Job? = null

    /** Dibaca layar untuk dipasang ke PlayerView; null saat tidak ada video. */
    val exoPlayer: ExoPlayer? get() = player

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val current = _state.value
            _state.value = current.copy(playing = isPlaying)
            if (!isPlaying) saveNow()
        }

        override fun onIsLoadingChanged(isLoading: Boolean) {
            val current = _state.value
            if (current.error == null) _state.value = current.copy(buffering = isLoading)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val current = _state.value
            _state.value = when (playbackState) {
                Player.STATE_READY -> current.copy(
                    ready = true,
                    buffering = false,
                    ended = false,
                    error = null,
                    durationMs = player?.duration?.takeIf { it > 0 && it != C.TIME_UNSET } ?: current.durationMs,
                )
                Player.STATE_BUFFERING -> current.copy(buffering = current.error == null)
                Player.STATE_ENDED -> {
                    currentUri?.let(resume::clear)
                    current.copy(playing = false, buffering = false, ended = true)
                }
                else -> current.copy(buffering = false, ready = false)
            }
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            val nextUri = mediaItem?.localConfiguration?.uri?.toString()
            if (nextUri != null) {
                if (currentUri != null && currentUri != nextUri) {
                    resume.save(currentUri!!, lastPositionMs, lastDurationMs)
                }
                currentUri = nextUri
            }
            // Auto-lanjut dan lompat manual memakai posisi tersimpan video tujuan; repeat selalu dari awal.
            val resumeAt = when (reason) {
                Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
                Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ->
                    nextUri?.let(resume::position)?.takeIf { VideoPlayback.shouldResume(it, 0) }
                else -> null
            }
            if (resumeAt != null) player?.seekTo(resumeAt)
            val index = player?.currentMediaItemIndex ?: 0
            val position = when {
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> _state.value.positionMs
                resumeAt != null -> resumeAt
                else -> player?.currentPosition?.coerceAtLeast(0) ?: 0
            }
            lastPositionMs = position
            lastDurationMs = 0
            _state.value = _state.value.copy(
                index = index,
                positionMs = position,
                durationMs = 0,
                bufferedMs = 0,
                // Daftar awal yang baru dipasang bukan perpindahan video: pertahankan chip lanjut tonton.
                resumeFromMs = when {
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> _state.value.resumeFromMs
                    resumeAt != null -> resumeAt
                    else -> 0
                },
                ended = false,
            )
            refreshTracks()
        }

        override fun onTracksChanged(tracks: Tracks) {
            refreshTracks(tracks)
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            _state.value = _state.value.copy(
                videoWidth = videoSize.width,
                videoHeight = videoSize.height,
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            saveNow()
            _state.value = _state.value.copy(
                playing = false,
                buffering = false,
                ready = false,
                error = playbackError(error),
            )
        }
    }

    fun open(entries: List<VideoEntry>, startIndex: Int = 0) {
        if (entries.isEmpty()) return
        closeInternal(savePosition = true)
        val start = startIndex.coerceIn(entries.indices)
        val saved = resume.position(entries[start].uri)
        val startPosition = if (VideoPlayback.shouldResume(saved, 0)) saved ?: 0 else 0
        val preferences = preferencesState()
        _state.value = preferences.copy(
            entries = entries,
            index = start,
            positionMs = startPosition,
            bufferedMs = 0,
            buffering = true,
            resumeFromMs = startPosition,
        )
        currentUri = entries[start].uri
        lastPositionMs = startPosition
        lastDurationMs = 0
        try {
            val exo = ExoPlayer.Builder(getApplication()).build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true,
                )
                setHandleAudioBecomingNoisy(true)
                repeatMode = preferences.repeat.media3Value()
                setPlaybackSpeed(preferences.speed)
                addListener(listener)
            }
            player = exo
            exo.setMediaItems(entries.map(::mediaItem), start, startPosition)
            exo.prepare()
            exo.play()
            startTicker()
        } catch (failure: Exception) {
            player?.release()
            player = null
            _state.value = _state.value.copy(
                buffering = false,
                error = "Tidak dapat membuka video: ${failure.message ?: "berkas tidak valid"}",
            )
        }
    }

    fun togglePlay() {
        val exo = player ?: return
        when {
            exo.playbackState == Player.STATE_ENDED -> {
                exo.seekTo(0)
                exo.play()
            }
            exo.isPlaying -> exo.pause()
            else -> exo.play()
        }
    }

    fun pause() {
        player?.pause()
        saveNow()
    }

    fun seekTo(positionMs: Long) {
        val exo = player ?: return
        val target = VideoPlayback.seekTarget(positionMs, 0, _state.value.durationMs)
        exo.seekTo(target)
        _state.value = _state.value.copy(positionMs = target, ended = false)
    }

    fun seekBy(deltaMs: Long) {
        seekTo(VideoPlayback.seekTarget(_state.value.positionMs, deltaMs, _state.value.durationMs))
    }

    fun restart() {
        seekTo(0)
        player?.play()
    }

    fun next() {
        val exo = player ?: return
        if (exo.hasNextMediaItem()) {
            exo.seekToNextMediaItem()
            exo.play()
        }
    }

    fun previous() {
        val exo = player ?: return
        if (_state.value.positionMs > 5_000 || !exo.hasPreviousMediaItem()) {
            exo.seekTo(0)
            _state.value = _state.value.copy(positionMs = 0, ended = false)
        } else {
            exo.seekToPreviousMediaItem()
            exo.play()
        }
    }

    fun retry() {
        val exo = player ?: return
        _state.value = _state.value.copy(error = null, buffering = true)
        exo.prepare()
        exo.play()
    }

    fun setSpeed(value: Float) {
        val speed = value.coerceIn(0.25f, 3f)
        preferences.edit { putFloat(KEY_SPEED, speed) }
        player?.setPlaybackSpeed(speed)
        _state.value = _state.value.copy(speed = speed)
    }

    fun setAspect(value: VideoAspect) {
        preferences.edit { putString(KEY_ASPECT, value.name) }
        _state.value = _state.value.copy(aspect = value)
    }

    fun setRepeat(value: VideoRepeat) {
        preferences.edit { putString(KEY_REPEAT, value.name) }
        player?.repeatMode = value.media3Value()
        _state.value = _state.value.copy(repeat = value)
    }

    fun cycleRepeat() = setRepeat(_state.value.repeat.next())

    fun selectAudio(track: VideoTrack) = selectTrack(track)

    fun selectSubtitle(track: VideoTrack) = selectTrack(track)

    private fun selectTrack(track: VideoTrack) {
        val exo = player ?: return
        val group = exo.currentTracks.groups.getOrNull(track.groupIndex) ?: return
        if (group.type != track.type) return
        val parameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(track.type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex))
            .build()
        exo.trackSelectionParameters = parameters
    }

    fun automaticTracks(type: Int) {
        val exo = player ?: return
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .clearOverridesOfType(type)
            .build()
    }

    fun disableSubtitles() {
        val exo = player ?: return
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    fun close() {
        closeInternal(savePosition = true)
    }

    private fun closeInternal(savePosition: Boolean) {
        ticker?.cancel()
        ticker = null
        val exo = player
        if (savePosition && exo != null) saveNow()
        exo?.removeListener(listener)
        exo?.release()
        player = null
        currentUri = null
        lastPositionMs = 0
        lastDurationMs = 0
        _state.value = preferencesState()
    }

    private fun saveNow() {
        val exo = player ?: return
        val uri = currentUri ?: return
        val duration = exo.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: lastDurationMs
        resume.save(uri, exo.currentPosition.coerceAtLeast(0), duration)
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive && player != null) {
                syncProgress()
                delay(500)
            }
        }
    }

    private fun syncProgress() {
        val exo = player ?: return
        val duration = exo.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0
        val position = exo.currentPosition.coerceAtLeast(0)
        val buffered = exo.bufferedPosition.coerceAtLeast(0).let { if (duration > 0) it.coerceAtMost(duration) else it }
        lastPositionMs = position
        lastDurationMs = duration
        val current = _state.value
        if (current.positionMs != position || current.durationMs != duration || current.bufferedMs != buffered) {
            _state.value = current.copy(
                positionMs = position,
                durationMs = duration,
                bufferedMs = buffered,
            )
        }
        val now = SystemClock.elapsedRealtime()
        if (exo.isPlaying && currentUri != null && now - lastResumeSaveAt >= 5_000) {
            resume.save(currentUri!!, position, duration)
            lastResumeSaveAt = now
        }
    }

    private fun refreshTracks(tracks: Tracks? = null) {
        val groups = (tracks ?: player?.currentTracks)?.groups ?: return
        val audio = mutableListOf<VideoTrack>()
        val text = mutableListOf<VideoTrack>()
        groups.forEachIndexed { groupIndex, group ->
            if (group.type != C.TRACK_TYPE_AUDIO && group.type != C.TRACK_TYPE_TEXT) return@forEachIndexed
            val kind = if (group.type == C.TRACK_TYPE_AUDIO) "Audio" else "Subtitle"
            for (trackIndex in 0 until group.length) {
                if (!group.isTrackSupported(trackIndex)) continue
                val format = group.getTrackFormat(trackIndex)
                val choice = VideoTrack(
                    groupIndex = groupIndex,
                    trackIndex = trackIndex,
                    type = group.type,
                    label = VideoPlayback.trackLabel(format.label, format.language, trackIndex, kind),
                    language = format.language.orEmpty(),
                    selected = group.isTrackSelected(trackIndex),
                )
                if (group.type == C.TRACK_TYPE_AUDIO) audio += choice else text += choice
            }
        }
        _state.value = _state.value.copy(
            audioTracks = audio,
            textTracks = text,
            subtitlesDisabled = player?.trackSelectionParameters?.disabledTrackTypes
                ?.contains(C.TRACK_TYPE_TEXT) == true,
        )
    }

    private fun mediaItem(entry: VideoEntry): androidx.media3.common.MediaItem {
        val builder = androidx.media3.common.MediaItem.Builder()
            .setUri(entry.uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(entry.displayTitle).build())
        if (entry.subtitles.isNotEmpty()) {
            builder.setSubtitleConfigurations(
                entry.subtitles.map { subtitle ->
                    androidx.media3.common.MediaItem.SubtitleConfiguration.Builder(subtitle.uri.toUri())
                        .setMimeType(subtitle.mime)
                        .apply {
                            if (subtitle.language.isNotBlank()) setLanguage(subtitle.language)
                            if (subtitle.label.isNotBlank()) setLabel(subtitle.label)
                        }
                        .build()
                },
            )
        }
        return builder.build()
    }

    private fun playbackError(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "File video tidak ditemukan. Mungkin sudah dipindah atau dihapus."
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
            "Izin membaca video dicabut. Pindai ulang Koleksi atau izinkan akses berkas."
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "Jaringan tidak tersedia saat membaca video."
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED ->
            "Format video atau audio ini tidak didukung pemutar di perangkat."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
            "Berkas video rusak atau kontainernya tidak dikenali."
        else -> "Tidak dapat memutar video (${error.errorCodeName})."
    }

    private fun preferencesState(): State = State(
        speed = preferences.getFloat(KEY_SPEED, 1f).coerceIn(0.25f, 3f),
        aspect = runCatching {
            VideoAspect.valueOf(preferences.getString(KEY_ASPECT, VideoAspect.FIT.name) ?: VideoAspect.FIT.name)
        }.getOrDefault(VideoAspect.FIT),
        repeat = runCatching {
            VideoRepeat.valueOf(preferences.getString(KEY_REPEAT, VideoRepeat.OFF.name) ?: VideoRepeat.OFF.name)
        }.getOrDefault(VideoRepeat.OFF),
    )

    private fun VideoRepeat.media3Value(): Int = when (this) {
        VideoRepeat.OFF -> Player.REPEAT_MODE_OFF
        VideoRepeat.ALL -> Player.REPEAT_MODE_ALL
        VideoRepeat.ONE -> Player.REPEAT_MODE_ONE
    }

    override fun onCleared() {
        closeInternal(savePosition = true)
    }

    private companion object {
        const val KEY_SPEED = "speed"
        const val KEY_ASPECT = "aspect"
        const val KEY_REPEAT = "repeat"
    }
}
