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
import dev.davirazuk.okplayer.library.Album
import dev.davirazuk.okplayer.ui.LibraryState
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
    query: String,
    onQuery: (String) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onPlaySong: (Album, Int) -> Unit,
    onRefresh: () -> Unit,
    onOptions: () -> Unit,
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
            } else {
                AlbumGrid(state.albums, query.trim(), onOpenAlbum, onPlaySong)
            }
        }
    }
}

private data class SongHit(val album: Album, val index: Int)

@Composable
private fun AlbumGrid(all: List<Album>, query: String, onOpenAlbum: (Album) -> Unit, onPlaySong: (Album, Int) -> Unit) {
    val albums = remember(all, query) {
        if (query.isEmpty()) all
        else all.filter { it.title.contains(query, true) || it.artist.contains(query, true) }
    }
    val songs = remember(all, query) {
        if (query.isEmpty()) emptyList()
        else all.flatMap { a -> a.tracks.mapIndexedNotNull { i, t -> if (t.title.contains(query, true)) SongHit(a, i) else null } }.take(60)
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(108.dp),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (query.isNotEmpty() && albums.isEmpty() && songs.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("No results for “$query”.", fontSize = 13.sp, color = Palette.Sub, modifier = Modifier.padding(12.dp))
            }
        }
        if (songs.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { GroupHeader("Songs (${songs.size})") }
            items(songs.size, span = { GridItemSpan(maxLineSpan) }) { i ->
                val hit = songs[i]
                val track = hit.album.tracks[hit.index]
                SongRow(track.title, "${track.artist} · ${hit.album.title}", formatTime(track.durationMs)) { onPlaySong(hit.album, hit.index) }
            }
        }
        if (albums.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) { GroupHeader("Albums (${albums.size})") }
            items(albums, key = { it.id }) { album -> AlbumTile(album) { onOpenAlbum(album) } }
        }
    }
}

@Composable
private fun SongRow(title: String, detail: String, duration: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .explorerItem(selected = false, source = source, pressed = pressed, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, fontSize = 11.5.sp, color = Palette.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(duration, fontSize = 12.sp, color = Palette.Sub)
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
private fun AlbumTile(album: Album, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Column(
        Modifier
            .explorerItem(selected = false, source = source, pressed = pressed, onClick = onClick)
            .padding(7.dp),
    ) {
        Artwork(
            album.artUri,
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(2.dp, RoundedCornerShape(1.dp)),
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
    onPlay: (startIndex: Int, shuffle: Boolean) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        CommandBar {
            Command("Play") { onPlay(0, false) }
            if (!noSkipping) Command("Shuffle") { onPlay(0, true) }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
                    Artwork(album.artUri, Modifier.size(112.dp).shadow(2.dp, RoundedCornerShape(1.dp)))
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(album.title, fontSize = 18.sp, color = Palette.Heading, lineHeight = 22.sp)
                        Text(album.artist, fontSize = 12.sp, color = Palette.Sub, modifier = Modifier.padding(top = 3.dp))
                        val meta = listOfNotNull(
                            album.year?.toString(),
                            "${album.tracks.size} songs",
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
                    onClick = { onPlay(index, false) },
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
private fun TrackRow(number: Int, title: String, rating: Int, duration: String, current: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .explorerItem(selected = current, source = source, pressed = pressed, onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$number", fontSize = 12.5.sp, color = Palette.Sub, modifier = Modifier.width(columnWidths[0]))
        Text(title, fontSize = 13.sp, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box(Modifier.width(columnWidths[1])) { Stars(rating) }
        Text(duration, fontSize = 12.5.sp, color = Palette.Sub, textAlign = TextAlign.End, modifier = Modifier.width(columnWidths[2]))
    }
}
