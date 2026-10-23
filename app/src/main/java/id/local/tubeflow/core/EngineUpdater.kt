package id.local.tubeflow.core

import android.content.Context
import id.local.tubeflow.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile

enum class EngineSource { BUNDLED, UPDATED }

data class InstalledEngine(
    val version: String,
    val sha256: String,
    val source: EngineSource,
    val file: File? = null,
)

data class EngineRelease(
    val version: String,
    val downloadUrl: String,
    val sumsUrl: String,
    val sha256: String,
    val sumsSha256: String,
    val size: Long,
)

sealed interface EngineUpdateState {
    data object Idle : EngineUpdateState
    data object Checking : EngineUpdateState
    data class UpToDate(val version: String) : EngineUpdateState
    data class Available(val release: EngineRelease) : EngineUpdateState
    data class Downloading(val release: EngineRelease, val received: Long) : EngineUpdateState
    data class Verifying(val release: EngineRelease) : EngineUpdateState
    data class Installing(val release: EngineRelease) : EngineUpdateState
    data class Updated(val version: String) : EngineUpdateState
    data class RolledBack(val version: String) : EngineUpdateState
    data class Failed(val message: String) : EngineUpdateState
}

class EngineUpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)

object EngineUpdater {
    private const val API_URL = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"
    private const val MAX_API_BYTES = 1024L * 1024
    private const val MAX_SUMS_BYTES = 256L * 1024
    private const val MAX_ENGINE_BYTES = 20L * 1024 * 1024
    private const val MAX_REDIRECTS = 5
    private val VERSION = Regex("""\d{4}\.\d{2}\.\d{2}""")
    private val SHA256 = Regex("""[a-f0-9]{64}""")
    private val ALLOWED_HOSTS = setOf(
        "api.github.com",
        "github.com",
        "github-releases.githubusercontent.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private val _state = MutableStateFlow<EngineUpdateState>(EngineUpdateState.Idle)
    val state: StateFlow<EngineUpdateState> = _state.asStateFlow()
    @Volatile private var job: Job? = null
    private var installedKey: String? = null
    private var installedCache: InstalledEngine? = null

    fun check(context: Context) = start(context) { app ->
        _state.value = EngineUpdateState.Checking
        val installed = installed(app)
        val release = fetchLatestRelease()
        _state.value = if (compareVersions(release.version, installed.version) > 0) {
            EngineUpdateState.Available(release)
        } else {
            EngineUpdateState.UpToDate(installed.version)
        }
    }

    fun install(context: Context, release: EngineRelease, blockedReason: String? = null) {
        if (blockedReason != null) {
            _state.value = EngineUpdateState.Failed(blockedReason)
            return
        }
        if (Engine.isBusy()) {
            _state.value = EngineUpdateState.Failed("Mesin media sedang dipakai. Selesaikan unduhan atau pemeriksaan aktif lalu coba lagi.")
            return
        }
        start(context) { app ->
            _state.value = EngineUpdateState.Downloading(release, 0)
            val directory = updateDirectory(app)
            directory.mkdirs()
            val temporary = File(directory, "yt-dlp.download")
            try {
                downloadTo(URL(release.downloadUrl), temporary, release.size) { received ->
                    _state.value = EngineUpdateState.Downloading(release, received)
                }
                _state.value = EngineUpdateState.Verifying(release)
                val actual = sha256(temporary)
                if (actual != release.sha256) throw EngineUpdateException("Checksum engine tidak cocok. Engine lama tetap dipakai.")
                if (readZipappVersion(temporary) != release.version) {
                    throw EngineUpdateException("Versi di dalam paket engine tidak cocok dengan rilis. Engine lama tetap dipakai.")
                }
                if (Engine.isBusy()) throw EngineUpdateException("Unduhan mulai berjalan saat pembaruan disiapkan. Coba lagi setelah selesai.")
                val target = File(directory, "yt-dlp-$actual.bin")
                if (!target.isFile || sha256(target) != actual) {
                    atomicReplace(temporary, target)
                }
                writeManifest(app, release, target.name)
                _state.value = EngineUpdateState.Installing(release)
                try {
                    Engine.activateStoredEngine(app)
                } catch (error: Throwable) {
                    runCatching { removeOverride(app) }
                    throw EngineUpdateException("Engine baru gagal diaktifkan. Engine lama tetap dipakai.", error)
                }
                invalidateInstalledCache()
                runCatching { removeOldBinaries(directory, target.name) }
                _state.value = EngineUpdateState.Updated(release.version)
            } finally {
                temporary.delete()
            }
        }
    }

    fun rollback(context: Context, blockedReason: String? = null) {
        if (blockedReason != null) {
            _state.value = EngineUpdateState.Failed(blockedReason)
            return
        }
        if (Engine.isBusy()) {
            _state.value = EngineUpdateState.Failed("Mesin media sedang dipakai. Selesaikan unduhan atau pemeriksaan aktif lalu coba lagi.")
            return
        }
        start(context) { app ->
            removeOverride(app)
            invalidateInstalledCache()
            Engine.activateStoredEngine(app)
            _state.value = EngineUpdateState.RolledBack(installed(app).version)
        }
    }

    fun cancel() {
        job?.cancel()
    }

    @Synchronized
    fun installed(context: Context): InstalledEngine {
        val bundled = bundled(context)
        val manifestFile = manifestFile(context)
        val key = "${bundled.version}:${manifestFile.lastModified()}:${manifestFile.length()}"
        if (key == installedKey) installedCache?.let { return it }
        val override = readOverride(context) ?: return bundled.also {
            installedKey = key
            installedCache = it
        }
        if (compareVersions(override.version, bundled.version) <= 0) return bundled.also {
            installedKey = key
            installedCache = it
        }
        return override.also {
            installedKey = key
            installedCache = it
        }
    }

    fun installedVersion(context: Context): String = installed(context).version

    @Synchronized
    fun invalidateInstalledCache() {
        installedKey = null
        installedCache = null
    }

    internal fun compareVersions(left: String, right: String): Int {
        val a = left.split('.')
        val b = right.split('.')
        if (a.size != 3 || b.size != 3) return left.compareTo(right)
        for (index in 0..2) {
            val difference = (a[index].toIntOrNull() ?: 0).compareTo(b[index].toIntOrNull() ?: 0)
            if (difference != 0) return difference
        }
        return 0
    }

    internal fun parseRelease(apiJson: String): EngineRelease {
        val json = try {
            JSONObject(apiJson)
        } catch (error: Exception) {
            throw EngineUpdateException("Respons rilis GitHub tidak valid.", error)
        }
        val version = json.optString("tag_name")
        if (!VERSION.matches(version)) throw EngineUpdateException("Rilis GitHub bukan versi stabil yang didukung.")
        val assets = json.optJSONArray("assets") ?: JSONArray()
        var engine: JSONObject? = null
        var sums: JSONObject? = null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            when (asset.optString("name")) {
                "yt-dlp" -> engine = asset
                "SHA2-256SUMS" -> sums = asset
            }
        }
        val engineAsset = engine ?: throw EngineUpdateException("Rilis tidak menyediakan paket yt-dlp.")
        val sumsAsset = sums ?: throw EngineUpdateException("Rilis tidak menyediakan daftar checksum.")
        val sha = engineAsset.optString("digest").removePrefix("sha256:").lowercase()
        val sumsSha = sumsAsset.optString("digest").removePrefix("sha256:").lowercase()
        if (!SHA256.matches(sha) || !SHA256.matches(sumsSha)) {
            throw EngineUpdateException("Metadata checksum rilis tidak lengkap.")
        }
        val size = engineAsset.optLong("size", 0)
        if (size <= 0 || size > MAX_ENGINE_BYTES) throw EngineUpdateException("Ukuran paket engine tidak wajar.")
        val downloadUrl = engineAsset.optString("browser_download_url")
        val sumsUrl = sumsAsset.optString("browser_download_url")
        validateReleaseUrl(downloadUrl)
        validateReleaseUrl(sumsUrl)
        return EngineRelease(version, downloadUrl, sumsUrl, sha, sumsSha, size)
    }

