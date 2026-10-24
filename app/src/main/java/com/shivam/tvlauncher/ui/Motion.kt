package com.shivam.tvlauncher.ui

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.min

private val FocusInSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 380f)
private val FocusOutSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f)
private val TiltSpring = spring<Float>(dampingRatio = 0.5f, stiffness = 260f)

/** Initial angular velocity (deg/s) of the tilt kick; peaks around 4–5°. */
private const val TiltKick = 120f

const val FocusedScale = 1.1f

/**
 * Last D-pad direction, written by a key listener at the screen root and read by the card
 * that receives focus. Deliberately not snapshot state: nothing should recompose on a key press.
 */
class FocusDirection {
    var dx = 0
    var dy = 0
}

val LocalFocusDirection = staticCompositionLocalOf { FocusDirection() }

/**
 * Per-card animation state. All values are read only inside graphicsLayer/draw lambdas,
 * so focus changes invalidate drawing but never recompose.
 */
@Stable
class CardMotion {
    /** 0 = resting, 1 = focused; overshoots slightly with the spring. */
    val focus = Animatable(0f)
    val tiltX = Animatable(0f)
    val tiltY = Animatable(0f)
    private var isFocused = false

    fun onFocusChanged(focused: Boolean, from: FocusDirection, scope: CoroutineScope) {
        if (focused == isFocused) return
        isFocused = focused
        scope.launch { focus.animateTo(if (focused) 1f else 0f, if (focused) FocusInSpring else FocusOutSpring) }
        if (focused) {
            // A kick that leans the card toward the direction of travel, then springs back flat.
            scope.launch { tiltY.animateTo(0f, TiltSpring, initialVelocity = from.dx * TiltKick) }
            scope.launch { tiltX.animateTo(0f, TiltSpring, initialVelocity = -from.dy * TiltKick) }
        }
    }
}

/** Slowly cross-fades the screen background toward the focused app's colour. */
@Stable
class BackgroundTint(private val scope: CoroutineScope, initial: Color) {
    val color = Animatable(initial)
    private var target = initial

    fun show(next: Color) {
        if (next == target) return
        target = next
        scope.launch { color.animateTo(next, tween(durationMillis = 1400, easing = FastOutSlowInEasing)) }
    }
}

val LocalBackgroundTint = staticCompositionLocalOf<BackgroundTint?> { null }

/**
 * Scrolls a row only when the focused card would come closer than [margin] to an edge,
 * so a neighbour stays peeking on each side. Critically damped, so held D-pad presses glide.
 */
@OptIn(ExperimentalFoundationApi::class)
class ComfortableScrollSpec(private val margin: Float) : BringIntoViewSpec {
    @Deprecated("Overrides the deprecated member in BringIntoViewSpec")
    override val scrollAnimationSpec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 300f)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val edge = min(margin + size * 0.35f, (containerSize - size) / 2f)
        val leading = offset - edge
        val trailing = offset + size - (containerSize - edge)
        return when {
            leading < 0f -> leading
            trailing > 0f -> trailing
            else -> 0f
        }
    }
}
