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
/**
 * Satu resolusi yang benar-benar disediakan sumber. [height] adalah tinggi piksel nyata untuk filter
 * `height<=?` yt-dlp, sedangkan [tier] adalah label "p" standar yang dikenal pengguna — video
 * 1920×818 (2.35:1) tetap tampil dan dipilih sebagai 1080p, bukan 818p.
 */
data class VideoResolution(val height: Int, val tier: Int)

data class VideoMetadata(val title: String, val uploader: String, val durationSeconds: Long, val thumbnail: String, val url: String, val subtitleLangs: List<String> = emptyList(), /** Nama artis bila platform menyediakannya (metadata asli, bukan tebakan channel). */ val artist: String = "", val track: String = "", val creator: String = "", val platformId: String = "", val sourceId: String = "", val extractor: String = "", val artistEvidence: String = "", /** Resolusi yang benar-benar disediakan sumber, urut menurun; kosong = sumber tidak melaporkan tinggi. */ val resolutions: List<VideoResolution> = emptyList())
data class PlaylistEntry(val id: String, val title: String, val durationSeconds: Long, val url: String, val uploader: String = "", val artist: String = "", val track: String = "", val thumbnail: String = "")
data class PlaylistMetadata(val title: String, val url: String, val entries: List<PlaylistEntry>, val channel: String = "", val folderName: String = "", val seedMetadata: VideoMetadata? = null)
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
 val sidecarPending: String? = null,    /** Subfolder tujuan per playlist/Mix (mis. "mix-denny-caknan"); kosong = langsung di folder utama. */
    @ColumnInfo(defaultValue = "''") val subfolder: String = "",
    /**
     * Nama artis hasil identifikasi (bukti: metadata `artist` atau nama channel yang muncul utuh di judul).
     * Kosong bila tidak ada bukti — sistem TIDAK menebak nama artis. Dipakai konsisten oleh penamaan file,
     * pencarian lirik, dan auto-folder (P1/P2).
     */
    @ColumnInfo(defaultValue = "''") val artist: String = "",
    /**
     * Jalur lokal sampul hasil cache (noBackupFilesDir/covers/<id>.jpg) — diisi worker dari thumbnail
     * unduhan, dipakai kartu Antrean/Riwayat. Kosong = fallback ikon (P2 thumbnail).
     */
    @ColumnInfo(defaultValue = "''") val coverPath: String = "",
    @ColumnInfo(defaultValue = "''") val rawTitle: String = "",
    @ColumnInfo(defaultValue = "''") val trackTitle: String = "",
    @ColumnInfo(defaultValue = "''") val creator: String = "",
    @ColumnInfo(defaultValue = "''") val remixer: String = "",
    @ColumnInfo(defaultValue = "''") val versionTag: String = "",
    @ColumnInfo(defaultValue = "''") val artistEvidence: String = "",
    @ColumnInfo(defaultValue = "''") val platformId: String = "",
    @ColumnInfo(defaultValue = "''") val thumbnailUrl: String = "",
    @ColumnInfo(defaultValue = "''") val sourceId: String = "",
    @ColumnInfo(defaultValue = "''") val extractor: String = "",
    @ColumnInfo(defaultValue = "''") val autoFolder: String = "",
    @ColumnInfo(defaultValue = "''") val resumeNotice: String = "",
    @ColumnInfo(defaultValue = "0") val partialBytes: Long = 0,
 @ColumnInfo(defaultValue = "''") val processingNotice: String = "",
 @ColumnInfo(defaultValue = "1") val autoRename: Boolean = true,
 @ColumnInfo(defaultValue = "1") val autoTag: Boolean = true,
 @ColumnInfo(defaultValue = "1") val autoLibrary: Boolean = true,
 val createdAt: Long = System.currentTimeMillis(),
 /** Waktu publikasi benar-benar selesai (detik ke ms) — label "Selesai ·" Riwayat memakai ini, bukan waktu antre (BUG-023). */
 @ColumnInfo(defaultValue = "0") val completedAt: Long = 0,
)

val DownloadJob.sourcePlatform: SourcePlatform get() =
    if (platformId.isBlank()) SourcePlatforms.fromUrl(url) else SourcePlatforms.fromId(platformId)
fun DownloadJob.songIdentity(): SongIdentity = if (trackTitle.isNotBlank())
    SongIdentity(trackTitle, artist.takeIf { artistEvidence.isNotBlank() && it.isNotBlank() }, versionTag, creator, remixer, artistEvidence)
else TitleSplit.identify(rawTitle.ifBlank { title })

