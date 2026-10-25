package com.shivam.tvlauncher.ui

import androidx.compose.animation.core.Animatable
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

enum class TileSize(val width: Dp, val gap: Dp) {
    Large(width = 192.dp, gap = 26.dp),
    Small(width = 152.dp, gap = 22.dp);

    val height: Dp get() = width * 9f / 16f
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
) {
    GlassTile(
        size = size,
        label = app.label,
        art = rememberAppArt(app)?.tile,
        onClick = onClick,
        onLongClick = onLongClick,
        onFocused = onFocused,
        modifier = modifier,
    )
}

/**
 * A focusable 16:9 glass tile with its label underneath (none if [label] is null). Draws [art]
 * full-bleed once it is available (fading it in), otherwise [content] on plain glass.
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

    Column(modifier = modifier.width(size.width)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size.width, size.height)
                .onFocusChanged {
                    motion.onFocusChanged(it.isFocused, animate, scope)
                    if (it.isFocused) onFocused()
                }
                .remoteClickable(onClick, onLongClick)
                .graphicsLayer {
                    val s = 1f + (FocusedScale - 1f) * motion.focus.value
                    scaleX = s
                    scaleY = s
                    // Leans the way focus moved, then springs upright; the far camera keeps it subtle.
                    rotationY = TiltDegrees * motion.tilt.value
                    cameraDistance = 14f * density
                }
                .focusGlow(motion, accent)
                .tileBorder(motion, accent)
                .clip(TileShape)
                .tileSurface(art, artAlpha, motion),
        ) {
            if (art == null) content()
        }
        if (label != null) Text(
            text = label,
            style = LauncherText.Label,
            color = LauncherColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 12.dp, start = 2.dp, end = 2.dp)
                .graphicsLayer {
                    // Ride below the scaled tile instead of being covered by it.
                    translationY = (FocusedScale - 1f) * motion.focus.value * size.height.toPx() / 2f
                    alpha = 0.55f + 0.45f * motion.amount
                    // Text never overlaps itself, so per-op alpha looks identical and skips an offscreen buffer.
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
        )
    }
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

/** Hairline glass edge at rest; a bright, slightly accent-tinted rim when focused. */
private fun Modifier.tileBorder(motion: FocusMotion, accent: Color): Modifier = drawWithCache {
    val radius = Radii.Tile.toPx()
    val focusedColor = lerp(Color.White, accent, 0.25f)
    onDrawWithContent {
        drawContent()
        val f = motion.amount
        val width = (1f + 1.5f * f).dp.toPx()
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
