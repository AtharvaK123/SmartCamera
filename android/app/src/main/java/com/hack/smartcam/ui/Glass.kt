package com.hack.smartcam.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

val GlassShape = RoundedCornerShape(22.dp)

/**
 * Liquid-glass surface: translucent fill, a sheen across the top, a bright rim that fades
 * around the edge, an inner glow and a soft shadow underneath.
 */
@Composable
fun Modifier.glass(shape: Shape = GlassShape, fill: Color = P.card, elevated: Boolean = true): Modifier {
    val p = P
    val rim = p.edge
    val solidDark = fill.alpha > 0.8f && fill.luminance() < 0.3f
    return this
        .then(if (elevated) Modifier.dropShadow(shape, Shadow(radius = 22.dp, color = p.shadow, offset = DpOffset(0.dp, 8.dp))) else Modifier)
        .clip(shape)
        .background(fill)
        // Sheen across the top; softer on dark, solid fills (buttons) so they don't look washed out.
        .background(Brush.verticalGradient(0f to p.shine.copy(alpha = p.shine.alpha * if (solidDark) 0.35f else 1f), 0.45f to Color.Transparent))
        .innerShadow(shape, Shadow(radius = 12.dp, color = Color.White.copy(alpha = if (p.dark || solidDark) 0.12f else 0.50f), offset = DpOffset(0.dp, 1.dp)))
        .border(
            1.2.dp,
            Brush.linearGradient(listOf(rim, rim.copy(alpha = rim.alpha * 0.2f), rim.copy(alpha = rim.alpha * 0.15f), rim.copy(alpha = rim.alpha * 0.7f))),
            shape,
        )
}

/** The soft colour field everything floats on. */
@Composable
fun Backdrop(modifier: Modifier = Modifier) {
    val p = P
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(p.bgTop, p.bgBottom)))
        val w = size.width; val h = size.height
        blob(p.blobs[0], Offset(w * 0.12f, h * 0.16f), w * 0.85f)
        blob(p.blobs[1], Offset(w * 0.95f, h * 0.48f), w * 0.75f)
        blob(p.blobs[2], Offset(w * 0.30f, h * 0.92f), w * 0.85f)
    }
}

private fun DrawScope.blob(c: Color, center: Offset, r: Float) =
    drawCircle(Brush.radialGradient(listOf(c, c.copy(alpha = 0f)), center, r), r, center)

/**
 * Text that writes itself out one character at a time; each new letter fades in.
 * Unrevealed letters are laid out but transparent, so the text never jumps around.
 */
@Composable
fun TypeText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, msPerChar: Int = 38) {
    val progress = remember(text) { Animatable(0f) }
    LaunchedEffect(text) {
        progress.animateTo(text.length.toFloat(), tween(text.length * msPerChar, easing = LinearEasing))
    }
    val shown = progress.value
    val full = shown.toInt()
    val annotated = buildAnnotatedString {
        append(text.take(full))
        if (full < text.length) {
            withStyle(SpanStyle(color = color.copy(alpha = color.alpha * (shown - full)))) { append(text[full]) }
            withStyle(SpanStyle(color = Color.Transparent)) { append(text.substring(full + 1)) }
        }
    }
    Text(annotated, style = style, color = color, modifier = modifier)
}
