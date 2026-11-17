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
 *            ExoPlayer hidup selama layar video terbuka dan dilepas saat ditutup. Activity-scoped ViewModel
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
        /** Naik setiap ExoPlayer dibuat ulang; dipakai layar untuk mengganti permukaan video. */
        val playerVersion: Int = 0,
        val equalizer: VideoEqualizer = VideoEqualizer.OFF,
        val audioEffect: VideoAudioEffect = VideoAudioEffect.OFF,
        val videoWidth: Int = 0,
        val videoHeight: Int = 0,
        val pixelRatio: Float = 1f,
        val brightnessMode: VideoBrightnessMode = VideoBrightnessMode.AUTO,
        val manualBrightness: Float = 0.5f,
        val volumeBoostDb: Int = 0,
        val volumeBoostAvailable: Boolean? = null,
    ) {
        val active: Boolean get() = entries.isNotEmpty()
        val entry: VideoEntry? get() = entries.getOrNull(index)
        val hasNext: Boolean get() = index < entries.lastIndex
        val hasPrevious: Boolean get() = index > 0
    }

    private val preferences = application.getSharedPreferences("video-fx", Context.MODE_PRIVATE)
    private val lastSession = application.getSharedPreferences("video-last", Context.MODE_PRIVATE)
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
    private var attachmentJob: Job? = null
    /** Waktu masuk STATE_BUFFERING; nol berarti tidak sedang menunggu buffer. */
    private var bufferingSince = 0L
    /** Pemulihan otomatis yang sudah dipakai pada episode macet ini. */
    private var stallRecoveries = 0
    /** Waktu mulai pemutaran stabil, dipakai untuk mereset hitungan macet. */
    private var playingSince = 0L
    private val attachmentChecked = mutableSetOf<String>()
    private var softwareFallbackUsed = false
    private var pendingSubtitleLabel: String? = null

    /** Bahasa subtitle unduhan pada video yang sedang dibuka, untuk pemilihan otomatis. */
    private var pendingSubtitleLanguages: List<String> = emptyList()
    private var pendingSubtitleSelection = false
    private var playerVersion = 0
    private var viewportWidth = 0
    private var viewportHeight = 0

    /** Dibaca layar untuk dipasang ke PlayerView; null saat tidak ada video. */
    val exoPlayer: ExoPlayer? get() = player

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val current = _state.value
            _state.value = current.copy(playing = isPlaying)
            // Waktu mulai dipakai watchdog untuk mereset hitungan macet hanya setelah pemutaran
            // benar-benar stabil, bukan sekejap setelah pemutar dibuka ulang.
            playingSince = if (isPlaying) SystemClock.elapsedRealtime() else 0L
            if (!isPlaying) saveNow()
        }

        // Jangan memakai onIsLoadingChanged untuk spinner: isLoading juga bernilai true saat
        // ExoPlayer melakukan prefetch latar yang normal. Indikator buffering hanya boleh
        // mengikuti STATE_BUFFERING agar tidak berkedip setiap buffer refill (~50 detik).
        override fun onPlaybackStateChanged(playbackState: Int) {
            val current = _state.value
            if (playbackState == Player.STATE_BUFFERING) {
                if (bufferingSince == 0L) bufferingSince = SystemClock.elapsedRealtime()
            } else {
                bufferingSince = 0L
            }
            _state.value = when (playbackState) {
                Player.STATE_READY -> current.copy(
                    ready = true,
                    buffering = false,
                    ended = false,
                    error = null,
                    durationMs = player?.duration?.takeIf { it > 0 && it != C.TIME_UNSET } ?: current.durationMs,
                )
                Player.STATE_BUFFERING -> current.copy(
                    buffering = VideoPlayback.shouldShowBuffering(playbackState, current.error != null),
                )
                Player.STATE_ENDED -> {
                    currentUri?.let(resume::clear)
                    current.copy(playing = false, buffering = false, ended = true)
                }
                else -> current.copy(buffering = false, ready = false)
            }
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            // Renderer tidak bisa diganti di tengah antrean. Selama ExoPlayer ini masih memakai
            // jalur software hasil fallback, statusnya harus tetap true agar efek GL tidak
            // dipasang ke sesi software dan "Coba lagi" tahu kapan harus membuat pemutar baru.
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
            // Pindah video (manual atau auto-lanjut) memulai episode macet baru. Daftar awal
            // yang baru dipasang bukan perpindahan, jadi hitungan pemulihan tidak direset.
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                stallRecoveries = 0
                bufferingSince = 0L
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
            _state.value.entries.getOrNull(index)?.let {
                rememberLast(it)
                prepareSubtitleSelection(it)
            }
            refreshTracks()
            discoverCurrentSubtitles()
        }

        override fun onTracksChanged(tracks: Tracks) {
            refreshTracks(tracks)
            syncVideoGeometry()
        }

        @androidx.annotation.OptIn(UnstableApi::class)
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            val current = _state.value
            audioFx.attach(audioSessionId, current.equalizer, current.audioEffect)
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (syncVideoGeometry()) return
            // Fallback saat videoFormat belum tersedia. Jangan pakai width/height mentah:
            // rekaman portrait sering membawa rotasi 90/270 derajat, sehingga sumbu dan
            // rasio pikselnya harus dinormalisasi sama seperti jalur format di atas.
            // Media3 1.11 selalu melaporkan unappliedRotationDegrees = 0 karena rotasi
            // diterapkan player, jadi nilainya tidak dibaca lagi.
            val geometry = VideoViewport.sourceSize(
                videoSize.width,
                videoSize.height,
                0,
                videoSize.pixelWidthHeightRatio,
            ) ?: return
            _state.value = _state.value.copy(
                videoWidth = geometry.first,
                videoHeight = geometry.second,
                pixelRatio = geometry.third,
            )
        }

        // Argumen copy() multi-baris di bawah if panjang adalah pola yang sudah dipakai di file
        // ini; lint salah membacanya sebagai statement terpisah.
        @Suppress("SuspiciousIndentation")
        override fun onPlayerError(error: PlaybackException) {
            val current = _state.value
            VideoDiagnostics.failure(error.errorCode, VideoPlayback.isCodecFailure(error.errorCode))
            AppLog.error(LogCategory.PLAYER, "Pemutar video melaporkan error ${error.errorCodeName}.", error)
            // Jangan memakai currentPosition mentah: ExoPlayer dapat melaporkan 0 setelah
            // dekoder gagal, sehingga fallback/retry akan memutar ulang dari awal.
            val position = VideoPlayback.reopenPosition(current.positionMs, player?.currentPosition ?: 0)
            if (currentUri != null) {
                val duration = player?.duration?.takeIf { it > 0 && it != C.TIME_UNSET } ?: lastDurationMs
                resume.save(currentUri!!, position, duration)
            }
            val entries = current.entries
            val index = if (entries.isEmpty()) 0 else current.index.coerceIn(entries.indices)
            // Codec yang tidak didukung GPU dicoba sekali lagi dengan FFmpeg/dav1d sebagai
            // dekoder utama. Bila build software juga gagal, error akhir tetap ditampilkan.
            // Mode Perangkat adalah pilihan manual: jangan diam-diam menggantinya; pengguna
            // mendapat tombol "Coba dekoder software" di layar error.
            if (VideoPlayback.shouldUseSoftwareFallback(
                    errorCode = error.errorCode,
                    mode = _state.value.decoderMode,
                    alreadyTried = softwareFallbackUsed,
                ) && entries.isNotEmpty()
            ) {
                softwareFallbackUsed = true
                fallbackJob?.cancel()
                AppLog.warn(LogCategory.PLAYER, "Mencoba dekoder software setelah ${error.errorCodeName}.")
                // Jangan biarkan watchdog ikut campur saat fallback dekoder sedang berjalan.
                bufferingSince = 0L
                stallRecoveries = 0
                // Error tidak boleh mengubah niat pengguna: video yang dijeda tetap dijeda
                // setelah fallback, sedangkan video yang berjalan tetap diputar.
                val startPlaying = player?.playWhenReady ?: current.playing
                _state.value = _state.value.copy(
                    playing = false,
                    buffering = true,
                    ready = false,
                    positionMs = position,
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
                        startPlaying = startPlaying,
                    )
                }
                return
            }
            bufferingSince = 0L
            _state.value = _state.value.copy(
                playing = false,
                buffering = false,
                ready = false,
                positionMs = position,
                error = playbackError(error),
            )
            AppLog.error(LogCategory.PLAYER, "Pemutaran video berhenti: ${playbackError(error)}")
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    fun open(entries: List<VideoEntry>, startIndex: Int = 0) {
        if (entries.isEmpty()) return
        closeInternal(savePosition = true)
        attachmentChecked.clear()
        val start = startIndex.coerceIn(entries.indices)
        val saved = resume.position(entries[start].uri)
        val startPosition = if (VideoPlayback.shouldResume(saved, 0)) saved ?: 0 else 0
        softwareFallbackUsed = false
        stallRecoveries = 0
        openInternal(
            entries.map(::withStoredSubtitles),
            start,
            startPosition,
            preferSoftware = false,
            // Mengembalikan posisi tersimpan berarti benar-benar lanjut tonton: chip muncul.
            resumeFromMs = startPosition,
        )
    }

    /**
     * Membuka video terakhir dari shortcut launcher. Preferensi video-last ditulis saat video
     * dibuka; bila belum ada (pengguna lama), VideoResumeStore menjadi cadangan.
     */
    fun openLastVideo(): Boolean {
        val savedUri = lastSession.getString(KEY_LAST_URI, null)?.takeIf { it.isNotBlank() }
        val uri = savedUri ?: resume.latestUri() ?: return false
        val title = lastSession.getString(KEY_LAST_TITLE, null).orEmpty()
        open(listOf(VideoEntry(uri, title)), 0)
        return true
    }

    @Suppress("SuspiciousIndentation")
    private fun rememberLast(entry: VideoEntry) {
        lastSession.edit {
            putString(KEY_LAST_URI, entry.uri)
            putString(KEY_LAST_TITLE, entry.title)
        }
    }

    private fun discoverCurrentSubtitles() {
        attachmentJob?.cancel()
        val current = _state.value
        val entry = current.entry ?: return
        if (entry.subtitles.isNotEmpty() || entry.uri in attachmentChecked) return
        val version = playerVersion
        attachmentJob = viewModelScope.launch {
            val subtitles = MediaAttachments.localVideoSubtitles(getApplication(), entry.uri)
            val latest = _state.value
            if (version != playerVersion || latest.index != current.index ||
                latest.entry?.uri != entry.uri || latest.entry?.subtitles?.isNotEmpty() == true) return@launch
            attachmentChecked.add(entry.uri)
            if (subtitles.isEmpty()) return@launch
            val entries = latest.entries.toMutableList()
            entries[current.index] = entry.copy(subtitles = subtitles)
            _state.value = latest.copy(entries = entries)
            reopenAtCurrent(forceSoftware = false)
        }
    }

    /** Video dengan subtitle unduhan memilih track otomatis; video tanpa subtitle tetap nonaktif. */
    private fun prepareSubtitleSelection(entry: VideoEntry) {
        pendingSubtitleLanguages = entry.subtitles.map { it.language }.filter { it.isNotBlank() }
        pendingSubtitleSelection = entry.subtitles.isNotEmpty()
    }

    /** Bahasa perangkat, termasuk bahasa aplikasi bila pengguna mengaturnya per aplikasi. */
    private fun deviceLanguages(): List<String> {
        val locales = getApplication<Application>().resources.configuration.locales
        return (0 until locales.size()).map { locales[it].toLanguageTag() }
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
        startPlaying: Boolean = true,
        /**
         * Nilai chip "Dilanjutkan dari". Hanya pembukaan yang benar-benar memulihkan posisi
         * tersimpan (atau pindah video) yang mengisinya; buka ulang internal seperti retry, ganti
         * dekoder, atau penyesuaian ukuran permukaan tidak boleh mengaku "dilanjutkan".
         */
        resumeFromMs: Long = 0,
    ) {
        val preferences = preferencesState()
        playerVersion += 1
        _state.value = preferences.copy(
            entries = entries,
            index = start,
            positionMs = startPosition,
            bufferedMs = 0,
            buffering = true,
            resumeFromMs = resumeFromMs,
            playerVersion = playerVersion,
        )
        // Setiap sesi baru mulai menghitung dari awal, termasuk sesi hasil pemulihan macet.
        bufferingSince = SystemClock.elapsedRealtime()
        playingSince = 0L
        currentUri = entries[start].uri
        rememberLast(entries[start])
        lastPositionMs = startPosition
        lastDurationMs = 0
        prepareSubtitleSelection(entries[start])
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
            val version = playerVersion
            val renderers = VideoRenderersFactory(getApplication(), mode,
                volumeBoostDb = preferences.volumeBoostDb,
                onBoostSupport = { supported ->
                    viewModelScope.launch {
                        if (playerVersion == version && _state.value.active)
                            _state.value = _state.value.copy(volumeBoostAvailable = supported)
                    }
                },
            ) {
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
                volume = 1f
                repeatMode = preferences.repeat.media3Value()
                setPlaybackSpeed(preferences.speed)
                addListener(listener)
            }
            player = exo
            exo.addAnalyticsListener(VideoDiagnostics.analytics())
            audioFx.attach(exo.audioSessionId, preferences.equalizer, preferences.audioEffect)
            VideoDiagnostics.session(
                reason = if (preferSoftware) "buka-software" else "buka",
                decoder = preferences.decoderMode.name,
                positionMs = startPosition,
                playerVersion = version,
            )
            // Efek GL memakai pipeline frame platform; renderer FFmpeg/dav1d bisa gagal saat
            // efek warna dipasang. Lewati efek untuk sesi software, bukan menggagalkan video.
            if (mode != DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER) {
                applyVideoEffects(exo, preferences.enhance)
            }
            exo.setMediaItems(entries.map(::mediaItem), start, startPosition)
            exo.prepare()
            exo.playWhenReady = startPlaying
            startTicker()
            AppLog.info(LogCategory.PLAYER, "Video dibuka: ${entries.size} item, dekoder ${_state.value.decoderMode.name.lowercase()}.")
        } catch (failure: Exception) {
            AppLog.error(LogCategory.PLAYER, "Video gagal dibuka.", failure)
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
            else -> {
                // Lanjut setelah jeda saat buffer memberi jendela watchdog baru.
                if (exo.playbackState == Player.STATE_BUFFERING) bufferingSince = SystemClock.elapsedRealtime()
                exo.play()
            }
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
        // Seek besar pada berkas lokal boleh menunggu buffer lagi; beri jendela baru sebelum
        // watchdog menganggapnya macet.
        if (exo.playbackState == Player.STATE_BUFFERING) bufferingSince = SystemClock.elapsedRealtime()
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
        if (current.entries.isEmpty()) return
        // Percobaan manual memberi anggaran pemulihan otomatis baru.
        stallRecoveries = 0
        bufferingSince = 0L
        val exo = player
        val target = VideoPlayback.reopenPosition(current.positionMs, exo?.currentPosition ?: 0)
        // Jika fallback software otomatis sudah gagal, mengulang renderer yang sama hanya akan
        // menghasilkan error yang sama. Buat pemutar baru dengan jalur AUTO (hardware dulu).
        if (VideoPlayback.needsDecoderRestart(hasPlayer = exo != null, softwareFallbackUsed = softwareFallbackUsed)) {
            fallbackJob?.cancel()
            fallbackJob = null
            releasePlayer()
            softwareFallbackUsed = false
            openInternal(
                entries = current.entries,
                start = current.index.coerceIn(current.entries.indices),
                startPosition = target,
                preferSoftware = current.decoderMode == VideoDecoderMode.SOFTWARE,
            )
            return
        }
        _state.value = current.copy(error = null, buffering = true, positionMs = target)
        exo!!.prepare()
        exo.seekTo(target)
        exo.play()
    }

    fun useSoftwareDecoder() {
        setDecoderMode(VideoDecoderMode.SOFTWARE, forceReopen = true)
    }

    /** Pemulihan dari mode Software yang gagal: coba lagi lewat jalur Otomatis/perangkat. */
    fun useAutomaticDecoder() {
        setDecoderMode(VideoDecoderMode.AUTO, forceReopen = true)
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
        // Mode tampilan hanya mengubah skala layer; jangan buka ulang player yang sedang dijeda
        // hanya untuk menerapkan efek warna yang tidak berubah.
    }

    fun cycleAspect() = setAspect(_state.value.aspect.next())

    fun setBrightnessMode(mode: VideoBrightnessMode, initial: Float) {
        val level = if (preferences.contains(KEY_BRIGHTNESS_LEVEL)) _state.value.manualBrightness
                    else VideoBrightnessPolicy.clamp(initial)
        preferences.edit {
            putString(KEY_BRIGHTNESS_MODE, mode.name)
            if (mode == VideoBrightnessMode.MANUAL) putFloat(KEY_BRIGHTNESS_LEVEL, level)
        }
        _state.value = _state.value.copy(brightnessMode = mode, manualBrightness = level)
    }

    fun setManualBrightness(value: Float, persist: Boolean = true) {
        val level = VideoBrightnessPolicy.clamp(value)
        _state.value = _state.value.copy(brightnessMode = VideoBrightnessMode.MANUAL, manualBrightness = level)
        if (persist) preferences.edit {
            putString(KEY_BRIGHTNESS_MODE, VideoBrightnessMode.MANUAL.name)
            putFloat(KEY_BRIGHTNESS_LEVEL, level)
        }
    }

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
        stallRecoveries = 0
        bufferingSince = 0L
        _state.value = current.copy(decoderMode = value)
        AppLog.info(LogCategory.PLAYER, "Dekoder video diatur ke ${value.name.lowercase()}.")
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
        refreshPowerPolicy()
    }

    fun setViewport(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (width == viewportWidth && height == viewportHeight) return
        viewportWidth = width
        viewportHeight = height
        VideoDiagnostics.viewport(width, height)
        // Viewport berubah saat rotasi atau masuk/keluar layar penuh. TextureView yang berubah
        // ukuran bisa mempertahankan transform lama di Android 10, dan pemutar yang berhenti tidak
        // menggambar frame baru. Buka ulang di posisi yang sama tanpa memaksa pemutaran; untuk
        // video yang sudah berakhir, buka 1 ms sebelum akhir agar frame terakhir muncul dengan
        // geometri baru.
        val state = _state.value
        val exo = player ?: return
        if (state.entries.isEmpty() || exo.isPlaying || exo.playbackState == Player.STATE_BUFFERING) return
        if (exo.playbackState == Player.STATE_ENDED) {
            val start = state.index.coerceIn(state.entries.indices)
            releasePlayer()
            openInternal(
                entries = state.entries,
                start = start,
                startPosition = (state.durationMs - 1).coerceAtLeast(0),
                preferSoftware = state.decoderMode == VideoDecoderMode.SOFTWARE || softwareFallbackUsed,
                startPlaying = false,
            )
        } else {
            reopenAtCurrent(forceSoftware = false)
        }
    }

    fun refreshPowerPolicy() {
        val exo = player ?: return
        val softwareSession = _state.value.decoderMode == VideoDecoderMode.SOFTWARE || softwareFallbackUsed
        if (softwareSession) return
        // The default effects pipeline cannot replay a cached frame. Reprepare only when paused,
        // preserving both position and play intent; normal playback updates the effects in place.
        if (!exo.playWhenReady && exo.playbackState == Player.STATE_READY) {
            reopenAtCurrent(forceSoftware = false)
        } else {
            applyVideoEffects(exo, _state.value.enhance)
        }
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

    fun setVolumeBoost(db: Int) {
        val value = VideoVolumePolicy.boostDb(db)
        if (_state.value.volumeBoostDb == value) return
        preferences.edit { putInt(KEY_VOLUME_BOOST, value) }
        _state.value = _state.value.copy(volumeBoostDb = value, volumeBoostAvailable = null)
        reopenAtCurrent(forceSoftware = false)
    }

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
        val startPlaying = player?.playWhenReady ?: current.playing
        releasePlayer()
        openInternal(
            entries = entries,
            start = current.index.coerceIn(entries.indices),
            startPosition = position,
            preferSoftware = current.decoderMode == VideoDecoderMode.SOFTWARE || softwareFallbackUsed,
            startPlaying = startPlaying,
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
        bufferingSince = 0L
        playingSince = 0L
        stallRecoveries = 0
        _state.value = preferencesState()
    }

    private fun releasePlayer() {
        attachmentJob?.cancel()
        attachmentJob = null
        // Pemutar baru wajib menerima efek lagi walau kombinasi inputnya sama.
        effectsSignature = null
        VideoDiagnostics.release(playerVersion)
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
        val position = VideoPlayback.reopenPosition(current.positionMs, player?.currentPosition ?: 0)
        val startPlaying = player?.playWhenReady ?: current.playing
        releasePlayer()
        openInternal(
            entries = current.entries,
            start = index,
            startPosition = position.coerceAtLeast(0),
            preferSoftware = forceSoftware || current.decoderMode == VideoDecoderMode.SOFTWARE || softwareFallbackUsed,
            startPlaying = startPlaying,
        )
    }

    /**
     * Tanda tangan input efek terakhir. Pipeline GL tidak dibangun ulang bila kombinasi ini tidak
     * berubah, sehingga tick progres dan perubahan viewport tidak memicu hitch berkala.
     */
    private var effectsSignature: String? = null

    /** Efek warna real-time Media3; preset hanya memakai API yang stabil untuk video SDR/HDR. */
    @androidx.annotation.OptIn(UnstableApi::class)
    private fun applyVideoEffects(exo: ExoPlayer, enhance: VideoEnhance) {
        val effective = if (PlaybackPower.saving) VideoEnhance.OFF else enhance
        val effects: List<Effect> = when (effective) {
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
        // Efek hanya mengubah warna; letterbox/crop selalu diurus permukaan VideoViewport. Pipeline
        // GL karena itu tidak pernah mengganti ukuran frame, sehingga rotasi layar atau pergantian
        // mode tampilan tidak memicu ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED.
        val signature = VideoPlayback.effectsKey(
            enhance = enhance,
            saving = PlaybackPower.saving,
            playerVersion = _state.value.playerVersion,
        )
        if (signature == effectsSignature) return
        // Efek GL tidak didukung semua kombinasi dekoder/GPU; kegagalan efek tidak boleh
        // menghentikan pemutaran video.
        val applied = runCatching { exo.setVideoEffects(effects) }.isSuccess
        VideoDiagnostics.effects(signature, applied)
        effectsSignature = signature
    }

    private fun saveNow() {
        val exo = player ?: return
        val uri = currentUri ?: return
        val duration = exo.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: lastDurationMs
        val position = VideoPlayback.reopenPosition(_state.value.positionMs, exo.currentPosition)
        resume.save(uri, position, duration)
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive && player != null) {
                syncProgress()
                delay(PlaybackPolicy.videoTickMs(PlaybackPower.saving))
            }
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun syncVideoGeometry(): Boolean {
        val format = player?.videoFormat ?: return false
        val geometry = VideoViewport.sourceSize(format.width, format.height, format.rotationDegrees, format.pixelWidthHeightRatio) ?: return false
        val current = _state.value
        if (current.videoWidth != geometry.first || current.videoHeight != geometry.second || current.pixelRatio != geometry.third) {
            _state.value = current.copy(videoWidth = geometry.first, videoHeight = geometry.second, pixelRatio = geometry.third)
        }
        return true
    }

    private fun syncProgress() {
        val exo = player ?: return
        syncVideoGeometry()
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
        if (exo.isPlaying) {
            if (playingSince == 0L) playingSince = now
            // Pemutaran yang bertahan cukup lama menandakan pemulihan berhasil; episode macet
            // berikutnya boleh memakai anggaran otomatis lagi.
            if (stallRecoveries > 0 && now - playingSince >= VideoPlayback.STALL_STEADY_PLAY_MS) {
                stallRecoveries = 0
                AppLog.info(LogCategory.PLAYER, "Pemutaran video pulih setelah macet.")
            }
        } else {
            playingSince = 0L
        }
        if (exo.isPlaying && currentUri != null && now - lastResumeSaveAt >= 5_000) {
            resume.save(currentUri!!, position, duration)
            lastResumeSaveAt = now
        }
        recoverStalledPlayback(exo, now)
    }

    /**
     * ExoPlayer tidak selalu melempar error saat dekoder berhenti merespons; ia bisa bertahan di
     * STATE_BUFFERING selamanya. Setelah [VideoPlayback.STALL_TIMEOUT_MS], buka ulang pada posisi
     * yang sama. Percobaan kedua beralih ke software (kecuali pengguna memilih Perangkat saja),
     * dan bila masih macet spinner dihentikan agar pengguna bisa memilih tindakan.
     */
    private fun recoverStalledPlayback(exo: ExoPlayer, now: Long) {
        val current = _state.value
        if (!current.active) return
        if (!VideoPlayback.shouldRecoverStall(
                bufferingSinceMs = bufferingSince,
                nowMs = now,
                playWhenReady = exo.playWhenReady,
                hasError = current.error != null,
            )
        ) return
        if (exo.playbackState != Player.STATE_BUFFERING) return
        val position = VideoPlayback.reopenPosition(current.positionMs, exo.currentPosition)
        bufferingSince = 0L
        if (!VideoPlayback.canRecoverStall(stallRecoveries)) {
            VideoDiagnostics.stallFailed(position)
            AppLog.error(LogCategory.PLAYER, "Pemutaran video macet dan tidak dapat dipulihkan otomatis.")
            _state.value = current.copy(
                playing = false,
                buffering = false,
                ready = false,
                positionMs = position,
                error = "Pemutaran berhenti merespons. Coba lagi atau ganti dekoder.",
            )
            return
        }
        val softwareActive = current.decoderMode == VideoDecoderMode.SOFTWARE || softwareFallbackUsed
        // Mode Perangkat adalah pilihan manual, jadi watchdog tidak boleh menggantinya diam-diam.
        val forceSoftware = stallRecoveries >= 1 && !softwareActive &&
            current.decoderMode != VideoDecoderMode.HARDWARE
        stallRecoveries += 1
        VideoDiagnostics.stall(stallRecoveries, position, software = softwareActive || forceSoftware)
        AppLog.warn(LogCategory.PLAYER, "Pemutaran video macet; membuka ulang otomatis (percobaan $stallRecoveries).")
        if (forceSoftware) softwareFallbackUsed = true
        val startPlaying = exo.playWhenReady
        val entries = current.entries
        val index = current.index.coerceIn(entries.indices)
        _state.value = current.copy(
            playing = false,
            buffering = true,
            ready = false,
            positionMs = position,
            error = null,
        )
        // Jangan melepas pemutar dari dalam ticker yang sedang berjalan: releasePlayer()
        // membatalkan ticker. Jalankan pemulihan di coroutine terpisah, lalu ticker baru dibuat
        // oleh openInternal().
        viewModelScope.launch {
            delay(1)
            if (!_state.value.active) return@launch
            releasePlayer()
            openInternal(
                entries = entries,
                start = index,
                startPosition = position,
                preferSoftware = softwareActive || forceSoftware,
                startPlaying = startPlaying,
            )
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
        if (pendingSubtitleSelection) {
            if (text.isEmpty()) return
            // Subtitle hasil unduhan langsung aktif; di antara pilihan yang ada, bahasa ponsel
            // menang sehingga pengguna tidak perlu menekan CC setiap kali membuka video.
            val pick = VideoPlayback.preferredSubtitleIndex(
                languages = text.map { it.language },
                downloadedLanguages = pendingSubtitleLanguages,
                preferredLanguages = deviceLanguages(),
            )
            pendingSubtitleSelection = false
            text.getOrNull(pick)?.let(::selectTrack)
            return
        }
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

    private fun playbackError(error: PlaybackException): String {
        // MediaStore pada Android 10-11 melempar IllegalArgumentException("Failed to determine if … is
        // child of …") ketika berkasnya sudah dihapus, dan ExoPlayer membungkusnya sebagai
        // ERROR_CODE_IO_UNSPECIFIED. Pesan generik menyembunyikan penyebab yang sebenarnya.
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is java.io.FileNotFoundException) {
                return "File video tidak ditemukan. Mungkin sudah dipindah atau dihapus."
            }
            cause = cause.cause
        }
        return errorMessage(error)
    }

    private fun errorMessage(error: PlaybackException): String = when (error.errorCode) {
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
        PlaybackException.ERROR_CODE_TIMEOUT ->
            "Dekoder video melewati batas waktu. Coba lagi; TubeFlow akan memakai dekoder software bila perlu."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
            "Berkas video rusak atau kontainernya tidak dikenali."
        else -> "Tidak dapat memutar video (${error.errorCodeName})."
    }

    private fun preferencesState(): State = State(
        brightnessMode = storedEnum(KEY_BRIGHTNESS_MODE, VideoBrightnessMode.AUTO),
        manualBrightness = VideoBrightnessPolicy.clamp(preferences.getFloat(KEY_BRIGHTNESS_LEVEL, 0.5f)),
        volumeBoostDb = VideoVolumePolicy.boostDb(preferences.getInt(KEY_VOLUME_BOOST, 0)),
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
        const val KEY_LAST_URI = "last_uri"
        const val KEY_LAST_TITLE = "last_title"
        const val KEY_VOLUME_BOOST = "video_volume_boost_db"
        const val KEY_BRIGHTNESS_MODE = "brightness_mode"
        const val KEY_BRIGHTNESS_LEVEL = "brightness_level"
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
