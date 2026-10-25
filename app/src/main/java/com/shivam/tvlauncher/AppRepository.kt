package com.shivam.tvlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.compose.runtime.Immutable

@Immutable
data class LaunchableApp(
    val packageName: String,
    val label: String,
    val component: ComponentName,
    /** Category the activity was found under; used to build the launch intent. */
    val category: String,
    val isSystem: Boolean,
    /** Changes whenever the app is reinstalled or updated, so cached art is refreshed. */
    val artKey: String,
) {
    val key: String get() = packageName

    fun launchIntent(): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(category)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
}

/**
 * Collects apps from LEANBACK_LAUNCHER and LAUNCHER, one entry per package.
 * Leanback entries win so TV apps open their TV activity. Labels only: art loads lazily.
 */
fun loadLaunchableApps(context: Context): List<LaunchableApp> {
    val pm = context.packageManager
    val byPackage = LinkedHashMap<String, LaunchableApp>()

    for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        for (info in pm.queryIntentActivities(intent, 0)) {
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName || pkg in byPackage) continue
            byPackage[pkg] = info.toLaunchableApp(pm, category)
        }
    }

    return byPackage.values.sortedBy { it.label.lowercase() }
}

private fun ResolveInfo.toLaunchableApp(pm: PackageManager, category: String): LaunchableApp {
    val activity = activityInfo
    val component = ComponentName(activity.packageName, activity.name)
    return LaunchableApp(
        packageName = activity.packageName,
        label = loadLabel(pm).toString(),
        component = component,
        category = category,
        isSystem = activity.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
        artKey = "${component.flattenToString()}@${activity.applicationInfo.sourceDir}",
    )
}

/**
 * Streaming apps pinned to My Apps on first run, if installed. Each entry lists the package
 * names one service ships under on different devices; the first installed one is used.
 */
val DefaultPins: List<List<String>> = listOf(
    listOf("com.netflix.ninja", "com.netflix.mediaclient"),
    listOf("com.amazon.firebat", "com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"),
    listOf("com.amazon.firetv.youtube", "com.google.android.youtube.tv", "com.google.android.youtube"),
    listOf("in.startv.hotstar", "in.startv.hotstar.dplus"),
    listOf("com.apple.atve.amazon.appletv", "com.apple.atve.androidtv.appletv"),
    listOf("com.zee5.amazon", "com.graymatrix.did"),
    listOf("com.sonyliv", "com.sonyliv.firetv"),
    listOf("com.spotify.tv.android"),
)

fun defaultPinsFor(installed: Collection<String>): List<String> =
    DefaultPins.mapNotNull { service -> service.firstOrNull { it in installed } }
