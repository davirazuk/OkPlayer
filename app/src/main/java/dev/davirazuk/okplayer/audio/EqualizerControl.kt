package dev.davirazuk.okplayer.audio

import android.content.Context
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One slider: its centre frequency and where it sits, in millibels. */
data class EqBand(val centerHz: Int, val levelMb: Int)

data class EqState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val bands: List<EqBand> = emptyList(),
    val minMb: Int = -1500,
    val maxMb: Int = 1500,
    val preset: String = "Flat",
)

/**
 * The graphic equalizer, on Android's own equalizer effect for the player's audio
 * session. Off by default: any equalizer changes the samples, so output stops being
 * bit-perfect while it's on.
 */
object EqualizerControl {
    private val _state = MutableStateFlow(EqState())
    val state: StateFlow<EqState> = _state.asStateFlow()

    private var eq: Equalizer? = null
    private lateinit var store: android.content.SharedPreferences

    /** Relative shapes, from -1 (cut) to 1 (boost), stretched across however many bands the phone has. */
    val presets: Map<String, List<Float>> = linkedMapOf(
        "Flat" to listOf(0f, 0f, 0f, 0f, 0f),
        "Rock" to listOf(0.5f, 0.25f, -0.2f, 0.25f, 0.55f),
        "Pop" to listOf(-0.15f, 0.25f, 0.45f, 0.25f, -0.1f),
        "Jazz" to listOf(0.3f, 0.15f, -0.1f, 0.2f, 0.35f),
        "Classical" to listOf(0.35f, 0.15f, 0f, 0.15f, 0.4f),
        "Bass boost" to listOf(0.7f, 0.4f, 0f, 0f, 0f),
        "Treble boost" to listOf(0f, 0f, 0f, 0.4f, 0.7f),
        "Vocal" to listOf(-0.25f, 0f, 0.45f, 0.3f, -0.1f),
        "Late night" to listOf(0.35f, 0.1f, 0f, -0.15f, -0.35f),
    )

    fun init(context: Context) {
        store = context.getSharedPreferences("equalizer", Context.MODE_PRIVATE)
    }

    /** Attaches to the player's audio session; call again if the session changes. */
    fun attach(audioSessionId: Int) {
        release()
        // Session 0 is the global mix; never touch other apps' sound.
        if (audioSessionId <= 0) return
        val effect = runCatching { Equalizer(0, audioSessionId) }.getOrNull()
        if (effect == null) {
            _state.value = EqState(available = false)
            return
        }
        eq = effect
        val range = effect.bandLevelRange
        val count = effect.numberOfBands.toInt()
        val saved = store.getString(KEY_LEVELS, null)?.split(',')?.mapNotNull { it.toIntOrNull() }
        val bands = (0 until count).map { i ->
            val level = saved?.getOrNull(i) ?: 0
            EqBand(effect.getCenterFreq(i.toShort()) / 1000, level.coerceIn(range[0].toInt(), range[1].toInt()))
        }
        bands.forEachIndexed { i, b -> runCatching { effect.setBandLevel(i.toShort(), b.levelMb.toShort()) } }
        val enabled = store.getBoolean(KEY_ENABLED, false)
        runCatching { effect.setEnabled(enabled) }
        _state.value = EqState(
            available = true,
            enabled = enabled,
            bands = bands,
            minMb = range[0].toInt(),
            maxMb = range[1].toInt(),
            preset = store.getString(KEY_PRESET, "Flat") ?: "Flat",
        )
    }

    fun release() {
        eq?.release()
        eq = null
    }

    fun setEnabled(enabled: Boolean) {
        val s = _state.value
        if (!s.available) return
        runCatching { eq?.setEnabled(enabled) }
        store.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _state.value = s.copy(enabled = enabled)
    }

    fun setBand(index: Int, levelMb: Int) {
        val s = _state.value
        if (index !in s.bands.indices) return
        val level = levelMb.coerceIn(s.minMb, s.maxMb)
        runCatching { eq?.setBandLevel(index.toShort(), level.toShort()) }
        val bands = s.bands.mapIndexed { i, b -> if (i == index) b.copy(levelMb = level) else b }
        save(bands, "Custom")
        _state.value = s.copy(bands = bands, preset = "Custom")
    }

    fun applyPreset(name: String) {
        val s = _state.value
        val shape = presets[name] ?: return
        val top = minOf(s.maxMb, 1200)
        val bands = s.bands.mapIndexed { i, b ->
            // Sample the five-point shape at this band's position.
            val x = if (s.bands.size == 1) 0f else i * (shape.size - 1f) / (s.bands.size - 1)
            val lo = x.toInt().coerceAtMost(shape.size - 2)
            val v = shape[lo] + (shape[lo + 1] - shape[lo]) * (x - lo)
            b.copy(levelMb = (v * top).toInt().coerceIn(s.minMb, s.maxMb))
        }
        bands.forEachIndexed { i, b -> runCatching { eq?.setBandLevel(i.toShort(), b.levelMb.toShort()) } }
        save(bands, name)
        _state.value = s.copy(bands = bands, preset = name)
    }

    private fun save(bands: List<EqBand>, preset: String) {
        store.edit()
            .putString(KEY_LEVELS, bands.joinToString(",") { it.levelMb.toString() })
            .putString(KEY_PRESET, preset)
            .apply()
    }

    private const val KEY_ENABLED = "enabled"
    private const val KEY_LEVELS = "levels"
    private const val KEY_PRESET = "preset"
}
