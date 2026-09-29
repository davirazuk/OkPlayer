package dev.davirazuk.okplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import dev.davirazuk.okplayer.ui.components.StartIcon
import dev.davirazuk.okplayer.ui.components.StartIcons
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.text.format.DateUtils
import dev.davirazuk.okplayer.data.PlayStat
import dev.davirazuk.okplayer.library.Album
import dev.davirazuk.okplayer.library.Track
import dev.davirazuk.okplayer.ui.components.MenuItem
import dev.davirazuk.okplayer.ui.components.Win7Menu
import dev.davirazuk.okplayer.ui.LibraryState
import dev.davirazuk.okplayer.ui.LibraryView
import dev.davirazuk.okplayer.ui.components.Artwork
import dev.davirazuk.okplayer.ui.components.Command
import dev.davirazuk.okplayer.ui.components.CommandBar
import dev.davirazuk.okplayer.ui.components.GroupHeader
import dev.davirazuk.okplayer.ui.components.InfoBar
import dev.davirazuk.okplayer.ui.components.Stars
import dev.davirazuk.okplayer.ui.components.Win7Button
import dev.davirazuk.okplayer.ui.components.explorerItem
import dev.davirazuk.okplayer.ui.components.formatTime
import dev.davirazuk.okplayer.ui.theme.Glyphs
import dev.davirazuk.okplayer.ui.theme.Palette

@Composable
fun LibraryScreen(
    state: LibraryState,
    view: LibraryView,
    query: String,
    onQuery: (String) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenArtist: (String) -> Unit,
    onPlaySong: (Album, Int) -> Unit,
    onRefresh: () -> Unit,
    onOptions: () -> Unit,
    actions: QueueActions = QueueActions(),
    stats: Map<String, PlayStat> = emptyMap(),
    onOpenFolder: (String) -> Unit = {},
    onPlayFolder: (List<Track>, Int, Boolean?) -> Unit = { _, _, _ -> },
) {
    Column(Modifier.fillMaxSize()) {
        CommandBar {
            Command("Refresh", onRefresh)
            Command("Options", onOptions)
            Spacer(Modifier.weight(1f))
            SearchBox(query, onQuery, Modifier.width(158.dp))
        }
        when (state) {
            LibraryState.NeedsPermission -> Column(Modifier.padding(12.dp)) {
                InfoBar("okplayer needs permission to read the music on this phone. It only reads audio files, and nothing leaves the device.")
                Spacer(Modifier.height(12.dp))
                Win7Button("Allow access", onRequestPermission)
            }

            LibraryState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Palette.Aero, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
            }

            is LibraryState.Ready -> if (state.albums.isEmpty()) {
                InfoBar(
                    "No music found. Copy FLAC, ALAC, MP3, AAC, OGG, Opus or WAV files into the Music folder, then tap Refresh.",
                    Modifier.padding(12.dp),
                )
            } else if (view == LibraryView.Folders && query.isBlank()) {
                FolderList(FolderIndex.of(state.albums), "", onOpenFolder, onPlayFolder, actions)
            } else {
                LibraryContent(view, state.albums, query.trim(), onOpenAlbum, onOpenArtist, onPlaySong, actions, stats)
            }
        }
    }
}

/** Long-press actions on songs and albums, like WMP's right-click menu. */
class QueueActions(
    val playNext: (List<Track>) -> Unit = {},
    val enqueue: (List<Track>) -> Unit = {},
)

private fun songMenu(track: Track, actions: QueueActions, play: () -> Unit) = listOf(
    MenuItem("Play", onClick = play),
    MenuItem("Play next") { actions.playNext(listOf(track)) },
    MenuItem("Add to Now Playing") { actions.enqueue(listOf(track)) },
)

private fun albumMenu(album: Album, actions: QueueActions, open: () -> Unit, play: () -> Unit) = listOf(
    MenuItem("Open", onClick = open),
    MenuItem("Play", onClick = play),
    MenuItem("Play next") { actions.playNext(album.tracks) },
    MenuItem("Add to Now Playing") { actions.enqueue(album.tracks) },
)

private fun plural(n: Int, word: String) = "$n $word" + if (n == 1) "" else "s"

private data class SongHit(val album: Album, val index: Int) {
    val track get() = album.tracks[index]
}

private data class ArtistEntry(val name: String, val albums: List<Album>) {
    val songCount get() = albums.sumOf { it.tracks.size }
}

