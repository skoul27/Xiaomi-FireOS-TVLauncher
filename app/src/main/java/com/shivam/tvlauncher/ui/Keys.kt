package com.shivam.tvlauncher.ui

import androidx.compose.foundation.focusable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

/** The remote's OK button, plus Enter keys from keyboards and game pads. */
fun KeyEvent.isSelectKey(): Boolean =
    key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter || key == Key.ButtonA

/**
 * OK/Enter handling: click on release, long-click once the key starts repeating (Android begins
 * repeating after the long-press delay). A release without a matching press is ignored, so a
 * key-up left over from another screen or dialog never launches anything.
 */
class SelectKeyState {
    private var pressed = false
    private var longPressed = false

    fun handle(event: KeyEvent, onClick: () -> Unit, onLongClick: (() -> Unit)?): Boolean {
        if (!event.isSelectKey()) return false
        when (event.type) {
            KeyEventType.KeyDown -> {
                if (event.nativeKeyEvent.repeatCount == 0) {
                    pressed = true
                    longPressed = false
                } else if (pressed && !longPressed && onLongClick != null) {
                    longPressed = true
                    onLongClick()
                }
            }
            KeyEventType.KeyUp -> {
                val click = pressed && !longPressed
                pressed = false
                if (click) onClick()
            }
        }
        return true
    }
}

/** Focusable + OK/long-press OK, with the same press/release rules everywhere. */
fun Modifier.remoteClickable(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier = composed {
    val keys = remember { SelectKeyState() }
    this
        .onKeyEvent { keys.handle(it, onClick, onLongClick) }
        .semantics {
            role = Role.Button
            onClick { onClick(); true }
            if (onLongClick != null) onLongClick { onLongClick(); true }
        }
        .focusable()
}
