package id.local.tubeflow.core

import kotlin.math.abs
import kotlin.math.max

/**
 * Status lirik yang disimpan di `DownloadJob.lyricStatus` dan dilaporkan ke pengguna (BUG-014).
 * Kosong berarti lirik tidak diminta untuk job ini.
 */
object LyricStatus {
    const val SAVED = "tersimpan"
    const val NOT_FOUND = "tidak_ditemukan"
    const val INSTRUMENTAL = "instrumental"
    const val NETWORK = "jaringan"
    const val SAVE_FAILED = "gagal_simpan"

    /** Label ramah pengguna untuk kartu riwayat dan notifikasi hasil. */
    fun label(status: String): String = when (status) {
        SAVED -> "Lirik tersimpan"
        NOT_FOUND -> "Lirik tidak ditemukan"
        INSTRUMENTAL -> "Lagu instrumental — tanpa lirik"
        NETWORK -> "Lirik gagal diunduh (jaringan)"
        SAVE_FAILED -> "Lirik gagal disimpan"
        else -> status
    }

    /** Status yang boleh dicari ulang tanpa mengunduh audio lagi. */
    fun retryable(status: String): Boolean = status in setOf(NOT_FOUND, NETWORK, SAVE_FAILED)
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
    /**
     * Toleransi durasi: 12 detik atau 10% dari durasi query (yang lebih longgar). Turunan dari probe R3 —
     * batas lama max(20 dtk, 15%) menerima 200 vs 229 detik dengan skor penuh; LRCLIB sendiri memakai
     * ±2 detik pada endpoint get (https://lrclib.net/docs). Durasi beda puluhan detik = rekaman beda.
     */
    private fun durationTolerance(query: LyricMatch.Query): Double =
        max(12.0, query.durationSeconds * 0.10)

    /** Kata judul video yang tidak memengaruhi identitas lagu (buang sebelum membandingkan). */
    private val junk = setOf(
        "official", "music", "video", "videos", "lyric", "lyrics", "audio", "visualizer", "visualiser",
        "hq", "hd", "uhd", "4k", "8k", "60fps", "mv", "clip", "topic", "full", "album",
        "remaster", "remastered", "explicit", "clean", "teaser",
    )

    /** Penanda versi yang mengubah isi/timestamp lagu — beda penanda = beda rekaman, tolak (BUG-013). */
    private val versionWords = setOf(
        "live", "acoustic", "cover", "remix", "demo", "instrumental", "karaoke", "unplugged",
        "session", "nightcore", "sped", "slowed", "extended", "reprise",
    )

    private fun norm(value: String): String =
        value.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()

    private fun tokens(value: String): Set<String> =
        norm(value).split(' ').filterTo(mutableSetOf()) { it.isNotEmpty() }

    /** Dipakai juga oleh [Lyrics.tidy] untuk mempertahankan penanda versi (BUG-013/R3). */
    internal fun versionSet(value: String): Set<String> =
        tokens(value).filterTo(mutableSetOf()) { it in versionWords }

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
        // Gerbang durasi: durasi yang jauh berarti versi rekaman berbeda (R3 — dulu max(20 dtk, 15%)).
        if (query.durationSeconds > 0 && candidate.durationSeconds > 0) {
            val diff = abs(query.durationSeconds - candidate.durationSeconds).toDouble()
            if (diff > durationTolerance(query)) return -1.0
        }
        val titleScore = similarity(coreTitle(query.title), coreTitle(candidate.title))
        if (titleScore < TITLE_MIN) return -1.0
        // Identitas asli tanpa artis DAN durasi: tak ada yang bisa diverifikasi — hanya judul persis yang aman
        // (kandidat mirip otomatis ditolak; kasus meragukan lain butuh pemilihan manual OPT-003) (BUG-013/R3).
        if (query.artist.isBlank() && query.durationSeconds <= 0L && titleScore < 1.0) return -1.0
        val artistKnown = query.artist.isNotBlank() && candidate.artist.isNotBlank()
        val artistScore = if (artistKnown) similarity(norm(query.artist), norm(candidate.artist)) else null
        // Artis diketahui di kedua pihak tapi tidak cocok → lagu lain, judul persis pun ditolak.
        if (artistScore != null && artistScore < ARTIST_MIN) return -1.0
        return if (artistScore != null) 0.6 * titleScore + 0.4 * artistScore else titleScore
    }
}

/**
 * Pencari lirik tersinkron dari LRCLIB (terbuka, tanpa kunci API).
 * [fetch] tidak pernah melempar: kegagalan jaringan dilaporkan sebagai [LyricStatus.NETWORK]
 * agar pengguna bisa membedakan "tidak ada" dengan "gagal" (BUG-014).
 */
object Lyrics {

