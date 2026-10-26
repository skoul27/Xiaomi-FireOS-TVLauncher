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

    /**
     * Copies the picked image in, scaled down to cover 1920×1080 at most, so it decodes fast and
     * stays small. Writes aside and swaps in, so a failure keeps the previous photo. Call off the
     * main thread; false if the image couldn't be read.
     */
    fun save(context: Context, uri: Uri): Boolean = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1920 && bounds.outHeight / (sample * 2) >= 1080) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return false
        val scale = maxOf(1920f / decoded.width, 1080f / decoded.height).coerceAtMost(1f)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else {
            decoded
        }
        val target = file(context)
        val temp = File(target.parentFile, "$Name.tmp")
        temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (!temp.renameTo(target)) {
            target.delete()
            check(temp.renameTo(target))
        }
        true
    }.getOrDefault(false)

    fun load(context: Context): Bitmap? = runCatching { BitmapFactory.decodeFile(file(context).path) }.getOrNull()
}
