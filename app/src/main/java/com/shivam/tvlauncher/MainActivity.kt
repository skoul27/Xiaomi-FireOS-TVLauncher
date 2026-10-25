package com.shivam.tvlauncher

import android.content.ActivityNotFoundException
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

        override fun openSystemSettings() = start(Intent(Settings.ACTION_SETTINGS), "Settings aren't available")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LauncherTheme {
                val ui by viewModel.ui.collectAsStateWithLifecycle()
                HomeScreen(ui = ui, actions = actions)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Picks up installs/uninstalls that happened while we were in the background.
        viewModel.refresh()
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
}
