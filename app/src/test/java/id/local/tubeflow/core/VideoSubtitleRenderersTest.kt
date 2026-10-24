package id.local.tubeflow.core

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.SubtitleDecoder
import androidx.media3.extractor.text.SubtitleInputBuffer
import androidx.media3.extractor.text.SubtitleOutputBuffer
import org.junit.Assert.assertEquals
import org.junit.Test

@androidx.annotation.OptIn(UnstableApi::class)
class VideoSubtitleRenderersTest {

    private class FakeDecoder : SubtitleDecoder {
        var queued: SubtitleInputBuffer? = null
        override fun getName(): String = "fake"
        override fun setOutputStartTimeUs(outputStartTimeUs: Long) = Unit
        override fun setPositionUs(positionUs: Long) = Unit
        override fun dequeueInputBuffer(): SubtitleInputBuffer = SubtitleInputBuffer()
        override fun queueInputBuffer(inputBuffer: SubtitleInputBuffer) {
            queued = inputBuffer
        }
        override fun dequeueOutputBuffer(): SubtitleOutputBuffer? = null
        override fun flush() = Unit
        override fun release() = Unit
    }

    @Test
    fun positiveDelayShiftsSampleAndSubsampleOffset() {
        val delegate = FakeDecoder()
        val decoder = DelayedSubtitleDecoder(delegate) { 250_000L }
        val input = SubtitleInputBuffer().apply {
            timeUs = 1_000_000L
            subsampleOffsetUs = 2_000L
        }

        decoder.queueInputBuffer(input)

        assertEquals(1_250_000L, delegate.queued?.timeUs)
        assertEquals(252_000L, delegate.queued?.subsampleOffsetUs)
    }

    @Test
    fun negativeDelayDoesNotTouchUnsetSubsampleOffset() {
        val delegate = FakeDecoder()
        val decoder = DelayedSubtitleDecoder(delegate) { -500_000L }
        val input = SubtitleInputBuffer().apply {
            timeUs = 2_000_000L
            subsampleOffsetUs = C.TIME_UNSET
        }

        decoder.queueInputBuffer(input)

        assertEquals(1_500_000L, delegate.queued?.timeUs)
        assertEquals(C.TIME_UNSET, delegate.queued?.subsampleOffsetUs)
    }

    @Test
    fun zeroDelayLeavesTimestampsUntouched() {
        val delegate = FakeDecoder()
        val decoder = DelayedSubtitleDecoder(delegate) { 0L }
        val input = SubtitleInputBuffer().apply {
            timeUs = 3_000_000L
            subsampleOffsetUs = 4_000L
        }

        decoder.queueInputBuffer(input)

        assertEquals(3_000_000L, delegate.queued?.timeUs)
        assertEquals(4_000L, delegate.queued?.subsampleOffsetUs)
    }
}
