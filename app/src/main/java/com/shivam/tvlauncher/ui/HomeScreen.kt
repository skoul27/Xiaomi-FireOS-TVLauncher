package com.shivam.tvlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.shivam.tvlauncher.BackupState
import com.shivam.tvlauncher.ColorKey
import com.shivam.tvlauncher.Console
import com.shivam.tvlauncher.HdmiPort
import com.shivam.tvlauncher.LaunchableApp
import com.shivam.tvlauncher.NowPlaying
import com.shivam.tvlauncher.PortTile
import com.shivam.tvlauncher.TileMode
import com.shivam.tvlauncher.consolesFor
import com.shivam.tvlauncher.LauncherUi
import com.shivam.tvlauncher.RemoteShortcut
import com.shivam.tvlauncher.RowKind
import com.shivam.tvlauncher.SystemShortcut
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Everything the screen can ask the outside world to do. */
@Stable
interface LauncherActions {
    fun launch(app: LaunchableApp)
    fun pin(app: LaunchableApp)
    fun unpin(app: LaunchableApp)
    fun move(app: LaunchableApp, row: RowKind, delta: Int)
    fun hide(app: LaunchableApp)
    fun unhide(app: LaunchableApp)
    fun openAppInfo(app: LaunchableApp)
    fun uninstall(app: LaunchableApp)
    fun openShortcut(shortcut: SystemShortcut)
    fun openWifiSettings()
    fun openCast()
    fun openConsole(console: Console)
    fun refreshApps()
    fun setShortcutKey(key: ColorKey?)
    fun setTheme(accent: Accent)
    fun setAnimations(enabled: Boolean)
    fun setHomeRedirect(enabled: Boolean)
    fun setConsoleTile(inputId: String, tile: PortTile)
    /** Re-reads whether the shortcut service is on; the panels call it as they open. */
    fun recheckShortcut()
    fun exportBackup()
    /** Validates the backup file; a valid one comes back as [BackupState.ConfirmImport]. */
    fun importBackup()
    fun confirmImport()
    fun dismissBackup()
}

/**
 * Room around My Apps' tiles so a scaled tile and its glow aren't clipped by the LazyRow. It
 * overlaps the header and label space around the row instead of taking room of its own.
 */
private val RowBleed = 24.dp
private val EdgeFade = 40.dp
private val SectionHeaderHeight = 22.dp
private val HeaderGap = 12.dp
private val SectionGap = 28.dp

/** Label line under a tile: 10dp gap plus one line of [LauncherText.Label]. */
private val LabelHeight = 30.dp
private val RowHeight = TileSize.Standard.height + LabelHeight

private data class MenuTarget(val packageName: String, val row: RowKind)

private enum class AppsPopup { All, Hidden }

private enum class SettingsPanel { Launcher, Theme, Shortcuts, More, ConsoleTiles }

private fun LauncherSection.panel(): SettingsPanel = when (this) {
    LauncherSection.Theme -> SettingsPanel.Theme
    LauncherSection.Shortcuts -> SettingsPanel.Shortcuts
    LauncherSection.ConsoleTiles -> SettingsPanel.ConsoleTiles
    LauncherSection.More -> SettingsPanel.More
}

