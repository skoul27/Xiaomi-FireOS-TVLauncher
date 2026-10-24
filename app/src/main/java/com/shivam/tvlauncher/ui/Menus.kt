package com.shivam.tvlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.shivam.tvlauncher.LaunchableApp

enum class MenuAction { TogglePin, MoveLeft, MoveRight, Hide, AppInfo, Uninstall }

private val PanelShape = RoundedCornerShape(20.dp)
private val ItemShape = RoundedCornerShape(12.dp)
private val PanelColor = Color(0xF51C202B)

/**
 * Long-press menu for one card. Stays open after Move left/right so the card can be nudged
 * several places in a row; every other action closes it.
 */
@Composable
fun AppMenu(
    app: LaunchableApp,
    isPinned: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onAction: (MenuAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    MenuDialog(onDismiss) {
        PanelTitle(app.label)
        MenuItem(if (isPinned) "Unpin" else "Pin", Modifier.focusRequester(first)) { onAction(MenuAction.TogglePin) }
        // Always present (just inert at the ends) so focus never lands on a vanished item.
        MenuItem("Move left", enabled = canMoveLeft) { onAction(MenuAction.MoveLeft) }
        MenuItem("Move right", enabled = canMoveRight) { onAction(MenuAction.MoveRight) }
        MenuItem("Hide") { onAction(MenuAction.Hide) }
        MenuItem("App info") { onAction(MenuAction.AppInfo) }
        if (!app.isSystem) MenuItem("Uninstall") { onAction(MenuAction.Uninstall) }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/** Lists hidden apps; selecting one shows it again. Closes itself when nothing is left. */
@Composable
fun HiddenAppsDialog(apps: List<LaunchableApp>, onUnhide: (LaunchableApp) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    MenuDialog(onDismiss, width = 420) {
        PanelTitle("Hidden apps")
        Text(
            "Select an app to show it again.",
            style = LauncherText.LabelSmall,
            color = LauncherColors.TextSecondary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        )
        LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
            itemsIndexed(apps, key = { _, app -> app.key }) { index, app ->
                HiddenAppRow(app, if (index == 0) Modifier.focusRequester(first) else Modifier) { onUnhide(app) }
            }
        }
    }
    // Re-seat focus whenever the list shrinks, since the focused row may have just been removed.
    LaunchedEffect(apps.size) {
        if (apps.isEmpty()) onDismiss() else runCatching { first.requestFocus() }
    }
}

/** The tile at the end of All apps that opens [HiddenAppsDialog]. */
@Composable
fun HiddenAppsTile(count: Int, onClick: () -> Unit) {
    MotionCard(
        size = CardSize.Small,
        label = "Hidden apps",
        accent = LauncherColors.InkRaised,
        image = null,
        onClick = onClick,
        onLongClick = null,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().background(Color(0xFF242A36)),
        ) {
            Text("Hidden", style = LauncherText.LabelLarge, color = LauncherColors.TextPrimary)
            Text(
                if (count == 1) "1 app" else "$count apps",
                style = LauncherText.LabelSmall,
                color = LauncherColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun MenuDialog(onDismiss: () -> Unit, width: Int = 320, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .width(width.dp)
                .clip(PanelShape)
                .background(PanelColor)
                .padding(vertical = 14.dp, horizontal = 10.dp)
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
        style = LauncherText.RowTitle,
        color = LauncherColors.TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun MenuItem(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    FocusRow(modifier = modifier.height(44.dp), onClick = { if (enabled) onClick() }) { focused ->
        Text(
            text,
            style = LauncherText.Body,
            color = when {
                focused -> LauncherColors.Ink
                enabled -> LauncherColors.TextPrimary
                else -> LauncherColors.TextPrimary.copy(alpha = 0.35f)
            },
        )
    }
}

@Composable
private fun HiddenAppRow(app: LaunchableApp, modifier: Modifier, onClick: () -> Unit) {
    FocusRow(modifier = modifier.height(56.dp), onClick = onClick) { focused ->
        Box(modifier = Modifier.size(64.dp, 36.dp).clip(RoundedCornerShape(6.dp))) {
            val image = app.visual.image
            if (image != null) {
                Image(image, null, contentScale = ContentScale.FillBounds, colorFilter = UndimFilter, modifier = Modifier.fillMaxSize())
            } else {
                LetterPlaceholder(app.label, app.visual.placeholder, CardSize.Small.letterSize * 0.5f)
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

/** A menu row that turns white when focused, tvOS-style. Dialogs are small, so plain state is fine here. */
@Composable
private fun FocusRow(modifier: Modifier, onClick: () -> Unit, content: @Composable (focused: Boolean) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(ItemShape)
            .background(if (focused) LauncherColors.TextPrimary else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        content(focused)
    }
}
