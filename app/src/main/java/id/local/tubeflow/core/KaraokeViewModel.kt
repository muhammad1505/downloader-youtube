package id.local.tubeflow.core

import android.app.Application
import android.util.AtomicFile
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** UI survives rotation/restart by observing the persisted WorkManager request, not a second player. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class KaraokeViewModel(app: Application) : AndroidViewModel(app) {
    private val requestFile = AtomicFile(File(app.noBackupFilesDir, "karaoke-request.json"))
    private val work = WorkManager.getInstance(app)
    private val id = MutableStateFlow<UUID?>(null)
    val info = id.flatMapLatest { value -> if (value == null) flowOf(null) else work.getWorkInfoByIdFlow(value) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val _selected = MutableStateFlow<PlayerViewModel.Track?>(null); val selected = _selected.asStateFlow()
    private val _sample = MutableStateFlow(true); val sample = _sample.asStateFlow()
    private val _startText = MutableStateFlow("0:00"); val startText = _startText.asStateFlow()
    private var lastStartMs = 0L
    fun startText(value: String) { _startText.value = value.take(16) }
    fun sample(enabled: Boolean) { _sample.value = enabled }
    private val _busy = MutableStateFlow(false); val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null); val message = _message.asStateFlow()
    private val _saved = MutableStateFlow<String?>(null); val saved = _saved.asStateFlow()
    private val _source = MutableStateFlow<PlayerViewModel.Track?>(null); val source = _source.asStateFlow()
    init {
        viewModelScope.launch(Dispatchers.IO) { requests.withLock {
            if (id.value == null && (requestFile.baseFile.exists() || File(requestFile.baseFile.path + ".bak").exists())) runCatching {
                val data = JSONObject(requestFile.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
                val d = data.getJSONObject("source")
                _source.value = PlayerViewModel.Track(d.getString("uri"), d.getString("title"), d.optString("artist"), d.optString("cover"), d.optString("lyrics").takeIf { it.isNotBlank() })
                id.value = UUID.fromString(data.getString("workId"))
                lastStartMs = data.optLong("startMs", 0)
            }.onFailure { _message.value = "Status karaoke lama tidak dapat dibaca. Audio tetap tersedia." }
        } }

        viewModelScope.launch(Dispatchers.IO) { KaraokeWorker.lock.withLock { runCatching { KaraokePublication(app).recover() }.onFailure { _message.value = it.message } } }
        viewModelScope.launch { info.collect { value ->
            _saved.value = withContext(Dispatchers.IO) { value?.takeIf { it.state == WorkInfo.State.SUCCEEDED }?.let {
                val f = File(KaraokeWorker.directory(app, it.id.toString()), "saved.json")
                runCatching { JSONObject(f.readText()).getString("uri") }.getOrNull()?.takeIf { uri -> StorageUris.exists(app, uri.toUri()) }
            } }
        } }
    }
    fun open(track: PlayerViewModel.Track, positionMs: Long = 0) { _selected.value = track; _startText.value = KaraokeInterval.formatStart(positionMs); _message.value = null }
    fun openCurrent() { _source.value?.let { open(it, info.value?.outputData?.getLong("startMs", lastStartMs) ?: lastStartMs) } }
    fun close() { _selected.value = null }
    fun start() {
        val track = _selected.value ?: return
        if (_busy.value) return
        val sampling = _sample.value
        val position = if (sampling) KaraokeInterval.parseStart(_startText.value) else 0L
        if (position == null) { _message.value = "Isi posisi mulai sebagai 01:30 atau 90 detik (maksimal 10 menit)."; return }
        _busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try { requests.withLock {
                val old = id.value?.let { work.getWorkInfoById(it).get() }
                require(old == null || old.state.isFinished) { "Satu proses karaoke masih berjalan. Batalkan atau tunggu selesai." }
                require(track.uri.startsWith("content://")) { "Pilih audio asli untuk membuat karaoke." }
                val request = OneTimeWorkRequestBuilder<KaraokeWorker>().setInputData(workDataOf(
                    "uri" to track.uri, "title" to track.title.take(500), "artist" to track.artist.take(300), "createdAt" to System.currentTimeMillis(), "sample" to sampling, "startMs" to position))
                    .addTag(KaraokeWorker.TAG).build()
                val data = JSONObject().put("uri", track.uri).put("title", track.title).put("artist", track.artist).put("cover", track.cover).put("lyrics", track.lyricUri.orEmpty())
                val output = requestFile.startWrite()
                try { output.write(JSONObject().put("workId", request.id.toString()).put("source", data).put("startMs", position).toString().toByteArray()); requestFile.finishWrite(output) }
                catch (failure: Exception) { requestFile.failWrite(output); throw failure }
                lastStartMs = position
                _source.value = track; _saved.value = null; id.value = request.id
                work.enqueueUniqueWork(KaraokeWorker.UNIQUE, ExistingWorkPolicy.KEEP, request).result.get()
                _message.value = null
            } } catch (failure: Exception) { _message.value = failure.message ?: "Karaoke belum dapat dimulai." }
            finally { _busy.value = false }
        }
    }
    fun cancel() { id.value?.let { work.cancelWorkById(it) } }
    fun preview(): PlayerViewModel.Track? {
        val current = info.value?.takeIf { it.state == WorkInfo.State.SUCCEEDED } ?: return null
        val track = _source.value ?: return null
        val path = current.outputData.getString("path") ?: return null
        val result = File(KaraokeWorker.directory(getApplication(), current.id.toString()), "instrumental.flac")
        if (result.path != path || !result.isFile) return null
        return track.copy(uri = android.net.Uri.fromFile(result).toString(), title = track.title + if (current.outputData.getBoolean("sample", false)) " (Karaoke cuplikan)" else " (Karaoke)",
            lyricUri = if (current.outputData.getBoolean("sample", false)) null else track.lyricUri)
    }
    fun save() {
        val current = info.value?.takeIf { it.state == WorkInfo.State.SUCCEEDED } ?: return
        val track = _source.value ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (uri, lyrics) = KaraokePublication(getApplication()).publish(current.id.toString(), current.outputData.getString("name").orEmpty(), if (current.outputData.getBoolean("sample", false)) track.copy(lyricUri = null) else track, current.outputData.getBoolean("sample", false))
                _saved.value = uri
                _message.value = "Instrumental disimpan ke folder TubeFlow/pilihan dan masuk Koleksi." + if (!lyrics) " Lirik belum dapat disalin; audio tetap tersedia." else ""
            } catch (failure: Exception) { _message.value = failure.message ?: "Hasil karaoke belum dapat disimpan." }
            finally { _busy.value = false }
        }
    }
    companion object { private val requests = Mutex() }
}
