# okplayer

A local music player for Android, styled after Windows 7's Aero, built around
playing albums the way they were sequenced.

- **Local library.** FLAC, MP3, AAC/M4A, OGG and WAV from the phone's storage,
  grouped into albums with cover art.
- **Bit-perfect USB DAC output.** On Android 14 and newer, okplayer asks the
  system to open a USB DAC in bit-perfect mode at the track's own sample rate,
  skipping Android's resampler. The now playing screen shows exactly what the
  DAC receives, and when bit-perfect isn't possible it says why.
- **In order by default.** Albums play front to back. Shuffle is opt-in.
- **No skipping mode.** Locks next, previous and seeking ahead until the song
  ends. It covers the notification, the lock screen and headset buttons as well
  as the app.
- **Spinning disc and visualizer.** The cover sits on a CD that turns while
  music plays. Behind it, an aurora follows the bass, mids and treble of the
  song itself.
- **Scrobbling.** Playback goes through a standard media session, so scrobblers
  that read media notifications (such as Pano Scrobbler) pick it up.

## Install

Every push to `main` builds a debug APK. Open the latest run under
**Actions → build**, download `okplayer-debug`, unzip it and install
`app-debug.apk`. Tagged versions (`v0.1.0`, …) attach the APK to a release.

All builds are signed with the same committed debug key, so a new build
installs over the old one without losing your library or settings.

## Hi-res and bit-perfect

| Source | Hi-res output off | Hi-res output on |
| --- | --- | --- |
| 16-bit | bit-perfect if the DAC accepts 16-bit at that rate | bit-perfect if the DAC accepts float at that rate |
| 24-bit | reduced to 16-bit | bit-perfect if the DAC accepts float at that rate |

Hi-res output is on by default and takes effect the next time playback starts.
Bit-perfect needs Android 14+ and a phone whose audio stack supports it; not
every manufacturer enables it. When it's unavailable the status line reads
"mixed by Android" or "resampled" and gives the reason.

## Build

Requirements: JDK 17 and the Android SDK (API 35).

```
./gradlew assembleDebug
```

## Layout

```
app/src/main/java/dev/davirazuk/okplayer/
  audio/      USB DAC routing and the level meter for the visualizer
  data/       settings and loved songs
  library/    MediaStore scanning, albums and tracks
  playback/   media session service and the UI-side controller
  ui/         Compose screens, components and theme
web/          browser prototype (installable PWA, files kept in app storage)
```

## Roadmap

- Queue view, loved songs list and album search
- Folder browsing for files outside the MediaStore
- Desktop build (Windows, Linux) sharing the player core
- PS Vita homebrew version (VitaSDK) for local FLAC/MP3 with covers
- Qobuz streaming, pending API access from Qobuz

## License

GPL-3.0. See `LICENSE`.
