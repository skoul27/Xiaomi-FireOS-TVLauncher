package com.shivam.tvlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * What sits behind the home screen. Dynamic follows the focused app's colours; the presets are
 * gradients drawn in code; Photo is a picture the user chose, copied into the app's files.
 */
enum class Wallpaper(val label: String) {
    Dynamic("Dynamic"),
    Aurora("Aurora"),
    Midnight("Midnight"),
    Dusk("Dusk"),
    Ocean("Ocean"),
    Photo("Photo");

    companion object {
        fun named(name: String): Wallpaper = entries.firstOrNull { it.name == name } ?: Dynamic
    }
}

/** The chosen photo, kept inside the app so it survives the original being moved or deleted. */
object WallpaperPhoto {
    private const val Name = "wallpaper.jpg"

    fun file(context: Context): File = File(context.filesDir, Name)

    fun exists(context: Context): Boolean = file(context).isFile

    /** The panel is 4K; anything bigger than covering it is wasted space. */
    private const val MaxWidth = 3840
    private const val MaxHeight = 2160

    /**
     * Copies the picked image in at full quality, scaled down only if it's bigger than covering
     * 3840×2160. Writes aside and swaps in, so a failure keeps the previous photo. Call off the
     * main thread; false if the image couldn't be read.
     */
    fun save(context: Context, uri: Uri): Boolean = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        val sample = sampleToCover(bounds.outWidth, bounds.outHeight, MaxWidth, MaxHeight)
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return false
        val scale = maxOf(MaxWidth.toFloat() / decoded.width, MaxHeight.toFloat() / decoded.height).coerceAtMost(1f)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else {
            decoded
        }
        val target = file(context)
        val temp = File(target.parentFile, "$Name.tmp")
        temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        if (!temp.renameTo(target)) {
            target.delete()
            check(temp.renameTo(target))
        }
        true
    }.getOrDefault(false)

    /**
     * Decoded no bigger than the app's window needs. Fire OS draws apps at 1920×1080 and scales
     * that up to the 4K panel, so a full 4K bitmap would only cost memory (33 MB against 8).
     */
    fun load(context: Context): Bitmap? = runCatching {
        val path = file(context).path
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val screen = context.resources.displayMetrics
        val sample = sampleToCover(bounds.outWidth, bounds.outHeight, screen.widthPixels, screen.heightPixels)
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    /** The largest power-of-two step down that still covers [width]×[height]. */
    private fun sampleToCover(srcWidth: Int, srcHeight: Int, width: Int, height: Int): Int {
        var sample = 1
        while (srcWidth / (sample * 2) >= width && srcHeight / (sample * 2) >= height) sample *= 2
        return sample
    }
}
