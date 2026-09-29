package dev.davirazuk.okplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.davirazuk.okplayer.audio.LevelMeter
import dev.davirazuk.okplayer.library.Album
import dev.davirazuk.okplayer.library.Track
import dev.davirazuk.okplayer.ui.DeckView
import dev.davirazuk.okplayer.ui.LibraryView
import dev.davirazuk.okplayer.ui.theme.Glyphs
import dev.davirazuk.okplayer.ui.theme.Palette
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import kotlin.math.cos
import kotlin.math.sin

/*
 * The Seven skin: okplayer's original look. A navy deck with a big disc, glass
 * orbs and pill toggles, in an Aero window over a Windows 7 taskbar with Start.
 */

/* ---------------- glass orbs and pills ---------------- */

/** A round glass button: blue for play, smoke grey for the rest. */
@Composable
fun GlassOrb(
    icon: ImageVector,
    label: String,
    size: Dp,
    primary: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val stops = if (primary) {
        listOf(Color(0xFFBFE6FF), Color(0xFF3B95D8), Color(0xFF0E4F8C), Color(0xFF0A3563))
    } else {
        listOf(Color(0xFFE7EEF6), Color(0xFF7D93AB), Color(0xFF34495F), Color(0xFF1D2C3C))
    }
    Box(
        Modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.4f)
            .drawBehind {
                val r = this.size.minDimension / 2
                if (primary) {
                    drawCircle(
                        Brush.radialGradient(listOf(Palette.Cyan.copy(alpha = if (pressed) 0.7f else 0.45f), Color.Transparent), radius = r * 1.45f),
                        radius = r * 1.45f,
                    )
                }
                drawCircle(Color.Black.copy(alpha = 0.45f), radius = r, center = center.copy(y = center.y + 2.dp.toPx()))
            }
            .clip(CircleShape)
            .drawWithCache {
                val r = this.size.minDimension / 2
                val body = Brush.radialGradient(
                    0f to stops[0], 0.45f to stops[1], 0.7f to stops[2], 1f to stops[3],
                    center = Offset(this.size.width / 2, this.size.height * 0.3f),
                    radius = r * 1.3f,
                )
                onDrawBehind {
                    drawCircle(body, r)
                    if (pressed) drawCircle(Color.Black.copy(alpha = 0.18f), r)
                    drawCircle(Color(0xFF0A2A4A), r - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
                }
            }
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlassHighlight(if (primary) 0.6f else 0.5f)
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(size * 0.4f))
    }
}

/** The original deck's rounded toggles: navy when off, lit blue with a glow when on. */
@Composable
fun Pill(
    label: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .height(28.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .then(
                if (on) Modifier.drawBehind {
                    drawRoundRect(Palette.Cyan.copy(alpha = 0.18f), Offset(-3.dp.toPx(), -3.dp.toPx()), Size(size.width + 6.dp.toPx(), size.height + 6.dp.toPx()), CornerRadius(size.height))
                } else Modifier,
            )
            .clip(shape)
            .background(if (on) Palette.PillOn else Palette.Pill)
            .border(1.dp, if (on) Palette.Cyan else Palette.PillEdge, shape)
            .clickable(enabled = enabled, role = Role.Switch, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = if (on) Color.White else Palette.NavyDim, modifier = Modifier.size(14.dp))
        if (label.isNotEmpty()) Text(label, fontSize = 12.sp, color = if (on) Color.White else Palette.NavyDim, maxLines = 1)
    }
}

/* ---------------- the transport ---------------- */

/**
 * The Seven skin's controls. On Now Playing: the seek line, the three orbs and the
 * toggles under the deck. Elsewhere: a strip with the song and smaller orbs.
 */
