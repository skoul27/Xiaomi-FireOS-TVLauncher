package com.shivam.tvlauncher.ui

import android.text.format.DateFormat
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.shivam.tvlauncher.NetworkKind
import com.shivam.tvlauncher.NetworkStatus
import com.shivam.tvlauncher.networkStatusFlow
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Time and date, then the round buttons ([buttons]), top-right. */
@Composable
fun TopBar(buttons: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = modifier.fillMaxWidth().height(TopBarHeight),
    ) {
        Clock()
        Spacer(Modifier.width(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { buttons() }
    }
}

val TopBarHeight = 52.dp

/** A round glass button holding one centred icon, drawn in the colour it's given. */
@Composable
fun RoundIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, icon: @Composable (Color) -> Unit) {
    GlassButton(
        onClick = onClick,
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        modifier = modifier.size(RoundButtonSize),
    ) { focused ->
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            icon(buttonContentColor(focused))
        }
    }
}

val RoundButtonSize = 48.dp

/** Wi-Fi bars or Ethernet as the network reports it; faint with no internet, crossed out offline. */
@Composable
fun NetworkButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val flow = remember(context) { networkStatusFlow(context) }
    val status by flow.collectAsStateWithLifecycle(NetworkStatus(NetworkKind.None))
    RoundIconButton(onClick, modifier) { color ->
        val shown = if (status.kind != NetworkKind.None && !status.online) color.copy(alpha = 0.5f) else color
        when (status.kind) {
            NetworkKind.Ethernet -> EthernetIcon(shown, size = 22.dp)
            NetworkKind.None -> WifiIcon(shown, level = -1, size = 22.dp)
            else -> WifiIcon(shown, level = status.level, size = 22.dp)
        }
    }
}

/** Only this composable recomposes when the minute ticks. */
@Composable
private fun Clock() {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000 + 50)
        }
    }

    val locale = Locale.getDefault()
    val is24h = DateFormat.is24HourFormat(context)
    val timeFormat = remember(is24h, locale) { SimpleDateFormat(if (is24h) "HH:mm" else "h:mm", locale) }
    val dateFormat = remember(locale) {
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEMMMd"), locale)
    }
    val date = Date(now)

    Column(horizontalAlignment = Alignment.End) {
        Text(timeFormat.format(date), style = LauncherText.Time, color = LauncherColors.TextPrimary)
        Text(dateFormat.format(date), style = LauncherText.Date, color = LauncherColors.TextSecondary)
    }
}
