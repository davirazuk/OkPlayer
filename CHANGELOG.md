# Changelog

## Unreleased

- **Seven skin**, now the default on PC and web: okplayer's original look is back and
  grown up. A dark deck with the big disc, glass orbs and pill toggles, a spectrum
  rising from the transport, and a Windows 7 **taskbar** with a working **Start menu**
  (every library view, search as you type, lyrics, play list, equalizer, sleep timer),
  a tray **volume flyout**, a clock and balloon notifications. The Windows Media
  Player 12 look is still there: switch with **Ctrl+2** or from the Start menu.
- Android gets the **Seven skin** too, as the default: the same deck, orbs, pills and
  spectrum, a taskbar with Start, Library and Now Playing buttons, tray icons for the
  USB DAC and the sleep timer, a clock, and a Start menu with search. Pick the look
  under **Options → Look**.
- Fixed: the blue desktop behind the window was white on wide screens.
- Fixed: the equalizer's sliders had no visible track.
- Web and desktop: **Artists, Songs, Recently added, Most played and Recently played**
  views from the breadcrumb, **search**, Windows 7 **right-click menus** (Play next,
  Add to Now Playing, Go to album / artist, Remove from play list), a **sleep timer**
  with a fade-out, a **volume slider**, Windows Media Player's **keyboard
  shortcuts**, and **drag and drop** to add music.

## 0.4.0

- **PS Vita version** (`okplayer-vita-*.vpk`, install with VitaShell): album folders
  from `ux0:music` with covers and tags, FLAC / MP3 / WAV / Ogg, the spinning disc
  and LCD, Up next, D-pad and touch controls, shuffle, No skipping, and reopening
  where you left off.
- **Desktop version** for Windows (portable `.exe`) and Linux (AppImage): the player
  in a frameless window whose Aero title bar is the real window frame.
- Web and desktop: the **graphic equalizer** with presets, and proper minimize /
  maximize / close glyphs on the title bar.
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
