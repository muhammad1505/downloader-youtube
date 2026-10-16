package id.local.tubeflow.core

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.channels.Channel

/** Versioned, bounded local snapshot. It never stores a promise to resume automatically. */
object PlayerSession {
    const val MAX_BYTES = 4 * 1024 * 1024
    const val MAX_TRACKS = 3000
    data class Saved(val queue: PlaybackQueue.Snapshot<PlayerViewModel.Track>, val positionMs: Long)

    fun encode(saved: Saved): ByteArray {
        require(saved.queue.entries.size in 1..MAX_TRACKS && saved.positionMs >= 0)
        val entries = JSONArray()
        saved.queue.entries.forEach { (id, track) ->
            require(localUri(track.uri))
            entries.put(JSONObject().put("id", id).put("uri", track.uri).put("title", track.title.take(1024))
                .put("artist", track.artist.take(1024)).put("cover", track.cover.take(4096))
                .put("lyrics", track.lyricUri?.takeIf(::localUri).orEmpty()))
        }
        return JSONObject().put("version", 1).put("entries", entries).put("index", saved.queue.index)
            .put("repeat", saved.queue.repeat.name).put("shuffle", saved.queue.shuffled)
            .put("lastId", saved.queue.lastId).put("position", saved.positionMs).toString().toByteArray().also {
                require(it.size <= MAX_BYTES)
            }
    }

    fun decode(bytes: ByteArray): Saved {
        require(bytes.size <= MAX_BYTES)
        val data = JSONObject(bytes.toString(Charsets.UTF_8))
        require(data.getInt("version") == 1)
        val entries = data.getJSONArray("entries")
        require(entries.length() in 1..MAX_TRACKS)
        val tracks = (0 until entries.length()).map { i -> entries.getJSONObject(i).let { row ->
            val uri = row.getString("uri"); require(localUri(uri))
            val lyric = row.optString("lyrics").takeIf { it.isNotBlank() && localUri(it) }
            row.getLong("id") to PlayerViewModel.Track(uri, row.getString("title").take(1024),
                row.optString("artist").take(1024), row.optString("cover").take(4096), lyric)
        } }
        val queue = PlaybackQueue.Snapshot(tracks, data.getInt("index"),
            PlaybackQueue.Repeat.valueOf(data.getString("repeat")), data.getBoolean("shuffle"), data.getLong("lastId"))
        PlaybackQueue.restore(queue) // Validate IDs/index even before provider availability filtering.
        return Saved(queue, data.getLong("position").also { require(it >= 0) })
    }

    fun positionBytes(revision: String, positionMs: Long): ByteArray = JSONObject()
        .put("revision", revision).put("position", positionMs.coerceAtLeast(0)).toString().toByteArray()

    fun restorePosition(bytes: ByteArray, revision: String, fallback: Long): Long = runCatching {
        require(bytes.size <= 512 && revision.isNotBlank())
        val data = JSONObject(bytes.toString(Charsets.UTF_8))
        if (data.getString("revision") == revision) data.getLong("position").also { require(it >= 0) } else fallback
    }.getOrDefault(fallback)

    private fun localUri(uri: String) = uri.length <= 8192 &&
        (uri.startsWith("content://") || uri.startsWith("file://"))
}

/** One conflated writer, consumed on IO; AtomicFile protects the previous snapshot on write failure. */
class PlayerSessionStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "player-session.json"))
    private val position = AtomicFile(File(context.noBackupFilesDir, "player-position.json"))
    private var lastQueue: PlaybackQueue.Snapshot<PlayerViewModel.Track>? = null
    private var revision = ""
    private data class Write(val saved: PlayerSession.Saved?)
    private val writes = Channel<Write>(Channel.CONFLATED)
    fun offer(saved: PlayerSession.Saved?) { writes.trySend(Write(saved)) }
    private fun write(target: AtomicFile, bytes: ByteArray): Boolean {
        var stream: java.io.FileOutputStream? = null
        return try { stream = target.startWrite(); stream.write(bytes); target.finishWrite(stream); true }
        catch (_: Exception) { target.failWrite(stream); false }
    }
    suspend fun writePending() {
        for (command in writes) synchronized(file) {
            val saved = command.saved
            if (saved == null) { file.delete(); position.delete(); lastQueue = null }
            else {
                if (lastQueue != saved.queue) {
                    val nextRevision = java.util.UUID.randomUUID().toString()
                    val bytes = runCatching { JSONObject(String(PlayerSession.encode(saved), Charsets.UTF_8))
                        .put("revision", nextRevision).toString().toByteArray().also { require(it.size <= PlayerSession.MAX_BYTES) } }.getOrNull()
                    if (bytes == null) { file.delete(); position.delete(); lastQueue = null }
                    else if (write(file, bytes)) { lastQueue = saved.queue; revision = nextRevision }
                }
                // Position checkpoints are tiny. A revision prevents applying a stale seek to a new queue.
                if (lastQueue == saved.queue) write(position, PlayerSession.positionBytes(revision,saved.positionMs))
            }
        }
    }
    fun read(): PlayerSession.Saved? = synchronized(file) {
        runCatching { file.openRead().use { input ->
            val bytes = input.readBytesBounded(PlayerSession.MAX_BYTES)
            val saved = PlayerSession.decode(bytes)
            val savedRevision = JSONObject(bytes.toString(Charsets.UTF_8)).optString("revision")
            val offset = runCatching { position.openRead().use { PlayerSession.restorePosition(it.readBytesBounded(512),savedRevision,saved.positionMs) } }.getOrDefault(saved.positionMs)
            saved.copy(positionMs = offset)
        } }.getOrNull()
    }
}

private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) return out.toByteArray()
        require(out.size() + count <= limit)
        out.write(buffer, 0, count)
    }
}
