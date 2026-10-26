package com.shivam.tvlauncher

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shivam.tvlauncher.ui.Accent
import com.shivam.tvlauncher.ui.HomeScreen
import com.shivam.tvlauncher.ui.LauncherActions
import com.shivam.tvlauncher.ui.LauncherTheme

class MainActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels()

    /** Guards against a second OK press launching the app again while it is still opening. */
    private var lastStartAt = 0L

    private val actions = object : LauncherActions {
        override fun launch(app: LaunchableApp) {
            start(app.launchIntent(), "Can't open ${app.label}")
        }
        override fun pin(app: LaunchableApp) = viewModel.pin(app)
        override fun unpin(app: LaunchableApp) = viewModel.unpin(app)
        override fun move(app: LaunchableApp, row: RowKind, delta: Int) = viewModel.move(app, row, delta)
        override fun hide(app: LaunchableApp) = viewModel.hide(app)
        override fun unhide(app: LaunchableApp) = viewModel.unhide(app)

        override fun openAppInfo(app: LaunchableApp) {
            start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(app)), "App info isn't available for ${app.label}")
        }

        // The system dialog confirms; onResume's refresh drops the app if it was removed.
        override fun uninstall(app: LaunchableApp) {
            start(Intent(Intent.ACTION_DELETE, packageUri(app)), "Can't uninstall ${app.label}")
        }

        // Every other app is in the background while we're in front, so this ends them all; the
        // system never kills a foreground app this way. Our own process is left out.
        // Apps playing media are stopped first: their playback service otherwise keeps them alive.
        override fun closeApps(apps: List<LaunchableApp>) {
            val am = getSystemService(ActivityManager::class.java)
            val packages = apps.map { it.packageName }.toSet() - packageName
            val kill = { packages.forEach { runCatching { am.killBackgroundProcesses(it) } } }
            if (stopMediaSessions(this@MainActivity, packages)) {
                // Give the apps a moment to stop playback and drop their foreground service.
                window.decorView.postDelayed(kill, 1500)
            } else {
                kill()
            }
            val message = apps.singleOrNull()?.let { "Closed ${it.label}" } ?: "Closed all apps"
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }

        override fun openShortcut(shortcut: SystemShortcut) {
            // Fire OS Settings opens via Amazon's home screen; don't let the service bounce it.
            // The other screens open directly, and must not leave Home waiting on it.
            if (shortcut.intent.action == Settings.ACTION_SETTINGS) ShortcutKeyService.expectSettings()
            start(Intent(shortcut.intent), "${shortcut.label} isn't available")
        }

        override fun openCast() {
            start(Intent(CastScreenIntent), "Screen mirroring isn't available")
        }

        override fun openWifiSettings() {
            start(Intent(Settings.ACTION_WIFI_SETTINGS), "Wi-Fi settings aren't available")
        }

        override fun openConsole(console: Console) {
            start(ConsoleActivity.intent(this@MainActivity, console), "Can't open ${console.name}")
        }

        override fun refreshApps() {
            viewModel.refresh()
            Toast.makeText(this@MainActivity, "App list refreshed", Toast.LENGTH_SHORT).show()
        }

        override fun setShortcutKey(key: ColorKey?) = viewModel.setShortcutKey(key)
        override fun setTheme(accent: Accent) = viewModel.setTheme(accent.name)
        override fun setAnimations(enabled: Boolean) = viewModel.setAnimations(enabled)
        override fun setHomeRedirect(enabled: Boolean) = viewModel.setHomeRedirect(enabled)
        override fun setConsoleTile(inputId: String, tile: PortTile) = viewModel.setConsoleTile(inputId, tile)
        override fun setWallpaper(wallpaper: Wallpaper) = viewModel.setWallpaper(wallpaper)
        override fun choosePhoto() {
            try {
                pickPhoto.launch(arrayOf("image/*"))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this@MainActivity, "No photo picker on this TV", Toast.LENGTH_SHORT).show()
            }
        }
        override fun recheckShortcut() {
            shortcutService = readShortcutService()
        }
        override fun exportBackup() = viewModel.exportBackup()
        override fun importBackup() = viewModel.checkImport()
        override fun confirmImport() = viewModel.confirmImport()
        override fun dismissBackup() = viewModel.dismissBackup()
    }

    /** Fire OS's document picker (DocumentsUI); the image is copied in, so no lasting access is kept. */
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        viewModel.savePhoto(uri) { saved ->
            if (!saved) Toast.makeText(this, "Couldn't use that image", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            LauncherTheme(accent = Accent.named(settings.theme), animationsEnabled = settings.animations) {
                val ui by viewModel.ui.collectAsStateWithLifecycle()
                val shortcuts by viewModel.shortcuts.collectAsStateWithLifecycle()
                val backup by viewModel.backup.collectAsStateWithLifecycle()
                val ports by viewModel.ports.collectAsStateWithLifecycle()
                val photoVersion by viewModel.photoVersion.collectAsStateWithLifecycle()
                val wallpaper = Wallpaper.named(settings.wallpaper)
                val hasPhoto = remember(photoVersion) { WallpaperPhoto.exists(this) }
                // Decoded only while it's the background, so the bitmap isn't held otherwise.
                val photo by produceState<ImageBitmap?>(null, wallpaper == Wallpaper.Photo, photoVersion) {
                    value = if (wallpaper == Wallpaper.Photo) {
                        withContext(Dispatchers.IO) { WallpaperPhoto.load(this@MainActivity)?.asImageBitmap() }
                    } else {
                        null
                    }
                }
                HomeScreen(
                    ui = ui,
                    shortcuts = shortcuts,
                    remoteShortcut = RemoteShortcut(settings.shortcutKey, shortcutService.on, shortcutService.enableCommands),
                    homeRedirect = settings.homeRedirect,
                    ports = ports,
                    consoleTiles = settings.consoleTiles,
                    wallpaper = wallpaper,
                    photo = photo,
                    hasPhoto = hasPhoto,
                    homePresses = homePresses,
                    opens = opens,
                    backup = backup,
                    actions = actions,
                )
            }
        }
    }

    /** Counts Home presses that arrive while we're already the Home screen; the UI resets on each. */
    private var homePresses by mutableIntStateOf(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses++
    }

    /** Whether the accessibility shortcut is turned on; re-checked on resume, after a trip to Settings. */
    private var shortcutService by mutableStateOf(ShortcutServiceState(on = false, enableCommands = emptyList()))

    /** Counts each time the launcher comes to the front; the screen fades in on each. */
    private var opens by mutableIntStateOf(0)

    override fun onStart() {
        super.onStart()
        opens++
    }

    override fun onResume() {
        super.onResume()
        inFront = true
        ShortcutKeyService.recordFront(this, true)
        // Picks up installs/uninstalls that happened while we were in the background.
        viewModel.refresh()
        shortcutService = readShortcutService()
    }

    override fun onPause() {
        inFront = false
        ShortcutKeyService.recordFront(this, false)
        super.onPause()
    }

    /**
     * Reads the same setting the system binds accessibility services from. Read-only: the app
     * never writes it. (AccessibilityManager's enabled list filters by feedback type, and this
     * service declares none, so it would always look off.)
     */
    private fun readShortcutService(): ShortcutServiceState {
        val ours = ComponentName(this, ShortcutKeyService::class.java)
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().trim().takeUnless { it == "null" }.orEmpty()
        val listed = current.split(':').any { ComponentName.unflattenFromString(it.trim()) == ours }
        // Listed but with accessibility switched off as a whole, the system binds nothing.
        val masterOn = Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        // Appends to whatever is on now (or keeps it, if we're already there), so following the
        // command never switches another service off.
        val list = when {
            listed -> current
            current.isEmpty() -> ours.flattenToShortString()
            else -> "$current:${ours.flattenToShortString()}"
        }
        return ShortcutServiceState(
            on = listed && masterOn,
            enableCommands = listOf(
                "adb shell settings put secure enabled_accessibility_services $list",
                "adb shell settings put secure accessibility_enabled 1",
            ),
        )
    }

    private class ShortcutServiceState(val on: Boolean, val enableCommands: List<String>)

    private fun packageUri(app: LaunchableApp): Uri = Uri.fromParts("package", app.packageName, null)

    private fun start(intent: Intent, failureMessage: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastStartAt < 1_000) return
        lastStartAt = now
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, failureMessage, Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, failureMessage, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        /** Read by [ShortcutKeyService], which runs in this process, to leave color keys to the UI. */
        @Volatile
        var inFront = false
            private set
    }
}
