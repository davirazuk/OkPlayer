package dev.davirazuk.okplayer.ui.components

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import dev.davirazuk.okplayer.ui.theme.Glyphs
import dev.davirazuk.okplayer.ui.theme.Palette

/* ---------------- window frame ---------------- */

data class MenuItem(val label: String, val checked: Boolean = false, val onClick: () -> Unit)

/** A breadcrumb segment. With a [menu], tapping it opens a drop-down like Explorer's. */
data class Crumb(val label: String, val onClick: (() -> Unit)? = null, val menu: List<MenuItem> = emptyList())

/**
 * The Aero window every screen lives in: title bar, glass toolbar with back and
 * forward, breadcrumb, then the content pane and the black control bar.
 */
@Composable
fun AeroWindow(
    title: String,
    crumbs: List<Crumb>,
    canGoBack: Boolean,
    onBack: () -> Unit,
    darkPane: Boolean,
    controlBar: @Composable () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Palette.SkyTop, Palette.SkyMid, Palette.SkyBottom))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Palette.Frame)
                .drawBehind {
                    drawRect(Palette.FrameEdge, style = Stroke(width = 1.dp.toPx()))
                }
                .statusBarsPadding()
                .padding(horizontal = 5.dp),
        ) {
            TitleBar(title)
            Toolbar(crumbs, canGoBack, onBack)
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(1.dp, Palette.FrameLine)
                    .background(if (darkPane) Palette.Black else Palette.Paper)
                    .padding(1.dp),
                content = content,
            )
            controlBar()
        }
    }
}

@Composable
private fun TitleBar(title: String) {
    Row(Modifier.fillMaxWidth().height(30.dp).padding(start = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        // okplayer's own mark: a small disc with the same sheen as the deck.
        Canvas(Modifier.size(16.dp)) {
            val r = size.minDimension / 2
            drawCircle(Color(0xFF3A4654), radius = r)
            drawCircle(
                Brush.sweepGradient(
                    0f to Color(0xFFDCE4EE), 0.18f to Palette.Pink, 0.32f to Color.White, 0.5f to Color(0xFF5BCEFA),
                    0.7f to Color(0xFFE9EEF5), 1f to Color(0xFFDCE4EE),
                ),
                radius = r - 1.dp.toPx() / 2,
            )
            drawCircle(Color(0xFF26384F), radius = r * 0.36f)
            drawCircle(Color.White.copy(alpha = 0.8f), radius = r * 0.12f)
        }
        Text(
            title,
            fontSize = 12.sp,
            color = Color.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 7.dp),
        )
        CaptionButtons(Modifier.align(Alignment.Top))
    }
}

@Composable
private fun CaptionButtons(modifier: Modifier) {
    val edge = Color(0x9922384F)
    val glass = Brush.verticalGradient(0f to Color(0xFFEEF4FA), 0.48f to Color(0xFFC3D6E8), 0.52f to Color(0xFFA9C1D8), 1f to Color(0xFFD4E3F1))
    val close = Brush.verticalGradient(0f to Color(0xFFE9A597), 0.48f to Color(0xFFD4583F), 0.52f to Color(0xFFC23B22), 1f to Color(0xFFE48A6B))
    Row(modifier) {
        Box(Modifier.size(26.dp, 18.dp).clip(RoundedCornerShape(bottomStart = 4.dp)).background(glass).border(1.dp, edge, RoundedCornerShape(bottomStart = 4.dp)))
        Box(Modifier.size(26.dp, 18.dp).background(glass).border(1.dp, edge))
        Box(Modifier.size(44.dp, 18.dp).clip(RoundedCornerShape(bottomEnd = 4.dp)).background(close).border(1.dp, edge, RoundedCornerShape(bottomEnd = 4.dp)))
    }
}

