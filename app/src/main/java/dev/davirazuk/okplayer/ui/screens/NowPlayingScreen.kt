package dev.davirazuk.okplayer.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.davirazuk.okplayer.audio.EqState
import dev.davirazuk.okplayer.audio.OutputStatus
import dev.davirazuk.okplayer.audio.TrackInfo
import dev.davirazuk.okplayer.playback.NowPlaying
import dev.davirazuk.okplayer.playback.QueueEntry
import dev.davirazuk.okplayer.ui.DeckView
import dev.davirazuk.okplayer.ui.LyricsState
import dev.davirazuk.okplayer.ui.components.Aurora
import dev.davirazuk.okplayer.ui.components.DiscWell
import dev.davirazuk.okplayer.ui.components.EqualizerPanel
import dev.davirazuk.okplayer.ui.components.Lamp
import dev.davirazuk.okplayer.ui.components.Lcd
import dev.davirazuk.okplayer.ui.components.formatTime
import dev.davirazuk.okplayer.ui.theme.Glyphs
import dev.davirazuk.okplayer.ui.theme.Palette

@Composable
fun NowPlayingScreen(
    state: NowPlaying,
    track: TrackInfo?,
    output: OutputStatus,
    rating: Int,
    noSkipping: Boolean,
    deckView: DeckView,
    lyrics: LyricsState,
    queue: List<QueueEntry>,
    onlineLyrics: Boolean,
    equalizer: EqState,
    equalizerPresets: List<String>,
    onRate: (Int) -> Unit,
    onShowDeck: (DeckView) -> Unit,
    onPlayAt: (Int) -> Unit,
    onEqualizer: (Boolean) -> Unit,
    onEqualizerBand: (Int, Int) -> Unit,
    onEqualizerPreset: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Aurora(playing = state.isPlaying)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            DeckTabs(deckView, onShowDeck)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (deckView) {
                    DeckView.Disc -> DiscWell(
                        artUri = state.artUri,
                        playing = state.isPlaying,
                        modifier = Modifier.fillMaxWidth(0.78f).widthIn(max = 330.dp),
                    )
                    DeckView.Lyrics -> LyricsView(lyrics, state.positionMs, onlineLyrics)
                    DeckView.PlayList -> PlayListView(queue, state.index, onPlayAt)
                    DeckView.Equalizer -> EqualizerPanel(
                        state = equalizer,
                        presets = equalizerPresets,
                        onEnabled = onEqualizer,
                        onBand = onEqualizerBand,
                        onPreset = onEqualizerPreset,
                        modifier = Modifier.padding(horizontal = 18.dp),
                    )
                }
            }

            Lcd(
                track = if (state.isEmpty) "--" else "%02d".format(state.index + 1),
                time = if (state.isEmpty) "-:--" else formatTime(state.positionMs),
                title = state.title.ifEmpty { "No disc" },
                subtitle = if (state.isEmpty) "Pick an album in the library" else listOf(state.artist, state.album).filter { it.isNotBlank() }.joinToString(" — "),
                lamps = listOf(
                    Lamp("SHUF", state.shuffle),
                    Lamp("NO SKIP", noSkipping),
                    Lamp("USB", output is OutputStatus.Usb),
                    Lamp("BIT-PERFECT", output is OutputStatus.Usb && output.bitPerfect),
                ),
                format = track?.let(::formatLabel) ?: "",
                modifier = Modifier.padding(horizontal = 18.dp),
            )

            if (deckView == DeckView.Disc) CurrentLyric(lyrics, state.positionMs)

            if (!state.isEmpty) RatingRow(rating, onRate)
            Text(
                describe(output),
                fontSize = 11.5.sp,
                color = Color(0xFF7F8B98),
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun DeckTabs(current: DeckView, onShow: (DeckView) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf(
            DeckView.Disc to "Disc",
            DeckView.Lyrics to "Lyrics",
            DeckView.PlayList to "Play list",
            DeckView.Equalizer to "Equalizer",
        ).forEach { (view, label) ->
            val on = view == current
            val shape = RoundedCornerShape(3.dp)
            Text(
                label,
                fontSize = 12.5.sp,
                color = if (on) Color.White else Color(0xFFAEB6BF),
                modifier = Modifier
                    .clip(shape)
                    .then(
                        if (on) Modifier
                            .background(Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x10FFFFFF))))
                            .border(1.dp, Color(0x40FFFFFF), shape)
                        else Modifier,
                    )
                    .clickable(role = Role.Tab) { onShow(view) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

private val lyricGlow = Shadow(Palette.LcdOn.copy(alpha = 0.6f), blurRadius = 12f)

@Composable
private fun CurrentLyric(lyrics: LyricsState, positionMs: Long) {
    val found = (lyrics as? LyricsState.Found)?.lyrics ?: return
    if (!found.synced) return
    val i = found.indexAt(positionMs)
    val line = if (i >= 0) found.lines[i].text else ""
    Text(
        line.ifBlank { "♪" },
        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Palette.LcdOn.copy(alpha = 0.85f), shadow = lyricGlow),
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 10.dp),
    )
}

