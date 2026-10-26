package com.shivam.tvlauncher.ui

import android.media.tv.TvInputManager
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.shivam.tvlauncher.Console
import com.shivam.tvlauncher.ConsoleBrand
import kotlin.math.min

/**
 * A console's card, laid out like the panel headers: the brand mark in a rounded tile of the
 * brand's colour, the name as a title, and "HDMI 3 · Standby" under it with a dot for whether
 * the TV sees the device there (green on, amber resting, grey unknown). The card itself is glass
 * washed with the brand colour. [compact] is one app column wide, for more than two cards.
 */
@Composable
fun ConsoleTile(console: Console, compact: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val state = rememberInputState(console.inputId)
    val size = if (compact) TileSize.ConsoleCompact else TileSize.Console
    val brand = brandColor(console.brand)
    GlassTile(size = size, label = null, art = null, onClick = onClick, onLongClick = onLongClick, modifier = modifier) {
        val background = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(brand.copy(alpha = 0.42f), brand.copy(alpha = 0.10f))))
        if (compact) {
            Column(verticalArrangement = Arrangement.Center, modifier = background.padding(horizontal = 14.dp)) {
                BrandMark(console, brand, 34.dp)
                Spacer(Modifier.height(8.dp))
                Text(console.name, style = LauncherText.Label.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                PortLine(console.port, state, compact = true)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = background.padding(horizontal = 20.dp)) {
                BrandMark(console, brand, 56.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(console.name, style = LauncherText.Title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    PortLine(console.port, state, compact = false)
                }
            }
        }
    }
}

@Composable
private fun PortLine(port: String, state: Int?, compact: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
        Box(Modifier.size(7.dp).background(stateColor(state), CircleShape))
        Spacer(Modifier.width(7.dp))
        Text(
            // The compact card has room for the port only.
            if (compact) port else listOfNotNull(port, stateLabel(state)).joinToString(" · "),
            style = LauncherText.Caption.copy(fontSize = if (compact) 12.sp else 14.sp),
            color = Color.White.copy(alpha = 0.78f),
            maxLines = 1,
        )
    }
}

/** The brand's symbol, white in a rounded tile of the brand's colour; Other shows its initial. */
@Composable
private fun BrandMark(console: Console, brand: Color, size: Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.26f))
            .background(Brush.verticalGradient(listOf(brand, brand.darken(0.7f)))),
    ) {
        val mark = Modifier.size(size * 0.58f)
        when (console.brand) {
            ConsoleBrand.PlayStation -> PathMark(PlayStationPath, mark)
            ConsoleBrand.Xbox -> PathMark(XboxPath, mark)
            ConsoleBrand.Nintendo -> PathMark(NintendoSwitchPath, mark)
            ConsoleBrand.Other -> Text(
                console.name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
                style = LauncherText.Title.copy(fontSize = (size.value * 0.44f).sp),
                color = Color.White,
            )
        }
    }
}

private fun brandColor(brand: ConsoleBrand): Color = when (brand) {
    ConsoleBrand.PlayStation -> Color(0xFF0070D1)
    ConsoleBrand.Xbox -> Color(0xFF107C10)
    ConsoleBrand.Nintendo -> Color(0xFFE60012)
    ConsoleBrand.Other -> Color(0xFF5B6B8C)
}

private fun Color.darken(factor: Float): Color = Color(red * factor, green * factor, blue * factor, alpha)

private fun stateColor(state: Int?): Color = when (state) {
    TvInputManager.INPUT_STATE_CONNECTED -> Color(0xFF3DDC84)
    TvInputManager.INPUT_STATE_CONNECTED_STANDBY -> Color(0xFFF5B400)
    else -> Color.White.copy(alpha = 0.35f)
}

private fun stateLabel(state: Int?): String? = when (state) {
    TvInputManager.INPUT_STATE_CONNECTED -> "On"
    TvInputManager.INPUT_STATE_CONNECTED_STANDBY -> "Standby"
    else -> null
}

/** The TV's view of the input (TvInputManager.INPUT_STATE_*), kept current; null if unknown. */
@Composable
private fun rememberInputState(inputId: String): Int? {
    val manager = LocalContext.current.getSystemService(TvInputManager::class.java)
    var state by remember(inputId) {
        mutableStateOf(runCatching { manager?.getInputState(inputId) }.getOrNull())
    }
    DisposableEffect(manager, inputId) {
        val callback = object : TvInputManager.TvInputCallback() {
            override fun onInputStateChanged(id: String, newState: Int) {
                if (id == inputId) state = newState
            }
        }
        manager?.registerCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { manager?.unregisterCallback(callback) }
    }
    return state
}

