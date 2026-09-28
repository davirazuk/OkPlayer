package dev.davirazuk.okplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.davirazuk.okplayer.data.Notice
import dev.davirazuk.okplayer.ui.PlayerViewModel
import dev.davirazuk.okplayer.ui.Screen
import dev.davirazuk.okplayer.ui.components.AeroWindow
import dev.davirazuk.okplayer.ui.components.ControlBar
import dev.davirazuk.okplayer.ui.components.Crumb
import dev.davirazuk.okplayer.ui.components.MenuItem
import dev.davirazuk.okplayer.ui.components.NowPlayingInfo
import dev.davirazuk.okplayer.ui.LibraryView
import dev.davirazuk.okplayer.ui.screens.ArtistScreen
import dev.davirazuk.okplayer.ui.screens.QueueActions
import androidx.compose.runtime.remember
import dev.davirazuk.okplayer.ui.screens.AlbumScreen
import dev.davirazuk.okplayer.ui.screens.LibraryScreen
import dev.davirazuk.okplayer.ui.screens.NowPlayingScreen
import dev.davirazuk.okplayer.ui.screens.OptionsScreen
import dev.davirazuk.okplayer.ui.theme.OkPlayerTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val vm: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OkPlayerTheme { App(vm) }
        }
    }
}

