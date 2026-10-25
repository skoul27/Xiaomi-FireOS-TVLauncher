package com.shivam.tvlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.FilterQuality
import com.shivam.tvlauncher.rememberAppArt
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.input.key.type
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
import com.shivam.tvlauncher.ColorKey
import com.shivam.tvlauncher.LaunchableApp
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
    fun refreshApps()
    fun setShortcutKey(key: ColorKey?)
    fun setTheme(accent: Accent)
    fun setAnimations(enabled: Boolean)
    fun clearRecent()
}

/** Vertical room inside each row so a scaled tile and its glow aren't clipped. */
private val RowBleed = 28.dp
private val EdgeFade = 40.dp
private val SectionTitleHeight = 22.dp

/** Label line under a tile: 12dp gap plus one line of [LauncherText.Label]. */
private val LabelHeight = 32.dp
private val MyAppsRowHeight = TileSize.Large.height + RowBleed * 2 + LabelHeight

private data class MenuTarget(val packageName: String, val row: RowKind)

private enum class AppsPopup { All, Hidden }

/**
 * One screen, no page scroll: the hero fills whatever the My Apps row and the All apps / Hidden
 * apps buttons below it leave; the top bar floats top-right over the hero's empty corner.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    ui: LauncherUi?,
    shortcuts: List<SystemShortcut>,
    remoteShortcut: RemoteShortcut,
    homePresses: Int,
    opens: Int,
    actions: LauncherActions,
) {
    val scope = rememberCoroutineScope()
    val animate = LocalAnimationsEnabled.current
    val backdrop = remember { BackdropState(scope) }
    val hero = remember { HeroState() }
    val initialFocus = remember { FocusRequester() }
    val openButton = remember { FocusRequester() }
    val settingsButton = remember { FocusRequester() }
    val allAppsButton = remember { FocusRequester() }
    val scrollMargin = with(LocalDensity.current) { SafeArea.Horizontal.toPx() }
    val rowScrollSpec = remember(scrollMargin, animate) { ComfortableScrollSpec(scrollMargin, animate) }
    var menu by remember { mutableStateOf<MenuTarget?>(null) }
    var popup by remember { mutableStateOf<AppsPopup?>(null) }
    var showSystem by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    // Where focus should go once the UI catches up with an action that removes the focused tile.
    var pendingFocus by remember { mutableStateOf<MenuTarget?>(null) }
    val openMenu = remember { { app: LaunchableApp, row: RowKind -> menu = MenuTarget(app.packageName, row) } }
    val focusDone = remember { { pendingFocus = null } }
    val showInHero = remember { { app: LaunchableApp -> hero.app = app } }
    val hasPins = ui?.pinned?.isNotEmpty() == true
    // My Apps' first tile, or the All apps button when there are no pins.
    val focusStart = remember(hasPins) {
        { runCatching { (if (hasPins) initialFocus else allAppsButton).requestFocus() }; Unit }
    }

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
            Hero(
                state = hero,
                onOpen = actions::launch,
                openButtonModifier = Modifier.focusRequester(openButton).focusProperties { up = settingsButton },
                modifier = Modifier
                    .weight(1f)
                    .padding(top = SafeArea.Vertical, start = SafeArea.Horizontal, end = SafeArea.Horizontal, bottom = 18.dp),
            ) {
                if (ui != null && ui.recent.isNotEmpty()) {
                    RecentStrip(ui.recent, onOpen = actions::launch, onFocused = showInHero, upTarget = settingsButton)
                }
            }

            SectionTitle("My Apps")
            when {
                ui == null -> Spacer(Modifier.height(MyAppsRowHeight))
                ui.pinned.isEmpty() -> EmptyMyAppsHint()
                else -> CompositionLocalProvider(LocalBringIntoViewSpec provides rowScrollSpec) {
                    AppRow(
                        ui.pinned, RowKind.Pinned, TileSize.Large, actions, openMenu, showInHero,
                        firstItemFocus = initialFocus,
                        upTarget = openButton,
                        pendingFocus = pendingFocus?.packageName,
                        onPendingFocusDone = focusDone,
                    )
                }
            }

            AppsButtons(
                hiddenCount = ui?.hidden?.size ?: 0,
                allAppsButton = allAppsButton,
                onAllApps = { if (ui != null) popup = AppsPopup.All },
                onHidden = { if (ui != null) popup = AppsPopup.Hidden },
            )
            Spacer(Modifier.height(SafeArea.Vertical))
        }

        TopBar(
            settingsButton = {
                SettingsButton(
                    onClick = { showSystem = true },
                    modifier = Modifier.focusRequester(settingsButton).focusProperties { down = openButton },
                )
            },
            modifier = Modifier.padding(top = SafeArea.Vertical, start = SafeArea.Horizontal, end = SafeArea.Horizontal),
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
                onOpen = { showSystem = false; actions.openShortcut(it) },
                onLauncherSettings = { showSystem = false; showSettings = true },
                onDismiss = { showSystem = false },
            )
        }
        if (showSettings) {
            LauncherSettingsMenu(
                accent = LocalAccentTheme.current,
                animations = animate,
                remoteShortcut = remoteShortcut,
                actions = actions,
                onDismiss = { showSettings = false },
            )
        }

        OpenVeil(opens)
    }

    // Home has nothing to go back to: Back returns to the start instead of leaving.
    BackHandler { focusStart() }

    // Home pressed while already home: close any overlay and go back to the start of My Apps.
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(homePresses) {
        if (homePresses == 0) return@LaunchedEffect
        menu = null
        popup = null
        showSystem = false
        showSettings = false
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
 * Apps recently opened from here, as icon chips on the Open button's line: the hero above
 * shows whichever chip has focus, so the chips don't need labels of their own.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RowScope.RecentStrip(
    apps: List<LaunchableApp>,
    onOpen: (LaunchableApp) -> Unit,
    onFocused: (LaunchableApp) -> Unit,
    upTarget: FocusRequester,
) {
    Spacer(Modifier.width(32.dp))
    Text("Recent", style = LauncherText.Label, color = LauncherColors.TextSecondary)
    Spacer(Modifier.width(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.focusRestorer()) {
        for (app in apps) {
            GlassButton(
                onClick = { onOpen(app) },
                shape = RecentChipShape,
                contentPadding = PaddingValues(7.dp),
                modifier = Modifier
                    .size(46.dp)
                    .focusProperties { up = upTarget }
                    .onFocusChanged { if (it.isFocused) onFocused(app) },
            ) {
                rememberAppArt(app)?.icon?.let {
                    Image(it, contentDescription = app.label, filterQuality = FilterQuality.Low, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

private val RecentChipShape = RoundedCornerShape(12.dp)

/** "All apps" and "Hidden apps", side by side at bottom centre. Down from My Apps lands here. */
@Composable
private fun AppsButtons(hiddenCount: Int, allAppsButton: FocusRequester, onAllApps: () -> Unit, onHidden: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(AppsButtonHeight),
    ) {
        SmallPill("All apps", onAllApps, Modifier.focusRequester(allAppsButton))
        SmallPill(if (hiddenCount > 0) "Hidden apps \u00b7 $hiddenCount" else "Hidden apps", onHidden)
    }
}

private val AppsButtonHeight = 38.dp

@Composable
private fun SmallPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = modifier.height(AppsButtonHeight),
    ) { focused ->
        Text(text, style = LauncherText.Label, color = buttonContentColor(focused))
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
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = LauncherText.Section,
        color = LauncherColors.TextSecondary,
        modifier = Modifier.height(SectionTitleHeight).padding(horizontal = SafeArea.Horizontal),
    )
}

@Composable
private fun EmptyMyAppsHint() {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(MyAppsRowHeight)
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
    upTarget: FocusRequester?,
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
        modifier = Modifier.fillMaxWidth().fadingHorizontalEdges(EdgeFade).focusRestorer(),
    ) {
        itemsIndexed(apps, key = { _, app -> app.key }) { index, app ->
            var tileModifier: Modifier = Modifier
            if (index == 0 && firstItemFocus != null) tileModifier = tileModifier.focusRequester(firstItemFocus)
            if (app.packageName == pendingFocus) tileModifier = tileModifier.focusRequester(pendingRequester)
            if (upTarget != null) tileModifier = tileModifier.focusProperties { up = upTarget }
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