@Composable
fun SevenControls(
    compact: Boolean,
    info: NowPlayingInfo?,
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    isPlaying: Boolean,
    shuffle: Boolean,
    noSkipping: Boolean,
    canGoNext: Boolean,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onNoSkipping: () -> Unit,
    onOpenNowPlaying: () -> Unit,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(1)
    val live = (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val shown = dragFraction ?: live
    val seek: @Composable (Modifier, Boolean) -> Unit = { modifier, big ->
        SeekLine(
            fraction = shown,
            enabled = enabled && durationMs > 0,
            maxFraction = if (noSkipping) live else 1f,
            onDrag = { dragFraction = it },
            onRelease = { f -> onSeek((f * duration).toLong()); dragFraction = null },
            modifier = modifier,
            trackHeight = if (big) 6.dp else 4.dp,
            thumbRadius = if (big) 8.dp else 6.dp,
            track = Color(0xFF2A394A),
            fill = listOf(Palette.Cyan, Color.White),
        )
    }

    if (compact) {
        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, Palette.FrameLine)
                .background(Brush.verticalGradient(listOf(Color(0xFF1C2D42), Palette.Navy))),
        ) {
            seek(Modifier.fillMaxWidth().padding(horizontal = 12.dp), false)
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(3.dp))
                        .clickable(onClickLabel = "Open Now Playing", onClick = onOpenNowPlaying)
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(info?.artUri, Modifier.size(40.dp).clip(RoundedCornerShape(2.dp)), name = info?.title ?: "okplayer")
                    Column(Modifier.padding(start = 9.dp)) {
                        Text(info?.title ?: "Nothing playing", fontSize = 13.sp, lineHeight = 16.sp, color = Palette.NavyText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(info?.artist ?: "okplayer", fontSize = 11.5.sp, lineHeight = 14.sp, color = Palette.NavyDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassOrb(Glyphs.Previous, "Previous", 36.dp, primary = false, enabled = enabled, onClick = onPrevious)
                    GlassOrb(if (isPlaying) Glyphs.Pause else Glyphs.Play, if (isPlaying) "Pause" else "Play", 48.dp, primary = true, enabled = enabled, onClick = onTogglePlay)
                    GlassOrb(Glyphs.Next, "Next", 36.dp, primary = false, enabled = enabled && canGoNext, onClick = onNext)
                }
            }
        }
        return
    }

    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Palette.FrameLine)
            .background(Palette.Navy)
            .padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        seek(Modifier.fillMaxWidth().padding(top = 2.dp), true)
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime((shown * durationMs).toLong()), fontSize = 11.sp, color = Palette.NavyDim)
            Spacer(Modifier.weight(1f))
            Text(formatTime(durationMs), fontSize = 11.sp, color = Palette.NavyDim)
        }
        Row(
            Modifier.padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassOrb(Glyphs.Previous, "Previous", 46.dp, primary = false, enabled = enabled, onClick = onPrevious)
            GlassOrb(if (isPlaying) Glyphs.Pause else Glyphs.Play, if (isPlaying) "Pause" else "Play", 68.dp, primary = true, enabled = enabled, onClick = onTogglePlay)
            GlassOrb(Glyphs.Next, "Next", 46.dp, primary = false, enabled = enabled && canGoNext, onClick = onNext)
        }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("shuffle", shuffle, icon = Glyphs.Shuffle, enabled = !noSkipping, onClick = onShuffle)
            Pill("no skipping", noSkipping, icon = Glyphs.Lock, onClick = onNoSkipping)
        }
    }
}

/* ---------------- spectrum ---------------- */

private const val BARS = 32

/**
 * The original deck's spectrum along the bottom: pink through lavender to blue.
 * It follows the live bass / mid / treble levels, spread across the bars.
 */
