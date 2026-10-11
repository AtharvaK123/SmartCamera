package com.hack.smartcam.ui

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The same hand-drawn stroke icons as the website, built from SVG path data. */
object Ic {
    private fun c(cx: Float, cy: Float, r: Float) = "M${cx - r},$cy a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0"
    private fun r(x: Float, y: Float, w: Float, h: Float, rx: Float) =
        "M${x + rx},$y h${w - 2 * rx} a$rx,$rx 0 0 1 $rx,$rx v${h - 2 * rx} a$rx,$rx 0 0 1 -$rx,$rx h${-(w - 2 * rx)} a$rx,$rx 0 0 1 -$rx,-$rx v${-(h - 2 * rx)} a$rx,$rx 0 0 1 $rx,-$rx z"

    private fun icon(name: String, vararg d: String, width: Float = 1.8f): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        d.forEach {
            b.addPath(
                pathData = PathParser().parsePathString(it).toNodes(),
                stroke = SolidColor(Color.Black), strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    val home = icon("home", "M3 11.5 12 4l9 7.5", "M5.5 10v9.5h13V10", "M10 19.5v-5h4v5")
    val camera = icon("camera", "M4 8h3l1.6-2.5h6.8L17 8h3v11H4z", c(12f, 13.5f, 3.5f))
    val food = icon("food", "M12 7.5c-1.6-1.2-4.8-1.4-6.2 1.2-1.6 3 .2 8 2.4 9.8 1.3 1 2.4.6 3.8 0 1.4.6 2.5 1 3.8 0 2.2-1.8 4-6.8 2.4-9.8-1.4-2.6-4.6-2.4-6.2-1.2z", "M12 7.5c0-2 1-3.5 3-4")
    val phone = icon("phone", r(7f, 2.5f, 10f, 19f, 2.5f), "M11 18.5h2")
    val bag = icon("bag", "M5 8h14l-1 12.5H6z", "M9 10V6.5a3 3 0 0 1 6 0V10")
    val dots = icon("dots", c(12f, 5f, 0.6f), c(12f, 12f, 0.6f), c(12f, 19f, 0.6f), width = 2.6f)
    val mic = icon("mic", r(9f, 3f, 6f, 11f, 3f), "M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21")
    val send = icon("send", "M4 12 20 4l-6 16-2.5-6.5z", "M11.5 13.5 20 4")
    val speaker = icon("speaker", "M4 9.5h3.5L12 5.5v13l-4.5-4H4z", "M15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11")
    val speakerOff = icon("speakerOff", "M4 9.5h3.5L12 5.5v13l-4.5-4H4z", "m16 9.5 5 5m0-5-5 5")
    val plus = icon("plus", "M12 5v14M5 12h14")
    val barcode = icon("barcode", "M4 6v12M7 6v12M10.5 6v12M13 6v12M16.5 6v12M20 6v12")
    val image = icon("image", r(3.5f, 5f, 17f, 14f, 2f), c(9f, 10f, 1.6f), "m4 17 5-4.5 4 3.5 2.5-2 4.5 3.5")
    val pencil = icon("pencil", "M4 20h4L19 9l-4-4L4 16z", "m13.5 6.5 4 4")
    val close = icon("close", "M6 6l12 12M18 6 6 18")
    val alert = icon("alert", "M12 4 2.8 19.5h18.4z", "M12 10v4.5M12 17v.01")
    val heart = icon("heart", "M12 20s-7.5-4.6-7.5-10A4.3 4.3 0 0 1 12 7.4 4.3 4.3 0 0 1 19.5 10c0 5.4-7.5 10-7.5 10z")
    val moon = icon("moon", "M19 14.5A7.5 7.5 0 0 1 9.5 5a7.5 7.5 0 1 0 9.5 9.5z")
    val alarm = icon("alarm", c(12f, 13f, 7f), "M12 9.5V13l2.5 2M4 5.5 7 3M20 5.5 17 3")
    val screen = icon("screen", r(3f, 4.5f, 18f, 12f, 2f), "M8.5 20h7M12 16.5V20")
    val lock = icon("lock", r(5f, 10.5f, 14f, 10f, 2f), "M8 10.5V8a4 4 0 0 1 8 0v2.5")
    val music = icon("music", "M9 18V6l10-2v12", c(6.5f, 18f, 2.5f), c(16.5f, 16f, 2.5f))
    val trash = icon("trash", "M5 7h14M10 7V4.5h4V7M6.5 7l1 13h9l1-13")
    val check = icon("check", "m5 12.5 4.5 4.5L19 7.5")
    val refresh = icon("refresh", "M19.5 12a7.5 7.5 0 1 1-2.2-5.3M19.5 4.5v4h-4")
    val steps = icon("steps", "M8 3.5c1.7 0 2.5 2 2.5 4.5S9.7 12 8 12 5.5 10.5 5.5 8 6.3 3.5 8 3.5zM6 14.5h4l-.3 2.5a2 2 0 0 1-3.4 0zM16 7.5c1.7 0 2.5 2 2.5 4.5S17.7 16 16 16s-2.5-1.5-2.5-4 .8-4.5 2.5-4.5zM14 18.5h4l-.3 1.5a2 2 0 0 1-3.4 0z")
    val wave = icon("wave", "M3 12h3l2-5 3.5 10 2.5-7 2 4h5")
    val leaf = icon("leaf", "M5 19c0-8 5-13 14-14 0 9-5 14-13 14z", "M5 19 13 11")
    val wallet = icon("wallet", r(3.5f, 6f, 17f, 13f, 2f), "M16 12.5h4.5M3.5 9.5h17")
    val info = icon("info", c(12f, 12f, 8.5f), "M12 11v5M12 8v.01")
    val play = icon("play", "M8 5.5v13l10.5-6.5z")
    val pause = icon("pause", "M8.5 5.5v13M15.5 5.5v13", width = 2.6f)
    val next = icon("next", "M6 5.5v13l9-6.5zM18 5.5v13")
    val prev = icon("prev", "M18 5.5v13l-9-6.5zM6 5.5v13")
    val spark = icon("spark", "M12 3.5c.6 4.3 2.2 5.9 6.5 6.5-4.3.6-5.9 2.2-6.5 6.5-.6-4.3-2.2-5.9-6.5-6.5 4.3-.6 5.9-2.2 6.5-6.5z", "M18.5 16.5c.2 1.4.8 2 2.2 2.2-1.4.2-2 .8-2.2 2.2-.2-1.4-.8-2-2.2-2.2 1.4-.2 2-.8 2.2-2.2z")
    val user = icon("user", c(12f, 8.5f, 3.8f), "M4.5 20c.8-3.6 3.8-5.8 7.5-5.8s6.7 2.2 7.5 5.8")
    val logout = icon("logout", "M14 5H6.5v14H14", "M10.5 12H20m-3.5-3.5L20 12l-3.5 3.5")
    val shield = icon("shield", "M12 3.5 19 6v5.5c0 4.4-3 7.7-7 9-4-1.3-7-4.6-7-9V6z", "m9 12 2.2 2.2L15.5 10")
    val flame = icon("flame", "M12 21c-3.6 0-6-2.4-6-5.8 0-3.9 3.4-5.6 3.9-9.7 2.6 1.6 4.2 4 4.4 6.4.9-.6 1.5-1.6 1.7-2.7 1.3 1.5 2 3.4 2 5.2C18 18.6 15.6 21 12 21z")
    val calendar = icon("calendar", r(4f, 5.5f, 16f, 15f, 2f), "M4 10h16M8.5 3.5v4M15.5 3.5v4")
    val minus = icon("minus", "M5 12h14")
    val gamepad = icon("gamepad", "M7 8.5h10a4 4 0 0 1 3.9 4.8l-.8 3.9a2 2 0 0 1-3.5.9L14.5 16h-5l-2.1 2.1a2 2 0 0 1-3.5-.9l-.8-3.9A4 4 0 0 1 7 8.5z", "M8 11.5v3M6.5 13h3M15.5 12.2v.01M17.5 14v.01")
    val chevronRight = icon("chevronRight", "m9.5 6 6 6-6 6")
    val stop = icon("stop", r(6.5f, 6.5f, 11f, 11f, 2f))
}
