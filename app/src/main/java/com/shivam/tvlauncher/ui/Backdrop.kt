package com.shivam.tvlauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.shivam.tvlauncher.Wallpaper
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.shivam.tvlauncher.AppArt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Cross-fades the backdrop between apps. Everything is read in the draw phase, so a change
 * redraws the backdrop layer for the length of the fade and nothing recomposes. At rest nothing
 * animates.
 */
@Stable
class BackdropState(private val scope: CoroutineScope) {
    var current by mutableStateOf<AppArt?>(null)
        private set
    var previous by mutableStateOf<AppArt?>(null)
        private set
    val fade = Animatable(1f)
    private var job: Job? = null

    fun show(art: AppArt, animate: Boolean) {
        if (art === current) return
        // Mid-fade, keep whichever image is more visible as the one fading out.
        if (fade.value >= 0.5f || previous == null) previous = current
        current = art
        job?.cancel()
        job = scope.launch {
            fade.snapTo(0f)
            fade.animateTo(1f, motionTween(animate, 600))
            previous = null
        }
    }
}

/**
 * The focused app's pre-composited backdrop (see [AppArt.backdrop]), stretched full screen.
 * At most two full-screen draws, and only during a fade.
 */
@Composable
fun Backdrop(state: BackdropState, modifier: Modifier = Modifier) {
    // Its own layer: focus animations elsewhere never make the full-screen backdrop redraw.
    Box(modifier.graphicsLayer().drawBehind {
        val p = state.fade.value
        val previous = state.previous
        val current = state.current
        // The art is opaque, so the charcoal base is only needed when nothing covers it fully.
        if (current == null || (p < 1f && previous == null)) drawRect(LauncherColors.Ink)
        if (previous != null && p < 1f) drawArt(previous.backdrop, alpha = 1f, zoom = 1f)
        // The incoming image settles from a slight zoom: the only motion, and only during the fade.
        if (current != null) drawArt(current.backdrop, alpha = p, zoom = 1.05f - 0.05f * p)
    })
}

/**
 * A fixed background instead of the app-tinted one: a preset gradient, or the chosen [photo]
 * (cropped to fill). Darkened top and bottom so the clock and tile labels stay readable.
 */
@Composable
fun WallpaperBackdrop(wallpaper: Wallpaper, photo: ImageBitmap?, modifier: Modifier = Modifier) {
    Box(modifier.graphicsLayer().drawWithCache {
        val scrim = Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.45f),
            0.3f to Color.Black.copy(alpha = 0.15f),
            0.6f to Color.Black.copy(alpha = 0.35f),
            1f to Color.Black.copy(alpha = 0.7f),
        )
        val layers = presetLayers(wallpaper, size)
        onDrawBehind {
            drawRect(LauncherColors.Ink)
            if (wallpaper == Wallpaper.Photo && photo != null) {
                val scale = maxOf(size.width / photo.width, size.height / photo.height)
                val w = photo.width * scale
                val h = photo.height * scale
                drawImage(
                    photo,
                    dstOffset = IntOffset(((size.width - w) / 2f).roundToInt(), ((size.height - h) / 2f).roundToInt()),
                    dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                    filterQuality = FilterQuality.Medium,
                )
            } else {
                layers.forEach { drawRect(it) }
            }
            drawRect(scrim)
        }
    })
}

/** A base gradient plus two soft glows, per preset. */
private fun presetLayers(wallpaper: Wallpaper, size: Size): List<Brush> {
    fun glow(color: Color, x: Float, y: Float, r: Float) =
        Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(size.width * x, size.height * y), radius = size.width * r)
    return when (wallpaper) {
        Wallpaper.Aurora -> listOf(
            Brush.linearGradient(listOf(Color(0xFF07131A), Color(0xFF0B2230), Color(0xFF0A1420))),
            glow(Color(0x9926C6A0), 0.25f, 0.2f, 0.55f),
            glow(Color(0x806C4CE0), 0.8f, 0.35f, 0.5f),
        )
        Wallpaper.Midnight -> listOf(
            Brush.verticalGradient(listOf(Color(0xFF0A0F24), Color(0xFF05060D))),
            glow(Color(0x803A5BD9), 0.75f, 0.1f, 0.6f),
            glow(Color(0x40A9C9FF), 0.15f, 0.9f, 0.45f),
        )
        Wallpaper.Dusk -> listOf(
            Brush.linearGradient(listOf(Color(0xFF2A1033), Color(0xFF1A0C1E), Color(0xFF0E0A12))),
            glow(Color(0x99FF7A59), 0.85f, 0.15f, 0.55f),
            glow(Color(0x80C2448F), 0.2f, 0.7f, 0.5f),
        )
        Wallpaper.Ocean -> listOf(
            Brush.verticalGradient(listOf(Color(0xFF062235), Color(0xFF031019))),
            glow(Color(0x9900A3C4), 0.3f, 0.25f, 0.6f),
            glow(Color(0x660D5BD1), 0.85f, 0.8f, 0.5f),
        )
        Wallpaper.Dynamic, Wallpaper.Photo -> emptyList()
    }
}

private fun DrawScope.drawArt(image: ImageBitmap, alpha: Float, zoom: Float) {
    if (alpha <= 0f) return
    val w = size.width * zoom
    val h = size.height * zoom
    drawImage(
        image,
        dstOffset = IntOffset(((size.width - w) / 2f).roundToInt(), ((size.height - h) / 2f).roundToInt()),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.Low,
    )
}