    internal fun parseSums(text: String): String? {
        for (line in text.lineSequence()) {
            val parts = line.trim().split(Regex("""\s+"""), limit = 2)
            if (parts.size == 2 && parts[1].removePrefix("*") == "yt-dlp") {
                val digest = parts[0].lowercase()
                return digest.takeIf { SHA256.matches(it) }
            }
        }
        return null
    }

    internal fun readZipappVersion(file: File): String {
        try {
            ZipFile(file).use { zip ->
                var versionEntry: java.util.zip.ZipEntry? = null
                var hasMain = false
                var expanded = 0L
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    if (name.startsWith("/") || name.split('/').any { it == ".." }) {
                        throw EngineUpdateException("Paket engine berisi jalur yang tidak aman.")
                    }
                    if (entry.size > 0) expanded += entry.size
                    if (expanded > 100L * 1024 * 1024) throw EngineUpdateException("Isi paket engine terlalu besar.")
                    if (name == "yt_dlp/version.py") versionEntry = entry
                    if (name == "__main__.py") hasMain = true
                }
                if (!hasMain) throw EngineUpdateException("Paket engine tidak dapat dijalankan.")
                val entry = versionEntry ?: throw EngineUpdateException("Paket engine tidak memuat versi.")
                val text = StringBuilder()
                zip.getInputStream(entry).bufferedReader().use { reader ->
                    val buffer = CharArray(4096)
                    while (text.length <= 64 * 1024) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        text.append(buffer, 0, count)
                    }
                }
                return Regex("""__version__\s*=\s*['"]([^'"]+)['"]""")
                    .find(text)?.groupValues?.get(1)
                    ?: throw EngineUpdateException("Versi paket engine tidak terbaca.")
            }
        } catch (error: EngineUpdateException) {
            throw error
        } catch (error: Exception) {
            throw EngineUpdateException("Paket engine rusak atau bukan zipapp yt-dlp.", error)
        }
    }

    internal fun validateReleaseUrl(raw: String) {
        val uri = try {
            URI(raw)
        } catch (error: Exception) {
            throw EngineUpdateException("Alamat rilis engine tidak valid.", error)
        }
        val host = uri.host?.lowercase()
        if (uri.scheme != "https" || uri.userInfo != null || uri.port !in setOf(-1, 443) ||
            host == null || host !in ALLOWED_HOSTS || uri.path.isNullOrBlank()
        ) {
            throw EngineUpdateException("Rilis engine harus berasal dari host resmi GitHub.")
        }
    }

    internal fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    internal fun atomicReplace(source: File, target: File) {
        target.parentFile?.mkdirs()
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun start(context: Context, operation: suspend (Context) -> Unit) {
        if (!running.compareAndSet(false, true)) return
        val application = context.applicationContext
        job = scope.launch {
            try {
                operation(application)
            } catch (cancelled: CancellationException) {
                _state.value = EngineUpdateState.Idle
                throw cancelled
            } catch (error: Throwable) {
                _state.value = EngineUpdateState.Failed(userMessage(error))
            } finally {
                running.set(false)
            }
        }
    }

    private fun fetchLatestRelease(): EngineRelease {
        val text = readAll(URL(API_URL), MAX_API_BYTES, "application/vnd.github+json")
            .toString(Charsets.UTF_8)
        val release = parseRelease(text)
        val sums = try {
            readAll(URL(release.sumsUrl), MAX_SUMS_BYTES, "text/plain")
        } catch (error: IOException) {
            throw EngineUpdateException("Daftar checksum resmi tidak dapat diunduh.", error)
        }
        if (sha256(sums) != release.sumsSha256) {
            throw EngineUpdateException("Daftar checksum rilis berubah atau tidak cocok.")
        }
        if (parseSums(sums.toString(Charsets.UTF_8)) != release.sha256) {
            throw EngineUpdateException("Checksum rilis tidak konsisten. Pembaruan dibatalkan.")
        }
        return release
    }

    private fun readAll(url: URL, maxBytes: Long, accept: String): ByteArray {
        val connection = openFollowingRedirects(url, accept)
        try {
            val length = connection.contentLengthLong
            if (length > maxBytes) throw EngineUpdateException("Respons rilis terlalu besar.")
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0L
            connection.inputStream.use { input ->
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > maxBytes) throw EngineUpdateException("Respons rilis terlalu besar.")
                    output.write(buffer, 0, count)
                }
            }
            return output.toByteArray()
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadTo(url: URL, target: File, expectedSize: Long, progress: (Long) -> Unit) {
        val connection = openFollowingRedirects(url, "application/octet-stream")
        var lastUpdate = 0L
        try {
            val length = connection.contentLengthLong
            if (length > MAX_ENGINE_BYTES || (length > 0 && length != expectedSize)) {
                throw EngineUpdateException("Ukuran unduhan engine tidak cocok dengan metadata rilis.")
            }
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(65536)
                var total = 0L
                connection.inputStream.use { input ->
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > expectedSize || total > MAX_ENGINE_BYTES) {
                            throw EngineUpdateException("Unduhan engine melebihi ukuran yang diizinkan.")
                        }
                        output.write(buffer, 0, count)
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= 150 || total == expectedSize) {
                            lastUpdate = now
                            progress(total)
                        }
                    }
                }
                output.fd.sync()
                if (total != expectedSize) throw EngineUpdateException("Unduhan engine terpotong. Coba lagi.")
            }
        } catch (error: Throwable) {
            target.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun openFollowingRedirects(url: URL, accept: String): HttpURLConnection {
        var current = url
        repeat(MAX_REDIRECTS + 1) {
            validateReleaseUrl(current.toString())
            val connection = current.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "TubeFlow/${BuildConfig.VERSION_NAME}")
            connection.setRequestProperty("Accept", accept)
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (current.host == "api.github.com") {
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
            val code = try {
                connection.responseCode
            } catch (error: IOException) {
                connection.disconnect()
                throw EngineUpdateException("Koneksi ke GitHub gagal. Periksa jaringan lalu coba lagi.", error)
            }
            when (code) {
                in 200..299 -> return connection
                301, 302, 303, 307, 308 -> {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (location.isNullOrBlank()) throw EngineUpdateException("Pengalihan unduhan engine tidak valid.")
                    current = try {
                        current.toURI().resolve(location).toURL()
                    } catch (error: Exception) {
                        throw EngineUpdateException("Pengalihan unduhan engine tidak valid.", error)
                    }
                }
                403, 429 -> {
                    connection.disconnect()
                    throw EngineUpdateException("GitHub membatasi pemeriksaan saat ini. Coba lagi nanti.")
                }
                else -> {
                    connection.disconnect()
                    throw EngineUpdateException("GitHub mengembalikan HTTP $code saat memeriksa pembaruan.")
                }
            }
        }
        throw EngineUpdateException("Terlalu banyak pengalihan saat mengunduh engine.")
    }

    private fun updateDirectory(context: Context): File =
        File(context.noBackupFilesDir, "engine-update")

    private fun manifestFile(context: Context): File = File(updateDirectory(context), "manifest.json")

    private fun bundled(context: Context): InstalledEngine {
        val manifest = try {
            JSONObject(context.assets.open("engine.json").bufferedReader().use { it.readText() })
        } catch (error: Exception) {
            throw EngineUpdateException("Manifest engine bawaan tidak terbaca.", error)
        }
        val version = manifest.optString("version")
        val sha = manifest.optString("sha256").lowercase()
        if (!VERSION.matches(version) || !SHA256.matches(sha)) {
            throw EngineUpdateException("Manifest engine bawaan tidak valid.")
        }
        return InstalledEngine(version, sha, EngineSource.BUNDLED)
    }

    private fun readOverride(context: Context): InstalledEngine? {
        val directory = updateDirectory(context)
        val manifest = runCatching {
            val json = JSONObject(File(directory, "manifest.json").readText())
            val version = json.optString("version")
            val sha = json.optString("sha256").lowercase()
            val name = json.optString("file")
            if (!VERSION.matches(version) || !SHA256.matches(sha) ||
                !Regex("""yt-dlp-[a-f0-9]{64}\.bin""").matches(name)
            ) return null
            val binary = File(directory, name)
            if (!binary.isFile || sha256(binary) != sha) return null
            InstalledEngine(version, sha, EngineSource.UPDATED, binary)
        }.getOrNull()
        return manifest
    }

    private fun writeManifest(context: Context, release: EngineRelease, fileName: String) {
        val directory = updateDirectory(context)
        directory.mkdirs()
        val json = JSONObject()
            .put("format", 1)
            .put("engine", "yt-dlp")
            .put("version", release.version)
            .put("sha256", release.sha256)
            .put("file", fileName)
            .put("source", release.downloadUrl)
            .put("installedAt", System.currentTimeMillis())
        val temporary = File(directory, "manifest.json.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(json.toString().toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        atomicReplace(temporary, File(directory, "manifest.json"))
        invalidateInstalledCache()
    }

    private fun removeOverride(context: Context) {
        val directory = updateDirectory(context)
        Files.deleteIfExists(File(directory, "manifest.json").toPath())
        directory.listFiles()?.forEach { file ->
            if (file.isFile && (file.name.startsWith("yt-dlp-") || file.name == "yt-dlp.download" || file.name.endsWith(".tmp"))) {
                Files.deleteIfExists(file.toPath())
            }
        }
        invalidateInstalledCache()
    }

    private fun removeOldBinaries(directory: File, keep: String) {
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.name != keep && (file.name.startsWith("yt-dlp-") || file.name == "yt-dlp.download")) {
                Files.deleteIfExists(file.toPath())
            }
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String {
        val chars = CharArray(size * 2)
        for (index in indices) {
            val value = this[index].toInt() and 0xff
            chars[index * 2] = "0123456789abcdef"[value ushr 4]
            chars[index * 2 + 1] = "0123456789abcdef"[value and 0x0f]
        }
        return String(chars)
    }

    private fun userMessage(error: Throwable): String = when (error) {
        is EngineUpdateException -> error.message ?: "Pembaruan engine gagal."
        is IOException -> "Koneksi atau penyimpanan bermasalah. Engine lama tetap dipakai."
        else -> "Pembaruan engine gagal. Engine lama tetap dipakai."
    }
}
