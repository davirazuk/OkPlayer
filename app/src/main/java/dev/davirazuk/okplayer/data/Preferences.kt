package dev.davirazuk.okplayer.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("okplayer", Context.MODE_PRIVATE)
        _noSkipping.value = prefs.getBoolean(KEY_NO_SKIPPING, false)
        _hiResOutput.value = prefs.getBoolean(KEY_HI_RES, true)
        _favorites.value = prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().toSet()
    }

    fun setNoSkipping(enabled: Boolean) {
        _noSkipping.value = enabled
        prefs.edit().putBoolean(KEY_NO_SKIPPING, enabled).apply()
    }

    fun setHiResOutput(enabled: Boolean) {
        _hiResOutput.value = enabled
        prefs.edit().putBoolean(KEY_HI_RES, enabled).apply()
    }

    fun toggleFavorite(mediaId: String) {
        val next = _favorites.value.let { if (mediaId in it) it - mediaId else it + mediaId }
        _favorites.value = next
        prefs.edit().putStringSet(KEY_FAVORITES, next).apply()
    }

    private const val KEY_NO_SKIPPING = "no_skipping"
    private const val KEY_HI_RES = "hi_res_output"
    private const val KEY_FAVORITES = "favorites"
}
