package id.local.tubeflow.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Metadata satu track Spotify hasil pembacaan halaman embed publik.
 *
 * Spotify TIDAK menyediakan audio yang bisa diunduh: stream-nya ber-DRM Widevine dan engine yt-dlp
 * tidak punya extractor Spotify. Karena itu metadata ini hanya dipakai untuk menamai, menandai,
 * dan mencocokkan track ke video YouTube yang menjadi sumber audio sebenarnya.
 */
data class SpotifyTrack(
    val id: String,
    val title: String,
    val artists: String,
    val durationSeconds: Long,
    val coverUrl: String,
    val url: String,
    val playable: Boolean = true,
)

enum class SpotifyKind { TRACK, ALBUM, PLAYLIST }

data class SpotifyRef(val kind: SpotifyKind, val id: String)

/** Daftar track album/playlist dari halaman embed; track memakai sampul koleksinya. */
data class SpotifyCollection(
    val id: String,
    val kind: SpotifyKind,
    val title: String,
    val owner: String,
    val coverUrl: String,
    val url: String,
    val tracks: List<SpotifyTrack>,
) {
    val playableCount: Int get() = tracks.count { it.playable }
    val totalSeconds: Long get() = tracks.sumOf { it.durationSeconds }
}

/**
 * Identitas tautan Spotify dan pembaca metadata publiknya (tanpa login, tanpa API key).
 *
 * Sumber: halaman `https://open.spotify.com/embed/<jenis>/<id>` memuat `__NEXT_DATA__` berisi
 * metadata track, atau daftar track untuk album/playlist (halaman embed membatasi playlist
 * hingga 100 track). Halaman ini milik Spotify dan dapat berubah sewaktu-waktu — kegagalan
 * parsing dilaporkan sebagai pesan pengguna, bukan crash.
 */
