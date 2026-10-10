package id.local.tubeflow.core

import java.net.URI
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

enum class MediaMode { VIDEO, AUDIO }
enum class JobState { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }
data class DownloadOptions(
 val url: String,
 val mode: MediaMode = MediaMode.VIDEO,
 val format: String = "auto",
 val quality: Int = 0,
 val bitrate: Int = 0,
 val subtitles: String = "",
 val embedSubs: Boolean = false,
 val thumbnail: Boolean = false,
 val embedThumb: Boolean = false,
)
data class VideoMetadata(val title: String, val uploader: String, val durationSeconds: Long, val thumbnail: String, val url: String, val subtitleLangs: List<String> = emptyList())
data class PlaylistEntry(val id: String, val title: String, val durationSeconds: Long, val url: String)
data class PlaylistMetadata(val title: String, val url: String, val entries: List<PlaylistEntry>)
@Entity(tableName = "downloads")
data class DownloadJob(
 @PrimaryKey val id: String,
 val url: String,
 val title: String,
 val mode: MediaMode,
 val format: String,
 val quality: Int,
 val bitrate: Int,
 val state: JobState = JobState.QUEUED,
 val progress: Float = 0f,
 @ColumnInfo(defaultValue = "'PREPARING'") val phase: String = DownloadPhase.PREPARING,
 val speed: String = "",
 val eta: String = "",
 val outputUri: String? = null,
 val pendingUri: String? = null,
 val error: String? = null,
 @ColumnInfo(defaultValue = "''") val subtitles: String = "",
 @ColumnInfo(defaultValue = "0") val embedSubs: Boolean = false,
 @ColumnInfo(defaultValue = "0") val thumbnail: Boolean = false,
 @ColumnInfo(defaultValue = "0") val embedThumb: Boolean = false,
 val sidecarUris: String? = null,
 /** Token eksekusi: naik saat resume/revive sehingga worker lama tak bisa menimpa job yang sudah dilanjutkan (BUG-002). */
 @ColumnInfo(defaultValue = "0") val generation: Int = 0,
 /** Durasi video (detik) saat diinspeksi — dipakai pencocokan lirik LRCLIB (BUG-013). */
 @ColumnInfo(defaultValue = "0") val durationSeconds: Long = 0,
 /** Status lirik terakhir: kosong/"tersimpan"/"tidak_ditemukan"/"instrumental"/"jaringan"/"gagal_simpan" (BUG-014). */
 @ColumnInfo(defaultValue = "''") val lyricStatus: String = "",
 /** URI sidecar yang sedang ditulis; dibersihkan recovery bila proses mati di tengah jalan (BUG-008). */
 val sidecarPending: String? = null,
 /** Subfolder tujuan per playlist/Mix (mis. "mix-denny-caknan"); kosong = langsung di folder utama. */
 @ColumnInfo(defaultValue = "''") val subfolder: String = "",
 val createdAt: Long = System.currentTimeMillis(),
 /** Waktu publikasi benar-benar selesai (detik ke ms) — label "Selesai ·" Riwayat memakai ini, bukan waktu antre (BUG-023). */
 @ColumnInfo(defaultValue = "0") val completedAt: Long = 0,
)

/** Semua opsi keluaran identik — dasar cek duplikat antrean maupun hasil selesai (BUG-005). */
fun DownloadJob.matchesOutput(url: String, options: DownloadOptions): Boolean =
 Selection.videoId(url)?.let { it == Selection.videoId(this.url) } == true &&
  mode == options.mode && format == options.format && quality == options.quality &&
  bitrate == options.bitrate && subtitles == options.subtitles && embedSubs == options.embedSubs &&
  thumbnail == options.thumbnail && embedThumb == options.embedThumb