private val audioPermission =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
private fun App(vm: PlayerViewModel) {
    val context = LocalContext.current
    val screen by vm.screen.collectAsStateWithLifecycle()
    val libraryState by vm.library.collectAsStateWithLifecycle()
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val queue by vm.queue.collectAsStateWithLifecycle()
    val output by vm.output.collectAsStateWithLifecycle()
    val track by vm.trackInfo.collectAsStateWithLifecycle()
    val pipeline by vm.pipeline.collectAsStateWithLifecycle()
    val ratings by vm.ratings.collectAsStateWithLifecycle()
    val noSkipping by vm.noSkipping.collectAsStateWithLifecycle()
    val hiRes by vm.hiResOutput.collectAsStateWithLifecycle()
    val builtInDecoder by vm.builtInDecoder.collectAsStateWithLifecycle()
    val onlineLyrics by vm.onlineLyrics.collectAsStateWithLifecycle()
    val deckView by vm.deckView.collectAsStateWithLifecycle()
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val libraryView by vm.libraryView.collectAsStateWithLifecycle()
    val equalizer by vm.equalizer.collectAsStateWithLifecycle()
    val playStats by vm.playStats.collectAsStateWithLifecycle()
    val sleepEndsAt by vm.sleepEndsAt.collectAsStateWithLifecycle()
    val queueActions = remember(vm) { QueueActions(playNext = vm::playNext, enqueue = vm::enqueue) }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[audioPermission] == true) vm.onPermissionGranted()
    }
    val request = {
        val wanted = buildList {
            add(audioPermission)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.launch(wanted.toTypedArray())
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED) {
            vm.onPermissionGranted()
        } else {
            request()
        }
    }

    BackHandler(enabled = screen != Screen.Library) { vm.back() }

    val views = LibraryView.entries.map { v -> MenuItem(v.label, checked = v == libraryView) { vm.setLibraryView(v) } }
    val library = Crumb("Library", onClick = { vm.setLibraryView(libraryView) })
    val crumbs = when (val s = screen) {
        Screen.Library -> listOf(library, Crumb(libraryView.label, menu = views))
        is Screen.AlbumDetail -> listOf(library, Crumb(libraryView.label, menu = views), Crumb(vm.album(s.albumId)?.title ?: "Album"))
        is Screen.ArtistDetail -> listOf(library, Crumb("Artists", menu = views), Crumb(s.artist))
        Screen.NowPlaying -> listOf(Crumb("Now Playing"))
        Screen.Options -> listOf(library, Crumb("Options"))
    }

    AeroWindow(
        title = if (nowPlaying.isEmpty) "okplayer" else "${nowPlaying.title} - okplayer",
        crumbs = crumbs,
        canGoBack = screen != Screen.Library,
        onBack = { vm.back() },
        darkPane = screen == Screen.NowPlaying,
        controlBar = {
            ControlBar(
                positionMs = nowPlaying.positionMs,
                durationMs = nowPlaying.durationMs,
                enabled = !nowPlaying.isEmpty,
                isPlaying = nowPlaying.isPlaying,
                shuffle = nowPlaying.shuffle,
                noSkipping = noSkipping,
                canGoNext = nowPlaying.hasNext && !noSkipping,
                onSeek = vm::seekTo,
                onTogglePlay = vm::togglePlay,
                onPrevious = vm::previous,
                onNext = vm::next,
                onShuffle = vm::toggleShuffle,
                onNoSkipping = { vm.setNoSkipping(!noSkipping) },
                onSwitchView = vm::switchView,
                nowPlaying = if (screen == Screen.NowPlaying || nowPlaying.isEmpty) null
                else NowPlayingInfo(nowPlaying.title, nowPlaying.artist, nowPlaying.artUri),
            )
        },
    ) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(100)) },
            label = "screen",
        ) { target ->
            when (target) {
                Screen.Library -> LibraryScreen(
                    state = libraryState,
                    view = libraryView,
                    query = query,
                    onQuery = vm::setQuery,
                    onRequestPermission = request,
                    onOpenAlbum = { vm.open(Screen.AlbumDetail(it.id)) },
                    onOpenArtist = { vm.open(Screen.ArtistDetail(it)) },
                    onPlaySong = { album, index -> vm.playAlbum(album, index) },
                    onRefresh = vm::refresh,
                    onOptions = { vm.open(Screen.Options) },
                    actions = queueActions,
                    stats = playStats,
                )

                is Screen.AlbumDetail -> {
                    val album = vm.album(target.albumId)
                    if (album == null) {
                        LaunchedEffect(Unit) { vm.back() }
                    } else {
                        AlbumScreen(
                            album = album,
                            currentId = nowPlaying.mediaId,
                            ratings = ratings,
                            noSkipping = noSkipping,
                            onPlay = { index, shuffle -> vm.playAlbum(album, index, shuffle) },
                            actions = queueActions,
                        )
                    }
                }

                is Screen.ArtistDetail -> ArtistScreen(
                    artist = target.artist,
                    albums = vm.artistAlbums(target.artist),
                    noSkipping = noSkipping,
                    onOpenAlbum = { vm.open(Screen.AlbumDetail(it.id)) },
                    onPlayAll = { shuffle -> vm.playArtist(target.artist, shuffle) },
                    onPlayAlbum = { vm.playAlbum(it) },
                    actions = queueActions,
                )

                Screen.NowPlaying -> NowPlayingScreen(
                    state = nowPlaying,
                    track = track,
                    output = output,
                    rating = nowPlaying.mediaId?.let { ratings[it] } ?: 0,
                    noSkipping = noSkipping,
                    deckView = deckView,
                    lyrics = lyrics,
                    queue = queue,
                    onlineLyrics = onlineLyrics,
                    equalizer = equalizer,
                    equalizerPresets = vm.equalizerPresets,
                    onRate = { stars -> nowPlaying.mediaId?.let { vm.rate(it, stars) } },
                    onShowDeck = vm::showDeck,
                    onPlayAt = vm::playAt,
                    onEqualizer = vm::setEqualizer,
                    onEqualizerBand = vm::setEqualizerBand,
                    onEqualizerPreset = vm::applyEqualizerPreset,
                    sleepEndsAt = sleepEndsAt,
                    onSleep = vm::setSleepTimer,
                )

                Screen.Options -> OptionsScreen(
                    hiResOutput = hiRes,
                    noSkipping = noSkipping,
                    builtInDecoder = builtInDecoder,
                    onlineLyrics = onlineLyrics,
                    output = output,
                    track = track,
                    pipeline = pipeline,
                    onHiRes = vm::setHiResOutput,
                    onNoSkipping = vm::setNoSkipping,
                    onBuiltInDecoder = vm::setBuiltInDecoder,
                    onOnlineLyrics = vm::setOnlineLyrics,
                )
            }
        }
        notice?.let { NoticeBalloon(it, onDismiss = { vm.dismissNotice(it.id) }) }
    }
}

/** A Windows 7 notification balloon along the bottom of the pane. */
@Composable
private fun BoxScope.NoticeBalloon(notice: Notice, onDismiss: () -> Unit) {
    LaunchedEffect(notice.id) {
        delay(if (notice.isError) 7000 else 4500)
        onDismiss()
    }
    Text(
        notice.text,
        fontSize = 12.5.sp,
        color = Color.Black,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(10.dp)
            .fillMaxWidth()
            .shadow(3.dp)
            .background(Color(0xFFFFFFE1))
            .border(1.dp, Color(0xFF767676))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}
