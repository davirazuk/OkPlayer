package dev.davirazuk.okplayer.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayStat(val count: Int, val lastPlayedMs: Long)

/** How often and how recently each song was played, for the auto playlists. */
object PlayStats {
    private lateinit var prefs: SharedPreferences
    private val _stats = MutableStateFlow<Map<String, PlayStat>>(emptyMap())
    val stats: StateFlow<Map<String, PlayStat>> = _stats.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("plays", Context.MODE_PRIVATE)
        _stats.value = prefs.all.mapNotNull { (id, v) ->
            val parts = (v as? String)?.split(',') ?: return@mapNotNull null
            val count = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            id to PlayStat(count, parts.getOrNull(1)?.toLongOrNull() ?: 0)
        }.toMap()
    }

    /** Counts a play; called when a song finishes or was listened to most of the way. */
    @Synchronized
    fun record(mediaId: String) {
        val now = System.currentTimeMillis()
        val next = PlayStat((_stats.value[mediaId]?.count ?: 0) + 1, now)
        _stats.value = _stats.value + (mediaId to next)
        prefs.edit().putString(mediaId, "${next.count},${next.lastPlayedMs}").apply()
    }
}

/** Pauses playback after a set time, fading out over the last few seconds. */
object SleepTimer {
    /** When playback should stop, or null when the timer is off. */
    private val _endsAt = MutableStateFlow<Long?>(null)
    val endsAt: StateFlow<Long?> = _endsAt.asStateFlow()

    fun start(minutes: Int) {
        _endsAt.value = System.currentTimeMillis() + minutes * 60_000L
    }

    fun cancel() {
        _endsAt.value = null
    }
}
