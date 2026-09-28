#include "player.h"

#include <ctype.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <psp2/audioout.h>
#include <psp2/kernel/threadmgr.h>

#include "dr_flac.h"
#include "dr_mp3.h"
#include "dr_wav.h"
#define STB_VORBIS_HEADER_ONLY
#include "stb_vorbis.c"

#define GRAIN 1024          /* frames per call to the audio port */
#define MAX_CHANNELS 8
#define MAX_FACTOR 4        /* 192 kHz down to 48 kHz */

typedef enum { DEC_NONE, DEC_FLAC, DEC_MP3, DEC_WAV, DEC_VORBIS } DecType;

typedef struct {
    DecType type;
    drflac *flac;
    drmp3 mp3;
    drwav wav;
    stb_vorbis *vorbis;
    unsigned rate, channels, bits;
    uint64_t total, pos; /* in frames */
} Decoder;

static const unsigned supported_rates[] = {8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000};

static struct {
    SceUID mutex;
    SceUID thread;
    int port;
    volatile int running;

    char *paths[PLAYER_MAX_QUEUE];
    int order[PLAYER_MAX_QUEUE];
    int count;
    int index; /* into order[] */

    Decoder dec;
    PlayerState state;
    int shuffle, no_skip;
    unsigned out_rate;
    int factor;         /* integer decimation, 1 when the rate is used as is */
    double step;        /* >0 when resampling linearly instead */
    char error[128];
    float level;
} P;

static int16_t in_buf[GRAIN * MAX_FACTOR * MAX_CHANNELS + MAX_CHANNELS];
static int16_t out_buf[GRAIN * 2];

static void lock(void) { sceKernelLockMutex(P.mutex, 1, NULL); }
static void unlock(void) { sceKernelUnlockMutex(P.mutex, 1); }

static int ends_with(const char *s, const char *ext) {
    size_t n = strlen(s), m = strlen(ext);
    if (n < m) return 0;
    for (size_t i = 0; i < m; i++)
        if (tolower((unsigned char)s[n - m + i]) != ext[i]) return 0;
    return 1;
}

static void dec_close(Decoder *d) {
    switch (d->type) {
        case DEC_FLAC: drflac_close(d->flac); break;
        case DEC_MP3: drmp3_uninit(&d->mp3); break;
        case DEC_WAV: drwav_uninit(&d->wav); break;
        case DEC_VORBIS: stb_vorbis_close(d->vorbis); break;
        default: break;
    }
    memset(d, 0, sizeof(*d));
}

static int dec_open(Decoder *d, const char *path) {
    memset(d, 0, sizeof(*d));
    if (ends_with(path, ".flac")) {
        d->flac = drflac_open_file(path, NULL);
        if (!d->flac) return 0;
        d->type = DEC_FLAC;
        d->rate = d->flac->sampleRate;
        d->channels = d->flac->channels;
        d->bits = d->flac->bitsPerSample;
        d->total = d->flac->totalPCMFrameCount;
    } else if (ends_with(path, ".mp3")) {
        if (!drmp3_init_file(&d->mp3, path, NULL)) return 0;
        d->type = DEC_MP3;
        d->rate = d->mp3.sampleRate;
        d->channels = d->mp3.channels;
        d->total = drmp3_get_pcm_frame_count(&d->mp3);
    } else if (ends_with(path, ".wav")) {
        if (!drwav_init_file(&d->wav, path, NULL)) return 0;
        d->type = DEC_WAV;
        d->rate = d->wav.sampleRate;
        d->channels = d->wav.channels;
        d->bits = d->wav.bitsPerSample;
        d->total = d->wav.totalPCMFrameCount;
    } else if (ends_with(path, ".ogg") || ends_with(path, ".oga")) {
        int err = 0;
        d->vorbis = stb_vorbis_open_filename(path, &err, NULL);
        if (!d->vorbis) return 0;
        stb_vorbis_info info = stb_vorbis_get_info(d->vorbis);
        d->type = DEC_VORBIS;
        d->rate = info.sample_rate;
        d->channels = info.channels;
        d->total = stb_vorbis_stream_length_in_samples(d->vorbis);
    } else {
        return 0;
    }
    if (d->channels < 1 || d->channels > MAX_CHANNELS || d->rate == 0) {
        dec_close(d);
        return 0;
    }
    return 1;
}

