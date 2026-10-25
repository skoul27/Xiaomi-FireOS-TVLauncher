package com.shivam.tvlauncher

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
        override fun launch(app: LaunchableApp) = start(app.launchIntent(), "Can't open ${app.label}")
        override fun pin(app: LaunchableApp) = viewModel.pin(app)
        override fun unpin(app: LaunchableApp) = viewModel.unpin(app)
        override fun move(app: LaunchableApp, row: RowKind, delta: Int) = viewModel.move(app, row, delta)
        override fun hide(app: LaunchableApp) = viewModel.hide(app)
        override fun unhide(app: LaunchableApp) = viewModel.unhide(app)

        override fun openAppInfo(app: LaunchableApp) = start(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(app)),
            "App info isn't available for ${app.label}",
        )

        // The system dialog confirms; onResume's refresh drops the app if it was removed.
        override fun uninstall(app: LaunchableApp) = start(
            Intent(Intent.ACTION_DELETE, packageUri(app)),
            "Can't uninstall ${app.label}",
        )

        override fun openShortcut(shortcut: SystemShortcut) =
            start(Intent(shortcut.intent), "${shortcut.label} isn't available")

        override fun refreshApps() {
            viewModel.refresh()
            Toast.makeText(this@MainActivity, "App list refreshed", Toast.LENGTH_SHORT).show()
        }

        override fun setShortcutKey(key: ColorKey?) = viewModel.setShortcutKey(key)
        override fun setTheme(accent: Accent) = viewModel.setTheme(accent.name)
        override fun setAnimations(enabled: Boolean) = viewModel.setAnimations(enabled)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            LauncherTheme(accent = Accent.named(settings.theme), animationsEnabled = settings.animations) {
                val ui by viewModel.ui.collectAsStateWithLifecycle()
                val shortcuts by viewModel.shortcuts.collectAsStateWithLifecycle()
                HomeScreen(
                    ui = ui,
                    shortcuts = shortcuts,
                    remoteShortcut = RemoteShortcut(settings.shortcutKey, shortcutServiceOn),
                    homePresses = homePresses,
                    opens = opens,
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
    private var shortcutServiceOn by mutableStateOf(false)

    /** Counts each time the launcher comes to the front; the screen fades in on each. */
    private var opens by mutableIntStateOf(0)

    override fun onStart() {
        super.onStart()
        opens++
    }

    override fun onResume() {
        super.onResume()
        inFront = true
        // Picks up installs/uninstalls that happened while we were in the background.
        viewModel.refresh()
        shortcutServiceOn = isShortcutServiceOn()
    }

    override fun onPause() {
        inFront = false
        super.onPause()
    }

    /**
     * Reads the same setting the system binds accessibility services from. Read-only: the app
     * never writes it. (AccessibilityManager's enabled list filters by feedback type, and this
     * service declares none, so it would always look off.)
     */
    private fun isShortcutServiceOn(): Boolean {
        val ours = ComponentName(this, ShortcutKeyService::class.java)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.split(':').any { ComponentName.unflattenFromString(it.trim()) == ours }
    }

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
