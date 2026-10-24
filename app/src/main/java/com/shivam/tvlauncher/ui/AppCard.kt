package com.shivam.tvlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.shivam.tvlauncher.ART_RESTING_BRIGHTNESS
import com.shivam.tvlauncher.LaunchableApp
import kotlin.math.roundToInt

enum class CardSize(val width: Dp, val gap: Dp, val letterSize: TextUnit, val alwaysShowLabel: Boolean) {
    Large(width = 224.dp, gap = 28.dp, letterSize = 52.sp, alwaysShowLabel = true),
    Small(width = 160.dp, gap = 22.dp, letterSize = 38.sp, alwaysShowLabel = false);

    val height: Dp get() = width * 9f / 16f
}

private const val GlareAlpha = 0.22f
private val RestElevation = 2.dp
private val FocusedElevation = 22.dp

@Composable
fun AppCard(
    app: LaunchableApp,
    size: CardSize,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = remember { CardMotion() }
    val scope = rememberCoroutineScope()
    val direction = LocalFocusDirection.current
    val tint = LocalBackgroundTint.current

    Column(modifier = modifier.width(size.width)) {
        Box(
            modifier = Modifier
                .size(size.width, size.height)
                .onFocusChanged {
                    motion.onFocusChanged(it.isFocused, direction, scope)
                    if (it.isFocused) tint?.show(app.visual.accent)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .graphicsLayer {
                    val f = motion.focus.value
                    val scale = 1f + (FocusedScale - 1f) * f
                    scaleX = scale
                    scaleY = scale
                    rotationX = motion.tiltX.value
                    rotationY = motion.tiltY.value
                    cameraDistance = 14f * density
                    shadowElevation = (RestElevation + (FocusedElevation - RestElevation) * f.coerceIn(0f, 1f)).toPx()
                    shape = CardShape
                    clip = true
                }
                .cardSurface(app, motion),
        ) {
            if (app.visual.image == null) LetterPlaceholder(app.label, app.visual.placeholder, size.letterSize)
        }
        Text(
            text = app.label,
            style = if (size == CardSize.Large) LauncherText.LabelLarge else LauncherText.LabelSmall,
            color = LauncherColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 10.dp, start = 2.dp, end = 2.dp)
                .graphicsLayer {
                    val f = motion.focus.value
                    // Ride below the scaled card instead of being covered by it.
                    translationY = (FocusedScale - 1f) * f * size.height.toPx() / 2f
                    alpha = if (size.alwaysShowLabel) 0.62f + 0.38f * f.coerceIn(0f, 1f) else f.coerceIn(0f, 1f)
                    // Text never overlaps itself, so per-op alpha looks identical and skips an offscreen buffer.
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
        )
    }
}

/**
 * Draws the card art with a soft diagonal glare when focused. Art is cached pre-dimmed (see
 * ART_RESTING_BRIGHTNESS), so resting cards are a plain bitmap draw and only an animating card
 * gets a brightening colour matrix. That matters because every card in a row is redrawn while
 * any one of them animates. Letter placeholders, which are rare, use an overlay instead.
 */
private fun Modifier.cardSurface(app: LaunchableApp, motion: CardMotion): Modifier = drawWithCache {
    val image = app.visual.image
    val dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
    val glare = Brush.linearGradient(
        0f to Color.White,
        0.35f to Color.White.copy(alpha = 0.25f),
        0.65f to Color.Transparent,
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )
    onDrawWithContent {
        val f = motion.focus.value.coerceIn(0f, 1f)
        if (image != null) {
            val filter = if (f > 0f) {
                val gain = 1f + (1f / ART_RESTING_BRIGHTNESS - 1f) * f
                ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(gain, gain, gain, 1f) })
            } else {
                null
            }
            drawImage(image, dstSize = dstSize, colorFilter = filter, filterQuality = FilterQuality.Low)
        } else {
            drawContent()
            if (f < 1f) drawRect(Color.Black, alpha = (1f - ART_RESTING_BRIGHTNESS) * (1f - f))
        }
        if (f > 0f) {
            // The highlight slides a little with the tilt, like light catching a surface.
            translate(left = motion.tiltY.value * size.width * 0.02f, top = -motion.tiltX.value * size.height * 0.02f) {
                drawRect(glare, alpha = GlareAlpha * f)
            }
        }
    }
}

@Composable
private fun LetterPlaceholder(label: String, color: Color, letterSize: TextUnit, modifier: Modifier = Modifier) {
    val letter = remember(label) {
        label.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    }
    val brush = remember(color) {
        Brush.linearGradient(listOf(color, Color(color.red * 0.5f, color.green * 0.5f, color.blue * 0.5f)))
    }
    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize().background(brush)) {
        Text(letter, style = LauncherText.Placeholder.copy(fontSize = letterSize), color = Color.White.copy(alpha = 0.92f))
    }
}
