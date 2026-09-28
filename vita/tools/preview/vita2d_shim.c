/* Software implementation of the vita2d stand-in: straight-alpha blending into 960x544. */
#include <math.h>
#include <png.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <ft2build.h>
#include FT_FREETYPE_H

#include "vita2d.h"

#define FW 960
#define FH 544

struct vita2d_texture {
    unsigned w, h;
    uint32_t *data;
};

struct vita2d_pgf {
    FT_Library lib;
    FT_Face face;
};

static uint32_t fb[FH][FW];
static unsigned clear_color = RGBA8(0, 0, 0, 255);

static void blend(int x, int y, uint32_t c, float cov) {
    if (x < 0 || y < 0 || x >= FW || y >= FH) return;
    float a = ((c >> 24) & 0xFF) / 255.0f * cov;
    if (a <= 0) return;
    uint32_t d = fb[y][x];
    float r = (c & 0xFF) * a + (d & 0xFF) * (1 - a);
    float g = ((c >> 8) & 0xFF) * a + ((d >> 8) & 0xFF) * (1 - a);
    float b = ((c >> 16) & 0xFF) * a + ((d >> 16) & 0xFF) * (1 - a);
    fb[y][x] = RGBA8((int)r, (int)g, (int)b, 255);
}

int vita2d_init(void) { return 1; }
void vita2d_set_clear_color(unsigned int color) { clear_color = color; }
void vita2d_set_vblank_wait(int enable) { (void)enable; }
void vita2d_start_drawing(void) {}
void vita2d_clear_screen(void) {
    for (int y = 0; y < FH; y++)
        for (int x = 0; x < FW; x++) fb[y][x] = clear_color;
}
void vita2d_end_drawing(void) {}
void vita2d_swap_buffers(void) {}
void vita2d_wait_rendering_done(void) {}

void vita2d_draw_rectangle(float x, float y, float w, float h, unsigned int color) {
    for (int j = (int)floorf(y); j < (int)ceilf(y + h); j++)
        for (int i = (int)floorf(x); i < (int)ceilf(x + w); i++) blend(i, j, color, 1);
}

void vita2d_draw_line(float x0, float y0, float x1, float y1, unsigned int color) {
    int n = (int)fmaxf(fabsf(x1 - x0), fabsf(y1 - y0)) + 1;
    for (int i = 0; i <= n; i++) blend((int)(x0 + (x1 - x0) * i / n), (int)(y0 + (y1 - y0) * i / n), color, 1);
}

void vita2d_draw_fill_circle(float cx, float cy, float r, unsigned int color) {
    for (int y = (int)(cy - r); y <= (int)(cy + r); y++)
        for (int x = (int)(cx - r); x <= (int)(cx + r); x++) {
            float d = sqrtf((x + 0.5f - cx) * (x + 0.5f - cx) + (y + 0.5f - cy) * (y + 0.5f - cy));
            if (d <= r) blend(x, y, color, fminf(1, r - d + 0.5f));
        }
}

vita2d_texture *vita2d_create_empty_texture(unsigned int w, unsigned int h) {
    vita2d_texture *t = calloc(1, sizeof(*t));
    t->w = w;
    t->h = h;
    t->data = calloc((size_t)w * h, 4);
    return t;
}

vita2d_texture *vita2d_load_PNG_file(const char *path) { (void)path; return NULL; }
vita2d_texture *vita2d_load_JPEG_file(const char *path) { (void)path; return NULL; }
void vita2d_free_texture(vita2d_texture *t) {
    if (!t) return;
    free(t->data);
    free(t);
}
void *vita2d_texture_get_datap(const vita2d_texture *t) { return t->data; }
unsigned int vita2d_texture_get_stride(const vita2d_texture *t) { return t->w * 4; }
unsigned int vita2d_texture_get_width(const vita2d_texture *t) { return t->w; }
unsigned int vita2d_texture_get_height(const vita2d_texture *t) { return t->h; }
SceGxmTextureFormat vita2d_texture_get_format(const vita2d_texture *t) { (void)t; return SCE_GXM_TEXTURE_FORMAT_U8U8U8U8_ABGR; }
void vita2d_texture_set_filters(vita2d_texture *t, SceGxmTextureFilter a, SceGxmTextureFilter b) { (void)t; (void)a; (void)b; }

/* Bilinear sample with straight alpha; out of range is transparent. */
static uint32_t sample(const vita2d_texture *t, float u, float v) {
    u -= 0.5f;
    v -= 0.5f;
    int x0 = (int)floorf(u), y0 = (int)floorf(v);
    float fx = u - x0, fy = v - y0;
    float acc[4] = {0};
    for (int k = 0; k < 4; k++) {
        int x = x0 + (k & 1), y = y0 + (k >> 1);
        float w = ((k & 1) ? fx : 1 - fx) * ((k >> 1) ? fy : 1 - fy);
        if (x < 0 || y < 0 || x >= (int)t->w || y >= (int)t->h) continue;
        uint32_t c = t->data[y * t->w + x];
        float a = ((c >> 24) & 0xFF) / 255.0f;
        acc[0] += (c & 0xFF) * a * w;
        acc[1] += ((c >> 8) & 0xFF) * a * w;
        acc[2] += ((c >> 16) & 0xFF) * a * w;
        acc[3] += a * w;
    }
    if (acc[3] <= 0) return 0;
    return RGBA8((int)(acc[0] / acc[3]), (int)(acc[1] / acc[3]), (int)(acc[2] / acc[3]), (int)(acc[3] * 255));
}

