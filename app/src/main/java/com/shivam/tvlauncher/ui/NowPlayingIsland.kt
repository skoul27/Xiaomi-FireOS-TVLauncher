package com.shivam.tvlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import androidx.tv.material3.Text
import com.shivam.tvlauncher.NowPlaying
import com.shivam.tvlauncher.nowPlayingFlow
import kotlinx.coroutines.delay

/** What another app is playing, kept current while the home screen is in front. */
@Composable
fun rememberNowPlaying(): NowPlaying? {
    val context = LocalContext.current.applicationContext
    val flow = remember(context) { nowPlayingFlow(context) }
    return flow.collectAsStateWithLifecycle(null).value
}

private val ExpandedWidth = 380.dp

/**
 * Now playing as a "dynamic island": a slim pill the height of the top bar's buttons (artwork,
 * title, a moving equaliser while playing). OK springs it open into a glass panel tinted from the
 * artwork, with progress and whichever of previous / play-pause / next the app supports, plus
 * Open. Back, or moving focus out of it, folds it up again. [bodyFocus] is the pill.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NowPlayingIsland(now: NowPlaying, bodyFocus: FocusRequester, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val animate = LocalAnimationsEnabled.current
    var expanded by remember { mutableStateOf(false) }
    var pillFocused by remember { mutableStateOf(false) }
    val mainControl = remember { FocusRequester() }
    val art = remember(now.art) { now.art?.asImageBitmap() }
    val tint = remember(now.art) { now.art?.let(::artTint) ?: Color(0xFF8A93A6) }

    // Back (or Open) folds it with focus returning to the pill; moving out of it folds it silently.
    var refocusPill by remember { mutableStateOf(false) }
    val expand = { expanded = true }
    val collapse = {
        refocusPill = true
        expanded = false
    }
    BackHandler(enabled = expanded, onBack = collapse)
    // Focus moves once the other state's content exists, a frame later.
    LaunchedEffect(expanded, refocusPill) {
        if (expanded) {
            delay(16)
            runCatching { mainControl.requestFocus() }
        } else if (refocusPill) {
            delay(16)
            runCatching { bodyFocus.requestFocus() }
            refocusPill = false
        }
    }
    // Compose drops focus for an instant whenever it moves (button to button, or pill to panel as
    // one replaces the other), so only fold when it's still gone a moment later.
    var hasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(hasFocus, expanded) {
        if (!hasFocus && expanded) {
            delay(120)
            expanded = false
        }
    }

    val corner by animateDpAsState(
        if (expanded) 24.dp else RoundButtonSize / 2,
        if (animate) spring(dampingRatio = 0.72f, stiffness = 380f) else snap(),
        label = "corner",
    )
    val shape = RoundedCornerShape(corner)
    val pillLit = pillFocused && !expanded

    Box(
        modifier
            .onFocusChanged { hasFocus = it.hasFocus }
            .shadow(if (expanded) 28.dp else 0.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .animateContentSize(if (animate) spring(dampingRatio = 0.68f, stiffness = 360f) else snap())
            .liquidGlass(shape, tint, lit = pillLit, solid = expanded),
    ) {
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                if (animate) {
                    fadeIn(tween(180, delayMillis = 60)) togetherWith fadeOut(tween(90))
                } else {
                    fadeIn(snap()) togetherWith fadeOut(snap())
                }
            },
            label = "island",
        ) { open ->
            if (!open) {
                Pill(
                    now, art, lit = pillLit, tint = tint,
                    modifier = Modifier
                        .focusRequester(bodyFocus)
                        .focusProperties { left = FocusRequester.Cancel }
                        .onFocusChanged { pillFocused = it.isFocused }
                        .remoteClickable(expand),
                )
            } else {
                Expanded(now, art, tint, mainControl, onOpen = { collapse(); now.open(context) })
            }
        }
    }
}

/**
 * Glass without blur: a translucent fill washed with the artwork's colour, a bright rim fading
 * down the sides and a specular sheen across the top. [solid] (open) is nearly opaque, so the
 * controls read over the tiles underneath; [lit] is the focused pill, solid white like the buttons.
 */
private fun Modifier.liquidGlass(shape: Shape, tint: Color, lit: Boolean, solid: Boolean): Modifier {
    val rim = if (lit) 0f else 1f
    return this
        .clip(shape)
        .background(
            when {
                lit -> Brush.verticalGradient(listOf(LauncherColors.TextPrimary, LauncherColors.TextPrimary))
                solid -> Brush.verticalGradient(listOf(lerpColor(Color(0xF0181A20), tint, 0.22f), Color(0xF5121318)))
                else -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), tint.copy(alpha = 0.10f)))
            },
        )
        .drawWithCache {
            val sheen = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.14f * rim), 0.45f to Color.Transparent)
            onDrawWithContent {
                drawRect(sheen)
                drawContent()
            }
        }
        .border(
            1.dp,
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.55f * rim),
                    Color.White.copy(alpha = 0.08f * rim),
                    tint.copy(alpha = 0.35f * rim),
                ),
            ),
            shape,
        )
}

private fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha,
)

/** A vivid colour from the artwork for the glass to pick up. */
private fun artTint(bitmap: android.graphics.Bitmap): Color? = runCatching {
    val palette = Palette.from(bitmap).maximumColorCount(12).resizeBitmapArea(96 * 96).generate()
    val swatch = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch
    swatch?.let { Color(it.rgb) }
}.getOrNull()

