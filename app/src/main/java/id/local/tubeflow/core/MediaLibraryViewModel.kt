package id.local.tubeflow.core

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.core.content.edit
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MediaLibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val database = DownloadDatabase.get(app)
    private val dao = database.media()
    val repository = MediaRepository(app)
    val lyricEditor = LyricEditor(app)
    data class Filter(val kind: String = "", val query: String = "", val sort: String = "recent", val folder: String = "", val artist: String = "", val album: String = "", val limit: Int = 100)
    private val filter = MutableStateFlow(Filter())
    val selectedFilter = filter.asStateFlow()
    val items = filter.debounce(180).flatMapLatest { f -> dao.page(f.kind, f.folder, f.artist, f.album, SmartMedia.search(f.query), f.sort, f.limit) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val totals = dao.totals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val folders = dao.folders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val artists = dao.artists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val albums = dao.albums().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val since = MutableStateFlow(DashboardCharts.since(7, java.time.LocalDate.now(), java.time.ZoneId.systemDefault()))
    val eventCounts = since.flatMapLatest(dao::eventCounts).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val daily = since.flatMapLatest(dao::daily).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val platforms = since.flatMapLatest(dao::platforms).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val pendingIndex = androidx.work.WorkManager.getInstance(app).getWorkInfosByTagFlow(MediaIndexWorker.TAG)
        .map { list -> list.count { !it.state.isFinished } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val recent = dao.recentEvents().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _busy = MutableStateFlow(false); val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null); val message = _message.asStateFlow()
    private val _lastScan = MutableStateFlow(app.getSharedPreferences("library", 0).getLong("lastScan", 0)); val lastScan = _lastScan.asStateFlow()
    data class DeviceStorage(val total: Long, val free: Long)
    val deviceStorage = MutableStateFlow<DeviceStorage?>(null)
    init {
        viewModelScope.launch(Dispatchers.IO) {
            CacheBudget.prune(java.io.File(app.noBackupFilesDir, "covers"), 32L * 1024 * 1024)
            CacheBudget.prune(java.io.File(app.noBackupFilesDir, "library-covers"), 24L * 1024 * 1024)
            repository.validateRecent()
            database.downloads().observe().map { list -> list.filter { it.state == JobState.COMPLETED && it.autoLibrary }.map { it.id to it.outputUri } }.distinctUntilChanged().collect { jobs ->
                for ((id, _) in jobs.take(200)) runCatching { repository.indexDownload(id) }
            }
        }
        updateDeviceStorage()
    }
    fun validateAvailability() { viewModelScope.launch(Dispatchers.IO) { runCatching { repository.validateRecent() } } }
    fun filter(value: Filter) { filter.value = value.copy(limit = 100) }
    fun more() { filter.update { it.copy(limit = (it.limit + 100).coerceAtMost(3000)) } }
    fun period(days: Int) { since.value = DashboardCharts.since(days, java.time.LocalDate.now(), java.time.ZoneId.systemDefault()) }
    fun clearMessage() { _message.value = null }
    fun updateDeviceStorage() { viewModelScope.launch(Dispatchers.IO) {
        deviceStorage.value = runCatching { android.os.StatFs(getApplication<Application>().filesDir.path).let { DeviceStorage(it.totalBytes, it.availableBytes) } }.getOrNull()
    } }
    private fun operation(block: suspend () -> String) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try { _message.value = block() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { _message.value = error.message ?: "Operasi media gagal." }
            finally { _busy.value = false; updateDeviceStorage() }
        }
    }
    fun scan() = operation {
        val (count, failed) = repository.scan()
        _lastScan.value = System.currentTimeMillis()
        getApplication<Application>().getSharedPreferences("library", 0).edit { putLong("lastScan", _lastScan.value) }
        "$count media diperiksa" + if (failed > 0) "; $failed file tidak dapat dibaca. Statistik dapat parsial." else "."
    }
    fun import(uris: List<android.net.Uri>) = operation {
        var count = 0; var failed = 0
        uris.take(100).forEach { uri ->
            try {
                runCatching { getApplication<Application>().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                    .recoverCatching { getApplication<Application>().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                repository.import(uri.toString()); count++
            } catch (_: Exception) { failed++ }
        }
        "$count file pilihan diperiksa; $failed gagal/tidak didukung."
    }
    fun rename(item: MediaItem, name: String) = operation { repository.rename(item, name); "Nama file diperbarui." }
    fun delete(item: MediaItem) = operation { repository.delete(item); "File dihapus." }
    fun correct(item: MediaItem, preview: LyricEditor.Preview, offset: Long) = operation {
        lyricEditor.apply(item, preview, offset)
        dao.event(MediaEvent(jobId = item.jobId.orEmpty(), action = "LYRIC_TIMING", kind = "AUDIO", platformId = item.platformId, at = System.currentTimeMillis()))
        "Waktu lirik disimpan. Pemutar TubeFlow memperbarui lirik; buka ulang lirik jika memakai pemutar lain."
    }
}
