package id.local.tubeflow.core

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "media_items", indices = [Index(value = ["jobId"]), Index(value = ["accessUri"])])
data class MediaItem(
    @PrimaryKey val uri: String,
    val jobId: String? = null,
    val kind: String,
    val title: String,
    val displayName: String,
    val mime: String,
    val folder: String,
    val sizeBytes: Long? = null,
    val durationMs: Long? = null,
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val genre: String = "",
    val track: String = "",
    val year: String = "",
    val creator: String = "",
    val platformId: String = "",
    val coverPath: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val availability: String = "AVAILABLE",
    val metadataStatus: String = "UNCHECKED",
    val artworkStatus: String = "UNCHECKED",
    val lyricUri: String? = null,
    val lyricStatus: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val checkedAt: Long = 0,
    val modifiedAt: Long? = null,
    val accessUri: String = "",
)

val MediaItem.location: String get() = accessUri.ifBlank { uri }

@Entity(tableName = "media_events", indices = [Index(value = ["at"])])
data class MediaEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: String,
    val action: String,
    val kind: String,
    val platformId: String,
    val at: Long,
)

data class LibraryTotals(
    val audio: Long, val video: Long, val unavailable: Long,
    val audioBytes: Long?, val videoBytes: Long?, val unknownSizes: Long,
    val artists: Long, val albums: Long, val tagged: Long, val partial: Long,
    val artwork: Long, val lyrics: Long, val noLyrics: Long, val unchecked: Long,
    val processingFailed: Long, val artworkAbsent: Long, val lyricsUnchecked: Long,
)
data class EventCount(val action: String, val count: Long)
data class DailyCount(val day: String, val count: Long)
data class PlatformCount(val platformId: String, val count: Long)

@Dao
interface MediaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(item: MediaItem)
    @Query("SELECT * FROM media_items WHERE uri = :uri") suspend fun get(uri: String): MediaItem?
    @Query("SELECT * FROM media_items WHERE accessUri = :raw LIMIT 1") suspend fun getByAccess(raw: String): MediaItem?
    // Dedup pemindaian SAF: file yang sama dapat punya baris MediaStore dan baris dokumen SAF.
    @Query("SELECT * FROM media_items WHERE folder = :folder AND displayName = :name ORDER BY (availability = 'AVAILABLE') DESC, checkedAt DESC LIMIT 1") suspend fun findByName(folder: String, name: String): MediaItem?
    @Query("DELETE FROM media_items WHERE uri = :uri") suspend fun remove(uri: String)
    @Query("UPDATE media_items SET checkedAt = :at WHERE uri IN (:uris)") suspend fun checked(uris: List<String>, at: Long)
    @Query("SELECT * FROM media_items ORDER BY checkedAt ASC LIMIT :limit") suspend fun oldest(limit: Int): List<MediaItem>
    // Koleksi utuh untuk antrean auto-play: lagu tersedia dalam urutan koleksi (terbaru lebih dulu).
    @Query("SELECT * FROM media_items WHERE kind = 'AUDIO' AND availability = 'AVAILABLE' ORDER BY addedAt DESC, title COLLATE NOCASE ASC") suspend fun availableAudio(): List<MediaItem>
    @Query("SELECT * FROM media_items WHERE folder = :folder AND displayName = :name AND uri != :exceptUri AND availability = 'AVAILABLE' LIMIT 1") suspend fun collision(folder: String, name: String, exceptUri: String): MediaItem?
    @Query("SELECT DISTINCT folder FROM media_items ORDER BY folder LIMIT 250") fun folders(): Flow<List<String>>
    @Query("SELECT DISTINCT artist FROM media_items WHERE kind = 'AUDIO' AND artist != '' ORDER BY artist LIMIT 250") fun artists(): Flow<List<String>>
    @Query("SELECT DISTINCT album FROM media_items WHERE kind = 'AUDIO' AND album != '' ORDER BY album LIMIT 250") fun albums(): Flow<List<String>>
    @Query("""SELECT * FROM media_items WHERE (:kind = '' OR kind = :kind)
        AND (:folder = '' OR folder = :folder) AND (:artist = '' OR artist = :artist) AND (:album = '' OR album = :album)
        AND (title LIKE :search ESCAPE '\' OR displayName LIKE :search ESCAPE '\' OR artist LIKE :search ESCAPE '\')
        ORDER BY CASE WHEN :sort = 'name' THEN title END COLLATE NOCASE ASC,
        CASE WHEN :sort = 'size' THEN sizeBytes END DESC,
        CASE WHEN :sort = 'duration' THEN durationMs END DESC, addedAt DESC LIMIT :limit""")
    fun page(kind: String, folder: String, artist: String, album: String, search: String, sort: String, limit: Int): Flow<List<MediaItem>>
    @Query("""SELECT COALESCE(SUM(kind='AUDIO' AND availability='AVAILABLE'),0) AS audio,
        COALESCE(SUM(kind='VIDEO' AND availability='AVAILABLE'),0) AS video,
        COALESCE(SUM(availability!='AVAILABLE'),0) AS unavailable,
        SUM(CASE WHEN kind='AUDIO' AND availability='AVAILABLE' THEN sizeBytes END) AS audioBytes,
        SUM(CASE WHEN kind='VIDEO' AND availability='AVAILABLE' THEN sizeBytes END) AS videoBytes,
        COALESCE(SUM(availability='AVAILABLE' AND sizeBytes IS NULL),0) AS unknownSizes,
        COUNT(DISTINCT CASE WHEN kind='AUDIO' AND artist!='' AND availability='AVAILABLE' THEN artist END) AS artists,
        COUNT(DISTINCT CASE WHEN kind='AUDIO' AND album!='' AND availability='AVAILABLE' THEN album END) AS albums,
        COALESCE(SUM(kind='AUDIO' AND availability='AVAILABLE' AND metadataStatus='READY'),0) AS tagged,
        COALESCE(SUM(kind='AUDIO' AND availability='AVAILABLE' AND metadataStatus='PARTIAL'),0) AS partial,
        COALESCE(SUM(kind='AUDIO' AND availability='AVAILABLE' AND artworkStatus='PRESENT'),0) AS artwork,
        COALESCE(SUM(kind='AUDIO' AND availability='AVAILABLE' AND lyricStatus='tersimpan'),0) AS lyrics,
        COALESCE(SUM(kind='AUDIO' AND availability='AVAILABLE' AND lyricStatus IN ('tidak_ditemukan','tidak_cocok')),0) AS noLyrics,
        COALESCE(SUM(kind='AUDIO' AND availability='AVAILABLE' AND metadataStatus='UNCHECKED'),0) AS unchecked,
        COALESCE(SUM(kind='AUDIO' AND availability='AVAILABLE' AND metadataStatus='FAILED'),0) AS processingFailed,
        COALESCE(SUM(kind='AUDIO' AND availability='AVAILABLE' AND artworkStatus='ABSENT'),0) AS artworkAbsent,
        COALESCE(SUM(kind='AUDIO' AND availability='AVAILABLE' AND lyricStatus=''),0) AS lyricsUnchecked FROM media_items""")
    fun totals(): Flow<LibraryTotals>
    @Insert suspend fun event(event: MediaEvent)
    @Query("SELECT * FROM media_events ORDER BY at DESC, id DESC LIMIT 20") fun recentEvents(): Flow<List<MediaEvent>>
    @Query("SELECT action, COUNT(*) AS count FROM media_events WHERE at >= :since GROUP BY action") fun eventCounts(since: Long): Flow<List<EventCount>>
    @Query("SELECT strftime('%Y-%m-%d', at/1000, 'unixepoch','localtime') AS day, COUNT(*) AS count FROM media_events WHERE at >= :since AND action = 'COMPLETED' GROUP BY day ORDER BY day") fun daily(since: Long): Flow<List<DailyCount>>
    @Query("SELECT platformId, COUNT(*) AS count FROM media_events WHERE at >= :since AND action = 'COMPLETED' GROUP BY platformId ORDER BY count DESC") fun platforms(since: Long): Flow<List<PlatformCount>>
}

