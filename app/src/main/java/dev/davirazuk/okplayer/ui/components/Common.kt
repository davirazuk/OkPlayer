package dev.davirazuk.okplayer.ui.components

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import dev.davirazuk.okplayer.audio.LevelMeter
import dev.davirazuk.okplayer.ui.theme.Palette
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Artwork(uri: Uri?, modifier: Modifier = Modifier) {
    val placeholder: @Composable () -> Unit = {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(Color(0xFF26384F), Color(0xFF16222F)))),
        )
    }
    if (uri == null) {
        Box(modifier) { placeholder() }
    } else {
        SubcomposeAsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
            loading = { placeholder() },
            error = { placeholder() },
        )
    }
}

/** The glossy Windows 7 style round button. */
@Composable
fun OrbButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    primary: Boolean = true,
    enabled: Boolean = true,
) {
    val body = if (primary) {
        listOf(Color(0xFFBFE6FF), Color(0xFF3B95D8), Palette.AeroDeep, Color(0xFF0A3563))
    } else {
        listOf(Color(0xFFE7EEF6), Color(0xFF7D93AB), Color(0xFF34495F), Color(0xFF1D2C3C))
    }
    Box(
        modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(Brush.radialGradient(body, center = Offset.Unspecified))
            .border(1.dp, Color(0xFF0A2A4A), CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color.White),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Top highlight, the part that makes it read as glass.
            drawOval(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), endY = this.size.height * 0.5f),
                topLeft = Offset(this.size.width * 0.14f, this.size.height * 0.04f),
                size = androidx.compose.ui.geometry.Size(this.size.width * 0.72f, this.size.height * 0.48f),
            )
        }
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(size * 0.4f))
    }
}

/**
 * Soft aurora behind the now playing screen. Reads [LevelMeter] every frame,
 * so it moves with the song rather than on a timer.
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
                time += dt * if (playing) 0.35f else 0.06f
                bass = LevelMeter.bass
                mid = LevelMeter.mid
                treble = LevelMeter.treble
            }
        }
    }

    Canvas(modifier.fillMaxSize()) {
        drawRect(Palette.Ink)
        val blobs = listOf(
            Triple(Palette.Aero, Offset(0.2f, 0.18f), bass),
            Triple(Palette.Pink, Offset(0.82f, 0.26f), mid),
            Triple(Palette.Lavender, Offset(0.5f, 0.62f), treble),
            Triple(Palette.Aero, Offset(0.86f, 0.84f), mid),
            Triple(Palette.Pink, Offset(0.12f, 0.78f), bass),
        )
        blobs.forEachIndexed { i, (color, anchor, level) ->
            val c = Offset(
                size.width * (anchor.x + 0.07f * sin(time + i * 1.7f)),
                size.height * (anchor.y + 0.05f * cos(time * 0.8f + i)),
            )
            val radius = size.maxDimension * (0.32f + level * 0.18f)
            val alpha = 0.10f + level * 0.22f
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = c, radius = radius),
                radius = radius,
                center = c,
                blendMode = BlendMode.Plus,
            )
        }
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Palette.Ink.copy(alpha = 0.85f)), startY = size.height * 0.55f))
    }
}

fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
