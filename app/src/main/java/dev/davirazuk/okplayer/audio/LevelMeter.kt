package dev.davirazuk.okplayer.audio

import androidx.media3.common.C
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/**
 * Keeps three rough band levels of the decoded audio for the visualizer.
 * It only reads the buffers [MeteringAudioSink] hands it, so output stays untouched.
 *
 * No FFT: a pair of one-pole filters is enough to make the picture follow the kick,
 * the voice and the cymbals, and it runs on the audio thread without allocating.
 */
object LevelMeter {

    @Volatile var bass = 0f; private set
    @Volatile var mid = 0f; private set
    @Volatile var treble = 0f; private set

    private var encoding = C.ENCODING_INVALID
    private var channels = 2
    private var lowAlpha = 0f
    private var highAlpha = 0f
    private var low = 0f
    private var lowMid = 0f

    fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        this.encoding = encoding
        channels = max(1, channelCount)
        lowAlpha = alpha(180f, sampleRateHz)
        highAlpha = alpha(2500f, sampleRateHz)
        low = 0f
        lowMid = 0f
    }

    fun handleBuffer(buffer: ByteBuffer) {
        if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) return
        val data = buffer.duplicate().order(ByteOrder.nativeOrder())
        var sumBass = 0f
        var sumMid = 0f
        var sumTreble = 0f
        var frames = 0

        while (data.remaining() >= frameBytes()) {
            var mono = 0f
            repeat(channels) { mono += readSample(data) }
            mono /= channels

            low += lowAlpha * (mono - low)
            lowMid += highAlpha * (mono - lowMid)
            sumBass += abs(low)
            sumMid += abs(lowMid - low)
            sumTreble += abs(mono - lowMid)
            frames++
        }
        if (frames == 0) return

        bass = smooth(bass, sumBass / frames * 3.2f)
        mid = smooth(mid, sumMid / frames * 4.5f)
        treble = smooth(treble, sumTreble / frames * 7f)
    }

    fun reset() {
        bass = 0f; mid = 0f; treble = 0f
    }

    private fun frameBytes() = channels * if (encoding == C.ENCODING_PCM_16BIT) 2 else 4

    private fun readSample(data: ByteBuffer): Float = when (encoding) {
        C.ENCODING_PCM_16BIT -> data.short / 32768f
        else -> data.float
    }

    private fun alpha(cutoffHz: Float, sampleRate: Int): Float {
        if (sampleRate <= 0) return 0f
        val rc = 1f / (2f * Math.PI.toFloat() * cutoffHz)
        val dt = 1f / sampleRate
        return dt / (rc + dt)
    }

    // Quick attack, slow release, clamped to 0..1.
    private fun smooth(current: Float, target: Float): Float {
        val t = target.coerceIn(0f, 1f)
        return if (t > current) current + (t - current) * 0.6f else current + (t - current) * 0.12f
    }
}
