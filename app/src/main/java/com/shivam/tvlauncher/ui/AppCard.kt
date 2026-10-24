package com.shivam.tvlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.shivam.tvlauncher.LaunchableApp

enum class CardSize(val width: Dp, val gap: Dp, val letterSize: TextUnit, val alwaysShowLabel: Boolean) {
    Large(width = 224.dp, gap = 28.dp, letterSize = 52.sp, alwaysShowLabel = true),
    Small(width = 160.dp, gap = 22.dp, letterSize = 38.sp, alwaysShowLabel = false);

    val height: Dp get() = width * 9f / 16f
}

@Composable
fun AppCard(
    app: LaunchableApp,
    size: CardSize,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }

    Column(modifier = modifier.width(size.width)) {
        Box(
            modifier = Modifier
                .size(size.width, size.height)
                .onFocusChanged { focused = it.isFocused }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .clip(CardShape)
                .border(if (focused) 3.dp else 0.dp, Color.White, CardShape),
        ) {
            CardArt(app, size)
        }
        Text(
            text = app.label,
            style = if (size == CardSize.Large) LauncherText.LabelLarge else LauncherText.LabelSmall,
            color = if (focused) LauncherColors.TextPrimary else LauncherColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 10.dp, start = 2.dp, end = 2.dp)
                .alpha(if (size.alwaysShowLabel || focused) 1f else 0f),
        )
    }
}

@Composable
fun CardArt(app: LaunchableApp, size: CardSize, modifier: Modifier = Modifier) {
    val image = app.visual.image
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        LetterPlaceholder(app.label, app.visual.placeholder, size.letterSize, modifier)
    }
}

@Composable
private fun LetterPlaceholder(label: String, color: Color, letterSize: TextUnit, modifier: Modifier) {
    val letter = remember(label) {
        label.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    }
    val brush = remember(color) {
        Brush.linearGradient(listOf(color, Color(color.red * 0.5f, color.green * 0.5f, color.blue * 0.5f)))
    }
    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize().background(brush)) {
        Text(letter, style = LauncherText.Placeholder.copy(fontSize = letterSize), color = Color.White.copy(alpha = 0.92f))
    }
}
