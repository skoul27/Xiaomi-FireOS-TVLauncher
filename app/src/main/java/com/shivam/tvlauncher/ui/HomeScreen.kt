package com.shivam.tvlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.shivam.tvlauncher.LaunchableApp
import com.shivam.tvlauncher.LauncherUi
import com.shivam.tvlauncher.RowKind
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
    fun openSystemSettings()
}

/** Vertical room inside each row so a scaled tile and its glow aren't clipped. */
private val RowBleed = 28.dp
private val EdgeFade = 40.dp

/** What the safe area still needs below a row once its own bleed is counted. */
private val BottomGap = (SafeArea.Vertical - RowBleed).coerceAtLeast(0.dp)
private val SectionTitleHeight = 22.dp

private data class MenuTarget(val packageName: String, val row: RowKind)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(ui: LauncherUi?, actions: LauncherActions) {
    val scope = rememberCoroutineScope()
    val animate = LocalAnimationsEnabled.current
    val backdrop = remember { BackdropState(scope) }
    val hero = remember { HeroState() }
    val initialFocus = remember { FocusRequester() }
    val openButton = remember { FocusRequester() }
    val settingsButton = remember { FocusRequester() }
    val scroll = rememberScrollState()
    val scrollMargin = with(LocalDensity.current) { SafeArea.Horizontal.toPx() }
    val rowScrollSpec = remember(scrollMargin, animate) { ComfortableScrollSpec(scrollMargin, animate) }
    var menu by remember { mutableStateOf<MenuTarget?>(null) }
    var showHidden by remember { mutableStateOf(false) }
    // Where focus should go once the UI catches up with an action that removes the focused tile.
    var pendingFocus by remember { mutableStateOf<MenuTarget?>(null) }
    val openMenu = remember { { app: LaunchableApp, row: RowKind -> menu = MenuTarget(app.packageName, row) } }
    val focusDone = remember { { pendingFocus = null } }
    val showInHero = remember { { app: LaunchableApp -> hero.app = app } }
    val scrollTo = remember(animate) {
        { top: Boolean ->
            scope.launch {
                val target = if (top) 0 else scroll.maxValue
                if (animate) scroll.animateScrollTo(target) else scroll.scrollTo(target)
            }
            Unit
        }
    }

    // The My Apps row sits on the bottom safe edge; the hero takes whatever is left above it.
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val myAppsHeight = SectionTitleHeight + RowBleed * 2 + TileSize.Large.height + LabelHeight
    val heroHeight = screenHeight - SafeArea.Vertical - TopBarHeight - myAppsHeight - BottomGap

    // Leanback devices default to a "pivot" spec that parks focus 30% down the screen. The page
    // scroll is driven explicitly by which section has focus, so it opts out; rows override it.
    CompositionLocalProvider(LocalBringIntoViewSpec provides NoScrollSpec) {
        Box(Modifier.fillMaxSize()) {
            Backdrop(backdrop, Modifier.fillMaxSize())
            BackdropDriver(hero, backdrop)

            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Column(Modifier.onFocusChanged { if (it.hasFocus) scrollTo(true) }) {
                    TopBar(
                        settingsButton = {
                            SettingsButton(
                                onClick = actions::openSystemSettings,
                                modifier = Modifier.focusRequester(settingsButton).focusProperties { down = openButton },
                            )
                        },
                        modifier = Modifier.padding(top = SafeArea.Vertical, start = SafeArea.Horizontal, end = SafeArea.Horizontal),
                    )
                    Hero(
                        state = hero,
                        onOpen = actions::launch,
                        openButtonModifier = Modifier.focusRequester(openButton).focusProperties { up = settingsButton },
                        modifier = Modifier
                            .height(heroHeight)
                            // Fades as the page scrolls to All Apps, so its top edge never sits clipped.
                            .graphicsLayer { alpha = (1f - 1.6f * scroll.value / heroHeight.toPx().coerceAtLeast(1f)).coerceIn(0f, 1f) }
                            .padding(start = SafeArea.Horizontal, end = SafeArea.Horizontal, bottom = 26.dp),
                    )

                    SectionTitle("My Apps")
                    if (ui != null) {
                        CompositionLocalProvider(LocalBringIntoViewSpec provides rowScrollSpec) {
                            if (ui.pinned.isEmpty()) {
                                EmptyMyAppsHint()
                            } else {
                                AppRow(
                                    ui.pinned, RowKind.Pinned, TileSize.Large, actions, openMenu, showInHero,
                                    firstItemFocus = initialFocus,
                                    upTarget = openButton,
                                    pendingFocus = pendingFocus?.takeIf { it.row == RowKind.Pinned }?.packageName,
                                    onPendingFocusDone = focusDone,
                                )
                            }
                        }
                    }
                }

                if (ui != null) {
                    Column(Modifier.onFocusChanged { if (it.hasFocus) scrollTo(false) }) {
                        SectionTitle("All Apps")
                        val hiddenCount = ui.hidden.size
                        CompositionLocalProvider(LocalBringIntoViewSpec provides rowScrollSpec) {
                            AppRow(
                                ui.all, RowKind.All, TileSize.Small, actions, openMenu, showInHero,
                                firstItemFocus = initialFocus.takeIf { ui.pinned.isEmpty() },
                                upTarget = null,
                                pendingFocus = pendingFocus?.takeIf { it.row == RowKind.All }?.packageName,
                                onPendingFocusDone = focusDone,
                                trailing = if (hiddenCount == 0) null else {
                                    { item(key = "hidden-apps") { HiddenAppsTile(hiddenCount) { showHidden = true } } }
                                },
                            )
                        }
                        Spacer(Modifier.height(BottomGap))
                    }
                }
            }

            if (ui != null) {
                menu?.let { target ->
                    AppMenuFor(ui, target, actions, onFocusMove = { pendingFocus = it }, onDismiss = { menu = null })
                }
                if (showHidden) {
                    HiddenAppsDialog(
                        ui.hidden,
                        onUnhide = { app ->
                            actions.unhide(app)
                            pendingFocus = MenuTarget(app.packageName, RowKind.All)
                        },
                        onDismiss = { showHidden = false },
                    )
                }
            }
        }
    }

    // Home has nothing to go back to: Back returns to My Apps (on the tile last used) instead of leaving.
    BackHandler {
        scrollTo(true)
        runCatching { initialFocus.requestFocus() }
    }

    // Start on the first tile. Only keyed on "loaded" so a resume keeps the user's place.
    LaunchedEffect(ui != null) {
        if (ui != null) {
            if (hero.app == null) hero.app = ui.pinned.firstOrNull() ?: ui.all.firstOrNull()
            runCatching { initialFocus.requestFocus() }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private val NoScrollSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
}

/** Label line under a tile: 12dp gap plus one line of [LauncherText.Label]. */
private val LabelHeight = 32.dp

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

    // The tile is about to leave this row: hand focus to its neighbour, or to All Apps if
    // My Apps empties, instead of letting it fall back to the first tile on screen.
    fun focusNeighbour() {
        val neighbour = row.getOrNull(index + 1) ?: row.getOrNull(index - 1)
        onFocusMove(
            when {
                neighbour != null -> MenuTarget(neighbour.packageName, target.row)
                target.row == RowKind.Pinned -> ui.all.firstOrNull { it.packageName != app.packageName }
                    ?.let { MenuTarget(it.packageName, RowKind.All) }
                else -> null
            },
        )
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
            .height(TileSize.Large.height + RowBleed * 2 + LabelHeight)
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
    trailing: (LazyListScope.() -> Unit)? = null,
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
        trailing?.invoke(this)
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
