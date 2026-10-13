package id.local.tubeflow.core

import kotlin.math.abs

/**
 * Status lirik yang disimpan di `DownloadJob.lyricStatus` dan dilaporkan ke pengguna (BUG-014).
 * Kosong berarti lirik tidak diminta untuk job ini.
 */
object LyricStatus {
    const val SAVED = "tersimpan"
    const val NOT_FOUND = "tidak_ditemukan"
    const val NO_MATCH = "tidak_cocok"
    const val INSTRUMENTAL = "instrumental"
    const val NETWORK = "jaringan"
    const val SAVE_FAILED = "gagal_simpan"

    /** Label ramah pengguna untuk kartu riwayat dan notifikasi hasil. */
    fun label(status: String): String = when (status) {
        SAVED -> "Lirik tersimpan"
        NOT_FOUND -> "Lirik tidak tersedia di penyedia lirik"
        NO_MATCH -> "Lirik belum cocok dengan versi audio"
        INSTRUMENTAL -> "Lagu instrumental — tanpa lirik"
        NETWORK -> "Pencarian lirik gagal (periksa jaringan)"
        SAVE_FAILED -> "Lirik gagal disimpan"
        else -> status
    }

    /** Status yang boleh dicari ulang tanpa mengunduh audio lagi. */
    fun retryable(status: String): Boolean = status in setOf(NOT_FOUND, NO_MATCH, NETWORK, SAVE_FAILED)
}

/** Hasil terstruktur satu pencarian lirik (BUG-014): [text] hanya berisi bila status [LyricStatus.SAVED]. */
data class LyricOutcome(val status: String, val text: String? = null)

/**
 * Pencocokan lirik berbasis identitas dan versi rekaman (BUG-013 / OPT-001).
 *
 * Aturan utama:
 * - Syarat judul dan artis dinilai TERPISAH — judul persis dengan artis yang salah tetap ditolak.
 * - Penanda versi (live/cover/remix/dst.) harus sama di kedua pihak: timestamp versi berbeda tidak pernah sinkron.
 * - Durasi rekaman menjadi gerbang; LRCLIB sendiri memakai kecocokan ±2 detik pada endpoint get
 *   (https://lrclib.net/docs). Durasi yang jauh berarti versi berbeda.
 * - Artis kosong TIDAK otomatis mendapat skor artis penuh — hanya judul dan durasi yang bisa diverifikasi.
 */
object LyricMatch {
    data class Query(val title: String, val artist: String = "", val durationSeconds: Long = 0L)

    data class Candidate(
        val title: String,
        val artist: String = "",
        val durationSeconds: Long = -1L,
        val instrumental: Boolean = false,
        val syncedLyrics: String? = null,
    )

    /** Skor minimum penerimaan; di bawah ini dianggap tidak cukup yakin untuk menulis .lrc. */
    const val ACCEPT = 0.62
    private const val TITLE_MIN = 0.55
    private const val ARTIST_MIN = 0.5
    /** Same duration gate for exact and fallback queries; intros are not proven timestamp offsets. */
    private const val DURATION_TOLERANCE_SECONDS = 2L

    /** Kata judul video yang tidak memengaruhi identitas lagu (buang sebelum membandingkan). */
    private val junk = setOf(
        "official", "music", "video", "videos", "lyric", "lyrics", "audio", "visualizer", "visualiser",
        "hq", "hd", "uhd", "4k", "8k", "60fps", "mv", "clip", "topic", "full", "album",
        "remaster", "remastered", "explicit", "clean", "teaser",
    )

    /** Penanda versi yang mengubah isi/timestamp lagu — beda penanda = beda rekaman, tolak (BUG-013). */
    private val versionWords = setOf(
        "live", "acoustic", "cover", "remix", "demo", "instrumental", "karaoke", "unplugged",
        "session", "nightcore", "sped", "slowed", "extended", "reprise", "dj", "rmx", "slow", "acustik", "bootleg", "mashup", "megamix", "reverb",
    )

    private fun norm(value: String): String =
        value.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()

