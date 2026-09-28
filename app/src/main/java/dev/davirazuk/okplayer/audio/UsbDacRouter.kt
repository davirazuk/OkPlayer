package dev.davirazuk.okplayer.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.media.AudioAttributes as PlatformAudioAttributes

/** What the current file is, as read from the stream. */
data class TrackInfo(
    val codec: String,
    val sampleRate: Int?,
    val bitDepth: Int?,
    val channels: Int?,
    val bitrate: Int?,
    val lossless: Boolean,
)

data class Pipeline(val decoder: String? = null, val pcmRate: Int? = null, val pcmEncoding: Int? = null) {
    val pcmLabel: String?
        get() = when (pcmEncoding) {
            null -> null
            C.ENCODING_PCM_16BIT -> "16-bit"
            C.ENCODING_PCM_24BIT -> "24-bit"
            C.ENCODING_PCM_32BIT -> "32-bit"
            C.ENCODING_PCM_FLOAT -> "32-bit float"
            else -> "PCM $pcmEncoding"
        }
}

/** Where the sound goes and whether it arrives untouched. */
sealed interface OutputStatus {
    data object Idle : OutputStatus

    data class Internal(val sampleRate: Int?) : OutputStatus

    data class Usb(
        val deviceName: String,
        val sampleRate: Int?,
        val bitPerfect: Boolean,
        val reason: String?,
        val supportedRates: List<Int>,
    ) : OutputStatus
}

object OutputState {
    private val _status = MutableStateFlow<OutputStatus>(OutputStatus.Idle)
    val status: StateFlow<OutputStatus> = _status.asStateFlow()

    private val _track = MutableStateFlow<TrackInfo?>(null)
    val track: StateFlow<TrackInfo?> = _track.asStateFlow()

    private val _pipeline = MutableStateFlow(Pipeline())

    /** Which decoder is running and what it hands the audio output, for Options. */
    val pipeline: StateFlow<Pipeline> = _pipeline.asStateFlow()

    internal fun setDecoder(name: String) {
        _pipeline.value = _pipeline.value.copy(decoder = name)
    }

    internal fun setPcm(sampleRate: Int, encoding: Int) {
        _pipeline.value = _pipeline.value.copy(pcmRate = sampleRate, pcmEncoding = encoding)
    }

    internal fun set(status: OutputStatus) {
        _status.value = status
    }

    internal fun setTrack(info: TrackInfo?) {
        _track.value = info
    }
}

/**
 * Asks Android 14+ to open a USB DAC in bit-perfect mode, matching the sample rate
 * and sample format the decoder actually produces. When the DAC or the phone can't
 * do that, Android's mixer resamples and the status line says why.
 *
 * [onOutputPcm] is called from the playback thread right before the audio track is
 * opened, so the preference is in place when the stream starts.
 */
class UsbDacRouter(context: Context, private val attributes: PlatformAudioAttributes) {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var usbDevice: AudioDeviceInfo? = null

