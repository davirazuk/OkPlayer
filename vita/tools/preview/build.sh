#!/bin/sh
# Builds the desktop preview and renders the screens: ./build.sh [output dir]
set -e
cd "$(dirname "$0")"
out="${1:-.}"
cc -O2 -std=gnu11 -w -Iinclude -I../../src -I/usr/include/freetype2 \
  preview.c vita2d_shim.c ../../src/gfx.c -o preview -lfreetype -lpng -lm
./preview "$out"
