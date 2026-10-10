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
 fun isRunning(id: String) = activeThreads[id]?.thread?.isAlive == true
 suspend fun initialize(context: Context) = lock.withLock {
  withContext(Dispatchers.IO) {
   try {
    installBundledEngine(context)
    YoutubeDL.getInstance().init(context)
    FFmpeg.getInstance().init(context)
   } catch (failure: LinkageError) {
    throw IllegalStateException("Mesin download gagal disiapkan. Pasang pembaruan aplikasi lalu coba lagi.", failure)
   }
  }
 }
 @Volatile private var assetInstalled = false
 private fun installBundledEngine(context: Context) {
  if(assetInstalled) return
  val manifest=JSONObject(context.assets.open("engine.json").bufferedReader().use { it.readText() })
  val expected=manifest.getString("sha256").lowercase()
  require(expected.matches(Regex("[a-f0-9]{64}"))) { "Manifest mesin tidak valid." }
  fun digest(file: File): String {
   val sha=java.security.MessageDigest.getInstance("SHA-256")
   file.inputStream().use { input -> val buffer=ByteArray(65536); while(true) { val n=input.read(buffer); if(n<0) break; sha.update(buffer,0,n) } }
   return sha.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
  }
  val target=File(context.noBackupFilesDir,"youtubedl-android/yt-dlp/yt-dlp-original")
  target.parentFile!!.mkdirs()
  if(!target.isFile || digest(target) != expected) {
   val temporary=File(target.parentFile,"yt-dlp.tmp")
   try {
    context.assets.open("yt-dlp").use { input -> java.io.FileOutputStream(temporary).use { output -> input.copyTo(output); output.fd.sync() } }
    require(digest(temporary)==expected) { "Verifikasi mesin media gagal." }
    android.system.Os.rename(temporary.absolutePath,target.absolutePath)
   } finally { temporary.delete() }
  }
  val entry=File(target.parentFile,"yt-dlp")
  val bootstrap=context.assets.open("engine-bootstrap.py").use { it.readBytes() }
  if(!entry.isFile || !entry.readBytes().contentEquals(bootstrap)) {
   val temporary=File(entry.parentFile,"bootstrap.tmp")
   try { java.io.FileOutputStream(temporary).use { it.write(bootstrap); it.fd.sync() }; android.system.Os.rename(temporary.absolutePath,entry.absolutePath) }
   finally { temporary.delete() }
  }
  processDirectory=File(context.noBackupFilesDir,"engine-processes").apply { mkdirs() }
  processDirectory.listFiles()?.forEach { record ->
   val token=record.name.removeSuffix(".json")
   if(record.extension == "json" && token.matches(Regex("[a-f0-9]{32}"))) signalOwned(record,token)
   record.delete()
  }
  assetInstalled=true
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
 private suspend fun execute(request: YoutubeDLRequest, id: String, callback: ((Float,Long,String)->Unit)? = null): String {
  var executionThread: Thread? = null
  val token=java.util.UUID.randomUUID().toString().replace("-", "")
  val controlFile=File(processDirectory,"$token.json")
  request.addCommands(listOf("--tubeflow-process-file",controlFile.absolutePath,"--tubeflow-process-token",token))
  var execution: Execution? = null
  try { return suspendCancellableCoroutine { continuation ->
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
   if (!currentCoroutineContext().isActive) withContext(NonCancellable + Dispatchers.IO) {
    execution?.let(::cancelExecution)
    executionThread?.join(5000)
   }
  }
 }
 private suspend fun dumpInfo(context: Context, url: String, id: String): JSONObject {
  initialize(context)
  val request = YoutubeDLRequest(url.trim()).addOption("--ignore-config").addOption("--no-colors").addOption("--no-playlist").addOption("--skip-download").addOption("--dump-single-json").addOption("--socket-timeout", 20)
  return JSONObject(execute(request,id))
 }
 /** Bahasa yang benar-benar disediakan video: track manual + bahasa asli auto-caption (bukan terjemahan mesin). */
 private fun genuineSubtitleLangs(json: JSONObject): List<String> {
  val manual = json.optJSONObject("subtitles")?.keys()?.asSequence()?.toList().orEmpty()
  val auto = json.optJSONObject("automatic_captions")?.keys()?.asSequence()?.toList().orEmpty()
  val original = auto.firstOrNull { it.endsWith("-orig") }?.removeSuffix("-orig")
  return (manual + listOfNotNull(original)).distinct().filterNot { it == "live_chat" || it == "orig" }.sorted()
 }
 /** Kode subtitle "asli": subtitle manual (dari kreator, paling akurat) didahulukan, baru auto-caption bahasa asli (bukan terjemahan mesin). */
 private fun originalSubtitleRequest(json: JSONObject): String? {
  json.optJSONObject("subtitles")?.keys()?.asSequence()?.toList().orEmpty().firstOrNull { it != "live_chat" }?.let { return it }
  return json.optJSONObject("automatic_captions")?.keys()?.asSequence()?.toList().orEmpty().firstOrNull { it.endsWith("-orig") }
 }
 suspend fun inspect(context: Context, url: String, id: String): VideoMetadata {
  require(Selection.validUrl(url)) { "Gunakan tautan video YouTube yang valid." }
  val json = dumpInfo(context, url, id)
  val langs = genuineSubtitleLangs(json).take(8)
  return VideoMetadata(json.optString("title", "Video"), json.optString("uploader"), json.optLong("duration"), json.optString("thumbnail"), url.trim(), langs)
 }
 suspend fun inspectPlaylist(context: Context, url: String, id: String): PlaylistMetadata {
  require(Selection.validPlaylistUrl(url)) { "Gunakan tautan playlist YouTube yang valid." }
  initialize(context)
  val request = YoutubeDLRequest(url.trim()).addOption("--ignore-config").addOption("--no-colors").addOption("--yes-playlist").addOption("--flat-playlist").addOption("--skip-download").addOption("--dump-single-json").addOption("--playlist-end", 500).addOption("--socket-timeout", 20)
  if (Selection.isMix(url)) request.addOption("--tubeflow-mix-snapshot")
  val json = JSONObject(execute(request,id))
  val array = json.optJSONArray("entries") ?: JSONArray()
  val entries = (0 until array.length()).mapNotNull { index ->
   val entry = array.optJSONObject(index) ?: return@mapNotNull null
   val videoId = entry.optString("id")
   if (videoId.isBlank()) return@mapNotNull null
   PlaylistEntry(videoId, entry.optString("title", "Video").ifBlank { "Video" }, entry.optLong("duration"), "https://www.youtube.com/watch?v=$videoId")
  }
  return PlaylistMetadata(json.optString("title", "Playlist").ifBlank { "Playlist" }, url.trim(), entries)
 }
 suspend fun download(context: Context, job: DownloadJob, directory: File, skipSubtitles: Boolean = false, callback: (DownloadProgress)->Unit): File {
  initialize(context)
  val options = DownloadOptions(job.url,job.mode,job.format,job.quality,job.bitrate,job.subtitles,job.embedSubs,job.thumbnail,job.embedThumb)
  Selection.validate(options)
 val request = YoutubeDLRequest(job.url).addOption("--ignore-config").addOption("--no-colors").addOption("--no-playlist").addOption("--newline").addOption("--socket-timeout",30).addOption("--retries",3).addOption("--fragment-retries",3).addOption("--add-metadata").addOption("-f",Selection.selector(options)).addOption("-o",File(directory,"media.%(ext)s").absolutePath)
 request.addOption("--progress")
 request.addCommands(listOf("--progress-template", DownloadProgress.DOWNLOAD_TEMPLATE, "--progress-template", DownloadProgress.PROCESS_TEMPLATE))
 // Tag judul/artis diambil dari nama file "Artis - Judul" (konvensi auto-tagger zdt-music-toolkit), bukan judul mentah YouTube.
 // --replace-in-metadata dipakai karena nilainya literal (aman untuk kolon, tanda kurung, %); tanpa " - " hanya judul yang diset.
 val neat = job.title.trim()
 fun literal(value: String) = value.replace("\\", "").replace('\n', ' ').replace('\r', ' ').trim()
 literal(if (" - " in neat) neat.substringAfter(" - ") else neat).takeIf { it.isNotEmpty() }?.let { value ->
  request.addCommands(listOf("--replace-in-metadata", "title", "(?s)^.*$", value))
 }
 literal(if (" - " in neat) neat.substringBefore(" - ") else "").takeIf { it.isNotEmpty() }?.let { value ->
  request.addCommands(listOf("--replace-in-metadata", "artist,uploader", "(?s)^.*$", value))
 }
 val embedCover = job.embedThumb && Selection.coverSupported(job.mode, job.format)
 if (job.mode == MediaMode.AUDIO) {
   request.addOption("-x").addOption("--audio-format",if(job.format == "auto") { if(job.bitrate > 0 || embedCover) "mp3" else "best" } else job.format)
   if (job.bitrate > 0 && job.format !in setOf("flac","wav","vorbis")) request.addOption("--audio-quality","${job.bitrate}K")
  } else if (job.format != "auto") request.addOption("--merge-output-format",job.format).addOption("--remux-video",job.format)
  if (job.subtitles.isNotEmpty() && !skipSubtitles) {
   // "asli" = bahasa asli video; kode aslinya (mis. "en-orig") diresolve per video biar bukan terjemahan mesin.
   var subLangs = job.subtitles
   if (subLangs == "asli") subLangs = originalSubtitleRequest(dumpInfo(context, job.url, job.id)) ?: ""
   if (subLangs.isNotEmpty()) {
    request.addOption("--write-subs").addOption("--write-auto-subs").addCommands(listOf("--sub-langs",subLangs)).addOption("--sub-format","srt/best")
    if (job.embedSubs) request.addOption("--embed-subs")
   }
  }
 if (job.thumbnail || embedCover || (job.embedThumb && job.mode == MediaMode.VIDEO)) {
  request.addOption("--write-thumbnail").addOption("--convert-thumbnails", "jpg").addOption("--tubeflow-compress-cover")
 }
 if (embedCover || (job.embedThumb && job.mode == MediaMode.VIDEO)) request.addOption("--embed-thumbnail")
  val manifest=File(directory,"result.txt")
  request.addCommands(listOf("--print-to-file","after_move:filepath",manifest.absolutePath))
  execute(request,job.id) { _, _, line -> DownloadProgress.parse(line)?.let(callback) }
  val sidecarExtensions = setOf("srt","vtt","ass","lrc","ttml","srv1","srv2","srv3","jpg","jpeg","png","webp","gif")
  val output=File(manifest.readLines().filter { line -> line.isNotBlank() && line.substringAfterLast('.').lowercase() !in sidecarExtensions }.lastOrNull() ?: error("Hasil unduhan tidak ditemukan.")).canonicalFile
  require(output.parentFile == directory.canonicalFile && output.isFile && output.name.startsWith("media.")) { "Jalur hasil unduhan tidak valid." }
  return output
 }
}
