package com.zynnn.cloneapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zynnn.cloneapp.model.GlassIntensity
import com.zynnn.cloneapp.model.ThemeMode

data class LiquidGlassPalette(
    val isDark: Boolean,
    val glassAlpha: Float,
    val borderAlpha: Float,
    val glassTint: Color,
    val borderStart: Color,
    val borderEnd: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accentCyan: Color,
    val accentViolet: Color,
    val accentEmerald: Color,
    val accentRose: Color,
    val wallpaperIndex: Int
)

val LocalLiquidGlass = staticCompositionLocalOf {
    LiquidGlassPalette(
        isDark = true,
        glassAlpha = 0.18f,
        borderAlpha = 0.35f,
        glassTint = Color.White,
        borderStart = Color.White.copy(alpha = 0.45f),
        borderEnd = Color.White.copy(alpha = 0.08f),
        textPrimary = Color(0xFFF5F7FA),
        textSecondary = Color(0xFFB4C0D3),
        accentCyan = Color(0xFF38BDF8),
        accentViolet = Color(0xFFA855F7),
        accentEmerald = Color(0xFF34D399),
        accentRose = Color(0xFFFB7185),
        wallpaperIndex = 0
    )
}

private val ZynnnTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 19.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp)
)

@Composable
fun CloneAppByZynnnTheme(
    themeMode: ThemeMode,
    glassIntensity: GlassIntensity,
    wallpaperIndex: Int,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.FOLLOW_SYSTEM -> systemDark
    }

    val palette = if (isDark) {
        LiquidGlassPalette(
            isDark = true,
            glassAlpha = glassIntensity.alpha,
            borderAlpha = glassIntensity.borderAlpha,
            glassTint = Color(0xFFE2EEFF),
            borderStart = Color.White.copy(alpha = glassIntensity.borderAlpha + 0.12f),
            borderEnd = Color.White.copy(alpha = 0.06f),
            textPrimary = Color(0xFFF8FAFC),
            textSecondary = Color(0xFF94A3B8),
            accentCyan = Color(0xFF38BDF8),
            accentViolet = Color(0xFFC084FC),
            accentEmerald = Color(0xFF34D399),
            accentRose = Color(0xFFFB7185),
            wallpaperIndex = wallpaperIndex
        )
    } else {
        LiquidGlassPalette(
            isDark = false,
            glassAlpha = (glassIntensity.alpha + 0.28f).coerceAtMost(0.65f),
            borderAlpha = (glassIntensity.borderAlpha + 0.15f).coerceAtMost(0.75f),
            glassTint = Color.White,
            borderStart = Color.White.copy(alpha = 0.85f),
            borderEnd = Color(0xFF94A3B8).copy(alpha = 0.25f),
            textPrimary = Color(0xFF0F172A),
            textSecondary = Color(0xFF475569),
            accentCyan = Color(0xFF0284C7),
            accentViolet = Color(0xFF7E22CE),
            accentEmerald = Color(0xFF059669),
            accentRose = Color(0xFFE11D48),
            wallpaperIndex = wallpaperIndex
        )
    }

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = palette.accentCyan,
            secondary = palette.accentViolet,
            background = Color(0xFF060913),
            surface = Color(0xFF0F172A)
        )
    } else {
        lightColorScheme(
            primary = palette.accentCyan,
            secondary = palette.accentViolet,
            background = Color(0xFFEFF6FF),
            surface = Color.White
        )
    }

    CompositionLocalProvider(LocalLiquidGlass provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ZynnnTypography,
            content = content
        )
    }
}
