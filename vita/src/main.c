/*
 * okplayer for PS Vita: a local music player in the style of Windows Media Player 12.
 *
 * Controls: D-pad to move, X to open or play, O to go back, triangle for Now Playing,
 * L / R for previous / next, START to pause, square to shuffle, SELECT for No skipping.
 * The front touch screen works too.
 */
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include <psp2/ctrl.h>
#include <psp2/io/stat.h>
#include <psp2/kernel/processmgr.h>
#include <psp2/touch.h>
#include <vita2d.h>

#include "gfx.h"
#include "library.h"
#include "player.h"

#define W 960
#define H 544

/* Window layout. */
#define WIN_X 10
#define WIN_Y 6
#define WIN_W 940
#define WIN_H 532
#define PANE_X 16
#define PANE_Y 70
#define PANE_W 928
#define BAR_Y 452
#define BAR_H 80
#define PANE_H (BAR_Y - PANE_Y)

/* Colours, from the Android app's palette. */
#define C_WHITE RGBA8(255, 255, 255, 255)
#define C_BLACK RGBA8(5, 6, 7, 255)
#define C_INK RGBA8(30, 30, 30, 255)
#define C_SUB RGBA8(109, 109, 109, 255)
#define C_HEAD RGBA8(30, 57, 91, 255)
#define C_RULE RGBA8(226, 231, 238, 255)
#define C_SEL RGBA8(204, 232, 255, 255)
#define C_SEL_EDGE RGBA8(153, 209, 255, 255)
#define C_LCD RGBA8(143, 240, 255, 255)
#define C_LCD_DIM RGBA8(143, 240, 255, 150)
#define C_LCD_OFF RGBA8(143, 240, 255, 40)
#define C_BAR_TEXT RGBA8(216, 221, 227, 255)
#define C_BAR_DIM RGBA8(150, 160, 171, 255)
#define C_AERO RGBA8(58, 167, 234, 255)
#define C_GLOW RGBA8(127, 208, 255, 255)

enum View { VIEW_LIBRARY, VIEW_ALBUM, VIEW_NOW };

#define TILE_W 128
#define TILE_H 156
#define COVER 110
#define COLS 7
#define ROW_H 32

static Library lib;
static Skin skin;
static vita2d_pgf *font;
static vita2d_texture **covers;  /* small covers, loaded as they scroll into view */
static unsigned char *cover_tried;
static vita2d_texture *label;    /* disc label for the album that's playing */
static int label_album = -2;

static int view = VIEW_LIBRARY, previous_view = VIEW_LIBRARY;
static int sel_album, lib_scroll;           /* library grid */
static int open_album = -1, sel_track, track_scroll;
static int playing_album = -1;
static int cur_album = -1, cur_song = -1;   /* the song playing, found once per change */
static char up_next[3][200];                 /* titles of the next songs, refreshed with it */

static void find_song(const char *path, int *album, int *song);

static void refresh_up_next(void) {
    for (int i = 0; i < 3; i++) {
        int a, s;
        find_song(player_queue_path(i + 1), &a, &s);
        if (s >= 0) snprintf(up_next[i], sizeof up_next[i], "%s  -  %s", lib.albums[a].songs[s].title, lib.albums[a].artist);
        else up_next[i][0] = 0;
    }
}
static float disc_angle, disc_speed;
static char last_error[128];
static int error_frames;

/* ---------- drawing helpers ---------- */

static void rect(float x, float y, float w, float h, unsigned c) { vita2d_draw_rectangle(x, y, w, h, c); }

static void frame(float x, float y, float w, float h, unsigned c) {
    rect(x, y, w, 1, c);
    rect(x, y + h - 1, w, 1, c);
    rect(x, y, 1, h, c);
    rect(x + w - 1, y, 1, h, c);
}

static void stretch(vita2d_texture *t, float x, float y, float w, float h) {
    vita2d_draw_texture_scale(t, x, y, w / vita2d_texture_get_width(t), h / vita2d_texture_get_height(t));
}

static void text(int x, int y, unsigned c, float s, const char *str) { vita2d_pgf_draw_text(font, x, y, c, s, str); }

static int text_w(float s, const char *str) { return vita2d_pgf_text_width(font, s, str); }

/* Draws text cut to fit max_w, ending in "..." when it had to be cut. */
static void text_fit(int x, int y, unsigned c, float s, const char *str, int max_w) {
    if (text_w(s, str) <= max_w) {
        text(x, y, c, s, str);
        return;
    }
    char buf[256];
    size_t n = strlen(str);
    if (n > sizeof(buf) - 4) n = sizeof(buf) - 4;
    while (n > 0) {
        memcpy(buf, str, n);
        strcpy(buf + n, "...");
        if (text_w(s, buf) <= max_w) break;
        n--;
        while (n > 0 && ((unsigned char)str[n] & 0xC0) == 0x80) n--; /* don't split UTF-8 */
    }
    text(x, y, c, s, buf);
}

static void text_center(int cx, int y, unsigned c, float s, const char *str) { text(cx - text_w(s, str) / 2, y, c, s, str); }

