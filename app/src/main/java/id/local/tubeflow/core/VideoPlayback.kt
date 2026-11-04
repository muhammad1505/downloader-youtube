package id.local.tubeflow.core

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mode tampilan video di dalam PlayerView; urutannya adalah urutan tombol.
 * FIT menampilkan seluruh frame tanpa distorsi, FILL memenuhi layar dengan memotong tepi,
 * dan ZOOM melakukan hal yang sama dengan pembesaran tambahan.
 */
enum class VideoAspect(val label: String, val crops: Boolean) {
    FIT("Utuh", false),
    FILL("Isi layar", true),
    ZOOM("Zoom", true);

    fun next(): VideoAspect = entries[(ordinal + 1) % entries.size]
}

enum class VideoRepeat(val label: String) {
    OFF("Mati"),
    ALL("Semua video"),
    ONE("Satu video");

    fun next(): VideoRepeat = entries[(ordinal + 1) % entries.size]
}

/** Pilihan renderer video; perangkat tertentu hanya mampu memutar codec lewat FFmpeg. */
enum class VideoDecoderMode(val label: String, val shortLabel: String) {
    AUTO("Otomatis", "Auto"),
    HARDWARE("Perangkat", "HW"),
    SOFTWARE("Software", "SW");

    fun next(): VideoDecoderMode = entries[(ordinal + 1) % entries.size]
}

/** Preset warna sederhana untuk Smart Enhance; efek dibuat di ViewModel (bukan di kelas host). */
enum class VideoEnhance(val label: String, val shortLabel: String) {
    OFF("Mati", "Mati"),
    LIGHT("Ringan", "Ringan"),
    VIVID("Vivid", "Vivid"),
    SHARP("Tajam", "Tajam");

    fun next(): VideoEnhance = entries[(ordinal + 1) % entries.size]
}

/** Preset equalizer untuk audio video, terpisah dari equalizer pemutar lagu. */
enum class VideoEqualizer(val label: String, val shortLabel: String) {
    OFF("Mati", "Mati"),
    BASS("Bass", "Bass"),
    VOCAL("Vokal", "Vokal"),
    CINEMA("Bioskop", "Film"),
    ROCK("Rock", "Rock");

    fun next(): VideoEqualizer = entries[(ordinal + 1) % entries.size]

    /** Kurva kasar per frekuensi tengah band; nilai dB akan dijepit ke rentang Equalizer. */
    fun levelDb(centerHz: Int): Float = when (this) {
        OFF -> 0f
        BASS -> when {
            centerHz < 150 -> 7f
            centerHz < 400 -> 4f
            centerHz < 1_000 -> 1f
            centerHz < 4_000 -> 0f
            else -> -1f
        }
        VOCAL -> when {
            centerHz < 250 -> -3f
            centerHz < 900 -> 1f
            centerHz < 2_800 -> 4f
            centerHz < 5_500 -> 2f
            else -> 0f
        }
        CINEMA -> when {
            centerHz < 120 -> 4f
            centerHz < 500 -> 2f
            centerHz < 1_500 -> 0f
            centerHz < 3_500 -> 2f
            else -> 4f
        }
        ROCK -> when {
            centerHz < 120 -> 5f
            centerHz < 500 -> 3f
            centerHz < 1_500 -> 1f
            centerHz < 4_000 -> 2f
            else -> 3f
        }
    }
}

/** Efek audio tambahan di atas equalizer; ketersediaan bergantung pada perangkat. */
enum class VideoAudioEffect(val label: String, val shortLabel: String) {
    OFF("Mati", "Mati"),
    BASS_BOOST("Bass Boost", "Bass"),
    SPATIAL("Ruang", "Ruang"),
    STUDIO("Studio", "Studio");

    fun next(): VideoAudioEffect = entries[(ordinal + 1) % entries.size]
}

/**
 * Orientasi layar pemutar. Otomatis mengikuti sensor perangkat (termasuk saat kunci rotasi
 * sistem aktif), sedangkan dua mode lain mengunci ke satu arah untuk menonton sambil berbaring.
 */
