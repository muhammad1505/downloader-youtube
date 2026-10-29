package id.local.tubeflow.core

/**
 * Usulan tag katalog dari nama berkas. Ini bukan penulisan tag ke dalam audio: nilai hanya
 * disimpan di Room, dan setiap usulan ditampilkan sebagai pratinjau sebelum diterapkan.
 *
 * Urutan artis/judul mengikuti kebiasaan nama TubeFlow ("Judul - Artis"). Bila nama folder
 * (subfolder artis hasil Auto Folder) cocok dengan salah satu sisi, kecocokan itu yang dipakai —
 * bukti dari folder, bukan tebakan posisi.
 */
object TagSuggestion {
    private val separators = Regex("""\s+[-–—]\s+""")
    private val slug = Regex("[^\\p{L}\\p{N}]+")

    data class Item(
        val uri: String,
        val displayName: String,
        val title: String,
        val artist: String,
        val folder: String = "",
    )

    data class Proposal(
        val uri: String,
        val displayName: String,
        val title: String,
        val artist: String,
        val previousTitle: String,
        val previousArtist: String,
        val note: String,
    ) {
        val changed: Boolean get() = title != previousTitle || artist != previousArtist
    }

    private fun slugOf(value: String): String = value.lowercase(java.util.Locale.ROOT).replace(slug, "-").trim('-')

    data class Reading(val title: String, val artist: String, val note: String)

    /** Baca identitas dari nama berkas; artis kosong berarti tidak ada bukti, bukan tebakan. */
    fun read(displayName: String, folder: String): Reading {
        val segments = RenamePlan.stem(displayName).split(separators).map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.isEmpty()) return Reading("", "", "Nama berkas tidak terbaca")
        val folderSlug = slugOf(folder.substringAfterLast('/'))
        fun matchesFolder(value: String) = folderSlug.isNotBlank() && slugOf(value) == folderSlug
        if (segments.size == 1) {
            return Reading(Format.musicLabel(TitleSplit.identify(segments[0])), "", "Nama berkas tanpa artis")
        }
        val first = segments.first()
        val last = segments.last()
        val artist = when {
            matchesFolder(first) && !matchesFolder(last) -> first
            matchesFolder(last) && !matchesFolder(first) -> last
            else -> last
        }
        val title = segments.filter { it != artist }.joinToString(" - ")
        val identity = TitleSplit.identify(title)
        val note = if (matchesFolder(artist)) "Artis cocok dengan folder" else "Urutan dibaca Judul - Artis"
        return Reading(Format.musicLabel(identity), artist, note)
    }

    /**
     * [fillTitle] mengganti judul bila hasil baca berbeda; [fillArtist] hanya mengisi artis yang
     * masih kosong supaya artis yang sudah terbukti tidak ditimpa tebakan dari nama berkas.
     */
    fun plan(items: List<Item>, fillTitle: Boolean, fillArtist: Boolean): List<Proposal> = items.map { item ->
        val reading = read(item.displayName, item.folder)
        val title = if (fillTitle && reading.title.isNotBlank()) reading.title else item.title
        val artist = if (fillArtist && item.artist.isBlank()) reading.artist else item.artist
        Proposal(
            uri = item.uri,
            displayName = item.displayName,
            title = title,
            artist = artist,
            previousTitle = item.title,
            previousArtist = item.artist,
            note = reading.note,
        )
    }
}
