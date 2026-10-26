package com.shivam.tvlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.shivam.tvlauncher.NowPlaying
import com.shivam.tvlauncher.nowPlayingFlow

/** What another app is playing, kept current while the home screen is in front. */
@Composable
fun rememberNowPlaying(): NowPlaying? {
    val context = LocalContext.current.applicationContext
    val flow = remember(context) { nowPlayingFlow(context) }
    return flow.collectAsStateWithLifecycle(null).value
}

/**
 * A slim pill the height of the top bar's buttons: round artwork, the title, and the app and
 * artist under it, with play/pause beside it. It focuses like the round buttons (solid white).
 * OK on the pill goes back into the app; [bodyFocus] is the pill itself.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NowPlayingCard(now: NowPlaying, bodyFocus: FocusRequester, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val art = remember(now.art) { now.art?.asImageBitmap() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        GlassButton(
            onClick = { now.open(context) },
            contentPadding = PaddingValues(start = 6.dp, end = 20.dp),
            modifier = Modifier
                .height(RoundButtonSize)
                .widthIn(max = 340.dp)
                .focusRequester(bodyFocus)
                .focusProperties { left = FocusRequester.Cancel },
        ) { focused ->
            val color = buttonContentColor(focused)
            if (art != null) {
                Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(36.dp).clip(CircleShape))
            } else {
                Box(Modifier.size(36.dp).background(Glass.TileBase, CircleShape))
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.Center, modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    now.title,
                    style = LauncherText.Label.copy(fontSize = 14.sp, lineHeight = 17.sp),
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Accent dot while playing, so "playing" reads at a glance without a label.
                    if (now.playing) {
                        Box(Modifier.size(6.dp).background(if (focused) color else accent, CircleShape))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        listOfNotNull(now.appName, now.subtitle).joinToString(" · "),
                        style = LauncherText.Caption.copy(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Normal),
                        color = color.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        RoundIconButton({ now.playPause() }) { color ->
            if (now.playing) PauseIcon(color, size = 16.dp) else PlayIcon(color, size = 16.dp, modifier = Modifier.padding(start = 2.dp))
        }
    }
}
