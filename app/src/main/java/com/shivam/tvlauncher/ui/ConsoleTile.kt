package com.shivam.tvlauncher.ui

import android.media.tv.TvInputManager
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.shivam.tvlauncher.Console
import com.shivam.tvlauncher.ConsoleBrand
import kotlin.math.min

/**
 * A console's wide card: its brand mark, its name, and the HDMI port with a dot for whether the
 * TV sees the console there (green on, amber resting, grey unplugged).
 */
@Composable
fun ConsoleTile(console: Console, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val state = rememberInputState(console.inputId)
    GlassTile(size = TileSize.Console, label = null, art = null, onClick = onClick, onLongClick = null, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().background(brandBackground(console.brand)).padding(horizontal = 24.dp),
        ) {
            when (console.brand) {
                ConsoleBrand.PlayStation5 -> PlayStationSymbol(Color.White, Modifier.size(46.dp))
            }
            Spacer(Modifier.width(20.dp))
            Column {
                Text(console.name, style = LauncherText.Title, color = Color.White)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Box(Modifier.size(8.dp).background(stateColor(state), CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "HDMI ${console.hdmiPort}",
                        style = LauncherText.Body.copy(fontSize = 15.sp),
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

private fun brandBackground(brand: ConsoleBrand): Brush = when (brand) {
    // PlayStation blue, deeper at the left where the mark sits.
    ConsoleBrand.PlayStation5 -> Brush.horizontalGradient(listOf(Color(0xFF00317A), Color(0xFF0070D1)))
}

private fun stateColor(state: Int?): Color = when (state) {
    TvInputManager.INPUT_STATE_CONNECTED -> Color(0xFF3DDC84)
    TvInputManager.INPUT_STATE_CONNECTED_STANDBY -> Color(0xFFF5B400)
    else -> Color.White.copy(alpha = 0.35f)
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

/** The PlayStation symbol, scaled to fit and centred. */
@Composable
private fun PlayStationSymbol(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val bounds = PlayStationPath.getBounds()
        val scale = min(size.width / bounds.width, size.height / bounds.height)
        translate(
            left = (size.width - bounds.width * scale) / 2f - bounds.left * scale,
            top = (size.height - bounds.height * scale) / 2f - bounds.top * scale,
        ) {
            scale(scale, pivot = Offset.Zero) { drawPath(PlayStationPath, color) }
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
