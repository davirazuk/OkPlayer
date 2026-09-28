/*
 * A desktop stand-in for the parts of vita2d okplayer uses. It draws into an image in
 * memory so the real drawing code can be rendered to PNGs off the Vita (see preview.c).
 */
#ifndef PREVIEW_VITA2D_H
#define PREVIEW_VITA2D_H

#include <stdint.h>

#define RGBA8(r, g, b, a) ((((a) & 0xFF) << 24) | (((b) & 0xFF) << 16) | (((g) & 0xFF) << 8) | (((r) & 0xFF) << 0))

typedef enum {
    SCE_GXM_TEXTURE_FORMAT_U8U8U8U8_ABGR = 0,
    SCE_GXM_TEXTURE_FORMAT_U8U8U8_BGR = 1,
} SceGxmTextureFormat;

typedef enum { SCE_GXM_TEXTURE_FILTER_POINT = 0, SCE_GXM_TEXTURE_FILTER_LINEAR = 1 } SceGxmTextureFilter;

typedef struct vita2d_texture vita2d_texture;
typedef struct vita2d_pgf vita2d_pgf;

int vita2d_init(void);
void vita2d_set_clear_color(unsigned int color);
void vita2d_set_vblank_wait(int enable);
void vita2d_start_drawing(void);
void vita2d_clear_screen(void);
void vita2d_end_drawing(void);
void vita2d_swap_buffers(void);
void vita2d_wait_rendering_done(void);

void vita2d_draw_rectangle(float x, float y, float w, float h, unsigned int color);
void vita2d_draw_line(float x0, float y0, float x1, float y1, unsigned int color);
void vita2d_draw_fill_circle(float x, float y, float radius, unsigned int color);

vita2d_texture *vita2d_create_empty_texture(unsigned int w, unsigned int h);
vita2d_texture *vita2d_load_PNG_file(const char *path);
vita2d_texture *vita2d_load_JPEG_file(const char *path);
void vita2d_free_texture(vita2d_texture *t);
void *vita2d_texture_get_datap(const vita2d_texture *t);
unsigned int vita2d_texture_get_stride(const vita2d_texture *t);
unsigned int vita2d_texture_get_width(const vita2d_texture *t);
unsigned int vita2d_texture_get_height(const vita2d_texture *t);
SceGxmTextureFormat vita2d_texture_get_format(const vita2d_texture *t);
void vita2d_texture_set_filters(vita2d_texture *t, SceGxmTextureFilter min, SceGxmTextureFilter mag);

void vita2d_draw_texture(const vita2d_texture *t, float x, float y);
void vita2d_draw_texture_scale(const vita2d_texture *t, float x, float y, float sx, float sy);
void vita2d_draw_texture_rotate(const vita2d_texture *t, float x, float y, float rad);
void vita2d_draw_texture_scale_rotate(const vita2d_texture *t, float x, float y, float sx, float sy, float rad);
void vita2d_draw_texture_tint_scale(const vita2d_texture *t, float x, float y, float sx, float sy, unsigned int color);

vita2d_pgf *vita2d_load_default_pgf(void);
void vita2d_free_pgf(vita2d_pgf *font);
int vita2d_pgf_draw_text(vita2d_pgf *font, int x, int y, unsigned int color, float scale, const char *text);
int vita2d_pgf_text_width(vita2d_pgf *font, float scale, const char *text);

/* Preview only: writes the current frame to a PNG. */
int preview_save_png(const char *path);

#endif
