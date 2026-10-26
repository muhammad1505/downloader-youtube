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

    fun size(width: Float, height: Float, videoWidth: Int, videoHeight: Int, aspect: VideoAspect,
             pixelRatio: Float = 1f): Pair<Float, Float> {
        if (width <= 0 || height <= 0 || videoWidth <= 0 || videoHeight <= 0 || aspect == VideoAspect.FILL) return width to height
        val ratio = pixelRatio.takeIf { it.isFinite() && it > 0 } ?: 1f
        val sourceWidth = videoWidth * ratio
        val scale = if (aspect == VideoAspect.ZOOM) max(width / sourceWidth, height / videoHeight)
                    else min(width / sourceWidth, height / videoHeight)
        return sourceWidth * scale to videoHeight * scale
    }
}
