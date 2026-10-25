package com.shivam.tvlauncher

import android.content.Context
import android.content.pm.ApplicationInfo
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Facts the system already knows about an app, for the hero's second line. Only what
 * PackageManager reports: nothing guessed, nothing fetched.
 */
@Immutable
class AppDetails(
    /** The category the app declares (Video, Audio, Games…), or null if it declares none. */
    val category: String?,
    val updated: String?,
    val version: String?,
) {
    /** "Video · Updated 12 Oct 2026 · Version 8.1", leaving out whatever is unknown. */
    val line: String = listOfNotNull(category, updated?.let { "Updated $it" }, version?.let { "Version $it" })
        .joinToString("  ·  ")
}

private val detailsCache = ConcurrentHashMap<String, AppDetails>()

/** The app's details, or null for the frame or two until they are read. */
@Composable
fun rememberAppDetails(app: LaunchableApp): AppDetails? {
    val context = LocalContext.current.applicationContext
    return produceState(detailsCache[app.artKey], app.artKey) {
        // Reset for the new key first: produceState keeps the previous app's value otherwise.
        value = detailsCache[app.artKey]
        if (value == null) {
            value = withContext(Dispatchers.IO) { readDetails(context, app) }.also { detailsCache[app.artKey] = it }
        }
    }.value
}

private fun readDetails(context: Context, app: LaunchableApp): AppDetails {
    val info = runCatching { context.packageManager.getPackageInfo(app.packageName, 0) }.getOrNull()
        ?: return AppDetails(null, null, null)
    val appInfo = info.applicationInfo
    val category = appInfo?.category
        ?.takeIf { it != ApplicationInfo.CATEGORY_UNDEFINED }
        ?.let { ApplicationInfo.getCategoryTitle(context, it)?.toString() }
    val locale = Locale.getDefault()
    val dateFormat = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "dMMMyyyy"), locale)
    return AppDetails(
        category = category,
        // Before 2010 means a factory build stamp, not a real update; say nothing rather than mislead.
        updated = info.lastUpdateTime.takeIf { it > 1_262_304_000_000L }?.let { dateFormat.format(Date(it)) },
        // Long build strings ("8.110.0 build 12 50735") are cut to the readable part.
        version = info.versionName?.trim()?.substringBefore(' ')?.takeIf { it.isNotEmpty() }?.take(24),
    )
}
