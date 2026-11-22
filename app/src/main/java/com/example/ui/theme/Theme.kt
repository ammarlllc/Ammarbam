package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val AmmarDarkColorScheme =
  darkColorScheme(
    primary = ElectricViolet,
    onPrimary = TextPrimary,
    primaryContainer = DeepRoyalPurple,
    onPrimaryContainer = TextPrimary,
    secondary = CyberCyan,
    onSecondary = ObsidianBg,
    secondaryContainer = ColorTokens.SecondaryContainer,
    onSecondaryContainer = CyberCyan,
    tertiary = CyberMagenta,
    onTertiary = TextPrimary,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceHigh,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    outline = ObsidianCardBorder,
    outlineVariant = ColorTokens.OutlineMuted,
    error = CrimsonError,
    onError = ObsidianBg,
  )

private object ColorTokens {
  val SecondaryContainer = androidx.compose.ui.graphics.Color(0xFF0A2E33)
  val OutlineMuted = androidx.compose.ui.graphics.Color(0xFF2B1F4D)
}

val AmmarShapes =
  Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
  )

@Composable
fun AmmarShareTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = AmmarDarkColorScheme,
    typography = AppTypography,
    shapes = AmmarShapes,
    content = content,
  )
}
