package com.shivam.tvlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class LaunchableApp(
    val label: String,
    val component: ComponentName,
    /** Category the activity was found under; used to build the launch intent. */
    val category: String,
    /** A 16:9 TV banner when the app provides one, otherwise null. */
    val banner: ImageBitmap?,
    val icon: ImageBitmap,
) {
    fun launchIntent(): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(category)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
}

/**
 * Collects apps from LEANBACK_LAUNCHER and LAUNCHER, one entry per package.
 * Leanback entries win so TV apps open their TV activity.
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
    val banner: Drawable? = activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm)
    return LaunchableApp(
        label = loadLabel(pm).toString(),
        component = ComponentName(activity.packageName, activity.name),
        category = category,
        banner = banner?.toImage(320, 180),
        icon = loadIcon(pm).toImage(128, 128),
    )
}

private fun Drawable.toImage(fallbackWidth: Int, fallbackHeight: Int): ImageBitmap {
    val w = if (intrinsicWidth > 0) intrinsicWidth else fallbackWidth
    val h = if (intrinsicHeight > 0) intrinsicHeight else fallbackHeight
    return toBitmap(w, h).asImageBitmap()
}
