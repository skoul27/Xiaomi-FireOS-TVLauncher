package com.shivam.tvlauncher.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusProperties
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
 * last update, version), and an Open button. No descriptions, nothing from the network: the
 * tile below already shows the app's art, so the hero doesn't repeat it.
 */
@Composable
fun Hero(
    state: HeroState,
    onOpen: (LaunchableApp) -> Unit,
    openButtonModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    val app = state.app
    val animate = LocalAnimationsEnabled.current
    Column(verticalArrangement = Arrangement.Bottom, modifier = modifier) {
        Crossfade(targetState = app, animationSpec = motionTween(animate, 260), label = "hero") { shown ->
            Column {
                Text(
                    text = shown?.label.orEmpty(),
                    style = LauncherText.Hero,
                    color = LauncherColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 760.dp),
                )
                Spacer(Modifier.height(8.dp))
                // Reserves its line even while loading, so the button below never jumps.
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
        Spacer(Modifier.height(22.dp))
        // Always composed so focus has a stable target; just unreachable until there's an app.
        GlassButton(
            onClick = { app?.let(onOpen) },
            modifier = openButtonModifier
                .height(46.dp)
                .focusProperties { canFocus = app != null }
                .alpha(if (app == null) 0f else 1f),
        ) { focused ->
            PlayIcon(buttonContentColor(focused))
            Spacer(Modifier.width(10.dp))
            Text("Open", style = LauncherText.Button, color = buttonContentColor(focused))
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
