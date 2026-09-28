#ifndef OKPLAYER_LIBRARY_H
#define OKPLAYER_LIBRARY_H

typedef struct {
    char *path;   /* full path to the file */
    char *title;  /* from the file name, track number and extension removed */
    int number;   /* leading track number from the file name, 0 when none */
} Song;

typedef struct {
    char *title;       /* folder name */
    char *artist;      /* parent folder, when the layout is Artist/Album */
    char *cover_path;  /* cover.jpg / folder.jpg / front.png ..., or NULL */
    Song *songs;
    int song_count;
} Album;

typedef struct {
    Album *albums;
    int count;
    int song_total;
} Library;

/* Where okplayer looks for music, in order. */
extern const char *const LIBRARY_ROOTS[];

/* Scans the roots (a few levels deep); albums sorted by artist then title. */
void library_scan(Library *lib);
void library_free(Library *lib);

#endif
