package com.shivam.tvlauncher.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
    val Ink = Color(0xFF0B0D12)
    val InkRaised = Color(0xFF161A23)
    val TextPrimary = Color(0xFFF5F6F8)
    val TextSecondary = Color(0xA6F5F6F8)
}

/** TV-safe area: ~5% horizontally, ~5% vertically on a 960×540dp screen. */
object SafeArea {
    val Horizontal = 48.dp
    val Vertical = 27.dp
}

val CardShape = RoundedCornerShape(16.dp)

object LauncherText {
    val Clock = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 22.sp, letterSpacing = 0.2.sp)
    val Date = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, letterSpacing = 0.2.sp)
    val RowTitle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.3.sp)
    val LabelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    val LabelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    val Body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp)
    val Placeholder = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold)
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
fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = LauncherColors.Ink,
            surface = LauncherColors.InkRaised,
            onBackground = LauncherColors.TextPrimary,
            onSurface = LauncherColors.TextPrimary,
        ),
        typography = InterTypography,
        content = content,
    )
}
