package io.github.zacharycanlas.cadence.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.zacharycanlas.cadence.R

private val Cobalt = Color(0xFF1F4FD8)
private val CobaltDeep = Color(0xFF16359A)
private val Ink = Color(0xFF0E1116)
private val Lime = Color(0xFFC8F031)
private val Laminate = Color(0xFFFFFFFF)

/** The band's own colours, which Material's roles do not name. */
@Immutable
data class BandColors(
    val band: Color,
    val onBand: Color,
    /** Secondary text on the band, tinted from the band rather than grey. */
    val onBandMuted: Color,
    /** "You are here": the current ramp row and the beat flash, and nothing else. */
    val marker: Color,
    val onMarker: Color,
    /** The hairlines that rule every module and split row. */
    val rule: Color,
)

private val LightBand = BandColors(
    band = Cobalt, onBand = Laminate, onBandMuted = Color(0xFFC9D6FA),
    marker = Lime, onMarker = Ink, rule = Color(0xFFD5DAE3),
)

private val DarkBand = BandColors(
    band = CobaltDeep, onBand = Laminate, onBandMuted = Color(0xFFB4C4F2),
    marker = Lime, onMarker = Ink, rule = Color(0xFF2A303B),
)

val LocalBandColors = staticCompositionLocalOf { LightBand }

private val LightScheme = lightColorScheme(
    primary = Cobalt,
    onPrimary = Laminate,
    primaryContainer = Color(0xFFDDE5FC),
    onPrimaryContainer = Color(0xFF0B2370),
    secondary = Color(0xFF3B4352),
    onSecondary = Laminate,
    secondaryContainer = Color(0xFFE6E9EF),
    onSecondaryContainer = Ink,
    background = Laminate,
    onBackground = Ink,
    surface = Laminate,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF1F3F6),
    onSurfaceVariant = Color(0xFF4A5261),
    surfaceContainer = Color(0xFFF4F6F9),
    surfaceContainerHigh = Color(0xFFEDF0F4),
    outline = Color(0xFF7A8292),
    outlineVariant = Color(0xFFD5DAE3),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9DB5FF),
    onPrimary = Color(0xFF0B2370),
    primaryContainer = CobaltDeep,
    onPrimaryContainer = Color(0xFFDDE5FC),
    secondary = Color(0xFFC3C9D4),
    onSecondary = Ink,
    secondaryContainer = Color(0xFF2A303B),
    onSecondaryContainer = Color(0xFFE6E9EF),
    background = Ink,
    onBackground = Color(0xFFE9ECF1),
    surface = Ink,
    onSurface = Color(0xFFE9ECF1),
    surfaceVariant = Color(0xFF1B2029),
    onSurfaceVariant = Color(0xFFAEB5C2),
    surfaceContainer = Color(0xFF161A21),
    surfaceContainerHigh = Color(0xFF1D222B),
    outline = Color(0xFF8A92A1),
    outlineVariant = Color(0xFF2A303B),
)

/** Printed figures: every measurement on the band and in the split rows. */
val Figures = FontFamily(
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_medium, FontWeight.Medium),
)

private const val TABULAR = "tnum"

private val BaseType = Typography()

private val CadenceType = BaseType.copy(
    displayLarge = TextStyle(fontFamily = Figures, fontWeight = FontWeight.SemiBold, fontSize = 128.sp, lineHeight = 120.sp, fontFeatureSettings = TABULAR),
    headlineSmall = TextStyle(fontFamily = Figures, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 30.sp, fontFeatureSettings = TABULAR),
    titleMedium = BaseType.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    // The modules' header tabs.
    labelSmall = TextStyle(fontFamily = Figures, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 1.4.sp),
)

/** Figures inside running text (split rows, slider values), at body size. */
val FigureBody = TextStyle(fontFamily = Figures, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 24.sp, fontFeatureSettings = TABULAR)

private val CadenceShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
)

@Composable
fun CadenceTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(LocalBandColors provides if (dark) DarkBand else LightBand) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = CadenceType,
            shapes = CadenceShapes,
            content = content,
        )
    }
}
