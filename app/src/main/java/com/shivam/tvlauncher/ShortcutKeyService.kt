package com.shivam.tvlauncher

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Brings the launcher to the front when the chosen color button is pressed in any app, and
 * stands in for Amazon's home screen: Fire OS won't let the stock launcher be disabled, handles
 * the Home key before any app sees it, and opens its own home whatever the preferred Home app
 * is. So when that home screen comes up, this launcher is opened over it (a brief flash). With
 * the same setting on, it also opens the launcher once after each boot (the service is often
 * bound after Amazon's home has already appeared), after the process dies while the launcher is
 * in front (an update, a crash, a low-memory kill), and whenever the screen wakes.
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

    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (homeRedirect && !MainActivity.inFront) bringLauncherToFront(home = true)
        }
    }

    override fun onServiceConnected() {
        val store = LauncherPrefsStore(this)
        scope.launch {
            store.prefs.collect {
                shortcutKey = it.shortcutKey
                homeRedirect = it.homeRedirect
            }
        }
        scope.launch {
            val state = getSharedPreferences(StateFile, MODE_PRIVATE)
            // The process (and so this service) died while one of our screens was in front: Amazon's
            // home took its place before we were back to see it, and Home pressed there changes
            // no window, so nothing would ever bring the launcher back.
            val diedInFront = state.getBoolean(OursInFront, false)
            val firstThisBoot = isFirstConnectThisBoot(state)
            if ((firstThisBoot || diedInFront) && !MainActivity.inFront && store.prefs.first().homeRedirect) {
                bringLauncherToFront(home = true)
            }
        }
        ContextCompat.registerReceiver(this, screenOn, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /**
     * True once per boot. The service also reconnects within a boot (after an update, or if the
     * process is killed), and those mustn't pull the launcher over whatever is on screen.
     */
    private fun isFirstConnectThisBoot(state: SharedPreferences): Boolean {
        val boot = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
        if (boot < 0) return false
        if (state.getInt(HandledBoot, -1) == boot) return false
        state.edit().putInt(HandledBoot, boot).apply()
        return true
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
     * to its own screen (MainSettingsActivity, also in Amazon's launcher): a settings screen of
     * that app turning up within the wait cancels the redirect. Its other screens don't, so one
     * that pops up by itself over the home screen can't keep Home on Amazon's. Right after a
     * boot Settings can take well over a second, so when the launcher has just opened it
     * ([expectSettings]) the next home screen is left alone, once.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val name = event.className?.toString() ?: return
        when {
            name == AmazonHome -> {
                val passingToSettings = SystemClock.elapsedRealtime() < settingsExpectedUntil
                settingsExpectedUntil = 0L
                if (homeRedirect && !passingToSettings) {
                    pendingRedirect?.cancel()
                    pendingRedirect = scope.launch {
                        delay(RedirectDelayMs)
                        bringLauncherToFront(home = true)
                    }
                }
            }
            name.startsWith("com.amazon.tv.launcher.") && name.contains("Settings") -> pendingRedirect?.cancel()
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val AmazonHome = "com.amazon.tv.launcher.ui.HomeActivity_vNext"
        private const val RedirectDelayMs = 400L
        private const val SettingsWaitMs = 10_000L
        private const val StateFile = "service"
        private const val HandledBoot = "handled_boot"
        private const val OursInFront = "ours_in_front"

        /** Same process as the launcher, so a plain field is enough to pass this along. */
        @Volatile
        private var settingsExpectedUntil = 0L

        /**
         * Our activities call this as they resume (true) and pause (false). On disk, because it
         * matters exactly when the process has died without pausing.
         */
        fun recordFront(context: Context, front: Boolean) {
            context.getSharedPreferences(StateFile, MODE_PRIVATE).edit().putBoolean(OursInFront, front).apply()
        }

        /** Called by the launcher just before it opens Fire OS Settings (via Amazon's home screen). */
        fun expectSettings() {
            settingsExpectedUntil = SystemClock.elapsedRealtime() + SettingsWaitMs
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOn) }
        scope.cancel()
        super.onDestroy()
    }
}
