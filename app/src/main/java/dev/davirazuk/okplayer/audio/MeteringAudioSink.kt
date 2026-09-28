package dev.davirazuk.okplayer.audio

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer

/**
 * Sits in front of the real sink (built with float output enabled) and does three jobs:
 *
 * - Offers float PCM only while [floatAllowed] says so, which is when a USB DAC is
 *   connected and hi-res output is on. Decoders ask the sink before choosing their
 *   output format, so everywhere else they stay on plain 16-bit, the path every phone
 *   decoder handles well.
 * - Reports the decoded PCM format to the DAC router before the audio track opens.
 * - Lets the level meter peek at each buffer. An audio processor tap would miss the
 *   float path, because ExoPlayer skips processors there.
 */
class MeteringAudioSink(
    sink: AudioSink,
    private val floatAllowed: () -> Boolean,
    private val onPcmFormat: (sampleRate: Int, encoding: Int) -> Unit,
) : ForwardingAudioSink(sink) {

    private var lastBuffer: ByteBuffer? = null
    private var lastPosition = -1

    override fun getFormatSupport(format: Format): Int {
        if (isFloatPcm(format) && !floatAllowed()) return AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING
        return super.getFormatSupport(format)
    }

    override fun supportsFormat(format: Format): Boolean =
        getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        onPcmFormat(inputFormat.sampleRate, inputFormat.pcmEncoding)
        LevelMeter.flush(inputFormat.sampleRate, inputFormat.channelCount, inputFormat.pcmEncoding)
        super.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        // The same buffer is offered again until the sink has taken all of it; read it once.
        if (buffer !== lastBuffer || buffer.position() != lastPosition) {
            LevelMeter.handleBuffer(buffer)
            lastBuffer = buffer
            lastPosition = buffer.position()
        }
        return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }

    override fun flush() {
        lastBuffer = null
        lastPosition = -1
        LevelMeter.reset()
        super.flush()
    }

    private fun isFloatPcm(format: Format) =
        MimeTypes.AUDIO_RAW == format.sampleMimeType && format.pcmEncoding == C.ENCODING_PCM_FLOAT
}
