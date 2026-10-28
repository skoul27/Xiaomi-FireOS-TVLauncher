package com.shivam.tvlauncher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.shivam.tvlauncher.AppArtCache
import com.shivam.tvlauncher.LaunchableApp
import com.shivam.tvlauncher.rememberWeather
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The app whose art tints the backdrop: whichever tile last had focus. */
@Stable
class HeroState {
    var app by mutableStateOf<LaunchableApp?>(null)
}

/**
 * The time, top-left and one top bar high, with the date and (once it loads) the weather stacked
 * beside it after a hairline. The focused app only tints the backdrop; its name is already under
 * its tile. Only this recomposes when the minute ticks.
 */
@Composable
fun Hero(modifier: Modifier = Modifier) {
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
    // Short names ("Wed, 28 Oct"): the long ones ran under the centred Now playing pill.
    val dateFormat = remember(locale) { SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale) }
    val date = Date(now)
    val weather = rememberWeather()

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.height(TopBarHeight)) {
        Text(timeFormat.format(date), style = LauncherText.Clock, color = LauncherColors.TextPrimary)
        Spacer(Modifier.width(18.dp))
        Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 8.dp).background(LauncherColors.TextTertiary))
        Spacer(Modifier.width(18.dp))
        Column(verticalArrangement = Arrangement.Center) {
            Text(dateFormat.format(date), style = LauncherText.ClockDetail, color = LauncherColors.TextPrimary)
            if (weather != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeatherIcon(weather.kind, weather.isDay, LauncherColors.TextSecondary, size = 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${weather.temperature}°" + if (weather.summary.isNotEmpty()) "  ${weather.summary}" else "",
                        style = LauncherText.ClockDetail,
                        color = LauncherColors.TextSecondary,
                    )
                }
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
