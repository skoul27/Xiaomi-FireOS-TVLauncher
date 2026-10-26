package com.shivam.tvlauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Small glyphs drawn in code: no icon library, no third-party assets.

@Composable
fun GearIcon(color: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val toothW = r * 0.36f
        repeat(8) { i ->
            rotate(i * 45f) {
                drawRoundRect(
                    color,
                    topLeft = Offset(center.x - toothW / 2f, center.y - r),
                    size = Size(toothW, r * 0.45f),
                    cornerRadius = CornerRadius(toothW * 0.25f),
                )
            }
        }
        drawCircle(color, radius = r * 0.53f, style = Stroke(width = r * 0.36f))
    }
}

/** Wi-Fi fan; arcs above [level] (0–3) are faint. [level] < 0 draws it crossed out. */
@Composable
fun WifiIcon(color: Color, level: Int, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val stroke = w * 0.12f
        val origin = Offset(w / 2f, this.size.height * 0.86f)
        drawCircle(color.copy(alpha = if (level >= 0) 1f else 0.35f), radius = stroke * 0.75f, center = origin)
        for (arc in 1..3) {
            val radius = w * (0.18f + arc * 0.15f)
            drawArc(
                color = color.copy(alpha = if (level >= arc) 1f else 0.3f),
                startAngle = -135f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(origin.x - radius, origin.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        if (level < 0) {
            drawLine(color, Offset(w * 0.15f, w * 0.12f), Offset(w * 0.85f, this.size.height * 0.92f), stroke, StrokeCap.Round)
        }
    }
}

/** An Ethernet port seen head-on. */
@Composable
fun EthernetIcon(color: Color, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val stroke = w * 0.11f
        val path = Path().apply {
            moveTo(w * 0.12f, w * 0.3f)
            lineTo(w * 0.88f, w * 0.3f)
            lineTo(w * 0.88f, w * 0.72f)
            lineTo(w * 0.68f, w * 0.72f)
            lineTo(w * 0.68f, w * 0.88f)
            lineTo(w * 0.32f, w * 0.88f)
            lineTo(w * 0.32f, w * 0.72f)
            lineTo(w * 0.12f, w * 0.72f)
            close()
        }
        drawPath(path, color, style = Stroke(width = stroke))
        for (x in listOf(0.36f, 0.5f, 0.64f)) {
            drawLine(color, Offset(w * x, w * 0.42f), Offset(w * x, w * 0.56f), stroke * 0.8f, StrokeCap.Round)
        }
    }
}

/** The Material "cast" glyph: a screen with broadcast arcs in its lower-left corner. */
@Composable
fun CastIcon(color: Color, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        scale(this.size.width / 24f, pivot = Offset.Zero) {
            drawPath(CastPath, color)
        }
    }
}

private val CastPath: Path by lazy {
    PathParser().parsePathString(
        "M1 18v3h3c0-1.66-1.34-3-3-3zm0-4v2c2.76 0 5 2.24 5 5h2c0-3.87-3.13-7-7-7zm0-4v2c4.97 0 9 4.03 9 9h2" +
            "c0-6.08-4.93-11-11-11zm20-7H3c-1.1 0-2 .9-2 2v3h2V5h18v14h-7v2h7c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z",
    ).toPath()
}

/** A right-pointing triangle, rounded a little: play. */
@Composable
fun PlayIcon(color: Color, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val path = Path().apply {
            moveTo(w * 0.24f, w * 0.12f)
            lineTo(w * 0.88f, w * 0.5f)
            lineTo(w * 0.24f, w * 0.88f)
            close()
        }
        drawPath(path, color)
        drawPath(path, color, style = Stroke(width = w * 0.08f, join = StrokeJoin.Round))
    }
}

/** Two rounded bars: pause. */
@Composable
fun PauseIcon(color: Color, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val bar = Size(w * 0.24f, w * 0.76f)
        for (x in listOf(0.18f, 0.58f)) {
            drawRoundRect(color, topLeft = Offset(w * x, w * 0.12f), size = bar, cornerRadius = CornerRadius(w * 0.06f))
        }
    }
}

