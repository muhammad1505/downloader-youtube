package id.local.tubeflow.core

import android.app.Application
import android.content.Intent
import androidx.room.withTransaction
import androidx.lifecycle.AndroidViewModel
import androidx.core.content.edit
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import androidx.core.net.toUri
import android.provider.DocumentsContract

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MediaLibraryViewModel(app: Application) : AndroidViewModel(app) {
    private companion object { const val AUTO_SCAN_INTERVAL_MS = 5_000L }
    private val database = DownloadDatabase.get(app)
    private val dao = database.media()
    val repository = MediaRepository(app)
    val lyricEditor = LyricEditor(app)
    data class Filter(val kind: String = "", val query: String = "", val sort: String = "recent", val folder: String = "", val artist: String = "", val album: String = "", val limit: Int = 100, val playlistId: String = "")
    private val filter = MutableStateFlow(Filter())
    val selectedFilter = filter.asStateFlow()
    val items = filter.debounce(180).flatMapLatest { f -> dao.page(f.kind, f.folder, f.artist, f.album, SmartMedia.search(f.query), f.sort, f.limit, f.playlistId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val playlists = database.personal().observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    /** Jumlah lagu tiap playlist untuk daftar Kelola playlist; playlist kosong tidak muncul di peta. */
    val playlistCounts = database.personal().observeCounts()
        .map { counts -> counts.associate { it.playlistId to it.count } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val favorites = database.personal().references(PersonalLibrary.FAVORITES).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun createPlaylist(name: String, onResult: (String?) -> Unit = {}) = operation(onResult) {
        require(database.personal().all().count { it.id != PersonalLibrary.FAVORITES } < 200) { "Maksimum 200 playlist." }
        database.personal().insert(PersonalLibrary.create(name)); "Playlist dibuat."
    }
    fun renamePlaylist(id: String, name: String, onResult: (String?) -> Unit = {}) = operation(onResult) {
        require(id != PersonalLibrary.FAVORITES)
        database.personal().rename(id, PersonalLibrary.name(name)); "Nama playlist diperbarui."
    }
    fun deletePlaylist(id: String, onResult: (String?) -> Unit = {}) = operation(onResult) {
        require(id != PersonalLibrary.FAVORITES)
        database.personal().delete(id)
        withContext(Dispatchers.Main) { if (filter.value.playlistId == id) filter.value = Filter(kind = "AUDIO") }
        "Playlist dihapus. File lagu tetap tersedia."
    }
    fun addToPlaylist(item: MediaItem, id: String) = operation {
        database.withTransaction {
            require(dao.get(item.uri)?.kind == "AUDIO") { "Pilih lagu yang masih ada di Koleksi." }
            if (id == PersonalLibrary.FAVORITES) database.personal().insert(PersonalPlaylist(id, "Favorit", 0))
            if (!database.personal().contains(id, item.uri)) {
                require(database.personal().count(id) < 3000) { "Maksimum 3000 lagu per playlist." }
                database.personal().add(PlaylistMember(id, item.uri, database.personal().nextPosition(id)))
            }
        }
        "Lagu ditambahkan."
    }
    fun removeFromPlaylist(item: MediaItem, id: String) = operation {
        database.personal().remove(id, item.uri); "Lagu dikeluarkan dari daftar. File tetap tersedia."
    }
    val totals = dao.totals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    /** Jumlah item per folder dari database penuh, memakai filter yang sama dengan daftar. */
    val folderCounts = filter.debounce(180).flatMapLatest { f ->
        dao.folderCounts(f.kind, f.folder, f.artist, f.album, SmartMedia.search(f.query), f.playlistId)
    }.map { rows -> rows.associate { it.folder to it.count } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
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
            MediaFileOperations.mutex.withLock { runCatching { LyricPublisher(app).recover(); MediaBackup(app).recoverLocked() } }
            repository.validateRecent(force=true)
            database.downloads().observe().map { list -> list.filter { it.state == JobState.COMPLETED && it.autoLibrary }.map { it.id to it.outputUri } }.distinctUntilChanged().collect { jobs ->
                for ((id, _) in jobs.take(200)) runCatching { repository.indexDownload(id) }
            }
        }
        viewModelScope.launch {
            callbackFlow {
                val observer=object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) { trySend(Unit) }
                }
                app.contentResolver.registerContentObserver("content://media".toUri(),true,observer)
                awaitClose { app.contentResolver.unregisterContentObserver(observer) }
            }.debounce(750).collect { validateAvailability() }
        }
        updateDeviceStorage()
    }
    private var availabilityCheck: Job? = null
    private var availabilityRequested = false
    private var autoScan: Job? = null
    private var lastAutoScanAt = 0L
    fun validateAvailability() {
        // Calls and this flag belong to Main; retain events arriving during provider I/O.
        availabilityRequested = true
        if (availabilityCheck?.isActive == true) return
        availabilityCheck = viewModelScope.launch {
            try {
                do {
                    availabilityRequested = false
                    repository.validateRecent(force = true)
                } while (availabilityRequested)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _message.value="Koleksi belum dapat diperiksa; coba lagi setelah akses penyimpanan tersedia." }
        }
    }
    private var playbackRequest: Job? = null
    /** Explicit playback loads the selected list, not just its first visible page. No bitmaps are loaded. */
    fun playSelection(player: PlayerViewModel, startUri: String? = null, shuffle: Boolean? = null) {
        val captured = filter.value
        playbackRequest?.cancel()
        playbackRequest = viewModelScope.launch {
            try {
                val selected = withContext(Dispatchers.IO) { dao.page(captured.kind, captured.folder, captured.artist,
                    captured.album, SmartMedia.search(captured.query), captured.sort, 3000, captured.playlistId).first() }
                if (filter.value.copy(limit = captured.limit) != captured) return@launch
                val start = startUri?.let { uri -> selected.indexOfFirst { it.uri == uri } } ?: 0
                if (selected.isEmpty() || start < 0) { _message.value = "Lagu tidak lagi cocok dengan daftar. Pilih ulang."; return@launch }
                player.playAll(selected, start, shuffle)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _message.value = "Daftar belum dapat diputar. Periksa akses file lalu coba lagi." }
        }
    }
    fun filter(value: Filter) { filter.value = value.copy(limit = 100) }
    fun more() { filter.update { it.copy(limit = (it.limit + 100).coerceAtMost(3000)) } }
    fun period(days: Int) { since.value = DashboardCharts.since(days, java.time.LocalDate.now(), java.time.ZoneId.systemDefault()) }
    fun clearMessage() { _message.value = null }
    fun updateDeviceStorage() { viewModelScope.launch(Dispatchers.IO) {
        deviceStorage.value = runCatching { android.os.StatFs(getApplication<Application>().filesDir.path).let { DeviceStorage(it.totalBytes, it.availableBytes) } }.getOrNull()
    } }
    private fun operation(onResult: ((String?) -> Unit)? = null, block: suspend () -> String) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _message.value = block()
                withContext(Dispatchers.Main) { onResult?.invoke(null) }
            }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                val message = error.message ?: "Operasi media gagal."
                _message.value = message
                withContext(Dispatchers.Main) { onResult?.invoke(message) }
            }
            finally { _busy.value = false; updateDeviceStorage() }
        }
    }
    private suspend fun scanCatalog(): String {
        val (count, failed, discovered, unreadable) = repository.scan()
        _lastScan.value = System.currentTimeMillis()
        getApplication<Application>().getSharedPreferences("library", 0).edit { putLong("lastScan", _lastScan.value) }
        if (count == 0 && discovered == 0) return "Tidak ada media terlihat di folder. Bila file memang ada, periksa izin baca atau tambahkan folder lewat Pengaturan → Penyimpanan."
        val suffix = buildString {
            if (failed > 0) append("; $failed file tidak dapat dibaca. Statistik dapat parsial.")
            else append(".")
            if (unreadable > 0) append(" $unreadable folder tidak dapat diakses; periksa izin di Pengaturan → Penyimpanan.")
        }
        return "$count media diperiksa$suffix"
    }
    fun scan() = operation { scanCatalog() }
    /** Cukup izin untuk membaca seluruh isi folder: tree pilihan atau grant default Download/TubeFlow. */
    fun hasFolderAccess(): Boolean = SettingsStore.collectionFolders.isNotEmpty() || SettingsStore.storageTree != null ||
        StorageFolderAccess.defaultTree(getApplication(), SettingsStore.storageFolder) != null
    /** Izin baca media membuat query MediaStore melihat file lama milik versi/SAF lain tanpa grant SAF. */
    fun hasReadPermission(): Boolean = MediaPermissions.hasFullRead(getApplication())
    /** Android 14+ dapat memberi izin visual sebagian; media terpilih tetap layak dipindai. */
    fun hasPartialVisualAccess(): Boolean = MediaPermissions.hasPartialVisualAccess(getApplication())
    /** Pemulihan otomatis: saat koleksi kosong (biasanya setelah data aplikasi dihapus), pindai sendiri tanpa memilih folder. */
    fun scanIfEmpty() {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (dao.oldest(1).isEmpty()) {
                    val (count, failed) = repository.scan()
                    _lastScan.value = System.currentTimeMillis()
                    getApplication<Application>().getSharedPreferences("library", 0).edit { putLong("lastScan", _lastScan.value) }
                    if (count > 0 || failed > 0) _message.value =
                        "$count media dipindai otomatis" + if (failed > 0) "; $failed file tidak dapat dibaca." else "."
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _message.value = error.message ?: "Pemindaian otomatis gagal; buka kembali aplikasi untuk mencoba lagi." }
            finally { _busy.value = false; updateDeviceStorage() }
        }
    }
    /**
     * Menemukan file yang belakangan masuk ke folder koleksi tanpa menunggu tombol Segarkan.
     * Dipanggil saat aplikasi kembali aktif dan saat tab Koleksi dibuka; validasi file lama tetap
     * ditangani [validateAvailability], jadi pemindaian ini tidak mengulang seluruh baris katalog.
     */
    fun refreshCatalogIfStale() {
        val now = System.currentTimeMillis()
        if (_busy.value || autoScan?.isActive == true ||
            (lastAutoScanAt > 0 && now - lastAutoScanAt < AUTO_SCAN_INTERVAL_MS)) return
        lastAutoScanAt = now
        autoScan = viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.scan(validateExisting = false)
                _lastScan.value = System.currentTimeMillis()
                getApplication<Application>().getSharedPreferences("library", 0).edit { putLong("lastScan", _lastScan.value) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { lastAutoScanAt = 0 }
        }
    }
    fun grantFolder(uri: android.net.Uri?) {
        if (uri == null) return
        operation {
            val app = getApplication<Application>()
            runCatching { app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                .recoverCatching { app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                .getOrThrow()
            // Grant folder bawaan hanya memulihkan akses baca tanpa memindah tujuan output;
            // folder lain menjadi tujuan output pilihan, sama seperti pemilihan folder di Pengaturan.
            val default = runCatching {
                StorageFolderAccess.matchesDefault(uri.authority, DocumentsContract.getTreeDocumentId(uri), SettingsStore.storageFolder)
            }.getOrDefault(false)
            SettingsStore.setStorageTree(app, if (default) null else uri.toString())
            scanCatalog()
        }
    }

    private fun treeId(uri: android.net.Uri): String? = CollectionFolders.documentId(uri.toString())
        ?: runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()

    /**
     * Menambahkan folder SAF sebagai sumber Koleksi tambahan. Ini tidak memindahkan tujuan download;
     * folder bawaan `Download/[folder]` tetap menjadi koleksi utama dan sudah dipindai otomatis.
     */
    fun addCollectionFolder(uri: android.net.Uri) = operation {
        val app = getApplication<Application>()
        val tree = CollectionFolders.normalize(uri.toString())
        val id = treeId(uri) ?: error("Folder tidak valid atau tidak dapat dibaca.")
        val existing = SettingsStore.collectionFolders
        require(existing.size < CollectionFolders.MAX) { "Maksimum ${CollectionFolders.MAX} folder koleksi tambahan." }
        require(existing.none { CollectionFolders.normalize(it.uri) == tree }) { "Folder ini sudah ada di koleksi." }
        val defaultUri = StorageFolderAccess.defaultTree(app, SettingsStore.storageFolder)
            ?: DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download/${SettingsStore.storageFolder}")
        val defaultId = treeId(defaultUri) ?: "primary:Download/${SettingsStore.storageFolder}"
        require(!CollectionFolders.overlaps(id, defaultId)) {
            "Folder ini masih termasuk Downloads/${SettingsStore.storageFolder}; subfoldernya sudah dipindai otomatis."
        }
        val outputId = SettingsStore.storageTree?.toUri()?.let(::treeId)
        require(outputId == null || !CollectionFolders.overlaps(id, outputId)) {
            "Folder ini sama dengan folder tujuan download. Pilih folder lain agar hasil unduhan dan koleksi tidak bercampur."
        }
        existing.forEach { other ->
            val otherId = treeId(other.uri.toUri()) ?: return@forEach
            require(!CollectionFolders.overlaps(id, otherId)) {
                if (id.startsWith("$otherId/")) "Folder ini sudah tercakup folder koleksi lain."
                else "Folder ini menaungi folder koleksi yang sudah ditambahkan."
            }
        }
        // Izin tulis ikut disimpan: tanpa itu file di folder ini bisa dipindai tetapi selalu ditolak
        // saat dihapus. Provider yang hanya menawarkan baca tetap diterima; hapus akan memberi petunjuk.
        runCatching {
            app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.recoverCatching {
            app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.getOrThrow()
        val labels = existing.map { it.label }.toMutableSet()
        labels += "Download/${SettingsStore.storageFolder}"
        SettingsStore.storageTree?.let { labels += CollectionFolders.FALLBACK_LABEL }
        val label = CollectionFolders.uniqueLabel(CollectionFolders.labelForId(id), labels)
        SettingsStore.setCollectionFolders(app, existing + CollectionFolder(tree, label))
        "Folder \"$label\" ditambahkan. ${scanCatalog()}"
    }

    /** Melepas folder koleksi dari indeks tanpa menghapus file atau subfolder di penyimpanan. */
    fun removeCollectionFolder(folder: CollectionFolder) = operation {
        val app = getApplication<Application>()
        val removed = repository.removeCollectionSource(folder.uri)
        SettingsStore.setCollectionFolders(app, SettingsStore.collectionFolders.filterNot {
            CollectionFolders.normalize(it.uri) == CollectionFolders.normalize(folder.uri)
        })
        runCatching {
            app.contentResolver.releasePersistableUriPermission(folder.uri.toUri(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.recoverCatching {
            app.contentResolver.releasePersistableUriPermission(folder.uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        "Folder \"${folder.label}\" dilepas dari koleksi. $removed baris indeks dibersihkan; file di penyimpanan tidak dihapus."
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

    /** Teks lirik tersimpan untuk alat ketuk-waktu; batasnya sama dengan editor lirik. */
    suspend fun lyricText(item: MediaItem): String = withContext(Dispatchers.IO) {
        val raw = item.lyricUri ?: error("Lagu ini belum punya lirik tersimpan. Cari lirik dulu.")
        val input = getApplication<Application>().contentResolver.openInputStream(raw.toUri()) ?: error("Lirik tidak dapat dibaca.")
        input.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                require(out.size() + read <= 512 * 1024) { "LRC terlalu besar." }
                out.write(buffer, 0, read)
            }
            out.toByteArray().toString(Charsets.UTF_8)
        }
    }

    /** Simpan LRC hasil ketuk-waktu lewat jalur publikasi lirik bersama (backup + verifikasi). */
    fun publishLyricsText(item: MediaItem, raw: String, onResult: (String?) -> Unit = {}) = operation(onResult) {
        val text = Lyrics.manualText(raw, item.durationMs)
        withContext(Dispatchers.IO) { MediaFileOperations.mutex.withLock {
            val current = dao.get(item.uri) ?: error("Media telah dihapus.")
            require(current.location == item.location && current.displayName == item.displayName && current.lyricUri == item.lyricUri) {
                "Media/lirik berubah; buka ulang alat lirik."
            }
            val expected = current.lyricUri?.let { mapOf(it to lyricEditor.captureForRefetch(it)) }.orEmpty()
            val uri = LyricPublisher(getApplication()).publish(current, text, expected)
            dao.put(current.copy(lyricUri = uri, lyricStatus = LyricStatus.SAVED))
            current.jobId?.let { database.downloads().lyricStatus(it, LyricStatus.SAVED) }
            dao.event(MediaEvent(jobId = current.jobId.orEmpty(), action = "LYRICS_TAP_SYNC", kind = "AUDIO", platformId = current.platformId, at = System.currentTimeMillis()))
        } }
        "Waktu lirik disimpan. Dengarkan beberapa bagian lagu; koreksi offset tersedia di editor waktu."
    }
    fun correct(item: MediaItem, preview: LyricEditor.Preview, offset: Long) = operation {
        lyricEditor.apply(item, preview, offset)
        dao.event(MediaEvent(jobId = item.jobId.orEmpty(), action = "LYRIC_TIMING", kind = "AUDIO", platformId = item.platformId, at = System.currentTimeMillis()))
        "Waktu lirik disimpan. Pemutar TubeFlow memperbarui lirik; buka ulang lirik jika memakai pemutar lain."
    }
    data class LyricsState(val item: MediaItem, val title: String, val artist: String,
        val choices: List<Lyrics.Choice> = emptyList(), val loading: Boolean = false,
        val saving: Boolean = false, val searched: Boolean = false, val error: String? = null,
        val canExpand: Boolean = false, val failureStatus: String? = null, val httpCode: Int? = null) {
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
    /** Item koleksi untuk alat lirik di pemutar (cari lirik / atur waktu), dipetakan dari URI track. */
    suspend fun itemFor(uri: String): MediaItem? = withContext(Dispatchers.IO) { dao.getByAccess(uri) ?: dao.get(uri) }
    /** Buka pencarian lirik langsung dari pemutar berdasarkan URI track — tanpa kembali ke Koleksi. */
    fun openLyricsForTrack(uri: String) { viewModelScope.launch {
        try {
            val item = itemFor(uri) ?: error("Lagu ini belum tercatat di koleksi.")
            openLyrics(item)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { _message.value = failure.message }
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
    fun searchLyrics(title: String, artist: String, expand: Boolean = false) {
        val previous=_lyrics.value ?: return
        if (previous.loading || previous.saving) return
        val session=++lyricsSession
        _lyrics.value=previous.copy(title=title.trim(),artist=artist.trim(),loading=true,choices=emptyList(),error=null,searched=false,canExpand=false,failureStatus=null,httpCode=null)
        lyricsRequest=viewModelScope.launch {
            try {
                val captured=withContext(Dispatchers.IO) { MediaFileOperations.mutex.withLock {
                    val current=dao.get(previous.item.uri) ?: error("Media telah dihapus; buka ulang pencarian.")
                    require(current.availability == "AVAILABLE" && displayName(current.location) == current.displayName) { "Nama/lokasi media berubah; buka ulang pencarian." }
                    val job=current.jobId?.let { database.downloads().get(it) }
                    val bytes=current.lyricUri?.let { mapOf(it to lyricEditor.captureForRefetch(it)) }.orEmpty()
                    Triple(current,bytes,job)
                } }
                val result=runInterruptible(Dispatchers.IO) {
                    val app=getApplication<Application>()
                    Lyrics.initialize(java.io.File(app.cacheDir,"lyrics-responses"),java.io.File(app.noBackupFilesDir,"lyrics-cooldown.json"))
                    Lyrics.search(title,artist,expand)
                }
                if (session != lyricsSession) return@launch
                expectedLyrics=captured.second; expectedJob=captured.third
                _lyrics.value=_lyrics.value?.copy(item=captured.first,choices=result.choices,loading=false,searched=true,
                    error=result.failureStatus?.let(LyricStatus::label),canExpand=result.canExpand,failureStatus=result.failureStatus,httpCode=result.httpCode)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (session == lyricsSession) _lyrics.value=_lyrics.value?.copy(loading=false,error=failure.message ?: "Pencarian gagal.") }
        }
    }
    fun saveManualLyrics(raw: String) {
        val current=_lyrics.value ?: return
        if (current.loading || current.saving) return
        val text=try { Lyrics.manualText(raw,current.item.durationMs) }
        catch (failure: Exception) { _lyrics.value=current.copy(error=failure.message ?: "LRC manual tidak valid."); return }
        _lyrics.value=current.copy(saving=true,error=null,failureStatus=null,httpCode=null)
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
                    val uri=LyricPublisher(getApplication()).publish(item,text,expectedLyrics)
                    dao.put(item.copy(lyricUri=uri,lyricStatus=LyricStatus.SAVED))
                    item.jobId?.let { database.downloads().lyricStatus(it,LyricStatus.SAVED) }
                    dao.event(MediaEvent(jobId=item.jobId.orEmpty(),action="LYRICS_MANUAL",kind="AUDIO",platformId=item.platformId,at=System.currentTimeMillis()))
                } }
                if (session == lyricsSession) { _lyrics.value=null; expectedLyrics=emptyMap(); expectedJob=null }
                _message.value="Lirik manual disimpan. Cek kecocokan vokal; koreksi waktu bila perlu."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (session == lyricsSession) _lyrics.value=_lyrics.value?.copy(saving=false,error=failure.message ?: "Lirik manual gagal disimpan.") }
        }
    }
    fun saveLyrics(choice: Lyrics.Choice, acknowledgedMismatch: Boolean) {
        val current=_lyrics.value ?: return
        if (current.loading || current.saving || !current.choices.contains(choice) || !choice.hasSyncedLyrics) return
        if (!choice.automaticMatch(current.query) && !acknowledgedMismatch) return
        _lyrics.value=current.copy(saving=true,error=null,failureStatus=null)
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