enum class VideoOrientation(val label: String) {
    AUTO("Otomatis"),
    LANDSCAPE("Lanskap"),
    PORTRAIT("Potret");

    fun next(): VideoOrientation = entries[(ordinal + 1) % entries.size]
}

/** Skala teks subtitle relatif terhadap ukuran bawaan SubtitleView Media3. */
enum class VideoSubtitleSize(val label: String, val scale: Float) {
    SMALL("Kecil", 0.8f),
    NORMAL("Normal", 1f),
    LARGE("Besar", 1.25f),
    HUGE("Sangat besar", 1.5f),
}

/** Gaya teks subtitle; warna dan garis tepi dipetakan ke CaptionStyleCompat di layar. */
enum class VideoSubtitleStyle(val label: String) {
    OUTLINE("Garis tepi"),
    BOLD("Tebal"),
    YELLOW("Kuning"),
}

/** Jarak subtitle dari tepi bawah; makin tinggi makin jauh dari kontrol pemutar. */
enum class VideoSubtitlePosition(val label: String, val bottomPaddingFraction: Float) {
    BOTTOM("Bawah", 0.08f),
    RAISED("Agak naik", 0.14f),
    HIGH("Naik", 0.22f),
    TOP("Tinggi", 0.30f),
}

/** Opasitas kotak latar subtitle. Nol berarti teks hanya memakai garis tepi. */
enum class VideoSubtitleBackground(val label: String, val alphaPercent: Int) {
    TRANSPARENT("Transparan", 0),
    LIGHT("Samar", 30),
    MEDIUM("Sedang", 60),
    DARK("Gelap", 85),
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
    const val SUBTITLE_DELAY_STEP_MS = 250
    const val SUBTITLE_DELAY_LIMIT_MS = 10_000
    /** Buffer sekejap saat refill normal tidak boleh memunculkan spinner. */
    const val BUFFERING_INDICATOR_DELAY_MS = 300L

    val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    private val languages = mapOf(
        "id" to "Indonesia",
        "ind" to "Indonesia",
        "en" to "Inggris",
        "eng" to "Inggris",
        "jv" to "Jawa",
        "jav" to "Jawa",
        "ms" to "Melayu",
        "msa" to "Melayu",
        "may" to "Melayu",
        "ar" to "Arab",
        "ara" to "Arab",
        "ja" to "Jepang",
        "jpn" to "Jepang",
        "ko" to "Korea",
        "kor" to "Korea",
        "zh" to "Mandarin",
        "zho" to "Mandarin",
        "chi" to "Mandarin",
        "es" to "Spanyol",
        "spa" to "Spanyol",
        "fr" to "Prancis",
        "fra" to "Prancis",
        "fre" to "Prancis",
        "de" to "Jerman",
        "deu" to "Jerman",
        "ger" to "Jerman",
        "pt" to "Portugis",
        "por" to "Portugis",
        "ru" to "Rusia",
        "rus" to "Rusia",
        "hi" to "Hindi",
        "hin" to "Hindi",
        "th" to "Thai",
        "tha" to "Thai",
        "vi" to "Vietnam",
        "vie" to "Vietnam",
        "tr" to "Turki",
        "tur" to "Turki",
        "it" to "Italia",
        "ita" to "Italia",
        "nl" to "Belanda",
        "nld" to "Belanda",
        "dut" to "Belanda",
        "pl" to "Polandia",
        "pol" to "Polandia",
        "uk" to "Ukraina",
        "ukr" to "Ukraina",
    )

    private val languageToken = Regex("""(?:^|[._\- ])([a-z]{2,3})(?:[._\- ]|$)""")

    private fun abs(value: Float): Float = if (value < 0) -value else value

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

    /**
     * Posisi yang aman untuk membuka ulang sesi. Setelah error dekoder, ExoPlayer dapat
     * melaporkan posisi 0 walaupun UI masih menyimpan posisi terakhir; jangan sampai retry atau
     * fallback software memulai video dari awal. Posisi pemutar yang lebih maju tetap dipakai
     * agar lompatan kecil dari ticker tidak hilang.
     */
    fun reopenPosition(stateMs: Long, playerMs: Long): Long =
        maxOf(stateMs.coerceAtLeast(0), playerMs.coerceAtLeast(0))

