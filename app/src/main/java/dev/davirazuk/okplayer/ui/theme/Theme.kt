package dev.davirazuk.okplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    val Ink = Color(0xFF0B1420)
    val Surface = Color(0xFF111D2B)
    val Raised = Color(0xFF172638)
    val Line = Color(0xFF22344A)
    val Text = Color(0xFFE6EEF7)
    val Muted = Color(0xFF8497AD)
    val Faint = Color(0xFF52667D)
    val Aero = Color(0xFF5BB8F0)
    val AeroDeep = Color(0xFF0E4F8C)
    val Pink = Color(0xFFF5A9B8)
    val Lavender = Color(0xFFC9B8FF)
    val Good = Color(0xFF6FD08C)
    val Warn = Color(0xFFE8B45A)
}

private val scheme = darkColorScheme(
    primary = Palette.Aero,
    onPrimary = Palette.Ink,
    secondary = Palette.Pink,
    background = Palette.Ink,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Raised,
    onSurfaceVariant = Palette.Muted,
    outline = Palette.Line,
)

private val type = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Light, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Light),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp),
)

@Composable
fun OkPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
