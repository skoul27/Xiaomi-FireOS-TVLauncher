package com.shivam.tvlauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.shivam.tvlauncher.LaunchableApp
import com.shivam.tvlauncher.rememberAppArt
import kotlin.math.roundToInt

enum class TileSize(val width: Dp, val height: Dp, val gap: Dp) {
    /** 16:9. Five across exactly fill the 864dp between the safe-area margins; the sixth peeks in. */
    Standard(width = 154.dp, height = 154.dp * 9f / 16f, gap = 23.dp),

    /** A wide card two app columns across, so console cards line up with the apps above. */
    Console(width = 154.dp * 2 + 23.dp, height = 96.dp, gap = 23.dp),

    /** One app column across, for when more than two wide console cards wouldn't fit. */
    ConsoleCompact(width = 154.dp, height = 96.dp, gap = 23.dp),
}

/** Resting tiles sit back; the focused one comes up to full brightness. */
private const val RestingDim = 0.4f

@Composable
fun AppTile(
    app: LaunchableApp,
    size: TileSize,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    onMenu: (() -> Unit)? = null,
    moving: Boolean = false,
) {
    GlassTile(
        size = size,
        label = app.label,
        art = rememberAppArt(app)?.tile,
        onClick = onClick,
        onLongClick = onLongClick,
        onFocused = onFocused,
        onMenu = onMenu,
        moving = moving,
        modifier = modifier,
    )
}

/**
 * A focusable 16:9 glass tile with its label underneath (none if [label] is null). Draws [art]
 * full-bleed once it is available (fading it in), otherwise [content] on plain glass. [moving]
 * (being placed with the arrows) gives it a solid accent rim and swaps the label for the hint.
 */
@Composable
fun GlassTile(
    size: TileSize,
    label: String?,
    art: ImageBitmap?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onMenu: (() -> Unit)? = null,
    moving: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val motion = remember { FocusMotion() }
    val scope = rememberCoroutineScope()
    val animate = LocalAnimationsEnabled.current
    val accent = LocalAccent.current
    val artAlpha = remember { Animatable(if (art != null) 1f else 0f) }
    LaunchedEffect(art != null) {
        if (art != null) artAlpha.animateTo(1f, motionTween(animate, 220))
    }
    // Being moved: the tile grows a little, and arrows either side nudge outwards.
    val lift by animateFloatAsState(
        if (moving) 1f else 0f,
        if (animate) spring(dampingRatio = 0.6f, stiffness = 320f) else snap(),
        label = "lift",
    )
    val nudge = if (moving && animate) {
        rememberInfiniteTransition(label = "nudge").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(560, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "nudge",
        )
    } else {
        null
    }

    Column(modifier = modifier.width(size.width)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size.width, size.height)
                .onFocusChanged {
                    motion.onFocusChanged(it.isFocused, animate, scope)
                    if (it.isFocused) onFocused()
                }
                .onMenuKey(onMenu)
                .remoteClickable(onClick, onLongClick)
                .graphicsLayer {
                    // Grows a touch more while moving; it stays in the row's bleed, so nothing clips it.
                    val s = 1f + (FocusedScale - 1f) * motion.focus.value + 0.03f * lift
                    scaleX = s
                    scaleY = s
                    // Leans the way focus moved, then springs upright; the far camera keeps it subtle.
                    rotationY = TiltDegrees * motion.tilt.value
                    cameraDistance = 14f * density
                }
                .movingArrows(lift, nudge, accent)
                .focusGlow(motion, accent)
                .tileBorder(motion, accent, moving)
                .clip(TileShape)
                .tileSurface(art, artAlpha, motion),
        ) {
            if (art == null) content()
        }
        if (label != null) Text(
            text = if (moving) "OK to place" else label,
            style = LauncherText.Label,
            color = if (moving) accent else LauncherColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 10.dp, start = 2.dp, end = 2.dp)
                .graphicsLayer {
                    // Ride below the scaled tile instead of being covered by it.
                    translationY = (FocusedScale - 1f + 0.03f * lift) * motion.focus.value * size.height.toPx() / 2f
                    alpha = 0.55f + 0.45f * motion.amount
                    // Text never overlaps itself, so per-op alpha looks identical and skips an offscreen buffer.
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
        )
    }
}

/**
 * Chevrons just outside the tile's left and right edges while it's being moved, fading in with
 * [lift] and drifting outwards with [nudge] (still when animations are off).
 */
private fun Modifier.movingArrows(lift: Float, nudge: State<Float>?, color: Color): Modifier = drawWithContent {
    drawContent()
    if (lift <= 0.01f) return@drawWithContent
    val arm = 7.dp.toPx()
    val stroke = 2.5.dp.toPx()
    val drift = 4.dp.toPx() * (nudge?.value ?: 0.5f)
    val gap = 12.dp.toPx() + drift
    val cy = size.height / 2f
    val tint = color.copy(alpha = lift.coerceIn(0f, 1f))
    // Left: pointing left, outside the left edge.
    val lx = -gap
    drawLine(tint, Offset(lx, cy - arm), Offset(lx - arm, cy), stroke, StrokeCap.Round)
    drawLine(tint, Offset(lx - arm, cy), Offset(lx, cy + arm), stroke, StrokeCap.Round)
    // Right: pointing right, outside the right edge.
    val rx = size.width + gap
    drawLine(tint, Offset(rx, cy - arm), Offset(rx + arm, cy), stroke, StrokeCap.Round)
    drawLine(tint, Offset(rx + arm, cy), Offset(rx, cy + arm), stroke, StrokeCap.Round)
}

/** Art, a dimming veil that lifts on focus, and the glass top highlight. */
private fun Modifier.tileSurface(art: ImageBitmap?, artAlpha: Animatable<Float, *>, motion: FocusMotion): Modifier =
    drawWithCache {
        val dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
        val highlight = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.16f), 0.5f to Color.Transparent)
        onDrawWithContent {
            val f = motion.amount
            drawRect(Glass.TileBase)
            drawRect(Glass.Fill)
            if (art != null) drawImage(art, dstSize = dstSize, alpha = artAlpha.value, filterQuality = FilterQuality.Low)
            drawContent()
            if (f < 1f) drawRect(Color.Black, alpha = RestingDim * (1f - f))
            drawRect(highlight, alpha = 0.5f + 0.5f * f)
        }
    }

/**
 * Hairline glass edge at rest; a bright, slightly accent-tinted rim when focused; a thicker rim
 * in the accent itself while [moving].
 */
private fun Modifier.tileBorder(motion: FocusMotion, accent: Color, moving: Boolean): Modifier = drawWithCache {
    val radius = Radii.Tile.toPx()
    val focusedColor = if (moving) accent else lerp(Color.White, accent, 0.25f)
    onDrawWithContent {
        drawContent()
        val f = motion.amount
        val width = ((if (moving) 1f + 3f * f else 1f + 1.5f * f)).dp.toPx()
        val inset = width / 2f
        drawRoundRect(
            color = lerp(Glass.Border, focusedColor, f),
            topLeft = Offset(inset, inset),
            size = Size(size.width - width, size.height - width),
            cornerRadius = CornerRadius(radius - inset),
            style = Stroke(width),
        )
    }
}
