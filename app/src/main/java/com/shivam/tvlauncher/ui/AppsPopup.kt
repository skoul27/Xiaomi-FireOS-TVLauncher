package com.shivam.tvlauncher.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.tv.material3.Text
import com.shivam.tvlauncher.ColorKey
import com.shivam.tvlauncher.LaunchableApp
import com.shivam.tvlauncher.LauncherUi
import com.shivam.tvlauncher.rememberAppArt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val RowHeight = 50.dp
private const val VisibleRows = 6
private val ListHeight = RowHeight * VisibleRows
private val IconShape = RoundedCornerShape(9.dp)

/**
 * Every visible app in a searchable list. OK opens, long-press OK opens the usual app menu.
 * The letter keys filter by name; a keyboard (or the Fire TV phone app) can type too.
 */
@Composable
fun AllAppsPopup(ui: LauncherUi, actions: LauncherActions, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val matches = remember(ui.all, query) { filterApps(ui.all, query) }

    PopupDialog(onDismiss, closeKey = ColorKey.Blue, width = 600.dp, onType = { query = it(query) }) { close ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(40.dp)) {
            Text("All apps", style = LauncherText.Title, color = LauncherColors.TextPrimary)
            Spacer(Modifier.width(18.dp))
            SearchField(query, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            KeyCap("⌫", width = 46.dp) { query = query.dropLast(1) }
            Spacer(Modifier.width(6.dp))
            KeyCap("Clear", width = 66.dp) { query = "" }
        }
        Spacer(Modifier.height(10.dp))
        LetterKeys { query += it }
        Spacer(Modifier.height(14.dp))
        AppList(
            apps = matches,
            resetKey = query,
            emptyText = if (query.isEmpty()) "No apps." else "No apps match “$query”.",
            onClick = { app -> actions.launch(app); close() },
            onLongClick = { app -> menuFor = app.packageName },
        ) { app, focused -> AppRowContent(app, focused, trailing = null) }
    }

    val menuApp = menuFor?.let { pkg -> ui.all.firstOrNull { it.packageName == pkg } }
    if (menuFor != null && menuApp == null) LaunchedEffect(Unit) { menuFor = null }
    if (menuApp != null) {
        val isPinned = ui.pinned.any { it.packageName == menuApp.packageName }
        AppMenu(
            app = menuApp,
            isPinned = isPinned,
            canMoveLeft = false,
            canMoveRight = false,
            showMove = false,
            onAction = { action ->
                when (action) {
                    MenuAction.TogglePin -> if (isPinned) actions.unpin(menuApp) else actions.pin(menuApp)
                    MenuAction.Hide -> actions.hide(menuApp)
                    MenuAction.AppInfo -> actions.openAppInfo(menuApp)
                    MenuAction.Uninstall -> actions.uninstall(menuApp)
                    MenuAction.MoveLeft, MenuAction.MoveRight -> {}
                }
                menuFor = null
            },
            onDismiss = { menuFor = null },
        )
    }
}

/** Hidden apps in the same list style; OK shows the app again. */
@Composable
fun HiddenAppsPopup(apps: List<LaunchableApp>, onUnhide: (LaunchableApp) -> Unit, onDismiss: () -> Unit) {
    PopupDialog(onDismiss, closeKey = ColorKey.Yellow, width = 520.dp) { close ->
        Text("Hidden apps", style = LauncherText.Title, color = LauncherColors.TextPrimary)
        Text(
            if (apps.isEmpty()) "Nothing is hidden. Long-press OK on an app and choose Hide." else "Select an app to show it again.",
            style = LauncherText.Caption,
            color = LauncherColors.TextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
        )
        if (apps.isEmpty()) {
            // Something focusable, so the color button and OK still reach this window.
            val closeButton = remember { FocusRequester() }
            GlassButton(onClick = close, modifier = Modifier.height(40.dp).focusRequester(closeButton)) { focused ->
                Text("Close", style = LauncherText.Button, color = buttonContentColor(focused))
            }
            LaunchedEffect(Unit) { runCatching { closeButton.requestFocus() } }
        } else {
            AppList(apps = apps, emptyText = "", onClick = onUnhide, onLongClick = null) { app, focused ->
                AppRowContent(app, focused, trailing = "Unhide")
            }
        }
    }
}

