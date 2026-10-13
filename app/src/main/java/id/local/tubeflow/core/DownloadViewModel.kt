package id.local.tubeflow.core

import android.app.Application
import android.content.ContentValues
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class DownloadViewModel(application: Application) : AndroidViewModel(application) {
 private val dao = DownloadDatabase.get(application).downloads()
 private val work = WorkManager.getInstance(application)
 val jobs = dao.observe().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 private val _metadata=MutableStateFlow<VideoMetadata?>(null)
 val metadata: StateFlow<VideoMetadata?> = _metadata.asStateFlow()
 private val _playlist=MutableStateFlow<PlaylistMetadata?>(null)
 val playlist: StateFlow<PlaylistMetadata?> = _playlist.asStateFlow()
 private val _busy=MutableStateFlow(false)
 val busy: StateFlow<Boolean> = _busy.asStateFlow()
 private val _error=MutableStateFlow<String?>(null)
 val error: StateFlow<String?> = _error.asStateFlow()
 private val _inspectError=MutableStateFlow<String?>(null)
 /** Alasan inspeksi terakhir gagal — menyalakan tombol "Coba lagi" untuk URL yang sama (BUG-006). */
 val inspectError: StateFlow<String?> = _inspectError.asStateFlow()
 private val _notice=MutableStateFlow<String?>(null)
 val notice: StateFlow<String?> = _notice.asStateFlow()
 private val _storageBytes=MutableStateFlow<Long?>(null)
 val storageBytes: StateFlow<Long?> = _storageBytes.asStateFlow()
 private val _storageError=MutableStateFlow(false)
 val storageError: StateFlow<Boolean> = _storageError.asStateFlow()
 private val _storageFolderAccess=MutableStateFlow(false)
 val storageFolderAccess: StateFlow<Boolean> = _storageFolderAccess.asStateFlow()
 private val _clearing=MutableStateFlow(false)
 /** True selama "Hapus semua file" berjalan — tombol di Pengaturan dinonaktifkan. */
 val clearing: StateFlow<Boolean> = _clearing.asStateFlow()
 private var storageRefresh: Job? = null
 init {
  refreshStorage()
  // Angka penyimpanan ikut naik/turun begitu ada unduhan selesai (atau file dihapus) tanpa perlu tombol Segarkan.
  viewModelScope.launch { jobs.map { list -> list.count { it.state == JobState.COMPLETED } }.distinctUntilChanged().collect { refreshStorage() } }
 }
 fun refreshStorage() {
  storageRefresh?.cancel()
  storageRefresh=viewModelScope.launch {
   _storageError.value=false
   _storageBytes.value=null
   val result=withContext(Dispatchers.IO) { runCatching { measureStorage() } }
   _storageFolderAccess.value=result.getOrNull()?.second == true
   _storageBytes.value=result.getOrNull()?.first
   _storageError.value=result.isFailure
  }
 }
 private fun measureStorage(): Pair<Long,Boolean> {
  var total=0L
  val tree=SettingsStore.storageTree
  if (tree != null) return measureTree(tree.toUri()) to true
  // Returning to the default output folder must not discard an existing read grant.
  defaultTreeGrant()?.let { return measureTree(it) to true }
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
  return total to false
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
 private fun defaultTreeGrant(requireWrite: Boolean = false): android.net.Uri? {
  val resolver=getApplication<Application>().contentResolver
  return resolver.persistedUriPermissions.firstOrNull { permission ->
   permission.isReadPermission && (!requireWrite || permission.isWritePermission) && runCatching {
    StorageFolderAccess.matchesDefault(permission.uri.authority,
     android.provider.DocumentsContract.getTreeDocumentId(permission.uri), SettingsStore.storageFolder)
   }.getOrDefault(false)
  }?.uri
 }
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
 private var inspection: Job?=null
 private var inspectionId: String?=null
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
     // URI pending/sidecar memakai helper bersama (BUG-003); sidecarPending membersihkan sisa publikasi (BUG-008).
     job.pendingUri?.let { raw -> StorageUris.delete(application, raw) }
     job.sidecarPending?.lines()?.filter { it.isNotBlank() }?.forEach { raw -> StorageUris.delete(application, raw) }
     if(job.sidecarPending != null) dao.sidecarPending(job.id, null)
     // Sidecar yatim: media tak pernah terbit (outputUri null) tapi berkas pendamping sudah tercatat —
     // finalisasi terputus; buang bersama jejaknya supaya tak menumpuk tanpa pasangan (BUG-008/R2).
     if(job.outputUri == null && job.sidecarUris != null) {
      job.sidecarUris.lines().mapNotNull { line -> line.substringAfter('|',"").takeIf { it.isNotBlank() } }.forEach { raw -> StorageUris.delete(application, raw) }
      dao.sidecars(job.id, null)
     }
     // Jeda/Gagal menyimpan berkas parsial — jangan dihapus supaya "Lanjutkan"/"Coba lagi" bisa
     // lanjut dari progres terakhir (BUG-004/P1 resume). Hanya batal yang dibersihkan.
     if(job.state == JobState.CANCELLED) deleteJobDir(job.id)
     if(job.state in setOf(JobState.RUNNING, JobState.QUEUED)) { if(infos.any { it.state == WorkInfo.State.CANCELLED }) dao.cancel(job.id) else dao.fail(job.id,"Unduhan terhenti. Ketuk Coba lagi untuk melanjutkan dari progres terakhir.") }
     dao.clearPending(job.id)
    }
   }
 }
 /** Berkas parsial sementara sebuah job di noBackupFilesDir (BUG-004). */
 private fun deleteJobDir(id: String) {
  val app=getApplication<Application>()
  java.io.File(app.noBackupFilesDir, "downloads/$id").deleteRecursively()
  java.io.File(app.noBackupFilesDir, "covers").listFiles()?.filter { it.name == "$id.jpg" || it.name.startsWith("$id-") }?.forEach { it.delete() }
 }
 /** Masih ada pekerjaan WorkManager aktif untuk job ini? */
 private fun hasActiveWork(id: String): Boolean = runCatching { work.getWorkInfosByTag(id).get().any { !it.state.isFinished } }.getOrDefault(false)
 fun clearError() { _error.value=null }
 fun clearNotice() { _notice.value=null }
 fun inspect(url: String) {
  inspectionId?.let(Engine::cancel); inspection?.cancel()
  lastInspectPlaylist=false
  val id=UUID.randomUUID().toString(); inspectionId=id
  inspection=viewModelScope.launch {
   _busy.value=true; _metadata.value=null; _playlist.value=null; _error.value=null; _inspectError.value=null
   try { _metadata.value=Engine.inspect(getApplication(),url,id) }
   catch(cancelled: CancellationException) { throw cancelled }
   catch(error: Exception) {
    val message=inspectErrorMessage(url, error.message ?: "Tidak dapat membaca video.")
    _error.value=message; _inspectError.value=message
   }
   finally { if(inspectionId==id) _busy.value=false }
  }
 }
 fun inspectPlaylist(url: String) {
  inspectionId?.let(Engine::cancel); inspection?.cancel()
  lastInspectPlaylist=true
  val id=UUID.randomUUID().toString(); inspectionId=id
  // Playlist biasa dinormalkan; Mix mempertahankan video awal (BUG-007/018).
  // identitas metadata tetap URL asli agar cocok dengan input pengguna di layar.
  val original=url.trim()
  val canonical=Selection.playlistInspectionUrl(original) ?: original
  val cachedSeed=_metadata.value?.takeIf { Selection.videoId(it.url) == Selection.videoId(canonical) }
  inspection=viewModelScope.launch {
   _busy.value=true; _metadata.value=null; _playlist.value=null; _error.value=null; _inspectError.value=null
   try {
    val result=Engine.inspectPlaylist(getApplication(),canonical,id).copy(url=original)
    val seed=if (Selection.isMix(original)) cachedSeed ?: try {
     Engine.inspect(getApplication(), "https://www.youtube.com/watch?v=${Selection.videoId(canonical)}", id)
    } catch(cancelled: CancellationException) { throw cancelled }
    catch(_: Exception) { null } else null
    _playlist.value=result.copy(folderName=Selection.collectionFolder(result, seed), seedMetadata=seed)
   }
   catch(cancelled: CancellationException) { throw cancelled }
   catch(error: Exception) {
    val message=inspectErrorMessage(url, error.message ?: "Tidak dapat membaca playlist.")
    _error.value=message; _inspectError.value=message
   }
   finally { if(inspectionId==id) _busy.value=false }
  }
 }
 fun clearPlaylist() { _playlist.value=null }
 /** Facebook sering menolak inspeksi (privat, butuh login, atau tautan kedaluwarsa) — pesan bawaan mesin tidak menjelaskan itu bagi pengguna. */
 private fun inspectErrorMessage(url: String, message: String): String {
  if(Selection.platform(url) != "Facebook") return message
  return "Tautan Facebook tidak dapat dibaca — video mungkin privat, butuh login, atau tautannya sudah kedaluwarsa. Coba salin tautan video publik langsung dari aplikasi Facebook."
 }
 /** Coba lagi memakai jenis inspeksi terakhir: watch+list yang gagal sebagai playlist harus mengulang
  * playlist, bukan inspeksi video (BUG-006/R6). */
 fun retryInspect(url: String) { if(lastInspectPlaylist) inspectPlaylist(url) else inspect(url) }
 /** Freeze one collection folder before enqueueing; song identities remain independent. */ fun queuePlaylist(playlist: PlaylistMetadata, entries: List<PlaylistEntry>, options: DownloadOptions) { if(submitting) return; submitting=true; viewModelScope.launch {
  try {
   val folder = Selection.safeFolder(playlist.folderName) ?: Selection.collectionFolder(playlist)
   val credits=CollectionCredit.learn(playlist)
   var queued=0; var skipped=0
   entries.forEach { entry ->
    try {
     val meta = VideoMetadata(entry.title, entry.uploader, entry.durationSeconds, entry.thumbnail, entry.url,
       artist = entry.artist, track = entry.track, creator = entry.uploader,
       platformId = SourcePlatforms.fromUrl(entry.url).id, sourceId = entry.id)
     submit(options.copy(url=entry.url), durationSeconds=entry.durationSeconds.takeIf { it > 0 }, subfolder=folder, entryMetadata=if (options.mode == MediaMode.AUDIO) credits.apply(meta) else meta, autoFolder="collection")
     queued++
    } catch(cancelled: CancellationException) { throw cancelled }
    catch(_: Exception) { skipped++ }
   }
   _notice.value="Playlist: $queued video diantrekan; semua file masuk folder $folder." + if(skipped > 0) " $skipped dilewati." else ""
   } catch(cancelled: CancellationException) { throw cancelled }
   catch(error: Exception) { _error.value=error.message }
   finally { submitting=false }
 } }
 @Volatile private var submitting=false
 fun enqueue(options: DownloadOptions) { if(submitting) return; submitting=true; viewModelScope.launch {
   try { submit(options) }
   catch(cancelled: CancellationException) { throw cancelled }
   catch(error: Exception) { _error.value=error.message }
   finally { submitting=false }
 } }
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
   catch(error: Exception) { _error.value=error.message }
   finally { submitting=false }
 } }
 private suspend fun submit(options: DownloadOptions, keepId: String? = null, titleHint: String? = null, durationSeconds: Long? = null, subfolder: String? = null, entryMetadata: VideoMetadata? = null, autoFolder: String = "") {
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
   val meta=entryMetadata ?: _metadata.value?.takeIf { it.url==url }
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
 }
 /** Menaruh pekerjaan WorkManager untuk sebuah job (dipakai submit baru maupun melanjutkan yang dijeda). */
 private suspend fun enqueueWork(id: String, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP, failMessage: String = "Tidak dapat memasukkan unduhan ke antrean.") {
  val request=OneTimeWorkRequestBuilder<DownloadWorker>().setInputData(workDataOf("jobId" to id, "generation" to (dao.get(id)?.generation ?: -1))).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).addTag(id).build()
  try { withContext(Dispatchers.IO) { work.enqueueUniqueWork("download-$id",policy,request).result.get() } }
  catch(cancelled: CancellationException) { throw cancelled }
  catch(error: Exception) { dao.fail(id,failMessage); throw error }
 }
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
  // Job tanpa pekerjaan (mis. dijeda) tak punya yang membersihkan berkas parsialnya; job berjalan dibersihkan
  // oleh finally milik worker setelah engine berhenti (BUG-004).
  if(cancelled > 0 && !hasActiveWork(id)) deleteJobDir(id)
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
  catch(error: Exception) { _error.value=error.message }
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
  catch(error: Exception) { _error.value=error.message }
 } }
 /** Melanjutkan unduhan yang dijeda; unduhan parsial dilanjutkan, bukan diulang. */
 fun resume(id: String) { viewModelScope.launch {
  try {
   awaitIdle(id)
   if(dao.resume(id) == 0) return@launch
   enqueueWork(id, ExistingWorkPolicy.REPLACE, "Tidak dapat melanjutkan unduhan.")
   _notice.value="Unduhan dilanjutkan."
  } catch(cancelled: CancellationException) { throw cancelled }
  catch(error: Exception) { _error.value=error.message }
 } }
 fun resumeAll() { viewModelScope.launch {
  try {
   val ids=dao.observe().first().filter { it.state == JobState.PAUSED }.map { it.id }
   if(ids.isEmpty()) { _notice.value="Tidak ada unduhan yang dijeda."; return@launch }
   var done=0
   ids.forEach { id -> awaitIdle(id); if(dao.resume(id) > 0) { enqueueWork(id, ExistingWorkPolicy.REPLACE, "Tidak dapat melanjutkan unduhan."); done++ } }
   _notice.value="${done} unduhan dilanjutkan."
  } catch(cancelled: CancellationException) { throw cancelled }
  catch(error: Exception) { _error.value=error.message }
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
   catch(error: Exception) { _error.value=error.message }
   finally { submitting=false }
  }
 }
 private fun enqueueRevive(job: DownloadJob) { if(submitting) return; submitting=true; viewModelScope.launch {
   try { submit(job.toOptions(), keepId = job.id) }
   catch(cancelled: CancellationException) { throw cancelled }
   catch(error: Exception) { _error.value=error.message }
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
   val root=SettingsStore.storageTree?.toUri() ?: defaultTreeGrant(requireWrite=true)
    ?: error("Akses folder diperlukan. Pilih Download/${SettingsStore.storageFolder} agar file dan subfolder dapat dibersihkan.")
   val result=MediaFileOperations.mutex.withLock {
    val rows=dao.observe().first()
    check(rows.none { it.state in setOf(JobState.QUEUED,JobState.RUNNING,JobState.PAUSED) } && !submitting) {
     "Masih ada unduhan berjalan atau dijeda — batalkan dulu sebelum menghapus semua file."
    }
    val swept=clearTree(root)
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
  catch(error: Exception) { _error.value=error.message ?: "Pembersihan folder gagal." }
  finally { _clearing.value=false; refreshStorage() }
 } }
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
    outputName to old.associateWith(editor::captureForRefetch)
   }
   _notice.value="Mencari lirik di penyedia lirik…"
   Lyrics.clearCache() // Explicit refresh bypasses provider response cache.
   val outcome=Lyrics.fetch(job.songIdentity(),job.durationSeconds)
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
 private suspend fun writeLyricBesideAudio(job: DownloadJob, text: String, expected: Map<String, ByteArray>): Boolean {
  val app=getApplication<Application>()
  val resolver=app.contentResolver
  val bytes=text.toByteArray(Charsets.UTF_8)
  val output=(job.outputUri ?: return false).toUri()
  fun writeTo(target: android.net.Uri, mode: String): Boolean = runCatching {
   val stream=resolver.openOutputStream(target, mode) ?: return@runCatching false
   stream.use { it.write(bytes) }
   require(LyricEditor(app).captureForRefetch(target.toString()).contentEquals(bytes)) { "Verifikasi lirik gagal." }
   true
  }.getOrDefault(false)
  /** Lepas jurnal pending setelah penghapusan terbukti berhasil; gagal = jejak tetap untuk recovery (BUG-008/R2). */
  suspend fun dropJournal(target: android.net.Uri) {
   if(StorageUris.delete(app, target)) dao.sidecarPending(job.id, null)
  }
  // Sidecar lama dinilai dari NAMA TAMPILAN aktual, bukan akhiran URI — provider bisa menyisipkan
  // ekstensi mime ("X.lrc.txt"). Yang benar .lrc jadi target timpa; yang salah ekstensi dibuang
  // setelah pengganti terbit (BUG-015).
  val subs=job.sidecarUris?.lines()?.filter { it.startsWith("sub|") }?.map { it.substringAfter('|') }
   .orEmpty().filter { it.isNotBlank() }.map { it.toUri() }
  val stem=displayNameOf(output)?.substringBeforeLast('.') ?: return false
  val requested="$stem.lrc"
  val validOld=subs.filter { displayNameOf(it)?.endsWith(".lrc", true) == true }
  require(validOld.map { it.toString() }.toSet() == expected.keys) { "Lirik berubah selama pencarian; coba lagi." }
  require(validOld.size <= 1) { "Beberapa file lirik tercatat; penggantian dibatalkan." }
  val stale=subs.filter { displayNameOf(it)?.endsWith(".lrc", true) != true }
  suspend fun dropStale() {
   if(stale.isEmpty()) return
   val staleRaw=stale.filter { StorageUris.delete(app, it) }.map { it.toString() }.toSet()
   val kept=job.sidecarUris?.lines()?.filter { it.isNotBlank() && it.substringAfter('|',"") !in staleRaw }.orEmpty()
   dao.sidecars(job.id, kept.joinToString("\n").ifBlank { null })
  }
  if(validOld.isNotEmpty()) {
   val old=validOld.first()
   require(displayNameOf(old) == requested) { "Nama lirik tidak sesuai audio; file lama tidak diubah." }
   LyricEditor(app).replaceForRefetch(old.toString(), text, requireNotNull(expected[old.toString()]), job.durationSeconds.takeIf { it > 0 }?.times(1000))
   dropStale()
   return true
  }
  val uri = if(android.provider.DocumentsContract.isDocumentUri(app, output)) {
   // Verify the actual parent; opaque providers without parent information fail closed.
   // ExternalStorageProvider document IDs supply a path fallback, not an arbitrary tree root.
   val parentId=runCatching {
    resolver.query(output, arrayOf("parent_document_id"), null, null, null)?.use { c -> if(c.moveToFirst()) c.getString(0) else null }
   }.getOrNull()
   // Fail closed for opaque providers rather than publishing beside the wrong audio.
   val documentId = android.provider.DocumentsContract.getDocumentId(output)
   val pathParent = if (output.authority == "com.android.externalstorage.documents" && documentId.contains('/')) documentId.substringBeforeLast('/') else null
   val resolvedParent = parentId ?: pathParent ?: error("Induk audio tidak dapat diverifikasi; file tidak diubah.")
   val parent=android.provider.DocumentsContract.buildDocumentUriUsingTree(output, resolvedParent)
   // BUG-015: paksa kembali nama yang diminta bila provider menyisipkan ekstensi mime.
   val made=runCatching { android.provider.DocumentsContract.createDocument(resolver, parent, "text/plain", requested) }.getOrNull() ?: return false
   // Jurnal sebelum menulis: proses mati di tengah penulisan tetap terlacak (BUG-008/R2).
   dao.sidecarPending(job.id, made.toString())
   val actual=displayNameOf(made)
   val final=if(actual != null && actual != requested) runCatching { android.provider.DocumentsContract.renameDocument(resolver, made, requested) }.getOrNull() ?: made else made
   // Setelah rename document ID bisa berubah — jurnal ikut diperbarui (R2).
   dao.sidecarPending(job.id, final.toString())
   if(!writeTo(final, "wt")) { dropJournal(final); return false }
   // R5: verifikasi nama tampilan akhir — rename yang ditolak provider tidak boleh dilaporkan sukses ("X.lrc.txt").
   if(displayNameOf(final) != requested) { dropJournal(final); return false }
   final
  } else {
   val relative=runCatching {
    resolver.query(output, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)?.use { c -> if(c.moveToFirst()) c.getString(0) else null }
   }.getOrNull() ?: return false
   val created=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
    put(MediaStore.Downloads.DISPLAY_NAME, "$stem.lrc")
    put(MediaStore.Downloads.MIME_TYPE, "text/*") // sengaja wildcard seperti worker: ekstensi ikut DISPLAY_NAME, bukan tebakan provider
    put(MediaStore.Downloads.RELATIVE_PATH, relative)
    put(MediaStore.Downloads.IS_PENDING, 1)
   }) ?: return false
   dao.sidecarPending(job.id, created.toString())
   if(!writeTo(created, "w")) { dropJournal(created); return false }
   if(resolver.update(created, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null) <= 0) { dropJournal(created); return false }
   // Nama MediaStore ikut DISPLAY_NAME; verifikasi sama seperti jalur SAF (R5).
   if(displayNameOf(created) != requested) { dropJournal(created); return false }
   created
  }
  // Perbarui daftar sidecar: buang baris lirik lama/salah ekstensi dan catat pengganti baru.
  dropStale()
  dao.commitSidecar(job.id, "sub|$uri", null)
  return true
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
