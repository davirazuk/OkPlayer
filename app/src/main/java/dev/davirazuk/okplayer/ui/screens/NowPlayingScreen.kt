package dev.davirazuk.okplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.davirazuk.okplayer.audio.OutputStatus
import dev.davirazuk.okplayer.audio.formatRate
import dev.davirazuk.okplayer.playback.NowPlaying
import dev.davirazuk.okplayer.ui.components.Aurora
import dev.davirazuk.okplayer.ui.components.Disc
import dev.davirazuk.okplayer.ui.components.OrbButton
import dev.davirazuk.okplayer.ui.components.formatTime
import dev.davirazuk.okplayer.ui.theme.Glyphs
import dev.davirazuk.okplayer.ui.theme.Palette

@Composable
fun NowPlayingScreen(
    state: NowPlaying,
    output: OutputStatus,
    favorite: Boolean,
    noSkipping: Boolean,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffle: (Boolean) -> Unit,
    onNoSkipping: (Boolean) -> Unit,
    onFavorite: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Aurora(playing = state.isPlaying)

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Glyphs.Back, "Back", tint = Palette.Text) }
                Text(
                    if (state.count > 0) "TRACK ${state.index + 1} OF ${state.count}" else "NOW PLAYING",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Muted,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = onFavorite, enabled = !state.isEmpty) {
                    Icon(
                        if (favorite) Glyphs.Heart else Glyphs.HeartOutline,
                        if (favorite) "Remove from loved" else "Love this song",
                        tint = if (favorite) Palette.Pink else Palette.Muted,
                    )
                }
            }

            Spacer(Modifier.weight(0.6f))
            Disc(artUri = state.artUri, playing = state.isPlaying, modifier = Modifier.fillMaxWidth(0.82f))
            Spacer(Modifier.weight(0.5f))

            Text(
                state.title.ifEmpty { "Nothing playing" },
                style = MaterialTheme.typography.titleLarge,
                color = Palette.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                listOf(state.artist, state.album).filter { it.isNotBlank() }.joinToString("  ·  ")
                    .ifEmpty { "Pick an album in your library" },
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )

            Seekbar(state, noSkipping, onSeek)

            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OrbButton(Glyphs.Previous, "Previous", onPrevious, size = 46.dp, primary = false, enabled = !state.isEmpty)
                OrbButton(
                    if (state.isPlaying) Glyphs.Pause else Glyphs.Play,
                    if (state.isPlaying) "Pause" else "Play",
                    onTogglePlay,
                    size = 72.dp,
                    enabled = !state.isEmpty,
                )
                OrbButton(
                    Glyphs.Next, "Next", onNext, size = 46.dp, primary = false,
                    enabled = !state.isEmpty && state.hasNext && !noSkipping,
                )
            }

            Row(
                Modifier.padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModeChip(Glyphs.InOrder, "In order", selected = !state.shuffle) { onShuffle(false) }
                ModeChip(Glyphs.Shuffle, "Shuffle", selected = state.shuffle, enabled = !noSkipping) { onShuffle(true) }
                ModeChip(Glyphs.Lock, "No skipping", selected = noSkipping) { onNoSkipping(!noSkipping) }
            }

            Spacer(Modifier.weight(0.4f))
            OutputLine(output, Modifier.padding(bottom = 12.dp))
        }
    }
}

@Composable
private fun Seekbar(state: NowPlaying, noSkipping: Boolean, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1)
    val fraction = dragging ?: (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)

    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Slider(
            value = fraction,
            onValueChange = { v ->
                val allowed = if (noSkipping) v.coerceAtMost(state.positionMs.toFloat() / duration) else v
                dragging = allowed
            },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * duration).toLong()) }
                dragging = null
            },
            enabled = !state.isEmpty,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Palette.Aero,
                inactiveTrackColor = Palette.Line,
                disabledThumbColor = Palette.Faint,
                disabledInactiveTrackColor = Palette.Line,
            ),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime((fraction * state.durationMs).toLong()), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            Spacer(Modifier.weight(1f))
            Text(formatTime(state.durationMs), style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
        }
    }
}

@Composable
private fun ModeChip(icon: ImageVector, label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .background(if (selected) Color(0xFF23466F) else Palette.Surface.copy(alpha = 0.7f))
            .border(1.dp, if (selected) Palette.Aero else Palette.Line, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = when {
            !enabled -> Palette.Faint
            selected -> Palette.Text
            else -> Palette.Muted
        }
        Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@Composable
fun OutputLine(output: OutputStatus, modifier: Modifier = Modifier) {
    val (dot, text) = describe(output)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (output is OutputStatus.Usb) {
            Icon(Glyphs.Usb, null, tint = Palette.Muted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Palette.Muted, maxLines = 2)
    }
}

private fun describe(o: OutputStatus): Pair<Color, String> {
    val format = listOfNotNull(o.sampleRate?.let(::formatRate), o.bitDepth?.let { "$it-bit" }).joinToString(" · ")
    return when (o) {
        OutputStatus.Idle -> Palette.Faint to "No output"
        is OutputStatus.Internal -> Palette.Muted to listOf("Phone audio", format, "mixed by Android").filter { it.isNotEmpty() }.joinToString(" · ")
        is OutputStatus.Usb ->
            if (o.bitPerfect) Palette.Good to listOf(o.deviceName, format, "bit-perfect").filter { it.isNotEmpty() }.joinToString(" · ")
            else Palette.Warn to listOfNotNull(o.deviceName, format.ifEmpty { null }, o.reason ?: "resampled").joinToString(" · ")
    }
}