@Composable
fun Spectrum(playing: Boolean, modifier: Modifier = Modifier) {
    var time by remember { mutableFloatStateOf(0f) }
    val levels = remember { FloatArray(BARS) }
    LaunchedEffect(playing) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                time += if (last == 0L) 0f else (now - last) / 1e9f
                last = now
            }
        }
    }
    Canvas(modifier) {
        val t = time
        val bw = size.width / BARS
        for (i in 0 until BARS) {
            val p = i / (BARS - 1f)
            val base = if (!playing) 0f else if (p < 0.5f) {
                LevelMeter.bass + (LevelMeter.mid - LevelMeter.bass) * p * 2
            } else {
                LevelMeter.mid + (LevelMeter.treble - LevelMeter.mid) * (p - 0.5f) * 2
            }
            val wobble = 0.55f + 0.45f * sin(t * 6.3f + i * 1.9f) * cos(t * 2.9f + i * 0.7f)
            val target = (base * (0.55f + 0.6f * wobble) * 1.4f).coerceIn(0f, 1f)
            levels[i] = if (target > levels[i]) target else levels[i] * 0.9f
            val h = (levels[i] * size.height).coerceAtLeast(1.5.dp.toPx())
            val hue = 330f - p * 130f
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.hsv(hue % 360f, 0.45f, 1f, 0.5f), Color.hsv(hue % 360f, 0.6f, 0.75f, 0.1f)),
                    startY = size.height - h,
                    endY = size.height,
                ),
                topLeft = Offset(i * bw + 1.dp.toPx(), size.height - h),
                size = Size(bw - 2.dp.toPx(), h),
            )
        }
    }
}

/* ---------------- the taskbar ---------------- */

/**
 * Windows 7's taskbar along the bottom: Start, a button for the library and one for
 * Now Playing, then the tray with the USB DAC and sleep icons and the clock.
 */
@Composable
fun Taskbar(
    nowPlayingTitle: String?,
    onNowPlaying: Boolean,
    onLibrary: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    startOpen: Boolean,
    onStart: () -> Unit,
    onStartDismiss: () -> Unit,
    usbConnected: Boolean,
    onUsb: () -> Unit,
    sleeping: Boolean,
    onSleep: () -> Unit,
    startMenu: @Composable () -> Unit,
) {
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(0f to Color(0xFF3E6082), 0.5f to Color(0xFF16283C), 1f to Color(0xFF081422)))
                .drawBehind { drawLine(Color.White.copy(alpha = 0.4f), Offset(0f, 0.5f), Offset(size.width, 0.5f), 1.dp.toPx()) }
                .navigationBarsPadding()
                .height(44.dp)
                .padding(start = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StartOrb(open = startOpen, onClick = onStart)
            TaskButton("Library", active = !onNowPlaying, maxWidth = 110.dp, onClick = onLibrary) { StartIcon(StartIcons.Library, 22.dp) }
            TaskButton(nowPlayingTitle ?: "Now Playing", active = onNowPlaying, maxWidth = 170.dp, onClick = onOpenNowPlaying) {
                StartIcon(StartIcons.NowPlaying, 22.dp)
            }
            Spacer(Modifier.weight(1f))
            if (usbConnected) TrayIcon(Glyphs.Usb, "USB DAC", lit = true, onClick = onUsb)
            if (sleeping) TrayIcon(Glyphs.Moon, "Sleep timer", lit = true, onClick = onSleep)
            Clock()
        }
        if (startOpen) {
            Popup(popupPositionProvider = AboveAnchor, onDismissRequest = onStartDismiss, properties = PopupProperties(focusable = true)) {
                startMenu()
            }
        }
    }
}

/** Places a popup right above whatever it's anchored to, at its left edge. */
private object AboveAnchor : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) =
        IntOffset(anchorBounds.left + 2, (anchorBounds.top - popupContentSize.height).coerceAtLeast(0))
}