@Composable
private fun LibraryContent(
    view: LibraryView,
    all: List<Album>,
    query: String,
    onOpenAlbum: (Album) -> Unit,
    onOpenArtist: (String) -> Unit,
    onPlaySong: (Album, Int) -> Unit,
    actions: QueueActions,
    stats: Map<String, PlayStat>,
) {
    val autoView = view in setOf(LibraryView.RecentlyAdded, LibraryView.MostPlayed, LibraryView.RecentlyPlayed)
    val albums = remember(all, query) {
        if (query.isEmpty()) all else all.filter { it.title.contains(query, true) || it.artist.contains(query, true) }
    }
    val artists = remember(all, query) {
        all.groupBy { it.artist }
            .map { (name, list) -> ArtistEntry(name, list) }
            .filter { query.isEmpty() || it.name.contains(query, true) }
            .sortedBy { it.name.lowercase() }
    }
    val songs = remember(all, query, view) {
        val hits = all.flatMap { a -> a.tracks.indices.map { SongHit(a, it) } }
        when {
            autoView -> emptyList()
            view == LibraryView.Songs && query.isEmpty() -> hits.sortedBy { it.track.title.lowercase() }
            view == LibraryView.Folders -> hits.filter { it.track.title.contains(query, true) || it.track.folder.contains(query, true) }
                .sortedBy { it.track.title.lowercase() }
            query.isEmpty() -> emptyList()
            else -> hits.filter { it.track.title.contains(query, true) || (view == LibraryView.Songs && it.track.artist.contains(query, true)) }
                .sortedBy { it.track.title.lowercase() }
                .let { if (view == LibraryView.Songs) it else it.take(60) }
        }
    }

    val played = remember(all, view, stats, query) {
        if (view != LibraryView.MostPlayed && view != LibraryView.RecentlyPlayed) return@remember emptyList()
        all.flatMap { a -> a.tracks.indices.map { SongHit(a, it) } }
            .filter { stats[it.track.id.toString()] != null }
            .filter { query.isEmpty() || it.track.title.contains(query, true) || it.track.artist.contains(query, true) }
            .let { list ->
                if (view == LibraryView.MostPlayed) list.sortedByDescending { stats[it.track.id.toString()]?.count ?: 0 }
                else list.sortedByDescending { stats[it.track.id.toString()]?.lastPlayedMs ?: 0 }
            }
            .take(100)
    }
    val recent = remember(albums, view) {
        if (view == LibraryView.RecentlyAdded) albums.sortedByDescending { it.addedSec }.take(60) else emptyList()
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(108.dp),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        val nothing = when (view) {
            LibraryView.Albums -> albums.isEmpty() && songs.isEmpty()
            LibraryView.Artists -> artists.isEmpty() && songs.isEmpty()
            LibraryView.Songs, LibraryView.Folders -> songs.isEmpty()
            LibraryView.RecentlyAdded -> recent.isEmpty()
            LibraryView.MostPlayed, LibraryView.RecentlyPlayed -> played.isEmpty()
        }
        if (query.isEmpty() && nothing && autoView) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Songs you play show up here once they've played through.",
                    fontSize = 13.sp, color = Palette.Sub, modifier = Modifier.padding(12.dp),
                )
            }
        }
        if (recent.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { GroupHeader("Recently added") }
            items(recent, key = { "recent-${it.id}" }) { album ->
                AlbumTile(album, albumMenu(album, actions, { onOpenAlbum(album) }, { onPlaySong(album, 0) })) { onOpenAlbum(album) }
            }
        }
        if (played.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { GroupHeader(view.label) }
            items(played.size, span = { GridItemSpan(maxLineSpan) }) { i ->
                val hit = played[i]
                val stat = stats[hit.track.id.toString()]
                val detail = when {
                    stat == null -> hit.track.artist
                    view == LibraryView.MostPlayed -> "${hit.track.artist} · ${plural(stat.count, "play")}"
                    else -> "${hit.track.artist} · " + DateUtils.getRelativeTimeSpanString(stat.lastPlayedMs)
                }
                val play = { onPlaySong(hit.album, hit.index) }
                SongRow(hit.track.title, detail, formatTime(hit.track.durationMs), songMenu(hit.track, actions, play), play)
            }
        }
        if (query.isNotEmpty() && nothing) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("No results for “$query”.", fontSize = 13.sp, color = Palette.Sub, modifier = Modifier.padding(12.dp))
            }
        }
        if (view == LibraryView.Artists && artists.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { GroupHeader("Artists (${artists.size})") }
            items(artists.size, key = { "artist-" + artists[it].name }, span = { GridItemSpan(maxLineSpan) }) { i ->
                ArtistRow(artists[i]) { onOpenArtist(artists[i].name) }
            }
        }
        if (songs.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { GroupHeader("Songs (${songs.size})") }
            items(songs.size, span = { GridItemSpan(maxLineSpan) }) { i ->
                val hit = songs[i]
                val play = { onPlaySong(hit.album, hit.index) }
                SongRow(
                    hit.track.title, "${hit.track.artist} · ${hit.album.title}", formatTime(hit.track.durationMs),
                    menu = songMenu(hit.track, actions, play), onClick = play,
                )
            }
        }
        if (view == LibraryView.Albums && albums.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { GroupHeader("Albums (${albums.size})") }
            items(albums, key = { it.id }) { album ->
                AlbumTile(album, albumMenu(album, actions, { onOpenAlbum(album) }, { onPlaySong(album, 0) })) { onOpenAlbum(album) }
            }
        }
    }
}

