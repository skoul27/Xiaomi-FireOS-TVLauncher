package com.shivam.tvlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.PathParser
import androidx.palette.graphics.Palette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Everything the UI draws for one app, rendered once off the main thread and cached. */
@Immutable
class AppArt(
    /** Exactly 16:9: the app's banner, or its icon on a tinted panel, or its initial. */
    val tile: ImageBitmap,
    /** The app's own icon, square; null if it has none. */
    val icon: ImageBitmap?,
    /**
     * The whole hero background, opaque and pre-composited: charcoal, Palette blooms, a blurred
     * copy of [tile] and the readability scrim. Stretched full screen it is one bitmap draw;
     * layering those live cost the Fire TV GPU ~20ms a frame.
     */
    val backdrop: ImageBitmap,
)

const val TILE_ART_WIDTH = 400
const val TILE_ART_HEIGHT = 225
private const val ICON_SIZE = 160
private const val BLUR_WIDTH = 48
private const val BLUR_HEIGHT = 27
private const val BACKDROP_WIDTH = 192
private const val BACKDROP_HEIGHT = 108
private const val INK = 0xFF0C0D10.toInt()

/** Process-wide, so coming back to the launcher never decodes art again. */
object AppArtCache {
    private val ready = ConcurrentHashMap<String, AppArt>()
    private val pending = HashMap<String, Deferred<AppArt>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun peek(app: LaunchableApp): AppArt? = ready[app.artKey]

    suspend fun load(context: Context, app: LaunchableApp): AppArt {
        ready[app.artKey]?.let { return it }
        val job = synchronized(pending) {
            pending.getOrPut(app.artKey) {
                scope.async {
                    renderArt(context.applicationContext, app).also {
                        ready[app.artKey] = it
                        synchronized(pending) { pending.remove(app.artKey) }
                    }
                }
            }
        }
        return job.await()
    }

    /** Warms the cache in the given order, so the first screen's tiles are ready first. */
    fun prefetch(context: Context, apps: List<LaunchableApp>) {
        for (app in apps) if (peek(app) == null) scope.launch { load(context, app) }
    }
}

/** The app's art, or null for the few frames until it is rendered. */
@Composable
fun rememberAppArt(app: LaunchableApp): AppArt? {
    val context = LocalContext.current.applicationContext
    return produceState(AppArtCache.peek(app), app.artKey) {
        // Reset for the new key first: produceState keeps the previous app's value otherwise.
        value = AppArtCache.peek(app)
        if (value == null) value = AppArtCache.load(context, app)
    }.value
}

private fun renderArt(context: Context, app: LaunchableApp): AppArt {
    val pm = context.packageManager
    val info = runCatching { pm.getActivityInfo(app.component, 0) }.getOrNull()
    val isCast = app.packageName == CAST_SCREEN_PACKAGE
    val banner = info?.takeUnless { isCast }?.let { runCatching { it.loadBanner(pm) ?: it.applicationInfo.loadBanner(pm) }.getOrNull() }
    val icon = if (isCast) {
        renderCastIcon(ICON_SIZE)
    } else {
        info?.let { runCatching { renderSquare(it.loadIcon(pm), ICON_SIZE) }.getOrNull() }
    }
    val bannerBitmap = banner?.let { runCatching { renderBanner(it) }.getOrNull() }
    val colors = artColors(bannerBitmap ?: icon, labelHue(app.label))
    val tile = bannerBitmap
        ?: icon?.let { renderIconTile(it, colors) }
        ?: renderLetterTile(context, app.label, colors)
    return AppArt(
        tile = tile.asImageBitmap(),
        icon = icon?.asImageBitmap(),
        backdrop = renderBackdrop(tile.blurredThumbnail(), colors).asImageBitmap(),
    )
}