@Composable
private fun StartOrb(open: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val lit = open || pressed
    Canvas(
        Modifier
            .padding(end = 4.dp)
            .size(40.dp)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = "Start", onClick = onClick),
    ) {
        val r = size.minDimension / 2 - 1.dp.toPx()
        drawCircle(Brush.radialGradient(listOf(Color(0xFF78C8FF).copy(alpha = if (lit) 0.9f else 0.35f), Color.Transparent), radius = r * 1.25f), r * 1.25f)
        drawCircle(
            Brush.radialGradient(
                0f to Color(0xFFE3F2FF), 0.22f to Color(0xFF79B7EA), 0.48f to Color(0xFF2167A8), 0.72f to Color(0xFF0C3765), 1f to Color(0xFF061A33),
                center = Offset(center.x, center.y - r * 0.44f),
                radius = r * 1.5f,
            ),
            r,
        )
        drawCircle(Color(0xCC00142A), r, style = Stroke(1.dp.toPx()))
        // Four panes, leaning slightly like the real flag.
        val q = r * 0.3f
        val gap = 1.2.dp.toPx()
        val colors = listOf(Color(0xFFF25022), Color(0xFF7FBA00), Color(0xFF00A4EF), Color(0xFFFFB900))
        colors.forEachIndexed { i, c ->
            val dx = if (i % 2 == 0) -q - gap / 2 else gap / 2
            val dy = if (i < 2) -q - gap / 2 else gap / 2
            drawRoundRect(c, Offset(center.x + dx, center.y + dy + (if (i % 2 == 0) 0.8f else -0.4f) * gap), Size(q, q), CornerRadius(1.5f))
        }
        drawOval(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.65f), Color.Transparent), startY = center.y - r, endY = center.y),
            topLeft = Offset(center.x - r * 0.64f, center.y - r * 0.94f),
            size = Size(r * 1.28f, r * 0.9f),
        )
    }
}

@Composable
private fun RowScope.TaskButton(label: String, active: Boolean, maxWidth: Dp, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val shape = RoundedCornerShape(3.dp)
    Row(
        Modifier
            .padding(horizontal = 2.dp)
            .height(40.dp)
            .widthIn(max = maxWidth)
            .clip(shape)
            .then(
                if (active) Modifier
                    .background(Brush.verticalGradient(listOf(Color(0x55FFFFFF), Color(0x14FFFFFF))))
                    .drawBehind {
                        drawOval(
                            Brush.radialGradient(listOf(Color(0x7378C8FF), Color.Transparent), center = Offset(size.width / 2, size.height), radius = size.width * 0.6f),
                            topLeft = Offset(0f, size.height * 0.4f),
                            size = Size(size.width, size.height * 1.2f),
                        )
                    }
                    .border(1.dp, Color(0x73FFFFFF), shape)
                else Modifier,
            )
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Text(label, fontSize = 12.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 7.dp))
    }
}

@Composable
private fun TrayIcon(icon: ImageVector, label: String, lit: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp, 36.dp)
            .clip(RoundedCornerShape(3.dp))
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (lit) Color(0xFF9FE0FF) else Color.White, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun Clock() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            now = System.currentTimeMillis()
        }
    }
    val date = Date(now)
    Column(Modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(date), fontSize = 11.5.sp, lineHeight = 14.sp, color = Color.White)
        Text(DateFormat.getDateInstance(DateFormat.SHORT).format(date), fontSize = 11.sp, lineHeight = 13.sp, color = Color.White)
    }
}

/* ---------------- the Start menu ---------------- */

