package id.local.tubeflow.core

import org.json.JSONArray
import org.json.JSONObject

/** Satu folder SAF tambahan yang ikut dipindai ke Koleksi; tidak mengubah tujuan output. */
data class CollectionFolder(val uri: String, val label: String)

/**
 * Sumber pemindaian SAF: folder bawaan Download/[folder], folder tujuan output, dan folder koleksi
 * tambahan. Hanya folder yang terdaftar yang dipindai.
 */
data class CollectionSource(val tree: String, val label: String, val removable: Boolean)

/**
 * Aturan murni untuk folder koleksi: label, nama unik, deteksi tumpang tindih, dan penyimpanan.
 * Tidak menyentuh provider atau filesystem supaya bisa diuji di JVM.
 */
object CollectionFolders {
    const val MAX = 8
    const val FALLBACK_LABEL = "Folder pilihan"

    fun normalize(tree: String): String = tree.trim().trimEnd('/')

    /**
     * Membaca document id dari URI tree SAF tanpa memanggil provider. Contoh:
     * `content://com.android.externalstorage.documents/tree/primary%3ADownload%2FMusik`
     * menghasilkan `primary:Download/Musik`.
     */
    fun documentId(tree: String): String? {
        val raw = normalize(tree)
        val marker = "/tree/"
        val start = raw.indexOf(marker).takeIf { it >= 0 }?.plus(marker.length) ?: return null
        val end = raw.indexOf('/', start).let { if (it < 0) raw.length else it }
        return percentDecode(raw.substring(start, end)).takeIf { it.isNotBlank() }
    }

    /** Label ramah untuk document id tree; tidak menebak isi folder. */
    fun labelForId(documentId: String): String {
        val id = documentId.trim().trimEnd('/')
        if (id.isBlank()) return FALLBACK_LABEL
        if (id.startsWith("raw:")) {
            val path = id.removePrefix("raw:").trim()
            val emulated = path.startsWith("/storage/emulated/")
            val relative = path.replaceFirst(Regex("""^/storage/(?:emulated/[0-9]+|[^/]+)/?"""), "").trim('/')
            if (relative.isBlank()) return FALLBACK_LABEL
            return if (emulated) relative else "SD card/$relative"
        }
        val volume = id.substringBefore(':', missingDelimiterValue = "")
        val path = id.substringAfter(':', missingDelimiterValue = "").trim('/')
        if (path.isBlank()) return volume.ifBlank { FALLBACK_LABEL }
        return if (volume == "primary" || volume.isBlank()) path else "SD card/$path"
    }

    /** Nama tampilan unik agar dua folder berbeda tidak menyatu di filter Koleksi. */
    fun uniqueLabel(base: String, taken: Collection<String>): String {
        val clean = base.trim().ifBlank { FALLBACK_LABEL }
        if (clean !in taken) return clean
        var number = 2
        while ("$clean ($number)" in taken && number < 1000) number++
        return "$clean ($number)"
    }

    /** True bila [candidate] sama dengan [other] atau berada di dalamnya (atau sebaliknya). */
    fun overlaps(candidate: String, other: String): Boolean {
        val a = candidate.trim().trimEnd('/')
        val b = other.trim().trimEnd('/')
        if (a.isBlank() || b.isBlank()) return false
        return a == b || a.startsWith("$b/") || b.startsWith("$a/")
    }

    /**
     * Menggabungkan sumber tanpa duplikat URI. Folder bawaan dan output tidak dapat dihapus dari
     * Koleksi; hanya folder tambahan yang bisa dilepas.
     */
    fun sources(
        defaultTree: String?,
        defaultLabel: String,
        outputTree: String?,
        extras: List<CollectionFolder>,
    ): List<CollectionSource> {
        val result = mutableListOf<CollectionSource>()
        val seen = mutableSetOf<String>()
        val labels = mutableSetOf<String>()
        fun add(tree: String?, label: String, removable: Boolean) {
            val key = normalize(tree.orEmpty())
            if (key.isBlank() || !seen.add(key)) return
            val shown = uniqueLabel(label, labels)
            labels += shown
            result += CollectionSource(key, shown, removable)
        }
        add(defaultTree, defaultLabel, removable = false)
        add(outputTree, FALLBACK_LABEL, removable = false)
        extras.forEach { add(it.uri, it.label, removable = true) }
        return result
    }

    fun encode(folders: List<CollectionFolder>): String {
        val data = JSONArray()
        folders.take(MAX).forEach { folder ->
            val uri = normalize(folder.uri)
            if (uri.isBlank()) return@forEach
            data.put(JSONObject().put("uri", uri).put("label", folder.label.trim().ifBlank { FALLBACK_LABEL }))
        }
        return data.toString()
    }

    fun decode(raw: String?): List<CollectionFolder> {
        if (raw.isNullOrBlank()) return emptyList()
        val data = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val result = mutableListOf<CollectionFolder>()
        var index = 0
        while (index < data.length() && result.size < MAX) {
            val item = data.optJSONObject(index)
            index++
            if (item == null) continue
            val uri = normalize(item.optString("uri"))
            if (uri.isBlank() || result.any { it.uri == uri }) continue
            val label = item.optString("label").trim().ifBlank {
                documentId(uri)?.let(::labelForId) ?: FALLBACK_LABEL
            }
            result += CollectionFolder(uri, uniqueLabel(label, result.map { it.label }.toSet()))
        }
        return result
    }

    private fun percentDecode(raw: String): String {
        if ('%' !in raw) return raw
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '%' && i + 2 < raw.length) {
                val value = raw.substring(i + 1, i + 3).toIntOrNull(16)
                if (value != null) {
                    out.append(value.toChar())
                    i += 3
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }
}