    /** Home hanya boleh masuk PiP saat video aktif diputar, bukan saat aplikasi membuka picker. */
    fun shouldEnterPipOnLeave(active: Boolean, playing: Boolean, suppressed: Boolean): Boolean =
        active && playing && !suppressed

    /**
     * Spinner buffering hanya saat ExoPlayer benar-benar menunggu data. `isLoading` saja tidak
     * cukup karena prefetch latar yang normal juga menyalakannya; memakainya membuat indikator
     * berkedip setiap buffer refill walaupun video terus berjalan.
     */
    fun shouldShowBuffering(playbackState: Int, hasError: Boolean): Boolean =
        !hasError && playbackState == Player.STATE_BUFFERING

    /**
     * Spinner baru muncul setelah buffer bertahan cukup lama. ExoPlayer dapat masuk
     * STATE_BUFFERING sekejap saat refill/seek; tanpa jeda ini indikator berkedip meski
     * video tidak benar-benar berhenti.
     */
    fun shouldRevealBuffering(buffering: Boolean, hasError: Boolean, waitedMs: Long): Boolean =
        buffering && !hasError && waitedMs >= BUFFERING_INDICATOR_DELAY_MS

    /** Setelah fallback software gagal, pemutar harus dibuat ulang agar percobaan berikutnya bisa hardware. */
    fun needsDecoderRestart(hasPlayer: Boolean, softwareFallbackUsed: Boolean): Boolean =
        !hasPlayer || softwareFallbackUsed

    /**
     * Kunci input pipeline efek video. Setiap `setVideoEffects` membangun ulang pipeline GL dan
     * memutus gambar sesaat; kunci yang sama karena itu harus dilewati supaya tick progres dan
     * perubahan viewport tidak memicu hitch berkala. Pemutar baru menghasilkan `playerVersion`
     * baru, sehingga efek tetap diterapkan ulang saat sesi dibuka kembali.
     */
    fun effectsKey(
        enhance: VideoEnhance,
        saving: Boolean,
        aspect: VideoAspect,
        width: Int,
        height: Int,
        playerVersion: Int,
    ): String = "${if (saving) VideoEnhance.OFF else enhance}|$aspect|$width|$height|$playerVersion"

    /** Delay subtitle positif menampilkan teks lebih lambat; batasnya menjaga hasil tetap masuk akal. */
    fun adjustSubtitleDelay(currentMs: Int, deltaMs: Int): Int =
        (currentMs + deltaMs).coerceIn(-SUBTITLE_DELAY_LIMIT_MS, SUBTITLE_DELAY_LIMIT_MS)

    fun subtitleDelayLabel(delayMs: Int): String {
        if (delayMs == 0) return "0,00 dtk"
        val magnitude = if (delayMs < 0) -delayMs else delayMs
        val seconds = String.format(Locale.ROOT, "%.2f", magnitude / 1000.0).replace('.', ',')
        return "${if (delayMs > 0) "+" else "−"}$seconds dtk"
    }

    /**
     * Kegagalan yang layak dicoba ulang dengan dekoder software: codec/query/performa
     * perangkat dan TIMEOUT renderer. Pada berkas lokal, MediaCodec yang macet memakai
     * TIMEOUT; percobaan ulang dengan FFmpeg/dav1d lebih berguna daripada langsung error.
     */
    fun isCodecFailure(errorCode: Int): Boolean = when (errorCode) {
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_TIMEOUT -> true
        else -> false
    }

    /** Fallback otomatis hanya sekali dan tidak menimpa pilihan manual "Perangkat". */
    fun shouldUseSoftwareFallback(
        errorCode: Int,
        mode: VideoDecoderMode,
        alreadyTried: Boolean,
    ): Boolean = !alreadyTried && mode != VideoDecoderMode.HARDWARE && isCodecFailure(errorCode)

