package id.local.tubeflow.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Pemilih rentang pada satu bilah: titik awal dan akhir dipegang dua bulatan pada track yang sama,
 * bukan dua slider terpisah. Menarik salah satu ujung mengubah batas itu saja, menarik bagian
 * tengah memindahkan seluruh klip tanpa mengubah panjangnya. Ketukan di luar rentang menarik ujung
 * terdekat ke posisi ketukan.
 *
 * Semua nilai dalam milidetik. [onRangeChange] sudah dijepit ke dalam [durationMs] dan
 * [minimumSpanMs]; pemanggil tetap perlu menyimpan hasilnya karena komponen ini tanpa state.
 */
@Composable
fun RangeScrubber(
    startMs: Float,
    endMs: Float,
    durationMs: Float,
    onRangeChange: (startMs: Float, endMs: Float) -> Unit,
    contentColor: Color,
    modifier: Modifier = Modifier,
    minimumSpanMs: Float = 0f,
    enabled: Boolean = true,
    trackColor: Color = contentColor.copy(alpha = 0.26f),
    selectionColor: Color = contentColor.copy(alpha = 0.30f),
    trackHeight: Dp = 3.dp,
    thumbRadius: Dp = 7.dp,
    touchHeight: Dp = 48.dp,
    contentDescription: String = "Rentang potongan",
    stateDescription: String? = null,
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { thumbRadius.toPx() }
    val grabPx = with(density) { 24.dp.toPx() }

    // Selama geser, state berubah dan composable digambar ulang. Nilai terbaru dibaca lewat
    // rememberUpdatedState supaya blok pointerInput tidak perlu di-restart (itu membatalkan geser).
    val currentStart by rememberUpdatedState(startMs)
    val currentEnd by rememberUpdatedState(endMs)
    val report by rememberUpdatedState(onRangeChange)

    Box(
        modifier
            .fillMaxWidth()
            .height(touchHeight)
            .pointerInput(enabled, durationMs, radiusPx, grabPx, minimumSpanMs) {
                if (!enabled || durationMs <= 0f) return@pointerInput
                var mode = RangeDragMode.Window
                var downX = 0f
                var initialStart = 0f
                var initialEnd = 0f
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val width = size.width.toFloat()
                        downX = offset.x
                        initialStart = currentStart
                        initialEnd = currentEnd
                        mode = rangeDragModeAt(
                            x = offset.x,
                            startX = scrubberXFor(initialStart, width, radiusPx, durationMs),
                            endX = scrubberXFor(initialEnd, width, radiusPx, durationMs),
                            grabPx = grabPx,
                        )
                    },
                    onDragEnd = {},
                    onDragCancel = {},
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val width = size.width.toFloat()
                        val delta = when (mode) {
                            RangeDragMode.Start -> {
                                val target = scrubberPositionAt(change.position.x, width, radiusPx, durationMs)
                                target - initialStart
                            }
                            RangeDragMode.End -> {
                                val target = scrubberPositionAt(change.position.x, width, radiusPx, durationMs)
                                target - initialEnd
                            }
                            RangeDragMode.Window -> {
                                val spanPx = (width - 2f * radiusPx).coerceAtLeast(1f)
                                (change.position.x - downX) * (durationMs / spanPx)
                            }
                        }
                        val next = rangeDragSelection(
                            mode = mode,
                            startMs = initialStart,
                            endMs = initialEnd,
                            durationMs = durationMs,
                            deltaMs = delta,
                            minimumSpanMs = minimumSpanMs,
                        )
                        report(next.startMs, next.endMs)
                    },
                )
            }
            .pointerInput(enabled, durationMs, radiusPx, minimumSpanMs) {
                if (!enabled || durationMs <= 0f) return@pointerInput
                detectTapGestures { offset ->
                    val width = size.width.toFloat()
                    val startX = scrubberXFor(currentStart, width, radiusPx, durationMs)
                    val endX = scrubberXFor(currentEnd, width, radiusPx, durationMs)
                    val target = scrubberPositionAt(offset.x, width, radiusPx, durationMs)
                    val useStart = abs(offset.x - startX) <= abs(offset.x - endX)
                    val mode = if (useStart) RangeDragMode.Start else RangeDragMode.End
                    val anchor = if (useStart) currentStart else currentEnd
                    val next = rangeDragSelection(
                        mode = mode,
                        startMs = currentStart,
                        endMs = currentEnd,
                        durationMs = durationMs,
                        deltaMs = target - anchor,
                        minimumSpanMs = minimumSpanMs,
                    )
                    report(next.startMs, next.endMs)
                }
            }
            .semantics {
                this.contentDescription = contentDescription
                stateDescription?.let { this.stateDescription = it }
                if (!enabled) {
                    disabled()
                } else {
                    // Rentang tidak bisa diwakili ProgressBarRangeInfo satu nilai; TalkBack
                    // memakai aksi khusus untuk menggeser tiap ujung per detik.
                    customActions = listOf(
                        rangeAction("Awal maju 1 detik", RangeDragMode.Start, 1_000f, startMs, endMs, durationMs, minimumSpanMs, report),
                        rangeAction("Awal mundur 1 detik", RangeDragMode.Start, -1_000f, startMs, endMs, durationMs, minimumSpanMs, report),
                        rangeAction("Akhir maju 1 detik", RangeDragMode.End, 1_000f, startMs, endMs, durationMs, minimumSpanMs, report),
                        rangeAction("Akhir mundur 1 detik", RangeDragMode.End, -1_000f, startMs, endMs, durationMs, minimumSpanMs, report),
                    )
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val trackH = trackHeight.toPx()
            val knobRadius = thumbRadius.toPx()
            val startX = knobRadius
            val endX = size.width - knobRadius
            val span = (endX - startX).coerceAtLeast(1f)
            fun position(valueMs: Float): Float {
                val fraction = if (durationMs > 0f) (valueMs / durationMs).coerceIn(0f, 1f) else 0f
                return startX + span * fraction
            }
            fun segment(from: Float, to: Float, color: Color) {
                if (to - from <= 0f) return
                drawRoundRect(
                    color = color,
                    topLeft = Offset(from, centerY - trackH / 2f),
                    size = Size(to - from, trackH),
                    cornerRadius = CornerRadius(trackH / 2f, trackH / 2f),
                )
            }

            val startPos = position(startMs)
            val endPos = position(endMs)
            segment(startX, endX, trackColor)
            segment(startPos, endPos, selectionColor)

            drawCircle(color = contentColor, radius = knobRadius, center = Offset(startPos, centerY))
            drawCircle(color = contentColor, radius = knobRadius, center = Offset(endPos, centerY))
        }
    }
}