@Composable
private fun LyricsView(lyrics: LyricsState, positionMs: Long, onlineLyrics: Boolean) {
    val message = when (lyrics) {
        LyricsState.Searching -> "Looking for lyrics…"
        LyricsState.None -> if (onlineLyrics) "No lyrics found for this song." else "No lyrics in this file. Online lookup is off in Options."
        is LyricsState.Found -> null
    }
    if (message != null) {
        Text(message, fontSize = 13.sp, color = Color(0xFF7F8B98), textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
        return
    }
    val found = (lyrics as LyricsState.Found).lyrics
    val current = found.indexAt(positionMs)
    val listState = rememberLazyListState()
    val centerOffset = with(LocalDensity.current) { 110.dp.roundToPx() }

    LaunchedEffect(current) {
        if (current >= 0) listState.animateScrollToItem(current, scrollOffset = -centerOffset)
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 90.dp),
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(found.lines) { i, line ->
            val active = i == current
            val color by animateColorAsState(
                when {
                    !found.synced -> Color(0xFFDCE6EE)
                    active -> Palette.LcdOn
                    else -> Color(0xFF5B6873)
                },
                label = "lyric",
            )
            Text(
                line.text.ifBlank { " " },
                style = TextStyle(
                    fontSize = if (active) 19.sp else 16.sp,
                    color = color,
                    shadow = if (active) lyricGlow else null,
                    lineHeight = 24.sp,
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun PlayListView(queue: List<QueueEntry>, currentIndex: Int, onPlayAt: (Int) -> Unit) {
    if (queue.isEmpty()) {
        Text("The play list is empty.", fontSize = 13.sp, color = Color(0xFF7F8B98))
        return
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0))
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
        itemsIndexed(queue, key = { i, e -> "$i-${e.mediaId}" }) { i, entry ->
            val current = i == currentIndex
            val shape = RoundedCornerShape(3.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(shape)
                    .then(
                        if (current) Modifier
                            .background(Brush.verticalGradient(listOf(Color(0xFF2C6EA6), Color(0xFF184C7C))))
                            .border(1.dp, Color(0xFF5AAEE8), shape)
                        else Modifier,
                    )
                    .clickable { onPlayAt(i) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", fontSize = 12.sp, color = if (current) Color.White else Color(0xFF6F7C89), modifier = Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.title, fontSize = 13.5.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (current) Color.White else Color(0xFFDCE3EA),
                    )
                    Text(
                        entry.artist, fontSize = 11.5.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (current) Color(0xFFBFDDF5) else Color(0xFF7F8B98),
                    )
                }
            }
        }
    }
}

@Composable
private fun RatingRow(rating: Int, onRate: (Int) -> Unit) {
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..5) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(role = Role.Button, onClickLabel = "Rate $i") { onRate(i) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Glyphs.Star, "$i stars", tint = if (i <= rating) Palette.Star else Palette.StarOff, modifier = Modifier.size(20.dp))
            }
        }
    }
    Spacer(Modifier.height(2.dp))
}

private fun formatLabel(t: TrackInfo): String {
    val parts = mutableListOf(t.codec)
    if (t.lossless && t.sampleRate != null) {
        val rate = if (t.sampleRate % 1000 == 0) "${t.sampleRate / 1000}" else "%.1f".format(t.sampleRate / 1000f)
        parts += if (t.bitDepth != null) "$rate/${t.bitDepth}" else "${rate}K"
    } else if (t.bitrate != null && t.bitrate > 0) {
        parts += "${t.bitrate / 1000}K"
    }
    return parts.joinToString(" ")
}

private fun describe(o: OutputStatus): String = when (o) {
    OutputStatus.Idle -> ""
    is OutputStatus.Internal -> "Phone audio, mixed by Android"
    is OutputStatus.Usb -> if (o.bitPerfect) "${o.deviceName} · bit-perfect" else "${o.deviceName} · ${o.reason ?: "resampled by Android"}"
}