    private fun tokens(value: String): Set<String> =
        norm(value).split(' ').filterTo(mutableSetOf()) { it.isNotEmpty() }

    /** Dipakai juga oleh [Lyrics.tidy] untuk mempertahankan penanda versi (BUG-013/R3). */
    internal fun versionSet(value: String): Set<String> =
        tokens(value).filter { it in versionWords }.map { when(it) { "rmx" -> "remix"; "slow" -> "slowed"; "acustik" -> "acoustic"; else -> it } }.toSet()

    /** Judul tanpa kata sampah, tetap mempertahankan penanda versi (hanya dibuang bila salah satu pihak tidak punya). */
    private fun coreTitle(value: String): String =
        tokens(value).filterNot { it in junk }.joinToString(" ")

    /** Skor kemiripan 0..1: persis 1.0, substring 0.85, bigram Dice sebagai pengaman. */
    fun similarity(a: String, b: String): Double {
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a == b) return 1.0
        if (a.contains(b) || b.contains(a)) return 0.85
        fun bigrams(value: String) = (0 until (value.length - 1)).map { value.substring(it, it + 2) }
        val left = bigrams(a)
        val right = bigrams(b)
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val pool = right.toMutableList()
        var hit = 0
        for (gram in left) if (pool.remove(gram)) hit++
        return 2.0 * hit / (left.size + right.size)
    }

    /**
     * Skor kandidat 0..1; nilai < 0 berarti TOLAK (identitas/versi tidak aman).
     * Dipakai untuk respons /api/get maupun kandidat /api/search — keduanya wajib lewat jalur ini.
     */
    fun score(query: Query, candidate: Candidate): Double {
        if (norm(query.title).isEmpty() || norm(candidate.title).isEmpty()) return -1.0
        // Gerbang versi: "(Live)" vs studio, remix vs asli — timestamp pasti tidak sinkron.
        if (versionSet(query.title) != versionSet(candidate.title)) return -1.0
        // Search must not bypass the provider's exact duration gate.
        if (query.durationSeconds > 0 && candidate.durationSeconds > 0) {
            val diff = abs(query.durationSeconds - candidate.durationSeconds)
            if (diff > DURATION_TOLERANCE_SECONDS) return -1.0
        }
        val titleScore = similarity(coreTitle(query.title), coreTitle(candidate.title))
        if (titleScore < TITLE_MIN) return -1.0
        // Identitas asli tanpa artis DAN durasi: tak ada yang bisa diverifikasi — hanya judul persis yang aman
        // (kandidat mirip otomatis ditolak; kasus meragukan lain butuh pemilihan manual OPT-003) (BUG-013/R3).
        if (query.artist.isBlank() && query.durationSeconds <= 0L && titleScore < 1.0) return -1.0
        if (query.artist.isNotBlank() && candidate.artist.isBlank()) return -1.0
        val artistKnown = query.artist.isNotBlank() && candidate.artist.isNotBlank()
        val artistScore = if (artistKnown) similarity(norm(query.artist), norm(candidate.artist)) else null
        // Artis diketahui di kedua pihak tapi tidak cocok → lagu lain, judul persis pun ditolak.
        if (artistScore != null && artistScore < ARTIST_MIN) return -1.0
        return if (artistScore != null) 0.6 * titleScore + 0.4 * artistScore else titleScore
    }

    /** Unknown duration cannot establish that timed lyrics belong to this recording. */
    fun acceptsTimedLyrics(query: Query, candidate: Candidate): Boolean =
        query.durationSeconds > 0 && candidate.durationSeconds > 0 && score(query, candidate) >= ACCEPT
}

/**
 * Pencari lirik tersinkron dari LRCLIB (terbuka, tanpa kunci API).
 * [fetch] tidak pernah melempar: kegagalan jaringan dilaporkan sebagai [LyricStatus.NETWORK]
 * agar pengguna bisa membedakan "tidak ada" dengan "gagal" (BUG-014).
 */
object Lyrics {