/** Labels that start with the query (or have a word that does) first, then any other match. */
private fun filterApps(apps: List<LaunchableApp>, query: String): List<LaunchableApp> {
    if (query.isBlank()) return apps
    val q = query.trim()
    val (prefix, rest) = apps.filter { it.label.contains(q, ignoreCase = true) }
        .partition { app -> app.label.split(' ', '-', '.').any { it.startsWith(q, ignoreCase = true) } }
    return prefix + rest
}

/**
 * A centred glass panel in its own window: fades in with a slight scale, and plays the reverse
 * before it goes. Back closes it; so does [closeKey], so the button that opened it toggles it.
 * [onType] receives printable keys from a hardware or phone keyboard as an edit to apply.
 */
@Composable
private fun PopupDialog(
    onDismiss: () -> Unit,
    closeKey: ColorKey?,
    width: Dp,
    onType: ((edit: (String) -> String) -> Unit)? = null,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val animate = LocalAnimationsEnabled.current
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    var closing by remember { mutableStateOf(false) }
    val close: () -> Unit = remember(animate) {
        {
            if (!closing) {
                closing = true
                scope.launch {
                    progress.animateTo(0f, motionTween(animate, 130))
                    dismiss()
                }
            }
        }
    }
    LaunchedEffect(Unit) { progress.animateTo(1f, motionTween(animate, 200)) }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // This panel animates itself; the platform's window animation would double it.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setWindowAnimations(0) }
        Column(
            modifier = Modifier
                .width(width)
                .graphicsLayer {
                    val p = progress.value
                    alpha = p
                    scaleX = 0.94f + 0.06f * p
                    scaleY = 0.94f + 0.06f * p
                }
                .glass(PanelShape, fill = Glass.PopupFill)
                .padding(horizontal = 22.dp, vertical = 20.dp)
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val isDown = event.type == KeyEventType.KeyDown
                    when {
                        closing -> true
                        closeKey != null && native.keyCode == closeKey.keyCode -> {
                            if (isDown && native.repeatCount == 0) close()
                            true
                        }
                        onType != null && isDown && native.keyCode == AndroidKeyEvent.KEYCODE_DEL -> {
                            onType { it.dropLast(1) }
                            true
                        }
                        onType != null && isDown && native.isPrintingKey && !native.isSystem && native.unicodeChar != 0 &&
                            !event.isSelectKey() && native.keyCode != AndroidKeyEvent.KEYCODE_SPACE -> {
                            val c = native.unicodeChar.toChar()
                            onType { it + c }
                            true
                        }
                        else -> false
                    }
                },
        ) {
            content(close)
        }
    }
}

/** Shows the query, or a placeholder; not focusable, the keys below drive it. */
@Composable
private fun SearchField(query: String, modifier: Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(38.dp).glass(PillShape).padding(horizontal = 16.dp),
    ) {
        Text(
            if (query.isEmpty()) "Search" else query,
            style = LauncherText.Body,
            color = if (query.isEmpty()) LauncherColors.TextTertiary else LauncherColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (query.isNotEmpty()) Text("|", style = LauncherText.Body, color = LocalAccent.current)
    }
}

/** A–M over N–Z: any letter is at most a few presses from any other. */
@Composable
private fun LetterKeys(onLetter: (Char) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        for (row in listOf('A'..'M', 'N'..'Z')) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                for (c in row) KeyCap(c.toString(), width = 36.dp) { onLetter(c) }
            }
        }
    }
}

/** One on-screen key: glass at rest, solid white when focused, like the other buttons. */
@Composable
private fun KeyCap(text: String, width: Dp, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width, 34.dp)
            .onFocusChanged { focused = it.isFocused }
            .remoteClickable(onClick)
            .glass(ItemShape, fill = if (focused) LauncherColors.TextPrimary else Glass.Fill, border = if (focused) Color.Transparent else Glass.Border),
    ) {
        Text(text, style = LauncherText.Label, color = buttonContentColor(focused))
    }
}

