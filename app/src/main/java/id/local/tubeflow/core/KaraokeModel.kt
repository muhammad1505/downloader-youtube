package id.local.tubeflow.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Optional download; trained weights are never bundled with the APK or fetched during ordinary playback. */
object KaraokeModel {
    const val BYTES = 66759214L
    const val SOURCE_SHA = "317554b07fe1ea5279a77f2b1520a41ea4b93432560c4ffd08792c30fddf9adc"
    const val PREPARED_BYTES = 66759212L
    const val SHA = "bf09aee9b867a102c786dd8205d7f5176938b78329d906773764e05918d21c4f"
    const val URL = "https://github.com/TRvlvr/model_repo/releases/download/all_public_uvr_models/UVR-MDX-NET-Inst_HQ_3.onnx"
    fun directory(context: Context) = File(context.noBackupFilesDir, "karaoke-models").apply { mkdirs() }
    fun digest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) } }
        return hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    /** Shorter time declarations and their protobuf lengths; no weight/tensor bytes are changed. */
    fun prepare(file: File) {
        require(file.length() == BYTES && digest(file) == SOURCE_SHA) { "Checksum model karaoke tidak sesuai." }
        data class Patch(val offset: Long, val before: ByteArray, val after: ByteArray)
        val patches = listOf(
            Patch(17L, byteArrayOf(149.toByte()), byteArrayOf(147.toByte())),
            Patch(66759120L, byteArrayOf(43.toByte()), byteArrayOf(42.toByte())),
            Patch(66759129L, byteArrayOf(34.toByte()), byteArrayOf(33.toByte())),
            Patch(66759131L, byteArrayOf(32.toByte()), byteArrayOf(31.toByte())),
            Patch(66759135L, byteArrayOf(28.toByte()), byteArrayOf(27.toByte())),
            Patch(66759160L, byteArrayOf(3.toByte()), byteArrayOf(2.toByte())),
            Patch(66759162L, byteArrayOf(128.toByte(),2.toByte()), byteArrayOf(64.toByte())),
            Patch(66759165L, byteArrayOf(44.toByte()), byteArrayOf(43.toByte())),
            Patch(66759175L, byteArrayOf(34.toByte()), byteArrayOf(33.toByte())),
            Patch(66759177L, byteArrayOf(32.toByte()), byteArrayOf(31.toByte())),
            Patch(66759181L, byteArrayOf(28.toByte()), byteArrayOf(27.toByte())),
            Patch(66759206L, byteArrayOf(3.toByte()), byteArrayOf(2.toByte())),
            Patch(66759208L, byteArrayOf(128.toByte(),2.toByte()), byteArrayOf(64.toByte()))
        )
        val adapted = File(file.parentFile, "model-adapted.part")
        try {
            file.inputStream().use { input -> java.io.FileOutputStream(adapted).use { output ->
                val buffer = ByteArray(65536)
                var position = 0L
                for (patch in patches) {
                    while (position < patch.offset) {
                        val n = input.read(buffer, 0, minOf(buffer.size.toLong(), patch.offset - position).toInt())
                        require(n > 0); output.write(buffer, 0, n); position += n
                    }
                    for (expected in patch.before) require(input.read() == (expected.toInt() and 255))
                    position += patch.before.size
                    output.write(patch.after)
                }
                input.copyTo(output); output.fd.sync()
            } }
            require(adapted.length() == PREPARED_BYTES && digest(adapted) == SHA) { "Verifikasi model karaoke gagal." }
            require(adapted.renameTo(file)) { "Model karaoke tidak dapat disiapkan." }
        } finally { adapted.delete() }
    }
    suspend fun obtain(context: Context, progress: suspend (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val target = File(directory(context), "uvr-inst-hq3-t64.onnx")
        if (target.isFile && target.length() == PREPARED_BYTES && digest(target) == SHA) return@withContext target
        val partial = File(target.parentFile, "model.part")
        val connection = URL(URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20000; connection.readTimeout = 20000
            connection.setRequestProperty("User-Agent", "TubeFlow-Karaoke")
            require(connection.responseCode == 200 && connection.url.protocol == "https") { "Model belum dapat diunduh. Periksa jaringan lalu coba lagi." }
            var count = 0L; var last = -1
            connection.inputStream.use { input -> java.io.FileOutputStream(partial).use { output ->
                val buffer = ByteArray(65536)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer); if (n < 0) break
                    count += n; require(count <= BYTES) { "Ukuran model tidak valid." }; output.write(buffer, 0, n)
                    val percent = (count * 100 / BYTES).toInt()
                    if (percent != last) { last = percent; progress(percent) }
                }
                output.fd.sync()
            } }
            prepare(partial)
            android.system.Os.rename(partial.path, target.path)
            target
        } finally { connection.disconnect(); partial.delete() }
    }
}
