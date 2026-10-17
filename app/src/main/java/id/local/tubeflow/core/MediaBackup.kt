package id.local.tubeflow.core

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class MediaBackup(private val context: Context) {
    private val database = DownloadDatabase.get(context)
    private val restoreJournal = android.util.AtomicFile(java.io.File(context.noBackupFilesDir, "backup-lyric-restore.json"))
    private fun saveRestore(record: JSONObject) {
        val bytes = record.toString().toByteArray()
        require(bytes.size <= 4 * 1024 * 1024)
        val output = restoreJournal.startWrite()
        try { output.write(bytes); restoreJournal.finishWrite(output) }
        catch (failure: Exception) { restoreJournal.failWrite(output); throw failure }
    }
    /** The shared media lock is held. Finish only an already committed, byte-identical LRC. */
    internal suspend fun recoverLocked() {
        if (!restoreJournal.baseFile.exists() && !java.io.File(restoreJournal.baseFile.path + ".bak").exists()) return
        val bytes = restoreJournal.openRead().use { input ->
            val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size() + n <= 4 * 1024 * 1024); out.write(buffer, 0, n) }
            out.toByteArray()
        }
        val pending = JSONObject(bytes.toString(Charsets.UTF_8))
        val item = database.media().get(pending.getString("item"))
        if (item != null && runCatching { identity(item) == pending.optString("sourceId") }.getOrDefault(false)) {
            val linked = item.jobId?.let { database.downloads().get(it) }?.takeIf { it.state == JobState.COMPLETED && it.outputUri == item.location }
            // A process can stop after the job's sidecar commit but before catalogue linkage.
            val raw = item.lyricUri ?: linked?.sidecarUris?.lines()?.filter { it.startsWith("sub|") }
                ?.map { it.substringAfter('|') }?.singleOrNull()
            if (raw != null) {
                val current = LyricEditor(context).captureForRefetch(raw)
                val text = pending.getString("text")
                if (current.contentEquals(text.toByteArray())) {
                    LyricEditor(context).restoreBaselineLocked(raw, pending.getString("original"), pending.getLong("offset"), text, item.durationMs)
                    database.media().put(item.copy(lyricUri = raw, lyricStatus = LyricStatus.SAVED))
                    linked?.let { database.downloads().lyricStatus(it.id, LyricStatus.SAVED) }
                }
            }
        }
        // No publication happened, or another edit won: no user file is rewritten by recovery.
        restoreJournal.delete()
    }
    data class Options(val settings: Boolean, val playlists: Boolean, val metadata: Boolean, val lyrics: Boolean, val replaceLyrics: Boolean)
    data class Export(val bytes: ByteArray, val media: Int, val skipped: Int, val lyricFailures: Int)
    data class Plan(val data: JSONObject, val matches: Map<String, MediaItem>, val missing: Int, val conflicts: Int)

    /** First/last 64 KiB plus exact size/name/duration; non-seekable providers are skipped safely. */
    private fun identity(item: MediaItem): String {
        require(item.availability == "AVAILABLE" && item.sizeBytes != null && item.sizeBytes > 0)
        val descriptor = context.contentResolver.openFileDescriptor(item.location.toUri(), "r") ?: error("File tidak dapat dibaca.")
        val evidence = ByteArrayOutputStream()
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            val channel = input.channel
            require(channel.size() == item.sizeBytes)
            val buffer = ByteBuffer.allocate(65536)
            for (position in listOf(0L, (item.sizeBytes - 65536).coerceAtLeast(0))) {
                channel.position(position); buffer.clear()
                while (buffer.hasRemaining()) { currentCoroutineCheck(); if (channel.read(buffer) < 0) break }
                evidence.write(buffer.array(), 0, buffer.position())
            }
        }
        evidence.write("\n${item.kind}\n${item.displayName}\n${item.sizeBytes}\n${item.durationMs ?: 0}".toByteArray())
        return MediaBackupCodec.digest(evidence.toByteArray())
    }
    private fun currentCoroutineCheck() { if (Thread.currentThread().isInterrupted) throw CancellationException() }
    private fun settings(): JSONObject = JSONObject().apply {
        put("theme", SettingsStore.themeMode); put("dynamic", SettingsStore.dynamicColor); put("artwork_accent", SettingsStore.artworkAccent)
        put("resume_interruption", SettingsStore.resumeAfterInterruption); put("concurrent", SettingsStore.maxConcurrent)
        put("rename", SettingsStore.autoRename); put("tag", SettingsStore.autoTag); put("artwork", SettingsStore.autoArtwork)
        put("lyrics", SettingsStore.autoLyrics); put("library", SettingsStore.autoLibrary)
    }
    suspend fun export(): Export = withContext(Dispatchers.IO) { MediaFileOperations.mutex.withLock {
        recoverLocked()
        val all = database.media().oldest(MediaBackupCodec.MAX_ITEMS + 1)
        require(all.size <= MediaBackupCodec.MAX_ITEMS) { "Cadangan mendukung maksimum 3000 media." }
        val items = JSONArray(); val map = mutableMapOf<String, String>(); val seen = mutableSetOf<String>()
        var skipped = 0; var lyricFailures = 0; var payloadBytes = 0
        for (item in all) {
            currentCoroutineContext().ensureActive()
            val key = try { identity(item) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { skipped++; continue }
            // Same content appearing at multiple locations is ambiguous on restore; keep no such entry.
            if (!seen.add(key)) { skipped++; continue }
            map[item.uri] = key
            val value = JSONObject().put("id", key).put("title", item.title).put("artist", item.artist).put("album", item.album)
                .put("albumArtist", item.albumArtist).put("genre", item.genre).put("track", item.track).put("year", item.year)
            if (item.lyricUri != null && item.kind == "AUDIO") try {
                val snapshot = LyricEditor(context).backupLocked(item)
                value.put("lyrics", JSONObject().put("original", snapshot.original).put("offset", snapshot.offsetMs))
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { lyricFailures++ }
            payloadBytes += value.toString().toByteArray().size
            require(payloadBytes <= 5 * 1024 * 1024) { "Teks cadangan terlalu besar. Maksimum isi 5 MB (dokumen terbungkus 12 MB)." }
            items.put(value)
        }
        val playlists = JSONArray()
        val personal = database.withTransaction { database.personal().members() to database.personal().all() }
        val entries = personal.first.groupBy { it.playlistId }
        personal.second.forEach { list -> playlists.put(JSONObject().put("id", list.id).put("name", list.name)
            .put("members", MediaBackupCodec.array(entries[list.id].orEmpty().mapNotNull { map[it.mediaUri] }.distinct()))) }
        val payload = JSONObject().put("settings", settings()).put("items", items).put("playlists", playlists)
        Export(MediaBackupCodec.encode(payload), items.length(), skipped, lyricFailures)
    } }
    suspend fun inspect(bytes: ByteArray): Plan = withContext(Dispatchers.IO) { MediaFileOperations.mutex.withLock {
        val data = MediaBackupCodec.decode(bytes)
        val all = database.media().oldest(MediaBackupCodec.MAX_ITEMS + 1)
        require(all.size <= MediaBackupCodec.MAX_ITEMS)
        val available = mutableMapOf<String, MutableList<MediaItem>>()
        for (item in all) {
            currentCoroutineContext().ensureActive()
            try { available.getOrPut(identity(item)) { mutableListOf() }.add(item) }
            catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { /* Unreadable is never a match. */ }
        }
        val items = data.getJSONArray("items"); val matches = mutableMapOf<String, MediaItem>(); var conflicts = 0
        repeat(items.length()) { index ->
            val id = items.getJSONObject(index).getString("id")
            val possible = available[id].orEmpty()
            if (possible.size == 1) matches[id] = possible.single() else if (possible.size > 1) conflicts++
        }
        Plan(data, matches, items.length() - matches.size - conflicts, conflicts)
    } }
    suspend fun restore(plan: Plan, options: Options): String = withContext(Dispatchers.IO) { MediaFileOperations.mutex.withLock {
        LyricPublisher(context).recover(); recoverLocked()
        // Revalidate before any write; never trust a URI from a backup or a stale preview.
        val matched = mutableMapOf<String, MediaItem>()
        plan.matches.forEach { (id, old) ->
            val current = database.media().get(old.uri)
            if (current != null && runCatching { identity(current) == id }.getOrDefault(false)) matched[id] = current
        }
        val records = plan.data.getJSONArray("items")
        if (options.metadata || options.playlists) database.withTransaction {
            if (options.metadata) repeat(records.length()) { index ->
                val record = records.getJSONObject(index); val item = matched[record.getString("id")] ?: return@repeat
                database.media().put(item.copy(title = record.getString("title"), artist = record.getString("artist"), album = record.getString("album"),
                    albumArtist = record.getString("albumArtist"), genre = record.getString("genre"), track = record.getString("track"), year = record.getString("year")))
            }
            if (options.playlists) {
                val dao = database.personal(); val lists = plan.data.getJSONArray("playlists")
                require((dao.all().map { it.id } + (0 until lists.length()).map { lists.getJSONObject(it).getString("id") }).distinct().count { it != PersonalLibrary.FAVORITES } <= 200)
                repeat(lists.length()) { index ->
                    val list = lists.getJSONObject(index); val id = list.getString("id")
                    // Existing playlists are merged; their name and members remain intact.
                    dao.insert(PersonalPlaylist(id, list.getString("name"), System.currentTimeMillis()))
                    val members = list.getJSONArray("members")
                    repeat(members.length()) { memberIndex -> matched[members.getString(memberIndex)]?.let { item ->
                        if (!dao.contains(id, item.uri)) {
                            require(dao.count(id) < 3000); val next = dao.nextPosition(id)
                            dao.add(PlaylistMember(id, item.uri, next))
                        }
                    } }
                }
            }
        }
        var restored = 0; var skipped = 0; var failed = 0
        if (options.lyrics) repeat(records.length()) { index ->
            currentCoroutineContext().ensureActive()
            val record = records.getJSONObject(index); val old = matched[record.getString("id")] ?: return@repeat
            val item = database.media().get(old.uri) ?: return@repeat
            val lyric = record.optJSONObject("lyrics") ?: return@repeat
            if (item.kind != "AUDIO") return@repeat
            try {
                val original = lyric.getString("original"); val offset = lyric.getLong("offset")
                val text = LrcTiming.shift(original, offset, item.durationMs)
                require(LrcLines.parse(text).isNotEmpty())
                val expected = item.lyricUri?.let { mapOf(it to LyricEditor(context).captureForRefetch(it)) }.orEmpty()
                val same = expected.values.singleOrNull()?.contentEquals(text.toByteArray()) == true
                if (expected.isNotEmpty() && !same && !options.replaceLyrics) { skipped++; return@repeat }
                saveRestore(JSONObject().put("sourceId", record.getString("id")).put("item", item.uri).put("original", original).put("offset", offset).put("text", text))
                val raw = if (same) requireNotNull(item.lyricUri) else LyricPublisher(context).publish(item, text, expected)
                database.media().put(item.copy(lyricUri = raw, lyricStatus = LyricStatus.SAVED))
                item.jobId?.let { database.downloads().lyricStatus(it, LyricStatus.SAVED) }
                LyricEditor(context).restoreBaselineLocked(raw, original, offset, text, item.durationMs)
                restoreJournal.delete()
                restored++
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) {
                failed++
                // Resolve this file before the next intent can replace its recovery record.
                try { recoverLocked() } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error("Pemulihan lirik tertunda; buka ulang aplikasi sebelum melanjutkan. $restored sudah selesai, $failed gagal.") }
            }
        }
        if (options.settings) withContext(Dispatchers.Main) { applySettings(plan.data.getJSONObject("settings")) }
        "Pemulihan selesai: ${matched.size} media cocok; $restored lirik dipulihkan, $skipped dilewati, $failed gagal. ${plan.missing + plan.conflicts + plan.matches.size - matched.size} media hilang/ambigu/berubah dilewati. File audio/video tidak diubah."
    } }
    private fun applySettings(data: JSONObject) {
        data.optString("theme").takeIf { it in setOf("system", "light", "dark") }?.let { SettingsStore.setThemeMode(context, it) }
        for (key in listOf("rename", "tag", "artwork", "lyrics", "library")) if (data.opt(key) is Boolean) SettingsStore.setSmart(context, key, data.getBoolean(key))
        if (data.opt("dynamic") is Boolean) SettingsStore.setDynamicColor(context, data.getBoolean("dynamic"))
        if (data.opt("artwork_accent") is Boolean) SettingsStore.setArtworkAccent(context, data.getBoolean("artwork_accent"))
        if (data.opt("resume_interruption") is Boolean) SettingsStore.setResumeAfterInterruption(context, data.getBoolean("resume_interruption"))
        data.optInt("concurrent").takeIf { it in 1..4 }?.let { SettingsStore.setMaxConcurrent(context, it) }
    }
}
