package com.opencapture.pocketglance.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object GlanceColors {
    val canvas = Color(0xFFF7F4EF)
    val pear = Color(0xFFE4C35A)
    val coral = Color(0xFFE85D3B)
    val azure = Color(0xFF2F6FED)
    val ink = Color(0xFF1A1A1A)
    val inkMuted = Color(0xFF5C5A56)
    val surface = Color(0xFFFFFFFF)
    val hairline = Color(0x1A1A1A1A)
}

private val glanceScheme =
    lightColorScheme(
        primary = GlanceColors.pear,
        onPrimary = GlanceColors.ink,
        secondary = GlanceColors.azure,
        onSecondary = Color.White,
        tertiary = GlanceColors.coral,
        onTertiary = Color.White,
        background = GlanceColors.canvas,
        onBackground = GlanceColors.ink,
        surface = GlanceColors.surface,
        onSurface = GlanceColors.ink,
        surfaceVariant = Color(0xFFF0EBE3),
        onSurfaceVariant = GlanceColors.inkMuted,
        error = GlanceColors.coral,
        onError = Color.White,
    )

private val glanceType =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                lineHeight = 44.sp,
                color = GlanceColors.ink,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 30.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 22.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = GlanceColors.inkMuted,
            ),
        labelLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            ),
    )

@Composable
fun GlanceTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = glanceScheme, typography = glanceType, content = content)
}