fun DownloadJob.withMetadata(meta: VideoMetadata): DownloadJob {
    val retained = meta.artist.isBlank() && !TitleSplit.isRemixTitle(meta.title) && "cover" !in LyricMatch.versionSet(meta.title) && artistEvidence.startsWith("collection_") && artist.isNotBlank() &&
        (TitleSplit.segments(meta.title).any { TitleSplit.creditKey(it) == TitleSplit.creditKey(artist) } ||
            (artistEvidence == "collection_album_credit" && rawTitle == meta.title && Selection.identity(url)?.let { it == Selection.identity(meta.url) } == true))
    val resolvedMeta = if (retained) meta.copy(artist=artist, artistEvidence=artistEvidence) else meta
    val identity = TitleSplit.identify(resolvedMeta.title, resolvedMeta.uploader, resolvedMeta.artist, resolvedMeta.track, resolvedMeta.creator, recognizeCollaboration=mode == MediaMode.AUDIO).let {
        if (resolvedMeta.artistEvidence.isBlank()) it else it.copy(evidence=resolvedMeta.artistEvidence)
    }
    return copy(rawTitle = meta.title, trackTitle = identity.title, title = TitleSplit.display(identity),
        artist = identity.artist.orEmpty(), creator = identity.creator, remixer = identity.remixer,
        versionTag = identity.versionTag, artistEvidence = identity.evidence,
        platformId = meta.platformId.ifBlank { SourcePlatforms.fromUrl(url).id },
        thumbnailUrl = meta.thumbnail.ifBlank { thumbnailUrl }, sourceId = meta.sourceId, extractor = meta.extractor,
        durationSeconds = meta.durationSeconds.takeIf { it > 0 } ?: durationSeconds,
        subfolder = automaticSubfolder(identity))
}

/** Collection snapshots survive per-song metadata refresh; legacy policies keep their behavior. */
private fun DownloadJob.automaticSubfolder(identity: SongIdentity): String = when (autoFolder) {
    "collection" -> subfolder
    "performer" -> Selection.artistFolder(identity.artist.orEmpty().ifBlank { identity.remixer }).orEmpty()
    "" -> subfolder
    else -> Selection.artistFolder(identity.artist.orEmpty(), autoFolder == "mix").orEmpty()
}

/** Existing file credits are evidence, never an uploader fallback. Keep raw source identity intact. */
fun DownloadJob.withNativeAudioMetadata(nativeTitle: String, nativeArtist: String, preferNativeTitle: Boolean): DownloadJob {
    fun valid(value: String) = value.trim().takeUnless {
        it.isBlank() || it.lowercase() in setOf("null", "unknown", "unknown artist")
    }.orEmpty()
    val identity = songIdentity()
    val performer = identity.artist.orEmpty().ifBlank { valid(nativeArtist) }
    val fileTitle = valid(nativeTitle)
    val resolved = identity.copy(
        title = if (preferNativeTitle && fileTitle.isNotBlank()) TitleSplit.identify(fileTitle, metadataArtist=performer).title else identity.title,
        artist = performer.takeIf { it.isNotBlank() },
        evidence = if (identity.artist.isNullOrBlank() && performer.isNotBlank()) "file_metadata" else identity.evidence)
    return copy(trackTitle=resolved.title, title=TitleSplit.display(resolved), artist=performer,
        artistEvidence=resolved.evidence,
        subfolder=automaticSubfolder(resolved))
}

