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

/**
 * Plus Jakarta Sans (SIL Open Font License), Latin subset from Fontsource: open, rounded
 * shapes that stay clear from across a room. Glyphs it lacks fall back to the system font.
 */
val LauncherFont = FontFamily(
    Font(R.font.jakarta_light, FontWeight.Light),
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
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

/**
 * Accent themes, all on the same dark glass: the colour of the focus glow and rim, list
 * highlights and the search cursor. Picked under Launcher settings.
 */
enum class Accent(val label: String, val color: Color) {
    Glacier("Glacier", Color(0xFFA9C9FF)),
    Aurora("Aurora", Color(0xFF8CE6C9)),
    Ember("Ember", Color(0xFFFFB98F)),
    Orchid("Orchid", Color(0xFFD4B0FF));

    companion object {
        fun named(name: String): Accent = entries.firstOrNull { it.name == name } ?: Glacier
    }
}

val LocalAccent = staticCompositionLocalOf { Accent.Glacier.color }

/** The theme itself, for the settings panel to show which one is picked. */
val LocalAccentTheme = staticCompositionLocalOf { Accent.Glacier }

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
    val Hero = TextStyle(fontFamily = LauncherFont, fontWeight = FontWeight.SemiBold, fontSize = 54.sp, lineHeight = 62.sp, letterSpacing = (-0.9).sp)
    val Display = TextStyle(fontFamily = LauncherFont, fontWeight = FontWeight.SemiBold, fontSize = 42.sp, lineHeight = 50.sp, letterSpacing = (-0.6).sp)
    /** The home screen's clock, top-left beside the date: light, one top bar high. */
    val Clock = TextStyle(fontFamily = LauncherFont, fontWeight = FontWeight.Light, fontSize = 44.sp, lineHeight = 52.sp, letterSpacing = (-0.4).sp)
    /** The date and weather lines beside the clock. */
    val ClockDetail = TextStyle(fontFamily = LauncherFont, fontWeight = FontWeight.Light, fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = 0.3.sp)
    // Plus Jakarta Sans sets tight; text sizes get a little tracking so they read from a distance.
    val Section = TextStyle(fontFamily = LauncherFont, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 22.sp, letterSpacing = 0.3.sp)
    val Title = TextStyle(fontFamily = LauncherFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.1.sp)
    val Button = TextStyle(fontFamily = LauncherFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = 0.2.sp)
    val Body = TextStyle(fontFamily = LauncherFont, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = 0.25.sp)
    val Label = TextStyle(fontFamily = LauncherFont, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp)
    /** adb commands shown on screen: monospace so they read character by character. */
    val Code = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 17.sp)
    val Caption = TextStyle(fontFamily = LauncherFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.3.sp)
}

private val LauncherTypography: Typography = Typography().run {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = LauncherFont),
        displayMedium = displayMedium.copy(fontFamily = LauncherFont),
        displaySmall = displaySmall.copy(fontFamily = LauncherFont),
        headlineLarge = headlineLarge.copy(fontFamily = LauncherFont),
        headlineMedium = headlineMedium.copy(fontFamily = LauncherFont),
        headlineSmall = headlineSmall.copy(fontFamily = LauncherFont),
        titleLarge = titleLarge.copy(fontFamily = LauncherFont),
        titleMedium = titleMedium.copy(fontFamily = LauncherFont),
        titleSmall = titleSmall.copy(fontFamily = LauncherFont),
        bodyLarge = bodyLarge.copy(fontFamily = LauncherFont),
        bodyMedium = bodyMedium.copy(fontFamily = LauncherFont),
        bodySmall = bodySmall.copy(fontFamily = LauncherFont),
        labelLarge = labelLarge.copy(fontFamily = LauncherFont),
        labelMedium = labelMedium.copy(fontFamily = LauncherFont),
        labelSmall = labelSmall.copy(fontFamily = LauncherFont),
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
        typography = LauncherTypography,
    ) {
        CompositionLocalProvider(
            LocalAccent provides accent.color,
            LocalAccentTheme provides accent,
            LocalAnimationsEnabled provides animationsEnabled,
            content = content,
        )
    }
}