@Composable
private fun Toolbar(crumbs: List<Crumb>, canGoBack: Boolean, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        NavOrb(Glyphs.Back, "Back", enabled = canGoBack, onClick = onBack)
        NavOrb(Glyphs.Forward, "Forward", enabled = false, onClick = {})
        Row(
            Modifier
                .weight(1f)
                .height(28.dp)
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFF3F7FB))))
                .border(1.dp, Color(0xFF8EA4BD))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            crumbs.forEachIndexed { i, crumb ->
                if (i > 0) Text("▸", fontSize = 9.sp, color = Color(0xFF8A9AAC), modifier = Modifier.padding(horizontal = 5.dp))
                val last = i == crumbs.lastIndex
                var open by remember { mutableStateOf(false) }
                val action: (() -> Unit)? = when {
                    crumb.menu.isNotEmpty() -> ({ open = true })
                    else -> crumb.onClick
                }
                Box(if (last) Modifier.weight(1f, fill = false) else Modifier) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .then(if (open) Modifier.background(Palette.Hover).border(1.dp, Palette.HoverEdge, RoundedCornerShape(2.dp)) else Modifier)
                            .then(if (action != null) Modifier.clickable(onClick = action) else Modifier)
                            .padding(horizontal = 3.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(crumb.label, fontSize = 12.5.sp, color = Color(0xFF333333), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (crumb.menu.isNotEmpty()) Text(" ▾", fontSize = 10.sp, color = Color(0xFF55667A))
                    }
                    if (open) Win7Menu(crumb.menu, onDismiss = { open = false })
                }
            }
        }
    }
}

/** Windows 7 context menu: white, grey border, soft shadow, blue selection, check marks. */
@Composable
fun Win7Menu(items: List<MenuItem>, onDismiss: () -> Unit, offsetY: Dp = 26.dp) {
    val below = with(LocalDensity.current) { offsetY.roundToPx() }
    Popup(
        offset = IntOffset(0, below),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .shadow(6.dp)
                .background(Color(0xFFF0F0F0))
                .border(1.dp, Color(0xFF979797))
                .padding(2.dp)
                .width(IntrinsicSize.Max),
        ) {
            items.forEach { item ->
                val source = remember { MutableInteractionSource() }
                val pressed by source.collectIsPressedAsState()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .then(
                            if (pressed) Modifier
                                .background(Brush.verticalGradient(listOf(Color(0xFFF2F7FD), Color(0xFFDDEBFA))))
                                .border(1.dp, Color(0xFFAECFF7), RoundedCornerShape(3.dp))
                            else Modifier,
                        )
                        .clickable(interactionSource = source, indication = null) {
                            onDismiss()
                            item.onClick()
                        }
                        .padding(end = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
                        if (item.checked) Text("●", fontSize = 8.sp, color = Color(0xFF1C3B6E))
                    }
                    Text(item.label, fontSize = 13.sp, color = Color.Black, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun NavOrb(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(CircleShape)
            .background(
                if (enabled) {
                    Brush.radialGradient(0f to Color(0xFFCBE9FF), 0.38f to Color(0xFF5AAEE8), 0.62f to Color(0xFF1F69AD), 1f to Color(0xFF16508A), center = Offset(45f, 22f))
                } else {
                    Brush.radialGradient(listOf(Color(0xFFF4F6F8), Color(0xFFB9C3CE)), center = Offset(45f, 22f))
                },
            )
            .border(1.dp, if (enabled) Color(0xFF1D4F7C) else Color(0xFF8C9BAA), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlassHighlight()
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun BoxScope.GlassHighlight(strength: Float = 0.55f) {
    Canvas(Modifier.matchParentSize()) {
        drawOval(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = strength), Color.Transparent), endY = size.height * 0.5f),
            topLeft = Offset(size.width * 0.14f, size.height * 0.04f),
            size = Size(size.width * 0.72f, size.height * 0.46f),
        )
    }
}

/* ---------------- library pane pieces ---------------- */

/** Explorer's command bar: flat text commands that light up when touched. */
@Composable
fun CommandBar(modifier: Modifier = Modifier, trailing: String? = null, commands: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(Brush.verticalGradient(listOf(Palette.CmdTop, Palette.CmdBottom)))
            .drawBehind { drawLine(Palette.CmdEdge, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f)) }
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        commands()
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            Text(trailing, fontSize = 12.sp, color = Palette.Sub, modifier = Modifier.padding(end = 4.dp), maxLines = 1)
        }
    }
}

