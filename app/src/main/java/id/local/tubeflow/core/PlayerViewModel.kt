package id.local.tubeflow.core

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.MediaPlayer
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicLong

/** One local audio engine, confined to one Looper and shared with PlaybackService. */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val RESTART_MS = 3_000L
        /** Jeda sebelum satu percobaan ulang fokus audio; cukup pendek agar putar tetap terasa instan. */
        const val FOCUS_RETRY_MS = 700L
        const val ACTION_BT_HEADSET = "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED"
        const val ACTION_BT_A2DP = "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"
        /** Batas tunggu pemulihan sesi jeda agar permintaan auto-play tidak dibuang sia-sia. */
        private const val RESTORE_SETTLE_MS = 3_000L
        private const val RESTORE_POLL_MS = 100L
        /** Engine aktif untuk PlaybackService (notifikasi/MediaSession); selalu satu per proses. */
        @Volatile var active: PlayerViewModel? = null
        @Volatile private var built: PlayerViewModel? = null

        /**
         * Engine milik proses, bukan milik activity: menutup UI tidak boleh mematikan pemutaran
         * atau auto-play headset. Pemanggilan berikutnya mengembalikan instance yang sama.
         */
        fun obtain(app: Application): PlayerViewModel = synchronized(this) {
            built ?: PlayerViewModel(app).also { built = it }
        }

        /** Dipakai receiver headset saat proses bisa jadi baru dibangun tanpa UI. */
        fun autoPlayFromSystem(app: Application) { PlaybackService.requestAutoPlay(app) }
        fun hasRestorableSession(context: Context): Boolean =
            context.getSharedPreferences("player-last", 0).getBoolean("session_active", false)

    }
    data class Track(val uri: String, val title: String, val artist: String = "", val cover: String = "", val lyricUri: String? = null)
    data class State(val track: Track? = null, val loading: Boolean = false, val playing: Boolean = false,
                     val positionMs: Long = 0, val durationMs: Long = 0, val lines: List<LrcLines.Line> = emptyList(),
                     val error: String? = null, val lyricError: String? = null, val speed: Float = 1f,
                     val queueTracks: List<Track> = emptyList(), val queueIndex: Int = 0, val queueHasNext: Boolean = false,
                     val shuffle: Boolean = false, val repeat: PlaybackQueue.Repeat = PlaybackQueue.Repeat.OFF)
    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    private val thread = HandlerThread("TubeFlow-audio").apply { start() }
    private val handler = Handler(thread.looper)
    private val engineDispatcher = handler.asCoroutineDispatcher("TubeFlow-audio")
    private val manager = application.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val token = AtomicLong(0)
    private var media: MediaPlayer? = null
    // Antrean putar hanya disentuh pada handler thread yang sama dengan MediaPlayer.
    private var queue: PlaybackQueue<Track>? = null
    val equalizer = PlayerEqualizer(application)
    private val sleeper = SleepTimer()
    data class SleepState(val remainingMs: Long = 0, val afterTrack: Boolean = false)
    private val _sleep = MutableStateFlow(SleepState())
    val sleep = _sleep.asStateFlow()
    private val sleepTick = object : Runnable {
        override fun run() {
            val now = android.os.SystemClock.elapsedRealtime()
            if (sleeper.due(now)) {
                sleeper.clear(); _sleep.value = SleepState(); pauseInternal(true)
                if (prepared) runCatching { media?.setVolume(1f, 1f) }
                return
            }
            _sleep.value = SleepState(sleeper.remaining(now), sleeper.afterTrack)
            if (prepared) runCatching { val gain = sleeper.gain(now); media?.setVolume(gain, gain) }
            if (sleeper.deadlineMs > 0) handler.postDelayed(this, 250)
        }
    }
    private fun stopAtTrackBoundary(): Boolean {
        if (!sleeper.finishTrack()) return false
        _sleep.value = SleepState(); pauseInternal(true)
        return true
    }
    fun setSleepTimer(minutes: Int?, afterTrack: Boolean = false) = handler.post {
        handler.removeCallbacks(sleepTick)
        when {
            afterTrack -> sleeper.endOfTrack()
            minutes != null -> sleeper.minutes(minutes, android.os.SystemClock.elapsedRealtime())
            else -> sleeper.clear()
        }
        sleepTick.run()
    }
    private data class ComparisonSession(val source: String, val instrumental: String, val startMs: Long, val durationMs: Long,
        val previous: PlaybackQueue<Track>?, val positionMs: Long, val playing: Boolean)
    private var comparison: ComparisonSession? = null
    private val _comparing = MutableStateFlow(false)
    val comparing = _comparing.asStateFlow()
    /** The same engine reloads at an aligned position; the original queue snapshot is retained. */
    fun compare(track: Track, sourceUri: String, instrumentalUri: String, startMs: Long, durationMs: Long) = handler.post {
        val current = _state.value
        val first = comparison?.instrumental != instrumentalUri
        if (first) {
            finishComparisonInternal()
            val previous = _state.value
            comparison = ComparisonSession(sourceUri, instrumentalUri, startMs, durationMs, queue, previous.positionMs, previous.playing)
        }
        _comparing.value = true
        val fromInstrumental = current.track?.uri == instrumentalUri
        val position = if (current.track?.uri in setOf(sourceUri, instrumentalUri)) current.positionMs else startMs
        val target = KaraokeComparison.target(position, fromInstrumental, track.uri == instrumentalUri, startMs, durationMs)
        load(PlaybackQueue(listOf(track)), playWhenReady = first || current.playing, startPositionMs = target)
    }
    fun finishComparison() = handler.post { finishComparisonInternal() }
    /** Cleanup can release the preview even when the original side of A/B is currently playing. */
    fun discardComparison(uri: String) = handler.post {
        if (comparison?.instrumental == uri) finishComparisonInternal()
        else if (_state.value.track?.uri == uri) stop()
    }
    private fun finishComparisonInternal() {
        val saved = comparison ?: return
        val current = _state.value
        comparison = null; _comparing.value = false
        val sourcePosition = if (current.track?.uri == saved.instrumental) current.positionMs + saved.startMs else current.positionMs
        val previous = saved.previous
        if (previous != null) load(previous, playWhenReady = saved.playing && current.playing,
            startPositionMs = if (previous.current.uri == saved.source) sourcePosition else saved.positionMs)
        else { token.incrementAndGet(); _state.value = State(); queue = null; release() }
    }
    private val sessionStore = PlayerSessionStore(application)
    private var lastCheckpoint = 0L
    private val fx = application.getSharedPreferences("player-fx", 0)
    private var speed = fx.getFloat("speed", 1f).coerceIn(0.25f, 3f)
    // Acak/ulangi adalah preferensi pengguna, bukan milik satu antrean. Antrean baru mewarisinya,
    // jadi memulai lagu/antrean berikutnya tidak lagi mengembalikan tombol ke posisi mati.
    private var shufflePref = fx.getBoolean("shuffle", false)
    private var repeatPref = PlaybackQueue.Repeat.values().getOrElse(fx.getInt("repeat", 0)) { PlaybackQueue.Repeat.OFF }
    private var engineToken = -1L
    private var prepared = false
    private var autoplay = false
    @Volatile private var allowInterruptionResume = application.getSharedPreferences("settings", 0).getBoolean("resume_interruption", true)
    private val interruption = PlaybackInterruption()
    private var interruptionReadyAt = 0L
    private val observePlayback = Runnable { observeInterruptedPlayback() }
    private val resumeAfterQuiet = Runnable {
        if (allowInterruptionResume && engineToken == token.get() && prepared && !_state.value.playing) {
            // Facebook can request permanent focus, which removes us from Android's focus stack.
            // Reclaim once after observed audio ends; never poll focus or use the headset retry loop.
            if (interruption.takeAfterQuiet(otherAudioActive(), manager.mode == AudioManager.MODE_NORMAL)) {
                if (claimFocus(engineToken)) resumeWithoutFocus()
            } else if (interruption.needsPlaybackEvents) handler.postDelayed(observePlayback, 1000)
        }
    }
    private val playbackActivity = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            // Read fresh state instead of trusting a queued snapshot from before our own pause.
            observeInterruptedPlayback()
        }
    }
    private var request: AudioFocusRequest? = null
    private var focusSequence = 0L
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause() }
    }

    /** Called only after PlaybackService has entered the foreground. */
    internal suspend fun resumeForConnectedDevice() {
        if (_state.value.playing) {
            AppLog.info(LogCategory.PLAYER, "Auto-play dilewati: pemutar sudah memutar.")
            return
        }
        val collection = withContext(Dispatchers.IO) {
            DownloadDatabase.get(getApplication()).media().availableAudio().mapNotNull(::trackOf)
        }
        // Proses baru memulihkan sesi dalam keadaan jeda, dan pemulihan itu bisa selesai di tengah
        // perencanaan. Satu pemeriksaan saja pernah membuang permintaan headset; perencanaan kini
        // diulang sampai pemulihan selesai atau batas tunggu habis.
        var waited = 0L
        while (!tryResumeForConnectedDevice(collection)) {
            if (waited >= RESTORE_SETTLE_MS) {
                AppLog.warn(LogCategory.PLAYER,
                    "Auto-play dilewati: sesi masih dipulihkan setelah ${RESTORE_SETTLE_MS} ms.")
                return
            }
            delay(RESTORE_POLL_MS)
            waited += RESTORE_POLL_MS
        }
    }

    /**
     * Satu percobaan perencanaan antrean. `false` berarti pemulihan sesi masih berjalan dan pemanggil
     * mengulanginya; `true` berarti permintaan sudah ditangani atau memang tidak ada yang diputar.
     */
    private suspend fun tryResumeForConnectedDevice(collection: List<Track>): Boolean = withContext(engineDispatcher) {
            val current = _state.value
            if (current.playing) return@withContext true
            if (current.loading) return@withContext false
            // A fresh headset connection returns to public playback, never a looping private preview.
            val preview = comparison
            val last = if (preview != null) collection.firstOrNull { it.uri == preview.source }
                ?: preview.previous?.current ?: lastTrack() else current.track ?: lastTrack()
            comparison = null; _comparing.value = false
            val planned = AutoPlayQueue.plan(collection, last?.uri) { it.uri }
            AppLog.info(LogCategory.PLAYER, when {
                collection.isEmpty() -> "Auto-play headset: belum ada audio di Koleksi."
                planned == null -> "Auto-play headset: antrean tidak dapat disusun."
                else -> "Auto-play headset: ${planned.first.size} lagu di antrean, mulai dari ${planned.second + 1}."
            })
            when {
                planned == null && preview != null -> if (last != null) {
                    load(PlaybackQueue(listOf(last)).withPreferences(shufflePref, repeatPref))
                } else stop()
                planned == null -> if (current.track != null && prepared) {
                    autoplay = true; start(token.get())
                } else last?.let { load(PlaybackQueue(listOf(it)).withPreferences(shufflePref, repeatPref)) }
                current.track != null && prepared && current.track.uri == planned.first[planned.second].uri -> {
                    val expanded = PlaybackQueue(planned.first, planned.second).withPreferences(shufflePref, repeatPref)
                    queue = expanded
                    syncQueue(expanded)
                    autoplay = true
                    start(token.get())
                }
                else -> load(PlaybackQueue(planned.first, planned.second).withPreferences(shufflePref, repeatPref),
                    startPositionMs = if (preview != null && current.track?.uri == preview.instrumental && last?.uri == preview.source)
                        KaraokeComparison.target(current.positionMs, true, false, preview.startMs, preview.durationMs) else 0)
            }
            true
    }

    /**
     * Rebuild a process-death snapshot paused. Only an explicit connection event may auto-play.
     * [allowLastTrack] dipakai shortcut "Pemutar audio": bila sesi sudah tidak aktif, lagu terakhir
     * tetap dibuka di pemutar penuh alih-alih hanya menampilkan Koleksi.
     */
    internal suspend fun restorePlaybackSession(allowLastTrack: Boolean = false) {
        if (_state.value.track != null) return
        if (!allowLastTrack && !hasRestorableSession(getApplication())) return
        val expected = token.get()
        val restored = withContext(Dispatchers.IO) {
            fun readable(track: Track) = runCatching {
                getApplication<Application>().contentResolver.openFileDescriptor(track.uri.toUri(), "r")?.use { true } == true
            }.getOrDefault(false)
            val saved = sessionStore.read()
            val savedPlan = saved?.let { session ->
                PlaybackQueue.restore(session.queue, ::readable)?.let { planned ->
                    val same = planned.snapshot().entries[planned.index].first == session.queue.entries[session.queue.index].first
                    planned to if (same) session.positionMs else 0L
                }
            }
            // Snapshot bisa rusak karena berkas sudah dipindah. Jatuh ke lagu terakhir yang masih
            // terbaca, terutama saat shortcut "Pemutar audio" meminta pemulihan eksplisit.
            savedPlan ?: lastTrack()?.takeIf(::readable)?.let {
                PlaybackQueue(listOf(it)).withPreferences(shufflePref, repeatPref) to 0L
            }
        }
        withContext(engineDispatcher) {
            if (expected != token.get() || _state.value.track != null) return@withContext
            if (restored != null) load(restored.first, playWhenReady = false, startPositionMs = restored.second)
            else {
                sessionStore.offer(null)
                getApplication<Application>().getSharedPreferences("player-last", 0).edit { putBoolean("session_active", false) }
            }
        }
    }
    private fun checkpoint(force: Boolean = false) {
        if (engineToken != token.get() || _state.value.track == null || _state.value.track?.uri?.startsWith("file:") == true || comparison != null) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && now - lastCheckpoint < 5000) return
        val planned = queue ?: return
        lastCheckpoint = now
        sessionStore.offer(PlayerSession.Saved(planned.snapshot(), _state.value.positionMs.coerceAtLeast(0)))
    }
    /** Lagu terakhir dipersist agar auto-play headset tetap punya sumber setelah pemutar kosong. */
    private fun rememberLast(track: Track) {
        if (track.uri.startsWith("file:") || comparison != null) return
        getApplication<Application>().getSharedPreferences("player-last", 0).edit {
            putBoolean("session_active", true)
            putString("uri", track.uri).putString("title", track.title).putString("artist", track.artist)
                .putString("cover", track.cover).putString("lyrics", track.lyricUri.orEmpty())
        }
    }
    private fun lastTrack(): Track? {
        val saved = getApplication<Application>().getSharedPreferences("player-last", 0)
        val uri = saved.getString("uri", null)?.takeIf { it.isNotBlank() } ?: return null
        return Track(uri, saved.getString("title", "").orEmpty(), saved.getString("artist", "").orEmpty(),
            saved.getString("cover", "").orEmpty(), saved.getString("lyrics", "").orEmpty().takeIf { it.isNotBlank() })
    }
    init {
        active = this
        viewModelScope.launch(Dispatchers.IO) { sessionStore.writePending() }
        viewModelScope.launch { restorePlaybackSession() }
        ContextCompat.registerReceiver(application, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        manager.registerAudioPlaybackCallback(playbackActivity, handler)
        // Satu titik sinkron service, termasuk seek ketika jeda dan progres per detik.
        viewModelScope.launch {
            var previous = _state.value
            _state.collect { current ->
                if (PlaybackNotifications.shouldUpdate(previous, current)) {
                    PlaybackService.sync(getApplication<Application>(), current)
                    previous = current
                }
            }
        }
    }
    private fun updateEngine(change: (State) -> State) {
        _state.update { if (engineToken == token.get()) change(it) else it }
    }
    private val tick = object : Runnable {
        override fun run() {
            val current = media
            if (current != null && prepared && engineToken == token.get()) try {
                val position = current.currentPosition.toLong()
                val preview = comparison
                if (preview != null && _state.value.track?.uri == preview.source && position >= preview.startMs + preview.durationMs) {
                    if (!stopAtTrackBoundary()) current.seekTo(preview.startMs, MediaPlayer.SEEK_CLOSEST)
                }
                updateEngine { it.copy(positionMs = position, playing = current.isPlaying) }; checkpoint()
                if (current.isPlaying) handler.postDelayed(this, PlaybackPolicy.audioTickMs(PlaybackPower.saving, _state.value.track?.lyricUri != null))
            } catch (_: IllegalStateException) { fail("Pemutaran terhenti. Coba putar ulang.") }
        }
    }
    fun play(job: DownloadJob) { trackOf(job)?.let(::play) }
    fun play(item: MediaItem) { trackOf(item)?.let(::play) }    private fun trackOf(job: DownloadJob): Track? {
        if (job.mode != MediaMode.AUDIO || job.state != JobState.COMPLETED || job.outputUri == null) return null
        val song = job.songIdentity()
        val lyric = job.sidecarUris?.lines()?.firstOrNull { it.startsWith("sub|") && job.lyricStatus == LyricStatus.SAVED }?.substringAfter('|')
        return Track(job.outputUri, Format.musicLabel(song), song.artist.orEmpty().ifBlank { song.remixer }, job.coverPath, lyric)
    }
    /** Track identik dengan pemutaran normal; dipakai alat karaoke agar judul/artis tidak berbeda. */
    fun trackFor(item: MediaItem): Track? = trackOf(item)

    private fun trackOf(item: MediaItem): Track? =
        if (item.kind == "AUDIO" && item.availability == "AVAILABLE") Track(item.location, Format.musicLabel(TitleSplit.identify(item.title,metadataArtist=item.artist,recognizeCollaboration=false)), item.artist, item.coverPath, item.lyricUri) else null

    /** Putar satu lagu: antrean satu item sehingga next/prev tetap mengikuti aturan antrean. */
    fun play(track: Track) = startQueue(listOf(track), start = track)
    /**
     * Putar dari daftar: SELURUH daftar masuk antrean dan mulai dari [startAt], bukan satu item saja.
     * [shuffle] null = ikuti preferensi pengguna; true/false = niat eksplisit untuk antrean ini saja.
     */
    fun playAll(items: List<MediaItem>, startAt: Int = 0, shuffle: Boolean? = null) =
        startQueue(items.mapNotNull(::trackOf), items.getOrNull(startAt)?.let(::trackOf), shuffle)

    /** Antrean berkas luar (mis. dari "Buka dengan"): urutan folder yang sama, mulai dari indeks ini. */
    fun playTracks(tracks: List<Track>, startAt: Int = 0) =
        startQueue(tracks, tracks.getOrNull(startAt))
    fun playJobs(jobs: List<DownloadJob>, startAt: Int = 0) =
        startQueue(jobs.mapNotNull(::trackOf), jobs.getOrNull(startAt)?.let(::trackOf))
    private fun startQueue(tracks: List<Track>, start: Track?, shuffle: Boolean? = null) {
        if (tracks.isEmpty()) return
        val index = start?.let { wanted -> tracks.indexOfFirst { it.uri == wanted.uri }.coerceAtLeast(0) } ?: 0
        if (shuffle == true) persistShuffle(true)
        handler.post {
            comparison = null; _comparing.value = false
            load(PlaybackQueue(tracks, index).withPreferences(shuffle ?: shufflePref, repeatPref))
        }
    }
    private fun persistShuffle(enabled: Boolean) {
        shufflePref = enabled
        fx.edit { putBoolean("shuffle", enabled) }
    }
    private fun persistRepeat(repeat: PlaybackQueue.Repeat) {
        repeatPref = repeat
        fx.edit { putInt("repeat", repeat.ordinal) }
    }
    /** Sisipkan setelah lagu aktif. Tanpa antrean aktif lagu langsung diputar agar tidak ada antrean tersembunyi. */
    fun playNext(item: MediaItem) { trackOf(item)?.let { addToQueue(it, next = true) } }
    fun enqueue(item: MediaItem) { trackOf(item)?.let { addToQueue(it, next = false) } }
    /** Varian dari Riwayat: job selesai sudah membawa URI, sampul, dan identitas lagu. */
    fun playNext(job: DownloadJob) { trackOf(job)?.let { addToQueue(it, next = true) } }
    fun enqueue(job: DownloadJob) { trackOf(job)?.let { addToQueue(it, next = false) } }
    private fun addToQueue(track: Track, next: Boolean) = handler.post {
        val current = queue
        if (current == null || _state.value.track == null) { load(PlaybackQueue(listOf(track))); return@post }
        val updated = if (next) current.addNext(track) else current.add(track)
        queue = updated
        syncQueue(updated)
    }
    fun jumpTo(position: Int) = handler.post {
        val updated = queue?.jumpTo(position) ?: return@post
        load(updated)
    }
    /**
     * Pindahkan posisi di antrean tanpa menyentuh pemutaran: lagu aktif tetap berbunyi dari
     * posisi yang sama, hanya urutan antreannya yang berubah.
     */
    fun moveInQueue(from: Int, to: Int) = handler.post {
        val current = queue ?: return@post
        val updated = current.move(from, to) ?: return@post
        queue = updated
        syncQueue(updated)
    }
    /** "Bersihkan antrean": sisakan lagu aktif, pemutaran berjalan tanpa terputus. */
    fun keepCurrentOnly() = handler.post {
        val current = queue ?: return@post
        val updated = current.onlyCurrent() ?: return@post
        queue = updated
        syncQueue(updated)
    }
    fun removeFromQueue(position: Int) = handler.post {
        val current = queue ?: return@post
        if (position !in 0 until current.size) return@post
        val updated = current.removeAt(position)
        if (updated == null) { queue = null; _state.value = State(); release(); return@post }
        queue = updated
        if (position == current.index) load(updated) else syncQueue(updated)
    }
    fun toggleShuffle() = handler.post { queue?.toggleShuffle()?.let { queue = it; persistShuffle(it.shuffled); syncQueue(it) } }
    fun cycleRepeat() = handler.post { queue?.cycleRepeat()?.let { queue = it; persistRepeat(it.repeat); syncQueue(it) } }
    fun next() = handler.post { queue?.next()?.let { load(it) } }
    fun previous() = handler.post {
        // Aturan lazim: >3 detik ke dalam lagu mengulang dari awal, selain itu kembali ke lagu sebelumnya.
        if (prepared && _state.value.positionMs > RESTART_MS) {
            try { media?.seekTo(0, MediaPlayer.SEEK_CLOSEST); updateEngine { it.copy(positionMs = 0) } }
            catch (_: IllegalStateException) { fail("Posisi audio belum dapat diubah.") }
            return@post
        }
        queue?.previous()?.let { load(it) } ?: seek(0)
    }
    private fun syncQueue(current: PlaybackQueue<Track>) {
        updateEngine { it.copy(queueTracks = current.tracks, queueIndex = current.index, queueHasNext = current.hasNext,
            shuffle = current.shuffled, repeat = current.repeat) }
        checkpoint(force = true)
    }
    private fun load(planned: PlaybackQueue<Track>, playWhenReady: Boolean = true, startPositionMs: Long = 0, retryOnError: Boolean = true) {
        queue = planned
        val track = planned.current
        AppLog.info(LogCategory.PLAYER, "Memuat audio ke pemutar (${planned.tracks.size} lagu di antrean).")
        rememberLast(track)
        val id = token.incrementAndGet()
        _state.value = State(track=track, loading=true, positionMs=startPositionMs.coerceAtLeast(0), speed=speed, queueTracks=planned.tracks, queueIndex=planned.index,
            queueHasNext=planned.hasNext, shuffle=planned.shuffled, repeat=planned.repeat)
        loadLyrics(track,id)
        handler.post {
            if (token.get() != id) return@post
            release()
            engineToken = id
            autoplay = playWhenReady
            checkpoint(force = true)
            val current = MediaPlayer(); media = current
            var initialSeek = false
            fun readyToPlay() {
                prepared = true
                updateEngine { it.copy(loading = false, positionMs = current.currentPosition.toLong()) }
                checkpoint(force = true)
                if (autoplay) start(id)
            }
            try {
                current.setAudioAttributes(attributes)
                current.setOnPreparedListener { ready ->
                    if (media !== ready || token.get() != id) return@setOnPreparedListener
                    equalizer.attach(ready.audioSessionId)
                    equalizer.followGenre(track.uri)
                    val duration = ready.duration.toLong().coerceAtLeast(0)
                    val target = startPositionMs.coerceIn(0, (duration - 1).coerceAtLeast(0))
                    updateEngine { it.copy(durationMs = duration, positionMs = target) }
                    if (target > 0) {
                        initialSeek = true
                        ready.seekTo(target, MediaPlayer.SEEK_CLOSEST)
                    } else readyToPlay()
                }
                current.setOnCompletionListener { completed ->
                    if (media !== completed || token.get() != id) return@setOnCompletionListener
                    if (stopAtTrackBoundary()) {
                        updateEngine { it.copy(positionMs = it.durationMs) }
                        return@setOnCompletionListener
                    }
                    // Completion may already be queued when the user pauses a short preview.
                    // Never let repeat/next override that explicit intent.
                    if (!autoplay) { updateEngine { it.copy(playing = false) }; checkpoint(force = true); return@setOnCompletionListener }
                    if (comparison != null) {
                        completed.seekTo(if (_state.value.track?.uri == comparison?.source) comparison!!.startMs else 0, MediaPlayer.SEEK_CLOSEST)
                        completed.start(); updateEngine { it.copy(playing = true) }
                        handler.removeCallbacks(tick); handler.post(tick)
                        return@setOnCompletionListener
                    }
                    val advanced = queue?.onCompleted()
                    when {
                        // Repeat ONE memutar ulang lagu yang sama tanpa membuat engine baru.
                        queue?.repeat == PlaybackQueue.Repeat.ONE -> try {
                            completed.seekTo(0)
                            completed.start()
                            updateEngine { it.copy(playing=true, positionMs=0) }
                            handler.removeCallbacks(tick); handler.post(tick)
                        } catch (_: IllegalStateException) { fail("Audio tidak dapat diulang. Coba putar ulang.") }
                        advanced != null -> load(advanced)
                        else -> { updateEngine { it.copy(playing=false, positionMs=it.durationMs) }; checkpoint(force = true); abandon() }
                    }
                }
                var retried = !retryOnError
                var retryScheduled = false
                current.setOnErrorListener { failed, what, extra ->
                    // Satu MediaPlayer dapat memanggil error lagi sebelum pemuatan ulang berjalan.
                    if (retryScheduled) return@setOnErrorListener true
                    if (media === failed && token.get() == id) {
                        // Perintah tidak sah saat pemutar dijeda (mis. pause() ganda) hanya diabaikan
                        // platform: pemutar masih siap, jadi sesi tidak boleh dihentikan.
                        if (!autoplay && !_state.value.playing && AudioErrorPolicy.benignWhilePaused(what)) {
                            android.util.Log.w("TubeFlow-audio",
                                "MediaPlayer ${AudioErrorPolicy.label(what, extra)}; perintah diabaikan saat jeda")
                            AppLog.debug(LogCategory.PLAYER, "Perintah pemutar diabaikan saat jeda (${AudioErrorPolicy.label(what, extra)}).")
                            return@setOnErrorListener true
                        }
                        val position = runCatching { failed.currentPosition.toLong() }
                            .getOrDefault(_state.value.positionMs.coerceAtLeast(0))
                        val planned = queue
                        if (planned != null && AudioErrorPolicy.shouldRetry(what, retried)) {
                            retried = true
                            retryScheduled = true
                            android.util.Log.w("TubeFlow-audio",
                                "MediaPlayer ${AudioErrorPolicy.label(what, extra)}; memuat ulang sekali")
                            AppLog.warn(LogCategory.PLAYER, "Pemutar audio mencoba memuat ulang setelah ${AudioErrorPolicy.label(what, extra)}.")
                            handler.post {
                                if (token.get() == id) {
                                    load(planned, playWhenReady = autoplay, startPositionMs = position, retryOnError = false)
                                }
                            }
                        } else {
                            android.util.Log.w("TubeFlow-audio",
                                "MediaPlayer ${AudioErrorPolicy.label(what, extra)}; berhenti")
                            AppLog.error(LogCategory.PLAYER, "Pemutar audio berhenti: ${AudioErrorPolicy.label(what, extra)}.")
                            fail("Audio tidak dapat diputar. Periksa file atau codec perangkat.")
                        }
                    }
                    true
                }
                current.setOnSeekCompleteListener { sought ->
                    if (media === sought && token.get() == id) {
                        if (initialSeek) { initialSeek = false; readyToPlay() }
                        else if (prepared) { updateEngine { it.copy(positionMs=sought.currentPosition.toLong()) }; checkpoint(force = true) }
                    }
                }
                // Data-source I/O and every MediaPlayer method stay off the UI thread.
                current.setDataSource(getApplication<Application>(), track.uri.toUri())
                current.prepareAsync()
            } catch (error: Exception) {
                if (token.get() == id) {
                    AppLog.error(LogCategory.PLAYER, "Berkas audio tidak dapat dibuka.", error)
                    fail("File audio tidak tersedia atau aksesnya berubah.")
                } else release()
            }
        }
    }
    /** Kecepatan putar (0,25–3×) berlaku untuk track aktif dan track berikutnya. */
    fun setSpeed(value: Float) { handler.post {
        speed = value.coerceIn(0.25f, 3f)
        getApplication<Application>().getSharedPreferences("player-fx", 0).edit { putFloat("speed", speed) }
        if (_state.value.playing) runCatching { media?.let { current -> current.playbackParams = current.playbackParams.setSpeed(speed) } }
        updateEngine { it.copy(speed=speed) }
    } }
    /** A replacement can have a new URI; reload the persisted catalogue reference. */
    fun refreshLyrics() {
        val track=_state.value.track ?: return
        val id=token.get()
        viewModelScope.launch {
            val item=withContext(Dispatchers.IO) { DownloadDatabase.get(getApplication()).media().let { it.getByAccess(track.uri) ?: it.get(track.uri) } }
            if (id != token.get()) return@launch
            val updated=track.copy(lyricUri=item?.lyricUri ?: track.lyricUri)
            _state.update { if (id == token.get()) it.copy(track=updated) else it }
            loadLyrics(updated,id)
        }
    }
    fun reloadLyrics() { _state.value.track?.let { loadLyrics(it,token.get()) } }
    private fun loadLyrics(track: Track, id: Long) {
        viewModelScope.launch {
            if (track.lyricUri == null) return@launch
            val result = withContext(Dispatchers.IO) { MediaFileOperations.mutex.withLock { runCatching {
                getApplication<Application>().contentResolver.openInputStream(track.lyricUri.toUri())?.use { input ->
                    val bytes = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                    while (true) { val read = input.read(buffer); if (read < 0) break; require(bytes.size() + read <= 1024 * 1024); bytes.write(buffer, 0, read) }
                    LrcLines.parse(bytes.toString("UTF-8"))
                } ?: error("LRC tidak dapat dibaca.")
            } } }
            if (token.get() == id) _state.update { if (token.get() == id) it.copy(lines=result.getOrDefault(emptyList()), lyricError=if (result.isFailure) "Lirik belum dapat dibaca." else null) else it }
        }
    }
    /** Permintaan fokus yang sama dipakai putar normal dan percobaan ulang setelah fokus ditolak. */
    private fun focusRequest(id: Long, sequence: Long) = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes).setWillPauseWhenDucked(true).setOnAudioFocusChangeListener({ change ->
            if (token.get() != id || sequence != focusSequence) return@setOnAudioFocusChangeListener
            when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> if (allowInterruptionResume && interruption.takeFocusGain()) { cancelInterruption(); resumeWithoutFocus() }
                AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    val permanent = change == AudioManager.AUDIOFOCUS_LOSS
                    interruption.lost(permanent, _state.value.playing, allowInterruptionResume)
                    pauseInternal(false)
                    if (permanent || !allowInterruptionResume) abandon()
                    // Give our own AudioTrack stop event time to settle before observing the blocker.
                    interruptionReadyAt = android.os.SystemClock.elapsedRealtime() + 250
                    handler.removeCallbacks(observePlayback)
                    handler.removeCallbacks(resumeAfterQuiet)
                    handler.postDelayed(observePlayback, 250)
                }
            }
        }, handler).build()
    private fun otherAudioActive(): Boolean = try {
        manager.isMusicActive || manager.activePlaybackConfigurations.isNotEmpty()
    } catch (_: RuntimeException) { true } // Unknown playback state must not steal focus.
    private fun observeInterruptedPlayback() {
        handler.removeCallbacks(observePlayback)
        handler.removeCallbacks(resumeAfterQuiet)
        if (!allowInterruptionResume || !interruption.needsPlaybackEvents || !prepared || engineToken != token.get() || _state.value.playing) return
        val settling = interruptionReadyAt - android.os.SystemClock.elapsedRealtime()
        if (settling > 0) {
            handler.removeCallbacks(observePlayback)
            handler.postDelayed(observePlayback, settling)
            return
        }
        if (interruption.playbackChanged(otherAudioActive())) handler.postDelayed(resumeAfterQuiet, 750)
        // Native AudioTrack users may not publish Java playback callbacks (reproduced on Android10).
        // Check only while this track awaits recovery; this never repeatedly requests audio focus.
        else handler.postDelayed(observePlayback, 1000)
    }
    fun setInterruptionResume(enabled: Boolean) {
        allowInterruptionResume = enabled
        handler.post {
            if (!allowInterruptionResume) {
                cancelInterruption()
                if (!_state.value.playing) abandon()
            }
        }
    }
    private fun cancelInterruption() {
        interruption.cancel()
        handler.removeCallbacks(observePlayback)
        handler.removeCallbacks(resumeAfterQuiet)
    }
    private fun claimFocus(id: Long): Boolean {
        val sequence=++focusSequence
        val focus=focusRequest(id,sequence)
        request?.let(manager::abandonAudioFocusRequest)
        request=focus
        return manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
    private fun start(id: Long) {
        if (!prepared || token.get() != id) return
        cancelInterruption()
        if (claimFocus(id)) { resumeWithoutFocus(); return }
        // Auto-play setelah headset tersambung (proses baru dibangun dari latar) mudah kehilangan
        // permintaan fokus pertama saat aplikasi lain baru melepaskannya: ulangi sekali sebelum
        // melaporkan gagal, tanpa membuat engine MediaPlayer baru.
        handler.postDelayed({ retryFocus(id) }, FOCUS_RETRY_MS)
    }
    private fun retryFocus(id: Long) {
        if (token.get() != id || !prepared || !autoplay || _state.value.playing) return
        if (claimFocus(id)) { resumeWithoutFocus(); return }
        abandon(); updateEngine { it.copy(error="Audio sedang dipakai aplikasi lain. Coba putar lagi.") }
    }
    private fun resumeWithoutFocus() {
        try {
            val current = media ?: return
            if (!prepared || engineToken != token.get()) return
            if (_state.value.durationMs > 0 && current.currentPosition.toLong() >= _state.value.durationMs) current.seekTo(0)
            runCatching { current.playbackParams = current.playbackParams.setSpeed(speed) }
            val gain = sleeper.gain(android.os.SystemClock.elapsedRealtime()); current.setVolume(gain, gain)
            current.start(); autoplay=true; updateEngine { it.copy(playing=true, error=null) }
            handler.removeCallbacks(tick); handler.post(tick)
        } catch (_: IllegalStateException) { fail("Audio tidak dapat dilanjutkan. Coba putar ulang.") }
    }
    fun toggle() {
        val id = token.get()
        val current = _state.value
        if (current.track == null) return
        handler.post {
            if (id != token.get()) return@post
            if (!prepared && !_state.value.loading) { play(requireNotNull(current.track)); return@post }
            if (_state.value.playing || (_state.value.loading && autoplay)) pauseInternal(true)
            else { autoplay=true; start(id) }
        }
    }
    fun pause() { handler.post { pauseInternal(true) } }
    private fun pauseInternal(relinquish: Boolean) {
        autoplay=false
        // Hanya perintah saat pemutar benar-benar berjalan yang sah; state lain dilaporkan
        // platform sebagai error -38 (lihat AudioPausePolicy).
        if (AudioPausePolicy.shouldPause(prepared, runCatching { media?.isPlaying }.getOrNull())) {
            runCatching { media?.pause() }
        }
        handler.removeCallbacks(tick); updateEngine { it.copy(playing=false) }; checkpoint(force = true)
        if (relinquish) { cancelInterruption(); abandon() }
    }
    fun seek(positionMs: Long) { val id=token.get(); handler.post {
        if (token.get() != id || !prepared) return@post
        val target=positionMs.coerceIn(0, _state.value.durationMs)
        try { media?.seekTo(target, MediaPlayer.SEEK_CLOSEST); updateEngine { it.copy(positionMs=target) } }
        catch (_: IllegalStateException) { fail("Posisi audio belum dapat diubah.") }
    } }
    fun skip(deltaMs: Long) = seek(_state.value.positionMs + deltaMs)
    fun stop() {
        getApplication<Application>().getSharedPreferences("player-last", 0).edit { putBoolean("session_active", false) }
        token.incrementAndGet(); _state.value=State(); handler.post { comparison = null; _comparing.value = false; sleeper.clear(); handler.removeCallbacks(sleepTick); _sleep.value = SleepState(); queue=null; sessionStore.offer(null); equalizer.release(); release() }
    }
    private fun abandon() { focusSequence++; request?.let(manager::abandonAudioFocusRequest); request=null }
    private fun release() { handler.removeCallbacks(tick); cancelInterruption(); abandon(); prepared=false; autoplay=false; equalizer.release(); media?.release(); media=null }
    private fun fail(message: String) { release(); updateEngine { it.copy(loading=false, playing=false, error=message) } }
    override fun onCleared() {
        token.incrementAndGet()
        if (active === this) active = null
        getApplication<Application>().unregisterReceiver(noisy)
        manager.unregisterAudioPlaybackCallback(playbackActivity)
        PlaybackService.sync(getApplication<Application>(), State())
        handler.post { handler.removeCallbacks(sleepTick); release(); thread.quitSafely() }
    }
}
