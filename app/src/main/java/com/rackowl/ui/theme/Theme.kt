package com.rackowl.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rackowl.R
import com.rackowl.data.ThemeMode

/** Palette du design (thème sombre) et son équivalent clair. */
@Immutable
data class HomeColors(
    val bg: Color,
    val surface: Color,
    val field: Color,
    val nav: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val textSecondary: Color,
    val muted: Color,
    val placeholder: Color,
    val selected: Color,
    val segmentOn: Color,
    val segmentOnText: Color,
    val trackOff: Color,
    val handle: Color,
    val accent: Color,
    val onAccent: Color,
    val accentBg: Color,
    val up: Color,
    val upBg: Color,
    val down: Color,
    val downBg: Color,
    val warn: Color,
    val warnBg: Color,
    val incidentBg: Color,
    val incidentBorder: Color,
    val incidentText: Color,
    val incidentInner: Color,
    val logBg: Color,
    val logBorder: Color,
    val isDark: Boolean,
)

val DarkColors = HomeColors(
    bg = Color(0xFF0F1115), surface = Color(0xFF171A21), field = Color(0xFF0F1115), nav = Color(0xFF13161C),
    border = Color(0xFF252A34), borderStrong = Color(0xFF2E3440),
    text = Color(0xFFE6E8EC), textSecondary = Color(0xFFC9CED6), muted = Color(0xFF9AA1AD), placeholder = Color(0xFF7D8491),
    selected = Color(0xFF1D2129), segmentOn = Color(0xFF252A34), segmentOnText = Color(0xFFFFFFFF),
    trackOff = Color(0xFF3A4150), handle = Color(0xFF5A6170),
    accent = Color(0xFF3DD6B5), onAccent = Color(0xFF0F1115), accentBg = Color(0xFF14302A),
    up = Color(0xFF4ADE80), upBg = Color(0xFF13261D), down = Color(0xFFF87171), downBg = Color(0xFF2C1719),
    warn = Color(0xFFF5A524), warnBg = Color(0xFF1A160C),
    incidentBg = Color(0xFF1E1416), incidentBorder = Color(0xFF4A2328), incidentText = Color(0xFFE3A9AE), incidentInner = Color(0xFF150E10),
    logBg = Color(0xFF0A0C0F), logBorder = Color(0xFF1E222A),
    isDark = true,
)

val LightColors = HomeColors(
    bg = Color(0xFFF4F5F7), surface = Color(0xFFFFFFFF), field = Color(0xFFF4F5F7), nav = Color(0xFFFFFFFF),
    border = Color(0xFFE1E4EA), borderStrong = Color(0xFFD3D8E0),
    text = Color(0xFF15181E), textSecondary = Color(0xFF3B414C), muted = Color(0xFF5F6672), placeholder = Color(0xFF858C98),
    selected = Color(0xFFEDEFF3), segmentOn = Color(0xFFE1E4EA), segmentOnText = Color(0xFF15181E),
    trackOff = Color(0xFFC5CAD3), handle = Color(0xFFA0A6B0),
    accent = Color(0xFF0E8F74), onAccent = Color(0xFFFFFFFF), accentBg = Color(0xFFDDF4EE),
    up = Color(0xFF15803D), upBg = Color(0xFFDCFCE7), down = Color(0xFFDC2626), downBg = Color(0xFFFDE4E4),
    warn = Color(0xFFB45309), warnBg = Color(0xFFFEF3E2),
    incidentBg = Color(0xFFFDF0F0), incidentBorder = Color(0xFFF3C4C4), incidentText = Color(0xFF9B2C2C), incidentInner = Color(0xFFFFF7F7),
    logBg = Color(0xFF0A0C0F), logBorder = Color(0xFF1E222A),
    isDark = false,
)

val Plex = FontFamily(
    Font(R.font.plex_sans_regular, FontWeight.Normal),
    Font(R.font.plex_sans_medium, FontWeight.Medium),
    Font(R.font.plex_sans_semibold, FontWeight.SemiBold),
)

val Mono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
)

val LocalColors = staticCompositionLocalOf { DarkColors }

object Home {
    val colors: HomeColors @Composable get() = LocalColors.current
}

fun mono(size: Int, color: Color = Color.Unspecified, weight: FontWeight = FontWeight.Medium) =
    TextStyle(fontFamily = Mono, fontSize = size.sp, fontWeight = weight, color = color)

@Composable
fun HomeTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val c = if (dark) DarkColors else LightColors
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = c.accent, onPrimary = c.onAccent, background = c.bg, surface = c.surface,
        onBackground = c.text, onSurface = c.text, outline = c.border, error = c.down,
        surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.selected,
    )
    val base = TextStyle(fontFamily = Plex, color = c.text)
    val typography = MaterialTheme.typography.let { t ->
        t.copy(
            bodyLarge = t.bodyLarge.merge(base), bodyMedium = t.bodyMedium.merge(base), bodySmall = t.bodySmall.merge(base),
            titleLarge = t.titleLarge.merge(base), titleMedium = t.titleMedium.merge(base), titleSmall = t.titleSmall.merge(base),
            labelLarge = t.labelLarge.merge(base), labelMedium = t.labelMedium.merge(base), labelSmall = t.labelSmall.merge(base),
            headlineSmall = t.headlineSmall.merge(base),
        )
    }
    CompositionLocalProvider(LocalColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
