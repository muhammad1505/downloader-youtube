package id.local.tubeflow.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.webkit.CookieManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Sesi YouTube/Google untuk video terbatas (usia, anggota kanal, atau privat).
 *
 * Cookie diambil dari WebView login resmi lalu disimpan terenkripsi AES-GCM dengan kunci Android
 * Keystore di `noBackupFilesDir`, sehingga tidak ikut backup. Cookie hanya ditulis ke file
 * sementara untuk `yt-dlp` pada tautan YouTube/Google dan tidak pernah dikirim ke platform lain.
 */
object YouTubeAuth {
    private const val FILE = "youtube-session.bin"
    private const val KEY_ALIAS = "tubeflow-youtube-session"
    private const val MAX_FILE_BYTES = 64 * 1024
    private const val VERSION = 1
    private val lock = Any()

    /** Kelas tanpa data class: `toString()` tidak boleh memuat nilai cookie. */
    class Session internal constructor(
        private val cookies: Map<String, String>,
        val savedAt: Long,
    ) {
        val signedIn: Boolean get() = YouTubeCookies.isSignedIn(cookies)
        internal val cookieHeader: String get() = YouTubeCookies.header(cookies)
        internal val jar: Map<String, String> get() = cookies
        override fun toString(): String = "YouTubeSession(signedIn=$signedIn, savedAt=$savedAt)"
    }

    var session by mutableStateOf<Session?>(null)
        private set
    @Volatile private var loaded = false
    // Sama seperti Bstation: cegah cookie lama tertangkap tepat setelah pengguna keluar.
    @Volatile private var captureBlockedUntil = 0L

    fun load(context: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            session = read(context.applicationContext)
            loaded = true
        }
    }

    fun isSignedIn(context: Context): Boolean {
        load(context)
        return session?.signedIn == true
    }

    /** Header `Cookie` untuk katalog/engine YouTube; null bila belum masuk. */
    fun cookieHeader(context: Context): String? {
        load(context)
        return session?.cookieHeader?.takeIf { it.isNotBlank() }
    }

    /** Ambil cookie setelah pengguna menekan login di WebView; hanya domain Google/YouTube. */
    fun saveFromWebView(context: Context): Boolean {
        if (android.os.SystemClock.elapsedRealtime() < captureBlockedUntil) return false
        val manager = CookieManager.getInstance()
        val cookies = YouTubeCookies.merge(
            manager.getCookie("https://accounts.google.com"),
            manager.getCookie("https://www.google.com"),
            manager.getCookie("https://m.youtube.com"),
            manager.getCookie("https://www.youtube.com"),
        )
        if (!YouTubeCookies.isSignedIn(cookies)) return false
        save(context, cookies)
        return true
    }

    internal fun save(context: Context, cookies: Map<String, String>) {
        require(YouTubeCookies.isSignedIn(cookies)) { "Cookie login YouTube tidak lengkap." }
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val payload = JSONObject()
                .put("v", VERSION)
                .put("savedAt", now)
                .put("cookies", JSONObject(cookies))
            writeEncrypted(context.applicationContext, payload.toString())
            session = Session(cookies, now)
            loaded = true
        }
    }

    /** File cookie sementara untuk `yt-dlp --cookies`; pemanggil/engine menghapusnya. */
    fun cookieFile(context: Context): File? {
        load(context)
        val jar = session?.jar ?: return null
        val content = YouTubeCookies.netscape(jar)
        if (content.isBlank()) return null
        val file = try {
            File.createTempFile("tubeflow-youtube-cookies-", ".txt", context.applicationContext.cacheDir)
        } catch (_: Exception) {
            return null
        }
        return try {
            file.writeText(content, StandardCharsets.UTF_8)
            runCatching { android.system.Os.chmod(file.absolutePath, 384) }
            file
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    /** Logout lokal sekaligus hapus cookie WebView agar tidak langsung masuk kembali. */
    fun clear(context: Context) {
        synchronized(lock) {
            session = null
            loaded = true
            captureBlockedUntil = android.os.SystemClock.elapsedRealtime() + 2_000
            val file = File(context.applicationContext.noBackupFilesDir, FILE)
            listOf(file, File(file.path + ".bak"), File(file.path + ".new")).forEach { it.delete() }
            runCatching {
                val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)
            }
        }
        runCatching {
            val manager = CookieManager.getInstance()
            manager.removeAllCookies { manager.flush() }
            android.webkit.WebStorage.getInstance().deleteAllData()
        }
    }

    private fun sessionFile(context: Context) = File(context.noBackupFilesDir, FILE)

    private fun writeEncrypted(context: Context, plain: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
        val encrypted = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
        val encoded = JSONObject()
            .put("v", VERSION)
            .put("iv", Base64.getEncoder().encodeToString(cipher.iv))
            .put("data", Base64.getEncoder().encodeToString(encrypted))
            .toString()
        val target = AtomicFile(sessionFile(context))
        val stream = target.startWrite()
        try {
            stream.write(encoded.toByteArray(StandardCharsets.UTF_8))
            target.finishWrite(stream)
        } catch (error: Exception) {
            target.failWrite(stream)
            throw IllegalStateException("Sesi YouTube tidak dapat disimpan dengan aman.", error)
        }
    }

    private fun read(context: Context): Session? {
        return runCatching {
            val target = AtomicFile(sessionFile(context))
            val file = target.baseFile
            if (!file.isFile || file.length() <= 0 || file.length() > MAX_FILE_BYTES) return null
            val envelope = target.openRead().use { input -> JSONObject(String(input.readBytes(), StandardCharsets.UTF_8)) }
            require(envelope.optInt("v") == VERSION)
            val iv = Base64.getDecoder().decode(envelope.getString("iv"))
            val data = Base64.getDecoder().decode(envelope.getString("data"))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(create = false) ?: return null, GCMParameterSpec(128, iv))
            val plain = JSONObject(String(cipher.doFinal(data), StandardCharsets.UTF_8))
            val stored = plain.optJSONObject("cookies") ?: return null
            val cookies = linkedMapOf<String, String>()
            stored.keys().forEach { name -> cookies[name] = stored.optString(name) }
            if (!YouTubeCookies.isSignedIn(cookies)) return null
            Session(cookies, plain.optLong("savedAt", 0))
        }.getOrNull()
    }

    private fun key(create: Boolean): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        if (!create) return null
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
