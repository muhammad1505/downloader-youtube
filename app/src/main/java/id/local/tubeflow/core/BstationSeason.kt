package id.local.tubeflow.core

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Katalog season Bstation; unduhan format tetap ditangani engine per episode. */
object BstationSeason {
    private const val API = "https://api.bilibili.tv/intl/gateway"
    private const val SEASON_PATH = "/web/v2/ogv/play/season_info"
    private const val EPISODES_PATH = "/web/v2/ogv/play/episodes"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) TubeFlow"

    suspend fun inspect(context: Context, seasonUrl: String, onPhase: (String) -> Unit = {}): PlaylistMetadata {
        val seasonId = BstationUrls.seasonId(seasonUrl)
            ?: throw IllegalArgumentException(BstationUrls.SEASON_AS_VIDEO)
        val canonical = BstationUrls.canonicalSeason(seasonUrl)
            ?: throw IllegalArgumentException("Gunakan tautan season Bstation yang valid.")
        onPhase(InspectionPhase.FETCHING_SEASON)
        // Cookie login membuat katalog menandai episode yang dapat diakses akun ini; tanpa cookie
        // katalog tetap bisa dibaca, tetapi episode Premium belum bisa diunduh.
        val cookie = BstationAuth.cookieHeader(context)

        // Katalog episode adalah data wajib. Info season hanya memperkaya judul/cover; kegagalannya
        // tidak boleh menyembunyikan daftar episode yang sebenarnya bisa dibaca.
        val seasonJson = try {
            getJson("$API$SEASON_PATH?season_id=$seasonId&platform=web", cookie)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        // Cookie kedaluwarsa tidak boleh membuat katalog gratis ikut gagal: jika API menolak
        // sesi, baca ulang katalog sebagai tamu. Sesi tetap tersimpan sampai pengguna logout.
        val episodesUrl = "$API$EPISODES_PATH?season_id=$seasonId&platform=web"
        val episodesJson = try {
            getJson(episodesUrl, cookie)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (premium: BstationPremiumException) {
            if (cookie.isNullOrBlank()) throw premium
            getJson(episodesUrl, null)
        }
        onPhase(InspectionPhase.READING_ENTRIES)
        return parse(episodesJson, seasonJson, canonical, seasonId)
    }

    /**
     * Judul seri untuk halaman episode tunggal. Info dict BiliIntl tidak memuat uploader/series,
     * jadi tanpa ini kartu pratinjau tidak punya kredit sumber sama sekali. Kegagalan API tidak
     * menggagalkan inspeksi — hanya kredit yang tidak ditampilkan.
     */
    suspend fun seriesTitle(context: Context, seasonId: String): String {
        if (!seasonId.matches(Regex("\\d+"))) return ""
        return try {
            getJson("$API$SEASON_PATH?season_id=$seasonId&platform=web", BstationAuth.cookieHeader(context))
                .optJSONObject("data")?.optJSONObject("season")
                ?.optString("title").cleanJsonString().orEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Data API Bstation yang dipakai pemilih. Dipisahkan dari jaringan agar dapat diuji dengan
     * fixture tanpa akun, wilayah, atau koneksi internet.
     */
    internal fun parse(
        episodesJson: JSONObject,
        seasonJson: JSONObject?,
        seasonUrl: String,
        seasonId: String,
    ): PlaylistMetadata {
        val season = seasonJson?.optJSONObject("data")?.optJSONObject("season")
        val seasonTitle = season?.optString("title").cleanJsonString()
        val title = seasonTitle.ifBlank { "Bstation season $seasonId" }
        val seasonCover = season?.optString("horizontal_cover").cleanJsonString()
            .ifBlank { season?.optString("vertical_cover").cleanJsonString() }

        val sections = episodesJson.optJSONObject("data")?.optJSONArray("sections") ?: JSONArray()
        val entries = mutableListOf<PlaylistEntry>()
        val seen = mutableSetOf<String>()
        for (sectionIndex in 0 until sections.length()) {
            val section = sections.optJSONObject(sectionIndex) ?: continue
            val episodes = section.optJSONArray("episodes") ?: continue
            for (index in 0 until episodes.length()) {
                val episode = episodes.optJSONObject(index) ?: continue
                val episodeId = episode.optString("episode_id").trim()
                if (!episodeId.matches(Regex("\\d+"))) continue
                if (!seen.add(episodeId)) continue
                val url = BstationUrls.episodeUrl(seasonId, episodeId) ?: continue
                val shortTitle = episode.optString("short_title_display").cleanJsonString()
                val longTitle = episode.optString("long_title_display").cleanJsonString()
                val displayTitle = episode.optString("title_display").cleanJsonString()
                val number = episodeNumber(shortTitle, displayTitle)
                val limit = episode.optInt("limit", 0)
                val limitText = episode.optString("limit_text").lowercase(Locale.ROOT)
                entries += PlaylistEntry(
                    id = episodeId,
                    title = episodeTitle(number, shortTitle, longTitle, displayTitle),
                    durationSeconds = 0,
                    url = url,
                    thumbnail = episode.optString("cover").cleanJsonString().ifBlank { seasonCover },
                    episodeNumber = number,
                    premium = limit > 0 || "premium" in limitText || "vip" in limitText,
                )
            }
        }
        if (entries.isEmpty()) throw BstationSeasonEmptyException(BstationUrls.SEASON_EMPTY)
        val ordered = entries.sortedWith(
            compareBy<PlaylistEntry> { it.episodeNumber == null }
                .thenBy { it.episodeNumber ?: Int.MAX_VALUE },
        )
        return PlaylistMetadata(
            title = title,
            url = seasonUrl,
            entries = ordered,
            folderName = Selection.seriesFolder(seasonTitle, seasonId),
        )
    }

    /** Episode Premium terkunci sampai pengguna masuk; episode gratis selalu bisa dipilih. */
    fun premiumLocked(entry: PlaylistEntry, signedIn: Boolean): Boolean =
        entry.premium == true && !signedIn

    /** Pilihan awal: episode gratis bernomor; PV/spesial dan Premium tidak dipilih. */
    fun initialSelection(entries: List<PlaylistEntry>): Set<String> {
        val free = entries.filter { it.premium != true }
        return (free.filter { it.episodeNumber != null }.ifEmpty { free }).map { it.id }.toSet()
    }

    internal fun episodeNumber(shortTitle: String, displayTitle: String): Int? {
        val prefix = Regex("""^\s*(?:E|EP|Episode)\s*(\d+)""", RegexOption.IGNORE_CASE)
        return prefix.find(shortTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: prefix.find(displayTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    internal fun episodeTitle(number: Int?, shortTitle: String, longTitle: String, displayTitle: String): String {
        if (number == null) return displayTitle.ifBlank { shortTitle }.ifBlank { "Spesial" }
        val prefix = "E%02d".format(Locale.ROOT, number)
        val remainder = longTitle.ifBlank {
            displayTitle.replace(Regex("""^\s*(?:E|EP|Episode)\s*\d+\s*[-–—:.]?\s*""", RegexOption.IGNORE_CASE), "").trim()
        }
        return if (remainder.isBlank()) prefix else "$prefix - $remainder"
    }

    private suspend fun getJson(url: String, cookie: String?): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            currentCoroutineContext().job.invokeOnCompletion { cause ->
                if (cause is CancellationException) connection.disconnect()
            }
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Referer", "https://www.bilibili.tv/")
            connection.setRequestProperty("Accept", "application/json")
            if (!cookie.isNullOrBlank()) connection.setRequestProperty("Cookie", cookie)
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IOException("Bstation HTTP $status")
            currentCoroutineContext().ensureActive()
            val json = JSONObject(body)
            val code = json.optInt("code", -1)
            if (code != 0) throw apiError(code, json.optString("message"))
            json
        } finally {
            connection.disconnect()
        }
    }

    private fun String?.cleanJsonString(): String = this.orEmpty().trim().takeUnless { it == "null" }.orEmpty()

    private fun apiError(code: Int, message: String): IOException = when (code) {
        10004001 -> BstationGeoException("Bstation geo restriction (10004001)")
        10004002, 10004003, 10004004, 10004005, 10023006 ->
            BstationPremiumException("Bstation login/premium required ($code)")
        else -> IOException("Bstation API $code: ${message.take(200)}")
    }
}

/** Error katalog yang dipetakan FailureDetails menjadi kategori geo/premium, bukan crash mentah. */
internal class BstationGeoException(message: String) : IOException(message)
internal class BstationPremiumException(message: String) : IOException(message)
internal class BstationSeasonEmptyException(message: String) : IOException(message)