/** Coloured icons for the Start menu and taskbar, drawn from layered paths. */
object StartIcons {
    val NowPlaying = layered(
        0xFF7FA3C7 to "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20z",
        0xFFE3EBF4 to "M12 2.9a9.1 9.1 0 1 0 0 18.2a9.1 9.1 0 1 0 0-18.2z",
        0xFF3A8FD4 to "M12 7.6a4.4 4.4 0 1 0 0 8.8a4.4 4.4 0 1 0 0-8.8z",
        0xFFFFFFFF to "M12 10.6a1.4 1.4 0 1 0 0 2.8a1.4 1.4 0 1 0 0-2.8z",
    )
    val Library = layered(
        0xFFE0A52C to "M10 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z",
        0xFFF7D67A to "M2 9h20v9c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2z",
    )
    val Artists = layered(
        0xFF8E6FD8 to "M12 4a4 4 0 1 0 0 8a4 4 0 1 0 0-8z",
        0xFFB49CF0 to "M4 20c0-4 3.6-6 8-6s8 2 8 6z",
    )
    val Albums = layered(
        0xFF2F9E8F to "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
        0xFFBFEEE6 to "M12 7a5 5 0 1 0 0 10a5 5 0 1 0 0-10z",
        0xFF2F9E8F to "M12 10.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3z",
    )
    val Songs = layered(0xFFE0508F to "M12 3v10.55A4 4 0 1 0 14 17V7h4V3h-6z")
    val Added = layered(0xFFF5B301 to "M12 2l2.9 6.6 7.1.6-5.4 4.7 1.6 7L12 17.3 5.8 21l1.6-7L2 9.2l7.1-.6z")
    val Most = layered(
        0xFF4A9BE0 to "M3 12h4v9H3z",
        0xFF2F7CC4 to "M10 7h4v14h-4z",
        0xFF1D5A99 to "M17 3h4v18h-4z",
    )
    val Played = layered(
        0xFF3F8F4F to "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20z",
        0xFFFFFFFF to "M12 4a8 8 0 1 0 0 16a8 8 0 1 0 0-16z",
        0xFF3F8F4F to "M11 7h2v5.2l3.4 2.4-1.2 1.6L11 13.2z",
    )
    val Options = layered(
        0xFF6A7F99 to "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.49.49 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.48.48 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z",
    )
    val Refresh = layered(
        0xFF3F8F4F to "M17.65 6.35A7.96 7.96 0 0 0 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08A5.99 5.99 0 0 1 12 18c-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z",
    )

    private fun layered(vararg layers: Pair<Long, String>): ImageVector {
        val b = ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        layers.forEach { (color, path) -> b.addPath(pathData = addPathNodes(path), fill = SolidColor(Color(color))) }
        return b.build()
    }
}

@Composable
fun StartIcon(icon: ImageVector, size: Dp = 24.dp) {
    Icon(icon, null, tint = Color.Unspecified, modifier = Modifier.size(size))
}

/**
 * Windows 7's Start menu: programs on white at the left with a search box, places on
 * glass at the right under a picture (the album playing), and Stop at the bottom.
 */
