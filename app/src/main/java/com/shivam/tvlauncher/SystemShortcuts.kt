package com.shivam.tvlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.runtime.Immutable

/** A Fire OS settings screen the launcher can open. Only ones that resolve on this device are offered. */
@Immutable
class SystemShortcut(val label: String, val intent: Intent)

private const val FireSettings = "com.amazon.tv.settings.v2"

/**
 * Candidate intents per entry, best first. Fire OS keeps its main Settings grid inside the stock
 * launcher, so "Fire OS Settings" disappears if that launcher is ever disabled; the screens below
 * it live in the separate settings app and keep working, including Developer Options (ADB) and
 * Restart under Device & Software.
 */
private val Candidates: List<Pair<String, List<Intent>>> = listOf(
    "Fire OS Settings" to listOf(Intent(Settings.ACTION_SETTINGS)),
    "Network" to listOf(Intent(Settings.ACTION_WIFI_SETTINGS)),
    // Explicit, because the generic action matches two apps here and would show a chooser.
    "Display & Sound" to listOf(
        Intent().setComponent(ComponentName(FireSettings, "$FireSettings.tv.display_sounds.DisplayAndSoundsActivity")),
        Intent(Settings.ACTION_DISPLAY_SETTINGS),
    ),
    "Applications" to listOf(Intent(Settings.ACTION_APPLICATION_SETTINGS)),
    "Device & Software" to listOf(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)),
)

/** Fire OS's screen mirroring receiver ("Enable Display Mirroring"), behind the top bar's Cast button. */
val CastScreenIntent: Intent = Intent().setComponent(
    ComponentName("com.amazon.cast.sink", "com.amazon.cast.sink.DisplayMirroringSinkActivity"),
)

/** Runs PackageManager queries; call off the main thread. */
fun availableSystemShortcuts(context: Context): List<SystemShortcut> =
    Candidates.mapNotNull { (label, intents) ->
        intents.firstOrNull { context.opensDirectly(it) }?.let { SystemShortcut(label, it) }
    }

/**
 * True if this app may start the intent's activity without the "choose an app" dialog. Several
 * Fire OS settings screens are exported but guarded by a signature permission
 * (com.amazon.tv.permission.LAUNCHER_SETTINGS) that adb's shell holds and apps don't.
 */
private fun Context.opensDirectly(intent: Intent): Boolean {
    val activity = runCatching { packageManager.resolveActivity(intent, 0)?.activityInfo }.getOrNull() ?: return false
    val permission = activity.permission
    return activity.exported &&
        activity.packageName != "android" &&
        (permission == null || checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED)
}