object SmartMedia {
    fun classify(mime: String?, name: String): String? {
        val type = mime.orEmpty().lowercase().substringBefore(';')
        if (type.startsWith("audio/")) return "AUDIO"
        if (type.startsWith("video/")) return "VIDEO"
        if (type.isNotBlank() && type !in setOf("application/octet-stream", "application/ogg")) return null
        return when (name.substringAfterLast('.', "").lowercase()) {
            "mp3", "m4a", "aac", "flac", "wav", "opus", "ogg" -> "AUDIO"
            "mp4", "mkv", "webm", "mov", "avi", "3gp" -> "VIDEO"
            else -> null
        }
    }
    fun fileName(identity: SongIdentity, originalTitle: String, extension: String, normalize: Boolean): String {
        require(extension.matches(Regex("[a-zA-Z0-9]{1,10}"))) { "Ekstensi tidak valid." }
        val stem = if (normalize) Format.musicFileName(identity) else Format.tidyFileName(originalTitle)
        return stem.trimEnd(' ', '.') .ifBlank { "Media" } + "." + extension
    }
    fun renameName(raw: String, existing: String): String {
        val extension = existing.substringAfterLast('.', "")
        require(extension.isEmpty() || extension.matches(Regex("[a-zA-Z0-9]{1,10}"))) { "Ekstensi tidak valid." }
        val stem = raw.trim().let { if (it.endsWith(".$extension", true)) it.dropLast(extension.length + 1) else it }
        require(stem.isNotBlank() && stem.toByteArray(Charsets.UTF_8).size <= 240 && !stem.contains(Regex("""[\\/:*?"<>\p{Cntrl}]"""))) { "Nama terlalu panjang atau berisi karakter terlarang." }
        return if (extension.isBlank()) stem else "$stem.$extension"
    }
    fun search(raw: String) = "%" + raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
}
