#!/bin/sh
# Downloads the single-file decoders into third_party/ (they aren't kept in the repo).
set -e
cd "$(dirname "$0")/.."
mkdir -p third_party
for f in dr_flac.h dr_mp3.h dr_wav.h; do
  curl -fsSL "https://raw.githubusercontent.com/mackron/dr_libs/master/$f" -o "third_party/$f"
done
curl -fsSL "https://raw.githubusercontent.com/nothings/stb/master/stb_vorbis.c" -o third_party/stb_vorbis.c
