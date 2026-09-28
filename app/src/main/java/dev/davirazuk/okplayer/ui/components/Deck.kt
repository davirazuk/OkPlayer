package dev.davirazuk.okplayer.ui.components

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.davirazuk.okplayer.audio.LevelMeter
import dev.davirazuk.okplayer.ui.theme.Palette
import kotlin.math.cos
import kotlin.math.sin

/** The disc sitting in a recessed well, like the tray of a CD player. */
@Composable
fun DiscWell(artUri: Uri?, playing: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .aspectRatio(1f)
            .drawBehind {
                val r = size.minDimension / 2f
                drawCircle(Color.Black.copy(alpha = 0.6f), radius = r + 10.dp.toPx(), center = center.copy(y = center.y + 12.dp.toPx()))
                drawCircle(Color(0xFF33383F), radius = r + 6.dp.toPx())
                drawCircle(Color(0xFF131518), radius = r + 5.dp.toPx())
                drawCircle(Color(0xFF2B2F35), radius = r + 1.dp.toPx())
                drawCircle(
                    Brush.radialGradient(
                        0.00f to Color(0xFF0B0C0E),
                        0.62f to Color(0xFF0B0C0E),
                        0.70f to Color(0xFF15171A),
                        1.00f to Color(0xFF050506),
                        center = center,
                        radius = r,
                    ),
                    radius = r,
                )
                // Inner shadow along the top edge of the well.
                drawCircle(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent), startY = 0f, endY = size.height * 0.3f),
                    radius = r,
                )
            }
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Disc(artUri = artUri, playing = playing, modifier = Modifier.fillMaxWidth(0.91f))
    }
}

private val lcdGlow = Shadow(color = Palette.LcdOn.copy(alpha = 0.55f), blurRadius = 10f)
private fun lcd(size: TextUnit, color: Color = Palette.LcdOn, glow: Boolean = true, spacing: TextUnit = 0.06.sp) =
    TextStyle(fontFamily = FontFamily.Monospace, fontSize = size, color = color, letterSpacing = spacing, shadow = if (glow) lcdGlow else null)

data class Lamp(val label: String, val lit: Boolean)

/** Backlit segment-style display under the disc. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Lcd(
    track: String,
    time: String,
    title: String,
    subtitle: String,
    lamps: List<Lamp>,
    format: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(4.dp)
    Column(
        modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .border(1.dp, Color(0xFF30353C), RoundedCornerShape(7.dp))
            .padding(1.dp)
            .border(3.dp, Color(0xFF121418), RoundedCornerShape(6.dp))
            .padding(3.dp)
            .border(1.dp, Color(0xFF2A2F36), shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF0A2025), Palette.LcdBg)))
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent), endY = 8.dp.toPx()))
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("TRACK", style = lcd(10.sp, spacing = 0.14.sp), modifier = Modifier.padding(end = 6.dp, bottom = 5.dp))
            Text(track, style = lcd(30.sp))
            Spacer(Modifier.weight(1f))
            Text(time, style = lcd(30.sp))
        }
        Text(
            title.uppercase(),
            style = lcd(15.sp),
            maxLines = 1,
            modifier = Modifier.padding(top = 8.dp).basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1500),
        )
        Text(subtitle.uppercase(), style = lcd(12.sp, color = Palette.LcdOn.copy(alpha = 0.75f)), maxLines = 1)
        Row(Modifier.padding(top = 8.dp)) {
            lamps.forEach { lamp ->
                Text(
                    lamp.label,
                    style = lcd(10.5.sp, color = if (lamp.lit) Palette.LcdOn else Palette.LcdOff, glow = lamp.lit, spacing = 0.12.sp),
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(format, style = lcd(10.5.sp, spacing = 0.12.sp), maxLines = 1)
        }
    }
}

/**
 * Soft light behind the deck. Reads [LevelMeter] every frame, so it breathes with
 * the song rather than on a timer.
 */
@Composable
fun Aurora(playing: Boolean, modifier: Modifier = Modifier) {
    var time by remember { mutableFloatStateOf(0f) }
    var bass by remember { mutableFloatStateOf(0f) }
    var mid by remember { mutableFloatStateOf(0f) }
    var treble by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playing) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1e9f
                last = now
                time += dt * if (playing) 0.3f else 0.05f
                bass = LevelMeter.bass
                mid = LevelMeter.mid
                treble = LevelMeter.treble
            }
        }
    }

    Canvas(modifier.fillMaxSize()) {
        drawRect(Palette.Black)
        val blobs = listOf(
            Triple(Palette.Aero, Offset(0.18f, 0.22f), bass),
            Triple(Palette.Pink, Offset(0.84f, 0.30f), mid),
            Triple(Color(0xFF5BCEFA), Offset(0.50f, 0.50f), treble),
            Triple(Palette.Aero, Offset(0.86f, 0.86f), mid),
            Triple(Palette.Lavender, Offset(0.10f, 0.80f), bass),
        )
        blobs.forEachIndexed { i, (color, anchor, level) ->
            val c = Offset(
                size.width * (anchor.x + 0.07f * sin(time + i * 1.7f)),
                size.height * (anchor.y + 0.05f * cos(time * 0.8f + i)),
            )
            val radius = size.maxDimension * (0.30f + level * 0.16f)
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = 0.05f + level * 0.16f), Color.Transparent), center = c, radius = radius),
                radius = radius,
                center = c,
                blendMode = BlendMode.Plus,
            )
        }
    }
}
