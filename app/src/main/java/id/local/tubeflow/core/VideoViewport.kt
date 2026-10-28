package id.local.tubeflow.core

import kotlin.math.max
import kotlin.math.min

/** Size of the video surface inside a clipped, centered viewport. Units can be dp or pixels. */
object VideoViewport {
    /** FILL already crops to the screen; ZOOM crops the same way with this extra magnification. */
    const val ZOOM_FACTOR = 1.25f

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
}
