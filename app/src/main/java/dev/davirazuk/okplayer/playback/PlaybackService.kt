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
import androidx.media3.common.MimeTypes
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
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dev.davirazuk.okplayer.MainActivity
import dev.davirazuk.okplayer.R
import dev.davirazuk.okplayer.audio.EqualizerControl
import dev.davirazuk.okplayer.audio.LevelMeter
import dev.davirazuk.okplayer.audio.MeteringAudioSink
import dev.davirazuk.okplayer.audio.OutputState
import dev.davirazuk.okplayer.audio.UsbDacRouter
import dev.davirazuk.okplayer.data.PlayStats
import dev.davirazuk.okplayer.data.PlaybackEvents
import dev.davirazuk.okplayer.data.SleepTimer
import dev.davirazuk.okplayer.data.Preferences
import dev.davirazuk.okplayer.data.QueueStore
import androidx.glance.appwidget.updateAll
import dev.davirazuk.okplayer.library.LibraryRepository
import dev.davirazuk.okplayer.widget.NowPlayingWidget
import dev.davirazuk.okplayer.widget.WidgetSnapshot
import dev.davirazuk.okplayer.widget.WidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val FADE_MS = 10_000L

class PlaybackService : MediaSessionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var router: UsbDacRouter
    private lateinit var queue: QueueStore
    private lateinit var library: LibraryRepository

    private var currentAudioMime: String? = null
    private var retriedMediaId: String? = null
    private var lastMediaId: String? = null

    override fun onCreate() {
        super.onCreate()
        queue = QueueStore(this)
        library = LibraryRepository(this)

        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        router = UsbDacRouter(this, attributes.audioAttributesV21.audioAttributes)

        // Float output only while a USB DAC is connected with hi-res on; see MeteringAudioSink.
        val hiResToDac = { Preferences.hiResOutput.value && router.usbConnected }
        DecoderPolicy.hiResToDac = hiResToDac
        val sink = MeteringAudioSink(
            DefaultAudioSink.Builder(this).setEnableFloatOutput(true).build(),
            floatAllowed = hiResToDac,
        ) { rate, encoding ->
            OutputState.setPcm(rate, encoding)
            router.onOutputPcm(rate, outputEncoding(encoding))
        }

        // Phone decoders never get asked for float: some (Samsung's among them) say they
        // output float but hand back 16-bit samples, which plays twice as fast and distorted.
        val phoneSink = NoFloatAudioSink(sink)
        val renderers = RenderersFactory { handler, _, audioListener, _, _ ->
            arrayOf<Renderer>(
                PlatformAudioRenderer(this, handler, audioListener, phoneSink),
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
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) = OutputState.setDecoder(decoderName)

            override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) =
                EqualizerControl.attach(audioSessionId)
        })
        EqualizerControl.attach(player.audioSessionId)

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_stat_okplayer) },
        )

        session = MediaSession.Builder(this, NoSkipPlayer(player))
            .setSessionActivity(openApp)
            .setCallback(callback)
            .build()

        restoreQueue()
        runSleepTimer()
    }

    /** Fades out over the last ten seconds, then pauses and restores the volume. */
    private fun runSleepTimer() = scope.launch {
        SleepTimer.endsAt.collectLatest { end ->
            player.volume = 1f
            if (end == null) return@collectLatest
            while (true) {
                val left = end - System.currentTimeMillis()
                if (left <= 0) {
                    player.pause()
                    player.volume = 1f
                    SleepTimer.cancel()
                    return@collectLatest
                }
                if (left < FADE_MS) player.volume = left.toFloat() / FADE_MS
                delay(if (left < FADE_MS) 100L else 1000L)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveQueue()
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        saveQueue()
        scope.cancel()
        EqualizerControl.release()
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
            // A song that ran to its end counts as played.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) lastMediaId?.let(PlayStats::record)
            lastMediaId = mediaItem?.mediaId
            retriedMediaId = null
            saveQueue()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) player.currentMediaItem?.mediaId?.let(PlayStats::record)
        }

        override fun onPlayerError(error: PlaybackException) = recover(error)

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                )
            ) {
                updateWidget()
            }
        }
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

    private fun updateWidget() {
        val m = player.mediaMetadata
        WidgetState.save(
            this,
            WidgetSnapshot(m.title?.toString().orEmpty(), m.artist?.toString().orEmpty(), m.artworkUri?.toString(), player.isPlaying),
        )
        scope.launch { runCatching { NowPlayingWidget().updateAll(this@PlaybackService) } }
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

/**
 * Decides which formats skip the phone's decoders for FFmpeg:
 * everything when the user asks for it, lossless formats while hi-res goes to a USB DAC
 * (FFmpeg's FLAC and ALAC output is exact, and phone decoders vary in float mode), and
 * any format whose phone decoder has already failed this session.
 */
object DecoderPolicy {
    val forcedBuiltIn: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

    @Volatile
    var hiResToDac: () -> Boolean = { false }

    private val lossless = setOf(MimeTypes.AUDIO_FLAC, MimeTypes.AUDIO_ALAC)

    fun useBuiltIn(mime: String?): Boolean {
        if (mime == null || !FfmpegLibrary.isAvailable() || !FfmpegLibrary.supportsFormat(mime)) return false
        return Preferences.builtInDecoder.value || mime in forcedBuiltIn || (mime in lossless && hiResToDac())
    }
}

/** Shares the real sink but tells the phone's decoders float PCM would need converting. */
private class NoFloatAudioSink(sink: AudioSink) : ForwardingAudioSink(sink) {
    override fun getFormatSupport(format: Format): Int =
        if (format.sampleMimeType == MimeTypes.AUDIO_RAW && format.pcmEncoding == C.ENCODING_PCM_FLOAT) {
            AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING
        } else {
            super.getFormatSupport(format)
        }

    override fun supportsFormat(format: Format): Boolean = getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED
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

/** What DefaultAudioSink (float output enabled) writes to the audio track for a decoder output. */
private fun outputEncoding(input: Int): Int = when (input) {
    C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> C.ENCODING_PCM_FLOAT
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
        if (locked) return
        countIfMostlyHeard()
        super.seekToNext()
    }

    /** Skipping a song after hearing most of it still counts as a play. */
    private fun countIfMostlyHeard() {
        val d = duration
        if (d > 0 && currentPosition >= d * 0.6) currentMediaItem?.mediaId?.let(PlayStats::record)
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