/* ---------------- folders ---------------- */

/** The folders that hold music, as a tree built from each song's folder. */
class FolderIndex private constructor(private val songs: Map<String, List<Track>>) {
    private val children: Map<String, List<String>> = buildMap<String, MutableSet<String>> {
        for (folder in songs.keys) {
            var path = folder
            while (path.isNotEmpty()) {
                val parent = path.substringBeforeLast('/', "")
                getOrPut(parent) { mutableSetOf() } += path
                path = parent
            }
        }
    }.mapValues { (_, set) -> set.sortedBy { it.lowercase() } }

    fun subfolders(path: String): List<String> = children[path].orEmpty()

    /** Songs directly in [path], in album order. */
    fun songsIn(path: String): List<Track> = songs[path].orEmpty()

    /** Every song under [path], folder by folder. */
    fun allUnder(path: String): List<Track> = songsIn(path) + subfolders(path).flatMap { allUnder(it) }

    fun count(path: String): Int = songsIn(path).size + subfolders(path).sumOf { count(it) }

    companion object {
        fun of(albums: List<Album>) = FolderIndex(
            albums.asSequence().flatMap { it.tracks }.groupBy { it.folder }
                .mapValues { (_, list) -> list.sortedWith(compareBy<Track>({ it.album.lowercase() }, { it.discNumber }, { it.trackNumber }, { it.title.lowercase() })) },
        )
    }
}

/** A folder's subfolders, then its songs, like Explorer's details view. */
@Composable
fun FolderList(
    index: FolderIndex,
    path: String,
    onOpenFolder: (String) -> Unit,
    onPlay: (List<Track>, Int, Boolean?) -> Unit,
    actions: QueueActions,
) {
    val folders = remember(index, path) { index.subfolders(path) }
    val songs = remember(index, path) { index.songsIn(path) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (folders.isEmpty() && songs.isEmpty()) {
            item { Text("This folder is empty.", fontSize = 13.sp, color = Palette.Sub, modifier = Modifier.padding(12.dp)) }
        }
        if (folders.isNotEmpty()) {
            item { GroupHeader("Folders (${folders.size})") }
            items(folders, key = { "folder-$it" }) { folder ->
                val subs = index.subfolders(folder).size
                val detail = listOfNotNull(
                    plural(index.count(folder), "song"),
                    if (subs > 0) plural(subs, "folder") else null,
                ).joinToString(", ")
                FolderRow(
                    name = folder.substringAfterLast('/'),
                    detail = detail,
                    menu = listOf(
                        MenuItem("Open") { onOpenFolder(folder) },
                        MenuItem("Play") { onPlay(index.allUnder(folder), 0, false) },
                        MenuItem("Play next") { actions.playNext(index.allUnder(folder)) },
                        MenuItem("Add to Now Playing") { actions.enqueue(index.allUnder(folder)) },
                    ),
                ) { onOpenFolder(folder) }
            }
        }
        if (songs.isNotEmpty()) {
            item { GroupHeader("Songs (${songs.size})") }
            items(songs.size) { i ->
                val track = songs[i]
                val play = { onPlay(songs, i, null) }
                SongRow(track.title, "${track.artist} · ${track.album}", formatTime(track.durationMs), songMenu(track, actions, play), play)
            }
        }
    }
}

