#ifndef OKPLAYER_GFX_H
#define OKPLAYER_GFX_H

#include <vita2d.h>

/* Everything drawn from code at startup, so the app ships no image assets. */
typedef struct {
    vita2d_texture *sky;      /* Aero desktop behind the window */
    vita2d_texture *disc;     /* silver disc with grooves, turns */
    vita2d_texture *sheen;    /* rainbow ring, stays still over the disc */
    vita2d_texture *hub;      /* metal hub and spindle hole */
    vita2d_texture *well;     /* recessed tray the disc sits in */
    vita2d_texture *orb;      /* glossy blue play button */
    vita2d_texture *orb_dim;
    vita2d_texture *capsule;  /* dark glass pill behind previous / next */
    vita2d_texture *bar;      /* black glass control bar, 8 px wide, stretched */
    vita2d_texture *toolbar;  /* light glass under the title, stretched */
    vita2d_texture *cmdbar;   /* Explorer command bar gradient, stretched */
    vita2d_texture *lcd;      /* backlit LCD panel gradient, stretched */
} Skin;

#define DISC_SIZE 256
#define LABEL_SIZE 128

void skin_build(Skin *s);
void skin_free(Skin *s);

/* Loads a JPEG or PNG and shrinks it to size x size. NULL if it can't be read. */
vita2d_texture *cover_load(const char *path, int size);

/* The cover cut into a round disc label with a hole, for the spinning disc. */
vita2d_texture *label_from_cover(const vita2d_texture *cover);

/* A label in a colour taken from the name, for albums without art. */
vita2d_texture *label_generated(const char *name);

unsigned hue_color(const char *name, float value, float saturation);

#endif
