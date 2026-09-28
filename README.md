# okplayer

A local music player for Android that looks and feels like Windows Media Player 12
on Windows 7, built for playing albums the way they were sequenced and for getting
them to a USB DAC untouched.

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
- Albums from the phone's media library, with covers, in an Explorer-style grid.
- Album view with track numbers, lengths and 1–5 star ratings.

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

| Source | Hi-res output off | Hi-res output on (default) |
| --- | --- | --- |
| 16-bit | bit-perfect if the DAC takes 16-bit at that rate | bit-perfect if the DAC takes float at that rate |
| 24-bit | reduced to 16-bit | bit-perfect if the DAC takes float at that rate |

32-bit float carries 16- and 24-bit samples exactly, so the float path is still
bit-perfect. Hi-res output takes effect the next time playback starts.

Bit-perfect needs Android 14+ and a phone whose audio HAL supports it; not every
manufacturer enables it. When it's unavailable the reason is shown.

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
- PS Vita homebrew version (VitaSDK) for local FLAC/MP3 with covers
- Qobuz streaming, pending API access from Qobuz

## Credits

- [Media3 / ExoPlayer](https://github.com/androidx/media) for playback
- [Jellyfin's Media3 FFmpeg build](https://github.com/jellyfin/jellyfin-androidx-media) for the FFmpeg decoders
- [LRCLIB](https://lrclib.net) for lyrics
- Icons from Material Symbols (Apache 2.0)

## License

GPL-3.0. See `LICENSE`.
