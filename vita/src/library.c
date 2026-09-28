#include "library.h"

#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <psp2/io/dirent.h>
#include <psp2/io/stat.h>

const char *const LIBRARY_ROOTS[] = {"ux0:music", "ux0:data/music", "ux0:data/okplayer", "uma0:music", NULL};

#define MAX_DEPTH 4

static const char *const AUDIO_EXT[] = {".flac", ".mp3", ".wav", ".ogg", ".oga", NULL};
static const char *const COVER_NAMES[] = {"cover.jpg", "folder.jpg", "front.jpg", "albumart.jpg", "cover.png", "folder.png", "front.png", NULL};

static int has_ext(const char *name, const char *const *exts) {
    size_t n = strlen(name);
    for (int i = 0; exts[i]; i++) {
        size_t m = strlen(exts[i]);
        if (n < m) continue;
        int ok = 1;
        for (size_t k = 0; k < m && ok; k++)
            if (tolower((unsigned char)name[n - m + k]) != exts[i][k]) ok = 0;
        if (ok) return 1;
    }
    return 0;
}

static int same_name(const char *a, const char *b) {
    for (; *a && *b; a++, b++)
        if (tolower((unsigned char)*a) != tolower((unsigned char)*b)) return 0;
    return *a == *b;
}

static char *join(const char *dir, const char *name) {
    size_t n = strlen(dir) + strlen(name) + 2;
    char *p = malloc(n);
    snprintf(p, n, "%s/%s", dir, name);
    return p;
}

static const char *base_name(const char *path) {
    const char *s = strrchr(path, '/');
    return s ? s + 1 : path;
}

/* "03 - Exit Music (For a Film).flac" -> number 3, title "Exit Music (For a Film)" */
static void parse_song_name(const char *file, Song *s) {
    const char *p = file;
    int number = 0, digits = 0;
    while (isdigit((unsigned char)*p) && digits < 3) number = number * 10 + (*p++ - '0'), digits++;
    if (digits > 0 && (*p == ' ' || *p == '-' || *p == '.' || *p == '_')) {
        while (*p == ' ' || *p == '-' || *p == '.' || *p == '_') p++;
    } else {
        p = file;
        number = 0;
    }
    const char *dot = strrchr(p, '.');
    size_t len = dot && dot > p ? (size_t)(dot - p) : strlen(p);
    s->title = malloc(len + 1);
    memcpy(s->title, p, len);
    s->title[len] = 0;
    s->number = number;
}

static int song_cmp(const void *a, const void *b) {
    const Song *x = a, *y = b;
    if (x->number != y->number && x->number && y->number) return x->number - y->number;
    return strcasecmp(base_name(x->path), base_name(y->path));
}

static int album_cmp(const void *a, const void *b) {
    const Album *x = a, *y = b;
    int c = strcasecmp(x->artist, y->artist);
    return c ? c : strcasecmp(x->title, y->title);
}

typedef struct {
    Album *items;
    int count, cap;
    int songs;
} AlbumList;

static void scan_dir(AlbumList *out, const char *dir, const char *parent_name, int depth) {
    SceUID d = sceIoDopen(dir);
    if (d < 0) return;

    Song *songs = NULL;
    int song_count = 0, song_cap = 0;
    char *cover = NULL;
    char **subdirs = NULL;
    int sub_count = 0, sub_cap = 0;

    SceIoDirent ent;
    memset(&ent, 0, sizeof(ent));
    while (sceIoDread(d, &ent) > 0) {
        const char *name = ent.d_name;
        if (name[0] == '.') continue;
        if (SCE_S_ISDIR(ent.d_stat.st_mode)) {
            if (depth < MAX_DEPTH) {
                if (sub_count == sub_cap) subdirs = realloc(subdirs, sizeof(char *) * (sub_cap = sub_cap ? sub_cap * 2 : 8));
                subdirs[sub_count++] = join(dir, name);
            }
        } else if (has_ext(name, AUDIO_EXT)) {
            if (song_count == song_cap) songs = realloc(songs, sizeof(Song) * (song_cap = song_cap ? song_cap * 2 : 16));
            Song *s = &songs[song_count++];
            s->path = join(dir, name);
            parse_song_name(name, s);
        } else if (!cover) {
            for (int i = 0; COVER_NAMES[i]; i++)
                if (same_name(name, COVER_NAMES[i])) cover = join(dir, name);
        }
        memset(&ent, 0, sizeof(ent));
    }
    sceIoDclose(d);

    if (song_count > 0) {
        qsort(songs, song_count, sizeof(Song), song_cmp);
        if (out->count == out->cap) out->items = realloc(out->items, sizeof(Album) * (out->cap = out->cap ? out->cap * 2 : 32));
        Album *a = &out->items[out->count++];
        a->title = strdup(base_name(dir));
        a->artist = strdup(parent_name && depth > 1 ? parent_name : "Unknown artist");
        a->cover_path = cover;
        a->songs = songs;
        a->song_count = song_count;
        out->songs += song_count;
    } else {
        free(cover);
        free(songs);
    }

    for (int i = 0; i < sub_count; i++) {
        scan_dir(out, subdirs[i], base_name(dir), depth + 1);
        free(subdirs[i]);
    }
    free(subdirs);
}

void library_scan(Library *lib) {
    AlbumList list = {0};
    for (int i = 0; LIBRARY_ROOTS[i]; i++) scan_dir(&list, LIBRARY_ROOTS[i], NULL, 0);
    if (list.count > 1) qsort(list.items, list.count, sizeof(Album), album_cmp);
    lib->albums = list.items;
    lib->count = list.count;
    lib->song_total = list.songs;
}

void library_free(Library *lib) {
    for (int i = 0; i < lib->count; i++) {
        Album *a = &lib->albums[i];
        for (int k = 0; k < a->song_count; k++) {
            free(a->songs[k].path);
            free(a->songs[k].title);
        }
        free(a->songs);
        free(a->title);
        free(a->artist);
        free(a->cover_path);
    }
    free(lib->albums);
    memset(lib, 0, sizeof(*lib));
}
