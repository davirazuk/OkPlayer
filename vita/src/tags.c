/*
 * Reads title, artist, album and track number from a file's tags: Vorbis comments in
 * FLAC and Ogg, ID3v2 in MP3. Used when an album is opened, so startup stays quick.
 */
#include "tags.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>

#include "dr_flac.h"
#define STB_VORBIS_HEADER_ONLY
#include "stb_vorbis.c"

static void set(char *dst, size_t cap, const char *src, size_t len) {
    if (len >= cap) len = cap - 1;
    memcpy(dst, src, len);
    dst[len] = 0;
    /* Trim trailing spaces and NULs some taggers leave. */
    while (len > 0 && (dst[len - 1] == ' ' || dst[len - 1] == 0)) dst[--len] = 0;
}

/* One "KEY=value" Vorbis comment. */
static void vorbis_field(Tags *t, const char *c, size_t len) {
    const char *eq = memchr(c, '=', len);
    if (!eq) return;
    size_t klen = (size_t)(eq - c), vlen = len - klen - 1;
    const char *v = eq + 1;
#define KEY(name) (klen == sizeof(name) - 1 && strncasecmp(c, name, klen) == 0)
    if (KEY("TITLE")) set(t->title, sizeof t->title, v, vlen);
    else if (KEY("ARTIST") && !t->artist[0]) set(t->artist, sizeof t->artist, v, vlen);
    else if (KEY("ALBUMARTIST") || KEY("ALBUM ARTIST")) set(t->artist, sizeof t->artist, v, vlen);
    else if (KEY("ALBUM")) set(t->album, sizeof t->album, v, vlen);
    else if (KEY("TRACKNUMBER")) t->track = atoi(v);
    else if (KEY("DISCNUMBER")) t->disc = atoi(v);
#undef KEY
}

static void flac_meta(void *user, drflac_metadata *m) {
    if (m->type != DRFLAC_METADATA_BLOCK_TYPE_VORBIS_COMMENT) return;
    drflac_vorbis_comment_iterator it;
    drflac_init_vorbis_comment_iterator(&it, m->data.vorbis_comment.commentCount, m->data.vorbis_comment.pComments);
    drflac_uint32 len;
    const char *c;
    while ((c = drflac_next_vorbis_comment(&it, &len)) != NULL) vorbis_field(user, c, len);
}

static int read_flac(const char *path, Tags *t) {
    drflac *f = drflac_open_file_with_metadata(path, flac_meta, t, NULL);
    if (!f) return 0;
    drflac_close(f);
    return 1;
}

static int read_ogg(const char *path, Tags *t) {
    int err = 0;
    stb_vorbis *v = stb_vorbis_open_filename(path, &err, NULL);
    if (!v) return 0;
    stb_vorbis_comment c = stb_vorbis_get_comment(v);
    for (int i = 0; i < c.comment_list_length; i++) vorbis_field(t, c.comment_list[i], strlen(c.comment_list[i]));
    stb_vorbis_close(v);
    return 1;
}

static unsigned syncsafe(const unsigned char *b) { return (b[0] & 0x7F) << 21 | (b[1] & 0x7F) << 14 | (b[2] & 0x7F) << 7 | (b[3] & 0x7F); }

/* ID3 text: encoding byte, then Latin-1, UTF-16 (with or without BOM) or UTF-8. Kept as UTF-8. */
static void id3_text(char *dst, size_t cap, const unsigned char *p, size_t n) {
    if (n < 1) return;
    int enc = p[0];
    p++;
    n--;
    size_t o = 0;
    if (enc == 0) {
        for (size_t i = 0; i < n && p[i] && o + 2 < cap; i++) {
            if (p[i] < 0x80) dst[o++] = (char)p[i];
            else { dst[o++] = (char)(0xC0 | p[i] >> 6); dst[o++] = (char)(0x80 | (p[i] & 0x3F)); }
        }
    } else if (enc == 1 || enc == 2) {
        int big = enc == 2;
        size_t i = 0;
        if (enc == 1 && n >= 2) {
            big = p[0] == 0xFE && p[1] == 0xFF;
            i = 2;
        }
        for (; i + 1 < n && o + 3 < cap; i += 2) {
            unsigned c = big ? (p[i] << 8 | p[i + 1]) : (p[i + 1] << 8 | p[i]);
            if (!c) break;
            if (c < 0x80) dst[o++] = (char)c;
            else if (c < 0x800) { dst[o++] = (char)(0xC0 | c >> 6); dst[o++] = (char)(0x80 | (c & 0x3F)); }
            else { dst[o++] = (char)(0xE0 | c >> 12); dst[o++] = (char)(0x80 | ((c >> 6) & 0x3F)); dst[o++] = (char)(0x80 | (c & 0x3F)); }
        }
    } else {
        for (size_t i = 0; i < n && p[i] && o + 1 < cap; i++) dst[o++] = (char)p[i];
    }
    dst[o] = 0;
}

static int read_mp3(const char *path, Tags *t) {
    FILE *f = fopen(path, "rb");
    if (!f) return 0;
    unsigned char h[10];
    int ok = 0;
    if (fread(h, 1, 10, f) == 10 && memcmp(h, "ID3", 3) == 0 && h[3] >= 3) {
        unsigned size = syncsafe(h + 6);
        /* Covers can be large; the text frames are nearly always in the first 256 KB. */
        if (size > 256 * 1024) size = 256 * 1024;
        unsigned char *tag = malloc(size);
        size = (unsigned)fread(tag, 1, size, f);
        unsigned p = 0;
        if (h[5] & 0x40) p += h[3] == 4 ? syncsafe(tag) : (unsigned)(tag[0] << 24 | tag[1] << 16 | tag[2] << 8 | tag[3]) + 4;
        char track[16] = {0};
        while (p + 10 <= size) {
            const unsigned char *fr = tag + p;
            if (!fr[0]) break;
            unsigned len = h[3] == 4 ? syncsafe(fr + 4) : (unsigned)(fr[4] << 24 | fr[5] << 16 | fr[6] << 8 | fr[7]);
            if (len == 0 || p + 10 + len > size) break;
            const unsigned char *body = fr + 10;
            if (!memcmp(fr, "TIT2", 4)) id3_text(t->title, sizeof t->title, body, len);
            else if (!memcmp(fr, "TPE1", 4) && !t->artist[0]) id3_text(t->artist, sizeof t->artist, body, len);
            else if (!memcmp(fr, "TPE2", 4)) id3_text(t->artist, sizeof t->artist, body, len);
            else if (!memcmp(fr, "TALB", 4)) id3_text(t->album, sizeof t->album, body, len);
            else if (!memcmp(fr, "TRCK", 4)) id3_text(track, sizeof track, body, len);
            p += 10 + len;
        }
        t->track = atoi(track);
        free(tag);
        ok = 1;
    }
    fclose(f);
    return ok;
}

int tags_read(const char *path, Tags *t) {
    memset(t, 0, sizeof(*t));
    size_t n = strlen(path);
    if (n > 5 && strcasecmp(path + n - 5, ".flac") == 0) return read_flac(path, t);
    if (n > 4 && strcasecmp(path + n - 4, ".mp3") == 0) return read_mp3(path, t);
    if (n > 4 && (strcasecmp(path + n - 4, ".ogg") == 0 || strcasecmp(path + n - 4, ".oga") == 0)) return read_ogg(path, t);
    return 0;
}
