package com.shivam.tvlauncher.ui

import android.text.InputFilter
import android.text.InputType
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.SoundEffectConstants
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.shivam.tvlauncher.ColorKey
import com.shivam.tvlauncher.HdmiPort
import com.shivam.tvlauncher.LaunchableApp
import com.shivam.tvlauncher.MaxTileName
import com.shivam.tvlauncher.PortTile
import com.shivam.tvlauncher.R
import com.shivam.tvlauncher.RemoteShortcut
import com.shivam.tvlauncher.SystemShortcut
import com.shivam.tvlauncher.TileMode
import com.shivam.tvlauncher.rememberAppArt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

/**
 * The gear button's panel: Hidden apps and Launcher settings, then the Fire OS settings screens
 * that open on this device.
 */
@Composable
fun SystemMenu(
    shortcuts: List<SystemShortcut>,
    remoteShortcut: RemoteShortcut,
    hiddenCount: Int,
    onHiddenApps: () -> Unit,
    onOpen: (SystemShortcut) -> Unit,
    onLauncherSettings: () -> Unit,
    onAbout: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    val key = remoteShortcut.key
    val shortcutOff = key != null && !remoteShortcut.serviceOn
    // Wider while the notice shows, so its commands wrap onto fewer lines.
    MenuDialog(onDismiss, width = if (shortcutOff) 480 else 360) {
        PanelTitle("Settings")
        if (key != null && shortcutOff) ShortcutOffNotice(key, remoteShortcut.enableCommands)
        NavItem(
            "Hidden apps",
            Modifier.focusRequester(first),
            hint = if (hiddenCount > 0) "$hiddenCount" else null,
            onClick = onHiddenApps,
        )
        NavItem("Launcher settings", onClick = onLauncherSettings)
        if (shortcuts.isEmpty()) {
            Text(
                "No Fire OS settings screens are available right now.",
                style = LauncherText.Body,
                color = LauncherColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        for (shortcut in shortcuts) MenuItem(shortcut.label) { onOpen(shortcut) }
        MenuDivider()
        NavItem("About", onClick = onAbout)
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
    homeRedirect: Boolean,
    remoteShortcut: RemoteShortcut,
    actions: LauncherActions,
    /** Back from More: focus lands on More again, not the first row. */
    focusMore: Boolean,
    onMore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    val more = remember { FocusRequester() }
    MenuDialog(onDismiss, width = 460) {
        PanelTitle("Launcher settings")
        SettingItem("Theme", accent.label, swatch = accent.color, modifier = Modifier.focusRequester(first)) {
            actions.setTheme(Accent.entries[(accent.ordinal + 1) % Accent.entries.size])
        }
        SettingItem("Animations", if (animations) "On" else "Off") { actions.setAnimations(!animations) }
        // Rides on the remote shortcut's service, so it needs that turned on too.
        SettingItem("Home button", if (homeRedirect) "This launcher" else "Fire TV home") {
            actions.setHomeRedirect(!homeRedirect)
        }
        val key = remoteShortcut.key
        SettingItem("Remote shortcut", key?.label ?: "Off") {
            val order = listOf(null) + ColorKey.entries
            actions.setShortcutKey(order[(order.indexOf(key) + 1) % order.size])
        }
        // Only worth a line when the shortcut is chosen but can't work outside the launcher.
        if (key != null && !remoteShortcut.serviceOn) {
            Text(
                "Works inside the launcher only: \"${stringResource(R.string.shortcut_service_label)}\" isn't turned on.",
                style = LauncherText.Caption,
                color = LauncherColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        NavItem("More settings", Modifier.focusRequester(more), hint = "Console tiles, Backup", onClick = onMore)
    }
    LaunchedEffect(Unit) { runCatching { (if (focusMore) more else first).requestFocus() } }
}

/** Less-used settings, a level down from Launcher settings. */
@Composable
fun MoreMenu(focusBackup: Boolean, onConsoleTiles: () -> Unit, onBackup: () -> Unit, onDismiss: () -> Unit) {
    val tiles = remember { FocusRequester() }
    val backup = remember { FocusRequester() }
    MenuDialog(onDismiss) {
        PanelTitle("More settings")
        NavItem("Console tiles", Modifier.focusRequester(tiles), onClick = onConsoleTiles)
        NavItem("Backup", Modifier.focusRequester(backup), onClick = onBackup)
    }
    LaunchedEffect(Unit) { runCatching { (if (focusBackup) backup else tiles).requestFocus() } }
}

/** Export or import the setup file, with the result of the last one underneath. */
@Composable
fun BackupMenu(message: String?, actions: LauncherActions, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    MenuDialog(onDismiss, width = 460) {
        PanelTitle("Backup")
        Text(
            "Your My Apps, hidden apps, console tiles, theme and settings, as one file on this TV. " +
                "Copying it to or from a computer needs adb.",
            style = LauncherText.Caption,
            color = LauncherColors.TextSecondary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        MenuItem("Export backup", Modifier.focusRequester(first), onClick = actions::exportBackup)
        MenuItem("Import backup", onClick = actions::importBackup)
        if (message != null) {
            Text(
                message,
                style = LauncherText.Caption,
                color = LauncherColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/**
 * One row per HDMI port; OK cycles its tile. An Other tile gets a Name row under it. Opened
 * from Launcher settings, or by long-pressing a console card.
 */
@Composable
fun ConsoleTilesMenu(
    ports: List<HdmiPort>,
    tiles: Map<String, PortTile>,
    onSet: (HdmiPort, PortTile) -> Unit,
    onRename: (HdmiPort) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    MenuDialog(onDismiss, width = 520) {
        PanelTitle("Console tiles")
        Text(
            "Left and right choose each port's tile. Automatic shows a port only while its device " +
                "announces itself over HDMI; a tile always shows it, and Hidden never does.",
            style = LauncherText.Caption,
            color = LauncherColors.TextSecondary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        if (ports.isEmpty()) {
            Text(
                "This TV reports no HDMI ports.",
                style = LauncherText.Body,
                color = LauncherColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        ports.forEachIndexed { index, port ->
            val tile = tiles[port.inputId] ?: PortTile(TileMode.Auto)
            val step = { delta: Int ->
                onSet(port, tile.copy(mode = TileMode.entries[(tile.mode.ordinal + delta).mod(TileMode.entries.size)]))
            }
            SettingItem(
                port.device?.let { "${port.label} · $it" } ?: port.label,
                tile.mode.label,
                modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                onStep = step,
            ) { step(1) }
            if (tile.mode == TileMode.Other) {
                SettingItem("      Name", tile.name.ifBlank { port.device ?: port.label }) { onRename(port) }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/**
 * Names an Other tile with the TV's on-screen keyboard. A blank name goes back to the default
 * (the device's own name, or the port's). A platform EditText, not Compose's text field: Fire
 * OS's keyboard opened over Compose's with no field attached, so typing went nowhere. OK on the
 * field reopens the keyboard after Back has closed it (EditText does that itself).
 */
@Composable
fun RenameTileMenu(port: HdmiPort, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val save by rememberUpdatedState(onSave)
    var field by remember { mutableStateOf<EditText?>(null) }
    MenuDialog(onDismiss, width = 460) {
        PanelTitle("Name the ${port.label} tile")
        AndroidView(
            factory = { context ->
                EditText(context).apply {
                    setText(initial)
                    setSelection(initial.length)
                    isSingleLine = true
                    filters = arrayOf(InputFilter.LengthFilter(MaxTileName))
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                    imeOptions = EditorInfo.IME_ACTION_DONE
                    setTextColor(android.graphics.Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    typeface = runCatching { ResourcesCompat.getFont(context, R.font.inter_medium) }.getOrNull()
                    background = null
                    val padH = (16 * resources.displayMetrics.density).toInt()
                    val padV = (12 * resources.displayMetrics.density).toInt()
                    setPadding(padH, padV, padH, padV)
                    setOnEditorActionListener { view, action, _ ->
                        if (action == EditorInfo.IME_ACTION_DONE) save(view.text.toString())
                        action == EditorInfo.IME_ACTION_DONE
                    }
                }.also { field = it }
            },
            modifier = Modifier.fillMaxWidth().clip(ItemShape).background(Color.White.copy(alpha = 0.10f)),
        )
        Spacer(Modifier.height(8.dp))
        MenuItem("Save") { save(field?.text?.toString().orEmpty()) }
        MenuItem("Cancel", onClick = onDismiss)
    }
    LaunchedEffect(field) {
        val edit = field ?: return@LaunchedEffect
        edit.requestFocus()
        // Only once the dialog's window has focus: Fire OS ignores the request before that.
        val showKeyboard = {
            edit.context.getSystemService(InputMethodManager::class.java)?.showSoftInput(edit, 0)
            Unit
        }
        if (edit.hasWindowFocus()) {
            edit.post(showKeyboard)
        } else {
            edit.viewTreeObserver.addOnWindowFocusChangeListener(object : ViewTreeObserver.OnWindowFocusChangeListener {
                override fun onWindowFocusChanged(hasFocus: Boolean) {
                    if (!hasFocus) return
                    edit.viewTreeObserver.removeOnWindowFocusChangeListener(this)
                    edit.post(showKeyboard)
                }
            })
        }
    }
}

/** Version and who made it. Close has focus, so Back and OK both leave. */
@Composable
fun AboutMenu(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val info = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull() }
    val close = remember { FocusRequester() }
    MenuDialog(onDismiss, width = 420) {
        PanelTitle("About")
        AboutRow("Version", info?.let { "${it.versionName} (build ${it.longVersionCode})" } ?: "Unknown")
        AboutRow("Installed", info?.let { formatDate(it.lastUpdateTime) } ?: "Unknown")
        AboutRow("Developer", "Shivam Koul")
        AboutRow("Email", "koul@duck.com")
        Spacer(Modifier.height(12.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            MenuItem("Close", Modifier.width(180.dp).focusRequester(close), centered = true, onClick = onDismiss)
        }
    }
    LaunchedEffect(Unit) { runCatching { close.requestFocus() } }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 16.dp),
    ) {
        Text(label, style = LauncherText.Body, color = LauncherColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(value, style = LauncherText.Body, color = LauncherColors.TextPrimary)
    }
}

private fun formatDate(millis: Long): String {
    val locale = Locale.getDefault()
    return SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "dMMMyyyy"), locale).format(Date(millis))
}

/** Shows what a valid backup holds before it replaces anything. Cancel has focus first. */
@Composable
fun ImportConfirmMenu(summary: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val cancel = remember { FocusRequester() }
    MenuDialog(onCancel, width = 460) {
        PanelTitle("Import backup?")
        Text(
            summary,
            style = LauncherText.Body,
            color = LauncherColors.TextSecondary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        )
        MenuItem("Cancel", Modifier.focusRequester(cancel), onClick = onCancel)
        MenuItem("Replace my setup", onClick = onConfirm)
    }
    LaunchedEffect(Unit) { runCatching { cancel.requestFocus() } }
}

/**
 * "Label        Value" on one row, with an optional colour swatch before the value. With
 * [onStep], left and right step the value back and forth (-1, +1) instead of leaving the row,
 * and arrows either side of the value say so.
 */
@Composable
private fun SettingItem(
    label: String,
    value: String,
    swatch: Color? = null,
    modifier: Modifier = Modifier,
    onStep: ((Int) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val stepKeys = if (onStep == null) Modifier else Modifier.onKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        val (delta, sound) = when (event.key) {
            Key.DirectionLeft -> -1 to SoundEffectConstants.NAVIGATION_LEFT
            Key.DirectionRight -> 1 to SoundEffectConstants.NAVIGATION_RIGHT
            else -> return@onKeyEvent false
        }
        view.playSoundEffect(sound)
        onStep(delta)
        true
    }
    FocusRow(modifier = modifier.then(stepKeys).height(48.dp), onClick = onClick) { focused ->
        val color = if (focused) LauncherColors.Ink else LauncherColors.TextPrimary
        val valueColor = if (focused) LauncherColors.Ink else LauncherColors.TextSecondary
        Text(label, style = LauncherText.Body, color = color, modifier = Modifier.weight(1f))
        if (swatch != null) {
            Box(Modifier.size(12.dp).clip(PillShape).background(swatch))
            Spacer(Modifier.width(8.dp))
        }
        if (onStep != null) Text("‹", style = LauncherText.Body, color = valueColor, modifier = Modifier.padding(end = 12.dp))
        Text(value, style = LauncherText.Body, color = valueColor)
        if (onStep != null) Text("›", style = LauncherText.Body, color = valueColor, modifier = Modifier.padding(start = 12.dp))
    }
}

/**
 * A row that opens another panel: an arrow on the right says so, after an optional [hint] of
 * what's there (a count, or the panel's contents).
 */
@Composable
private fun NavItem(text: String, modifier: Modifier = Modifier, hint: String? = null, onClick: () -> Unit) {
    FocusRow(modifier = modifier.height(48.dp), onClick = onClick) { focused ->
        val secondary = if (focused) LauncherColors.Ink else LauncherColors.TextSecondary
        Text(text, style = LauncherText.Body, color = if (focused) LauncherColors.Ink else LauncherColors.TextPrimary, modifier = Modifier.weight(1f))
        if (hint != null) Text(hint, style = LauncherText.Body, color = secondary, maxLines = 1)
        Text("›", style = LauncherText.Body, color = secondary, modifier = Modifier.padding(start = 12.dp))
    }
}

/** A thin rule between groups of items. Not focusable, so the D-pad steps straight over it. */
@Composable
private fun MenuDivider() {
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.14f)),
    )
}

@Composable
private fun MenuDialog(onDismiss: () -> Unit, width: Int = 360, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .width(width.dp)
                .glass(PanelShape, fill = Glass.PanelFill)
                .padding(vertical = 16.dp, horizontal = 12.dp)
                .navigationSounds()
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
private fun MenuItem(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, centered: Boolean = false, onClick: () -> Unit) {
    FocusRow(
        modifier = modifier.height(48.dp),
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
        onClick = { if (enabled) onClick() },
    ) { focused ->
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
private fun FocusRow(
    modifier: Modifier,
    onClick: () -> Unit,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable RowScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        horizontalArrangement = horizontalArrangement,
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
