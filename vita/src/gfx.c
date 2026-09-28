#include "gfx.h"

#include <math.h>
#include <stdint.h>
#include <string.h>

#define PI 3.14159265f

static inline uint32_t *pixels(vita2d_texture *t) { return (uint32_t *)vita2d_texture_get_datap(t); }
static inline unsigned pitch(vita2d_texture *t) { return vita2d_texture_get_stride(t) / 4; }

static inline float clampf(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }
static inline float mixf(float a, float b, float t) { return a + (b - a) * t; }
static inline float smooth(float e0, float e1, float x) {
    float t = clampf((x - e0) / (e1 - e0), 0, 1);
    return t * t * (3 - 2 * t);
}

static uint32_t rgba(float r, float g, float b, float a) {
    return RGBA8((int)clampf(r, 0, 255), (int)clampf(g, 0, 255), (int)clampf(b, 0, 255), (int)clampf(a, 0, 255));
}

/* Vertical gradient through up to four stops, into an 8 px wide texture that gets stretched. */
static vita2d_texture *vgradient(int h, const float stops[][5], int n) {
    vita2d_texture *t = vita2d_create_empty_texture(8, h);
    uint32_t *p = pixels(t);
    unsigned w = pitch(t);
    for (int y = 0; y < h; y++) {
        float f = (float)y / (h - 1);
        int i = 0;
        while (i < n - 2 && f > stops[i + 1][0]) i++;
        float span = stops[i + 1][0] - stops[i][0];
        float k = span > 0 ? clampf((f - stops[i][0]) / span, 0, 1) : 0;
        uint32_t c = rgba(mixf(stops[i][1], stops[i + 1][1], k), mixf(stops[i][2], stops[i + 1][2], k),
                          mixf(stops[i][3], stops[i + 1][3], k), mixf(stops[i][4], stops[i + 1][4], k));
        for (int x = 0; x < 8; x++) p[y * w + x] = c;
    }
    vita2d_texture_set_filters(t, SCE_GXM_TEXTURE_FILTER_LINEAR, SCE_GXM_TEXTURE_FILTER_LINEAR);
    return t;
}

static vita2d_texture *build_sky(void) {
    vita2d_texture *t = vita2d_create_empty_texture(960, 544);
    uint32_t *p = pixels(t);
    unsigned w = pitch(t);
    for (int y = 0; y < 544; y++)
        for (int x = 0; x < 960; x++) {
            float d = (x / 960.0f * 0.45f + y / 544.0f * 0.55f);
            float r = d < 0.45f ? mixf(47, 19, d / 0.45f) : mixf(19, 10, (d - 0.45f) / 0.55f);
            float g = d < 0.45f ? mixf(143, 90, d / 0.45f) : mixf(90, 47, (d - 0.45f) / 0.55f);
            float b = d < 0.45f ? mixf(206, 145, d / 0.45f) : mixf(145, 82, (d - 0.45f) / 0.55f);
            /* The soft light pooling in the bottom-left corner, as on Windows 7's desktop. */
            float gx = (x - 260) / 700.0f, gy = (y - 620) / 380.0f;
            float glow = clampf(1 - sqrtf(gx * gx + gy * gy), 0, 1);
            r = mixf(r, 126, glow * 0.7f);
            g = mixf(g, 211, glow * 0.7f);
            b = mixf(b, 255, glow * 0.7f);
            p[y * w + x] = rgba(r, g, b, 255);
        }
    return t;
}

