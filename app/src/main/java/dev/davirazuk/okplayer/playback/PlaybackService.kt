package dev.davirazuk.okplayer.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.davirazuk.okplayer.MainActivity
import dev.davirazuk.okplayer.audio.LevelMeter
import dev.davirazuk.okplayer.audio.UsbDacRouter
import dev.davirazuk.okplayer.data.Preferences

class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var router: UsbDacRouter

    override fun onCreate() {
        super.onCreate()

        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // Read once per service start; the settings screen says a restart applies it.
        val floatOutput = Preferences.hiResOutput.value
        val sink = DefaultAudioSink.Builder(this)
            .setEnableFloatOutput(floatOutput)
            .setAudioProcessors(arrayOf(TeeAudioProcessor(LevelMeter)))
            .build()
        val renderers = RenderersFactory { handler, _, audioListener, _, _ ->
            arrayOf<Renderer>(MediaCodecAudioRenderer(this, MediaCodecSelector.DEFAULT, handler, audioListener, sink))
        }

        val player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(attributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        router = UsbDacRouter(this, attributes.audioAttributesV21.audioAttributes, floatOutput)
        router.start()
        player.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                val audio = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
                router.onTrackFormat(audio?.getTrackFormat(0))
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) LevelMeter.reset()
            }
        })

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        session = MediaSession.Builder(this, NoSkipPlayer(player))
            .setSessionActivity(openApp)
            .setCallback(SessionCallback)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        router.stop()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    /** Controllers send items without a playable URI; restore it from the request metadata. */
    private object SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(
                mediaItems.map { item ->
                    item.requestMetadata.mediaUri?.let { item.buildUpon().setUri(it).build() } ?: item
                }.toMutableList(),
            )
    }
}

/**
 * Enforces "no skipping" for every controller, including the notification,
 * lock screen, headset buttons and the in-app UI.
 */
private class NoSkipPlayer(player: Player) : ForwardingPlayer(player) {
    private val locked get() = Preferences.noSkipping.value

    override fun seekToNext() {
        if (!locked) super.seekToNext()
    }

    override fun seekToNextMediaItem() {
        if (!locked) super.seekToNextMediaItem()
    }

    override fun seekToPrevious() {
        if (locked) wrappedPlayer.seekTo(0) else super.seekToPrevious()
    }

    override fun seekToPreviousMediaItem() {
        if (!locked) super.seekToPreviousMediaItem()
    }

    override fun seekTo(positionMs: Long) {
        if (locked && positionMs > currentPosition + SEEK_TOLERANCE_MS) return
        super.seekTo(positionMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (locked && mediaItemIndex != currentMediaItemIndex) return
        if (locked && positionMs > currentPosition + SEEK_TOLERANCE_MS) return
        super.seekTo(mediaItemIndex, positionMs)
    }

    override fun seekForward() {
        if (!locked) super.seekForward()
    }

    private companion object {
        const val SEEK_TOLERANCE_MS = 1_000L
    }
}