    /**
     * Cari lirik untuk nama konsisten "Judul - Artis" (judul dulu — urutan yang dipakai nama file)
     * atau judul polos, pada durasi video [durationSeconds].
     */
    /** Resolve ambiguous order using provider records, never the position of a name alone. */
    fun resolveIdentity(identity: SongIdentity, durationSeconds: Long): SongIdentity {
        if (identity.artist != null || durationSeconds <= 0) return identity
        val hypotheses = linkedSetOf(identity)
        if (identity.remixer.isBlank()) {
            val parts = identity.title.split(" - ").map { it.trim() }
            if (parts.size == 2 && parts.all { it.isNotBlank() }) {
                hypotheses += identity.copy(title=parts[1], artist=parts[0])
                hypotheses += identity.copy(title=parts[0], artist=parts[1])
            }
        }
        val accepted = linkedMapOf<Pair<String,String>, SongIdentity>()
        for (hypothesis in hypotheses) {
            val query = LyricMatch.Query(hypothesis.lyricTitle, hypothesis.artist.orEmpty(), durationSeconds)
            val path = "/api/search?track_name=${enc(hypothesis.title)}" + hypothesis.artist?.let { "&artist_name=${enc(it)}" }.orEmpty()
            val response = get(path) as? Res.Body ?: continue
            val array = runCatching { org.json.JSONArray(response.text) }.getOrNull() ?: continue
            for (index in 0 until array.length()) {
                val record = array.optJSONObject(index) ?: continue
                val candidate = candidateOf(record)
                if (candidate.artist.isBlank() || candidate.durationSeconds <= 0 ||
                    abs(durationSeconds - candidate.durationSeconds) > 2 || LyricMatch.score(query,candidate) < 0.95) continue
                val resolved = hypothesis.copy(title=candidate.title, artist=candidate.artist, evidence="provider_identity")
                accepted[candidate.title.lowercase() to candidate.artist.lowercase()] = resolved
            }
        }
        return accepted.values.singleOrNull() ?: identity
    }

    /** Unstructured legacy input carries no evidence of an artist; never split merely on a dash. */
    fun fetch(title: String, durationSeconds: Long): LyricOutcome = fetch(TitleSplit.identify(title), durationSeconds)

