package id.local.tubeflow.core

import org.json.JSONObject
import java.net.URLDecoder
import java.util.Locale

/** Mode tampilan video di dalam PlayerView; urutannya adalah urutan tombol. */
enum class VideoAspect(val label: String) {
    FIT("Pas"),
    FILL("Penuh"),
    ZOOM("Zoom");

    fun next(): VideoAspect = entries[(ordinal + 1) % entries.size]
}

enum class VideoRepeat(val label: String) {
    OFF("Mati"),
    ALL("Semua video"),
    ONE("Satu video");

    fun next(): VideoRepeat = entries[(ordinal + 1) % entries.size]
}

/** Berkas subtitle sampingan yang ditemukan dari catatan unduhan (`sub|…`). */
data class VideoSubtitle(val uri: String, val mime: String, val language: String, val label: String)

/** Satu video dalam antrean pemutar; URI adalah lokasi yang benar-benar dapat dibuka ExoPlayer. */
data class VideoEntry(
    val uri: String,
    val title: String,
    val subtitles: List<VideoSubtitle> = emptyList(),
) {
    val displayTitle: String get() = title.trim().ifBlank { "Video" }
}

/**
 * Logika pemutaran video yang tidak menyentuh Android/ExoPlayer supaya dapat diuji host:
 * pemetaan subtitle unduhan, antrean Koleksi/Riwayat, lompatan waktu, dan posisi lanjut tonton.
 * Pemutar audio lama (PlayerViewModel) sengaja tidak memakai kelas ini.
 */
object VideoPlayback {
    const val SEEK_STEP_MS = 10_000L
    const val RESUME_MIN_MS = 10_000L
    const val RESUME_TAIL_MS = 5_000L

    val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    private val languages = mapOf(
        "id" to "Indonesia",
        "en" to "Inggris",
        "jv" to "Jawa",
        "ms" to "Melayu",
        "ar" to "Arab",
        "ja" to "Jepang",
        "ko" to "Korea",
        "zh" to "Mandarin",
        "es" to "Spanyol",
        "fr" to "Prancis",
        "de" to "Jerman",
        "pt" to "Portugis",
        "ru" to "Rusia",
        "hi" to "Hindi",
        "th" to "Thai",
        "vi" to "Vietnam",
        "tr" to "Turki",
        "it" to "Italia",
        "nl" to "Belanda",
        "pl" to "Polandia",
        "uk" to "Ukraina",
    )

    private val languageToken = Regex("""(?:^|[._\- ])([a-z]{2,3})(?:[._\- ]|$)""")

