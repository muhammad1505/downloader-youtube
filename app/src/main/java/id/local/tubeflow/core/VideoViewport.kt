package id.local.tubeflow.core

import kotlin.math.max
import kotlin.math.min

/** Size of the video surface inside a clipped, centered viewport. Units can be dp or pixels. */
object VideoViewport {
    fun sourceSize(width: Int, height: Int, rotation: Int, pixelRatio: Float): Triple<Int, Int, Float>? {
        if (width <= 0 || height <= 0) return null
        val ratio = pixelRatio.takeIf { it.isFinite() && it > 0 } ?: 1f
        return if (((rotation % 360) + 360) % 360 in setOf(90, 270)) Triple(height, width, 1f / ratio)
               else Triple(width, height, ratio)
    }

    /**
     * Ukuran tampilan yang dituju untuk setiap mode. [density] hanya dipakai mode 100% agar satu
     * piksel sumber dipetakan ke satu piksel layar; satuan keluaran mengikuti satuan
     * [width]/[height] (dp di UI). Semua mode dipusatkan, jadi Potong membuang tepi atas dan
     * bawah secara merata.
     */
    fun size(
        width: Float,
        height: Float,
        videoWidth: Int,
        videoHeight: Int,
        aspect: VideoAspect,
        pixelRatio: Float = 1f,
        density: Float = 1f,
    ): Pair<Float, Float> {
        if (width <= 0 || height <= 0 || videoWidth <= 0 || videoHeight <= 0) return width to height
        val ratio = pixelRatio.takeIf { it.isFinite() && it > 0 } ?: 1f
        val sourceWidth = videoWidth * ratio
        val sourceHeight = videoHeight.toFloat()
        return when (aspect) {
            // Rentangkan memenuhi seluruh wadah; rasio video berubah, tetapi tidak ada isi hilang.
            VideoAspect.STRETCH -> width to height
            VideoAspect.PERCENT_100 -> {
                val scale = 1f / (density.takeIf { it.isFinite() && it > 0f } ?: 1f)
                sourceWidth * scale to sourceHeight * scale
            }
            // Pas ke layar menampilkan seluruh frame; Potong menutup wadah lalu dipangkas
            // clipToBounds secara simetris.
            VideoAspect.FIT, VideoAspect.CROP -> {
                val baseScale = if (aspect == VideoAspect.FIT) {
                    min(width / sourceWidth, height / sourceHeight)
                } else {
                    max(width / sourceWidth, height / sourceHeight)
                }
                sourceWidth * baseScale to sourceHeight * baseScale
            }
        }
    }

    /**
     * Skala layer untuk mode tampilan. `PlayerView` selalu full-screen dan video di dalamnya
     * memakai ukuran Pas ke layar; mode lain dicapai dengan menskalakan layer itu, bukan dengan
     * mengubah ukuran TextureView. Menghindari bug transform TextureView basi di Android 10.
     */
    fun modeScale(
        width: Float,
        height: Float,
        videoWidth: Int,
        videoHeight: Int,
        aspect: VideoAspect,
        pixelRatio: Float = 1f,
        density: Float = 1f,
    ): Pair<Float, Float> {
        if (width <= 0f || height <= 0f || videoWidth <= 0 || videoHeight <= 0) return 1f to 1f
        val (fitWidth, fitHeight) = size(width, height, videoWidth, videoHeight, VideoAspect.FIT, pixelRatio)
        if (fitWidth <= 0f || fitHeight <= 0f) return 1f to 1f
        val (targetWidth, targetHeight) = size(
            width, height, videoWidth, videoHeight, aspect, pixelRatio, density,
        )
        return targetWidth / fitWidth to targetHeight / fitHeight
    }
}
