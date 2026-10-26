package id.local.tubeflow.core

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Engine {
 private val lock = Mutex()
 private data class Execution(val thread: Thread, val controlFile: File, val token: String)
 private val activeThreads=java.util.concurrent.ConcurrentHashMap<String,Execution>()
 private lateinit var processDirectory: File
 private lateinit var extractorCache: File
 fun isRunning(id: String) = activeThreads[id]?.thread?.isAlive == true
 suspend fun initialize(context: Context) = lock.withLock {
  withContext(Dispatchers.IO) {
   try {
    Lyrics.initialize(File(context.cacheDir, "lyrics-responses"), File(context.noBackupFilesDir, "lyrics-cooldown.json"))
    installBundledEngine(context)
    YoutubeDL.getInstance().init(context)
    FFmpeg.getInstance().init(context)
   } catch (failure: LinkageError) {
    throw IllegalStateException("Mesin download gagal disiapkan. Pasang pembaruan aplikasi lalu coba lagi.", failure)
   }
  }
 }
 @Volatile private var assetInstalled = false
 fun isBusy(): Boolean = activeThreads.values.any { it.thread.isAlive }
 suspend fun activateStoredEngine(context: Context) = lock.withLock {
  check(activeThreads.values.none { it.thread.isAlive }) { "Mesin media sedang dipakai." }
  withContext(Dispatchers.IO) {
   EngineUpdater.invalidateInstalledCache()
   installEngineFile(context, force = true)
  }
 }
 private fun installBundledEngine(context: Context) {
  if(assetInstalled) return
  installEngineFile(context, force = false)
  installBootstrap(context)
  extractorCache=File(context.cacheDir, "extractor-cache").apply { mkdirs() }
  processDirectory=File(context.noBackupFilesDir,"engine-processes").apply { mkdirs() }
  processDirectory.listFiles()?.forEach { record ->
   val token=record.name.removeSuffix(".json")
   if(record.extension == "json" && token.matches(Regex("[a-f0-9]{32}"))) signalOwned(record,token)
   record.delete()
  }
  assetInstalled=true
 }
 private fun engineTarget(context: Context)=File(context.noBackupFilesDir,"youtubedl-android/yt-dlp/yt-dlp-original")
 private fun installEngineFile(context: Context, force: Boolean) {
  if(assetInstalled && !force) return
  val chosen=EngineUpdater.installed(context)
  val target=engineTarget(context)
  target.parentFile!!.mkdirs()
  if(force || !target.isFile || EngineUpdater.sha256(target) != chosen.sha256) {
   val temporary=File(target.parentFile,"yt-dlp.tmp")
   try {
    val source=chosen.file
    if (source == null) context.assets.open("yt-dlp").use { input -> java.io.FileOutputStream(temporary).use { output -> input.copyTo(output); output.fd.sync() } }
    else source.inputStream().use { input -> java.io.FileOutputStream(temporary).use { output -> input.copyTo(output); output.fd.sync() } }
    require(EngineUpdater.sha256(temporary)==chosen.sha256) { "Verifikasi mesin media gagal." }
    EngineUpdater.atomicReplace(temporary,target)
   } finally { temporary.delete() }
  }
 }
 private fun installBootstrap(context: Context) {
  val entry=File(engineTarget(context).parentFile,"yt-dlp")
  val bootstrap=context.assets.open("engine-bootstrap.py").use { it.readBytes() }
  if(!entry.isFile || !entry.readBytes().contentEquals(bootstrap)) {
   val temporary=File(entry.parentFile,"bootstrap.tmp")
   try { java.io.FileOutputStream(temporary).use { it.write(bootstrap); it.fd.sync() }; android.system.Os.rename(temporary.absolutePath,entry.absolutePath) }
   finally { temporary.delete() }
  }
 }
 fun cancel(id: String) {
  activeThreads[id]?.let(::cancelExecution)
 }
 private fun signalOwned(controlFile: File, token: String) {
  runCatching {
   val record=JSONObject(controlFile.readText())
   val identity=ProcessIdentity(record.getInt("pid"),record.getInt("pgid"),record.getInt("sid"),record.getString("startTimeTicks"),record.getInt("uid"),record.getString("token"))
   val pid=identity.pid
   val stat=File("/proc/$pid/stat").readText()
   require(identity.isOwned(stat,android.system.Os.stat("/proc/$pid").st_uid,android.os.Process.myUid(),token,android.os.Process.myPid()))
   android.system.Os.kill(-pid,android.system.OsConstants.SIGKILL)
  }
 }
 private fun cancelExecution(execution: Execution) {
  signalOwned(execution.controlFile,execution.token)
  YoutubeDL.getInstance().destroyProcessById(execution.token)
 }
 private suspend fun execute(request: YoutubeDLRequest, id: String, cookieFile: File? = null, callback: ((Float,Long,String)->Unit)? = null): String {
  var executionThread: Thread? = null
  val token=java.util.UUID.randomUUID().toString().replace("-", "")
  val controlFile=File(processDirectory,"$token.json")
  request.addOption("--cache-dir", extractorCache.absolutePath)
  request.addCommands(listOf("--tubeflow-process-file",controlFile.absolutePath,"--tubeflow-process-token",token))
  var execution: Execution? = null
  try {
   // File cookie hanya dibuat untuk host Bstation; cookie Bstation tidak boleh terkirim ke platform lain.
   if (cookieFile != null) request.addOption("--cookies", cookieFile.absolutePath)
   return suspendCancellableCoroutine { continuation ->
  val thread = Thread {
   try {
    if (continuation.isActive) {
     val output = YoutubeDL.getInstance().execute(request,token,callback).out
     if(continuation.isActive) continuation.resumeWith(Result.success(output))
    }
   } catch(error: Exception) { if(continuation.isActive) continuation.resumeWith(Result.failure(error)) }
   finally { execution?.let { activeThreads.remove(id,it) }; controlFile.delete(); File(controlFile.path+".tmp").delete() }
  }
  executionThread=thread
  val activeExecution=Execution(thread,controlFile,token)
  execution=activeExecution
  activeThreads[id]=activeExecution
  continuation.invokeOnCancellation {
   Thread { while(thread.isAlive) { cancelExecution(activeExecution); Thread.sleep(100) } }.apply { isDaemon=true }.start()
  }
  thread.start()
  } } finally {
   cookieFile?.delete()
   if (!currentCoroutineContext().isActive) withContext(NonCancellable + Dispatchers.IO) {
    execution?.let(::cancelExecution)
    executionThread?.join(5000)
   }
  }
 }
 /** File cookie sementara hanya untuk URL Bstation; null membuat engine memakai jalur tanpa login. */
 private fun cookieFile(context: Context, url: String): File? {
  val host = runCatching { java.net.URI(url.trim()).host }.getOrNull() ?: return null
  if (!BstationUrls.isHost(host)) return null
  return BstationAuth.cookieFile(context)
 }
 private suspend fun dumpInfo(context: Context, url: String, id: String, onPhase: (String) -> Unit = {}): JSONObject {
  onPhase(InspectionPhase.PREPARING_ENGINE)
  initialize(context)
  onPhase(InspectionPhase.FETCHING_METADATA)
  // `--write-subs` diminta walau tidak ada yang ditulis (mode --skip-download): extractor yang membaca
  // subtitle lewat extract_subtitles() — BiliIntl/Bstation, antara lain — hanya mengisi daftar subtitle
  // di JSON dump bila subtitle diminta. Tanpa flag ini Bstation selalu tampak "tanpa subtitle".
  val request = YoutubeDLRequest(url.trim()).addOption("--ignore-config").addOption("--no-colors").addOption("--no-playlist").addOption("--skip-download").addOption("--dump-single-json").addOption("--write-subs").addOption("--socket-timeout", 20)
  return JSONObject(execute(request,id,cookieFile(context,url)))
 }
 suspend fun inspect(context: Context, url: String, id: String, onPhase: (String) -> Unit = {}): VideoMetadata {
  // Season bukan satu video; pipeline unduhan memakai satu nama keluaran sehingga tidak boleh
  // menerima daftar episode. Tolak dengan pesan yang menjelaskan sebelum menyentuh engine.
  require(!BstationUrls.isSeasonOnly(url)) { BstationUrls.SEASON_AS_VIDEO }
  require(Selection.validUrl(url)) { "Gunakan tautan video dari platform yang didukung." }
  val json = dumpInfo(context, url, id, onPhase)
  val langs = SourceMetadata.subtitleLangs(json).take(8)
  val parsed = SourceMetadata.parse(json, url.trim()).copy(subtitleLangs = langs)
  if (parsed.uploader.isNotBlank()) return parsed
  // Info dict BiliIntl tidak memuat uploader maupun durasi. Judul seri dipakai sebagai kredit
  // sumber; durasi tidak dikarang — UI menyembunyikan durasi yang belum diketahui.
  val seasonId = BstationUrls.seasonIdOfEpisode(url) ?: return parsed
  val series = BstationSeason.seriesTitle(context, seasonId)
  return if (series.isBlank()) parsed else parsed.copy(uploader = series)
 }
 suspend fun inspectPlaylist(context: Context, url: String, id: String, onPhase: (String) -> Unit = {}): PlaylistMetadata {
  // Season Bstation memakai katalog API-nya sendiri; sisanya tetap jalur playlist YouTube.
  BstationUrls.canonicalSeason(url)?.let { return BstationSeason.inspect(context, it, onPhase) }
  require(Selection.validPlaylistUrl(url)) { "Gunakan tautan playlist YouTube yang valid." }
  onPhase(InspectionPhase.PREPARING_ENGINE)
  initialize(context)
  onPhase(InspectionPhase.FETCHING_PLAYLIST)
  val request = YoutubeDLRequest(url.trim()).addOption("--ignore-config").addOption("--no-colors").addOption("--yes-playlist").addOption("--flat-playlist").addOption("--skip-download").addOption("--dump-single-json").addOption("--playlist-end", 500).addOption("--socket-timeout", 20)
  if (Selection.isMix(url)) request.addOption("--tubeflow-mix-snapshot")
  val json = JSONObject(execute(request,id))
  onPhase(InspectionPhase.READING_ENTRIES)
  val array = json.optJSONArray("entries") ?: JSONArray()
  val entryChannels = mutableListOf<String>()
  val entries = (0 until array.length()).mapNotNull { index ->
   val entry = array.optJSONObject(index) ?: return@mapNotNull null
   val videoId = entry.optString("id")
   if (videoId.isBlank()) return@mapNotNull null
   entryChannels += entry.optString("channel").ifBlank { entry.optString("uploader") }
   PlaylistEntry(videoId, entry.optString("title", "Video").ifBlank { "Video" }, entry.optLong("duration"), "https://www.youtube.com/watch?v=$videoId", entry.optString("uploader").ifBlank { entry.optString("channel") }, entry.optString("artist").takeUnless { it == "null" }.orEmpty(), entry.optString("track").takeUnless { it == "null" }.orEmpty(), SourceMetadata.parse(entry, "https://www.youtube.com/watch?v=$videoId").thumbnail)
  }
  // Channel kreator dipertahankan untuk auto-folder Mix — judul Mix YouTube sering tidak terpecah
  // oleh heuristik segmen (satu kalimat panjang tanpa " - "), sedangkan channel artisnya jelas.
  val channel = Selection.pickChannel(
   json.optString("channel").ifBlank { null } ?: json.optString("uploader").ifBlank { null },
   entryChannels,
  )
  return PlaylistMetadata(json.optString("title", "Playlist").ifBlank { "Playlist" }, url.trim(), entries, channel)
 }
 /**
  * Pencarian YouTube untuk pencocokan track Spotify (atau sumber lain tanpa tautan video).
  * Hanya metadata ringkas (`--flat-playlist`): unduhan tetap memakai URL video yang dipilih pengguna.
  */
 suspend fun search(context: Context, query: String, id: String, limit: Int = 5, onPhase: (String) -> Unit = {}): List<VideoMetadata> {
  val trimmed = query.replace(Regex("\\s+"), " ").trim().take(200)
  require(trimmed.isNotEmpty()) { "Kata kunci pencarian kosong." }
  onPhase(InspectionPhase.PREPARING_ENGINE)
  initialize(context)
  onPhase(InspectionPhase.SEARCHING)
  val request = YoutubeDLRequest("ytsearch$limit:$trimmed").addOption("--ignore-config").addOption("--no-colors")
   .addOption("--flat-playlist").addOption("--skip-download").addOption("--dump-single-json").addOption("--socket-timeout", 20)
  val entries = JSONObject(execute(request,id)).optJSONArray("entries") ?: return emptyList()
  return (0 until entries.length()).mapNotNull { index ->
   val entry = entries.optJSONObject(index) ?: return@mapNotNull null
   val videoId = entry.optString("id").takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) } ?: return@mapNotNull null
   SourceMetadata.parse(entry, "https://www.youtube.com/watch?v=$videoId")
  }
 }
 suspend fun download(context: Context, job: DownloadJob, directory: File, skipSubtitles: Boolean = false, callback: (DownloadProgress)->Unit, resumeNotice: (String)->Unit = {}, processingNotice: (String)->Unit = {}): File {
  initialize(context)
  val options = DownloadOptions(job.url,job.mode,job.format,job.quality,job.bitrate,job.subtitles,job.embedSubs,job.thumbnail,job.embedThumb)
  Selection.validate(options)
 val request = YoutubeDLRequest(job.url).addOption("--ignore-config").addOption("--no-colors").addOption("--no-playlist") // retries mengikuti default yt-dlp (10) — nilai 3 lama membuat jaringan sedikit goyang langsung menggagalkan job (P1 resume).
 .addOption("--newline").addOption("--socket-timeout",30).addOption("--retries",10).addOption("--fragment-retries",10).addOption("-f",Selection.selector(options)).addOption("-o",File(directory,"media.%(ext)s").absolutePath)
 request.addOption("--progress")
 request.addCommands(listOf("--progress-template", DownloadProgress.DOWNLOAD_TEMPLATE, "--progress-template", DownloadProgress.PROCESS_TEMPLATE))
 // Explicit meta_* also suppresses yt-dlp's uploader fallback and original track-title precedence.
 if (job.mode == MediaMode.AUDIO && job.autoTag) {
  request.addOption("--add-metadata")
  request.addCommands(MetadataTags.arguments(job.songIdentity()))
 }
 request.addOption("--continue").addOption("--tubeflow-validate-resume")
 if (job.mode == MediaMode.AUDIO) request.addOption("--tubeflow-optional-audio")
 val embedCover = job.embedThumb && Selection.coverSupported(job.mode, job.format)
 if (job.mode == MediaMode.AUDIO) {
   request.addOption("-x").addOption("--audio-format",Selection.audioTargetFormat(job.format, job.bitrate))
   if (job.bitrate > 0 && job.format !in setOf("flac","wav","vorbis")) request.addOption("--audio-quality","${job.bitrate}K")
  } else if (job.format != "auto") request.addOption("--merge-output-format",job.format).addOption("--remux-video",job.format)
  if (job.subtitles.isNotEmpty() && !skipSubtitles) {
   // "asli" = bahasa asli video; kode aslinya (mis. "en-orig") diresolve per video biar bukan terjemahan mesin.
   var subLangs = job.subtitles
   if (subLangs == "asli") subLangs = SourceMetadata.originalSubtitleLang(
     dumpInfo(context, job.url, job.id), java.util.Locale.getDefault().language,
    ) ?: ""
   if (subLangs.isNotEmpty()) {
    request.addOption("--write-subs").addOption("--write-auto-subs").addCommands(listOf("--sub-langs",subLangs)).addOption("--sub-format","srt/best")
    if (job.embedSubs) request.addOption("--embed-subs")
   }
  }
 // Source previews use CoverCache separately. Download/embed artwork only when this job requests it.
 if (job.thumbnail || job.embedThumb) request.addOption("--write-thumbnail").addOption("--convert-thumbnails", "jpg").addOption("--tubeflow-compress-cover")
 if (embedCover || (job.embedThumb && job.mode == MediaMode.VIDEO)) request.addOption("--embed-thumbnail")
  val manifest=File(directory,"result.txt")
  request.addCommands(listOf("--print-to-file","after_move:filepath",manifest.absolutePath))
  execute(request,job.id,cookieFile(context,job.url)) { _, _, line ->
   if (line.contains("[tubeflow-resume]")) resumeNotice(line.substringAfter("[tubeflow-resume]").trim())
   if (line.contains("[tubeflow-processing]")) processingNotice(line.substringAfter("[tubeflow-processing]").trim())
   DownloadProgress.parse(line)?.let(callback)
  }
  val sidecarExtensions = setOf("srt","vtt","ass","lrc","ttml","srv1","srv2","srv3","jpg","jpeg","png","webp","gif")
  val output=File(manifest.readLines().filter { line -> line.isNotBlank() && line.substringAfterLast('.').lowercase() !in sidecarExtensions }.lastOrNull() ?: error("Hasil unduhan tidak ditemukan.")).canonicalFile
  require(output.parentFile == directory.canonicalFile && output.isFile && output.name.startsWith("media.")) { "Jalur hasil unduhan tidak valid." }
  return output
 }
}
