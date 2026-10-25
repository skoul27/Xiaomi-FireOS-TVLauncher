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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
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

/** PlayStation blue, lighter at the top left. */
private val PlayStationBackground = Brush.linearGradient(listOf(Color(0xFF0070D1), Color(0xFF00296B)))

@Composable
private fun PlayStationArt() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().background(PlayStationBackground),
    ) {
        Ps5Wordmark(Color.White, Modifier.size(width = 104.dp, height = 24.dp))
    }
}

/** The official PS5 wordmark, scaled to fit and centred. */
@Composable
private fun Ps5Wordmark(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val bounds = Ps5Path.getBounds()
        val scale = min(size.width / bounds.width, size.height / bounds.height)
        translate(
            left = (size.width - bounds.width * scale) / 2f - bounds.left * scale,
            top = (size.height - bounds.height * scale) / 2f - bounds.top * scale,
        ) {
            scale(scale, pivot = Offset.Zero) { drawPath(Ps5Path, color) }
        }
    }
}

/**
 * The "PS5" letters from the PlayStation 5 logo on Wikimedia Commons
 * (File:PS5_logo.svg, path "ps5"), without the trademark sign.
 */
private val Ps5Path: Path by lazy {
    PathParser().parsePathString(
        "M945,42.94H761V92.82s-.56,19.26,23.26,19.26H896.73a31.57,31.57,0,0,1,12.83,2.61c6.28,2.8,13.34,8" +
        ".5,13.34,20,0,10.32-5,16.18-9.95,19.46a25,25,0,0,1-13.76,4H761v14H915.09s35-1.42,35-37.39c0-18.2" +
        "9-10.68-27.35-20.29-31.79A47,47,0,0,0,910,98.68H798.53a11.24,11.24,0,0,1-5.34-1.27c-2.38-1.29-5-" +
        "3.85-5-8.89V56.75H945ZM529.41,158.1v14h78.1a38.71,38.71,0,0,0,25.5-9.23,32.27,32.27,0,0,0,11.38-" +
        "25.36V78a21.5,21.5,0,0,1,8-17A20,20,0,0,1,665,56.75h68.09V42.94H652s-35,.56-35,37.58v55.57A24.12" +
        ",24.12,0,0,1,613.46,149a18.78,18.78,0,0,1-16.7,9.13ZM349.09,42.94V56.75h139.2a26.28,26.28,0,0,1," +
        "11.22,2.41c5.52,2.59,11.72,7.92,11.72,18.73,0,10.5-6.2,15.75-11.72,18.35a26.78,26.78,0,0,1-11.4," +
        "2.44H384.87a39.28,39.28,0,0,0-17.64,4c-8.59,4.31-18.14,14.46-18.14,31.2v38h26.79V134.05a19.66,19" +
        ".66,0,0,1,1.31-7.2c2.21-5.67,7.8-14.77,20.87-14.77h104.1a37.78,37.78,0,0,0,10.36-1.39c9.56-2.72," +
        "25.5-10.58,25.5-32.8,0-34.53-34.37-34.95-34.37-34.95Z",
    ).toPath()
}
