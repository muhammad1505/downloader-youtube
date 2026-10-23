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
    const val RATE_LIMIT = "dibatasi_penyedia"
    const val SERVICE = "gangguan_penyedia"
    const val INVALID_RESPONSE = "respons_tidak_valid"
    const val SAVE_FAILED = "gagal_simpan"

    /** Label ramah pengguna untuk kartu riwayat dan notifikasi hasil. */
    fun label(status: String): String = when (status) {
        SAVED -> "Lirik tersimpan"
        NOT_FOUND -> "Lirik tidak tersedia di penyedia lirik"
        NO_MATCH -> "Lirik belum cocok dengan versi audio"
        INSTRUMENTAL -> "Lagu instrumental — tanpa lirik"
        NETWORK -> "Tidak dapat terhubung ke LRCLIB; coba lagi sebentar"
        RATE_LIMIT -> "LRCLIB membatasi permintaan; tunggu sebelum mencoba lagi"
        SERVICE -> "LRCLIB sedang bermasalah atau menolak akses; coba lagi nanti"
        INVALID_RESPONSE -> "Respons LRCLIB tidak valid; coba lagi nanti"
        SAVE_FAILED -> "Lirik gagal disimpan"
        else -> status
    }

    /** Status yang boleh dicari ulang tanpa mengunduh audio lagi. */
    fun retryable(status: String): Boolean = status in setOf(NOT_FOUND, NO_MATCH, NETWORK, RATE_LIMIT, SERVICE, INVALID_RESPONSE, SAVE_FAILED)
}

/** Hasil terstruktur satu pencarian lirik (BUG-014): [text] hanya berisi bila status [LyricStatus.SAVED]. */
data class LyricOutcome(val status: String, val text: String? = null, val httpCode: Int? = null)

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

    /** Penanda versi dipertahankan saat validasi semua variasi query (BUG-013/R3). */
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
        fun numbers(value: String) = tokens(value).filter { it.all(Char::isDigit) }.toSet()
        if (numbers(coreTitle(query.title)) != numbers(coreTitle(candidate.title))) return -1.0
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
 * [fetch] meneruskan pembatalan; kegagalan jaringan dilaporkan sebagai [LyricStatus.NETWORK]
 * agar pengguna bisa membedakan "tidak ada" dengan "gagal" (BUG-014).
 */
object Lyrics {
    data class Choice(val id: Long, val title: String, val artist: String, val album: String,
                      val durationSeconds: Long, val text: String?) {
        val hasSyncedLyrics: Boolean get() = !text.isNullOrBlank()
        fun automaticMatch(query: LyricMatch.Query): Boolean = LyricMatch.acceptsTimedLyrics(query,
            LyricMatch.Candidate(title,artist,durationSeconds,syncedLyrics=text))
    }
    data class Search(val choices: List<Choice>, val incomplete: Boolean = false, val failureStatus: String? = null,
                      val canExpand: Boolean = false, val httpCode: Int? = null)

    /** Explicit search exposes alternatives without weakening automatic download matching. */
    fun search(title: String, artist: String, expand: Boolean = false): Search {
        require(title.isNotBlank() && title.length <= 200 && artist.length <= 200) { "Judul wajib diisi, maksimal 200 karakter." }
        val paths = searchPaths(title, artist)
        val found=linkedMapOf<Long,Choice>()
        var failure: String? = null
        var failureHttp: Int? = null
        var canExpand = false
        for (path in paths) {
            when (val response=get(path, refreshMissing=true)) {
                is Res.Body -> {
                    val records=runCatching { org.json.JSONArray(response.text) }.getOrNull()
                    if (records == null) { failure=LyricStatus.INVALID_RESPONSE; continue }
                    for (i in 0 until minOf(records.length(),200)) {
                        val record=records.optJSONObject(i) ?: continue
                        val id=record.optLong("id",-1)
                        fun field(key: String) = if (record.isNull(key)) "" else record.optString(key).trim().takeUnless { it == "null" }.orEmpty()
                        val track=field("trackName")
                        if (id <= 0 || track.isBlank() || record.optBoolean("instrumental")) continue
                        val raw=record.optString("syncedLyrics").takeIf { !record.isNull("syncedLyrics") && it.isNotBlank() }
                        val text=raw?.trimStart('\uFEFF')?.takeIf { value -> value.toByteArray().size <= 512*1024 &&
                            runCatching { LrcTiming.shift(value,0,null); LrcLines.parse(value).any { it.text.isNotBlank() } }.getOrDefault(false) }
                        val choice=Choice(id,track,field("artistName"),field("albumName"),record.optLong("duration",-1),text)
                        if (found[id]?.hasSyncedLyrics != true) found[id]=choice
                        if (found.size >= 50) break
                    }
                }
                is Res.Fail -> { failure=response.status; failureHttp=response.httpCode; if (response.status == LyricStatus.RATE_LIMIT) break }
                else -> Unit
            }
            // Offer further variants on demand instead of spending requests after useful results.
            if (!expand && found.values.any { it.hasSyncedLyrics }) {
                canExpand = path != paths.last()
                break
            }
        }
        return Search(found.values.take(50),failure != null,failure,canExpand,failureHttp)
    }

