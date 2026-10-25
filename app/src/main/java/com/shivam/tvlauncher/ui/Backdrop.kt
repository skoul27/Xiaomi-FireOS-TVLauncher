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
