package id.local.tubeflow.core

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
class VideoVolumeProcessor(db: Int, private val onSupport: (Boolean) -> Unit = {}) : BaseAudioProcessor() {
    private val gain = VideoVolumePolicy.gain(db)

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val supported = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        onSupport(supported)
        return if (supported && gain > 1f) inputAudioFormat else AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val output = replaceOutputBuffer(inputBuffer.remaining())
        inputBuffer.order(ByteOrder.nativeOrder())
        if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
            while (inputBuffer.remaining() >= 4) output.putFloat(VideoVolumePolicy.amplify(inputBuffer.float, gain))
        } else {
            while (inputBuffer.remaining() >= 2) {
                val sample = inputBuffer.short / 32768f
                val amplified = (VideoVolumePolicy.amplify(sample, gain) * 32768f).roundToInt().coerceIn(-32768, 32767)
                output.putShort(amplified.toShort())
            }
        }
        output.flip()
    }
}
