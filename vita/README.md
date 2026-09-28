# okplayer for PS Vita

The okplayer look on the Vita: Aero window, black glass control bar, and a CD
spinning in its tray above a backlit LCD. Homebrew; needs a Vita running HENkaku
or Ensō with VitaShell.

## Install

Download `okplayer-vita` from the latest run under **Actions → vita** (or from a
release), unzip it, copy `okplayer.vpk` to the Vita and install it with VitaShell.

## Music

Put albums in `ux0:music`, one folder per album. `Artist/Album/` works too, and the
artist folder's name is used as the artist.

```
ux0:music/
  Radiohead/
    OK Computer/
      01 Airbag.flac
      02 Paranoid Android.flac
      cover.jpg
```

- Formats: FLAC, MP3, WAV and Ogg Vorbis.
- Artwork: `cover.jpg`, `folder.jpg`, `front.jpg` or the PNG versions in the album
  folder. Albums without one get a coloured cover with their initial.
- Songs play in file name order; a leading track number is left off the title.
- Also scanned: `ux0:data/music`, `ux0:data/okplayer` and `uma0:music`.

The Vita's audio output runs at up to 48 kHz, so 88.2/176.4 kHz files are played at
44.1 kHz and 96/192 kHz files at 48 kHz, by averaging whole samples. The LCD shows
both rates.

## Controls

| Button | Action |
| --- | --- |
| D-pad | Move around the library and album |
| X | Open an album, play a song; play / pause on Now Playing |
| O | Back |
| Triangle | Now Playing and back |
| L / R | Previous / next |
| START | Play / pause anywhere |
| Square | Shuffle (on an album: play it shuffled; in the library: shuffle everything) |
| SELECT | No skipping: locks next, previous and seeking ahead |
| Left / right on Now Playing | Seek 10 seconds |

The front touch screen works for tiles, songs, the play orb, previous / next and the
seek line.

## Build

With [VitaSDK](https://vitasdk.org) installed:

```
tools/fetch_decoders.sh
cmake -S . -B build && cmake --build build
```

`tools/make_livearea.py` redraws the LiveArea images.

## Credits

dr_flac, dr_mp3 and dr_wav by David Reid, stb_vorbis by Sean Barrett (public
domain), vita2d by xerpi.
