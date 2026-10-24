package id.local.tubeflow.core

/** Snapshot pratinjau link Spotify di layar Download — hanya satu inspeksi yang boleh menulis. */
data class SpotifyPreview(
    val url: String = "",
    val track: SpotifyTrack? = null,
    val candidates: List<SpotifyCandidate> = emptyList(),
    val busy: Boolean = false,
    val phase: String? = null,
    val error: String? = null,
)

/** Snapshot pratinjau album/playlist Spotify, termasuk progres pencocokan per track. */
data class SpotifyCollectionPreview(
    val url: String = "",
    val collection: SpotifyCollection? = null,
    val busy: Boolean = false,
    val phase: String? = null,
    val error: String? = null,
    val matching: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    /** trackId → kandidat YouTube terbaik; hanya diisi untuk track yang sudah dicocokkan. */
    val matches: Map<String, SpotifyCandidate> = emptyMap(),
    /** trackId → alasan pencarian gagal, supaya UI dapat menampilkan baris bermasalah. */
    val failures: Map<String, String> = emptyMap(),
)

/**
 * Satu kandidat video YouTube untuk sebuah track Spotify, lengkap dengan skor kecocokan.
 * Skor hanya alat bantu keputusan pengguna — unduhan selalu memakai video yang dipilih, bukan tebakan.
 */
data class SpotifyCandidate(
    val video: VideoMetadata,
    val score: Int,
    val durationDeltaSeconds: Long,
    val titleCoverage: Int,
    val artistMatch: Boolean,
) {
    /** Kecocokan kuat: judul hampir utuh, artis terbukti, dan durasi dekat. */
    val confident: Boolean get() = score >= 70
}

/**
 * Pencocokan track Spotify ke hasil pencarian YouTube.
 *
 * Aturan:
 * - Judul dibandingkan setelah embel-embel promosi dibuang ("Official Video", "Lyrics", "4K", dst.);
 *   sinonim yang tidak informatif tidak boleh membuat kandidat salah menang.
 * - Artis dicari di judul maupun nama channel.
 * - Durasi menjadi penentu kedua: versi live/remix/extended yang lebih panjang harus kalah dari
 *   rekaman studio yang durasinya sama.
 * - Semua fungsi murni supaya perilakunya bisa diuji tanpa jaringan/engine.
 */
object SpotifyMatch {
    private val decoration = Regex("""(?i)\b(?:official|officiel|video|audio|lyric|lyrics|lirik|visualizer|visualiser|mv|m/v|hd|hq|4k|8k|remastered|remaster|explicit|clean|full|clip)\b""")
    private val nonWord = Regex("""[^\p{L}\p{N}]+""")
    private val artistSplitter = Regex("""\s*[&,;]\s*|\s+(?:feat\.?|ft\.?|featuring|vs\.?|with|x|xx)\s+""", RegexOption.IGNORE_CASE)
    /** Versi yang bukan rekaman asli — boleh muncul di pencarian, tapi tidak boleh dianggap cocok kuat. */
    private val unwanted = Regex("""(?i)\b(?:karaoke|instrumental|tribute|cover|reaction|lesson|tutorial|nightcore|slowed|reverb|8d)\b""")
    fun query(track: SpotifyTrack): String = Spotify.searchQuery(track)

    fun words(value: String): Set<String> = nonWord.replace(value.lowercase(), " ")
        .trim().split(' ').filter { it.isNotBlank() }.toSet()

    /** Kata judul tanpa embel-embel promosi. */
    fun titleWords(value: String): Set<String> = words(decoration.replace(value, " ")).filter { it.length > 1 || it.all(Char::isDigit) }.toSet()

    /** Nama artis utama saja — kolaborator tidak boleh menggagalkan pencocokan. */
    fun mainArtist(value: String): String = artistSplitter.split(value).firstOrNull().orEmpty().trim()

    fun durationDelta(track: SpotifyTrack, video: VideoMetadata): Long =
        if (track.durationSeconds <= 0 || video.durationSeconds <= 0) -1
        else kotlin.math.abs(track.durationSeconds - video.durationSeconds)

    /** Artis utama harus muncul utuh di judul atau nama channel. */
    fun artistMatches(track: SpotifyTrack, video: VideoMetadata): Boolean {
        val wanted = words(mainArtist(track.artists))
        return wanted.isNotEmpty() && words("${video.title} ${video.uploader}").containsAll(wanted)
    }

    /**
     * Skor 0..100: judul (60) + artis (25) + durasi (15).
     * Durasi tidak diketahui tidak memberi poin, tetapi juga tidak menghukum kandidat.
     */
    fun score(track: SpotifyTrack, video: VideoMetadata): Int {
        val wanted = titleWords(track.title)
        val titleScore = if (wanted.isEmpty()) 0 else {
            val found = titleWords(video.title)
            (wanted.count { it in found } * 60) / wanted.size
        }
        val artistScore = if (artistMatches(track, video)) 25 else 0
        val delta = durationDelta(track, video)
        val durationScore = when {
            delta < 0 -> 0
            delta <= 2 -> 15
            delta <= 5 -> 12
            delta <= 10 -> 9
            delta <= 20 -> 5
            delta <= 40 -> 2
            // Durasi jelas berbeda = versi lain (live/remix/extended), bukan rekaman yang sama.
            else -> -20
        }
        val variantPenalty = if (unwanted.containsMatchIn(video.title)) 40 else 0
        return (titleScore + artistScore + durationScore - variantPenalty).coerceIn(0, 100)
    }

    /** Kandidat terbaik lebih dulu; seri diputus durasi terdekat, lalu judul yang lebih pendek. */
    fun rank(track: SpotifyTrack, videos: List<VideoMetadata>, limit: Int = 5): List<SpotifyCandidate> {
        val seen = mutableSetOf<String>()
        return videos.mapNotNull { video ->
            val identity = Selection.identity(video.url) ?: return@mapNotNull null
            if (!seen.add(identity) || video.title.isBlank()) return@mapNotNull null
            val delta = durationDelta(track, video)
            val wanted = titleWords(track.title)
            val found = titleWords(video.title)
            SpotifyCandidate(
                video = video,
                score = score(track, video),
                durationDeltaSeconds = delta,
                titleCoverage = if (wanted.isEmpty()) 0 else (wanted.count { it in found } * 100) / wanted.size,
                artistMatch = artistMatches(track, video),
            )
        }.sortedWith(
            compareByDescending<SpotifyCandidate> { it.score }
                .thenBy { if (it.durationDeltaSeconds < 0) Long.MAX_VALUE else it.durationDeltaSeconds }
                .thenBy { it.video.title.length },
        ).take(limit)
    }
}
