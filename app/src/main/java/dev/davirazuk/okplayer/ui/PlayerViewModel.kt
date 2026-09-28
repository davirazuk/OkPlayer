package dev.davirazuk.okplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.davirazuk.okplayer.audio.OutputState
import dev.davirazuk.okplayer.data.Preferences
import dev.davirazuk.okplayer.library.Album
import dev.davirazuk.okplayer.library.LibraryRepository
import dev.davirazuk.okplayer.playback.PlayerConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LibraryState {
    data object NeedsPermission : LibraryState
    data object Loading : LibraryState
    data class Ready(val albums: List<Album>) : LibraryState
}

sealed interface Screen {
    data object Library : Screen
    data class AlbumDetail(val albumId: Long) : Screen
    data object NowPlaying : Screen
    data object Settings : Screen
}

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = LibraryRepository(app)
    val connection = PlayerConnection(app, viewModelScope)

    private val _library = MutableStateFlow<LibraryState>(LibraryState.NeedsPermission)
    val library: StateFlow<LibraryState> = _library.asStateFlow()

    private val _screen = MutableStateFlow<Screen>(Screen.Library)
    val screen: StateFlow<Screen> = _screen.asStateFlow()
    private val backStack = ArrayDeque<Screen>()

    val nowPlaying = connection.state
    val output = OutputState.status
    val favorites = Preferences.favorites
    val noSkipping = Preferences.noSkipping
    val hiResOutput = Preferences.hiResOutput

    init {
        connection.connect()
    }

    fun onPermissionGranted() {
        if (_library.value is LibraryState.Ready) return
        refresh()
    }

    fun refresh() {
        _library.value = LibraryState.Loading
        viewModelScope.launch { _library.value = LibraryState.Ready(repository.loadAlbums()) }
    }

    fun open(screen: Screen) {
        if (_screen.value == screen) return
        backStack.addLast(_screen.value)
        _screen.value = screen
    }

    /** Returns false when there's nothing to go back to. */
    fun back(): Boolean {
        val previous = backStack.removeLastOrNull() ?: return false
        _screen.value = previous
        return true
    }

    fun album(id: Long): Album? = (library.value as? LibraryState.Ready)?.albums?.firstOrNull { it.id == id }

    fun playAlbum(album: Album, startIndex: Int = 0, shuffle: Boolean = false) {
        connection.play(album.tracks.map { it.toMediaItem() }, startIndex, shuffle && !noSkipping.value)
        open(Screen.NowPlaying)
    }

    fun togglePlay() {
        connection.togglePlay()
    }

    fun next() {
        connection.next()
    }

    fun previous() {
        connection.previous()
    }

    fun seekTo(positionMs: Long) {
        connection.seekTo(positionMs)
    }

    fun setShuffle(enabled: Boolean) {
        if (enabled && noSkipping.value) return
        connection.setShuffle(enabled)
    }

    fun setNoSkipping(enabled: Boolean) {
        Preferences.setNoSkipping(enabled)
        if (enabled) connection.setShuffle(false)
    }

    fun setHiResOutput(enabled: Boolean) {
        Preferences.setHiResOutput(enabled)
    }

    fun toggleFavorite(mediaId: String) {
        Preferences.toggleFavorite(mediaId)
    }

    override fun onCleared() {
        connection.release()
    }
}
