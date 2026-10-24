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
import android.graphics.PorterDuff
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
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
    /** Dark, Palette-derived tint the background fades to while this card is focused. */
    val accent: Color,
)

const val CARD_ART_WIDTH = 448
const val CARD_ART_HEIGHT = 252

/**
 * Card art is stored pre-dimmed to its resting (unfocused) brightness, so the many resting cards
 * draw a plain bitmap; only a focused card pays for a filter that brightens it back to 1.0.
 */
const val ART_RESTING_BRIGHTNESS = 0.7f

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
    val letterVisual = CardVisual(image = null, placeholder = placeholder, accent = placeholder.withLightness(0.2f))

    return runCatching {
        val banner = activity.loadBanner(pm) ?: activity.applicationInfo.loadBanner(pm)
        when {
            banner != null -> {
                val art = renderBanner(banner)
                val accent = dominantHsl(art).toAccent()
                CardVisual(art.bakeRestingDim().asImageBitmap(), placeholder, accent)
            }
            activity.iconResource != 0 -> {
                val icon = loadIcon(pm)
                // Near-grey icons (white glyphs, monochrome logos) would give a dull grey panel,
                // so they borrow the label-derived hue instead.
                val hsl = dominantHsl(icon.toBitmap(96, 96))?.takeIf { it[1] >= 0.15f }
                val panel = hsl?.let { Color.hsl(it[0], min(it[1], 0.45f), 0.30f) } ?: placeholder
                val accent = if (hsl != null) hsl.toAccent() else placeholder.withLightness(0.2f)
                CardVisual(renderIcon(icon, panel).bakeRestingDim().asImageBitmap(), placeholder, accent)
            }
            else -> letterVisual
        }
    }.getOrDefault(letterVisual)
}

private fun dominantHsl(bitmap: Bitmap): FloatArray? {
    val palette = Palette.from(bitmap).maximumColorCount(16).generate()
    val swatch = palette.vibrantSwatch ?: palette.darkVibrantSwatch ?: palette.mutedSwatch ?: palette.dominantSwatch
    return swatch?.hsl
}

/** Keeps the hue but forces it dark and calm enough to sit behind white text. */
private fun FloatArray?.toAccent(): Color =
    if (this == null) DefaultAccent else Color.hsl(this[0], min(this[1], 0.6f), this[2].coerceIn(0.16f, 0.24f))

private fun Color.withLightness(lightness: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    return Color.hsl(hsl[0], hsl[1], lightness)
}

private val DefaultAccent = Color(0xFF1A2030)

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

private fun Bitmap.bakeRestingDim(): Bitmap = apply {
    val black = android.graphics.Color.argb(((1f - ART_RESTING_BRIGHTNESS) * 255).roundToInt(), 0, 0, 0)
    Canvas(this).drawColor(black, PorterDuff.Mode.SRC_ATOP)
}

private fun placeholderColor(label: String): Color =
    Color.hsl(hue = (abs(label.hashCode()) % 360).toFloat(), saturation = 0.28f, lightness = 0.30f)
