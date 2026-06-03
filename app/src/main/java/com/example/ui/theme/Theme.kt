package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CosmicDarkColorScheme = darkColorScheme(
    primary = NeonBlue,
    secondary = NeonPink,
    tertiary = AcidGreen,
    background = DeepBackground,
    surface = SurfaceDark,
    onPrimary = DeepBackground,
    onSecondary = DeepBackground,
    onTertiary = DeepBackground,
    onBackground = DarkTextPrimary,
    onSurface = DarkTextPrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark mode for a professional premium DAW aesthetic
    dynamicColor: Boolean = false, // Disable dynamic colors to preserve our customized cyber-neon brand identity
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CosmicDarkColorScheme,
        typography = Typography,
        content = content
    )
}