    fun fetch(identity: SongIdentity, durationSeconds: Long): LyricOutcome {
        val rawTrack = identity.lyricTitle
        val rawArtist = identity.artist.orEmpty()
        // Lookup variations may omit version/artist terms; acceptance ALWAYS uses the full identity.
        val validation = LyricMatch.Query(rawTrack, rawArtist, durationSeconds)
        val pairs = linkedSetOf<Pair<String, String>>()
        if (rawTrack.isNotBlank()) {
            pairs += rawTrack to rawArtist
            pairs += rawTrack to ""
            pairs += tidy(rawTrack) to rawArtist
            if (identity.title != rawTrack) {
                pairs += identity.title to rawArtist
                pairs += identity.title to ""
            }
        }
        if (pairs.isEmpty()) return LyricOutcome(LyricStatus.NOT_FOUND)

        var sawFailure = false
        var sawUnmatchedLyrics = false
        var bestScore = 0.0
        var bestDurationDifference = Long.MAX_VALUE
        var bestText: String? = null

        for ((track, artist) in pairs) {
            val query = validation
            val exact = buildString {
                append("track_name=").append(enc(track))
                if (artist.isNotBlank()) append("&artist_name=").append(enc(artist))
                // LRCLIB mencocokkan durasi ±2 detik pada get — kirim bila durasi valid (BUG-013/R3).
                if (durationSeconds > 0) append("&duration=").append(durationSeconds)
            }
            // 1) Endpoint exact: identitas cocok di database LRCLIB; instrumental berarti tanpa lirik.
            when (val res = get("/api/get?$exact")) {
                Res.Fail -> sawFailure = true
                Res.Rejected -> Unit // provider menolak query (bukan jaringan) — lanjut ke pencarian
                Res.NotFound -> Unit
                is Res.Body -> {
                    val record = runCatching { org.json.JSONObject(res.text) }.getOrNull()
                    if (record != null) {
                        val candidate = candidateOf(record)
                        if (LyricMatch.acceptsTimedLyrics(query, candidate)) {
                            if (candidate.instrumental) return LyricOutcome(LyricStatus.INSTRUMENTAL)
                            cleanLyrics(candidate.syncedLyrics, durationSeconds)?.let { return LyricOutcome(LyricStatus.SAVED, it) }
                        }
                        if (!candidate.syncedLyrics.isNullOrBlank()) sawUnmatchedLyrics = true
                    }
                }
            }
            // 2) Pencarian by-field lalu bebas: semua kandidat dinilai LyricMatch, bukan skor buta.
            for (path in listOf(
                "/api/search?track_name=${enc(track)}" + (if (artist.isBlank()) "" else "&artist_name=${enc(artist)}"),
                "/api/search?q=${enc(listOf(track, artist).filter { it.isNotBlank() }.joinToString(" "))}",
            )) {
                when (val res = get(path)) {
                    Res.Fail -> sawFailure = true
                    Res.Rejected -> Unit // jawaban definitif provider: bukan jaringan, anggap tidak ada
                    Res.NotFound -> Unit
                    is Res.Body -> {
                        val array = runCatching { org.json.JSONArray(res.text) }.getOrNull() ?: continue
                        for (index in 0 until array.length()) {
                            val record = array.optJSONObject(index) ?: continue
                            val candidate = candidateOf(record)
                            val score = LyricMatch.score(query, candidate)
                            if (!LyricMatch.acceptsTimedLyrics(query, candidate)) {
                                if (!candidate.syncedLyrics.isNullOrBlank()) sawUnmatchedLyrics = true
                                continue
                            }
                            if (candidate.instrumental) return LyricOutcome(LyricStatus.INSTRUMENTAL)
                            val text = cleanLyrics(candidate.syncedLyrics, durationSeconds)
                            if (text == null) {
                                if (!candidate.syncedLyrics.isNullOrBlank()) sawUnmatchedLyrics = true
                                continue
                            }
                            val durationDifference = abs(durationSeconds - candidate.durationSeconds)
                            // Equal identity scores should prefer the closest recording duration,
                            // rather than whichever entry the provider returned first.
                            if (score > bestScore || score == bestScore && durationDifference < bestDurationDifference) {
                                bestScore = score
                                bestDurationDifference = durationDifference
                                bestText = text
                            }
                        }
                    }
                }
            }
            if (bestText != null) break
        }

        bestText?.let { return LyricOutcome(LyricStatus.SAVED, it) }
        // Ada kegagalan jaringan di jalur mana pun + tak ada kandidat diterima = pencarian belum lengkap,
        // bukan “lagu tidak ada” — pengguna perlu bisa mencoba lagi (BUG-014/R4). Provider yang menjawab
        // tegas (2xx/4xx tanpa kandidat) = memang tidak tersedia di penyedia, bukan masalah jaringan.
        return LyricOutcome(when {
            sawFailure -> LyricStatus.NETWORK
            sawUnmatchedLyrics -> LyricStatus.NO_MATCH
            else -> LyricStatus.NOT_FOUND
        })
    }

    private fun candidateOf(record: org.json.JSONObject) = LyricMatch.Candidate(
        title = record.optString("trackName"),
        artist = record.optString("artistName"),
        durationSeconds = record.optLong("duration", -1L).takeIf { it > 0 } ?: -1L,
        instrumental = record.optBoolean("instrumental"),
        syncedLyrics = if (record.isNull("syncedLyrics")) null else record.optString("syncedLyrics").takeIf { it.isNotBlank() },
    )

    private val groupPattern = Regex("""\s*[(\[][^\)\]]*[)\]]""")
    /**
     * Buang embel-embel promosi "(Official Video)" tanpa membuang penanda versi "(Live)" —
     * varian kedua dulu membangun query identitas baru dan lolos menerima versi studio (BUG-013/R3).
     */
    private fun tidy(value: String): String =
        groupPattern.replace(value) { match ->
            if (LyricMatch.versionSet(match.value).isNotEmpty()) match.value else " "
        }.replace(Regex("""\s+"""), " ").trim()

