package com.shivam.tvlauncher

import android.view.KeyEvent
import androidx.compose.runtime.Immutable

/** The remote's four color buttons. The Fire TV remote's key layout maps them to PROG_*. */
enum class ColorKey(val keyCode: Int, val label: String) {
    Red(KeyEvent.KEYCODE_PROG_RED, "Red"),
    Green(KeyEvent.KEYCODE_PROG_GREEN, "Green"),
    Yellow(KeyEvent.KEYCODE_PROG_YELLOW, "Yellow"),
    Blue(KeyEvent.KEYCODE_PROG_BLUE, "Blue");

    companion object {
        fun fromKeyCode(keyCode: Int): ColorKey? = entries.firstOrNull { it.keyCode == keyCode }
    }
}

/** The global shortcut as the System panel shows it: which button, and whether the service is on. */
@Immutable
data class RemoteShortcut(val key: ColorKey?, val serviceOn: Boolean)
