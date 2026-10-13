package id.local.tubeflow.core

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
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
    data class Track(val uri: String, val title: String, val artist: String = "", val cover: String = "", val lyricUri: String? = null)
    data class State(val track: Track? = null, val loading: Boolean = false, val playing: Boolean = false,
                     val positionMs: Long = 0, val durationMs: Long = 0, val lines: List<LrcLines.Line> = emptyList(),
                     val error: String? = null, val lyricError: String? = null)
    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()
    private val thread = HandlerThread("TubeFlow-audio").apply { start() }
    private val handler = Handler(thread.looper)
    private val manager = application.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val token = AtomicLong(0)
    private var media: MediaPlayer? = null
    private var engineToken = -1L
    private var prepared = false
    private var autoplay = false
    private var transientResume = false
    private var request: AudioFocusRequest? = null
    private var focusSequence = 0L
    @Volatile private var foreground = true
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause() }
    }
    init {
        ContextCompat.registerReceiver(application, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
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
    fun play(job: DownloadJob) {
        if (job.mode != MediaMode.AUDIO || job.state != JobState.COMPLETED || job.outputUri == null) return
        val song = job.songIdentity()
        val lyric = job.sidecarUris?.lines()?.firstOrNull { it.startsWith("sub|") && job.lyricStatus == LyricStatus.SAVED }?.substringAfter('|')
        play(Track(job.outputUri, Format.musicLabel(song), song.artist.orEmpty().ifBlank { song.remixer }, job.coverPath, lyric))
    }
    fun play(item: MediaItem) {
        if (item.kind == "AUDIO" && item.availability == "AVAILABLE") play(Track(item.location, Format.musicLabel(TitleSplit.identify(item.title,metadataArtist=item.artist,recognizeCollaboration=false)), item.artist, item.coverPath, item.lyricUri))
    }
    fun play(track: Track) {
        val id = token.incrementAndGet()
        _state.value = State(track=track, loading=true)
        loadLyrics(track,id)
        handler.post {
            if (token.get() != id) return@post
            release()
            engineToken = id
            autoplay = foreground
            val current = MediaPlayer(); media = current
            try {
                current.setAudioAttributes(attributes)
                current.setOnPreparedListener { ready ->
                    if (media !== ready || token.get() != id) return@setOnPreparedListener
                    prepared = true
                    updateEngine { it.copy(loading=false, durationMs=ready.duration.toLong().coerceAtLeast(0)) }
                    if (autoplay && foreground) start(id)
                }
                current.setOnCompletionListener { completed ->
                    if (media !== completed || token.get() != id) return@setOnCompletionListener
                    updateEngine { it.copy(playing=false, positionMs=it.durationMs) }
                    abandon()
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
    private fun start(id: Long) {
        if (!prepared || !foreground || token.get() != id) return
        val sequence=++focusSequence
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
            .setWillPauseWhenDucked(true).setOnAudioFocusChangeListener({ change ->
                if (token.get() != id || sequence != focusSequence) return@setOnAudioFocusChangeListener
                when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> if (transientResume && foreground) { transientResume=false; resumeWithoutFocus() }
                    AudioManager.AUDIOFOCUS_LOSS -> pauseInternal(true)
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        transientResume = _state.value.playing; pauseInternal(false)
                    }
                }
            }, handler).build()
        request?.let(manager::abandonAudioFocusRequest)
        request = focus
        if (manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            abandon(); updateEngine { it.copy(error="Audio sedang dipakai aplikasi lain. Coba putar lagi.") }; return
        }
        resumeWithoutFocus()
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
    fun setForeground(visible: Boolean) { foreground=visible; if (!visible) pause() }
    fun stop() { token.incrementAndGet(); _state.value=State(); handler.post { release() } }
    private fun abandon() { focusSequence++; request?.let(manager::abandonAudioFocusRequest); request=null }
    private fun release() { handler.removeCallbacks(tick); abandon(); prepared=false; autoplay=false; transientResume=false; media?.release(); media=null }
    private fun fail(message: String) { release(); updateEngine { it.copy(loading=false, playing=false, error=message) } }
    override fun onCleared() {
        token.incrementAndGet()
        getApplication<Application>().unregisterReceiver(noisy)
        handler.post { release(); thread.quitSafely() }
    }
}