    /** Whether a USB DAC is the current output; read from the playback thread. */
    @Volatile
    var usbConnected = false
        private set
    private var pcmRate: Int? = null
    private var pcmEncoding: Int = C.ENCODING_INVALID
    private var hasTrack = false
    private var preferredSetOn: AudioDeviceInfo? = null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refreshDevice()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refreshDevice()
    }

    fun start() {
        audioManager.registerAudioDeviceCallback(deviceCallback, handler)
        refreshDevice()
    }

    @Synchronized
    fun stop() {
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        clearPreferred()
        OutputState.set(OutputStatus.Idle)
        OutputState.setTrack(null)
    }

    /** The compressed stream's format, used for the display. */
    @Synchronized
    fun onTrackFormat(format: Format?) {
        hasTrack = format != null
        OutputState.setTrack(format?.let(::describe))
        if (format == null) pcmRate = null
        apply()
    }

    /** The decoded PCM the sink is about to play. */
    @Synchronized
    fun onOutputPcm(sampleRate: Int, encoding: Int) {
        pcmRate = sampleRate
        pcmEncoding = encoding
        apply()
    }

    @Synchronized
    private fun refreshDevice() {
        usbDevice = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE }
        usbConnected = usbDevice != null
        apply()
    }

    private fun apply() {
        val device = usbDevice
        val rate = pcmRate
        if (device == null) {
            preferredSetOn = null
            OutputState.set(if (hasTrack) OutputStatus.Internal(rate) else OutputStatus.Idle)
            return
        }
        val name = device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "USB DAC"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            OutputState.set(OutputStatus.Usb(name, rate, false, "Needs Android 14 or newer", emptyList()))
            return
        }
        OutputState.set(applyBitPerfect(device, name, rate))
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun applyBitPerfect(device: AudioDeviceInfo, name: String, rate: Int?): OutputStatus {
        val options = runCatching { audioManager.getSupportedMixerAttributes(device) }.getOrDefault(emptyList())
            .filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
        val rates = options.map { it.format.sampleRate }.distinct().sorted()

        if (options.isEmpty()) {
            clearPreferred()
            return OutputStatus.Usb(name, rate, false, "This phone doesn't offer bit-perfect USB output", rates)
        }
        if (rate == null) return OutputStatus.Usb(name, null, false, null, rates)

        val wanted = platformEncoding(pcmEncoding)
        val match = options.firstOrNull { it.format.sampleRate == rate && it.format.encoding == wanted }
        if (match == null) {
            clearPreferred()
            val reason = when {
                rate !in rates -> "DAC has no bit-perfect mode at ${formatRate(rate)}"
                else -> "DAC doesn't take this sample format bit-perfect"
            }
            return OutputStatus.Usb(name, rate, false, reason, rates)
        }

        val ok = runCatching { audioManager.setPreferredMixerAttributes(attributes, device, match) }.getOrDefault(false)
        preferredSetOn = if (ok) device else null
        return OutputStatus.Usb(name, rate, ok, if (ok) null else "Android refused bit-perfect mode", rates)
    }

    private fun clearPreferred() {
        val device = preferredSetOn ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { audioManager.clearPreferredMixerAttributes(attributes, device) }
        }
        preferredSetOn = null
    }

    private fun platformEncoding(encoding: Int) = when (encoding) {
        C.ENCODING_PCM_FLOAT -> AudioFormat.ENCODING_PCM_FLOAT
        C.ENCODING_PCM_24BIT -> AudioFormat.ENCODING_PCM_24BIT_PACKED
        C.ENCODING_PCM_32BIT -> AudioFormat.ENCODING_PCM_32BIT
        else -> AudioFormat.ENCODING_PCM_16BIT
    }
}

private fun describe(format: Format): TrackInfo {
    val mime = format.sampleMimeType
    val codec = when (mime) {
        MimeTypes.AUDIO_FLAC -> "FLAC"
        MimeTypes.AUDIO_ALAC -> "ALAC"
        MimeTypes.AUDIO_MPEG -> "MP3"
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_VORBIS -> "Vorbis"
        MimeTypes.AUDIO_OPUS -> "Opus"
        MimeTypes.AUDIO_RAW -> "PCM"
        MimeTypes.AUDIO_AC3 -> "AC-3"
        MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "E-AC-3"
        MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD -> "DTS"
        MimeTypes.AUDIO_TRUEHD -> "TrueHD"
        MimeTypes.AUDIO_AMR_NB, MimeTypes.AUDIO_AMR_WB -> "AMR"
        else -> mime?.substringAfter('/')?.uppercase() ?: "Audio"
    }
    val bits = when (format.pcmEncoding) {
        C.ENCODING_PCM_8BIT -> 8
        C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
        C.ENCODING_PCM_24BIT -> 24
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 32
        else -> null
    }
    return TrackInfo(
        codec = codec,
        sampleRate = format.sampleRate.takeIf { it != Format.NO_VALUE },
        bitDepth = bits,
        channels = format.channelCount.takeIf { it != Format.NO_VALUE },
        bitrate = format.averageBitrate.takeIf { it != Format.NO_VALUE } ?: format.bitrate.takeIf { it != Format.NO_VALUE },
        lossless = codec in setOf("FLAC", "ALAC", "PCM", "TrueHD"),
    )
}

fun formatRate(hz: Int): String =
    if (hz % 1000 == 0) "${hz / 1000} kHz" else "%.1f kHz".format(hz / 1000f)
