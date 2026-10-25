package com.shivam.tvlauncher

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Brings the launcher to the front when the chosen color button is pressed in any app, and
 * stands in for Amazon's home screen: Fire OS won't let the stock launcher be disabled, handles
 * the Home key before any app sees it, and opens its own home whatever the preferred Home app
 * is. So when that home screen comes up, this launcher is opened over it (a brief flash).
 *
 * Its config (res/xml/shortcut_key_service.xml) asks for key filtering and for window changes in
 * Amazon's launcher only, never window content. It looks at the key code and at which Amazon
 * screen opened, nothing else, and logs nothing.
 */
class ShortcutKeyService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Cached from DataStore, because onKeyEvent must answer synchronously. */
    @Volatile
    private var shortcutKey: ColorKey? = LauncherPrefs().shortcutKey

    /** Set when we consumed a key-down, so its repeats and key-up are consumed too. */
    private var swallowing = false

    @Volatile
    private var homeRedirect = LauncherPrefs().homeRedirect

    override fun onServiceConnected() {
        scope.launch {
            LauncherPrefsStore(this@ShortcutKeyService).prefs.collect {
                shortcutKey = it.shortcutKey
                homeRedirect = it.homeRedirect
            }
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

    private var pendingRedirect: Job? = null

    /**
     * The config limits events to window changes in Amazon's launcher. Its home screen is
     * replaced after a short wait, because Fire OS Settings passes through that home on the way
     * to its own screen (MainSettingsActivity, also in Amazon's launcher): any other screen of
     * that app turning up within the wait cancels the redirect.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val name = event.className?.toString() ?: return
        when {
            name == AmazonHome -> if (homeRedirect) {
                pendingRedirect?.cancel()
                pendingRedirect = scope.launch {
                    delay(RedirectDelayMs)
                    bringLauncherToFront(home = true)
                }
            }
            // Another of its activities; dialogs and views (android.*) don't count.
            name.startsWith("com.amazon.tv.launcher.") -> pendingRedirect?.cancel()
        }
    }

    override fun onInterrupt() = Unit

    private companion object {
        const val AmazonHome = "com.amazon.tv.launcher.ui.HomeActivity_vNext"
        const val RedirectDelayMs = 400L
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
