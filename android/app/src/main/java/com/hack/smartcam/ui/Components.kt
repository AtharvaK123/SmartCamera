package com.hack.smartcam.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

enum class Level { Good, Warn, Bad }

@Composable fun levelColor(l: Level) = when (l) { Level.Good -> P.good; Level.Warn -> P.warn; Level.Bad -> P.bad }
@Composable fun levelSoft(l: Level) = when (l) { Level.Good -> P.goodSoft; Level.Warn -> P.warnSoft; Level.Bad -> P.badSoft }

/** Liquid-glass card used across SmartCam. `shadow = false` for cards nested inside other glass. */
@Composable
fun ScCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 14.dp,
    shadow: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .glass(GlassShape, elevated = shadow)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun Eyebrow(text: String, icon: ImageVector? = null, color: Color = P.ink2) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (icon != null) Icon(icon, null, Modifier.size(16.dp), tint = color)
        Text(text.uppercase(), style = T.eyebrow, color = color)
    }
}

@Composable
fun BigNumber(value: String, unit: String? = null, size: Int = 32, color: Color = P.ink) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = T.big.copy(fontSize = size.sp), color = color)
        if (unit != null) Text(" $unit", style = T.small, color = P.ink2, modifier = Modifier.padding(bottom = (size / 7).dp))
    }
}

@Composable
fun Chip(text: String, level: Level? = null, icon: ImageVector? = null, dashed: Boolean = false) {
    val bg = level?.let { levelSoft(it) } ?: if (dashed) Color.Transparent else P.card2
    val fg = level?.let { levelColor(it) } ?: P.ink2
    val ic = icon ?: when (level) { Level.Good -> Ic.check; null -> null; else -> Ic.alert }
    val dash = P.ink3
    Row(
        Modifier
            .clip(CircleShape)
            .background(bg)
            .then(if (dashed) Modifier.drawBehind {
                drawRoundRect(dash, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))), cornerRadius = CornerRadius(size.height / 2))
            } else Modifier)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (ic != null) Icon(ic, null, Modifier.size(14.dp), tint = fg)
        Text(text, style = T.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 12.sp), color = fg, maxLines = 1)
    }
}

enum class Btn { Primary, Ghost, Pop, Danger }

@Composable
fun ScButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: Btn = Btn.Primary,
    small: Boolean = false,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null, // e.g. a brand logo instead of an icon
) {
    val p = P
    val (bg, fg) = when (style) {
        Btn.Primary -> p.accent to p.accentInk
        Btn.Ghost -> p.card to p.ink
        Btn.Pop -> p.pop to p.popInk
        Btn.Danger -> p.card to p.bad
    }
    Row(
        modifier
            .glass(CircleShape, fill = if (enabled) bg else bg.copy(alpha = bg.alpha * 0.45f), elevated = enabled && style != Btn.Ghost)
            .clickable(enabled = enabled, role = Role.Button) { Haptics.click(); onClick() }
            .height(if (small) 38.dp else 46.dp)
            .padding(horizontal = if (small) 14.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = fg)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = T.bodyStrong.copy(fontSize = if (small) 14.sp else 15.sp), color = fg, maxLines = 1)
    }
}

@Composable
fun IconBtn(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = P.ink, bg: Color = Color.Transparent, size: Dp = 42.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(bg).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, Modifier.size(22.dp), tint = tint) }
}

@Composable
fun GeminiMark(size: Dp = 30.dp) {
    Box(Modifier.size(size).glass(CircleShape, elevated = false), contentAlignment = Alignment.Center) {
        GeminiLogo(size * 0.62f)
    }
}

@Composable
fun Meter(fraction: Float, level: Level? = null, modifier: Modifier = Modifier) {
    val c = level?.let { levelColor(it) } ?: P.accent
    Box(modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(P.card2)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(c))
    }
}

@Composable
fun Notice(text: String, level: Level? = null, icon: ImageVector = Ic.info, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null) {
    val bg = when (level) { Level.Warn -> P.warnSoft; Level.Bad -> P.badSoft; else -> P.card2 }
    val ic = level?.let { levelColor(it) } ?: P.ink2
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg).border(1.dp, P.line, RoundedCornerShape(14.dp)).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = ic)
        Text(text, style = T.small, color = if (level == null) P.ink2 else P.ink, modifier = Modifier.weight(1f))
        action?.invoke(this)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = T.h2, color = P.ink, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun ScField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    number: Boolean = false,
    singleLine: Boolean = true,
    password: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (label != null) Text(label, style = T.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = P.ink2)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = T.body.copy(color = P.ink),
            cursorBrush = SolidColor(P.accent),
            keyboardOptions = KeyboardOptions(keyboardType = when { password -> KeyboardType.Password; number -> KeyboardType.Decimal; else -> KeyboardType.Text }),
            visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(P.card2)
                        .border(1.dp, P.line, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    if (value.isEmpty()) Text(placeholder, style = T.body, color = P.ink3)
                    inner()
                }
            },
        )
    }
}