/*
 * PlayStation button symbols, drawn rather than taken from the font: {X} cross,
 * {O} circle, {T} triangle, {S} square, in their usual colours.
 */
static int glyph(char kind, int x, int y, float scale, int draw) {
    int s = (int)(13 * scale / 0.75f);
    if (draw) {
        int top = y - s + 1;
        float cx = x + s / 2.0f, cy = top + s / 2.0f, r = s / 2.0f - 1;
        for (int t = 0; t < 2; t++) {
            float o = t * 0.9f;
            switch (kind) {
                case 'X': {
                    unsigned c = RGBA8(125, 160, 235, 255);
                    vita2d_draw_line(x + 1 + o, top + 1, x + s - 1 + o, top + s - 1, c);
                    vita2d_draw_line(x + s - 1 + o, top + 1, x + 1 + o, top + s - 1, c);
                    break;
                }
                case 'O': {
                    unsigned c = RGBA8(235, 105, 105, 255);
                    for (int i = 0; i < 24; i++) {
                        float a0 = i * 6.2831853f / 24, a1 = (i + 1) * 6.2831853f / 24;
                        vita2d_draw_line(cx + (r - o) * cosf(a0), cy + (r - o) * sinf(a0), cx + (r - o) * cosf(a1), cy + (r - o) * sinf(a1), c);
                    }
                    break;
                }
                case 'T': {
                    unsigned c = RGBA8(80, 205, 165, 255);
                    vita2d_draw_line(cx, top + 1 + o, x + 1 + o, top + s - 1 - o, c);
                    vita2d_draw_line(x + 1 + o, top + s - 1 - o, x + s - 1 - o, top + s - 1 - o, c);
                    vita2d_draw_line(x + s - 1 - o, top + s - 1 - o, cx, top + 1 + o, c);
                    break;
                }
                case 'S':
                    frame(x + 1 + o, top + 1 + o, s - 2 - 2 * o, s - 2 - 2 * o, RGBA8(225, 135, 205, 255));
                    break;
            }
        }
    }
    return s + 5;
}

/* Text with {X} {O} {T} {S} replaced by button symbols. Returns the width. */
static int rich(int x, int y, unsigned c, float s, const char *str, int draw) {
    int pen = x;
    char buf[256];
    while (*str) {
        const char *brace = strchr(str, '{');
        size_t n = brace ? (size_t)(brace - str) : strlen(str);
        if (n > sizeof buf - 1) n = sizeof buf - 1;
        if (n) {
            memcpy(buf, str, n);
            buf[n] = 0;
            if (draw) text(pen, y, c, s, buf);
            pen += text_w(s, buf);
            str += n;
        }
        if (brace && brace[1] && brace[2] == '}') {
            pen += glyph(brace[1], pen, y, s, draw);
            str = brace + 3;
        } else if (brace) {
            str = brace + 1;
        }
    }
    return pen - x;
}

static void khz(char *out, size_t n, unsigned hz) {
    if (hz % 1000 == 0) snprintf(out, n, "%u", hz / 1000);
    else snprintf(out, n, "%u.%u", hz / 1000, (hz % 1000) / 100);
}

static void triangle_right(float x, float y, float h, unsigned c) {
    for (int i = 0; i < (int)h; i++) {
        float half = (i < h / 2 ? i : h - i) * 0.9f;
        rect(x, y + i, half + 1, 1, c);
    }
}

static void triangle_left(float x, float y, float h, unsigned c) {
    for (int i = 0; i < (int)h; i++) {
        float half = (i < h / 2 ? i : h - i) * 0.9f;
        rect(x - half, y + i, half + 1, 1, c);
    }
}

static void fmt_time(char *out, size_t n, uint64_t ms) {
    unsigned s = (unsigned)(ms / 1000);
    snprintf(out, n, "%u:%02u", s / 60, s % 60);
}

static void upper(char *dst, const char *src, size_t n) {
    size_t i = 0;
    for (; src[i] && i < n - 1; i++) dst[i] = (src[i] >= 'a' && src[i] <= 'z') ? src[i] - 32 : src[i];
    dst[i] = 0;
}

/* ---------- library helpers ---------- */

static void find_song(const char *path, int *album, int *song) {
    *album = -1;
    *song = -1;
    if (!path) return;
    for (int a = 0; a < lib.count; a++)
        for (int s = 0; s < lib.albums[a].song_count; s++)
            if (strcmp(lib.albums[a].songs[s].path, path) == 0) {
                *album = a;
                *song = s;
                return;
            }
}

static void ensure_label(int album) {
    if (album == label_album) return;
    vita2d_wait_rendering_done();
    if (label) vita2d_free_texture(label);
    label = NULL;
    label_album = album;
    if (album < 0) return;
    if (!covers[album] && !cover_tried[album] && lib.albums[album].cover_path) {
        covers[album] = cover_load(lib.albums[album].cover_path, COVER);
        cover_tried[album] = 1;
    }
    label = covers[album] ? label_from_cover(covers[album]) : label_generated(lib.albums[album].title);
}

