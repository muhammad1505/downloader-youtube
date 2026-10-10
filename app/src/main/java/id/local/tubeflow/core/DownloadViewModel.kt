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
  val resolver=getApplication<Application>().contentResolver
  val tree=SettingsStore.storageTree
  if (tree != null) return measureTree(tree.toUri()) to true
  // Returning to the default output folder must not discard an existing read grant.
  val defaultTree=resolver.persistedUriPermissions.firstOrNull { permission ->
   permission.isReadPermission && runCatching {
    StorageFolderAccess.matchesDefault(permission.uri.authority,
     android.provider.DocumentsContract.getTreeDocumentId(permission.uri), SettingsStore.storageFolder)
   }.getOrDefault(false)
  }?.uri
  if (defaultTree != null) return measureTree(defaultTree) to true
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
 private var inspection: Job?=null
 private var inspectionId: String?=null
 /** Jenis inspeksi terakhir — retry harus mengulang operasi yang sama, bukan yang lain (BUG-006/R6). */
 private var lastInspectPlaylist=false
 private val recovery = viewModelScope.launch(Dispatchers.IO) {
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
     // Unduhan yang dijeda menyimpan berkas parsialnya — jangan dihapus supaya "Lanjutkan" bisa melanjutkan (BUG-004).
     if(job.state != JobState.PAUSED) deleteJobDir(job.id)
     if(job.state in setOf(JobState.RUNNING,JobState.QUEUED)) { if(infos.any { it.state == WorkInfo.State.CANCELLED }) dao.cancel(job.id) else dao.fail(job.id,"Unduhan terhenti. Ketuk coba lagi untuk mengunduh ulang.") }
     dao.clearPending(job.id)
    }
   }
 }
 /** Berkas parsial sementara sebuah job di noBackupFilesDir (BUG-004). */
 private fun deleteJobDir(id: String) { java.io.File(getApplication<Application>().noBackupFilesDir, "downloads/$id").deleteRecursively() }
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
    val message=error.message ?: "Tidak dapat membaca video."
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
  inspection=viewModelScope.launch {
   _busy.value=true; _metadata.value=null; _playlist.value=null; _error.value=null; _inspectError.value=null
   try { _playlist.value=Engine.inspectPlaylist(getApplication(),canonical,id).copy(url=original) }
   catch(cancelled: CancellationException) { throw cancelled }
   catch(error: Exception) {
    val message=error.message ?: "Tidak dapat membaca playlist."
    _error.value=message; _inspectError.value=message
   }
   finally { if(inspectionId==id) _busy.value=false }
  }
 }
 fun clearPlaylist() { _playlist.value=null }
 /** Coba lagi memakai jenis inspeksi terakhir: watch+list yang gagal sebagai playlist harus mengulang
  * playlist, bukan inspeksi video (BUG-006/R6). */
 fun retryInspect(url: String) { if(lastInspectPlaylist) inspectPlaylist(url) else inspect(url) }
 /** Queues every selected entry as its own job using the shared options; seluruh batch berbagi SATU subfolder otomatis dari judul playlist/Mix. */
 fun queuePlaylist(entries: List<PlaylistEntry>, options: DownloadOptions, folderTitle: String? = null) { if(submitting) return; submitting=true; viewModelScope.launch {
   try {
    // Auto-folder: engine selalu mengunduh per-video (--no-playlist), jadi subfolder diwirakan di sini
    // saat batch diajukan — "Mix - DENNY CAKNAN" → folder "mix-denny-caknan" untuk seluruh batch.
    val folder=folderTitle?.let { Selection.playlistFolder(it) }
    var queued=0; var skipped=0
    entries.forEach { entry ->
     try { submit(options.copy(url=entry.url), titleHint=entry.title, durationSeconds = entry.durationSeconds.takeIf { it > 0 }, subfolder=folder); queued++ }
     catch(cancelled: CancellationException) { throw cancelled }
     catch(_: Exception) { skipped++ }
    }
    _notice.value="Playlist: $queued video diantrekan" + (folder?.let { " ke folder \"$it\"" } ?: "") + if(skipped > 0) ", $skipped dilewati." else "."
   } catch(cancelled: CancellationException) { throw cancelled }
   catch(error: Exception) { _error.value=error.message }
   finally { submitting=false }
 } }
 private var submitting=false
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
 private suspend fun submit(options: DownloadOptions, keepId: String? = null, titleHint: String? = null, durationSeconds: Long? = null, subfolder: String? = null) {
   recovery.join()
   Selection.validate(options)
   val url=options.url.trim()
   // Cek duplikat memakai SELURUH opsi keluaran (BUG-005): bitrate/lirik/sematkan ikut dibandingkan.
   val duplicate=dao.observe().first().any { it.id != keepId && it.matchesOutput(url, options) && it.state in setOf(JobState.QUEUED,JobState.RUNNING,JobState.PAUSED) }
   require(!duplicate) { "Unduhan ini sudah ada di antrean." }
   val id=keepId ?: UUID.randomUUID().toString()
   val previous=keepId?.let { dao.get(it) }
   val done=dao.observe().first().firstOrNull { it.id != keepId && it.matchesOutput(url, options) && it.state == JobState.COMPLETED && outputStillExists(it) }
   require(done == null) { "File ini sudah pernah diunduh." }
   val meta=_metadata.value?.takeIf { it.url==url }
   val title=Format.neatTitle(titleHint ?: meta?.title ?: previous?.title ?: "Video YouTube", meta?.uploader)
   if(keepId != null) {
    val revived=dao.revive(id,title,options.mode,options.format,options.quality,options.bitrate,options.subtitles,options.embedSubs,options.thumbnail,options.embedThumb,System.currentTimeMillis())
    if(revived == 0) return
    cancelResultNotification(id)
   } else {
    dao.put(DownloadJob(id,url,title,options.mode,options.format,options.quality,options.bitrate, subtitles=options.subtitles, embedSubs=options.embedSubs, thumbnail=options.thumbnail, embedThumb=options.embedThumb, durationSeconds=durationSeconds ?: meta?.durationSeconds ?: previous?.durationSeconds ?: 0L, subfolder=subfolder ?: ""))
   }
   enqueueWork(id)
 }
 /** Menaruh pekerjaan WorkManager untuk sebuah job (dipakai submit baru maupun melanjutkan yang dijeda). */
 private suspend fun enqueueWork(id: String, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP, failMessage: String = "Tidak dapat memasukkan unduhan ke antrean.") {
  val request=OneTimeWorkRequestBuilder<DownloadWorker>().setInputData(workDataOf("jobId" to id)).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).addTag(id).build()
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
   if(infos.all { it.state.isFinished }) { delay(250); return@withContext }
   delay(100)
  }
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
  viewModelScope.launch {
   try {
    val app=getApplication<Application>()
    var failed=0
    // StorageUris.delete jujur: true = sudah hilang/terbukti terhapus, false = provider menolak sambil file masih ada.
    job.outputUri?.let { raw -> if(!StorageUris.delete(app, raw)) failed++ }
    job.sidecarUris?.lines()?.forEach { line ->
     val raw=line.substringAfter('|',"")
     if(raw.isNotBlank() && !StorageUris.delete(app, raw.toUri())) failed++
    }
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
 fun remove(id: String) { viewModelScope.launch { cancelResultNotification(id); // Hapus direktori hanya bila barisnya benar-benar terhapus — jangan menyentuh parsial job yang baru dilanjutkan (R1).
  if(dao.remove(id) > 0) deleteJobDir(id) } }
 /** Removes the entry together with its stored file, sidecar files and result notification. */
 fun removeFile(id: String) { viewModelScope.launch {
  cancelResultNotification(id)
  val job=dao.get(id)
  var failed=0
  if(job != null) {
   val app=getApplication<Application>()
   job.outputUri?.let { raw -> if(!StorageUris.delete(app, raw)) failed++ }
   job.sidecarUris?.lines()?.forEach { line ->
    val raw=line.substringAfter('|',"")
    if(raw.isNotBlank() && !StorageUris.delete(app, raw)) failed++
   }
  }
  val removed=dao.remove(id)
  // Jangan melapor sukses bila file ternyata tidak terhapus (izin/provider menolak) — BUG-003.
  _notice.value=when {
   removed == 0 -> "Entri sudah tidak ada."
   failed > 0 -> "Entri dihapus, tetapi $failed file gagal dihapus dari penyimpanan."
   else -> "File dan entri riwayat dihapus."
  }
 } }
 /**
 * Hapus SEMUA berkas di folder tujuan (aksi eksplisit pengguna lewat dialog konfirmasi Pengaturan —
 * berbeda dari "bersihkan cache" OPT-008 yang tidak boleh menyentuh media hasil download):
 * (1) berkas yang tercatat di riwayat dihapus via StorageUris (jujur); (2) folder ditelusuri agar
 * berkas yatim tak tercatat ikut hilang; (3) parsial semua job non-aktif dibuang. Riwayat Room TIDAK
 * dihapus — baris tanpa file terbaca "file tidak ada" lewat outputStillExists. Diblokir selama ada
 * unduhan QUEUED/RUNNING/PAUSED agar parsial yang masih dipakai tidak ikut terhapus.
 */
 fun clearStorageFolder() { if(_clearing.value) return; _clearing.value=true; viewModelScope.launch(Dispatchers.IO) {
  try {
   val rows=dao.observe().first()
   if(rows.any { it.state in setOf(JobState.QUEUED,JobState.RUNNING,JobState.PAUSED) }) {
    _notice.value="Masih ada unduhan berjalan atau dijeda — batalkan dulu sebelum menghapus semua file."; return@launch
   }
   val app=getApplication<Application>()
   var files=0; var failed=0
   // 1) Berkas tercatat di riwayat — penghapusan jujur lewat helper bersama (BUG-003).
   rows.forEach { job ->
    job.outputUri?.let { raw -> if(StorageUris.delete(app, raw)) files++ else failed++ }
    job.sidecarUris?.lines()?.forEach { line ->
     val raw=line.substringAfter('|',"")
     if(raw.isNotBlank()) { if(StorageUris.delete(app, raw)) files++ else failed++ }
    }
   }
   // 2) Telusuri folder tujuan — berkas yatim yang tak tercatat ikut terhapus.
   val tree=SettingsStore.storageTree
   val swept=if(tree != null) clearTree(tree.toUri(), 0) else clearMediaStoreFolder()
   files+=swept.first; failed+=swept.second
   // 3) Parsial sementara (noBackupFilesDir) semua job — sudah dipastikan tidak ada yang berjalan/dijeda.
   rows.forEach { deleteJobDir(it.id); cancelResultNotification(it.id) }
   _notice.value=when {
    files == 0 && failed == 0 -> "Folder sudah bersih — tidak ada file untuk dihapus."
    failed > 0 -> "$files file dihapus · $failed file gagal dihapus."
    else -> "$files file dihapus dari penyimpanan."
   }
  } catch(cancelled: CancellationException) { throw cancelled }
  catch(error: Exception) { _error.value=error.message ?: "Pembersihan folder gagal." }
  finally { _clearing.value=false; refreshStorage() }
 } }
 /** Telusuri & hapus semua dokumen di bawah root tree; daftar anak dibaca dulu agar kursor tidak basi saat dihapus. */
 private fun clearTree(root: android.net.Uri, depth: Int): Pair<Int,Int> {
  if(depth > 8) return 0 to 1 // folder terlalu dalam — hentikan, jangan berjalan tanpa batas.
  val app=getApplication<Application>()
  val children=android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(root, android.provider.DocumentsContract.getTreeDocumentId(root))
  val entries=mutableListOf<Pair<String,String>>()
  app.contentResolver.query(children, arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID, android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
   while(c.moveToNext()) entries += (c.getString(0) to (c.getString(1) ?: ""))
  } ?: return 0 to 1 // folder tidak terbaca = gagal, bukan "sudah bersih".
  var deleted=0; var failed=0
  entries.forEach { (id, mime) ->
   val uri=android.provider.DocumentsContract.buildDocumentUriUsingTree(root, id)
   if(mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) {
    val nested=clearTree(uri, depth+1)
    deleted+=nested.first; failed+=nested.second
    if(StorageUris.delete(app, uri)) deleted++ else failed++ // direktori kosong ikut dihapus.
   } else if(StorageUris.delete(app, uri)) deleted++ else failed++
  }
  return deleted to failed
 }
 /** Baris MediaStore di bawah folder tujuan (termasuk subfolder playlist) — escape % dan _ agar batas berhenti di folder ini (BUG-012). */
 private fun clearMediaStoreFolder(): Pair<Int,Int> {
  val app=getApplication<Application>()
  val folder="Download/${SettingsStore.storageFolder}"
  val escaped=folder.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")
  val selection="${MediaStore.Downloads.RELATIVE_PATH} = ? OR ${MediaStore.Downloads.RELATIVE_PATH} LIKE ? ESCAPE '\\'"
  val ids=mutableListOf<Long>()
  app.contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Downloads._ID), selection, arrayOf("$folder/","$escaped/%"), null)?.use { c ->
   while(c.moveToNext()) ids += c.getLong(0)
  } ?: return 0 to 1
  var deleted=0; var failed=0
  ids.forEach { id ->
   val uri=android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
   if(StorageUris.delete(app, uri)) deleted++ else failed++
  }
  return deleted to failed
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
   _notice.value="Mencari lirik di penyedia lirik…"
   val outcome=Lyrics.fetch(job.title,job.durationSeconds)
   val status=when {
    outcome.status != LyricStatus.SAVED -> outcome.status
    writeLyricBesideAudio(job,requireNotNull(outcome.text)) -> LyricStatus.SAVED
    else -> LyricStatus.SAVE_FAILED
   }
   dao.lyricStatus(id,status)
   _notice.value=LyricStatus.label(status)+"."
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
 private suspend fun writeLyricBesideAudio(job: DownloadJob, text: String): Boolean {
  val app=getApplication<Application>()
  val resolver=app.contentResolver
  val bytes=text.toByteArray(Charsets.UTF_8)
  val output=(job.outputUri ?: return false).toUri()
  fun writeTo(target: android.net.Uri, mode: String): Boolean = runCatching {
   val stream=resolver.openOutputStream(target, mode) ?: return@runCatching false
   stream.use { it.write(bytes) }
   true
  }.getOrDefault(false)
  fun readAll(target: android.net.Uri): ByteArray? = runCatching {
   resolver.openInputStream(target)?.use { it.readBytes() }
  }.getOrNull()
  /** Lepas jurnal pending setelah penghapusan terbukti berhasil; gagal = jejak tetap untuk recovery (BUG-008/R2). */
  suspend fun dropJournal(target: android.net.Uri) {
   if(StorageUris.delete(app, target)) dao.sidecarPending(job.id, null)
  }
  // Sidecar lama dinilai dari NAMA TAMPILAN aktual, bukan akhiran URI — provider bisa menyisipkan
  // ekstensi mime ("X.lrc.txt"). Yang benar .lrc jadi target timpa; yang salah ekstensi dibuang
  // setelah pengganti terbit (BUG-015).
  val subs=job.sidecarUris?.lines()?.filter { it.startsWith("sub|") }?.map { it.substringAfter('|') }
   .orEmpty().filter { it.isNotBlank() }.map { it.toUri() }
  val validOld=subs.filter { displayNameOf(it)?.endsWith(".lrc") == true }
  val stale=subs.filter { displayNameOf(it)?.endsWith(".lrc") != true }
  suspend fun dropStale() {
   if(stale.isEmpty()) return
   stale.forEach { StorageUris.delete(app, it) }
   val staleRaw=stale.map { it.toString() }.toSet()
   val kept=job.sidecarUris?.lines()?.filter { it.isNotBlank() && it.substringAfter('|',"") !in staleRaw }.orEmpty()
   dao.sidecars(job.id, kept.joinToString("\n").ifBlank { null })
  }
  // R7: cadangkan isi lama SEBELUM menimpa — mode "wt" men-truncate begitu stream terbuka. Bila
  // write/close gagal, isi lama dipulihkan dari cadangan dan refetch melapor gagal simpan (bukan diam-diam
  // mengosongkan lirik). Cadangan gagal dibaca = tetap coba timpa tanpa rollback (perilaku lama).
  if(validOld.isNotEmpty()) {
   val old=validOld.first()
   val backup=readAll(old)
   if(writeTo(old, "wt")) { dropStale(); return true }
   if(backup != null) runCatching { resolver.openOutputStream(old, "wt")?.use { it.write(backup) } }
   return false
  }
  val stem=displayNameOf(output)?.substringBeforeLast('.') ?: return false
  val uri = if(android.provider.DocumentsContract.isDocumentUri(app, output)) {
   // Induk file audio yang SEBENARNYA (parent dokumen), bukan akar tree — hasil subfolder playlist
   // tetap mendapat .lrc di folder yang sama dengan audionya. Gagal query = akar tree (perilaku lama).
   // "parent_document_id" adalah kolom kontrak dokumen; konstanta SDK publiknya tidak ada — provider
   // yang tak mendukung akan gagal query dan jatuh ke akar tree seperti perilaku lama.
   val parentId=runCatching {
    resolver.query(output, arrayOf("parent_document_id"), null, null, null)?.use { c -> if(c.moveToFirst()) c.getString(0) else null }
   }.getOrNull()
   val parent=parentId?.let { android.provider.DocumentsContract.buildDocumentUriUsingTree(output, it) }
    ?: android.provider.DocumentsContract.buildDocumentUriUsingTree(output, android.provider.DocumentsContract.getTreeDocumentId(output))
   // BUG-015: paksa kembali nama yang diminta bila provider menyisipkan ekstensi mime.
   val requested="$stem.lrc"
   val made=runCatching { android.provider.DocumentsContract.createDocument(resolver, parent, "text/plain", requested) }.getOrNull() ?: return false
   // Jurnal sebelum menulis: proses mati di tengah penulisan tetap terlacak (BUG-008/R2).
   dao.sidecarPending(job.id, made.toString())
   val actual=displayNameOf(made)
   val final=if(actual != null && actual != requested) runCatching { android.provider.DocumentsContract.renameDocument(resolver, made, requested) }.getOrNull() ?: made else made
   // Setelah rename document ID bisa berubah — jurnal ikut diperbarui (R2).
   dao.sidecarPending(job.id, final.toString())
   if(!writeTo(final, "wt")) { dropJournal(final); return false }
   // R5: verifikasi nama tampilan akhir — rename yang ditolak provider tidak boleh dilaporkan sukses ("X.lrc.txt").
   if(displayNameOf(final)?.endsWith(".lrc") != true) { dropJournal(final); return false }
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
   if(displayNameOf(created)?.endsWith(".lrc") != true) { dropJournal(created); return false }
   created
  }
  // Perbarui daftar sidecar: buang baris lirik lama/salah ekstensi dan catat pengganti baru.
  dropStale()
  val staleRaw=stale.map { it.toString() }.toSet()
  val kept=job.sidecarUris?.lines()?.filter { it.isNotBlank() && it.substringAfter('|',"") !in staleRaw }.orEmpty()
  dao.sidecars(job.id, (kept + "sub|$uri").joinToString("\n"))
  // Pengganti sudah tercatat di sidecarUris — jurnal pending dilepas (BUG-008/R2).
  dao.sidecarPending(job.id, null)
  return true
 }
 fun clearCompleted() { viewModelScope.launch {
  val ids=dao.observe().first().filter { it.state == JobState.COMPLETED }.map { it.id }
  val n=dao.clearCompleted(); ids.forEach(::cancelResultNotification)
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
