package id.local.tubeflow.core

import android.app.Application
import android.util.AtomicFile
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** One serialized request; WorkManager owns execution, the journal owns its source identity. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class KaraokeViewModel(app: Application) : AndroidViewModel(app) {
    private val requestFile = AtomicFile(File(app.noBackupFilesDir, "karaoke-request.json"))
    private val work = WorkManager.getInstance(app)
    private val id = MutableStateFlow<UUID?>(null)
    val requestId = id.asStateFlow()
    val info = id.flatMapLatest { value -> if (value == null) flowOf(null) else work.getWorkInfoByIdFlow(value) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _selected = MutableStateFlow<PlayerViewModel.Track?>(null); val selected = _selected.asStateFlow()
    private val _open = MutableStateFlow(false); val dialogOpen = _open.asStateFlow()
    private val _sample = MutableStateFlow(true); val sample = _sample.asStateFlow()
    private val _startText = MutableStateFlow("0:00"); val startText = _startText.asStateFlow()
    private var lastStartMs = 0L
    private val _cleanupPending = MutableStateFlow(false); val cleanupPending = _cleanupPending.asStateFlow()
    fun startText(value: String) { _startText.value = value.take(16) }
    fun sample(enabled: Boolean) { if (!_busy.value) _sample.value = enabled }
    private val _busy = MutableStateFlow(true); val busy = _busy.asStateFlow()
    private val _stopping = MutableStateFlow(false); val stopping = _stopping.asStateFlow()
    private val _message = MutableStateFlow<String?>(null); val message = _message.asStateFlow()
    private val _saved = MutableStateFlow<String?>(null); val saved = _saved.asStateFlow()
    private val _source = MutableStateFlow<PlayerViewModel.Track?>(null); val source = _source.asStateFlow()
    private fun currentInfo() = info.value?.takeIf { it.id == id.value }

    init {
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { requests.withLock {
                var discardRequested = false
                if (requestFile.baseFile.exists() || File(requestFile.baseFile.path + ".bak").exists()) try {
                    val bytes = requestFile.openRead().use { input ->
                        val bytes = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer); if (count < 0) break
                            require(bytes.size() + count <= 65536); bytes.write(buffer, 0, count)
                        }
                        bytes.toByteArray()
                    }
                    require(bytes.size <= 65536)
                    val data = JSONObject(bytes.toString(Charsets.UTF_8))
                    id.value = UUID.fromString(data.getString("workId"))
                    discardRequested = data.optBoolean("discardRequested", false)
                    _cleanupPending.value = discardRequested
                    _source.value = data.optJSONObject("source")?.let { d ->
                        PlayerViewModel.Track(d.getString("uri"), d.getString("title"), d.optString("artist"), d.optString("cover"), d.optString("lyrics").takeIf { it.isNotBlank() })
                    }
                    lastStartMs = data.optLong("startMs", 0)
                    _sample.value = data.optBoolean("sample", true)
                    _startText.value = KaraokeInterval.formatStart(lastStartMs)
                } catch (failure: Exception) { _message.value = "Status karaoke lama tidak dapat dibaca. Audio tetap tersedia." }
                if (discardRequested) withContext(NonCancellable) {
                    work.cancelUniqueWork(KaraokeWorker.UNIQUE).result.get()
                    id.value?.let { work.cancelWorkById(it).result.get() }
                    KaraokeWorker.lock.withLock {
                        KaraokePublication(app).recover()
                        id.value?.let { discard(it) {} }
                        clearState()
                    }
                    _message.value = "Pembersihan karaoke yang tertunda sudah selesai. Audio asli tetap tersedia."
                } else {
                    val stored = id.value?.let { work.getWorkInfoById(it).get() }
                    if (stored?.state == WorkInfo.State.SUCCEEDED) _sample.value = stored.outputData.getBoolean("sample", _sample.value)
                    val running = adoptRunning()
                    if (running == null && stored?.state == WorkInfo.State.CANCELLED && stored.id == id.value) {
                        recordDiscard(stored.id)
                        withContext(NonCancellable) {
                            KaraokeWorker.lock.withLock {
                                KaraokePublication(app).recover()
                                discard(stored.id) {}
                                clearState()
                            }
                        }
                        _message.value = "Berkas sementara dari proses yang dibatalkan sudah dibersihkan."
                    }
                }
            } } } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _message.value = "Status karaoke belum dapat dimuat. Coba buka lagi." }
            finally { _busy.value = false }
        }
        viewModelScope.launch(Dispatchers.IO) { KaraokeWorker.lock.withLock {
            try { KaraokePublication(app).recover() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _message.value = "Pemulihan hasil simpan tertunda. Periksa akses folder sebelum menyimpan lagi." }
        } }
        viewModelScope.launch { info.collect { value ->
            if (value == null || value.id != id.value) return@collect
            // Notification cancellation is persisted by WorkManager even when this ViewModel was absent.
            if (value.state == WorkInfo.State.CANCELLED && !_busy.value) { cancel(); return@collect }
            val details = if (value.state.isFinished) value.outputData else value.progress
            trackFrom(details)?.let { _source.value = it }
            val saved = withContext(Dispatchers.IO) { value.takeIf { it.state == WorkInfo.State.SUCCEEDED }?.let {
                val f = File(KaraokeWorker.directory(app, it.id.toString()), "saved.json")
                runCatching { JSONObject(f.readText()).getString("uri") }.getOrNull()?.takeIf { uri -> StorageUris.exists(app, uri.toUri()) }
            } }
            // Old asynchronous reads must not label a newly accepted request as already saved.
            if (value.id == id.value && (saved != null || _saved.value == null)) _saved.value = saved
        } }
    }
    private fun trackFrom(data: Data): PlayerViewModel.Track? {
        val uri = data.getString("uri")?.takeIf { it.startsWith("content://") } ?: return null
        return PlayerViewModel.Track(uri, data.getString("title").orEmpty(), data.getString("artist").orEmpty(),
            data.getString("cover").orEmpty(), data.getString("lyrics")?.takeIf { it.isNotBlank() })
    }
    /** Call under requests: never attach the journal of A to an untracked worker B. */
    private suspend fun adoptRunning(): WorkInfo? {
        val running = work.getWorkInfosForUniqueWork(KaraokeWorker.UNIQUE).get().firstOrNull { !it.state.isFinished }
        if (running != null && running.id != id.value) {
            withContext(Dispatchers.Main) {
                _source.value = trackFrom(running.progress); _saved.value = null; id.value = running.id
            }
        }
        return running
    }
    fun open(track: PlayerViewModel.Track, positionMs: Long = 0) {
        if (_selected.value?.uri != track.uri) {
            _sample.value = true; _startText.value = KaraokeInterval.formatStart(positionMs)
        }
        // Opening another song never silently discards an unsaved preview or a running request.
        _selected.value = track; _open.value = true; _message.value = null
    }
    fun openCurrent() {
        _selected.value = _source.value
        currentInfo()?.let { current ->
            val data = if (current.state.isFinished) current.outputData else current.progress
            _sample.value = data.getBoolean("sample", _sample.value)
            _startText.value = KaraokeInterval.formatStart(data.getLong("startMs", lastStartMs))
        }
        _open.value = true; _message.value = null
    }
    fun close() { _open.value = false }
    fun isPrivatePreview(uri: String?): Boolean = uri?.startsWith(
        android.net.Uri.fromFile(File(getApplication<Application>().noBackupFilesDir, "karaoke-work")).toString().trimEnd('/') + "/") == true
    fun start(onAccepted: () -> Unit = {}) {
        val track = _selected.value ?: return
        if (_busy.value) return
        if (_cleanupPending.value) {
            _message.value = "Pembersihan belum selesai. Pilih Buang pratinjau sebelum memulai lagi."
            return
        }
        val sampling = _sample.value
        val position = if (sampling) KaraokeInterval.parseStart(_startText.value) else 0L
        if (position == null) { _message.value = "Isi posisi mulai sebagai 01:30 atau 90 detik (maksimal 10 menit)."; return }
        _busy.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { requests.withLock {
                    val old = id.value?.let { work.getWorkInfoById(it).get() }
                    require(!KaraokeProcessPolicy.startBlocked(old?.state?.isFinished == false, adoptRunning() != null)) {
                        "Satu proses karaoke masih berjalan. Batalkan atau tunggu selesai."
                    }
                    require(track.uri.startsWith("content://")) { "Pilih audio asli untuk membuat karaoke." }
                    // CANCELLED is not proof of native cleanup: enqueue only after the old lease is free.
                    KaraokeWorker.lock.withLock {
                        val request = OneTimeWorkRequestBuilder<KaraokeWorker>().setInputData(workDataOf(
                            "uri" to track.uri, "title" to track.title.take(500), "artist" to track.artist.take(300),
                            "cover" to track.cover.take(2000), "lyrics" to track.lyricUri.orEmpty().take(2000),
                            "createdAt" to System.currentTimeMillis(), "sample" to sampling, "startMs" to position))
                            .addTag(KaraokeWorker.TAG).build()
                        val previous = if (requestFile.baseFile.exists()) requestFile.openRead().use { it.readBytes() } else null
                        val data = JSONObject().put("uri", track.uri).put("title", track.title).put("artist", track.artist)
                            .put("cover", track.cover).put("lyrics", track.lyricUri.orEmpty())
                        writeRequest(JSONObject().put("workId", request.id.toString()).put("source", data)
                            .put("startMs", position).put("sample", sampling).toString().toByteArray())
                        try { work.enqueueUniqueWork(KaraokeWorker.UNIQUE, ExistingWorkPolicy.KEEP, request).result.get() }
                        catch (failure: Exception) {
                            if (previous == null) requestFile.delete() else writeRequest(previous)
                            throw failure
                        }
                        withContext(Dispatchers.Main) {
                            lastStartMs = position; _source.value = track; _saved.value = null; id.value = request.id
                        }
                    }
                } }
                // Preserve the queue and seek; a rejected click must not close the player's session.
                onAccepted(); _message.value = null
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _message.value = failure.message ?: "Karaoke belum dapat dimulai." }
            finally { _busy.value = false }
        }
    }
    private fun writeRequest(bytes: ByteArray) {
        val output = requestFile.startWrite()
        try { output.write(bytes); requestFile.finishWrite(output) }
        catch (failure: Exception) { requestFile.failWrite(output); throw failure }
    }
    fun cancel(onDiscard: (String) -> Unit = {}) {
        if (_busy.value) return
        _busy.value = true; _stopping.value = true
        viewModelScope.launch {
            try { withContext(NonCancellable + Dispatchers.IO) { requests.withLock {
                val current = adoptRunning()?.id ?: id.value
                current?.let(::recordDiscard)
                work.cancelUniqueWork(KaraokeWorker.UNIQUE).result.get()
                id.value?.let { work.cancelWorkById(it).result.get() }
                KaraokeWorker.lock.withLock {
                    KaraokePublication(getApplication()).recover()
                    current?.let { discard(it, onDiscard) }
                    clearState()
                }
            } }; _message.value = "Proses karaoke dibatalkan dan cleanup selesai. Audio asli tetap tersedia." }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _message.value = "Pembatalan belum dapat dipastikan. Periksa status sebelum memulai lagi." }
            finally { _stopping.value = false; _busy.value = false }
        }
    }
    /** Explicitly discard only this private preview; never remove an already published file. */
    fun clear(onDiscard: (String) -> Unit = {}) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try { withContext(NonCancellable + Dispatchers.IO) { requests.withLock {
                val current = id.value
                require(adoptRunning() == null) { "Batalkan proses yang berjalan terlebih dahulu." }
                current?.let(::recordDiscard)
                KaraokeWorker.lock.withLock {
                    KaraokePublication(getApplication()).recover()
                    val root = KaraokeWorker.root(getApplication())
                    root.listFiles()?.filter { it.isDirectory }?.forEach { directory ->
                        val owned = runCatching { UUID.fromString(directory.name) }.getOrNull()
                            ?: error("Folder proses tidak dikenal; data tidak dihapus.")
                        require(owned.toString().equals(directory.name, true)) { "Nama folder proses tidak dikenal; data tidak dihapus." }
                        discard(owned, onDiscard)
                    }
                    clearState()
                }
            } }; _message.value = "Pratinjau privat dibuang. Audio asli dan hasil di Koleksi tetap tersedia." }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _message.value = failure.message ?: "Pratinjau belum dapat dibuang." }
            finally { _busy.value = false }
        }
    }
    private fun recordDiscard(current: UUID) {
        val record = JSONObject().put("workId", current.toString()).put("discardRequested", true)
            .put("startMs", lastStartMs).put("sample", _sample.value)
        _source.value?.let { track -> record.put("source", JSONObject().put("uri", track.uri)
            .put("title", track.title).put("artist", track.artist).put("cover", track.cover)
            .put("lyrics", track.lyricUri.orEmpty())) }
        writeRequest(record.toString().toByteArray())
        _cleanupPending.value = true
    }
    private suspend fun discard(current: UUID, onDiscard: (String) -> Unit) {
        val root = KaraokeWorker.root(getApplication())
        val preview = File(File(root, current.toString()), "instrumental.flac")
        withContext(Dispatchers.Main) { onDiscard(android.net.Uri.fromFile(preview).toString()) }
        require(KaraokeResult.discard(root, current.toString())) {
            "Berkas sementara belum dapat dibersihkan. Coba lagi setelah proses berhenti."
        }
    }
    private fun clearState() {
        require(KaraokeResult.discardModelPartials(KaraokeModel.directory(getApplication()))) {
            "Sisa unduhan model belum dapat dibersihkan. Coba Buang pratinjau lagi."
        }
        requestFile.delete()
        require(listOf(requestFile.baseFile, File(requestFile.baseFile.path + ".bak"), File(requestFile.baseFile.path + ".new"))
            .all { java.nio.file.Files.notExists(it.toPath()) }) { "Status pembersihan belum dapat dihapus. Coba lagi." }
        _cleanupPending.value = false
        id.value = null; _source.value = null; _saved.value = null; lastStartMs = 0L
    }
    fun preview(onReady: (PlayerViewModel.Track) -> Unit) {
        val current = currentInfo()?.takeIf { it.state == WorkInfo.State.SUCCEEDED } ?: return
        val track = _source.value ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val sampling = current.outputData.getBoolean("sample", false)
                val result = withContext(Dispatchers.IO) { requests.withLock { KaraokeWorker.lock.withLock {
                    require(id.value == current.id)
                    KaraokeResult.verify(KaraokeWorker.directory(getApplication(), current.id.toString()), track.uri, sampling)
                } } }
                if (id.value == current.id) onReady(track.copy(uri = android.net.Uri.fromFile(result).toString(),
                    title = track.title + if (sampling) " (Karaoke cuplikan)" else " (Karaoke)",
                    lyricUri = if (sampling) null else track.lyricUri))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _message.value = failure.message ?: "Pratinjau belum dapat diputar." }
            finally { _busy.value = false }
        }
    }
    fun compare(player: PlayerViewModel, instrumental: Boolean) {
        val current = currentInfo()?.takeIf { it.state == WorkInfo.State.SUCCEEDED } ?: return
        val source = _source.value ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { requests.withLock { KaraokeWorker.lock.withLock {
                    require(id.value == current.id)
                    KaraokeResult.verify(KaraokeWorker.directory(getApplication(), current.id.toString()), source.uri, current.outputData.getBoolean("sample", false))
                } } }
                if (id.value == current.id) {
                    val uri = android.net.Uri.fromFile(result).toString()
                    val track = if (instrumental) source.copy(uri = uri, title = source.title + if (current.outputData.getBoolean("sample", false)) " (Karaoke cuplikan)" else " (Karaoke)",
                        lyricUri = if (current.outputData.getBoolean("sample", false)) null else source.lyricUri) else source
                    player.compare(track, source.uri, uri, current.outputData.getLong("startMs", 0), current.outputData.getLong("durationMs", 0))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _message.value = failure.message ?: "Perbandingan belum dapat diputar." }
            finally { _busy.value = false }
        }
    }
    fun save(expectedId: UUID? = id.value, format: KaraokeOutput = KaraokeOutput.FLAC) {
        val current = currentInfo()?.takeIf { it.state == WorkInfo.State.SUCCEEDED && it.id == expectedId } ?: return
        val track = _source.value ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val (uri, lyrics) = withContext(Dispatchers.IO) { requests.withLock {
                    require(id.value == current.id)
                    KaraokePublication(getApplication()).publish(current.id.toString(), current.outputData.getString("name").orEmpty(),
                        if (current.outputData.getBoolean("sample", false)) track.copy(lyricUri = null) else track, current.outputData.getBoolean("sample", false), format)
                } }
                if (id.value == current.id) {
                    _saved.value = uri
                    _message.value = "Instrumental disimpan dan masuk Koleksi." + if (!lyrics) " Lirik belum dapat disalin; audio tetap tersedia." else ""
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _message.value = failure.message ?: "Hasil karaoke belum dapat disimpan." }
            finally { _busy.value = false }
        }
    }
    companion object { internal val requests = Mutex() }
}