/** Semua opsi keluaran identik — dasar cek duplikat antrean maupun hasil selesai (BUG-005). */
fun DownloadJob.matchesOutput(url: String, options: DownloadOptions): Boolean =
 Selection.identity(url)?.let { it == Selection.identity(this.url) } == true &&
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
 @Query("SELECT COUNT(*) FROM downloads WHERE state IN ('QUEUED','RUNNING')") suspend fun activeCount(): Int
 @Query("SELECT * FROM downloads ORDER BY createdAt DESC") fun observe(): Flow<List<DownloadJob>>
 @Query("SELECT * FROM downloads WHERE id = :id") suspend fun get(id: String): DownloadJob?
 @Query("UPDATE downloads SET rawTitle = :rawTitle, trackTitle = :trackTitle, title = :title, artist = :artist, creator = :creator, remixer = :remixer, versionTag = :versionTag, artistEvidence = :evidence, platformId = :platform, thumbnailUrl = :thumb, sourceId = :source, extractor = :extractor, subfolder = :folder, durationSeconds = :duration WHERE id = :id AND generation = :gen AND state IN ('QUEUED','RUNNING')")
 suspend fun metadata(id: String, gen: Int, rawTitle: String, trackTitle: String, title: String, artist: String, creator: String, remixer: String, versionTag: String, evidence: String, platform: String, thumb: String, source: String, extractor: String, folder: String, duration: Long): Int
 @Query("UPDATE downloads SET partialBytes = 0, progress = 0, phase = 'PREPARING', resumeNotice = 'Berkas sementara dihapus. Lanjutkan/Coba lagi akan mulai dari awal.' WHERE id = :id AND generation = :gen AND state IN ('PAUSED','FAILED','CANCELLED')") suspend fun discardedPartial(id: String, gen: Int): Int
 @Query("UPDATE downloads SET partialBytes = :bytes, resumeNotice = :notice WHERE id = :id AND generation = :gen AND state IN ('QUEUED','RUNNING','PAUSED','FAILED')") suspend fun resumeInfo(id: String, gen: Int, bytes: Long, notice: String): Int
 @Query("UPDATE downloads SET platformId = :platform WHERE id = :id AND platformId = ''") suspend fun platform(id: String, platform: String): Int
 @Query("UPDATE downloads SET coverPath = :path WHERE id = :id AND generation = :gen AND outputUri = :uri AND state = 'COMPLETED'") suspend fun recoveredCover(id: String, gen: Int, uri: String, path: String): Int
 @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(job: DownloadJob)
 @Query("UPDATE downloads SET title = :title WHERE id = :id AND generation = :gen AND state IN ('QUEUED','RUNNING')") suspend fun title(id: String, gen: Int, title: String): Int
 @Query("UPDATE downloads SET state = 'FAILED', error = :message, pendingUri = NULL WHERE id = :id AND state IN ('RUNNING','QUEUED')") suspend fun fail(id: String, message: String): Int
 /** Versi milik worker: hanya mengenai baris dengan generasi eksekusi ini — worker lama tak bisa menimpa job yang dilanjutkan. */
 @Query("UPDATE downloads SET state = 'FAILED', error = :message, pendingUri = NULL WHERE id = :id AND generation = :gen AND state IN ('RUNNING','QUEUED')") suspend fun failOwned(id: String, gen: Int, message: String): Int
 @Query("UPDATE downloads SET pendingUri = NULL WHERE id = :id") suspend fun clearPending(id: String)
 /** Pertahankan jurnal saat penghapusan pending ditolak provider; recovery berikutnya mencoba lagi. */
 @Query("UPDATE downloads SET pendingUri = :uri WHERE id = :id AND generation = :gen AND state IN ('FAILED','CANCELLED')") suspend fun retainPending(id: String, gen: Int, uri: String): Int
 /** Versi milik worker untuk cleanup akhir: worker lama tak boleh menghapus jejak pending milik generasi baru (BUG-002/R1). */
 @Query("UPDATE downloads SET pendingUri = NULL WHERE id = :id AND generation = :gen") suspend fun clearPendingOwned(id: String, gen: Int): Int
 @Query("UPDATE downloads SET pendingUri = :uri WHERE id = :id AND generation = :gen AND state = 'RUNNING'") suspend fun pending(id: String, gen: Int, uri: String?): Int
 /** Durasi hasil inspeksi worker — gerbang durasi lirik butuh durasi nyata, bukan 0 (BUG-013/R3). */
 @Query("UPDATE downloads SET durationSeconds = :seconds WHERE id = :id AND generation = :gen AND state IN ('QUEUED','RUNNING')") suspend fun duration(id: String, gen: Int, seconds: Long): Int
 /** Nama artis hasil inspeksi worker (placeholder "Video YouTube" → identitas tersimpan, P1/P3). */
 @Query("UPDATE downloads SET artist = :artist WHERE id = :id AND generation = :gen AND state IN ('QUEUED','RUNNING')") suspend fun artist(id: String, gen: Int, artist: String): Int
 /** Jalur sampul hasil cache — ditulis worker setelah thumbnail terunduh (P2 thumbnail). */
 @Query("UPDATE downloads SET coverPath = :path WHERE id = :id AND generation = :gen AND state IN ('QUEUED','RUNNING')") suspend fun coverPath(id: String, gen: Int, path: String): Int
 @Query("UPDATE downloads SET state = 'COMPLETED', progress = 100, speed = '', eta = '', outputUri = :uri, completedAt = :now, pendingUri = NULL WHERE id = :id AND generation = :gen AND state = 'RUNNING'") suspend fun finish(id: String, gen: Int, uri: String, now: Long): Int
 @Query("UPDATE downloads SET state = :state, progress = :progress, phase = :phase, speed = :speed, eta = :eta WHERE id = :id AND generation = :gen AND state IN ('QUEUED','RUNNING')") suspend fun progress(id: String, gen: Int, state: JobState, progress: Float, speed: String, eta: String, phase: String = DownloadPhase.PREPARING)
 @Query("UPDATE downloads SET state = 'QUEUED' WHERE id = :id AND generation = :gen AND state = 'RUNNING'") suspend fun interrupted(id: String, gen: Int): Int
 @Query("UPDATE downloads SET state = 'PAUSED', speed = '', eta = '' WHERE id = :id AND state IN ('QUEUED','RUNNING')") suspend fun pause(id: String): Int
 @Query("UPDATE downloads SET state = 'PAUSED', speed = '', eta = '' WHERE state IN ('QUEUED','RUNNING')") suspend fun pauseAll(): Int
 @Query("UPDATE downloads SET state = 'QUEUED', speed = '', eta = '', error = NULL, generation = generation + 1 WHERE id = :id AND state = 'PAUSED'") suspend fun resume(id: String): Int
 @Query("UPDATE downloads SET state = 'CANCELLED' WHERE id = :id AND state IN ('QUEUED','RUNNING','PAUSED')") suspend fun cancel(id: String): Int
 /** Versi milik worker untuk handleStop; lihat [failOwned]. */
 @Query("UPDATE downloads SET state = 'CANCELLED' WHERE id = :id AND generation = :gen AND state IN ('QUEUED','RUNNING','PAUSED')") suspend fun cancelOwned(id: String, gen: Int): Int
 @Query("DELETE FROM downloads WHERE id = :id AND state IN ('COMPLETED','FAILED','CANCELLED')") suspend fun remove(id: String): Int
 @Query("DELETE FROM downloads WHERE state = 'COMPLETED'") suspend fun clearCompleted(): Int
 /** Retensi riwayat: baris lama memakai createdAt bila completedAt masih 0 (BUG-023). */
 @Query("DELETE FROM downloads WHERE state = 'COMPLETED' AND COALESCE(NULLIF(completedAt, 0), createdAt) < :cutoff") suspend fun clearCompletedBefore(cutoff: Long): Int
 @Query("DELETE FROM downloads WHERE state IN ('FAILED','CANCELLED')") suspend fun clearUnsuccessful(): Int
 @Query("UPDATE downloads SET state = 'QUEUED', speed = '', eta = '', error = NULL, outputUri = NULL, pendingUri = NULL, sidecarUris = NULL, lyricStatus = '', generation = generation + 1, title = :title, artist = :artist, mode = :mode, format = :format, quality = :quality, bitrate = :bitrate, subtitles = :subtitles, embedSubs = :embedSubs, thumbnail = :thumbnail, embedThumb = :embedThumb, createdAt = :now WHERE id = :id AND state IN ('FAILED','CANCELLED')") suspend fun revive(id: String, title: String, artist: String, mode: MediaMode, format: String, quality: Int, bitrate: Int, subtitles: String, embedSubs: Boolean, thumbnail: Boolean, embedThumb: Boolean, now: Long): Int
 @Query("UPDATE downloads SET sidecarUris = :uris WHERE id = :id") suspend fun sidecars(id: String, uris: String?)
 @Query("UPDATE downloads SET sidecarPending = :uris WHERE id = :id") suspend fun sidecarPending(id: String, uris: String?): Int
 /** Satu transaksi per sidecar: baris terbit langsung tercatat dan jurnal dilepas — jendela proses mati tertutup (BUG-008/R2). */
 @Query("UPDATE downloads SET sidecarUris = CASE WHEN sidecarUris IS NULL OR sidecarUris = '' THEN :line ELSE sidecarUris || char(10) || :line END, sidecarPending = :pending WHERE id = :id") suspend fun commitSidecar(id: String, line: String, pending: String?): Int
 /** Worker mutations never overwrite journals/status belonging to a newer generation. */
 @Query("UPDATE downloads SET sidecarPending = :uris WHERE id = :id AND generation = :gen") suspend fun sidecarPendingOwned(id: String, gen: Int, uris: String?): Int
 @Query("UPDATE downloads SET sidecarUris = :uris WHERE id = :id AND generation = :gen") suspend fun sidecarsOwned(id: String, gen: Int, uris: String?): Int
 @Query("UPDATE downloads SET sidecarUris = CASE WHEN sidecarUris IS NULL OR sidecarUris = '' THEN :line ELSE sidecarUris || char(10) || :line END, sidecarPending = :pending WHERE id = :id AND generation = :gen AND state = 'RUNNING'") suspend fun commitSidecarOwned(id: String, gen: Int, line: String, pending: String?): Int
 @Query("UPDATE downloads SET lyricStatus = :status WHERE id = :id AND generation = :gen AND state = 'RUNNING'") suspend fun lyricStatusOwned(id: String, gen: Int, status: String): Int
 /** Status lirik terakhir untuk ditampilkan di Riwayat (BUG-014). */
 @Query("UPDATE downloads SET processingNotice = :notice WHERE id = :id AND generation = :gen") suspend fun processingNotice(id: String, gen: Int, notice: String): Int
 @Query("UPDATE downloads SET outputUri = :uri WHERE id = :id AND state = 'COMPLETED'") suspend fun renamedOutput(id: String, uri: String)
 @Query("UPDATE downloads SET lyricStatus = :status WHERE id = :id") suspend fun lyricStatus(id: String, status: String)
 /**
  * Antre ulang baris SELESAI memakai opsi yang sama ("Unduh lagi"): hasil lama sudah dihapus pemanggil,
  * jadi baris dipakai ulang alih-alih membuat duplikat (BUG-026). Gagal bila statusnya bukan COMPLETED.
  */
 @Query("UPDATE downloads SET state = 'QUEUED', progress = 0, phase = 'PREPARING', speed = '', eta = '', error = NULL, outputUri = NULL, pendingUri = NULL, sidecarUris = NULL, lyricStatus = '', completedAt = 0, generation = generation + 1, createdAt = :now WHERE id = :id AND state = 'COMPLETED'") suspend fun requeueCompleted(id: String, now: Long): Int
}
@Database(entities = [DownloadJob::class, MediaItem::class, MediaEvent::class, PersonalPlaylist::class, PlaylistMember::class], version = 10, exportSchema = true)
@TypeConverters(Converters::class)
abstract class DownloadDatabase : RoomDatabase() {
 abstract fun downloads(): DownloadDao
 abstract fun personal(): PersonalLibraryDao
 abstract fun media(): MediaDao
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
  private val MIGRATION_6_7 = object : Migration(6, 7) {
   override fun migrate(db: SupportSQLiteDatabase) {
    // Nama artis hasil identifikasi + jalur sampul cache — baris lama '' (tanpa bukti / tanpa sampah).
    db.execSQL("ALTER TABLE downloads ADD COLUMN artist TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE downloads ADD COLUMN coverPath TEXT NOT NULL DEFAULT ''")
   }
  }
  private val MIGRATION_7_8 = object : Migration(7, 8) {
   override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE downloads ADD COLUMN rawTitle TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE downloads ADD COLUMN trackTitle TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE downloads ADD COLUMN creator TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE downloads ADD COLUMN remixer TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE downloads ADD COLUMN versionTag TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE downloads ADD COLUMN artistEvidence TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE downloads ADD COLUMN platformId TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE downloads ADD COLUMN thumbnailUrl TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE downloads ADD COLUMN sourceId TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE downloads ADD COLUMN extractor TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE downloads ADD COLUMN autoFolder TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE downloads ADD COLUMN resumeNotice TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE downloads ADD COLUMN partialBytes INTEGER NOT NULL DEFAULT 0")
   }
  }
  private val MIGRATION_8_9 = object : Migration(8, 9) {
   override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE downloads ADD COLUMN autoRename INTEGER NOT NULL DEFAULT 1")
    db.execSQL("ALTER TABLE downloads ADD COLUMN autoTag INTEGER NOT NULL DEFAULT 1")
    db.execSQL("ALTER TABLE downloads ADD COLUMN autoLibrary INTEGER NOT NULL DEFAULT 1")
    db.execSQL("ALTER TABLE downloads ADD COLUMN processingNotice TEXT NOT NULL DEFAULT ''")
    createMediaTables(db)
   }
  }
  private val MIGRATION_9_10 = object : Migration(9, 10) {
   override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS personal_playlists (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
    db.execSQL("CREATE TABLE IF NOT EXISTS playlist_members (`playlistId` TEXT NOT NULL, `mediaUri` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `mediaUri`), FOREIGN KEY(`playlistId`) REFERENCES `personal_playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_members_mediaUri ON playlist_members(mediaUri)")
   }
  }
  private fun createMediaTables(db: SupportSQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS media_items (`uri` TEXT NOT NULL, `jobId` TEXT, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, `displayName` TEXT NOT NULL, `mime` TEXT NOT NULL, `folder` TEXT NOT NULL, `sizeBytes` INTEGER, `durationMs` INTEGER, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `albumArtist` TEXT NOT NULL, `genre` TEXT NOT NULL, `track` TEXT NOT NULL, `year` TEXT NOT NULL, `creator` TEXT NOT NULL, `platformId` TEXT NOT NULL, `coverPath` TEXT NOT NULL, `width` INTEGER, `height` INTEGER, `availability` TEXT NOT NULL, `metadataStatus` TEXT NOT NULL, `artworkStatus` TEXT NOT NULL, `lyricUri` TEXT, `lyricStatus` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, `checkedAt` INTEGER NOT NULL, `modifiedAt` INTEGER, `accessUri` TEXT NOT NULL, PRIMARY KEY(`uri`))")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_media_items_jobId ON media_items(jobId)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_media_items_accessUri ON media_items(accessUri)")
    db.execSQL("CREATE TABLE IF NOT EXISTS media_events (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `jobId` TEXT NOT NULL, `action` TEXT NOT NULL, `kind` TEXT NOT NULL, `platformId` TEXT NOT NULL, `at` INTEGER NOT NULL)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_media_events_at ON media_events(at)")
  }
  private val eventCallback = object : Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        // Journal only real transitions after upgrade; never infer deleted or old attempts.
        db.execSQL("CREATE TRIGGER IF NOT EXISTS download_event_insert AFTER INSERT ON downloads BEGIN INSERT INTO media_events(jobId,action,kind,platformId,at) VALUES(NEW.id,NEW.state,NEW.mode,NEW.platformId,CAST(strftime('%s','now') AS INTEGER)*1000); END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS download_event_state AFTER UPDATE OF state ON downloads WHEN NEW.state != OLD.state AND NEW.state IN ('COMPLETED','FAILED','CANCELLED','QUEUED') BEGIN INSERT INTO media_events(jobId,action,kind,platformId,at) VALUES(NEW.id,NEW.state,NEW.mode,NEW.platformId,CAST(strftime('%s','now') AS INTEGER)*1000); END")
    }
  }
  @Volatile private var instance: DownloadDatabase? = null
  fun get(context: android.content.Context) = instance ?: synchronized(this) { instance ?: Room.databaseBuilder(context.applicationContext, DownloadDatabase::class.java, "downloads.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10).addCallback(eventCallback).build().also { instance = it } }
 }
}
object Selection {
 val videoFormats = setOf("auto", "mp4", "mkv", "webm", "mov", "avi")
 val audioFormats = setOf("auto", "mp3", "m4a", "opus", "flac", "wav", "aac", "vorbis")
 val subtitleChoices = setOf("", "asli", "id", "en", ".*")
 /** Audio formats whose cover art can be embedded without the missing `mutagen` package (ffmpeg fallback path). */
 val coverFormats = setOf("auto", "mp3", "m4a")
 fun coverSupported(mode: MediaMode, format: String): Boolean = mode == MediaMode.AUDIO && format in coverFormats
 /** Auto keeps native audio unless the user explicitly requests a target bitrate. Artwork never selects a codec. */
 fun audioTargetFormat(format: String, bitrate: Int): String = if (format == "auto") { if (bitrate > 0) "mp3" else "best" } else format
 /** Pilihan resolusi yang benar-benar tersedia: tinggi terdekat ≤ permintaan, atau Auto bila tidak ada. */
 fun closestQuality(requested: Int, available: List<Int>): Int {
  if (requested <= 0 || available.isEmpty()) return requested
  return available.filter { it <= requested }.maxOrNull() ?: 0
 }
 /**
  * Tinggi piksel untuk tier yang diminta (mis. preset 1080p). Label tier dipakai untuk tampilan,
  * tetapi yt-dlp memfilter dengan tinggi nyata, jadi tier 1080 pada video 2.35:1 mengirim 818.
  * Mengembalikan 0 (Auto) bila tidak ada tier yang ≤ permintaan.
  */
 fun heightForTier(requested: Int, available: List<VideoResolution>): Int {
  if (requested <= 0 || available.isEmpty()) return requested
  // Nilai yang sudah berupa tinggi nyata (dipilih dari menu) tidak boleh dipetakan ulang, kalau
  // tidak 818 pada video 2.35:1 akan turun sendiri ke tier 720 saat komposisi dibuat ulang.
  if (available.any { it.height == requested }) return requested
  return available.filter { it.tier <= requested }.maxByOrNull { it.tier }?.height ?: 0
 }
 /** Safe MediaStore folder name (single segment under Downloads/); null when unusable. */
 fun safeFolder(raw: String): String? {
  val name = raw.trim()
  if (name.isEmpty() || name.length > 40) return null
  if (name.endsWith(".")) return null
  return if (name.matches(Regex("[\\p{L}\\p{N}][\\p{L}\\p{N} ._()-]*"))) name else null
 }
 /** Pemisah nama kolaborator pada judul (" x ", " ft", " feat", "&", dst.) — hanya artis utama yang dipakai. */
 private val collaborator = Regex("""\s+(?:xx|x|ft\.?|feat(?:uring)?\.?|vs\.?|with)\s+|\s*[&+]\s*""", RegexOption.IGNORE_CASE)
 /**
 * Nama subfolder otomatis dari judul playlist/Mix: HANYA bagian artis/nama utama — berhenti pada
 * segmen judul lagu ("Mix - ARTIS - LAGU") dan pemisah kolaborasi (" x ", " ft ", "&", …), lalu semua
 * yang bukan huruf/angka jadi pemisah "-" (mis. "Mix - DENNY CAKNAN X HAPPY ASMARA - SATRU 2" →
 * "mix-denny-caknan"). Hasil SELALU lolos [safeFolder] — satu segmen, maks 40 karakter, huruf/angka
 * di awal. Null bila judul tidak menghasilkan nama yang aman (mis. hanya simbol) — unduhan langsung
 * di folder utama seperti sebelumnya.
 */
 /** Embel-embel kurung sarkat pada judul (Official, Live, 2024, dst.) — dibuang sebelum memilih segmen nama. */
 private val decoration = Regex("""\s*[(\[][^\)\]]*[)\]]""")
 /** Channel otomatis YouTube Music ("<artis> - Topic") — sufiks dibuang agar folder tidak jadi "…-topic". */
 private val topicChannel = Regex("""\s*-\s*Topic\s*$""", RegexOption.IGNORE_CASE)
 /**
  * Pilih nama channel untuk auto-folder Mix: channel playlist bila ada, kalau tidak channel yang PALING
  * SERING muncul di antara entri (Mix radio bisa berisi banyak kreator — suara terbanyak = artis utama).
  * Nilai kosong/blank diabaikan; hasil kosong berarti tidak ada sinyal channel (heuristik judul dipakai).
  */
 fun pickChannel(playlistChannel: String?, entryChannels: List<String>): String {
  playlistChannel?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
  return entryChannels.mapNotNull { value -> value.trim().takeIf { it.isNotEmpty() } }
   .groupingBy { it }.eachCount()
   .maxByOrNull { it.value }?.key.orEmpty()
 }
 /**
 * Nama subfolder otomatis dari judul playlist/Mix — heuristik, bukan tebakaran sempurna:
 * (1) buang embel-embel "(...)"/"[...]"; (2) pecah pada " - " lalu potong pemisah kolaborasi
 * (" x ", " ft", "&", dst.) dan ambil segmen PERTAMA tiap bagian (artis utama);
 * (3) Mix ("Mix - <judul video>") memakai NAMA CHANNEL kreator bila tersedia ([pickChannel]) — judul Mix
 * YouTube bisa satu kalimat panjang tanpa pemisah, sehingga heuristik segmen gagal; tanpa channel,
 * kandidat TERPANJANG dipakai karena urutan "ARTIS - LAGU" dan "LAGU - ARTIS" dua-duanya muncul di
 * YouTube (mis. "Mix - LAMUNAN - SASYA ARKHISNA (Official…)" → "mix-sasya-arkhisna-…"); playlist biasa
 * memakai segmen pertama (konvensi "ARTIS - TOPIK"; channel pemilik playlist tidak dipakai).
 * Hasil SELALU lolos [safeFolder] — satu segmen, maks 40 karakter, huruf/angka di awal. Null bila
 * judul tidak menghasilkan nama yang aman — unduhan langsung di folder utama seperti sebelumnya.
 */
 fun playlistFolder(title: String, channel: String = "", provenArtist: String = "", entryArtists: List<String> = emptyList()): String? {
  val trimmed = title.trim()
  // Judul Mix "Mix - <judul video>": penanda Mix dipertahankan sebagai prefiks folder.
  val isMix = trimmed.startsWith("Mix - ", ignoreCase = true)
  fun slugOf(prefix: String, name: String): String? =
   ((if (isMix) prefix else "") + name).lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "-")
    .trim('-').take(40).trimEnd('-').takeIf { it.isNotEmpty() }?.let { safeFolder(it) }