/** Ujung yang dipegang saat jari menyentuh bilah. */
internal enum class RangeDragMode { Start, End, Window }

/**
 * Memilih ujung yang paling dekat dengan sentuhan. Sentuhan di tengah bagian terpilih berarti
 * menggeser seluruh klip; sentuhan di luar bagian terpilih menarik ujung terdekat.
 */
internal fun rangeDragModeAt(x: Float, startX: Float, endX: Float, grabPx: Float): RangeDragMode {
    val nearStart = abs(x - startX) <= grabPx
    val nearEnd = abs(x - endX) <= grabPx
    return when {
        // Video panjang bisa membuat klip 30 detik lebih sempit dari radius sentuh kedua ujung.
        // Keduanya tetap dapat diraih: separuh kiri untuk awal, separuh kanan untuk akhir.
        nearStart && nearEnd -> if (x < (startX + endX) / 2f) RangeDragMode.Start else RangeDragMode.End
        nearStart -> RangeDragMode.Start
        nearEnd -> RangeDragMode.End
        x > startX && x < endX -> RangeDragMode.Window
        x < startX -> RangeDragMode.Start
        else -> RangeDragMode.End
    }
}

internal data class RangeSelection(val startMs: Float, val endMs: Float)

/** Menerapkan satu langkah geser ke ujung atau ke seluruh rentang, tetap di dalam video. */
internal fun rangeDragSelection(
    mode: RangeDragMode,
    startMs: Float,
    endMs: Float,
    durationMs: Float,
    deltaMs: Float,
    minimumSpanMs: Float = 0f,
): RangeSelection {
    if (durationMs <= 0f || !durationMs.isFinite()) return RangeSelection(0f, 0f)
    val duration = durationMs
    val minSpan = minimumSpanMs.coerceIn(0f, duration)
    val start = startMs.coerceIn(0f, duration)
    val end = endMs.coerceIn(start, duration)
    return when (mode) {
        RangeDragMode.Start -> {
            val nextStart = (start + deltaMs).coerceIn(0f, (end - minSpan).coerceAtLeast(0f))
            RangeSelection(nextStart, end)
        }
        RangeDragMode.End -> {
            val nextEnd = (end + deltaMs).coerceIn((start + minSpan).coerceAtMost(duration), duration)
            RangeSelection(start, nextEnd)
        }
        RangeDragMode.Window -> {
            val span = (end - start).coerceIn(0f, duration)
            val nextStart = (start + deltaMs).coerceIn(0f, duration - span)
            RangeSelection(nextStart, nextStart + span)
        }
    }
}

/** Posisi x untuk sebuah nilai waktu; kebalikan dari [scrubberPositionAt]. */
internal fun scrubberXFor(valueMs: Float, width: Float, radiusPx: Float, durationMs: Float): Float {
    val span = (width - 2f * radiusPx).coerceAtLeast(1f)
    val fraction = if (durationMs > 0f) (valueMs / durationMs).coerceIn(0f, 1f) else 0f
    return radiusPx + span * fraction
}

private fun rangeAction(
    label: String,
    mode: RangeDragMode,
    deltaMs: Float,
    startMs: Float,
    endMs: Float,
    durationMs: Float,
    minimumSpanMs: Float,
    report: (Float, Float) -> Unit,
): CustomAccessibilityAction = CustomAccessibilityAction(label) {
    val next = rangeDragSelection(mode, startMs, endMs, durationMs, deltaMs, minimumSpanMs)
    report(next.startMs, next.endMs)
    true
}
