package id.local.tubeflow.core

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tingkat catatan log aplikasi; dipakai untuk filter dan warna di layar Log. */
enum class LogLevel(val label: String, val badge: String) {
    DEBUG("Debug", "D"),
    INFO("Info", "I"),
    WARN("Peringatan", "W"),
    ERROR("Galat", "E");

    companion object {
        fun fromName(name: String?): LogLevel? = entries.firstOrNull { it.name == name }
    }
}

/** Kategori proses agar satu layar Log dapat memisahkan mesin, unduhan, pemutar, dan lainnya. */
enum class LogCategory(val label: String) {
    APP("Aplikasi"),
    ENGINE("Mesin"),
    DOWNLOAD("Unduhan"),
    PLAYER("Pemutar"),
    LIBRARY("Koleksi"),
    KARAOKE("Karaoke"),
    ACCOUNT("Akun"),
    UPDATE("Pembaruan"),
    STORAGE("Penyimpanan");

    companion object {
        fun fromName(name: String?): LogCategory? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Satu catatan log yang sudah disanitasi sebelum disimpan. `detail` hanya diisi untuk kegagalan
 * atau konteks teknis yang membantu, dan tetap melewati redaksi yang sama dengan [message].
 */
data class LogEntry(
    val seq: Long,
    val at: Long,
    val level: LogLevel,
    val category: LogCategory,
    val message: String,
    val detail: String? = null,
) {
    fun json(): JSONObject = JSONObject()
        .put("seq", seq)
        .put("at", at)
        .put("level", level.name)
        .put("category", category.name)
        .put("message", message)
        .apply { if (detail != null) put("detail", detail) }

    fun searchable(): String =
        "${level.label} ${category.label} $message ${detail.orEmpty()}".lowercase(Locale.ROOT)

    companion object {
        /** Baris JSONL rusak diabaikan agar satu tulisan terputus tidak menghapus seluruh riwayat. */
        fun parse(line: String): LogEntry? {
            val json = runCatching { JSONObject(line) }.getOrNull() ?: return null
            val level = LogLevel.fromName(json.optString("level")) ?: return null
            val category = LogCategory.fromName(json.optString("category")) ?: return null
            val message = json.optString("message").takeIf { it.isNotBlank() } ?: return null
            return LogEntry(
                seq = json.optLong("seq"),
                at = json.optLong("at"),
                level = level,
                category = category,
                message = message,
                detail = json.optString("detail").takeIf { it.isNotBlank() },
            )
        }
    }
}

/** Filter murni supaya perilaku pencarian dan kategori dapat diuji tanpa Android. */
data class LogFilter(
    val level: LogLevel? = null,
    val category: LogCategory? = null,
    val query: String = "",
) {
    fun matches(entry: LogEntry): Boolean {
        if (level != null && entry.level != level) return false
        if (category != null && entry.category != category) return false
        val needle = query.trim().lowercase(Locale.ROOT)
        return needle.isEmpty() || entry.searchable().contains(needle)
    }
}

/** Format waktu dan ekspor yang sama di layar Log maupun berkas yang dibagikan pengguna. */
object LogFormat {
    fun time(at: Long): String =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(Date(at))

    fun dateTime(at: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(Date(at))

    fun export(entries: List<LogEntry>, header: String): String = buildString {
        if (header.isNotBlank()) {
            appendLine(header.trimEnd())
            appendLine("---")
        }
        entries.sortedBy { it.seq }.forEach { entry ->
            append(dateTime(entry.at))
            append(" [").append(entry.level.name).append(']')
            append(" [").append(entry.category.label).append("] ")
            appendLine(entry.message)
            entry.detail?.let { appendLine(it) }
        }
    }
}
