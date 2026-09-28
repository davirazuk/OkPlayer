#ifndef OKPLAYER_TAGS_H
#define OKPLAYER_TAGS_H

typedef struct {
    char title[128];
    char artist[96];   /* album artist when present, otherwise the artist */
    char album[128];
    int track;
    int disc;
} Tags;

/* Fills t from the file's tags. Returns 0 when the file has none or can't be read. */
int tags_read(const char *path, Tags *t);

#endif
