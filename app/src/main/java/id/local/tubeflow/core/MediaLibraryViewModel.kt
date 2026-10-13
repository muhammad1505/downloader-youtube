package id.local.tubeflow.core

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.core.content.edit
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import androidx.core.net.toUri

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
            MediaFileOperations.mutex.withLock { runCatching { LyricPublisher(app).recover() } }
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
    fun rename(item: MediaItem, name: String, searchAfter: Boolean = false) = operation {
        val changed=repository.rename(item, name)
        if (searchAfter && changed.kind == "AUDIO") withContext(Dispatchers.Main.immediate) { openLyrics(changed, ManualLyricQuery.fromFilename(changed.displayName)) }
        "Nama file diperbarui."
    }
    fun delete(item: MediaItem) = operation { repository.delete(item); "File dihapus." }
    fun correct(item: MediaItem, preview: LyricEditor.Preview, offset: Long) = operation {
        lyricEditor.apply(item, preview, offset)
        dao.event(MediaEvent(jobId = item.jobId.orEmpty(), action = "LYRIC_TIMING", kind = "AUDIO", platformId = item.platformId, at = System.currentTimeMillis()))
        "Waktu lirik disimpan. Pemutar TubeFlow memperbarui lirik; buka ulang lirik jika memakai pemutar lain."
    }
    data class LyricsState(val item: MediaItem, val title: String, val artist: String,
        val choices: List<Lyrics.Choice> = emptyList(), val loading: Boolean = false,
        val saving: Boolean = false, val searched: Boolean = false, val error: String? = null) {
        val query get() = LyricMatch.Query(TitleSplit.identify(item.title,metadataArtist=item.artist,recognizeCollaboration=false).lyricTitle,item.artist,(item.durationMs ?: 0)/1000)
    }
    private val _lyrics=MutableStateFlow<LyricsState?>(null)
    val lyrics=_lyrics.asStateFlow()
    private var lyricsSession=0L
    private var lyricsRequest: Job?=null
    private var expectedLyrics: Map<String,ByteArray> = emptyMap()
    private var expectedJob: DownloadJob?=null

    fun openLyrics(jobId: String) { viewModelScope.launch {
        try {
            val item=withContext(Dispatchers.IO) {
                repository.indexDownload(jobId, manual=true)
                database.downloads().get(jobId)?.outputUri?.let { dao.getByAccess(it) ?: dao.get(it) }
            } ?: error("File belum tersedia di koleksi.")
            openLyrics(item)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { _message.value=failure.message }
    } }
    fun openLyrics(item: MediaItem, query: ManualLyricQuery? = null) {
        if (_lyrics.value?.saving == true || item.kind != "AUDIO") return
        closeLyrics()
        val identity=TitleSplit.identify(item.title,metadataArtist=item.artist,recognizeCollaboration=false)
        _lyrics.value=LyricsState(item,query?.title ?: identity.lyricTitle,query?.artist ?: item.artist)
        searchLyrics(_lyrics.value!!.title,_lyrics.value!!.artist)
    }
    fun closeLyrics() {
        if (_lyrics.value?.saving == true) return
        lyricsSession++; lyricsRequest?.cancel(); _lyrics.value=null
        expectedLyrics=emptyMap(); expectedJob=null
    }
    private fun displayName(raw: String): String? = getApplication<Application>().contentResolver.query(raw.toUri(),
        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    fun searchLyrics(title: String, artist: String) {
        val previous=_lyrics.value ?: return
        if (previous.loading || previous.saving) return
        val session=++lyricsSession
        _lyrics.value=previous.copy(title=title.trim(),artist=artist.trim(),loading=true,choices=emptyList(),error=null,searched=false)
        lyricsRequest=viewModelScope.launch {
            try {
                val captured=withContext(Dispatchers.IO) { MediaFileOperations.mutex.withLock {
                    val current=dao.get(previous.item.uri) ?: error("Media telah dihapus; buka ulang pencarian.")
                    require(current.availability == "AVAILABLE" && displayName(current.location) == current.displayName) { "Nama/lokasi media berubah; buka ulang pencarian." }
                    val job=current.jobId?.let { database.downloads().get(it) }
                    val bytes=current.lyricUri?.let { mapOf(it to lyricEditor.captureForRefetch(it)) }.orEmpty()
                    Triple(current,bytes,job)
                } }
                val result=withContext(Dispatchers.IO) { Lyrics.search(title,artist) }
                if (session != lyricsSession) return@launch
                expectedLyrics=captured.second; expectedJob=captured.third
                _lyrics.value=_lyrics.value?.copy(item=captured.first,choices=result.choices,loading=false,searched=true,
                    error=if (result.incomplete) "Sebagian pencarian gagal. Coba lagi jika hasil belum sesuai." else null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (session == lyricsSession) _lyrics.value=_lyrics.value?.copy(loading=false,error=failure.message ?: "Pencarian gagal.") }
        }
    }
    fun saveLyrics(choice: Lyrics.Choice, acknowledgedMismatch: Boolean) {
        val current=_lyrics.value ?: return
        if (current.loading || current.saving || !current.choices.contains(choice) || !choice.hasSyncedLyrics) return
        if (!choice.automaticMatch(current.query) && !acknowledgedMismatch) return
        _lyrics.value=current.copy(saving=true,error=null)
        val session=lyricsSession
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { MediaFileOperations.mutex.withLock {
                    val item=dao.get(current.item.uri) ?: error("Media telah dihapus.")
                    require(item.location == current.item.location && item.displayName == current.item.displayName &&
                        item.lyricUri == current.item.lyricUri && displayName(item.location) == current.item.displayName) { "Media/lirik berubah selama pencarian; buka ulang." }
                    expectedJob?.let { old -> database.downloads().get(old.id)?.let { job ->
                        require(job.state == JobState.COMPLETED && job.generation == old.generation &&
                            job.outputUri == old.outputUri && job.sidecarUris == old.sidecarUris) { "Download berubah; lirik tidak diganti." }
                    } }
                    val text=Lyrics.selectedText(choice,item.durationMs)
                    val uri=LyricPublisher(getApplication()).publish(item,text,expectedLyrics)
                    dao.put(item.copy(lyricUri=uri,lyricStatus=LyricStatus.SAVED))
                    item.jobId?.let { database.downloads().lyricStatus(it,LyricStatus.SAVED) }
                    dao.event(MediaEvent(jobId=item.jobId.orEmpty(),action="LYRICS_SELECTED",kind="AUDIO",platformId=item.platformId,at=System.currentTimeMillis()))
                } }
                if (session == lyricsSession) { _lyrics.value=null; expectedLyrics=emptyMap(); expectedJob=null }
                _message.value="Lirik pilihan disimpan. Cek kecocokan vokal; koreksi waktu bila perlu."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (session == lyricsSession) _lyrics.value=_lyrics.value?.copy(saving=false,error=failure.message ?: "Lirik gagal disimpan.") }
        }
    }

}
