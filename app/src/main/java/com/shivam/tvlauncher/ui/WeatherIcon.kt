package com.shivam.tvlauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shivam.tvlauncher.WeatherKind

/**
 * Thin line weather glyphs, to sit with the light clock text. Drawn on a 24-unit grid; the moon
 * replaces the sun at night.
 */
@Composable
fun WeatherIcon(kind: WeatherKind, isDay: Boolean, color: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        scale(this.size.width / 24f, pivot = Offset.Zero) {
            val line = Stroke(width = 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun sky(center: Offset, radius: Float) =
                if (isDay) sun(center, radius, color, line) else moon(center, radius + 1.5f, color, line)
            when (kind) {
                WeatherKind.Clear -> sky(Offset(12f, 12f), 4.5f)
                WeatherKind.PartlyCloudy -> {
                    val cloud = cloudPath(1f, 2f)
                    clipPath(cloud, ClipOp.Difference) { sky(Offset(9f, 9f), 3.2f) }
                    drawPath(cloud, color, style = line)
                }
                WeatherKind.Cloudy -> drawPath(cloudPath(0f, -1f), color, style = line)
                WeatherKind.Fog -> {
                    drawPath(cloudPath(0f, -4f), color, style = line)
                    drawLine(color, Offset(4f, 17.5f), Offset(20f, 17.5f), line.width, StrokeCap.Round)
                    drawLine(color, Offset(7f, 21f), Offset(17f, 21f), line.width, StrokeCap.Round)
                }
                WeatherKind.Rain -> {
                    drawPath(cloudPath(0f, -3f), color, style = line)
                    for (x in listOf(8f, 12f, 16f)) {
                        drawLine(color, Offset(x, 18f), Offset(x - 1f, 21f), line.width, StrokeCap.Round)
                    }
                }
                WeatherKind.Snow -> {
                    drawPath(cloudPath(0f, -3f), color, style = line)
                    drawCircle(color, 1.1f, Offset(8f, 19f))
                    drawCircle(color, 1.1f, Offset(12f, 21f))
                    drawCircle(color, 1.1f, Offset(16f, 19f))
                }
                WeatherKind.Thunder -> {
                    drawPath(cloudPath(0f, -3f), color, style = line)
                    val bolt = Path().apply {
                        moveTo(13f, 16f)
                        lineTo(10.5f, 19.5f)
                        lineTo(13.5f, 19.5f)
                        lineTo(11f, 23f)
                    }
                    drawPath(bolt, color, style = line)
                }
            }
        }
    }
}

private fun DrawScope.sun(center: Offset, radius: Float, color: Color, line: Stroke) {
    drawCircle(color, radius, center, style = line)
    for (i in 0 until 8) {
        val angle = Math.toRadians(i * 45.0)
        val dir = Offset(kotlin.math.cos(angle).toFloat(), kotlin.math.sin(angle).toFloat())
        drawLine(color, center + dir * (radius + 2.3f), center + dir * (radius + 4f), line.width, StrokeCap.Round)
    }
}

/** A crescent: the disc less a second disc offset to the upper right. */
private fun DrawScope.moon(center: Offset, radius: Float, color: Color, line: Stroke) {
    val disc = Path().apply { addOval(Rect(center, radius)) }
    val bite = Path().apply { addOval(Rect(center + Offset(radius * 0.55f, -radius * 0.45f), radius * 0.85f)) }
    drawPath(Path.combine(PathOperation.Difference, disc, bite), color, style = line)
}

/** One outline around three puffs and a flat base, moved by ([dx], [dy]). */
private fun cloudPath(dx: Float, dy: Float): Path {
    fun circle(x: Float, y: Float, r: Float) = Path().apply { addOval(Rect(Offset(x + dx, y + dy), r)) }
    val base = Path().apply {
        addRoundRect(RoundRect(Rect(4f + dx, 12f + dy, 20f + dx, 18.5f + dy), CornerRadius(3.25f)))
    }
    return listOf(circle(8.5f, 14f, 4.5f), circle(13.5f, 11.5f, 5.5f), circle(17f, 14.5f, 4f))
        .fold(base) { acc, puff -> Path.combine(PathOperation.Union, acc, puff) }
}
