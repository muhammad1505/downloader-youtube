package id.local.tubeflow.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import androidx.core.graphics.createBitmap
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import java.util.concurrent.Executors

/**
 * Menyimpan satu frame video ke Pictures/TubeFlow. Ambil dari surface video (bukan seluruh layar)
 * supaya kontrol Compose, subtitle, dan gesture overlay tidak ikut tertangkap. PixelCopy hanya
 * tersedia untuk SurfaceView; TextureView memakai bitmap bawaannya.
 */
object VideoScreenshot {
    private val io = Executors.newSingleThreadExecutor { task ->
        Thread(task, "video-screenshot").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())

    @androidx.annotation.OptIn(UnstableApi::class)
    fun capture(
        context: Context,
        playerView: PlayerView,
        title: String,
        onResult: (Result<Uri>) -> Unit,
    ) {
        val view = playerView.videoSurfaceView
        when (view) {
            is TextureView -> {
                val bitmap = view.bitmap
                if (bitmap == null) fail(onResult, "Frame video belum siap. Coba lagi setelah video berjalan.")
                else save(context.applicationContext, bitmap, title, onResult)
            }
            is SurfaceView -> copySurface(context.applicationContext, view, title, onResult)
            else -> fail(onResult, "Permukaan video tidak mendukung tangkapan layar.")
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun copySurface(
        context: Context,
        surfaceView: SurfaceView,
        title: String,
        onResult: (Result<Uri>) -> Unit,
    ) {
        val width = surfaceView.width
        val height = surfaceView.height
        if (width <= 0 || height <= 0 || !surfaceView.holder.surface.isValid) {
            fail(onResult, "Frame video belum siap. Coba lagi setelah video berjalan.")
            return
        }
        val bitmap = runCatching { createBitmap(width, height, Bitmap.Config.ARGB_8888) }
            .getOrElse {
                fail(onResult, "Tidak dapat menyiapkan tangkapan layar.")
                return
            }
        runCatching {
            PixelCopy.request(
                surfaceView,
                bitmap,
                { result ->
                    if (result == PixelCopy.SUCCESS) save(context, bitmap, title, onResult)
                    else {
                        bitmap.recycle()
                        fail(onResult, "Frame video belum siap. Coba lagi setelah video berjalan.")
                    }
                },
                main,
            )
        }.onFailure {
            bitmap.recycle()
            fail(onResult, "Tangkapan layar gagal: ${it.message ?: "PixelCopy ditolak"}.")
        }
    }

    private fun save(
        context: Context,
        bitmap: Bitmap,
        title: String,
        onResult: (Result<Uri>) -> Unit,
    ) {
        io.execute {
            val result = runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, VideoPlayback.screenshotFileName(title))
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/TubeFlow")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("MediaStore tidak membuat entri")
                try {
                    val output = resolver.openOutputStream(uri)
                        ?: throw IllegalStateException("MediaStore tidak membuka berkas")
                    output.use { stream ->
                        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                            throw IllegalStateException("PNG tidak dapat ditulis")
                        }
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    uri
                } catch (failure: Exception) {
                    runCatching { resolver.delete(uri, null, null) }
                    throw failure
                }
            }
            bitmap.recycle()
            main.post { onResult(result) }
        }
    }

    private fun fail(onResult: (Result<Uri>) -> Unit, message: String) {
        main.post { onResult(Result.failure(IllegalStateException(message))) }
    }
}
