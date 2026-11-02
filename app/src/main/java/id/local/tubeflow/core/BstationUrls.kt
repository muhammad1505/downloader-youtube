package id.local.tubeflow.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

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
    const val SHORT_LINK_LOADING = "Membuka tautan pendek Bstation…"
    const val SHORT_LINK_ERROR = "Tautan pendek Bstation tidak bisa dibuka. Salin tautan lengkap dari halaman Bstation, lalu coba lagi."

    val domains = setOf("bilibili.tv", "biliintl.com")
    // Harus sama dengan host yang dikenali regex BiliIntl upstream; subdomain lain (mis. m.)
    // hanya diberi label platform, bukan diteruskan ke engine karena extractor akan menolaknya.
    private val supportedHosts = setOf("bilibili.tv", "www.bilibili.tv", "biliintl.com", "www.biliintl.com")

    private val episodePath = Regex("""^/(?:[A-Za-z]{2}/)?play/(\d+)/(\d+)/?$""")
    private val videoPath = Regex("""^/(?:[A-Za-z]{2}/)?video/(\d+)/?$""")
    private val seasonPath = Regex("""^/(?:[A-Za-z]{2}/)?(?:play|media)/(\d+)/?$""")
    private const val SHORT_HOST = "bili.im"
    private const val SHORT_LINK_MAX_CHARS = 512 * 1024
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) TubeFlow"
    private val shortToken = Regex("^[A-Za-z0-9_-]+$")
    private val htmlTag = Regex("""<(?:link|meta)\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val relCanonical = Regex("""\brel\s*=\s*["'][^"']*\bcanonical\b[^"']*["']""", RegexOption.IGNORE_CASE)
    private val hrefAttribute = Regex("""\bhref\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val ogUrlProperty = Regex("""\b(?:property|name)\s*=\s*["']og:url["']""", RegexOption.IGNORE_CASE)
    private val contentAttribute = Regex("""\bcontent\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    /** Host Bstation atau subdomainnya; host mirip seperti `bilibili.tv.evil.org` tidak cocok. */
    fun isHost(host: String?): Boolean {
        val normalized = host?.lowercase() ?: return false
        return domains.any { normalized == it || normalized.endsWith(".$it") }
    }

    /**
     * Tautan pendek resmi Bstation (`https://bili.im/<token>`) yang harus dibuka dulu untuk
     * mendapatkan halaman season/episode/video aslinya. Host ini sengaja terpisah dari host
     * extractor: URL pendek tidak boleh diteruskan ke engine sebelum di-resolve.
     */
    fun isShortLink(raw: String): Boolean {
        val uri = try { URI(raw.trim()) } catch (_: Exception) { return false }
        if (uri.scheme != "https" || uri.userInfo != null || (uri.port != -1 && uri.port != 443)) return false
        if (uri.host?.lowercase() != SHORT_HOST) return false
        val token = uri.path?.removePrefix("/")?.trimEnd('/') ?: return false
        return token.isNotBlank() && '/' !in token && shortToken.matches(token)
    }

    /**
     * Membuka tautan pendek Bstation dan mengembalikan URL kanonis Bstation yang sah, atau null
     * bila halaman tidak dapat dibaca/tidak menunjuk ke Bstation. Jaringan hanya menyentuh host
     * `bili.im`; URL hasilnya selalu melewati gerbang host yang sama dengan extractor.
     */
    suspend fun resolveShortLink(raw: String): String? = withContext(Dispatchers.IO) {
        if (!isShortLink(raw)) return@withContext null
        val connection = try {
            URL(raw.trim()).openConnection() as HttpURLConnection
        } catch (_: Exception) {
            return@withContext null
        }
        try {
            currentCoroutineContext().job.invokeOnCompletion { cause ->
                if (cause is CancellationException) connection.disconnect()
            }
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            val status = connection.responseCode
            if (status !in 200..299) return@withContext null
            val html = connection.inputStream.bufferedReader(Charsets.UTF_8)
                .use { readLimited(it, SHORT_LINK_MAX_CHARS) }
            currentCoroutineContext().ensureActive()
            shortLinkTarget(html) ?: canonicalTarget(connection.url.toString())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            null
        } finally {
            connection.disconnect()
        }
    }

    /** Canonical/og:url dari halaman tautan pendek; dipisahkan agar dapat diuji tanpa jaringan. */
    internal fun shortLinkTarget(html: String): String? {
        for (tag in htmlTag.findAll(html)) {
            val value = tag.value
            val href = hrefAttribute.find(value)?.groupValues?.getOrNull(1)
            if (href != null && relCanonical.containsMatchIn(value)) {
                canonicalTarget(href)?.let { return it }
            }
            val content = contentAttribute.find(value)?.groupValues?.getOrNull(1)
            if (content != null && ogUrlProperty.containsMatchIn(value)) {
                canonicalTarget(content)?.let { return it }
            }
        }
        return null
    }

    private fun canonicalTarget(raw: String): String? {
        val decoded = raw.trim().replace("&amp;", "&")
        val absolute = when {
            decoded.startsWith("//") -> "https:$decoded"
            decoded.startsWith("/") -> "https://www.bilibili.tv$decoded"
            else -> decoded
        }
        return canonicalSeason(absolute) ?: canonical(absolute)
    }

    private fun readLimited(reader: Reader, maxChars: Int): String {
        val buffer = CharArray(8 * 1024)
        val output = StringBuilder()
        while (output.length < maxChars) {
            val read = reader.read(buffer, 0, minOf(buffer.size, maxChars - output.length))
            if (read <= 0) break
            output.append(buffer, 0, read)
        }
        return output.toString()
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