/** A folder inside the library, with Play all and Shuffle for everything under it. */
@Composable
fun FolderScreen(
    albums: List<Album>,
    path: String,
    noSkipping: Boolean,
    onOpenFolder: (String) -> Unit,
    onPlay: (List<Track>, Int, Boolean?) -> Unit,
    actions: QueueActions = QueueActions(),
) {
    val index = remember(albums) { FolderIndex.of(albums) }
    Column(Modifier.fillMaxSize()) {
        CommandBar(trailing = plural(index.count(path), "song")) {
            Command("Play all") { onPlay(index.allUnder(path), 0, false) }
            if (!noSkipping) Command("Shuffle") { onPlay(index.allUnder(path), 0, true) }
            Command("Add to Now Playing") { actions.enqueue(index.allUnder(path)) }
        }
        FolderList(index, path, onOpenFolder, onPlay, actions)
    }
}

@Composable
private fun FolderRow(name: String, detail: String, menu: List<MenuItem>, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .explorerItem(selected = open, source = source, pressed = pressed, onLongClick = { open = true }, onClick = onClick)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StartIcon(StartIcons.Library, 30.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(name, fontSize = 13.5.sp, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, fontSize = 11.5.sp, color = Palette.Sub, maxLines = 1)
            }
        }
        if (open) Win7Menu(menu, onDismiss = { open = false }, offsetY = 48.dp)
    }
}

@Composable
private fun ArtistRow(artist: ArtistEntry, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .explorerItem(selected = false, source = source, pressed = pressed, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // WMP stacks an artist's covers like a small pile of cases.
        Box(Modifier.size(62.dp, 52.dp)) {
            artist.albums.take(3).reversed().forEachIndexed { i, album ->
                val depth = minOf(artist.albums.size, 3) - 1 - i
                Artwork(
                    album.artUri,
                    Modifier
                        .padding(start = (depth * 7).dp, top = ((2 - depth).coerceAtLeast(0) * 3).dp)
                        .size(44.dp)
                        .shadow(3.dp, RoundedCornerShape(1.dp)),
                    name = album.title,
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(artist.name, fontSize = 13.5.sp, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${plural(artist.albums.size, "album")}, ${plural(artist.songCount, "song")}",
                fontSize = 11.5.sp, color = Palette.Sub,
            )
        }
    }
}

@Composable
fun ArtistScreen(
    artist: String,
    albums: List<Album>,
    noSkipping: Boolean,
    onOpenAlbum: (Album) -> Unit,
    onPlayAll: (shuffle: Boolean) -> Unit,
    onPlayAlbum: (Album) -> Unit = {},
    actions: QueueActions = QueueActions(),
) {
    Column(Modifier.fillMaxSize()) {
        CommandBar(trailing = "${plural(albums.size, "album")}, ${plural(albums.sumOf { it.tracks.size }, "song")}") {
            Command("Play all") { onPlayAll(false) }
            if (!noSkipping) Command("Shuffle") { onPlayAll(true) }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(108.dp),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { GroupHeader(artist) }
            items(albums, key = { it.id }) { album ->
                AlbumTile(album, albumMenu(album, actions, { onOpenAlbum(album) }, { onPlayAlbum(album) })) { onOpenAlbum(album) }
            }
        }
    }
}

@Composable
private fun SongRow(title: String, detail: String, duration: String, menu: List<MenuItem>, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .explorerItem(selected = open, source = source, pressed = pressed, onLongClick = { open = true }, onClick = onClick)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, fontSize = 11.5.sp, color = Palette.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(duration, fontSize = 12.sp, color = Palette.Sub)
        }
        if (open) Win7Menu(menu, onDismiss = { open = false }, offsetY = 40.dp)
    }
}

/** The Windows 7 search box: white field, grey hint, magnifier that becomes a clear button. */
@Composable
private fun SearchBox(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(24.dp)
            .background(Color.White)
            .border(1.dp, if (query.isEmpty()) Color(0xFFA8B7C9) else Color(0xFF3D7BAD))
            .padding(start = 6.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("Search", fontSize = 12.sp, color = Color(0xFF8A8A8A), fontStyle = FontStyle.Italic)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(fontSize = 12.5.sp, color = Palette.Ink),
                cursorBrush = SolidColor(Palette.Ink),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Icon(
            if (query.isEmpty()) Glyphs.Search else Glyphs.Close,
            if (query.isEmpty()) null else "Clear search",
            tint = Color(0xFF5B6F86),
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .clickable(enabled = query.isNotEmpty()) { onQuery("") }
                .padding(2.dp),
        )
    }
}

