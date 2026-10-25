package com.shivam.tvlauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.shivam.tvlauncher.Console
import com.shivam.tvlauncher.ConsoleBrand
import kotlin.math.min

/** A console's tile: its logo drawn in code, with the HDMI port it's on. */
@Composable
fun ConsoleTile(console: Console, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassTile(size = TileSize.Standard, label = console.name, art = null, onClick = onClick, onLongClick = null, modifier = modifier) {
        when (console.brand) {
            ConsoleBrand.PlayStation5 -> PlayStationArt()
        }
        Text(
            text = "HDMI ${console.hdmiPort}",
            style = LauncherText.Caption.copy(fontSize = 11.sp, lineHeight = 14.sp),
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 9.dp),
        )
    }
}

/** Black, faintly lit from the top left, like the PS5's own branding. */
private val PlayStationBackground = Brush.linearGradient(listOf(Color(0xFF23262E), Color(0xFF050608)))

@Composable
private fun PlayStationArt() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().background(PlayStationBackground),
    ) {
        Ps5Wordmark(Color.White, Modifier.size(width = 70.dp, height = 29.dp))
    }
}

/**
 * "PS5" in the logo's geometric monoline style: every letter is built from one stroke weight,
 * straight runs and half-circles. Drawn in a 23.8 × 10 unit box, scaled to fit.
 */
@Composable
private fun Ps5Wordmark(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = min(size.width / 23.8f, size.height / 10f)
        translate(left = (size.width - 23.8f * unit) / 2f, top = (size.height - 10f * unit) / 2f) {
            scale(unit, pivot = Offset.Zero) {
                drawPath(Ps5Path, color, style = Stroke(width = 1.6f, join = StrokeJoin.Miter))
            }
        }
    }
}

private val Ps5Path: Path by lazy {
    val t = 0.8f // half the stroke: centre lines sit this far inside the 10-unit height
    fun Path.halfCircle(cx: Float, cy: Float, r: Float, start: Float, sweep: Float) =
        arcTo(Rect(cx - r, cy - r, cx + r, cy + r), start, sweep, forceMoveTo = false)

    Path().apply {
        // P: stem, then a bowl closing back onto it at mid-height.
        moveTo(t, 10f)
        lineTo(t, t)
        lineTo(3.2f, t)
        halfCircle(3.2f, 3.0f, 2.2f, 270f, 180f)
        lineTo(t, 5.2f)

        // S (x 8–15): top run, left half-circle down to the middle, right half-circle to the base.
        moveTo(15f, t)
        lineTo(8f + t + 2.1f, t)
        halfCircle(8f + t + 2.1f, 2.9f, 2.1f, 270f, -180f)
        lineTo(15f - t - 2.1f, 5f)
        halfCircle(15f - t - 2.1f, 7.1f, 2.1f, 270f, 180f)
        lineTo(8f, 10f - t)

        // 5 (x 16.8–23.8): flat top, short drop on the left, then a deeper bowl to the base.
        moveTo(23.8f, t)
        lineTo(16.8f + t, t)
        lineTo(16.8f + t, 4.6f)
        lineTo(23.8f - t - 2.3f, 4.6f)
        halfCircle(23.8f - t - 2.3f, 6.9f, 2.3f, 270f, 180f)
        lineTo(16.8f, 10f - t)
    }
}