/**
 * One screen, no page scroll: the hero fills whatever the My Apps and Consoles sections below it
 * leave; the top bar floats top-right over the hero's empty corner. Both sections use one tile
 * size and one header style, so their columns line up.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    ui: LauncherUi?,
    shortcuts: List<SystemShortcut>,
    remoteShortcut: RemoteShortcut,
    homeRedirect: Boolean,
    ports: List<HdmiPort>,
    consoleTiles: Map<String, PortTile>,
    homePresses: Int,
    opens: Int,
    backup: BackupState?,
    actions: LauncherActions,
) {
    val scope = rememberCoroutineScope()
    val animate = LocalAnimationsEnabled.current
    val backdrop = remember { BackdropState(scope) }
    val hero = remember { HeroState() }
    val initialFocus = remember { FocusRequester() }
    val allAppsButton = remember { FocusRequester() }
    val scrollMargin = with(LocalDensity.current) { SafeArea.Horizontal.toPx() }
    val rowScrollSpec = remember(scrollMargin, animate) { ComfortableScrollSpec(scrollMargin, animate) }
    var menu by remember { mutableStateOf<MenuTarget?>(null) }
    var popup by remember { mutableStateOf<AppsPopup?>(null) }
    var showSystem by remember { mutableStateOf(false) }
    // Launcher settings and the panels under it, newest last. Only the newest shows; Back closes it
    // and returns to the one before, with focus on the item that opened it.
    var panels by remember { mutableStateOf(emptyList<SettingsPanel>()) }
    var returnedFrom by remember { mutableStateOf<SettingsPanel?>(null) }
    val openPanel = { panel: SettingsPanel -> returnedFrom = null; panels = panels + panel }
    val closePanel = { returnedFrom = panels.lastOrNull(); panels = panels.dropLast(1) }
    var renaming by remember { mutableStateOf<HdmiPort?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    val consoles = remember(ports, consoleTiles) { consolesFor(ports, consoleTiles) }
    // Where focus should go once the UI catches up with an action that removes the focused tile.
    var pendingFocus by remember { mutableStateOf<MenuTarget?>(null) }
    val openMenu = remember { { app: LaunchableApp, row: RowKind -> menu = MenuTarget(app.packageName, row) } }
    val focusDone = remember { { pendingFocus = null } }
    val showInHero = remember { { app: LaunchableApp -> hero.app = app } }
    // The My Apps tile focused last, to come back to from the top bar or the Consoles row.
    var lastPinned by remember { mutableStateOf<String?>(null) }
    val pinnedFocused = remember { { app: LaunchableApp -> hero.app = app; lastPinned = app.packageName } }
    // Compose's own search sees the row as one block the width of the screen, and can pass over
    // it to the Consoles row, so entering it from above or below is done by hand: the tile left
    // last (or the first), scrolled into view. False with no pins, leaving the press to the default.
    val enterMyApps: () -> Boolean = {
        val pins = ui?.pinned.orEmpty()
        val target = pins.firstOrNull { it.packageName == lastPinned } ?: pins.firstOrNull()
        if (target != null) pendingFocus = MenuTarget(target.packageName, RowKind.Pinned)
        target != null
    }
    val hasPins = ui?.pinned?.isNotEmpty() == true
    // My Apps' first tile, or the All apps button when there are no pins.
    val focusStart = remember(hasPins) {
        { runCatching { (if (hasPins) initialFocus else allAppsButton).requestFocus() }; Unit }
    }

    // The Now playing card. While it has focus it stays, showing the last session, even if that
    // session ends: removing the focused card would leave focus nowhere.
    val playing = rememberNowPlaying()
    val nowPlayingFocus = remember { FocusRequester() }
    var nowPlayingFocused by remember { mutableStateOf(false) }
    val lastPlaying = remember { arrayOfNulls<NowPlaying>(1) }
    if (playing != null) lastPlaying[0] = playing
    val shownPlaying = playing ?: lastPlaying[0].takeIf { nowPlayingFocused }

    // Color buttons, from wherever focus is. Acts on the press; the release is just swallowed.
    // While a pop-up is open its own window gets the keys; each closes on the button that opened it.
    Box(
        Modifier
            .fillMaxSize()
            // Remember which way the D-pad moved, so the tile focus lands on can lean that way.
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    FocusDirection.horizontal = when (event.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> -1
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                        else -> 0
                    }
                }
                false
            }
            .navigationSounds()
            .onKeyEvent { event ->
            val color = ColorKey.fromKeyCode(event.nativeKeyEvent.keyCode) ?: return@onKeyEvent false
            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                when (color) {
                    ColorKey.Blue -> if (ui != null) popup = AppsPopup.All
                    ColorKey.Green -> showSystem = true
                    ColorKey.Yellow -> if (ui != null) popup = AppsPopup.Hidden
                    ColorKey.Red -> actions.refreshApps()
                }
            }
            true
        },
    ) {
        Backdrop(backdrop, Modifier.fillMaxSize())
        BackdropDriver(hero, backdrop)

        Column(Modifier.fillMaxSize()) {
            // The top row (clock, Now playing, buttons) floats over this; the backdrop shows through.
            Spacer(Modifier.weight(1f))

            SectionHeader("My Apps")
            Spacer(Modifier.height(HeaderGap))
            when {
                ui == null -> Spacer(Modifier.height(RowHeight))
                ui.pinned.isEmpty() -> EmptyMyAppsHint()
                else -> CompositionLocalProvider(LocalBringIntoViewSpec provides rowScrollSpec) {
                    AppRow(
                        ui.pinned, RowKind.Pinned, TileSize.Standard, actions, openMenu, pinnedFocused,
                        firstItemFocus = initialFocus,
                        pendingFocus = pendingFocus?.packageName,
                        onPendingFocusDone = focusDone,
                    )
                }
            }

            // Nothing to show (every console off and Automatic): no section, and the hero takes the room.
            if (consoles.isNotEmpty()) {
                Spacer(Modifier.height(SectionGap))
                SectionHeader("Consoles")
                Spacer(Modifier.height(HeaderGap))
                ConsolesRow(
                    consoles,
                    onOpen = actions::openConsole,
                    onLongPress = { panels = listOf(SettingsPanel.ConsoleTiles) },
                    modifier = Modifier.onDpad(Key.DirectionUp, enterMyApps),
                )
            }
            Spacer(Modifier.height(SafeArea.Vertical))
        }

        val topRow = Modifier.padding(top = SafeArea.Vertical, start = SafeArea.Horizontal, end = SafeArea.Horizontal)
        Hero(topRow.align(Alignment.TopStart))
        shownPlaying?.let {
            NowPlayingCard(
                it,
                nowPlayingFocus,
                Modifier
                    // Centred on the top bar's row, level with its buttons.
                    .padding(top = SafeArea.Vertical + (TopBarHeight - RoundButtonSize) / 2)
                    .align(Alignment.TopCenter)
                    .onFocusChanged { state -> nowPlayingFocused = state.hasFocus }
                    .onDpad(Key.DirectionDown, enterMyApps),
            )
        }

        TopBar(
            buttons = {
                // Ends of the bar: nothing beyond them, rather than a diagonal jump to a tile. With
                // Now playing showing, Left from All apps goes to its play/pause button.
                RoundIconButton(
                    { if (ui != null) popup = AppsPopup.All },
                    Modifier.focusRequester(allAppsButton).focusProperties {
                        if (shownPlaying == null) left = FocusRequester.Cancel
                    },
                ) {
                    GridIcon(it, size = 18.dp)
                }
                RoundIconButton(actions::openCast) { CastIcon(it, size = 21.dp) }
                NetworkButton(actions::openWifiSettings)
                RoundIconButton({ showSystem = true }, Modifier.focusProperties { right = FocusRequester.Cancel }) { GearIcon(it) }
            },
            modifier = topRow.onDpad(Key.DirectionDown, enterMyApps),
        )

        if (ui != null) {
            menu?.let { target ->
                AppMenuFor(ui, target, actions, onFocusMove = { pendingFocus = it }, onDismiss = { menu = null })
            }
            when (popup) {
                AppsPopup.All -> AllAppsPopup(ui, actions, onDismiss = { popup = null })
                AppsPopup.Hidden -> HiddenAppsPopup(ui.hidden, onUnhide = actions::unhide, onDismiss = { popup = null })
                null -> {}
            }
        }
        // Outside the ui check: Settings must be reachable even if the app list never loads.
        if (showSystem) {
            SystemMenu(
                shortcuts,
                remoteShortcut,
                hiddenCount = ui?.hidden?.size ?: 0,
                onHiddenApps = { showSystem = false; if (ui != null) popup = AppsPopup.Hidden },
                onOpen = { showSystem = false; actions.openShortcut(it) },
                onLauncherSettings = { showSystem = false; panels = listOf(SettingsPanel.Launcher) },
                onAbout = { showSystem = false; showAbout = true },
                onDismiss = { showSystem = false },
            )
        }
        if (showAbout) AboutMenu(onDismiss = { showAbout = false })
        // One glass panel at a time: two stacked read as clutter.
        when (panels.lastOrNull()) {
            SettingsPanel.Launcher -> LauncherSettingsMenu(
                returnedFrom = LauncherSection.entries.firstOrNull { it.panel() == returnedFrom },
                onOpen = { openPanel(it.panel()) },
                onDismiss = closePanel,
            )
            SettingsPanel.Theme -> ThemeMenu(LocalAccentTheme.current, animate, actions, onDismiss = closePanel)
            SettingsPanel.Shortcuts -> ShortcutsMenu(homeRedirect, remoteShortcut, actions, onDismiss = closePanel)
            SettingsPanel.ConsoleTiles -> {
                ConsoleTilesMenu(
                    ports,
                    consoleTiles,
                    onSet = { port, tile -> actions.setConsoleTile(port.inputId, tile) },
                    onRename = { renaming = it },
                    onDismiss = closePanel,
                )
                renaming?.let { port ->
                    val tile = consoleTiles[port.inputId] ?: PortTile(TileMode.Other)
                    RenameTileMenu(
                        port,
                        initial = tile.name,
                        onSave = { name ->
                            actions.setConsoleTile(port.inputId, tile.copy(name = name))
                            renaming = null
                        },
                        onDismiss = { renaming = null },
                    )
                }
            }
            SettingsPanel.More -> {
                MoreMenu(
                    message = (backup as? BackupState.Message)?.text,
                    actions = actions,
                    onDismiss = {
                        actions.dismissBackup()
                        closePanel()
                    },
                )
                (backup as? BackupState.ConfirmImport)?.let { pending ->
                    ImportConfirmMenu(pending.summary, onConfirm = actions::confirmImport, onCancel = actions::dismissBackup)
                }
            }
            null -> {}
        }

        OpenVeil(opens)
    }

    // The status can change while we're in front (a package update briefly drops the service),
    // so read it fresh whenever a panel that shows it opens.
    val shortcutsOpen = panels.lastOrNull() == SettingsPanel.Shortcuts
    LaunchedEffect(showSystem, shortcutsOpen) { if (showSystem || shortcutsOpen) actions.recheckShortcut() }

    // Home has nothing to go back to: Back returns to the start instead of leaving.
    BackHandler { focusStart() }

    // Home pressed while already home: close any overlay and go back to the start of My Apps.
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(homePresses) {
        if (homePresses == 0) return@LaunchedEffect
        menu = null
        popup = null
        showSystem = false
        if (panels.lastOrNull() == SettingsPanel.More) actions.dismissBackup()
        panels = emptyList()
        renaming = null
        showAbout = false
        // A closing dialog hands focus back to our window; wait for that, or it overrides us.
        snapshotFlow { windowInfo.isWindowFocused }.first { it }
        focusStart()
    }

    // Start on the first tile, and re-seat focus if My Apps empties under it. Keyed on
    // "loaded" and "has pins" only, so a resume keeps the user's place.
    LaunchedEffect(ui != null, hasPins) {
        if (ui != null) {
            if (hero.app == null) hero.app = ui.pinned.firstOrNull() ?: ui.all.firstOrNull()
            snapshotFlow { windowInfo.isWindowFocused }.first { it }
            withFrameNanos { }
            focusStart()
        }
    }
}

/**
 * Fades the screen in from black each time the launcher comes to the front. One full-screen
 * rect over everything, drawn only during the fade: cheaper than an alpha layer on the whole UI.
 */
