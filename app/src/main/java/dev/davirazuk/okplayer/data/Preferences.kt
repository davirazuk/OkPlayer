package dev.davirazuk.okplayer.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the app looks: okplayer's original dark deck with a taskbar, or Windows Media Player 12. */
enum class Skin { Seven, Wmp }

/**
 * App-wide settings. The playback service and the UI live in the same process,
 * so both read the same instance.
 */
object Preferences {
    private lateinit var prefs: SharedPreferences

    private val _noSkipping = MutableStateFlow(false)
    val noSkipping: StateFlow<Boolean> = _noSkipping.asStateFlow()

    private val _hiResOutput = MutableStateFlow(true)
    val hiResOutput: StateFlow<Boolean> = _hiResOutput.asStateFlow()

    private val _builtInDecoder = MutableStateFlow(false)

    /** Decode everything FFmpeg can handle with FFmpeg instead of the phone's decoders. */
    val builtInDecoder: StateFlow<Boolean> = _builtInDecoder.asStateFlow()

    private val _onlineLyrics = MutableStateFlow(true)

    /** Look up lyrics on lrclib.net when the file has none. Sends artist, title, album and length. */
    val onlineLyrics: StateFlow<Boolean> = _onlineLyrics.asStateFlow()

    private val _skin = MutableStateFlow(Skin.Seven)
    val skin: StateFlow<Skin> = _skin.asStateFlow()

    private lateinit var ratingPrefs: SharedPreferences
    private val _ratings = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** Star ratings, 1 to 5, keyed by media id. Unrated songs are absent. */
    val ratings: StateFlow<Map<String, Int>> = _ratings.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("okplayer", Context.MODE_PRIVATE)
        _noSkipping.value = prefs.getBoolean(KEY_NO_SKIPPING, false)
        _hiResOutput.value = prefs.getBoolean(KEY_HI_RES, true)
        _builtInDecoder.value = prefs.getBoolean(KEY_BUILT_IN_DECODER, false)
        _onlineLyrics.value = prefs.getBoolean(KEY_ONLINE_LYRICS, true)
        _skin.value = if (prefs.getString(KEY_SKIN, null) == Skin.Wmp.name) Skin.Wmp else Skin.Seven
        ratingPrefs = context.getSharedPreferences("ratings", Context.MODE_PRIVATE)
        _ratings.value = ratingPrefs.all.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.toMap()
    }

    fun setNoSkipping(enabled: Boolean) {
        _noSkipping.value = enabled
        prefs.edit().putBoolean(KEY_NO_SKIPPING, enabled).apply()
    }

    fun setHiResOutput(enabled: Boolean) {
        _hiResOutput.value = enabled
        prefs.edit().putBoolean(KEY_HI_RES, enabled).apply()
    }

    fun setBuiltInDecoder(enabled: Boolean) {
        _builtInDecoder.value = enabled
        prefs.edit().putBoolean(KEY_BUILT_IN_DECODER, enabled).apply()
    }

    fun setOnlineLyrics(enabled: Boolean) {
        _onlineLyrics.value = enabled
        prefs.edit().putBoolean(KEY_ONLINE_LYRICS, enabled).apply()
    }

    fun setSkin(skin: Skin) {
        _skin.value = skin
        prefs.edit().putString(KEY_SKIN, skin.name).apply()
    }

    /** Sets a rating; passing the current rating again clears it, like Windows Media Player. */
    fun rate(mediaId: String, stars: Int) {
        val current = _ratings.value[mediaId]
        if (current == stars || stars !in 1..5) {
            _ratings.value = _ratings.value - mediaId
            ratingPrefs.edit().remove(mediaId).apply()
        } else {
            _ratings.value = _ratings.value + (mediaId to stars)
            ratingPrefs.edit().putInt(mediaId, stars).apply()
        }
    }

    private const val KEY_NO_SKIPPING = "no_skipping"
    private const val KEY_HI_RES = "hi_res_output"
    private const val KEY_BUILT_IN_DECODER = "built_in_decoder"
    private const val KEY_ONLINE_LYRICS = "online_lyrics"
    private const val KEY_SKIN = "skin"
}
