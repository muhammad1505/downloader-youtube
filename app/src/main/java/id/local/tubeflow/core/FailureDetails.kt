package id.local.tubeflow.core

import android.content.Context
import android.util.AtomicFile
import id.local.tubeflow.BuildConfig
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A deliberately small support report. Raw URLs, credentials and local paths never leave here. */
data class FailureDetails(val category: String, val phase: String, val message: String,
    val appVersion: String, val engineVersion: String, val at: Long? = null, val http: Int? = null) {
    fun text(): String = listOf(
        "TubeFlow: $appVersion", "Engine: $engineVersion", "Tahap: ${safe(phase)}", "Kategori: $category",
        "Waktu: " + (at?.let { SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT).format(Date(it)) } ?: "Tidak tercatat"),
        "HTTP: ${http ?: "Tidak tercatat"}", "Detail: ${safe(message)}",
    ).joinToString("\n")

    val userMessage: String get() = when (category) {
        "rate_limit" -> "Penyedia membatasi permintaan. Tunggu sebelum mencoba lagi."
        "access" -> "Sumber menolak akses. Coba lagi nanti atau periksa apakah konten masih dapat diakses."
        "storage" -> "Ruang penyimpanan tidak cukup. Kosongkan ruang lalu coba lagi."
        "permission" -> "Akses penyimpanan ditolak. Periksa izin atau pilih ulang folder."
        "network" -> "Koneksi ke penyedia terputus. Coba lagi setelah koneksi pulih."
        "provider" -> "Penyedia sedang bermasalah. Coba lagi nanti."
        else -> "Proses gagal. Coba lagi; buka Detail error untuk pemeriksaan lebih lanjut."
    }

    fun json() = JSONObject().put("category", category).put("phase", phase).put("message", safe(message))
        .put("app", appVersion).put("engine", engineVersion).put("at", at ?: JSONObject.NULL)
        .put("http", http ?: JSONObject.NULL)

    companion object {
        fun safe(raw: String): String {
            var text = raw.replace(Regex("\u001B\\[[0-9;]*[A-Za-z]"), "")
            text = text.replace(Regex("(?im)(authorization|set-cookie|cookie)\\s*[:=][^\\r\\n]+"), "$1=[disamarkan]")
            text = text.replace(Regex("(?i)\\b(?:https?|content|file)://[^\\s<>]+"), "[alamat disamarkan]")
            text = text.replace(Regex("(?i)\\b(token|secret_token|signature|sig|password|passwd|api[_-]?key|credential)\\s*[:=]\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s,;]+)"), "$1=[disamarkan]")
            text = text.replace(Regex("(?:/(?:data|storage|sdcard|home|mnt|tmp)/[^\\s\"']+|[A-Za-z]:\\\\[^\\s]+)"), "[path disamarkan]")
            return text.filter { it == '\n' || it == '\t' || !it.isISOControl() }.take(2000)
        }
        fun capture(raw: String, phase: String, app: String, engine: String, at: Long? = null): FailureDetails {
            val http = Regex("(?i)\\b(?:HTTP(?: Error)?|status(?: code)?)\\s*[:=]?\\s*([45][0-9]{2})\\b")
                .find(raw)?.groupValues?.get(1)?.toIntOrNull()
            val lower = raw.lowercase(Locale.ROOT)
            val category = when {
                http == 429 -> "rate_limit"
                http == 401 || http == 403 -> "access"
                http != null && http >= 500 -> "provider"
                listOf("enospc", "no space left", "penyimpanan tidak cukup").any { it in lower } -> "storage"
                listOf("securityexception", "permission denied", "access denied", "akses folder", "izin akses").any { it in lower } -> "permission"
                listOf("timeout", "timed out", "unknownhost", "unable to resolve", "connection", "network is unreachable").any { it in lower } -> "network"
                else -> "unknown"
            }
            return FailureDetails(category, safe(phase), safe(raw), app, engine, at, http)
        }
        fun fromJson(raw: String): FailureDetails = JSONObject(raw).let {
            FailureDetails(it.getString("category"), safe(it.getString("phase")), safe(it.getString("message")),
                it.getString("app"), it.getString("engine"), if (it.isNull("at")) null else it.getLong("at"),
                if (it.isNull("http")) null else it.getInt("http"))
        }
    }
}

class FailureDetailsStore(private val context: Context) {
    companion object { private val lock = Any() }
    private val directory = File(context.noBackupFilesDir, "failure-details")
    private fun file(id: String, generation: Int): AtomicFile {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,100}")) && generation >= 0)
        return AtomicFile(File(directory, "$id-$generation.json"))
    }
    fun capture(error: Exception, phase: String): FailureDetails {
        val engine = runCatching { context.assets.open("engine.json").bufferedReader().use { JSONObject(it.readText()).getString("version") } }.getOrDefault("Tidak terbaca")
        return FailureDetails.capture("${error.javaClass.simpleName}: ${error.message.orEmpty()}", phase,
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", engine, System.currentTimeMillis())
    }
    fun put(id: String, generation: Int, details: FailureDetails) = synchronized(lock) {
        directory.mkdirs()
        val target = file(id,generation); val stream = target.startWrite()
        try { stream.write(details.json().toString().toByteArray()); target.finishWrite(stream) }
        catch (error: Exception) { target.failWrite(stream); throw error }
        CacheBudget.prune(directory, 1024L*1024, 80, setOf(target.baseFile))
    }
    fun get(id: String, generation: Int): FailureDetails? = synchronized(lock) { runCatching {
        file(id,generation).openRead().use { input ->
            val bytes = ByteArray(16385); var count=0
            while (count < bytes.size) { val read = input.read(bytes,count,bytes.size-count); if (read < 0) break; count+=read }
            require(count < bytes.size)
            FailureDetails.fromJson(String(bytes,0,count,Charsets.UTF_8))
        }
    }.getOrNull() }
}
