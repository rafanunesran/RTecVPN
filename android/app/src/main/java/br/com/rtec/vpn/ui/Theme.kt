package br.com.rtec.vpn.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import br.com.rtec.vpn.R
import br.com.rtec.vpn.ThemeMode

// Tokens de docs/design/app-ui.md
val Cyan = Color(0xFF22D3EE)
val Violet = Color(0xFF8B5CF6)
val Success = Color(0xFF22C55E)
val Warning = Color(0xFFF59E0B)
val ErrorRed = Color(0xFFEF4444)

val BrandGradient = Brush.linearGradient(listOf(Cyan, Violet), start = Offset.Zero, end = Offset.Infinite)

@Immutable
data class RTecColors(
    val background: Color,
    val surface: Color,
    val surface2: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val idle: Color,
)

private val DarkTokens = RTecColors(
    background = Color(0xFF0B1020),
    surface = Color(0xFF121933),
    surface2 = Color(0xFF1A2242),
    border = Color(0x14FFFFFF),
    text = Color(0xFFE6E9F5),
    muted = Color(0xFF8A93B2),
    idle = Color(0xFF4B5578),
)

private val LightTokens = RTecColors(
    background = Color(0xFFF5F7FC),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFEEF1F8),
    border = Color(0x140B1020),
    text = Color(0xFF0B1020),
    muted = Color(0xFF5B6584),
    idle = Color(0xFFA3ABC4),
)

val LocalRTec = staticCompositionLocalOf { DarkTokens }

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)
val Mono = FontFamily(Font(R.font.jetbrains_mono))

object Type {
    val display = TextStyle(fontFamily = Inter, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
    val headline = TextStyle(fontFamily = Inter, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
    val title = TextStyle(fontFamily = Inter, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
    val bodySmall = TextStyle(fontFamily = Inter, fontSize = 13.5.sp, lineHeight = 19.sp)
    val caption = TextStyle(fontFamily = Inter, fontSize = 12.sp)
    val overline = TextStyle(fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em)
    val mono = TextStyle(fontFamily = Mono, fontSize = 12.5.sp)
}

@Composable
fun RTecTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val t = if (dark) DarkTokens else LightTokens
    val scheme = if (dark) {
        darkColorScheme(
            primary = Cyan, secondary = Violet, background = t.background, surface = t.surface,
            surfaceVariant = t.surface2, onBackground = t.text, onSurface = t.text,
            onSurfaceVariant = t.muted, outline = t.border, error = ErrorRed, onPrimary = t.background,
            surfaceContainer = t.surface, surfaceContainerHigh = t.surface2,
        )
    } else {
        lightColorScheme(
            primary = Cyan, secondary = Violet, background = t.background, surface = t.surface,
            surfaceVariant = t.surface2, onBackground = t.text, onSurface = t.text,
            onSurfaceVariant = t.muted, outline = t.border, error = ErrorRed, onPrimary = Color(0xFF0B1020),
            surfaceContainer = t.surface, surfaceContainerHigh = t.surface2,
        )
    }
    val typography = Typography(
        bodyLarge = Type.body, bodyMedium = Type.bodySmall, titleMedium = Type.title,
        labelLarge = Type.body.copy(fontWeight = FontWeight.SemiBold), headlineSmall = Type.headline,
    )
    CompositionLocalProvider(LocalRTec provides t) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
