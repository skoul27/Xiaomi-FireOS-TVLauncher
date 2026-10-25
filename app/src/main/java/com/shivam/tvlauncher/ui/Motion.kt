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
    private var isFocused = false

    fun onFocusChanged(focused: Boolean, animate: Boolean, scope: CoroutineScope) {
        if (focused == isFocused) return
        isFocused = focused
        val target = if (focused) 1f else 0f
        scope.launch {
            if (animate) focus.animateTo(target, if (focused) FocusInSpring else FocusOutSpring) else focus.snapTo(target)
        }
    }

    /** Clamped for alpha and colour, which must not overshoot. */
    val amount: Float get() = focus.value.coerceIn(0f, 1f)
}

/**
 * Scrolls a row only when the focused tile would come closer than [margin] to an edge,
 * so a neighbour stays peeking on each side. Critically damped, so held D-pad presses glide.
 */
@OptIn(ExperimentalFoundationApi::class)
class ComfortableScrollSpec(private val margin: Float, animate: Boolean) : BringIntoViewSpec {
    @Deprecated("Overrides the deprecated member in BringIntoViewSpec")
    override val scrollAnimationSpec: AnimationSpec<Float> =
        if (animate) spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 300f) else snap()

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
