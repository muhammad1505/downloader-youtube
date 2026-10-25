package id.local.tubeflow.core

import android.content.Context
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.text.SubtitleDecoderFactory
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.extractor.text.SubtitleDecoder
import androidx.media3.extractor.text.SubtitleInputBuffer
import androidx.media3.extractor.text.SubtitleOutputBuffer
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory

/**
 * TextRenderer dengan offset waktu subtitle. Offset diterapkan saat sampel subtitle masuk decoder,
 * jadi berlaku untuk subtitle sidecar maupun track yang tertanam di video. Nilai positif menunda
 * subtitle; nilai negatif menampilkannya lebih awal.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class VideoRenderersFactory(
    context: Context,
    extensionRendererMode: Int = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON,
    private val subtitleDelayUs: () -> Long,
) : NextRenderersFactory(context) {
    init {
        // ON: dekoder perangkat tetap prioritas; FFmpeg/dav1d dipakai saat formatnya tidak
        // didukung. PREFER dipakai saat membuka ulang setelah kegagalan dekoder perangkat.
        setExtensionRendererMode(extensionRendererMode)
        setEnableDecoderFallback(true)
    }

    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) {
        out.add(TextRenderer(output, outputLooper, DelayedSubtitleDecoderFactory(subtitleDelayUs)))
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private class DelayedSubtitleDecoderFactory(
    private val subtitleDelayUs: () -> Long,
) : SubtitleDecoderFactory {
    override fun supportsFormat(format: Format): Boolean =
        SubtitleDecoderFactory.DEFAULT.supportsFormat(format)

    override fun createDecoder(format: Format): SubtitleDecoder =
        DelayedSubtitleDecoder(SubtitleDecoderFactory.DEFAULT.createDecoder(format), subtitleDelayUs)
}

@androidx.annotation.OptIn(UnstableApi::class)
internal class DelayedSubtitleDecoder(
    private val delegate: SubtitleDecoder,
    private val subtitleDelayUs: () -> Long,
) : SubtitleDecoder {
    override fun getName(): String = delegate.name

    override fun setOutputStartTimeUs(outputStartTimeUs: Long) =
        delegate.setOutputStartTimeUs(outputStartTimeUs)

    override fun setPositionUs(positionUs: Long) = delegate.setPositionUs(positionUs)

    override fun dequeueInputBuffer(): SubtitleInputBuffer? = delegate.dequeueInputBuffer()

    override fun queueInputBuffer(inputBuffer: SubtitleInputBuffer) {
        val delayUs = subtitleDelayUs()
        if (delayUs != 0L) {
            if (inputBuffer.timeUs != C.TIME_UNSET) {
                inputBuffer.timeUs += delayUs
            }
            // TextRenderer menyalin format.subsampleOffsetUs ke input; ikut digeser agar track
            // yang memakai offset subsample tidak kehilangan delay.
            if (inputBuffer.subsampleOffsetUs != C.TIME_UNSET) {
                inputBuffer.subsampleOffsetUs += delayUs
            }
        }
        delegate.queueInputBuffer(inputBuffer)
    }

    override fun dequeueOutputBuffer(): SubtitleOutputBuffer? = delegate.dequeueOutputBuffer()

    override fun flush() = delegate.flush()

    override fun release() = delegate.release()
}