static void play_album(int album, int start, int shuffle) {
    Album *a = &lib.albums[album];
    library_load_tags(a);
    const char **paths = malloc(sizeof(char *) * a->song_count);
    for (int i = 0; i < a->song_count; i++) paths[i] = a->songs[i].path;
    player_set_shuffle(shuffle);
    player_play(paths, a->song_count, start);
    free(paths);
    playing_album = album;
}

/* ---------- resume where you left off ---------- */

#define STATE_DIR "ux0:data/okplayer"
#define STATE_FILE STATE_DIR "/state.txt"

static void save_state(const PlayerStatus *st) {
    const char *path = player_current_path();
    if (!path) return;
    sceIoMkdir(STATE_DIR, 0777);
    FILE *f = fopen(STATE_FILE, "w");
    if (!f) return;
    fprintf(f, "%s\n%llu\n", path, (unsigned long long)st->position_ms);
    fclose(f);
}

static void restore_state(void) {
    FILE *f = fopen(STATE_FILE, "r");
    if (!f) return;
    char path[512];
    unsigned long long pos = 0;
    if (fgets(path, sizeof path, f) && fscanf(f, "%llu", &pos) == 1) {
        path[strcspn(path, "\r\n")] = 0;
        int a, s;
        find_song(path, &a, &s);
        if (a >= 0) {
            library_load_tags(&lib.albums[a]);
            find_song(path, &a, &s); /* tags may have re-sorted the album */
            Album *al = &lib.albums[a];
            const char **paths = malloc(sizeof(char *) * al->song_count);
            for (int i = 0; i < al->song_count; i++) paths[i] = al->songs[i].path;
            player_open(paths, al->song_count, s, pos, 1);
            free(paths);
            playing_album = a;
        }
    }
    fclose(f);
}

static void play_everything_shuffled(void) {
    int n = 0;
    const char *paths[PLAYER_MAX_QUEUE];
    for (int a = 0; a < lib.count && n < PLAYER_MAX_QUEUE; a++)
        for (int s = 0; s < lib.albums[a].song_count && n < PLAYER_MAX_QUEUE; s++) paths[n++] = lib.albums[a].songs[s].path;
    if (!n) return;
    player_set_shuffle(1);
    player_play(paths, n, rand() % n);
}

/* ---------- window chrome ---------- */

static void draw_window(const char *title, const char *crumb, int can_go_back) {
    vita2d_draw_texture(skin.sky, 0, 0);
    rect(WIN_X, WIN_Y, WIN_W, WIN_H, RGBA8(170, 205, 235, 165));
    frame(WIN_X, WIN_Y, WIN_W, WIN_H, RGBA8(18, 38, 58, 160));
    frame(WIN_X + 1, WIN_Y + 1, WIN_W - 2, WIN_H - 2, RGBA8(255, 255, 255, 170));

    /* Title bar: okplayer's disc mark, the title, and the caption buttons. */
    vita2d_draw_texture_scale(skin.disc, WIN_X + 10, WIN_Y + 7, 16.0f / DISC_SIZE, 16.0f / DISC_SIZE);
    text_fit(WIN_X + 34, WIN_Y + 21, C_BLACK, 0.85f, title, 700);
    int bx = WIN_X + WIN_W - 110;
    rect(bx, WIN_Y + 1, 27, 19, RGBA8(200, 218, 235, 255));
    rect(bx + 27, WIN_Y + 1, 27, 19, RGBA8(200, 218, 235, 255));
    rect(bx + 54, WIN_Y + 1, 48, 19, RGBA8(210, 80, 55, 255));
    frame(bx, WIN_Y + 1, 102, 19, RGBA8(40, 60, 80, 200));
    rect(bx + 27, WIN_Y + 1, 1, 19, RGBA8(40, 60, 80, 200));
    rect(bx + 54, WIN_Y + 1, 1, 19, RGBA8(40, 60, 80, 200));

    /* Toolbar: back orb and the breadcrumb. */
    vita2d_draw_texture_tint_scale(skin.orb, WIN_X + 10, WIN_Y + 32, 30.0f / 60, 30.0f / 60,
                                   can_go_back ? C_WHITE : RGBA8(255, 255, 255, 110));
    triangle_left(WIN_X + 21, WIN_Y + 40, 14, C_WHITE);
    rect(WIN_X + 48, WIN_Y + 33, WIN_W - 60, 28, C_WHITE);
    frame(WIN_X + 48, WIN_Y + 33, WIN_W - 60, 28, RGBA8(142, 164, 189, 255));
    text_fit(WIN_X + 58, WIN_Y + 53, RGBA8(51, 51, 51, 255), 0.85f, crumb, WIN_W - 80);
}

