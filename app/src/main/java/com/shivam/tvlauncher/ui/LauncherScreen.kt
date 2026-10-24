package com.shivam.tvlauncher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.shivam.tvlauncher.LaunchableApp
import com.shivam.tvlauncher.LauncherUi
import kotlin.math.roundToInt

/** Vertical room inside each row so a scaled card and its shadow aren't clipped. */
private val RowBleed = 20.dp
private val EdgeFade = 40.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherScreen(ui: LauncherUi?, onLaunch: (LaunchableApp) -> Unit) {
    val initialFocus = remember { FocusRequester() }
    val direction = remember { FocusDirection() }
    val scope = rememberCoroutineScope()
    val tint = remember { BackgroundTint(scope, LauncherColors.Ink) }
    val scrollMargin = with(LocalDensity.current) { SafeArea.Horizontal.toPx() }
    val scrollSpec = remember(scrollMargin) { ComfortableScrollSpec(scrollMargin) }

    CompositionLocalProvider(
        LocalFocusDirection provides direction,
        LocalBackgroundTint provides tint,
        LocalBringIntoViewSpec provides scrollSpec,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .launcherBackground(tint)
                .trackDirection(direction)
                .padding(vertical = SafeArea.Vertical),
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = SafeArea.Horizontal)) {
                Spacer(Modifier.weight(1f))
                Clock()
            }

            if (ui != null) {
                RowTitle("Pinned", Modifier.padding(top = 4.dp))
                if (ui.pinned.isEmpty()) {
                    EmptyPinnedHint()
                } else {
                    AppRow(ui.pinned, CardSize.Large, onLaunch, initialFocus)
                }

                RowTitle("All apps", Modifier.padding(top = 2.dp))
                AppRow(ui.all, CardSize.Small, onLaunch, initialFocus.takeIf { ui.pinned.isEmpty() })
            }
        }
    }

    // Start on the first card. Only keyed on "loaded" so a resume keeps the user's place.
    LaunchedEffect(ui != null) {
        if (ui != null) runCatching { initialFocus.requestFocus() }
    }
}

/** Records the last D-pad direction for the tilt; never consumes the event. */
private fun Modifier.trackDirection(direction: FocusDirection): Modifier = onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown) {
        when (event.key) {
            Key.DirectionLeft -> { direction.dx = -1; direction.dy = 0 }
            Key.DirectionRight -> { direction.dx = 1; direction.dy = 0 }
            Key.DirectionUp -> { direction.dx = 0; direction.dy = -1 }
            Key.DirectionDown -> { direction.dx = 0; direction.dy = 1 }
        }
    }
    false
}

@Composable
private fun RowTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = LauncherText.RowTitle,
        color = LauncherColors.TextSecondary,
        modifier = modifier.padding(horizontal = SafeArea.Horizontal),
    )
}

@Composable
private fun EmptyPinnedHint() {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(CardSize.Large.height + RowBleed * 2 + 28.dp)
            .padding(horizontal = SafeArea.Horizontal),
    ) {
        Text("Long-press OK on any app to pin it here.", style = LauncherText.Body, color = LauncherColors.TextSecondary)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AppRow(
    apps: List<LaunchableApp>,
    size: CardSize,
    onLaunch: (LaunchableApp) -> Unit,
    firstItemFocus: FocusRequester?,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = SafeArea.Horizontal, vertical = RowBleed),
        horizontalArrangement = Arrangement.spacedBy(size.gap),
        // Coming back to a row lands on the card you left, like tvOS.
        modifier = Modifier.fillMaxWidth().fadingHorizontalEdges(EdgeFade).focusRestorer(),
    ) {
        itemsIndexed(apps, key = { _, app -> app.key }) { index, app ->
            AppCard(
                app = app,
                size = size,
                onClick = { onLaunch(app) },
                modifier = if (index == 0 && firstItemFocus != null) Modifier.focusRequester(firstItemFocus) else Modifier,
            )
        }
    }
}

/** Fades the row content to transparent at the left/right edges of the screen. */
private fun Modifier.fadingHorizontalEdges(width: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val fraction = (width.toPx() / size.width).coerceIn(0f, 0.5f)
        val mask = Brush.horizontalGradient(
            0f to Color.Transparent,
            fraction to Color.Black,
            1f - fraction to Color.Black,
            1f to Color.Transparent,
        )
        onDrawWithContent {
            drawContent()
            drawRect(mask, blendMode = BlendMode.DstIn)
        }
    }

/**
 * The focused app's colour washing down from the top over the window's near-black background.
 *
 * Per-pixel gradient shaders across the full screen cost ~25ms/frame on Fire TV GPUs, so the
 * glow's shape is baked once into a tiny alpha mask, stretched with bilinear filtering and
 * tinted. The colour is read here, in the draw phase, so its cross-fade never recomposes.
 */
private fun Modifier.launcherBackground(tint: BackgroundTint): Modifier = drawWithCache {
    val dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
    onDrawBehind {
        drawImage(
            image = GlowMask,
            dstSize = dstSize,
            colorFilter = ColorFilter.tint(tint.color.value),
            filterQuality = FilterQuality.Low,
        )
    }
}

/** White glow on transparent: a top-down wash plus a soft bloom near the top-left. */
private val GlowMask: ImageBitmap by lazy {
    val w = 160
    val h = 90
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val white = android.graphics.Color.WHITE
    val clear = android.graphics.Color.TRANSPARENT
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(0f, 0f, 0f, h * 0.8f, white, clear, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    paint.shader = RadialGradient(w * 0.15f, 0f, w * 0.6f, 0x8CFFFFFF.toInt(), clear, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    bitmap.asImageBitmap()
}
