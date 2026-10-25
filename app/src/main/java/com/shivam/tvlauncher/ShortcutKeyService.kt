package com.shivam.tvlauncher

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Brings the launcher to the front when the chosen color button is pressed in any app.
 *
 * Its config (res/xml/shortcut_key_service.xml) asks only for key filtering: no accessibility
 * events, no window content. It looks at the key code and nothing else, and logs nothing.
 */
class ShortcutKeyService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Cached from DataStore, because onKeyEvent must answer synchronously. */
    @Volatile
    private var shortcutKey: ColorKey? = LauncherPrefs().shortcutKey

    /** Set when we consumed a key-down, so its repeats and key-up are consumed too. */
    private var swallowing = false

    override fun onServiceConnected() {
        scope.launch {
            LauncherPrefsStore(this@ShortcutKeyService).prefs.collect { shortcutKey = it.shortcutKey }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != shortcutKey?.keyCode) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            // Already in front: let the launcher's own color-key handling take it.
            swallowing = !MainActivity.inFront
            if (swallowing) bringLauncherToFront()
        }
        val consumed = swallowing
        if (event.action == KeyEvent.ACTION_UP) swallowing = false
        return consumed
    }

    private fun bringLauncherToFront() {
        try {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            // Can't happen for our own activity; never let a key press crash the service.
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
