package com.shivam.tvlauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.min

private val FocusInSpring = spring<Float>(dampingRatio = 0.62f, stiffness = 420f)
private val TiltSpring = spring<Float>(dampingRatio = 0.45f, stiffness = 180f)

/** Degrees a newly focused tile leans toward the way focus moved, before springing upright. */
const val TiltDegrees = 7f

/**
 * Horizontal direction of the last D-pad press (-1 left, 1 right, 0 otherwise), set by the home
 * screen before focus moves so a tile can lean the way it was reached. Plain field: it is only
 * read inside a focus callback that runs right after the key.
 */
object FocusDirection {
    var horizontal = 0
}
private val FocusOutSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 520f)

const val FocusedScale = 1.08f

/** A tween, or an instant snap when the user has turned animations off. */
fun <T> motionTween(enabled: Boolean, durationMillis: Int): FiniteAnimationSpec<T> =
    if (enabled) tween(durationMillis, easing = FastOutSlowInEasing) else snap()

/**
 * Per-tile focus progress (0 = resting, 1 = focused; overshoots a little on the spring).
 * Read only inside graphicsLayer/draw lambdas, so focus changes redraw but never recompose.
 */
@Stable
class FocusMotion {
    val focus = Animatable(0f)
    /** -1..1, decaying to 0: the lean a tile gets as focus arrives. Always 0 with animations off. */
    val tilt = Animatable(0f)
    private var isFocused = false

    fun onFocusChanged(focused: Boolean, animate: Boolean, scope: CoroutineScope) {
        if (focused == isFocused) return
        isFocused = focused
        val target = if (focused) 1f else 0f
        scope.launch {
            if (animate) focus.animateTo(target, if (focused) FocusInSpring else FocusOutSpring) else focus.snapTo(target)
        }
        val lean = FocusDirection.horizontal
        if (focused && animate && lean != 0) {
            scope.launch {
                tilt.snapTo(lean.toFloat())
                tilt.animateTo(0f, TiltSpring)
            }
        }
    }

    /** Clamped for alpha and colour, which must not overshoot. */
    val amount: Float get() = focus.value.coerceIn(0f, 1f)
}

/**
 * Scrolls a row only when the focused tile would come closer than [margin] to an edge,
 * so a neighbour stays peeking on each side.
 */
@OptIn(ExperimentalFoundationApi::class)
class ComfortableScrollSpec(private val margin: Float, animate: Boolean) : BringIntoViewSpec {
    @Deprecated("Overrides the deprecated member in BringIntoViewSpec")
    override val scrollAnimationSpec: AnimationSpec<Float> =
        // Just under critical damping: a held D-pad still glides, a single press settles with a hint of spring.
        if (animate) spring(dampingRatio = 0.82f, stiffness = 300f) else snap()

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