    /** Cari lirik untuk judul "Artis - Judul" (atau judul polos) pada durasi video [durationSeconds]. */
    fun fetch(title: String, durationSeconds: Long): LyricOutcome {
        val neat = title.trim()
        val rawTrack = if (" - " in neat) neat.substringAfter(" - ").trim() else neat
        val rawArtist = if (" - " in neat) neat.substringBefore(" - ").trim() else ""
        // Varian kandidat: mentah lebih dulu, lalu tanpa tanda kurung (mis. "(Official Video)").
        val pairs = linkedSetOf<Pair<String, String>>()
        if (rawTrack.isNotBlank()) {
            pairs += rawTrack to rawArtist
            val track = tidy(rawTrack)
            val artist = tidy(rawArtist)
            if (track.isNotBlank() && (track != rawTrack || artist != rawArtist)) pairs += track to artist
        }
        if (pairs.isEmpty()) return LyricOutcome(LyricStatus.NOT_FOUND)

        var sawFailure = false
        var bestScore = 0.0
        var bestText: String? = null

        for ((track, artist) in pairs) {
            val query = LyricMatch.Query(track, artist, durationSeconds)
            val exact = buildString {
                append("track_name=").append(enc(track))
                if (artist.isNotBlank()) append("&artist_name=").append(enc(artist))
                // LRCLIB mencocokkan durasi ±2 detik pada get — kirim bila durasi valid (BUG-013/R3).
                if (durationSeconds > 0) append("&duration=").append(durationSeconds)
            }
            // 1) Endpoint exact: identitas cocok di database LRCLIB; instrumental berarti tanpa lirik.
            when (val res = get("/api/get?$exact")) {
                Res.Fail -> sawFailure = true
                Res.NotFound -> Unit
                is Res.Body -> {
                    val record = runCatching { org.json.JSONObject(res.text) }.getOrNull()
                    if (record != null) {
                        val candidate = candidateOf(record)
                        if (LyricMatch.score(query, candidate) >= LyricMatch.ACCEPT) {
                            if (candidate.instrumental) return LyricOutcome(LyricStatus.INSTRUMENTAL)
                            cleanLyrics(candidate.syncedLyrics)?.let { return LyricOutcome(LyricStatus.SAVED, it) }
                        }
                    }
                }
            }
            // 2) Pencarian by-field lalu bebas: semua kandidat dinilai LyricMatch, bukan skor buta.
            for (path in listOf(
                "/api/search?$exact",
                "/api/search?q=${enc(listOf(track, artist).filter { it.isNotBlank() }.joinToString(" "))}",
            )) {
                when (val res = get(path)) {
                    Res.Fail -> sawFailure = true
                    Res.NotFound -> Unit
                    is Res.Body -> {
                        val array = runCatching { org.json.JSONArray(res.text) }.getOrNull() ?: continue
                        for (index in 0 until array.length()) {
                            val record = array.optJSONObject(index) ?: continue
                            val candidate = candidateOf(record)
                            val score = LyricMatch.score(query, candidate)
                            if (score < LyricMatch.ACCEPT) continue
                            if (candidate.instrumental) return LyricOutcome(LyricStatus.INSTRUMENTAL)
                            val text = cleanLyrics(candidate.syncedLyrics) ?: continue
                            if (score > bestScore) { bestScore = score; bestText = text }
                        }
                    }
                }
            }
            if (bestText != null) break
        }

        bestText?.let { return LyricOutcome(LyricStatus.SAVED, it) }
        // Ada satu kegagalan HTTP di jalur mana pun + tak ada kandidat diterima = pencarian belum lengkap,
        // bukan “lagu tidak ada” — pengguna perlu bisa mencoba lagi (BUG-014/R4). Semua respons sukses
        // tanpa kandidat = memang tidak ditemukan.
        return LyricOutcome(if (sawFailure) LyricStatus.NETWORK else LyricStatus.NOT_FOUND)
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

    /** Bersihkan agar pemutar (AIMP/Poweramp) selalu mau baca: buang baris timestamp tanpa teks, paksa newline akhir. */
    private fun cleanLyrics(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val cleaned = text.lines().map { it.trimEnd() }
            .filterNot { it.isBlank() || (it.startsWith("[") && it.substringAfter(']', "").isBlank()) }
            .joinToString("\n").trimEnd() + "\n"
        return cleaned.takeIf { value -> value.lines().any { it.startsWith("[") } }
    }

    private sealed interface Res {
        data class Body(val text: String) : Res
        object NotFound : Res
        object Fail : Res
    }

    /**
     * Asal permintaan; diganti oleh tes fixture host (LyricsFetchTest) — di produksi selalu LRCLIB.
     * Bukan kontrak API publik; jangan pakai untuk tujuan lain.
     */
    internal var baseUrl = "https://lrclib.net"

    /** Isi HTTP GET sederhana; 404 = tidak ditemukan, status lain/exception = gagal. */
    private fun get(path: String): Res = try {
        val connection = java.net.URL("$baseUrl$path").openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.setRequestProperty("User-Agent", "TubeFlow/1.0")
        try {
            when (connection.responseCode) {
                200 -> Res.Body(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                404 -> Res.NotFound
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