static void draw_control_bar(const PlayerStatus *st) {
    stretch(skin.bar, PANE_X, BAR_Y, PANE_W, BAR_H);
    rect(PANE_X, BAR_Y, PANE_W, 1, RGBA8(255, 255, 255, 60));

    /* Seek line. */
    char a[16], b[16];
    fmt_time(a, sizeof a, st->position_ms);
    fmt_time(b, sizeof b, st->duration_ms);
    text(PANE_X + 12, BAR_Y + 22, C_BAR_DIM, 0.7f, a);
    text(PANE_X + PANE_W - 12 - text_w(0.7f, b), BAR_Y + 22, C_BAR_DIM, 0.7f, b);
    float lx = PANE_X + 64, lw = PANE_W - 128, ly = BAR_Y + 14;
    rect(lx, ly, lw, 5, RGBA8(43, 47, 53, 255));
    float f = st->duration_ms ? (float)st->position_ms / st->duration_ms : 0;
    if (f > 1) f = 1;
    rect(lx, ly, lw * f, 5, C_AERO);
    if (st->index >= 0) vita2d_draw_fill_circle(lx + lw * f, ly + 2.5f, 6, RGBA8(200, 232, 255, 255));

    /* Transport: capsule with previous / next and the play orb on top. */
    float cx = W / 2.0f, cy = BAR_Y + 52;
    vita2d_draw_texture(skin.capsule, cx - 88, cy - 18);
    triangle_left(cx - 52, cy - 7, 14, C_BAR_TEXT);
    rect(cx - 64, cy - 7, 2, 14, C_BAR_TEXT);
    triangle_right(cx + 52, cy - 7, 14, C_BAR_TEXT);
    rect(cx + 62, cy - 7, 2, 14, C_BAR_TEXT);
    vita2d_draw_fill_circle(cx, cy, 34, RGBA8(58, 167, 234, st->state == PLAYER_PLAYING ? 70 : 35));
    vita2d_draw_texture(skin.orb, cx - 30, cy - 30);
    if (st->state == PLAYER_PLAYING) {
        rect(cx - 8, cy - 9, 5, 18, C_WHITE);
        rect(cx + 3, cy - 9, 5, 18, C_WHITE);
    } else {
        triangle_right(cx - 5, cy - 10, 20, C_WHITE);
    }

    /* Toggles on the left, view switch hint on the right. */
    rich(PANE_X + 16, BAR_Y + 58, st->shuffle ? C_GLOW : C_BAR_DIM, 0.75f, "{S} Shuffle", 1);
    text(PANE_X + 118, BAR_Y + 58, st->no_skip ? C_GLOW : C_BAR_DIM, 0.75f, "SELECT No skipping");
    const char *hint = view == VIEW_NOW ? "{T} Library" : "{T} Now Playing";
    rich(PANE_X + PANE_W - 16 - rich(0, 0, 0, 0.75f, hint, 0), BAR_Y + 58, C_BAR_DIM, 0.75f, hint, 1);
}

static void command_bar(const char *left, const char *right) {
    stretch(skin.cmdbar, PANE_X, PANE_Y, PANE_W, 30);
    rect(PANE_X, PANE_Y + 30, PANE_W, 1, RGBA8(160, 175, 195, 255));
    rich(PANE_X + 12, PANE_Y + 21, C_INK, 0.8f, left, 1);
    if (right) text(PANE_X + PANE_W - 12 - text_w(0.8f, right), PANE_Y + 21, C_SUB, 0.8f, right);
}

static void group_header(int x, int y, int w, const char *label_text) {
    text(x, y, C_HEAD, 0.85f, label_text);
    int tw = text_w(0.85f, label_text);
    rect(x + tw + 10, y - 6, w - tw - 10, 1, RGBA8(197, 211, 227, 255));
}

static void draw_cover(int album, float x, float y, float size) {
    if (covers[album]) {
        vita2d_draw_texture_scale(covers[album], x, y, size / COVER, size / COVER);
    } else {
        rect(x, y, size, size, hue_color(lib.albums[album].title, 0.62f, 0.45f));
        char letter[2] = {lib.albums[album].title[0], 0};
        text_center((int)(x + size / 2), (int)(y + size / 2 + size * 0.17f), RGBA8(255, 255, 255, 230), size / 40.0f, letter);
    }
    frame(x - 1, y - 1, size + 2, size + 2, RGBA8(0, 0, 0, 40));
}

/* ---------- views ---------- */

static int grid_top(void) { return PANE_Y + 62; }

