package com.hack.smartcam.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.hack.smartcam.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

/* Brand marks for the services SmartCam uses, drawn from their public logo shapes. */

private fun path(d: String): Path = PathParser().parsePathString(d).toPath()

private val GEMINI = "M12 0C12 6.627 17.373 12 24 12C17.373 12 12 17.373 12 24C12 17.373 6.627 12 0 12C6.627 12 12 6.627 12 0Z"
private val GEMINI_COLORS = listOf(Color(0xFF4285F4), Color(0xFF9B72CB), Color(0xFFD96570))

/** The Gemini sparkle. */
@Composable
fun GeminiLogo(size: Dp = 22.dp, modifier: Modifier = Modifier) {
    val p = remember { path(GEMINI) }
    Canvas(modifier.size(size)) {
        val s = min(this.size.width, this.size.height) / 24f
        scale(s, s, pivot = Offset.Zero) {
            drawPath(p, Brush.linearGradient(GEMINI_COLORS, start = Offset(2f, 20f), end = Offset(22f, 4f)))
        }
    }
}

private val SOLANA = listOf(
    "M64.6 237.9c2.4-2.4 5.7-3.8 9.2-3.8h317.4c5.8 0 8.7 7 4.6 11.1l-62.7 62.7c-2.4 2.4-5.7 3.8-9.2 3.8H6.5c-5.8 0-8.7-7-4.6-11.1l62.7-62.7z",
    "M64.6 3.8C67.1 1.4 70.4 0 73.8 0h317.4c5.8 0 8.7 7 4.6 11.1l-62.7 62.7c-2.4 2.4-5.7 3.8-9.2 3.8H6.5c-5.8 0-8.7-7-4.6-11.1L64.6 3.8z",
    "M333.1 120.1c-2.4-2.4-5.7-3.8-9.2-3.8H6.5c-5.8 0-8.7 7-4.6 11.1l62.7 62.7c2.4 2.4 5.7 3.8 9.2 3.8h317.4c5.8 0 8.7-7 4.6-11.1l-62.7-62.7z",
)

/** The Solana mark: three slanted bars with the purple-to-green gradient. */
@Composable
fun SolanaLogo(size: Dp = 20.dp, modifier: Modifier = Modifier) {
    val paths = remember { SOLANA.map(::path) }
    Canvas(modifier.size(size)) {
        val s = min(this.size.width / 398f, this.size.height / 312f)
        val dy = (this.size.height - 312f * s) / 2
        translate(top = dy) {
            scale(s, s, pivot = Offset.Zero) {
                val brush = Brush.linearGradient(listOf(Color(0xFF00FFA3), Color(0xFFDC1FFF)), start = Offset(0f, 312f), end = Offset(398f, 0f))
                paths.forEach { drawPath(it, brush) }
            }
        }
    }
}

/** The ElevenLabs "II" mark. */
@Composable
fun ElevenLabsLogo(size: Dp = 18.dp, color: Color = P.ink, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val bar = w * 0.17f
        drawRect(color, Offset(w * 0.26f, w * 0.12f), androidx.compose.ui.geometry.Size(bar, w * 0.76f))
        drawRect(color, Offset(w * 0.57f, w * 0.12f), androidx.compose.ui.geometry.Size(bar, w * 0.76f))
    }
}

/** SmartCam's own mood-music mark: a note on a sky-to-mauve disc. */
@Composable
fun MusicLogo(size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        drawCircle(Brush.linearGradient(listOf(Brand.sky, Brand.mauve), start = Offset.Zero, end = Offset(w, w)), w / 2)
        val s = w / 24f
        scale(s, s, pivot = Offset.Zero) {
            drawPath(path("M10 16.6V7.8l7-1.6v8.4"), Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(1.7f))
            drawCircle(Color.White, 1.9f, Offset(8.4f, 16.7f))
            drawCircle(Color.White, 1.9f, Offset(15.4f, 15.1f))
        }
    }
}

/** "Powered by Presage" credit line. */
@Composable
fun PoweredByPresage(color: Color = P.ink2, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.material3.Icon(Ic.wave, null, Modifier.size(14.dp), tint = color)
        Text("Powered by Presage", style = T.eyebrow.copy(fontSize = 11.sp), color = color)
    }
}

/** The SmartCam app icon (a heart with a heartbeat line), as it looks on the home screen. */
@Composable
fun AppLogo(size: Dp, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier.size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(Brush.linearGradient(listOf(Brand.slate, Brand.navy, Brand.midnight))),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(size * 108f / 62f))
    }
}
