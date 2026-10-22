package com.rackowl.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Icônes au trait reprises du design (grille 24×24, trait 1,8). */
object HIcons {
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r},$cy a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0z"

    private fun rect(x: Float, y: Float, w: Float, h: Float, rx: Float) =
        "M${x + rx},$y h${w - 2 * rx} a$rx,$rx 0 0 1 $rx,$rx v${h - 2 * rx} a$rx,$rx 0 0 1 ${-rx},$rx " +
            "h${-(w - 2 * rx)} a$rx,$rx 0 0 1 ${-rx},${-rx} v${-(h - 2 * rx)} a$rx,$rx 0 0 1 $rx,${-rx}z"

    private fun stroke(name: String, vararg paths: String, width: Float = 1.8f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    PathParser().parsePathString(d).toNodes(),
                    stroke = SolidColor(Color.Black), strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun fill(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d -> addPath(PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black)) }
        }.build()

    val Search = stroke("search", circle(11f, 11f, 7f), "M20 20l-3.5-3.5")
    val Edit = stroke("edit", "M4 20h4L19 9l-4-4L4 16z", "M13 7l4 4")
    val Plus = stroke("plus", "M12 5v14M5 12h14")
    val Grid = stroke("grid", rect(4f, 4f, 7f, 7f, 1.5f), rect(13f, 4f, 7f, 7f, 1.5f), rect(4f, 13f, 7f, 7f, 1.5f), rect(13f, 13f, 7f, 7f, 1.5f))
    val Bell = stroke("bell", "M6 16V11a6 6 0 1 1 12 0v5l1.5 2h-15z", "M10 21h4")
    val BellOff = stroke("bellOff", "M6 16V11a6 6 0 0 1 9-5.2M18 11v5l1.5 2H8", "M3 3l18 18")
    val Terminal = stroke("terminal", rect(3f, 4f, 18f, 16f, 2.5f), "M7 9l3 3-3 3", "M13 15h4")
    val Sliders = stroke("sliders", "M4 7h10M18 7h2M4 17h4M12 17h8", circle(16f, 7f, 2f), circle(10f, 17f, 2f))
    val Close = stroke("close", "M6 6l12 12M18 6L6 18")
    val Back = stroke("back", "M15 5l-7 7 7 7")
    val ChevronLeft = stroke("chevL", "M15 6l-6 6 6 6", width = 2f)
    val ChevronRight = stroke("chevR", "M9 6l6 6-6 6", width = 2f)
    val ChevronUp = stroke("chevU", "M6 15l6-6 6 6", width = 2f)
    val ChevronDown = stroke("chevD", "M6 9l6 6 6-6", width = 2f)
    val Info = ImageVector.Builder("info", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(PathParser().parsePathString(circle(12f, 12f, 9f) + "M12 11v5").toNodes(),
            stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round)
        addPath(PathParser().parsePathString(circle(12f, 7.8f, 1f)).toNodes(), fill = SolidColor(Color.Black))
    }.build()
    val Eye = stroke("eye", "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z", circle(12f, 12f, 3f))
    val EyeOff = stroke("eyeOff", "M3 3l18 18", "M10.6 5.1A10 10 0 0 1 12 5c6.5 0 10 7 10 7a17 17 0 0 1-3.2 4.1M6.6 6.6C3.8 8.4 2 12 2 12s3.5 7 10 7c1.9 0 3.5-.6 4.9-1.4")
    val Trash = stroke("trash", "M4 7h16", "M9 7V4h6v3", "M6 7l1 13h10l1-13")
    val External = stroke("external", "M14 4h6v6", "M20 4l-9 9", "M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5", width = 2f)
    val Power = stroke("power", "M12 3v8", "M6.3 7.3a8 8 0 1 0 11.4 0")
    val CheckAll = stroke("checkAll", "M2 12l5 5L18 6", "M12 15l2 2L22 8")
    val Check = stroke("check", "M5 12l5 5 9-10", width = 2.6f)
    val Pause = stroke("pause", "M9 6v12M15 6v12")
    val Play = stroke("play", "M8 5l11 7-11 7z")
    val Download = stroke("download", "M12 4v11", "M7 10l5 5 5-5", "M5 20h14")
    val Upload = stroke("upload", "M12 15V4", "M7 9l5-5 5 5", "M5 20h14")
    val Phone = stroke("phone", rect(6f, 2.5f, 12f, 19f, 2.5f), "M10.5 18.5h3")
    val Server = stroke("server", rect(4f, 4f, 16f, 7f, 1.5f), rect(4f, 13f, 16f, 7f, 1.5f), "M8 7.5h.01M8 16.5h.01")
    val Refresh = stroke("refresh", "M20 11a8 8 0 1 0-2.3 5.7", "M20 5v6h-6")
    val Drag = fill("drag", circle(9f, 6f, 1.6f), circle(15f, 6f, 1.6f), circle(9f, 12f, 1.6f), circle(15f, 12f, 1.6f), circle(9f, 18f, 1.6f), circle(15f, 18f, 1.6f))
    val Docker = stroke("docker", rect(3f, 10f, 18f, 8f, 2f), "M6 10V7h3v3M9 10V7h3v3M12 10V7h3v3M9 7V4h3v3")
    val Settings = ImageVector.Builder("settings", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            PathParser().parsePathString(
                "M10.325 4.317c.426 -1.756 2.924 -1.756 3.35 0a1.724 1.724 0 0 0 2.573 1.066c1.543 -.94 3.31 .826 2.37 2.37a1.724 1.724 0 0 0 1.065 2.572c1.756 .426 1.756 2.924 0 3.35a1.724 1.724 0 0 0 -1.066 2.573c.94 1.543 -.826 3.31 -2.37 2.37a1.724 1.724 0 0 0 -2.572 1.065c-.426 1.756 -2.924 1.756 -3.35 0a1.724 1.724 0 0 0 -2.573 -1.066c-1.543 .94 -3.31 -.826 -2.37 -2.37a1.724 1.724 0 0 0 -1.065 -2.572c-1.756 -.426 -1.756 -2.924 0 -3.35a1.724 1.724 0 0 0 1.066 -2.573c-.94 -1.543 .826 -3.31 2.37 -2.37c1 .608 2.296 .07 2.572 -1.065z" +
                    circle(12f, 12f, 3f),
            ).toNodes(),
            stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }.build()
    val Wrench = stroke("wrench", "M14.7 6.3a4 4 0 0 0-5.4 5.4L4 17l3 3 5.3-5.3a4 4 0 0 0 5.4-5.4l-2.4 2.4-2.6-.4-.4-2.6z")
    val Bug = stroke("bug", "M9 9V7a3 3 0 0 1 6 0v2", rect(7f, 9f, 10f, 11f, 5f), "M12 13v7M3 13h4M17 13h4M4 8l3 2M20 8l-3 2M4 19l3-2M20 19l-3-2")
    val Activity = stroke("activity", "M3 12h4l3-8 4 16 3-8h4")
}
