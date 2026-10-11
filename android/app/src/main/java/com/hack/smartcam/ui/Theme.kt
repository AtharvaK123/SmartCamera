package com.hack.smartcam.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hack.smartcam.R

/*
 * Brand colours (from the brief):
 *   #0B1B32 midnight · #0D1E4C navy · #26415E slate · #83A6CE sky · #C48CB3 mauve · #E5C9D7 blush
 * Glass surfaces are translucent white over a soft gradient of those colours.
 */
object Brand {
    val midnight = Color(0xFF0B1B32)
    val navy = Color(0xFF0D1E4C)
    val slate = Color(0xFF26415E)
    val sky = Color(0xFF83A6CE)
    val mauve = Color(0xFFC48CB3)
    val blush = Color(0xFFE5C9D7)
}

@Immutable
data class Palette(
    val paper: Color, val sheet: Color, val card: Color, val card2: Color,
    val ink: Color, val ink2: Color, val ink3: Color, val line: Color, val edge: Color, val shine: Color,
    val accent: Color, val accentInk: Color, val accentSoft: Color, val pop: Color, val popInk: Color,
    val good: Color, val goodSoft: Color, val warn: Color, val warnSoft: Color, val bad: Color, val badSoft: Color,
    val shadow: Color, val bgTop: Color, val bgBottom: Color, val blobs: List<Color>, val dark: Boolean,
)

val LightPalette = Palette(
    paper = Color(0xFFF6EEF3), sheet = Color(0xFFF3EAF1),
    card = Color.White.copy(alpha = 0.52f), card2 = Brand.midnight.copy(alpha = 0.07f),
    ink = Brand.midnight, ink2 = Brand.slate, ink3 = Color(0xFF63789A),
    line = Brand.midnight.copy(alpha = 0.12f), edge = Color.White.copy(alpha = 0.85f), shine = Color.White.copy(alpha = 0.55f),
    accent = Brand.navy, accentInk = Color.White, accentSoft = Brand.sky.copy(alpha = 0.30f),
    pop = Brand.mauve, popInk = Brand.midnight,
    good = Color(0xFF247F67), goodSoft = Color(0xFF247F67).copy(alpha = 0.15f),
    warn = Color(0xFFA86A12), warnSoft = Color(0xFFD99A32).copy(alpha = 0.20f),
    bad = Color(0xFFB8325F), badSoft = Color(0xFFB8325F).copy(alpha = 0.14f),
    shadow = Brand.midnight.copy(alpha = 0.16f),
    bgTop = Brand.blush, bgBottom = Color(0xFFBFD0E6),
    blobs = listOf(Brand.sky.copy(alpha = 0.75f), Brand.mauve.copy(alpha = 0.60f), Color.White.copy(alpha = 0.65f)),
    dark = false,
)

val DarkPalette = Palette(
    paper = Color(0xFF0E203B), sheet = Color(0xFF10223F),
    card = Color.White.copy(alpha = 0.075f), card2 = Color.White.copy(alpha = 0.08f),
    ink = Color(0xFFF5ECF2), ink2 = Color(0xFFC6D3E6), ink3 = Color(0xFF8C9FBC),
    line = Color.White.copy(alpha = 0.12f), edge = Color.White.copy(alpha = 0.30f), shine = Color.White.copy(alpha = 0.14f),
    accent = Brand.sky, accentInk = Brand.midnight, accentSoft = Brand.sky.copy(alpha = 0.18f),
    pop = Brand.mauve, popInk = Brand.midnight,
    good = Color(0xFF72D3B3), goodSoft = Color(0xFF72D3B3).copy(alpha = 0.16f),
    warn = Color(0xFFF1C46E), warnSoft = Color(0xFFF1C46E).copy(alpha = 0.16f),
    bad = Color(0xFFF28DB1), badSoft = Color(0xFFF28DB1).copy(alpha = 0.16f),
    shadow = Color.Black.copy(alpha = 0.40f),
    bgTop = Brand.navy, bgBottom = Brand.midnight,
    blobs = listOf(Brand.slate.copy(alpha = 0.95f), Brand.mauve.copy(alpha = 0.38f), Brand.sky.copy(alpha = 0.30f)),
    dark = true,
)

@OptIn(ExperimentalTextApi::class)
val Display = FontFamily(
    Font(R.font.bricolage, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.bricolage, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.bricolage, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)
@OptIn(ExperimentalTextApi::class)
val Body = FontFamily(
    Font(R.font.instrument_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.instrument_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.instrument_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)
val Mono = FontFamily(Font(R.font.dm_mono, FontWeight.Normal), Font(R.font.dm_mono_medium, FontWeight.Medium))

object T {
    val big = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, letterSpacing = (-0.8).sp)
    val h1 = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, letterSpacing = (-0.4).sp)
    val h2 = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    val h3 = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    val body = TextStyle(fontFamily = Body, fontSize = 15.sp, lineHeight = 21.sp)
    val bodyStrong = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    val small = TextStyle(fontFamily = Body, fontSize = 13.sp, lineHeight = 18.sp)
    val eyebrow = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.8.sp)
    val mono = TextStyle(fontFamily = Mono, fontSize = 13.sp)
}

val LocalPalette = staticCompositionLocalOf { LightPalette }
val P: Palette @Composable get() = LocalPalette.current

@Composable
fun SmartCamTheme(theme: String, content: @Composable () -> Unit) {
    val dark = when (theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) darkColorScheme(
        primary = p.accent, onPrimary = p.accentInk, background = p.paper, surface = p.sheet, onSurface = p.ink, onBackground = p.ink,
        surfaceContainerLow = p.sheet, surfaceContainer = p.sheet, surfaceContainerHigh = p.sheet, outline = p.line,
    ) else lightColorScheme(
        primary = p.accent, onPrimary = p.accentInk, background = p.paper, surface = p.sheet, onSurface = p.ink, onBackground = p.ink,
        surfaceContainerLow = p.sheet, surfaceContainer = p.sheet, surfaceContainerHigh = p.sheet, outline = p.line,
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