static void draw_library(void) {
    rect(PANE_X, PANE_Y, PANE_W, PANE_H, C_WHITE);
    int visible_rows = (PANE_H - 64) / TILE_H + 1;
    int loaded_one = 0;
    for (int i = lib_scroll * COLS; i < lib.count && i < (lib_scroll + visible_rows + 1) * COLS; i++) {
        int row = i / COLS - lib_scroll, col = i % COLS;
        float x = PANE_X + 12 + col * (TILE_W + 2), y = grid_top() + row * TILE_H;
        if (i == sel_album) {
            rect(x, y, TILE_W, TILE_H - 4, C_SEL);
            frame(x, y, TILE_W, TILE_H - 4, C_SEL_EDGE);
        }
        /* Load at most one cover per frame so scrolling stays smooth. */
        if (!cover_tried[i] && !loaded_one && lib.albums[i].cover_path) {
            covers[i] = cover_load(lib.albums[i].cover_path, COVER);
            cover_tried[i] = 1;
            loaded_one = 1;
        }
        draw_cover(i, x + (TILE_W - COVER) / 2.0f, y + 8, COVER);
        text_fit((int)x + 9, (int)y + COVER + 28, C_INK, 0.72f, lib.albums[i].title, TILE_W - 16);
        text_fit((int)x + 9, (int)y + COVER + 44, C_SUB, 0.65f, lib.albums[i].artist, TILE_W - 16);
    }
    /* The command bar and header go over the grid so scrolled tiles slide under them. */
    rect(PANE_X, PANE_Y + 30, PANE_W, grid_top() - PANE_Y - 30, C_WHITE);
    char head[64], count[64];
    snprintf(head, sizeof head, "Albums (%d)", lib.count);
    group_header(PANE_X + 16, PANE_Y + 54, PANE_W - 32, head);
    snprintf(count, sizeof count, "%d albums, %d songs", lib.count, lib.song_total);
    command_bar("{X} Open     {S} Shuffle all", count);

    if (lib.count == 0) {
        text(PANE_X + 24, PANE_Y + 110, C_INK, 0.95f, "No music found.");
        text(PANE_X + 24, PANE_Y + 140, C_SUB, 0.8f, "Put album folders in ux0:music (FLAC, MP3, WAV or OGG),");
        text(PANE_X + 24, PANE_Y + 162, C_SUB, 0.8f, "with a cover.jpg in each folder for the artwork.");
    }
}

static void draw_album(const PlayerStatus *st) {
    rect(PANE_X, PANE_Y, PANE_W, PANE_H, C_WHITE);
    Album *a = &lib.albums[open_album];
    const char *current = player_current_path();

    int list_x = PANE_X + 196, list_w = PANE_W - 212, top = PANE_Y + 44;
    int visible = (PANE_H - 50) / ROW_H;
    for (int i = track_scroll; i < a->song_count && i < track_scroll + visible; i++) {
        int y = top + (i - track_scroll) * ROW_H;
        int playing = current && strcmp(current, a->songs[i].path) == 0;
        if (i == sel_track) {
            rect(list_x, y, list_w, ROW_H - 2, C_SEL);
            frame(list_x, y, list_w, ROW_H - 2, C_SEL_EDGE);
        }
        char num[8];
        int n = a->songs[i].number % 1000;
        snprintf(num, sizeof num, "%d", n ? n : i + 1);
        text(list_x + 10, y + 21, C_SUB, 0.78f, num);
        if (playing && st->state != PLAYER_STOPPED) triangle_right(list_x + list_w - 22, y + 9, 12, C_AERO);
        text_fit(list_x + 48, y + 21, playing ? C_HEAD : C_INK, 0.8f, a->songs[i].title, list_w - 80);
    }

    draw_cover(open_album, PANE_X + 20, PANE_Y + 46, 160);
    text_fit(PANE_X + 20, PANE_Y + 232, C_HEAD, 0.95f, a->title, 170);
    text_fit(PANE_X + 20, PANE_Y + 254, C_SUB, 0.75f, a->artist, 170);
    char meta[32];
    snprintf(meta, sizeof meta, "%d song%s", a->song_count, a->song_count == 1 ? "" : "s");
    text(PANE_X + 20, PANE_Y + 274, C_SUB, 0.75f, meta);

    command_bar("{X} Play     {S} Shuffle     {O} Back", NULL);
}

