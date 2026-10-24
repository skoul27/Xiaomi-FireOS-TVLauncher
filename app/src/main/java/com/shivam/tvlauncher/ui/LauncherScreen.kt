package com.shivam.tvlauncher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
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
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.shivam.tvlauncher.LaunchableApp
import com.shivam.tvlauncher.LauncherUi
import com.shivam.tvlauncher.RowKind
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

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
}

/** Vertical room inside each row so a scaled card and its shadow aren't clipped. */
private val RowBleed = 20.dp
private val EdgeFade = 40.dp

private data class MenuTarget(val packageName: String, val row: RowKind)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherScreen(ui: LauncherUi?, actions: LauncherActions) {
    val initialFocus = remember { FocusRequester() }
    val direction = remember { FocusDirection() }
    val scope = rememberCoroutineScope()
    val tint = remember { BackgroundTint(scope, LauncherColors.Ink) }
    val scrollMargin = with(LocalDensity.current) { SafeArea.Horizontal.toPx() }
    val scrollSpec = remember(scrollMargin) { ComfortableScrollSpec(scrollMargin) }
    var menu by remember { mutableStateOf<MenuTarget?>(null) }
    var showHidden by remember { mutableStateOf(false) }
    // Where focus should go once the UI catches up with an action that removes the focused card.
    var pendingFocus by remember { mutableStateOf<MenuTarget?>(null) }
    val openMenu = remember { { app: LaunchableApp, row: RowKind -> menu = MenuTarget(app.packageName, row) } }
    val focusDone = remember { { pendingFocus = null } }

    CompositionLocalProvider(
        LocalFocusDirection provides direction,
        LocalBackgroundTint provides tint,
        LocalBringIntoViewSpec provides scrollSpec,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .launcherBackground(tint)
                .trackDirection(direction)
                .padding(vertical = SafeArea.Vertical),
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = SafeArea.Horizontal)) {
                Spacer(Modifier.weight(1f))
                Clock()
            }

            if (ui != null) {
                RowTitle("Pinned", Modifier.padding(top = 4.dp))
                if (ui.pinned.isEmpty()) {
                    EmptyPinnedHint()
                } else {
                    AppRow(
                        ui.pinned, RowKind.Pinned, CardSize.Large, actions, openMenu, initialFocus,
                        pendingFocus = pendingFocus?.takeIf { it.row == RowKind.Pinned }?.packageName,
                        onPendingFocusDone = focusDone,
                    )
                }

                RowTitle("All apps", Modifier.padding(top = 2.dp))
                val hiddenCount = ui.hidden.size
                AppRow(
                    ui.all, RowKind.All, CardSize.Small, actions, openMenu,
                    firstItemFocus = initialFocus.takeIf { ui.pinned.isEmpty() },
                    pendingFocus = pendingFocus?.takeIf { it.row == RowKind.All }?.packageName,
                    onPendingFocusDone = focusDone,
                    trailing = if (hiddenCount == 0) null else {
                        { item(key = "hidden-apps") { HiddenAppsTile(hiddenCount) { showHidden = true } } }
                    },
                )
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

    // Start on the first card. Only keyed on "loaded" so a resume keeps the user's place.
    LaunchedEffect(ui != null) {
        if (ui != null) runCatching { initialFocus.requestFocus() }
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

    // The card is about to leave this row: hand focus to its neighbour, or to All apps if the
    // Pinned row empties, instead of letting it fall back to the first card on screen.
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

/** Records the last D-pad direction for the tilt; never consumes the event. */
private fun Modifier.trackDirection(direction: FocusDirection): Modifier = onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown) {
        when (event.key) {
            Key.DirectionLeft -> { direction.dx = -1; direction.dy = 0 }
            Key.DirectionRight -> { direction.dx = 1; direction.dy = 0 }
            Key.DirectionUp -> { direction.dx = 0; direction.dy = -1 }
            Key.DirectionDown -> { direction.dx = 0; direction.dy = 1 }
        }
    }
    false
}

@Composable
private fun RowTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = LauncherText.RowTitle,
        color = LauncherColors.TextSecondary,
        modifier = modifier.padding(horizontal = SafeArea.Horizontal),
    )
}

@Composable
private fun EmptyPinnedHint() {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(CardSize.Large.height + RowBleed * 2 + 28.dp)
            .padding(horizontal = SafeArea.Horizontal),
    ) {
        Text("Long-press OK on any app to pin it here.", style = LauncherText.Body, color = LauncherColors.TextSecondary)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AppRow(
    apps: List<LaunchableApp>,
    kind: RowKind,
    size: CardSize,
    actions: LauncherActions,
    onMenu: (LaunchableApp, RowKind) -> Unit,
    firstItemFocus: FocusRequester?,
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
        // Coming back to a row lands on the card you left, like tvOS.
        modifier = Modifier.fillMaxWidth().fadingHorizontalEdges(EdgeFade).focusRestorer(),
    ) {
        itemsIndexed(apps, key = { _, app -> app.key }) { index, app ->
            var cardModifier: Modifier = Modifier
            if (index == 0 && firstItemFocus != null) cardModifier = cardModifier.focusRequester(firstItemFocus)
            if (app.packageName == pendingFocus) cardModifier = cardModifier.focusRequester(pendingRequester)
            AppCard(
                app = app,
                size = size,
                onClick = { actions.launch(app) },
                onLongClick = { onMenu(app, kind) },
                modifier = cardModifier,
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

/**
 * The focused app's colour washing down from the top over the window's near-black background.
 *
 * Per-pixel gradient shaders across the full screen cost ~25ms/frame on Fire TV GPUs, so the
 * glow's shape is baked once into a tiny alpha mask, stretched with bilinear filtering and
 * tinted. The colour is read here, in the draw phase, so its cross-fade never recomposes.
 */
private fun Modifier.launcherBackground(tint: BackgroundTint): Modifier = drawWithCache {
    val dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
    onDrawBehind {
        drawImage(
            image = GlowMask,
            dstSize = dstSize,
            colorFilter = ColorFilter.tint(tint.color.value),
            filterQuality = FilterQuality.Low,
        )
    }
}

/** White glow on transparent: a top-down wash plus a soft bloom near the top-left. */
private val GlowMask: ImageBitmap by lazy {
    val w = 160
    val h = 90
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val white = android.graphics.Color.WHITE
    val clear = android.graphics.Color.TRANSPARENT
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(0f, 0f, 0f, h * 0.8f, white, clear, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    paint.shader = RadialGradient(w * 0.15f, 0f, w * 0.6f, 0x8CFFFFFF.toInt(), clear, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    bitmap.asImageBitmap()
}
