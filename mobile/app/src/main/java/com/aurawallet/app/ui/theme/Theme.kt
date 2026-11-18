package com.aurawallet.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.sp
import com.aurawallet.app.R

/** Токени кольору Aura — ті самі, що в iOS-версії (Theme.swift). */
@Immutable
data class AuraColors(
    val bg: Color,
    val surface: Color,
    val ink: Color,
    val onInk: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val positive: Color,
    val negative: Color,
    val lilac: Color,
    val blush: Color,
    val butter: Color,
    val stone: Color,
    val gradLilac: Color,
    val gradBlush: Color,
    val gradButter: Color,
)

val LightAura = AuraColors(
    bg = Color(0xFFFAFAF7), surface = Color(0xFFFFFFFF), ink = Color(0xFF121A0E), onInk = Color(0xFFFFFFFF),
    ink2 = Color(0xFF5C6457), ink3 = Color(0xFF8E9488), line = Color(0xFFECEAE4), positive = Color(0xFF2F6B1F),
    negative = Color(0xFFB3261E), lilac = Color(0xFFE9DAFB), blush = Color(0xFFF7E2E7), butter = Color(0xFFF4EFCF),
    stone = Color(0xFFF2F1EC), gradLilac = Color(0xFFDCC6FA), gradBlush = Color(0xFFF6CFDA), gradButter = Color(0xFFF3E3A6),
)

val DarkAura = AuraColors(
    bg = Color(0xFF0D0F0C), surface = Color(0xFF171A15), ink = Color(0xFFEEF0E9), onInk = Color(0xFF121A0E),
    ink2 = Color(0xFFA4AA9E), ink3 = Color(0xFF70766B), line = Color(0xFF262A23), positive = Color(0xFF8FCB7C),
    negative = Color(0xFFF2B8B5), lilac = Color(0xFF352B45), blush = Color(0xFF452C34), butter = Color(0xFF3D3A24),
    stone = Color(0xFF1F221C), gradLilac = Color(0xFF5B47A0), gradBlush = Color(0xFF8A4366), gradButter = Color(0xFF7A6A2A),
)

val LocalAura = staticCompositionLocalOf { LightAura }

val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
)

private val AuraTypography = Typography(
    headlineLarge = TextStyle(fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-1.2).sp),
    titleLarge = TextStyle(fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp),
)

object Aura {
    val colors: AuraColors @Composable get() = LocalAura.current
}

@Composable
fun AuraTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val aura = if (dark) DarkAura else LightAura
    val scheme = if (dark) {
        darkColorScheme(primary = aura.ink, onPrimary = aura.onInk, background = aura.bg, surface = aura.surface,
            onBackground = aura.ink, onSurface = aura.ink, outline = aura.line, error = aura.negative)
    } else {
        lightColorScheme(primary = aura.ink, onPrimary = aura.onInk, background = aura.bg, surface = aura.surface,
            onBackground = aura.ink, onSurface = aura.ink, outline = aura.line, error = aura.negative)
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalAura provides aura) {
        MaterialTheme(colorScheme = scheme, typography = AuraTypography, content = content)
    }
}