@Composable
private fun AlbumTile(album: Album, menu: List<MenuItem>, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var open by remember { mutableStateOf(false) }
    if (open) Box { Win7Menu(menu, onDismiss = { open = false }, offsetY = 90.dp) }
    Column(
        Modifier
            .explorerItem(selected = open, source = source, pressed = pressed, onLongClick = { open = true }, onClick = onClick)
            .padding(7.dp),
    ) {
        Artwork(
            album.artUri,
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(2.dp, RoundedCornerShape(1.dp)),
            name = album.title,
        )
        Text(
            album.title, fontSize = 12.5.sp, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(album.artist, fontSize = 11.5.sp, color = Palette.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun AlbumScreen(
    album: Album,
    currentId: String?,
    ratings: Map<String, Int>,
    noSkipping: Boolean,
    onPlay: (startIndex: Int, shuffle: Boolean?) -> Unit,
    actions: QueueActions = QueueActions(),
) {
    Column(Modifier.fillMaxSize()) {
        CommandBar {
            Command("Play") { onPlay(0, false) }
            if (!noSkipping) Command("Shuffle") { onPlay(0, true) }
            Command("Add to Now Playing") { actions.enqueue(album.tracks) }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
                    Artwork(album.artUri, Modifier.size(112.dp).shadow(2.dp, RoundedCornerShape(1.dp)), name = album.title)
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(album.title, fontSize = 18.sp, color = Palette.Heading, lineHeight = 22.sp)
                        Text(album.artist, fontSize = 12.sp, color = Palette.Sub, modifier = Modifier.padding(top = 3.dp))
                        val meta = listOfNotNull(
                            album.year?.toString(),
                            plural(album.tracks.size, "song"),
                            "${album.durationMs / 60_000} minutes",
                        ).joinToString(", ")
                        Text(meta, fontSize = 12.sp, color = Palette.Sub)
                    }
                }
            }
            item { ColumnHeaders() }
            itemsIndexed(album.tracks, key = { _, t -> t.id }) { index, track ->
                val id = track.id.toString()
                TrackRow(
                    number = track.trackNumber.takeIf { it > 0 } ?: (index + 1),
                    title = track.title,
                    rating = ratings[id] ?: 0,
                    duration = formatTime(track.durationMs),
                    current = id == currentId,
                    menu = songMenu(track, actions) { onPlay(index, null) },
                    onClick = { onPlay(index, null) },
                )
            }
        }
    }
}

private val columnWidths = listOf(30.dp, 70.dp, 44.dp)

@Composable
private fun ColumnHeaders() {
    Row(
        Modifier
            .fillMaxWidth()
            .height(25.dp)
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFF5F8FB))))
            .drawBehind {
                drawLine(Palette.Rule, Offset(0f, 0f), Offset(size.width, 0f))
                drawLine(Palette.Rule, Offset(0f, size.height), Offset(size.width, size.height))
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = Color(0xFF4C607A)
        Text("#", fontSize = 11.5.sp, color = ink, modifier = Modifier.width(columnWidths[0]))
        Text("Title", fontSize = 11.5.sp, color = ink, modifier = Modifier.weight(1f))
        Text("Rating", fontSize = 11.5.sp, color = ink, modifier = Modifier.width(columnWidths[1]))
        Text("Length", fontSize = 11.5.sp, color = ink, textAlign = TextAlign.End, modifier = Modifier.width(columnWidths[2]))
    }
}

@Composable
private fun TrackRow(
    number: Int,
    title: String,
    rating: Int,
    duration: String,
    current: Boolean,
    menu: List<MenuItem>,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .explorerItem(selected = current || open, source = source, pressed = pressed, onLongClick = { open = true }, onClick = onClick)
                .padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$number", fontSize = 12.5.sp, color = Palette.Sub, modifier = Modifier.width(columnWidths[0]))
            Text(title, fontSize = 13.sp, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box(Modifier.width(columnWidths[1])) { Stars(rating) }
            Text(duration, fontSize = 12.5.sp, color = Palette.Sub, textAlign = TextAlign.End, modifier = Modifier.width(columnWidths[2]))
        }
        if (open) Win7Menu(menu, onDismiss = { open = false }, offsetY = 34.dp)
    }
}