@Composable
private fun OpenVeil(opens: Int) {
    val animate = LocalAnimationsEnabled.current
    val veil = remember { Animatable(0f) }
    LaunchedEffect(opens) {
        if (!animate || opens == 0) return@LaunchedEffect
        veil.snapTo(1f)
        veil.animateTo(0f, tween(320, easing = LinearOutSlowInEasing))
    }
    Box(
        Modifier.fillMaxSize().drawBehind {
            val v = veil.value
            if (v > 0f) drawRect(LauncherColors.Ink, alpha = v)
        },
    )
}

/**
 * A plain Row, not lazy: a TV has only a few HDMI ports, and a Row doesn't clip their glow, so it
 * needs no bleed. Two wide cards fit across; with more, all of them go compact.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ConsolesRow(consoles: List<Console>, onOpen: (Console) -> Unit, onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    val compact = consoles.size > 2
    Row(
        horizontalArrangement = Arrangement.spacedBy(TileSize.Console.gap),
        modifier = modifier.fillMaxWidth().padding(horizontal = SafeArea.Horizontal),
    ) {
        // Left and right stay in the row instead of jumping up to a tile or the top bar.
        consoles.forEachIndexed { index, console ->
            ConsoleTile(
                console,
                compact = compact,
                onClick = { onOpen(console) },
                onLongClick = onLongPress,
                modifier = Modifier.focusProperties {
                    if (index == 0) left = FocusRequester.Cancel
                    if (index == consoles.lastIndex) right = FocusRequester.Cancel
                },
            )
        }
    }
}



/** Resolves the target against the live UI so the menu reflects moves as they happen. */
@Composable
private fun AppMenuFor(
    ui: LauncherUi,
    target: MenuTarget,
    actions: LauncherActions,
    onFocusMove: (MenuTarget?) -> Unit,
    onDismiss: () -> Unit,
) {
    val row = ui.row(target.row)
    val index = row.indexOfFirst { it.packageName == target.packageName }
    val app = row.getOrNull(index)
    if (app == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val isPinned = ui.pinned.any { it.packageName == app.packageName }

    // The tile is about to leave this row: hand focus to its neighbour instead of letting it
    // fall back to the first focusable thing on screen.
    fun focusNeighbour() {
        val neighbour = row.getOrNull(index + 1) ?: row.getOrNull(index - 1)
        // Null when the row empties: the screen then moves focus to the All apps button.
        onFocusMove(neighbour?.let { MenuTarget(it.packageName, target.row) })
    }

    AppMenu(
        app = app,
        isPinned = isPinned,
        canMoveLeft = index > 0,
        canMoveRight = index < row.lastIndex,
        onAction = { action ->
            when (action) {
                MenuAction.TogglePin -> if (isPinned) {
                    if (target.row == RowKind.Pinned) focusNeighbour()
                    actions.unpin(app)
                } else {
                    actions.pin(app)
                }
                MenuAction.MoveLeft -> actions.move(app, target.row, -1)
                MenuAction.MoveRight -> actions.move(app, target.row, 1)
                MenuAction.Hide -> {
                    focusNeighbour()
                    actions.hide(app)
                }
                MenuAction.AppInfo -> actions.openAppInfo(app)
                MenuAction.Uninstall -> actions.uninstall(app)
            }
            if (action != MenuAction.MoveLeft && action != MenuAction.MoveRight) onDismiss()
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = LauncherText.Section,
        color = LauncherColors.TextSecondary,
        modifier = Modifier.height(SectionHeaderHeight).padding(horizontal = SafeArea.Horizontal),
    )
}

@Composable
private fun EmptyMyAppsHint() {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .padding(horizontal = SafeArea.Horizontal),
    ) {
        Text(
            "Long-press OK on any app below to add it to My Apps.",
            style = LauncherText.Body,
            color = LauncherColors.TextSecondary,
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AppRow(
    apps: List<LaunchableApp>,
    kind: RowKind,
    size: TileSize,
    actions: LauncherActions,
    onMenu: (LaunchableApp, RowKind) -> Unit,
    onFocused: (LaunchableApp) -> Unit,
    firstItemFocus: FocusRequester?,
    pendingFocus: String? = null,
    onPendingFocusDone: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val pendingRequester = remember { FocusRequester() }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = SafeArea.Horizontal, vertical = RowBleed),
        horizontalArrangement = Arrangement.spacedBy(size.gap),
        // Coming back to a row lands on the tile you left.
        modifier = Modifier.overlappingBleed(RowBleed).fillMaxWidth().fadingHorizontalEdges(EdgeFade).focusRestorer(),
    ) {
        itemsIndexed(apps, key = { _, app -> app.key }) { index, app ->
            var tileModifier: Modifier = Modifier
            if (index == 0 && firstItemFocus != null) tileModifier = tileModifier.focusRequester(firstItemFocus)
            if (app.packageName == pendingFocus) tileModifier = tileModifier.focusRequester(pendingRequester)
            AppTile(
                app = app,
                size = size,
                onClick = { actions.launch(app) },
                onLongClick = { onMenu(app, kind) },
                onFocused = { onFocused(app) },
                modifier = tileModifier,
            )
        }
    }

    // Runs again when the list changes, so a target that only appears once prefs land
    // (e.g. a just-unhidden app) still gets focus.
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(pendingFocus, apps) {
        val target = pendingFocus ?: return@LaunchedEffect
        val index = apps.indexOfFirst { it.packageName == target }
        if (index < 0) return@LaunchedEffect
        // If a dialog is closing, wait for our window to get focus back; its own focus restore
        // would otherwise override this request.
        snapshotFlow { windowInfo.isWindowFocused }.first { it }
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) listState.scrollToItem(index)
        withFrameNanos { }
        runCatching { pendingRequester.requestFocus() }
        onPendingFocusDone()
    }
}

/** Lays the row out [bleed] smaller top and bottom, so its bleed padding overlaps its neighbours. */
private fun Modifier.overlappingBleed(bleed: Dp): Modifier = layout { measurable, constraints ->
    val px = bleed.roundToPx()
    val placeable = measurable.measure(constraints)
    layout(placeable.width, (placeable.height - px * 2).coerceAtLeast(0)) { placeable.place(0, -px) }
}

/** Fades the row content to transparent at the left/right edges of the screen. */
private fun Modifier.fadingHorizontalEdges(width: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val fraction = (width.toPx() / size.width).coerceIn(0f, 0.5f)
        val mask = Brush.horizontalGradient(
            0f to Color.Transparent,
            fraction to Color.Black,
            1f - fraction to Color.Black,
            1f to Color.Transparent,
        )
        onDrawWithContent {
            drawContent()
            drawRect(mask, blendMode = BlendMode.DstIn)
        }
    }
