package com.shivam.tvlauncher

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** What the user has arranged. Everything is by package name. */
data class LauncherPrefs(
    /** My Apps row, in display order. */
    val pinned: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    /** Custom All apps order; apps not listed follow alphabetically. Empty means alphabetical. */
    val allOrder: List<String> = emptyList(),
    /** Set once the default streaming apps have been pinned, so unpinning them sticks. */
    val seeded: Boolean = false,
    /** Color button that brings the launcher to the front from any app; null turns it off. */
    val shortcutKey: ColorKey? = ColorKey.Blue,
    /** Accent theme name; unknown names fall back to the first theme. */
    val theme: String = "",
    val animations: Boolean = true,
    /** Open this launcher whenever Amazon's home screen comes up (Fire OS sends Home there). */
    val homeRedirect: Boolean = true,
    /** Console tile per HDMI port, by TV input id; ports not listed are Automatic. */
    val consoleTiles: Map<String, PortTile> = emptyMap(),
    /** [Wallpaper] name; unknown names are Dynamic. */
    val wallpaper: String = "",
)

private val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher")

class LauncherPrefsStore(context: Context) {
    private val store = context.applicationContext.launcherDataStore

    val prefs: Flow<LauncherPrefs> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toLauncherPrefs() }

    suspend fun update(transform: (LauncherPrefs) -> LauncherPrefs) {
        store.edit { stored ->
            val next = transform(stored.toLauncherPrefs())
            stored[Pinned] = next.pinned.joinToString(Separator)
            stored[Hidden] = next.hidden
            stored[AllOrder] = next.allOrder.joinToString(Separator)
            stored[Seeded] = next.seeded
            stored[ShortcutKey] = next.shortcutKey?.name ?: ShortcutOff
            stored[Theme] = next.theme
            stored[Animations] = next.animations
            stored[HomeRedirect] = next.homeRedirect
            stored[ConsoleTiles] = next.consoleTiles.entries.joinToString(Separator) { (input, tile) ->
                "$input$Field${tile.mode.name}$Field${tile.name}"
            }
            stored[WallpaperKey] = next.wallpaper
            // Left by a removed "Recent" feature; drop it so no launch history stays on disk.
            stored.remove(RemovedRecent)
        }
    }

    private fun Preferences.toLauncherPrefs() = LauncherPrefs(
        pinned = this[Pinned].toPackageList(),
        hidden = this[Hidden].orEmpty(),
        allOrder = this[AllOrder].toPackageList(),
        seeded = this[Seeded] ?: false,
        shortcutKey = this[ShortcutKey].let { stored ->
            if (stored == null) ColorKey.Blue else ColorKey.entries.firstOrNull { it.name == stored }
        },
        theme = this[Theme].orEmpty(),
        animations = this[Animations] ?: true,
        homeRedirect = this[HomeRedirect] ?: true,
        consoleTiles = this[ConsoleTiles].toConsoleTiles(),
        wallpaper = this[WallpaperKey].orEmpty(),
    )

    private fun String?.toPackageList(): List<String> =
        this?.split(Separator)?.filter { it.isNotBlank() }.orEmpty()

    /** One "input id, mode, name" line per port; lines with an unknown mode are dropped. */
    private fun String?.toConsoleTiles(): Map<String, PortTile> =
        this?.split(Separator).orEmpty().mapNotNull { line ->
            val parts = line.split(Field, limit = 3)
            val mode = TileMode.entries.firstOrNull { it.name == parts.getOrNull(1) } ?: return@mapNotNull null
            parts[0] to PortTile(mode, cleanTileName(parts.getOrNull(2).orEmpty()))
        }.toMap()

    private companion object {
        // Package names can't contain newlines, so a joined string keeps order cheaply.
        const val Separator = "\n"
        // Input ids and tile names hold no control characters, so a tab splits a tile's fields.
        const val Field = "\t"
        val Pinned = stringPreferencesKey("pinned")
        val Hidden = stringSetPreferencesKey("hidden")
        val AllOrder = stringPreferencesKey("all_order")
        val Seeded = booleanPreferencesKey("seeded")
        val ShortcutKey = stringPreferencesKey("shortcut_key")
        const val ShortcutOff = "off"
        val Theme = stringPreferencesKey("theme")
        val Animations = booleanPreferencesKey("animations")
        val HomeRedirect = booleanPreferencesKey("home_redirect")
        val ConsoleTiles = stringPreferencesKey("console_tiles")
        val WallpaperKey = stringPreferencesKey("wallpaper")
        val RemovedRecent = stringPreferencesKey("recent")
    }
}
