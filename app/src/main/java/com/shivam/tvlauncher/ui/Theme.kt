package com.shivam.tvlauncher.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import com.shivam.tvlauncher.R

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

object LauncherColors {
    /** Near-black charcoal the whole launcher sits on. */
    val Ink = Color(0xFF0C0D10)
    val TextPrimary = Color(0xFFF4F5F7)
    val TextSecondary = Color(0xB3F4F5F7)
    val TextTertiary = Color(0x73F4F5F7)
}

/**
 * Glass without real blur (no RenderEffect below API 31): a faint white fill, a hairline white
 * border and a top highlight. Overlays use [PanelFill], which is nearly opaque so text over a
 * busy backdrop stays readable.
 */
object Glass {
    val Fill = Color.White.copy(alpha = 0.08f)
    val FillFocused = Color.White.copy(alpha = 0.18f)
    val Border = Color.White.copy(alpha = 0.13f)
    val BorderFocused = Color.White.copy(alpha = 0.85f)
    val Highlight = Color.White.copy(alpha = 0.10f)
    val PanelFill = Color(0xEB16181D)
    /** The big list pop-ups cover most of the screen, so they hide what's under them. */
    val PopupFill = Color(0xFF16181D)
    /** Tiles show this while their art loads, and behind transparent art. */
    val TileBase = Color(0xFF1A1C22)
}

/** Accent used for focus glow and small highlights; the user can pick one in Settings. */
enum class Accent(val label: String, val color: Color) {
    Glacier("Glacier", Color(0xFFA9C9FF)),
    Aurora("Aurora", Color(0xFF8CE6C9)),
    Ember("Ember", Color(0xFFFFB98F)),
    Orchid("Orchid", Color(0xFFD4B0FF)),
    Mono("Mono", Color(0xFFFFFFFF)),
}

val LocalAccent = staticCompositionLocalOf { Accent.Glacier.color }

/** When false every transition snaps; focus is still shown by border, glow and scale. */
val LocalAnimationsEnabled = staticCompositionLocalOf { true }

/** TV-safe area: 5% of the 960×540dp screen on each side. */
object SafeArea {
    val Horizontal = 48.dp
    val Vertical = 27.dp
}

object Radii {
    val Tile = 16.dp
    val Chip = 22.dp
    val Panel = 26.dp
    val Item = 14.dp
}

val TileShape = RoundedCornerShape(Radii.Tile)
val PanelShape = RoundedCornerShape(Radii.Panel)
val ItemShape = RoundedCornerShape(Radii.Item)
val PillShape = RoundedCornerShape(percent = 50)

/** Sizes are for a 540dp-tall screen viewed from a sofa: nothing below 14sp. */
object LauncherText {
    val Display = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 42.sp, lineHeight = 50.sp, letterSpacing = (-0.6).sp)
    val Time = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = 0.sp)
    val Date = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp)
    val Section = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp, letterSpacing = 0.2.sp)
    val Title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp)
    val Button = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp)
    val Body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp)
    val Label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp)
    val Caption = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp)
}

private val InterTypography: Typography = Typography().run {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = Inter),
        displayMedium = displayMedium.copy(fontFamily = Inter),
        displaySmall = displaySmall.copy(fontFamily = Inter),
        headlineLarge = headlineLarge.copy(fontFamily = Inter),
        headlineMedium = headlineMedium.copy(fontFamily = Inter),
        headlineSmall = headlineSmall.copy(fontFamily = Inter),
        titleLarge = titleLarge.copy(fontFamily = Inter),
        titleMedium = titleMedium.copy(fontFamily = Inter),
        titleSmall = titleSmall.copy(fontFamily = Inter),
        bodyLarge = bodyLarge.copy(fontFamily = Inter),
        bodyMedium = bodyMedium.copy(fontFamily = Inter),
        bodySmall = bodySmall.copy(fontFamily = Inter),
        labelLarge = labelLarge.copy(fontFamily = Inter),
        labelMedium = labelMedium.copy(fontFamily = Inter),
        labelSmall = labelSmall.copy(fontFamily = Inter),
    )
}

@Composable
fun LauncherTheme(
    accent: Accent = Accent.Glacier,
    animationsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = LauncherColors.Ink,
            surface = Glass.PanelFill,
            onBackground = LauncherColors.TextPrimary,
            onSurface = LauncherColors.TextPrimary,
        ),
        typography = InterTypography,
    ) {
        CompositionLocalProvider(
            LocalAccent provides accent.color,
            LocalAnimationsEnabled provides animationsEnabled,
            content = content,
        )
    }
}
