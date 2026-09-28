/*
 * Renders okplayer's Vita screens to PNGs on a desktop, using the real drawing code
 * from src/main.c and src/gfx.c on top of the vita2d stand-in. See build.sh.
 */
#include <stdio.h>

#define main okplayer_main
#include "../../src/main.c"
#undef main

/* ---- stand-ins for the player and tag reader ---- */

static PlayerStatus fake;
static const char *fake_path;

int player_init(void) { return 1; }
void player_shutdown(void) {}
void player_play(const char *const *paths, int count, int start) { (void)paths; (void)count; (void)start; }
void player_toggle(void) {}
void player_next(void) {}
void player_previous(void) {}
void player_seek(uint64_t ms) { (void)ms; }
void player_set_shuffle(int on) { (void)on; }
void player_set_no_skip(int on) { (void)on; }
const char *player_current_path(void) { return fake_path; }
const char *player_queue_path(int offset) { return offset + 1 < 12 ? lib.albums[0].songs[1 + offset].path : NULL; }
void player_status(PlayerStatus *s) { *s = fake; }
void library_load_tags(Album *album) { (void)album; }
void library_scan(Library *l) { (void)l; }

static void add_album(int i, const char *title, const char *artist, const char *const *songs, int n) {
    Album *a = &lib.albums[i];
    a->title = strdup(title);
    a->artist = strdup(artist);
    a->songs = calloc(n, sizeof(Song));
    a->song_count = n;
    for (int k = 0; k < n; k++) {
        char path[256];
        snprintf(path, sizeof path, "ux0:music/%s/%s/%02d %s.flac", artist, title, k + 1, songs[k]);
        a->songs[k].path = strdup(path);
        a->songs[k].title = strdup(songs[k]);
        a->songs[k].number = k + 1;
    }
    lib.song_total += n;
}

static void render(int v, const char *dir, const char *name) {
    view = v;
    char title[200], crumb[200], out[512];
    if (cur_song >= 0) snprintf(title, sizeof title, "%s - okplayer", lib.albums[cur_album].songs[cur_song].title);
    else strcpy(title, "okplayer");
    if (view == VIEW_NOW) strcpy(crumb, "Now Playing");
    else if (view == VIEW_ALBUM) snprintf(crumb, sizeof crumb, "Library  >  Albums  >  %s", lib.albums[open_album].title);
    else strcpy(crumb, "Library  >  Albums");

    vita2d_start_drawing();
    vita2d_clear_screen();
    draw_window(title, crumb, view != VIEW_LIBRARY);
    if (view == VIEW_LIBRARY) draw_library();
    else if (view == VIEW_ALBUM) draw_album(&fake);
    else draw_now(&fake);
    draw_control_bar(&fake);
    snprintf(out, sizeof out, "%s/%s", dir, name);
    preview_save_png(out);
    printf("wrote %s\n", out);
}

int main(int argc, char **argv) {
    const char *dir = argc > 1 ? argv[1] : ".";
    vita2d_init();
    vita2d_set_clear_color(RGBA8(19, 90, 145, 255));
    font = vita2d_load_default_pgf();
    skin_build(&skin);

    static const char *const ok[] = {"Airbag", "Paranoid Android", "Subterranean Homesick Alien", "Exit Music (For a Film)",
                                     "Let Down", "Karma Police", "Fitter Happier", "Electioneering",
                                     "Climbing Up the Walls", "No Surprises", "Lucky", "The Tourist"};
    static const char *const strokes[] = {"Is This It", "The Modern Age", "Soma"};
    static const char *const af[] = {"Never Meant", "The Summer Ends"};
    static const char *const ph[] = {"You", "Creep"};
    static const char *const nat[] = {"Arrival"};
    static const char *const gh[] = {"Ghost"};
    static const char *const ir[] = {"15 Step", "Bodysnatchers", "Nude"};
    static const char *const bends[] = {"Planet Telex", "The Bends", "High and Dry"};
    static const char *const kid[] = {"Everything in Its Right Place", "Kid A"};
    lib.albums = calloc(9, sizeof(Album));
    lib.count = 9;
    add_album(0, "OK Computer", "Radiohead", ok, 12);
    add_album(1, "Is This It", "The Strokes", strokes, 3);
    add_album(2, "American Football", "American Football", af, 2);
    add_album(3, "Pablo Honey", "Radiohead", ph, 2);
    add_album(4, "To See the Next Part of the Dream", "Parannoul", nat, 1);
    add_album(5, "Ghost", "Panchiko", gh, 1);
    add_album(6, "In Rainbows", "Radiohead", ir, 3);
    add_album(7, "The Bends", "Radiohead", bends, 3);
    add_album(8, "Kid A", "Radiohead", kid, 2);
    covers = calloc(lib.count, sizeof(*covers));
    cover_tried = calloc(lib.count, 1);
    memset(cover_tried, 1, lib.count);

    fake.state = PLAYER_PLAYING;
    fake.index = 1;
    fake.count = 12;
    fake.position_ms = 151000;
    fake.duration_ms = 383000;
    fake.sample_rate = 96000;
    fake.bits = 24;
    fake.channels = 2;
    fake.output_rate = 48000;
    fake.codec = "FLAC";
    fake.level = 0.6f;
    fake_path = lib.albums[0].songs[1].path;
    find_song(fake_path, &cur_album, &cur_song);
    refresh_up_next();
    playing_album = cur_album;
    ensure_label(playing_album);
    disc_angle = 0.7f;

    sel_album = 1;
    render(VIEW_LIBRARY, dir, "vita_library.png");
    open_album = 0;
    sel_track = 4;
    render(VIEW_ALBUM, dir, "vita_album.png");
    render(VIEW_NOW, dir, "vita_now_playing.png");
    return 0;
}