/** The hero backdrop, low-res: everything in it is soft, so bilinear upscaling hides the size. */
private fun renderBackdrop(blurred: Bitmap, colors: ArtColors): Bitmap {
    val w = BACKDROP_WIDTH.toFloat()
    val h = BACKDROP_HEIGHT.toFloat()
    val out = Bitmap.createBitmap(BACKDROP_WIDTH, BACKDROP_HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawColor(INK)

    // Palette blooms: primary from the top-right, where the hero has no text; secondary under the row.
    paint.shader = RadialGradient(w * 0.78f, h * 0.05f, w * 0.75f, colors.primary.withAlpha(0.85f), 0, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, w, h, paint)
    paint.shader = RadialGradient(w * 0.2f, h * 1.05f, w * 0.8f, colors.secondary.withAlpha(0.65f), 0, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, w, h, paint)
    paint.shader = null

    // Bright art (white banners) is toned down so it doesn't wash the screen out.
    paint.alpha = ((0.8f - 0.6f * blurred.meanLuminance()).coerceIn(0.3f, 0.7f) * 255).roundToInt()
    canvas.drawBitmap(blurred, null, android.graphics.RectF(0f, 0f, w, h), paint)
    paint.alpha = 255

    // Charcoal scrim: darkest behind the hero text (left) and the rows (bottom).
    canvas.drawColor(INK and 0x40FFFFFF)
    paint.shader = LinearGradient(0f, 0f, w * 0.72f, 0f, INK and 0xC7FFFFFF.toInt(), INK and 0x00FFFFFF, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, w, h, paint)
    paint.shader = LinearGradient(0f, h, 0f, h * 0.4f, INK and 0xE6FFFFFF.toInt(), INK and 0x00FFFFFF, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, w, h, paint)
    return out
}

private fun Color.withAlpha(alpha: Float): Int = copy(alpha = alpha).toArgb()

private class ArtColors(val primary: Color, val secondary: Color)

/** Near-grey swatches (white logos on black) are ignored in favour of a label-derived hue. */
private fun artColors(bitmap: Bitmap?, fallbackHue: Float): ArtColors {
    val palette = bitmap?.let { Palette.from(it).maximumColorCount(16).generate() }
    val first = palette?.let { it.vibrantSwatch ?: it.dominantSwatch }?.hsl?.takeIf { it[1] >= 0.18f }
    val second = palette?.let { it.darkVibrantSwatch ?: it.mutedSwatch ?: it.darkMutedSwatch }?.hsl?.takeIf { it[1] >= 0.15f }
    val hue = first?.get(0) ?: fallbackHue
    val primary = Color.hsl(hue, min(first?.get(1) ?: 0.45f, 0.7f), 0.42f)
    val secondary = if (second != null) {
        Color.hsl(second[0], min(second[1], 0.6f), 0.26f)
    } else {
        Color.hsl((hue + 40f) % 360f, 0.45f, 0.24f)
    }
    return ArtColors(primary, secondary)
}

private fun labelHue(label: String): Float = (abs(label.hashCode()) % 360).toFloat()

/** Centre-crops the banner into the fixed 16:9 frame. */
private fun renderBanner(banner: Drawable): Bitmap {
    val out = Bitmap.createBitmap(TILE_ART_WIDTH, TILE_ART_HEIGHT, Bitmap.Config.ARGB_8888)
    val srcW = banner.intrinsicWidth.takeIf { it > 0 } ?: TILE_ART_WIDTH
    val srcH = banner.intrinsicHeight.takeIf { it > 0 } ?: TILE_ART_HEIGHT
    val scale = max(TILE_ART_WIDTH / srcW.toFloat(), TILE_ART_HEIGHT / srcH.toFloat())
    val w = (srcW * scale).roundToInt()
    val h = (srcH * scale).roundToInt()
    val left = (TILE_ART_WIDTH - w) / 2
    val top = (TILE_ART_HEIGHT - h) / 2
    val canvas = Canvas(out)
    // Banners with transparent corners would otherwise show the backdrop through the tile.
    canvas.drawColor(0xFF1A1C22.toInt())
    banner.setBounds(left, top, left + w, top + h)
    banner.draw(canvas)
    return out
}

/** Fits the drawable, keeping its aspect ratio, into a transparent square. */
private fun renderSquare(drawable: Drawable, size: Int): Bitmap {
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val srcW = drawable.intrinsicWidth.takeIf { it > 0 } ?: size
    val srcH = drawable.intrinsicHeight.takeIf { it > 0 } ?: size
    val scale = min(size / srcW.toFloat(), size / srcH.toFloat())
    val w = (srcW * scale).roundToInt()
    val h = (srcH * scale).roundToInt()
    drawable.setBounds((size - w) / 2, (size - h) / 2, (size - w) / 2 + w, (size - h) / 2 + h)
    drawable.draw(Canvas(out))
    return out
}

/**
 * Phone-style apps: the icon on a two-tone panel of its own colours, lifted by a soft halo and
 * shadow, in the same 16:9 frame as banners so a row of mixed apps still reads as one set.
 */
private fun renderIconTile(icon: Bitmap, colors: ArtColors): Bitmap {
    val out = panel(colors)
    val canvas = Canvas(out)
    val size = (TILE_ART_HEIGHT * 0.58f).roundToInt()
    val left = (TILE_ART_WIDTH - size) / 2f
    val top = (TILE_ART_HEIGHT - size) / 2f
    val cx = TILE_ART_WIDTH / 2f
    val cy = TILE_ART_HEIGHT / 2f
    val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(cx, cy, size * 1.1f, colors.primary.withAlpha(0.55f), 0, Shader.TileMode.CLAMP)
    }
    canvas.drawCircle(cx, cy, size * 1.1f, halo)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        setShadowLayer(16f, 0f, 7f, 0x73000000)
    }
    canvas.drawBitmap(icon, null, android.graphics.RectF(left, top, left + size, top + size), paint)
    return out
}

