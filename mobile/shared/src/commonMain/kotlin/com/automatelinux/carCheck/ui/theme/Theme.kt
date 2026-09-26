package com.automatelinux.carCheck.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The palette is the plate: Israeli registration plates are a yellow field, black
 * digits, and a blue band with the flag. Everything else in the app is asphalt
 * and paper, so the plate is the only saturated thing on screen and the eye
 * goes to it first — which is where the user's attention belongs.
 */
object Plate {
    val Yellow = Color(0xFFFFD21F)
    val YellowDeep = Color(0xFFE9B800)
    val Blue = Color(0xFF123C8C)
    val BlueBright = Color(0xFF2F63D6)
    val Ink = Color(0xFF14161A)
    val InkOnYellow = Color(0xFF1A1400)
}

/** Colours the Material scheme has no slot for: statuses, hairlines, the two paper tones. */
data class Palette(
    val page: Color,
    val panel: Color,
    val panelAlt: Color,
    val ink: Color,
    val inkDim: Color,
    val hairline: Color,
    val accent: Color,
    val onAccent: Color,
    val link: Color,
    val good: Color,
    val goodBg: Color,
    val warn: Color,
    val warnBg: Color,
    val bad: Color,
    val badBg: Color,
    val isNight: Boolean,
)

private val Day = Palette(
    page = Color(0xFFF3F4F6),
    panel = Color.White,
    panelAlt = Color(0xFFF7F8FA),
    ink = Color(0xFF15171C),
    inkDim = Color(0xFF646A76),
    hairline = Color(0xFFE4E6EB),
    accent = Plate.Blue,
    onAccent = Color.White,
    link = Plate.Blue,
    good = Color(0xFF1E8E4E),
    goodBg = Color(0xFFE6F5EC),
    warn = Color(0xFFB96A00),
    warnBg = Color(0xFFFFF3E0),
    bad = Color(0xFFC6303B),
    badBg = Color(0xFFFDE8EA),
    isNight = false,
)

private val Night = Palette(
    page = Color(0xFF0F1216),
    panel = Color(0xFF1A1E25),
    panelAlt = Color(0xFF22272F),
    ink = Color(0xFFECEEF2),
    inkDim = Color(0xFF9AA1AC),
    hairline = Color(0xFF2A2F38),
    accent = Plate.BlueBright,
    onAccent = Color.White,
    link = Color(0xFF8FB0FF),
    good = Color(0xFF4CC97A),
    goodBg = Color(0xFF17301F),
    warn = Color(0xFFF0A62B),
    warnBg = Color(0xFF33270F),
    bad = Color(0xFFF0616B),
    badBg = Color(0xFF3A181B),
    isNight = true,
)

val LocalPalette = staticCompositionLocalOf { Day }

private val LightScheme = lightColorScheme(
    primary = Plate.Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FA),
    onPrimaryContainer = Plate.Blue,
    secondary = Plate.YellowDeep,
    onSecondary = Plate.InkOnYellow,
    background = Day.page,
    onBackground = Day.ink,
    surface = Day.panel,
    onSurface = Day.ink,
    surfaceVariant = Day.panelAlt,
    onSurfaceVariant = Day.inkDim,
    outline = Day.hairline,
    error = Day.bad,
)

private val DarkScheme = darkColorScheme(
    primary = Plate.BlueBright,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1B2F5C),
    onPrimaryContainer = Color(0xFFDCE6FA),
    secondary = Plate.Yellow,
    onSecondary = Plate.InkOnYellow,
    background = Night.page,
    onBackground = Night.ink,
    surface = Night.panel,
    onSurface = Night.ink,
    surfaceVariant = Night.panelAlt,
    onSurfaceVariant = Night.inkDim,
    outline = Night.hairline,
    error = Night.bad,
)

// Registry data is dense; headings are set heavy so the scan order is obvious,
// and body text gets a little more leading than Material's default for Hebrew.
private val AppTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.4).sp, lineHeight = 34.sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.2).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Bold),
        bodyLarge = bodyLarge.copy(lineHeight = 24.sp),
        bodyMedium = bodyMedium.copy(lineHeight = 21.sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Bold),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp),
    )
}

@Composable
fun AppTheme(night: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides if (night) Night else Day) {
        MaterialTheme(
            colorScheme = if (night) DarkScheme else LightScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
