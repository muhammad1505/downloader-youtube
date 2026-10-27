package id.local.tubeflow.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RangeScrubberGeometryTest {

    private val duration = 60_000f
    private val minSpan = 1_000f

    @Test
    fun touchesNearAnEdgeGrabThatEdgeAndTheMiddleMovesTheWindow() {
        val startX = 100f
        val endX = 200f
        val grab = 24f

        assertEquals(RangeDragMode.Start, rangeDragModeAt(startX, startX, endX, grab))
        assertEquals(RangeDragMode.Start, rangeDragModeAt(110f, startX, endX, grab))
        assertEquals(RangeDragMode.Window, rangeDragModeAt(150f, startX, endX, grab))
        assertEquals(RangeDragMode.Window, rangeDragModeAt(126f, startX, endX, grab))
        assertEquals(RangeDragMode.End, rangeDragModeAt(190f, startX, endX, grab))
        assertEquals(RangeDragMode.End, rangeDragModeAt(260f, startX, endX, grab))
    }

    @Test
    fun overlappingHandlesStayReachableOnLongVideos() {
        // Klip 30 detik pada video berjam-jam: kedua ujung hanya berjarak beberapa piksel.
        val startX = 5f
        val endX = 8f

        assertEquals(RangeDragMode.Start, rangeDragModeAt(4f, startX, endX, 24f))
        assertEquals(RangeDragMode.End, rangeDragModeAt(7f, startX, endX, 24f))
    }

    @Test
    fun touchesOutsideTheSelectionPullTheNearestEdge() {
        val startX = 100f
        val endX = 200f

        assertEquals(RangeDragMode.Start, rangeDragModeAt(40f, startX, endX, 24f))
        assertEquals(RangeDragMode.End, rangeDragModeAt(260f, startX, endX, 24f))
    }

    @Test
    fun draggingTheStartStopsOneSecondBeforeTheEnd() {
        val next = rangeDragSelection(RangeDragMode.Start, 10_000f, 40_000f, duration, 100_000f, minSpan)

        assertEquals(39_000f, next.startMs, 0.01f)
        assertEquals(40_000f, next.endMs, 0.01f)
    }

    @Test
    fun draggingTheStartStaysInsideTheVideo() {
        val next = rangeDragSelection(RangeDragMode.Start, 10_000f, 40_000f, duration, -50_000f, minSpan)

        assertEquals(0f, next.startMs, 0.01f)
        assertEquals(40_000f, next.endMs, 0.01f)
    }

    @Test
    fun draggingTheEndStopsAtTheVideoAndAtTheMinimumSpan() {
        val forward = rangeDragSelection(RangeDragMode.End, 10_000f, 40_000f, duration, 100_000f, minSpan)
        assertEquals(10_000f, forward.startMs, 0.01f)
        assertEquals(duration, forward.endMs, 0.01f)

        val backward = rangeDragSelection(RangeDragMode.End, 10_000f, 40_000f, duration, -100_000f, minSpan)
        assertEquals(10_000f, backward.startMs, 0.01f)
        assertEquals(11_000f, backward.endMs, 0.01f)
    }

    @Test
    fun draggingTheMiddleKeepsTheSpanAndStaysInside() {
        val shifted = rangeDragSelection(RangeDragMode.Window, 10_000f, 40_000f, duration, 5_000f, minSpan)
        assertEquals(15_000f, shifted.startMs, 0.01f)
        assertEquals(45_000f, shifted.endMs, 0.01f)
        assertEquals(30_000f, shifted.endMs - shifted.startMs, 0.01f)

        val farRight = rangeDragSelection(RangeDragMode.Window, 10_000f, 40_000f, duration, 100_000f, minSpan)
        assertEquals(30_000f, farRight.startMs, 0.01f)
        assertEquals(duration, farRight.endMs, 0.01f)

        val farLeft = rangeDragSelection(RangeDragMode.Window, 10_000f, 40_000f, duration, -100_000f, minSpan)
        assertEquals(0f, farLeft.startMs, 0.01f)
        assertEquals(30_000f, farLeft.endMs, 0.01f)
    }

    @Test
    fun minimumSpanLargerThanAShortVideoIsClamped() {
        val end = rangeDragSelection(RangeDragMode.End, 0f, 500f, 500f, -1_000f, 1_000f)
        assertEquals(0f, end.startMs, 0.01f)
        assertEquals(500f, end.endMs, 0.01f)

        val start = rangeDragSelection(RangeDragMode.Start, 0f, 500f, 500f, 1_000f, 1_000f)
        assertEquals(0f, start.startMs, 0.01f)
        assertEquals(500f, start.endMs, 0.01f)
    }

    @Test
    fun unknownDurationKeepsTheSelectionEmpty() {
        val next = rangeDragSelection(RangeDragMode.Window, 1_000f, 2_000f, 0f, 500f, minSpan)

        assertEquals(0f, next.startMs, 0.01f)
        assertEquals(0f, next.endMs, 0.01f)
    }

    @Test
    fun valueToXIsTheInverseOfPositionAt() {
        assertEquals(5f, scrubberXFor(0f, 300f, 5f, duration), 0.01f)
        assertEquals(295f, scrubberXFor(duration, 300f, 5f, duration), 0.01f)
        assertEquals(150f, scrubberXFor(duration / 2f, 300f, 5f, duration), 0.01f)
        // Durasi belum diketahui: nilai apa pun menempel di ujung kiri track, sama seperti nilai 0.
        assertEquals(5f, scrubberXFor(999_000f, 300f, 5f, 0f), 0.01f)

        val x = scrubberXFor(20_000f, 333f, 7f, duration)
        assertEquals(20_000f, scrubberPositionAt(x, 333f, 7f, duration), 0.01f)
    }
}