  // (1) PRIORITAS: artis yang sudah terbukti dari metadata/identifikasi judul — bukan nama channel.
  provenArtist.replace(decoration, " ").replace(topicChannel, " ")
   .split(collaborator).first().trim().takeIf { it.isNotEmpty() }
   ?.let { slugOf("Mix ", it)?.let { s -> return s } }
  // (1b) Suara terbanyak dari identifikasi artis antar-entri (Mix berisi banyak kreator).
  entryArtists.mapNotNull { v -> v.trim().takeIf { it.isNotEmpty() } }
   .groupingBy { it }.eachCount().maxByOrNull { it.value }
   ?.takeIf { it.value >= 2 }?.key
   ?.let { slugOf("Mix ", it)?.let { s -> return s } }

  // No artist evidence: keep the root folder, rather than promote an uploader/title fragment.
  return null
 }
 /** One folder per collection. Mix uses the verified seed performer, never the playlist owner. */
 fun collectionFolder(playlist: PlaylistMetadata, seed: VideoMetadata? = null): String {
  if (!isMix(playlist.url)) return artistFolder(playlist.title)
   ?: "playlist-${java.security.MessageDigest.getInstance("SHA-256").digest(playlist.url.toByteArray()).take(6).joinToString("") { "%02x".format(it) }}"
  val seedId = videoId(playlistInspectionUrl(playlist.url).orEmpty())
  val trusted = seed?.takeIf { seedId != null && videoId(it.url) == seedId }
  val identity = trusted?.let { TitleSplit.identify(it.title, it.uploader, it.artist, it.track, it.creator) }
  val performer = identity?.artist.orEmpty().ifBlank { identity?.remixer.orEmpty() }
   .split(collaborator).first().trim()
  return artistFolder(performer, mix=true)
   ?: artistFolder(seedId.orEmpty().ifBlank { listParam(playlist.url).orEmpty() }, mix=true)
   ?: "mix-${java.security.MessageDigest.getInstance("SHA-256").digest(playlist.url.toByteArray()).take(6).joinToString("") { "%02x".format(it) }}"
 }
 fun artistFolder(artist: String, mix: Boolean = false): String? {
  if (artist.isBlank()) return null
  return ((if (mix) "mix-" else "") + artist.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-'))
   .take(40).trimEnd('-').takeIf { it.isNotBlank() }?.let(::safeFolder)
 }
 private val hosts = setOf("youtube.com","www.youtube.com","m.youtube.com","music.youtube.com","youtu.be")
 /**
 * Platform non-YouTube yang didukung yt-dlp; subdomain ikut diterima (mis. vm.tiktok.com).
 * Allowlist ketat — tautan asing tetap ditolak sebelum menyentuh engine.
 */
 private val otherPlatforms = SourcePlatforms.known.filter { it.id !in setOf("youtube", "youtube_music") }.flatMap { it.domains }.toSet()

 fun validate(options: DownloadOptions) {
  require(validUrl(options.url)) { "Gunakan tautan video dari platform yang didukung." }
  require(options.format in if (options.mode == MediaMode.VIDEO) videoFormats else audioFormats) { "Format tidak didukung." }
  require(options.quality in setOf(0,360,480,720,1080,1440,2160,4320))
  require(options.bitrate in setOf(0,64,128,192,256,320))
  require(options.subtitles in subtitleChoices) { "Pilihan subtitle tidak didukung." }
  require(!options.embedSubs || options.mode == MediaMode.VIDEO) { "Sematkan subtitle hanya untuk video." }
 }
 fun validUrl(raw: String): Boolean = runCatching {
  val uri = URI(raw.trim()); val host = uri.host?.lowercase() ?: return false
  if (uri.scheme != "https" || uri.userInfo != null || (uri.port != -1 && uri.port != 443)) return false
  if (host in hosts) return (if (host == "youtu.be") uri.path.length > 1 else uri.path == "/watch" && uri.rawQuery?.split('&')?.any { it.startsWith("v=") && it.length > 2 } == true || uri.path.startsWith("/shorts/") || uri.path.startsWith("/live/"))
  // Platform lain: cukup https + host di allowlist + ada path — validasi isi biar engine (yt-dlp).
  val path = uri.path ?: return false
  otherPlatforms.any { host == it || host.endsWith(".$it") } && path.isNotBlank() && path != "/"
 }.getOrDefault(false)
 /**
 * Identitas sumber untuk deteksi duplikat: ID video YouTube (alias watch/youtu.be/Shorts sama),
 * SoundCloud mengabaikan parameter pelacakan utm_*; parameter akses tetap dipertahankan.
 * Platform lain memakai URL utuh tanpa fragment. Null bila URL tidak valid.
 */
 fun identity(raw: String): String? {
    videoId(raw)?.let { return it }
    val source = raw.trim().substringBefore('#').takeIf(::validUrl) ?: return null
    val uri = URI(source)
    if (uri.host.lowercase() !in setOf("soundcloud.com", "www.soundcloud.com", "m.soundcloud.com")) return source
    val query = uri.rawQuery?.split('&')?.filterNot { parameter ->
        java.net.URLDecoder.decode(parameter.substringBefore('='), "UTF-8").startsWith("utm_", ignoreCase = true)
    }?.joinToString("&").orEmpty()
    return "https://soundcloud.com${uri.rawPath}" + if (query.isEmpty()) "" else "?$query"
 }
 /** Identitas sumber, termasuk URL riwayat lama; parameter playlist/waktu tidak membedakan video. Hanya YouTube — platform lain memakai URL utuh lewat [identity]. */
 fun videoId(raw: String): String? {
  if (!validUrl(raw)) return null
  return runCatching {
   val uri = URI(raw.trim())
   // Gerbang host: tanpa ini, path platform lain (mis. vimeo.com/123456) bisa terbaca sebagai "ID" dan tabrakan lintas situs.
   if (uri.host?.lowercase() !in hosts) return null
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
 /** Nama platform yang dikenal dari URL — dipakai filter kategori Riwayat; host asing = nama host sendiri. */
 fun platform(raw: String): String? = SourcePlatforms.fromUrl(raw).takeIf { it.id != "unknown" }?.label
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
  // `<=?` mengizinkan format yang tidak melaporkan tinggi (umum di Instagram), dan `/best` terakhir
  // menjaga unduhan tetap berhasil bila sumber tidak punya format yang cocok sama sekali.
  val height = if (options.quality > 0) "[height<=?${options.quality}]" else ""
  return when(options.format) {
   "mp4", "mov", "avi" -> "bestvideo$height[ext=mp4][vcodec^=avc1]+bestaudio[ext=m4a]/best$height[ext=mp4][vcodec^=avc1]/bestvideo$height[ext=mp4]+bestaudio[ext=m4a]/best$height[ext=mp4]/best"
   "webm" -> "bestvideo$height[ext=webm]+bestaudio[ext=webm]/best$height[ext=webm]/best"
   else -> "bestvideo$height+bestaudio/best$height/best"
  }
 }
}