@Composable
fun StartMenuPanel(
    albums: List<Album>,
    playingTitle: String?,
    playingArtist: String?,
    playingArt: android.net.Uri?,
    playingAlbum: String?,
    sleepEndsAt: Long?,
    onNowPlaying: () -> Unit,
    onView: (LibraryView) -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenArtist: (String) -> Unit,
    onPlaySongs: (List<Track>, Int) -> Unit,
    onDeck: (DeckView) -> Unit,
    onSleep: (Int) -> Unit,
    onSkin: () -> Unit,
    onOptions: () -> Unit,
    onRefresh: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
) {
    var query by remember { mutableStateOf(initialQuery) }
    val menuHeight = minOf((LocalConfiguration.current.screenHeightDp * 0.78f).dp, 560.dp)
    val act: (() -> Unit) -> () -> Unit = { f -> { onDismiss(); f() } }
    val results = remember(query, albums) { StartSearch.run(query, albums) }

    Row(
        modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth(0.97f)
            .height(menuHeight)
            .shadow(10.dp, RoundedCornerShape(7.dp))
            .clip(RoundedCornerShape(7.dp))
            .background(Brush.verticalGradient(0f to Color(0xF04C78A0), 0.45f to Color(0xF51A3450), 1f to Color(0xFA0C1A2A)))
            .border(1.dp, Color(0x99FFFFFF), RoundedCornerShape(7.dp))
            .padding(8.dp),
    ) {
        // Left: white, with the programs (library views) or search results, and the search box.
        Column(
            Modifier
                .weight(1.45f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFF1E3348), RoundedCornerShape(4.dp)),
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(5.dp)) {
                if (query.isBlank()) {
                    StartItem(StartIcons.NowPlaying, "Now Playing", playingTitle?.let { "$it — ${playingArtist.orEmpty()}" } ?: "Nothing playing", onClick = act(onNowPlaying))
                    StartItem(StartIcons.Library, "Library", onClick = act { onView(LibraryView.Albums) })
                    StartRule()
                    StartItem(StartIcons.Artists, "Artists", onClick = act { onView(LibraryView.Artists) })
                    StartItem(StartIcons.Albums, "Albums", onClick = act { onView(LibraryView.Albums) })
                    StartItem(StartIcons.Songs, "Songs", onClick = act { onView(LibraryView.Songs) })
                    StartItem(StartIcons.Added, "Recently added", onClick = act { onView(LibraryView.RecentlyAdded) })
                    StartItem(StartIcons.Most, "Most played", onClick = act { onView(LibraryView.MostPlayed) })
                    StartItem(StartIcons.Played, "Recently played", onClick = act { onView(LibraryView.RecentlyPlayed) })
                    StartRule()
                    StartItem(StartIcons.Refresh, "Refresh library", onClick = act(onRefresh))
                } else if (results.isEmpty) {
                    Text("No items match your search.", fontSize = 12.5.sp, color = Palette.Sub, modifier = Modifier.padding(10.dp))
                } else {
                    var first = true
                    if (results.artists.isNotEmpty()) {
                        StartHeading("Artists", results.artists.size)
                        results.artists.take(3).forEach { a ->
                            StartItem(StartIcons.Artists, a.first, plural(a.second, "album"), highlighted = first, onClick = act { onOpenArtist(a.first) })
                            first = false
                        }
                    }
                    if (results.albums.isNotEmpty()) {
                        StartHeading("Albums", results.albums.size)
                        results.albums.take(4).forEach { al ->
                            StartItem(null, al.title, al.artist, art = al.artUri, highlighted = first, onClick = act { onOpenAlbum(al) })
                            first = false
                        }
                    }
                    if (results.songs.isNotEmpty()) {
                        StartHeading("Songs", results.songs.size)
                        results.songs.take(6).forEachIndexed { i, t ->
                            StartItem(null, t.title, "${t.artist} — ${t.album}", art = t.artUri, highlighted = first, onClick = act { onPlaySongs(results.songs, i) })
                            first = false
                        }
                    }
                }
            }
            StartSearchBox(query, { query = it }) {
                if (!results.isEmpty) onDismiss()
                when {
                    results.artists.isNotEmpty() -> onOpenArtist(results.artists[0].first)
                    results.albums.isNotEmpty() -> onOpenAlbum(results.albums[0])
                    results.songs.isNotEmpty() -> onPlaySongs(results.songs, 0)
                }
            }
        }

        // Right: glass, with the album playing as the account picture.
        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp, end = 2.dp)) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 2.dp, bottom = 8.dp)
                    .size(64.dp)
                    .shadow(4.dp, RoundedCornerShape(5.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xE6FFFFFF), Color(0x99C8E1F5))), RoundedCornerShape(5.dp))
                    .padding(5.dp),
            ) {
                Artwork(playingArt, Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)), name = playingAlbum ?: "okplayer")
            }
            StartLink(playingArtist ?: "okplayer", bold = true, onClick = act { if (playingArtist != null) onOpenArtist(playingArtist) else onNowPlaying() })
            StartLink("Lyrics", onClick = act { onDeck(DeckView.Lyrics) })
            StartLink("Play list", onClick = act { onDeck(DeckView.PlayList) })
            StartLink("Equalizer", onClick = act { onDeck(DeckView.Equalizer) })
            var sleepMenu by remember { mutableStateOf(false) }
            Box {
                val left = sleepEndsAt?.let { ((it - System.currentTimeMillis()) / 60_000 + 1).coerceAtLeast(1) }
                StartLink(if (left != null) "Sleep timer ($left min)" else "Sleep timer", onClick = { sleepMenu = true })
                if (sleepMenu) {
                    Win7Menu(
                        listOf(15, 30, 45, 60, 90).map { m -> MenuItem("Stop in $m minutes") { onSleep(m); onDismiss() } } +
                            listOfNotNull(if (sleepEndsAt != null) MenuItem("Turn off sleep timer") { onSleep(0); onDismiss() } else null),
                        onDismiss = { sleepMenu = false },
                        offsetY = 30.dp,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp).height(1.dp).background(Color(0x2EFFFFFF)))
            StartLink("WMP 12 skin", onClick = act(onSkin))
            StartLink("Options", onClick = act(onOptions))
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .align(Alignment.End)
                    .padding(top = 10.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.verticalGradient(0f to Color(0xFFD9695A), 0.5f to Color(0xFFB8412C), 0.51f to Color(0xFF9E2C18), 1f to Color(0xFFC9573D)))
                    .border(1.dp, Color(0x99000000), RoundedCornerShape(3.dp))
                    .clickable(onClick = act(onStop))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Stop", fontSize = 12.5.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun StartItem(
    icon: ImageVector?,
    label: String,
    sub: String? = null,
    art: android.net.Uri? = null,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(3.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (sub != null) 44.dp else 40.dp)
            .clip(shape)
            .then(
                if (pressed || highlighted) Modifier
                    .background(Brush.verticalGradient(listOf(Color(0xFFF2F8FE), Color(0xFFDBEAF9))))
                    .border(1.dp, Color(0xFFA6CAF0), shape)
                else Modifier,
            )
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (icon != null) StartIcon(icon, 26.dp) else Artwork(art, Modifier.size(28.dp).clip(RoundedCornerShape(2.dp)), name = label)
        }
        Column(Modifier.padding(start = 9.dp)) {
            Text(label, fontSize = 13.sp, lineHeight = 16.sp, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, fontSize = 11.sp, lineHeight = 13.sp, color = Palette.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StartRule() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).height(1.dp).background(Color(0xFFE3E8EE)))
}

