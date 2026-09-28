package dev.davirazuk.okplayer.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class NowPlaying(
    val mediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artUri: Uri? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val hasNext: Boolean = false,
    val index: Int = 0,
    val count: Int = 0,
) {
    val isEmpty get() = mediaId == null
}

data class QueueEntry(val mediaId: String, val title: String, val artist: String)

/** UI-side handle on the playback service. */
class PlayerConnection(private val context: Context, private val scope: CoroutineScope) {

    private var controller: MediaController? = null
    private var ticker: Job? = null

    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueEntry>>(emptyList())
    val queue: StateFlow<List<QueueEntry>> = _queue.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish()
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) publishQueue()
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) updateTicker()
        }
    }

    fun connect() {
        if (controller != null) return
        scope.launch {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val c = MediaController.Builder(context, token).buildAsync().await()
            controller = c
            c.addListener(listener)
            publish()
            publishQueue()
            updateTicker()
        }
    }

    fun release() {
        ticker?.cancel()
        controller?.run {
            removeListener(listener)
            release()
        }
        controller = null
    }

    fun play(items: List<MediaItem>, startIndex: Int, shuffle: Boolean) {
        val c = controller ?: return
        c.shuffleModeEnabled = shuffle
        c.setMediaItems(items, startIndex, 0)
        c.prepare()
        c.play()
    }

    /** Puts songs right after the current one, or starts them if nothing is loaded. */
    fun playNext(items: List<MediaItem>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(items, 0, false)
        c.addMediaItems(c.currentMediaItemIndex + 1, items)
    }

    /** Adds songs to the end of the queue, or starts them if nothing is loaded. */
    fun enqueue(items: List<MediaItem>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(items, 0, false)
        c.addMediaItems(items)
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0, 0)
            c.play()
        }
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun playAt(index: Int) {
        val c = controller ?: return
        c.seekTo(index, 0)
        c.play()
    }

    fun setShuffle(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
    }

    private fun updateTicker() {
        ticker?.cancel()
        if (controller?.isPlaying != true) return
        ticker = scope.launch {
            while (isActive) {
                publish()
                delay(250)
            }
        }
    }

    private fun publishQueue() {
        val c = controller ?: return
        _queue.value = (0 until c.mediaItemCount).map { i ->
            val item = c.getMediaItemAt(i)
            QueueEntry(item.mediaId, item.mediaMetadata.title?.toString().orEmpty(), item.mediaMetadata.artist?.toString().orEmpty())
        }
    }

    private fun publish() {
        val c = controller ?: return
        val item = c.currentMediaItem
        val meta = c.mediaMetadata
        _state.value = NowPlaying(
            mediaId = item?.mediaId,
            title = meta.title?.toString().orEmpty(),
            artist = meta.artist?.toString().orEmpty(),
            album = meta.albumTitle?.toString().orEmpty(),
            artUri = meta.artworkUri,
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: 0,
            shuffle = c.shuffleModeEnabled,
            hasNext = c.hasNextMediaItem(),
            index = c.currentMediaItemIndex,
            count = c.mediaItemCount,
        )
    }
}