@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = P
    val x by androidx.compose.animation.core.animateDpAsState(if (checked) 20.dp else 0.dp, androidx.compose.animation.core.spring(dampingRatio = 0.6f), label = "toggle")
    Box(
        Modifier.size(width = 48.dp, height = 28.dp).clip(CircleShape).background(if (checked) p.accent else p.card2)
            .border(1.dp, p.line, CircleShape).clickable(role = Role.Switch) { Haptics.tick(); onChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.padding(3.dp).offset(x = x).size(22.dp).glass(CircleShape, fill = Color.White.copy(alpha = 0.92f)))
    }
}

@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onDark: Boolean = false, onSelect: (String) -> Unit) {
    val light = Color.White
    Row(Modifier.clip(CircleShape).background(if (onDark) light.copy(alpha = 0.1f) else P.card2).border(1.dp, if (onDark) light.copy(alpha = 0.2f) else P.line, CircleShape).padding(3.dp)) {
        options.forEach { (key, label) ->
            val on = key == selected
            Text(
                label,
                style = T.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                color = when { onDark && on -> Brand.midnight; onDark -> light.copy(alpha = 0.8f); on -> P.ink; else -> P.ink2 },
                modifier = Modifier.clip(CircleShape)
                    .then(if (on) Modifier.glass(CircleShape, fill = if (onDark) Color.White.copy(alpha = 0.92f) else if (P.dark) P.accentSoft else Color.White.copy(alpha = 0.8f), elevated = false) else Modifier)
                    .clickable { if (!on) Haptics.tick(); onSelect(key) }.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

/** Single-series bar chart. Tap a bar to see its value. */
@Composable
fun BarChart(values: List<Float>, labels: List<String>, format: (Float) -> String, ref: Float? = null, refLabel: String? = null, height: Dp = 150.dp) {
    var selected by remember(values) { mutableIntStateOf(values.lastIndex) }
    val top = (listOfNotNull(values.maxOrNull(), ref).maxOrNull() ?: 1f).coerceAtLeast(1f) * 1.1f
    val p = P
    Column {
        Text(
            if (selected in values.indices) "${labels[selected]} · ${format(values[selected])}" else " ",
            style = T.mono, color = P.ink2, modifier = Modifier.padding(bottom = 6.dp),
        )
        Box(Modifier.fillMaxWidth().height(height)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                values.forEachIndexed { i, v ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clickable { selected = i },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight((v / top).coerceIn(0.02f, 1f))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(if (i == selected) p.accent else p.accent.copy(alpha = 0.45f)),
                        )
                    }
                }
            }
            if (ref != null) {
                Box(
                    Modifier.fillMaxSize().drawBehind {
                        val y = size.height * (1 - ref / top)
                        drawLine(p.ink3, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                    },
                ) {
                    if (refLabel != null) Text(
                        refLabel, style = T.mono.copy(fontSize = 11.sp), color = P.ink2,
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = (height * (1 - ref / top) - 18.dp).coerceAtLeast(0.dp))
                            .background(P.sheet.copy(alpha = 0.8f), CircleShape).padding(horizontal = 5.dp),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEach { Text(it.take(1), style = T.mono.copy(fontSize = 11.sp), color = P.ink2, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
    }
}

@Composable
fun HBars(rows: List<Pair<String, Int>>, format: (Int) -> String) {
    val max = (rows.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { (name, v) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(name, style = T.small, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(100.dp))
                Box(Modifier.weight(1f).height(12.dp)) {
                    Box(Modifier.fillMaxWidth(v.toFloat() / max).fillMaxHeight().clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)).background(P.accent))
                }
                Text(format(v), style = T.mono.copy(fontSize = 12.sp), color = P.ink2, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 52.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = P.sheet,
        contentColor = P.ink,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = T.h1.copy(fontSize = 22.sp), color = P.ink, modifier = Modifier.weight(1f))
            IconBtn(Ic.close, "Close", onDismiss)
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/* ---- toasts ---- */

object Toasts {
    data class Msg(val text: String, val bad: Boolean, val id: Long = System.nanoTime())
    val current = MutableStateFlow<Msg?>(null)
    fun show(text: String, bad: Boolean = false) { current.value = Msg(text, bad) }
}

@Composable
fun ToastHost() {
    val msg by Toasts.current.collectAsState()
    LaunchedEffect(msg?.id) {
        if (msg != null) { delay(3500); Toasts.current.value = null }
    }
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(msg != null, enter = fadeIn() + slideInVertically(), exit = fadeOut() + slideOutVertically()) {
            val m = msg ?: return@AnimatedVisibility
            Text(
                m.text, style = T.bodyStrong.copy(fontSize = 14.sp), color = if (m.bad) Color.White else P.paper,
                modifier = Modifier.widthIn(max = 420.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (m.bad) P.bad else P.ink).padding(horizontal = 16.dp, vertical = 11.dp),
            )
        }
    }
}

val Zero = PaddingValues(0.dp)
fun Modifier.borderLine(color: Color) = this.border(BorderStroke(1.dp, color), RoundedCornerShape(0.dp))