object Spotify {
    const val PLATFORM_ID = "spotify"
    private const val EMBED = "https://open.spotify.com/embed/"
    private const val CACHE_MILLIS = 7L * 24 * 60 * 60 * 1000
    private const val MAX_PAGE_BYTES = 1024 * 1024
    private val hosts = setOf("open.spotify.com", "www.open.spotify.com")
    private val idPattern = Regex("[A-Za-z0-9]{22}")
    private val kinds = mapOf(
        "track" to SpotifyKind.TRACK,
        "album" to SpotifyKind.ALBUM,
        "playlist" to SpotifyKind.PLAYLIST,
    )
    private val uriForm = Regex("""(?i)^spotify:(track|album|playlist):([A-Za-z0-9]{22})$""")
    private val nextData = Regex(
        """<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )

    /**
     * Identitas tautan `https://open.spotify.com/<jenis>/<id>` (termasuk `/intl-xx/…`) atau URI
     * `spotify:<jenis>:<id>`. Hanya track/album/playlist; artist dan episode podcast ditolak.
     */
    fun ref(raw: String): SpotifyRef? = runCatching {
        val value = raw.trim()
        uriForm.matchEntire(value)?.let { match ->
            return SpotifyRef(kinds.getValue(match.groupValues[1].lowercase()), match.groupValues[2])
        }
        val uri = URI(value)
        val host = uri.host?.lowercase() ?: return null
        if (uri.scheme != "https" || uri.userInfo != null || (uri.port != -1 && uri.port != 443)) return null
        if (host !in hosts) return null
        val parts = uri.path.orEmpty().trim('/').split('/').filter(String::isNotBlank)
        val index = parts.indexOfFirst { it.lowercase() in kinds }
        if (index < 0 || index + 1 >= parts.size) return null
        val kind = kinds[parts[index].lowercase()] ?: return null
        val id = parts[index + 1].takeIf(idPattern::matches) ?: return null
        SpotifyRef(kind, id)
    }.getOrNull()

    /** ID track dari tautan Spotify; null untuk album/playlist (alur koleksinya terpisah). */
    fun trackId(raw: String): String? = ref(raw)?.takeIf { it.kind == SpotifyKind.TRACK }?.id

    /** True bila tautan ini track Spotify — dipakai UI sebelum inspeksi engine mana pun. */
    fun isTrackLink(raw: String): Boolean = trackId(raw) != null

    /** True bila tautan ini album/playlist Spotify. */
    fun isCollectionLink(raw: String): Boolean = ref(raw)?.kind.let { it == SpotifyKind.ALBUM || it == SpotifyKind.PLAYLIST }

    /** Judul + artis; pencarian YouTube menaruh artis lebih dulu karena banyak judul video memuat keduanya. */
    fun searchQuery(track: SpotifyTrack): String =
        listOf(track.artists, track.title).map(String::trim).filter(String::isNotBlank).joinToString(" ").take(200)

    /**
     * Metadata sumber untuk job: identitas Spotify (judul/artis/sampul/durasi) + URL video YouTube
     * hasil pencocokan. `platformId` sengaja Spotify agar riwayat menampilkan asal tautannya,
     * sedangkan `url` tetap URL YouTube supaya retry/resume memakai jalur engine yang sudah ada.
     */
    fun toMetadata(track: SpotifyTrack, candidate: VideoMetadata): VideoMetadata = VideoMetadata(
        title = track.title,
        uploader = candidate.uploader,
        durationSeconds = candidate.durationSeconds.takeIf { it > 0 } ?: track.durationSeconds,
        thumbnail = track.coverUrl.ifBlank { candidate.thumbnail },
        url = candidate.url,
        artist = track.artists,
        track = track.title,
        creator = candidate.uploader,
        platformId = PLATFORM_ID,
        sourceId = track.id,
        extractor = "spotify+youtube",
    )

    /** Baca metadata satu track, memakai cache privat 7 hari agar tautan yang sama tidak diambil berulang. */
    suspend fun fetchTrack(context: Context, url: String, now: Long = System.currentTimeMillis()): SpotifyTrack = withContext(Dispatchers.IO) {
        val reference = ref(url) ?: throw IllegalArgumentException("Tautan bukan track Spotify yang valid.")
        if (reference.kind != SpotifyKind.TRACK) throw IllegalArgumentException("Tautan ini album/playlist, bukan satu track.")
        val cache = File(context.cacheDir, "spotify-cache")
        readTrackCache(cache, reference.id, now)?.let { return@withContext it }
        val track = parse(request(SpotifyKind.TRACK, reference.id), url)
            ?: throw IllegalStateException("Detail track Spotify tidak bisa dibaca. Halaman Spotify mungkin berubah; coba lagi nanti.")
        writeTrackCache(cache, track)
        track
    }

    /** Baca daftar track album/playlist dari satu halaman embed (playlist dibatasi Spotify hingga 100 track). */
    suspend fun fetchCollection(context: Context, url: String, now: Long = System.currentTimeMillis()): SpotifyCollection = withContext(Dispatchers.IO) {
        val reference = ref(url) ?: throw IllegalArgumentException("Tautan bukan album/playlist Spotify yang valid.")
        if (reference.kind == SpotifyKind.TRACK) throw IllegalArgumentException("Tautan ini satu track, bukan album/playlist.")
        val cache = File(context.cacheDir, "spotify-cache")
        readCollectionCache(cache, reference, now)?.let { return@withContext it }
        val collection = parseCollection(request(reference.kind, reference.id), url)
            ?: throw IllegalStateException("Daftar lagu Spotify tidak bisa dibaca. Halaman Spotify mungkin berubah; coba lagi nanti.")
        writeCollectionCache(cache, collection)
        collection
    }

    /** Parse halaman embed track; null bila tidak memuat entity track (mis. tautan album atau halaman berubah bentuk). */
    fun parse(html: String, url: String): SpotifyTrack? = runCatching {
        val entity = entityOf(html) ?: return null
        val type = entity.optString("type")
        if (type.isNotBlank() && type != "track") return null
        val title = entity.optString("title").ifBlank { entity.optString("name") }.trim()
        if (title.isBlank()) return null
        SpotifyTrack(
            id = entity.optString("id").ifBlank { trackId(url).orEmpty() },
            title = title,
            artists = entity.optJSONArray("artists").names(),
            durationSeconds = seconds(entity.optLong("duration")),
            coverUrl = coverOf(entity),
            url = url.trim(),
        )
    }.getOrNull()

    /** Parse halaman embed album/playlist menjadi daftar track; entri episode/tipe lain dilewati. */
    fun parseCollection(html: String, url: String): SpotifyCollection? = runCatching {
        val entity = entityOf(html) ?: return null
        val kind = kinds[entity.optString("type").lowercase()]?.takeIf { it != SpotifyKind.TRACK } ?: return null
        val title = entity.optString("title").ifBlank { entity.optString("name") }.trim()
        if (title.isBlank()) return null
        val cover = coverOf(entity)
        val tracks = entity.optJSONArray("trackList").objects().mapNotNull { entry ->
            if (entry.optString("entityType").ifBlank { "track" } != "track") return@mapNotNull null
            val id = entry.optString("uri").substringAfterLast(':').takeIf(idPattern::matches) ?: return@mapNotNull null
            val trackTitle = entry.optString("title").trim()
            if (trackTitle.isBlank()) return@mapNotNull null
            SpotifyTrack(
                id = id,
                title = trackTitle,
                artists = entry.optString("subtitle").trim(),
                durationSeconds = seconds(entry.optLong("duration")),
                coverUrl = cover,
                url = "https://open.spotify.com/track/$id",
                playable = entry.optBoolean("isPlayable", true),
            )
        }
        SpotifyCollection(
            id = entity.optString("id").ifBlank { ref(url)?.id.orEmpty() },
            kind = kind,
            title = title,
            owner = entity.optString("subtitle").trim(),
            coverUrl = cover,
            url = url.trim(),
            tracks = tracks,
        )
    }.getOrNull()

    private fun entityOf(html: String): JSONObject? = runCatching {
        val raw = nextData.find(html)?.groupValues?.getOrNull(1) ?: return null
        JSONObject(raw).optJSONObject("props")
            ?.optJSONObject("pageProps")
            ?.optJSONObject("state")
            ?.optJSONObject("data")
            ?.optJSONObject("entity")
    }.getOrNull()

    private fun coverOf(entity: JSONObject): String {
        // Playlist memakai coverArt.sources; album/track memakai visualIdentity.image.
        entity.optJSONObject("coverArt")?.optJSONArray("sources").objects()
            .firstNotNullOfOrNull { it.optString("url").takeIf(String::isNotBlank) }
            ?.let { return it }
        return entity.optJSONObject("visualIdentity")?.optJSONArray("image").objects()
            .filter { it.optString("url").isNotBlank() }
            .maxByOrNull { maxOf(it.optInt("maxWidth"), it.optInt("maxHeight")) }
            ?.optString("url").orEmpty()
    }

    private fun seconds(millis: Long): Long = if (millis > 0) (millis + 500) / 1000 else 0

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }

    private fun JSONArray?.names(): String {
        if (this == null) return ""
        return (0 until length()).mapNotNull { optJSONObject(it)?.optString("name")?.trim()?.takeIf(String::isNotBlank) }
            .joinToString(" & ")
    }

    private fun request(kind: SpotifyKind, id: String): String {
        val connection = URL("$EMBED${kind.name.lowercase()}/$id").openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) TubeFlow")
        connection.setRequestProperty("Accept-Language", "en")
        try {
            when (val code = connection.responseCode) {
                200 -> connection.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    val output = java.io.ByteArrayOutputStream()
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        require(output.size() + read <= MAX_PAGE_BYTES) { "Halaman Spotify terlalu besar." }
                        output.write(buffer, 0, read)
                    }
                    return output.toString("UTF-8")
                }
                404 -> error("Halaman Spotify tidak ditemukan — periksa tautannya.")
                429 -> error("Spotify membatasi permintaan sementara. Coba lagi beberapa saat lagi.")
                in 400..499 -> error("Spotify menolak permintaan (HTTP $code). Pastikan kontennya publik.")
                in 500..599 -> error("Spotify sedang bermasalah (HTTP $code). Coba lagi nanti.")
                else -> error("Metadata Spotify tidak bisa dibaca (HTTP $code).")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun readTrackCache(directory: File, id: String, now: Long): SpotifyTrack? = runCatching {
        val file = File(directory, "$id.json")
        if (!file.isFile || now - file.lastModified() > CACHE_MILLIS) return null
        val json = JSONObject(file.readText())
        SpotifyTrack(
            id = json.optString("id").ifBlank { id },
            title = json.optString("title"),
            artists = json.optString("artists"),
            durationSeconds = json.optLong("durationSeconds"),
            coverUrl = json.optString("coverUrl"),
            url = json.optString("url"),
        ).takeIf { it.title.isNotBlank() }
    }.getOrNull()

    private fun writeTrackCache(directory: File, track: SpotifyTrack) {
        writeJson(directory, "${track.id}.json", JSONObject()
            .put("id", track.id)
            .put("title", track.title)
            .put("artists", track.artists)
            .put("durationSeconds", track.durationSeconds)
            .put("coverUrl", track.coverUrl)
            .put("url", track.url))
    }

    private fun readCollectionCache(directory: File, reference: SpotifyRef, now: Long): SpotifyCollection? = runCatching {
        val file = File(directory, "${reference.kind.name.lowercase()}-${reference.id}.json")
        if (!file.isFile || now - file.lastModified() > CACHE_MILLIS) return null
        val json = JSONObject(file.readText())
        val kind = runCatching { SpotifyKind.valueOf(json.optString("kind")) }.getOrNull() ?: return null
        val title = json.optString("title")
        if (title.isBlank()) return null
        val cover = json.optString("coverUrl")
        val tracks = json.optJSONArray("tracks").objects().mapNotNull { entry ->
            val id = entry.optString("id").takeIf(idPattern::matches) ?: return@mapNotNull null
            val trackTitle = entry.optString("title")
            if (trackTitle.isBlank()) return@mapNotNull null
            SpotifyTrack(
                id = id,
                title = trackTitle,
                artists = entry.optString("artists"),
                durationSeconds = entry.optLong("durationSeconds"),
                coverUrl = cover,
                url = "https://open.spotify.com/track/$id",
                playable = entry.optBoolean("playable", true),
            )
        }
        SpotifyCollection(
            id = json.optString("id").ifBlank { reference.id },
            kind = kind,
            title = title,
            owner = json.optString("owner"),
            coverUrl = cover,
            url = json.optString("url"),
            tracks = tracks,
        )
    }.getOrNull()

    private fun writeCollectionCache(directory: File, collection: SpotifyCollection) {
        val tracks = JSONArray()
        collection.tracks.forEach { track ->
            tracks.put(JSONObject()
                .put("id", track.id)
                .put("title", track.title)
                .put("artists", track.artists)
                .put("durationSeconds", track.durationSeconds)
                .put("playable", track.playable))
        }
        writeJson(directory, "${collection.kind.name.lowercase()}-${collection.id}.json", JSONObject()
            .put("id", collection.id)
            .put("kind", collection.kind.name)
            .put("title", collection.title)
            .put("owner", collection.owner)
            .put("coverUrl", collection.coverUrl)
            .put("url", collection.url)
            .put("tracks", tracks))
    }

    private fun writeJson(directory: File, name: String, json: JSONObject) {
        runCatching {
            if (!directory.isDirectory && !directory.mkdirs()) return
            val temporary = File(directory, "$name.tmp")
            temporary.writeText(json.toString())
            if (!temporary.renameTo(File(directory, name))) temporary.delete()
        }
    }
}