/* Draws a texture with its centre at (cx, cy), scaled and rotated, tinted by `tint`. */
static void draw_tex(const vita2d_texture *t, float cx, float cy, float sx, float sy, float rad, uint32_t tint) {
    if (!t) return;
    float hw = t->w * sx / 2, hh = t->h * sy / 2;
    float rr = sqrtf(hw * hw + hh * hh) + 1;
    float c = cosf(rad), s = sinf(rad);
    float ta = ((tint >> 24) & 0xFF) / 255.0f;
    for (int y = (int)(cy - rr); y <= (int)(cy + rr); y++)
        for (int x = (int)(cx - rr); x <= (int)(cx + rr); x++) {
            float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
            float lx = (dx * c + dy * s) / sx + t->w / 2.0f;
            float ly = (-dx * s + dy * c) / sy + t->h / 2.0f;
            if (lx < -1 || ly < -1 || lx > t->w + 1 || ly > t->h + 1) continue;
            uint32_t p = sample(t, lx, ly);
            float r = (p & 0xFF) * (tint & 0xFF) / 255.0f, g = ((p >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255.0f;
            float b = ((p >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255.0f;
            blend(x, y, RGBA8((int)r, (int)g, (int)b, (int)(((p >> 24) & 0xFF) * ta)), 1);
        }
}

void vita2d_draw_texture(const vita2d_texture *t, float x, float y) {
    if (t) draw_tex(t, x + t->w / 2.0f, y + t->h / 2.0f, 1, 1, 0, 0xFFFFFFFF);
}
void vita2d_draw_texture_scale(const vita2d_texture *t, float x, float y, float sx, float sy) {
    if (t) draw_tex(t, x + t->w * sx / 2, y + t->h * sy / 2, sx, sy, 0, 0xFFFFFFFF);
}
void vita2d_draw_texture_rotate(const vita2d_texture *t, float x, float y, float rad) { draw_tex(t, x, y, 1, 1, rad, 0xFFFFFFFF); }
void vita2d_draw_texture_scale_rotate(const vita2d_texture *t, float x, float y, float sx, float sy, float rad) {
    draw_tex(t, x, y, sx, sy, rad, 0xFFFFFFFF);
}
void vita2d_draw_texture_tint_scale(const vita2d_texture *t, float x, float y, float sx, float sy, unsigned int color) {
    if (t) draw_tex(t, x + t->w * sx / 2, y + t->h * sy / 2, sx, sy, 0, color);
}

/* Text: DejaVu Sans stands in for the Vita's system font at roughly the same size. */
vita2d_pgf *vita2d_load_default_pgf(void) {
    vita2d_pgf *f = calloc(1, sizeof(*f));
    FT_Init_FreeType(&f->lib);
    if (FT_New_Face(f->lib, "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 0, &f->face)) {
        fprintf(stderr, "no DejaVu Sans\n");
        exit(1);
    }
    return f;
}
void vita2d_free_pgf(vita2d_pgf *f) { (void)f; }

static unsigned next_codepoint(const char **s) {
    const unsigned char *p = (const unsigned char *)*s;
    unsigned c = *p++;
    if (c >= 0xF0) { c = (c & 7) << 18 | (p[0] & 0x3F) << 12 | (p[1] & 0x3F) << 6 | (p[2] & 0x3F); p += 3; }
    else if (c >= 0xE0) { c = (c & 15) << 12 | (p[0] & 0x3F) << 6 | (p[1] & 0x3F); p += 2; }
    else if (c >= 0xC0) { c = (c & 31) << 6 | (p[0] & 0x3F); p += 1; }
    *s = (const char *)p;
    return c;
}

static int text_run(vita2d_pgf *f, int x, int y, unsigned color, float scale, const char *text, int draw) {
    FT_Set_Pixel_Sizes(f->face, 0, (FT_UInt)(17 * scale));
    int pen = x;
    while (*text) {
        unsigned cp = next_codepoint(&text);
        if (FT_Load_Char(f->face, cp, draw ? FT_LOAD_RENDER : FT_LOAD_DEFAULT)) continue;
        FT_GlyphSlot g = f->face->glyph;
        if (draw)
            for (unsigned r = 0; r < g->bitmap.rows; r++)
                for (unsigned c = 0; c < g->bitmap.width; c++) {
                    float cov = g->bitmap.buffer[r * g->bitmap.pitch + c] / 255.0f;
                    blend(pen + g->bitmap_left + (int)c, y - g->bitmap_top + (int)r, color, cov);
                }
        pen += (int)(g->advance.x >> 6);
    }
    return pen - x;
}

int vita2d_pgf_draw_text(vita2d_pgf *f, int x, int y, unsigned int color, float scale, const char *text) {
    return text_run(f, x, y, color, scale, text, 1);
}
int vita2d_pgf_text_width(vita2d_pgf *f, float scale, const char *text) { return text_run(f, 0, 0, 0, scale, text, 0); }

int preview_save_png(const char *path) {
    FILE *fp = fopen(path, "wb");
    if (!fp) return 0;
    png_structp png = png_create_write_struct(PNG_LIBPNG_VER_STRING, NULL, NULL, NULL);
    png_infop info = png_create_info_struct(png);
    png_init_io(png, fp);
    png_set_IHDR(png, info, FW, FH, 8, PNG_COLOR_TYPE_RGBA, PNG_INTERLACE_NONE, PNG_COMPRESSION_TYPE_DEFAULT, PNG_FILTER_TYPE_DEFAULT);
    png_write_info(png, info);
    for (int y = 0; y < FH; y++) png_write_row(png, (png_bytep)fb[y]);
    png_write_end(png, NULL);
    png_destroy_write_struct(&png, &info);
    fclose(fp);
    return 1;
}
