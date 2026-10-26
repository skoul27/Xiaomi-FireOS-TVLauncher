package com.shivam.tvlauncher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shivam.tvlauncher.NetworkKind
import com.shivam.tvlauncher.NetworkStatus
import com.shivam.tvlauncher.networkStatusFlow

/** The round buttons ([buttons]), top-right. */
@Composable
fun TopBar(buttons: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = modifier.fillMaxWidth().height(TopBarHeight),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { buttons() }
    }
}

val TopBarHeight = 52.dp

/**
 * A glass button holding one centred icon, drawn in the colour it's given. Round by default (the
 * Now playing controls); the top bar passes [TopBarButtonShape].
 */
@Composable
fun RoundIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, shape: Shape = CircleShape, icon: @Composable (Color) -> Unit) {
    GlassButton(
        onClick = onClick,
        shape = shape,
        contentPadding = PaddingValues(0.dp),
        modifier = modifier.size(RoundButtonSize),
    ) { focused ->
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            icon(buttonContentColor(focused))
        }
    }
}

val RoundButtonSize = 42.dp

/** The top bar's buttons and the Now playing pill: rounded squares, like the panels' icon tiles. */
val TopBarButtonShape = RoundedCornerShape(12.dp)

/** Wi-Fi bars or Ethernet as the network reports it; faint with no internet, crossed out offline. */
@Composable
fun NetworkButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val flow = remember(context) { networkStatusFlow(context) }
    val status by flow.collectAsStateWithLifecycle(NetworkStatus(NetworkKind.None))
    RoundIconButton(onClick, modifier, TopBarButtonShape) { color ->
        val shown = if (status.kind != NetworkKind.None && !status.online) color.copy(alpha = 0.5f) else color
        when (status.kind) {
            NetworkKind.Ethernet -> EthernetIcon(shown, size = 19.dp)
            NetworkKind.None -> WifiIcon(shown, level = -1, size = 19.dp)
            else -> WifiIcon(shown, level = status.level, size = 19.dp)
        }
    }
}