static vita2d_texture *build_disc(void) {
    const int s = DISC_SIZE;
    vita2d_texture *t = vita2d_create_empty_texture(s, s);
    uint32_t *p = pixels(t);
    unsigned w = pitch(t);
    float c = s / 2.0f, R = s / 2.0f - 1;
    for (int y = 0; y < s; y++)
        for (int x = 0; x < s; x++) {
            float dx = x + 0.5f - c, dy = y + 0.5f - c;
            float r = sqrtf(dx * dx + dy * dy) / R;
            float a = 255 * (1 - smooth(0.985f, 1.0f, r));
            if (r > 1) { p[y * w + x] = 0; continue; }
            float base = r < 0.30f ? mixf(223, 170, r / 0.30f) : (r < 0.31f ? 233 : mixf(233, 200, (r - 0.31f) / 0.69f));
            /* Grooves: faint rings every few pixels. */
            float groove = (((int)(r * R) % 3) == 0) ? -6 : 0;
            /* A tick on the rim so the rotation reads even with a plain label. */
            float tick = (fabsf(dx) < 0.8f && dy < -0.55f * R) ? 20 : 0;
            float v = base + groove + tick;
            p[y * w + x] = rgba(v - 8, v - 2, v + 6, a);
        }
    vita2d_texture_set_filters(t, SCE_GXM_TEXTURE_FILTER_LINEAR, SCE_GXM_TEXTURE_FILTER_LINEAR);
    return t;
}

/* The same rainbow bands as the Android deck: pink, pale yellow, mint and Aero blue. */
static vita2d_texture *build_sheen(void) {
    const int s = DISC_SIZE;
    vita2d_texture *t = vita2d_create_empty_texture(s, s);
    uint32_t *p = pixels(t);
    unsigned w = pitch(t);
    float c = s / 2.0f, R = s / 2.0f - 1;
    static const float bands[][5] = {
        /* angle 0..1, r, g, b, alpha */
        {0.10f, 245, 169, 184, 0.45f}, {0.14f, 255, 240, 170, 0.35f}, {0.18f, 160, 255, 200, 0.28f},
        {0.23f, 58, 167, 234, 0.45f},  {0.60f, 245, 169, 184, 0.35f}, {0.65f, 255, 240, 170, 0.28f},
        {0.71f, 58, 167, 234, 0.40f},
    };
    for (int y = 0; y < s; y++)
        for (int x = 0; x < s; x++) {
            float dx = x + 0.5f - c, dy = y + 0.5f - c;
            float r = sqrtf(dx * dx + dy * dy) / R;
            if (r > 1 || r < 0.27f) { p[y * w + x] = 0; continue; }
            float ang = (atan2f(dy, dx) + PI) / (2 * PI);
            float cr = 0, cg = 0, cb = 0, ca = 0;
            for (size_t i = 0; i < sizeof(bands) / sizeof(bands[0]); i++) {
                float d = fabsf(ang - bands[i][0]);
                float k = clampf(1 - d / 0.045f, 0, 1) * bands[i][4];
                if (k > ca) { cr = bands[i][1]; cg = bands[i][2]; cb = bands[i][3]; ca = k; }
            }
            ca *= 1 - smooth(0.9f, 1.0f, r);
            p[y * w + x] = rgba(cr, cg, cb, ca * 255);
        }
    vita2d_texture_set_filters(t, SCE_GXM_TEXTURE_FILTER_LINEAR, SCE_GXM_TEXTURE_FILTER_LINEAR);
    return t;
}

static vita2d_texture *build_hub(void) {
    const int s = 48;
    vita2d_texture *t = vita2d_create_empty_texture(s, s);
    uint32_t *p = pixels(t);
    unsigned w = pitch(t);
    float c = s / 2.0f, R = s / 2.0f - 1;
    for (int y = 0; y < s; y++)
        for (int x = 0; x < s; x++) {
            float dx = x + 0.5f - c, dy = y + 0.5f - c;
            float r = sqrtf(dx * dx + dy * dy) / R;
            uint32_t col = 0;
            if (r < 0.34f) col = rgba(5, 6, 7, 255);
            else if (r < 0.72f) {
                float v = r < 0.52f ? mixf(140, 223, (r - 0.34f) / 0.18f) : mixf(223, 125, (r - 0.52f) / 0.20f);
                col = rgba(v, v + 6, v + 12, 255);
            } else if (r < 1) col = rgba(42, 47, 53, 255 * (1 - smooth(0.9f, 1, r)));
            p[y * w + x] = col;
        }
    vita2d_texture_set_filters(t, SCE_GXM_TEXTURE_FILTER_LINEAR, SCE_GXM_TEXTURE_FILTER_LINEAR);
    return t;
}

