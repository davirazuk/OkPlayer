package dev.davirazuk.okplayer.audio

import androidx.media3.common.Format
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import java.nio.ByteBuffer

/**
 * Sits in front of the real sink. It reports the decoded PCM format to the DAC router
 * before the audio track opens, and lets the level meter peek at each buffer.
 *
 * This replaces an audio processor tap, because ExoPlayer skips audio processors when
 * it outputs float, which is exactly the hi-res path.
 */
class MeteringAudioSink(
    sink: AudioSink,
    private val onPcmFormat: (sampleRate: Int, encoding: Int) -> Unit,
) : ForwardingAudioSink(sink) {

    private var lastBuffer: ByteBuffer? = null
    private var lastPosition = -1

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
}
