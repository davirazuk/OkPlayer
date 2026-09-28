package dev.davirazuk.okplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.davirazuk.okplayer.audio.EqualizerControl
import dev.davirazuk.okplayer.audio.OutputState
import dev.davirazuk.okplayer.data.Lyrics
import dev.davirazuk.okplayer.data.LyricsRepository
import dev.davirazuk.okplayer.data.PlaybackEvents
import dev.davirazuk.okplayer.data.Preferences
import dev.davirazuk.okplayer.library.Album
import dev.davirazuk.okplayer.library.LibraryRepository
import dev.davirazuk.okplayer.playback.PlayerConnection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed interface LibraryState {
    data object NeedsPermission : LibraryState
    data object Loading : LibraryState
    data class Ready(val albums: List<Album>) : LibraryState
}

/** How the library is browsed, as in WMP's Artist / Album / Songs views. */
enum class LibraryView(val label: String) { Artists("Artists"), Albums("Albums"), Songs("Songs") }

sealed interface Screen {
    data object Library : Screen
    data class AlbumDetail(val albumId: Long) : Screen
    data class ArtistDetail(val artist: String) : Screen
    data object NowPlaying : Screen
    data object Options : Screen
}

/** What fills the top of the Now Playing pane. */
enum class DeckView { Disc, Lyrics, PlayList, Equalizer }

sealed interface LyricsState {
    data object None : LyricsState
    data object Searching : LyricsState
    data class Found(val lyrics: Lyrics) : LyricsState
}

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = LibraryRepository(app)
    private val lyricsRepository = LyricsRepository(app)
    val connection = PlayerConnection(app, viewModelScope)

    private val _library = MutableStateFlow<LibraryState>(LibraryState.NeedsPermission)
    val library: StateFlow<LibraryState> = _library.asStateFlow()

    private val _screen = MutableStateFlow<Screen>(Screen.Library)
    val screen: StateFlow<Screen> = _screen.asStateFlow()
    private val backStack = ArrayDeque<Screen>()

    private val _deckView = MutableStateFlow(DeckView.Disc)
    val deckView: StateFlow<DeckView> = _deckView.asStateFlow()

    private val _libraryView = MutableStateFlow(LibraryView.Albums)
    val libraryView: StateFlow<LibraryView> = _libraryView.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _lyrics = MutableStateFlow<LyricsState>(LyricsState.None)
    val lyrics: StateFlow<LyricsState> = _lyrics.asStateFlow()

    val nowPlaying = connection.state
    val queue = connection.queue
    val output = OutputState.status
    val trackInfo = OutputState.track
    val pipeline = OutputState.pipeline
    val equalizer = EqualizerControl.state
    val equalizerPresets = EqualizerControl.presets.keys.toList()
    val notice = PlaybackEvents.notice
    val ratings = Preferences.ratings
    val noSkipping = Preferences.noSkipping
    val hiResOutput = Preferences.hiResOutput
    val builtInDecoder = Preferences.builtInDecoder
    val onlineLyrics = Preferences.onlineLyrics

    init {
        connection.connect()
        watchLyrics()
    }

    private data class LyricsKey(val id: String?, val artist: String, val title: String, val album: String, val durationMs: Long)

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun watchLyrics() = viewModelScope.launch {
        nowPlaying
            // Duration arrives a moment after the song changes; wait for it so lookups match.
            .map { LyricsKey(it.mediaId, it.artist, it.title, it.album, if (it.durationMs > 0) it.durationMs else 0) }
            .distinctUntilChanged()
            .collectLatest { key ->
                if (key.id == null) {
                    _lyrics.value = LyricsState.None
                    return@collectLatest
                }
                if (key.durationMs == 0L) {
                    _lyrics.value = LyricsState.Searching
                    return@collectLatest
                }
                _lyrics.value = LyricsState.Searching
                val found = lyricsRepository.find(key.id, key.artist, key.title, key.album, key.durationMs)
                _lyrics.value = found?.let { LyricsState.Found(it) } ?: LyricsState.None
            }
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

    /** The view switch button: Now Playing and wherever you were before it. */
    fun switchView() {
        if (_screen.value == Screen.NowPlaying) {
            if (!back()) _screen.value = Screen.Library
        } else {
            open(Screen.NowPlaying)
        }
    }

    fun showDeck(view: DeckView) {
        _deckView.value = if (_deckView.value == view) DeckView.Disc else view
    }

    fun album(id: Long): Album? = (library.value as? LibraryState.Ready)?.albums?.firstOrNull { it.id == id }

    fun playAlbum(album: Album, startIndex: Int = 0, shuffle: Boolean = false) {
        connection.play(album.tracks.map { it.toMediaItem() }, startIndex, shuffle && !noSkipping.value)
        _deckView.value = DeckView.Disc
        open(Screen.NowPlaying)
    }

    fun setLibraryView(view: LibraryView) {
        _libraryView.value = view
        backStack.clear()
        _screen.value = Screen.Library
    }

    fun setQuery(text: String) {
        _query.value = text
    }

    fun artistAlbums(artist: String): List<Album> =
        (library.value as? LibraryState.Ready)?.albums?.filter { it.artist == artist }.orEmpty()

    fun playArtist(artist: String, shuffle: Boolean) {
        val tracks = artistAlbums(artist).flatMap { it.tracks }
        if (tracks.isEmpty()) return
        connection.play(tracks.map { it.toMediaItem() }, 0, shuffle && !noSkipping.value)
        _deckView.value = DeckView.Disc
        open(Screen.NowPlaying)
    }

    fun playAt(index: Int) {
        if (noSkipping.value) {
            PlaybackEvents.post("No skipping is on. Finish this song first.", isError = false)
            return
        }
        connection.playAt(index)
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

    fun toggleShuffle() {
        if (noSkipping.value) return
        connection.setShuffle(!nowPlaying.value.shuffle)
    }

    fun setNoSkipping(enabled: Boolean) {
        Preferences.setNoSkipping(enabled)
        if (enabled) connection.setShuffle(false)
    }

    fun setHiResOutput(enabled: Boolean) {
        Preferences.setHiResOutput(enabled)
    }

    fun setBuiltInDecoder(enabled: Boolean) {
        Preferences.setBuiltInDecoder(enabled)
    }

    fun setOnlineLyrics(enabled: Boolean) {
        Preferences.setOnlineLyrics(enabled)
    }

    fun rate(mediaId: String, stars: Int) {
        Preferences.rate(mediaId, stars)
    }

    fun setEqualizer(enabled: Boolean) = EqualizerControl.setEnabled(enabled)

    fun setEqualizerBand(index: Int, levelMb: Int) = EqualizerControl.setBand(index, levelMb)

    fun applyEqualizerPreset(name: String) = EqualizerControl.applyPreset(name)

    fun dismissNotice(id: Long) {
        PlaybackEvents.dismiss(id)
    }

    override fun onCleared() {
        connection.release()
    }
}