/** A brand symbol in white, scaled to fit and centred. */
@Composable
private fun PathMark(path: Path, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val bounds = path.getBounds()
        val scale = min(size.width / bounds.width, size.height / bounds.height)
        translate(
            left = (size.width - bounds.width * scale) / 2f - bounds.left * scale,
            top = (size.height - bounds.height * scale) / 2f - bounds.top * scale,
        ) {
            scale(scale, pivot = Offset.Zero) { drawPath(path, Color.White) }
        }
    }
}

/**
 * The PlayStation symbol from the PlayStation 5 logo on Wikimedia Commons
 * (File:PS5_logo.svg, path "ps_logo"), without the registered sign. The SVG fills it even-odd.
 */
private val PlayStationPath: Path by lazy {
    PathParser().parsePathString(
        "M20.79,183C-1,176.77-4.7,163.91,5.26,156.5c9.21-6.87,24.87-12,24.87-12L94.8,121.4v26.32L48.26,16" +
        "4.45c-8.23,3-9.49,7.16-2.8,9.35s18.78,1.57,27-1.39l22.32-8.14v23.55c-1.41.26-3,.51-4.45.75A145.7" +
        "9,145.79,0,0,1,20.79,183Zm136.44,2.74,72.57-25.9c8.24-3,9.5-7.14,2.81-9.33s-18.78-1.57-27,1.4l-4" +
        "8.36,17.06V141.78l2.8-.95a165.71,165.71,0,0,1,33.61-7.13c19.65-2.17,43.71.3,62.59,7.46,21.29,6.7" +
        "4,23.69,16.67,18.28,23.51s-18.67,11.71-18.67,11.71l-98.61,35.49Zm8.49-168.57c38.21,13.19,51.16,2" +
        "9.61,51.16,66.61,0,36.05-22.14,49.72-50.26,36.07V52.62c0-7.88-1.44-15.14-8.8-17.2-5.64-1.82-9.14" +
        ",3.45-9.14,11.33V215l-45.12-14.4V0c19.19,3.58,47.14,12,62.16,17.13Z",
    ).toPath().apply { fillType = PathFillType.EvenOdd }
}

/** The Xbox sphere, from Simple Icons 11.0.0 (icons/xbox.svg). */
private val XboxPath: Path by lazy {
    PathParser().parsePathString(
        "M4.102 21.033C6.211 22.881 8.977 24 12 24c3.026 0 5.789-1.119 7.902-2.967 1.877-1.912-4.316-8.709-7.902-11.417" +
        "-3.582 2.708-9.779 9.505-7.898 11.417zm11.16-14.406c2.5 2.961 7.484 10.313 6.076 12.912C23.002 17.48 24 14.861 24 " +
        "12.004c0-3.34-1.365-6.362-3.57-8.536 0 0-.027-.022-.082-.042-.063-.022-.152-.045-.281-.045-.592 0-1.985.434-4.805 " +
        "3.246zM3.654 3.426c-.057.02-.082.041-.086.042C1.365 5.642 0 8.664 0 12.004c0 2.854.998 5.473 2.661 7.533-1.401-2.605 " +
        "3.579-9.951 6.08-12.91-2.82-2.813-4.216-3.245-4.806-3.245-.131 0-.223.021-.281.046v-.002zM12 3.551S9.055 1.828 6.755 " +
        "1.746c-.903-.033-1.454.295-1.521.339C7.379.646 9.659 0 11.984 0H12c2.334 0 4.605.646 6.766 2.085-.068-.046-.615-.372" +
        "-1.52-.339C14.946 1.828 12 3.545 12 3.545v.006z",
    ).toPath()
}

/** The Nintendo Switch Joy-Con mark, from Simple Icons 11.0.0 (icons/nintendoswitch.svg). */
private val NintendoSwitchPath: Path by lazy {
    PathParser().parsePathString(
        "M14.176 24h3.674c3.376 0 6.15-2.774 6.15-6.15V6.15C24 2.775 21.226 0 17.85 0H14.1c-.074 0-.15.074-.15.15v23.7" +
        "c-.001.076.075.15.226.15zm4.574-13.199c1.351 0 2.399 1.125 2.399 2.398 0 1.352-1.125 2.4-2.399 2.4-1.35 0-2.4-1.049" +
        "-2.4-2.4-.075-1.349 1.05-2.398 2.4-2.398zM11.4 0H6.15C2.775 0 0 2.775 0 6.15v11.7C0 21.226 2.775 24 6.15 24h5.25" +
        "c.074 0 .15-.074.15-.149V.15c.001-.076-.075-.15-.15-.15zM9.676 22.051H6.15c-2.326 0-4.201-1.875-4.201-4.201V6.15" +
        "c0-2.326 1.875-4.201 4.201-4.201H9.6l.076 20.102zM3.75 7.199c0 1.275.975 2.25 2.25 2.25s2.25-.975 2.25-2.25" +
        "c0-1.273-.975-2.25-2.25-2.25s-2.25.977-2.25 2.25z",
    ).toPath()
}
