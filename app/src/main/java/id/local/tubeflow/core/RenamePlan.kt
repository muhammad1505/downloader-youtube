package id.local.tubeflow.core

/**
 * Rencana ubah nama massal: murni dan deterministik supaya pratinjau di layar sama dengan hasil
 * yang diterapkan. Ekstensi selalu diambil dari nama aktual, dan pemanggil tetap memakai
 * [MediaRepository.rename] yang memeriksa konflik, nama dari provider, serta jurnal pemulihan.
 */
object RenamePlan {
    /** Batas aman nama tampilan; provider memberi ruang lebih, sisa dipakai ekstensi/sufiks. */
    const val MAX_NAME_BYTES = 200

    val tokens = listOf("{judul}", "{artis}", "{album}", "{track}", "{nama}")

    private val illegal = Regex("""[\\/:*?"<>|\p{Cntrl}]""")
    private val spaces = Regex("\\s+")
    private val edges = Regex("""^[\s._\-–—]+|[\s._\-–—]+$""")
    // Kurung kurawal sisa token dibuang manual: parser regex Android menolak "}" tanpa escape,
    // sementara JVM unit test menerimanya — pola lama membuat kelas gagal init di perangkat.
    private val emptyBrackets = Regex("""\(\s*\)|\[\s*\]""")
    private fun dropUnknownTokens(text: String): String {
        val result = StringBuilder(text.length)
        var cursor = 0
        while (cursor < text.length) {
            val start = text.indexOf('{', cursor)
            if (start < 0) { result.append(text, cursor, text.length); break }
            result.append(text, cursor, start)
            val end = text.indexOf('}', start + 1)
            if (end < 0 || end - start - 1 > 24) { result.append('{'); cursor = start + 1 } else cursor = end + 1
        }
        return result.toString()
    }

    data class Item(
        val uri: String,
        val displayName: String,
        val title: String,
        val artist: String = "",
        val album: String = "",
        val track: String = "",
    )

    /** [skip] berisi alasan bila berkas tidak akan diubah; [applies] adalah satu-satunya gerbang. */
    data class Proposal(val uri: String, val before: String, val after: String, val skip: String? = null) {
        val applies: Boolean get() = skip == null && before != after
    }

    data class Plan(val proposals: List<Proposal>) {
        val changes: Int get() = proposals.count { it.applies }
        val kept: Int get() = proposals.size - changes
    }

    /** Nama tanpa ekstensi yang aman: akhiran titik tanpa ekstensi wajar dianggap bagian nama. */
    fun stem(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || name.length - dot - 1 !in 1..10) return name
        val extension = name.substring(dot + 1)
        return if (extension.all { it.isLetterOrDigit() }) name.substring(0, dot) else name
    }

    /** Akhiran berkas apa adanya (termasuk titik), kosong bila nama tidak punya ekstensi wajar. */
    fun extension(name: String): String = name.substring(stem(name).length)

    fun clean(raw: String): String {
        val replaced = raw.replace(illegal, " ").replace(emptyBrackets, "").replace(spaces, " ")
        return replaced.trim().replace(edges, "").replace(spaces, " ").trim()
    }

    /** Judul/artis kosong pada pola tidak boleh meninggalkan pemisah (" - Judul" → "Judul"). */
    fun expand(pattern: String, item: Item): String {
        val original = stem(item.displayName)
        val text = pattern
            .replace("{judul}", item.title.ifBlank { original })
            .replace("{artis}", item.artist)
            .replace("{album}", item.album)
            .replace("{track}", item.track)
            .replace("{nama}", original)
        return clean(dropUnknownTokens(text))
    }

    private fun limited(value: String, budget: Int = MAX_NAME_BYTES): String {
        if (value.toByteArray(Charsets.UTF_8).size <= budget) return value
        var end = value.length
        while (end > 0 && value.substring(0, end).toByteArray(Charsets.UTF_8).size > budget) end--
        return value.substring(0, end).trimEnd(' ', '.', '-', '_')
    }

    /**
     * [pattern] kosong memakai mode cari-ganti pada nama asli. Nama yang tidak berubah, nama
     * kosong, dan dua item dengan tujuan sama tidak pernah diterapkan.
     */
    fun plan(items: List<Item>, pattern: String, find: String = "", replace: String = ""): Plan {
        val targets = mutableSetOf<String>()
        return Plan(items.map { item ->
            val requested = when {
                pattern.isNotBlank() -> expand(pattern, item)
                find.isNotBlank() -> clean(stem(item.displayName).replace(find, replace))
                else -> ""
            }
            val extension = extension(item.displayName)
            val name = limited(requested, MAX_NAME_BYTES - extension.toByteArray(Charsets.UTF_8).size)
            if (name.isBlank()) return@map Proposal(item.uri, item.displayName, item.displayName, "Pola tidak menghasilkan nama")
            val after = name + extension
            when {
                after == item.displayName -> Proposal(item.uri, item.displayName, after, "Nama tidak berubah")
                !targets.add(after.lowercase(java.util.Locale.ROOT)) -> Proposal(item.uri, item.displayName, after, "Nama bentrok dengan item lain")
                else -> Proposal(item.uri, item.displayName, after)
            }
        })
    }
}