@Composable
private fun Pill(now: NowPlaying, art: ImageBitmap?, lit: Boolean, tint: Color, modifier: Modifier) {
    val color = buttonContentColor(lit)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(RoundButtonSize)
            // Narrow enough to clear the date on the left whatever the title.
            .widthIn(max = 280.dp)
            .padding(start = 6.dp, end = 18.dp),
    ) {
        Artwork(art, 36.dp, CircleShape)
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.Center, modifier = Modifier.weight(1f, fill = false)) {
            Text(
                now.title,
                style = LauncherText.Label.copy(fontSize = 14.sp, lineHeight = 17.sp),
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(now.appName, now.subtitle).joinToString(" · "),
                style = LauncherText.Caption.copy(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Normal),
                color = color.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(14.dp))
        Equalizer(playing = now.playing, color = if (lit) color else tint.brighten())
    }
}

/** The pill's sign of life: three bars bouncing while playing, resting low when paused. */
@Composable
private fun Equalizer(playing: Boolean, color: Color) {
    val moving = LocalAnimationsEnabled.current && playing
    val transition = rememberInfiniteTransition(label = "eq")
    val bars = listOf(420, 300, 520).mapIndexed { i, period ->
        val bounce by transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(period, delayMillis = i * 70), RepeatMode.Reverse),
            label = "bar$i",
        )
        when {
            moving -> bounce
            playing -> listOf(0.7f, 1f, 0.5f)[i]
            else -> 0.25f
        }
    }
    Canvas(Modifier.size(width = 16.dp, height = 14.dp)) {
        val barW = size.width / 5f
        bars.forEachIndexed { i, h ->
            val barH = size.height * h
            drawRoundRect(
                color,
                topLeft = Offset(i * barW * 2f, size.height - barH),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f),
            )
        }
    }
}

@Composable
private fun Expanded(now: NowPlaying, art: ImageBitmap?, tint: Color, mainControl: FocusRequester, onOpen: () -> Unit) {
    Column(Modifier.width(ExpandedWidth).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(art, 60.dp, RoundedCornerShape(14.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    now.appName.uppercase() + if (now.playing) "  ·  PLAYING" else "  ·  PAUSED",
                    style = LauncherText.Caption.copy(fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp),
                    color = tint.brighten(),
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    now.title,
                    style = LauncherText.Title.copy(fontSize = 17.sp, lineHeight = 22.sp),
                    color = LauncherColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                now.subtitle?.let {
                    Text(it, style = LauncherText.Body.copy(fontSize = 14.sp, lineHeight = 18.sp), color = LauncherColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (now.durationMs > 0) {
            Spacer(Modifier.height(12.dp))
            Progress(now, tint)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.Center),
            ) {
                if (now.canSkipPrevious) {
                    RoundIconButton(now::skipPrevious, Modifier.size(42.dp)) { SkipIcon(it, previous = true, size = 15.dp) }
                }
                // The middle control takes focus on opening; Open does if the app has no play/pause.
                if (now.canPlayPause) {
                    RoundIconButton(now::playPause, Modifier.focusRequester(mainControl).size(50.dp)) {
                        if (now.playing) PauseIcon(it, size = 17.dp) else PlayIcon(it, size = 17.dp, modifier = Modifier.padding(start = 2.dp))
                    }
                }
                if (now.canSkipNext) {
                    RoundIconButton(now::skipNext, Modifier.size(42.dp)) { SkipIcon(it, previous = false, size = 15.dp) }
                }
            }
            GlassButton(
                onClick = onOpen,
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .height(36.dp)
                    .then(if (now.canPlayPause) Modifier else Modifier.focusRequester(mainControl)),
            ) { focused ->
                Text("Open", style = LauncherText.Button.copy(fontSize = 14.sp), color = buttonContentColor(focused))
            }
        }
    }
}

/** A thin track with the elapsed and total time, ticking while playing. */
@Composable
private fun Progress(now: NowPlaying, tint: Color) {
    var position by remember(now) { mutableLongStateOf(now.position()) }
    LaunchedEffect(now) {
        while (now.playing) {
            position = now.position()
            delay(500)
        }
        position = now.position()
    }
    val fraction = (position.toFloat() / now.durationMs).coerceIn(0f, 1f)
    val fill = tint.brighten()
    Column {
        Canvas(Modifier.fillMaxWidth().height(4.dp)) {
            val r = CornerRadius(size.height / 2f)
            drawRoundRect(Color.White.copy(alpha = 0.16f), cornerRadius = r)
            drawRoundRect(fill, size = Size(size.width * fraction, size.height), cornerRadius = r)
        }
        Spacer(Modifier.height(6.dp))
        Row {
            val style = LauncherText.Caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal)
            Text(formatTime(position), style = style, color = LauncherColors.TextTertiary)
            Spacer(Modifier.weight(1f))
            Text(formatTime(now.durationMs), style = style, color = LauncherColors.TextTertiary)
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun Artwork(art: ImageBitmap?, size: Dp, shape: Shape) {
    if (art != null) {
        Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(shape))
    } else {
        Box(Modifier.size(size).background(Glass.TileBase, shape))
    }
}

/** Lifts dark artwork colours so accents stay legible on the dark glass. */
private fun Color.brighten(): Color {
    val luminance = 0.299f * red + 0.587f * green + 0.114f * blue
    return if (luminance >= 0.55f) this else lerpColor(this, Color.White, (0.55f - luminance) / 0.55f * 0.7f)
}
