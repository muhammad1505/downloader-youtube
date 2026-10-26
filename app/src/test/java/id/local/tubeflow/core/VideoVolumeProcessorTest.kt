package id.local.tubeflow.core

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

@androidx.annotation.OptIn(UnstableApi::class)
class VideoVolumeProcessorTest {
    @Test fun boostsStereoWithoutSwappingChannelsOrChangingFrameCount() {
        val processor = VideoVolumeProcessor(6)
        val format = AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)
        assertEquals(format, processor.configure(format))
        processor.flush(StreamMetadata.DEFAULT)
        val input = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder())
        listOf(2_000, -4_000, 0, 32_767).forEach { input.putShort(it.toShort()) }; input.flip()
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder())
        assertEquals(8, output.remaining())
        assertTrue(output.short.toInt() in 3_980..4_000)
        assertTrue(output.short.toInt() in -8_000..-7_960)
        assertEquals(0, output.short.toInt())
        assertTrue(output.short > 0) // Peaks must never overflow into negative PCM.
        assertFalse(input.hasRemaining())
        processor.queueEndOfStream()
        assertTrue(processor.isEnded)
    }

    @Test fun floatAudioIsFiniteAndSoftLimitedOnBothPolarities() {
        val processor = VideoVolumeProcessor(9)
        processor.configure(AudioFormat(44_100, 1, C.ENCODING_PCM_FLOAT)); processor.flush(StreamMetadata.DEFAULT)
        val samples = listOf(.1f, -.1f, 1f, -1f, Float.NaN, Float.MAX_VALUE)
        val input = ByteBuffer.allocateDirect(samples.size * 4).order(ByteOrder.nativeOrder())
        samples.forEach(input::putFloat); input.flip(); processor.queueInput(input)
        val out = processor.output.order(ByteOrder.nativeOrder())
        val values = List(samples.size) { out.float }
        assertEquals(.28184f, values[0], .001f)
        assertEquals(-values[0], values[1], .001f)
        assertTrue(values.all { it.isFinite() && it in -1f..1f })
        assertTrue(values[2] > .9f); assertTrue(values[3] < -.9f)
        assertEquals(0f, values[4], 0f)
    }

    @Test fun unsupportedFormatsAreBypassedAndReportedRatherThanReinterpretedAsPcm16() {
        var supported: Boolean? = null
        val processor = VideoVolumeProcessor(6) { supported = it }
        assertEquals(AudioFormat.NOT_SET, processor.configure(AudioFormat(48_000, 2, C.ENCODING_PCM_8BIT)))
        processor.flush(StreamMetadata.DEFAULT); assertFalse(processor.isActive); assertEquals(false, supported)
    }

    @Test fun normalDoesNotActivateProcessingAndFlushDropsOldOutput() {
        val format = AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)
        val normal = VideoVolumeProcessor(0); assertEquals(AudioFormat.NOT_SET, normal.configure(format))
        normal.flush(StreamMetadata.DEFAULT); assertFalse(normal.isActive)
        val boosted = VideoVolumeProcessor(3); boosted.configure(format); boosted.flush(StreamMetadata.DEFAULT)
        val input = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).putInt(0); input.flip()
        boosted.queueInput(input); boosted.flush(StreamMetadata.DEFAULT); assertFalse(boosted.output.hasRemaining())
    }

    @Test fun gainStepsAreBoundedAndKneeIsContinuousMonotonicAndSymmetric() {
        assertEquals(0, VideoVolumePolicy.boostDb(Int.MIN_VALUE))
        assertEquals(9, VideoVolumePolicy.boostDb(Int.MAX_VALUE))
        assertEquals(3, VideoVolumePolicy.boostDb(5))
        assertEquals(1f, VideoVolumePolicy.gain(0), 0f)
        val g = VideoVolumePolicy.gain(6)
        val values = (0..100).map { VideoVolumePolicy.amplify(it / 100f, g) }
        assertTrue(values.zipWithNext().all { (a,b) -> a <= b })
        assertTrue(values.all { it in 0f..1f })
        assertEquals(-VideoVolumePolicy.amplify(.8f,g), VideoVolumePolicy.amplify(-.8f,g), 0f)
        val threshold = .9f/g
        assertTrue(VideoVolumePolicy.amplify(threshold + .0001f,g) - VideoVolumePolicy.amplify(threshold - .0001f,g) < .001f)
    }
}