static void draw_now(const PlayerStatus *st) {
    rect(PANE_X, PANE_Y, PANE_W, PANE_H, C_BLACK);

    /* Soft light behind the deck that swells with the music. */
    float glow = 0.25f + st->level * 0.75f;
    for (int i = 0; i < 12; i++) {
        vita2d_draw_fill_circle(250, 262, 250 - i * 16, RGBA8(58, 167, 234, (int)(5 * glow)));
        vita2d_draw_fill_circle(760, 170, 190 - i * 12, RGBA8(245, 169, 184, (int)(4 * glow)));
    }

    /* The disc in its well: disc and label turn, sheen and hub stay put. */
    float cx = 250, cy = 262;
    vita2d_draw_texture(skin.well, cx - 150, cy - 150);
    vita2d_draw_texture_rotate(skin.disc, cx, cy, disc_angle);
    if (label) vita2d_draw_texture_rotate(label, cx, cy, disc_angle);
    vita2d_draw_texture(skin.sheen, cx - DISC_SIZE / 2.0f, cy - DISC_SIZE / 2.0f);
    vita2d_draw_texture(skin.hub, cx - 24, cy - 24);

    /* The LCD. */
    int lx = 452, ly = 112, lw = 470, lh = 196;
    frame(lx - 5, ly - 5, lw + 10, lh + 10, RGBA8(48, 53, 60, 255));
    rect(lx - 4, ly - 4, lw + 8, 4, RGBA8(18, 20, 24, 255));
    rect(lx - 4, ly + lh, lw + 8, 4, RGBA8(18, 20, 24, 255));
    rect(lx - 4, ly, 4, lh, RGBA8(18, 20, 24, 255));
    rect(lx + lw, ly, 4, lh, RGBA8(18, 20, 24, 255));
    stretch(skin.lcd, lx, ly, lw, lh);
    rect(lx, ly, lw, 6, RGBA8(0, 0, 0, 120));

    int ai = cur_album, si = cur_song;
    char num[16], tbuf[16], title[160], sub[200];
    if (st->index >= 0) snprintf(num, sizeof num, "%02d", st->index + 1);
    else strcpy(num, "--");
    if (st->index >= 0) fmt_time(tbuf, sizeof tbuf, st->position_ms);
    else strcpy(tbuf, "-:--");

    text(lx + 16, ly + 44, C_LCD_DIM, 0.7f, "TRACK");
    text(lx + 74, ly + 50, C_LCD, 2.0f, num);
    /* A paused CD player blinks its time. */
    int blink = st->state == PLAYER_PAUSED && (sceKernelGetProcessTimeLow() / 500000) % 2;
    text(lx + lw - 16 - text_w(2.0f, tbuf), ly + 50, blink ? C_LCD_OFF : C_LCD, 2.0f, tbuf);

    if (ai >= 0) {
        upper(title, lib.albums[ai].songs[si].title, sizeof title);
        char u1[80], u2[80];
        upper(u1, lib.albums[ai].artist, sizeof u1);
        upper(u2, lib.albums[ai].title, sizeof u2);
        snprintf(sub, sizeof sub, "%s - %s", u1, u2);
    } else {
        strcpy(title, "NO DISC");
        strcpy(sub, "PICK AN ALBUM IN THE LIBRARY");
    }
    text_fit(lx + 16, ly + 94, C_LCD, 1.15f, title, lw - 32);
    text_fit(lx + 16, ly + 120, C_LCD_DIM, 0.8f, sub, lw - 32);

    text(lx + 16, ly + 176, st->shuffle ? C_LCD : C_LCD_OFF, 0.7f, "SHUF");
    text(lx + 72, ly + 176, st->no_skip ? C_LCD : C_LCD_OFF, 0.7f, "NO SKIP");
    if (st->sample_rate) {
        char f[64], in[12], outr[12];
        khz(in, sizeof in, st->sample_rate);
        if (st->bits) snprintf(f, sizeof f, "%s %s/%u", st->codec, in, st->bits);
        else snprintf(f, sizeof f, "%s %s kHz", st->codec, in);
        if (st->output_rate && st->output_rate != st->sample_rate) {
            size_t n = strlen(f);
            khz(outr, sizeof outr, st->output_rate);
            snprintf(f + n, sizeof f - n, " > %s kHz", outr);
        }
        text(lx + lw - 16 - text_w(0.7f, f), ly + 176, C_LCD, 0.7f, f);
    }

    /* Up next: the following songs in play order. */
    text(lx, ly + lh + 38, C_BAR_DIM, 0.62f, "UP NEXT");
    rect(lx + 64, ly + lh + 33, lw - 64, 1, RGBA8(255, 255, 255, 30));
    int shown = 0;
    for (int i = 0; i < 3; i++) {
        if (!up_next[i][0]) break;
        char row[240];
        snprintf(row, sizeof row, "%d.  %s", st->index + 2 + i, up_next[i]);
        text_fit(lx + 4, ly + lh + 62 + i * 22, i == 0 ? C_BAR_TEXT : C_BAR_DIM, 0.75f, row, lw - 8);
        shown++;
    }
    if (!shown && st->index >= 0) text(lx + 4, ly + lh + 62, C_BAR_DIM, 0.75f, "End of the play list");

    rich(lx, BAR_Y - 14, RGBA8(110, 120, 132, 255), 0.64f, "L / R  prev / next     START  pause     {S} shuffle     SELECT  no skipping     {O} back", 1);

    if (error_frames > 0) {
        int y = BAR_Y - 40;
        rect(PANE_X + 12, y, PANE_W - 24, 26, RGBA8(255, 255, 225, 255));
        frame(PANE_X + 12, y, PANE_W - 24, 26, RGBA8(118, 118, 118, 255));
        text_fit(PANE_X + 22, y + 18, C_BLACK, 0.75f, last_error, PANE_W - 44);
    }
}

/* ---------- input ---------- */

static void keep_visible_grid(void) {
    int row = sel_album / COLS, visible = (PANE_H - 64) / TILE_H;
    if (visible < 1) visible = 1;
    if (row < lib_scroll) lib_scroll = row;
    if (row >= lib_scroll + visible) lib_scroll = row - visible + 1;
}

static void keep_visible_list(void) {
    int visible = (PANE_H - 50) / ROW_H;
    if (sel_track < track_scroll) track_scroll = sel_track;
    if (sel_track >= track_scroll + visible) track_scroll = sel_track - visible + 1;
}

