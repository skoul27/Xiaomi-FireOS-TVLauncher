package com.shivam.tvlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.shivam.tvlauncher.ColorKey
import com.shivam.tvlauncher.LaunchableApp
import com.shivam.tvlauncher.R
import com.shivam.tvlauncher.RemoteShortcut
import com.shivam.tvlauncher.SystemShortcut
import com.shivam.tvlauncher.rememberAppArt

enum class MenuAction { TogglePin, MoveLeft, MoveRight, Hide, AppInfo, Uninstall }

/**
 * Long-press menu for one tile. Stays open after Move left/right so the tile can be nudged
 * several places in a row; every other action closes it.
 */
@Composable
fun AppMenu(
    app: LaunchableApp,
    isPinned: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onAction: (MenuAction) -> Unit,
    /** False where order isn't the user's (the All apps list is alphabetical). */
    showMove: Boolean = true,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    MenuDialog(onDismiss) {
        PanelTitle(app.label)
        MenuItem(if (isPinned) "Remove from My Apps" else "Add to My Apps", Modifier.focusRequester(first)) {
            onAction(MenuAction.TogglePin)
        }
        // Always present (just inert at the ends) so focus never lands on a vanished item.
        if (showMove) {
            MenuItem("Move left", enabled = canMoveLeft) { onAction(MenuAction.MoveLeft) }
            MenuItem("Move right", enabled = canMoveRight) { onAction(MenuAction.MoveRight) }
        }
        MenuItem("Hide") { onAction(MenuAction.Hide) }
        MenuItem("App info") { onAction(MenuAction.AppInfo) }
        if (!app.isSystem) MenuItem("Uninstall") { onAction(MenuAction.Uninstall) }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/** The gear button's panel: Launcher settings, then Fire OS settings screens that open on this device. */
@Composable
fun SystemMenu(
    shortcuts: List<SystemShortcut>,
    remoteShortcut: RemoteShortcut,
    onOpen: (SystemShortcut) -> Unit,
    onLauncherSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    val key = remoteShortcut.key
    val shortcutOff = key != null && !remoteShortcut.serviceOn
    // Wider while the notice shows, so its commands wrap onto fewer lines.
    MenuDialog(onDismiss, width = if (shortcutOff) 480 else 360) {
        PanelTitle("System")
        if (key != null && shortcutOff) ShortcutOffNotice(key, remoteShortcut.enableCommands)
        MenuItem("Launcher settings", Modifier.focusRequester(first), onClick = onLauncherSettings)
        if (shortcuts.isEmpty()) {
            Text(
                "No Fire OS settings screens are available right now.",
                style = LauncherText.Body,
                color = LauncherColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        for (shortcut in shortcuts) MenuItem(shortcut.label) { onOpen(shortcut) }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/**
 * Health check: the chosen color button won't work outside the launcher because its service is
 * off (a Fire OS update can do that). Shows the commands to run from a computer; the launcher
 * itself never changes the setting.
 */
@Composable
private fun ShortcutOffNotice(key: ColorKey, commands: List<String>) {
    Column(
        modifier = Modifier
            .padding(start = 4.dp, end = 4.dp, bottom = 10.dp)
            .fillMaxWidth()
            .clip(ItemShape)
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text("${key.label}-button shortcut is off", style = LauncherText.Label, color = LauncherColors.TextPrimary)
        Text(
            "It works inside the launcher only. To turn it back on, run from a computer on this network:",
            style = LauncherText.Caption,
            color = LauncherColors.TextSecondary,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
        )
        for (command in commands) {
            Text(command, style = LauncherText.Code, color = LauncherColors.TextPrimary, modifier = Modifier.padding(vertical = 1.dp))
        }
    }
}

/** This launcher's own settings. Each row cycles its value on OK and saves at once. */
@Composable
fun LauncherSettingsMenu(
    accent: Accent,
    animations: Boolean,
    remoteShortcut: RemoteShortcut,
    actions: LauncherActions,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    MenuDialog(onDismiss, width = 420) {
        PanelTitle("Launcher settings")
        SettingItem("Theme", accent.label, swatch = accent.color, modifier = Modifier.focusRequester(first)) {
            actions.setTheme(Accent.entries[(accent.ordinal + 1) % Accent.entries.size])
        }
        SettingItem("Animations", if (animations) "On" else "Off") { actions.setAnimations(!animations) }
        val key = remoteShortcut.key
        SettingItem("Remote shortcut", key?.label ?: "Off") {
            val order = listOf(null) + ColorKey.entries
            actions.setShortcutKey(order[(order.indexOf(key) + 1) % order.size])
        }
        Text(
            when {
                key == null -> "Color buttons still work inside the launcher."
                remoteShortcut.serviceOn -> "${key.label} opens the launcher from any app."
                else -> "Works inside the launcher. \"${stringResource(R.string.shortcut_service_label)}\" " +
                    "isn't turned on, so other apps don't see it."
            },
            style = LauncherText.Caption,
            color = LauncherColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/** "Label        Value" on one row, with an optional colour swatch before the value. */
@Composable
private fun SettingItem(label: String, value: String, swatch: Color? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusRow(modifier = modifier.height(48.dp), onClick = onClick) { focused ->
        val color = if (focused) LauncherColors.Ink else LauncherColors.TextPrimary
        Text(label, style = LauncherText.Body, color = color, modifier = Modifier.weight(1f))
        if (swatch != null) {
            Box(Modifier.size(12.dp).clip(PillShape).background(swatch))
            Spacer(Modifier.width(8.dp))
        }
        Text(value, style = LauncherText.Body, color = if (focused) LauncherColors.Ink else LauncherColors.TextSecondary)
    }
}

@Composable
private fun MenuDialog(onDismiss: () -> Unit, width: Int = 360, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .width(width.dp)
                .glass(PanelShape, fill = Glass.PanelFill)
                .padding(vertical = 16.dp, horizontal = 12.dp)
                // A long-press that opened this menu keeps sending OK repeats until released.
                // Swallow them so letting go can't trigger whichever item has focus.
                .onPreviewKeyEvent {
                    it.isSelectKey() && it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount > 0
                },
            content = content,
        )
    }
}

@Composable
private fun PanelTitle(text: String) {
    Text(
        text,
        style = LauncherText.Title,
        color = LauncherColors.TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 10.dp),
    )
}

@Composable
private fun MenuItem(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    FocusRow(modifier = modifier.height(48.dp), onClick = { if (enabled) onClick() }) { focused ->
        Text(
            text,
            style = LauncherText.Body,
            color = when {
                focused -> LauncherColors.Ink
                enabled -> LauncherColors.TextPrimary
                else -> LauncherColors.TextTertiary
            },
        )
    }
}

@Composable
private fun HiddenAppRow(app: LaunchableApp, modifier: Modifier, onClick: () -> Unit) {
    FocusRow(modifier = modifier.height(58.dp), onClick = onClick) { focused ->
        Box(modifier = Modifier.size(64.dp, 36.dp).clip(RoundedCornerShape(8.dp)).background(Glass.TileBase)) {
            rememberAppArt(app)?.tile?.let {
                Image(it, null, contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Low, modifier = Modifier.size(64.dp, 36.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            app.label,
            style = LauncherText.Body,
            color = if (focused) LauncherColors.Ink else LauncherColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A menu row that turns white when focused. Dialogs are small, so plain state is fine here. */
@Composable
private fun FocusRow(modifier: Modifier, onClick: () -> Unit, content: @Composable RowScope.(focused: Boolean) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .remoteClickable(onClick)
            .clip(ItemShape)
            .background(if (focused) LauncherColors.TextPrimary else Color.Transparent)
            .padding(horizontal = 16.dp),
    ) {
        content(focused)
    }
}