static uint64_t dec_read(Decoder *d, int16_t *out, uint64_t frames) {
    uint64_t n = 0;
    switch (d->type) {
        case DEC_FLAC: n = drflac_read_pcm_frames_s16(d->flac, frames, out); break;
        case DEC_MP3: n = drmp3_read_pcm_frames_s16(&d->mp3, frames, out); break;
        case DEC_WAV: n = drwav_read_pcm_frames_s16(&d->wav, frames, out); break;
        case DEC_VORBIS:
            n = (uint64_t)stb_vorbis_get_samples_short_interleaved(d->vorbis, (int)d->channels, out, (int)(frames * d->channels));
            break;
        default: break;
    }
    d->pos += n;
    return n;
}

static void dec_seek(Decoder *d, uint64_t frame) {
    if (d->total && frame >= d->total) frame = d->total - 1;
    switch (d->type) {
        case DEC_FLAC: drflac_seek_to_pcm_frame(d->flac, frame); break;
        case DEC_MP3: drmp3_seek_to_pcm_frame(&d->mp3, frame); break;
        case DEC_WAV: drwav_seek_to_pcm_frame(&d->wav, frame); break;
        case DEC_VORBIS: stb_vorbis_seek(d->vorbis, (unsigned)frame); break;
        default: return;
    }
    d->pos = frame;
}

static const char *codec_name(DecType t) {
    switch (t) {
        case DEC_FLAC: return "FLAC";
        case DEC_MP3: return "MP3";
        case DEC_WAV: return "WAV";
        case DEC_VORBIS: return "Vorbis";
        default: return "";
    }
}

/* Picks an output rate the Vita supports: the file's own, or an exact divisor of it. */
static void configure_output(unsigned rate) {
    P.factor = 1;
    P.step = 0;
    P.out_rate = 48000;
    for (size_t i = 0; i < sizeof(supported_rates) / sizeof(supported_rates[0]); i++)
        if (supported_rates[i] == rate) P.out_rate = rate;
    if (P.out_rate != rate) {
        if (rate % 44100 == 0 && rate / 44100 <= MAX_FACTOR) {
            P.out_rate = 44100;
            P.factor = rate / 44100;
        } else if (rate % 48000 == 0 && rate / 48000 <= MAX_FACTOR) {
            P.out_rate = 48000;
            P.factor = rate / 48000;
        } else {
            P.out_rate = 48000;
            P.step = (double)rate / 48000.0;
        }
    }
    sceAudioOutSetConfig(P.port, GRAIN, P.out_rate, SCE_AUDIO_OUT_MODE_STEREO);
}

/* Takes the left and right channel of a frame; mono is copied to both sides. */
static inline void frame_lr(const int16_t *f, unsigned ch, int *l, int *r) {
    *l = f[0];
    *r = ch > 1 ? f[1] : f[0];
}

static void set_error(const char *path, const char *what) {
    const char *name = strrchr(path, '/');
    snprintf(P.error, sizeof(P.error), "Couldn't play %s: %s", name ? name + 1 : path, what);
}

/* Opens order[index]; skips files that won't open. Returns 0 when nothing could be opened. */
static int load_current(void) {
    dec_close(&P.dec);
    for (int tries = 0; tries < P.count; tries++) {
        const char *path = P.paths[P.order[P.index]];
        if (dec_open(&P.dec, path)) {
            configure_output(P.dec.rate);
            return 1;
        }
        set_error(path, "unsupported or damaged file");
        if (P.index + 1 >= P.count) break;
        P.index++;
    }
    P.state = PLAYER_STOPPED;
    return 0;
}