@Composable
private fun StartHeading(label: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$label ($count)", fontSize = 12.sp, color = Palette.Heading)
        Box(Modifier.padding(start = 6.dp).weight(1f).height(1.dp).background(Color(0xFFD8E2EE)))
    }
}

@Composable
private fun StartLink(label: String, bold: Boolean = false, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(3.dp)
    Text(
        label,
        style = TextStyle(fontSize = 13.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, color = Color.White),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (pressed) Modifier
                    .background(Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x14FFFFFF))))
                    .border(1.dp, Color(0x59FFFFFF), shape)
                else Modifier,
            )
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 8.dp),
    )
}

@Composable
private fun StartSearchBox(query: String, onQuery: (String) -> Unit, onGo: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFFF4F7FB), Color(0xFFDFE8F2))))
            .padding(8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .background(Color.White)
                .border(1.dp, Color(0xFF8A9EB6))
                .padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text("Search music", fontSize = 12.5.sp, fontStyle = FontStyle.Italic, color = Palette.Sub)
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp, color = Color.Black),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onGo() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Icon(Glyphs.Search, "Search", tint = Color(0xFF4F6D8F), modifier = Modifier.size(16.dp))
        }
    }
}

/** What the Start menu's search box finds, like Windows 7 grouping programs and files. */
data class StartResults(val artists: List<Pair<String, Int>>, val albums: List<Album>, val songs: List<Track>) {
    val isEmpty get() = artists.isEmpty() && albums.isEmpty() && songs.isEmpty()
}

object StartSearch {
    fun run(query: String, albums: List<Album>): StartResults {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return StartResults(emptyList(), emptyList(), emptyList())
        val artists = albums.groupBy { it.artist }
            .filterKeys { it.lowercase().contains(q) }
            .map { (name, list) -> name to list.size }
            .sortedBy { it.first.lowercase() }
        val albumHits = albums.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
        val songs = albums.asSequence().flatMap { it.tracks }
            .filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q) }
            .sortedBy { it.title.lowercase() }
            .toList()
        return StartResults(artists, albumHits, songs)
    }
}

private fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"
