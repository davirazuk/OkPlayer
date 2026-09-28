#ifndef OKPLAYER_PLAYER_H
#define OKPLAYER_PLAYER_H

#include <stdint.h>

#define PLAYER_MAX_QUEUE 512

typedef enum { PLAYER_STOPPED, PLAYER_PLAYING, PLAYER_PAUSED } PlayerState;

typedef struct {
    PlayerState state;
    int index;              /* position in the queue, -1 when empty */
    int count;              /* songs in the queue */
    uint64_t position_ms;
    uint64_t duration_ms;
    unsigned sample_rate;   /* of the file */
    unsigned bits;          /* of the file, 0 when unknown (lossy) */
    unsigned channels;
    unsigned output_rate;   /* what the Vita's audio output runs at */
    const char *codec;      /* "FLAC", "MP3", "WAV", "Vorbis" */
    int shuffle;
    int no_skip;
    char error[128];        /* last file that failed, shown for a few seconds */
    float level;            /* rough loudness 0..1 for the deck's glow */
} PlayerStatus;

int player_init(void);
void player_shutdown(void);

/* Replaces the queue with the given files and starts at `start`. Paths are copied. */
void player_play(const char *const *paths, int count, int start);

/* Like player_play, starting at position_ms and optionally paused (used to resume). */
void player_open(const char *const *paths, int count, int start, uint64_t position_ms, int paused);
void player_toggle(void);
void player_next(void);
void player_previous(void);
void player_seek(uint64_t position_ms);
void player_set_shuffle(int on);
void player_set_no_skip(int on);

/* Path of the current song, or NULL. Valid until the queue changes. */
const char *player_current_path(void);

/* Path `offset` songs after the current one in play order, or NULL past the end. */
const char *player_queue_path(int offset);
void player_status(PlayerStatus *out);

#endif
