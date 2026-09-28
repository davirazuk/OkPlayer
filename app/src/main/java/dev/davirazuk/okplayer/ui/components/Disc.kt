package dev.davirazuk.okplayer.ui.components

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import dev.davirazuk.okplayer.ui.theme.Palette
import kotlinx.coroutines.isActive

private const val SECONDS_PER_TURN = 3.6f

/**
 * A CD that spins while music plays and coasts to a stop on pause.
 * The rainbow sheen stays still while the disc turns under it, like light on a real disc.
 */
@Composable
fun Disc(artUri: Uri?, playing: Boolean, modifier: Modifier = Modifier, name: String? = null) {
    val angle = remember { Animatable(0f) }

    LaunchedEffect(playing) {
        if (playing) {
            while (isActive) {
                angle.snapTo(angle.value % 360f)
                angle.animateTo(
                    angle.value + 360f,
                    tween(durationMillis = (SECONDS_PER_TURN * 1000).toInt(), easing = LinearEasing),
                )
            }
        } else {
            angle.animateTo(angle.value + 40f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
        }
    }

    Box(modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = angle.value }) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                drawCircle(
                    Brush.radialGradient(
                        0.00f to Color(0xFFDFE6EF),
                        0.30f to Color(0xFFAAB6C6),
                        0.31f to Color(0xFFE9EEF5),
                        0.72f to Color(0xFFC8D1DC),
                        1.00f to Color(0xFFB2BDCB),
                        center = center,
                        radius = r,
                    ),
                    radius = r,
                )
                // Grooves.
                var g = r * 0.33f
                while (g < r * 0.98f) {
                    drawCircle(Color.Black.copy(alpha = 0.035f), radius = g, style = Stroke(width = 1f))
                    g += 3.2f
                }
                // A faint mark so the rotation is visible even without artwork.
                drawLine(
                    Color.White.copy(alpha = 0.5f),
                    start = center.copy(y = center.y - r * 0.97f),
                    end = center.copy(y = center.y - r * 0.55f),
                    strokeWidth = 1.5f,
                )
            }
            Artwork(
                uri = artUri,
                name = name,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.5f)
                    .aspectRatio(1f)
                    .clip(CircleShape),
            )
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            val r = size.minDimension / 2f
            val ring = r * 0.72f
            drawCircle(
                Brush.sweepGradient(
                    0.00f to Color.Transparent,
                    0.06f to Color.Transparent,
                    0.10f to Palette.Pink.copy(alpha = 0.45f),
                    0.14f to Color(0xFFFFF0AA).copy(alpha = 0.35f),
                    0.18f to Color(0xFFA0FFC8).copy(alpha = 0.28f),
                    0.23f to Palette.Aero.copy(alpha = 0.45f),
                    0.31f to Color.Transparent,
                    0.56f to Color.Transparent,
                    0.60f to Palette.Pink.copy(alpha = 0.35f),
                    0.65f to Color(0xFFFFF0AA).copy(alpha = 0.28f),
                    0.71f to Palette.Aero.copy(alpha = 0.4f),
                    0.79f to Color.Transparent,
                    1.00f to Color.Transparent,
                    center = center,
                ),
                radius = r - ring / 2f,
                style = Stroke(width = ring),
                blendMode = BlendMode.Screen,
            )
            drawCircle(Color.White.copy(alpha = 0.6f), radius = r * 0.52f, style = Stroke(width = 2.5f))
            // Metal hub with the spindle hole.
            drawCircle(
                Brush.radialGradient(
                    0.00f to Palette.Black,
                    0.34f to Palette.Black,
                    0.36f to Color(0xFF8C96A3),
                    0.52f to Color(0xFFDFE5EC),
                    0.70f to Color(0xFF7D8793),
                    0.72f to Color(0xFF2A2F35),
                    1.00f to Color.Transparent,
                    center = center,
                    radius = r * 0.13f,
                ),
                radius = r * 0.13f,
            )
        }
    }
}
