package dev.davirazuk.okplayer.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The queue as it was when playback last paused, so the app reopens where you left off. */
data class SavedQueue(val ids: List<Long>, val index: Int, val positionMs: Long)

class QueueStore(context: Context) {
    private val prefs = context.getSharedPreferences("queue", Context.MODE_PRIVATE)

    fun save(ids: List<Long>, index: Int, positionMs: Long) {
        prefs.edit()
            .putString(KEY_IDS, ids.joinToString(","))
            .putInt(KEY_INDEX, index)
            .putLong(KEY_POSITION, positionMs)
            .apply()
    }

    fun load(): SavedQueue? {
        val ids = prefs.getString(KEY_IDS, null)?.split(',')?.mapNotNull { it.toLongOrNull() }.orEmpty()
        if (ids.isEmpty()) return null
        return SavedQueue(ids, prefs.getInt(KEY_INDEX, 0).coerceIn(0, ids.lastIndex), prefs.getLong(KEY_POSITION, 0))
    }

    private companion object {
        const val KEY_IDS = "ids"
        const val KEY_INDEX = "index"
        const val KEY_POSITION = "position"
    }
}

/** One-line messages from the playback service for the status bar. */
data class Notice(val id: Long, val text: String, val isError: Boolean)

object PlaybackEvents {
    private val _notice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = _notice.asStateFlow()
    private var nextId = 0L

    @Synchronized
    fun post(text: String, isError: Boolean = true) {
        _notice.value = Notice(nextId++, text, isError)
    }

    fun dismiss(id: Long) {
        if (_notice.value?.id == id) _notice.value = null
    }
}
