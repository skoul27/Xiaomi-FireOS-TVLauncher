package com.shivam.tvlauncher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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

/** Network status, time and date, and the settings button, top-right. */
@Composable
fun TopBar(settingsButton: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = modifier.fillMaxWidth().height(TopBarHeight),
    ) {
        NetworkPill()
        Spacer(Modifier.width(22.dp))
        Clock()
        Spacer(Modifier.width(22.dp))
        settingsButton()
    }
}

val TopBarHeight = 52.dp

@Composable
fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassButton(
        onClick = onClick,
        shape = CircleShape,
        contentPadding = PaddingValues(13.dp),
        modifier = modifier.size(48.dp),
    ) { focused ->
        GearIcon(buttonContentColor(focused))
    }
}

@Composable
private fun NetworkPill() {
    val context = LocalContext.current.applicationContext
    val flow = remember(context) { networkStatusFlow(context) }
    val status by flow.collectAsStateWithLifecycle(NetworkStatus(NetworkKind.None))
    val color = LauncherColors.TextPrimary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(36.dp).glass(PillShape).padding(horizontal = 14.dp),
    ) {
        when (status.kind) {
            NetworkKind.Ethernet -> EthernetIcon(color)
            NetworkKind.None -> WifiIcon(color, level = -1)
            else -> WifiIcon(color, level = status.level)
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text = when {
                status.kind == NetworkKind.None -> "Offline"
                !status.online -> "No internet"
                status.kind == NetworkKind.Ethernet -> "Ethernet"
                status.kind == NetworkKind.Wifi -> "Wi-Fi"
                else -> "Connected"
            },
            style = LauncherText.Caption,
            color = if (status.kind == NetworkKind.None || !status.online) LauncherColors.TextSecondary else color,
        )
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