static void open_album_view(int album) {
    library_load_tags(&lib.albums[album]);
    open_album = album;
    sel_track = 0;
    track_scroll = 0;
    view = VIEW_ALBUM;
}

static void go_back(void) {
    if (view == VIEW_NOW) view = previous_view == VIEW_NOW ? VIEW_LIBRARY : previous_view;
    else if (view == VIEW_ALBUM) view = VIEW_LIBRARY;
}

static void toggle_now(void) {
    if (view == VIEW_NOW) go_back();
    else {
        previous_view = view;
        view = VIEW_NOW;
    }
}

static void handle_buttons(unsigned pressed, unsigned repeat) {
    unsigned nav = pressed | repeat;
    if (pressed & SCE_CTRL_START) player_toggle();
    if (pressed & SCE_CTRL_LTRIGGER) player_previous();
    if (pressed & SCE_CTRL_RTRIGGER) player_next();
    if (pressed & SCE_CTRL_TRIANGLE) toggle_now();
    if (pressed & SCE_CTRL_CIRCLE) go_back();
    if (pressed & SCE_CTRL_SELECT) {
        PlayerStatus st;
        player_status(&st);
        player_set_no_skip(!st.no_skip);
    }

    if (view == VIEW_LIBRARY && lib.count > 0) {
        if (nav & SCE_CTRL_RIGHT && sel_album + 1 < lib.count) sel_album++;
        if (nav & SCE_CTRL_LEFT && sel_album > 0) sel_album--;
        if (nav & SCE_CTRL_DOWN) sel_album = sel_album + COLS < lib.count ? sel_album + COLS : lib.count - 1;
        if (nav & SCE_CTRL_UP && sel_album - COLS >= 0) sel_album -= COLS;
        keep_visible_grid();
        if (pressed & SCE_CTRL_CROSS) open_album_view(sel_album);
        if (pressed & SCE_CTRL_SQUARE) {
            play_everything_shuffled();
            previous_view = view;
            view = VIEW_NOW;
        }
    } else if (view == VIEW_ALBUM) {
        Album *a = &lib.albums[open_album];
        if (nav & SCE_CTRL_DOWN && sel_track + 1 < a->song_count) sel_track++;
        if (nav & SCE_CTRL_UP && sel_track > 0) sel_track--;
        keep_visible_list();
        if (pressed & (SCE_CTRL_CROSS | SCE_CTRL_SQUARE)) {
            play_album(open_album, sel_track, (pressed & SCE_CTRL_SQUARE) != 0);
            previous_view = view;
            view = VIEW_NOW;
        }
    } else if (view == VIEW_NOW) {
        if (pressed & SCE_CTRL_CROSS) player_toggle();
        if (pressed & SCE_CTRL_SQUARE) {
            PlayerStatus st;
            player_status(&st);
            player_set_shuffle(!st.shuffle);
        }
        PlayerStatus st;
        player_status(&st);
        if (repeat & SCE_CTRL_RIGHT) player_seek(st.position_ms + 10000);
        if (repeat & SCE_CTRL_LEFT) player_seek(st.position_ms > 10000 ? st.position_ms - 10000 : 0);
    }
}

static void handle_tap(int x, int y, const PlayerStatus *st) {
    float cx = W / 2.0f, cy = BAR_Y + 52;
    if (y >= BAR_Y) {
        if (fabsf(x - cx) < 32 && fabsf(y - cy) < 32) player_toggle();
        else if (x > cx - 90 && x < cx - 34 && fabsf(y - cy) < 20) player_previous();
        else if (x > cx + 34 && x < cx + 90 && fabsf(y - cy) < 20) player_next();
        else if (y < BAR_Y + 26 && x > PANE_X + 64 && x < PANE_X + PANE_W - 64 && st->duration_ms)
            player_seek((uint64_t)((x - (PANE_X + 64)) / (float)(PANE_W - 128) * st->duration_ms));
        else if (x > PANE_X + PANE_W - 180) toggle_now();
        else if (x < PANE_X + 110) player_set_shuffle(!st->shuffle);
        else if (x < PANE_X + 300) player_set_no_skip(!st->no_skip);
        return;
    }
    if (y < PANE_Y) {
        if (x < WIN_X + 44) go_back();
        return;
    }
    if (view == VIEW_LIBRARY && y > grid_top()) {
        int col = (x - PANE_X - 12) / (TILE_W + 2), row = (y - grid_top()) / TILE_H + lib_scroll;
        int i = row * COLS + col;
        if (col >= 0 && col < COLS && i < lib.count) {
            sel_album = i;
            open_album_view(i);
        }
    } else if (view == VIEW_ALBUM) {
        Album *a = &lib.albums[open_album];
        int i = (y - (PANE_Y + 44)) / ROW_H + track_scroll;
        if (x > PANE_X + 196 && i >= 0 && i < a->song_count) {
            sel_track = i;
            play_album(open_album, i, 0);
            previous_view = view;
            view = VIEW_NOW;
        }
    } else if (view == VIEW_NOW) {
        if ((x - 250) * (x - 250) + (y - 262) * (y - 262) < 140 * 140) player_toggle();
    }
}

