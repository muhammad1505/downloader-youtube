package id.local.tubeflow.core

import android.content.Context
import android.os.Build
import android.util.Log
import id.local.tubeflow.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executors

/**
 * Log aplikasi yang terlihat pengguna dan tetap ada setelah proses berhenti.
 *
 * Catatan disimpan di memori untuk layar Log dan ditulis sebagai JSONL bergilir di
 * `noBackupFilesDir/logs`. Semua pesan dan detail melewati [FailureDetails.safe] lebih dulu,
 * sehingga URL, path, token, cookie, dan kredensial tidak ikut tersimpan atau dibagikan.
 * Penulisan berkas memakai satu thread latar; hanya jalur crash yang menulis langsung.
 */
object AppLog {
    private const val MAX_ENTRIES = 1200
    private const val MAX_FILE_BYTES = 512L * 1024
    private const val MAX_EXPORT_CHARS = 200_000
    private const val CURRENT_FILE = "app.jsonl"
    private val FILES = listOf("app.2.jsonl", "app.1.jsonl", CURRENT_FILE)

    private val lock = Any()
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "tubeflow-log-writer").apply { isDaemon = true }
    }
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private var directory: File? = null
    private var sequence = 0L
    private var generation = 0L
    private var crashHandlerInstalled = false

    /** Dipanggil sekali dari Application sebelum Activity atau worker mana pun berjalan. */
    fun initialize(context: Context) {
        synchronized(lock) {
            if (directory != null) return
            val directory = File(context.applicationContext.noBackupFilesDir, "logs").apply { mkdirs() }
            this.directory = directory
            // Urutan berkas harus dari yang paling lama agar tampilan tetap kronologis.
            val loaded = FILES
                .map { File(directory, it) }
                .filter { it.isFile }
                .flatMap { file ->
                    runCatching {
                        file.useLines { lines -> lines.mapNotNull { LogEntry.parse(it) }.toList() }
                    }.getOrDefault(emptyList())
                }
            // Catatan yang sempat masuk sebelum Application siap tetap dipertahankan.
            val combined = (loaded + _entries.value).takeLast(MAX_ENTRIES)
                .mapIndexed { index, entry -> entry.copy(seq = index + 1L) }
            _entries.value = combined
            sequence = combined.lastOrNull()?.seq ?: 0L
        }
        info(LogCategory.APP, "TubeFlow ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) dibuka.")
    }

    /**
     * Log yang tidak tertangani tetap ditulis sebelum proses berhenti. Handler lama tetap dipanggil
     * supaya perilaku crash Android (termasuk laporan sistem) tidak berubah.
     */
    fun installCrashHandler() {
        synchronized(lock) {
            if (crashHandlerInstalled) return
            crashHandlerInstalled = true
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                write(
                    LogLevel.ERROR,
                    LogCategory.APP,
                    "Aplikasi berhenti karena error tak tertangani di thread ${thread.name}.",
                    error.stackTraceToString(),
                    sync = true,
                )
            }
            if (previous != null) previous.uncaughtException(thread, error)
            else android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    fun debug(category: LogCategory, message: String) =
        write(LogLevel.DEBUG, category, message)

    fun info(category: LogCategory, message: String) =
        write(LogLevel.INFO, category, message)

    fun warn(
        category: LogCategory,
        message: String,
        throwable: Throwable? = null,
        detail: String? = null,
    ) = write(LogLevel.WARN, category, message, detail ?: throwable?.stackTraceToString())

    fun error(
        category: LogCategory,
        message: String,
        throwable: Throwable? = null,
        detail: String? = null,
    ) = write(LogLevel.ERROR, category, message, detail ?: throwable?.stackTraceToString())

    /** Menghapus memori dan semua berkas; satu catatan baru menandai bahwa log dibersihkan. */
    fun clear() {
        synchronized(lock) {
            generation++
            directory?.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".jsonl") }
                ?.forEach { it.delete() }
            _entries.value = emptyList()
            sequence = 0L
        }
        info(LogCategory.APP, "Log aplikasi dibersihkan dari layar Log.")
    }

    /**
     * Teks yang siap disalin/dibagikan; isinya sudah disanitasi saat dicatat. Bila terlalu besar
     * untuk clipboard/Intent, hanya catatan terbaru yang dipertahankan agar aksi tetap berhasil.
     */
    fun exportText(): String {
        val snapshot = _entries.value
        val header = buildString {
            appendLine("TubeFlow ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            append("Log ${snapshot.size} catatan · diekspor ${LogFormat.dateTime(System.currentTimeMillis())}")
        } + "\n---\n"
        val body = LogFormat.export(snapshot, "")
        if (header.length + body.length <= MAX_EXPORT_CHARS) return header + body
        val note = "… Log lama dipotong agar tetap dapat disalin atau dibagikan.\n"
        val room = (MAX_EXPORT_CHARS - header.length - note.length).coerceAtLeast(0)
        val tail = body.takeLast(room)
        return header + note + tail.substringAfter('\n', tail)
    }

    private fun write(
        level: LogLevel,
        category: LogCategory,
        message: String,
        detail: String? = null,
        sync: Boolean = false,
    ) {
        val safeMessage = FailureDetails.safe(message).trim().ifBlank { "(tanpa pesan)" }.take(1000)
        val safeDetail = detail
            ?.let { FailureDetails.safe(it).trim() }
            ?.takeIf { it.isNotBlank() }
        val entry = synchronized(lock) {
            val entry = LogEntry(++sequence, System.currentTimeMillis(), level, category, safeMessage, safeDetail)
            _entries.value = (_entries.value + entry).takeLast(MAX_ENTRIES)
            directory?.let { scheduleLocked(it, entry, generation, sync) }
            entry
        }
        mirror(entry)
    }

    private fun scheduleLocked(dir: File, entry: LogEntry, currentGeneration: Long, sync: Boolean) {
        if (sync) {
            appendLocked(dir, entry)
            return
        }
        runCatching {
            writer.execute {
                synchronized(lock) {
                    // clear() menaikkan generasi; catatan lama yang masih antre tidak boleh
                    // membuat berkas baru setelah pengguna membersihkan log.
                    if (currentGeneration == generation) appendLocked(dir, entry)
                }
            }
        }
    }

    private fun appendLocked(dir: File, entry: LogEntry) {
        runCatching {
            dir.mkdirs()
            val line = entry.json().toString() + "\n"
            val current = File(dir, CURRENT_FILE)
            if (current.isFile &&
                current.length() + line.toByteArray(Charsets.UTF_8).size > MAX_FILE_BYTES
            ) {
                rotateLocked(dir)
            }
            current.appendText(line, Charsets.UTF_8)
        }
    }

    private fun rotateLocked(dir: File) {
        File(dir, "app.2.jsonl").delete()
        File(dir, "app.1.jsonl").renameTo(File(dir, "app.2.jsonl"))
        File(dir, CURRENT_FILE).renameTo(File(dir, "app.1.jsonl"))
    }

    private fun mirror(entry: LogEntry) {
        val text = entry.detail?.takeIf { it.isNotBlank() }
            ?.let { "${entry.message}\n$it" }
            ?: entry.message
        val tag = "TubeFlow/${entry.category.name.take(14)}"
        runCatching {
            when (entry.level) {
                LogLevel.DEBUG -> Log.d(tag, text)
                LogLevel.INFO -> Log.i(tag, text)
                LogLevel.WARN -> Log.w(tag, text)
                LogLevel.ERROR -> Log.e(tag, text)
            }
        }
    }
}
