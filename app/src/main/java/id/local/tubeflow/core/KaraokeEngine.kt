package id.local.tubeflow.core

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/** One bounded CPU separator. Native handles and the owned FFmpeg child close before the next job starts. */
class KaraokeEngine(private val context: Context) {
    private val guard = Any()
    private var options: OrtSession.RunOptions? = null
    private var process: Process? = null
    @Volatile private var cancelled = false
    fun cancel() = synchronized(guard) {
        cancelled = true
        runCatching { options?.setTerminate(true) }
        process?.destroyForcibly()
    }
    private fun checkCancelled() { if (cancelled) throw kotlinx.coroutines.CancellationException("Karaoke dibatalkan.") }
    suspend fun ffmpeg(directory: File, arguments: List<String>) = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive(); checkCancelled()
        Engine.initialize(context)
        val builder = ProcessBuilder(listOf(File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so").path,
            "-nostdin", "-hide_banner", "-loglevel", "error", "-y", "-threads", "2") + arguments)
            .redirectErrorStream(true).redirectOutput(File(directory, "ffmpeg.log"))
        // libc++/TLS live in the bundled Python runtime; use the same package paths as yt-dlp.
        val packages = File(context.noBackupFilesDir, "youtubedl-android/packages")
        builder.environment()["LD_LIBRARY_PATH"] = (listOf(context.applicationInfo.nativeLibraryDir) +
            listOf("python", "ffmpeg", "aria2c").map { File(packages, "$it/usr/lib").path }).joinToString(":")
        val child = synchronized(guard) { checkCancelled(); builder.start().also { process = it } }
        try {
            var seconds = 0
            while (!child.waitFor(1, TimeUnit.SECONDS)) {
                currentCoroutineContext().ensureActive(); checkCancelled()
                require(++seconds <= 180) { "Pemrosesan format melewati batas waktu." }
            }
            currentCoroutineContext().ensureActive(); checkCancelled()
            require(child.exitValue() == 0) { "Format audio tidak dapat diproses oleh FFmpeg." }
        } finally {
            child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS)
            synchronized(guard) { if (process === child) process = null }
        }
    }
    suspend fun separate(model: File, input: File, output: File, progress: suspend (Int) -> Unit) = withContext(Dispatchers.Default) {
        require(input.length() > 0 && input.length() % 8 == 0L) { "PCM audio tidak valid." }
        val total = input.length() / 8
        require(total <= 44100L * 600) { "Karaoke saat ini mendukung audio hingga 10 menit." }
        val dsp = KaraokeDsp()
        val environment = OrtEnvironment.getEnvironment().apply { setTelemetry(false) }
        OrtSession.SessionOptions().use { sessionOptions ->
            sessionOptions.setIntraOpNumThreads(2); sessionOptions.setInterOpNumThreads(1)
            sessionOptions.setCPUArenaAllocator(false); sessionOptions.setMemoryPatternOptimization(false)
            environment.createSession(model.path, sessionOptions).use { session ->
                val shape = (session.inputInfo.getValue("input").info as TensorInfo).shape
                require(shape.contentEquals(longArrayOf(-1, 4, 3072, 64))) { "Bentuk model tidak didukung." }
                OrtSession.RunOptions().use { run ->
                    synchronized(guard) { checkCancelled(); options = run }
                    try { RandomAccessFile(input, "r").use { pcm -> output.outputStream().buffered(65536).use { destination ->
                        var position = 0L
                        val buffer = ByteArray(dsp.samples * 8)
                        val out = ByteBuffer.allocate(dsp.stride * 8).order(ByteOrder.LITTLE_ENDIAN)
                        val direct = ByteBuffer.allocateDirect(4 * dsp.bins * dsp.frames * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
                        while (position < total) {
                            currentCoroutineContext().ensureActive(); checkCancelled()
                            val start = position - dsp.trim
                            val leading = (-start).coerceAtLeast(0).toInt()
                            val from = start.coerceAtLeast(0)
                            val readable = minOf(dsp.samples - leading, (total - from).coerceAtLeast(0).toInt())
                            buffer.fill(0); pcm.seek(from * 8); pcm.readFully(buffer, leading * 8, readable * 8)
                            val data = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                            val wave = Array(2) { FloatArray(dsp.samples) }
                            for (i in 0 until dsp.samples) { wave[0][i] = data.get(); wave[1][i] = data.get() }
                            val features = dsp.forward(wave)
                            direct.clear(); direct.put(features).rewind()
                            val prediction = OnnxTensor.createTensor(environment, direct, longArrayOf(1, 4, 3072, 64)).use { tensor ->
                                session.run(mapOf("input" to tensor), run).use { result ->
                                    (result[0] as OnnxTensor).floatBuffer.let { values -> FloatArray(values.remaining()).also { values.get(it) } }
                                }
                            }
                            currentCoroutineContext().ensureActive(); checkCancelled()
                            val instrumental = dsp.inverse(prediction)
                            val count = minOf(dsp.stride.toLong(), total - position).toInt()
                            out.clear()
                            for (i in 0 until count) for (channel in 0..1) {
                                val sample = instrumental[channel][i + dsp.trim] * 1.022f
                                require(sample.isFinite()) { "Model menghasilkan audio tidak valid." }
                                out.putFloat(sample.coerceIn(-1f, 1f))
                            }
                            destination.write(out.array(), 0, count * 8)
                            position += count; progress((position * 100 / total).toInt())
                        }
                    } } } finally { synchronized(guard) { options = null } }
                }
            }
        }
        require(output.length() == input.length()) { "Durasi hasil karaoke tidak sesuai sumber." }
    }
}
