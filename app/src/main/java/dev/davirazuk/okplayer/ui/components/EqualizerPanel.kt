package dev.davirazuk.okplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.davirazuk.okplayer.audio.EqState
import dev.davirazuk.okplayer.ui.theme.Palette
import kotlin.math.roundToInt

/** WMP's "Enhancements > Graphic equalizer": a black glass panel of vertical sliders. */
@Composable
fun EqualizerPanel(
    state: EqState,
    presets: List<String>,
    onEnabled: (Boolean) -> Unit,
    onBand: (index: Int, levelMb: Int) -> Unit,
    onPreset: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier
            .widthIn(max = 380.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF23272C), Color(0xFF101215) , Color(0xFF0B0C0E))))
            .border(1.dp, Color(0xFF3A4048), shape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Graphic equalizer", fontSize = 14.sp, color = Color.White, modifier = Modifier.weight(1f))
            GlassToggle(if (state.enabled) "Turn off" else "Turn on", on = state.enabled, enabled = state.available) { onEnabled(!state.enabled) }
        }
        if (!state.available) {
            Text(
                "This phone doesn't offer an equalizer to apps.",
                fontSize = 12.sp, color = Palette.BarDim, modifier = Modifier.padding(top = 10.dp),
            )
            return@Column
        }

        Box(Modifier.padding(top = 8.dp)) {
            var open by remember { mutableStateOf(false) }
            Text(
                "${state.preset} ▾",
                fontSize = 12.5.sp,
                color = Palette.Glow,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .clickable(role = Role.DropdownList) { open = true }
                    .padding(vertical = 4.dp, horizontal = 2.dp),
            )
            if (open) Win7Menu(presets.map { p -> MenuItem(p, checked = p == state.preset) { onPreset(p) } }, onDismiss = { open = false })
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .alpha(if (state.enabled) 1f else 0.45f),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            state.bands.forEachIndexed { i, band ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val db = band.levelMb / 100f
                    Text(
                        (if (db > 0) "+" else "") + "%.0f".format(db),
                        fontSize = 10.5.sp, color = Palette.BarDim, textAlign = TextAlign.Center,
                    )
                    VerticalSlider(
                        value = (band.levelMb - state.minMb).toFloat() / (state.maxMb - state.minMb),
                        onChange = { f -> onBand(i, (state.minMb + f * (state.maxMb - state.minMb)).roundToInt()) },
                        modifier = Modifier.padding(vertical = 6.dp).width(34.dp).height(150.dp),
                    )
                    Text(formatHz(band.centerHz), fontSize = 10.5.sp, color = Color(0xFFCFD6DE))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "The equalizer changes the sound, so output isn't bit-perfect while it's on.",
            fontSize = 11.sp, lineHeight = 14.sp, color = Color(0xFF7F8B98),
        )
    }
}

@Composable
private fun GlassToggle(label: String, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(3.dp)
    Text(
        label,
        fontSize = 12.sp,
        color = if (on) Color.White else Color(0xFFDFE6EC),
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(
                if (on) Brush.verticalGradient(listOf(Color(0xFF4A9BDB), Color(0xFF1F5E97), Color(0xFF184C7C)))
                else Brush.verticalGradient(listOf(Color(0xFF4A4F56), Color(0xFF2B2F34), Color(0xFF1A1D20))),
            )
            .border(1.dp, Color.Black, shape)
            .clickable(enabled = enabled, role = Role.Switch, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
private fun VerticalSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier) {
    val change by rememberUpdatedState(onChange)
    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { o -> change((1f - o.y / size.height).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { c, _ -> change((1f - c.position.y / size.height).coerceIn(0f, 1f)) }
            },
    ) {
        val cx = size.width / 2
        val pad = 8.dp.toPx()
        val usable = size.height - pad * 2
        // Tick marks at every quarter, the middle one brighter for 0 dB.
        for (k in 0..4) {
            val y = pad + usable * k / 4
            drawLine(Color.White.copy(alpha = if (k == 2) 0.35f else 0.12f), Offset(cx - 11.dp.toPx(), y), Offset(cx + 11.dp.toPx(), y), 1f)
        }
        val trackW = 4.dp.toPx()
        drawRoundRect(Color.Black, Offset(cx - trackW / 2, pad), Size(trackW, usable), CornerRadius(trackW / 2))
        val y = pad + usable * (1f - value.coerceIn(0f, 1f))
        val mid = pad + usable / 2
        drawRoundRect(
            Palette.Aero.copy(alpha = 0.8f),
            Offset(cx - trackW / 2, minOf(y, mid)),
            Size(trackW, kotlin.math.abs(mid - y)),
            CornerRadius(trackW / 2),
        )
        val thumb = Size(22.dp.toPx(), 11.dp.toPx())
        val tl = Offset(cx - thumb.width / 2, y - thumb.height / 2)
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFFE6F4FF), Color(0xFF8CCBF5), Color(0xFF3B92D4)), startY = tl.y, endY = tl.y + thumb.height),
            tl, thumb, CornerRadius(3.dp.toPx()),
        )
        drawRoundRect(Color(0xFF0C3F68), tl, thumb, CornerRadius(3.dp.toPx()), style = Stroke(1f))
    }
}

private fun formatHz(hz: Int): String = when {
    hz >= 1000 -> {
        val k = hz / 1000f
        if (k >= 10 || k == k.toInt().toFloat()) "${k.roundToInt()}k" else "%.1fk".format(k)
    }
    else -> "$hz"
}