/* ---------- main ---------- */

static void loading_frame(const char *msg) {
    vita2d_start_drawing();
    vita2d_clear_screen();
    if (skin.sky) vita2d_draw_texture(skin.sky, 0, 0);
    text_center(W / 2, H / 2, C_WHITE, 1.0f, msg);
    vita2d_end_drawing();
    vita2d_swap_buffers();
}

int main(void) {
    vita2d_init();
    vita2d_set_clear_color(RGBA8(19, 90, 145, 255));
    vita2d_set_vblank_wait(1);
    font = vita2d_load_default_pgf();
    sceCtrlSetSamplingMode(SCE_CTRL_MODE_ANALOG);
    sceTouchSetSamplingState(SCE_TOUCH_PORT_FRONT, SCE_TOUCH_SAMPLING_STATE_START);
    srand((unsigned)time(NULL));

    loading_frame("okplayer");
    skin_build(&skin);
    loading_frame("Looking for music in ux0:music ...");
    library_scan(&lib);
    covers = calloc(lib.count ? lib.count : 1, sizeof(*covers));
    cover_tried = calloc(lib.count ? lib.count : 1, 1);
    player_init();
    restore_state();
    int save_frames = 0;
    PlayerState last_state = PLAYER_STOPPED;

    unsigned old = 0;
    int hold = 0, touching = 0, tx = 0, ty = 0;
    const char *last_path = (const char *)-1;

    for (;;) {
        SceCtrlData pad;
        memset(&pad, 0, sizeof pad);
        sceCtrlPeekBufferPositive(0, &pad, 1);
        unsigned pressed = pad.buttons & ~old;
        unsigned held = pad.buttons & (SCE_CTRL_UP | SCE_CTRL_DOWN | SCE_CTRL_LEFT | SCE_CTRL_RIGHT);
        unsigned repeat = 0;
        if (held && held == (old & held)) {
            if (++hold > 22 && hold % 5 == 0) repeat = held;
        } else {
            hold = 0;
        }
        old = pad.buttons;

        PlayerStatus st;
        player_status(&st);
        handle_buttons(pressed, repeat);

        SceTouchData touch;
        memset(&touch, 0, sizeof touch);
        sceTouchPeek(SCE_TOUCH_PORT_FRONT, &touch, 1);
        if (touch.reportNum > 0) {
            touching = 1;
            tx = touch.report[0].x / 2;
            ty = touch.report[0].y / 2;
        } else if (touching) {
            touching = 0;
            handle_tap(tx, ty, &st);
        }

        player_status(&st);
        const char *path = player_current_path();
        if (path != last_path) {
            /* The song changed: find it once, and follow its album on the disc label. */
            find_song(path, &cur_album, &cur_song);
            refresh_up_next();
            playing_album = cur_album;
            last_path = path;
        }
        ensure_label(playing_album);
        if (st.error[0] && strcmp(st.error, last_error) != 0) {
            snprintf(last_error, sizeof last_error, "%s", st.error);
            error_frames = 60 * 6;
        }
        if (error_frames > 0) error_frames--;

        /* Spin up and coast down instead of starting and stopping dead. */
        float target = st.state == PLAYER_PLAYING ? 1.0f : 0.0f;
        disc_speed += (target - disc_speed) * (target > disc_speed ? 0.02f : 0.04f);
        disc_angle += disc_speed * (2 * 3.14159265f / (3.6f * 60));
        if (disc_angle > 6.2831853f) disc_angle -= 6.2831853f;

        if (st.state == PLAYER_PLAYING) sceKernelPowerTick(SCE_KERNEL_POWER_TICK_DISABLE_AUTO_SUSPEND);

        /* Save on every pause and every 15 seconds of playback, so a crash or the PS button
           never loses more than that. */
        if ((st.state != last_state && st.state != PLAYER_PLAYING) || (st.state == PLAYER_PLAYING && ++save_frames >= 60 * 15)) {
            save_state(&st);
            save_frames = 0;
        }
        last_state = st.state;

        char title[200], crumb[200];
        if (st.index >= 0 && cur_song >= 0) {
            snprintf(title, sizeof title, "%s - okplayer", lib.albums[cur_album].songs[cur_song].title);
        } else {
            strcpy(title, "okplayer");
        }
        if (view == VIEW_NOW) strcpy(crumb, "Now Playing");
        else if (view == VIEW_ALBUM) snprintf(crumb, sizeof crumb, "Library  >  Albums  >  %s", lib.albums[open_album].title);
        else strcpy(crumb, "Library  >  Albums");

        vita2d_start_drawing();
        vita2d_clear_screen();
        draw_window(title, crumb, view != VIEW_LIBRARY);
        if (view == VIEW_LIBRARY) draw_library();
        else if (view == VIEW_ALBUM) draw_album(&st);
        else draw_now(&st);
        draw_control_bar(&st);
        vita2d_end_drawing();
        vita2d_swap_buffers();
    }
    return 0;
}
