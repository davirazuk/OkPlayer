package dev.davirazuk.okplayer

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.BoxScope
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.media3.common.C
import dev.davirazuk.okplayer.audio.EqBand
import dev.davirazuk.okplayer.audio.EqState
import dev.davirazuk.okplayer.audio.OutputStatus
import dev.davirazuk.okplayer.audio.Pipeline
import dev.davirazuk.okplayer.audio.TrackInfo
import dev.davirazuk.okplayer.data.LyricLine
import dev.davirazuk.okplayer.data.PlayStat
import dev.davirazuk.okplayer.data.Lyrics
import dev.davirazuk.okplayer.library.Album
import dev.davirazuk.okplayer.library.Track
import dev.davirazuk.okplayer.playback.NowPlaying
import dev.davirazuk.okplayer.playback.QueueEntry
import dev.davirazuk.okplayer.ui.DeckView
import dev.davirazuk.okplayer.ui.LibraryState
import dev.davirazuk.okplayer.ui.LyricsState
import dev.davirazuk.okplayer.ui.components.AeroWindow
import dev.davirazuk.okplayer.ui.components.ControlBar
import dev.davirazuk.okplayer.ui.components.Crumb
import dev.davirazuk.okplayer.ui.components.MenuItem
import dev.davirazuk.okplayer.ui.components.NowPlayingInfo
import dev.davirazuk.okplayer.ui.LibraryView
import dev.davirazuk.okplayer.ui.screens.ArtistScreen
import dev.davirazuk.okplayer.ui.screens.AlbumScreen
import dev.davirazuk.okplayer.ui.screens.LibraryScreen
import dev.davirazuk.okplayer.ui.screens.NowPlayingScreen
import dev.davirazuk.okplayer.ui.screens.OptionsScreen
import dev.davirazuk.okplayer.ui.theme.OkPlayerTheme
import dev.davirazuk.okplayer.ui.theme.Palette
import dev.davirazuk.okplayer.ui.components.SevenControls
import dev.davirazuk.okplayer.ui.components.StartMenuPanel
import dev.davirazuk.okplayer.ui.components.Taskbar
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.junit.Rule
import org.junit.Test

/**
 * Renders every screen with sample data so design changes can be reviewed as images.
 * `./gradlew recordPaparazziDebug` writes them to app/src/test/snapshots/images.
 */
class ScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.NoActionBar")

    private val okComputer = listOf(
        "Airbag" to 284, "Paranoid Android" to 383, "Subterranean Homesick Alien" to 267, "Exit Music (For a Film)" to 264,
        "Let Down" to 299, "Karma Police" to 261, "Fitter Happier" to 117, "Electioneering" to 230,
        "Climbing Up the Walls" to 285, "No Surprises" to 228, "Lucky" to 259, "The Tourist" to 324,
    )

    private fun album(id: Long, title: String, artist: String, year: Int, songs: List<Pair<String, Int>>) = Album(
        id = id, title = title, artist = artist, year = year,
        tracks = songs.mapIndexed { i, (name, secs) ->
            Track(id * 100 + i, Uri.EMPTY, name, artist, title, id, artist, i + 1, 1, secs * 1000L, year, "audio/flac")
        },
    )

    private val albums = listOf(
        album(1, "OK Computer", "Radiohead", 1997, okComputer),
        album(2, "Is This It", "The Strokes", 2001, listOf("Is This It" to 155, "The Modern Age" to 212, "Soma" to 157)),
        album(3, "American Football", "American Football", 1999, listOf("Never Meant" to 268, "The Summer Ends" to 287)),
        album(4, "Pablo Honey", "Radiohead", 1993, listOf("You" to 208, "Creep" to 238)),
        album(5, "Natural", "Parannoul", 2023, listOf("Arrival" to 300)),
        album(6, "Ghost", "Panchiko", 2020, listOf("Ghost" to 250)),
    )

    private val playing = NowPlaying(
        mediaId = "101", title = "Paranoid Android", artist = "Radiohead", album = "OK Computer",
        isPlaying = true, positionMs = 151_000, durationMs = 383_000, hasNext = true, index = 1, count = 12,
    )

    private val usb = OutputStatus.Usb("FiiO KA3", 44_100, true, null, listOf(44_100, 48_000, 88_200, 96_000, 176_400, 192_000))
    private val flac = TrackInfo("FLAC", 44_100, 16, 2, 1_010_000, lossless = true)

    private val lyrics = LyricsState.Found(
        Lyrics(
            listOf(
                LyricLine(140_000, "Please could you stop the noise?"),
                LyricLine(146_000, "I'm trying to get some rest"),
                LyricLine(150_000, "From all the unborn chicken voices"),
                LyricLine(156_000, "In my head"),
                LyricLine(162_000, "What's that?"),
                LyricLine(165_000, "I may be paranoid, but not an android"),
            ),
            synced = true,
        ),
    )

    @Composable
    private fun Window(title: String, crumbs: List<Crumb>, dark: Boolean, state: NowPlaying = playing, content: @Composable BoxScope.() -> Unit) {
        OkPlayerTheme {
            AeroWindow(
                title = title,
                crumbs = crumbs,
                canGoBack = crumbs.size > 1 || dark,
                onBack = {},
                darkPane = dark,
                controlBar = {
                    ControlBar(
                        positionMs = state.positionMs, durationMs = state.durationMs, enabled = !state.isEmpty,
                        isPlaying = state.isPlaying, shuffle = false, noSkipping = false, canGoNext = true,
                        onSeek = {}, onTogglePlay = {}, onPrevious = {}, onNext = {}, onShuffle = {}, onNoSkipping = {}, onSwitchView = {},
                        nowPlaying = if (dark || state.isEmpty) null else NowPlayingInfo(state.title, state.artist, null),
                    )
                },
                content = content,
            )
        }
    }

    @Composable
    private fun NowPlayingIn(view: DeckView) = Window("Paranoid Android - okplayer", listOf(Crumb("Now Playing")), dark = true) {
        NowPlayingScreen(
            state = playing, track = flac, output = usb, rating = 5, noSkipping = false, deckView = view,
            lyrics = lyrics, onlineLyrics = true,
            queue = okComputer.mapIndexed { i, (t, _) -> QueueEntry("${100 + i}", t, "Radiohead") },
            equalizer = eq, equalizerPresets = listOf("Flat", "Rock", "Custom"),
            onRate = {}, onShowDeck = {}, onPlayAt = {}, onEqualizer = {}, onEqualizerBand = { _, _ -> }, onEqualizerPreset = {},
        )
    }

    @Test
    fun nowPlaying() = paparazzi.snapshot { NowPlayingIn(DeckView.Disc) }

    /* ---------- the Seven skin ---------- */

    @Composable
    private fun SevenWindow(nowPlaying: Boolean, state: NowPlaying = playing, content: @Composable BoxScope.() -> Unit) {
        OkPlayerTheme {
            AeroWindow(
                title = if (state.isEmpty) "okplayer" else "${state.title} - okplayer",
                crumbs = if (nowPlaying) listOf(Crumb("Now Playing")) else listOf(Crumb("Library"), Crumb("Albums", menu = listOf(MenuItem("Albums") {}))),
                canGoBack = false,
                onBack = {},
                darkPane = nowPlaying,
                darkColor = Palette.Navy,
                showToolbar = !nowPlaying,
                taskbar = {
                    Taskbar(
                        nowPlayingTitle = if (state.isEmpty) null else state.title, onNowPlaying = nowPlaying, onLibrary = {}, onOpenNowPlaying = {},
                        startOpen = false, onStart = {}, onStartDismiss = {}, usbConnected = true, onUsb = {}, sleeping = true, onSleep = {},
                    ) {}
                },
                controlBar = {
                    SevenControls(
                        compact = !nowPlaying, info = if (state.isEmpty) null else NowPlayingInfo(state.title, state.artist, null),
                        positionMs = state.positionMs, durationMs = state.durationMs, enabled = !state.isEmpty, isPlaying = state.isPlaying,
                        shuffle = true, noSkipping = false, canGoNext = true,
                        onSeek = {}, onTogglePlay = {}, onPrevious = {}, onNext = {}, onShuffle = {}, onNoSkipping = {}, onOpenNowPlaying = {},
                    )
                },
                content = content,
            )
        }
    }

    @Composable
    private fun SevenNowPlaying(view: DeckView, state: NowPlaying = playing) = SevenWindow(nowPlaying = true, state = state) {
        NowPlayingScreen(
            state = state, track = if (state.isEmpty) null else flac, output = if (state.isEmpty) OutputStatus.Idle else usb, rating = 4,
            noSkipping = false, deckView = view, lyrics = lyrics, onlineLyrics = true,
            queue = okComputer.mapIndexed { i, (t, _) -> QueueEntry("${100 + i}", t, "Radiohead") },
            equalizer = eq, equalizerPresets = listOf("Flat", "Rock", "Custom"),
            onRate = {}, onShowDeck = {}, onPlayAt = {}, onEqualizer = {}, onEqualizerBand = { _, _ -> }, onEqualizerPreset = {},
            sleepEndsAt = System.currentTimeMillis() + 29 * 60_000L, seven = true,
        )
    }

    @Test
    fun sevenNowPlaying() = paparazzi.snapshot { SevenNowPlaying(DeckView.Disc) }

    @Test
    fun sevenLyrics() = paparazzi.snapshot { SevenNowPlaying(DeckView.Lyrics) }

    @Test
    fun sevenEmpty() = paparazzi.snapshot { SevenNowPlaying(DeckView.Disc, NowPlaying()) }

    @Test
    fun sevenLibrary() = paparazzi.snapshot {
        SevenWindow(nowPlaying = false) {
            LibraryScreen(LibraryState.Ready(albums), LibraryView.Albums, "", {}, onRequestPermission = {}, onOpenAlbum = {}, onOpenArtist = {}, onPlaySong = { _, _ -> }, onRefresh = {}, onOptions = {})
        }
    }

    /** The Start menu, drawn in place over the deck since popups don't render here. */
    @Composable
    private fun StartMenuOver(query: String) = SevenWindow(nowPlaying = true) {
        NowPlayingScreen(
            state = playing, track = flac, output = usb, rating = 4, noSkipping = false, deckView = DeckView.Disc, lyrics = lyrics, onlineLyrics = true,
            queue = emptyList(), equalizer = eq, equalizerPresets = emptyList(),
            onRate = {}, onShowDeck = {}, onPlayAt = {}, onEqualizer = {}, onEqualizerBand = { _, _ -> }, onEqualizerPreset = {}, seven = true,
        )
        StartMenuPanel(
            albums = albums, playingTitle = playing.title, playingArtist = playing.artist, playingArt = null, playingAlbum = playing.album,
            sleepEndsAt = null, onNowPlaying = {}, onView = {}, onOpenAlbum = {}, onOpenArtist = {}, onPlaySongs = { _, _ -> }, onDeck = {},
            onSleep = {}, onSkin = {}, onOptions = {}, onRefresh = {}, onStop = {}, onDismiss = {},
            modifier = Modifier.align(Alignment.BottomStart), initialQuery = query,
        )
    }

    @Test
    fun sevenStartMenu() = paparazzi.snapshot { StartMenuOver("") }

    @Test
    fun sevenStartSearch() = paparazzi.snapshot { StartMenuOver("radio") }

    @Test
    fun nowPlayingLyrics() = paparazzi.snapshot { NowPlayingIn(DeckView.Lyrics) }

    private val eq = EqState(
        available = true, enabled = true, preset = "Rock",
        bands = listOf(EqBand(60, 600), EqBand(230, 300), EqBand(910, -240), EqBand(3600, 300), EqBand(14000, 660)),
    )

    @Test
    fun nowPlayingEqualizer() = paparazzi.snapshot { NowPlayingIn(DeckView.Equalizer) }

    @Test
    fun nowPlayingPlayList() = paparazzi.snapshot { NowPlayingIn(DeckView.PlayList) }

    @Test
    fun nowPlayingEmpty() = paparazzi.snapshot {
        Window("okplayer", listOf(Crumb("Now Playing")), dark = true, state = NowPlaying()) {
            NowPlayingScreen(
                state = NowPlaying(), track = null, output = OutputStatus.Idle, rating = 0, noSkipping = false,
                deckView = DeckView.Disc, lyrics = LyricsState.None, onlineLyrics = true, queue = emptyList(),
                equalizer = EqState(), equalizerPresets = emptyList(),
                onRate = {}, onShowDeck = {}, onPlayAt = {}, onEqualizer = {}, onEqualizerBand = { _, _ -> }, onEqualizerPreset = {},
            )
        }
    }

    @Test
    fun library() = paparazzi.snapshot {
        Window("Paranoid Android - okplayer", listOf(Crumb("Library"), Crumb("Albums")), dark = false) {
            LibraryScreen(LibraryState.Ready(albums), LibraryView.Albums, "", {}, onRequestPermission = {}, onOpenAlbum = {}, onOpenArtist = {}, onPlaySong = { _, _ -> }, onRefresh = {}, onOptions = {})
        }
    }

    @Test
    fun album() = paparazzi.snapshot {
        Window("Paranoid Android - okplayer", listOf(Crumb("Library"), Crumb("Albums"), Crumb("OK Computer")), dark = false) {
            AlbumScreen(albums[0], currentId = "101", ratings = mapOf("100" to 4, "101" to 5, "104" to 5), noSkipping = false, onPlay = { _, _ -> })
        }
    }

    @Test
    fun options() = paparazzi.snapshot {
        Window("okplayer", listOf(Crumb("Library"), Crumb("Options")), dark = false) {
            OptionsScreen(
                hiResOutput = true, noSkipping = false, builtInDecoder = false, onlineLyrics = true, output = usb, track = flac,
                pipeline = Pipeline("ffmpeg6.0-flac", 44_100, C.ENCODING_PCM_FLOAT),
                onHiRes = {}, onNoSkipping = {}, onBuiltInDecoder = {}, onOnlineLyrics = {},
            )
        }
    }

    @Test
    fun artists() = paparazzi.snapshot {
        Window("okplayer", listOf(Crumb("Library"), Crumb("Artists", menu = listOf(MenuItem("Artists") {}))), dark = false) {
            LibraryScreen(LibraryState.Ready(albums), LibraryView.Artists, "", {}, onRequestPermission = {}, onOpenAlbum = {}, onOpenArtist = {}, onPlaySong = { _, _ -> }, onRefresh = {}, onOptions = {})
        }
    }

    @Test
    fun songs() = paparazzi.snapshot {
        Window("okplayer", listOf(Crumb("Library"), Crumb("Songs", menu = listOf(MenuItem("Songs") {}))), dark = false) {
            LibraryScreen(LibraryState.Ready(albums), LibraryView.Songs, "", {}, onRequestPermission = {}, onOpenAlbum = {}, onOpenArtist = {}, onPlaySong = { _, _ -> }, onRefresh = {}, onOptions = {})
        }
    }

    @Test
    fun mostPlayed() = paparazzi.snapshot {
        Window("okplayer", listOf(Crumb("Library"), Crumb("Most played", menu = listOf(MenuItem("Most played") {}))), dark = false) {
            LibraryScreen(
                LibraryState.Ready(albums), LibraryView.MostPlayed, "", {}, onRequestPermission = {}, onOpenAlbum = {}, onOpenArtist = {},
                onPlaySong = { _, _ -> }, onRefresh = {}, onOptions = {},
                stats = mapOf("101" to PlayStat(42, 0), "104" to PlayStat(17, 0), "200" to PlayStat(9, 0), "301" to PlayStat(4, 0)),
            )
        }
    }

    @Test
    fun artist() = paparazzi.snapshot {
        Window("okplayer", listOf(Crumb("Library"), Crumb("Artists"), Crumb("Radiohead")), dark = false) {
            ArtistScreen("Radiohead", albums.filter { it.artist == "Radiohead" }, noSkipping = false, onOpenAlbum = {}, onPlayAll = {})
        }
    }

    @Test
    fun search() = paparazzi.snapshot {
        Window("okplayer", listOf(Crumb("Library"), Crumb("Albums")), dark = false) {
            LibraryScreen(LibraryState.Ready(albums), LibraryView.Albums, "radio", {}, onRequestPermission = {}, onOpenAlbum = {}, onOpenArtist = {}, onPlaySong = { _, _ -> }, onRefresh = {}, onOptions = {})
        }
    }

    @Test
    fun needsPermission() = paparazzi.snapshot {
        Window("okplayer", listOf(Crumb("Library"), Crumb("Albums")), dark = false, state = NowPlaying()) {
            LibraryScreen(LibraryState.NeedsPermission, LibraryView.Albums, "", {}, onRequestPermission = {}, onOpenAlbum = {}, onOpenArtist = {}, onPlaySong = { _, _ -> }, onRefresh = {}, onOptions = {})
        }
    }
}