static vita2d_texture *build_well(void) {
    const int s = 300;
    vita2d_texture *t = vita2d_create_empty_texture(s, s);
    uint32_t *p = pixels(t);
    unsigned w = pitch(t);
    float c = s / 2.0f;
    for (int y = 0; y < s; y++)
        for (int x = 0; x < s; x++) {
            float dx = x + 0.5f - c, dy = y + 0.5f - c;
            float d = sqrtf(dx * dx + dy * dy);
            uint32_t col = 0;
            if (d < 138) {
                /* Dark recess, darker toward the top edge where the lip shades it. */
                float shade = 11 + 8 * smooth(90, 125, d) - 10 * clampf(-dy / 138.0f, 0, 1) * smooth(100, 138, d);
                col = rgba(shade, shade + 1, shade + 3, 255);
            } else if (d < 140) col = rgba(43, 47, 53, 255);
            else if (d < 145) col = rgba(19, 21, 24, 255);
            else if (d < 147) col = rgba(51, 56, 63, 255 * (1 - smooth(146, 147, d)));
            p[y * w + x] = col;
        }
    vita2d_texture_set_filters(t, SCE_GXM_TEXTURE_FILTER_LINEAR, SCE_GXM_TEXTURE_FILTER_LINEAR);
    return t;
}

static vita2d_texture *build_orb(int s, int lit) {
    vita2d_texture *t = vita2d_create_empty_texture(s, s);
    uint32_t *p = pixels(t);
    unsigned w = pitch(t);
    float c = s / 2.0f, R = s / 2.0f - 1;
    for (int y = 0; y < s; y++)
        for (int x = 0; x < s; x++) {
            float dx = x + 0.5f - c, dy = y + 0.5f - c;
            float r = sqrtf(dx * dx + dy * dy) / R;
            if (r > 1) { p[y * w + x] = 0; continue; }
            /* Radial body lit from the top, like the WMP 12 play button. */
            float ly = (y + 0.5f - s * 0.3f) / R, lx = dx / R;
            float lr = sqrtf(lx * lx + ly * ly);
            float r0 = 143, g0 = 211, b0 = 255, r1 = 44, g1 = 134, b1 = 207, r2 = 6, g2 = 24, b2 = 44;
            if (!lit) { r0 = 200; g0 = 205; b0 = 212; r1 = 110; g1 = 120; b1 = 132; r2 = 30; g2 = 36; b2 = 44; }
            float cr = lr < 0.5f ? mixf(r0, r1, lr / 0.5f) : mixf(r1, r2, clampf((lr - 0.5f) / 0.8f, 0, 1));
            float cg = lr < 0.5f ? mixf(g0, g1, lr / 0.5f) : mixf(g1, g2, clampf((lr - 0.5f) / 0.8f, 0, 1));
            float cb = lr < 0.5f ? mixf(b0, b1, lr / 0.5f) : mixf(b1, b2, clampf((lr - 0.5f) / 0.8f, 0, 1));
            /* Glass highlight across the top half. */
            float hx = dx / (R * 0.72f), hy = (y + 0.5f - s * 0.26f) / (R * 0.46f);
            float h = hx * hx + hy * hy;
            if (h < 1) {
                float k = (1 - h) * 0.55f * clampf(1 - (y / (float)s) * 1.8f, 0, 1);
                cr = mixf(cr, 255, k); cg = mixf(cg, 255, k); cb = mixf(cb, 255, k);
            }
            float edge = r > 0.95f ? 0.35f : 1;
            p[y * w + x] = rgba(cr * edge, cg * edge, cb * edge, 255 * (1 - smooth(0.97f, 1, r)));
        }
    vita2d_texture_set_filters(t, SCE_GXM_TEXTURE_FILTER_LINEAR, SCE_GXM_TEXTURE_FILTER_LINEAR);
    return t;
}

