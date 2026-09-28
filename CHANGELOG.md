# Changelog

## 0.3.0

- **Home screen widget** in the style of WMP 12's mini player: cover, title,
  previous / play / next, all working with the app closed.
- **Browse by Artists, Albums or Songs** from the breadcrumb, plus the auto
  playlists **Recently added**, **Most played** and **Recently played**.
- **Long-press menus**: Play, Play next, Add to Now Playing.
- **Graphic equalizer** with presets (off by default; not bit-perfect while on).
- **Sleep timer** with a fade-out.
- Play counts, the current song in the control bar while browsing, generated
  covers for albums without art, and a disc that spins up and a time display
  that blinks while paused.
- Headset, Bluetooth and widget buttons can start playback with the app closed.

## 0.2.0

- Windows Media Player 12 design: Aero window, black glass control bar, CD deck
  with a backlit LCD.
- FFmpeg decoders with automatic fallback when the phone's decoder fails;
  ALAC, AC-3, E-AC-3, DTS and TrueHD support.
- Fixed songs playing too fast and distorted on phones whose decoders mishandle
  float output.
- Bit-perfect USB output on Android 14+, with the reason shown when it isn't
  possible.
- Synced lyrics from the file's tags or LRCLIB, the play list, search, star
  ratings, and resuming where you left off.

## 0.1.0

- First build: local library, spinning disc, in-order and no-skipping modes.