@Composable
fun Command(label: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Text(
        label,
        fontSize = 12.5.sp,
        color = Palette.Ink,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .then(
                if (pressed) Modifier
                    .background(Brush.verticalGradient(listOf(Color(0xFFFBFDFF), Color(0xFFDBEAF8))))
                    .border(1.dp, Color(0xFFA8C8E4), RoundedCornerShape(3.dp))
                else Modifier,
            )
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** The blue selection look Windows uses for list items, shown while pressed or when current. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.explorerItem(
    selected: Boolean,
    source: MutableInteractionSource,
    pressed: Boolean,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val shape = RoundedCornerShape(3.dp)
    val (fill, edge) = when {
        pressed -> Palette.Selected to Palette.SelectedEdge
        selected -> Palette.Selected to Palette.SelectedEdge
        else -> Color.Transparent to Color.Transparent
    }
    return this
        .clip(shape)
        .background(fill)
        .border(1.dp, edge, shape)
        .combinedClickable(interactionSource = source, indication = null, onLongClick = onLongClick, onClick = onClick)
}

@Composable
fun GroupHeader(label: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = Palette.Heading)
        Box(
            Modifier
                .padding(start = 8.dp)
                .weight(1f)
                .height(1.dp)
                .background(Brush.horizontalGradient(listOf(Color(0xFFC5D3E3), Color(0x00C5D3E3)))),
        )
    }
}

/** Windows 7 push button. */
@Composable
fun Win7Button(label: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val fill = if (pressed) {
        Brush.verticalGradient(0f to Color(0xFFE5F4FC), 0.49f to Color(0xFFC4E5F6), 0.51f to Color(0xFF98D1EF), 1f to Color(0xFF68B3DB))
    } else {
        Brush.verticalGradient(0f to Color(0xFFF2F2F2), 0.49f to Color(0xFFEBEBEB), 0.51f to Color(0xFFDDDDDD), 1f to Color(0xFFCFCFCF))
    }
    Text(
        label,
        fontSize = 12.5.sp,
        color = Color.Black,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(fill)
            .border(1.dp, if (pressed) Color(0xFF2C628B) else Color(0xFF707070), RoundedCornerShape(3.dp))
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 5.dp),
    )
}

/** Windows 7 check box with its label. */
@Composable
fun Win7Check(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier.clickable(role = Role.Checkbox) { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(15.dp)
                .border(1.dp, Color(0xFF8E8F8F))
                .padding(2.dp)
                .background(Brush.linearGradient(listOf(Color(0xFFDCDCDC), Color.White))),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w * 0.12f, w * 0.52f)
                        lineTo(w * 0.40f, w * 0.80f)
                        lineTo(w * 0.90f, w * 0.18f)
                    }
                    drawPath(path, Color(0xFF1C3B6E), style = Stroke(width = 2.dp.toPx()))
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
fun InfoBar(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 12.5.sp,
        color = Color(0xFF5A4A0C),
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFFFFBE6))
            .border(1.dp, Color(0xFFE3CF7A))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
fun Stars(rating: Int, modifier: Modifier = Modifier, size: Dp = 12.dp, off: Color = Color(0xFFC9CED6)) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        for (i in 1..5) {
            Icon(Glyphs.Star, null, tint = if (i <= rating) Palette.Star else off, modifier = Modifier.size(size))
        }
    }
}

/**
 * Cover art. Albums without art get a cover of their own: a gradient in a colour
 * derived from [name] with its first letter, so they stay tellable apart.
 */
