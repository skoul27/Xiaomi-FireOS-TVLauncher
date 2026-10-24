package com.shivam.tvlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Immutable
data class LaunchableApp(
    val packageName: String,
    val label: String,
    val component: ComponentName,
    /** Category the activity was found under; used to build the launch intent. */
    val category: String,
    val isSystem: Boolean,
    val visual: CardVisual,
) {
    val key: String get() = packageName

    val isTvApp: Boolean get() = category == Intent.CATEGORY_LEANBACK_LAUNCHER

    fun launchIntent(): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(category)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
}

/**
 * Pre-rendered card art. [image] is always exactly 16:9 so every card crops the same way;
 * it is null when the app has neither banner nor icon, and the UI draws a letter instead.
 */
@Immutable
class CardVisual(
    val image: ImageBitmap?,
    /** Colour for the letter placeholder, derived from the label so it's stable. */
    val placeholder: Color,
)

const val CARD_ART_WIDTH = 448
const val CARD_ART_HEIGHT = 252

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
    val label = loadLabel(pm).toString()
    val component = ComponentName(activity.packageName, activity.name)
    // sourceDir changes whenever the app is reinstalled or updated, so it doubles as a version.
    val cacheKey = "${component.flattenToString()}@${activity.applicationInfo.sourceDir}"
    return LaunchableApp(
        packageName = activity.packageName,
        label = label,
        component = component,
        category = category,
        isSystem = activity.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
        visual = CardVisualCache.getOrPut(cacheKey) { renderVisual(pm, label) },
    )
}

/** Process-wide cache so returning to the launcher never re-decodes art. */
private object CardVisualCache {
    private val entries = HashMap<String, CardVisual>()

    @Synchronized
    fun getOrPut(key: String, create: () -> CardVisual): CardVisual = entries.getOrPut(key, create)
}

private fun ResolveInfo.renderVisual(pm: PackageManager, label: String): CardVisual {
    val activity = activityInfo
    val placeholder = placeholderColor(label)
    val image = runCatching {
        val banner = activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm)
        when {
            banner != null -> renderBanner(banner)
            activity.iconResource != 0 -> renderIcon(loadIcon(pm), placeholder)
            else -> null
        }
    }.getOrNull()
    return CardVisual(image = image?.asImageBitmap(), placeholder = placeholder)
}

/** Centre-crops the banner into the fixed card frame. */
private fun renderBanner(banner: Drawable): Bitmap {
    val out = Bitmap.createBitmap(CARD_ART_WIDTH, CARD_ART_HEIGHT, Bitmap.Config.ARGB_8888)
    val srcW = banner.intrinsicWidth.takeIf { it > 0 } ?: CARD_ART_WIDTH
    val srcH = banner.intrinsicHeight.takeIf { it > 0 } ?: CARD_ART_HEIGHT
    val scale = max(CARD_ART_WIDTH / srcW.toFloat(), CARD_ART_HEIGHT / srcH.toFloat())
    val w = (srcW * scale).roundToInt()
    val h = (srcH * scale).roundToInt()
    val left = (CARD_ART_WIDTH - w) / 2
    val top = (CARD_ART_HEIGHT - h) / 2
    banner.setBounds(left, top, left + w, top + h)
    banner.draw(Canvas(out))
    return out
}

/** Phone-style apps: the icon centred on a soft tinted panel, in the same 16:9 frame. */
private fun renderIcon(icon: Drawable, tint: Color): Bitmap {
    val out = Bitmap.createBitmap(CARD_ART_WIDTH, CARD_ART_HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val top = tint.toArgb()
    val bottom = Color(
        red = tint.red * 0.55f,
        green = tint.green * 0.55f,
        blue = tint.blue * 0.55f,
    ).toArgb()
    val paint = Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, CARD_ART_HEIGHT.toFloat(), top, bottom, Shader.TileMode.CLAMP)
    }
    canvas.drawRect(0f, 0f, CARD_ART_WIDTH.toFloat(), CARD_ART_HEIGHT.toFloat(), paint)
    val size = (CARD_ART_HEIGHT * 0.52f).roundToInt()
    val left = (CARD_ART_WIDTH - size) / 2
    val iconTop = (CARD_ART_HEIGHT - size) / 2
    icon.setBounds(left, iconTop, left + size, iconTop + size)
    icon.draw(canvas)
    return out
}

private fun placeholderColor(label: String): Color =
    Color.hsl(hue = (abs(label.hashCode()) % 360).toFloat(), saturation = 0.28f, lightness = 0.30f)
