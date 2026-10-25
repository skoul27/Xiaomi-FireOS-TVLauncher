package com.shivam.tvlauncher.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.shivam.tvlauncher.AppArtCache
import com.shivam.tvlauncher.LaunchableApp
import com.shivam.tvlauncher.rememberAppDetails
import kotlinx.coroutines.delay

/** The app the hero shows: whichever tile last had focus. */
@Stable
class HeroState {
    var app by mutableStateOf<LaunchableApp?>(null)
}

/**
 * The focused app's name, large, over a line of facts the system reports about it (category,
 * last update, version). No descriptions, nothing from the network: the tile below already shows
 * the app's art, so the hero doesn't repeat it. No Open button either: OK on the tile opens it.
 */
@Composable
fun Hero(state: HeroState, modifier: Modifier = Modifier) {
    val app = state.app
    val animate = LocalAnimationsEnabled.current
    Column(verticalArrangement = Arrangement.Bottom, modifier = modifier) {
        Crossfade(targetState = app, animationSpec = motionTween(animate, 260), label = "hero") { shown ->
            Column {
                Text(
                    text = shown?.label.orEmpty(),
                    style = LauncherText.Display,
                    color = LauncherColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Narrow enough to clear the top bar, which this line can sit level with.
                    modifier = Modifier.widthIn(max = 480.dp),
                )
                Spacer(Modifier.height(6.dp))
                // Reserves its line even while loading, so the title never jumps.
                Text(
                    text = shown?.let { rememberAppDetails(it) }?.line.orEmpty(),
                    style = LauncherText.Body,
                    color = LauncherColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 760.dp),
                )
            }
        }
    }
}

/** Follows the hero's app with a short debounce, so skimming along a row doesn't strobe. */
@Composable
fun BackdropDriver(hero: HeroState, backdrop: BackdropState) {
    val context = LocalContext.current.applicationContext
    val animate = LocalAnimationsEnabled.current
    val app = hero.app ?: return
    LaunchedEffect(app.artKey, animate) {
        if (backdrop.current != null) delay(140)
        backdrop.show(AppArtCache.load(context, app), animate)
    }
}
