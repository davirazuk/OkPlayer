package dev.davirazuk.okplayer.playback

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dev.davirazuk.okplayer.MainActivity
import dev.davirazuk.okplayer.audio.LevelMeter
import dev.davirazuk.okplayer.audio.MeteringAudioSink
import dev.davirazuk.okplayer.audio.UsbDacRouter
import dev.davirazuk.okplayer.data.PlaybackEvents
import dev.davirazuk.okplayer.data.Preferences
import dev.davirazuk.okplayer.data.QueueStore
import dev.davirazuk.okplayer.library.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var router: UsbDacRouter
    private lateinit var queue: QueueStore
    private lateinit var library: LibraryRepository

    private var currentAudioMime: String? = null
    private var retriedMediaId: String? = null

    override fun onCreate() {
        super.onCreate()
        queue = QueueStore(this)
        library = LibraryRepository(this)

        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        router = UsbDacRouter(this, attributes.audioAttributesV21.audioAttributes)

        // Read once per service start; Options says the change applies next time.
        val floatOutput = Preferences.hiResOutput.value
        val sink = MeteringAudioSink(
            DefaultAudioSink.Builder(this).setEnableFloatOutput(floatOutput).build(),
        ) { rate, encoding -> router.onOutputPcm(rate, outputEncoding(encoding, floatOutput)) }

        val renderers = RenderersFactory { handler, _, audioListener, _, _ ->
            arrayOf<Renderer>(
                PlatformAudioRenderer(this, handler, audioListener, sink),
                FfmpegAudioRenderer(handler, audioListener, sink),
            )
        }
        val extractors = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING)

        player = ExoPlayer.Builder(this, renderers, DefaultMediaSourceFactory(this, extractors))
            .setAudioAttributes(attributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        router.start()
        player.addListener(listener)

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, NoSkipPlayer(player))
            .setSessionActivity(openApp)
            .setCallback(callback)
            .build()

        restoreQueue()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveQueue()
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        saveQueue()
        scope.cancel()
        router.stop()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private val listener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) {
            val format = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }?.getTrackFormat(0)
            currentAudioMime = format?.sampleMimeType
            router.onTrackFormat(format)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) {
                LevelMeter.reset()
                saveQueue()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            retriedMediaId = null
            saveQueue()
        }

        override fun onPlayerError(error: PlaybackException) = recover(error)
    }

    /**
     * Keeps an album going when one file misbehaves: first retry with FFmpeg if the
     * phone's decoder failed, then skip to the next song and say what happened.
     */
    private fun recover(error: PlaybackException) {
        val item = player.currentMediaItem
        val title = item?.mediaMetadata?.title?.toString() ?: "This song"
        val mime = currentAudioMime

        if (error.isDecoderError() && mime != null && mime !in DecoderPolicy.forcedBuiltIn &&
            FfmpegLibrary.isAvailable() && FfmpegLibrary.supportsFormat(mime)
        ) {
            DecoderPolicy.forcedBuiltIn += mime
            PlaybackEvents.post("The phone's decoder failed on “$title”. Switched to the built-in decoder.", isError = false)
            player.prepare()
            player.play()
            return
        }
        if (error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED && retriedMediaId != item?.mediaId) {
            // Usually the output rejecting a format; one retry lets Android pick another path.
            retriedMediaId = item?.mediaId
            player.prepare()
            player.play()
            return
        }

        PlaybackEvents.post("Couldn't play “$title”: ${error.describe()}.")
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
        }
    }

    private fun saveQueue() {
        if (!::player.isInitialized || player.mediaItemCount == 0) return
        val ids = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).mediaId.toLongOrNull() }
        if (ids.isEmpty()) return
        queue.save(ids, player.currentMediaItemIndex, player.currentPosition.coerceAtLeast(0))
    }

    private fun restoreQueue() {
        if (!canReadMusic()) return
        val saved = queue.load() ?: return
        scope.launch {
            val tracks = library.tracksById(saved.ids)
            // A controller may have started something else while we were looking.
            if (tracks.isEmpty() || player.mediaItemCount > 0) return@launch
            val index = tracks.indexOfFirst { it.id == saved.ids[saved.index] }.coerceAtLeast(0)
            player.setMediaItems(tracks.map { it.toMediaItem() }, index, saved.positionMs)
            player.prepare()
        }
    }

    private fun canReadMusic(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private val callback = object : MediaSession.Callback {
        /** Controllers send items without a playable URI; restore it from the request metadata. */
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

        /** Lets Bluetooth play buttons and the system media controls resume the last queue. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            val saved = queue.load()
            if (saved == null || !canReadMusic()) {
                result.setException(UnsupportedOperationException("Nothing to resume"))
                return result
            }
            scope.launch {
                val tracks = library.tracksById(saved.ids)
                if (tracks.isEmpty()) {
                    result.setException(UnsupportedOperationException("Saved songs are gone"))
                } else {
                    val index = tracks.indexOfFirst { it.id == saved.ids[saved.index] }.coerceAtLeast(0)
                    result.set(MediaSession.MediaItemsWithStartPosition(tracks.map { it.toMediaItem() }, index, saved.positionMs))
                }
            }
            return result
        }
    }
}

/** Formats the phone's decoders get wrong, sent to FFmpeg instead. Lives as long as the process. */
object DecoderPolicy {
    val forcedBuiltIn: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

    fun useBuiltIn(mime: String?): Boolean {
        if (mime == null || !FfmpegLibrary.isAvailable() || !FfmpegLibrary.supportsFormat(mime)) return false
        return Preferences.builtInDecoder.value || mime in forcedBuiltIn
    }
}

/** The phone's MediaCodec decoders, stepping aside for formats [DecoderPolicy] routes to FFmpeg. */
private class PlatformAudioRenderer(
    context: android.content.Context,
    handler: Handler?,
    listener: AudioRendererEventListener?,
    sink: AudioSink,
) : MediaCodecAudioRenderer(context, MediaCodecSelector.DEFAULT, handler, listener, sink) {
    override fun supportsFormat(mediaCodecSelector: MediaCodecSelector, format: Format): Int =
        if (DecoderPolicy.useBuiltIn(format.sampleMimeType)) RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE)
        else super.supportsFormat(mediaCodecSelector, format)
}

/** What DefaultAudioSink ends up writing to the audio track for a given decoder output. */
private fun outputEncoding(input: Int, floatOutput: Boolean): Int = when (input) {
    C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT ->
        if (floatOutput) C.ENCODING_PCM_FLOAT else C.ENCODING_PCM_16BIT
    else -> C.ENCODING_PCM_16BIT
}

private fun PlaybackException.isDecoderError() = errorCode in setOf(
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
)

private fun PlaybackException.describe(): String = when (errorCode) {
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    -> "no decoder for this format"
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "this file type isn't supported"
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "the file looks damaged"
    PlaybackException.ERROR_CODE_DECODING_FAILED -> "decoding failed partway, the file may be damaged"
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "the file was moved or deleted"
    PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "okplayer isn't allowed to read it"
    PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> "the audio output wouldn't open"
    else -> errorCodeName.lowercase().replace('_', ' ')
}

/**
 * Enforces "no skipping" for every controller, including the notification,
 * lock screen, headset buttons and the in-app UI.
 */
private class NoSkipPlayer(player: Player) : androidx.media3.common.ForwardingPlayer(player) {
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
