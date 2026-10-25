package id.local.tubeflow.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Bilah progres bersama untuk pemutar video dan audio.
 *
 * Material Slider standar menyisakan track tebal dan titik yang menempel ke ujung bar, sehingga
 * terlihat berat di bar pemutar yang tipis. Scrubber ini menggambar sendiri: track tipis membulat,
 * bagian termuat (buffer) lebih terang, bagian terputar memakai warna aksen, dan titik bulat yang
 * membesar saat digeser. Titik dan track dipisah celah kecil supaya keduanya tidak menyatu, dan
 * area sentuh tetap tinggi tanpa membuat bar terlihat gemuk.
 *
 * Semua nilai dalam milidetik; [onScrub] dipanggil terus selama geser dan [onScrubFinished] sekali
 * saat jari dilepas atau track diketuk.
 */
@Composable
fun Scrubber(
    value: Float,
    duration: Float,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    contentColor: Color,
    modifier: Modifier = Modifier,
    buffered: Float = 0f,
    enabled: Boolean = true,
    activeColor: Color = contentColor,
    trackColor: Color = contentColor.copy(alpha = 0.26f),
    bufferedColor: Color = contentColor.copy(alpha = 0.42f),
    trackHeight: Dp = 3.dp,
    thumbRadius: Dp = 5.dp,
    touchHeight: Dp = 36.dp,
    contentDescription: String = "Posisi pemutaran",
    stateDescription: String? = null,
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { thumbRadius.toPx() }
    val gapPx = with(density) { 2.dp.toPx() }
    val fraction = if (duration > 0f) (value / duration).coerceIn(0f, 1f) else 0f
    var dragging by remember { mutableStateOf(false) }
    val thumb by animateDpAsState(
        targetValue = if (dragging) thumbRadius * 1.9f else thumbRadius,
        label = "scrubberThumb",
    )

    Box(
        modifier
            .fillMaxWidth()
            .height(touchHeight)
            .pointerInput(enabled, duration) {
                if (!enabled || duration <= 0f) return@pointerInput
                val span = (size.width - 2 * radiusPx).coerceAtLeast(1f)
                fun at(x: Float) = ((x - radiusPx) / span).coerceIn(0f, 1f) * duration
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        onScrub(at(offset.x))
                    },
                    onDragEnd = {
                        dragging = false
                        onScrubFinished()
                    },
                    onDragCancel = {
                        dragging = false
                        onScrubFinished()
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onScrub(at(change.position.x))
                    },
                )
            }
            .pointerInput(enabled, duration) {
                if (!enabled || duration <= 0f) return@pointerInput
                val span = (size.width - 2 * radiusPx).coerceAtLeast(1f)
                detectTapGestures { offset ->
                    onScrub((((offset.x - radiusPx) / span).coerceIn(0f, 1f)) * duration)
                    onScrubFinished()
                }
            }
            .semantics {
                this.contentDescription = contentDescription
                if (!enabled) disabled()
                if (duration > 0f) {
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    setProgress { target ->
                        onScrub(target.coerceIn(0f, 1f) * duration)
                        onScrubFinished()
                        true
                    }
                    stateDescription?.let { this.stateDescription = it }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val trackH = trackHeight.toPx()
            val knobRadius = thumb.toPx()
            val startX = knobRadius
            val endX = size.width - knobRadius
            val span = (endX - startX).coerceAtLeast(1f)
            val knobX = startX + span * fraction
            val leftEdge = (knobX - knobRadius - gapPx).coerceAtLeast(startX)
            val rightEdge = (knobX + knobRadius + gapPx).coerceAtMost(endX)

            fun segment(from: Float, to: Float, color: Color) {
                if (to - from <= 0f) return
                drawRoundRect(
                    color = color,
                    topLeft = Offset(from, centerY - trackH / 2f),
                    size = Size(to - from, trackH),
                    cornerRadius = CornerRadius(trackH / 2f, trackH / 2f),
                )
            }

            segment(rightEdge, endX, trackColor)
            if (buffered > 0f && duration > 0f) {
                val bufferedX = startX + span * (buffered / duration).coerceIn(0f, 1f)
                if (bufferedX > rightEdge) segment(rightEdge, bufferedX, bufferedColor)
            }
            segment(startX, leftEdge, activeColor)
            drawCircle(color = contentColor, radius = knobRadius, center = Offset(knobX, centerY))
        }
    }
}
