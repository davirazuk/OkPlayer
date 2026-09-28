package dev.davirazuk.okplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.davirazuk.okplayer.audio.OutputStatus
import dev.davirazuk.okplayer.audio.formatRate
import dev.davirazuk.okplayer.library.Album
import dev.davirazuk.okplayer.playback.NowPlaying
import dev.davirazuk.okplayer.ui.LibraryState
import dev.davirazuk.okplayer.ui.components.Artwork
import dev.davirazuk.okplayer.ui.components.OrbButton
import dev.davirazuk.okplayer.ui.components.formatTime
import dev.davirazuk.okplayer.ui.theme.Glyphs
import dev.davirazuk.okplayer.ui.theme.Palette

/** Window-style header: a strip of Aero glass with the title on it. */
@Composable
private fun GlassHeader(title: String, subtitle: String?, onBack: (() -> Unit)?, action: @Composable () -> Unit = {}) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF2A5F8F), Color(0xFF173F63), Color(0xFF10243A))))
            .statusBarsPadding(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.35f))
                .align(Alignment.TopCenter),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Glyphs.Back, "Back", tint = Palette.Text) }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFFB5CBE0))
            }
            action()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.5f)).align(Alignment.BottomCenter))
    }
}

@Composable
fun LibraryScreen(
    state: LibraryState,
    nowPlaying: NowPlaying,
    onRequestPermission: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onTogglePlay: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Palette.Ink)) {
        val subtitle = (state as? LibraryState.Ready)?.albums?.let { a -> "${a.size} albums · ${a.sumOf { it.tracks.size }} songs" }
        GlassHeader("Library", subtitle, onBack = null) {
            IconButton(onClick = onOpenSettings) { Icon(Glyphs.Tune, "Settings", tint = Palette.Text) }
        }

        Box(Modifier.weight(1f)) {
            when (state) {
                LibraryState.NeedsPermission -> Message(
                    title = "okplayer needs to see your music",
                    body = "It only reads audio files on this phone. Nothing leaves the device.",
                    action = "Allow access",
                    onAction = onRequestPermission,
                )
                LibraryState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Palette.Aero)
                }
                is LibraryState.Ready -> if (state.albums.isEmpty()) {
                    Message(
                        title = "No music found",
                        body = "Copy FLAC, MP3, M4A or OGG files into the Music folder on your phone, then reopen the app.",
                    )
                } else {
                    AlbumGrid(state.albums, onOpenAlbum)
                }
            }
        }

        if (!nowPlaying.isEmpty) MiniPlayer(nowPlaying, onOpenNowPlaying, onTogglePlay)
    }
}

@Composable
private fun AlbumGrid(albums: List<Album>, onOpenAlbum: (Album) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums, key = { it.id }) { album ->
            Column(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onOpenAlbum(album) }
                    .padding(2.dp),
            ) {
                Artwork(
                    album.artUri,
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(3.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(3.dp)),
                )
                Text(
                    album.title, style = MaterialTheme.typography.titleSmall, color = Palette.Text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp),
                )
                Text(album.artist, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun MiniPlayer(state: NowPlaying, onOpen: () -> Unit, onTogglePlay: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF1B2F46), Color(0xFF0E1A28))))
            .clickable(onClick = onOpen)
            .navigationBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.25f)))
        val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(2.dp).background(Palette.Aero))
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(state.artUri, Modifier.size(44.dp).clip(RoundedCornerShape(3.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(state.title, style = MaterialTheme.typography.titleSmall, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(state.artist, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OrbButton(
                if (state.isPlaying) Glyphs.Pause else Glyphs.Play,
                if (state.isPlaying) "Pause" else "Play",
                onTogglePlay,
                size = 40.dp,
            )
        }
    }
}

@Composable
fun AlbumScreen(
    album: Album,
    nowPlaying: NowPlaying,
    favorites: Set<String>,
    noSkipping: Boolean,
    onBack: () -> Unit,
    onPlay: (startIndex: Int, shuffle: Boolean) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Palette.Ink)) {
        GlassHeader(album.title, album.artist, onBack = onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Bottom) {
                    Artwork(album.artUri, Modifier.size(132.dp).clip(RoundedCornerShape(3.dp)))
                    Column(Modifier.padding(start = 16.dp)) {
                        val meta = listOfNotNull(
                            album.year?.toString(),
                            "${album.tracks.size} songs",
                            "${album.durationMs / 60_000} min",
                        ).joinToString(" · ")
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AeroButton("Play in order") { onPlay(0, false) }
                            if (!noSkipping) AeroButton("Shuffle", primary = false) { onPlay(0, true) }
                        }
                    }
                }
            }
            itemsIndexed(album.tracks, key = { _, t -> t.id }) { index, track ->
                val id = track.id.toString()
                val current = nowPlaying.mediaId == id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (current) Color(0xFF16304C) else Color.Transparent)
                        .clickable { onPlay(index, false) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        track.trackNumber.takeIf { it > 0 }?.toString() ?: "${index + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (current) Palette.Aero else Palette.Faint,
                        modifier = Modifier.width(28.dp),
                    )
                    Text(
                        track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (current) Palette.Aero else Palette.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (id in favorites) {
                        Icon(Glyphs.Heart, "Loved", tint = Palette.Pink, modifier = Modifier.padding(horizontal = 8.dp).size(14.dp))
                    }
                    Text(formatTime(track.durationMs), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                }
            }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
fun SettingsScreen(
    hiResOutput: Boolean,
    noSkipping: Boolean,
    output: OutputStatus,
    onBack: () -> Unit,
    onHiRes: (Boolean) -> Unit,
    onNoSkipping: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Palette.Ink)) {
        GlassHeader("Settings", null, onBack = onBack)
        Column(Modifier.padding(vertical = 8.dp)) {
            SettingRow(
                "Hi-res output",
                "Decodes to 32-bit float so 24-bit files reach a USB DAC untouched. Takes effect the next time playback starts.",
                hiResOutput, onHiRes,
            )
            SettingRow(
                "No skipping",
                "Songs can't be skipped or fast-forwarded until they end. Applies to the lock screen and headset buttons too.",
                noSkipping, onNoSkipping,
            )

            Text(
                "OUTPUT",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Muted,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
            )
            OutputLine(output, Modifier.padding(horizontal = 16.dp))
            if (output is OutputStatus.Usb && output.supportedRates.isNotEmpty()) {
                Text(
                    "Bit-perfect rates on this DAC: " + output.supportedRates.joinToString(", ") { formatRate(it) },
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Faint,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Text)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.Aero, checkedThumbColor = Color.White),
        )
    }
}

@Composable
private fun AeroButton(label: String, primary: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(3.dp)
    val fill = if (primary) {
        listOf(Color(0xFF7CC4F2), Color(0xFF3B95D8), Color(0xFF1F6FB3), Color(0xFF2C86CC))
    } else {
        listOf(Color(0xFF3A4B60), Color(0xFF2A394B), Color(0xFF1E2A38), Color(0xFF263546))
    }
    Box(
        Modifier
            .clip(shape)
            .background(Brush.verticalGradient(fill))
            .border(1.dp, Color(0xFF0A2A4A), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

@Composable
private fun Message(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.padding(top = 8.dp))
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            AeroButton(action, onClick = onAction)
        }
    }
}
