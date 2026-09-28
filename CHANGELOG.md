# Changelog

## 0.4.0

- **PS Vita version** (`okplayer-vita-*.vpk`, install with VitaShell): album folders
  from `ux0:music` with covers and tags, FLAC / MP3 / WAV / Ogg, the spinning disc
  and LCD, Up next, D-pad and touch controls, shuffle, No skipping, and reopening
  where you left off.
- **Desktop version** for Windows (portable `.exe`) and Linux (AppImage): the player
  in a frameless window whose Aero title bar is the real window frame.
- Android: okplayer's disc in the status bar and notification, and a preview of the
  widget in the widget picker.

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