static void make_order(int keep_current) {
    int current = (P.index >= 0 && P.index < P.count) ? P.order[P.index] : 0;
    for (int i = 0; i < P.count; i++) P.order[i] = i;
    if (P.shuffle) {
        for (int i = P.count - 1; i > 0; i--) {
            int j = rand() % (i + 1);
            int t = P.order[i];
            P.order[i] = P.order[j];
            P.order[j] = t;
        }
        if (keep_current) {
            /* Put the song that's playing first so shuffling doesn't interrupt it. */
            for (int i = 0; i < P.count; i++)
                if (P.order[i] == current) {
                    P.order[i] = P.order[0];
                    P.order[0] = current;
                }
            P.index = 0;
        }
    } else if (keep_current) {
        P.index = current;
    }
}

/* Fills out_buf with GRAIN stereo frames. Returns frames produced; 0 at the end of the song. */
static int render(void) {
    Decoder *d = &P.dec;
    unsigned ch = d->channels;
    int produced = 0;
    double sum = 0;

    if (P.step > 0) {
        uint64_t need = (uint64_t)(GRAIN * P.step) + 2;
        uint64_t got = dec_read(d, in_buf, need);
        if (got < 2) return 0;
        for (int i = 0; i < GRAIN; i++) {
            double x = i * P.step;
            uint64_t a = (uint64_t)x;
            if (a + 1 >= got) break;
            double t = x - (double)a;
            int l0, r0, l1, r1;
            frame_lr(in_buf + a * ch, ch, &l0, &r0);
            frame_lr(in_buf + (a + 1) * ch, ch, &l1, &r1);
            out_buf[i * 2] = (int16_t)(l0 + (l1 - l0) * t);
            out_buf[i * 2 + 1] = (int16_t)(r0 + (r1 - r0) * t);
            produced++;
        }
        /* Keep the reported position in step with what was actually output. */
        d->pos -= got - (uint64_t)(produced * P.step);
        dec_seek(d, d->pos);
    } else {
        int f = P.factor;
        uint64_t got = dec_read(d, in_buf, (uint64_t)GRAIN * f);
        produced = (int)(got / f);
        for (int i = 0; i < produced; i++) {
            int l = 0, r = 0;
            for (int k = 0; k < f; k++) {
                int a, b;
                frame_lr(in_buf + ((uint64_t)i * f + k) * ch, ch, &a, &b);
                l += a;
                r += b;
            }
            out_buf[i * 2] = (int16_t)(l / f);
            out_buf[i * 2 + 1] = (int16_t)(r / f);
        }
    }
    if (produced == 0) return 0;
    for (int i = 0; i < produced * 2; i += 8) sum += (double)out_buf[i] * out_buf[i];
    float rms = (float)sqrt(sum / (produced / 4 + 1)) / 32768.0f;
    P.level = P.level * 0.7f + fminf(rms * 3.0f, 1.0f) * 0.3f;
    for (int i = produced * 2; i < GRAIN * 2; i++) out_buf[i] = 0;
    return produced;
}

static int audio_thread(SceSize args, void *argp) {
    (void)args;
    (void)argp;
    while (P.running) {
        lock();
        if (P.state != PLAYER_PLAYING || P.dec.type == DEC_NONE) {
            P.level *= 0.8f;
            unlock();
            sceKernelDelayThread(10 * 1000);
            continue;
        }
        int produced = render();
        if (produced == 0) {
            /* End of the song: move on, or stop after the last one. */
            if (P.index + 1 < P.count) {
                P.index++;
                load_current();
            } else {
                dec_close(&P.dec);
                P.state = PLAYER_STOPPED;
                P.index = 0;
                if (P.count > 0) load_current();
                P.state = PLAYER_STOPPED;
            }
            unlock();
            continue;
        }
        unlock();
        sceAudioOutOutput(P.port, out_buf);
    }
    return sceKernelExitDeleteThread(0);
}