static vita2d_texture *build_capsule(void) {
    const int w0 = 176, h0 = 36;
    vita2d_texture *t = vita2d_create_empty_texture(w0, h0);
    uint32_t *p = pixels(t);
    unsigned w = pitch(t);
    float r = h0 / 2.0f;
    for (int y = 0; y < h0; y++)
        for (int x = 0; x < w0; x++) {
            float cx = clampf(x + 0.5f, r, w0 - r), dx = x + 0.5f - cx, dy = y + 0.5f - r;
            float d = sqrtf(dx * dx + dy * dy);
            if (d > r) { p[y * w + x] = 0; continue; }
            float f = (float)y / h0;
            float v = f < 0.48f ? mixf(90, 43, f / 0.48f) : (f < 0.52f ? 16 : mixf(16, 35, (f - 0.52f) / 0.48f));
            if (d > r - 1.2f) v = 0;
            p[y * w + x] = rgba(v, v + 4, v + 8, 255 * (1 - smooth(r - 0.5f, r, d)));
        }
    vita2d_texture_set_filters(t, SCE_GXM_TEXTURE_FILTER_LINEAR, SCE_GXM_TEXTURE_FILTER_LINEAR);
    return t;
}

void skin_build(Skin *s) {
    s->sky = build_sky();
    s->disc = build_disc();
    s->sheen = build_sheen();
    s->hub = build_hub();
    s->well = build_well();
    s->orb = build_orb(60, 1);
    s->orb_dim = build_orb(40, 0);
    s->capsule = build_capsule();
    static const float bar[][5] = {{0, 59, 62, 67, 255}, {0.46f, 35, 38, 42, 255}, {0.5f, 10, 11, 13, 255}, {1, 20, 22, 25, 255}};
    s->bar = vgradient(96, bar, 4);
    static const float tool[][5] = {{0, 190, 216, 238, 255}, {1, 160, 196, 226, 255}};
    s->toolbar = vgradient(32, tool, 2);
    static const float cmd[][5] = {{0, 250, 252, 254, 255}, {1, 227, 236, 245, 255}};
    s->cmdbar = vgradient(32, cmd, 2);
    static const float lcd[][5] = {{0, 10, 32, 37, 255}, {1, 7, 24, 28, 255}};
    s->lcd = vgradient(64, lcd, 2);
}

void skin_free(Skin *s) {
    vita2d_texture **all = (vita2d_texture **)s;
    for (size_t i = 0; i < sizeof(Skin) / sizeof(vita2d_texture *); i++)
        if (all[i]) vita2d_free_texture(all[i]);
    memset(s, 0, sizeof(*s));
}

static int is_png(const char *path) {
    size_t n = strlen(path);
    return n > 4 && (path[n - 1] == 'g' || path[n - 1] == 'G') && (path[n - 2] == 'n' || path[n - 2] == 'N');
}

