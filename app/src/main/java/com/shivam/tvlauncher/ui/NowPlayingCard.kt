package com.shivam.tvlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

private val CardShape = RoundedCornerShape(Radii.Item + 4.dp)
private val ArtShape = RoundedCornerShape(10.dp)

/**
 * Artwork, title and app, with play/pause beside it. OK on the card goes back into the app;
 * [bodyFocus] is the card itself, for the top bar to move down to.
 */
@Composable
fun NowPlayingCard(now: NowPlaying, bodyFocus: FocusRequester, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val motion = remember { FocusMotion() }
    val scope = rememberCoroutineScope()
    val animate = LocalAnimationsEnabled.current
    var focused by remember { mutableStateOf(false) }
    val art = remember(now.art) { now.art?.asImageBitmap() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .width(340.dp)
                .height(80.dp)
                .focusRequester(bodyFocus)
                .onFocusChanged {
                    focused = it.isFocused
                    motion.onFocusChanged(it.isFocused, animate, scope)
                }
                .remoteClickable({ now.open(context) })
                .graphicsLayer {
                    val s = 1f + 0.05f * motion.focus.value
                    scaleX = s
                    scaleY = s
                }
                .glass(
                    CardShape,
                    fill = if (focused) Glass.FillFocused else Glass.Fill,
                    border = if (focused) Glass.BorderFocused else Glass.Border,
                )
                .padding(10.dp),
        ) {
            if (art != null) {
                Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(60.dp).clip(ArtShape))
            } else {
                Box(Modifier.size(60.dp).background(Glass.TileBase, ArtShape))
            }
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = (if (now.playing) "Now playing · " else "Paused · ") + now.appName,
                    style = LauncherText.Caption,
                    color = if (now.playing) accent else LauncherColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(now.title, style = LauncherText.Button, color = LauncherColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                now.subtitle?.let {
                    Text(it, style = LauncherText.Caption, color = LauncherColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        RoundIconButton({ now.playPause() }) { color ->
            if (now.playing) PauseIcon(color, size = 18.dp) else PlayIcon(color, size = 18.dp, modifier = Modifier.padding(start = 2.dp))
        }
    }
}