@Composable
fun Artwork(uri: Uri?, modifier: Modifier = Modifier, name: String? = null) {
    val placeholder: @Composable () -> Unit = {
        if (name.isNullOrBlank()) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFB9C6D6), Color(0xFF7E8FA5)))))
        } else {
            val hue = ((name.hashCode() % 360) + 360) % 360f
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Color.hsv(hue, 0.45f, 0.72f), Color.hsv((hue + 40) % 360, 0.6f, 0.32f)))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.trim().first().uppercase(),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = (maxWidth.value * 0.42f).sp,
                    fontWeight = FontWeight.Light,
                )
            }
        }
    }
    if (uri == null) {
        Box(modifier) { placeholder() }
    } else {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(uri).crossfade(250).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
            loading = { placeholder() },
            error = { placeholder() },
        )
    }
}

/* ---------------- control bar ---------------- */

/**
 * WMP 12's black glass control bar: seek line on top, toggles on the left,
 * the capsule with the play orb in the middle, view switch on the right.
 */
@Composable
fun ControlBar(
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
    onSwitchView: () -> Unit,
    nowPlaying: NowPlayingInfo? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Color.Black)
            .background(Brush.verticalGradient(0f to Color(0xFF3B3E43), 0.46f to Color(0xFF23262A), 0.5f to Color(0xFF0A0B0D), 1f to Color(0xFF141619)))
            .drawBehind { drawLine(Color.White.copy(alpha = 0.22f), Offset(0f, 1f), Offset(size.width, 1f)) }
            .navigationBarsPadding(),
    ) {
        if (nowPlaying != null) NowPlayingStrip(nowPlaying, onSwitchView)
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            var dragFraction by remember { mutableStateOf<Float?>(null) }
            val duration = durationMs.coerceAtLeast(1)
            val live = (positionMs.toFloat() / duration).coerceIn(0f, 1f)
            val shown = dragFraction ?: live
            Text(formatTime((shown * durationMs).toLong()), fontSize = 11.sp, color = Palette.BarDim)
            SeekLine(
                fraction = shown,
                enabled = enabled && durationMs > 0,
                maxFraction = if (noSkipping) live else 1f,
                onDrag = { dragFraction = it },
                onRelease = { f -> onSeek((f * duration).toLong()); dragFraction = null },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(formatTime(durationMs), fontSize = 11.sp, color = Palette.BarDim)
        }
        Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 10.dp)) {
            Row(Modifier.align(Alignment.CenterStart), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                BarToggle(Glyphs.Shuffle, "Shuffle", shuffle, enabled = !noSkipping, onClick = onShuffle)
                BarToggle(Glyphs.Lock, "No skipping", noSkipping, onClick = onNoSkipping)
            }
            Transport(
                isPlaying = isPlaying,
                enabled = enabled,
                canGoNext = canGoNext,
                onTogglePlay = onTogglePlay,
                onPrevious = onPrevious,
                onNext = onNext,
                modifier = Modifier.align(Alignment.Center),
            )
            BarToggle(Glyphs.SwitchView, "Switch view", false, onClick = onSwitchView, modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}

/** What the control bar shows about the current song while you browse the library. */
data class NowPlayingInfo(val title: String, val artist: String, val artUri: Uri?)

@Composable
private fun NowPlayingStrip(info: NowPlayingInfo, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open Now Playing", onClick = onOpen)
            .padding(start = 10.dp, end = 12.dp, top = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(info.artUri, Modifier.size(30.dp).border(1.dp, Color.Black), name = info.title)
        Column(Modifier.padding(start = 9.dp).weight(1f)) {
            Text(info.title, fontSize = 12.5.sp, lineHeight = 15.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(info.artist, fontSize = 11.sp, lineHeight = 13.sp, color = Palette.BarDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SeekLine(
    fraction: Float,
    enabled: Boolean,
    maxFraction: Float,
    onDrag: (Float) -> Unit,
    onRelease: (Float) -> Unit,
    modifier: Modifier,
) {
    var last by remember { mutableStateOf(fraction) }
    Canvas(
        modifier
            .height(22.dp)
            .pointerInput(enabled, maxFraction) {
                if (!enabled) return@pointerInput
                detectTapGestures { o ->
                    val f = (o.x / size.width).coerceIn(0f, maxFraction)
                    onRelease(f)
                }
            }
            .pointerInput(enabled, maxFraction) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = { onRelease(last) },
                    onDragCancel = { onRelease(last) },
                ) { change, _ ->
                    last = (change.position.x / size.width).coerceIn(0f, maxFraction)
                    onDrag(last)
                }
            },
    ) {
        val h = 5.dp.toPx()
        val y = (size.height - h) / 2
        val r = CornerRadius(h / 2, h / 2)
        drawRoundRect(Color(0xFF2B2F35), Offset(0f, y), Size(size.width, h), r)
        drawRoundRect(Color.Black.copy(alpha = 0.6f), Offset(0f, y), Size(size.width, 1.dp.toPx()), r)
        val w = size.width * fraction
        if (w > 0f) {
            drawRoundRect(Brush.horizontalGradient(listOf(Palette.Aero, Color(0xFF9FDCFF)), endX = w.coerceAtLeast(1f)), Offset(0f, y), Size(w, h), r)
        }
        if (enabled) {
            val c = Offset(w.coerceIn(6.dp.toPx(), size.width - 6.dp.toPx()), size.height / 2)
            drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFA8DCFF), Color(0xFF3B92D4)), center = c.copy(y = c.y - 2f), radius = 7.dp.toPx()), 6.5.dp.toPx(), c)
            drawCircle(Color(0xFF0C3F68), 6.5.dp.toPx(), c, style = Stroke(1.dp.toPx()))
        }
    }
}