    private val timestamp = Regex("""\[(\d+):(\d{2})(?:\.(\d{1,3}))?\]""")
    private val metadataTag = Regex("""\[(?:ar|ti|al|by|re|ve|length|offset):[^\]]*\]""", RegexOption.IGNORE_CASE)

    /** Validate timed lines against the recording; preserve metadata such as the player's offset. */
    private fun cleanLyrics(text: String?, durationSeconds: Long): String? {
        if (text.isNullOrBlank() || durationSeconds <= 0) return null
        val lines = mutableListOf<String>()
        var hasTimedText = false
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (metadataTag.matches(line)) {
                if (line.startsWith("[offset:", ignoreCase=true) &&
                    line.substringAfter(':').dropLast(1).trim().toLongOrNull() == null) return null
                lines += line
                continue
            }
            var end = 0
            while (true) {
                val match = timestamp.find(line, end)?.takeIf { it.range.first == end } ?: break
                val minutes = match.groupValues[1].toDoubleOrNull() ?: return null
                val seconds = match.groupValues[2].toInt()
                val fraction = match.groupValues[3].let { if (it.isEmpty()) 0.0 else "0.$it".toDouble() }
                if (seconds >= 60 || minutes * 60 + seconds + fraction > durationSeconds + 2.0) return null
                end = match.range.last + 1
            }
            if (end == 0 || line.substring(end).startsWith('[')) return null
            if (line.substring(end).isBlank()) continue
            hasTimedText = true
            lines += line
        }
        return if (hasTimedText) lines.joinToString("\n", postfix="\n") else null
    }

    private sealed interface Res {
        data class Body(val text: String) : Res
        object NotFound : Res
        object Fail : Res
        /** Provider MENJAWAB tapi menolak permintaan (4xx selain 429) — bukan gangguan jaringan. */
        object Rejected : Res
    }

    /**
     * Asal permintaan; diganti oleh tes fixture host (LyricsFetchTest) — di produksi selalu LRCLIB.
     * Bukan kontrak API publik; jangan pakai untuk tujuan lain.
     */
    internal var baseUrl = "https://lrclib.net"

    /**
     * Isi HTTP GET sederhana. 404 = tidak ditemukan; 400/422/451 = provider menolak query itu sendiri
     * (bukan jaringan); status lain (401/403/408/429/5xx/exception) = gagal sementara, bisa dicoba lagi.
     */
    @Volatile internal var responseCache: LyricsResponseCache? = null
    fun clearCache() { responseCache?.clear() }
    private fun get(path: String): Res {
        val cache = responseCache ?: return networkGet(path)
        val cached = cache.request("$baseUrl$path") {
            when (val result = networkGet(path)) {
                is Res.Body -> LyricsResponseCache.Response(200, result.text)
                Res.NotFound -> LyricsResponseCache.Response(404, "")
                Res.Rejected -> LyricsResponseCache.Response(400, "")
                Res.Fail -> LyricsResponseCache.Response(503, "")
            }
        }
        return when (cached.code) { 200 -> Res.Body(cached.text); 404 -> Res.NotFound; 400 -> Res.Rejected; else -> Res.Fail }
    }
    private fun networkGet(path: String): Res = try {
        val connection = java.net.URL("$baseUrl$path").openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.setRequestProperty("User-Agent", "TubeFlow/1.0")
        try {
            when (val code = connection.responseCode) {
                200 -> Res.Body(connection.inputStream.use { input ->
                    val buffer = ByteArray(8192); val out = java.io.ByteArrayOutputStream()
                    while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size() + n <= 1024 * 1024); out.write(buffer, 0, n) }
                    out.toString("UTF-8")
                })
                404 -> Res.NotFound
                // 400/422/451 = jawaban tegas provider atas query itu sendiri → bukan jaringan.
                // 401/403/408/429/dst tetap gagal sementara — salah melabelinya "tidak ada" menyesatkan.
                400, 422, 451 -> Res.Rejected
                else -> Res.Fail
            }
        } finally {
            connection.disconnect()
        }
    } catch (failure: Exception) {
        Res.Fail
    }

    private fun enc(value: String) = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
