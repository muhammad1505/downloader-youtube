package id.local.tubeflow.core

import org.json.JSONObject

/**
 * Nama tampilan folder Koleksi. Alias adalah fitur lama: rename folder saat ini mengganti direktori
 * fisik, sedangkan alias yang tersisa tetap dipakai sebagai label sampai pengguna mengembalikannya.
 */
object FolderAliases {
    const val MAX_NAME = 60
    const val MAX_STORAGE_NAME = 40
    private const val MAX_ENTRIES = 500

    fun normalize(folder: String): String = folder.trim().trimEnd('/')

    /** Label tampilan: alias pengguna bila ada, selain itu jalur folder seperti sebelumnya. */
    fun label(folder: String, aliases: Map<String, String>): String =
        aliases[normalize(folder)]?.takeIf { it.isNotBlank() } ?: CollectionGroups.label(folder)

    /** Null berarti nama dapat disimpan; selain itu pesan yang siap ditampilkan ke pengguna. */
    fun validate(raw: String, folder: String, aliases: Map<String, String>): String? {
        val name = raw.trim()
        if (name.isEmpty()) return "Nama folder tidak boleh kosong."
        if (name.length > MAX_NAME) return "Nama folder maksimal $MAX_NAME karakter."
        if (name.any { it == '/' || it == '\\' }) return "Nama folder tidak boleh memakai garis miring."
        if (name.any { it.isISOControl() }) return "Nama folder berisi karakter yang tidak didukung."
        val key = normalize(folder)
        val duplicate = aliases.any { (other, value) ->
            other != key && value.trim().equals(name, ignoreCase = true)
        }
        return if (duplicate) "Nama itu sudah dipakai folder lain." else null
    }

    /** Null berarti nama folder fisik dapat dipakai; selain itu pesan yang siap ditampilkan. */
    fun validateStorageName(raw: String): String? {
        val name = raw.trim()
        if (name.isEmpty()) return "Nama folder tidak boleh kosong."
        if (name.length > MAX_STORAGE_NAME) return "Nama folder maksimal $MAX_STORAGE_NAME karakter."
        if (name.any { it == '/' || it == '\\' }) return "Nama folder tidak boleh memakai garis miring."
        if (Selection.safeFolder(name) == null) {
            return "Nama folder harus diawali huruf atau angka dan hanya memakai huruf, angka, spasi, titik, garis, atau tanda kurung."
        }
        return null
    }

    /** Menambah alias, atau menghapusnya bila [raw] kosong; kunci selalu dinormalisasi. */
    fun withAlias(aliases: Map<String, String>, folder: String, raw: String): Map<String, String> {
        val key = normalize(folder)
        if (key.isBlank()) return aliases
        val name = raw.trim()
        return if (name.isEmpty()) aliases - key else aliases + (key to name)
    }

    fun encode(aliases: Map<String, String>): String {
        val data = JSONObject()
        aliases.entries.take(MAX_ENTRIES).forEach { (folder, name) ->
            val key = normalize(folder)
            if (key.isNotBlank() && name.isNotBlank()) data.put(key, name.trim())
        }
        return data.toString()
    }

    fun decode(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val data = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val result = LinkedHashMap<String, String>()
        val keys = data.keys()
        while (keys.hasNext() && result.size < MAX_ENTRIES) {
            val rawKey = keys.next()
            val folder = normalize(rawKey)
            val name = data.optString(rawKey).trim()
            if (folder.isNotBlank() && name.isNotBlank()) result[folder] = name
        }
        return result
    }
}

/** Ringkasan isi satu folder Koleksi; dihitung dari baris katalog, bukan dari halaman yang termuat. */
data class FolderSummary(
    val folder: String,
    val total: Int,
    val audio: Int,
    val video: Int,
    val available: Int,
    val unavailable: Int,
    val bytes: Long,
    val unknownSizes: Int,
) {
    companion object {
        fun of(folder: String, items: List<MediaItem>): FolderSummary {
            val available = items.filter { it.availability == "AVAILABLE" }
            return FolderSummary(
                folder = folder,
                total = items.size,
                audio = items.count { it.kind == "AUDIO" },
                video = items.count { it.kind == "VIDEO" },
                available = available.size,
                unavailable = items.size - available.size,
                bytes = available.sumOf { it.sizeBytes ?: 0L },
                unknownSizes = available.count { it.sizeBytes == null },
            )
        }
    }
}
