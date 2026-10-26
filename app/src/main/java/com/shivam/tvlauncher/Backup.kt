package com.shivam.tvlauncher

import android.content.Context
import com.shivam.tvlauncher.ui.Accent
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

/** What the settings panel shows about export/import: a result line, or an import to confirm. */
sealed interface BackupState {
    class Message(val text: String) : BackupState
    class ConfirmImport(val contents: LauncherBackup.Contents, val summary: String) : BackupState
}

/**
 * Backup and restore of everything the user arranged, as one JSON file in the app's external
 * files directory (no permission needed). adb copies it in and out through [BackupFileProvider].
 * No file picker: Fire OS may not have one.
 */
object LauncherBackup {
    const val FILE_NAME = "launcher-backup.json"
    private const val FORMAT = "tv-launcher-backup"
    /** 2 added console tiles; version 1 files still import, keeping the current tiles. */
    private const val VERSION = 2

    /** Far above any real backup; stops a wrong file (a video, say) from being read into memory. */
    private const val MAX_BYTES = 256 * 1024
    private const val MAX_PACKAGES = 2000
    private const val MAX_TILES = 64
    private const val MAX_INPUT_ID = 255
    private val PackageName = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

    fun file(context: Context): File? = context.getExternalFilesDir(null)?.let { File(it, FILE_NAME) }

    fun encode(prefs: LauncherPrefs, exportedAt: Long): String = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("exportedAt", exportedAt)
        .put("pinned", JSONArray(prefs.pinned))
        .put("hidden", JSONArray(prefs.hidden.sorted()))
        .put("allOrder", JSONArray(prefs.allOrder))
        .put(
            "consoleTiles",
            JSONArray(
                prefs.consoleTiles.map { (input, tile) ->
                    JSONObject().put("input", input).put("tile", tile.mode.name).put("name", tile.name)
                },
            ),
        )
        .put(
            "settings",
            JSONObject()
                .put("theme", Accent.named(prefs.theme).name)
                .put("animations", prefs.animations)
                .put("remoteShortcut", prefs.shortcutKey?.name ?: "Off"),
        )
        .toString(2)

    /** What a valid file holds; [apply] puts it over the current prefs. */
    class Contents(
        val exportedAt: Long?,
        val pinned: List<String>,
        val hidden: Set<String>,
        val allOrder: List<String>,
        val theme: Accent,
        val animations: Boolean,
        val shortcutKey: ColorKey?,
        /** Null in a version 1 backup, which predates console tiles. */
        val consoleTiles: Map<String, PortTile>?,
    ) {
        fun apply(current: LauncherPrefs): LauncherPrefs = current.copy(
            pinned = pinned,
            hidden = hidden,
            allOrder = allOrder,
            theme = theme.name,
            animations = animations,
            shortcutKey = shortcutKey,
            consoleTiles = consoleTiles ?: current.consoleTiles,
            // The backup's pins are the user's choice; don't add the first-run defaults on top.
            seeded = true,
        )
    }

    sealed interface Read {
        class Valid(val contents: Contents) : Read
        class Invalid(val reason: String) : Read
    }

    /** Reads and fully validates the file. Never throws; anything wrong becomes [Read.Invalid]. */
    fun read(file: File?): Read {
        if (file == null) return Read.Invalid("Storage isn't available right now.")
        if (!file.isFile) return Read.Invalid("No backup file at ${file.path}.")
        if (file.length() > MAX_BYTES) return Read.Invalid("The file is too large to be a backup.")
        val text = try {
            file.readText()
        } catch (e: Exception) {
            return Read.Invalid("The file couldn't be read.")
        }
        return decode(text)
    }

    fun decode(text: String): Read = try {
        Read.Valid(parse(JSONObject(text)))
    } catch (e: JSONException) {
        Read.Invalid("The file isn't valid JSON.")
    } catch (e: BadBackup) {
        Read.Invalid(e.message ?: "The file isn't a valid backup.")
    }

    private class BadBackup(message: String) : Exception(message)

    private fun parse(json: JSONObject): Contents {
        if (json.optString("format") != FORMAT) throw BadBackup("This isn't a TV Launcher backup.")
        val version = json.optInt("version", -1)
        if (version > VERSION) throw BadBackup("This backup is from a newer version of the launcher.")
        if (version < 1) throw BadBackup("This backup's version isn't recognised.")

        val settings = json.optJSONObject("settings") ?: throw BadBackup("The backup has no settings section.")
        val themeName = settings.optString("theme")
        val theme = Accent.entries.firstOrNull { it.name == themeName } ?: throw BadBackup("Unknown theme \"$themeName\".")
        if (!settings.has("animations") || settings.opt("animations") !is Boolean) {
            throw BadBackup("\"animations\" must be true or false.")
        }
        val shortcutName = settings.optString("remoteShortcut")
        val shortcutKey = when (shortcutName) {
            "Off" -> null
            else -> ColorKey.entries.firstOrNull { it.name == shortcutName }
                ?: throw BadBackup("Unknown remote shortcut \"$shortcutName\".")
        }

        return Contents(
            exportedAt = json.optLong("exportedAt", 0L).takeIf { it > 0L },
            pinned = packages(json, "pinned"),
            hidden = packages(json, "hidden").toSet(),
            allOrder = packages(json, "allOrder"),
            theme = theme,
            animations = settings.getBoolean("animations"),
            shortcutKey = shortcutKey,
            consoleTiles = if (version >= 2) consoleTiles(json) else null,
        )
    }

    /** The console tile per HDMI port: known modes, sane ids and names, each port once. */
    private fun consoleTiles(json: JSONObject): Map<String, PortTile> {
        val array = json.optJSONArray("consoleTiles") ?: throw BadBackup("\"consoleTiles\" is missing or isn't a list.")
        if (array.length() > MAX_TILES) throw BadBackup("\"consoleTiles\" has too many entries.")
        val out = LinkedHashMap<String, PortTile>()
        for (i in 0 until array.length()) {
            val entry = array.opt(i) as? JSONObject ?: throw BadBackup("\"consoleTiles\" has an entry that isn't an object.")
            val input = entry.opt("input") as? String ?: throw BadBackup("A console tile has no \"input\".")
            if (input.isBlank() || input.length > MAX_INPUT_ID || input.any { it.isISOControl() }) {
                throw BadBackup("A console tile has an invalid input: \"${input.take(60)}\".")
            }
            val modeName = entry.opt("tile") as? String
            val mode = TileMode.entries.firstOrNull { it.name == modeName } ?: throw BadBackup("Unknown console tile \"$modeName\".")
            val name = entry.opt("name") as? String ?: ""
            if (name.length > MaxTileName || name.any { it.isISOControl() }) {
                throw BadBackup("A console tile's name is too long or has invalid characters.")
            }
            out[input] = PortTile(mode, name.trim())
        }
        return out
    }

    /** A required list of package names: right type, sane size, each a well-formed name. */
    private fun packages(json: JSONObject, key: String): List<String> {
        val array = json.optJSONArray(key) ?: throw BadBackup("\"$key\" is missing or isn't a list.")
        if (array.length() > MAX_PACKAGES) throw BadBackup("\"$key\" has too many entries.")
        val out = LinkedHashSet<String>()
        for (i in 0 until array.length()) {
            val name = array.opt(i) as? String ?: throw BadBackup("\"$key\" has an entry that isn't text.")
            if (name.length > 255 || !PackageName.matches(name)) {
                throw BadBackup("\"$key\" has an invalid app name: \"${name.take(60)}\".")
            }
            out += name
        }
        return out.toList()
    }
}
