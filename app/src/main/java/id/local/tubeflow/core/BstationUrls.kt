package id.local.tubeflow.core

import java.net.URI

/**
 * Aturan URL Bstation/Bilibili TV (`bilibili.tv`, `biliintl.com`).
 *
 * Episode (`/play/<season>/<episode>`, `/video/<id>`) diunduh sebagai satu job. Halaman season
 * (`/play/<season>`, `/media/<season>`) dibaca sebagai koleksi lewat pemilih episode, bukan
 * diteruskan ke pipeline video tunggal.
 */
object BstationUrls {
    const val PLATFORM_ID = "bilibili_tv"
    const val LABEL = "Bstation"
    const val SEASON_LOADING = "Season Bstation terdeteksi — memuat daftar episode…"
    const val SEASON_EMPTY = "Season ini tidak memuat episode yang bisa diunduh di wilayah ini."
    const val SEASON_AS_VIDEO = "Halaman season tidak bisa diunduh sebagai satu video. Pilih episode dari daftar season."

    val domains = setOf("bilibili.tv", "biliintl.com")
    // Harus sama dengan host yang dikenali regex BiliIntl upstream; subdomain lain (mis. m.)
    // hanya diberi label platform, bukan diteruskan ke engine karena extractor akan menolaknya.
    private val supportedHosts = setOf("bilibili.tv", "www.bilibili.tv", "biliintl.com", "www.biliintl.com")

    private val episodePath = Regex("""^/(?:[A-Za-z]{2}/)?play/(\d+)/(\d+)/?$""")
    private val videoPath = Regex("""^/(?:[A-Za-z]{2}/)?video/(\d+)/?$""")
    private val seasonPath = Regex("""^/(?:[A-Za-z]{2}/)?(?:play|media)/(\d+)/?$""")

    /** Host Bstation atau subdomainnya; host mirip seperti `bilibili.tv.evil.org` tidak cocok. */
    fun isHost(host: String?): Boolean {
        val normalized = host?.lowercase() ?: return false
        return domains.any { normalized == it || normalized.endsWith(".$it") }
    }

    /**
     * Bentuk kanonis halaman season: locale dan query pelacakan dibuang.
     * Null bila bukan `/play/<season>` atau `/media/<season>` yang didukung extractor.
     */
    fun canonicalSeason(raw: String): String? {
        val seasonId = seasonId(raw) ?: return null
        return "https://www.bilibili.tv/play/$seasonId"
    }

    /** ID season dari halaman `/play/<id>` atau `/media/<id>`; null untuk episode/video. */
    fun seasonId(raw: String): String? {
        val path = parse(raw)?.path ?: return null
        return seasonPath.matchEntire(path)?.groupValues?.getOrNull(1)
    }

    /** ID season induk dari halaman episode `/play/<season>/<ep>`; null untuk halaman season/video UGC. */
    fun seasonIdOfEpisode(raw: String): String? {
        val path = parse(raw)?.path ?: return null
        return episodePath.matchEntire(path)?.groupValues?.getOrNull(1)
    }

    /** URL episode kanonis di dalam satu season; null bila ID bukan angka. */
    fun episodeUrl(seasonId: String, episodeId: String): String? {
        if (!seasonId.matches(Regex("\\d+")) || !episodeId.matches(Regex("\\d+"))) return null
        return "https://www.bilibili.tv/play/$seasonId/$episodeId"
    }

    /** Episode anime (`/play/<season>/<episode>`), bukan UGC `/video/<id>`. */
    fun isSeriesEpisode(raw: String): Boolean {
        val path = parse(raw)?.path ?: return false
        return episodePath.matches(path)
    }

    /**
     * Bentuk kanonis untuk identitas/duplikat: locale dan query pelacakan (`?bstar_from=…`) dibuang.
     * Null bila bukan episode yang didukung.
     */
    fun canonical(raw: String): String? {
        val path = parse(raw)?.path ?: return null
        episodePath.matchEntire(path)?.let { match ->
            return "https://www.bilibili.tv/play/${match.groupValues[1]}/${match.groupValues[2]}"
        }
        videoPath.matchEntire(path)?.let { match ->
            return "https://www.bilibili.tv/video/${match.groupValues[1]}"
        }
        return null
    }

    /** Halaman season/media Bstation (bukan episode). Dipakai untuk pesan yang menjelaskan. */
    fun isSeasonOnly(raw: String): Boolean {
        val path = parse(raw)?.path ?: return false
        return seasonPath.matches(path)
    }

    private fun parse(raw: String): URI? {
        val uri = try { URI(raw.trim()) } catch (_: Exception) { return null }
        if (uri.scheme != "https" || uri.userInfo != null || (uri.port != -1 && uri.port != 443)) return null
        if (uri.host?.lowercase() !in supportedHosts) return null
        return uri
    }
}
