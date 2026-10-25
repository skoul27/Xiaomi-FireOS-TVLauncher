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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Brings the launcher to the front when the chosen color button is pressed in any app, and
 * makes Home open this launcher: Fire OS won't let the stock launcher be disabled and sends Home
 * to it whatever the preferred Home app is. A long press of Home still reaches Amazon's home.
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

    /** Set once a held Home press has been handed to the system as a long press. */
    private var homeLongPressed = false

    /** The global Home action may come back through this filter as a key; let that one through. */
    private var passHome = false

    override fun onServiceConnected() {
        scope.launch {
            LauncherPrefsStore(this@ShortcutKeyService).prefs.collect { shortcutKey = it.shortcutKey }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_HOME) return onHomeKey(event)
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

    /**
     * Acts on release, so a held press can still go to Amazon's home: the way back if this
     * launcher ever misbehaves.
     */
    private fun onHomeKey(event: KeyEvent): Boolean {
        if (passHome) {
            if (event.action == KeyEvent.ACTION_UP) passHome = false
            return false
        }
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                homeLongPressed = false
            } else if (!homeLongPressed && event.isLongPress) {
                homeLongPressed = true
                passHome = true
                performGlobalAction(GLOBAL_ACTION_HOME)
                // In case it doesn't come back as a key, don't let a later real press slip through.
                scope.launch {
                    delay(1_000)
                    passHome = false
                }
            }
            KeyEvent.ACTION_UP -> if (!homeLongPressed) bringLauncherToFront(home = true)
        }
        return true
    }

    /** [home] marks it as a Home press, so the launcher resets to the start if already in front. */
    private fun bringLauncherToFront(home: Boolean = false) {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (home) intent.setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        try {
            startActivity(intent)
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
