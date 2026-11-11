package id.local.tubeflow.core

import android.app.Application
import android.content.ContentValues
import android.os.SystemClock
import android.provider.MediaStore
import androidx.compose.runtime.snapshotFlow
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DownloadViewModel(application: Application) : AndroidViewModel(application) {
 private val dao = DownloadDatabase.get(application).downloads()
 private val work = WorkManager.getInstance(application)
 val jobs = dao.observe().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 private val inspectionState=InspectionState()
 private val inspectionCache by lazy { InspectionCache(java.io.File(getApplication<Application>().cacheDir,"inspection-cache")) }
 val metadata = inspectionState.state.map { it.metadata }.stateIn(viewModelScope,SharingStarted.Eagerly,null)
 val playlist = inspectionState.state.map { it.playlist }.stateIn(viewModelScope,SharingStarted.Eagerly,null)
 val busy = inspectionState.state.map { it.busy }.stateIn(viewModelScope,SharingStarted.Eagerly,false)
 val inspectPhase = inspectionState.state.map { it.phase }.stateIn(viewModelScope,SharingStarted.Eagerly,null)
 val inspectStartedAt = inspectionState.state.map { it.startedAt }.stateIn(viewModelScope,SharingStarted.Eagerly,0L)
 private val _error=MutableStateFlow<String?>(null)
 val error: StateFlow<String?> = _error.asStateFlow()
 /** Alasan inspeksi terakhir gagal — menyalakan tombol "Coba lagi" untuk URL yang sama (BUG-006). */
 val inspectError = inspectionState.state.map { it.error }.stateIn(viewModelScope,SharingStarted.Eagerly,null)
 val inspectDetails = inspectionState.state.map { it.details }.stateIn(viewModelScope,SharingStarted.Eagerly,null)
 private val _notice=MutableStateFlow<String?>(null)
 val notice: StateFlow<String?> = _notice.asStateFlow()
 private val _storageBytes=MutableStateFlow<Long?>(null)
 val storageBytes: StateFlow<Long?> = _storageBytes.asStateFlow()
 private val _storageError=MutableStateFlow(false)
 val storageError: StateFlow<Boolean> = _storageError.asStateFlow()
 private val _storageFolderAccess=MutableStateFlow(false)
 val storageFolderAccess: StateFlow<Boolean> = _storageFolderAccess.asStateFlow()
 private val _storageCatalogue=MutableStateFlow(false)
 /**
  * True bila angka ukuran berasal dari katalog media (baris yang sudah terindeks, sama seperti layar
  * Koleksi) sehingga tidak butuh grant folder. Label di Pengaturan ikut dibedakan agar angka ini
  * tidak pernah diklaim sebagai ukuran folder penuh.
  */
 val storageCatalogue: StateFlow<Boolean> = _storageCatalogue.asStateFlow()
 private val _storageUnknownSizes=MutableStateFlow(0L)
 val storageUnknownSizes: StateFlow<Long> = _storageUnknownSizes.asStateFlow()
 private val _clearing=MutableStateFlow(false)
 /** True selama "Hapus semua file" berjalan — tombol di Pengaturan dinonaktifkan. */
 val clearing: StateFlow<Boolean> = _clearing.asStateFlow()
 private val _temporaryUsage = MutableStateFlow<PrivateStorage.Usage?>(null)
 val temporaryUsage = _temporaryUsage.asStateFlow()
 private val _cleaningPartial = MutableStateFlow(false)
 val cleaningPartial = _cleaningPartial.asStateFlow()
 fun clearPartialFiles() {
  if (_cleaningPartial.value) return
  _cleaningPartial.value = true
  viewModelScope.launch(Dispatchers.IO) {
   var removed = 0; var skipped = 0; var failed = 0
   try {
    val app = getApplication<Application>()
    val root = java.io.File(app.noBackupFilesDir, "downloads")
    val entries = if (root.exists()) root.listFiles() ?: error("Folder sementara belum dapat dibaca.") else emptyArray()
    for (directory in entries) {
     ensureActive()
     val id = directory.name
     val lease = DownloadFileAccess.lock(id)
     if (!lease.tryLock()) { skipped++; continue }
     try {
      val row = dao.get(id)
      if (Engine.isRunning(id) || hasActiveWork(id) || row?.state in setOf(JobState.QUEUED,JobState.RUNNING,JobState.COMPLETED)) { skipped++; continue }
      if (PrivateStorage.deleteJob(root,directory)) {
       row?.let { dao.discardedPartial(id,it.generation) }
       removed++
      } else failed++
     } finally { lease.unlock() }
    }
    _notice.value="$removed folder sementara dibersihkan; $skipped job aktif/dilindungi dilewati; $failed gagal. Media hasil download tetap tersimpan."
   } catch (cancelled: CancellationException) { throw cancelled }
   catch (_: Exception) { _error.value="Berkas sementara belum dapat dibersihkan seluruhnya. Coba lagi." }
   finally { _cleaningPartial.value=false; refreshStorage() }
  }
 }
 private var storageRefresh: Job? = null
 /** Percobaan penjadwalan ulang per job untuk penjaga antrean; dideklarasikan sebelum `init` karena
  * `viewModelScope.launch` memakai Main.immediate dan bisa menjalankan `watchQueue` saat konstruksi. */
 private val rescheduleAttempts = ConcurrentHashMap<String, Int>()
 /** Waktu pertama sebuah job terlihat QUEUED; dipakai untuk masa tenggang penggantian jadwal. */
 private val queueWaitingSince = ConcurrentHashMap<String, Long>()
 init {
   refreshStorage()
   // Penjaga antrean: job QUEUED tanpa pekerjaan aktif dijadwalkan ulang agar playlist tidak
   // tertinggal di "Menunggu giliran" setelah enqueue terputus atau pekerjaannya dihentikan sistem.
   viewModelScope.launch { watchQueue() }
   // Syarat WorkManager menempel pada request lama. Saat toggle WiFi/daya diubah, pasang ulang
   // antrean yang belum berjalan agar tidak terus menunggu syarat yang sudah tidak dipilih.
   viewModelScope.launch {
    snapshotFlow { SettingsStore.downloadPolicyRevision }.drop(1).collect {
     rescheduleQueued("Syarat unduhan berubah; antrean menunggu dijadwalkan ulang.")
    }
   }
   // Panaskan engine saat aplikasi dibuka supaya inspeksi pertama tidak menanggung seluruh biaya init.
   viewModelScope.launch {
    delay(800)
    withContext(Dispatchers.IO) { runCatching { Engine.initialize(getApplication()) } }
   }
   // Angka penyimpanan ikut naik/turun begitu ada unduhan selesai (atau file dihapus) tanpa perlu tombol Segarkan.
  viewModelScope.launch { jobs.map { list -> list.count { it.state == JobState.COMPLETED } }.distinctUntilChanged().collect { refreshStorage() } }
  // Pemindaian, impor, hapus, dan hasil karaoke mengubah katalog tanpa mengubah riwayat download.
  viewModelScope.launch {
   DownloadDatabase.get(application).media().totals()
    .map { listOf(it.audio, it.video, it.audioBytes, it.videoBytes, it.unknownSizes) }
    .distinctUntilChanged().collect { refreshStorage() }
  }
 }
 fun refreshStorage() {
  storageRefresh?.cancel()
  storageRefresh=viewModelScope.launch {
   _storageError.value=false
   _storageBytes.value=null
   _temporaryUsage.value=withContext(Dispatchers.IO) {
    runCatching { PrivateStorage.summary(getApplication<Application>().cacheDir,getApplication<Application>().noBackupFilesDir) }.getOrNull()
   }
   val result=withContext(Dispatchers.IO) { runCatching { measureStorage() } }
   ensureActive()
   _storageFolderAccess.value=result.getOrNull()?.folderAccess == true
   _storageCatalogue.value=result.getOrNull()?.fromCatalogue == true
   _storageUnknownSizes.value=result.getOrNull()?.unknownSizes ?: 0L
   _storageBytes.value=result.getOrNull()?.bytes
   _storageError.value=result.isFailure
  }
 }
 private data class StorageMeasurement(val bytes: Long, val folderAccess: Boolean, val fromCatalogue: Boolean, val unknownSizes: Long = 0)
 private suspend fun measureStorage(): StorageMeasurement {
  SettingsStore.storageTree?.let { return StorageMeasurement(measureTree(it.toUri()),true,false) }
  // Returning to the default output folder must not discard an existing read grant.
  defaultTreeGrant()?.let { return StorageMeasurement(measureTree(it),true,false) }
  // Tanpa grant: pakai katalog yang sama dengan layar Koleksi (baris media yang sudah terindeks).
  // Angka ini selalu terbaca — tidak ada enumerasi folder atau izin tambahan yang diminta lebih dulu —
  // dan labelnya di Pengaturan dibedakan supaya tidak diklaim sebagai ukuran folder penuh.
  val totals=DownloadDatabase.get(getApplication()).media().totals().first()
  if (totals.audio > 0 || totals.video > 0) {
   return StorageMeasurement(Math.addExact(totals.audioBytes ?: 0L,totals.videoBytes ?: 0L),false,true,totals.unknownSizes)
  }
  // Katalog masih kosong: pertahankan jalur lama supaya folder yang belum dipindai tetap terbaca.
  return StorageMeasurement(measureDownloadsFolder(),false,false)
 }
 /** Jumlah ukuran baris MediaStore pada folder output; dipakai saat katalog masih kosong. */
 private fun measureDownloadsFolder(): Long {
  var total=0L
  // Batas folder HARUS berhenti tepat di folder pilihan: dulu prefix "Download/TubeFlow%" ikut menghitung
  // folder saudara (TubeFlowBackup) dan "_" dalam nama jadi wildcard SQL (BUG-012).
  val folder="Download/${SettingsStore.storageFolder}"
  val escaped=folder.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")
  val selection="${MediaStore.Downloads.RELATIVE_PATH} = ? OR ${MediaStore.Downloads.RELATIVE_PATH} LIKE ? ESCAPE '\\'"
  val cursor=getApplication<Application>().contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Downloads.SIZE), selection, arrayOf("$folder/","$escaped/%"), null)
   ?: error("Ukuran penyimpanan belum dapat dibaca.")
  cursor.use { while(it.moveToNext()) {
   require(!it.isNull(0) && it.getLong(0) >= 0) { "Ukuran file belum tersedia." }
   total=Math.addExact(total,it.getLong(0))
  } }
  return total
 }
 /** Sums file sizes under a picked SAF tree (bounded walk so huge trees stay cheap). */
 private fun measureTree(root: android.net.Uri): Long {
  val resolver=getApplication<Application>().contentResolver
  var total=0L
  var visited=0
  fun walk(docId: String, depth: Int) {
   require(depth <= 8) { "Folder terlalu dalam untuk dihitung sekaligus." }
   val children=android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(root, docId)
   val cursor=resolver.query(children, arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID, android.provider.DocumentsContract.Document.COLUMN_SIZE, android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)
    ?: error("Folder belum dapat dibaca.")
   cursor.use { c ->
    while (c.moveToNext()) {
     require(visited < 4000) { "Folder terlalu besar untuk dihitung sekaligus." }
     visited++
     val mime=c.getString(2) ?: ""
     val id=c.getString(0) ?: error("Identitas file belum tersedia.")
     // MIME direktori yang benar "vnd.android.document/directory" — endsWith("/") tak pernah cocok (BUG-011).
     if (mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) walk(id, depth+1)
     else {
      require(!c.isNull(1) && c.getLong(1) >= 0) { "Ukuran file belum tersedia." }
      total=Math.addExact(total,c.getLong(1))
     }
    }
   }
  }
  walk(android.provider.DocumentsContract.getTreeDocumentId(root), 0)
  return total
 }
 /** Grant SAF tersimpan yang menunjuk folder bawaan (pernah dipilih lewat pemilihan folder) — dipakai mengukur ukuran folder penuh dan membersihkan folder kosong. */
 private fun defaultTreeGrant(requireWrite: Boolean = false): android.net.Uri? =
  StorageFolderAccess.defaultTree(getApplication(), SettingsStore.storageFolder, requireWrite)
 // Old history keeps its original title/output; recover artwork from the actual audio, not a guessed URL.
 private val artworkRecovery = viewModelScope.launch(Dispatchers.IO) {
  val attempted=mutableSetOf<String>()
  dao.observe().collect { rows ->
   rows.forEach { job ->
    if (job.platformId.isBlank()) dao.platform(job.id, SourcePlatforms.fromUrl(job.url).id)
    if (job.state == JobState.COMPLETED && (job.coverPath.isBlank() || !java.io.File(job.coverPath).isFile) && attempted.add(job.id)) {
     val path=CoverCache.fromMedia(getApplication(),job) ?: CoverCache.fetch(getApplication(),job)
     if (path != null && dao.recoveredCover(job.id,job.generation,job.outputUri.orEmpty(),path) == 0) java.io.File(path).delete()
    }
   }
  }
 }
 /**
  * Pratinjau link Spotify: metadata track + kandidat video YouTube. State terpisah dari inspeksi
  * engine karena link Spotify tidak pernah masuk ke yt-dlp; sumber audionya selalu YouTube.
  */
 private val _spotify=MutableStateFlow(SpotifyPreview())
 val spotify: StateFlow<SpotifyPreview> = _spotify.asStateFlow()
 private var spotifyInspection: Job?=null
 private var spotifyToken: String?=null
 fun inspectSpotify(url: String) {
  val trimmed=url.trim()
  if (!Spotify.isTrackLink(trimmed)) return
  spotifyToken?.let(Engine::cancel); spotifyInspection?.cancel()
  val id=UUID.randomUUID().toString()
  spotifyToken=id
  _spotify.value=SpotifyPreview(url=trimmed,busy=true,phase=InspectionPhase.PREPARING_ENGINE)
  spotifyInspection=viewModelScope.launch {
   try {
    val track=Spotify.fetchTrack(getApplication(),trimmed)
    if (_spotify.value.url != trimmed) return@launch
    _spotify.value=_spotify.value.copy(track=track,phase=InspectionPhase.SEARCHING)
    val videos=Engine.search(getApplication(),Spotify.searchQuery(track),id) { phase ->
     if (_spotify.value.url == trimmed) _spotify.value=_spotify.value.copy(phase=phase)
    }
    if (_spotify.value.url != trimmed) return@launch
    _spotify.value=SpotifyPreview(url=trimmed,track=track,candidates=SpotifyMatch.rank(track,videos))
   } catch(cancelled: CancellationException) { throw cancelled }
   catch(error: Exception) {
    val details=withContext(Dispatchers.IO) { FailureDetailsStore(getApplication()).capture(error,"SPOTIFY_INSPECTION") }
    val message=error.message?.takeIf { it.isNotBlank() } ?: details.userMessage
    // Pratinjau yang sudah dibersihkan/diganti tidak boleh memunculkan error tertinggal.
    if (_spotify.value.url == trimmed) {
     _spotify.value=_spotify.value.copy(error=message)
     _error.value=message
    }
   }
   finally {
    // Inspeksi yang sudah digantikan tidak boleh mematikan indikator milik inspeksi baru.
    if (spotifyToken == id) {
     spotifyToken=null
     if (_spotify.value.url == trimmed) _spotify.value=_spotify.value.copy(busy=false,phase=null)
    }
   }
  }
 }
 fun clearSpotify() {
  spotifyToken?.let(Engine::cancel)
  spotifyToken=null
  spotifyInspection?.cancel(); spotifyInspection=null
  if (_spotify.value != SpotifyPreview()) _spotify.value=SpotifyPreview()
 }
 /** Album/playlist Spotify: metadata dulu, pencocokan YouTube per track dijalankan terpisah. */
 private val _spotifyCollection=MutableStateFlow(SpotifyCollectionPreview())
 val spotifyCollection: StateFlow<SpotifyCollectionPreview> = _spotifyCollection.asStateFlow()
 private var collectionInspection: Job?=null
 private var collectionToken: String?=null
 private var collectionMatch: Job?=null
 private var collectionMatchToken: String?=null
 fun inspectSpotifyCollection(url: String) {
  val trimmed=url.trim()
  if (!Spotify.isCollectionLink(trimmed)) return
  cancelSpotifyMatch()
  collectionInspection?.cancel()
  val id=UUID.randomUUID().toString()
  collectionToken=id
  _spotifyCollection.value=SpotifyCollectionPreview(url=trimmed,busy=true,phase=InspectionPhase.PREPARING_ENGINE)
  collectionInspection=viewModelScope.launch {
   try {
    _spotifyCollection.value=_spotifyCollection.value.copy(phase=InspectionPhase.FETCHING_COLLECTION)
    val collection=Spotify.fetchCollection(getApplication(),trimmed)
    if (_spotifyCollection.value.url != trimmed) return@launch
    _spotifyCollection.value=SpotifyCollectionPreview(url=trimmed,collection=collection)
   } catch(cancelled: CancellationException) { throw cancelled }
   catch(error: Exception) {
    val details=withContext(Dispatchers.IO) { FailureDetailsStore(getApplication()).capture(error,"SPOTIFY_COLLECTION") }
    val message=error.message?.takeIf { it.isNotBlank() } ?: details.userMessage
    if (_spotifyCollection.value.url == trimmed) {
     _spotifyCollection.value=_spotifyCollection.value.copy(error=message)
     _error.value=message
    }
   }
   finally {
    if (collectionToken == id) {
     collectionToken=null
     if (_spotifyCollection.value.url == trimmed) _spotifyCollection.value=_spotifyCollection.value.copy(busy=false,phase=null)
    }
   }
  }
 }
 fun clearSpotifyCollection() {
  collectionToken=null
  collectionInspection?.cancel(); collectionInspection=null
  cancelSpotifyMatch()
  if (_spotifyCollection.value != SpotifyCollectionPreview()) _spotifyCollection.value=SpotifyCollectionPreview()
 }
 /**
  * Cocokkan track terpilih satu per satu (sekuensial — satu proses yt-dlp pada satu waktu) dan
  * simpan kandidat terbaik per track. Track yang sudah punya kandidat tidak dicari ulang.
  */
 fun matchSpotifyTracks(tracks: List<SpotifyTrack>) {
  val state=_spotifyCollection.value
  val collection=state.collection ?: return
  if (state.matching) return
  val wanted=tracks.filter { it.playable && !state.matches.containsKey(it.id) }
  if (wanted.isEmpty()) return
  val token=UUID.randomUUID().toString().replace("-","")
  collectionMatchToken=token
  _spotifyCollection.value=state.copy(matching=true,done=0,total=wanted.size)
  collectionMatch=viewModelScope.launch {
   val matched=state.matches.toMutableMap()
   val failures=state.failures.toMutableMap()
   var done=0
   try {
    wanted.forEach { track ->
     ensureActive()
     if (_spotifyCollection.value.collection?.id != collection.id) return@launch
     try {
      val videos=Engine.search(getApplication(),Spotify.searchQuery(track),token,limit=3)
      val best=SpotifyMatch.rank(track,videos).firstOrNull()
      if (best == null) failures[track.id]="Tidak ada kandidat YouTube." else { matched[track.id]=best; failures.remove(track.id) }
     } catch(cancelled: CancellationException) { throw cancelled }
     catch(error: Exception) { failures[track.id]=error.message?.takeIf { it.isNotBlank() } ?: "Pencarian gagal." }
     done++
     // State bisa dibersihkan/diganti saat pencarian terakhir berjalan — jangan tulis ke pratinjau lain.
     val current=_spotifyCollection.value
     if (current.collection?.id == collection.id) _spotifyCollection.value=current.copy(done=done,matches=matched.toMap(),failures=failures.toMap())
    }
   } catch(cancelled: CancellationException) { throw cancelled }
   catch(error: Exception) { _error.value=userMessage(error) }
   finally {
    if (collectionMatchToken == token) {
     collectionMatchToken=null
     if (_spotifyCollection.value.collection?.id == collection.id) _spotifyCollection.value=_spotifyCollection.value.copy(matching=false)
    }
   }
  }
 }
 fun cancelSpotifyMatch() {
  collectionMatchToken?.let(Engine::cancel)
  collectionMatchToken=null
  collectionMatch?.cancel(); collectionMatch=null
  if (_spotifyCollection.value.matching) _spotifyCollection.value=_spotifyCollection.value.copy(matching=false)
 }
 /** Antrekan track terpilih yang sudah punya kandidat; satu job per lagu, satu folder koleksi. */
 fun enqueueSpotifyCollection(options: DownloadOptions, collection: SpotifyCollection, tracks: List<SpotifyTrack>, onQueued: ((String) -> Unit)? = null) {
  if(submitting) { _error.value="Masih memproses permintaan sebelumnya. Coba lagi sebentar."; return }
  submitting=true
  // Ditangkap SEBELUM state dibersihkan pemanggil; coroutine belum tentu berjalan saat itu.
  val matches=_spotifyCollection.value.matches
  viewModelScope.launch {
   try {
    // Album memakai artis (mis. ed-sheeran); playlist memakai judulnya (mis. today-s-top-hits).
    val folder=(if (collection.kind == SpotifyKind.ALBUM) Selection.artistFolder(collection.owner) else Selection.artistFolder(collection.title)).orEmpty()
    var queued=0; var skipped=0
    var firstQueued: String? = null
    tracks.filter { it.playable }.forEach { track ->
     val candidate=matches[track.id] ?: run { skipped++; return@forEach }
     try {
      submit(options.copy(url=candidate.video.url), durationSeconds=candidate.video.durationSeconds.takeIf { it > 0 },
        subfolder=folder, entryMetadata=Spotify.toMetadata(track,candidate.video), autoFolder="collection", onQueued = { id ->
         if (firstQueued == null) { firstQueued = id; onQueued?.invoke(id) }
        })
      queued++
     } catch(cancelled: CancellationException) { throw cancelled }
     catch(_: Exception) { skipped++ }
    }
    _notice.value="${collection.title}: $queued lagu diantrekan" + (if (folder.isNotBlank()) " ke folder $folder" else "") + (if(skipped > 0) "; $skipped dilewati." else ".")
    if (queued == 0) _error.value="Tidak ada lagu yang bisa diantrekan dari ${collection.title}."
   } catch(cancelled: CancellationException) { throw cancelled }
   catch(error: Exception) { _error.value=userMessage(error) }
   finally { submitting=false }
  }
 }
 /** Satu track Spotify diantrekan memakai video YouTube pilihan pengguna, dengan tag metadata Spotify. */
 fun enqueueSpotify(options: DownloadOptions, track: SpotifyTrack, candidate: SpotifyCandidate, onQueued: ((String) -> Unit)? = null) {
  if(submitting) { _error.value="Masih memproses permintaan sebelumnya. Coba lagi sebentar."; return }
  submitting=true
  viewModelScope.launch {
   try {
    require(Selection.videoId(candidate.video.url) != null) { "Video YouTube hasil pencocokan tidak valid." }
    submit(options.copy(url=candidate.video.url), durationSeconds=candidate.video.durationSeconds.takeIf { it > 0 },
      entryMetadata=Spotify.toMetadata(track,candidate.video), onQueued=onQueued)
   }
   catch(cancelled: CancellationException) { throw cancelled }
   catch(error: Exception) { _error.value=userMessage(error) }
   finally { submitting=false }
  }
 }
 private var inspection: Job?=null
 private val inspectionId: String? get()=inspectionState.token
 /** Jenis inspeksi terakhir — retry harus mengulang operasi yang sama, bukan yang lain (BUG-006/R6). */
 private var lastInspectPlaylist=false
 private val recovery = viewModelScope.launch(Dispatchers.IO) {
   StalePublicationCleanup.recover(application)
   dao.observe().first().filter {
    it.state in setOf(JobState.RUNNING,JobState.QUEUED,JobState.FAILED,JobState.CANCELLED) ||
     it.pendingUri != null || it.sidecarPending != null
   }.forEach { job ->
    val infos=work.getWorkInfosByTag(job.id).get()
    if(infos.isEmpty() || infos.all { it.state.isFinished }) {
     // URI pending/sidecar memakai helper bersama (BUG-003); jurnal hanya dilepas bila provider
     // membuktikan berkas hilang, supaya referensi ke file yang menolak dihapus tidak hilang (BUG-008).
     val pendingGone=DownloadRecovery.clearPendingAfterDelete(job) { raw -> StorageUris.delete(application, raw) }
     if(job.sidecarPending != null) dao.sidecarPendingOwned(job.id, job.generation, DownloadRecovery.retainedSidecarPending(job) { raw -> StorageUris.delete(application, raw) })
     // Sidecar yatim: media tak pernah terbit (outputUri null) tapi berkas pendamping sudah tercatat —
     // finalisasi terputus; buang bersama jejaknya supaya tak menumpuk tanpa pasangan (BUG-008/R2).
     if(job.outputUri == null && job.sidecarUris != null) dao.sidecarsOwned(job.id, job.generation, DownloadRecovery.retainedOrphanSidecars(job) { raw -> StorageUris.delete(application, raw) })
     // Jeda/Gagal menyimpan berkas parsial — jangan dihapus supaya "Lanjutkan"/"Coba lagi" bisa
     // lanjut dari progres terakhir (BUG-004/P1 resume). Hanya batal yang dibersihkan.
     if(job.state == JobState.CANCELLED) deleteJobDir(job.id)
     if(job.state in setOf(JobState.RUNNING, JobState.QUEUED)) {
      if(infos.any { it.state == WorkInfo.State.CANCELLED }) dao.cancel(job.id) else dao.fail(job.id,"Unduhan terhenti. Ketuk Coba lagi untuk melanjutkan dari progres terakhir.")
      if(!pendingGone && job.pendingUri != null) dao.retainPending(job.id, job.generation, job.pendingUri)
     } else if(pendingGone) dao.clearPendingOwned(job.id, job.generation)
    }
   }
 }
 /**
  * Berkas sementara sebuah job di noBackupFilesDir (BUG-004). Lease eksekusi menentukan pemilik:
  * worker yang sedang berjalan (termasuk saat baru dibatalkan) memegang lease dan membersihkan
  * direktorinya sendiri di finally, jadi penghapusan dari sini dilewati. WorkManager berstatus
  * ENQUEUED belum tentu berjalan, sehingga status WorkInfo bukan penentu — lease dan engine yang dipakai.
  */
 private suspend fun deleteJobDir(id: String): Boolean {
  val lease = DownloadFileAccess.lock(id)
  if (!lease.tryLock()) return false
  try {
   if (Engine.isRunning(id)) return false
   val app=getApplication<Application>()
   java.io.File(app.noBackupFilesDir, "downloads/$id").deleteRecursively()
   java.io.File(app.noBackupFilesDir, "covers").listFiles()?.filter { it.name == "$id.jpg" || it.name.startsWith("$id-") }?.forEach { it.delete() }
   return true
  } finally { lease.unlock() }
 }
 /** Masih ada pekerjaan WorkManager aktif untuk job ini? */
 private fun hasActiveWork(id: String): Boolean = runCatching { work.getWorkInfosByTag(id).get().any { !it.state.isFinished } }.getOrDefault(false)
 fun clearError() { _error.value=null }
 fun clearNotice() { _notice.value=null }
 /** Exception tanpa pesan (mis. `require` tanpa lambda) tidak boleh hilang sebagai snackbar kosong. */
 private fun userMessage(error: Throwable, fallback: String = "Tindakan belum dapat diselesaikan. Coba lagi."): String =
  error.message?.takeIf { it.isNotBlank() } ?: fallback
  fun inspect(url: String) {
   inspectionId?.let(Engine::cancel); inspection?.cancel()
   lastInspectPlaylist=false
   val id=UUID.randomUUID().toString(); inspectionState.begin(id); _error.value=null
   inspection=viewModelScope.launch {
    try {
     val cached=withContext(Dispatchers.IO) { runCatching { inspectionCache.read(url) }.getOrNull() }
     if(cached != null) {
      inspectionState.video(id,cached)
      return@launch
     }
     val result=Engine.inspect(getApplication(),url,id) { phase -> inspectionState.phase(id,phase) }
     withContext(Dispatchers.IO) { runCatching { inspectionCache.write(url,result) } }
     inspectionState.video(id,result)
    }
    catch(cancelled: CancellationException) { throw cancelled }
    catch(error: Exception) {
     val details=withContext(Dispatchers.IO) { FailureDetailsStore(getApplication()).capture(error, "INSPECTION") }
     val message=inspectErrorMessage(url,details)
     if (inspectionState.fail(id,message,details)) _error.value=message
    }
    finally { inspectionState.finish(id) }
   }
  }
 fun inspectPlaylist(url: String) {
  inspectionId?.let(Engine::cancel); inspection?.cancel()
  lastInspectPlaylist=true
  val id=UUID.randomUUID().toString()
  // Playlist biasa dinormalkan; Mix mempertahankan video awal (BUG-007/018).
  // identitas metadata tetap URL asli agar cocok dengan input pengguna di layar.
  val original=url.trim()
  val canonical=BstationUrls.canonicalSeason(original) ?: Selection.playlistInspectionUrl(original) ?: original
  val cachedSeed=inspectionState.state.value.metadata?.takeIf { Selection.videoId(it.url) == Selection.videoId(canonical) }
  inspectionState.begin(id); _error.value=null
  inspection=viewModelScope.launch {
   try {
    val result=Engine.inspectPlaylist(getApplication(),canonical,id) { phase -> inspectionState.phase(id,phase) }.copy(url=original)
    val seed=if (Selection.isMix(original)) cachedSeed ?: try {
     Engine.inspect(getApplication(), "https://www.youtube.com/watch?v=${Selection.videoId(canonical)}", id)
    } catch(cancelled: CancellationException) { throw cancelled }
    catch(_: Exception) { null } else null
    // Season memakai folder yang sudah dihitung dari judul; jangan ditimpa policy artis/musik.
    val folder=result.folderName.ifBlank {
     if (BstationUrls.isSeasonOnly(original)) Selection.seriesFolder(result.title, BstationUrls.seasonId(original).orEmpty())
     else Selection.collectionFolder(result, seed)
    }
    inspectionState.playlist(id,result.copy(folderName=folder, seedMetadata=seed))
   }
   catch(cancelled: CancellationException) { throw cancelled }
   catch(error: Exception) {
    val details=withContext(Dispatchers.IO) { FailureDetailsStore(getApplication()).capture(error, "PLAYLIST_INSPECTION") }
    val message=inspectErrorMessage(url,details)
    if (inspectionState.fail(id,message,details)) _error.value=message
   }
   finally { inspectionState.finish(id) }
  }
 }
  fun clearPlaylist() {
   inspectionState.clear()?.let(Engine::cancel)
   inspection?.cancel(); inspection=null; _error.value=null
  }
  fun cancelInspection() {
   inspectionState.clear()?.let(Engine::cancel)
   inspection?.cancel(); inspection=null; _error.value=null
  }
 /** Facebook sering menolak inspeksi (privat, butuh login, atau tautan kedaluwarsa) — pesan bawaan mesin tidak menjelaskan itu bagi pengguna. */
 private fun inspectErrorMessage(url: String, details: FailureDetails): String {
  if (details.category == "empty") return details.userMessage
  // Jangan tuduh tautan privat saat masalahnya DNS/jaringan/penyimpanan — pakai pesan kategori itu.
  if(Selection.platform(url) != "Facebook" || details.category in setOf("dns","network","rate_limit","storage","permission")) return details.userMessage
  return "Tautan Facebook tidak dapat dibaca — video mungkin privat, butuh login, atau tautannya sudah kedaluwarsa. Coba salin tautan video publik langsung dari aplikasi Facebook."
 }
 /** Coba lagi memakai jenis inspeksi terakhir: watch+list yang gagal sebagai playlist harus mengulang
  * playlist, bukan inspeksi video (BUG-006/R6). */
 fun retryInspect(url: String) { if(lastInspectPlaylist) inspectPlaylist(url) else inspect(url) }
 /** Freeze one collection folder before enqueueing; song identities remain independent. */ fun queuePlaylist(playlist: PlaylistMetadata, entries: List<PlaylistEntry>, options: DownloadOptions, onQueued: ((String) -> Unit)? = null) {
  if(submitting) { _error.value="Masih memproses permintaan sebelumnya. Coba lagi sebentar."; return }
  submitting=true
  viewModelScope.launch {
  try {
   val season = BstationUrls.isSeasonOnly(playlist.url)
   // Satu judul yang dipilih dari playlist bukan koleksi: jangan buat folder playlist khusus.
   val single = entries.size == 1
   val folder = Selection.safeFolder(playlist.folderName)
     ?: if (season) Selection.seriesFolder(playlist.title, BstationUrls.seasonId(playlist.url).orEmpty())
        else Selection.collectionFolder(playlist)
   val credits=if (options.mode == MediaMode.AUDIO && !season) CollectionCredit.learn(playlist) else null
   // Episode Premium hanya boleh diantrekan setelah pengguna masuk. Pemeriksaan di sini menutup
   // jalur selain pemilih (mis. state UI lama) sehingga antrean tidak pernah membuang episode diam-diam.
   val premiumAllowed = !season || BstationAuth.isSignedIn(getApplication())
   var queued=0; var skipped=0; var premiumSkipped=0
   var firstQueued: String? = null
   entries.forEach { entry ->
    if (season && BstationSeason.premiumLocked(entry, premiumAllowed)) { premiumSkipped++; return@forEach }
    try {
     val meta = VideoMetadata(entry.title, entry.uploader, entry.durationSeconds, entry.thumbnail, entry.url,
       artist = entry.artist, track = entry.track, creator = entry.uploader,
       platformId = SourcePlatforms.fromUrl(entry.url).id, sourceId = entry.id)
     submit(options.copy(url=entry.url), durationSeconds=entry.durationSeconds.takeIf { it > 0 }, subfolder=if (single) null else folder, entryMetadata=credits?.apply(meta) ?: meta, autoFolder=if (season || single) "" else "collection", onQueued = { id ->
      if (firstQueued == null) { firstQueued = id; onQueued?.invoke(id) }
     })
     queued++
    } catch(cancelled: CancellationException) { throw cancelled }
    catch(_: Exception) { skipped++ }
   }
   _notice.value=buildString {
    append(if (season) "Season: $queued episode diantrekan" else "Playlist: $queued video diantrekan")
    if (single) append("; satu judul disimpan seperti unduhan tunggal tanpa folder playlist.")
    else append("; semua file masuk folder $folder.")
    if (skipped > 0) append(" $skipped dilewati.")
    if (premiumSkipped > 0) append(" $premiumSkipped Premium terkunci — masuk ke Bstation dulu.")
   }
   } catch(cancelled: CancellationException) { throw cancelled }
   catch(error: Exception) { _error.value=userMessage(error) }
   finally { submitting=false }
 } }
 @Volatile private var submitting=false
 fun enqueue(options: DownloadOptions, onQueued: ((String) -> Unit)? = null) {
   if(submitting) {
     _error.value="Masih memproses permintaan sebelumnya. Coba lagi sebentar."
     return
   }
   submitting=true
   viewModelScope.launch {
     try { submit(options, onQueued = onQueued) }
     catch(cancelled: CancellationException) { throw cancelled }
     catch(error: Exception) { _error.value=userMessage(error, "Unduhan belum dapat diantrekan. Coba lagi.") }
     finally { submitting=false }
   }
 }
 /** Resubmits every failed entry in one pass (the submitting guard only spans this coroutine). */
 fun retryFailed() { if(submitting) return; submitting=true; viewModelScope.launch {
   try {
    recovery.join()
    val failed=dao.observe().first().filter { it.state == JobState.FAILED }
    var queued=0; var skipped=0
    failed.forEach { job ->
     // Kegagalan satu job tidak membatalkan sisa batch — laporan parsial tetap disampaikan.
     try { submit(job.toOptions(), keepId = job.id); queued++ }
     catch(cancelled: CancellationException) { throw cancelled }
     catch(_: Exception) { skipped++ }
    }
    _notice.value = when {
     queued == 0 && skipped == 0 -> "Tidak ada unduhan gagal."
     queued == 0 -> "$skipped unduhan tidak dapat diantrekan ulang."
     else -> "$queued unduhan diantrekan ulang." + if(skipped > 0) " $skipped dilewati." else ""
    }
   } catch(cancelled: CancellationException) { throw cancelled }
   catch(error: Exception) { _error.value=userMessage(error) }
   finally { submitting=false }
 } }
 private suspend fun submit(options: DownloadOptions, keepId: String? = null, titleHint: String? = null, durationSeconds: Long? = null, subfolder: String? = null, entryMetadata: VideoMetadata? = null, autoFolder: String = "", onQueued: ((String) -> Unit)? = null) {
   recovery.join()
   check(!_clearing.value) { "Tunggu pembersihan folder selesai sebelum mengunduh." }
   Selection.validate(options)
   val url=options.url.trim()
   // Cek duplikat memakai SELURUH opsi keluaran (BUG-005): bitrate/lirik/sematkan ikut dibandingkan.
   val duplicate=dao.observe().first().any { it.id != keepId && it.matchesOutput(url, options) && it.state in setOf(JobState.QUEUED,JobState.RUNNING,JobState.PAUSED) }
   require(!duplicate) { "Unduhan ini sudah ada di antrean." }
   val id=keepId ?: UUID.randomUUID().toString()
   val previous=keepId?.let { dao.get(it) }
   val done=dao.observe().first().firstOrNull { it.id != keepId && it.matchesOutput(url, options) && it.state == JobState.COMPLETED && outputStillExists(it) }
   require(done == null) { "File ini sudah pernah diunduh." }
   val meta=entryMetadata ?: inspectionState.state.value.metadata?.takeIf { it.url==url }
   val base = previous ?: DownloadJob(id,url,titleHint ?: "Video",options.mode,options.format,options.quality,options.bitrate,
       subtitles=options.subtitles, embedSubs=options.embedSubs, thumbnail=options.thumbnail, embedThumb=options.embedThumb,
       durationSeconds=durationSeconds ?: 0L, subfolder=subfolder.orEmpty(), autoFolder=autoFolder,
       platformId=SourcePlatforms.fromUrl(url).id, rawTitle=titleHint.orEmpty(),
       autoRename=SettingsStore.autoRename, autoTag=SettingsStore.autoTag, autoLibrary=options.mode != MediaMode.AUDIO || SettingsStore.autoLibrary)
   val prepared = meta?.let { base.withMetadata(it) } ?: base
   if(keepId != null) {
    awaitIdle(id)
    val revived=dao.revive(id,prepared.title,prepared.artist,options.mode,options.format,options.quality,options.bitrate,options.subtitles,options.embedSubs,options.thumbnail,options.embedThumb,System.currentTimeMillis())
    if(revived == 0) return
    cancelResultNotification(id)
   } else dao.put(prepared)
   viewModelScope.launch {
    val current = dao.get(id) ?: return@launch
    CoverCache.fetch(getApplication(), current)?.let { if (dao.coverPath(id, current.generation, it) == 0) CoverCache.deleteIfUnused(getApplication(), id, it) }
   }
   enqueueWork(id)
   onQueued?.invoke(id)
 }
 /** Menaruh pekerjaan WorkManager untuk sebuah job (dipakai submit baru maupun melanjutkan yang dijeda). */
 private suspend fun enqueueWork(id: String, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP, failMessage: String = "Tidak dapat memasukkan unduhan ke antrean.") {
  val request=OneTimeWorkRequestBuilder<DownloadWorker>().setInputData(workDataOf("jobId" to id, "generation" to (dao.get(id)?.generation ?: -1))).setConstraints(DownloadPolicy.constraints(SettingsStore.downloadWifiOnly, SettingsStore.downloadChargingOnly)).addTag(id).build()
  try { withContext(Dispatchers.IO) { work.enqueueUniqueWork("download-$id",policy,request).result.get() } }
  catch(cancelled: CancellationException) { throw cancelled }
  catch(error: Exception) { dao.fail(id,failMessage); throw error }
 }
 /**
  * Ganti jadwal job QUEUED dengan syarat terbaru. Generasi dinaikkan lebih dulu supaya worker lama
  * yang mungkin baru mulai tidak menandai baris baru sebagai CANCELLED saat `REPLACE` membatalkannya.
  * Mengembalikan false bila baris sudah bukan QUEUED lagi.
  */
 private suspend fun replaceQueuedWork(id: String): Boolean {
  if (dao.bumpQueuedGeneration(id) == 0) return false
  enqueueWork(id, ExistingWorkPolicy.REPLACE, "Antrean tidak dapat dijadwalkan ulang.")
  return true
 }
 /**
  * Pekerjaan WorkManager yang masih hidup untuk job ini. `Result.failure` berarti status belum
  * dapat dibaca; pemanggil harus menganggapnya hidup agar pekerjaan berjalan tidak diganggu.
  */
 private fun liveWork(id: String): Result<WorkInfo?> =
  runCatching { work.getWorkInfosForUniqueWork("download-$id").get().firstOrNull { !it.state.isFinished } }
 /**
  * Pemeriksaan berkala antrean: hanya job QUEUED yang diperiksa karena status lain memang tidak
  * punya pekerjaan aktif (dijeda/dibatalkan/gagal/selesai) atau sedang berjalan. Tanpa penjaga ini,
  * job yang jadwalnya hilang tetap "Menunggu giliran" selamanya walau tidak ada yang berjalan.
  */
 private suspend fun watchQueue() {
  while (true) {
   val all = try { dao.observe().first() }
   catch (cancelled: CancellationException) { throw cancelled }
   catch (_: Exception) { emptyList() }
   val waiting = all.filter { it.state == JobState.QUEUED }
   // Job yang sudah keluar dari QUEUED tidak lagi dihitung agar percobaan berikutnya tetap penuh.
   val ids = waiting.mapTo(mutableSetOf()) { it.id }
   rescheduleAttempts.keys.retainAll(ids)
   queueWaitingSince.keys.retainAll(ids)
   // Jangan menyentuh jadwal QUEUED saat unduhan lain masih berjalan: WorkManager memang boleh
   // menahan job berikutnya sampai kapasitas tersedia.
   val otherRunning = all.any { it.state == JobState.RUNNING }
   if (waiting.isNotEmpty()) withContext(Dispatchers.IO) { waiting.forEach { rescheduleStalled(it, otherRunning) } }
   delay(if (waiting.isEmpty()) 15_000 else 4_000)
  }
 }
 /** Batas percobaan penjadwalan ulang per job mencegah loop 4 detik untuk job yang memang tak bisa jalan. */
 private suspend fun rescheduleStalled(job: DownloadJob, otherWorkRunning: Boolean) {
  val attempts = rescheduleAttempts[job.id] ?: 0
  if (attempts >= MAX_RESCHEDULES) return
  val workInfo = liveWork(job.id).getOrElse { return }
  val engine = Engine.isRunning(job.id)
  val now = SystemClock.elapsedRealtime()
  val since = queueWaitingSince.getOrPut(job.id) { now }
  val waited = (now - since).coerceAtLeast(0)
  val work = when (workInfo?.state) {
   WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> QueueWork.WAITING
   WorkInfo.State.RUNNING -> QueueWork.RUNNING
   else -> QueueWork.NONE
  }
  val stale = QueueWatchdog.stalled(job.state, workInfo != null, engine)
  val replace = QueueWatchdog.replaceWaiting(
   state = job.state,
   work = work,
   engineRunning = engine,
   constraintsMet = DownloadPolicy.conditionsMet(
    getApplication(), SettingsStore.downloadWifiOnly, SettingsStore.downloadChargingOnly,
   ),
   otherWorkRunning = otherWorkRunning,
   waitingMillis = waited,
  )
  if (!stale && !replace) return
  try {
   val requeued = if (replace) replaceQueuedWork(job.id) else {
    enqueueWork(job.id, ExistingWorkPolicy.KEEP, "Antrean tidak dapat dijadwalkan ulang.")
    true
   }
   if (!requeued) return
   rescheduleAttempts[job.id] = attempts + 1
   queueWaitingSince[job.id] = now
   _notice.value = if (replace) "Antrean yang tertahan dijadwalkan ulang." else "Antrean dilanjutkan otomatis."
  } catch(cancelled: CancellationException) { throw cancelled }
  catch(_: Exception) { /* Penyebab sudah dicatat enqueueWork sebagai status gagal. */ }
 }
 /** Memasang ulang seluruh job QUEUED setelah syarat WiFi/daya berubah. */
 private suspend fun rescheduleQueued(message: String) {
  val waiting = runCatching { dao.observe().first().filter { it.state == JobState.QUEUED } }.getOrDefault(emptyList())
  if (waiting.isEmpty()) return
  withContext(Dispatchers.IO) {
   val now = SystemClock.elapsedRealtime()
   waiting.forEach { job ->
    if (Engine.isRunning(job.id)) return@forEach
    val workInfo = liveWork(job.id).getOrElse { return@forEach }
    if (workInfo?.state == WorkInfo.State.RUNNING) return@forEach
    runCatching {
     // Beri masa tenggang baru: penggantian ini sudah memakai syarat terbaru, jadi penjaga
     // antrean tidak boleh langsung menggantinya lagi hanya karena timer lama masih berjalan.
     if (replaceQueuedWork(job.id)) queueWaitingSince[job.id] = now
    }
   }
  }
  val ids = waiting.mapTo(mutableSetOf()) { it.id }
  rescheduleAttempts.keys.retainAll(ids)
  queueWaitingSince.keys.retainAll(ids)
  _notice.value = message
 }
 private companion object { const val MAX_RESCHEDULES = 3 }
 /** True selama uri hasil unduhan masih bisa dibuka; row basi (file dihapus manual) dianggap tidak ada. */
 private fun outputStillExists(job: DownloadJob): Boolean {
  val raw=job.outputUri ?: return false
  return runCatching { getApplication<Application>().contentResolver.openFileDescriptor(raw.toUri(),"r")?.use { true } == true }.getOrDefault(false)
 }
 fun cancel(id: String) { viewModelScope.launch { cancelOne(id); _notice.value="Unduhan dibatalkan." } }
 private suspend fun cancelOne(id: String) {
  cancelResultNotification(id)
  val cancelled=dao.cancel(id)
  Engine.cancel(id); work.cancelAllWorkByTag(id)
  // Job tanpa pekerjaan (mis. dijeda/diantre) tak punya yang membersihkan berkas parsialnya; job berjalan
  // dibersihkan oleh finally milik worker setelah engine berhenti (BUG-004). Lease di deleteJobDir mencegah
  // penghapusan direktori milik worker yang masih berjalan, termasuk saat pekerjaan baru dibatalkan.
  if(cancelled > 0) deleteJobDir(id)
 }
 fun cancelAll() { viewModelScope.launch {
  val ids=dao.observe().first().filter { it.state in setOf(JobState.QUEUED,JobState.RUNNING,JobState.PAUSED) }.map { it.id }
  if(ids.isEmpty()) { _notice.value="Tidak ada unduhan yang berjalan."; return@launch }
  ids.forEach { cancelOne(it) }
  _notice.value="${ids.size} unduhan dibatalkan."
 } }
 /** Menunggu pekerjaan lama untuk job ini benar-benar berhenti — mencegah pekerjaan lama menimpa status lanjutan. */
 private suspend fun awaitIdle(id: String) { withContext(Dispatchers.IO) {
  repeat(100) {
   val infos=runCatching { work.getWorkInfosByTag(id).get() }.getOrDefault(emptyList())
   if(infos.all { it.state.isFinished } && !Engine.isRunning(id)) { return@withContext }
   delay(100)
  }
  error("Unduhan sebelumnya masih berhenti. Coba lagi sebentar.")
 } }
 /** Menjeda satu unduhan: status dicatat lebih dulu lalu pekerjaan dihentikan; berkas parsial disimpan. */
 fun pause(id: String) { viewModelScope.launch {
  try { if(pauseOne(id)) _notice.value="Unduhan dijeda." }
  catch(cancelled: CancellationException) { throw cancelled }
  catch(error: Exception) { _error.value=userMessage(error) }
 } }
 private suspend fun pauseOne(id: String): Boolean {
  if(dao.pause(id) == 0) return false
  Engine.cancel(id); work.cancelAllWorkByTag(id)
  return true
 }
 fun pauseAll() { viewModelScope.launch {
  try {
   val ids=dao.observe().first().filter { it.state == JobState.RUNNING || it.state == JobState.QUEUED }.map { it.id }
   if(ids.isEmpty()) { _notice.value="Tidak ada unduhan yang berjalan."; return@launch }
   dao.pauseAll()
   ids.forEach { Engine.cancel(it); work.cancelAllWorkByTag(it) }
   _notice.value="${ids.size} unduhan dijeda."
  } catch(cancelled: CancellationException) { throw cancelled }
  catch(error: Exception) { _error.value=userMessage(error) }
 } }
 /** Melanjutkan unduhan yang dijeda; unduhan parsial dilanjutkan, bukan diulang. */
 fun resume(id: String) { viewModelScope.launch {
  try {
   awaitIdle(id)
   if(dao.resume(id) == 0) return@launch
   enqueueWork(id, ExistingWorkPolicy.REPLACE, "Tidak dapat melanjutkan unduhan.")
   _notice.value="Unduhan dilanjutkan."
  } catch(cancelled: CancellationException) { throw cancelled }
  catch(error: Exception) { _error.value=userMessage(error) }
 } }
 fun resumeAll() { viewModelScope.launch {
  try {
   val ids=dao.observe().first().filter { it.state == JobState.PAUSED }.map { it.id }
   if(ids.isEmpty()) { _notice.value="Tidak ada unduhan yang dijeda."; return@launch }
   var done=0
   ids.forEach { id -> awaitIdle(id); if(dao.resume(id) > 0) { enqueueWork(id, ExistingWorkPolicy.REPLACE, "Tidak dapat melanjutkan unduhan."); done++ } }
   _notice.value="${done} unduhan dilanjutkan."
  } catch(cancelled: CancellationException) { throw cancelled }
  catch(error: Exception) { _error.value=userMessage(error) }
 } }
 private fun DownloadJob.toOptions() = DownloadOptions(url,mode,format,quality,bitrate,subtitles,embedSubs,thumbnail,embedThumb)
 /**
  * "Unduh lagi" pada hasil SELESAI = ganti hasil lama: media + sidecar lama dihapus lebih dulu, lalu baris
  * yang SAMA diantrekan ulang (tanpa baris duplikat). Dulu jalur ini memanggil `enqueue` sehingga guard
  * duplikat (BUG-019) menemukan barisnya sendiri dan tombol selalu gagal selama file masih ada (BUG-026).
  * Untuk gagal/dibatalkan tetap revive seperti sebelumnya.
  */
 fun retry(job: DownloadJob) {
  if(job.state != JobState.COMPLETED) { enqueueRevive(job); return }
  if(submitting) return
  submitting=true
  viewModelScope.launch(Dispatchers.IO) {
   try {
    val app=getApplication<Application>()
    var failed=0
    MediaFileOperations.mutex.withLock {
    // StorageUris.delete jujur: true = sudah hilang/terbukti terhapus, false = provider menolak sambil file masih ada.
    job.outputUri?.let { raw -> if(!StorageUris.delete(app, raw)) failed++ }
    job.sidecarUris?.lines()?.forEach { line ->
     val raw=line.substringAfter('|',"")
     if(raw.isNotBlank() && !StorageUris.delete(app, raw.toUri())) failed++
    }
    }
    job.outputUri?.let { MediaRepository(app).refresh(it) }
    if(failed>0) { _notice.value="File lama tidak dapat dihapus ($failed file) — unduhan ulang dibatalkan."; return@launch }
    if(dao.requeueCompleted(job.id, System.currentTimeMillis()) == 0) { _notice.value="Entri sudah tidak berstatus selesai."; return@launch }
    cancelResultNotification(job.id)
    enqueueWork(job.id, ExistingWorkPolicy.KEEP, "Tidak dapat mengunduh ulang.")
    _notice.value="Mengunduh ulang…"
   } catch(cancelled: CancellationException) { throw cancelled }
   catch(error: Exception) { _error.value=userMessage(error) }
   finally { submitting=false }
  }
 }
 private fun enqueueRevive(job: DownloadJob) { if(submitting) return; submitting=true; viewModelScope.launch {
   try { submit(job.toOptions(), keepId = job.id) }
   catch(cancelled: CancellationException) { throw cancelled }
   catch(error: Exception) { _error.value=userMessage(error) }
   finally { submitting=false }
 } }
 fun remove(id: String) { viewModelScope.launch { MediaFileOperations.mutex.withLock {
  cancelResultNotification(id)
  if(dao.remove(id) > 0) deleteJobDir(id)
 } } }
 /** Removes the entry together with its stored file, sidecar files and result notification. */
 fun removeFile(id: String) { viewModelScope.launch(Dispatchers.IO) {
  cancelResultNotification(id)
  val job=dao.get(id)
  var failed=0
  if(job != null) {
   val app=getApplication<Application>()
   MediaFileOperations.mutex.withLock {
   job.outputUri?.let { raw -> if(!StorageUris.delete(app, raw)) failed++ }
   job.sidecarUris?.lines()?.forEach { line ->
    val raw=line.substringAfter('|',"")
    if(raw.isNotBlank() && !StorageUris.delete(app, raw)) failed++
   }
  }
  }
  job?.outputUri?.let { MediaRepository(getApplication()).refresh(it) }
  val removed=MediaFileOperations.mutex.withLock { dao.remove(id) }
  // Jangan melapor sukses bila file ternyata tidak terhapus (izin/provider menolak) — BUG-003.
  _notice.value=when {
   removed == 0 -> "Entri sudah tidak ada."
   failed > 0 -> "Entri dihapus, tetapi $failed file gagal dihapus dari penyimpanan."
   else -> "File dan entri riwayat dihapus."
  }
 } }
 /** Clear the selected root's contents only; keep the root and download history. */
 fun clearStorageFolder() { if(_clearing.value) return; _clearing.value=true; viewModelScope.launch(Dispatchers.IO) {
  try {
   val app=getApplication<Application>()
   // Snapshot the confirmed destination; a later settings change must not widen the deletion scope.
   val folder=SettingsStore.storageFolder
   val selectedTree=SettingsStore.storageTree?.toUri()
   val legacy=selectedTree == null && StorageFolderAccess.hasLegacyWriteAccess(app)
   val root=selectedTree ?: StorageFolderAccess.defaultTree(app,folder,requireWrite=true)
   check(legacy || root != null) {
    "Akses folder diperlukan. Pilih Download/$folder agar file dan subfolder dapat dibersihkan."
   }
   val result=MediaFileOperations.mutex.withLock {
    val rows=dao.observe().first()
    check(rows.none { it.state in setOf(JobState.QUEUED,JobState.RUNNING,JobState.PAUSED) } && !submitting) {
     "Masih ada unduhan berjalan atau dijeda — batalkan dulu sebelum menghapus semua file."
    }
    val swept=if(legacy) clearLegacyFolder(folder) else clearTree(requireNotNull(root))
    rows.forEach { deleteJobDir(it.id); cancelResultNotification(it.id) }
    swept
   }
   MediaRepository(app).refreshAllAvailability()
   _notice.value=if(result.clean) {
    "Folder bersih — ${result.files} file dan ${result.folders} subfolder dihapus. Folder utama tetap tersedia."
   } else {
    "${result.files} file dan ${result.folders} subfolder dihapus; pembersihan belum lengkap (${result.failures} kegagalan). Periksa akses folder lalu coba lagi."
   }
  } catch(cancelled: CancellationException) { throw cancelled }
  catch(error: Exception) { _error.value=userMessage(error, "Pembersihan folder gagal.") }
  finally { _clearing.value=false; refreshStorage() }
 } }
 @Suppress("DEPRECATION")
 private fun clearLegacyFolder(folder: String): FolderCleanup.Result {
  val app=getApplication<Application>()
  check(StorageFolderAccess.hasLegacyWriteAccess(app)) { "Izin hapus berubah; coba lagi." }
  require(Selection.safeFolder(folder) == folder) { "Nama folder tidak valid." }
  val downloads=android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).canonicalFile
  val root=java.io.File(downloads,folder)
  require(root.canonicalFile == root.absoluteFile && root.parentFile == downloads) { "Folder tujuan tidak valid." }
  return LegacyFolderCleanup.clear(root) { file ->
   // Delete indexed files through MediaStore as well, so scans do not resurrect stale rows.
   runCatching { app.contentResolver.delete(MediaStore.Files.getContentUri("external"),
    "${MediaStore.MediaColumns.DATA} = ?",arrayOf(file.path)) }
   if(java.nio.file.Files.notExists(file.toPath())) true else {
    val deleted=file.delete()
    if(deleted) android.media.MediaScannerConnection.scanFile(app,arrayOf(file.path),null,null)
    deleted
   }
  }
 }
 /** Use child document IDs at every level; never delete the granted root. */
 private fun clearTree(root: android.net.Uri): FolderCleanup.Result {
  val app=getApplication<Application>()
  val resolver=app.contentResolver
  return FolderCleanup.clear(android.provider.DocumentsContract.getTreeDocumentId(root), list={ id ->
   val children=android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(root, id)
   val entries=mutableListOf<FolderCleanup.Entry>()
   val cursor=resolver.query(children, arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID, android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)
    ?: error("Folder belum dapat dibaca.")
   cursor.use { c ->
    while(c.moveToNext()) {
     require(entries.size < 4000) { "Folder terlalu besar untuk dibersihkan sekaligus." }
     entries += FolderCleanup.Entry(c.getString(0) ?: error("Identitas dokumen tidak tersedia."),
      (c.getString(1) ?: error("Jenis dokumen tidak tersedia.")) == android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
    }
   }
   entries
  }, delete={ id -> StorageUris.delete(app, android.provider.DocumentsContract.buildDocumentUriUsingTree(root, id)) })
 }
 /** Matches the tag used by DownloadWorker.notifyResult so stale result notifications disappear with the entry. */
 private fun cancelResultNotification(id: String) { runCatching {
  getApplication<Application>().getSystemService(android.app.NotificationManager::class.java)
   .cancel(id.hashCode().let { if(it == 0) 1 else it })
 } }
/** Job yang sedang mencari ulang lirik — cegah dua permintaan bersamaan untuk audio yang sama (BUG-014). */
 private val lyricRetries = java.util.Collections.synchronizedSet(mutableSetOf<String>())
 /**
  * Cari ulang lirik TANPA mengunduh ulang audio (BUG-014 / OPT-002 ringkas): lirik lama tetap dipakai
  * sampai pengganti berhasil ditulis, dan sidecar baru selalu ditulis di folder yang sama dengan audio.
  */
 fun refetchLyrics(id: String) { viewModelScope.launch(Dispatchers.IO) {
  if(!lyricRetries.add(id)) { _notice.value="Masih mencari lirik untuk audio ini."; return@launch }
  try {
   val job=dao.get(id)
   if(job == null || job.state != JobState.COMPLETED || job.mode != MediaMode.AUDIO || job.subtitles.isEmpty()) {
    _notice.value="Hanya audio yang sudah selesai dan punya lirik yang bisa dicari ulang."; return@launch
   }
   if(job.outputUri.isNullOrBlank() || !outputStillExists(job)) { _notice.value="File audio tidak ditemukan di penyimpanan."; return@launch }
   val editor = LyricEditor(getApplication())
   val snapshot = MediaFileOperations.mutex.withLock {
    val outputName = displayNameOf(requireNotNull(job.outputUri).toUri()) ?: error("Nama audio tidak dapat dibaca.")
    val old = job.sidecarUris?.lines()?.filter { it.startsWith("sub|") }
     ?.map { it.substringAfter('|') }?.filter {
      val name = displayNameOf(it.toUri()) ?: error("File pendamping tidak dapat diperiksa; lirik tidak diubah.")
      name.endsWith(".lrc", true)
     }.orEmpty()
    val media=DownloadDatabase.get(getApplication()).media().let { it.getByAccess(requireNotNull(job.outputUri)) ?: it.get(requireNotNull(job.outputUri)) }
    Triple(outputName,old.associateWith(editor::captureForRefetch),media?.durationMs?.takeIf { it > 0 }?.let { (it+500)/1000 } ?: job.durationSeconds)
   }
   _notice.value="Mencari lirik di penyedia lirik…"
   Lyrics.initialize(java.io.File(getApplication<Application>().cacheDir,"lyrics-responses"),
    java.io.File(getApplication<Application>().noBackupFilesDir,"lyrics-cooldown.json"))
   val query=PublishedLyricIdentity.fromName(job,snapshot.first)
   val outcome=runInterruptible(Dispatchers.IO) {
    val resolved=Lyrics.resolveIdentity(query,snapshot.third,refreshMissing=true)
    Lyrics.fetch(resolved,snapshot.third,refreshMissing=true)
   }
   var saveError: String? = null
   val status = MediaFileOperations.mutex.withLock {
    val current = dao.get(id) ?: error("Entri telah dihapus; lirik tidak diubah.")
    require(current.state == JobState.COMPLETED && current.generation == job.generation &&
     current.outputUri == job.outputUri && current.sidecarUris == job.sidecarUris && outputStillExists(current) &&
     displayNameOf(requireNotNull(current.outputUri).toUri()) == snapshot.first) { "Audio berubah selama pencarian; coba lagi." }
    val result = when {
     outcome.status != LyricStatus.SAVED -> outcome.status
     else -> try {
      if (writeLyricBesideAudio(current, requireNotNull(outcome.text), snapshot.second)) LyricStatus.SAVED else LyricStatus.SAVE_FAILED
     } catch (cancelled: CancellationException) { throw cancelled }
     catch (failure: Exception) { saveError = failure.message; LyricStatus.SAVE_FAILED }
    }
    dao.lyricStatus(id, result)
    if (FailureDetails.isLyricFailure(result)) runCatching {
     FailureDetailsStore(getApplication()).let { it.put("$id-lyrics",job.generation,it.captureLyrics(result,outcome.httpCode)) }
    }
    result
   }
   MediaRepository(getApplication()).indexDownload(id)
   _notice.value=LyricStatus.label(status)+"." + saveError?.let { " $it" }.orEmpty()
  } catch(cancelled: CancellationException) { throw cancelled }
  catch(error: Exception) { _notice.value="Pencarian lirik gagal: "+(error.message ?: "kesalahan tak dikenal.") }
  finally { lyricRetries.remove(id) }
 } }
 /** Nama tampilan aktual sebuah URI penyimpanan; null bila tidak bisa dibaca. */
 private fun displayNameOf(uri: android.net.Uri): String? = runCatching {
  getApplication<Application>().contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if(c.moveToFirst()) c.getString(0) else null }
 }.getOrNull()
 /**
  * Menulis .lrc di samping audio: timpa sidecar lama bila masih ada, kalau tidak — buat dokumen baru di
  * folder yang SAMA dengan audio (folder tujuan tidak lagi bergantung pada pengaturan sekarang).
  * Nama mengikuti nama audio yang benar-benar tersimpan (BUG-010); kegagalan menulis selalu gagal total,
  * tidak meninggalkan dokumen parsial (BUG-008).
  */
 private suspend fun writeLyricBesideAudio(job: DownloadJob, text: String, expected: Map<String, ByteArray>): Boolean =
  LyricPublisher(getApplication()).publish(job, text, expected)

 /**
  * Retensi riwayat: hapus catatan selesai yang lebih lama dari [days]. File media TIDAK dihapus —
  * hanya baris riwayat dan notifikasi hasilnya. 0 berarti nonaktif.
  */
 fun pruneHistory(days: Int) {
  val cutoff = historyCutoff(days, System.currentTimeMillis()) ?: return
  viewModelScope.launch {
   val ids = dao.observe().first()
    .filter { it.state == JobState.COMPLETED && historyTimestamp(it.completedAt, it.createdAt) < cutoff }
    .map { it.id }
   if (ids.isEmpty()) return@launch
   val removed = dao.clearCompletedBefore(cutoff)
   if (removed > 0) {
    ids.forEach(::cancelResultNotification)
    // Retensi berjalan diam-diam saat aplikasi dibuka; beri tahu hasilnya agar tidak terasa seperti kehilangan data.
    _notice.value = "$removed entri riwayat lama dihapus otomatis. File media tetap tersimpan."
   }
  }
 }

 fun clearCompleted() { viewModelScope.launch {
  val ids=dao.observe().first().filter { it.state == JobState.COMPLETED }.map { it.id }
  val n=MediaFileOperations.mutex.withLock { dao.clearCompleted() }; ids.forEach(::cancelResultNotification)
  _notice.value=if(n>0) "Riwayat dibersihkan." else "Riwayat sudah kosong."
 } } fun clearUnsuccessful() { viewModelScope.launch {
  val ids=dao.observe().first().filter { it.state == JobState.FAILED || it.state == JobState.CANCELLED }.map { it.id }
  dao.clearUnsuccessful()
  // Baris yang selamat dari penghapusan (mis. baru saja dilanjutkan) tak kehilangan berkas parsialnya (R1).
  ids.forEach { cancelResultNotification(it); if(dao.get(it) == null) deleteJobDir(it) }
  _notice.value=if(ids.isNotEmpty()) "Entri gagal/dibatalkan dihapus." else "Tidak ada entri gagal."
 } }
 override fun onCleared() { inspectionId?.let(Engine::cancel) }
}

/** Batas waktu retensi riwayat; null berarti retensi nonaktif. */
internal fun historyCutoff(days: Int, now: Long): Long? =
    if (days <= 0) null else now - days.coerceIn(0, 365) * 86_400_000L

/** Waktu selesai efektif; baris lama tanpa completedAt memakai createdAt (BUG-023). */
internal fun historyTimestamp(completedAt: Long, createdAt: Long): Long =
    if (completedAt > 0) completedAt else createdAt
