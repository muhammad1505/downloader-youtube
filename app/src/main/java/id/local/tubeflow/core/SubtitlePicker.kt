package id.local.tubeflow.core

/**
 * Pilihan subtitle mengikuti konsep platform sumber.
 *
 * YouTube punya "bahasa asli": caption otomatisnya menandai bahasa asli dengan akhiran `-orig`,
 * sedangkan sisanya terjemahan mesin. Sumber lain (mis. Bstation) hanya menyediakan beberapa track
 * manual tanpa penanda seperti itu, jadi "bahasa asli" tidak punya arti yang jelas — yang benar
 * adalah menampilkan bahasa yang memang tersedia.
 */
object SubtitlePicker {
    const val ORIGINAL = "asli"
    const val ALL = ".*"

    private val youtubePlatforms = setOf("youtube", "youtube_music")

    fun isYouTube(meta: VideoMetadata?): Boolean = meta?.platformId in youtubePlatforms

    /** Kode bahasa yang benar-benar dilaporkan sumber, urut seperti metadata (tanpa duplikat). */
    fun available(meta: VideoMetadata?): List<String> = meta?.subtitleLangs.orEmpty()
        .map { it.trim() }
        .filter { it.isNotEmpty() && it != "live_chat" }
        .distinct()

    /** Pasangan nilai→label untuk chip pemilih subtitle. */
    fun options(meta: VideoMetadata?): List<Pair<String, String>> {
        val youtube = isYouTube(meta)
        val langs = available(meta)
        val entries = buildList {
            if (youtube) add(ORIGINAL to "Bahasa asli")
            if (langs.isEmpty()) {
                // Sumber tidak melaporkan daftar CC; sediakan pilihan umum supaya pengguna tetap
                // bisa meminta subtitle bila ternyata tersedia setelah diunduh.
                if (!youtube) {
                    add("id" to "Bahasa Indonesia")
                    add("en" to "Bahasa Inggris")
                }
            } else {
                langs.forEach { add(it to label(it)) }
            }
            add(ALL to "Semua bahasa")
        }
        return entries.distinctBy { it.first }
    }

    /** Nama bahasa Indonesia untuk kode ISO; kode tak dikenal ditampilkan apa adanya. */
    fun label(code: String): String {
        val value = code.trim().lowercase()
        if (value == ORIGINAL) return "Bahasa asli"
        if (value == ALL) return "Semua bahasa"
        return VideoPlayback.languageName(value).ifBlank {
            VideoPlayback.languageName(value.substringBefore('-')).ifBlank { value.uppercase() }
        }
    }

    /**
     * Pilihan saat download subtitle dinyalakan. Bahasa perangkat dipakai lebih dulu bila sumber
     * menyediakannya (aplikasi ini berbahasa Indonesia), lalu pilihan pertama sumber. "Bahasa asli"
     * hanya untuk YouTube karena hanya di sana arti kode itu pasti.
     */
    fun defaultSelection(meta: VideoMetadata?, deviceLanguage: String = ""): String {
        // YouTube tetap memakai "Bahasa asli": auto-caption `-orig` membuat artinya pasti.
        if (isYouTube(meta)) return ORIGINAL
        val langs = available(meta)
        // Java lama memakai kode "in" untuk Indonesia; samakan dengan tag subtitle Bstation ("id").
        val preferred = deviceLanguage.trim().lowercase().let { if (it == "in") "id" else it }
        if (preferred.isNotEmpty()) {
            langs.firstOrNull { it.equals(preferred, true) || it.startsWith("$preferred-", true) }
                ?.let { return it }
        }
        return langs.firstOrNull() ?: "id"
    }

    /** Label ringkas untuk ringkasan unduhan dan kartu pratinjau. */
    fun describe(code: String): String = when (code.trim()) {
        "" -> ""
        ORIGINAL -> "bahasa asli"
        ALL -> "semua bahasa"
        else -> label(code)
    }
}
