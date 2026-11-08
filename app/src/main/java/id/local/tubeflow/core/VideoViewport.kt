package id.local.tubeflow.core

import kotlin.math.max
import kotlin.math.min

/** Size of the video surface inside a clipped, centered viewport. Units can be dp or pixels. */
object VideoViewport {
    /** FILL already crops to the screen; ZOOM crops the same way with this extra magnification. */
    const val ZOOM_FACTOR = 1.25f

    /**
     * Transform NDC untuk mode potong: skala sumber sampai menutup viewport, lalu geser vertikal
     * bila perlu. Satuan matriks adalah normalized device coordinates (-1..1), bukan piksel.
     */
    data class CropTransform(
        val scaleX: Float,
        val scaleY: Float,
        val translateX: Float,
        val translateY: Float,
    ) {
        /** Matriks 4x4 column-major yang dipakai Media3 di vertex shader. */
        fun toGlMatrix(): FloatArray = floatArrayOf(
            scaleX, 0f, 0f, 0f,
            0f, scaleY, 0f, 0f,
            0f, 0f, 1f, 0f,
            translateX, translateY, 0f, 1f,
        )
    }

    fun sourceSize(width: Int, height: Int, rotation: Int, pixelRatio: Float): Triple<Int, Int, Float>? {
        if (width <= 0 || height <= 0) return null
        val ratio = pixelRatio.takeIf { it.isFinite() && it > 0 } ?: 1f
        return if (((rotation % 360) + 360) % 360 in setOf(90, 270)) Triple(height, width, 1f / ratio)
               else Triple(width, height, ratio)
    }

    fun size(width: Float, height: Float, videoWidth: Int, videoHeight: Int, aspect: VideoAspect,
             pixelRatio: Float = 1f): Pair<Float, Float> {
        if (width <= 0 || height <= 0 || videoWidth <= 0 || videoHeight <= 0) return width to height
        val ratio = pixelRatio.takeIf { it.isFinite() && it > 0 } ?: 1f
        val sourceWidth = videoWidth * ratio
        // FIT shows the whole frame; FILL/ZOOM cover the viewport and crop the overflow.
        val baseScale = if (aspect == VideoAspect.FIT) min(width / sourceWidth, height / videoHeight)
                        else max(width / sourceWidth, height / videoHeight)
        val scale = if (aspect == VideoAspect.ZOOM) baseScale * ZOOM_FACTOR else baseScale
        return sourceWidth * scale to videoHeight * scale
    }

    /**
     * Hitung crop-cover tanpa distorsi. Sumber lanskap yang terpotong vertikal (layar lebih lebar
     * dari video) dikunci ke tepi bawah supaya hardsub di dasar frame tidak ikut hilang.
     * Crop kiri-kanan tetap di tengah karena tidak ada tepi bawah yang bisa diselamatkan.
     */
    fun cropTransform(
        inputWidth: Int,
        inputHeight: Int,
        outputWidth: Int,
        outputHeight: Int,
        zoom: Float = 1f,
        anchorBottom: Boolean = true,
    ): CropTransform {
        require(inputWidth > 0 && inputHeight > 0 && outputWidth > 0 && outputHeight > 0)
        val safeZoom = zoom.takeIf { it.isFinite() && it > 0f } ?: 1f
        val inputAspect = inputWidth.toFloat() / inputHeight
        val outputAspect = outputWidth.toFloat() / outputHeight
        var scaleX = safeZoom
        var scaleY = safeZoom
        when {
            outputAspect > inputAspect -> scaleY *= outputAspect / inputAspect
            outputAspect < inputAspect -> scaleX *= inputAspect / outputAspect
        }
        // Hanya sumber lanskap yang dijamin punya subtitle di tepi bawah. Untuk video portrait,
        // crop vertikal biasanya terlalu ekstrem; biarkan tengah agar subjek utama tetap terlihat.
        val keepBottom = anchorBottom && inputAspect >= 1f && scaleY > 1f
        return CropTransform(
            scaleX = scaleX,
            scaleY = scaleY,
            translateX = 0f,
            translateY = if (keepBottom) scaleY - 1f else 0f,
        )
    }

    /**
     * True bila permukaan software FILL/ZOOM melebihi wadah secara vertikal dan tepi bawahnya
     * harus ditempel ke layar. Dipakai jalur tanpa pipeline efek GL.
     */
    fun anchorsBottom(
        width: Float,
        height: Float,
        videoWidth: Int,
        videoHeight: Int,
        aspect: VideoAspect,
        pixelRatio: Float = 1f,
    ): Boolean {
        if (aspect == VideoAspect.FIT || width <= 0f || height <= 0f) return false
        if (videoWidth <= 0 || videoHeight <= 0) return false
        val ratio = pixelRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
        if (videoWidth * ratio < videoHeight) return false
        val (_, surfaceHeight) = size(width, height, videoWidth, videoHeight, aspect, ratio)
        return surfaceHeight > height + 0.5f
    }
}
