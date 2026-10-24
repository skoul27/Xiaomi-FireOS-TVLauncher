package com.shivam.tvlauncher.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.shivam.tvlauncher.LaunchableApp
import com.shivam.tvlauncher.LauncherUi

/** Vertical room inside each row so a scaled card and its shadow aren't clipped. */
private val RowBleed = 20.dp
private val EdgeFade = 40.dp

@Composable
fun LauncherScreen(ui: LauncherUi?, onLaunch: (LaunchableApp) -> Unit) {
    val initialFocus = remember { FocusRequester() }

    Box(modifier = Modifier.fillMaxSize().launcherBackground()) {
        Column(modifier = Modifier.fillMaxSize().padding(vertical = SafeArea.Vertical)) {
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

private fun Modifier.launcherBackground(): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1A2030), LauncherColors.Ink)))
}