@Composable
private fun BarToggle(
    icon: ImageVector,
    label: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier
            .size(38.dp, 32.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(shape)
            .then(
                if (on) Modifier
                    .background(Brush.verticalGradient(listOf(Palette.Glow.copy(alpha = 0.22f), Palette.Glow.copy(alpha = 0.05f))))
                    .border(1.dp, Palette.Glow.copy(alpha = 0.35f), shape)
                else Modifier,
            )
            .clickable(enabled = enabled, role = Role.Switch, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (on) Palette.Glow else Color(0xFFCFD6DE), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Transport(
    isPlaying: Boolean,
    enabled: Boolean,
    canGoNext: Boolean,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier.size(150.dp, 52.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .size(150.dp, 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(0f to Color(0xFF5A5F66), 0.48f to Color(0xFF2B2F34), 0.52f to Color(0xFF101214), 1f to Color(0xFF23272B)))
                .border(1.dp, Color.Black, RoundedCornerShape(16.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CapsuleButton(Glyphs.Previous, "Previous", enabled, onPrevious, Modifier.weight(1f).padding(end = 26.dp))
            CapsuleButton(Glyphs.Next, "Next", enabled && canGoNext, onNext, Modifier.weight(1f).padding(start = 26.dp))
        }
        PlayOrb(isPlaying, enabled, onTogglePlay)
    }
}

@Composable
private fun CapsuleButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color(0xFFDFE6EC), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PlayOrb(isPlaying: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        Modifier
            .size(52.dp)
            .drawBehind {
                drawCircle(Palette.Aero.copy(alpha = if (pressed) 0.55f else 0.3f), radius = size.minDimension / 2 + 7.dp.toPx())
                drawCircle(Color.Black.copy(alpha = 0.5f), radius = size.minDimension / 2 + 2.dp.toPx())
            }
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    0f to Color(0xFF8FD3FF), 0.36f to Color(0xFF2C86CF), 0.64f to Color(0xFF0E3D6D), 1f to Color(0xFF06182C),
                ),
            )
            .border(1.dp, Color.Black, CircleShape)
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlassHighlight()
        Icon(
            if (isPlaying) Glyphs.Pause else Glyphs.Play,
            if (isPlaying) "Pause" else "Play",
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
    }
}

fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