    fun selectedText(choice: Choice, durationMs: Long?): String {
        val text=choice.text ?: error("Hasil ini tidak mempunyai lirik bertimestamp.")
        LrcTiming.shift(text,0,durationMs)
        require(durationMs == null || LrcLines.parse(text).all { it.atMs <= durationMs + 2000 }) { "Offset lirik melewati akhir audio." }
        val tagged="[re:TubeFlow / LRCLIB #${choice.id}]\n$text"
        require(tagged.toByteArray().size <= 512*1024) { "LRC terlalu besar." }
        return tagged
    }

    fun manualText(raw: String, durationMs: Long?): String {
        val text=raw.trimStart('\uFEFF').trim()
        require(text.isNotBlank()) { "Lirik manual masih kosong." }
        require(text.toByteArray().size <= 512*1024) { "LRC terlalu besar." }
        LrcTiming.shift(text,0,durationMs)
        return "[re:TubeFlow / manual]\n$text\n"
    }


    /**
     * Cari lirik untuk nama konsisten "Judul - Artis" (judul dulu — urutan yang dipakai nama file)
     * atau judul polos, pada durasi video [durationSeconds].
     */
    /** Resolve ambiguous order using provider records, never the position of a name alone. */
    fun resolveIdentity(identity: SongIdentity, durationSeconds: Long, refreshMissing: Boolean = false): SongIdentity {
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
            val response = get(path, refreshMissing)
            if (response is Res.Fail && response.status == LyricStatus.RATE_LIMIT) break
            if (response !is Res.Body) continue
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

    fun fetch(identity: SongIdentity, durationSeconds: Long, refreshMissing: Boolean = false): LyricOutcome {
        val validation = LyricMatch.Query(identity.lyricTitle, identity.artist.orEmpty(), durationSeconds)
        if (validation.title.isBlank()) return LyricOutcome(LyricStatus.NOT_FOUND)
        // Use the same cheap discovery as manual search before the exact endpoint, which may
        // fetch external sources. A valid search result must not incur an unnecessary get/429.
        val paths = linkedSetOf<String>().apply { addAll(searchPaths(validation.title, validation.artist)) }
        if (validation.artist.isNotBlank() && durationSeconds > 0) {
            paths += "/api/get?track_name=${enc(validation.title)}&artist_name=${enc(validation.artist)}&album_name=&duration=$durationSeconds"
            val lead = TitleSplit.primaryArtist(validation.artist)
            if (lead != validation.artist) paths += "/api/get?track_name=${enc(validation.title)}&artist_name=${enc(lead)}&album_name=&duration=$durationSeconds"
        }
        var failure: String? = null
        var failureHttp: Int? = null
        var unmatched = false
        var bestScore = 0.0
        var bestDifference = Long.MAX_VALUE
        var bestText: String? = null
        var instrumental = false
        val acceptedCredits = mutableSetOf<String>()
        for (path in paths) {
            val response = get(path, refreshMissing)
            when (response) {
                is Res.Fail -> {
                    failure = response.status
                    failureHttp = response.httpCode
                    if (response.status == LyricStatus.RATE_LIMIT) break
                }
                is Res.Body -> {
                    val records = runCatching {
                        if (path.startsWith("/api/get?")) listOf(org.json.JSONObject(response.text))
                        else org.json.JSONArray(response.text).let { array ->
                            (0 until minOf(array.length(), 200)).mapNotNull(array::optJSONObject)
                        }
                    }.getOrNull()
                    if (records == null) { failure = LyricStatus.INVALID_RESPONSE; continue }
                    for (record in records) {
                        val candidate = candidateOf(record)
                        if (!LyricMatch.acceptsTimedLyrics(validation, candidate)) {
                            if (!candidate.syncedLyrics.isNullOrBlank()) unmatched = true
                            continue
                        }
                        val text = cleanLyrics(candidate.syncedLyrics, durationSeconds)
                        if (text == null && !candidate.instrumental) {
                            if (!candidate.syncedLyrics.isNullOrBlank()) unmatched = true
                            continue
                        }
                        acceptedCredits += TitleSplit.creditKey(candidate.artist)
                        val score = LyricMatch.score(validation, candidate)
                        val diff = abs(durationSeconds - candidate.durationSeconds)
                        if (score > bestScore || score == bestScore && diff < bestDifference) {
                            bestScore = score; bestDifference = diff
                            bestText = text; instrumental = candidate.instrumental
                        }
                    }
                    // Avoid querying variants when a verified timed candidate already exists.
                    if (bestText != null) break
                }
                else -> Unit
            }
        }
        if (validation.artist.isBlank() && acceptedCredits.size > 1) return LyricOutcome(LyricStatus.NO_MATCH)
        bestText?.let { return LyricOutcome(LyricStatus.SAVED, it) }
        val status=when {
            instrumental -> LyricStatus.INSTRUMENTAL
            failure != null -> failure
            unmatched -> LyricStatus.NO_MATCH
            else -> LyricStatus.NOT_FOUND
        }
        return LyricOutcome(status, httpCode=if (status == failure) failureHttp else null)
    }

    /** Same discovery plan for automatic/refetch/manual; at most four distinct searches. */
    internal fun searchPaths(title: String, artist: String): List<String> {
        val raw = title.trim()
        val credit = artist.trim()
        val simple = TitleSplit.identify(raw, metadataArtist=credit, recognizeCollaboration=false).title
        val lead = TitleSplit.primaryArtist(credit)
        fun field(track: String, who: String) = "/api/search?track_name=${enc(track)}" +
            who.takeIf { it.isNotBlank() }?.let { "&artist_name=${enc(it)}" }.orEmpty()
        // Search the normalized song/lead credit first; keep the original recording for validation.
        // A display label such as "SATRU 2 | Live" is not necessarily the provider's track name.
        return linkedSetOf(field(simple,lead), field(raw,credit),
            "/api/search?q=${enc(listOf(raw,credit).filter { it.isNotBlank() }.joinToString(" "))}",
            field(simple, "")).take(4)
    }

    private fun candidateOf(record: org.json.JSONObject) = LyricMatch.Candidate(
        title = record.optString("trackName").takeUnless { record.isNull("trackName") }.orEmpty(),
        artist = record.optString("artistName").takeUnless { record.isNull("artistName") }.orEmpty(),
        durationSeconds = record.optLong("duration", -1L).takeIf { it > 0 } ?: -1L,
        instrumental = record.optBoolean("instrumental"),
        syncedLyrics = if (record.isNull("syncedLyrics")) null else record.optString("syncedLyrics").takeIf { it.isNotBlank() },
    )

    /** Automatic and manual lookup share syntax/timeline validation, including blank timed lines. */
    private fun cleanLyrics(text: String?, durationSeconds: Long): String? {
        if (text.isNullOrBlank() || durationSeconds <= 0 || text.toByteArray().size > 512 * 1024) return null
        val normalized = text.trimStart('\uFEFF').trim() + "\n"
        return runCatching {
            LrcTiming.shift(normalized, 0, durationSeconds * 1000)
            val lines = LrcLines.parse(normalized)
            require(lines.any { it.text.isNotBlank() } && lines.all { it.atMs <= durationSeconds * 1000 + 2000 })
            normalized
        }.getOrNull()
    }

    private sealed interface Res {
        data class Body(val text: String) : Res
        object NotFound : Res
        data class Fail(val status: String, val httpCode: Int? = null) : Res
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
    @Volatile internal var requestGate = LyricsRequestGate()
    fun initialize(cacheDirectory: java.io.File, cooldownFile: java.io.File) {
        requestGate.attach(cooldownFile)
        synchronized(this) {
            if (responseCache == null) responseCache = LyricsResponseCache(cacheDirectory)
        }
    }
    fun retryAfterSeconds(): Long = requestGate.remainingSeconds()
    fun clearCache(cacheDirectory: java.io.File): LyricsResponseCache.ClearResult {
        val cache = synchronized(this) {
            responseCache ?: LyricsResponseCache(cacheDirectory).also { responseCache = it }
        }
        return cache.clear()
    }
    private fun validProviderBody(path: String, text: String): Boolean = runCatching {
        fun validRecord(record: org.json.JSONObject) = !record.isNull("trackName") &&
            record.opt("trackName") is String && record.getString("trackName").isNotBlank()
        if (path.startsWith("/api/search?")) {
            val records=org.json.JSONArray(text)
            (0 until records.length()).all { index -> records.optJSONObject(index)?.let(::validRecord) == true }
        } else validRecord(org.json.JSONObject(text))
    }.getOrDefault(false)
    private fun get(path: String, refreshMissing: Boolean = false): Res {
        if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException()
        val cache = responseCache ?: return networkGet(path)
        val cached = cache.request("$baseUrl$path", refreshMissing, validateBody={ validProviderBody(path,it) }) {
            when (val result = networkGet(path)) {
                is Res.Body -> LyricsResponseCache.Response(200, result.text)
                Res.NotFound -> LyricsResponseCache.Response(404, "")
                Res.Rejected -> LyricsResponseCache.Response(400, "")
                is Res.Fail -> LyricsResponseCache.Response(result.httpCode ?: 0, "", result.status)
            }
        }
        cached.failureStatus?.let { return Res.Fail(it,cached.code.takeIf { code -> code in 100..599 }) }
        return when (cached.code) { 200 -> Res.Body(cached.text); 404 -> Res.NotFound; 400 -> Res.Rejected; else -> Res.Fail(LyricStatus.NETWORK) }
    }
    private fun networkGet(path: String): Res {
        return try { requestGate.request {
        val connection = java.net.URL("$baseUrl$path").openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.setRequestProperty("User-Agent", "TubeFlow/1.1.15 (https://github.com/muhammad1505/downloader-youtube)")
        try {
            when (val code = connection.responseCode) {
                200 -> connection.inputStream.use { input ->
                    val buffer = ByteArray(8192); val out = java.io.ByteArrayOutputStream()
                    while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size() + n <= 1024 * 1024); out.write(buffer, 0, n) }
                    val text=out.toString("UTF-8").trimStart('\uFEFF')
                    if (validProviderBody(path,text)) Res.Body(text).also { requestGate.accepted() }
                    else Res.Fail(LyricStatus.INVALID_RESPONSE,200)
                }
                404 -> Res.NotFound.also { requestGate.accepted() }
                // 400/422/451 = jawaban tegas provider atas query itu sendiri → bukan jaringan.
                // 401/403/408/429/dst tetap gagal sementara — salah melabelinya "tidak ada" menyesatkan.
                400, 422, 451 -> Res.Rejected
                429 -> {
                    requestGate.limited(connection.getHeaderField("Retry-After"), connection.getHeaderField("Date"))
                    Res.Fail(LyricStatus.RATE_LIMIT,429)
                }
                in 500..599, 401, 403 -> Res.Fail(LyricStatus.SERVICE,code)
                else -> Res.Fail(LyricStatus.NETWORK,code)
            }
        } finally {
            connection.disconnect()
        } } ?: Res.Fail(LyricStatus.RATE_LIMIT)
    } catch (cancelled: java.util.concurrent.CancellationException) { throw cancelled }
      catch (_: InterruptedException) { Thread.currentThread().interrupt(); throw java.util.concurrent.CancellationException() }
      catch (_: IllegalArgumentException) { Res.Fail(LyricStatus.INVALID_RESPONSE) }
      catch (_: Exception) { Res.Fail(LyricStatus.NETWORK) }
    }

    private fun enc(value: String) = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
