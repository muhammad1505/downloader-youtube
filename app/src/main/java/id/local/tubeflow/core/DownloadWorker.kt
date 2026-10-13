package id.local.tubeflow.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.io.File class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context,params) {
 companion object {
  /**
   * Gerbang adisi tunggal untuk semua worker. Batas dibaca ulang dari pengaturan pada tiap percobaan masuk,
   * sehingga mengubah maxConcurrent langsung dipakai antrean berikutnya. Dulu ada semaphore terpisah per nilai
   * batas (3 dan 4) — yang lama masih dipegang worker lama sehingga batas terlewati (3+4 = 7 berjalan sekaligus).
   */
  private val gateLock = Any()
  private var activeWorkers = 0
  private suspend fun acquireSlot() {
   while (true) {
    val limit = SettingsStore.maxConcurrent.coerceIn(1, 4)
    val admitted = synchronized(gateLock) {
     if (activeWorkers < limit) { activeWorkers++; true } else false
    }
    if (admitted) return
    delay(300)
   }
  }
  private fun releaseSlot() = synchronized(gateLock) { activeWorkers-- }
 }
 private val dao = DownloadDatabase.get(context).downloads()
 override suspend fun getForegroundInfo(): ForegroundInfo = foreground(-1,"Menyiapkan unduhan")
 private fun foreground(progress: Int, text: String): ForegroundInfo {
  val manager = applicationContext.getSystemService(NotificationManager::class.java)
  manager.createNotificationChannel(NotificationChannel("downloads","Unduhan",NotificationManager.IMPORTANCE_LOW))
  val notification = NotificationCompat.Builder(applicationContext,"downloads").setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("TubeFlow").setContentText(text).setProgress(100,progress.coerceAtLeast(0),progress < 0).setOnlyAlertOnce(true).setOngoing(true).addAction(android.R.drawable.ic_menu_close_clear_cancel,"Batalkan", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
  return ForegroundInfo(id.hashCode().let { if(it == 0) 1 else it },notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
 }
 private suspend fun handleStop(jobId: String, generation: Int) {
  // Jeda/batal sudah dicatat lebih dulu dari UI — jangan menimpa status PAUSED/CANCELLED yang dipilih pengguna.
  val state = dao.get(jobId)?.state
  if (state == JobState.PAUSED || state == JobState.CANCELLED) return
  // Job sudah digantikan pekerjaan lain (jeda→lanjut) — pemilik barulah yang mengelola statusnya (BUG-002).
  if (takenOverByOther()) return
  val cancelledByApp = if(android.os.Build.VERSION.SDK_INT >= 31) stopReason == WorkInfo.STOP_REASON_CANCELLED_BY_APP else WorkManager.getInstance(applicationContext).getWorkInfoById(id).get()?.state == WorkInfo.State.CANCELLED
  if(cancelledByApp) dao.cancelOwned(jobId, generation)
  else dao.interrupted(jobId, generation)
 }
 override suspend fun doWork(): Result {
  val jobId = inputData.getString("jobId")
  // Generasi eksekusi diambil SEBELUM antre slot: worker yang digantikan (jeda→lanjut) memakai generasi lamanya,
  // sehingga semua update bersyaratnya otomatis tidak mengenai baris yang sudah dilanjutkan (BUG-002).
  val generation = inputData.getInt("generation", -1).takeIf { it >= 0 } ?: jobId?.let { dao.get(it)?.generation } ?: -1
  try {
   setForeground(getForegroundInfo())
   SettingsStore.load(applicationContext)
   acquireSlot()
   try { return executeDownload(generation) } finally { releaseSlot() }
  } catch(cancelled: CancellationException) {
   jobId?.let { withContext(NonCancellable) { handleStop(it, generation) } }
   throw cancelled
  } catch(error: Exception) {
   jobId?.let { dao.failOwned(it, generation, error.message?.take(500) ?: "Unduhan tidak dapat dimulai.") }
   return Result.success()
  }
 }
 private suspend fun executeDownload(generation: Int): Result = withContext(Dispatchers.IO) {
  val jobId = inputData.getString("jobId") ?: return@withContext Result.failure()
  var job = dao.get(jobId) ?: return@withContext Result.failure()
  if(job.state !in setOf(JobState.QUEUED, JobState.RUNNING) || job.generation != generation) return@withContext Result.success()
  StalePublicationCleanup.recover(applicationContext)
  job.pendingUri?.let { raw -> check(StalePublicationCleanup.delete(applicationContext, raw)) { "File parsial lama belum dapat dihapus; periksa akses folder." } }
  // Sisa publikasi sidecar dari run sebelumnya yang mati di tengah jalan — bersihkan sekarang (BUG-008).
  if(job.sidecarPending != null) {
   job.sidecarPending.lines().filter { it.isNotBlank() }.forEach { raw -> check(StalePublicationCleanup.delete(applicationContext, raw)) { "File pendukung lama belum dapat dihapus." } }
   dao.sidecarPendingOwned(jobId, generation, null)
  }
  // Sidecar terbit dari finalisasi yang terputus: media tidak pernah terbit (outputUri null), jadi berkas
  // pendamping di folder tujuan yatim — buang sekarang agar run ini menerbitkan pengganti tanpa ganda (R2).
  if(job.outputUri == null && job.sidecarUris != null) {
   job.sidecarUris.lines().mapNotNull { line -> line.substringAfter('|',"").takeIf { it.isNotBlank() } }.forEach { raw -> check(StalePublicationCleanup.delete(applicationContext, raw)) { "File pendukung lama belum dapat dihapus." } }
   dao.sidecarsOwned(jobId, generation, null)
  }
  val directory = File(applicationContext.noBackupFilesDir,"downloads/$jobId").apply { mkdirs() }
  dao.resumeInfo(jobId, generation, directory.walkTopDown().filter { it.isFile && it.name.endsWith(".part") }.sumOf { it.length() }, "")
  var pending: Uri? = null
  val ownSidecars=mutableListOf<Uri>()
  var completed=false
  // Auto-folder playlist/Mix: subfolder satu segmen yang divalidasi (nama kosong/tak aman = folder utama).

  try {
   val gate = Any()
   var last = 0L
   var lastProgress = DownloadProgress(DownloadPhase.PREPARING)
   fun publish(update: DownloadProgress, force: Boolean = false) {
    if (isStopped) return
    synchronized(gate) {
     val now = android.os.SystemClock.elapsedRealtime()
     val changedPhase = update.phase != lastProgress.phase || update.track != lastProgress.track
     if (!force && !changedPhase && update.percent != 100f && now - last < 500) return
     last = now
     lastProgress = update
     runCatching { runBlocking {
      dao.progress(jobId,generation,JobState.RUNNING,update.percent,update.speed,update.eta,update.phase)
      setForeground(foreground(update.percent.toInt(), "${DownloadPhase.label(update.phase)} · ${job.title}"))
     } }
    }
   }
   publish(lastProgress, force = true)
   var sourceTrackAvailable = false
   // Refresh source metadata on every attempt: playlist entries and expired URLs need the same identity path.
   try {
    var metadata = Engine.inspect(applicationContext, job.url, job.id)
    sourceTrackAvailable = metadata.track.isNotBlank()
    if (job.mode == MediaMode.AUDIO || job.autoFolder.isNotBlank()) {
     val initial = TitleSplit.identify(metadata.title, metadata.uploader, metadata.artist, metadata.track, metadata.creator)
     val resolved = Lyrics.resolveIdentity(initial, metadata.durationSeconds)
     if (resolved.evidence == "provider_identity") metadata = metadata.copy(artist=resolved.artist.orEmpty(), track=resolved.title, artistEvidence=resolved.evidence)
    }
    val updated = job.withMetadata(metadata)
    if (dao.metadata(jobId, generation, updated.rawTitle, updated.trackTitle, updated.title, updated.artist,
      updated.creator, updated.remixer, updated.versionTag, updated.artistEvidence, updated.platformId,
      updated.thumbnailUrl, updated.sourceId, updated.extractor, updated.subfolder, updated.durationSeconds) == 0) throw CancellationException()
    job = updated
   } catch (cancelled: CancellationException) { throw cancelled }
   catch (_: Exception) { /* Extracting metadata may fail while the engine can still download. Keep the stored identity. */ }
   CoverCache.fetch(applicationContext, job)?.let { path ->
    if (dao.coverPath(jobId, generation, path) > 0) job = job.copy(coverPath = path) else CoverCache.deleteIfUnused(applicationContext, jobId, path)
   }
   ensureActive()
   if(dao.get(jobId)?.state == JobState.CANCELLED) throw CancellationException()
   // Mode audio tanpa lirik penyedia: caption YouTube tidak ditulis sama sekali (lebih baik kosong daripada ngaco).
   val skipSubtitles = job.mode == MediaMode.AUDIO
   // Each track reports its actual byte percentage; postprocessors have no reliable percent.
   dao.processingNotice(jobId, generation, "")
   val result = Engine.download(applicationContext,job,directory,skipSubtitles, callback = { update -> publish(update) }, resumeNotice = { notice ->
    runBlocking { dao.resumeInfo(jobId, generation, directory.walkTopDown().filter { it.isFile && it.name.endsWith(".part") }.sumOf { it.length() }, notice) }
   }, processingNotice = { notice -> runBlocking { dao.processingNotice(jobId, generation, notice) } })
   // Inspect completed audio only. File tags can resolve identity when source credits were absent.
   if (job.mode == MediaMode.AUDIO) {
    val reader = MediaMetadataRetriever()
    try {
     reader.setDataSource(result.absolutePath)
     val updated = job.withNativeAudioMetadata(
      reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty(),
      reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty(), !sourceTrackAvailable)
     if (dao.metadata(jobId, generation, updated.rawTitle, updated.trackTitle, updated.title, updated.artist,
       updated.creator, updated.remixer, updated.versionTag, updated.artistEvidence, updated.platformId,
       updated.thumbnailUrl, updated.sourceId, updated.extractor, updated.subfolder, updated.durationSeconds) == 0) throw CancellationException()
     job = updated
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { /* Optional tag inspection never discards complete media. */ }
    finally { reader.release() }
   }
   val subfolder = Selection.safeFolder(job.subfolder)
   val relativePath = "Download/${SettingsStore.storageFolder}" + (subfolder?.let { "/$it" } ?: "")
   // Lirik audio HANYA dari penyedia lirik (LRCLIB) — caption YouTube untuk musik sering berantakan/beda lagu.
   // Hasil terstruktur (tersimpan/tidak ditemukan/instrumental/jaringan) dicatat ke riwayat (BUG-014).
   val lyricOutcome = if (job.mode == MediaMode.AUDIO && job.subtitles.isNotEmpty()) {
    publish(DownloadProgress(DownloadPhase.LYRICS), force = true)
    Lyrics.fetch(job.songIdentity(), job.durationSeconds)
   } else null
   // Cache sampul untuk kartu Antrean/Riwayat (P2): salin thumbnail hasil unduhan ke covers/<id>.jpg —
   // jalur internal yang bertahan setelah direktori kerja job dibersihkan. Gagal menyalin tidak menggagalkan job.
   runCatching {
    val thumb = directory.listFiles()?.firstOrNull { it.isFile && it.name.startsWith("media.") && it.extension.equals("jpg", ignoreCase = true) }
    if (thumb != null) {
     CoverCache.fromFile(applicationContext, job, thumb)?.let { path ->
      dao.coverPath(jobId, generation, path)
     }
    }
   }
   ensureActive()
   if(dao.get(jobId)?.state == JobState.CANCELLED) throw CancellationException()
   val resolver = applicationContext.contentResolver
   val tree = SettingsStore.storageTree
   // SAF: dokumen induk media & sidecar — direktori subfolder playlist bila ada, akar tree kalau tidak.
   val parentDoc: Uri? = if (tree != null) (subfolder?.let { MediaFileOperations.mutex.withLock { ensureSubfolderDoc(resolver, tree, it) } } ?: treeRootUri(tree)) else null
   val name = SmartMedia.fileName(job.songIdentity(), job.rawTitle.ifBlank { job.title }, result.extension, job.autoRename)
   val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(result.extension) ?: "application/octet-stream"
   publish(DownloadProgress(DownloadPhase.SAVING, 0f), force = true)
   pending = if (tree != null) {
    DocumentsContract.createDocument(resolver, parentDoc ?: error("Folder tujuan belum dapat disiapkan."), mime, name) ?: error("Tidak dapat membuat file di folder pilihan.")
   } else {
    resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
     put(MediaStore.Downloads.DISPLAY_NAME, name)
     put(MediaStore.Downloads.MIME_TYPE, mime)
     put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
     put(MediaStore.Downloads.IS_PENDING, 1)
    }) ?: error("Tidak dapat membuat file di Downloads.")
   }
   if(dao.pending(jobId,generation,pending.toString()) == 0) throw CancellationException()
   val copyTotal = result.length().coerceAtLeast(1L)
   var copied = 0L
   resolver.openOutputStream(pending, if (tree != null) "wt" else "w")?.use { output -> result.inputStream().use { input -> val buffer=ByteArray(65536); while(true) { ensureActive(); val size=input.read(buffer); if(size<0) break; output.write(buffer,0,size); copied += size; publish(DownloadProgress(DownloadPhase.SAVING, (100.0 * copied / copyTotal).toFloat())) } } } ?: error("Tidak dapat menyimpan file.")
   ensureActive()
   publish(DownloadProgress(DownloadPhase.FINALIZING), force = true)
   if(dao.get(jobId)?.state == JobState.CANCELLED) throw CancellationException()
   withContext(NonCancellable) {
    val published=pending
    if (tree == null) require(resolver.update(published,ContentValues().apply { put(MediaStore.Downloads.IS_PENDING,0) },null,null) > 0) { "Tidak dapat menerbitkan hasil unduhan." }
    lyricOutcome?.text?.let { text -> runCatching { File(directory,"media.lrc").writeText(text) } }
    // BUG-010: provider boleh mengganti nama saat tabrakan (mis. "Judul (1).mp3") — sidecar wajib ikut nama itu.
    val publishedStem = displayNameOf(resolver, published)?.substringBeforeLast('.') ?: Format.tidyFileName(job.title)
    // Baris sidecar sudah dicatat atomik per file di dalam publishSidecars — jangan ditulis ulang dari sini (R2).
    val sidecarLines = publishSidecars(directory,result,job,resolver,relativePath,parentDoc,publishedStem,ownSidecars)
    // Status lirik: lirik ditemukan tetapi .lrc gagal terbit = gagal disimpan (BUG-014).
    // Kebenaran dinilai dari nama tampilan aktual, bukan akhiran URI — provider bisa mengganti nama (BUG-015).
    val lyricStatus = when {
     lyricOutcome == null -> ""
     lyricOutcome.status == LyricStatus.SAVED ->
      if (hasPublishedLyric(sidecarLines, resolver)) LyricStatus.SAVED else LyricStatus.SAVE_FAILED
     else -> lyricOutcome.status
    }
    // BUG-015/R5: rename ditolak provider sehingga nama salah ("X.lrc.txt") → status gagal simpan DAN
    // berkas salah nama dibuang — bukan sukses palsu, bukan sampah yang tertinggal.
    if (lyricOutcome?.status == LyricStatus.SAVED && lyricStatus == LyricStatus.SAVE_FAILED && sidecarLines != null) {
     val lines = sidecarLines.lines().filter { it.isNotBlank() }
     var kept = lines
     lines.filter { it.startsWith("sub|") }.forEach { line ->
      val raw = line.substringAfter('|',"")
      val goodName = raw.isNotBlank() && runCatching { displayNameOf(resolver, raw.toUri()) }.getOrNull()?.endsWith(".lrc") == true
      // Jurnal hanya dilepas bila penghapusan benar-benar berhasil — gagal = jejak tetap ada (R2).
      if (!goodName && raw.isNotBlank() && StorageUris.delete(applicationContext, raw.toUri())) kept = kept - line
     }
     if (kept.size != lines.size) dao.sidecarsOwned(jobId, generation, kept.joinToString("\n").ifBlank { null })
    }
    if (lyricStatus.isNotEmpty() && dao.lyricStatusOwned(jobId, generation, lyricStatus) == 0) throw CancellationException()
    // Completed only after media, requested sidecars and their outcomes are recorded.
    if(dao.finish(jobId,generation,published.toString(),System.currentTimeMillis()) == 0) throw CancellationException()
    completed=true
    pending=null
    dao.get(jobId)?.processingNotice?.takeIf { it.isNotBlank() }?.let {
     DownloadDatabase.get(applicationContext).media().event(MediaEvent(jobId = jobId, action = "PROCESSING_FAILED", kind = job.mode.name, platformId = job.platformId, at = System.currentTimeMillis()))
    }
    runCatching { MediaIndexWorker.enqueue(applicationContext, jobId) }
    notifyResult(job, published, null, lyricStatus)
   }
   Result.success()
  } catch(cancelled: CancellationException) {
   withContext(NonCancellable) { handleStop(jobId, generation) }
   throw cancelled
  } catch(error: Exception) {
   // Penghentian karena jeda/batal/digantikan pekerjaan lain bukan kegagalan — jangan menandai Gagal.
   val takenOver = takenOverByOther()
   val state = dao.get(jobId)?.state
   if (takenOver || state == JobState.PAUSED || state == JobState.CANCELLED || isStopped) {
    if (!takenOver) withContext(NonCancellable) { handleStop(jobId, generation) }
   } else {
    dao.failOwned(jobId, generation, error.message?.take(500) ?: "Unduhan gagal.")
    notifyResult(job, null, error.message?.take(200) ?: "Unduhan gagal.")
   }
   Result.success()
  } finally {
   // Hanya pemilik unique work "download-$jobId" yang boleh mematikan mesin/menghapus berkas;
   // pekerjaan lama yang digantikan (jeda lalu lanjut) harus diam agar unduhan barunya tidak ikut mati.
   // Kepemilikan dievaluasi ulang di dalam NonCancellable tepat sebelum mutasi — resume boleh masuk di
   // antara pemeriksaan, dan semua tulisan dibatasi generasi milik worker ini (BUG-002/004/R1).
   withContext(NonCancellable) {
    val takenOver = takenOverByOther()
    if (!takenOver) Engine.cancel(jobId)
    if (!completed) ownSidecars.forEach { uri -> runCatching { StalePublicationCleanup.delete(applicationContext, uri.toString()) } }
    val pendingDeleted=pending?.let { uri -> runCatching { StalePublicationCleanup.delete(applicationContext, uri.toString()) }.getOrDefault(false) } ?: true
    val current = dao.get(jobId)
    if (!takenOver && current != null && current.generation == generation) {
     if (pendingDeleted) dao.clearPendingOwned(jobId, generation)
     dao.resumeInfo(jobId, generation, directory.walkTopDown().filter { it.isFile && it.name.endsWith(".part") }.sumOf { it.length() }, dao.get(jobId)?.resumeNotice.orEmpty())
    // Jeda (PAUSED), putus koneksi (kembali QUEUED), maupun Gagal (FAILED) menyimpan parsial —
    // "Lanjutkan"/"Coba lagi" lanjut dari file .part, bukan dari nol (BUG-004/BUG-025/P1 resume).
    // yt-dlp memvalidasi sendiri keaslian parsial (Range/ukuran; -c default) saat lanjut. Batal tetap dibersihkan.
    val keepPartial = current.state == JobState.PAUSED || current.state == JobState.QUEUED || current.state == JobState.FAILED
     if (!Engine.isRunning(jobId) && !keepPartial && !takenOverByOther()) directory.deleteRecursively()
    }
   }
  }
 }
 /** True bila ada pekerjaan WorkManager lain untuk job ini yang masih aktif (jeda→lanjut menggantikan pekerjaan lama). */
 private fun takenOverByOther(): Boolean = runCatching {
  WorkManager.getInstance(applicationContext).getWorkInfosForUniqueWork("download-${inputData.getString("jobId")}").get()
 }.getOrDefault(emptyList()).any { it.id != id && !it.state.isFinished }

 /** Ada .lrc yang benar-benar terbit dengan nama akhiran .lrc (bukan sisipan ekstensi mime provider) — BUG-015. */
 private fun hasPublishedLyric(sidecarLines: String?, resolver: android.content.ContentResolver): Boolean =
  sidecarLines?.lines()?.any { line ->
   line.startsWith("sub|") && runCatching { displayNameOf(resolver, line.substringAfter('|',"").toUri()) }.getOrNull()?.endsWith(".lrc") == true
  } == true
 /** Menyalin berkas pendamping di samping media ke folder tujuan; mengembalikan baris "kind|uri".
  * Gagal satu berkas tidak menggagalkan job, tetapi URI parsial yang terlanjur dibuat ikut dihapus (BUG-008)
  * dan nama keluaran mengikuti nama media yang benar-benar diterbitkan (BUG-010). */
 private suspend fun publishSidecars(directory: File, main: File, job: DownloadJob, resolver: android.content.ContentResolver, relativePath: String, parentDoc: Uri?, publishedStem: String, ownSidecars: MutableList<Uri>): String? {
  val mediaExtensions = setOf("srt","vtt","ass","lrc","ttml","srv1","srv2","srv3","jpg","jpeg","png","webp","gif")
  val recorded = mutableListOf<String>()
  val inFlight = mutableListOf<String>()
  for (file in directory.listFiles().orEmpty()) {
   if (!file.isFile || file == main || file.name == "result.txt") continue
   val extension = file.extension.lowercase()
   if (extension !in mediaExtensions) continue
   val isThumb = extension in setOf("jpg","jpeg","png","webp","gif")
    // Thumbnail selalu terunduh untuk cache sampul (P2) — sebagai sidecar hanya bila diminta pengguna.
    if (isThumb && !job.thumbnail) continue
   // Lirik audio disimpan sebagai .lrc — .srt hanya untuk subtitle video.
   val kind = if (isThumb) "thumb" else "sub"
   val lyric = kind == "sub" && job.mode == MediaMode.AUDIO
   // Lirik audio hanya file .lrc dari penyedia lirik — caption/subtitle tidak pernah dikonversi jadi lirik.
   if (lyric && extension != "lrc") continue
   val content = if (!lyric) null else runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
   if (lyric && content == null) continue
   val outExt = if (lyric) "lrc" else extension
   val tempStem = main.nameWithoutExtension
   val suffix = file.nameWithoutExtension.removePrefix(tempStem).ifBlank { "" }
   var created: Uri? = null
   suspend fun stillOwned(): Boolean = dao.get(job.id)?.let { it.generation == job.generation && it.state == JobState.RUNNING } == true
   try {
    if (!stillOwned()) throw CancellationException()
    val mime = when {
     lyric && parentDoc == null -> "text/*" // MediaStore tidak boleh menebak ekstensi (dulu jadi ".lrc.txt")
     lyric -> "text/plain" // path SAF butuh mime nyata
     else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(outExt) ?: if (parentDoc != null) "application/octet-stream" else if(isThumb) "image/*" else "text/*"
    }
    val uri = if (parentDoc != null) {
     // BUG-015: provider SAF kadang menyisipkan ekstensi mime ("X.lrc" → "X.lrc.txt") — paksa kembali nama yang diminta.
     val requested="$publishedStem$suffix.$outExt"
     val made=DocumentsContract.createDocument(resolver, parentDoc, mime, requested) ?: error("Tidak dapat membuat file pendukung di folder pilihan.")
     val actual=displayNameOf(resolver, made)
     if(actual != null && actual != requested) runCatching { DocumentsContract.renameDocument(resolver, made, requested) }.getOrNull() ?: made else made
    } else {
     val values = ContentValues().apply {
      put(MediaStore.Downloads.DISPLAY_NAME,"$publishedStem$suffix.$outExt")
      put(MediaStore.Downloads.MIME_TYPE,mime)
      put(MediaStore.Downloads.RELATIVE_PATH,relativePath)
      put(MediaStore.Downloads.IS_PENDING,1)
     }
     resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values) ?: error("Tidak dapat membuat file pendukung di Downloads.")
    }
    created = uri
    ownSidecars += uri
    // Catat sebelum menulis: bila proses mati di tengah penulisan, recovery tahu URI mana yang harus dibersihkan.
    inFlight += uri.toString()
    if (dao.sidecarPendingOwned(job.id, job.generation, inFlight.joinToString("\n")) == 0 || !stillOwned()) throw CancellationException()
    resolver.openOutputStream(uri, if (parentDoc != null) "wt" else "w")?.use { output ->
     if (content != null) output.write(content.toByteArray(Charsets.UTF_8))
     else file.inputStream().use { input -> input.copyTo(output) }
    } ?: error("Tidak dapat menyimpan file pendukung.")
    // Publikasi wajib berhasil sebelum dicatat; baris MediaStore yang gagal terbit tetap URI parsial.
    if (parentDoc == null) require(resolver.update(uri,ContentValues().apply { put(MediaStore.Downloads.IS_PENDING,0) },null,null) > 0) { "Tidak dapat menerbitkan file pendukung." }
    if (!stillOwned()) throw CancellationException()
    inFlight -= uri.toString()
    // Satu transaksi atomik per sidecar: baris terbit langsung tercatat dan jurnal dilepas — proses mati
    // di antara keduanya tidak lagi meninggalkan file terbit tanpa jejak (BUG-008/R2).
    if (dao.commitSidecarOwned(job.id, job.generation, "$kind|$uri", inFlight.joinToString("\n").ifBlank { null }) == 0) throw CancellationException()
    recorded += "$kind|$uri"
   } catch (failure: Exception) {
    // Setiap jalur kegagalan membersihkan URI yang baru dibuat — jangan tinggalkan dokumen/parsial (BUG-008).
    // Jurnal pending hanya dilepas bila penghapusan terbukti berhasil; kalau provider menolak, URI tetap
    // tercatat supaya recovery bisa mencoba lagi (BUG-003/R2).
    created?.let { gone -> if (StalePublicationCleanup.delete(applicationContext, gone.toString())) { inFlight -= gone.toString(); ownSidecars -= gone } }
    dao.sidecarPendingOwned(job.id, job.generation, inFlight.joinToString("\n").ifBlank { null })
    if (failure is CancellationException || !stillOwned()) throw CancellationException()
   }
  }
  return recorded.takeIf { it.isNotEmpty() }?.joinToString("\n")
 }

 /** Nama tampilan aktual hasil publikasi — MediaStore/SAF bisa mengganti nama saat tabrakan (BUG-010). */
 private fun displayNameOf(resolver: android.content.ContentResolver, uri: Uri): String? = runCatching {
  resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
 }.getOrNull()

 /** URI dokumen untuk akar tree: createDocument MENOLAK tree URI polos — dokumen harus berformat /tree/<id>/document/<id> ( Android 10 melempar "Invalid URI" untuk /tree/<id> saja). */
 private fun treeRootUri(tree: String): Uri {
  val treeUri = tree.toUri()
  return DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
 }

 /** Dokumen direktori <name> di bawah akar tree (auto-folder playlist): dipakai yang sudah ada — dua worker yang balapan membuat folder yang sama tidak meninggalkan "name (1)". */
 private fun ensureSubfolderDoc(resolver: android.content.ContentResolver, tree: String, name: String): Uri {
  val treeUri = tree.toUri()
  val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
  val rootDoc = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
  fun findExisting(): String? {
   resolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(rootDoc, treeDocId), arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID, android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME, android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
    while (cursor.moveToNext()) if (cursor.getString(2) == android.provider.DocumentsContract.Document.MIME_TYPE_DIR && cursor.getString(1) == name) return cursor.getString(0)
   }
   ?: error("Folder pilihan belum dapat dibaca.")
   return null
  }
  findExisting()?.let { return DocumentsContract.buildDocumentUriUsingTree(rootDoc, it) }
  val made = DocumentsContract.createDocument(resolver, rootDoc, android.provider.DocumentsContract.Document.MIME_TYPE_DIR, name) ?: error("Tidak dapat membuat subfolder di folder pilihan.")
  // Balapan dua worker: menangkan direktori paling awal dan buang yang kosong baru dibuat.
  val winner = findExisting()
  if (winner != null) {
   val winnerUri = DocumentsContract.buildDocumentUriUsingTree(rootDoc, winner)
   if (winnerUri != made) runCatching { DocumentsContract.deleteDocument(resolver, made) }
   return winnerUri
  }
  require(displayNameOf(resolver, made) == name) { "Nama folder koleksi tidak sesuai; periksa konflik nama di folder pilihan." }
  return made
 }


 /** Posts a completion/failure notification; progress is only shown while the worker runs. */
 private fun notifyResult(job: DownloadJob, uri: Uri?, error: String?, lyricStatus: String = "") {
  val manager = applicationContext.getSystemService(NotificationManager::class.java)
  manager.createNotificationChannel(NotificationChannel("results","Hasil unduhan",NotificationManager.IMPORTANCE_DEFAULT))
  val ok = uri != null
  val intent = if (ok) {
   val mime = applicationContext.contentResolver.getType(uri) ?: if (job.mode == MediaMode.AUDIO) "audio/*" else "video/*"
   Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri("Download", uri) }
  } else applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName) ?: Intent()
  val pendingIntent = PendingIntent.getActivity(applicationContext, job.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val notification = NotificationCompat.Builder(applicationContext,"results")
   .setSmallIcon(if (ok) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
   .setContentTitle(if (ok) "Selesai: ${Format.tidyFileName(job.title)}" else "Gagal: ${Format.tidyFileName(job.title)}")
   .setContentText(if (ok) "Ketuk untuk membuka file." else (error ?: "Unduhan gagal."))
   // Status lirik terpisah: audio sukses tidak dilabeli gagal hanya karena lirik tidak tersedia (BUG-014).
   .setStyle(NotificationCompat.BigTextStyle().bigText(if (ok) buildString {
    append("File tersimpan dan siap diputar.")
    if (lyricStatus.isNotEmpty()) append('\n').append(LyricStatus.label(lyricStatus)).append('.')
   } else (error ?: "Unduhan gagal.")))
   .setContentIntent(pendingIntent).setAutoCancel(true)
  // Notifikasi hasil hilang sendiri: sukses 10 detik (tetap bisa dibuka dari Riwayat), gagal 30 menit biar sempat dibaca.
  .setTimeoutAfter(if (ok) 10_000L else 30 * 60 * 1000L)
   .build()
  runCatching { manager.notify(job.id.hashCode().let { if (it == 0) 1 else it }, notification) }
 }
}
