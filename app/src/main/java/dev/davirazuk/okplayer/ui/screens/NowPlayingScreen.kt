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
import dev.davirazuk.okplayer.ui.components.Disc
import dev.davirazuk.okplayer.ui.components.Pill
import dev.davirazuk.okplayer.ui.components.Spectrum
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import dev.davirazuk.okplayer.ui.components.DiscWell
import dev.davirazuk.okplayer.ui.components.EqualizerPanel
import dev.davirazuk.okplayer.ui.components.MenuItem
import dev.davirazuk.okplayer.ui.components.Win7Menu
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    sleepEndsAt: Long? = null,
    onSleep: (Int) -> Unit = {},
    seven: Boolean = false,
) {
    Box(Modifier.fillMaxSize()) {
        Aurora(playing = state.isPlaying, base = if (seven) Palette.Navy else Palette.Black)
        if (seven) Spectrum(state.isPlaying, Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.2f))
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (seven) SevenTabs(deckView, onShowDeck, sleepEndsAt, onSleep) else DeckTabs(deckView, onShowDeck, sleepEndsAt, onSleep)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (deckView) {
                    DeckView.Disc -> if (seven) {
                        // The original deck: the disc on its own, glowing, no well.
                        Disc(
                            artUri = state.artUri,
                            playing = state.isPlaying,
                            name = state.album.ifBlank { null },
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .widthIn(max = 320.dp)
                                .drawBehind {
                                    val r = size.minDimension / 2
                                    drawCircle(Brush.radialGradient(listOf(Palette.Cyan.copy(alpha = 0.16f), Color.Transparent), radius = r * 1.35f), r * 1.35f)
                                    drawCircle(Color.Black.copy(alpha = 0.5f), r, center.copy(y = center.y + 10.dp.toPx()))
                                },
                        )
                    } else {
                        DiscWell(
                            artUri = state.artUri,
                            playing = state.isPlaying,
                            name = state.album.ifBlank { null },
                            modifier = Modifier.fillMaxWidth(0.78f).widthIn(max = 330.dp),
                        )
                    }
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

            if (seven) SevenMeta(state, track, output, noSkipping) else Lcd(
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
                blinkTime = !state.isEmpty && !state.isPlaying,
            )

            if (deckView == DeckView.Disc && !state.isEmpty) CurrentLyric(lyrics, state.positionMs)

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
private fun DeckTabs(current: DeckView, onShow: (DeckView) -> Unit, sleepEndsAt: Long?, onSleep: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
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
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        SleepButton(sleepEndsAt, onSleep)
    }
}

/** The Seven skin's deck tabs: the original pill toggles, centred, with the sleep timer last. */
@Composable
private fun SevenTabs(current: DeckView, onShow: (DeckView) -> Unit, sleepEndsAt: Long?, onSleep: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(DeckView.Disc to "Disc", DeckView.Lyrics to "Lyrics", DeckView.PlayList to "Play list", DeckView.Equalizer to "Equalizer")
            .forEach { (view, label) -> Pill(label, view == current, onClick = { onShow(view) }) }
        SleepButton(sleepEndsAt, onSleep, seven = true)
    }
}

/** The original deck's title block: the song large and light, then what it is as small chips. */
@Composable
private fun SevenMeta(state: NowPlaying, track: TrackInfo?, output: OutputStatus, noSkipping: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            state.title.ifEmpty { "nothing playing" },
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Light, lineHeight = 27.sp, shadow = Shadow(Color.Black.copy(alpha = 0.7f), blurRadius = 8f)),
            color = Palette.NavyText,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (state.isEmpty) "pick something from the library" else listOf(state.artist, state.album).filter { it.isNotBlank() }.joinToString(" — "),
            fontSize = 13.sp,
            color = Palette.NavyDim,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
        if (!state.isEmpty) {
            val usb = output as? OutputStatus.Usb
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                track?.let { Chip(formatLabel(it)) }
                if (state.count > 1) Chip("${state.index + 1} of ${state.count}")
                if (usb != null) Chip(if (usb.bitPerfect) "USB · BIT-PERFECT" else "USB", lit = usb.bitPerfect)
                if (noSkipping) Chip("NO SKIP")
            }
        }
    }
}

@Composable
private fun Chip(text: String, lit: Boolean = false) {
    val shape = RoundedCornerShape(50)
    Text(
        text,
        fontSize = 10.5.sp,
        letterSpacing = 0.08.em,
        color = if (lit) Color.White else Color(0xFF9FD8FF),
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(if (lit) Color(0xAA23466F) else Color(0x7323466F))
            .border(1.dp, Palette.Cyan.copy(alpha = if (lit) 0.8f else 0.35f), shape)
            .padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

@Composable
private fun SleepButton(endsAt: Long?, onSleep: (Int) -> Unit, seven: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    val left = endsAt?.let { ((it - System.currentTimeMillis()) / 60_000 + 1).coerceAtLeast(1) }
    Box {
        if (seven) {
            Pill(if (left != null) "$left min" else "", on = left != null, icon = Glyphs.Moon, onClick = { open = true })
        } else Row(
            Modifier
                .clip(RoundedCornerShape(3.dp))
                .clickable(onClickLabel = "Sleep timer") { open = true }
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Glyphs.Moon, null, tint = if (left != null) Palette.Glow else Color(0xFFAEB6BF), modifier = Modifier.size(15.dp))
            if (left != null) Text(" $left min", fontSize = 12.sp, color = Palette.Glow)
        }
        if (open) {
            Win7Menu(
                listOf(15, 30, 45, 60, 90).map { m -> MenuItem("Stop in $m minutes") { onSleep(m) } } +
                    listOfNotNull(if (endsAt != null) MenuItem("Turn off sleep timer") { onSleep(0) } else null),
                onDismiss = { open = false },
                offsetY = 30.dp,
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
