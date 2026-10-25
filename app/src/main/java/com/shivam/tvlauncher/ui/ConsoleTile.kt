package com.shivam.tvlauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.shivam.tvlauncher.Console
import com.shivam.tvlauncher.ConsoleBrand

/** A console's tile: its logo drawn in code, with the HDMI port it's on. The logo is the label. */
@Composable
fun ConsoleTile(console: Console, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassTile(size = TileSize.Small, label = null, art = null, onClick = onClick, onLongClick = null, modifier = modifier) {
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

private val PlayStationBlue = Color(0xFF0070D1)
private val PlayStationNavy = Color(0xFF00246B)

/** "PS5" over the four face-button shapes, on PlayStation blue. */
@Composable
private fun PlayStationArt() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().background(Brush.linearGradient(listOf(PlayStationBlue, PlayStationNavy))),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "PS5",
                style = LauncherText.Display.copy(fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp),
                color = Color.White,
            )
            FaceButtons(Color.White.copy(alpha = 0.85f))
        }
    }
}

/** Triangle, circle, cross, square, in a row. */
@Composable
private fun FaceButtons(color: Color) {
    Canvas(Modifier.size(width = 62.dp, height = 11.dp)) {
        val s = size.height
        val gap = (size.width - s * 4) / 3f
        val stroke = Stroke(width = s * 0.15f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val inset = stroke.width / 2f
        fun left(i: Int) = i * (s + gap)

        val triangle = Path().apply {
            moveTo(left(0) + s / 2f, inset)
            lineTo(left(0) + s - inset, s - inset)
            lineTo(left(0) + inset, s - inset)
            close()
        }
        drawPath(triangle, color, style = stroke)
        drawCircle(color, radius = s / 2f - inset, center = Offset(left(1) + s / 2f, s / 2f), style = stroke)
        drawLine(color, Offset(left(2) + inset, inset), Offset(left(2) + s - inset, s - inset), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(left(2) + s - inset, inset), Offset(left(2) + inset, s - inset), stroke.width, StrokeCap.Round)
        drawRect(color, topLeft = Offset(left(3) + inset, inset), size = Size(s - inset * 2, s - inset * 2), style = stroke)
    }
}
