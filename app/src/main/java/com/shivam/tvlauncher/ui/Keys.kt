package com.shivam.tvlauncher.ui

import android.view.SoundEffectConstants
import androidx.compose.foundation.focusable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
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

/**
 * Focusable + OK/long-press OK, with the same press/release rules everywhere. OK clicks with
 * Fire OS's own sound (off when its Navigation sounds setting is off).
 */
fun Modifier.remoteClickable(onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier = composed {
    val keys = remember { SelectKeyState() }
    val view = LocalView.current
    this
        .onKeyEvent { event ->
            keys.handle(
                event,
                onClick = { view.playSoundEffect(SoundEffectConstants.CLICK); onClick() },
                onLongClick = onLongClick?.let { { view.playSoundEffect(SoundEffectConstants.CLICK); it() } },
            )
        }
        .semantics {
            role = Role.Button
            onClick { onClick(); true }
            if (onLongClick != null) onLongClick { onLongClick(); true }
        }
        .focusable()
}

/**
 * The remote's Menu (≡) button: acts on the press, swallows the release. Put it before the
 * element's focusable (e.g. before [remoteClickable]) so it sees the key. Null does nothing.
 */
fun Modifier.onMenuKey(onMenu: (() -> Unit)?): Modifier = if (onMenu == null) this else onKeyEvent { event ->
    if (event.key != Key.Menu) return@onKeyEvent false
    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) onMenu()
    true
}

/** Fire OS's navigation sound for each D-pad direction. */
private val DpadMoves = mapOf(
    Key.DirectionLeft to (FocusDirection.Left to SoundEffectConstants.NAVIGATION_LEFT),
    Key.DirectionRight to (FocusDirection.Right to SoundEffectConstants.NAVIGATION_RIGHT),
    Key.DirectionUp to (FocusDirection.Up to SoundEffectConstants.NAVIGATION_UP),
    Key.DirectionDown to (FocusDirection.Down to SoundEffectConstants.NAVIGATION_DOWN),
)

/**
 * D-pad sounds for everything inside, put on the root of each window (screen, menus, pop-ups).
 * Keys reach it only if nothing inside used them; it then moves focus exactly as Compose would
 * by default, and plays Fire OS's navigation sound when focus actually moved, so nothing plays
 * at the end of a row. Follows Fire OS's Navigation sounds setting.
 */
fun Modifier.navigationSounds(): Modifier = composed {
    val view = LocalView.current
    val focus = LocalFocusManager.current
    onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        val (direction, sound) = DpadMoves[event.key] ?: return@onKeyEvent false
        focus.moveFocus(direction).also { moved -> if (moved) view.playSoundEffect(sound) }
    }
}

/**
 * Takes over D-pad presses in [key]'s direction from inside: [onPress] moves focus itself and
 * returns true, or returns false to leave the press to the default search. Plays the same sound.
 */
fun Modifier.onDpad(key: Key, onPress: () -> Boolean): Modifier = composed {
    val view = LocalView.current
    onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || event.key != key) return@onKeyEvent false
        onPress().also { handled -> if (handled) view.playSoundEffect(DpadMoves.getValue(key).second) }
    }
}
