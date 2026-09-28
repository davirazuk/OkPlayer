package dev.davirazuk.okplayer

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.BoxScope
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import dev.davirazuk.okplayer.audio.OutputStatus
import dev.davirazuk.okplayer.audio.TrackInfo
import dev.davirazuk.okplayer.data.LyricLine
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
import dev.davirazuk.okplayer.ui.screens.AlbumScreen
import dev.davirazuk.okplayer.ui.screens.LibraryScreen
import dev.davirazuk.okplayer.ui.screens.NowPlayingScreen
import dev.davirazuk.okplayer.ui.screens.OptionsScreen
import dev.davirazuk.okplayer.ui.theme.OkPlayerTheme
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
            onRate = {}, onShowDeck = {}, onPlayAt = {},
        )
    }

    @Test
    fun nowPlaying() = paparazzi.snapshot { NowPlayingIn(DeckView.Disc) }

    @Test
    fun nowPlayingLyrics() = paparazzi.snapshot { NowPlayingIn(DeckView.Lyrics) }

    @Test
    fun nowPlayingPlayList() = paparazzi.snapshot { NowPlayingIn(DeckView.PlayList) }

    @Test
    fun nowPlayingEmpty() = paparazzi.snapshot {
        Window("okplayer", listOf(Crumb("Now Playing")), dark = true, state = NowPlaying()) {
            NowPlayingScreen(
                state = NowPlaying(), track = null, output = OutputStatus.Idle, rating = 0, noSkipping = false,
                deckView = DeckView.Disc, lyrics = LyricsState.None, onlineLyrics = true, queue = emptyList(),
                onRate = {}, onShowDeck = {}, onPlayAt = {},
            )
        }
    }

    @Test
    fun library() = paparazzi.snapshot {
        Window("Paranoid Android - okplayer", listOf(Crumb("Library"), Crumb("Albums")), dark = false) {
            LibraryScreen(LibraryState.Ready(albums), onRequestPermission = {}, onOpenAlbum = {}, onRefresh = {}, onOptions = {})
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
                onHiRes = {}, onNoSkipping = {}, onBuiltInDecoder = {}, onOnlineLyrics = {},
            )
        }
    }

    @Test
    fun needsPermission() = paparazzi.snapshot {
        Window("okplayer", listOf(Crumb("Library"), Crumb("Albums")), dark = false, state = NowPlaying()) {
            LibraryScreen(LibraryState.NeedsPermission, onRequestPermission = {}, onOpenAlbum = {}, onRefresh = {}, onOptions = {})
        }
    }
}