/**
 * The Material "cast" glyph, white on a blue-to-teal rounded square, so Cast Screen sits among
 * real app icons. Path data is the 24-unit Material icon, scaled up.
 */
private fun renderCastIcon(size: Int): Bitmap {
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val s = size.toFloat()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 0f, s, s, 0xFF2F6FED.toInt(), 0xFF12B5CB.toInt(), Shader.TileMode.CLAMP)
    }
    canvas.drawRoundRect(0f, 0f, s, s, s * 0.22f, s * 0.22f, paint)
    val glyph = PathParser.createPathFromPathData(
        "M1 18v3h3c0-1.66-1.34-3-3-3zm0-4v2c2.76 0 5 2.24 5 5h2c0-3.87-3.13-7-7-7zm0-4v2c4.97 0 9 4.03 9 9h2" +
            "c0-6.08-4.93-11-11-11zm20-7H3c-1.1 0-2 .9-2 2v3h2V5h18v14h-7v2h7c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2z",
    )
    val glyphSize = s * 0.58f
    canvas.translate((s - glyphSize) / 2f, (s - glyphSize) / 2f)
    canvas.scale(glyphSize / 24f, glyphSize / 24f)
    canvas.drawPath(glyph, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE })
    return out
}

/** Apps with no artwork at all get their initial, so the tile never looks broken. */
private fun renderLetterTile(context: Context, label: String, colors: ArtColors): Bitmap {
    val out = panel(colors)
    val letter = label.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        alpha = 235
        textSize = TILE_ART_HEIGHT * 0.42f
        textAlign = Paint.Align.CENTER
        typeface = runCatching { ResourcesCompat.getFont(context, R.font.inter_semibold) }.getOrNull()
    }
    val baseline = TILE_ART_HEIGHT / 2f - (paint.descent() + paint.ascent()) / 2f
    Canvas(out).drawText(letter, TILE_ART_WIDTH / 2f, baseline, paint)
    return out
}

private fun panel(colors: ArtColors): Bitmap {
    val out = Bitmap.createBitmap(TILE_ART_WIDTH, TILE_ART_HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val w = TILE_ART_WIDTH.toFloat()
    val h = TILE_ART_HEIGHT.toFloat()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(0f, 0f, w, h, colors.primary.darker(0.85f), colors.secondary.toArgb(), Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, w, h, paint)
    paint.shader = RadialGradient(w * 0.2f, 0f, w * 0.7f, 0x26FFFFFF, 0, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, w, h, paint)
    return out
}

private fun Color.darker(factor: Float): Int =
    Color(red * factor, green * factor, blue * factor).toArgb()

/** A 48×27 box-blurred copy; stretched with bilinear filtering it reads as a deep blur. */
private fun Bitmap.blurredThumbnail(): Bitmap {
    val small = Bitmap.createScaledBitmap(this, BLUR_WIDTH, BLUR_HEIGHT, true)
    val w = small.width
    val h = small.height
    val a = IntArray(w * h)
    val b = IntArray(w * h)
    small.getPixels(a, 0, w, 0, 0, w, h)
    repeat(2) {
        boxBlur(a, b, w, h, radius = 2, horizontal = true)
        boxBlur(b, a, w, h, radius = 2, horizontal = false)
    }
    return Bitmap.createBitmap(a, w, h, Bitmap.Config.ARGB_8888)
}

private fun Bitmap.meanLuminance(): Float {
    val px = IntArray(width * height)
    getPixels(px, 0, width, 0, 0, width, height)
    var sum = 0f
    for (p in px) sum += (0.2126f * ((p shr 16) and 0xFF) + 0.7152f * ((p shr 8) and 0xFF) + 0.0722f * (p and 0xFF)) / 255f
    return sum / px.size
}

private fun boxBlur(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val length = if (horizontal) w else h
    for (line in 0 until lines) {
        for (i in 0 until length) {
            var a = 0; var r = 0; var g = 0; var bl = 0; var n = 0
            for (k in max(0, i - radius)..min(length - 1, i + radius)) {
                val p = src[if (horizontal) line * w + k else k * w + line]
                a += p ushr 24; r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; bl += p and 0xFF; n++
            }
            dst[if (horizontal) line * w + i else i * w + line] =
                ((a / n) shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (bl / n)
        }
    }
}