/** A triangle into a bar: next track. [previous] mirrors it. */
@Composable
fun SkipIcon(color: Color, previous: Boolean, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        scale(if (previous) -1f else 1f, 1f) {
            val path = Path().apply {
                moveTo(w * 0.16f, w * 0.16f)
                lineTo(w * 0.68f, w * 0.5f)
                lineTo(w * 0.16f, w * 0.84f)
                close()
            }
            drawPath(path, color)
            drawPath(path, color, style = Stroke(width = w * 0.08f, join = StrokeJoin.Round))
            drawRoundRect(color, topLeft = Offset(w * 0.7f, w * 0.16f), size = Size(w * 0.14f, w * 0.68f), cornerRadius = CornerRadius(w * 0.05f))
        }
    }
}

/** A box with an arrow leaving its top-right corner: open in the app. */
@Composable
fun OpenIcon(color: Color, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val line = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val box = Path().apply {
            moveTo(w * 0.45f, w * 0.14f)
            lineTo(w * 0.22f, w * 0.14f)
            quadraticTo(w * 0.12f, w * 0.14f, w * 0.12f, w * 0.24f)
            lineTo(w * 0.12f, w * 0.78f)
            quadraticTo(w * 0.12f, w * 0.88f, w * 0.22f, w * 0.88f)
            lineTo(w * 0.76f, w * 0.88f)
            quadraticTo(w * 0.86f, w * 0.88f, w * 0.86f, w * 0.78f)
            lineTo(w * 0.86f, w * 0.55f)
        }
        drawPath(box, color, style = line)
        drawLine(color, Offset(w * 0.46f, w * 0.54f), Offset(w * 0.88f, w * 0.12f), line.width, StrokeCap.Round)
        val head = Path().apply {
            moveTo(w * 0.62f, w * 0.12f)
            lineTo(w * 0.88f, w * 0.12f)
            lineTo(w * 0.88f, w * 0.38f)
        }
        drawPath(head, color, style = line)
    }
}

/** A 24-unit icon path, filled and scaled to [size]. */
@Composable
fun PathIcon(path: Path, color: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        scale(this.size.width / 24f, pivot = Offset.Zero) { drawPath(path, color) }
    }
}

/** The app menus' icons: Material Icons outlines (Apache License 2.0), 24-unit paths. */
object MenuIcons {
    private fun icon(d: String): Path = PathParser().parsePathString(d).toPath()

    val Add: Path by lazy {
        icon("M13 7h-2v4H7v2h4v4h2v-4h4v-2h-4V7zm-1-5C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z")
    }
    val Remove: Path by lazy {
        icon("M7 11v2h10v-2H7zm5-9C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z")
    }
    val Move: Path by lazy {
        icon("M6.99 11L3 15l3.99 4v-3H14v-2H6.99v-3zM21 9l-3.99-4v3H10v2h7.01v3L21 9z")
    }
    val Hide: Path by lazy {
        icon(
            "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5" +
                "-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12" +
                "c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55" +
                "c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5" +
                " 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z",
        )
    }
    val Info: Path by lazy {
        // The ring (even-odd, so its middle is empty) plus the "i" drawn on top as its own shapes.
        icon("M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z")
            .apply {
                fillType = PathFillType.EvenOdd
                addPath(icon("M11 7h2v2h-2z M11 11h2v6h-2z"))
            }
    }
    val Delete: Path by lazy {
        icon("M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM8 9h8v10H8V9zm7.5-5l-1-1h-5l-1 1H5v2h14V4h-3.5z")
    }
    val Close: Path by lazy {
        icon(
            "M14.59 8L12 10.59 9.41 8 8 9.41 10.59 12 8 14.59 9.41 16 12 13.41 14.59 16 16 14.59 13.41 12 16 9.41 14.59 8z" +
                "M12 2C6.47 2 2 6.47 2 12s4.47 10 10 10 10-4.47 10-10S17.53 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z",
        )
    }
    val CloseAll: Path by lazy { icon("M5 13h14v-2H5v2zm-2 4h14v-2H3v2zM7 7v2h14V7H7z") }
}

/** A 3×3 grid of rounded squares: all apps. */
@Composable
fun GridIcon(color: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val cell = w * 0.26f
        val gap = (w - cell * 3) / 2f
        for (row in 0..2) for (col in 0..2) {
            drawRoundRect(
                color,
                topLeft = Offset(col * (cell + gap), row * (cell + gap)),
                size = Size(cell, cell),
                cornerRadius = CornerRadius(cell * 0.3f),
            )
        }
    }
}
