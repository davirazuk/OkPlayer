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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.davirazuk.okplayer.ui.PlayerViewModel
import dev.davirazuk.okplayer.ui.Screen
import dev.davirazuk.okplayer.ui.screens.AlbumScreen
import dev.davirazuk.okplayer.ui.screens.LibraryScreen
import dev.davirazuk.okplayer.ui.screens.NowPlayingScreen
import dev.davirazuk.okplayer.ui.screens.SettingsScreen
import dev.davirazuk.okplayer.ui.theme.OkPlayerTheme

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
    val library by vm.library.collectAsStateWithLifecycle()
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val output by vm.output.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val noSkipping by vm.noSkipping.collectAsStateWithLifecycle()
    val hiRes by vm.hiResOutput.collectAsStateWithLifecycle()

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

    AnimatedContent(
        targetState = screen,
        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
        label = "screen",
    ) { target ->
        when (target) {
            Screen.Library -> LibraryScreen(
                state = library,
                nowPlaying = nowPlaying,
                onRequestPermission = request,
                onOpenAlbum = { vm.open(Screen.AlbumDetail(it.id)) },
                onOpenSettings = { vm.open(Screen.Settings) },
                onOpenNowPlaying = { vm.open(Screen.NowPlaying) },
                onTogglePlay = vm::togglePlay,
            )

            is Screen.AlbumDetail -> {
                val album = vm.album(target.albumId)
                if (album == null) {
                    LaunchedEffect(Unit) { vm.back() }
                } else {
                    AlbumScreen(
                        album = album,
                        nowPlaying = nowPlaying,
                        favorites = favorites,
                        noSkipping = noSkipping,
                        onBack = { vm.back() },
                        onPlay = { index, shuffle -> vm.playAlbum(album, index, shuffle) },
                    )
                }
            }

            Screen.NowPlaying -> NowPlayingScreen(
                state = nowPlaying,
                output = output,
                favorite = nowPlaying.mediaId?.let { it in favorites } == true,
                noSkipping = noSkipping,
                onBack = { vm.back() },
                onTogglePlay = vm::togglePlay,
                onNext = vm::next,
                onPrevious = vm::previous,
                onSeek = vm::seekTo,
                onShuffle = vm::setShuffle,
                onNoSkipping = vm::setNoSkipping,
                onFavorite = { nowPlaying.mediaId?.let(vm::toggleFavorite) },
            )

            Screen.Settings -> SettingsScreen(
                hiResOutput = hiRes,
                noSkipping = noSkipping,
                output = output,
                onBack = { vm.back() },
                onHiRes = vm::setHiResOutput,
                onNoSkipping = vm::setNoSkipping,
            )
        }
    }
}
