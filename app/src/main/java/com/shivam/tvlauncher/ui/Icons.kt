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
import androidx.compose.ui.graphics.StrokeCap
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
