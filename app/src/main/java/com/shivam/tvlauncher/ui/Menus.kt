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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
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

enum class MenuAction { TogglePin, Move, Hide, AppInfo, Uninstall, Close, CloseAll }

/** A tile's two menus: long-press OK arranges it, the remote's Menu (≡) button manages the app. */
enum class AppMenuKind { Arrange, Manage }

/**
 * One tile's menu: the app's artwork and name on top, then actions with icons.
 * Arrange (long-press OK): add or remove from My Apps, Move (where order is the user's), Hide.
 * Manage (Menu button): Close app, Close all apps, App info, Uninstall. Every action closes it.
 */
@Composable
fun AppMenu(
    app: LaunchableApp,
    isPinned: Boolean,
    kind: AppMenuKind,
    /** False where order isn't the user's (the All apps list is alphabetical) or nothing to swap with. */
    canMove: Boolean,
    onAction: (MenuAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    MenuDialog(onDismiss, width = 380) {
        AppMenuHeader(
            app,
            subtitle = when {
                kind == AppMenuKind.Manage -> "App options"
                isPinned -> "In My Apps"
                else -> "Not in My Apps"
            },
        )
        MenuDivider()
        when (kind) {
            AppMenuKind.Arrange -> {
                ActionItem(
                    if (isPinned) MenuIcons.Remove else MenuIcons.Add,
                    if (isPinned) "Remove from My Apps" else "Add to My Apps",
                    Modifier.focusRequester(first),
                ) { onAction(MenuAction.TogglePin) }
                if (canMove) ActionItem(MenuIcons.Move, "Move") { onAction(MenuAction.Move) }
                ActionItem(MenuIcons.Hide, "Hide") { onAction(MenuAction.Hide) }
            }
            AppMenuKind.Manage -> {
                ActionItem(MenuIcons.Close, "Close app", Modifier.focusRequester(first)) { onAction(MenuAction.Close) }
                ActionItem(MenuIcons.CloseAll, "Close all apps") { onAction(MenuAction.CloseAll) }
                MenuDivider()
                ActionItem(MenuIcons.Info, "App info") { onAction(MenuAction.AppInfo) }
                if (!app.isSystem) ActionItem(MenuIcons.Delete, "Uninstall", danger = true) { onAction(MenuAction.Uninstall) }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/** The app's tile art beside its name, so the menu shows which app it's about. */
@Composable
private fun AppMenuHeader(app: LaunchableApp, subtitle: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 6.dp),
    ) {
        Box(Modifier.size(96.dp, 54.dp).clip(RoundedCornerShape(10.dp)).background(Glass.TileBase)) {
            rememberAppArt(app)?.tile?.let {
                Image(it, null, contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Low, modifier = Modifier.size(96.dp, 54.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(app.label, style = LauncherText.Title, color = LauncherColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = LauncherText.Caption, color = LauncherColors.TextSecondary, maxLines = 1)
        }
    }
}

/** A menu row with a leading icon. [danger] tints it red at rest (Uninstall). */
@Composable
private fun ActionItem(icon: Path, text: String, modifier: Modifier = Modifier, danger: Boolean = false, onClick: () -> Unit) {
    FocusRow(modifier = modifier.height(50.dp), onClick = onClick) { focused ->
        val color = when {
            focused -> LauncherColors.Ink
            danger -> DangerColor
            else -> LauncherColors.TextPrimary
        }
        PathIcon(icon, color, size = 20.dp)
        Spacer(Modifier.width(14.dp))
        Text(text, style = LauncherText.Body, color = color)
    }
}

private val DangerColor = Color(0xFFFF8A80)

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
    MenuDialog(onDismiss, width = if (shortcutOff) 480 else 400) {
        PanelHeader("Settings", "Apps, launcher and Fire OS") { GearIcon(it, size = 26.dp) }
        if (key != null && shortcutOff) ShortcutOffNotice(key, remoteShortcut.enableCommands)
        NavItem(
            "Hidden apps",
            when (hiddenCount) {
                0 -> "None hidden right now"
                1 -> "1 app you've hidden"
                else -> "$hiddenCount apps you've hidden"
            },
            Modifier.focusRequester(first),
            icon = MenuIcons.Hide,
            onClick = onHiddenApps,
        )
        NavItem("Launcher settings", "Theme, shortcuts, console tiles and backup", icon = MenuIcons.Tune, onClick = onLauncherSettings)
        if (shortcuts.isEmpty()) {
            Text(
                "No Fire OS settings screens are available right now.",
                style = LauncherText.Body,
                color = LauncherColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        for (shortcut in shortcuts) MenuItem(shortcut.label, icon = MenuIcons.OpenExternal) { onOpen(shortcut) }
        MenuDivider()
        NavItem("About", "Version and developer", icon = MenuIcons.Info, onClick = onAbout)
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

/** Where Launcher settings leads. Back from one of them puts focus on its row again. */
enum class LauncherSection { Theme, Shortcuts, ConsoleTiles, More }

/** This launcher's own settings: three groups, each a panel of its own. */
@Composable
fun LauncherSettingsMenu(
    returnedFrom: LauncherSection?,
    onOpen: (LauncherSection) -> Unit,
    onDismiss: () -> Unit,
) {
    val rows = remember { LauncherSection.entries.associateWith { FocusRequester() } }
    MenuDialog(onDismiss, width = 460) {
        PanelHeader("Launcher settings", "Make this launcher yours", MenuIcons.Tune)
        NavItem("Theme", "Accent colour and animations", Modifier.focusRequester(rows.getValue(LauncherSection.Theme)), MenuIcons.Palette) {
            onOpen(LauncherSection.Theme)
        }
        NavItem("Shortcuts", "Home button and remote shortcut", Modifier.focusRequester(rows.getValue(LauncherSection.Shortcuts)), MenuIcons.Remote) {
            onOpen(LauncherSection.Shortcuts)
        }
        NavItem("Console tiles", "A card for each HDMI port", Modifier.focusRequester(rows.getValue(LauncherSection.ConsoleTiles)), MenuIcons.Gamepad) {
            onOpen(LauncherSection.ConsoleTiles)
        }
        NavItem("More settings", "Export or import a backup", Modifier.focusRequester(rows.getValue(LauncherSection.More)), MenuIcons.Cloud) {
            onOpen(LauncherSection.More)
        }
    }
    LaunchedEffect(Unit) { runCatching { rows.getValue(returnedFrom ?: LauncherSection.Theme).requestFocus() } }
}

/** Accent colour and animations. Left and right change a value. */
@Composable
fun ThemeMenu(accent: Accent, animations: Boolean, actions: LauncherActions, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    MenuDialog(onDismiss, width = 460) {
        PanelHeader("Theme", "Colour and motion", MenuIcons.Palette)
        val stepAccent = { delta: Int -> actions.setTheme(Accent.entries.stepFrom(accent, delta)) }
        SettingItem("Accent", accent.label, swatch = accent.color, icon = MenuIcons.Palette, modifier = Modifier.focusRequester(first), onStep = stepAccent)
        SettingItem("Animations", if (animations) "On" else "Off", icon = MenuIcons.Sparkle, onStep = { actions.setAnimations(!animations) })
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/** What the Home button and a colour button on the remote do. Left and right change a value. */
@Composable
fun ShortcutsMenu(homeRedirect: Boolean, remoteShortcut: RemoteShortcut, actions: LauncherActions, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    MenuDialog(onDismiss, width = 460) {
        PanelHeader("Shortcuts", "Home and remote buttons", MenuIcons.Remote)
        // Rides on the remote shortcut's service, so it needs that turned on too.
        SettingItem(
            "Home button",
            if (homeRedirect) "This launcher" else "Fire TV home",
            icon = MenuIcons.Home,
            modifier = Modifier.focusRequester(first),
            onStep = { actions.setHomeRedirect(!homeRedirect) },
        )
        val key = remoteShortcut.key
        val order = listOf(null) + ColorKey.entries
        SettingItem(
            "Remote shortcut",
            key?.label ?: "Off",
            swatch = key?.swatch(),
            icon = MenuIcons.Remote,
            onStep = { delta -> actions.setShortcutKey(order.stepFrom(key, delta)) },
        )
        // Only worth a line when the shortcut is chosen but can't work outside the launcher.
        if (key != null && !remoteShortcut.serviceOn) {
            Text(
                "Works inside the launcher only: \"${stringResource(R.string.shortcut_service_label)}\" isn't turned on.",
                style = LauncherText.Caption,
                color = LauncherColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/** The colour of the button on the remote, for the swatch beside its name. */
private fun ColorKey.swatch(): Color = when (this) {
    ColorKey.Red -> Color(0xFFE53935)
    ColorKey.Green -> Color(0xFF43A047)
    ColorKey.Yellow -> Color(0xFFFDD835)
    ColorKey.Blue -> Color(0xFF1E88E5)
}

/** The entry [delta] places along from [current], wrapping at both ends. */
private fun <T> List<T>.stepFrom(current: T, delta: Int): T = this[(indexOf(current) + delta).mod(size)]

/** Export or import the setup file, with the result of the last one underneath. */
@Composable
fun MoreMenu(message: String?, actions: LauncherActions, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    MenuDialog(onDismiss, width = 460) {
        PanelHeader("More settings", "Backup and restore", MenuIcons.Cloud)
        Text(
            "Your My Apps, hidden apps, console tiles, theme and settings, as one file on this TV. " +
                "Copying it to or from a computer needs adb.",
            style = LauncherText.Caption,
            color = LauncherColors.TextSecondary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        MenuItem("Export backup", Modifier.focusRequester(first), icon = MenuIcons.Upload, onClick = actions::exportBackup)
        MenuItem("Import backup", icon = MenuIcons.Download, onClick = actions::importBackup)
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
 * One row per HDMI port; left and right choose its tile. An Other tile gets a Name row under it. Opened
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
        PanelHeader("Console tiles", "One card per HDMI port", MenuIcons.Gamepad)
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
            SettingItem(
                port.device?.let { "${port.label} · $it" } ?: port.label,
                tile.mode.label,
                icon = MenuIcons.Input,
                modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                onStep = { delta -> onSet(port, tile.copy(mode = TileMode.entries.stepFrom(tile.mode, delta))) },
            )
            if (tile.mode == TileMode.Other) {
                // OK opens the keyboard; no arrows, since there's nothing to step through.
                SettingItem("Name", tile.name.ifBlank { port.device ?: port.label }, icon = MenuIcons.Edit, indent = true, onClick = { onRename(port) })
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
        PanelHeader("Name the ${port.label} tile", "Shown on its console card", MenuIcons.Edit)
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
                    typeface = runCatching { ResourcesCompat.getFont(context, R.font.jakarta_medium) }.getOrNull()
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
        MenuItem("Save", icon = MenuIcons.Check) { save(field?.text?.toString().orEmpty()) }
        MenuItem("Cancel", icon = MenuIcons.Close, onClick = onDismiss)
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
        PanelHeader(stringResource(R.string.app_name), "Version and developer", MenuIcons.Info)
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
        PanelHeader("Import backup?", "This replaces your current setup", MenuIcons.Download)
        Text(
            summary,
            style = LauncherText.Body,
            color = LauncherColors.TextSecondary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        )
        MenuItem("Cancel", Modifier.focusRequester(cancel), icon = MenuIcons.Close, onClick = onCancel)
        MenuItem("Replace my setup", icon = MenuIcons.Check, onClick = onConfirm)
    }
    LaunchedEffect(Unit) { runCatching { cancel.requestFocus() } }
}

/**
 * "Label        Value" on one row, with an optional colour swatch before the value. Either
 * [onStep]: left and right step the value back and forth (-1, +1), ‹ › either side of the value
 * say so, and OK does nothing; or [onClick]: OK acts (renaming, say) and there are no arrows.
 */
@Composable
private fun SettingItem(
    label: String,
    value: String,
    swatch: Color? = null,
    modifier: Modifier = Modifier,
    icon: Path? = null,
    /** Sits under the row before it (an Other tile's Name under its port). */
    indent: Boolean = false,
    onStep: ((Int) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
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
    FocusRow(modifier = modifier.then(stepKeys).height(50.dp), onClick = onClick) { focused ->
        val color = if (focused) LauncherColors.Ink else LauncherColors.TextPrimary
        val valueColor = if (focused) LauncherColors.Ink else LauncherColors.TextSecondary
        if (indent) Spacer(Modifier.width(34.dp))
        RowIcon(icon, color)
        Text(label, style = LauncherText.Body, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (swatch != null) {
            Box(Modifier.size(12.dp).clip(PillShape).background(swatch))
            Spacer(Modifier.width(8.dp))
        }
        // The only rows with arrows: they mean "left/right change this".
        if (onStep != null) Text("‹", style = LauncherText.Body, color = valueColor, modifier = Modifier.padding(end = 12.dp))
        Text(value, style = LauncherText.Body, color = valueColor)
        if (onStep != null) Text("›", style = LauncherText.Body, color = valueColor, modifier = Modifier.padding(start = 12.dp))
    }
}

/**
 * A row that opens another panel: its name over a line saying what's in there. Two lines and
 * no arrow, so it can't be mistaken for a value that left/right change.
 */
@Composable
private fun NavItem(text: String, summary: String, modifier: Modifier = Modifier, icon: Path? = null, onClick: () -> Unit) {
    FocusRow(modifier = modifier.height(64.dp), onClick = onClick) { focused ->
        RowIcon(icon, if (focused) LauncherColors.Ink else LauncherColors.TextPrimary)
        Column {
            Text(text, style = LauncherText.Body, color = if (focused) LauncherColors.Ink else LauncherColors.TextPrimary)
            Text(
                summary,
                style = LauncherText.Caption,
                color = if (focused) LauncherColors.Ink.copy(alpha = 0.7f) else LauncherColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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

/** Every menu panel: springs up from slightly small and transparent as it opens. */
@Composable
private fun MenuDialog(onDismiss: () -> Unit, width: Int = 360, content: @Composable ColumnScope.() -> Unit) {
    val animate = LocalAnimationsEnabled.current
    val appear = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 520f)) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .width(width.dp)
                .graphicsLayer {
                    val t = appear.value
                    val s = 0.9f + 0.1f * t
                    scaleX = s
                    scaleY = s
                    alpha = t.coerceIn(0f, 1f)
                }
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

/**
 * Every panel's header, matching the app menus': an accent-tinted tile holding the panel's icon
 * beside its title and a line about it, then a divider. [trailing] fills the rest of the row
 * (the All apps search).
 */
@Composable
fun PanelHeader(
    title: String,
    subtitle: String,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    icon: @Composable (Color) -> Unit,
) {
    val accent = LocalAccent.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.16f)),
        ) { icon(accent) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = LauncherText.Title, color = LauncherColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = LauncherText.Caption, color = LauncherColors.TextSecondary, maxLines = 1)
        }
        if (trailing != null) {
            Spacer(Modifier.width(20.dp))
            trailing()
        }
    }
    MenuDivider()
}

@Composable
fun PanelHeader(title: String, subtitle: String, icon: Path) =
    PanelHeader(title, subtitle) { PathIcon(icon, it, size = 26.dp) }

/** A row's leading icon, in the row's text colour, with the gap after it. */
@Composable
private fun RowIcon(icon: Path?, color: Color) {
    if (icon == null) return
    PathIcon(icon, color, size = 20.dp)
    Spacer(Modifier.width(14.dp))
}

@Composable
private fun MenuItem(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    centered: Boolean = false,
    icon: Path? = null,
    onClick: () -> Unit,
) {
    FocusRow(
        modifier = modifier.height(50.dp),
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
        onClick = { if (enabled) onClick() },
    ) { focused ->
        val color = when {
            focused -> LauncherColors.Ink
            enabled -> LauncherColors.TextPrimary
            else -> LauncherColors.TextTertiary
        }
        RowIcon(icon, color)
        Text(text, style = LauncherText.Body, color = color)
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

/**
 * A menu row that turns white when focused. Dialogs are small, so plain state is fine here.
 * With no [onClick] it only takes focus, and OK does nothing on it.
 */
@Composable
private fun FocusRow(
    modifier: Modifier,
    onClick: (() -> Unit)?,
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
            .then(if (onClick != null) Modifier.remoteClickable(onClick) else Modifier.focusable())
            .clip(ItemShape)
            .background(if (focused) LauncherColors.TextPrimary else Color.Transparent)
            .padding(horizontal = 16.dp),
    ) {
        content(focused)
    }
}
