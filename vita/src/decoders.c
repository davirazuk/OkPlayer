/*
 * The single-file decoders, compiled once here. They're fetched into third_party/
 * by the build (see README): dr_flac, dr_mp3, dr_wav (public domain / MIT-0,
 * David Reid) and stb_vorbis (public domain, Sean Barrett).
 */
#define DR_FLAC_IMPLEMENTATION
#define DR_FLAC_NO_OGG
#include "dr_flac.h"

#define DR_MP3_IMPLEMENTATION
#include "dr_mp3.h"

#define DR_WAV_IMPLEMENTATION
#include "dr_wav.h"

#include "stb_vorbis.c"