vita2d_texture *cover_load(const char *path, int size) {
    vita2d_texture *src = is_png(path) ? vita2d_load_PNG_file(path) : vita2d_load_JPEG_file(path);
    if (!src) return NULL;
    int sw = vita2d_texture_get_width(src), sh = vita2d_texture_get_height(src);
    uint32_t *sp = pixels(src);
    unsigned sp_pitch = pitch(src);
    vita2d_texture *dst = vita2d_create_empty_texture(size, size);
    uint32_t *dp = pixels(dst);
    unsigned dp_pitch = pitch(dst);
    /* Centre-crop to a square, then average blocks down to the target size. */
    int side = sw < sh ? sw : sh, ox = (sw - side) / 2, oy = (sh - side) / 2;
    float scale = (float)side / size;
    int box = scale < 1 ? 1 : (int)scale;
    if (box > 6) box = 6;
    for (int y = 0; y < size; y++)
        for (int x = 0; x < size; x++) {
            int bx = ox + (int)(x * scale), by = oy + (int)(y * scale);
            unsigned r = 0, g = 0, b = 0, n = 0;
            for (int j = 0; j < box; j++)
                for (int i = 0; i < box; i++) {
                    int px = bx + i, py = by + j;
                    if (px >= sw || py >= sh) continue;
                    uint32_t c = sp[py * sp_pitch + px];
                    r += c & 0xFF; g += (c >> 8) & 0xFF; b += (c >> 16) & 0xFF; n++;
                }
            if (!n) n = 1;
            dp[y * dp_pitch + x] = RGBA8(r / n, g / n, b / n, 255);
        }
    vita2d_wait_rendering_done();
    vita2d_free_texture(src);
    vita2d_texture_set_filters(dst, SCE_GXM_TEXTURE_FILTER_LINEAR, SCE_GXM_TEXTURE_FILTER_LINEAR);
    return dst;
}

static vita2d_texture *label_with(const vita2d_texture *cover, uint32_t fill) {
    const int s = LABEL_SIZE;
    vita2d_texture *t = vita2d_create_empty_texture(s, s);
    uint32_t *p = pixels(t);
    unsigned w = pitch(t);
    const uint32_t *cp = cover ? (const uint32_t *)vita2d_texture_get_datap(cover) : NULL;
    unsigned cpitch = cover ? vita2d_texture_get_stride(cover) / 4 : 0;
    int cs = cover ? (int)vita2d_texture_get_width(cover) : 0;
    float c = s / 2.0f, R = s / 2.0f - 1;
    for (int y = 0; y < s; y++)
        for (int x = 0; x < s; x++) {
            float dx = x + 0.5f - c, dy = y + 0.5f - c;
            float r = sqrtf(dx * dx + dy * dy) / R;
            if (r > 1) { p[y * w + x] = 0; continue; }
            uint32_t col = fill;
            if (cp) col = cp[(y * cs / s) * cpitch + (x * cs / s)];
            float a = 255 * (1 - smooth(0.97f, 1, r));
            /* A thin white ring where the label meets the silver. */
            if (r > 0.94f) col = RGBA8(235, 240, 245, 255);
            p[y * w + x] = (col & 0x00FFFFFF) | ((uint32_t)a << 24);
        }
    vita2d_texture_set_filters(t, SCE_GXM_TEXTURE_FILTER_LINEAR, SCE_GXM_TEXTURE_FILTER_LINEAR);
    return t;
}

vita2d_texture *label_from_cover(const vita2d_texture *cover) { return label_with(cover, 0); }

unsigned hue_color(const char *name, float value, float saturation) {
    unsigned h = 5381;
    for (const char *c = name; *c; c++) h = h * 33 ^ (unsigned char)*c;
    float hue = (float)(h % 360) / 60.0f;
    float cc = value * saturation, xx = cc * (1 - fabsf(fmodf(hue, 2) - 1)), m = value - cc;
    float r, g, b;
    switch ((int)hue) {
        case 0: r = cc; g = xx; b = 0; break;
        case 1: r = xx; g = cc; b = 0; break;
        case 2: r = 0; g = cc; b = xx; break;
        case 3: r = 0; g = xx; b = cc; break;
        case 4: r = xx; g = 0; b = cc; break;
        default: r = cc; g = 0; b = xx; break;
    }
    return RGBA8((int)((r + m) * 255), (int)((g + m) * 255), (int)((b + m) * 255), 255);
}

vita2d_texture *label_generated(const char *name) { return label_with(NULL, hue_color(name, 0.62f, 0.5f)); }
