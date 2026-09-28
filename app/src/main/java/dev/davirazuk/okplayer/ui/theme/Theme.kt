package dev.davirazuk.okplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    // Window frame and desktop.
    val SkyTop = Color(0xFF2F8FCE)
    val SkyMid = Color(0xFF135A91)
    val SkyBottom = Color(0xFF0A2F52)
    val Frame = Color(0xA8AACDEB)
    val FrameEdge = Color(0xBFFFFFFF)
    val FrameLine = Color(0x9E12263A)

    // Library pane, after Explorer and WMP 12.
    val Paper = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1E1E1E)
    val Sub = Color(0xFF6D6D6D)
    val Heading = Color(0xFF1E395B)
    val Rule = Color(0xFFE2E7EE)
    val Hover = Color(0xFFE5F3FB)
    val HoverEdge = Color(0xFF70C0E7)
    val Selected = Color(0xFFCCE8FF)
    val SelectedEdge = Color(0xFF99D1FF)
    val CmdTop = Color(0xFFFAFCFE)
    val CmdBottom = Color(0xFFE3ECF5)
    val CmdEdge = Color(0xFFA0AFC3)

    // Now playing and the control bar.
    val Black = Color(0xFF050607)
    val BarText = Color(0xFFD8DDE3)
    val BarDim = Color(0xFFAEB6BF)
    val Glow = Color(0xFF7FD0FF)
    val Aero = Color(0xFF3AA7EA)
    val LcdBg = Color(0xFF07181C)
    val LcdOn = Color(0xFF8FF0FF)
    val LcdOff = Color(0x218FF0FF)
    val Star = Color(0xFFF5B301)
    val StarOff = Color(0xFF3A414B)
    val Pink = Color(0xFFF5A9B8)
    val Lavender = Color(0xFFC9B8FF)
}

private val scheme = lightColorScheme(
    primary = Palette.Aero,
    onPrimary = Color.White,
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.Paper,
    onSurface = Palette.Ink,
    outline = Palette.CmdEdge,
)

/** Windows 7 sets almost everything at one size; hierarchy comes from colour, not weight. */
private val type = Typography(
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Normal, color = Palette.Heading),
    titleMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 13.5.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelMedium = TextStyle(fontSize = 12.5.sp),
    labelSmall = TextStyle(fontSize = 11.sp),
)

@Composable
fun OkPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