    /** "01:23" atau "1:02:03"; nilai negatif dianggap 0. */
    fun timeLabel(ms: Long): String {
        val seconds = ms.coerceAtLeast(0) / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val rest = seconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, rest)
        else "%d:%02d".format(Locale.ROOT, minutes, rest)
    }

    /** Label kecepatan memakai koma desimal Indonesia, mis. "1,25×". */
    fun speedLabel(speed: Float): String {
        val number = if (speed % 1f == 0f) speed.toInt().toString()
        else String.format(Locale.ROOT, "%.2f", speed).trimEnd('0').trimEnd('.').replace('.', ',')
        return "$number×"
    }

    fun seekTarget(positionMs: Long, deltaMs: Long, durationMs: Long): Long {
        val moved = positionMs.coerceAtLeast(0) + deltaMs
        val capped = if (durationMs > 0) moved.coerceAtMost(durationMs) else moved
        return capped.coerceAtLeast(0)
    }

    /** Posisi lama hanya dipakai bila sudah cukup jauh dan bukan bagian akhir video. */
    fun shouldResume(savedMs: Long?, durationMs: Long): Boolean {
        val saved = savedMs ?: return false
        if (saved < RESUME_MIN_MS) return false
        return durationMs <= 0 || saved < durationMs - RESUME_TAIL_MS
    }

    /**
     * Rasio aman untuk jendela Picture-in-Picture. Android menolak rasio di luar 0,4184–2,39,
     * jadi video ultra-lebar/ekstrem dipetakan ke batas tersebut alih-alih gagal masuk PiP.
     */
    fun pipAspect(width: Int, height: Int): Pair<Int, Int> {
        val safeWidth = width.takeIf { it > 0 } ?: 16
        val safeHeight = height.takeIf { it > 0 } ?: 9
        val ratio = safeWidth.toDouble() / safeHeight
        return when {
            ratio > 2.39 -> 239 to 100
            ratio < 0.4184 -> 4184 to 10_000
            else -> safeWidth to safeHeight
        }
    }

    /** MIME yang dipahami ExoPlayer; LRC lirik audio sengaja tidak dikenali sebagai subtitle video. */
    fun subtitleMime(nameOrUri: String): String? = when (extension(nameOrUri)) {
        "srt" -> "application/x-subrip"
        "vtt" -> "text/vtt"
        "ass", "ssa" -> "text/x-ssa"
        "ttml", "dfxp" -> "application/ttml+xml"
        else -> null
    }

    fun languageName(tag: String): String = languages[tag.trim().lowercase(Locale.ROOT)].orEmpty()

    fun languageFrom(name: String): String {
        val stem = name.substringBeforeLast('.').lowercase(Locale.ROOT)
        return languageToken.findAll(stem).map { it.groupValues[1] }
            .firstOrNull { it in languages }.orEmpty()
    }

    fun trackLabel(label: String?, language: String?, index: Int, kind: String): String {
        val languageName = languageName(language.orEmpty())
        val parts = listOfNotNull(
            label?.trim()?.takeIf { it.isNotBlank() },
            languageName.takeIf { it.isNotBlank() },
        ).distinctBy { it.lowercase(Locale.ROOT) }
        return parts.joinToString(" · ").ifBlank { "$kind ${index + 1}" }
    }

    /**
     * Subtitle hasil unduhan. Satu baris `sub|<uri>` menjadi satu pilihan; bahasa dibaca dari nama
     * berkas bila jelas (mis. `judul.id.srt`), selain itu label memakai nama berkas.
     */
    fun subtitlesFromSidecars(sidecars: String?): List<VideoSubtitle> {
        if (sidecars.isNullOrBlank()) return emptyList()
        return sidecars.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("sub|") }
            .mapNotNull { line ->
                val uri = line.substringAfter('|', "").trim()
                val name = fileName(uri)
                val mime = subtitleMime(name) ?: return@mapNotNull null
                val language = languageFrom(name)
                val label = languageName(language).ifBlank { name.ifBlank { "Subtitle" } }
                VideoSubtitle(uri, mime, language, label)
            }
            .distinctBy { it.uri }
            .toList()
    }

    fun entry(item: MediaItem, sidecars: String? = null): VideoEntry = VideoEntry(
        uri = item.location,
        title = item.title.ifBlank { item.displayName.substringBeforeLast('.') },
        subtitles = subtitlesFromSidecars(sidecars),
    )

    /** Daftar video yang dapat diputar pada urutan Koleksi saat ini, mulai dari item yang diketuk. */
    fun videoEntries(
        items: List<MediaItem>,
        startUri: String,
        sidecars: (MediaItem) -> String? = { null },
    ): Pair<List<VideoEntry>, Int> {
        val playable = items.filter { it.kind == "VIDEO" && it.availability == "AVAILABLE" }
        val start = playable.indexOfFirst { it.uri == startUri || it.location == startUri }.coerceAtLeast(0)
        return playable.map { entry(it, sidecars(it)) } to start
    }

    /** Daftar video selesai pada urutan Riwayat saat ini, mulai dari job yang diketuk. */
    fun videoEntries(jobs: List<DownloadJob>, startJobId: String): Pair<List<VideoEntry>, Int> {
        val playable = jobs.mapNotNull { job -> entry(job)?.let { job to it } }
        val start = playable.indexOfFirst { (job, _) -> job.id == startJobId }.coerceAtLeast(0)
        return playable.map { it.second } to start
    }

    fun entry(job: DownloadJob): VideoEntry? {
        if (job.mode != MediaMode.VIDEO || job.state != JobState.COMPLETED) return null
        val uri = job.outputUri?.takeIf { it.isNotBlank() } ?: return null
        return VideoEntry(uri, job.title, subtitlesFromSidecars(job.sidecarUris))
    }

    private fun extension(nameOrUri: String): String =
        nameOrUri.substringBefore('?').substringBefore('#').substringAfterLast('/').substringAfterLast('.', "").lowercase(Locale.ROOT)

    private fun fileName(raw: String): String = runCatching {
        URLDecoder.decode(raw.substringBefore('?').substringBefore('#').substringAfterLast('/'), "UTF-8")
    }.getOrDefault(raw.substringBefore('?').substringAfterLast('/'))
}

/**
 * Riwayat posisi tonton yang disimpan sebagai JSON kecil. Batas 200 entri dan umur 90 hari
 * mencegah preferensi tumbuh tanpa batas setelah bertahun-tahun pemakaian.
 */
object VideoResumeCodec {
    data class Entry(val positionMs: Long, val durationMs: Long, val updatedAt: Long)

    const val LIMIT = 200
    const val MAX_AGE_MS = 90L * 24 * 60 * 60 * 1000

    fun encode(entries: Map<String, Entry>): String {
        val root = JSONObject()
        entries.forEach { (uri, value) ->
            if (uri.isBlank()) return@forEach
            // Entri tanpa posisi valid tidak pernah ditulis; decode tetap mempertahankan entri sah.
            val position = value.positionMs.takeIf { it >= 0 } ?: return@forEach
            root.put(
                uri,
                JSONObject()
                    .put("positionMs", position)
                    .put("durationMs", value.durationMs.coerceAtLeast(0))
                    .put("updatedAt", value.updatedAt),
            )
        }
        return root.toString()
    }

    fun decode(
        raw: String?,
        now: Long = System.currentTimeMillis(),
        limit: Int = LIMIT,
        maxAgeMs: Long = MAX_AGE_MS,
    ): Map<String, Entry> {
        if (raw.isNullOrBlank()) return emptyMap()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        return root.keys().asSequence()
            .mapNotNull { uri ->
                val value = root.optJSONObject(uri) ?: return@mapNotNull null
                val position = value.optLong("positionMs", -1L)
                val duration = value.optLong("durationMs", 0L)
                val updated = value.optLong("updatedAt", 0L)
                if (position < 0 || updated <= 0 || now - updated > maxAgeMs) null
                else uri to Entry(position, duration.coerceAtLeast(0), updated)
            }
            .sortedByDescending { it.second.updatedAt }
            .take(limit.coerceAtLeast(0))
            .toMap(LinkedHashMap())
    }
}