class Converters {
 @TypeConverter fun mode(value: String) = MediaMode.valueOf(value)
 @TypeConverter fun mode(value: MediaMode) = value.name
 @TypeConverter fun state(value: String) = JobState.valueOf(value)
 @TypeConverter fun state(value: JobState) = value.name
}
@Dao interface DownloadDao {
 @Query("SELECT * FROM downloads ORDER BY createdAt DESC") fun observe(): Flow<List<DownloadJob>>
 @Query("SELECT * FROM downloads WHERE id = :id") suspend fun get(id: String): DownloadJob?
 @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(job: DownloadJob)
 @Query("UPDATE downloads SET title = :title WHERE id = :id AND generation = :gen AND state IN ('QUEUED','RUNNING')") suspend fun title(id: String, gen: Int, title: String): Int
 @Query("UPDATE downloads SET state = 'FAILED', error = :message, pendingUri = NULL WHERE id = :id AND state IN ('RUNNING','QUEUED')") suspend fun fail(id: String, message: String): Int
 /** Versi milik worker: hanya mengenai baris dengan generasi eksekusi ini — worker lama tak bisa menimpa job yang dilanjutkan. */
 @Query("UPDATE downloads SET state = 'FAILED', error = :message, pendingUri = NULL WHERE id = :id AND generation = :gen AND state IN ('RUNNING','QUEUED')") suspend fun failOwned(id: String, gen: Int, message: String): Int
 @Query("UPDATE downloads SET pendingUri = NULL WHERE id = :id") suspend fun clearPending(id: String)
 /** Versi milik worker untuk cleanup akhir: worker lama tak boleh menghapus jejak pending milik generasi baru (BUG-002/R1). */
 @Query("UPDATE downloads SET pendingUri = NULL WHERE id = :id AND generation = :gen") suspend fun clearPendingOwned(id: String, gen: Int): Int
 @Query("UPDATE downloads SET pendingUri = :uri WHERE id = :id AND generation = :gen AND state = 'RUNNING'") suspend fun pending(id: String, gen: Int, uri: String?): Int
 /** Durasi hasil inspeksi worker — gerbang durasi lirik butuh durasi nyata, bukan 0 (BUG-013/R3). */
 @Query("UPDATE downloads SET durationSeconds = :seconds WHERE id = :id AND generation = :gen AND state IN ('QUEUED','RUNNING')") suspend fun duration(id: String, gen: Int, seconds: Long): Int
 @Query("UPDATE downloads SET state = 'COMPLETED', progress = 100, speed = '', eta = '', outputUri = :uri, completedAt = :now, pendingUri = NULL WHERE id = :id AND generation = :gen AND state = 'RUNNING'") suspend fun finish(id: String, gen: Int, uri: String, now: Long): Int
 @Query("UPDATE downloads SET state = :state, progress = :progress, phase = :phase, speed = :speed, eta = :eta WHERE id = :id AND generation = :gen AND state IN ('QUEUED','RUNNING')") suspend fun progress(id: String, gen: Int, state: JobState, progress: Float, speed: String, eta: String, phase: String = DownloadPhase.PREPARING)
 @Query("UPDATE downloads SET state = 'QUEUED' WHERE id = :id AND generation = :gen AND state = 'RUNNING'") suspend fun interrupted(id: String, gen: Int): Int
 @Query("UPDATE downloads SET state = 'PAUSED', speed = '', eta = '' WHERE id = :id AND state IN ('QUEUED','RUNNING')") suspend fun pause(id: String): Int
 @Query("UPDATE downloads SET state = 'PAUSED', speed = '', eta = '' WHERE state IN ('QUEUED','RUNNING')") suspend fun pauseAll(): Int
 @Query("UPDATE downloads SET state = 'QUEUED', progress = 0, phase = 'PREPARING', speed = '', eta = '', error = NULL, generation = generation + 1 WHERE id = :id AND state = 'PAUSED'") suspend fun resume(id: String): Int
 @Query("UPDATE downloads SET state = 'CANCELLED' WHERE id = :id AND state IN ('QUEUED','RUNNING','PAUSED')") suspend fun cancel(id: String): Int
 /** Versi milik worker untuk handleStop; lihat [failOwned]. */
 @Query("UPDATE downloads SET state = 'CANCELLED' WHERE id = :id AND generation = :gen AND state IN ('QUEUED','RUNNING','PAUSED')") suspend fun cancelOwned(id: String, gen: Int): Int
 @Query("DELETE FROM downloads WHERE id = :id AND state IN ('COMPLETED','FAILED','CANCELLED')") suspend fun remove(id: String): Int
 @Query("DELETE FROM downloads WHERE state = 'COMPLETED'") suspend fun clearCompleted(): Int
 @Query("DELETE FROM downloads WHERE state IN ('FAILED','CANCELLED')") suspend fun clearUnsuccessful(): Int
 @Query("UPDATE downloads SET state = 'QUEUED', progress = 0, phase = 'PREPARING', speed = '', eta = '', error = NULL, outputUri = NULL, pendingUri = NULL, sidecarUris = NULL, lyricStatus = '', generation = generation + 1, title = :title, mode = :mode, format = :format, quality = :quality, bitrate = :bitrate, subtitles = :subtitles, embedSubs = :embedSubs, thumbnail = :thumbnail, embedThumb = :embedThumb, createdAt = :now WHERE id = :id AND state IN ('FAILED','CANCELLED')") suspend fun revive(id: String, title: String, mode: MediaMode, format: String, quality: Int, bitrate: Int, subtitles: String, embedSubs: Boolean, thumbnail: Boolean, embedThumb: Boolean, now: Long): Int
 @Query("UPDATE downloads SET sidecarUris = :uris WHERE id = :id") suspend fun sidecars(id: String, uris: String?)
 @Query("UPDATE downloads SET sidecarPending = :uris WHERE id = :id") suspend fun sidecarPending(id: String, uris: String?): Int
 /** Satu transaksi per sidecar: baris terbit langsung tercatat dan jurnal dilepas — jendela proses mati tertutup (BUG-008/R2). */
 @Query("UPDATE downloads SET sidecarUris = CASE WHEN sidecarUris IS NULL OR sidecarUris = '' THEN :line ELSE sidecarUris || char(10) || :line END, sidecarPending = :pending WHERE id = :id") suspend fun commitSidecar(id: String, line: String, pending: String?): Int
 /** Status lirik terakhir untuk ditampilkan di Riwayat (BUG-014). */
 @Query("UPDATE downloads SET lyricStatus = :status WHERE id = :id") suspend fun lyricStatus(id: String, status: String)
 /**
  * Antre ulang baris SELESAI memakai opsi yang sama ("Unduh lagi"): hasil lama sudah dihapus pemanggil,
  * jadi baris dipakai ulang alih-alih membuat duplikat (BUG-026). Gagal bila statusnya bukan COMPLETED.
  */
 @Query("UPDATE downloads SET state = 'QUEUED', progress = 0, phase = 'PREPARING', speed = '', eta = '', error = NULL, outputUri = NULL, pendingUri = NULL, sidecarUris = NULL, lyricStatus = '', completedAt = 0, generation = generation + 1, createdAt = :now WHERE id = :id AND state = 'COMPLETED'") suspend fun requeueCompleted(id: String, now: Long): Int
}
@Database(entities = [DownloadJob::class], version = 6, exportSchema = true)
@TypeConverters(Converters::class)
abstract class DownloadDatabase : RoomDatabase() {
 abstract fun downloads(): DownloadDao
 companion object {
  private val MIGRATION_1_2 = object : Migration(1, 2) {
   override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE downloads ADD COLUMN subtitles TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE downloads ADD COLUMN embedSubs INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE downloads ADD COLUMN thumbnail INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE downloads ADD COLUMN embedThumb INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE downloads ADD COLUMN sidecarUris TEXT")
   }
  }
  private val MIGRATION_2_3 = object : Migration(2, 3) {
   override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE downloads ADD COLUMN generation INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE downloads ADD COLUMN durationSeconds INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE downloads ADD COLUMN lyricStatus TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE downloads ADD COLUMN sidecarPending TEXT")
   }
  }
  private val MIGRATION_3_4 = object : Migration(3, 4) {
   override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE downloads ADD COLUMN phase TEXT NOT NULL DEFAULT 'PREPARING'")
   }
  }
  private val MIGRATION_4_5 = object : Migration(4, 5) {
   override fun migrate(db: SupportSQLiteDatabase) {
    // completedAt untuk baris lama tetap 0 — UI jatuh ke createdAt sampai baris diunduh ulang (BUG-023).
    db.execSQL("ALTER TABLE downloads ADD COLUMN completedAt INTEGER NOT NULL DEFAULT 0")
   }
  }
  private val MIGRATION_5_6 = object : Migration(5, 6) {
   override fun migrate(db: SupportSQLiteDatabase) {
    // Subfolder tujuan per playlist/Mix — baris lama tetap '' (langsung di folder utama, perilaku lama).
    db.execSQL("ALTER TABLE downloads ADD COLUMN subfolder TEXT NOT NULL DEFAULT ''")
   }
  }
  @Volatile private var instance: DownloadDatabase? = null
  fun get(context: android.content.Context) = instance ?: synchronized(this) { instance ?: Room.databaseBuilder(context.applicationContext, DownloadDatabase::class.java, "downloads.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { instance = it } }
 }
}
object Selection {
 val videoFormats = setOf("auto", "mp4", "mkv", "webm", "mov", "avi")
 val audioFormats = setOf("auto", "mp3", "m4a", "opus", "flac", "wav", "aac", "vorbis")
 val subtitleChoices = setOf("", "asli", "id", "en", ".*")
 /** Audio formats whose cover art can be embedded without the missing `mutagen` package (ffmpeg fallback path). */
 val coverFormats = setOf("auto", "mp3", "m4a")
 fun coverSupported(mode: MediaMode, format: String): Boolean = mode == MediaMode.AUDIO && format in coverFormats
 /** Safe MediaStore folder name (single segment under Downloads/); null when unusable. */
 fun safeFolder(raw: String): String? {
  val name = raw.trim()
  if (name.isEmpty() || name.length > 40) return null
  if (name.endsWith(".")) return null
  return if (name.matches(Regex("[\\p{L}\\p{N}][\\p{L}\\p{N} ._()-]*"))) name else null
 }
 /**
 * Nama subfolder otomatis dari judul playlist/Mix: semua yang bukan huruf/angka jadi pemisah "-"
 * (mis. "Mix - DENNY CAKNAN" → "mix-denny-caknan"). Hasil SELALU lolos [safeFolder] — satu segmen,
 * maks 40 karakter, huruf/angka di awal. Null bila judul tidak menghasilkan nama yang aman
 * (mis. hanya simbol) — unduhan langsung di folder utama seperti sebelumnya.
 */
 fun playlistFolder(title: String): String? {
  val slug = title.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').take(40).trimEnd('-')
  return slug.takeIf { it.isNotEmpty() }?.let { safeFolder(it) }
 }
 private val hosts = setOf("youtube.com","www.youtube.com","m.youtube.com","music.youtube.com","youtu.be")
 fun validate(options: DownloadOptions) {
  require(validUrl(options.url)) { "Gunakan tautan video YouTube yang valid." }
  require(options.format in if (options.mode == MediaMode.VIDEO) videoFormats else audioFormats) { "Format tidak didukung." }
  require(options.quality in setOf(0,360,480,720,1080,1440,2160,4320))
  require(options.bitrate in setOf(0,64,128,192,256,320))
  require(options.subtitles in subtitleChoices) { "Pilihan subtitle tidak didukung." }
  require(!options.embedSubs || options.mode == MediaMode.VIDEO) { "Sematkan subtitle hanya untuk video." }
 }
 fun validUrl(raw: String): Boolean = runCatching {
  val uri = URI(raw.trim()); val host = uri.host?.lowercase() ?: return false
  uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443) && host in hosts && (if (host == "youtu.be") uri.path.length > 1 else uri.path == "/watch" && uri.rawQuery?.split('&')?.any { it.startsWith("v=") && it.length > 2 } == true || uri.path.startsWith("/shorts/") || uri.path.startsWith("/live/"))
 }.getOrDefault(false)
 /** Identitas sumber, termasuk URL riwayat lama; parameter playlist/waktu tidak membedakan video. */
 fun videoId(raw: String): String? {
  if (!validUrl(raw)) return null
  return runCatching {
   val uri = URI(raw.trim())
   val id = when {
    uri.host.equals("youtu.be", ignoreCase = true) -> uri.path.removePrefix("/").trimEnd('/')
    uri.path == "/watch" -> uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("v=") }?.substringAfter('=')
    else -> uri.path.removePrefix("/shorts/").removePrefix("/live/").trimEnd('/')
   }
   val decoded = id?.let { java.net.URLDecoder.decode(it, "UTF-8") }
   decoded?.takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) }
  }.getOrNull()
 }
 fun isMix(raw: String): Boolean = validPlaylistUrl(raw) && listParam(raw)?.startsWith("RD") == true
 /**
  * Seed tersembunyi di ID Mix: YouTube membentuknya "RD"/"RDMM" + videoId (11 karakter).
  * Tautan Bagikan dari autoplay menghasilkan playlist?list=RD… TANPA seed, dan YouTube menolak
  * endpoint playlist polos untuk Mix ("This playlist type is unviewable") — seed dipulihkan dari
  * ID-nya sendiri lalu dibuka lewat jalur watch+list yang sudah ditangani (BUG-018/BUG-027).
  */
 private fun mixSeed(id: String): String? {
  val rest = id.removePrefix("RD")
  val seed = when {
   rest.length == 11 -> rest
   rest.startsWith("MM") && rest.length == 13 -> rest.removePrefix("MM")
   else -> null
  }
  return seed?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
 }
 /** Mix mempertahankan seed; playlist biasa dinormalkan ke halaman playlist. */
 fun playlistInspectionUrl(raw: String): String? {
  val canonical = canonicalPlaylistUrl(raw) ?: return null
  val id = listParam(raw)
  // Seed datang dari URL (watch/shorts/youtu.be + list) atau dipulihkan dari ID Mix (BUG-027).
  val seed = videoId(raw) ?: id?.let { if (it.startsWith("RD")) mixSeed(it) else null }
  return if (isMix(raw) && seed != null)
   "https://www.youtube.com/watch?v=$seed&list=$id&start_radio=1"
  else canonical
 }
 /** Pure playlist page: https://www.youtube.com/playlist?list=… atau tautan video (watch/shorts/live/youtu.be) yang membawa param list (BUG-007). */
 fun validPlaylistUrl(raw: String): Boolean = runCatching {
  val uri = URI(raw.trim()); val host = uri.host?.lowercase() ?: return false
  if (!(uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443) && host in hosts)) return false
  if (listParam(raw) == null) return false
  val path = uri.path ?: return false
  path == "/playlist" || path == "/watch" || path.startsWith("/shorts/") || path.startsWith("/live/") || (host == "youtu.be" && path.length > 1)
 }.getOrDefault(false)
 /** URL kanonis https://www.youtube.com/playlist?list=… dari tautan YouTube mana pun yang membawa param list; null bila tidak ada / bukan host YouTube. */
 fun canonicalPlaylistUrl(raw: String): String? {
  if (!validPlaylistUrl(raw)) return null
  val id = listParam(raw) ?: return null
  if (!id.matches(Regex("[A-Za-z0-9_-]+"))) return null
  return "https://www.youtube.com/playlist?list=$id"
 }
 /** Raw `list=` playlist id from any YouTube link, or null when absent. */
 fun listParam(raw: String): String? = runCatching {
  URI(raw.trim()).rawQuery?.split('&')?.firstOrNull { it.startsWith("list=") }?.substringAfter('=')?.takeIf { it.length > 5 }
 }.getOrNull()
 /** A video link that also carries a playlist parameter (watch?v=…&list=…). */
 fun hasListParam(raw: String): Boolean = validUrl(raw) && runCatching {
  URI(raw.trim()).rawQuery?.split('&')?.any { it.startsWith("list=") && it.length > 5 } == true
 }.getOrDefault(false)
 fun selector(options: DownloadOptions): String {
  if (options.mode == MediaMode.AUDIO) return if(options.bitrate > 0) when(options.format) {
   // Wadah sumber sengaja BERBEDA dari target: yt-dlp hanya me-remux (copy) bila codec sudah cocok dan
   // --audio-quality diabaikan — lintas wadah memaksa re-encode sesuai bitrate pilihan. Fallback mencegah
   // kegagalan total bila track ext terpilih tidak tersedia (BUG-024).
   "m4a" -> "bestaudio[ext=webm]/bestaudio/best"
   "opus" -> "bestaudio[ext=m4a]/bestaudio/best"
   else -> "bestaudio/best"
  } else "bestaudio/best"
  val height = if (options.quality > 0) "[height<=${options.quality}]" else ""
  return when(options.format) {
   "mp4", "mov", "avi" -> "bestvideo$height[ext=mp4][vcodec^=avc1]+bestaudio[ext=m4a]/best$height[ext=mp4][vcodec^=avc1]/bestvideo$height[ext=mp4]+bestaudio[ext=m4a]/best$height[ext=mp4]"
   "webm" -> "bestvideo$height[ext=webm]+bestaudio[ext=webm]/best$height[ext=webm]"
   else -> "bestvideo$height+bestaudio/best$height"
  }
 }
}