/**
 * A fixed-height list, so the panel doesn't jump as the search narrows it. Starts focused on the
 * first row; when the focused row disappears (hidden, unhidden) focus moves to its neighbour.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppList(
    apps: List<LaunchableApp>,
    emptyText: String,
    /** When this changes (a new search) the list jumps back to its best match. */
    resetKey: Any? = null,
    onClick: (LaunchableApp) -> Unit,
    onLongClick: ((LaunchableApp) -> Unit)?,
    rowContent: @Composable RowScope.(LaunchableApp, focused: Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    val target = remember { FocusRequester() }
    var focusedKey by remember { mutableStateOf<String?>(null) }
    val currentApps by rememberUpdatedState(apps)
    var focusedIndex by remember { mutableStateOf(0) }
    // Row to focus once composed: the first on open, a neighbour after the focused row leaves.
    var refocusKey by remember { mutableStateOf(apps.firstOrNull()?.key) }
    val margin = with(LocalDensity.current) { RowHeight.toPx() }
    val animate = LocalAnimationsEnabled.current
    val scrollSpec = remember(margin, animate) { ComfortableScrollSpec(margin, animate) }

    // Lazy lists keep their place by key; a new search should start from the top instead.
    LaunchedEffect(resetKey) { listState.scrollToItem(0) }

    LaunchedEffect(apps) {
        val key = focusedKey ?: return@LaunchedEffect
        if (apps.none { it.key == key } && apps.isNotEmpty()) {
            refocusKey = apps[focusedIndex.coerceAtMost(apps.lastIndex)].key
        }
    }

    Box(Modifier.fillMaxWidth().height(ListHeight)) {
        if (apps.isEmpty()) {
            Text(emptyText, style = LauncherText.Body, color = LauncherColors.TextSecondary, modifier = Modifier.padding(16.dp))
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides scrollSpec) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ListHeight)
                    // Focus moved out to the keys: a row leaving now (search narrowing) is no reason
                    // to pull focus back. A focused row being removed leaves it set, since its key
                    // is already gone from the list by then.
                    .onFocusChanged { state ->
                        if (!state.hasFocus && currentApps.any { it.key == focusedKey }) focusedKey = null
                    },
            ) {
                itemsIndexed(apps, key = { _, app -> app.key }) { index, app ->
                    var focused by remember { mutableStateOf(false) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(RowHeight)
                            .then(if (app.key == refocusKey) Modifier.focusRequester(target) else Modifier)
                            .onFocusChanged {
                                focused = it.isFocused
                                if (it.isFocused) {
                                    focusedKey = app.key
                                    focusedIndex = index
                                }
                            }
                            .remoteClickable(onClick = { onClick(app) }, onLongClick = onLongClick?.let { { it(app) } })
                            .clip(ItemShape)
                            .background(if (focused) Glass.FillFocused else Color.Transparent)
                            .border(1.dp, if (focused) Glass.BorderFocused else Color.Transparent, ItemShape)
                            .padding(horizontal = 12.dp),
                    ) {
                        rowContent(app, focused)
                    }
                }
            }
        }
    }

    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(refocusKey, apps) {
        val key = refocusKey ?: return@LaunchedEffect
        val index = apps.indexOfFirst { it.key == key }
        if (index < 0) return@LaunchedEffect
        // A menu closing over us hands focus back to this window first; wait, or it wins.
        snapshotFlow { windowInfo.isWindowFocused }.first { it }
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.scrollToItem(index)
        withFrameNanos { }
        runCatching { target.requestFocus() }
        refocusKey = null
    }
}

@Composable
private fun RowScope.AppRowContent(app: LaunchableApp, focused: Boolean, trailing: String?) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(36.dp).clip(IconShape).background(Glass.TileBase),
    ) {
        rememberAppArt(app)?.icon?.let {
            Image(it, contentDescription = null, filterQuality = FilterQuality.Low, modifier = Modifier.size(30.dp))
        }
    }
    Spacer(Modifier.width(14.dp))
    Text(
        app.label,
        style = LauncherText.Body,
        color = LauncherColors.TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
    if (trailing != null && focused) {
        Text(trailing, style = LauncherText.Caption, color = LauncherColors.TextSecondary)
    }
}
