package id.local.tubeflow.core

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName = "personal_playlists")
data class PersonalPlaylist(@PrimaryKey val id: String, val name: String, val createdAt: Long)

/** References only: no media copy, no FK to replace-on-scan catalogue rows. */
@Entity(tableName = "playlist_members", primaryKeys = ["playlistId", "mediaUri"],
    foreignKeys = [ForeignKey(entity = PersonalPlaylist::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("mediaUri")])
data class PlaylistMember(val playlistId: String, val mediaUri: String, val position: Long)

/** Jumlah lagu per playlist untuk baris Kelola playlist; dihitung Room, bukan di UI. */
data class PlaylistCount(val playlistId: String, val count: Int)

@Dao
interface PersonalLibraryDao {
    @Query("SELECT * FROM personal_playlists ORDER BY createdAt, id") fun observe(): Flow<List<PersonalPlaylist>>
    @Query("SELECT playlistId, COUNT(*) AS count FROM playlist_members GROUP BY playlistId") fun observeCounts(): Flow<List<PlaylistCount>>
    @Query("SELECT * FROM personal_playlists ORDER BY createdAt, id") suspend fun all(): List<PersonalPlaylist>
    @Query("SELECT * FROM playlist_members ORDER BY position, mediaUri") suspend fun members(): List<PlaylistMember>
    @Query("SELECT mediaUri FROM playlist_members WHERE playlistId = :id ORDER BY position, mediaUri") fun references(id: String): Flow<List<String>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(playlist: PersonalPlaylist)
    @Query("UPDATE personal_playlists SET name = :name WHERE id = :id") suspend fun rename(id: String, name: String)
    @Query("DELETE FROM personal_playlists WHERE id = :id") suspend fun delete(id: String)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun add(member: PlaylistMember)
    @Query("DELETE FROM playlist_members WHERE playlistId = :id AND mediaUri = :uri") suspend fun remove(id: String, uri: String)
    @Query("SELECT EXISTS(SELECT 1 FROM playlist_members WHERE playlistId = :id AND mediaUri = :uri)") suspend fun contains(id: String, uri: String): Boolean
    @Query("SELECT COUNT(*) FROM playlist_members WHERE playlistId = :id") suspend fun count(id: String): Int
    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_members WHERE playlistId = :id") suspend fun nextPosition(id: String): Long
    @Query("UPDATE OR IGNORE playlist_members SET mediaUri = :next WHERE mediaUri = :before") suspend fun relink(before: String, next: String)
    @Query("DELETE FROM playlist_members WHERE mediaUri = :uri") suspend fun removeMedia(uri: String)
}

object PersonalLibrary {
    const val FAVORITES = "favorites"
    fun name(raw: String): String = raw.trim().also {
        require(it.isNotBlank() && it.length <= 80 && it.none(Char::isISOControl)) { "Nama playlist harus 1–80 karakter." }
    }
    fun create(raw: String) = PersonalPlaylist(UUID.randomUUID().toString(), name(raw), System.currentTimeMillis())
}
