package id.local.tubeflow.core

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicLong

/** One local audio engine, confined to one Looper. No background-playback service is advertised. */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val RESTART_MS = 3_000L
        /** Jeda sebelum satu percobaan ulang fokus audio; cukup pendek agar putar tetap terasa instan. */
        const val FOCUS_RETRY_MS = 700L
        const val ACTION_BT_HEADSET = "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED"
        const val ACTION_BT_A2DP = "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"
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
        fun autoPlayFromSystem(app: Application) { obtain(app).autoPlayOnDevice() }

        /** Jenis output yang memicu auto-play: headset kabel, TWS/Bluetooth, dan USB audio. */
        private val AUTOPLAY_OUTPUTS = setOf(
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
            android.media.AudioDeviceInfo.TYPE_USB_DEVICE,
        )
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
    private val manager = application.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val token = AtomicLong(0)
    private var media: MediaPlayer? = null
    // Antrean putar hanya disentuh pada handler thread yang sama dengan MediaPlayer.
    private var queue: PlaybackQueue<Track>? = null
    val equalizer = PlayerEqualizer(application)
    private val fx = application.getSharedPreferences("player-fx", 0)
    private var speed = fx.getFloat("speed", 1f).coerceIn(0.25f, 3f)
    // Acak/ulangi adalah preferensi pengguna, bukan milik satu antrean. Antrean baru mewarisinya,
    // jadi memulai lagu/antrean berikutnya tidak lagi mengembalikan tombol ke posisi mati.
    private var shufflePref = fx.getBoolean("shuffle", false)
    private var repeatPref = PlaybackQueue.Repeat.values().getOrElse(fx.getInt("repeat", 0)) { PlaybackQueue.Repeat.OFF }
    private var engineToken = -1L
    private var prepared = false
    private var autoplay = false
    private var transientResume = false
    private var request: AudioFocusRequest? = null
    private var focusSequence = 0L
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause() }
    }
    /** Headset/TWS baru tersambung: musik yang sedang berhenti dilanjutkan otomatis tanpa sentuhan layar. */
    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            if (addedDevices.any { it.isSink && it.type in AUTOPLAY_OUTPUTS }) autoPlayOnDevice()
        }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {}
    }
    /** Pemicu cadangan: sebagian perangkat lebih cepat melaporkan aksi headset/Bluetooth daripada AudioDeviceCallback. */
    private val devicePlug = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_HEADSET_PLUG -> if (intent.getIntExtra("state", -1) == 1) autoPlayOnDevice()
                ACTION_BT_A2DP, ACTION_BT_HEADSET -> if (intent.getIntExtra(android.bluetooth.BluetoothProfile.EXTRA_STATE, -1) ==
                    android.bluetooth.BluetoothProfile.STATE_CONNECTED) autoPlayOnDevice()
            }
        }
    }
    private fun autoPlayOnDevice() {
        val snapshot = _state.value
        android.util.Log.d("TubeFlow-autoload", "output added; track=${snapshot.track?.title} playing=${snapshot.playing} prepared=$prepared queue=${snapshot.queueTracks.size}")
        viewModelScope.launch {
            val collection = withContext(Dispatchers.IO) {
                runCatching { DownloadDatabase.get(getApplication()).media().availableAudio().mapNotNull(::trackOf) }.getOrDefault(emptyList())
            }
            handler.post {
                val current = _state.value
                if (current.playing || current.loading) return@post
                val last = current.track ?: lastTrack()
                val planned = AutoPlayQueue.plan(collection, last?.uri) { it.uri }
                when {
                    // Koleksi kosong: pertahankan lagu terakhir apa adanya.
                    planned == null -> if (current.track != null && prepared) { autoplay = true; start(token.get()) } else last?.let { play(it) }
                    // Lagu aktif masih sama dan siap: lebarkan antrean TANPA memuat ulang agar posisi tidak hilang.
                    current.track != null && prepared && current.track.uri == planned.first[planned.second].uri -> {
                        val expanded = PlaybackQueue(planned.first, planned.second).withPreferences(shufflePref, repeatPref)
                        queue = expanded
                        syncQueue(expanded)
                        autoplay = true
                        start(token.get())
                    }
                    // Tidak ada track siap (aplikasi baru dibuka atau pemutar sudah ditutup):
                    // antrean diisi SELURUH koleksi dan mulai dari lagu terakhir, bukan satu lagu.
                    else -> startQueue(planned.first, planned.first[planned.second])
                }
            }
        }
    }
    /** Lagu terakhir dipersist agar auto-play headset tetap punya sumber setelah pemutar kosong. */
    private fun rememberLast(track: Track) {
        getApplication<Application>().getSharedPreferences("player-last", 0).edit {
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
        ContextCompat.registerReceiver(application, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(application, devicePlug, IntentFilter(Intent.ACTION_HEADSET_PLUG).apply {
            addAction(ACTION_BT_A2DP)
            addAction(ACTION_BT_HEADSET)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        manager.registerAudioDeviceCallback(devices, handler)
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
                updateEngine { it.copy(positionMs=current.currentPosition.toLong(), playing=current.isPlaying) }
                if (current.isPlaying) handler.postDelayed(this, 200)
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
    fun playJobs(jobs: List<DownloadJob>, startAt: Int = 0) =
        startQueue(jobs.mapNotNull(::trackOf), jobs.getOrNull(startAt)?.let(::trackOf))
    private fun startQueue(tracks: List<Track>, start: Track?, shuffle: Boolean? = null) {
        if (tracks.isEmpty()) return
        val index = start?.let { wanted -> tracks.indexOfFirst { it.uri == wanted.uri }.coerceAtLeast(0) } ?: 0
        if (shuffle == true) persistShuffle(true)
        handler.post {
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
    private fun syncQueue(current: PlaybackQueue<Track>) = updateEngine {
        it.copy(queueTracks = current.tracks, queueIndex = current.index, queueHasNext = current.hasNext,
            shuffle = current.shuffled, repeat = current.repeat)
    }
    private fun load(planned: PlaybackQueue<Track>) {
        queue = planned
        val track = planned.current
        rememberLast(track)
        val id = token.incrementAndGet()
        _state.value = State(track=track, loading=true, queueTracks=planned.tracks, queueIndex=planned.index,
            queueHasNext=planned.hasNext, shuffle=planned.shuffled, repeat=planned.repeat)
        loadLyrics(track,id)
        handler.post {
            if (token.get() != id) return@post
            release()
            engineToken = id
            autoplay = true
            val current = MediaPlayer(); media = current
            try {
                current.setAudioAttributes(attributes)
                current.setOnPreparedListener { ready ->
                    if (media !== ready || token.get() != id) return@setOnPreparedListener
                    prepared = true
                    equalizer.attach(ready.audioSessionId)
                    runCatching { ready.playbackParams = ready.playbackParams.setSpeed(speed) }
                    updateEngine { it.copy(loading=false, durationMs=ready.duration.toLong().coerceAtLeast(0)) }
                    if (autoplay) start(id)
                }
                current.setOnCompletionListener { completed ->
                    if (media !== completed || token.get() != id) return@setOnCompletionListener
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
                        else -> { updateEngine { it.copy(playing=false, positionMs=it.durationMs) }; abandon() }
                    }
                }
                current.setOnErrorListener { failed, _, _ ->
                    if (media === failed && token.get() == id) fail("Audio tidak dapat diputar. Periksa file atau codec perangkat.")
                    true
                }
                current.setOnSeekCompleteListener { sought ->
                    if (media === sought && prepared && token.get() == id) updateEngine { it.copy(positionMs=sought.currentPosition.toLong()) }
                }
                // Data-source I/O and every MediaPlayer method stay off the UI thread.
                current.setDataSource(getApplication<Application>(), track.uri.toUri())
                current.prepareAsync()
            } catch (_: Exception) { if (token.get() == id) fail("File audio tidak tersedia atau aksesnya berubah.") else release() }
        }
    }
    /** Kecepatan putar (0,25–3×) berlaku untuk track aktif dan track berikutnya. */
    fun setSpeed(value: Float) { handler.post {
        speed = value.coerceIn(0.25f, 3f)
        getApplication<Application>().getSharedPreferences("player-fx", 0).edit { putFloat("speed", speed) }
        runCatching { media?.let { current -> current.playbackParams = current.playbackParams.setSpeed(speed) } }
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
                AudioManager.AUDIOFOCUS_GAIN -> if (transientResume) { transientResume=false; resumeWithoutFocus() }
                AudioManager.AUDIOFOCUS_LOSS -> pauseInternal(true)
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    transientResume = _state.value.playing; pauseInternal(false)
                }
            }
        }, handler).build()
    private fun claimFocus(id: Long): Boolean {
        val sequence=++focusSequence
        val focus=focusRequest(id,sequence)
        request?.let(manager::abandonAudioFocusRequest)
        request=focus
        return manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
    private fun start(id: Long) {
        if (!prepared || token.get() != id) return
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
            if (!prepared) return
            if (_state.value.durationMs > 0 && current.currentPosition.toLong() >= _state.value.durationMs) current.seekTo(0)
            current.start(); updateEngine { it.copy(playing=true, error=null) }
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
        if (prepared) try { media?.pause() } catch (_: IllegalStateException) { }
        handler.removeCallbacks(tick); updateEngine { it.copy(playing=false) }
        if (relinquish) { transientResume=false; abandon() }
    }
    fun seek(positionMs: Long) { val id=token.get(); handler.post {
        if (token.get() != id || !prepared) return@post
        val target=positionMs.coerceIn(0, _state.value.durationMs)
        try { media?.seekTo(target, MediaPlayer.SEEK_CLOSEST); updateEngine { it.copy(positionMs=target) } }
        catch (_: IllegalStateException) { fail("Posisi audio belum dapat diubah.") }
    } }
    fun skip(deltaMs: Long) = seek(_state.value.positionMs + deltaMs)
    fun stop() { token.incrementAndGet(); _state.value=State(); handler.post { queue=null; equalizer.release(); release() } }
    private fun abandon() { focusSequence++; request?.let(manager::abandonAudioFocusRequest); request=null }
    private fun release() { handler.removeCallbacks(tick); abandon(); prepared=false; autoplay=false; transientResume=false; equalizer.release(); media?.release(); media=null }
    private fun fail(message: String) { release(); updateEngine { it.copy(loading=false, playing=false, error=message) } }
    override fun onCleared() {
        token.incrementAndGet()
        if (active === this) active = null
        getApplication<Application>().unregisterReceiver(noisy)
        runCatching { getApplication<Application>().unregisterReceiver(devicePlug) }
        manager.unregisterAudioDeviceCallback(devices)
        PlaybackService.sync(getApplication<Application>(), State())
        handler.post { release(); thread.quitSafely() }
    }
}
