package com.shivam.tvlauncher.ui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Faint fill, top highlight and hairline border: the launcher's one glass recipe. */
fun Modifier.glass(
    shape: Shape,
    fill: Color = Glass.Fill,
    border: Color = Glass.Border,
): Modifier = this
    .clip(shape)
    .background(fill)
    .drawWithCache {
        val highlight = Brush.verticalGradient(0f to Glass.Highlight, 0.55f to Color.Transparent)
        onDrawWithContent {
            drawRect(highlight)
            drawContent()
        }
    }
    .border(1.dp, border, shape)

/**
 * A soft accent glow around a focused 16:9 tile, from a tiny pre-blurred mask stretched with
 * bilinear filtering: one bitmap draw instead of a per-pixel blur.
 */
fun Modifier.focusGlow(motion: FocusMotion, color: Color): Modifier = drawWithCache {
    val pad = size.width * GlowPadFraction
    val dstOffset = IntOffset(-pad.roundToInt(), -pad.roundToInt())
    val dstSize = IntSize((size.width + pad * 2).roundToInt(), (size.height + pad * 2).roundToInt())
    val tint = ColorFilter.tint(color, BlendMode.SrcIn)
    onDrawBehind {
        val f = motion.amount
        if (f > 0f) {
            drawImage(GlowMask, dstOffset = dstOffset, dstSize = dstSize, alpha = 0.7f * f, colorFilter = tint, filterQuality = FilterQuality.Low)
        }
    }
}

private const val GlowPadFraction = 0.11f

/** A blurred white 16:9 rounded rect with room around it for the blur to spread. */
private val GlowMask: ImageBitmap by lazy {
    val inner = 128f
    val pad = inner * GlowPadFraction
    val w = (inner + pad * 2).roundToInt()
    val h = (inner * 9f / 16f + pad * 2).roundToInt()
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        maskFilter = BlurMaskFilter(pad * 0.75f, BlurMaskFilter.Blur.NORMAL)
    }
    Canvas(bitmap).drawRoundRect(pad, pad, pad + inner, pad + inner * 9f / 16f, 12f, 12f, paint)
    bitmap.asImageBitmap()
}

/**
 * A pill (or circle) button: glass at rest, solid white with dark content when focused,
 * tvOS-style, so focus is obvious even with animations off.
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = PillShape,
    contentPadding: PaddingValues = PaddingValues(horizontal = 26.dp),
    content: @Composable RowScope.(focused: Boolean) -> Unit,
) {
    val motion = remember { FocusMotion() }
    val scope = rememberCoroutineScope()
    val animate = LocalAnimationsEnabled.current
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .onFocusChanged {
                focused = it.isFocused
                motion.onFocusChanged(it.isFocused, animate, scope)
            }
            .remoteClickable(onClick)
            .graphicsLayer {
                val s = 1f + 0.07f * motion.focus.value
                scaleX = s
                scaleY = s
            }
            .glass(
                shape,
                fill = if (focused) LauncherColors.TextPrimary else Glass.Fill,
                border = if (focused) Color.Transparent else Glass.Border,
            )
            .padding(contentPadding),
    ) {
        content(focused)
    }
}

/** Content colour for a [GlassButton]: dark on the white focused fill. */
fun buttonContentColor(focused: Boolean): Color = if (focused) LauncherColors.Ink else LauncherColors.TextPrimary
