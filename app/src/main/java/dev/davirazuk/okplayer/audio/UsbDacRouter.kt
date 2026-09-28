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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.media.AudioAttributes as PlatformAudioAttributes

/** What the listener actually gets, shown under the controls. */
sealed interface OutputStatus {
    val sampleRate: Int?
    val bitDepth: Int?

    data object Idle : OutputStatus {
        override val sampleRate: Int? = null
        override val bitDepth: Int? = null
    }

    data class Internal(override val sampleRate: Int?, override val bitDepth: Int?) : OutputStatus

    data class Usb(
        val deviceName: String,
        override val sampleRate: Int?,
        override val bitDepth: Int?,
        val bitPerfect: Boolean,
        val reason: String?,
        val supportedRates: List<Int>,
    ) : OutputStatus
}

object OutputState {
    private val _status = MutableStateFlow<OutputStatus>(OutputStatus.Idle)
    val status: StateFlow<OutputStatus> = _status.asStateFlow()
    internal fun set(status: OutputStatus) {
        _status.value = status
    }
}

/**
 * Asks Android 14+ to open USB DACs in bit-perfect mode at the sample rate of the
 * current track. On older versions, or when the DAC doesn't offer the needed format,
 * Android's mixer resamples and the status says so.
 */
class UsbDacRouter(
    context: Context,
    private val attributes: PlatformAudioAttributes,
    private val floatOutput: Boolean,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var usbDevice: AudioDeviceInfo? = null
    private var format: Format? = null
    private var preferredSetOn: AudioDeviceInfo? = null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refreshDevice()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refreshDevice()
    }

    fun start() {
        audioManager.registerAudioDeviceCallback(deviceCallback, handler)
        refreshDevice()
    }

    fun stop() {
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        clearPreferred()
        OutputState.set(OutputStatus.Idle)
    }

    fun onTrackFormat(format: Format?) {
        this.format = format
        apply()
    }

    private fun refreshDevice() {
        usbDevice = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE }
        apply()
    }

    private fun apply() {
        val rate = format?.sampleRate?.takeIf { it != Format.NO_VALUE }
        val bits = format?.let(::bitDepthOf)
        val device = usbDevice

        if (device == null) {
            preferredSetOn = null
            OutputState.set(if (format == null) OutputStatus.Idle else OutputStatus.Internal(rate, bits))
            return
        }

        val name = device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "USB DAC"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            OutputState.set(OutputStatus.Usb(name, rate, bits, false, "Needs Android 14 or newer", emptyList()))
            return
        }
        OutputState.set(applyBitPerfect(device, name, rate, bits))
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun applyBitPerfect(device: AudioDeviceInfo, name: String, rate: Int?, bits: Int?): OutputStatus {
        val options = runCatching { audioManager.getSupportedMixerAttributes(device) }.getOrDefault(emptyList())
            .filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
        val rates = options.map { it.format.sampleRate }.distinct().sorted()

        if (options.isEmpty()) {
            clearPreferred()
            return OutputStatus.Usb(name, rate, bits, false, "This phone doesn't offer bit-perfect USB output", rates)
        }
        if (rate == null) {
            return OutputStatus.Usb(name, null, null, false, null, rates)
        }

        val wanted = if (floatOutput) AudioFormat.ENCODING_PCM_FLOAT else AudioFormat.ENCODING_PCM_16BIT
        val match = options.firstOrNull { it.format.sampleRate == rate && it.format.encoding == wanted }
        if (match == null) {
            clearPreferred()
            val reason = if (rate in rates) "DAC doesn't accept this bit depth bit-perfect" else "DAC doesn't support ${formatRate(rate)}"
            return OutputStatus.Usb(name, rate, bits, false, reason, rates)
        }

        val ok = runCatching { audioManager.setPreferredMixerAttributes(attributes, device, match) }.getOrDefault(false)
        if (ok) preferredSetOn = device
        return OutputStatus.Usb(name, rate, bits, ok, if (ok) null else "Android refused bit-perfect mode", rates)
    }

    private fun clearPreferred() {
        val device = preferredSetOn ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { audioManager.clearPreferredMixerAttributes(attributes, device) }
        }
        preferredSetOn = null
    }

    private fun bitDepthOf(format: Format): Int? = when (format.pcmEncoding) {
        C.ENCODING_PCM_8BIT -> 8
        C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
        C.ENCODING_PCM_24BIT -> 24
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 32
        else -> null
    }
}

fun formatRate(hz: Int): String =
    if (hz % 1000 == 0) "${hz / 1000} kHz" else "%.1f kHz".format(hz / 1000f)