int player_init(void) {
    memset(&P, 0, sizeof(P));
    P.index = -1;
    P.mutex = sceKernelCreateMutex("okplayer_audio", 0, 0, NULL);
    P.port = sceAudioOutOpenPort(SCE_AUDIO_OUT_PORT_TYPE_BGM, GRAIN, 48000, SCE_AUDIO_OUT_MODE_STEREO);
    if (P.port < 0) return 0;
    int vol[2] = {SCE_AUDIO_VOLUME_0DB, SCE_AUDIO_VOLUME_0DB};
    sceAudioOutSetVolume(P.port, SCE_AUDIO_VOLUME_FLAG_L_CH | SCE_AUDIO_VOLUME_FLAG_R_CH, vol);
    P.running = 1;
    P.thread = sceKernelCreateThread("okplayer_audio", audio_thread, 0x10000100, 0x40000, 0, 0, NULL);
    if (P.thread < 0) return 0;
    sceKernelStartThread(P.thread, 0, NULL);
    return 1;
}

void player_shutdown(void) {
    P.running = 0;
    sceKernelWaitThreadEnd(P.thread, NULL, NULL);
    dec_close(&P.dec);
    for (int i = 0; i < P.count; i++) free(P.paths[i]);
    sceAudioOutReleasePort(P.port);
    sceKernelDeleteMutex(P.mutex);
}

void player_play(const char *const *paths, int count, int start) {
    lock();
    for (int i = 0; i < P.count; i++) free(P.paths[i]);
    if (count > PLAYER_MAX_QUEUE) count = PLAYER_MAX_QUEUE;
    for (int i = 0; i < count; i++) P.paths[i] = strdup(paths[i]);
    P.count = count;
    P.index = start;
    make_order(1);
    P.error[0] = 0;
    if (count > 0 && load_current()) P.state = PLAYER_PLAYING;
    unlock();
}

void player_toggle(void) {
    lock();
    if (P.state == PLAYER_PLAYING) P.state = PLAYER_PAUSED;
    else if (P.dec.type != DEC_NONE) P.state = PLAYER_PLAYING;
    unlock();
}

void player_next(void) {
    lock();
    if (!P.no_skip && P.index + 1 < P.count) {
        P.index++;
        if (load_current() && P.state != PLAYER_PAUSED) P.state = PLAYER_PLAYING;
    }
    unlock();
}

void player_previous(void) {
    lock();
    uint64_t ms = P.dec.rate ? P.dec.pos * 1000 / P.dec.rate : 0;
    if (ms > 3000 || P.no_skip || P.index == 0) {
        dec_seek(&P.dec, 0);
    } else {
        P.index--;
        load_current();
    }
    unlock();
}

void player_seek(uint64_t position_ms) {
    lock();
    if (P.dec.type != DEC_NONE) {
        uint64_t frame = position_ms * P.dec.rate / 1000;
        if (!P.no_skip || frame <= P.dec.pos) dec_seek(&P.dec, frame);
    }
    unlock();
}

void player_set_shuffle(int on) {
    lock();
    if (!P.no_skip || !on) {
        P.shuffle = on;
        make_order(1);
    }
    unlock();
}

void player_set_no_skip(int on) {
    lock();
    P.no_skip = on;
    if (on && P.shuffle) {
        P.shuffle = 0;
        make_order(1);
    }
    unlock();
}

const char *player_current_path(void) {
    if (P.count == 0 || P.index < 0 || P.index >= P.count) return NULL;
    return P.paths[P.order[P.index]];
}

void player_status(PlayerStatus *s) {
    lock();
    memset(s, 0, sizeof(*s));
    s->state = P.state;
    s->index = P.count ? P.index : -1;
    s->count = P.count;
    if (P.dec.rate) {
        s->position_ms = P.dec.pos * 1000 / P.dec.rate;
        s->duration_ms = P.dec.total * 1000 / P.dec.rate;
    }
    s->sample_rate = P.dec.rate;
    s->bits = P.dec.bits;
    s->channels = P.dec.channels;
    s->output_rate = P.out_rate;
    s->codec = codec_name(P.dec.type);
    s->shuffle = P.shuffle;
    s->no_skip = P.no_skip;
    s->level = P.level;
    snprintf(s->error, sizeof(s->error), "%s", P.error);
    unlock();
}