    /** Kecepatan berikutnya untuk tombol cepat; urutan tetap dan berputar. */
    fun nextSpeed(current: Float): Float {
        val index = speeds.indexOfFirst { abs(it - current) < 0.001f }.takeIf { it >= 0 } ?: 3
        return speeds[(index + 1) % speeds.size]
    }

    /** MIME subtitle dari tipe yang dilaporkan penyedia berkas, untuk berkas tanpa ekstensi. */
    fun subtitleMimeFromType(type: String?): String? = when (type?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)) {
        "application/x-subrip", "application/x-srt", "application/srt" -> "application/x-subrip"
        "text/vtt", "text/webvtt" -> "text/vtt"
        "text/x-ssa", "text/x-ass", "text/ass" -> "text/x-ssa"
        "application/ttml+xml", "application/xml+ttml" -> "application/ttml+xml"
        else -> null
    }

    /** Nama screenshot: buang karakter khusus, batasi panjang judul, lalu tambahkan waktu lokal. */
    fun screenshotFileName(title: String, at: Long = System.currentTimeMillis()): String {
        val safe = title.trim()
            .replace(Regex("[^\\p{L}\\p{N} _-]"), "")
            .replace(Regex("\\s+"), "-")
            .trim('-', '_', ' ')
            .take(48)
            .ifBlank { "video" }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(at))
        return "$safe-$stamp.png"
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

    /** Tag bahasa dinormalkan ke nama yang sama agar `id`, `id-ID`, dan `ind` dianggap setara. */
    private fun canonicalLanguage(tag: String): String {
        val primary = tag.trim().lowercase(Locale.ROOT).substringBefore('-').substringBefore('_')
        if (primary.isBlank()) return ""
        return languages[primary] ?: primary
    }

    /**
     * Track subtitle yang dipilih otomatis saat video dibuka. Subtitle hasil unduhan diutamakan
     * agar berkas `.srt` yang baru diunduh langsung tampil, lalu di dalamnya bahasa perangkat
     * menang (mis. ponsel Indonesia memilih `id` walau ada subtitle Inggris). Track bawaan video
     * hanya dipakai bila tidak ada subtitle unduhan yang cocok.
     *
     * @return indeks pada [languages], atau -1 bila tidak ada track.
     */
    fun preferredSubtitleIndex(
        languages: List<String>,
        downloadedLanguages: List<String> = emptyList(),
        preferredLanguages: List<String> = emptyList(),
    ): Int {
        if (languages.isEmpty()) return -1
        val downloaded = downloadedLanguages.map(::canonicalLanguage).filter { it.isNotBlank() }.toSet()
        val preferred = preferredLanguages.map(::canonicalLanguage).filter { it.isNotBlank() }
        for (tag in preferred) {
            languages.indices.firstOrNull {
                canonicalLanguage(languages[it]) == tag && canonicalLanguage(languages[it]) in downloaded
            }?.let { return it }
        }
        languages.indices.firstOrNull { canonicalLanguage(languages[it]) in downloaded }?.let { return it }
        for (tag in preferred) {
            languages.indices.firstOrNull { canonicalLanguage(languages[it]) == tag }?.let { return it }
        }
        return 0
    }

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
     *
     * Baris MediaStore hanya berupa ID (`content://media/.../197934`) tanpa nama, sehingga ekstensi
     * dan bahasanya tidak bisa ditebak. [nameOf] membaca nama tampilan dari penyedia berkas agar
     * subtitle yang sudah terunduh tetap muncul di pemutar.
     */
    fun subtitlesFromSidecars(sidecars: String?, nameOf: (String) -> String? = { null }): List<VideoSubtitle> {
        if (sidecars.isNullOrBlank()) return emptyList()
        return sidecars.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("sub|") }
            .mapNotNull { line ->
                val uri = line.substringAfter('|', "").trim()
                if (uri.isEmpty()) return@mapNotNull null
                val fromUri = fileName(uri)
                // Hanya tanya penyedia bila URI tidak memuat ekstensi (baris MediaStore berangka).
                val name = if (subtitleMime(fromUri) != null) fromUri
                else nameOf(uri)?.takeIf { subtitleMime(it) != null } ?: fromUri
                val mime = subtitleMime(name) ?: return@mapNotNull null
                val language = languageFrom(name)
                val label = languageName(language).ifBlank { name.ifBlank { "Subtitle" } }
                VideoSubtitle(uri, mime, language, label)
            }
            .distinctBy { it.uri }
            .toList()
    }

    fun entry(item: MediaItem, sidecars: String? = null, nameOf: (String) -> String? = { null }): VideoEntry = VideoEntry(
        uri = item.location,
        title = item.title.ifBlank { item.displayName.substringBeforeLast('.') },
        subtitles = subtitlesFromSidecars(sidecars, nameOf),
    )

    /** Daftar video yang dapat diputar pada urutan Koleksi saat ini, mulai dari item yang diketuk. */
    fun videoEntries(
        items: List<MediaItem>,
        startUri: String,
        sidecars: (MediaItem) -> String? = { null },
        nameOf: (String) -> String? = { null },
    ): Pair<List<VideoEntry>, Int> {
        val playable = items.filter { it.kind == "VIDEO" && it.availability == "AVAILABLE" }
        val start = playable.indexOfFirst { it.uri == startUri || it.location == startUri }.coerceAtLeast(0)
        return playable.map { entry(it, sidecars(it), nameOf) } to start
    }

    /** Daftar video selesai pada urutan Riwayat saat ini, mulai dari job yang diketuk. */
    fun videoEntries(jobs: List<DownloadJob>, startJobId: String, nameOf: (String) -> String? = { null }): Pair<List<VideoEntry>, Int> {
        val playable = jobs.mapNotNull { job -> entry(job, nameOf)?.let { job to it } }
        val start = playable.indexOfFirst { (job, _) -> job.id == startJobId }.coerceAtLeast(0)
        return playable.map { it.second } to start
    }

    fun entry(job: DownloadJob, nameOf: (String) -> String? = { null }): VideoEntry? {
        if (job.mode != MediaMode.VIDEO || job.state != JobState.COMPLETED) return null
        val uri = job.outputUri?.takeIf { it.isNotBlank() } ?: return null
        return VideoEntry(uri, job.title, subtitlesFromSidecars(job.sidecarUris, nameOf))
    }

    private fun extension(nameOrUri: String): String =
        nameOrUri.substringBefore('?').substringBefore('#').substringAfterLast('/').substringAfterLast('.', "").lowercase(Locale.ROOT)

    private fun fileName(raw: String): String = runCatching {
        URLDecoder.decode(raw.substringBefore('?').substringBefore('#').substringAfterLast('/'), "UTF-8")
    }.getOrDefault(raw.substringBefore('?').substringAfterLast('/'))
}

/** Subtitle yang dipilih pengguna dari penyimpanan; disimpan per URI video agar tetap ada saat dibuka lagi. */
object VideoExternalSubtitleCodec {
    const val LIMIT = 12

    fun encode(subtitles: List<VideoSubtitle>): String {
        val array = JSONArray()
        subtitles.filter { it.uri.isNotBlank() && it.mime.isNotBlank() }
            .distinctBy { it.uri }
            .take(LIMIT)
            .forEach { subtitle ->
                array.put(
                    JSONObject()
                        .put("uri", subtitle.uri)
                        .put("mime", subtitle.mime)
                        .put("language", subtitle.language)
                        .put("label", subtitle.label),
                )
            }
        return array.toString()
    }

    fun decode(raw: String?): List<VideoSubtitle> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val value = array.optJSONObject(index) ?: return@mapNotNull null
            val uri = value.optString("uri").trim()
            val mime = value.optString("mime").trim()
            if (uri.isBlank() || mime.isBlank()) null
            else VideoSubtitle(
                uri = uri,
                mime = mime,
                language = value.optString("language").trim(),
                label = value.optString("label").trim().ifBlank { "Subtitle" },
            )
        }.distinctBy { it.uri }.take(LIMIT)
    }
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
