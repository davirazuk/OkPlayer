# okplayer

A local music player for Android that looks and feels like Windows Media Player 12
on Windows 7, built for playing albums the way they were sequenced and for getting
them to a USB DAC untouched.

<p align="center">
  <img src="https://raw.githubusercontent.com/davirazuk/okplayer/screenshots/dev.davirazuk.okplayer_ScreenshotTest_nowPlaying.png" width="24%" alt="Now Playing: the CD deck and LCD">
  <img src="https://raw.githubusercontent.com/davirazuk/okplayer/screenshots/dev.davirazuk.okplayer_ScreenshotTest_nowPlayingLyrics.png" width="24%" alt="Synced lyrics">
  <img src="https://raw.githubusercontent.com/davirazuk/okplayer/screenshots/dev.davirazuk.okplayer_ScreenshotTest_library.png" width="24%" alt="Album library">
  <img src="https://raw.githubusercontent.com/davirazuk/okplayer/screenshots/dev.davirazuk.okplayer_ScreenshotTest_album.png" width="24%" alt="Album with ratings">
</p>
<p align="center">
  <img src="https://raw.githubusercontent.com/davirazuk/okplayer/screenshots/dev.davirazuk.okplayer_ScreenshotTest_nowPlayingEqualizer.png" width="24%" alt="Graphic equalizer">
  <img src="https://raw.githubusercontent.com/davirazuk/okplayer/screenshots/dev.davirazuk.okplayer_ScreenshotTest_artists.png" width="24%" alt="Artists">
  <img src="https://raw.githubusercontent.com/davirazuk/okplayer/screenshots/dev.davirazuk.okplayer_ScreenshotTest_nowPlayingPlayList.png" width="24%" alt="Play list">
  <img src="https://raw.githubusercontent.com/davirazuk/okplayer/screenshots/dev.davirazuk.okplayer_ScreenshotTest_options.png" width="24%" alt="Options">
</p>

<sub>Rendered from the code on every push (sample library, placeholder covers).</sub>

## Features

**Playback**
- Plays FLAC, ALAC, WAV, MP3, AAC/M4A, Ogg Vorbis, Opus, AC-3, E-AC-3, DTS and
  TrueHD from the phone's storage, gapless.
- Two decoders: the phone's own, with FFmpeg as a fallback. If the phone's decoder
  fails on a file, okplayer switches that format to FFmpeg and tries again. Options
  can force FFmpeg for everything.
- A file that still won't play gets a notice saying why, and the album moves on.
- Albums play in order. Shuffle is opt-in.
- **No skipping** locks next, previous and seeking ahead until the song ends,
  including from the notification, the lock screen and headset buttons.
- Picks up where you left off, including from a Bluetooth play button after the
  app was closed.

**Bit-perfect USB output**
- On Android 14 and newer, okplayer asks the system to open a USB DAC in
  bit-perfect mode at the sample rate and sample format the decoder produces,
  skipping Android's mixer and resampler.
- The LCD lights **USB** and **BIT-PERFECT** when that's happening. When it isn't,
  the line under the deck says why, and Options lists the rates your DAC accepts.

**The deck**
- The cover sits on a CD that spins in a recessed well while music plays.
- A backlit LCD shows track number, time, title, codec and sample rate/bit depth.
- An aurora behind it follows the bass, mids and treble of the song itself.
- Tabs switch the deck to **Lyrics** (synced, auto-scrolling) or the **Play list**.

**Library**
- Albums from the phone's media library in an Explorer-style grid. The breadcrumb
  switches between Artists, Albums, Songs and the auto playlists Recently added,
  Most played and Recently played.
- Album view with track numbers, lengths and 1–5 star ratings.
- Search box, and long-press menus to play a song or album next or add it to
  Now Playing.
- Albums without artwork get a generated cover instead of a grey square.

**Extras**
- Graphic equalizer with presets, after WMP's Enhancements panel (off by default,
  since it isn't bit-perfect).
- Sleep timer that fades out before pausing.
- The black control bar shows what's playing while you browse.

**Lyrics**
- Synced or plain lyrics from [LRCLIB](https://lrclib.net), a free and open lyrics
  database. Only the artist, title, album and length are sent, results are cached,
  and it can be turned off in Options.

## Install

Grab `app-debug.apk` from the latest [release](../../releases), or from the latest
run under **Actions → build** (artifact `okplayer-debug`).

Every build is signed with the same committed debug key, so a new build installs
over the old one and keeps your library, ratings and settings.

## Hi-res and bit-perfect

Phone speakers and Bluetooth always get standard 16-bit PCM from the phone's own
decoders, the path every Android phone handles well.

With a USB DAC connected and **Hi-res output to USB DACs** on (the default):

- FLAC and ALAC are decoded by FFmpeg, whose output is sample-exact, and 24-bit files
  reach the DAC at full resolution as 32-bit float, which carries 16- and 24-bit
  samples exactly.
- On Android 14+, okplayer asks for a bit-perfect mixer at the file's sample rate
  and format. The LCD lights **USB** and **BIT-PERFECT** when that's in effect;
  otherwise the line under the deck says why, and Options lists the rates your DAC
  accepts in bit-perfect mode.

Bit-perfect needs a phone whose audio HAL supports it; not every manufacturer enables
it.

## PS Vita version

`vita/` is a homebrew build for the PS Vita with the same look: album folders from
`ux0:music`, FLAC / MP3 / WAV / Ogg, the spinning disc and LCD, and D-pad or touch
controls. Releases include `okplayer-vita-*.vpk` for VitaShell. See
[vita/README.md](vita/README.md).

## Desktop version

`desktop/` wraps the web player in Electron as a frameless window: its Aero title
bar is the window's frame, with working minimize, maximize and close. CI builds a
Windows `.exe` (portable, no install) and a Linux AppImage; releases include both.

```
cd desktop && npm install && npm start
```

## Web version

`web/` is a browser version with the same design: add files or a folder, and they're
copied into the page's storage so they're still there next time. It installs as an
app from Chrome. It's deployed to GitHub Pages from `main` once Pages is switched on
under **Settings → Pages → Source: GitHub Actions**.

## Build

Requirements: JDK 17 and the Android SDK (API 35).

```
./gradlew assembleDebug
```

## Layout

```
app/src/main/java/dev/davirazuk/okplayer/
  audio/      USB DAC routing, the metering sink and the level meter
  data/       settings, ratings, saved queue, lyrics
  library/    MediaStore scanning, albums and tracks
  playback/   media session service, decoder fallback, UI-side controller
  ui/         Compose screens, the Aero chrome, the deck and theme
web/          browser version (installable PWA)
```

## Roadmap

- Search, artists and genres, folder browsing for files outside the media library
- Embedded and sidecar `.lrc` lyrics
- Desktop build (Windows, Linux)
- Qobuz streaming, pending API access from Qobuz

## Credits

- [Media3 / ExoPlayer](https://github.com/androidx/media) for playback
- [Jellyfin's Media3 FFmpeg build](https://github.com/jellyfin/jellyfin-androidx-media) for the FFmpeg decoders
- [LRCLIB](https://lrclib.net) for lyrics
- Icons from Material Symbols (Apache 2.0)

## License

GPL-3.0. See `LICENSE`.
