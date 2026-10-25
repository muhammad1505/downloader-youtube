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
import androidx.media3.common.Effect
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import java.net.URLEncoder
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
        val subtitleSize: VideoSubtitleSize = VideoSubtitleSize.NORMAL,
        val subtitleStyle: VideoSubtitleStyle = VideoSubtitleStyle.OUTLINE,
        val subtitlePosition: VideoSubtitlePosition = VideoSubtitlePosition.BOTTOM,
        val subtitleBackground: VideoSubtitleBackground = VideoSubtitleBackground.TRANSPARENT,
        val subtitleDelayMs: Int = 0,
        val decoderMode: VideoDecoderMode = VideoDecoderMode.AUTO,
        val enhance: VideoEnhance = VideoEnhance.OFF,
        val equalizer: VideoEqualizer = VideoEqualizer.OFF,
        val audioEffect: VideoAudioEffect = VideoAudioEffect.OFF,
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
    private val audioFx = VideoAudioFx()

    private val _state = MutableStateFlow(preferencesState())
    val state = _state.asStateFlow()

    private var player: ExoPlayer? = null
    private var currentUri: String? = null
    private var lastPositionMs = 0L
    private var lastDurationMs = 0L
    private var lastResumeSaveAt = 0L
    private var ticker: Job? = null
    private var fallbackJob: Job? = null
    private var softwareFallbackUsed = false
    private var pendingSubtitleLabel: String? = null

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
            // Video berikutnya berhak mencoba dekoder perangkat lagi; reopen software memakai
            // PLAYLIST_CHANGED sehingga status fallback tidak ikut direset.
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                softwareFallbackUsed = false
            }
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

        @androidx.annotation.OptIn(UnstableApi::class)
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            val current = _state.value
            audioFx.attach(audioSessionId, current.equalizer, current.audioEffect)
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            _state.value = _state.value.copy(
                videoWidth = videoSize.width,
                videoHeight = videoSize.height,
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            saveNow()
            val entries = _state.value.entries
            val index = player?.currentMediaItemIndex ?: _state.value.index
            val position = player?.currentPosition?.coerceAtLeast(0) ?: _state.value.positionMs
            // Codec yang tidak didukung GPU dicoba sekali lagi dengan FFmpeg/dav1d sebagai
            // dekoder utama. Bila build software juga gagal, error akhir tetap ditampilkan.
            // Mode Perangkat adalah pilihan manual: jangan diam-diam menggantinya; pengguna
            // mendapat tombol "Coba dekoder software" di layar error.
            if (VideoPlayback.isCodecFailure(error.errorCode) &&
                _state.value.decoderMode != VideoDecoderMode.HARDWARE &&
                !softwareFallbackUsed &&
                entries.isNotEmpty()
            ) {
                softwareFallbackUsed = true
                fallbackJob?.cancel()
                _state.value = _state.value.copy(
                    playing = false,
                    buffering = true,
                    ready = false,
                    error = null,
                )
                fallbackJob = viewModelScope.launch {
                    delay(75)
                    if (!_state.value.active) return@launch
                    releasePlayer()
                    openInternal(
                        entries = entries,
                        start = index.coerceIn(entries.indices),
                        startPosition = position,
                        preferSoftware = true,
                    )
                }
                return
            }
            _state.value = _state.value.copy(
                playing = false,
                buffering = false,
                ready = false,
                error = playbackError(error),
            )
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    fun open(entries: List<VideoEntry>, startIndex: Int = 0) {
        if (entries.isEmpty()) return
        closeInternal(savePosition = true)
        val start = startIndex.coerceIn(entries.indices)
        val saved = resume.position(entries[start].uri)
        val startPosition = if (VideoPlayback.shouldResume(saved, 0)) saved ?: 0 else 0
        softwareFallbackUsed = false
        openInternal(entries.map(::withStoredSubtitles), start, startPosition, preferSoftware = false)
    }

    /** Subtitle pilihan pengguna disimpan per video; gabungkan lagi setiap kali antrean dibuka. */
    private fun withStoredSubtitles(entry: VideoEntry): VideoEntry {
        val stored = VideoExternalSubtitleCodec.decode(preferences.getString(externalKey(entry.uri), null))
        if (stored.isEmpty()) return entry
        return entry.copy(subtitles = (entry.subtitles + stored).distinctBy { it.uri })
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun openInternal(
        entries: List<VideoEntry>,
        start: Int,
        startPosition: Long,
        preferSoftware: Boolean,
    ) {
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
            // ON memakai dekoder perangkat lebih dulu dan FFmpeg/dav1d saat format tidak
            // didukung. PREFER memaksa jalur software setelah dekoder perangkat gagal init.
            // OFF menghormati pilihan manual "Perangkat" tanpa memuat fallback FFmpeg.
            val mode = when {
                preferSoftware || preferences.decoderMode == VideoDecoderMode.SOFTWARE ->
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                preferences.decoderMode == VideoDecoderMode.HARDWARE ->
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            }
            val renderers = VideoRenderersFactory(getApplication(), mode) {
                _state.value.subtitleDelayMs * 1000L
            }
            val exo = ExoPlayer.Builder(getApplication(), renderers).build().apply {
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
            audioFx.attach(exo.audioSessionId, preferences.equalizer, preferences.audioEffect)
            // Efek GL memakai pipeline frame platform; renderer FFmpeg/dav1d bisa gagal saat
            // efek warna dipasang. Lewati efek untuk sesi software, bukan menggagalkan video.
            if (mode != DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER) {
                applyVideoEffects(exo, preferences.enhance)
            }
            exo.setMediaItems(entries.map(::mediaItem), start, startPosition)
            exo.prepare()
            exo.play()
            startTicker()
        } catch (failure: Exception) {
            audioFx.release()
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
        val current = _state.value
        val exo = player
        if (exo == null) {
            if (current.entries.isEmpty()) return
            // Player bisa sudah dilepas setelah kegagalan pembukaan; membuat ulang adalah satu-
            // satunya cara "Coba lagi" benar-benar mencoba lagi.
            openInternal(
                entries = current.entries,
                start = current.index.coerceIn(current.entries.indices),
                startPosition = current.positionMs.coerceAtLeast(0),
                preferSoftware = current.decoderMode == VideoDecoderMode.SOFTWARE || softwareFallbackUsed,
            )
            return
        }
        _state.value = current.copy(error = null, buffering = true)
        exo.prepare()
        exo.play()
    }

    fun useSoftwareDecoder() {
        setDecoderMode(VideoDecoderMode.SOFTWARE, forceReopen = true)
    }

    fun setSpeed(value: Float) {
        val speed = value.coerceIn(0.25f, 3f)
        preferences.edit { putFloat(KEY_SPEED, speed) }
        player?.setPlaybackSpeed(speed)
        _state.value = _state.value.copy(speed = speed)
    }

    fun cycleSpeed() = setSpeed(VideoPlayback.nextSpeed(_state.value.speed))

    fun setAspect(value: VideoAspect) {
        preferences.edit { putString(KEY_ASPECT, value.name) }
        _state.value = _state.value.copy(aspect = value)
    }

    fun cycleAspect() = setAspect(_state.value.aspect.next())

    fun setRepeat(value: VideoRepeat) {
        preferences.edit { putString(KEY_REPEAT, value.name) }
        player?.repeatMode = value.media3Value()
        _state.value = _state.value.copy(repeat = value)
    }

    fun cycleRepeat() = setRepeat(_state.value.repeat.next())

    fun setDecoderMode(value: VideoDecoderMode, forceReopen: Boolean = false) {
        val current = _state.value
        if (!forceReopen && current.decoderMode == value) return
        preferences.edit { putString(KEY_DECODER, value.name) }
        // Pilihan manual membatalkan fallback otomatis sebelumnya; kalau tidak, memilih
        // "Perangkat" tetap membuka renderer software dari kegagalan video sebelumnya.
        softwareFallbackUsed = false
        _state.value = current.copy(decoderMode = value)
        if (current.active) {
            reopenAtCurrent(forceSoftware = value == VideoDecoderMode.SOFTWARE)
        }
    }

    fun cycleDecoderMode() = setDecoderMode(_state.value.decoderMode.next())

    fun setEnhance(value: VideoEnhance) {
        preferences.edit { putString(KEY_ENHANCE, value.name) }
        _state.value = _state.value.copy(enhance = value)
        // Sesi software tidak memakai pipeline efek GL; nilainya tetap diingat untuk sesi
        // berikutnya yang memakai dekoder perangkat.
        val softwareSession = _state.value.decoderMode == VideoDecoderMode.SOFTWARE || softwareFallbackUsed
        if (!softwareSession) player?.let { applyVideoEffects(it, value) }
    }

    fun cycleEnhance() = setEnhance(_state.value.enhance.next())

    fun setEqualizer(value: VideoEqualizer) {
        preferences.edit { putString(KEY_EQUALIZER, value.name) }
        _state.value = _state.value.copy(equalizer = value)
        audioFx.apply(value, _state.value.audioEffect)
    }

    fun cycleEqualizer() = setEqualizer(_state.value.equalizer.next())

    fun setAudioEffect(value: VideoAudioEffect) {
        preferences.edit { putString(KEY_AUDIO_EFFECT, value.name) }
        _state.value = _state.value.copy(audioEffect = value)
        audioFx.apply(_state.value.equalizer, value)
    }

    fun cycleAudioEffect() = setAudioEffect(_state.value.audioEffect.next())

    /** Lompat ke video lain di antrean tanpa menutup layar pemutar. */
    fun jumpTo(index: Int) {
        val current = _state.value
        val target = index.coerceIn(current.entries.indices)
        val exo = player
        if (exo != null) {
            exo.seekTo(target, 0)
            exo.play()
        } else if (current.entries.isNotEmpty()) {
            openInternal(
                entries = current.entries,
                start = target,
                startPosition = 0,
                preferSoftware = current.decoderMode == VideoDecoderMode.SOFTWARE || softwareFallbackUsed,
            )
        }
    }

    /**
     * Menambahkan subtitle dari penyimpanan. Berkas dibuka ulang pada posisi yang sama karena
     * MediaItem hanya menerima daftar subtitle saat dibuat; URI dibuka lagi dari daftar baru.
     */
    fun addExternalSubtitle(subtitle: VideoSubtitle) {
        val current = _state.value
        val entry = current.entry ?: return
        if (entry.subtitles.any { it.uri == subtitle.uri }) return
        val mergedSubtitles = (entry.subtitles + subtitle).distinctBy { it.uri }
        pendingSubtitleLabel = subtitle.label
        val stored = VideoExternalSubtitleCodec.decode(preferences.getString(externalKey(entry.uri), null))
        preferences.edit {
            putString(
                externalKey(entry.uri),
                VideoExternalSubtitleCodec.encode((stored + subtitle).distinctBy { it.uri }),
            )
        }
        val entries = current.entries.toMutableList().also { list ->
            list[current.index] = entry.copy(subtitles = mergedSubtitles)
        }
        val position = player?.currentPosition ?: current.positionMs
        releasePlayer()
        openInternal(
            entries = entries,
            start = current.index.coerceIn(entries.indices),
            startPosition = position,
            preferSoftware = current.decoderMode == VideoDecoderMode.SOFTWARE || softwareFallbackUsed,
        )
    }

    fun setSubtitleSize(value: VideoSubtitleSize) {
        preferences.edit { putString(KEY_SUBTITLE_SIZE, value.name) }
        _state.value = _state.value.copy(subtitleSize = value)
    }

    fun setSubtitleStyle(value: VideoSubtitleStyle) {
        preferences.edit { putString(KEY_SUBTITLE_STYLE, value.name) }
        _state.value = _state.value.copy(subtitleStyle = value)
    }

    fun setSubtitlePosition(value: VideoSubtitlePosition) {
        preferences.edit { putString(KEY_SUBTITLE_POSITION, value.name) }
        _state.value = _state.value.copy(subtitlePosition = value)
    }

    fun setSubtitleBackground(value: VideoSubtitleBackground) {
        preferences.edit { putString(KEY_SUBTITLE_BACKGROUND, value.name) }
        _state.value = _state.value.copy(subtitleBackground = value)
    }

    /** Menggeser sampel subtitle memerlukan flush decoder; seek ke posisi yang sama memicunya. */
    fun setSubtitleDelay(valueMs: Int) {
        val value = VideoPlayback.adjustSubtitleDelay(valueMs, 0)
        preferences.edit { putInt(KEY_SUBTITLE_DELAY, value) }
        val position = player?.currentPosition ?: _state.value.positionMs
        _state.value = _state.value.copy(subtitleDelayMs = value)
        player?.seekTo(position)
    }

    fun adjustSubtitleDelay(deltaMs: Int) =
        setSubtitleDelay(VideoPlayback.adjustSubtitleDelay(_state.value.subtitleDelayMs, deltaMs))

    fun resetSubtitleDelay() = setSubtitleDelay(0)

    fun selectAudio(track: VideoTrack) = selectTrack(track)

    fun selectSubtitle(track: VideoTrack) = selectTrack(track)

    /** Tombol cepat subtitle: Nonaktif → track 1 → track 2 → … → Nonaktif. */
    fun cycleSubtitle() {
        val current = _state.value
        val tracks = current.textTracks
        if (current.subtitlesDisabled) {
            tracks.firstOrNull()?.let(::selectSubtitle)
            return
        }
        val selected = tracks.indexOfFirst { it.selected }
        when {
            selected < 0 -> tracks.firstOrNull()?.let(::selectSubtitle)
            selected == tracks.lastIndex -> disableSubtitles()
            else -> selectSubtitle(tracks[selected + 1])
        }
    }

    private fun selectTrack(track: VideoTrack) {
        val exo = player ?: return
        val group = exo.currentTracks.groups.getOrNull(track.groupIndex) ?: return
        if (group.type != track.type) return
        val parameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(track.type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex))
            .build()
        exo.trackSelectionParameters = parameters
        _state.value = _state.value.let { state ->
            when (track.type) {
                C.TRACK_TYPE_AUDIO -> state.copy(
                    audioTracks = state.audioTracks.map {
                        it.copy(selected = it.groupIndex == track.groupIndex && it.trackIndex == track.trackIndex)
                    },
                )
                C.TRACK_TYPE_TEXT -> state.copy(
                    subtitlesDisabled = false,
                    textTracks = state.textTracks.map {
                        it.copy(selected = it.groupIndex == track.groupIndex && it.trackIndex == track.trackIndex)
                    },
                )
                else -> state
            }
        }
    }

    fun automaticTracks(type: Int) {
        val exo = player ?: return
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .clearOverridesOfType(type)
            .build()
        if (type == C.TRACK_TYPE_TEXT) {
            _state.value = _state.value.copy(
                subtitlesDisabled = false,
                textTracks = _state.value.textTracks.map { it.copy(selected = false) },
            )
        }
    }

    fun disableSubtitles() {
        val exo = player ?: return
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        _state.value = _state.value.copy(
            subtitlesDisabled = true,
            textTracks = _state.value.textTracks.map { it.copy(selected = false) },
        )
    }

    fun close() {
        closeInternal(savePosition = true)
    }

    private fun closeInternal(savePosition: Boolean) {
        fallbackJob?.cancel()
        fallbackJob = null
        if (savePosition && player != null) saveNow()
        releasePlayer()
        currentUri = null
        lastPositionMs = 0
        lastDurationMs = 0
        softwareFallbackUsed = false
        _state.value = preferencesState()
    }

    private fun releasePlayer() {
        ticker?.cancel()
        ticker = null
        player?.removeListener(listener)
        player?.release()
        player = null
        audioFx.release()
    }

    /** Membuka ulang video yang sama pada posisi sekarang, dipakai saat mengganti dekoder/subtitle. */
    private fun reopenAtCurrent(forceSoftware: Boolean) {
        val current = _state.value
        if (current.entries.isEmpty()) return
        val index = (player?.currentMediaItemIndex ?: current.index).coerceIn(current.entries.indices)
        val position = player?.currentPosition ?: current.positionMs
        releasePlayer()
        openInternal(
            entries = current.entries,
            start = index,
            startPosition = position.coerceAtLeast(0),
            preferSoftware = forceSoftware || current.decoderMode == VideoDecoderMode.SOFTWARE || softwareFallbackUsed,
        )
    }

    /** Efek warna real-time Media3; preset hanya memakai API yang stabil untuk video SDR/HDR. */
    @androidx.annotation.OptIn(UnstableApi::class)
    private fun applyVideoEffects(exo: ExoPlayer, enhance: VideoEnhance) {
        val effects: List<Effect> = when (enhance) {
            VideoEnhance.OFF -> emptyList()
            VideoEnhance.LIGHT -> listOf(
                Contrast(0.05f),
                HslAdjustment.Builder().adjustSaturation(6f).build(),
            )
            VideoEnhance.VIVID -> listOf(
                Contrast(0.12f),
                HslAdjustment.Builder().adjustSaturation(16f).adjustLightness(1f).build(),
            )
            VideoEnhance.SHARP -> listOf(
                VideoSharpenEffect(0.08f),
                Contrast(0.20f),
                HslAdjustment.Builder().adjustSaturation(8f).adjustLightness(-2f).build(),
            )
        }
        // Efek GL tidak didukung semua kombinasi dekoder/GPU; kegagalan efek tidak boleh
        // menghentikan pemutaran video.
        runCatching { exo.setVideoEffects(effects) }
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
        // Setelah subtitle eksternal ditambahkan, pilih track yang baru itu supaya pengguna
        // tidak perlu menekan CC lagi. Label berkas dipakai sebagai pembeda dari track tertanam.
        val wanted = pendingSubtitleLabel ?: return
        val match = text.firstOrNull { it.label.contains(wanted, ignoreCase = true) }
            ?: text.lastOrNull()
        if (match != null) {
            pendingSubtitleLabel = null
            selectTrack(match)
        }
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
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
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
        subtitleSize = runCatching {
            VideoSubtitleSize.valueOf(
                preferences.getString(KEY_SUBTITLE_SIZE, VideoSubtitleSize.NORMAL.name)
                    ?: VideoSubtitleSize.NORMAL.name,
            )
        }.getOrDefault(VideoSubtitleSize.NORMAL),
        subtitleStyle = storedSubtitleStyle(),
        subtitlePosition = runCatching {
            VideoSubtitlePosition.valueOf(
                preferences.getString(KEY_SUBTITLE_POSITION, VideoSubtitlePosition.BOTTOM.name)
                    ?: VideoSubtitlePosition.BOTTOM.name,
            )
        }.getOrDefault(VideoSubtitlePosition.BOTTOM),
        subtitleBackground = runCatching {
            VideoSubtitleBackground.valueOf(
                preferences.getString(KEY_SUBTITLE_BACKGROUND, VideoSubtitleBackground.TRANSPARENT.name)
                    ?: VideoSubtitleBackground.TRANSPARENT.name,
            )
        }.getOrDefault(VideoSubtitleBackground.TRANSPARENT),
        subtitleDelayMs = VideoPlayback.adjustSubtitleDelay(
            preferences.getInt(KEY_SUBTITLE_DELAY, 0),
            0,
        ),
        decoderMode = storedEnum(KEY_DECODER, VideoDecoderMode.AUTO),
        enhance = storedEnum(KEY_ENHANCE, VideoEnhance.OFF),
        equalizer = storedEnum(KEY_EQUALIZER, VideoEqualizer.OFF),
        audioEffect = storedEnum(KEY_AUDIO_EFFECT, VideoAudioEffect.OFF),
    )

    private inline fun <reified T : Enum<T>> storedEnum(key: String, fallback: T): T =
        runCatching { enumValueOf<T>(preferences.getString(key, fallback.name) ?: fallback.name) }
            .getOrDefault(fallback)

    private fun externalKey(uri: String): String =
        "external_subs_" + URLEncoder.encode(uri, Charsets.UTF_8.name())

    /** Build pengembangan sempat menyimpan BOX; petakan ke gaya tebal yang setara. */
    private fun storedSubtitleStyle(): VideoSubtitleStyle {
        val raw = preferences.getString(KEY_SUBTITLE_STYLE, null) ?: return VideoSubtitleStyle.OUTLINE
        if (raw == "BOX") return VideoSubtitleStyle.BOLD
        return runCatching { VideoSubtitleStyle.valueOf(raw) }.getOrDefault(VideoSubtitleStyle.OUTLINE)
    }

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
        const val KEY_SUBTITLE_SIZE = "subtitle_size"
        const val KEY_SUBTITLE_STYLE = "subtitle_style"
        const val KEY_SUBTITLE_POSITION = "subtitle_position"
        const val KEY_SUBTITLE_BACKGROUND = "subtitle_background"
        const val KEY_SUBTITLE_DELAY = "subtitle_delay_ms"
        const val KEY_DECODER = "decoder_mode"
        const val KEY_ENHANCE = "smart_enhance"
        const val KEY_EQUALIZER = "video_equalizer"
        const val KEY_AUDIO_EFFECT = "video_audio_effect"
    }
}
